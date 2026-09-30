package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustrialFacility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetPublicIndustrySubsidyCommandTest {
    @Test
    void onlyOwnerMayMarkPublicFacilityForSupport() {
        Empire owner = new Empire("owner", "Owner", "human", "Individualist", 100.0,
                0.1, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        Empire other = new Empire("other", "Other", "human", "Individualist", 100.0,
                0.1, List.of(), List.of(), Map.of(), List.of(), List.of());
        IndustrialFacility facility = new IndustrialFacility("plant", "earth", "solar_power", "owner",
                IndustrialFacility.PUBLIC_STATE, 1, 10, "technician", false, 0.0);
        GameState state = GameState.builder().empires(List.of(owner, other))
                .industrialFacilities(List.of(facility)).build();

        assertFalse(new SetPublicIndustrySubsidyCommand("other", "plant", true).validate(state));
        GameState enabled = new SetPublicIndustrySubsidyCommand("owner", "plant", true).apply(state);
        assertTrue(enabled.industryAccounts().getFirst().publicSubsidyEnabled());
        GameState disabled = new SetPublicIndustrySubsidyCommand("owner", "plant", false).apply(enabled);
        assertFalse(disabled.industryAccounts().getFirst().publicSubsidyEnabled());
    }
}
