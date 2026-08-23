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

class TacticalCombatCarrierTest {

    private TacticalCombatProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TacticalCombatProcessor(new Random(42));
    }

    @Test
    void testCarrierStrikeWingsEngageHostileFleet() {
        ShipInstance carrierShip = new ShipInstance(
                "carrier_1", "design_carrier", "terran_confederation",
                1000.0, 500.0, 5000.0, Map.of()
        );
        Fleet attacker = new Fleet(
                "fleet_att", "Strike Group Alpha", "terran_confederation",
                "sol", "sol", 0.0, 0.0, 0.0, false,
                TacticalCombatProcessor.STANCE_AGGRESSIVE_BRAWL, List.of(carrierShip)
        );

        ShipInstance targetShip = new ShipInstance(
                "frigate_1", "design_frigate", "centauri_dominion",
                500.0, 200.0, 2000.0, Map.of()
        );
        Fleet defender = new Fleet(
                "fleet_def", "Centauri Patrol", "centauri_dominion",
                "sol", "sol", 0.0, 0.0, 0.0, false,
                TacticalCombatProcessor.STANCE_STANDOFF_KITE, List.of(targetShip)
        );

        ShipDesign carrierDesign = new ShipDesign(
                "design_carrier", "Fleet Carrier", "terran_confederation",
                "CARRIER_SHIP", "steel", List.of(), "steel", 10.0,
                150000.0, 50000.0, 1000.0, 1500.0, 50000.0, 100000.0, true, false
        );
        ShipDesign frigateDesign = new ShipDesign(
                "design_frigate", "Combat Frigate", "centauri_dominion",
                "COMBAT_SHIP", "steel", List.of(), "steel", 5.0,
                40000.0, 5000.0, 500.0, 1200.0, 20000.0, 60000.0, true, false
        );

        CarrierWing bombers = new CarrierWing(
                "wing_bomber_1", "carrier_1", CarrierWing.TYPE_TORPEDO_BOMBER,
                12, 12, 50.0, 25.0, CarrierWing.TARGET_CAPITAL_SHIPS
        );

        TacticalCombatProcessor.CombatEngagementResult result = processor.resolveFleetEngagementWithCarrierWings(
                attacker, defender, List.of(carrierDesign, frigateDesign),
                List.of(bombers), List.of(), null, TacticalCombatProcessor.TARGET_SUBSYSTEM_SHIELDS
        );

        assertNotNull(result);
        assertFalse(result.roundReports().isEmpty());
        assertTrue(result.roundReports().get(0).detailedActions().stream()
                .anyMatch(a -> a.phase().equals("STRIKE_WING")));
    }

    @Test
    void testPlanetaryDefenseBatteryFiresIntoOrbit() {
        ShipInstance invaderShip = new ShipInstance(
                "transport_1", "design_transport", "centauri_dominion",
                400.0, 100.0, 2000.0, Map.of()
        );
        Fleet attacker = new Fleet(
                "fleet_att", "Invasion Force", "centauri_dominion",
                "sol", "sol", 0.0, 0.0, 0.0, false,
                TacticalCombatProcessor.STANCE_AGGRESSIVE_BRAWL, List.of(invaderShip)
        );

        ShipInstance defenderShip = new ShipInstance(
                "corvette_1", "design_corvette", "terran_confederation",
                300.0, 150.0, 1500.0, Map.of()
        );
        Fleet defender = new Fleet(
                "fleet_def", "Home Guard", "terran_confederation",
                "sol", "sol", 0.0, 0.0, 0.0, false,
                TacticalCombatProcessor.STANCE_POINT_DEFENSE_SCREEN, List.of(defenderShip)
        );

        PlanetaryDefenseBattery battery = new PlanetaryDefenseBattery(
                "battery_earth_1", "earth", 150.0, 2, true
        );

        ShipDesign transDesign = new ShipDesign(
                "design_transport", "Troop Transport", "centauri_dominion",
                "TROOP_TRANSPORT", "steel", List.of(), "steel", 3.0,
                30000.0, 20000.0, 300.0, 1000.0, 15000.0, 40000.0, true, false
        );
        ShipDesign corvDesign = new ShipDesign(
                "design_corvette", "Corvette", "terran_confederation",
                "ESCORT", "steel", List.of(), "steel", 4.0,
                20000.0, 2000.0, 400.0, 1100.0, 10000.0, 35000.0, true, false
        );

        TacticalCombatProcessor.CombatEngagementResult result = processor.resolveFleetEngagementWithCarrierWings(
                attacker, defender, List.of(transDesign, corvDesign),
                List.of(), List.of(), battery, TacticalCombatProcessor.TARGET_SUBSYSTEM_ALL
        );

        assertNotNull(result);
        assertTrue(result.roundReports().get(0).detailedActions().stream()
                .anyMatch(a -> a.phase().equals("SURFACE_BATTERY")));
    }
}
