package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Immutable, discarded preview of an owned body's feasible and fully affordable generation project. */
public record PowerInvestmentQuote(String empireId, String bodyId, String applicationId,
                                   String facilityId, int targetTier, double additionalKw, double score) {
    public static boolean controls(GameState state, Empire empire, String body) {
        return populations(state, empire, body) != null;
    }
    private static List<Population> populations(GameState state, Empire empire, String body) {
        for (var system : state.solarSystems()) if (empire.controlledSystemIds().contains(system.id()))
            for (var planet : system.planets()) {
                if (body.equals(planet.id())) return planet.populations();
                for (var moon : planet.moons()) if (body.equals(moon.id())) return moon.populations();
            }
        return null;
    }
    public static double shortage(GameState state, String body) {
        return state.powerGrids().stream().filter(grid -> body.equals(grid.entityId()))
                .mapToDouble(grid -> Math.max(Math.max(0, -grid.netBalanceKw()), grid.isDeficitBrownoutActive()
                        ? Math.max(0, requestedKw(state, body, grid.totalDemandKw()) - grid.totalGenerationKw() - grid.currentStoredKwh() / 24) : 0))
                .max().orElse(0);
    }
    private static double requestedKw(GameState state, String body, double recordedDemand) {
        var accounts = new HashMap<String, Integer>();
        state.industryAccounts().forEach(account -> accounts.put(account.facilityId(), account.paidWorkers()));
        double industry = state.industrialFacilities().stream().filter(facility -> body.equals(facility.planetId()))
                .mapToDouble(facility -> ProductionWork.definition(facility.applicationId()) == null
                        ? PowerProcessor.requestedIndustryKw(facility, accounts.getOrDefault(facility.id(), 0))
                        : ProductionWork.requestedKw(state, facility, accounts.getOrDefault(facility.id(), 0))).sum();
        double households = state.empires().stream().mapToDouble(empire -> {
            var people = populations(state, empire, body);
            return people == null ? 0 : com.spaceconquest.engine.industry.PowerBillingProcessor.householdDemandKwh(empire,
                    people.stream().mapToLong(Population::totalCount).sum()) / 24;
        }).sum();
        double launches = state.launchActivities().stream().filter(activity -> body.equals(activity.bodyId())).mapToDouble(activity -> activity.powerKwh() / 24).sum();
        return Math.max(recordedDemand, industry + households + launches);
    }
    public static boolean pending(GameState state, String body) {
        return state.industrialFacilities().stream().anyMatch(facility -> body.equals(facility.planetId())
                && PowerPlantCatalog.find(facility.applicationId()) != null
                && (facility.isUndergoingExpansion() || state.industryAccounts().stream()
                .anyMatch(account -> facility.id().equals(account.facilityId()) && account.subsidyCredits() > 0
                        && (account.paidWorkers() == 0 || account.generatedKwh() <= 0))
                || state.expansionProjects().stream()
                .anyMatch(project -> facility.id().equals(project.facilityId()))));
    }
    public static List<PowerInvestmentQuote> candidates(GameState state, Empire empire, String body) {
        if (!controls(state, empire, body) || shortage(state, body) <= 0 || pending(state, body)) return List.of();
        var quotes = new ArrayList<PowerInvestmentQuote>();
        for (var app : PowerPlantCatalog.all().keySet().stream().sorted().toList()) {
            var existing = state.industrialFacilities().stream().filter(facility -> body.equals(facility.planetId())
                    && empire.id().equals(facility.ownerEntityId()) && app.equals(facility.applicationId())
                    && IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType()) && facility.tier() > 0)
                    .sorted(Comparator.comparingInt(IndustrialFacility::tier).thenComparing(IndustrialFacility::id)).toList();
            for (var facility : existing) {
                var quote = evaluate(state, empire, body, app, facility.id());
                if (quote != null) quotes.add(quote);
            }
            var quote = evaluate(state, empire, body, app, null);
            if (quote != null) quotes.add(quote);
        }
        return List.copyOf(quotes);
    }
    public static PowerInvestmentQuote evaluate(GameState state, Empire empire, String body, String app, String facilityId) {
        var plant = PowerPlantCatalog.find(app);
        if (plant == null || (empire.societyStructure() != null && empire.societyStructure().toLowerCase(Locale.ROOT).contains("hive"))
                || !controls(state, empire, body) || shortage(state, body) <= 0 || pending(state, body)
                || !empire.unlockedTechIds().contains("electricity") || !empire.unlockedTechIds().contains(plant.requiredTechnology())
                || !ApplicationProduction.isAvailable(empire, app)) return null;
        var existing = facilityId == null ? null : state.industrialFacilities().stream()
                .filter(facility -> facilityId.equals(facility.id()) && empire.id().equals(facility.ownerEntityId())
                        && body.equals(facility.planetId()) && app.equals(facility.applicationId())
                        && IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType()) && facility.tier() > 0).findFirst().orElse(null);
        if (facilityId != null && existing == null) return null;
        var account = existing == null ? null : state.industryAccounts().stream()
                .filter(value -> existing.id().equals(value.facilityId())).findFirst().orElse(null);
        int units = existing == null ? 1 : GenerationCapacity.units(existing);
        if (existing != null && (account == null || account.paidWorkers() < GenerationCapacity.workers(existing, plant)
                || existing.allocatedWorkers() < GenerationCapacity.workers(existing, plant) || account.operatingCashCredits() <= 0)) return null;
        if (existing == null && availableWorkers(state, empire, body) < plant.requiredWorkers()) return null;
        int oldTier = existing == null ? 0 : existing.tier();
        int tier = Math.max(oldTier + 1, FacilityManufacturingCapacity.minimumTier(state, empire.id(), app));
        var preview = new IndustrialFacility("preview", body, app, empire.id(), IndustrialFacility.PUBLIC_STATE,
                tier, plant.requiredWorkers(), plant.workerProfessionId(), false, 0);
        double environment = PowerGenerationProcessor.environmentFactor(state.solarSystems(), preview);
        double multiplier = ApplicationProduction.modifiersForOwner(state, empire.id(), app).effectMultiplier();
        double prior = existing != null && FacilityManufacturingCapacity.canOperate(state, existing) ? oldTier : 0;
        double extra = plant.kwPerTier() * (tier - prior) * environment * multiplier * units;
        if (!Double.isFinite(extra) || extra <= 0) return null;
        var bill = existing == null ? ConstructionMaterialCatalog.facility(state, empire.id(), app, tier - oldTier)
                : ConstructionMaterialCatalog.facility(state, existing, tier - oldTier);
        var purchase = ConstructionMaterials.buyUpTo(state, body, empire.id(), bill);
        double capital = existing == null ? plant.operatingCapitalCredits() : 0;
        double remaining = purchase.state().empires().stream().filter(value -> empire.id().equals(value.id()))
                .mapToDouble(Empire::treasuryCredits).findFirst().orElse(0);
        if (!purchase.covers(bill) || remaining < capital) return null;
        double fuelCost = 0;
        if (plant.fuelMaterialId() != null) {
            var fuel = state.commercialHubs().stream().filter(hub -> body.equals(hub.entityId()))
                    .map(hub -> hub.activeOrders().get(plant.fuelMaterialId())).filter(Objects::nonNull).findFirst().orElse(null);
            double needed = plant.fuelKgPerTierDay() * tier * units;
            if (fuel == null || fuel.supplyKg() < needed) return null;
            fuelCost = needed * Math.max(0, fuel.pricePerKg());
        }
        double wages = (long) plant.requiredWorkers() * units * Profession.getBaseWageForProfession(plant.workerProfessionId());
        if (fuelCost + wages > (existing == null ? capital : account.operatingCashCredits())) return null;
        double cost = empire.treasuryCredits() - remaining + capital + fuelCost;
        return new PowerInvestmentQuote(empire.id(), body, app, facilityId, tier, extra,
                Math.min(extra, shortage(state, body)) / Math.max(1, cost));
    }
    private static long availableWorkers(GameState state, Empire empire, String body) {
        long population = populations(state, empire, body).stream().flatMap(value -> value.ageGroups().entrySet().stream())
                .filter(age -> age.getKey() >= 18 && age.getKey() < 65).mapToLong(Map.Entry::getValue).sum();
        long allocated = state.industrialFacilities().stream().filter(facility -> body.equals(facility.planetId()))
                .mapToLong(IndustrialFacility::allocatedWorkers).sum();
        return Math.max(0, population - allocated);
    }
}
