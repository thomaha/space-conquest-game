package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.industry.FactoryDefinition;
import java.util.LinkedHashMap;
import java.util.Map;

/** Researchable service capabilities, separate from bulk material production. */
public record InfrastructureService(String facilityType, FactoryDefinition infrastructure,
        String professionId, int workers, LaunchService.Mode mode, boolean passengers,
        boolean shipLaunch, double capacityKgPerTier, double powerKwPerTier,
        double powerKwPerKg, double feePerKg, double minimumFee, double powerPriceMultiplier,
        String driveId, double vehicleDryMassKg, double fuelStockKgPerTier, double liftDiscount,
        Work work) {
    public record Work(double unitsPerKg, com.spaceconquest.engine.industry.ProductionEquipment equipment) {
        public Work {
            if (!Double.isFinite(unitsPerKg) || unitsPerKg <= 0 || equipment == null)
                throw new IllegalArgumentException("Invalid launch work definition");
        }
    }
    public InfrastructureService {
        if (facilityType == null || facilityType.isBlank() || infrastructure == null
                || professionId == null || professionId.isBlank() || workers < 0 || mode == null || work == null
                || !professionId.equals(work.equipment().operatorProfession()) || workers != work.equipment().referenceOperators())
            throw new IllegalArgumentException("Invalid infrastructure service");
        for (double value : new double[]{capacityKgPerTier, powerKwPerTier, powerKwPerKg,
                feePerKg, minimumFee, powerPriceMultiplier, vehicleDryMassKg, fuelStockKgPerTier})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid service capacity or cost");
        if (!Double.isFinite(liftDiscount) || liftDiscount < 0 || liftDiscount > 1
                || capacityKgPerTier <= 0 || (mode == LaunchService.Mode.ROCKET && (driveId == null || driveId.isBlank())))
            throw new IllegalArgumentException("Missing service capacity or propulsion");
    }
    public record Definition(String applicationId, InfrastructureService capability) {}
    private static final class Loaded {
        static final Map<String, Definition> ALL = load();
        private static Map<String, Definition> load() {
            var result = new LinkedHashMap<String, Definition>();
            try {
                for (var technology : DataModelLoader.loadTechnologies()) for (var app : technology.applications()) {
                    var service = app.infrastructureService();
                    if (service != null && result.putIfAbsent(service.facilityType(), new Definition(app.id(), service)) != null)
                        throw new IllegalArgumentException("Duplicate service facility type: " + service.facilityType());
                }
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            return Map.copyOf(result);
        }
    }
    public static Map<String, Definition> all() { return Loaded.ALL; }
    public static Definition find(String type) { return Loaded.ALL.get(type); }
}
