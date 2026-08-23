package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TacticalCombatArenaTest {

    private TacticalCombatProcessor processor;
    private List<ShipDesign> designs;
    private Fleet attackerFleet;
    private Fleet defenderFleet;

    @BeforeEach
    void setUp() {
        processor = new TacticalCombatProcessor(new Random(42));

        ShipDesign cruiser = new ShipDesign(
                "design_cruiser", "Terran Battlecruiser", "terran_confederation",
                "COMBAT_SHIP", "steel", List.of(), "titanium_aluminide", 5.0,
                10000.0, 5000.0, 1000.0, 12.0, 50000.0, 80000.0, true, false
        );

        ShipDesign raider = new ShipDesign(
                "design_raider", "Corsair Raider", "shadow_syndicate",
                "COMBAT_SHIP", "steel", List.of(), "inconel_alloy", 3.0,
                6000.0, 2000.0, 600.0, 8.0, 30000.0, 60000.0, true, false
        );

        designs = List.of(cruiser, raider);

        ShipInstance attShip1 = new ShipInstance("ship_att_1", "design_cruiser", "terran_confederation", 1000, 300, 200, Map.of());
        ShipInstance attShip2 = new ShipInstance("ship_att_2", "design_cruiser", "terran_confederation", 1000, 300, 200, Map.of());
        attackerFleet = new Fleet("att_fleet", "Imperial 1st Taskforce", "terran_confederation", "sol", "", 0, 0, 0, false, TacticalCombatProcessor.STANCE_AGGRESSIVE_BRAWL, List.of(attShip1, attShip2));

        ShipInstance defShip1 = new ShipInstance("ship_def_1", "design_raider", "shadow_syndicate", 600, 150, 100, Map.of());
        ShipInstance defShip2 = new ShipInstance("ship_def_2", "design_raider", "shadow_syndicate", 600, 150, 100, Map.of());
        defenderFleet = new Fleet("def_fleet", "Syndicate Raider Pack", "shadow_syndicate", "sol", "", 0, 0, 0, false, TacticalCombatProcessor.STANCE_STANDOFF_KITE, List.of(defShip1, defShip2));
    }

    @Test
    void testSubsystemTargetingAndVisualRoundsTelemetry() {
        TacticalCombatProcessor.CombatEngagementResult result = processor.resolveFleetEngagementWithCarrierWings(
                attackerFleet, defenderFleet, designs, List.of(), List.of(), null, TacticalCombatProcessor.TARGET_SUBSYSTEM_WARP
        );

        assertNotNull(result);
        assertNotNull(result.visualRounds());
        assertFalse(result.visualRounds().isEmpty());

        TacticalCombatProcessor.CombatVisualRound r1 = result.visualRounds().get(0);
        assertEquals(1, r1.roundNumber());
        assertNotNull(r1.attackerShipStates());
        assertNotNull(r1.defenderShipStates());
        assertNotNull(r1.activeProjectiles());
        assertNotNull(r1.eventLogs());

        // Verify telemetry positions & projectile types
        assertFalse(r1.attackerShipStates().isEmpty());
        assertEquals(180.0, r1.attackerShipStates().get(0).posX(), 0.1);
        assertEquals(640.0, r1.defenderShipStates().get(0).posX(), 0.1);

        assertTrue(r1.activeProjectiles().stream().anyMatch(p -> p.weaponType().equals("LASER") || p.weaponType().equals("TORPEDO") || p.weaponType().equals("MASS_DRIVER")));
    }

    @Test
    void testShieldTargetingPenetration() {
        TacticalCombatProcessor.CombatEngagementResult shieldTargetResult = processor.resolveFleetEngagementWithCarrierWings(
                attackerFleet, defenderFleet, designs, List.of(), List.of(), null, TacticalCombatProcessor.TARGET_SUBSYSTEM_SHIELDS
        );

        TacticalCombatProcessor.CombatEngagementResult allTargetResult = processor.resolveFleetEngagementWithCarrierWings(
                attackerFleet, defenderFleet, designs, List.of(), List.of(), null, TacticalCombatProcessor.TARGET_SUBSYSTEM_ALL
        );

        assertNotNull(shieldTargetResult);
        assertNotNull(allTargetResult);
    }
}
