package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;

/** Tick-owned exact private service payments and temporary fee holds within discarded construction steps. */
public final class ShipyardPayments {
    private ShipyardPayments() {}
    public static GameState pay(GameState state, String payer, ShipyardContract contract, double work) {
        if (contract == null) return state;
        double fee = work * contract.feeCreditsPerWorkUnit();
        if (!Double.isFinite(fee) || fee < 0 || EconomicOwnership.cash(state, payer) < fee || EconomicOwnership.empireFor(state, contract.providerOwnerId()) == null) return null;
        return change(change(state, payer, -fee), contract.providerOwnerId(), fee);
    }
    static GameState change(GameState state, String owner, double amount) {
        return state.toBuilder().empires(state.empires().stream().map(empire -> !owner.equals(empire.id()) ? empire
                : new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), empire.treasuryCredits() + amount,
                        empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(), empire.unlockedTechIds(), empire.activeShipDesignIds())).toList())
                .corporations(state.corporations().stream().map(corp -> !owner.equals(corp.id()) ? corp
                        : new Corporation(corp.id(), corp.name(), corp.empireId(), corp.headquartersEntityId(), corp.marketOrientation(), corp.liquidCapitalReserves() + amount,
                                corp.ownedFacilityIds(), corp.ownedShipIds(), corp.claimedVeinIds())).toList()).build();
    }
}
