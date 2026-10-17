package com.spaceconquest.engine.industry;

/** Parallel installed generator units using application ratings and the existing immutable equipment pool. */
public final class GenerationCapacity {
    private GenerationCapacity() {}
    public static int units(IndustrialFacility facility) {
        return facility.productionEquipment() == null ? 1 : facility.productionEquipment().parallelLines();
    }
    public static long workers(IndustrialFacility facility, PowerPlantCatalog.Plant plant) {
        return (long) plant.requiredWorkers() * units(facility);
    }
    public static double scale(IndustrialFacility facility, PowerPlantCatalog.Plant plant, int paidWorkers) {
        return facility.tier() * Math.clamp((double) paidWorkers / workers(facility, plant), 0, 1)
                * facility.getEffectiveThroughputMultiplier() / Math.pow(1.5, Math.max(0, facility.tier() - 1)) * units(facility);
    }
    public static IndustrialFacility pool(IndustrialFacility facility, int units) {
        var plant = PowerPlantCatalog.find(facility.applicationId());
        if (plant == null || units < 1) throw new IllegalArgumentException("A generator pool needs an installed application and positive units");
        var equipment = new ProductionEquipment(facility.applicationId() + "_units", "GENERATION", plant.kwPerTier() * 24,
                plant.requiredWorkers(), plant.workerProfessionId(), 0, 1, units);
        int workers = Math.toIntExact((long) facility.allocatedWorkers() * units);
        return new IndustrialFacility(facility.id(), facility.planetId(), facility.applicationId(), facility.ownerEntityId(), facility.ownershipType(),
                facility.tier(), workers, facility.workerProfessionId(), facility.isUndergoingExpansion(), facility.expansionProgress(), equipment);
    }
}
