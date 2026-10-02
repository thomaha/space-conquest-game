package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;

import java.util.ArrayList;
import java.util.List;

/** Transfers opening working capital from the owning state treasury to a new facility. */
final class FacilityOperatingCapital {
    private FacilityOperatingCapital() {}

    static GameState addFacility(GameState state, IndustrialFacility facility) {
        List<IndustrialFacility> facilities = new ArrayList<>(state.industrialFacilities());
        facilities.add(facility);
        GameState next = state.withIndustrialFacilities(facilities);
        if (!IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) return next;
        Empire owner = state.empires().stream().filter(empire -> empire.id().equals(facility.ownerEntityId()))
                .findFirst().orElse(null);
        if (owner == null) return next;
        double capital = Math.clamp(owner.treasuryCredits(), 0.0, 50_000.0);
        List<Empire> empires = new ArrayList<>(state.empires());
        empires.remove(owner);
        empires.add(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(),
                owner.treasuryCredits() - capital, owner.corporateTaxRate(), owner.controlledSystemIds(),
                owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(),
                owner.activeShipDesignIds()));
        List<IndustryAccount> accounts = new ArrayList<>(state.industryAccounts());
        accounts.add(IndustryAccount.empty(facility.id()).withOperatingCash(capital));
        return next.toBuilder().empires(empires).industryAccounts(accounts).build();
    }
}
