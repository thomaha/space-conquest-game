package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.LoadPassengersCommand;
import com.spaceconquest.control.command.SetPassengerTransitModeCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PassengerCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;

    @BeforeEach
    void setUp() {
        commandQueue = new CommandQueue();
        ShipInstance ship = new ShipInstance(
                "transport_alpha", "design_troop", "terran_confederation",
                500.0, 200.0, 100.0, Map.of()
        );
        Fleet fleet = new Fleet(
                "fleet_sol_transport", "Sol 1st Transport Wing", "terran_confederation",
                "sol", "", 0.0, 0.0, 0.0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.surface("earth"))
        );

        initialState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0,
                        1, 1, "Yellow", List.of(
                        new Planet("earth", "Earth", "", 1, 9.81, 1, 0, 12_742,
                                "terrestrial", "breathable", true, 1, List.of(), List.of(),
                                List.of(new Population("human", Map.of(25, 1_000L)))),
                        new Planet("mars", "Mars", "", 1, 3.71, 1, 0, 6_779,
                                "terrestrial", "none", false, 0, List.of(), List.of(), List.of())),
                        List.of())))
                .shipDesigns(List.of(new ShipDesign("design_troop", "Transport",
                        "terran_confederation", ShipRole.CARGO_TRANSPORT, "steel", List.of(),
                        "steel", 0, 1_000, 30_000, 0, 1, 0, 450_000, true, false)))
                .empires(List.of(new Empire("terran_confederation", "Terran", "human",
                        "Individualist", 1_000_000, 0.1, List.of("sol"), List.of(),
                        Map.of(), List.of(), List.of())))
                .commercialHubs(List.of(new CommercialHub("hub_earth", "earth", 0.0,
                        200_000, 100_000, 10, Map.of("rp1_kerosene",
                        new MarketOrder("rp1_kerosene", 50_000, 0, 2, 0),
                        "liquid_oxygen", new MarketOrder("liquid_oxygen", 50_000, 0, 2, 0)))))
                .industrialFacilities(List.of(new IndustrialFacility("launch_earth", "earth",
                        "cargo_terminal", "terran_confederation",
                        IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0.0)))
                .fleets(List.of(fleet))
                .build();
    }

    @Test
    void testLoadPassengersCommand() {
        LoadPassengersCommand cmd = new LoadPassengersCommand(
                "fleet_sol_transport", "transport_alpha", "human", 250,
                ShipInstance.MODE_CRYOGENIC_STASIS, "mars"
        );

        assertFalse(cmd.validate(initialState));
        GameState nextState = new LoadPassengersCommand("fleet_sol_transport",
                "transport_alpha", "human", 250, null, "mars").apply(initialState);

        ShipInstance updatedShip = nextState.fleets().get(0).ships().get(0);
        assertEquals(250, updatedShip.passengerCount());
        assertEquals("human", updatedShip.passengerRaceId());
        assertEquals(ShipInstance.MODE_CONSCIOUS, updatedShip.transitMode());
        assertEquals(750, nextState.solarSystems().getFirst().planets().getFirst()
                .populations().getFirst().totalCount());
        assertEquals(250, nextState.passengerManifests().getFirst().headcount());
        GameState ascending = initialState.withFleets(List.of(initialState.fleets().getFirst()
                .withLocation(initialState.fleets().getFirst().location().depart(
                        FleetLocation.Site.orbit("earth"), 1))));
        assertFalse(cmd.validate(ascending));
    }

    @Test
    void bookedPeopleRemainInTransitUntilTheirDestinationIsReached() {
        LoadPassengersCommand booking = new LoadPassengersCommand(
                "fleet_sol_transport", "transport_alpha", "human", 100,
                ShipInstance.MODE_CONSCIOUS, "mars");
        GameState boarded = booking.apply(initialState);
        Fleet traveling = boarded.fleets().getFirst().withLocation(
                boarded.fleets().getFirst().location().depart(
                        FleetLocation.Site.orbit("earth"), 1.0));
        GameState inOrbit = boarded.withFleets(new FleetProcessor().processFleetMovements(
                List.of(traveling), List.of(), List.of()));
        GameState waiting = PassengerTransitProcessor.disembarkArrivals(inOrbit);
        assertEquals(100, waiting.passengerManifests().getFirst().headcount());
        assertEquals(0, waiting.solarSystems().getFirst().planets().get(1).populations().size());
        GameState landed = waiting.withFleets(List.of(waiting.fleets().getFirst()
                .withLocation(FleetLocation.at(FleetLocation.Site.surface("mars")))));
        GameState arrived = PassengerTransitProcessor.disembarkArrivals(landed);
        assertTrue(arrived.passengerManifests().isEmpty());
        assertEquals(900, arrived.solarSystems().getFirst().planets().getFirst()
                .populations().getFirst().totalCount());
        assertEquals(100, arrived.solarSystems().getFirst().planets().get(1)
                .populations().getFirst().totalCount());
        assertEquals(0, arrived.fleets().getFirst().ships().getFirst().passengerCount());
    }

    @Test
    void testSetPassengerTransitModeCommand() {
        // First load passengers
        LoadPassengersCommand loadCmd = new LoadPassengersCommand(
                "fleet_sol_transport", "transport_alpha", "human", 100,
                ShipInstance.MODE_CONSCIOUS, "mars"
        );
        GameState loadedState = loadCmd.apply(initialState);

        // Stasis requires both researched technology and an equipped pod.
        SetPassengerTransitModeCommand modeCmd = new SetPassengerTransitModeCommand(
                "fleet_sol_transport", "transport_alpha", ShipInstance.MODE_CRYOGENIC_STASIS
        );
        assertFalse(modeCmd.validate(loadedState));
        ShipDesign equipped = new ShipDesign("design_troop", "Transport",
                "terran_confederation", ShipRole.CARGO_TRANSPORT, "steel",
                List.of(PassengerStasis.MODULE_ID), "steel", 0, 1_000,
                30_000, 0, 1, 0, 0, true, false);
        Empire owner = new Empire("terran_confederation", "Terran", "human",
                "Individualist", 0, 0, List.of("sol"), List.of(), Map.of(),
                List.of(PassengerStasis.TECHNOLOGY_ID), List.of(equipped.id()));
        GameState capable = loadedState.toBuilder().shipDesigns(List.of(equipped))
                .empires(List.of(owner)).build();
        assertTrue(modeCmd.validate(capable));
        GameState updatedState = modeCmd.apply(capable);

        ShipInstance updatedShip = updatedState.fleets().get(0).ships().get(0);
        assertEquals(100, updatedShip.passengerCount());
        assertEquals(ShipInstance.MODE_CRYOGENIC_STASIS, updatedShip.transitMode());
    }

    @Test
    void consciousPassengersConsumeCarriedSuppliesAndShortagesReduceArrivals() throws Exception {
        ShipInstance loaded = new ShipInstance("transport_alpha", "design_troop",
                "terran_confederation", 500, 200, 100,
                Map.of("oxygen_gas", 5.0, "food_matrix", 10.0));
        GameState stocked = initialState.withFleets(List.of(initialState.fleets().getFirst()
                .withShips(List.of(loaded))));
        GameState boarded = new LoadPassengersCommand("fleet_sol_transport",
                "transport_alpha", "human", 100, null, "mars").apply(stocked);
        GameState dayOne = PassengerTransitProcessor.advanceDay(boarded, DataModelLoader.loadRaces());
        ShipInstance afterDayOne = dayOne.fleets().getFirst().ships().getFirst();
        assertEquals(0.0, afterDayOne.storedCargoKg().get("oxygen_gas"), 1e-9);
        assertEquals(0.0, afterDayOne.storedCargoKg().get("food_matrix"), 1e-9);
        assertEquals(100, dayOne.passengerManifests().getFirst().headcount());
        GameState dayTwo = PassengerTransitProcessor.advanceDay(dayOne, DataModelLoader.loadRaces());
        assertEquals(99, dayTwo.passengerManifests().getFirst().headcount());
        assertEquals(99, dayTwo.fleets().getFirst().ships().getFirst().passengerCount());
        GameState landed = dayTwo.withFleets(List.of(dayTwo.fleets().getFirst()
                .withLocation(FleetLocation.at(FleetLocation.Site.surface("mars")))));
        GameState arrived = PassengerTransitProcessor.disembarkArrivals(landed);
        assertEquals(99, arrived.solarSystems().getFirst().planets().get(1)
                .populations().getFirst().totalCount());
    }

    @Test
    void stockedPassengersCanCompleteAnUnresearchedSublightCrossing() throws Exception {
        ShipInstance stockedShip = new ShipInstance("transport_alpha", "design_troop",
                "terran_confederation", 500, 200, 100,
                Map.of("oxygen_gas", 5.0, "food_matrix", 10.0));
        Planet destination = new Planet("new_world", "New world", "", 1, 9.81,
                1, 0, 12_000, "terrestrial", "breathable", true, 1,
                List.of(), List.of(), List.of());
        SolarSystem alpha = new SolarSystem("alpha", "Alpha", "", 0.001, 0, 0,
                1, 1, "Yellow", List.of(destination), List.of());
        GameState state = initialState.toBuilder()
                .solarSystems(List.of(initialState.solarSystems().getFirst(), alpha))
                .fleets(List.of(initialState.fleets().getFirst().withShips(List.of(stockedShip))))
                .build();
        state = new LoadPassengersCommand("fleet_sol_transport", "transport_alpha",
                "human", 10, null, "new_world").apply(state);
        MoveFleetCommand cross = new MoveFleetCommand("fleet_sol_transport", "alpha");
        ShipInstance withoutSupplies = new ShipInstance("transport_alpha", "design_troop",
                "terran_confederation", 500, 200, 100, Map.of(), 10, "human",
                ShipInstance.MODE_CONSCIOUS);
        GameState hungry = state.withFleets(List.of(state.fleets().getFirst()
                .withShips(List.of(withoutSupplies))));
        assertFalse(cross.validate(hungry));
        assertTrue(cross.validate(state));
        state = cross.apply(state);
        assertEquals(Fleet.MODE_SUBLIGHT, state.fleets().getFirst().interstellarMode());
        assertTrue(state.fleets().getFirst().interstellarTravelDays() > 4.0);
        assertTrue(state.fleets().getFirst().interstellarTravelDays() < 6.0);
        FleetProcessor movement = new FleetProcessor();
        for (int day = 0; day < 10 && state.fleets().getFirst().hasInterstellarOrder(); day++) {
            state = state.withFleets(movement.processFleetMovements(state.fleets(),
                    List.of(), List.of()));
            state = PassengerTransitProcessor.advanceDay(state, DataModelLoader.loadRaces());
        }
        assertEquals("alpha", state.fleets().getFirst().currentSystemId());
        assertEquals(10, state.passengerManifests().getFirst().headcount());
        assertTrue(state.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("food_matrix") < 10.0);
        GameState landed = state.withFleets(List.of(state.fleets().getFirst()
                .withLocation(FleetLocation.at(FleetLocation.Site.surface("new_world")))));
        GameState arrived = PassengerTransitProcessor.disembarkArrivals(landed);
        assertEquals(10, arrived.solarSystems().get(1).planets().getFirst()
                .populations().getFirst().totalCount());
    }
}
