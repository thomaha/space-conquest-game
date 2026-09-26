package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.economy.HouseholdAccount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settles local electricity service before material production and credits generating plants. */
public class PowerBillingProcessor {
    public static final double PRICE_PER_KWH = 0.02;
    private static final double HOURS_PER_DAY = 24.0;
    private static final double HOUSEHOLD_KW_PER_PERSON = 0.0000005;

    public record Result(List<Empire> empires, List<Corporation> corporations,
                         List<HouseholdAccount> households, List<PowerGridState> grids,
                         Map<String, Integer> poweredWorkers, Map<String, Double> facilityPowerCosts,
                         Map<String, Double> plantSales, Map<String, Double> imperialReceipts,
                         Map<String, Double> imperialExpenses) {}

    public static double householdDemandKwh(Empire empire, long headcount) {
        if (empire == null || empire.unlockedTechIds() == null
                || (empire.societyStructure() != null
                        && empire.societyStructure().toLowerCase().contains("hive"))
                || !empire.unlockedTechIds().contains("electricity")
                || !empire.unlockedTechIds().contains("industrial_production")) return 0.0;
        return Math.max(0L, headcount) * HOUSEHOLD_KW_PER_PERSON * HOURS_PER_DAY;
    }

    public Result process(GameState state, PowerProcessor.DayResult balance,
                          List<IndustryAccount> generators, Map<String, Integer> paidWorkers) {
        Ledger ledger = new Ledger(state);
        Map<String, Double> physical = new HashMap<>();
        Map<String, Double> billable = new HashMap<>();
        Map<String, Double> consumed = new HashMap<>();
        Map<String, Double> chargesByBody = new HashMap<>();
        Map<String, Double> costs = new HashMap<>();
        Map<String, Integer> powered = new HashMap<>(balance.poweredWorkers());
        for (PowerGridState grid : balance.grids()) {
            double stored = state.powerGrids().stream()
                    .filter(prior -> prior.entityId().equals(grid.entityId()))
                    .mapToDouble(PowerGridState::currentStoredKwh).findFirst().orElse(0.0);
            double available = Math.max(0.0, grid.totalGenerationKw() * HOURS_PER_DAY + stored);
            physical.put(grid.entityId(), Math.min(grid.totalDemandKw() * HOURS_PER_DAY, available));
            billable.put(grid.entityId(), Math.max(0.0, grid.totalGenerationKw() * HOURS_PER_DAY));
        }
        List<HouseholdAccount> households = new ArrayList<>();
        for (HouseholdAccount household : state.householdAccounts()) {
            String body = household.bodyId();
            double desired = household.unmetBasicElectricityKwh();
            double delivered = Math.min(desired, physical.getOrDefault(body, 0.0));
            double priced = Math.min(delivered, billable.getOrDefault(body, 0.0));
            double purchased = Math.min(priced, household.savingsCredits() / PRICE_PER_KWH);
            double received = delivered - priced + purchased;
            double payment = purchased * PRICE_PER_KWH;
            households.add(household.withElectricitySettlement(received, payment));
            physical.merge(body, -received, Double::sum);
            billable.merge(body, -purchased, Double::sum);
            consumed.merge(body, received, Double::sum);
            chargesByBody.merge(body, payment, Double::sum);
        }
        for (IndustrialFacility facility : state.industrialFacilities()) {
            billFacility(facility, paidWorkers, powered, billable, consumed,
                    chargesByBody, costs, ledger);
        }
        Map<String, Double> sales = payGenerators(state, generators, chargesByBody, ledger);
        List<PowerGridState> grids = settledGrids(state, balance.grids(), consumed);
        return new Result(List.copyOf(ledger.empires.values()), List.copyOf(ledger.corporations.values()),
                List.copyOf(households), grids, Map.copyOf(powered), Map.copyOf(costs), Map.copyOf(sales),
                Map.copyOf(ledger.receipts), Map.copyOf(ledger.expenses));
    }

    private void billFacility(IndustrialFacility facility, Map<String, Integer> paidWorkers,
                              Map<String, Integer> powered, Map<String, Double> billable,
                              Map<String, Double> consumed, Map<String, Double> chargesByBody,
                              Map<String, Double> costs, Ledger ledger) {
        IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(facility.applicationId());
        if (recipe == null) return;
        int workers = powered.getOrDefault(facility.id(), 0);
        int paid = paidWorkers.getOrDefault(facility.id(), 0);
        Empire technologyOwner = ledger.technologyOwner(facility);
        if (!IndustryRecipeCatalog.isUnlocked(recipe, technologyOwner)
                || facility.tier() < recipe.minTier() || workers <= 0 || paid <= 0) {
            powered.put(facility.id(), 0);
            return;
        }
        String body = facility.planetId();
        double fullKwh = PowerProcessor.requestedIndustryKw(facility, paid)
                * HOURS_PER_DAY * workers / paid;
        if (fullKwh <= 0.0) return;
        double pricedKwh = Math.min(fullKwh, billable.getOrDefault(body, 0.0));
        double pricePerWorker = pricedKwh * PRICE_PER_KWH / workers;
        int affordable = pricePerWorker <= 0.0 ? workers : Math.min(workers,
                (int) Math.floor((ledger.ownerBalance(facility) + 0.0000001) / pricePerWorker));
        double payment = affordable * pricePerWorker;
        powered.put(facility.id(), affordable);
        ledger.changeOwnerBalance(facility, -payment);
        billable.merge(body, -payment / PRICE_PER_KWH, Double::sum);
        consumed.merge(body, fullKwh * affordable / workers, Double::sum);
        chargesByBody.merge(body, payment, Double::sum);
        costs.put(facility.id(), payment);
    }

    private Map<String, Double> payGenerators(GameState state, List<IndustryAccount> generators,
                                              Map<String, Double> chargesByBody, Ledger ledger) {
        Map<String, IndustrialFacility> facilities = new HashMap<>();
        for (IndustrialFacility facility : state.industrialFacilities()) facilities.put(facility.id(), facility);
        Map<String, Double> totalByBody = new HashMap<>();
        for (IndustryAccount account : generators) {
            IndustrialFacility facility = facilities.get(account.facilityId());
            if (facility != null && account.generatedKwh() > 0.0) {
                totalByBody.merge(facility.planetId(), account.generatedKwh(), Double::sum);
            }
        }
        Map<String, Double> sales = new HashMap<>();
        for (IndustryAccount account : generators) {
            IndustrialFacility facility = facilities.get(account.facilityId());
            if (facility == null || account.generatedKwh() <= 0.0) continue;
            String body = facility.planetId();
            double total = totalByBody.getOrDefault(body, 0.0);
            if (total <= 0.0) continue;
            double revenue = chargesByBody.getOrDefault(body, 0.0) * account.generatedKwh() / total;
            ledger.changeOwnerBalance(facility, revenue);
            sales.put(facility.id(), revenue);
        }
        return sales;
    }

    private List<PowerGridState> settledGrids(GameState state, List<PowerGridState> requested,
                                              Map<String, Double> consumed) {
        PowerProcessor processor = new PowerProcessor();
        List<PowerGridState> grids = new ArrayList<>();
        for (PowerGridState grid : requested) {
            PowerGridState prior = state.powerGrids().stream()
                    .filter(item -> item.entityId().equals(grid.entityId())).findFirst().orElse(null);
            PowerGridState settled = processor.balanceGrid(grid.entityId(), grid.totalGenerationKw(),
                    consumed.getOrDefault(grid.entityId(), 0.0) / HOURS_PER_DAY,
                    prior == null ? 0.0 : prior.currentStoredKwh(), grid.batteryCapacityKwh());
            grids.add(new PowerGridState(settled.entityId(), settled.totalGenerationKw(),
                    settled.totalDemandKw(), settled.netBalanceKw(), settled.batteryCapacityKwh(),
                    settled.currentStoredKwh(), grid.isDeficitBrownoutActive()));
        }
        return List.copyOf(grids);
    }

    private static final class Ledger {
        private final Map<String, Empire> empires = new LinkedHashMap<>();
        private final Map<String, Corporation> corporations = new LinkedHashMap<>();
        private final Map<String, Double> receipts = new HashMap<>();
        private final Map<String, Double> expenses = new HashMap<>();

        private Ledger(GameState state) {
            for (Empire empire : state.empires()) empires.put(empire.id(), empire);
            for (Corporation corporation : state.corporations()) corporations.put(corporation.id(), corporation);
        }

        private Empire technologyOwner(IndustrialFacility facility) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                return empires.get(facility.ownerEntityId());
            }
            Corporation owner = corporations.get(facility.ownerEntityId());
            return owner == null ? null : empires.get(owner.empireId());
        }

        private double ownerBalance(IndustrialFacility facility) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                Empire owner = empires.get(facility.ownerEntityId());
                return owner == null ? 0.0 : Math.max(0.0, owner.treasuryCredits());
            }
            Corporation owner = corporations.get(facility.ownerEntityId());
            return owner == null ? 0.0 : Math.max(0.0, owner.liquidCapitalReserves());
        }

        private void changeOwnerBalance(IndustrialFacility facility, double delta) {
            if (delta == 0.0) return;
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                Empire owner = empires.get(facility.ownerEntityId());
                if (owner == null) return;
                if (delta > 0.0) receipts.merge(owner.id(), delta, Double::sum);
                else expenses.merge(owner.id(), -delta, Double::sum);
                empires.put(owner.id(), new Empire(owner.id(), owner.name(), owner.raceId(),
                        owner.societyStructure(), Math.max(0.0, owner.treasuryCredits() + delta),
                        owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(),
                        owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds()));
            } else if (IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) {
                Corporation owner = corporations.get(facility.ownerEntityId());
                if (owner == null) return;
                corporations.put(owner.id(), new Corporation(owner.id(), owner.name(), owner.empireId(),
                        owner.headquartersEntityId(), owner.marketOrientation(),
                        Math.max(0.0, owner.liquidCapitalReserves() + delta), owner.ownedFacilityIds(),
                        owner.ownedShipIds(), owner.claimedVeinIds()));
            }
        }
    }
}
