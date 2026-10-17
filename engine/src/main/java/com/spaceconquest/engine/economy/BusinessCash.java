package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;

/** Explicit transfers to and from the owner's spendable cash. */
public final class BusinessCash {
    private BusinessCash() {}
    public static GameState change(GameState state, String owner, double delta) {
        double cash = EconomicOwnership.cash(state, owner);
        if (!Double.isFinite(delta) || cash + delta < -1e-6) throw new IllegalArgumentException("Unfunded business payment");
        return state.toBuilder().corporations(state.corporations().stream().map(c -> !c.id().equals(owner) ? c
                : new Corporation(c.id(), c.name(), c.empireId(), c.headquartersEntityId(), c.marketOrientation(),
                Math.max(0, c.liquidCapitalReserves() + delta), c.ownedFacilityIds(), c.ownedShipIds(), c.claimedVeinIds())).toList())
                .empires(state.empires().stream().map(e -> !e.id().equals(owner) ? e : new Empire(e.id(), e.name(), e.raceId(),
                        e.societyStructure(), Math.max(0, e.treasuryCredits() + delta), e.corporateTaxRate(), e.controlledSystemIds(),
                        e.ministries(), e.systemGovernorAssignments(), e.unlockedTechIds(), e.activeShipDesignIds())).toList()).build();
    }
}
