package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;

import java.util.ArrayList;
import java.util.List;

/** Opts a state-owned facility into or out of local infrastructure-funded support. */
public record SetPublicIndustrySubsidyCommand(String empireId, String facilityId,
                                               boolean enabled) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || facilityId == null
                || state.empires().stream().noneMatch(empire -> empireId.equals(empire.id()))) return false;
        return state.industrialFacilities().stream().anyMatch(facility ->
                facilityId.equals(facility.id()) && empireId.equals(facility.ownerEntityId())
                        && IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        List<IndustryAccount> updated = new ArrayList<>(state.industryAccounts());
        IndustryAccount account = updated.stream().filter(item -> facilityId.equals(item.facilityId()))
                .findFirst().orElse(IndustryAccount.empty(facilityId));
        updated.removeIf(item -> facilityId.equals(item.facilityId()));
        updated.add(account.withPublicSubsidy(enabled));
        return state.withIndustryAccounts(updated);
    }
}
