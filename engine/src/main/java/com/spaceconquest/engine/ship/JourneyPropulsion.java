package com.spaceconquest.engine.ship;

/** Propulsion selected and paid for when a physical journey is planned. */
public record JourneyPropulsion(String designId, String driveModuleId, double exhaustVelocityMps,
                                String reactorFeedId, double committedReactorKg) {
    public JourneyPropulsion {
        if (designId == null || designId.isBlank() || driveModuleId == null || driveModuleId.isBlank()
                || !Double.isFinite(exhaustVelocityMps) || exhaustVelocityMps <= 0
                || !Double.isFinite(committedReactorKg) || committedReactorKg < 0
                || committedReactorKg > 0 && (reactorFeedId == null || reactorFeedId.isBlank()))
            throw new IllegalArgumentException("Invalid journey propulsion");
    }

    public static JourneyPropulsion capture(ShipInstance ship, PropulsionCatalog.Drive drive,
                                           PropulsionCatalog.ReactorFuel feed, double propellantKg) {
        return new JourneyPropulsion(ship.designId(), drive.moduleId(), drive.exhaustVelocityMps()
                * (feed == null ? 1 : feed.exhaustMultiplier()), feed == null ? null : feed.materialId(),
                feed == null ? 0 : propellantKg * feed.kgPerPropellantKg());
    }
}
