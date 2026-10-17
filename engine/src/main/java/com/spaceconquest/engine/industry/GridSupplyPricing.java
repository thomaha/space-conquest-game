package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.market.*;
import java.util.*;

/** A uniform local tariff covering each participating supplier's share of expected funded sales. */
public final class GridSupplyPricing {
    public record Forecast(Map<String, Double> tariffs, Map<String, Double> productionEnergyKwh) {}
    private GridSupplyPricing() {}
    public static double price(GameState state, String body) {
        var local = state.toBuilder().industrialFacilities(state.industrialFacilities().stream().filter(f -> f.planetId().equals(body)).toList())
                .commercialHubs(state.commercialHubs().stream().filter(h -> h.entityId().equals(body)).toList())
                .powerGrids(state.powerGrids().stream().filter(g -> g.entityId().equals(body)).toList()).build();
        return prices(local).getOrDefault(body, PowerBillingProcessor.PRICE_PER_KWH);
    }
    /** Discarded restart quote includes the prospective funded load rather than only idle demand. */
    public static double priceWithOperatingCash(GameState state, IndustrialFacility facility, double cash) {
        return priceWithFacility(state, facility, cash);
    }
    /** Read-only prospective installation or expansion, including its conditional working cash. */
    public static double priceWithFacility(GameState state, IndustrialFacility facility, double cash) {
        var facilities = new ArrayList<>(state.industrialFacilities());
        facilities.removeIf(f -> f.id().equals(facility.id())); facilities.add(facility);
        var accounts = new ArrayList<>(state.industryAccounts());
        var account = accounts.stream().filter(a -> a.facilityId().equals(facility.id())).findFirst()
                .orElse(IndustryAccount.empty(facility.id()));
        accounts.removeIf(a -> a.facilityId().equals(facility.id()));
        accounts.add(account.withOperatingCash(Math.max(account.operatingCashCredits(), cash)));
        return price(state.toBuilder().industrialFacilities(facilities).industryAccounts(accounts).build(), facility.planetId());
    }
    public static Map<String, Double> prices(GameState state) {
        return forecast(state).tariffs();
    }
    public static Forecast forecast(GameState state) {
        var workers = new HashMap<String, Integer>(); var wages = new HashMap<String, Double>();
        state.industrialFacilities().forEach(f -> {
            double wage = Profession.getBaseWageForProfession(f.workerProfessionId());
            int paid = Math.min(f.allocatedWorkers(), wage <= 0 ? f.allocatedWorkers() : (int) Math.min(Integer.MAX_VALUE, FacilitySupplyPricing.cash(state, f) / wage));
            workers.put(f.id(), paid); wages.put(f.id(), paid * wage);
        });
        var power = new PowerGenerationProcessor().process(state, workers, wages);
        var balance = new PowerProcessor().balanceDay(state, power.generationKw(), workers);
        var energy = new HashMap<>(balance.productionEnergyKwh());
        state.industrialFacilities().stream().filter(f -> ProductionWork.definition(f.applicationId()) == null).forEach(f ->
                energy.put(f.id(), PowerProcessor.requestedIndustryKw(f, balance.poweredWorkers().getOrDefault(f.id(), 0)) * 24));
        return new Forecast(tariffs(state, power.accounts(), balance), Map.copyOf(energy));
    }
    public static Map<String, Double> tariffs(GameState state, List<IndustryAccount> generators, PowerProcessor.DayResult balance) {
        var result = new HashMap<String, Double>();
        var facilities = new HashMap<String, IndustrialFacility>();
        state.industrialFacilities().forEach(f -> facilities.put(f.id(), f));
        for (var grid : balance.grids()) {
            var participants = generators.stream().filter(a -> facilities.containsKey(a.facilityId())
                    && facilities.get(a.facilityId()).planetId().equals(grid.entityId())
                    && PowerPlantCatalog.find(facilities.get(a.facilityId()).applicationId()) != null).toList();
            double generated = participants.stream().mapToDouble(IndustryAccount::generatedKwh).sum();
            double sold = Math.min(generated, grid.totalDemandKw() * 24);
            double price = PowerBillingProcessor.PRICE_PER_KWH;
            if (sold <= 0 || generated <= 0) continue;
            var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(grid.entityId())).findFirst().orElse(null);
            if (hub == null) continue;
            var costsByOwner = new HashMap<String, Double>();
            var reservesByOwner = new HashMap<String, Double>();
            var generationByOwner = new HashMap<String, Double>();
            for (var account : participants) {
                var f = facilities.get(account.facilityId());
                if (IndustrialFacility.HIVE_GRID.equals(f.ownershipType())) continue;
                double upkeep = FacilitySupplyPricing.declaredMaintenance(f);
                double cost = account.inputCostsCredits() + account.wageCostsCredits() + upkeep;
                double capital = FacilitySupplyPricing.capital(state, hub, f) / SupplyPricingPolicy.current().capitalRecoveryDays();
                costsByOwner.merge(f.ownerEntityId(), cost, Double::sum);
                reservesByOwner.merge(f.ownerEntityId(), capital, Double::sum);
                generationByOwner.merge(f.ownerEntityId(), account.generatedKwh(), Double::sum);
            }
            for (var owner : costsByOwner.keySet()) {
                double receipts = SupplyPricingPolicy.current().requiredReceipts(costsByOwner.get(owner), reservesByOwner.get(owner),
                        SupplyPricingPolicy.taxRate(state, owner));
                double required = receipts / (sold * generationByOwner.get(owner) / generated);
                if (Double.isFinite(required)) price = Math.max(price, required);
            }
            result.put(grid.entityId(), price);
        }
        return Map.copyOf(result);
    }
}
