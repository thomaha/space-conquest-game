package com.spaceconquest.engine.market;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Prices actual installed application-defined capacity without selling imaginary coproducts. */
public final class FacilitySupplyPricing {
    private FacilitySupplyPricing() {}
    public static List<SupplyOffer> offers(GameState state, CommercialHub hub, double electricityPrice) {
        return offers(state, hub, electricityPrice, GridSupplyPricing.forecast(state).productionEnergyKwh());
    }
    public static List<SupplyOffer> offers(GameState state, CommercialHub hub, double electricityPrice, Map<String, Double> energyBudgets) {
        return offers(state, hub, electricityPrice, energyBudgets, null);
    }
    public static List<SupplyOffer> offers(GameState state, CommercialHub hub, double electricityPrice,
                                           Map<String, Double> energyBudgets, String onlyFacilityId) {
        return offers(state, hub, electricityPrice, energyBudgets, onlyFacilityId, null);
    }
    public static List<SupplyOffer> offers(GameState state, CommercialHub hub, double electricityPrice,
                                           Map<String, Double> energyBudgets, String onlyFacilityId, String requestedMaterial) {
        var offers = new ArrayList<SupplyOffer>();
        for (var facility : state.industrialFacilities()) {
            if (onlyFacilityId != null && !onlyFacilityId.equals(facility.id())) continue;
            if (!facility.planetId().equals(hub.entityId()) || facility.tier() <= 0 || facility.isUndergoingExpansion()
                    || IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())) continue;
            var recipe = IndustryRecipeCatalog.forFacility(state, facility);
            if (recipe == null || !FacilityManufacturingCapacity.canOperate(state, facility)) continue;
            var ownerId = state.corporations().stream().filter(c -> c.id().equals(facility.ownerEntityId())).map(Corporation::empireId).findFirst().orElse(facility.ownerEntityId());
            var owner = state.empires().stream().filter(e -> e.id().equals(ownerId)).findFirst().orElse(null);
            if (!IndustryRecipeCatalog.isUnlocked(recipe, owner) || facility.tier() < recipe.minTier()) continue;
            boolean typed = ProductionWork.definition(facility.applicationId()) != null;
            double rated = typed ? ProductionWork.quote(state, facility, facility.allocatedWorkers()).batchesPerDay()
                    : facility.allocatedWorkers() * facility.getEffectiveThroughputMultiplier()
                    * ApplicationProduction.modifiersForOwner(state, facility.ownerEntityId(), facility.applicationId()).effectMultiplier()
                    * recipe.technologyMultiplier(owner) / recipe.workersPerBatch();
            if (rated <= 0) continue;
            double energyPerBatch = typed ? ProductionWork.definition(state, facility).energyKwhPerBatch()
                    : PowerProcessor.requestedIndustryKw(facility, facility.allocatedWorkers()) * 24 / rated;
            double batches = Math.min(rated, ProcessInput.maximumBatches(state, facility.planetId(), recipe.inputSources(), state.geologicalDeposits()));
            if (energyPerBatch > 0) batches = Math.min(batches, energyBudgets.getOrDefault(facility.id(), 0.0) / energyPerBatch);
            double materials = 0;
            for (var input : recipe.inputsKg().entrySet()) {
                var order = hub.activeOrders().get(input.getKey());
                if (order == null || order.supplyKg() <= 0) { batches = 0; break; }
                batches = Math.min(batches, order.supplyKg() / input.getValue());
                materials += input.getValue() * order.pricePerKg();
            }
            if (batches <= 0) continue;
            double payroll = facility.allocatedWorkers() * Profession.getBaseWageForProfession(facility.workerProfessionId());
            double maintenance = declaredMaintenance(facility);
            double variable = materials + energyPerBatch * electricityPrice;
            double funds = cash(state, facility);
            if (variable > 0) batches = Math.min(batches, Math.max(0, funds - payroll - maintenance) / variable);
            if (batches <= rated * 1e-9 || funds < payroll + maintenance) continue;
            double capital = capital(state, hub, facility) / SupplyPricingPolicy.current().capitalRecoveryDays();
            addOffers(state, hub, facility, recipe, batches, payroll + maintenance + batches * variable, capital, offers, requestedMaterial);
        }
        return List.copyOf(offers);
    }
    private static void addOffers(GameState state, CommercialHub hub, IndustrialFacility facility,
                                  IndustryRecipeCatalog.Recipe recipe, double batches, double costs, double capital,
                                  List<SupplyOffer> offers, String requestedMaterial) {
        double tax = SupplyPricingPolicy.taxRate(state, facility.ownerEntityId());
        double receipts = SupplyPricingPolicy.current().requiredReceipts(costs, capital, tax);
        if (!Double.isFinite(receipts)) return;
        var primary = recipe.outputsKg().entrySet().stream().filter(e -> recipe.infrastructure() == null || !recipe.infrastructure().byproductMaterialIds().contains(e.getKey())
                        || e.getKey().equals(requestedMaterial) || committedDemand(state, facility, e.getKey()) > 0)
                .filter(e -> demand(state, hub, facility, e.getKey()) > 0).toList();
        double weight = primary.stream().mapToDouble(e -> Math.min(e.getValue() * batches, demand(state, hub, facility, e.getKey()))
                * MarketProcessor.basePricePerKg(e.getKey())).sum();
        if (weight <= 0) return;
        double delay = recipe.farming() == null ? recipe.work() == null ? 1 : Math.max(1, recipe.work().minimumProcessingDays()) : recipe.farming().growingDays();
        if (recipe.farming() != null) {
            var growth = state.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id()))
                    .map(IndustryAccount::farmingState).findFirst().orElse(FarmingState.empty());
            if (growth.cultivatedBatches() > 0) delay = Math.max(1, delay - (state.turn() - growth.startedTurn() + 1));
        }
        // Allocate the complete bill once across demanded primary products. Unsold byproducts earn no forecast credit.
        for (var output : primary) {
            double quantity = Math.min(output.getValue() * batches, demand(state, hub, facility, output.getKey()));
            double allocation = quantity * MarketProcessor.basePricePerKg(output.getKey()) / weight;
            double price = receipts * allocation / quantity / IndustryMarketProcessor.WHOLESALE_SHARE;
            if (Double.isFinite(price) && price > 0) offers.add(new SupplyOffer(facility.id(), output.getKey(), quantity, price, delay, costs * allocation, capital * allocation, tax));
        }
    }
    private static double committedDemand(GameState state, IndustrialFacility facility, String material) {
        return state.businessLedger().contracts().stream().filter(c -> c.openAt(state.turn()) && c.facilityId().equals(facility.id())
                && c.supplierOwnerId().equals(facility.ownerEntityId()) && c.materialId().equals(material))
                .mapToDouble(c -> c.remainingKg() / Math.max(1, c.deadlineTurn() - state.turn() + 1)).sum();
    }
    private static double demand(GameState state, CommercialHub hub, IndustrialFacility facility, String material) {
        var order = hub.activeOrders().get(material);
        return Math.max(order == null ? 0 : order.demandKg(), committedDemand(state, facility, material));
    }
    public static double cash(GameState state, IndustrialFacility facility) {
        if (IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) return state.corporations().stream()
                .filter(c -> c.id().equals(facility.ownerEntityId())).mapToDouble(Corporation::liquidCapitalReserves).findFirst().orElse(0);
        return state.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id()))
                .mapToDouble(IndustryAccount::operatingCashCredits).findFirst().orElse(0);
    }
    public static double declaredMaintenance(IndustrialFacility facility) {
        var policy = OperatingReservePolicy.forApplication(facility.applicationId());
        return policy == null ? OperatingReservePolicy.maintenance(facility) : policy.maintenanceCreditsPerTier() * facility.tier()
                * (PowerPlantCatalog.find(facility.applicationId()) == null ? 1 : GenerationCapacity.units(facility));
    }
    public static double capital(GameState state, CommercialHub hub, IndustrialFacility facility) {
        var definition = FactoryDefinition.find(facility.applicationId());
        var service = com.spaceconquest.engine.logistics.InfrastructureService.find(facility.applicationId());
        if (definition == null && service != null) definition = service.capability().infrastructure();
        var plant = PowerPlantCatalog.find(facility.applicationId());
        var materials = definition == null ? plant == null ? ConstructionMaterialCatalog.facility(facility.applicationId(), 1) : plant.constructionMaterialsKg() : definition.constructionMaterialsKg();
        double cost = materials.entrySet().stream().mapToDouble(e -> e.getValue() * (hub != null && hub.activeOrders().containsKey(e.getKey())
                ? hub.activeOrders().get(e.getKey()).pricePerKg() : MarketProcessor.basePricePerKg(e.getKey()))).sum();
        double lines = facility.productionEquipment() == null ? 1 : facility.productionEquipment().parallelLines();
        return (cost + (definition == null ? 0 : definition.investmentCredits())) * facility.tier() * lines;
    }
}
