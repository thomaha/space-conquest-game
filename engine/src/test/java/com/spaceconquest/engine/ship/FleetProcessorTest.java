package com.spaceconquest.engine.ship;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FleetProcessorTest {

    private FleetProcessor fleetProcessor;

    @BeforeEach
    public void setUp() {
        fleetProcessor = new FleetProcessor();
    }

    @Test
    public void testFleetEntersAndCompletesWarpTransit() {
        ShipInstance ship = new ShipInstance(
                "ship_1", "design_scout", "emp_terran",
                500.0, 200.0, 50.0, Map.of()
        );

        Fleet initialFleet = new Fleet(
                "fleet_1", "1st Recon Squadron", "emp_terran",
                "sol", "alpha_centauri", 0.0, 0.0, 0.0, false, "PASSIVE", List.of(ship)
        );

        // Turn 1: Enters warp and moves 0.25 progress without burning sublight propellant.
        List<Fleet> step1 = fleetProcessor.processFleetMovements(List.of(initialFleet), null, null);
        Fleet f1 = step1.getFirst();
        assertTrue(f1.isInWarp(), "Fleet should enter warp transit");
        assertEquals(0.25, f1.transitProgress(), 0.001);
        assertEquals(50.0, f1.ships().getFirst().currentFuelKg(), 0.001);

        // Turn 2: Advances to 0.50
        List<Fleet> step2 = fleetProcessor.processFleetMovements(step1, null, null);
        Fleet f2 = step2.getFirst();
        assertEquals(0.50, f2.transitProgress(), 0.001);

        // Turn 3: Advances to 0.75
        List<Fleet> step3 = fleetProcessor.processFleetMovements(step2, null, null);
        Fleet f3 = step3.getFirst();
        assertEquals(0.75, f3.transitProgress(), 0.001);

        // Turn 4: Reaches 1.0 -> Arrives at alpha_centauri and exits warp
        List<Fleet> step4 = fleetProcessor.processFleetMovements(step3, null, null);
        Fleet f4 = step4.getFirst();
        assertFalse(f4.isInWarp(), "Fleet should exit warp upon arrival");
        assertEquals("alpha_centauri", f4.currentSystemId());
        assertEquals("", f4.targetSystemId());
        assertEquals(0.0, f4.transitProgress(), 0.001);
    }

    @Test
    public void patrolStanceDoesNotInventMovementWithoutAnOrder() {
        Fleet patrolFleet = new Fleet(
                "fleet_patrol", "Patrol Wing", "emp_terran",
                "sol", "", 10.0, 20.0, 0.0, false, "PATROL", List.of()
        );

        List<Fleet> updated = fleetProcessor.processFleetMovements(List.of(patrolFleet), null, null);
        Fleet f = updated.getFirst();
        assertEquals(10.0, f.coordinateX(), 0.001);
        assertEquals(20.0, f.coordinateY(), 0.001);
        assertEquals(patrolFleet.location(), f.location());
    }

    @Test
    void sublightCrossingKeepsItsModeProgressAndFuelUntilArrival() {
        ShipInstance ship = new ShipInstance("ship", "design", "emp", 100, 0,
                25, Map.of());
        Fleet ordered = new Fleet("fleet", "Slow ship", "emp", "sol", "alpha",
                0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_SUBLIGHT, 3.0);
        Fleet first = fleetProcessor.processFleetMovements(List.of(ordered), null, null).getFirst();
        assertFalse(first.isInWarp());
        assertTrue(first.isInterstellarTransit());
        assertEquals(1.0 / 3.0, first.transitProgress(), 0.000001);
        assertEquals(25.0, first.ships().getFirst().currentFuelKg(), 0.000001);
        Fleet second = fleetProcessor.processFleetMovements(List.of(first), null, null).getFirst();
        assertEquals(2.0 / 3.0, second.transitProgress(), 0.000001);
        Fleet arrived = fleetProcessor.processFleetMovements(List.of(second), null, null).getFirst();
        assertEquals("alpha", arrived.currentSystemId());
        assertFalse(arrived.hasInterstellarOrder());
        assertEquals(0.0, arrived.interstellarTravelDays(), 0.000001);
    }

    @Test
    void interstellarOrderLeavesOrbitBeforeWarpAndArrivesInDestinationSpace() {
        Fleet ordered = new Fleet("fleet", "Courier", "emp", "sol", "alpha",
                0, 0, 0, false, "PASSIVE", List.of(),
                FleetLocation.at(FleetLocation.Site.orbit("earth")).depart(
                        FleetLocation.Site.deepSpace(), 2.0));
        Fleet first = fleetProcessor.processFleetMovements(List.of(ordered), null, null).getFirst();
        assertTrue(first.location().inTransit());
        assertEquals(FleetLocation.Site.deepSpace(), first.location().destination());
        assertEquals(0.5, first.location().progress(), 0.001);
        Fleet second = fleetProcessor.processFleetMovements(List.of(first), null, null).getFirst();
        assertTrue(second.location().isAt(FleetLocation.Site.deepSpace()));
        Fleet third = fleetProcessor.processFleetMovements(List.of(second), null, null).getFirst();
        assertTrue(third.isInWarp());
        assertEquals(0.25, third.transitProgress(), 0.001);
        Fleet current = third;
        for (int day = 0; day < 3; day++)
            current = fleetProcessor.processFleetMovements(List.of(current), null, null).getFirst();
        assertEquals("alpha", current.currentSystemId());
        assertTrue(current.location().isAt(FleetLocation.Site.deepSpace()));
        assertFalse(current.isInWarp());
    }

    @Test
    public void testFleetScannerRange() {
        ShipDesign explorerDesign = new ShipDesign(
                "design_explorer", "Pathfinder Explorer", "emp_terran",
                ShipRole.EXPLORER, "steel", List.of(), "steel", 1.0,
                5000.0, 1000.0, 100.0, 1.5, 50000.0, 200000.0, true, false
        );

        ShipDesign combatDesign = new ShipDesign(
                "design_combat", "Aegis Destroyer", "emp_terran",
                ShipRole.COMBAT_SHIP, "steel", List.of(), "steel", 2.0,
                15000.0, 2000.0, 300.0, 1.2, 150000.0, 600000.0, true, false
        );

        ShipInstance explorerShip = new ShipInstance("s1", "design_explorer", "emp_terran", 100.0, 100.0, 50.0, Map.of());
        ShipInstance combatShip = new ShipInstance("s2", "design_combat", "emp_terran", 500.0, 500.0, 100.0, Map.of());

        Fleet explorerFleet = new Fleet("f_exp", "Explorer Fleet", "emp_terran", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(explorerShip));
        Fleet combatFleet = new Fleet("f_com", "Combat Fleet", "emp_terran", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(combatShip));
        Fleet emptyFleet = new Fleet("f_emp", "Empty Fleet", "emp_terran", "sol", "", 0, 0, 0, false, "PASSIVE", List.of());

        double explorerRange = fleetProcessor.calculateFleetScannerRange(explorerFleet, List.of(explorerDesign, combatDesign));
        double combatRange = fleetProcessor.calculateFleetScannerRange(combatFleet, List.of(explorerDesign, combatDesign));
        double emptyRange = fleetProcessor.calculateFleetScannerRange(emptyFleet, List.of(explorerDesign, combatDesign));

        assertEquals(25.0, explorerRange, 0.001);
        assertEquals(15.0, combatRange, 0.001);
        assertEquals(5.0, emptyRange, 0.001);
    }
}
