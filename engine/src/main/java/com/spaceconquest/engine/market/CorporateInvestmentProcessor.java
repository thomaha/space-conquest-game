package com.spaceconquest.engine.market;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.FacilityManufacturingCapacity;
import com.spaceconquest.engine.industry.IndustryRecipeCatalog;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.ApplicationProduction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Converts funded corporate investments into physical game-state assets. */
public class CorporateInvestmentProcessor {
    public static final double INFRASTRUCTURE_COST = 8000.0;
    public static final double SHIP_PROCUREMENT_COST = 12000.0;
    public static final double SHORTCOMING_INFRASTRUCTURE_THRESHOLD = 0.50;
    public static final double SHORTCOMING_FLEET_THRESHOLD = 0.40;

    public GameState processCorporateInvestments(GameState state, Map<String, Double> trustPenalties) {
        GameState current = state;
        for (Corporation corporation : state.corporations()) {
            if (trustPenalties != null && trustPenalties.getOrDefault(corporation.empireId(), 0.0) < -20.0) continue;
            if ("TRANSPORT".equalsIgnoreCase(corporation.marketOrientation())) continue;
            GameState snapshot = current;
            CommercialHub target = current.commercialHubs().stream()
                    .filter(hub -> controlsBody(snapshot, corporation, hub.entityId()))
                    .filter(hub -> bestInvestmentOrder(snapshot, corporation, hub, true) != null)
                    .max(Comparator.comparingDouble(hub ->
                            opportunityScore(snapshot, corporation, hub, bestInvestmentOrder(snapshot, corporation, hub, true))))
                    .orElse(null);
            if (target == null) continue;
            MarketOrder order = bestInvestmentOrder(current, corporation, target, true);
            if (order.shortcomingScore() >= SHORTCOMING_INFRASTRUCTURE_THRESHOLD
                    && opportunityScore(current, corporation, target, order) > 0.0) {
                current = invest(current, corporation.id(), target.entityId(), "INFRASTRUCTURE",
                        infrastructureCost(applicationForBody(current, corporation, target.entityId(), order.resourceId())));
            }
        }
        return current;
    }

    public boolean canInvest(GameState state, String corporationId, String bodyId, String type, double credits) {
        if (state == null || corporationId == null || bodyId == null || type == null || !Double.isFinite(credits)) return false;
        Corporation corporation = state.corporations().stream()
                .filter(item -> corporationId.equals(item.id())).findFirst().orElse(null);
        if (corporation == null || !controlsBody(state, corporation, bodyId)) return false;
        CommercialHub hub = state.commercialHubs().stream()
                .filter(item -> bodyId.equals(item.entityId())).findFirst().orElse(null);
        if (hub == null) return false;
        Empire empire = state.empires().stream().filter(item -> corporation.empireId().equals(item.id()))
                .findFirst().orElse(null);
        if ("INFRASTRUCTURE".equalsIgnoreCase(type)) {
            MarketOrder order = bestInvestmentOrder(state, corporation, hub);
            String application = order == null ? null
                    : applicationForBody(state, corporation, bodyId, order.resourceId());
            if (application == null) return false;
            boolean alreadyBuilding = state.expansionProjects().stream().anyMatch(project ->
                    state.industrialFacilities().stream().anyMatch(facility ->
                            project.facilityId().equals(facility.id())
                                    && corporationId.equals(facility.ownerEntityId())
                                    && bodyId.equals(facility.planetId())
                                    && application.equals(facility.applicationId())));
            if (alreadyBuilding) return false;
            IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(application);
            return credits == infrastructureCost(application) && corporation.liquidCapitalReserves() >= credits
                    && IndustryRecipeCatalog.isUnlocked(recipe, empire)
                    && (recipe.infrastructure() == null || recipe.infrastructure().permitsSite(state, bodyId))
                    && cultivationSite(state, corporation, bodyId, recipe)
                    && productionPowerAvailable(state, corporation, bodyId, recipe)
                    && com.spaceconquest.engine.industry.ProcessInput.maximumBatches(state, bodyId,
                    recipe.inputSources(), state.geologicalDeposits()) > 0;
        }
        if ("FLEET".equalsIgnoreCase(type) || "SHIP".equalsIgnoreCase(type)) {
            if (empire == null || credits != SHIP_PROCUREMENT_COST || corporation.liquidCapitalReserves() < credits
                    || !empire.unlockedTechIds().contains("rocketry") || !empire.unlockedTechIds().contains("computers")
                    || state.shipConstructionOrders().stream().anyMatch(order -> corporationId.equals(order.ownerEntityId()))) return false;
            ShipDesign existingDesign = ownedFleetDesign(state, corporation, bodyId);
            return existingDesign != null;
        }
        return false;
    }

    public GameState invest(GameState state, String corporationId, String bodyId, String type, double credits) {
        if (!canInvest(state, corporationId, bodyId, type, credits)) return state;
        Corporation corporation = state.corporations().stream()
                .filter(item -> corporationId.equals(item.id())).findFirst().orElseThrow();
        List<String> facilityIds = new ArrayList<>(corporation.ownedFacilityIds());
        List<String> shipIds = new ArrayList<>(corporation.ownedShipIds());
        GameState.Builder builder = state.toBuilder();
        if ("INFRASTRUCTURE".equalsIgnoreCase(type)) {
            CommercialHub hub = state.commercialHubs().stream()
                    .filter(item -> bodyId.equals(item.entityId())).findFirst().orElseThrow();
            String application = applicationForBody(state, corporation, bodyId,
                    bestInvestmentOrder(state, corporation, hub).resourceId());
            IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(application);
            var existing = existingFactory(state, corporationId, bodyId, application);
            var equipment = com.spaceconquest.engine.industry.ProductionWork.initialEquipment(application);
            String id = existing == null ? "facility_corp_" + UUID.randomUUID() : existing.id();
            int targetTier = Math.max(recipe.minTier(), Math.max(existing == null ? 1 : existing.tier() + 1,
                    FacilityManufacturingCapacity.minimumTier(state, corporationId, application)));
            String profession = equipment != null ? equipment.operatorProfession() : "industrial_worker";
            List<IndustrialFacility> facilities = new ArrayList<>(state.industrialFacilities());
            facilities.removeIf(facility -> id.equals(facility.id()));
            facilities.add(new IndustrialFacility(id, bodyId, application, corporationId,
                    IndustrialFacility.PRIVATE_CORPORATE, existing == null ? 0 : existing.tier(),
                    existing == null ? (equipment == null ? Math.max(1, recipe.workersPerBatch()) : Math.toIntExact(equipment.referenceCrew())) : existing.allocatedWorkers(),
                    existing == null ? profession : existing.workerProfessionId(), true, 0.0,
                    existing == null ? null : existing.productionEquipment()));
            builder.industrialFacilities(facilities);
            List<FacilityExpansionProject> projects = new ArrayList<>(state.expansionProjects());
            projects.add(new FacilityExpansionProject("project_corp_" + UUID.randomUUID(), id,
                    targetTier, 0.0, ApplicationProduction.constructionWorkHours(
                    state, corporationId, application, ConstructionMaterialCatalog.facilityWork(application,
                            existing == null ? targetTier : targetTier - existing.tier(), existing != null)
                            * ConstructionMaterialCatalog.installedLines(existing)), credits,
                    existing == null ? ConstructionMaterialCatalog.facility(state, corporationId, application, targetTier)
                            : ConstructionMaterialCatalog.facility(state, existing, targetTier - existing.tier()), Map.of()));
            builder.expansionProjects(projects);
            if (!facilityIds.contains(id)) facilityIds.add(id);
        } else {
            ShipDesign design = ownedFleetDesign(state, corporation, bodyId);
            List<ShipDesign> designs = new ArrayList<>(state.shipDesigns());
            if (designs.stream().noneMatch(item -> item.id().equals(design.id()))) {
                designs.add(design);
                builder.shipDesigns(designs);
            }
            ShipConstructionRequirements.Estimate quote = ShipConstructionRequirements.estimate(design);
            List<ShipConstructionOrder> orders = new ArrayList<>(state.shipConstructionOrders());
            orders.add(new ShipConstructionOrder("corp_" + UUID.randomUUID(), corporationId,
                    design.id(), systemForBody(state, bodyId), bodyId, 0.0,
                    quote.workUnits(), quote.materialsKg(), Map.of(), ShipyardAccess.contractAt(state, corporationId, systemForBody(state, bodyId), bodyId, design)));
            builder.shipConstructionOrders(orders);
        }
        List<Corporation> corporations = new ArrayList<>();
        for (Corporation item : state.corporations()) {
            corporations.add(item.id().equals(corporationId) ? new Corporation(item.id(), item.name(),
                    item.empireId(), item.headquartersEntityId(), item.marketOrientation(),
                    item.liquidCapitalReserves() - credits, facilityIds, shipIds, item.claimedVeinIds()) : item);
        }
        return builder.corporations(corporations).build();
    }

    private String fleetRole(Corporation corporation) {
        return "EXTRACTION".equalsIgnoreCase(corporation.marketOrientation())
                ? ShipRole.MINING_SHIP : ShipRole.CARGO_TRANSPORT;
    }

    private ShipDesign ownedFleetDesign(GameState state, Corporation corporation, String bodyId) {
        double cargo = state.commercialHubs().stream().filter(hub -> bodyId.equals(hub.entityId()))
                .flatMap(hub -> hub.activeOrders().values().stream()).mapToDouble(MarketOrder::demandKg).max().orElse(0);
        return CorporateShipDesigner.choose(state, corporation.id(), fleetRole(corporation), cargo);
    }
    public double findMaxShortcoming(Corporation corporation, List<CommercialHub> hubs) {
        return hubs == null ? 0.0 : hubs.stream().map(hub -> bestOrder(corporation, hub))
                .filter(order -> order != null).mapToDouble(MarketOrder::shortcomingScore).max().orElse(0.0);
    }

    private MarketOrder bestOrder(Corporation corporation, CommercialHub hub) {
        return hub.activeOrders().values().stream()
                .filter(order -> "TRANSPORT".equalsIgnoreCase(corporation.marketOrientation())
                        || applicationFor(corporation, order.resourceId()) != null)
                .max(Comparator.<MarketOrder>comparingDouble(order ->
                        opportunityScore(corporation, hub, order))
                        .thenComparingDouble(MarketOrder::shortcomingScore)).orElse(null);
    }

    private MarketOrder bestInvestmentOrder(GameState state, Corporation corporation, CommercialHub hub) {
        return bestInvestmentOrder(state, corporation, hub, false);
    }
    private MarketOrder bestInvestmentOrder(GameState state, Corporation corporation, CommercialHub hub, boolean requireProfit) {
        var empire = state.empires().stream().filter(value -> corporation.empireId().equals(value.id())).findFirst().orElse(null);
        return hub.activeOrders().values().stream().filter(order -> {
            String application = applicationForBody(state, corporation, hub.entityId(), order.resourceId());
            if (application == null) return false;
            var recipe = IndustryRecipeCatalog.find(application);
            if (!IndustryRecipeCatalog.isUnlocked(recipe, empire)) return false;
            if (state.industrialFacilities().stream().anyMatch(facility -> corporation.id().equals(facility.ownerEntityId())
                    && hub.entityId().equals(facility.planetId()) && application.equals(facility.applicationId()) && facility.isUndergoingExpansion())) return false;
            return com.spaceconquest.engine.industry.ProcessInput.maximumBatches(state, hub.entityId(),
                    recipe.inputSources(), state.geologicalDeposits()) > 0;
        }).filter(order -> order.shortcomingScore() >= SHORTCOMING_INFRASTRUCTURE_THRESHOLD
                && (!requireProfit || opportunityScore(state, corporation, hub, order) > 0))
                .max(Comparator.<MarketOrder>comparingDouble(order -> opportunityScore(state, corporation, hub, order)).thenComparing(MarketOrder::resourceId)).orElse(null);
    }

    private double opportunityScore(Corporation corporation, CommercialHub hub, MarketOrder order) {
        return opportunityScore(null, corporation, hub, order);
    }
    private double opportunityScore(GameState state, Corporation corporation, CommercialHub hub, MarketOrder order) {
        if ("TRANSPORT".equalsIgnoreCase(corporation.marketOrientation())) {
            return order.shortcomingScore();
        }
        String application = state == null ? applicationFor(corporation, order.resourceId()) : applicationForBody(state, corporation, hub.entityId(), order.resourceId());
        IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(application);
        if (recipe == null || order.demandKg() <= 0.0 || order.shortcomingScore() <= 0.0) return 0.0;
        var existing = state == null ? null : existingFactory(state, corporation.id(), hub.entityId(), application);
        int tier = state == null ? recipe.minTier() : Math.max(recipe.minTier(), Math.max(existing == null ? 1 : existing.tier() + 1,
                FacilityManufacturingCapacity.minimumTier(state, corporation.id(), application)));
        double effect = state == null ? 1 : ApplicationProduction.modifiersForOwner(state, corporation.id(), application).effectMultiplier();
        var empire = state == null ? null : state.empires().stream().filter(value -> corporation.empireId().equals(value.id())).findFirst().orElse(null);
        double technology = empire == null ? 1 : recipe.technologyMultiplier(empire);
        boolean workBased = com.spaceconquest.engine.industry.ProductionWork.definition(application) != null && state != null;
        String profession = "industrial_worker";
        var installed = com.spaceconquest.engine.industry.ProductionWork.initialEquipment(application);
        if (installed != null) profession = installed.operatorProfession();
        int operators = com.spaceconquest.engine.industry.ProductionWork.initialEquipment(application) == null ? recipe.workersPerBatch()
                : Math.toIntExact(com.spaceconquest.engine.industry.ProductionWork.initialEquipment(application).referenceCrew());
        var preview = new IndustrialFacility(existing == null ? "preview" : existing.id(), hub.entityId(), application, corporation.id(),
                IndustrialFacility.PRIVATE_CORPORATE, tier, existing == null ? operators : existing.allocatedWorkers(),
                existing == null ? profession : existing.workerProfessionId(), false, 0,
                existing == null ? null : existing.productionEquipment());
        double throughput = workBased ? com.spaceconquest.engine.industry.ProductionWork.quote(state, preview, preview.allocatedWorkers()).batchesPerDay()
                : Math.pow(1.5, Math.max(0, tier - 1)) * effect * technology;
        double priorThroughput = existing == null || !FacilityManufacturingCapacity.canOperate(state, existing) ? 0
                : workBased ? com.spaceconquest.engine.industry.ProductionWork.quote(state, existing, existing.allocatedWorkers()).batchesPerDay()
                : Math.pow(1.5, Math.max(0, existing.tier() - 1)) * effect * technology;
        var operatingRecipe = workBased ? IndustryRecipeCatalog.forFacility(state, preview) : recipe;
        double revenue = operatingRecipe.outputsKg().entrySet().stream().mapToDouble(output -> {
            MarketOrder buyer = hub.activeOrders().get(output.getKey());
            return buyer == null || buyer.demandKg() <= 0.0 ? 0.0
                    : (Math.min(output.getValue() * throughput, buyer.demandKg()) - Math.min(output.getValue() * priorThroughput, buyer.demandKg()))
                    * buyer.pricePerKg() * 0.80;
        }).sum();
        double inputs = operatingRecipe.inputsKg().entrySet().stream().mapToDouble(input -> {
            MarketOrder seller = hub.activeOrders().get(input.getKey());
            return input.getValue() * (throughput - priorThroughput) * (seller == null
                    ? MarketProcessor.basePricePerKg(input.getKey()) : seller.pricePerKg());
        }).sum();
        double payroll = existing == null ? operators * Profession.getBaseWageForProfession(profession) : 0;
        if (state != null) revenue = Math.min(revenue, inputs + state.marketAccounts().stream()
                .filter(a -> a.hubId().equals(hub.id())).mapToDouble(com.spaceconquest.engine.economy.MarketAccount::unsettledSalesCredits).findFirst().orElse(0));
        double tariff = state == null ? com.spaceconquest.engine.industry.PowerBillingProcessor.PRICE_PER_KWH
                : com.spaceconquest.engine.industry.GridSupplyPricing.priceWithFacility(state, preview, corporation.liquidCapitalReserves());
        double power = workBased ? Math.max(0, throughput - priorThroughput)
                * com.spaceconquest.engine.industry.ProductionWork.definition(state, preview).energyKwhPerBatch() * tariff
                : recipe.powerDrawKw() * (tier - (existing == null ? 0 : existing.tier())) * 24.0 * tariff;
        int addedTiers = existing == null ? tier : tier - existing.tier();
        double construction = (state == null ? ConstructionMaterialCatalog.facility(application, addedTiers)
                : existing == null ? ConstructionMaterialCatalog.facility(state, corporation.id(), application, addedTiers)
                : ConstructionMaterialCatalog.facility(state, existing, addedTiers))
                .entrySet().stream().mapToDouble(material ->
                        material.getValue() * MarketProcessor.basePricePerKg(material.getKey())).sum();
        double upkeep = FacilitySupplyPricing.declaredMaintenance(preview)
                - (existing == null ? 0 : FacilitySupplyPricing.declaredMaintenance(existing));
        double reserve = (infrastructureCost(application) + construction) / SupplyPricingPolicy.current().capitalRecoveryDays();
        double profit = revenue - SupplyPricingPolicy.current().requiredReceipts(inputs + payroll + power + upkeep, reserve,
                state == null ? 0 : SupplyPricingPolicy.taxRate(state, corporation.id()));
        return Math.max(0.0, profit) * order.shortcomingScore()
                / (infrastructureCost(application) + construction);
    }

    private double infrastructureCost(String application) {
        var definition = com.spaceconquest.engine.industry.FactoryDefinition.find(application);
        return definition == null ? INFRASTRUCTURE_COST : definition.investmentCredits();
    }

    private IndustrialFacility existingFactory(GameState state, String owner, String body, String application) {
        return state.industrialFacilities().stream().filter(facility -> owner.equals(facility.ownerEntityId()) && body.equals(facility.planetId())
                && application.equals(facility.applicationId()) && facility.tier() > 0 && !facility.isUndergoingExpansion())
                .min(Comparator.comparingInt(IndustrialFacility::tier).thenComparing(IndustrialFacility::id)).orElse(null);
    }

    private String applicationFor(Corporation corporation, String resourceId) {
        var defined = IndustryRecipeCatalog.all().stream()
                .filter(recipe -> recipe.infrastructure() != null && recipe.outputsKg().containsKey(resourceId)
                        && recipe.infrastructure().marketOrientations().stream().anyMatch(corporation.marketOrientation()::equalsIgnoreCase))
                .map(IndustryRecipeCatalog.Recipe::applicationId).sorted().findFirst();
        return defined.orElse(null);
    }

    private String applicationForBody(GameState state, Corporation corporation, String bodyId,
                                      String resourceId) {
        var candidates = IndustryRecipeCatalog.all().stream().filter(recipe -> recipe.infrastructure() != null
                && recipe.outputsKg().containsKey(resourceId) && recipe.infrastructure().marketOrientations().stream()
                .anyMatch(corporation.marketOrientation()::equalsIgnoreCase)).toList();
        if (candidates.isEmpty()) return applicationFor(corporation, resourceId);
        var empire = state.empires().stream().filter(e -> e.id().equals(corporation.empireId())).findFirst().orElse(null);
        return candidates.stream().filter(recipe -> IndustryRecipeCatalog.isUnlocked(recipe, empire)
                && recipe.infrastructure().permitsSite(state, bodyId)
                && cultivationSite(state, corporation, bodyId, recipe)
                && com.spaceconquest.engine.industry.ProcessInput.maximumBatches(state, bodyId,
                recipe.inputSources(), state.geologicalDeposits()) > 0)
                .min(Comparator.comparingDouble((IndustryRecipeCatalog.Recipe recipe) -> unitProcessCost(state, bodyId, recipe, resourceId))
                        .thenComparing(IndustryRecipeCatalog.Recipe::applicationId))
                .map(IndustryRecipeCatalog.Recipe::applicationId).orElse(null);
    }

    private double unitProcessCost(GameState state, String body, IndustryRecipeCatalog.Recipe recipe, String product) {
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(body)).findFirst().orElse(null);
        double inputs = recipe.inputsKg().entrySet().stream().mapToDouble(i -> {
            var order = hub == null ? null : hub.activeOrders().get(i.getKey());
            return i.getValue() * (order == null ? MarketProcessor.basePricePerKg(i.getKey()) : Math.max(0, order.pricePerKg()));
        }).sum();
        return (inputs + com.spaceconquest.engine.industry.ProductionWork.definition(recipe.applicationId()).energyKwhPerBatch()
                * com.spaceconquest.engine.industry.GridSupplyPricing.price(state, body)) / recipe.outputsKg().get(product);
    }

    private boolean cultivationSite(GameState state, Corporation corporation, String body, IndustryRecipeCatalog.Recipe recipe) {
        if (recipe.farming() == null) return true;
        var existing = existingFactory(state, corporation.id(), body, recipe.applicationId());
        if (existing != null && state.industryAccounts().stream().anyMatch(a -> a.facilityId().equals(existing.id()) && a.farmingState().cultivatedBatches() > 0)) return false;
        int tier = Math.max(FacilityManufacturingCapacity.minimumTier(state, corporation.id(), recipe.applicationId()),
                existing == null ? recipe.minTier() : existing.tier() + 1);
        return com.spaceconquest.engine.industry.FarmingDefinition.canInstall(state, recipe.applicationId(), body,
                existing == null ? "" : existing.id(), tier, existing == null ? null : existing.productionEquipment());
    }

    /** Do not commission additional load onto a known saturated grid; pending projects reserve capacity. */
    private boolean productionPowerAvailable(GameState state, Corporation corporation, String body, IndustryRecipeCatalog.Recipe recipe) {
        var grid = state.powerGrids().stream().filter(g -> g.entityId().equals(body)).findFirst().orElse(null);
        if (grid == null) return true;
        var existing = existingFactory(state, corporation.id(), body, recipe.applicationId());
        int tier = Math.max(FacilityManufacturingCapacity.minimumTier(state, corporation.id(), recipe.applicationId()),
                existing == null ? recipe.minTier() : existing.tier() + 1);
        var equipment = com.spaceconquest.engine.industry.ProductionWork.initialEquipment(recipe.applicationId());
        var preview = existing == null ? new IndustrialFacility("preview", body, recipe.applicationId(), corporation.id(),
                IndustrialFacility.PRIVATE_CORPORATE, tier, equipment == null ? recipe.workersPerBatch() : Math.toIntExact(equipment.referenceCrew()),
                equipment == null ? "industrial_worker" : equipment.operatorProfession(), false, 0) : atTier(existing, tier);
        double additional = Math.max(0, nominalPower(state, preview) - (existing == null ? 0 : nominalPower(state, existing)));
        double reserved = 0;
        for (var project : state.expansionProjects()) {
            var facility = state.industrialFacilities().stream().filter(f -> f.id().equals(project.facilityId()) && f.planetId().equals(body)).findFirst().orElse(null);
            if (facility != null) reserved += Math.max(0, nominalPower(state, atTier(facility, project.targetTier())) - nominalPower(state, facility));
        }
        double committed = state.industrialFacilities().stream().filter(f -> f.planetId().equals(body))
                .mapToDouble(f -> nominalPower(state, f)).sum();
        committed += state.householdAccounts().stream().filter(h -> h.bodyId().equals(body)).mapToDouble(h ->
                com.spaceconquest.engine.industry.PowerBillingProcessor.householdDemandKwh(state.empires().stream()
                        .filter(e -> e.id().equals(h.empireId())).findFirst().orElse(null), h.headcount()) / 24).sum();
        committed += state.launchActivities().stream().filter(a -> a.bodyId().equals(body)).mapToDouble(a -> a.powerKwh() / 24).sum();
        return additional + reserved <= Math.max(0, grid.totalGenerationKw() - Math.max(committed, grid.totalDemandKw())) + 1e-6;
    }

    private double nominalPower(GameState state, IndustrialFacility facility) {
        if (facility.tier() <= 0) return 0;
        return com.spaceconquest.engine.industry.ProductionWork.definition(facility.applicationId()) == null
                ? com.spaceconquest.engine.industry.PowerProcessor.requestedIndustryKw(facility, facility.allocatedWorkers())
                : com.spaceconquest.engine.industry.ProductionWork.quote(state, facility, facility.allocatedWorkers()).energyKwh() / 24;
    }
    private IndustrialFacility atTier(IndustrialFacility facility, int tier) {
        return new IndustrialFacility(facility.id(), facility.planetId(), facility.applicationId(), facility.ownerEntityId(),
                facility.ownershipType(), tier, facility.allocatedWorkers(), facility.workerProfessionId(), false, 0, facility.productionEquipment());
    }

    private boolean controlsBody(GameState state, Corporation corporation, String bodyId) {
        String systemId = systemForBody(state, bodyId);
        return systemId != null && state.empires().stream().anyMatch(empire -> corporation.empireId().equals(empire.id())
                && empire.controlledSystemIds().contains(systemId));
    }

    private String systemForBody(GameState state, String bodyId) {
        return com.spaceconquest.engine.industry.ConstructionMaterials.systemForBody(state, bodyId);
    }
}
