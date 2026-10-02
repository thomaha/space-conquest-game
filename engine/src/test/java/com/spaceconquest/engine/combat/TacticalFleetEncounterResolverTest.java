package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalFleetEncounterResolverTest {
    private static final ShipDesign COMBAT_DESIGN = new ShipDesign(
            "combat_design", "Combat ship", "terran", ShipRole.COMBAT_SHIP, "steel", List.of(), "steel",
            2.0, 20_000.0, 1_000.0, 200.0, 1.5, 100_000.0, 500_000.0, true, false);

    @Test
    void resolvesWarEncounterAtSharedSiteAndRecordsOutcome() {
        Fleet attacker = fleet("attacker", "terran", List.of(
                ship("a1", "terran", 1_000.0), ship("a2", "terran", 1_000.0)));
        Fleet defender = fleet("defender", "vulkan", List.of(ship("d1", "vulkan", 300.0)));
        GameState state = GameState.builder().turn(18).shipDesigns(List.of(COMBAT_DESIGN))
                .fleets(List.of(attacker, defender))
                .diplomaticRelations(List.of(new DiplomaticRelation("terran", "vulkan", "TOTAL_WAR", 0.0)))
                .build();

        GameState resolved = resolver().resolveEncounters(state);

        assertEquals(1, resolved.fleetEngagements().size());
        FleetEngagementRecord battle = resolved.fleetEngagements().getFirst();
        assertEquals(18, battle.turn());
        assertEquals("sol", battle.systemId());
        assertEquals("attacker", battle.attackerFleetId());
        assertEquals("defender", battle.defenderFleetId());
        assertEquals("terran", battle.winnerEmpireId());
        assertEquals(List.of("d1"), battle.destroyedShipIds());
        assertEquals(1, resolved.fleets().size());
        assertEquals(List.of("a1", "a2"), resolved.fleets().getFirst().ships().stream()
                .map(ShipInstance::id).toList());
        assertTrue(state.fleets().get(1).ships().getFirst().currentHullHealth() == 300.0);
    }

    @Test
    void doesNotEngageInNeutralRelationsOrDuringTransit() {
        Fleet attacker = fleet("attacker", "terran", List.of(ship("a1", "terran", 1_000.0)));
        Fleet defender = fleet("defender", "vulkan", List.of(ship("d1", "vulkan", 1_000.0)));
        GameState peaceful = GameState.builder().shipDesigns(List.of(COMBAT_DESIGN))
                .fleets(List.of(attacker, defender))
                .diplomaticRelations(List.of(new DiplomaticRelation("terran", "vulkan", "NEUTRAL", 0.0)))
                .build();

        GameState unchanged = resolver().resolveEncounters(peaceful);

        assertTrue(unchanged.fleetEngagements().isEmpty());
        assertEquals(peaceful.fleets(), unchanged.fleets());
        assertFalse(unchanged.fleets().isEmpty());
    }

    private TacticalFleetEncounterResolver resolver() {
        return new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42)));
    }

    private Fleet fleet(String id, String owner, List<ShipInstance> ships) {
        return new Fleet(id, id, owner, "sol", "", 0.0, 0.0, 0.0, false, "AGGRESSIVE", ships);
    }

    private ShipInstance ship(String id, String owner, double hull) {
        return new ShipInstance(id, COMBAT_DESIGN.id(), owner, hull, 500.0, 100.0, Map.of());
    }
}
