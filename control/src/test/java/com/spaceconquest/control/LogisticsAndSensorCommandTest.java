package com.spaceconquest.control;

import com.spaceconquest.control.command.CancelTradeRouteCommand;
import com.spaceconquest.control.command.CreateTradeRouteCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.ScanSystemCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.galaxy.FogOfWarState;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LogisticsAndSensorCommandTest {

    @Test
    public void testCreateAndCancelTradeRouteCommand() {
        ShipDesign design = new ShipDesign("cargo_design", "Cargo", "terran_confederation",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                1_000, 5_000, 0, 1, 0, 100_000, true, false);
        Fleet freighter = new Fleet("fleet", "Freighter", "terran_confederation",
                "sol", "", 0, 0, 0, false, "PASSIVE", List.of(new ShipInstance(
                "freighter_01", design.id(), "terran_confederation", 100, 0, 100, Map.of())));
        GameState state = GameState.builder().solarSystems(List.of(
                new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1, "Yellow",
                        List.of(), List.of()),
                new SolarSystem("alpha", "Alpha", "", 1, 0, 0, 1, 1, "Yellow",
                        List.of(), List.of())))
                .empires(List.of(new Empire("terran_confederation", "Terran", "human",
                        "Individualist", 1_000, 0.1, List.of("sol"), List.of(),
                        Map.of(), List.of(), List.of())))
                .commercialHubs(List.of(
                new CommercialHub("hub_earth", "earth", 0, 10_000, 0, 10, Map.of()),
                new CommercialHub("hub_mars", "mars", 0, 10_000, 0, 10, Map.of())))
                .shipDesigns(List.of(design)).fleets(List.of(freighter)).build();

        CreateTradeRouteCommand createCmd = new CreateTradeRouteCommand(
                "terran_confederation", "Earth-Mars Iron Line", "hub_earth", "hub_mars",
                "refined_iron", 2000.0, 1000.0, 20000.0, List.of("freighter_01")
        );

        assertTrue(createCmd.validate(state));
        GameState afterCreate = createCmd.apply(state);

        assertEquals(1, afterCreate.tradeRoutes().size());
        TradeRoute route = afterCreate.tradeRoutes().get(0);
        assertEquals("Earth-Mars Iron Line", route.name());
        assertEquals("refined_iron", route.materialId());
        assertTrue(route.isActive());
        assertFalse(createCmd.validate(afterCreate));
        assertFalse(new MoveFleetCommand(freighter.id(), "alpha").validate(afterCreate));

        CancelTradeRouteCommand cancelCmd = new CancelTradeRouteCommand(route.id(), "terran_confederation");
        assertTrue(cancelCmd.validate(afterCreate));
        GameState afterCancel = cancelCmd.apply(afterCreate);

        assertEquals(1, afterCancel.tradeRoutes().size());
        assertFalse(afterCancel.tradeRoutes().get(0).isActive());
        assertTrue(new MoveFleetCommand(freighter.id(), "alpha").validate(afterCancel));
    }

    @Test
    public void testScanSystemCommand() {
        Planet mars = new Planet(
                "mars", "Mars", "Red Planet", 6.42e23, 0.38, 2.279e8, 1.85, 6792.0,
                "terrestrial", "Thin CO2", false, 0.0, List.of("iron_ore"), List.of(), List.of()
        );

        SolarSystem sol = new SolarSystem(
                "sol", "Sol", "Home System", 0.0, 0.0, 0.0, 1.989e30, 1392700.0, "#FFF500",
                List.of(mars), List.of()
        );

        Empire terran = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                100000.0, 0.10, List.of(), List.of(), Map.of(), List.of(), List.of()
        );

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(sol))
                .empires(List.of(terran))
                .build();

        ScanSystemCommand scanCmd = new ScanSystemCommand("terran_confederation", "sol");
        assertTrue(scanCmd.validate(state));
        GameState afterScan = scanCmd.apply(state);

        assertEquals(1, afterScan.fogOfWarStates().size());
        FogOfWarState fow = afterScan.fogOfWarStates().get(0);
        assertTrue(fow.isSystemExplored("sol"));
        assertTrue(fow.isPlanetScanned("mars"));
    }
}
