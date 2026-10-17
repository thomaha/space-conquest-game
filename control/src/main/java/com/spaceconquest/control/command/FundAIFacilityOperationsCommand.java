package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Transfers existing treasury cash to a viable public facility, using its current application reserve quote. */
public record FundAIFacilityOperationsCommand(String empireId, String facilityId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && empireId != null && facilityId != null
                && FacilityOperatingReadiness.inspect(state, empireId, facilityId).status() == FacilityOperatingReadiness.Status.CASH_LOW;
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var quote = FacilityOperatingReadiness.inspect(state, empireId, facilityId);
        var empires = state.empires().stream().map(owner -> !empireId.equals(owner.id()) ? owner : new Empire(owner.id(), owner.name(),
                owner.raceId(), owner.societyStructure(), owner.treasuryCredits() - quote.transferCredits(), owner.corporateTaxRate(),
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())).toList();
        var accounts = new ArrayList<>(state.industryAccounts());
        var account = accounts.stream().filter(value -> facilityId.equals(value.facilityId())).findFirst().orElse(IndustryAccount.empty(facilityId));
        accounts.removeIf(value -> facilityId.equals(value.facilityId())); accounts.add(account.withSubsidy(quote.transferCredits()));
        return state.toBuilder().empires(empires).industryAccounts(accounts).build();
    }
}
