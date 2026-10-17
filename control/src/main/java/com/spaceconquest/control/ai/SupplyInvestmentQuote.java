package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.InfrastructureService;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Discarded construction and operating budget. Forecasts never buy or reserve actual goods. */
public record SupplyInvestmentQuote(BuildFacilityCommand command, double operatingCapitalCredits) {
    public record Assessment(SupplyInvestmentQuote quote, String reason) {}
    private static Assessment blocked(String reason) { return new Assessment(null, reason); }
    public static SupplyInvestmentQuote evaluate(GameState state, Empire empire, String body, String application) {
        return assess(state, empire, body, application).quote();
    }
    public static Assessment assess(GameState state, Empire empire, String body, String application) {
        if (!PowerInvestmentQuote.controls(state, empire, body)) return blocked("Site is not controlled");
        var recipe = IndustryRecipeCatalog.find(application); var plant = PowerPlantCatalog.find(application);
        var service = InfrastructureService.find(application);
        if (recipe == null && plant == null && service == null) return blocked("Missing application capability");
        if (plant == null && SupplyChainNeeds.installed(state, empire, body, application)) return blocked("Existing or pending supplier");
        if (plant != null && PowerInvestmentQuote.pending(state, body)) return blocked("Generation is pending");
        var policy = OperatingReservePolicy.forApplication(application);
        if (policy == null) return blocked("Missing operating reserve definition");
        int tier = FacilityManufacturingCapacity.minimumTier(state, empire.id(), application);
        var equipment = ProductionWork.initialEquipment(application);
        int workers = plant != null ? plant.requiredWorkers() : equipment.autonomousFraction() == 1 ? 0 : Math.toIntExact(equipment.referenceCrew());
        String profession = plant != null ? plant.workerProfessionId() : equipment.operatorProfession();
        var command = new BuildFacilityCommand(body, application, empire.id(), IndustrialFacility.PUBLIC_STATE, workers, profession);
        if (!command.validate(state)) return blocked("Research or installation requirements are not met");
        var preview = new IndustrialFacility("supply-preview", body, application, empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, profession, false, 0);
        if (workers > availableWorkers(state, empire, body)) return blocked("Insufficient uncommitted workforce");
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(body)).findFirst().orElse(null);
        if (hub == null) return blocked("No local market");
        var inputs = recipe != null ? recipe.inputsKg() : plant != null && plant.fuelMaterialId() != null
                ? Map.of(plant.fuelMaterialId(), plant.fuelKgPerTierDay() * tier) : service != null && service.capability().driveId() != null
                ? com.spaceconquest.engine.ship.PropulsionCatalog.drive(service.capability().driveId()).propellantMaterials(service.capability().fuelStockKgPerTier()) : Map.<String, Double>of();
        if (inputs.entrySet().stream().anyMatch(e -> SupplyChainNeeds.stock(hub, e.getKey()) < e.getValue()
                && !fundedSupplier(state, empire, body, e.getKey()))) return blocked("Missing feedstock or funded upstream supplier");
        double load = load(state, body, application, tier);
        if (plant == null && load > freePower(state, body) + 1e-6) return blocked("Insufficient uncommitted generation");
        if (plant != null && PowerGenerationProcessor.environmentFactor(state.solarSystems(), preview) <= 0) return blocked("Generation environment is unavailable");
        double inputCost = inputs.entrySet().stream().mapToDouble(e -> e.getValue() * price(hub, e.getKey())).sum();
        if (recipe != null) inputCost *= load * 24 / ProductionWork.definition(state, preview).energyKwhPerBatch();
        double daily = workers * Profession.getBaseWageForProfession(profession) + inputCost
                + load * 24 * prospectiveTariff(state, preview, hub, inputs, policy.maximumCashCredits()) + policy.maintenanceCreditsPerTier() * tier;
        double reserve = Math.min(policy.maximumCashCredits(), daily * policy.targetDays());
        if (!Double.isFinite(reserve) || reserve < daily) return blocked("One operating period exceeds the reserve limit: " + daily);
        var built = command.apply(state);
        var fresh = built.industrialFacilities().stream().filter(f -> state.industrialFacilities().stream().noneMatch(old -> old.id().equals(f.id()))).findFirst().orElseThrow();
        double opening = built.industryAccounts().stream().filter(a -> a.facilityId().equals(fresh.id())).mapToDouble(IndustryAccount::operatingCashCredits).findFirst().orElse(0);
        var bill = ConstructionMaterialCatalog.facility(state, empire.id(), application, tier);
        var purchase = ConstructionMaterials.buyUpTo(built, body, empire.id(), bill);
        if (!purchase.covers(bill) || EconomicOwnership.cash(purchase.state(), empire.id())
                < Math.max(0, reserve - opening) + policy.treasuryReserveCredits()) return blocked("Construction materials or combined working capital are unavailable");
        return new Assessment(new SupplyInvestmentQuote(command, Math.max(opening, reserve)), "Ready");
    }
    private static double price(CommercialHub hub, String material) {
        var order = hub.activeOrders().get(material);
        return order == null ? com.spaceconquest.engine.market.MarketProcessor.basePricePerKg(material) : Math.max(0, order.pricePerKg());
    }
    /** Conditional delivery is confined to this discarded tariff forecast, after upstream feasibility checks. */
    private static double prospectiveTariff(GameState state, IndustrialFacility preview, CommercialHub hub,
                                            Map<String, Double> inputs, double workingCash) {
        var facilities = new ArrayList<>(state.industrialFacilities()); facilities.add(preview);
        var accounts = new ArrayList<>(state.industryAccounts()); accounts.add(IndustryAccount.empty(preview.id()).withOperatingCash(workingCash));
        var orders = new HashMap<>(hub.activeOrders());
        inputs.forEach((id, kg) -> {
            var old = orders.get(id);
            orders.put(id, new MarketOrder(id, Math.max(kg, old == null ? 0 : old.supplyKg()),
                    old == null ? 0 : old.demandKg(), price(hub, id), old == null ? 0 : old.shortcomingScore()));
        });
        var forecastHub = new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(), hub.storageCapacityKg(),
                orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), hub.logisticsRangeUnits(), orders, hub.stockTargets());
        var forecast = state.toBuilder().industrialFacilities(facilities).industryAccounts(accounts)
                .commercialHubs(state.commercialHubs().stream().map(h -> h.id().equals(hub.id()) ? forecastHub : h).toList()).build();
        return GridSupplyPricing.price(forecast, hub.entityId());
    }
    private static long availableWorkers(GameState state, Empire empire, String body) {
        long adults = state.householdAccounts().stream().filter(a -> a.empireId().equals(empire.id()) && a.bodyId().equals(body))
                .mapToLong(a -> Math.max(0, a.employment().workingAge() - a.employment().publicWorkers())).sum();
        long committed = state.industrialFacilities().stream().filter(f -> f.planetId().equals(body)).mapToLong(IndustrialFacility::allocatedWorkers).sum();
        return Math.max(0, adults - committed);
    }
    public static boolean fundedSupplier(GameState state, Empire empire, String body, String material) {
        return fundedSupplier(state, empire, body, material, new HashSet<>(), 0);
    }
    private static boolean fundedSupplier(GameState state, Empire empire, String body, String material, Set<String> path, int depth) {
        if (depth >= 8 || !path.add(material)) return false;
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(body)).findFirst().orElse(null);
        if (hub == null) return false;
        return state.industrialFacilities().stream().filter(f -> body.equals(f.planetId()) && f.tier() > 0 && !f.isUndergoingExpansion())
                .anyMatch(f -> {
                    var recipe = IndustryRecipeCatalog.find(f.applicationId());
                    return recipe != null && recipe.outputsKg().containsKey(material) && IndustryRecipeCatalog.isUnlocked(recipe, empire)
                            && (empire.id().equals(f.ownerEntityId()) || state.corporations().stream().anyMatch(c -> c.id().equals(f.ownerEntityId()) && empire.id().equals(c.empireId())))
                            && ProcessInput.maximumBatches(state, body, recipe.inputSources(), state.geologicalDeposits()) > 0
                            && com.spaceconquest.engine.market.FacilitySupplyPricing.cash(state, f) >= f.allocatedWorkers()
                            * Profession.getBaseWageForProfession(f.workerProfessionId()) + com.spaceconquest.engine.market.FacilitySupplyPricing.declaredMaintenance(f)
                            && recipe.inputsKg().entrySet().stream().allMatch(e -> SupplyChainNeeds.stock(hub, e.getKey()) >= e.getValue()
                            || fundedSupplier(state, empire, body, e.getKey(), new HashSet<>(path), depth + 1));
                });
    }
    public static double load(GameState state, String body, String application, int tier) {
        var equipment = ProductionWork.initialEquipment(application);
        if (equipment == null) return 0;
        String owner = state.empires().stream().filter(e -> PowerInvestmentQuote.controls(state, e, body))
                .map(Empire::id).findFirst().orElse("");
        return load(state, new IndustrialFacility("load-preview", body, application, owner, IndustrialFacility.PUBLIC_STATE,
                tier, Math.toIntExact(equipment.referenceCrew()), equipment.operatorProfession(), false, 0));
    }
    public static double load(GameState state, IndustrialFacility facility) {
        String application = facility.applicationId(); int tier = facility.tier();
        var service = InfrastructureService.find(application);
        if (service != null) return (service.capability().powerKwPerTier() * tier
                + service.capability().powerKwPerKg() * service.capability().capacityKgPerTier() * tier) / 24;
        if (ProductionWork.definition(application) == null) return 0;
        var work = ProductionWork.quote(state, facility, facility.allocatedWorkers());
        return Math.max(work.energyKwh() / 24, ProductionWork.nominalKw(facility, facility.allocatedWorkers()));
    }
    public static double freePower(GameState state, String body) {
        var paidWorkers = new HashMap<String, Integer>();
        state.industryAccounts().forEach(a -> paidWorkers.put(a.facilityId(), a.paidWorkers()));
        // Discarded rated probe still checks paid crew, research, fuel stock and fuel cash.
        double rated = new PowerGenerationProcessor().process(state, paidWorkers, Map.of(), false).generationKw().getOrDefault(body, 0.0);
        double committed = state.industrialFacilities().stream().filter(f -> body.equals(f.planetId()))
                .mapToDouble(f -> load(state, new IndustrialFacility(f.id(), body, f.applicationId(), f.ownerEntityId(), f.ownershipType(),
                        Math.max(f.tier(), state.expansionProjects().stream().filter(p -> p.facilityId().equals(f.id()))
                                .mapToInt(FacilityExpansionProject::targetTier).max().orElse(f.tier())),
                        f.allocatedWorkers(), f.workerProfessionId(), false, 0, f.productionEquipment()))).sum();
        var grid = state.powerGrids().stream().filter(g -> body.equals(g.entityId())).findFirst().orElse(null);
        return Math.max(0, rated - Math.max(committed, grid == null ? 0 : grid.totalDemandKw()));
    }
    public static String generation(GameState state, Empire empire, String body) {
        return PowerPlantCatalog.all().entrySet().stream().filter(e -> {
            var plant = e.getValue();
            var preview = new IndustrialFacility("power-preview", body, e.getKey(), empire.id(), IndustrialFacility.PUBLIC_STATE,
                    1, plant.requiredWorkers(), plant.workerProfessionId(), false, 0);
            return plant.fuelMaterialId() == null && PowerGenerationProcessor.environmentFactor(state.solarSystems(), preview) > 0;
        }).sorted(Comparator.comparingDouble((Map.Entry<String, PowerPlantCatalog.Plant> e) -> {
                    var p = e.getValue();
                    var preview = new IndustrialFacility("power-preview", body, e.getKey(), empire.id(), IndustrialFacility.PUBLIC_STATE,
                            1, p.requiredWorkers(), p.workerProfessionId(), false, 0);
                    return p.constructionMaterialsKg().values().stream().mapToDouble(Double::doubleValue).sum()
                            / (p.kwPerTier() * PowerGenerationProcessor.environmentFactor(state.solarSystems(), preview));
                }).thenComparing(e -> !ApplicationProduction.isAvailable(empire, e.getKey()))
                .thenComparing(Map.Entry::getKey)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
}
