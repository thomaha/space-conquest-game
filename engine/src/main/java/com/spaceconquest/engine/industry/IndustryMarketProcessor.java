package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.economy.MarketAccount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Clears paid industrial production against physical hub stock and hub cash. */
public class IndustryMarketProcessor {
    public static final double WHOLESALE_SHARE = 0.80;
    public record TurnResult(List<Empire> empires, List<Corporation> corporations,
                             List<CommercialHub> hubs, List<MarketAccount> marketAccounts,
                             List<GeologicalDeposit> deposits, List<IndustryAccount> industryAccounts,
                             Map<String, Double> imperialReceipts, Map<String, Double> imperialExpenses,
                             Map<String, Double> productionEnergyKwh, Map<String, Double> productionPlantSales,
                             com.spaceconquest.engine.economy.BusinessLedger businessLedger) {}

    public TurnResult process(GameState state, Map<String, Integer> paidWorkers,
                              Map<String, Double> paidWages) {
        return process(state, paidWorkers, paidWages, paidWorkers);
    }

    public TurnResult process(GameState state, Map<String, Integer> poweredWorkers,
                              Map<String, Double> paidWages,
                              Map<String, Integer> actualPaidWorkers) {
        return process(state, poweredWorkers, paidWages, actualPaidWorkers, null);
    }

    public TurnResult process(GameState state, Map<String, Integer> poweredWorkers,
                              Map<String, Double> paidWages, Map<String, Integer> actualPaidWorkers,
                              Map<String, ProductionWork.EnergyBudget> workEnergy) {
        Ledger ledger = new Ledger(state);
        Map<String, IndustryAccount> previous = new HashMap<>();
        for (IndustryAccount account : state.industryAccounts()) previous.put(account.facilityId(), account);
        List<IndustryAccount> accounts = new ArrayList<>();
        for (IndustrialFacility facility : state.industrialFacilities()) {
            IndustryAccount old = previous.get(facility.id());
            IndustryAccount day = processFacility(facility, old,
                    poweredWorkers.getOrDefault(facility.id(), 0),
                    paidWages.getOrDefault(facility.id(), 0.0),
                    actualPaidWorkers.getOrDefault(facility.id(), 0), ledger, workEnergy);
            for (var activity : state.launchActivities()) {
                if (facility.id().equals(activity.providerId()))
                    day = day.withPowerSale(activity.feeCredits())
                            .withPowerCost(activity.powerCostCredits());
            }
            accounts.add(day);
        }
        ledger.payProductionGenerators();
        accounts = accounts.stream().map(ledger.cash::withDay).toList();
        return new TurnResult(List.copyOf(ledger.empires.values()), List.copyOf(ledger.corporations.values()),
                List.copyOf(ledger.hubs.values()), List.copyOf(ledger.marketAccounts.values()),
                List.copyOf(ledger.deposits.values()), List.copyOf(accounts),
                Map.copyOf(ledger.imperialReceipts), Map.copyOf(ledger.imperialExpenses),
                Map.copyOf(ledger.productionEnergy), Map.copyOf(ledger.productionPlantSales), state.businessLedger().withContracts(ledger.contracts));
    }

    private IndustryAccount processFacility(IndustrialFacility facility, IndustryAccount previous,
                                             int poweredWorkers, double wages,
                                             int actualPaidWorkers, Ledger ledger,
                                             Map<String, ProductionWork.EnergyBudget> workEnergy) {
        Map<String, Double> stock = new HashMap<>(previous == null ? Map.of() : previous.unsoldStockKg());
        Map<String, Double> produced = new HashMap<>();
        Map<String, Double> sold = new HashMap<>();
        var refinery = previous == null ? RefineryState.empty() : previous.refineryState();
        var farming = previous == null ? FarmingState.empty() : previous.farmingState();
        var refining = RefineryProcess.plan(ledger.state, facility, refinery);
        IndustryRecipeCatalog.Recipe recipe = refining == null ? IndustryRecipeCatalog.find(facility.applicationId()) : refining.recipe();
        Empire technologyOwner = ledger.technologyOwner(facility);
        if (technologyOwner == null) {
            return new IndustryAccount(facility.id(), stock, produced, sold,
                    0.0, wages, 0.0, 0.0, 0.0, 0.0).withPaidWorkers(actualPaidWorkers).withRefineryState(refinery).withFarmingState(farming);
        }
        CommercialHub hub = ledger.hubOn(facility.planetId());
        double inputCosts = 0.0;
        double powerCosts = 0.0;
        boolean workBased = ProductionWork.definition(facility.applicationId()) != null;
        if (hub != null && (workBased || poweredWorkers > 0 && facility.allocatedWorkers() > 0)
                && com.spaceconquest.engine.economy.BusinessOperatingPlan.allowed(ledger.state, facility)
                && IndustryRecipeCatalog.isUnlocked(recipe, technologyOwner)
                && facility.tier() >= recipe.minTier()
                && FacilityManufacturingCapacity.canOperate(ledger.state, facility)) {
            double batches = Math.min(poweredWorkers, facility.allocatedWorkers())
                    * facility.getEffectiveThroughputMultiplier()
                    * ApplicationProduction.modifiers(ledger.state, technologyOwner.id(), facility.applicationId()).effectMultiplier()
                    * recipe.technologyMultiplier(technologyOwner) / recipe.workersPerBatch();
            ProductionWork.EnergyBudget budget = null;
            if (workBased) {
                // Compatibility processor entry points supply settled powered operators but no grid budget.
                int operators = IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())
                        ? facility.allocatedWorkers() : actualPaidWorkers;
                var quote = ProductionWork.quote(ledger.state, facility, operators);
                budget = workEnergy == null
                        ? new ProductionWork.EnergyBudget(ProductionWork.quote(ledger.state, facility, poweredWorkers).energyKwh(), 0)
                        : workEnergy.getOrDefault(facility.id(), new ProductionWork.EnergyBudget(0, 0));
                batches = Math.min(quote.batchesPerDay(), budget.availableKwh()
                        / ProductionWork.definition(ledger.state, facility).energyKwhPerBatch());
            }
            if (refining != null) batches = Math.min(batches, refining.maximumBatches());
            if (recipe.farming() != null && farming.lastWorkedTurn() >= ledger.state.turn()) batches = 0;
            batches = affordableBatches(recipe, batches, facility, hub, ledger);
            if (workBased && !IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())) {
                double materials = recipe.inputsKg().entrySet().stream().mapToDouble(input -> input.getValue()
                        * (hub.activeOrders().get(input.getKey()) == null ? 0
                        : Math.max(0, hub.activeOrders().get(input.getKey()).pricePerKg()))).sum();
                double energy = ProductionWork.definition(ledger.state, facility).energyKwhPerBatch() * budget.pricePerKwh();
                if (materials + energy > 0) batches = Math.min(batches, ledger.ownerBalance(facility) / (materials + energy));
            }
            if (batches > 0.0) {
                if (workBased) {
                    double energy = batches * ProductionWork.definition(ledger.state, facility).energyKwhPerBatch();
                    ledger.productionEnergy.merge(facility.planetId(), energy, Double::sum);
                    powerCosts = IndustrialFacility.HIVE_GRID.equals(facility.ownershipType()) ? 0 : energy * budget.pricePerKwh();
                    ledger.changeOwnerBalance(facility, -powerCosts);
                    ledger.productionCharges.merge(facility.planetId(), powerCosts, Double::sum);
                }
                inputCosts = buyInputs(recipe, batches, facility, hub, ledger);
                if (recipe.farming() == null) produce(recipe, batches, facility, ledger, stock, produced);
                else {
                    farming = farming.cultivate(recipe.applicationId(), ledger.state.turn(), batches);
                    if (farming.mature(ledger.state.turn(), recipe.farming())) {
                        produce(recipe, farming.cultivatedBatches(), facility, ledger, stock, produced);
                        farming = farming.harvested();
                    }
                }
                if (refining != null) refinery = refining.complete(batches, ledger.state.turn());
            }
        }
        double sales = sellStock(facility, hub, stock, sold, ledger);
        double maintenance = OperatingReservePolicy.maintenance(facility);
        maintenance = Math.min(maintenance, ledger.ownerBalance(facility));
        if (maintenance > 0.0) ledger.changeOwnerBalance(facility, -maintenance);
        return new IndustryAccount(facility.id(), stock, produced, sold, inputCosts,
                wages, sales, 0.0, 0.0, powerCosts).withMaintenanceCost(maintenance)
                .withPaidWorkers(actualPaidWorkers).withRefineryState(refinery).withFarmingState(farming);
    }

    private double affordableBatches(IndustryRecipeCatalog.Recipe recipe, double requested,
                                      IndustrialFacility facility, CommercialHub hub, Ledger ledger) {
        double batches = Math.max(0.0, requested);
        batches = Math.min(batches, ProcessInput.maximumBatches(ledger.state, facility.planetId(), recipe.inputSources(), ledger.deposits.values()));
        double costPerBatch = 0.0;
        for (var input : recipe.inputsKg().entrySet()) {
            MarketOrder order = hub.activeOrders().get(input.getKey());
            if (order == null || order.supplyKg() <= 0.0 || input.getValue() <= 0.0) return 0.0;
            batches = Math.min(batches, order.supplyKg() / input.getValue());
            costPerBatch += input.getValue() * Math.max(0.0, order.pricePerKg());
        }
        if (costPerBatch > 0.0 && !IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())) {
            batches = Math.min(batches, ledger.ownerBalance(facility) / costPerBatch);
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
        if (IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())) return 0.0;
        ledger.changeOwnerBalance(facility, -cost);
        ledger.changeHubCash(initialHub.id(), cost);
        return cost;
    }

    private void produce(IndustryRecipeCatalog.Recipe recipe, double batches,
                         IndustrialFacility facility, Ledger ledger,
                         Map<String, Double> stock, Map<String, Double> produced) {
        ProcessInput.consumeDeposits(ledger.deposits, facility.planetId(), recipe.inputSources(), batches);
        for (var output : recipe.outputsKg().entrySet()) {
            double amount = output.getValue() * batches;
            stock.merge(output.getKey(), amount, Double::sum);
            produced.merge(output.getKey(), amount, Double::sum);
        }
    }

    private double sellStock(IndustrialFacility facility, CommercialHub initialHub,
                               Map<String, Double> stock, Map<String, Double> sold, Ledger ledger) {
        if (initialHub == null) return 0.0;
        double gross = sellContractedStock(facility, initialHub, stock, sold, ledger);
        boolean hive = IndustrialFacility.HIVE_GRID.equals(facility.ownershipType());
        for (String resource : List.copyOf(stock.keySet())) {
            CommercialHub hub = ledger.hubs.get(initialHub.id());
            MarketOrder order = hub.activeOrders().get(resource);
            if (!hive && (order == null || order.demandKg() <= 0.0)) continue;
            double price = (order == null ? 2.0 : Math.max(0.01, order.pricePerKg())) * WHOLESALE_SHARE;
            double freeSpace = Math.max(0.0, hub.storageCapacityKg() - hub.currentStoredWeightKg());
            double wantedKg = hive ? freeSpace
                    : Math.max(0.0, order.demandKg() * 30.0 - order.supplyKg());
            double amount = Math.min(stock.get(resource), Math.min(freeSpace, wantedKg));
            if (!hive) amount = Math.min(amount, ledger.hubCash(hub.id()) / price);
            if (amount <= 0.0) continue;
            double payment = hive ? 0.0 : amount * price;
            if (!hive) {
                ledger.changeHubCash(hub.id(), -payment);
                ledger.changeOwnerBalance(facility, payment);
            }
            ledger.replaceOrder(hub, resource, (order == null ? 0.0 : order.supplyKg()) + amount,
                    hub.currentStoredWeightKg() + amount);
            stock.put(resource, Math.max(0.0, stock.get(resource) - amount));
            sold.merge(resource, amount, Double::sum);
            gross += payment;
        }
        stock.entrySet().removeIf(entry -> entry.getValue() <= 0.000001);
        return gross;
    }

    private double sellContractedStock(IndustrialFacility facility, CommercialHub initialHub, Map<String, Double> stock,
                                       Map<String, Double> sold, Ledger ledger) {
        double receipts = 0;
        for (int index = 0; index < ledger.contracts.size(); index++) {
            var contract = ledger.contracts.get(index);
            if (!contract.openAt(ledger.state.turn()) || !contract.facilityId().equals(facility.id())
                    || !contract.supplierOwnerId().equals(facility.ownerEntityId()) || !contract.hubId().equals(initialHub.id())) continue;
            var hub = ledger.hubs.get(initialHub.id());
            double quantity = Math.min(contract.remainingKg(), Math.min(stock.getOrDefault(contract.materialId(), 0.0),
                    Math.max(0, hub.storageCapacityKg() - hub.currentStoredWeightKg())));
            if (quantity <= 0) continue;
            double payment = quantity * contract.pricePerKg();
            ledger.changeOwnerBalance(facility, payment);
            var order = hub.activeOrders().get(contract.materialId());
            ledger.replaceOrder(hub, contract.materialId(), (order == null ? 0 : order.supplyKg()) + quantity, hub.currentStoredWeightKg() + quantity);
            stock.put(contract.materialId(), Math.max(0, stock.get(contract.materialId()) - quantity));
            sold.merge(contract.materialId(), quantity, Double::sum);
            var delivered = contract.delivered(quantity);
            if (com.spaceconquest.engine.market.SupplyContract.FULFILLED.equals(delivered.status()))
                ledger.changeHubCash(hub.id(), Math.max(0, contract.escrowCredits() - payment));
            ledger.contracts.set(index, delivered);
            receipts += payment;
        }
        return receipts;
    }

    private static final class Ledger {
        private final GameState state;
        private final List<com.spaceconquest.engine.market.SupplyContract> contracts;
        private final Map<String, Empire> empires = new LinkedHashMap<>();
        private final Map<String, Corporation> corporations = new LinkedHashMap<>();
        private final Map<String, CommercialHub> hubs = new LinkedHashMap<>();
        private final Map<String, MarketAccount> marketAccounts = new LinkedHashMap<>();
        private final Map<String, GeologicalDeposit> deposits = new LinkedHashMap<>();
        private final IndustryOperatingLedger cash;
        private final Map<String, Double> imperialReceipts = new HashMap<>();
        private final Map<String, Double> imperialExpenses = new HashMap<>();
        private final Map<String, Double> productionEnergy = new HashMap<>();
        private final Map<String, Double> productionCharges = new HashMap<>();
        private final Map<String, Double> productionPlantSales = new HashMap<>();

        private void payProductionGenerators() {
            for (var charge : productionCharges.entrySet()) {
                var generators = state.industrialFacilities().stream().filter(f -> f.planetId().equals(charge.getKey()))
                        .filter(f -> PowerPlantCatalog.find(f.applicationId()) != null).toList();
                double generated = state.industryAccounts().stream().filter(a -> generators.stream().anyMatch(f -> f.id().equals(a.facilityId())))
                        .mapToDouble(IndustryAccount::generatedKwh).sum();
                if (generated <= 0) continue;
                for (var generator : generators) {
                    double energy = state.industryAccounts().stream().filter(a -> a.facilityId().equals(generator.id()))
                            .mapToDouble(IndustryAccount::generatedKwh).sum();
                    double payment = charge.getValue() * energy / generated;
                    changeOwnerBalance(generator, payment);
                    productionPlantSales.merge(generator.id(), payment, Double::sum);
                }
            }
        }

        private Ledger(GameState state) {
            this.state = state;
            contracts = new ArrayList<>(state.businessLedger().contracts());
            cash = new IndustryOperatingLedger(state.industryAccounts());
            for (Empire item : state.empires()) empires.put(item.id(), item);
            for (Corporation item : state.corporations()) corporations.put(item.id(), item);
            for (CommercialHub item : state.commercialHubs()) hubs.put(item.id(), item);
            for (MarketAccount item : state.marketAccounts()) marketAccounts.put(item.hubId(), item);
            for (GeologicalDeposit item : state.geologicalDeposits()) deposits.put(item.id(), item);
        }

        private Empire technologyOwner(IndustrialFacility facility) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())
                    || IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())) {
                return empires.get(facility.ownerEntityId());
            }
            Corporation owner = corporations.get(facility.ownerEntityId());
            return owner == null ? null : empires.get(owner.empireId());
        }

        private CommercialHub hubOn(String bodyId) {
            return hubs.values().stream().filter(hub -> bodyId.equals(hub.entityId())).findFirst().orElse(null);
        }


        private double ownerBalance(IndustrialFacility facility) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                return cash.balance(facility);
            }
            if (!IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) return 0.0;
            Corporation owner = corporations.get(facility.ownerEntityId());
            return owner == null ? 0.0 : owner.liquidCapitalReserves();
        }

        private void changeOwnerBalance(IndustrialFacility facility, double delta) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                cash.change(facility, delta);
                return;
            }
            if (!IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) return;
            Corporation owner = corporations.get(facility.ownerEntityId());
            if (owner == null) return;
            corporations.put(owner.id(), new Corporation(owner.id(), owner.name(), owner.empireId(),
                    owner.headquartersEntityId(), owner.marketOrientation(),
                    Math.max(0.0, owner.liquidCapitalReserves() + delta), owner.ownedFacilityIds(),
                    owner.ownedShipIds(), owner.claimedVeinIds()));
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
            hubs.put(hub.id(), hub.withInventory(state.turn(), Math.max(0.0, storedWeight), orders));
        }
    }
}
