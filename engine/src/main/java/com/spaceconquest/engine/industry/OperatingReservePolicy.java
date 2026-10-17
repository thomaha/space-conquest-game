package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.DataModelLoader;

/** Application-defined operating reserve targets and public maintenance. */
public record OperatingReservePolicy(double targetDays, double refillBelowDays, double maximumCashCredits,
                                     double treasuryReserveCredits, double maintenanceCreditsPerTier) {
    public OperatingReservePolicy {
        for (double value : new double[]{targetDays, refillBelowDays, maximumCashCredits, treasuryReserveCredits, maintenanceCreditsPerTier})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid operating reserve policy");
        if (targetDays < 1 || refillBelowDays <= 0 || refillBelowDays > targetDays || maximumCashCredits <= 0)
            throw new IllegalArgumentException("Invalid operating reserve target");
    }
    public static OperatingReservePolicy forApplication(String id) {
        String applicationId = com.spaceconquest.engine.technology.ApplicationProduction.capabilityApplication(id);
        try {
            return DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream())
                    .filter(app -> app.id().equals(applicationId)).map(app -> app.operatingReserve())
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
    public static double maintenance(IndustrialFacility facility) {
        if (!IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) return 0;
        var policy = forApplication(facility.applicationId());
        return (policy == null ? 80 : policy.maintenanceCreditsPerTier()) * Math.max(0, facility.tier())
                * (PowerPlantCatalog.find(facility.applicationId()) == null ? 1 : GenerationCapacity.units(facility));
    }
}
