package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.habitation.PassengerStasis;

import java.util.LinkedHashMap;
import java.util.Map;

/** Material bills for the construction paths currently represented by game-state assets. */
public final class ConstructionMaterialCatalog {
    private ConstructionMaterialCatalog() {}

    public static int installedLines(IndustrialFacility facility) {
        return facility == null || facility.productionEquipment() == null ? 1 : facility.productionEquipment().parallelLines();
    }
    public static Map<String, Double> facility(GameState state, IndustrialFacility facility, int addedTiers) {
        var bill = new LinkedHashMap<String, Double>();
        facility(state, facility.ownerEntityId(), facility.applicationId(), addedTiers)
                .forEach((id, kg) -> bill.put(id, kg * installedLines(facility)));
        return Map.copyOf(bill);
    }

    public static Map<String, Double> facility(GameState state, String ownerId, String applicationId, int tier) {
        double multiplier = ApplicationProduction.modifiersForOwner(state, ownerId, applicationId).costMultiplier();
        Map<String, Double> bill = new LinkedHashMap<>();
        facility(applicationId, tier).forEach((material, kg) -> bill.put(material, kg * multiplier));
        return Map.copyOf(bill);
    }

    public static Map<String, Double> facility(String applicationId, int tier) {
        double scale = Math.max(1, tier);
        Map<String, Double> bill = new LinkedHashMap<>();
        var yard = com.spaceconquest.engine.ship.ShipyardTechnology.yard(applicationId);
        var factory = FactoryDefinition.find(applicationId);
        if (factory != null) {
            factory.constructionMaterialsKg().forEach((material, kg) -> bill.put(material, kg * scale));
            return Map.copyOf(bill);
        }
        if (yard != null) {
            yard.materialsKg().forEach((material, kg) -> bill.put(material, kg * scale));
            return Map.copyOf(bill);
        }
        var plant = PowerPlantCatalog.find(applicationId);
        if (plant != null) {
            plant.constructionMaterialsKg().forEach((material, kg) -> bill.put(material, kg * scale));
            return Map.copyOf(bill);
        }
        var service = com.spaceconquest.engine.logistics.InfrastructureService.find(applicationId);
        if (service == null) throw new IllegalArgumentException("No construction definition: " + applicationId);
        service.capability().infrastructure().constructionMaterialsKg()
                .forEach((material, kg) -> bill.put(material, kg * scale));
        return Map.copyOf(bill);
    }

    public static double facilityWork(String applicationId, int tiers, boolean expansion) {
        var factory = FactoryDefinition.find(applicationId);
        if (factory != null) return (expansion ? factory.expansionWorkPerTier() : factory.constructionWorkPerTier()) * tiers;
        var plant = PowerPlantCatalog.find(applicationId);
        if (plant != null) return (expansion ? plant.expansionWorkHoursPerTier() : plant.constructionWorkHoursPerTier()) * tiers;
        var yard = com.spaceconquest.engine.ship.ShipyardTechnology.yard(applicationId);
        if (yard != null) return yard.constructionWork() * tiers;
        var service = com.spaceconquest.engine.logistics.InfrastructureService.find(applicationId);
        if (service == null) throw new IllegalArgumentException("No construction definition: " + applicationId);
        return (expansion ? service.capability().infrastructure().expansionWorkPerTier()
                : service.capability().infrastructure().constructionWorkPerTier()) * tiers;
    }

    public static Map<String, Double> ship(ShipDesign design) {
        if (design == null) return Map.of();
        var bill = new LinkedHashMap<>(ship(design.totalDryMassKg(), design.hullMaterialId(),
                design.armorMaterialId(), design.equippedModuleIds()));
        for (String id : design.equippedModuleIds()) {
            var baseline = com.spaceconquest.engine.ship.CombatModuleCatalog.module(id);
            var installed = com.spaceconquest.engine.ship.CombatModuleCatalog.module(design, id);
            if (baseline == null) continue;
            baseline.materialCostsKg().forEach((material, kg) -> bill.merge(material, -kg, Double::sum));
            if (installed != null) installed.materialCostsKg().forEach((material, kg) -> bill.merge(material, kg, Double::sum));
        }
        bill.values().removeIf(kg -> kg <= 0);
        return Map.copyOf(bill);
    }

    public static Map<String, Double> ship(double dryMassKg, String hullMaterialId,
                                           String armorMaterialId, java.util.List<String> moduleIds) {
        double mass = Math.max(1_000.0, Double.isFinite(dryMassKg) ? dryMassKg : 0.0);
        Map<String, Double> bill = new LinkedHashMap<>();
        bill.merge(hullMaterialId, mass * 0.65, Double::sum);
        bill.merge(armorMaterialId, mass * 0.20, Double::sum);
        bill.merge("refined_copper", mass * 0.10, Double::sum);
        bill.merge("silicon", mass * 0.05, Double::sum);
        long pods = (moduleIds == null ? java.util.List.<String>of() : moduleIds).stream()
                .filter(PassengerStasis.MODULE_ID::equals).count();
        if (pods > 0) {
            bill.merge("refined_aluminum", pods * 100.0, Double::sum);
            bill.merge("refined_copper", pods * 50.0, Double::sum);
        }
          for (String id : moduleIds == null ? java.util.List.<String>of() : moduleIds) {
              var module = com.spaceconquest.engine.ship.CombatModuleCatalog.module(id);
              if (module == null) module = com.spaceconquest.engine.ship.ShipRepairModuleCatalog.module(id);
              if (module != null) module.materialCostsKg().forEach((material, kg) -> bill.merge(material, kg, Double::sum));
          }
        return Map.copyOf(bill);
    }

    public static Map<String, Double> orbitalStation(String armorMaterialId, int slots) {
        Map<String, Double> bill = new LinkedHashMap<>();
        bill.put("refined_iron", Math.max(1, slots) * 500.0);
        bill.merge(armorMaterialId, Math.max(1, slots) * 100.0, Double::sum);
        bill.put("refined_copper", Math.max(1, slots) * 50.0);
        return Map.copyOf(bill);
    }

    public static Map<String, Double> spaceElevator() {
        return com.spaceconquest.engine.logistics.InfrastructureService.find("SPACE_ELEVATOR")
                .capability().infrastructure().constructionMaterialsKg();
    }

    public static Map<String, Double> stationModule(double dryMassKg) {
        return Map.of("refined_iron", Math.max(100.0, dryMassKg * 0.75),
                "refined_copper", Math.max(25.0, dryMassKg * 0.15),
                "silicon", Math.max(10.0, dryMassKg * 0.05));
    }

    public static Map<String, Double> megastructure(String type) {
        double multiplier = type != null && type.contains("GATEWAY") ? 4.0 : 10.0;
        return Map.of("refined_iron", 50_000.0 * multiplier,
                "refined_aluminum", 20_000.0 * multiplier,
                "refined_copper", 5_000.0 * multiplier,
                "silicon", 1_000.0 * multiplier);
    }

    public static Map<String, Double> terraforming(String type) {
        if (type != null && (type.endsWith("SEEDING") || type.contains("ALGAE"))) {
            return Map.of("agricultural_biomass", 1_000.0, "purified_water", 500.0);
        }
        return Map.of("refined_iron", 2_000.0, "refined_copper", 500.0,
                "silicon", 100.0);
    }
}
