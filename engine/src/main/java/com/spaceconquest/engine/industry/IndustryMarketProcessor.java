package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.economy.MarketAccount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Clears paid industrial production against physical hub stock and hub cash. */
public class IndustryMarketProcessor {
    public record TurnResult(List<Empire> empires, List<Corporation> corporations,
                             List<CommercialHub> hubs, List<MarketAccount> marketAccounts,
                             List<GeologicalDeposit> deposits, List<IndustryAccount> industryAccounts,
                             Map<String, Double> imperialReceipts, Map<String, Double> imperialExpenses) {}

    public TurnResult process(GameState state, Map<String, Integer> paidWorkers,
                              Map<String, Double> paidWages) {
        Ledger ledger = new Ledger(state);
        Map<String, IndustryAccount> previous = new HashMap<>();
        for (IndustryAccount account : state.industryAccounts()) previous.put(account.facilityId(), account);
        List<IndustryAccount> accounts = new ArrayList<>();
        for (IndustrialFacility facility : state.industrialFacilities()) {
            IndustryAccount old = previous.get(facility.id());
            accounts.add(processFacility(facility, old, paidWorkers.getOrDefault(facility.id(), 0),
                    paidWages.getOrDefault(facility.id(), 0.0), ledger));
        }
        return new TurnResult(List.copyOf(ledger.empires.values()), List.copyOf(ledger.corporations.values()),
                List.copyOf(ledger.hubs.values()), List.copyOf(ledger.marketAccounts.values()),
                List.copyOf(ledger.deposits.values()), List.copyOf(accounts),
                Map.copyOf(ledger.imperialReceipts), Map.copyOf(ledger.imperialExpenses));
    }

    private IndustryAccount processFacility(IndustrialFacility facility, IndustryAccount previous,
                                             int paidWorkers, double wages, Ledger ledger) {
        Map<String, Double> stock = new HashMap<>(previous == null ? Map.of() : previous.unsoldStockKg());
        Map<String, Double> produced = new HashMap<>();
        Map<String, Double> sold = new HashMap<>();
        IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(facility.applicationId());
        Empire technologyOwner = ledger.technologyOwner(facility);
        if (technologyOwner == null) {
            return new IndustryAccount(facility.id(), stock, produced, sold, 0.0, wages, 0.0, 0.0, 0.0, 0.0);
        }
        CommercialHub hub = ledger.hubOn(facility.planetId());
        double inputCosts = 0.0;
        if (hub != null && paidWorkers > 0 && facility.allocatedWorkers() > 0
                && IndustryRecipeCatalog.isUnlocked(recipe, technologyOwner)
                && facility.tier() >= recipe.minTier()) {
            double batches = Math.min(paidWorkers, facility.allocatedWorkers())
                    * facility.getEffectiveThroughputMultiplier()
                    * recipe.technologyMultiplier(technologyOwner) / recipe.workersPerBatch();
            batches = affordableBatches(recipe, batches, facility, hub, ledger);
            if (batches > 0.0) {
                inputCosts = buyInputs(recipe, batches, facility, hub, ledger);
                produce(recipe, batches, facility, ledger, stock, produced);
            }
        }
        double sales = sellStock(facility, hub, stock, sold, ledger);
        return new IndustryAccount(facility.id(), stock, produced, sold, inputCosts,
                wages, sales, 0.0, 0.0, 0.0);
    }

    private double affordableBatches(IndustryRecipeCatalog.Recipe recipe, double requested,
                                      IndustrialFacility facility, CommercialHub hub, Ledger ledger) {
        double batches = Math.max(0.0, requested);
        double costPerBatch = 0.0;
        for (var input : recipe.inputsKg().entrySet()) {
            MarketOrder order = hub.activeOrders().get(input.getKey());
            if (order == null || order.supplyKg() <= 0.0 || input.getValue() <= 0.0) return 0.0;
            batches = Math.min(batches, order.supplyKg() / input.getValue());
            costPerBatch += input.getValue() * Math.max(0.0, order.pricePerKg());
        }
        if (costPerBatch > 0.0) {
            batches = Math.min(batches, ledger.ownerBalance(facility) / costPerBatch);
        }
        if (recipe.extractsDeposit()) {
            GeologicalDeposit deposit = ledger.depositOn(facility.planetId(),
                    recipe.outputsKg().keySet().iterator().next());
            if (deposit == null) return 0.0;
            batches = Math.min(batches, deposit.remainingVolumeKg()
                    / recipe.outputsKg().values().iterator().next());
        }
        return Math.max(0.0, batches);
    }

    private double buyInputs(IndustryRecipeCatalog.Recipe recipe, double batches,
                             IndustrialFacility facility, CommercialHub initialHub, Ledger ledger) {
        double cost = 0.0;
        for (var input : recipe.inputsKg().entrySet()) {
            CommercialHub hub = ledger.hubs.get(initialHub.id());
            MarketOrder order = hub.activeOrders().get(input.getKey());
            double amount = input.getValue() * batches;
            cost += amount * Math.max(0.0, order.pricePerKg());
            ledger.replaceOrder(hub, input.getKey(), order.supplyKg() - amount,
                    hub.currentStoredWeightKg() - amount);
        }
        ledger.changeOwnerBalance(facility, -cost);
        ledger.changeHubCash(initialHub.id(), cost);
        return cost;
    }

    private void produce(IndustryRecipeCatalog.Recipe recipe, double batches,
                         IndustrialFacility facility, Ledger ledger,
                         Map<String, Double> stock, Map<String, Double> produced) {
        for (var output : recipe.outputsKg().entrySet()) {
            double amount = output.getValue() * batches;
            if (recipe.extractsDeposit()) {
                GeologicalDeposit deposit = ledger.depositOn(facility.planetId(), output.getKey());
                if (deposit == null) continue;
                ledger.deposits.put(deposit.id(), new GeologicalDeposit(deposit.id(), deposit.planetId(),
                        deposit.materialId(), deposit.initialVolumeKg(),
                        Math.max(0.0, deposit.remainingVolumeKg() - amount),
                        deposit.concentrationModifier(), deposit.isDiscovered(), deposit.ownerEntityId()));
            }
            stock.merge(output.getKey(), amount, Double::sum);
            produced.merge(output.getKey(), amount, Double::sum);
        }
    }

    private double sellStock(IndustrialFacility facility, CommercialHub initialHub,
                               Map<String, Double> stock, Map<String, Double> sold, Ledger ledger) {
        double gross = 0.0;
        if (initialHub == null) return 0.0;
        for (String resource : List.copyOf(stock.keySet())) {
            CommercialHub hub = ledger.hubs.get(initialHub.id());
            MarketOrder order = hub.activeOrders().get(resource);
            double price = order == null ? 2.0 : Math.max(0.01, order.pricePerKg());
            double freeSpace = Math.max(0.0, hub.storageCapacityKg() - hub.currentStoredWeightKg());
            double amount = Math.min(stock.get(resource), Math.min(freeSpace,
                    ledger.hubCash(hub.id()) / price));
            if (amount <= 0.0) continue;
            double payment = amount * price;
            ledger.changeHubCash(hub.id(), -payment);
            ledger.changeOwnerBalance(facility, payment);
            ledger.replaceOrder(hub, resource, (order == null ? 0.0 : order.supplyKg()) + amount,
                    hub.currentStoredWeightKg() + amount);
            stock.put(resource, Math.max(0.0, stock.get(resource) - amount));
            sold.merge(resource, amount, Double::sum);
            gross += payment;
        }
        stock.entrySet().removeIf(entry -> entry.getValue() <= 0.000001);
        return gross;
    }

    private static final class Ledger {
        private final Map<String, Empire> empires = new LinkedHashMap<>();
        private final Map<String, Corporation> corporations = new LinkedHashMap<>();
        private final Map<String, CommercialHub> hubs = new LinkedHashMap<>();
        private final Map<String, MarketAccount> marketAccounts = new LinkedHashMap<>();
        private final Map<String, GeologicalDeposit> deposits = new LinkedHashMap<>();
        private final Map<String, Double> imperialReceipts = new HashMap<>();
        private final Map<String, Double> imperialExpenses = new HashMap<>();

        private Ledger(GameState state) {
            for (Empire item : state.empires()) empires.put(item.id(), item);
            for (Corporation item : state.corporations()) corporations.put(item.id(), item);
            for (CommercialHub item : state.commercialHubs()) hubs.put(item.id(), item);
            for (MarketAccount item : state.marketAccounts()) marketAccounts.put(item.hubId(), item);
            for (GeologicalDeposit item : state.geologicalDeposits()) deposits.put(item.id(), item);
        }

        private Empire technologyOwner(IndustrialFacility facility) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                return empires.get(facility.ownerEntityId());
            }
            Corporation owner = corporations.get(facility.ownerEntityId());
            return owner == null ? null : empires.get(owner.empireId());
        }

        private CommercialHub hubOn(String bodyId) {
            return hubs.values().stream().filter(hub -> bodyId.equals(hub.entityId())).findFirst().orElse(null);
        }

        private GeologicalDeposit depositOn(String bodyId, String materialId) {
            return deposits.values().stream().filter(deposit -> bodyId.equals(deposit.planetId())
                    && materialId.equals(deposit.materialId()) && deposit.isDiscovered()
                    && deposit.remainingVolumeKg() > 0.0).findFirst().orElse(null);
        }

        private double ownerBalance(IndustrialFacility facility) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                Empire owner = empires.get(facility.ownerEntityId());
                return owner == null ? 0.0 : owner.treasuryCredits();
            }
            Corporation owner = corporations.get(facility.ownerEntityId());
            return owner == null ? 0.0 : owner.liquidCapitalReserves();
        }

        private void changeOwnerBalance(IndustrialFacility facility, double delta) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                changeEmpireBalance(facility.ownerEntityId(), delta);
                return;
            }
            Corporation owner = corporations.get(facility.ownerEntityId());
            if (owner == null) return;
            corporations.put(owner.id(), new Corporation(owner.id(), owner.name(), owner.empireId(),
                    owner.headquartersEntityId(), owner.marketOrientation(),
                    Math.max(0.0, owner.liquidCapitalReserves() + delta), owner.ownedFacilityIds(),
                    owner.ownedShipIds(), owner.claimedVeinIds()));
        }

        private void changeEmpireBalance(String empireId, double delta) {
            Empire owner = empires.get(empireId);
            if (owner == null) return;
            if (delta > 0.0) imperialReceipts.merge(empireId, delta, Double::sum);
            if (delta < 0.0) imperialExpenses.merge(empireId, -delta, Double::sum);
            empires.put(owner.id(), new Empire(owner.id(), owner.name(), owner.raceId(),
                    owner.societyStructure(), Math.max(0.0, owner.treasuryCredits() + delta),
                    owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(),
                    owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds()));
        }

        private double hubCash(String hubId) {
            MarketAccount account = marketAccounts.get(hubId);
            return account == null ? 0.0 : account.unsettledSalesCredits();
        }

        private void changeHubCash(String hubId, double delta) {
            marketAccounts.put(hubId, new MarketAccount(hubId, Math.max(0.0, hubCash(hubId) + delta)));
        }

        private void replaceOrder(CommercialHub hub, String resource, double quantity, double storedWeight) {
            MarketOrder prior = hub.activeOrders().get(resource);
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            orders.put(resource, new MarketOrder(resource, Math.max(0.0, quantity),
                    prior == null ? 0.0 : prior.demandKg(), prior == null ? 2.0 : prior.pricePerKg(),
                    prior == null ? 0.0 : prior.shortcomingScore()));
            hubs.put(hub.id(), new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                    hub.storageCapacityKg(), Math.max(0.0, storedWeight), hub.logisticsRangeUnits(), orders));
        }
    }
}
