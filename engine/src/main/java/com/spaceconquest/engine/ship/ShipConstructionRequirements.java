package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Shared production estimate used by the designer and shipyard order creation. */
public final class ShipConstructionRequirements {
    /** One work unit represents one standardized shipyard labor-hour. */
    public static final double MINIMUM_WORK_UNITS = 200.0;
    public static final double KG_PER_WORK_UNIT = 100.0;

    public record Estimate(Map<String, Double> materialsKg, double workUnits) {
        public Estimate {
            materialsKg = materialsKg == null ? Map.of() : Map.copyOf(materialsKg);
        }
    }

    private ShipConstructionRequirements() {}

    public static Estimate estimate(ShipDesign design) {
        if (design == null) return new Estimate(Map.of(), 0.0);
        return estimate(design.totalDryMassKg(), design.hullMaterialId(),
                design.armorMaterialId(), design.equippedModuleIds(), design.manufacturingProfile());
    }

    public static Estimate estimate(double dryMassKg, String hullMaterialId,
                                    String armorMaterialId, List<String> moduleIds,
                                    ShipManufacturingProfile profile) {
        Estimate baseline = estimate(dryMassKg, hullMaterialId, armorMaterialId, moduleIds);
        Map<String, Double> materials = new LinkedHashMap<>();
        baseline.materialsKg().forEach((material, kg) -> materials.put(material, kg * profile.costMultiplier()));
        return new Estimate(materials, baseline.workUnits() * profile.costMultiplier());
    }

    public static Estimate estimate(double dryMassKg, String hullMaterialId,
                                    String armorMaterialId, List<String> moduleIds) {
        double safeMass = Math.max(0.0, Double.isFinite(dryMassKg) ? dryMassKg : 0.0);
        Map<String, Double> materials = ConstructionMaterialCatalog.ship(
                safeMass, hullMaterialId, armorMaterialId, moduleIds);
        double workUnits = Math.max(MINIMUM_WORK_UNITS, safeMass / KG_PER_WORK_UNIT);
        return new Estimate(materials, workUnits);
    }
}
