package com.spaceconquest.control;

import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.MoveFleetLocalCommand;
import com.spaceconquest.control.command.RecoverFleetTravelCommand;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.PopulationProcessor;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.habitation.PassengerManifest;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.FlightMotion;
import com.spaceconquest.engine.ship.LocalTravel;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PassengerJourneyReadinessTest {
    @TempDir Path directory;

    private Map<String, Double> supplies(double days) throws Exception {
        var human = DataModelLoader.loadRaces().stream().filter(race -> race.id().equals("human")).findFirst().orElseThrow();
        var cargo = new HashMap<String, Double>();
        new PopulationProcessor().calculateDailyNutrientRequirements(10, human)
                .forEach((material, kg) -> cargo.put(material, kg * days));
        return Map.copyOf(cargo);
    }

    private GameState world(double days, double thrust, boolean stasis) throws Exception {
        var modules = stasis ? List.of("mod_chemical_rocket", PassengerStasis.MODULE_ID) : List.of("mod_chemical_rocket");
        var design = new ShipDesign("design", "Passenger ship", "owner", ShipRole.CARGO_TRANSPORT,
                "steel", modules, "steel", 0, 1000, 10000, 1000, 0, 1, 0, thrust, false, false);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 1000,
                supplies(days), 10, "human", stasis ? ShipInstance.MODE_CRYOGENIC_STASIS : ShipInstance.MODE_CONSCIOUS);
        var fleet = new Fleet("fleet", "Passengers", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000, 0,
                List.of("a", "b"), List.of(), Map.of(), List.of("warp", PassengerStasis.TECHNOLOGY_ID), List.of());
        var systems = List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "yellow",
                List.of(planet("earth"), planet("mars")), List.of()),
                new SolarSystem("b", "B", "", 1, 0, 0, 1, 1, "yellow", List.of(planet("new_world")), List.of()));
        return GameState.builder().shipDesigns(List.of(design)).empires(List.of(owner)).solarSystems(systems)
                .fleets(List.of(fleet)).passengerManifests(List.of(new PassengerManifest("ship", "earth", "mars",
                        "human", Map.of(30, 10L)))).build();
    }

    private Planet planet(String id) {
        return new Planet(id, id, "", 1, 1, 1, 0, 1000, "terrestrial", "none",
                false, 0, List.of(), List.of(), List.of());
    }

    private GameState stocked(GameState state, double days) throws Exception {
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst();
        var updated = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                ship.currentShieldHealth(), ship.currentFuelKg(), supplies(days), ship.passengerCount(),
                ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
        return state.withFleets(List.of(fleet.withShips(List.of(updated))));
    }

    private GameState tick(GameState state) throws Exception {
        var moving = state.withFleets(new FleetProcessor().processFleetMovements(state.fleets(), List.of(), List.of()));
        return PassengerTransitProcessor.advanceDay(moving, DataModelLoader.loadRaces());
    }

    @Test void longLocalJourneyRequiresAllSpeciesSuppliesAndConsumesExactlyItsScheduledDays() throws Exception {
        var command = new MoveFleetLocalCommand("fleet", FleetLocation.Kind.SURFACE, "mars");
        var state = world(40, .05, false);
        var destination = FleetLocation.Site.surface("mars");
        double days = LocalTravel.plan(state, state.fleets().getFirst(), destination).days();
        assertTrue(days > 2);
        var hungry = stocked(state, days - 1);
        assertFalse(command.validate(hungry));
        assertSame(hungry, command.apply(hungry));
        state = stocked(state, days);
        assertTrue(command.validate(state));
        state = command.apply(state);
        assertEquals(days, state.fleets().getFirst().location().travelDays());
        for (int day = 0; day < days; day++) state = tick(state);
        assertTrue(state.passengerManifests().isEmpty());
        assertEquals(10, state.solarSystems().getFirst().planets().getLast().populations().getFirst().totalCount());
        state.fleets().getFirst().ships().getFirst().storedCargoKg().values().forEach(kg -> assertEquals(0, kg, 1e-8));
    }

    @Test void crossingCountsTheActualSlowLocalDepartureAndDoesNotAssumeFourLocalDays() throws Exception {
        var state = world(60, .005, false);
        var port = new com.spaceconquest.engine.macrostructure.OrbitalStation("port", "Port", "a", "", "owner",
                "PUBLIC_STATE", 10, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
        state = state.toBuilder().orbitalStations(List.of(port)).fleets(List.of(state.fleets().getFirst()
                .withLocation(FleetLocation.at(FleetLocation.Site.docked("port"))))).build();
        var command = new MoveFleetCommand("fleet", "b");
        var preview = command.preview(state, false);
        assertNotNull(preview);
        assertTrue(preview.local().days() > 4);
        assertEquals(Math.ceil(preview.local().days()) + Math.ceil(preview.crossing().days()), preview.totalDays());
        var hungry = stocked(state, 10);
        assertNull(command.preview(hungry, false));
        assertSame(hungry, command.apply(hungry));
        state = stocked(state, preview.totalDays());
        assertTrue(command.validate(state));
        var boardingSupplies = state.fleets().getFirst().ships().getFirst().storedCargoKg();
        state = command.apply(state);
        double scheduledDays = Math.ceil(state.fleets().getFirst().location().travelDays())
                + Math.ceil(state.fleets().getFirst().interstellarTravelDays());
        for (int day = 0; day < scheduledDays; day++) state = tick(state);
        assertEquals("b", state.fleets().getFirst().currentSystemId());
        assertEquals(10, state.passengerManifests().getFirst().headcount());
        var consumed = supplies(scheduledDays);
        var remaining = state.fleets().getFirst().ships().getFirst().storedCargoKg();
        boardingSupplies.forEach((material, kg) -> assertEquals(kg - consumed.get(material), remaining.getOrDefault(material, 0.0), 1e-8));
    }

    @Test void everyBookedFleetMemberMustFundItsOwnSupplies() throws Exception {
        var state = world(60, .05, false);
        var fleet = state.fleets().getFirst();
        var hungry = new ShipInstance("second", "design", "owner", 100, 0, 1000,
                supplies(1), 10, "human", ShipInstance.MODE_CONSCIOUS);
        state = state.toBuilder().fleets(List.of(fleet.withShips(List.of(fleet.ships().getFirst(), hungry))))
                .passengerManifests(List.of(state.passengerManifests().getFirst(),
                        new PassengerManifest("second", "earth", "mars", "human", Map.of(30, 10L)))).build();
        assertFalse(new MoveFleetLocalCommand("fleet", FleetLocation.Kind.SURFACE, "mars").validate(state));
        assertFalse(new MoveFleetCommand("fleet", "b").validate(state));
    }

    @Test void aPausedLongJourneyConsumesSuppliesWhileWaitingAndRecoveryChecksOnlyWhatRemains() throws Exception {
        var state = world(20, .05, false);
        var command = new MoveFleetLocalCommand("fleet", FleetLocation.Kind.SURFACE, "mars");
        state = command.apply(state);
        double paidFuel = state.fleets().getFirst().ships().getFirst().currentFuelKg();
        for (int day = 0; day < 5; day++) state = tick(state);
        state = state.withFleets(List.of(state.fleets().getFirst().withInterruptedTravel()));
        var location = state.fleets().getFirst().location();
        state = tick(state);
        assertEquals(location, state.fleets().getFirst().location());
        var recover = new RecoverFleetTravelCommand("fleet");
        assertFalse(recover.validate(state));
        assertSame(state, recover.apply(state));
        state = stocked(state, 15);
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("passenger-local.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(state.fleets(), loaded.fleets());
        assertEquals(state.passengerManifests(), loaded.passengerManifests());
        assertTrue(recover.validate(loaded));
        state = recover.apply(loaded);
        assertEquals(paidFuel, state.fleets().getFirst().ships().getFirst().currentFuelKg());
        for (int day = 0; day < 15; day++) state = tick(state);
        assertTrue(state.passengerManifests().isEmpty());
        assertEquals(10, state.solarSystems().getFirst().planets().getLast().populations().getFirst().totalCount());
    }

    @Test void validStasisAvoidsNutritionalPurchasesButStillRequiresPodsAndResearch() throws Exception {
        var state = world(0, .05, true);
        var command = new MoveFleetLocalCommand("fleet", FleetLocation.Kind.SURFACE, "mars");
        assertTrue(command.validate(state));
        var noResearch = state.toBuilder().empires(List.of()).build();
        assertFalse(command.validate(noResearch));
        assertSame(noResearch, command.apply(noResearch));
    }

    @Test void warpRecoveryUsesRemainingCrossingDaysWithoutRestartingTheFoodBudget() throws Exception {
        var state = world(9, 2000, false);
        var original = state.fleets().getFirst();
        var paused = new Fleet(original.id(), original.name(), "owner", "a", "b", 0, 0, 2.0 / 12,
                false, "PASSIVE", original.ships(), FleetLocation.at(FleetLocation.Site.deepSpace()),
                Fleet.MODE_POWER_INTERRUPTED, 12, 0, 0, 2, 0, Map.of());
        state = state.withFleets(List.of(paused));
        var command = new RecoverFleetTravelCommand("fleet");
        assertEquals(10, command.pausedPreview(state).remainingDays());
        assertFalse(command.validate(state));
        assertSame(state, command.apply(state));
        state = stocked(state, 10);
        assertTrue(command.validate(state));
        state = command.apply(state);
        assertEquals(2, state.fleets().getFirst().interstellarElapsedDays());
        for (int day = 0; day < 10; day++) state = tick(state);
        assertEquals("b", state.fleets().getFirst().currentSystemId());
        assertEquals(10, state.passengerManifests().getFirst().headcount());
    }

    @Test void physicalRecoveryRequiresSuppliesThroughItsReplacementTrajectory() throws Exception {
        var state = world(0, 2000, false);
        var original = state.fleets().getFirst();
        var drifting = new Fleet(original.id(), original.name(), "owner", "a", "b", 0, 0, .01,
                false, "PASSIVE", original.ships(), FleetLocation.at(FleetLocation.Site.deepSpace()),
                Fleet.MODE_POWER_INTERRUPTED, 5, 1e6, 1, 1, 100, Map.of("ship", 100.0),
                new FlightMotion(10000, 100, null, 0));
        state = state.withFleets(List.of(drifting));
        var command = new RecoverFleetTravelCommand("fleet");
        assertNull(command.preview(state));
        assertSame(state, command.apply(state));
        state = stocked(state, 1);
        var plan = command.preview(state);
        assertNotNull(plan);
        assertTrue(plan.trajectory().totalSeconds() < 86400);
        state = command.apply(state);
        assertEquals(Fleet.MODE_RECOVERY, state.fleets().getFirst().interstellarMode());
        state = state.withFleets(com.spaceconquest.engine.ship.ShipPowerProcessor.advanceDay(state));
        state = PassengerTransitProcessor.advanceDay(state, DataModelLoader.loadRaces());
        assertEquals("b", state.fleets().getFirst().currentSystemId());
        assertEquals(10, state.passengerManifests().getFirst().headcount());
    }

    @Test void supplyChecksRejectUnknownSpeciesInvalidDurationsAndNonfiniteStock() throws Exception {
        var state = world(20, .05, false);
        var fleet = state.fleets().getFirst();
        for (double days : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(PassengerTransitProcessor.canSustainJourney(state, fleet, DataModelLoader.loadRaces(), days));
        assertFalse(PassengerTransitProcessor.canSustainJourney(state, fleet, List.of(), 1));
        var ship = fleet.ships().getFirst();
        var invalid = new ShipInstance(ship.id(), ship.designId(), "owner", 100, 0, 1000,
                Map.of("oxygen_gas", Double.NaN, "food_matrix", 1000.0), 10, "human", ShipInstance.MODE_CONSCIOUS);
        assertFalse(PassengerTransitProcessor.canSustainJourney(state, fleet.withShips(List.of(invalid)),
                DataModelLoader.loadRaces(), 1));
    }
}
