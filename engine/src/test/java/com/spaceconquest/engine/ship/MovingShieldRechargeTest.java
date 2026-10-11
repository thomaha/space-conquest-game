package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MovingShieldRechargeTest {
    private ShipDesign design(double reactor, double drive) {
        var p = new ShipPowerProfile(0, 0, reactor, 5000, 100, 100, 0, 10, 32, drive, 0, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6_000_000)));
        return design(p);
    }
    private ShipDesign design(ShipPowerProfile p) {
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(CombatModuleCatalog.SHIELD),
                "steel", 0, 1000, 1000, 1000, 100, 1, 0, 1000, false, false, ShipManufacturingProfile.baseline(), p);
    }
    private ShipInstance ship(double fuel, double battery, double shield) {
        return new ShipInstance("ship", "design", "owner", 1000, shield, 100, Map.of())
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", fuel), "rp1", "uranium", battery, true, 1, 1, 0, 0, 0, 0, 0));
    }
    private Fleet fleet(ShipInstance ship) {
        return new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
    }
    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }
    private GameState world(ShipDesign design, Fleet fleet, boolean detected) {
        var sensor = new ShipDesign("sensor", "Scout", "owner", ShipRole.EXPLORER, "steel", List.of(SensorModuleCatalog.BASIC),
                "steel", 0, 1000, 1000, 1000, 100, 1, 0, 1000, false, false);
        var enemy = new Fleet("enemy", "Enemy", "enemy", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("enemy-ship", "design", "enemy", 1000, 0, 0, Map.of())));
        var scout = new Fleet("scout", "Scout", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("scout-ship", "sensor", "owner", 1000, 0, 0, Map.of())));
        return GameState.builder().shipDesigns(List.of(design, sensor)).empires(List.of(empire("owner"), empire("enemy")))
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)))
                .fleets(detected ? List.of(fleet, enemy, scout) : List.of(fleet, enemy)).build();
    }

    @Test void alwaysChargeOverridesQuietTravelButConserveOverridesDetectedDanger() {
        var design = design(100, 20); var original = ship(1, 0, 7);
        var moving = fleet(original).withLocation(fleet(original).location().depart(FleetLocation.Site.orbit("earth"), 2));
        moving = moving.withShieldPolicy(FleetShieldPolicy.Mode.ALWAYS_CHARGE);
        var quiet = world(design, moving, false);
        var charged = ShipPowerProcessor.advanceDay(quiet).getFirst();
        assertEquals(200, charged.ships().getFirst().currentShieldHealth(), 1e-9);
        assertEquals(1 - (52 * 24 + 193 * 2) / 6_000_000.0, charged.ships().getFirst().generatorFuelMassKg(), 1e-12);
        assertEquals(FleetShieldPolicy.Mode.ALWAYS_CHARGE, charged.shieldPolicy());
        var conserving = moving.withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
        var danger = world(design, conserving, true);
        assertFalse(FleetShieldPolicy.recharge(danger, conserving, true));
        assertEquals(7, ShipPowerProcessor.advanceDay(danger).getFirst().ships().getFirst().currentShieldHealth());
        var dry = moving.withShips(List.of(ship(0, 0, 7)));
        assertEquals(7, ShipPowerProcessor.advanceDay(world(design, dry, false)).getFirst().ships().getFirst().currentShieldHealth());
    }

    @Test void travelDefersRechargeUntilSharedSensorsDetectAHostileAndKeepsExistingCharge() {
        var design = design(100, 20); var original = ship(1, 0, 7);
        var moving = fleet(original).withLocation(fleet(original).location().depart(FleetLocation.Site.orbit("earth"), 2));
        var hidden = world(design, moving, false);
        assertFalse(FleetShieldPolicy.recharge(hidden, moving, true));
        var saved = ShipPowerProcessor.advanceDay(hidden).getFirst().ships().getFirst();
        assertEquals(7, saved.currentShieldHealth());
        assertEquals(1 - 52 * 24 / 6_000_000.0, saved.generatorFuelMassKg(), 1e-12);
        var detected = world(design, moving, true);
        assertTrue(FleetShieldPolicy.recharge(detected, moving, true), "A different friendly scout contributes to the common view.");
        var charged = ShipPowerProcessor.advanceDay(detected).getFirst().ships().getFirst();
        assertEquals(200, charged.currentShieldHealth(), 1e-9);
        assertEquals(1 - (52 * 24 + 193 * 2) / 6_000_000.0, charged.generatorFuelMassKg(), 1e-12);
        var peace = detected.withDiplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "NEUTRAL", 0)));
        assertFalse(FleetShieldPolicy.recharge(peace, moving, true));
        assertEquals(7, ShipPowerProcessor.advanceDay(peace).getFirst().ships().getFirst().currentShieldHealth());
    }

    @Test void travelPreviewMatchesTicksWithAndWithoutDetectedThreats() {
        var design = design(100, 20); var initial = fleet(ship(1, 0, 0));
        var destination = FleetLocation.Site.orbit("earth"); var plan = new LocalTravel.Plan(2, Map.of(), Map.of());
        for (boolean detected : List.of(false, true)) {
            var state = world(design, initial, detected);
            var preview = LocalTravel.arrivalPreview(state, initial, destination, plan).ships().getFirst();
            var check = ShipPowerForecast.departure(state, initial, destination, plan, null).getFirst();
            assertTrue(check.ready());
            state = state.withFleets(List.of(initial.withLocation(initial.location().depart(destination, 2)), state.fleets().get(1)));
            if (detected) state = world(design, state.fleets().getFirst(), true);
            for (int day = 0; day < 2; day++) {
                var ships = ShipPowerProcessor.advanceDay(state);
                state = state.withFleets(new FleetProcessor().processFleetMovements(ships, List.of(), List.of()));
            }
            var actual = state.fleets().getFirst().ships().getFirst();
            assertEquals(preview.currentShieldHealth(), actual.currentShieldHealth(), 1e-9);
            assertEquals(preview.generatorFuelMassKg(), actual.generatorFuelMassKg(), 1e-12);
            assertEquals(1 - actual.generatorFuelMassKg(), check.generatorFuelUsedKg(), 1e-12);
        }
    }

    @Test void corporateAndForeignSensorContactsFollowAffiliationAndExplicitAgreements() {
        var design = design(100, 0); var moving = fleet(ship(1, 0, 0));
        moving = moving.withLocation(moving.location().depart(FleetLocation.Site.orbit("earth"), 2));
        var initial = world(design, moving, true); var scout = initial.fleets().getLast();
        var domestic = new Corporation("domestic", "Domestic", "owner", "", "TRANSPORT", 0, List.of(), List.of(), List.of());
        var corporate = new Fleet(scout.id(), scout.name(), "domestic", "sol", "", 0, 0, 0, false, "PASSIVE", scout.ships());
        var state = initial.withCorporations(List.of(domestic)).withFleets(List.of(moving, initial.fleets().get(1), corporate));
        assertTrue(FleetShieldPolicy.recharge(state, moving, true));
        var global = new Corporation("domestic", "Global", "", "", "TRANSPORT", 0, List.of(), List.of(), List.of());
        state = state.withCorporations(List.of(global));
        assertFalse(FleetShieldPolicy.recharge(state, moving, true));
        var grant = new com.spaceconquest.engine.governance.DiplomaticProposal("grant", "domestic", "owner",
                com.spaceconquest.engine.governance.DiplomaticPact.SENSOR_DATA_SHARING, "ACCEPTED");
        state = state.toBuilder().diplomaticProposals(List.of(grant)).build();
        assertTrue(FleetShieldPolicy.recharge(state, moving, true));
        state = state.toBuilder().diplomaticProposals(List.of(grant.withStatus("REVOKED"))).build();
        assertFalse(FleetShieldPolicy.recharge(state, moving, true));
        var corporateFleet = new Fleet(moving.id(), moving.name(), "domestic", "sol", "", 0, 0, 0, false, "PASSIVE",
                moving.ships(), moving.location());
        state = initial.withCorporations(List.of(domestic)).withFleets(List.of(corporateFleet, initial.fleets().get(1), scout));
        assertTrue(FleetShieldPolicy.recharge(state, corporateFleet, true), "Corporate ships use their empire's war and shared contacts.");
    }

    @Test void optionalWorkPreservesAllLaterBurnsAndArrivalElectricity() {
        var design = design(0, 20); var original = ship(0, 3600, 0);
        var phases = List.of(new ShipShieldPower.Phase(12, 20, ShipSolarEnvironment.DARK),
                new ShipShieldPower.Phase(24, 0, ShipSolarEnvironment.DARK), new ShipShieldPower.Phase(12, 20, ShipSolarEnvironment.DARK));
        var budget = ShipShieldPower.forecast(original, design, phases, ShipSolarEnvironment.DARK);
        assertTrue(budget.ready());
        assertTrue(budget.ship().currentShieldHealth() > 23.8 && budget.ship().currentShieldHealth() <= 24.000001);
        assertEquals(3600 - 32 * 48 - 20 * 24 - budget.ship().currentShieldHealth() * 2,
                budget.ship().powerState().batteryChargeKwh(), 1e-9);
        assertTrue(budget.ship().powerState().batteryChargeKwh() >= 32 * 48 - 1e-6);
        assertEquals(0, original.currentShieldHealth()); assertEquals(3600, original.powerState().batteryChargeKwh());
    }

    @Test void solarTravelPreviewMatchesActualEclipseAndBatteryAccounting() {
        var p = new ShipPowerProfile(120, 0, 0, 5000, 100, 100, 0, 0, 32, 20, 0, .65, Map.of());
        var design = design(p); var initial = fleet(ship(0, 1000, 0));
        var earth = new Planet("earth", "Earth", "", 5.972e24, 9.81, ShipSolarEnvironment.AU_KM, 0, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1_392_700, "yellow", List.of(earth), List.of());
        var state = world(design, initial, true).toBuilder().solarSystems(List.of(system)).build();
        var destination = FleetLocation.Site.orbit("earth"); var plan = new LocalTravel.Plan(2, Map.of(), Map.of());
        var expected = LocalTravel.arrivalPreview(state, initial, destination, plan).ships().getFirst();
        state = state.withFleets(List.of(initial.withLocation(initial.location().depart(destination, 2)), state.fleets().get(1), state.fleets().get(2)));
        for (int day = 0; day < 2; day++) state = state.withFleets(new FleetProcessor()
                .processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
        var actual = state.fleets().getFirst().ships().getFirst();
        assertEquals(200, actual.currentShieldHealth(), 1e-9);
        assertEquals(expected.powerState().batteryChargeKwh(), actual.powerState().batteryChargeKwh(), 1e-9);
        assertEquals(0, actual.generatorFuelMassKg()); assertEquals(0, actual.powerState().lastUnmetEssentialKwh());
        assertEquals(0, actual.powerState().unmetDriveKwh());
    }

    @Test void orbitalServicesRechargeWithoutSpendingFutureAuxiliaryBurns() {
        var design = design(0, 20); var original = ship(0, 2464, 0);
        var burn = new OrbitalFlight.Burn(1, 1000, 2, 0, null, 0);
        var events = List.of(new OrbitalFlight.Maneuver(0, 1, Map.of("ship", burn)),
                new OrbitalFlight.Maneuver(43200, 1, Map.of("ship", burn)), new OrbitalFlight.Maneuver(86400, 1, Map.of("ship", burn)));
        var itinerary = new OrbitalFlight.Itinerary("earth", "earth", 100, 200, 0, 0,
                new HohmannCoast(3.986e14, 6.5e6, 6.6e6, 0), ShipSolarEnvironment.DARK, events);
        var location = new FleetLocation(FleetLocation.Site.orbit("earth", 100), FleetLocation.Site.orbit("earth", 200), 0, 1,
                null, new OrbitalFlight(itinerary, 0, 0, OrbitalFlight.Status.WAITING, ""));
        var moving = fleet(original).withLocation(location); var state = world(design, moving, true);
        var expected = OrbitalPowerAccounting.forecast(original, design, itinerary, true);
        var actual = OrbitalFlightProcessor.advanceDay(state, moving).ships().getFirst();
        assertTrue(expected.ready()); assertTrue(actual.currentShieldHealth() > 49.8 && actual.currentShieldHealth() <= 50.000001);
        assertEquals(expected.projectedShip().currentShieldHealth(), actual.currentShieldHealth(), 1e-9);
        assertEquals(expected.state().batteryChargeKwh(), actual.powerState().batteryChargeKwh(), 1e-9);
        assertEquals(expected.projectedShip().currentFuelKg(), actual.currentFuelKg(), 1e-9);
        assertTrue(actual.powerState().batteryChargeKwh() >= 32 * 48 - 1e-6);
    }

    @Test void combatChargesPerElapsedMinuteFromActualStoresAndDamagedEmitters() {
        var design = design(100, 0);
        for (double damage : List.of(0.0, 50.0)) {
            var original = ship(1, 0, 0).withModuleDamage(new ShipModuleDamage(Map.of(0, damage)));
            var attacker = fleet(original); var defender = new Fleet("def", "Defender", "enemy", "sol", "", 0, 0, 0,
                    false, "PASSIVE", List.of(new ShipInstance("other", "legacy", "enemy", 1000, 0, 0, Map.of())));
            var result = new TacticalCombatProcessor(new Random(1)).resolveFleetEngagement(attacker, defender, List.of(design));
            var actual = result.survivingAttackerFleet().ships().getFirst();
            double hours = result.roundReports().size() * TacticalCombatProcessor.SECONDS_PER_ROUND / 3600;
            double charge = 10 * (1 - damage / 100) * hours;
            assertEquals(10, result.roundReports().size()); assertEquals(charge, actual.currentShieldHealth(), 1e-9);
            assertEquals(1 - ((32 - 30 * damage / 100) * hours + charge * 2) / 6_000_000, actual.generatorFuelMassKg(), 1e-12);
            assertEquals(original.moduleDamage(), actual.moduleDamage()); assertEquals(1000, actual.currentHullHealth());
        }
    }

    @Test void timedSupplyPowerPathChargesAcrossBurnAndCoastBoundariesAndRetainsReserve() {
        var design = design(100, 20); var original = ship(1, 0, 0);
        var trajectory = new FlightMotion.Trajectory(0, 0, 1, 3600, 3600, 79200, 3600);
        var moving = new Fleet("fleet", "Fleet", "owner", "sol", "other", 0, 0, 0, false, "PASSIVE", List.of(original),
                FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_RECOVERY, 1, trajectory.at(86400).positionMeters(),
                1, 0, 3600, Map.of(), trajectory.at(0), Map.of());
        var state = world(design, moving, true);
        var result = FleetPropulsionSupply.run(state, moving, 24); var actual = result.fleet().ships().getFirst();
        assertTrue(result.problems().isEmpty()); assertFalse(result.fleet().hasInterstellarOrder());
        assertEquals(200, actual.currentShieldHealth(), 1e-9);
        assertEquals(1 - (32 * 24 + 20 * 2 + 400) / 6_000_000.0, actual.generatorFuelMassKg(), 1e-12);
        assertEquals(32 * 24 + 20 * 2 + 400, result.demandKwh().get("ship"), 1e-9);
        var quiet = FleetPropulsionSupply.run(world(design, moving, false), moving, 24).fleet().ships().getFirst();
        assertEquals(0, quiet.currentShieldHealth());
        assertEquals(1 - (32 * 24 + 20 * 2) / 6_000_000.0, quiet.generatorFuelMassKg(), 1e-12);
    }

    @Test void combatCannotChargeAtTheExpenseOfRequiredReserveOrBeyondCapacity() {
        var design = design(100, 0);
        for (var original : List.of(ship(32 * 48 / 6_000_000.0, 0, 0), ship(1, 0, 200))) {
            var defender = fleet(ship(1, 0, 200));
            var result = new TacticalCombatProcessor(new Random(1)).resolveFleetEngagement(fleet(original), defender, List.of(design));
            var actual = result.survivingAttackerFleet().ships().getFirst();
            assertEquals(original.currentShieldHealth(), actual.currentShieldHealth());
            assertEquals(original.generatorFuelMassKg() - 32.0 * 10 / 60 / 6_000_000,
                    actual.generatorFuelMassKg(), 1e-12);
        }
    }
}
