package com.spaceconquest.engine.habitation;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalPopulationTransitTest {
    @Test
    void bookedResidentsDisembarkIntoStationPopulationAndRespectHabitationCapacity() {
        Planet earth = new Planet("earth", "Earth", "", 1, 9.81, 0, 0, 1,
                "terrestrial", "breathable", true, 1, List.of(), List.of(),
                List.of(new Population("human", Map.of(25, 100L))));
        SolarSystem system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1,
                "yellow", List.of(earth), List.of());
        StationModule habitation = new StationModule("hab", "Habitation", StationModule.TYPE_HABITATION,
                2, 4_000, 10, 0, Map.of(), "technician", 1, true);
        OrbitalStation station = new OrbitalStation("station", "Orbital home", "sol", "earth",
                "emp", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 20, List.of(habitation),
                Map.of(), 0, 10, 0, 0, 100, 100, "steel", 1, true);
        Fleet fleet = new Fleet("fleet", "Passenger ship", "emp", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("ship", "design", "emp",
                100, 0, 0, Map.of())), FleetLocation.at(FleetLocation.Site.surface("earth")));
        Empire empire = new Empire("emp", "Empire", "human", "Individualist", 1_000,
                0.1, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        GameState state = GameState.builder().solarSystems(List.of(system)).empires(List.of(empire))
                .orbitalStations(List.of(station)).fleets(List.of(fleet)).build();

        assertTrue(PassengerTransitProcessor.canBoard(state, "ship", "human", 40, "station"));
        GameState boarded = PassengerTransitProcessor.board(state, "ship", "human", 40,
                "station", ShipInstance.MODE_CONSCIOUS);
        assertEquals(60, boarded.solarSystems().getFirst().planets().getFirst()
                .populations().getFirst().totalCount());
        assertFalse(PassengerTransitProcessor.canBoard(boarded, "ship", "human", 161, "station"));

        Fleet docked = boarded.fleets().getFirst().withLocation(
                FleetLocation.at(FleetLocation.Site.docked("station")));
        GameState arrived = PassengerTransitProcessor.disembarkArrivals(
                boarded.withFleets(List.of(docked)));
        assertEquals(40, arrived.orbitalStations().getFirst().populations().getFirst().totalCount());
        assertEquals(0, arrived.fleets().getFirst().ships().getFirst().passengerCount());
    }
}
