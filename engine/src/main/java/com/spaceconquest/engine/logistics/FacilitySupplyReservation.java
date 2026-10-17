package com.spaceconquest.engine.logistics;

/** Saved limits and actual empty-trip spending; claimed goods remain in public stock until paid loading. */
public record FacilitySupplyReservation(long expiresTurn, double spentCredits, double maximumCostCredits, double treasuryFloorCredits) {
    public FacilitySupplyReservation {
        if (expiresTurn < 0 || !Double.isFinite(spentCredits) || spentCredits < 0
                || !Double.isFinite(maximumCostCredits) || maximumCostCredits <= 0
                || !Double.isFinite(treasuryFloorCredits) || treasuryFloorCredits < 0)
            throw new IllegalArgumentException("Invalid facility supply reservation");
    }
    public FacilitySupplyReservation withSpending(double cost) {
        return new FacilitySupplyReservation(expiresTurn, spentCredits + cost, maximumCostCredits, treasuryFloorCredits);
    }
}
