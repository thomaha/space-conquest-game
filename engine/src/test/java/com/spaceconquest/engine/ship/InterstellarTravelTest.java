package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InterstellarTravelTest {
    @Test
    void accelerationAndBrakingMakeProgressNonlinear() {
        double distance = InterstellarTravel.METERS_PER_LIGHT_YEAR;
        double seconds = InterstellarTravel.travelSeconds(distance, 1.0);
        assertEquals(0.125, InterstellarTravel.progress(distance, 1.0, seconds / 4.0), 0.001);
        assertEquals(0.5, InterstellarTravel.progress(distance, 1.0, seconds / 2.0), 0.001);
        assertEquals(0.875, InterstellarTravel.progress(distance, 1.0, seconds * 0.75), 0.001);
        assertEquals(1.0, InterstellarTravel.progress(distance, 1.0, seconds), 0.000001);
        assertTrue(InterstellarTravel.velocityMps(distance, 1.0, seconds / 4.0)
                < InterstellarTravel.velocityMps(distance, 1.0, seconds / 2.0));
        assertEquals(InterstellarTravel.velocityMps(distance, 1.0, seconds / 4.0),
                InterstellarTravel.velocityMps(distance, 1.0, seconds * 0.75), 0.001);
    }

    @Test
    void cargoMassSlowsShipAndSlowestShipSetsFleetTravelTime() {
        ShipDesign fast = design("fast", 10_000, 500_000);
        ShipDesign slow = design("slow", 20_000, 500_000);
        ShipDesign dead = design("dead", 10_000, 0);
        ShipInstance empty = new ShipInstance("empty", fast.id(), "owner", 100, 0,
                100, Map.of());
        ShipInstance loaded = new ShipInstance("loaded", slow.id(), "owner", 100, 0,
                100, Map.of("steel", 5_000.0));
        GameState state = GameState.builder().solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "Yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 1, 0, 0, 1, 1, "Yellow", List.of(), List.of())))
                .shipDesigns(List.of(fast, slow, dead)).build();
        Fleet lightFleet = fleet(List.of(empty));
        Fleet heavyFleet = fleet(List.of(loaded));
        InterstellarTravel.Plan light = InterstellarTravel.plan(state, lightFleet, "b");
        InterstellarTravel.Plan heavy = InterstellarTravel.plan(state, heavyFleet, "b");
        InterstellarTravel.Plan mixed = InterstellarTravel.plan(state,
                fleet(List.of(empty, loaded)), "b");
        assertEquals(500_000.0 / 10_100.0, light.accelerationMps2(), 0.000001);
        assertEquals(500_000.0 / 25_100.0, heavy.accelerationMps2(), 0.000001);
        assertTrue(heavy.days() > light.days());
        assertEquals(heavy.accelerationMps2(), mixed.accelerationMps2(), 0.000001);
        assertEquals(heavy.days(), mixed.days(), 0.000001);
        Fleet ordered = new Fleet("fleet", "Fleet", "owner", "a", "b",
                0, 0, 0, false, "PASSIVE", List.of(empty),
                FleetLocation.at(FleetLocation.Site.deepSpace()), light.mode(), light.days(),
                light.distanceMeters(), light.accelerationMps2(), 0.0);
        Fleet afterDay = new FleetProcessor().processFleetMovements(List.of(ordered),
                List.of(), List.of()).getFirst();
        assertTrue(afterDay.transitProgress() > 0.0);
        assertTrue(afterDay.transitProgress() < 1.0 / light.days());
        assertEquals(1.0, afterDay.interstellarElapsedDays(), 0.001);
        assertNull(InterstellarTravel.plan(state, fleet(List.of(new ShipInstance("broken",
                dead.id(), "owner", 100, 0, 100,
                Map.of()))), "b"));
    }

    @Test
    void documentedDriveLimitsCruiseSpeedAndReservesFuelForBraking() {
        ShipDesign chemical = new ShipDesign("chemical", "Chemical", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_chemical_rocket"),
                "steel", 0, 10_000, 30_000, 0, 1, 0, 500_000, true, false);
        ShipInstance ship = new ShipInstance("ship", chemical.id(), "owner", 100, 0,
                100, Map.of());
        GameState state = GameState.builder().solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "Yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 0.001, 0, 0, 1, 1, "Yellow", List.of(), List.of())))
                .shipDesigns(List.of(chemical)).build();
        InterstellarTravel.Plan plan = InterstellarTravel.plan(state, fleet(List.of(ship)), "b");
        assertNotNull(plan);
        assertTrue(plan.peakSpeedMps() < 100.0);
        assertTrue(plan.days() > 1_000.0);
        assertTrue(plan.fuelBudgetKg().get(ship.id()) <= ship.currentFuelKg());
        assertEquals(0.5, InterstellarTravel.fuelBurnFraction(plan.distanceMeters(),
                plan.accelerationMps2(), plan.peakSpeedMps(),
                InterstellarTravel.travelSeconds(plan.distanceMeters(), plan.accelerationMps2(),
                        plan.peakSpeedMps()) / 2.0), 0.000001);

        Fleet ordered = new Fleet("fleet", "Fleet", "owner", "a", "b", 0, 0, 0,
                false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.deepSpace()),
                plan.mode(), plan.days(), plan.distanceMeters(), plan.accelerationMps2(),
                0.0, plan.peakSpeedMps(), plan.fuelBudgetKg());
        Fleet afterDay = new FleetProcessor().processFleetMovements(List.of(ordered),
                List.of(), List.of()).getFirst();
        assertTrue(afterDay.ships().getFirst().currentFuelKg() < ship.currentFuelKg());
        assertTrue(afterDay.ships().getFirst().currentFuelKg() > 0.0);
    }

    @Test
    void fissionNeedsRadioactiveFuelAndFusionIsotopesImproveRange() {
        ShipDesign fission = new ShipDesign("fission", "Fission", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_fission_thruster"),
                "steel", 0, 10_000, 30_000, 0, 1, 0, 500_000, true, false);
        ShipDesign fusion = new ShipDesign("fusion", "Fusion", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_fusion_drive"),
                "steel", 0, 10_000, 30_000, 0, 1, 0, 500_000, true, false);
        GameState state = GameState.builder().solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "Yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 0.001, 0, 0, 1, 1, "Yellow", List.of(), List.of())))
                .shipDesigns(List.of(fission, fusion)).build();
        ShipInstance emptyReactor = new ShipInstance("ship", fission.id(), "owner",
                100, 0, 100, Map.of());
        assertNull(InterstellarTravel.plan(state, fleet(List.of(emptyReactor)), "b"));
        ShipInstance uranium = new ShipInstance("ship", fission.id(), "owner",
                100, 0, 100, Map.of("refined_uranium", 0.1));
        InterstellarTravel.Plan fissionPlan = InterstellarTravel.plan(state,
                fleet(List.of(uranium)), "b");
        assertNotNull(fissionPlan);
        assertEquals("refined_uranium", fissionPlan.reactorFuelBudgetKg()
                .get("ship").materialId());
        assertTrue(InterstellarTravel.commitReactorFuel(fleet(List.of(uranium)), fissionPlan)
                .ships().getFirst().storedCargoKg().getOrDefault("refined_uranium", 0.0) < 0.1);
        ShipInstance hydrogen = new ShipInstance("ship", fusion.id(), "owner",
                100, 0, 100, Map.of());
        ShipInstance deuterium = new ShipInstance("ship", fusion.id(), "owner",
                100, 0, 100, Map.of("deuterium_gas", 2.0));
        ShipInstance pellets = new ShipInstance("ship", fusion.id(), "owner",
                100, 0, 100, Map.of("fusion_fuel_pellets", 1.0));
        double hydrogenDays = InterstellarTravel.plan(state, fleet(List.of(hydrogen)), "b").days();
        double deuteriumDays = InterstellarTravel.plan(state, fleet(List.of(deuterium)), "b").days();
        double pelletDays = InterstellarTravel.plan(state, fleet(List.of(pellets)), "b").days();
        assertTrue(hydrogenDays > deuteriumDays);
        assertTrue(deuteriumDays > pelletDays);
    }

    private ShipDesign design(String id, double mass, double thrust) {
        return new ShipDesign(id, id, "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of(), "steel", 0, mass, 30_000, 0, 1, 0, thrust, true, false);
    }

    private Fleet fleet(List<ShipInstance> ships) {
        return new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0,
                false, "PASSIVE", ships);
    }
}
