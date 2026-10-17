package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.macrostructure.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class OrbitalRendezvousTest {
    GameState world() throws Exception {
        var research = List.of("electricity", "industrial_production", "ship_cannons", "nuclear_fission", "nuclear_fusion", "energy_fields", "scanners");
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(), research, List.of());
        var enemy = new Empire("enemy", "Enemy", "human", "Individualist", 1e6, 0, List.of(), List.of(), Map.of(), research, List.of());
        var sol = DataModelLoader.loadSolarSystems().stream().filter(system -> system.id().equals("sol")).findFirst().orElseThrow();
        var earth = sol.planets().stream().filter(planet -> planet.id().equals("earth")).findFirst().orElseThrow();
        var system = new SolarSystem(sol.id(), sol.name(), sol.description(), 0, 0, 0, sol.sunMass(), sol.sunDiameter(), sol.sunColor(), List.of(earth), List.of());
        var state = GameState.builder().empires(List.of(owner, enemy)).solarSystems(List.of(system))
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
        var yards = new ArrayList<OrbitalStation>();
        for (String id : List.of("owner", "enemy")) yards.add(new OrbitalStation(id + "-yard", id, "sol", "earth", id,
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 20, List.of(new StationModule(id + "-slipway", "Slipway",
                StationModule.TYPE_CAPITAL_SLIPWAY, 4, 10000, 0, 0, Map.of(), "engineer", 0, true)), Map.of(),
                100, 0, 0, 0, 100, 100, "steel", 0, true));
        state = state.toBuilder().orbitalStations(yards).build();
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_fusion_drive", false, ShipComponentCatalog.FISSION_REACTOR_ID));
        modules.add(SensorModuleCatalog.ADVANCED);
        modules.add(CombatModuleCatalog.GUN);
        var designs = new ArrayList<ShipDesign>(); var fleets = new ArrayList<Fleet>();
        for (String id : List.of("owner", "enemy")) {
            var blueprint = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification(id + "-design", id, id,
                    ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5));
            assertTrue(blueprint.valid(), blueprint.errors().toString()); designs.add(blueprint.design());
            var ship = new ShipInstance(id + "-ship", blueprint.design().id(), id, 100, 0, blueprint.design().fuelCapacityKg(),
                    Map.of("fusion_fuel_pellets", 200.0, "cannon_round", 100.0)).withPowerState(new ShipPowerState(Map.of("refined_uranium", 5.0),
                    "rp1", "uranium", 500, false, 1, 1, 0, 0, 0, 0, 0));
            fleets.add(new Fleet(id.equals("owner") ? "hunter" : "target", id, id, "sol", "", 0, 0, 0, false,
                    "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.deepSpace())));
        }
        state = state.withShipDesigns(designs).withFleets(fleets);
        var start = SensorPosition.at(state, "sol", fleets.getFirst().location());
        var coast = new HohmannCoast(CircularOrbitalEphemeris.gravitationalParameter(system), norm(start) + 1e6,
                norm(start) * 1.2, Math.atan2(start.yMeters(), start.xMeters()));
        var itinerary = new OrbitalFlight.Itinerary("earth", "earth", 500, 1000, 0, 0, coast, ShipSolarEnvironment.DARK,
                List.of(new OrbitalFlight.Maneuver(0, 1, Map.of()), new OrbitalFlight.Maneuver(coast.coastSeconds(), 1, Map.of()),
                        new OrbitalFlight.Maneuver(coast.coastSeconds() + 3600, 1, Map.of())),
                new OrbitalFlight.Frame("", OrbitalFlight.CenterKind.STELLAR));
        var target = fleets.getLast().withLocation(new FleetLocation(FleetLocation.Site.orbit("earth", 500),
                FleetLocation.Site.orbit("earth", 1000), 0, itinerary.totalSeconds() / 86400, null,
                new OrbitalFlight(itinerary, 0, 1, OrbitalFlight.Status.COAST, "")));
        return state.withFleets(List.of(fleets.getFirst(), target));
    }
    private Fleet hunter(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private Fleet target(GameState state) { return FleetOrganization.find(state, "target"); }
    private GameState assigned(GameState state) { return new InterceptFleetCommand("owner", "hunter", "target").apply(state); }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private TacticalFleetEncounterResolver resolver() { return new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42))); }

    @Test void fundedCurvedCoastMatchesObservedOrbitAfterRealPowerAndFuelTick() throws Exception {
        var initial = assigned(world()); var plan = OrbitalRendezvousPlanner.plan(initial, hunter(initial), target(initial));
        assertNotNull(plan); assertNotNull(plan.physical().vectorTrajectory().gravity());
        assertEquals(1, plan.days());
        var expected = LocalTravel.arrivalPreview(initial, hunter(initial), target(initial).location().destination(), plan);
        var launched = FleetInterceptProcessor.advance(initial);
        assertTrue(resolver().resolveEncounters(launched).fleetEngagements().isEmpty());
        var state = day(launched);
        assertTrue(MovingFleetRendezvous.matches(state, hunter(state), target(state)));
        assertEquals(expected.ships().getFirst().currentFuelKg(), hunter(state).ships().getFirst().currentFuelKg(), .0001);
        assertTrue(hunter(state).ships().getFirst().currentFuelKg() < hunter(initial).ships().getFirst().currentFuelKg());
        var battle = resolver().resolveEncounters(state);
        assertEquals(1, battle.fleetEngagements().size());
        assertEquals("RENDEZVOUS", battle.fleetEngagements().getFirst().siteKind());
    }

    @Test void gravityPropagationAgreesWithAnalyticEllipseAndDiffersFromLinearDrift() throws Exception {
        var state = world(); var coast = target(state).location().orbitalFlight().itinerary().coast(); var initial = coast.at(0);
        var start = new LocalSiteGeometry.Point(initial.xMeters(), initial.yMeters(), 0);
        var velocity = new LocalSiteGeometry.Point(initial.vxMps(), initial.vyMps(), 0);
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var trajectory = new VectorFlight.Trajectory(start, velocity, tiny, tiny, 21600, 43200,
                new TwoBodyFlight.Gravity(coast.gravitationalParameter(), .1 * ShipSolarEnvironment.AU_KM * 1000));
        var actual = trajectory.at(86400); var expected = coast.at(86400);
        assertTrue(norm(subtract(actual.position(), new LocalSiteGeometry.Point(expected.xMeters(), expected.yMeters(), 0))) < .1);
        assertTrue(norm(subtract(actual.velocity(), new LocalSiteGeometry.Point(expected.vxMps(), expected.vyMps(), 0))) < .0001);
        assertTrue(norm(subtract(actual.position(), add(start, scale(velocity, 86400)))) > 1e6);
    }

    @Test void cancellationAndReloadPreserveGravityWithoutPreventingInRangeFire() throws Exception {
        var state = FleetInterceptProcessor.advance(assigned(world()));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class)
                .toGameState(0, "RUNNING");
        assertEquals(hunter(state), hunter(restored));
        var canceled = new CancelInterceptFleetCommand("owner", "hunter").apply(restored);
        assertEquals(hunter(restored).location(), hunter(canceled).location());
        var advanced = day(canceled);
        assertFalse(MovingFleetRendezvous.matches(advanced, hunter(advanced), target(advanced)));
        assertTrue(ShipWeaponRange.reaches(advanced, hunter(advanced), target(advanced)));
        assertEquals("WEAPON_RANGE", resolver().resolveEncounters(advanced).fleetEngagements().getFirst().siteKind());
        var drift = FleetInterceptProcessor.finishContacts(advanced);
        var before = hunter(drift).location().localFlight().vectorFlight();
        var after = day(drift); var actual = hunter(after).location().localFlight().vectorFlight();
        assertNotNull(actual.trajectory().gravity());
        assertTrue(norm(subtract(actual.position(), add(before.position(), scale(before.velocity(), 86400)))) > 1e6);
        assertFalse(new RecoverFleetTravelCommand("hunter").validate(drift));
    }

    @Test void emptyMainTankWaitsWithoutAddingFuelAndPowerFailureCannotEngage() throws Exception {
        var initial = assigned(world()); var ship = hunter(initial).ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, ship.storedCargoKg())
                .withPowerState(ship.powerState());
        var state = initial.withFleets(List.of(hunter(initial).withShips(List.of(empty)), target(initial)));
        assertEquals(hunter(state), hunter(FleetInterceptProcessor.advance(state)));
        var launched = FleetInterceptProcessor.advance(initial);
        var cold = hunter(launched).ships().getFirst().withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var failed = day(launched.withFleets(List.of(hunter(launched).withShips(List.of(cold)), target(launched))));
        assertTrue(hunter(failed).location().localFlight().interrupted());
        assertNotNull(hunter(failed).location().localFlight().vectorFlight().trajectory().gravity());
        assertTrue(resolver().resolveEncounters(failed).fleetEngagements().isEmpty());
    }

    @Test void interiorGravityRemainsFiniteWithoutIntroducingCollisionDamage() throws Exception {
        var state = FleetInterceptProcessor.advance(assigned(world())); var fleet = hunter(state);
        var flight = fleet.location().localFlight(); var trajectory = flight.vectorFlight().trajectory();
        var interior = new VectorFlight(trajectory, new LocalSiteGeometry.Point(trajectory.gravity().minimumRadiusMeters() / 2, 0, 0),
                new LocalSiteGeometry.Point(0, 0, 0), 0);
        var drifting = fleet.withInterceptOrder(null).withLocation(fleet.location().withFlight(new LocalFlight(flight.geometry(),
                VectorFlight.motion(interior, flight.geometry()), flight.propellantKg(), flight.propulsion(), true, false, null, interior)));
        var advanced = LocalFlightProcessor.advance(drifting, 24, false);
        assertEquals(drifting.ships(), advanced.ships());
        assertTrue(Double.isFinite(norm(advanced.location().localFlight().vectorFlight().position())));
        assertTrue(Double.isFinite(norm(advanced.location().localFlight().vectorFlight().velocity())));
        assertThrows(IllegalArgumentException.class, () -> TwoBodyFlight.predict(interior, 86400));
    }
}
