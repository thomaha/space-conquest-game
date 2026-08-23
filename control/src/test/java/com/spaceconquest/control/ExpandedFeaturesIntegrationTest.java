package com.spaceconquest.control;

import com.spaceconquest.control.command.CancelTradeRouteCommand;
import com.spaceconquest.control.command.CreateTradeRouteCommand;
import com.spaceconquest.control.command.ScanSystemCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Material;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SaveGame;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.espionage.PirateBase;
import com.spaceconquest.engine.galaxy.FogOfWarState;
import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import com.spaceconquest.engine.industry.refinement.RefinementRecipe;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExpandedFeaturesIntegrationTest {

    @Test
    public void testExpandedMaterialsAndRefinementRecipes() throws IOException {
        List<Material> materials = DataModelLoader.loadMaterials();
        assertNotNull(materials);
        assertTrue(materials.size() >= 80, "Material library should feature at least 80 distinct materials");

        assertTrue(materials.stream().anyMatch(m -> m.id().equals("hyperdense_neutronium")));
        assertTrue(materials.stream().anyMatch(m -> m.id().equals("metamaterial_composites")));
        assertTrue(materials.stream().anyMatch(m -> m.id().equals("antimatter_containment_cell")));
        assertTrue(materials.stream().anyMatch(m -> m.id().equals("quantum_spin_glass")));
        assertTrue(materials.stream().anyMatch(m -> m.id().equals("stabilized_metallic_hydrogen")));
        assertTrue(materials.stream().anyMatch(m -> m.id().equals("monomolecular_wire")));
        assertTrue(materials.stream().anyMatch(m -> m.id().equals("crystalline_boron_nitride")));

        RefinementProcessor refinementProcessor = new RefinementProcessor();
        List<RefinementRecipe> recipes = refinementProcessor.getStandardRecipes();
        assertTrue(recipes.stream().anyMatch(r -> r.id().equals("synthesis_metamaterials")));
        assertTrue(recipes.stream().anyMatch(r -> r.id().equals("synthesis_antimatter_cells")));
        assertTrue(recipes.stream().anyMatch(r -> r.id().equals("synthesis_metallic_hydrogen")));
    }

    @Test
    public void testAutomatedTradeRouteSimulationLoop() {
        SpaceConquestEngine engine = new SpaceConquestEngine();

        CommercialHub originHub = new CommercialHub(
                "hub_earth", "earth", 0.05, 500000.0, 6000.0, 15.0,
                Map.of("refined_iron", new MarketOrder("refined_iron", 5000.0, 2000.0, 10.0, 0.0))
        );

        CommercialHub destHub = new CommercialHub(
                "hub_mars", "mars", 0.05, 500000.0, 200.0, 15.0,
                Map.of("refined_iron", new MarketOrder("refined_iron", 200.0, 4000.0, 10.0, 0.8))
        );

        Empire terran = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                100000.0, 0.10, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        TradeRoute route = new TradeRoute(
                "route_01", "Sol Earth-Mars Iron", "terran_confederation",
                "hub_earth", "hub_mars", "refined_iron",
                1500.0, 1000.0, 20000.0, List.of("freighter_01"), 0.0, true
        );

        GameState initialState = new GameState(
                0, "RUNNING", List.of(), List.of(terran), List.of(),
                List.of(originHub, destHub), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null,
                List.of(route), List.of()
        );

        engine.applyGameState(initialState);
        engine.stepTurn();

        GameState stateAfterTurn = engine.getGameState();
        assertEquals(1, stateAfterTurn.tradeRoutes().size());
        TradeRoute updatedRoute = stateAfterTurn.tradeRoutes().get(0);
        assertEquals(1500.0, updatedRoute.totalVolumeMovedKg(), 0.001);

        CommercialHub updatedOrigin = stateAfterTurn.commercialHubs().stream()
                .filter(h -> h.id().equals("hub_earth")).findFirst().orElseThrow();
        assertEquals(3500.0, updatedOrigin.activeOrders().get("refined_iron").supplyKg(), 0.001);

        CommercialHub updatedDest = stateAfterTurn.commercialHubs().stream()
                .filter(h -> h.id().equals("hub_mars")).findFirst().orElseThrow();
        assertEquals(1700.0, updatedDest.activeOrders().get("refined_iron").supplyKg(), 0.001);

        Empire updatedEmpire = stateAfterTurn.empires().stream()
                .filter(e -> e.id().equals("terran_confederation")).findFirst().orElseThrow();
        assertTrue(updatedEmpire.treasuryCredits() > 100000.0);
    }

    @Test
    public void testFogOfWarSensorCoverageAndSaveGameRoundTrip(@TempDir Path tempDir) throws IOException {
        Empire terran = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                100000.0, 0.10, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        Planet earth = new Planet(
                "earth", "Earth", "Homeworld", 5.97e24, 1.0, 1.496e8, 0.0, 12742.0,
                "terrestrial", "Breathable", true, 0.71, List.of("iron_ore"), List.of(), List.of()
        );

        SolarSystem sol = new SolarSystem(
                "sol", "Sol", "Home System", 0.0, 0.0, 0.0, 1.989e30, 1392700.0, "#FFF500",
                List.of(earth), List.of()
        );

        Planet alphaPrime = new Planet(
                "ac_prime", "Alpha Centauri Prime", "Colony World", 4.0e24, 0.9, 1.2e8, 0.0, 11000.0,
                "terrestrial", "Breathable", true, 0.50, List.of("copper_ore"), List.of(), List.of()
        );

        SolarSystem alphaCentauri = new SolarSystem(
                "alpha_centauri", "Alpha Centauri", "Neighbor System", 4.3, 0.0, 0.0, 2.0e30, 1400000.0, "#FFCC00",
                List.of(alphaPrime), List.of()
        );

        ShipDesign expDesign = new ShipDesign(
                "design_exp", "Explorer", "terran_confederation", ShipRole.EXPLORER,
                "carbon_nanotubes", List.of("scanner_array_01"), "steel", 10.0, 50000.0, 10000.0,
                500.0, 10.0, 1000.0, 10000.0, true, false
        );

        Fleet expFleet = new Fleet(
                "fleet_exp", "Survey Fleet", "terran_confederation", "alpha_centauri", "",
                0.0, 0.0, 0.0, false, "PATROL",
                List.of(new ShipInstance("ship_01", "design_exp", "terran_confederation", 100.0, 100.0, 100.0, Map.of()))
        );

        PirateBase rogueBase = new PirateBase(
                "pirate_01", "shadow_syndicate_sol", "alpha_centauri", "asteroid_01",
                2500.0, 2, true
        );

        TradeRoute route = new TradeRoute(
                "route_01", "Interstellar Supply Line", "terran_confederation",
                "hub_earth", "hub_ac", "refined_iron",
                2000.0, 500.0, 50000.0, List.of("freighter_01"), 5000.0, true
        );

        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameState state = new GameState(
                5, "RUNNING", List.of(sol, alphaCentauri), List.of(terran), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(expDesign), List.of(expFleet), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(rogueBase), List.of(), List.of(), null,
                List.of(route), List.of()
        );

        engine.applyGameState(state);
        engine.stepTurn();

        GameState simulatedState = engine.getGameState();
        assertFalse(simulatedState.fogOfWarStates().isEmpty());
        FogOfWarState terranFOW = simulatedState.fogOfWarStates().get(0);
        assertTrue(terranFOW.isSystemExplored("sol"));
        assertTrue(terranFOW.isSystemExplored("alpha_centauri"));
        assertTrue(terranFOW.isPlanetScanned("earth"));
        assertTrue(terranFOW.isPlanetScanned("ac_prime"));
        assertTrue(terranFOW.discoveredPirateBaseIds().contains("pirate_01"));

        // Test File Save and Load roundtrip
        SaveGameManager saveMgr = new SaveGameManager(tempDir);
        Path savePath = saveMgr.save("test_save_full", simulatedState, 1, "Day 5 12:00");
        assertTrue(savePath.toFile().exists());

        SaveGame loadedSave = saveMgr.load(savePath.toFile());
        assertNotNull(loadedSave);
        assertEquals(8, loadedSave.version());
        assertEquals(1, loadedSave.tradeRoutes().size());
        assertEquals("route_01", loadedSave.tradeRoutes().get(0).id());
        assertEquals(1, loadedSave.fogOfWarStates().size());
        assertTrue(loadedSave.fogOfWarStates().get(0).isSystemExplored("alpha_centauri"));

        // Test QuickSave and Delete
        Path qsPath = saveMgr.quickSave(simulatedState, 1, "Day 5");
        assertTrue(qsPath.toFile().exists());
        boolean deleted = saveMgr.deleteSave("test_save_full");
        assertTrue(deleted);
        assertFalse(savePath.toFile().exists());
    }
}
