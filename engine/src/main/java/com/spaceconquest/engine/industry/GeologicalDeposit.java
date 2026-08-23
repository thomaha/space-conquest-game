package com.spaceconquest.engine.industry;

/**
 * Subterranean geological mineral vein located in a celestial body crust.
 *
 * @param id                    unique deposit identifier
 * @param planetId              host planet or moon identifier
 * @param materialId            mineral or ore material identifier
 * @param initialVolumeKg       total original deposit volume in kilograms
 * @param remainingVolumeKg     remaining extractable volume in kilograms
 * @param concentrationModifier purity and extraction speed modifier
 * @param isDiscovered          true if uncovered through geological prospecting
 * @param ownerEntityId         public state or private corporate leaseholder
 */
public record GeologicalDeposit(
        String id,
        String planetId,
        String materialId,
        double initialVolumeKg,
        double remainingVolumeKg,
        double concentrationModifier,
        boolean isDiscovered,
        String ownerEntityId
) {
    public boolean isDepleted() {
        return remainingVolumeKg <= 0.0;
    }

    public double getDepletionPercentage() {
        if (initialVolumeKg <= 0.0) return 100.0;
        return (1.0 - (remainingVolumeKg / initialVolumeKg)) * 100.0;
    }
}
