package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.ColonizePlanetCommand;
import com.spaceconquest.control.command.PlaceFacilityOnTileCommand;
import com.spaceconquest.control.command.StartProspectingMissionCommand;
import com.spaceconquest.control.command.StartTerraformingProjectCommand;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.SystemGovernor;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.technology.ResearchProject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EmpireViewTest {

    @BeforeAll
    public static void initJavaFx() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    public void testPlanetaryBodyEntryFromPlanet() {
        Planet colonizedPlanet = new Planet(
                "earth", "Earth", "Home world",
                5.97e24, 9.81, 1.0, 0.0, 12742.0, "TERRESTRIAL", "Oxygen-Nitrogen",
                true, 0.71, List.of("iron_ore", "copper_ore", "silicates"),
                List.of(), List.of(new Population("human", Map.of(20, 5000000L, 30, 3000000L)))
        );
        SolarSystem sol = new SolarSystem(
                "sol", "Sol", "Home solar system",
                0.0, 0.0, 0.0, 1.0, 1392700.0, "#fff5f0",
                List.of(colonizedPlanet), List.of()
        );

        PlanetaryBodyEntry entry = PlanetaryBodyEntry.fromPlanet(colonizedPlanet, sol);
        assertNotNull(entry);
        assertEquals("earth", entry.id());
        assertEquals("Earth", entry.name());
        assertEquals("sol", entry.systemId());
        assertEquals("Sol", entry.systemName());
        assertFalse(entry.isMoon());
        assertNull(entry.parentPlanetName());
        assertEquals(8000000L, entry.totalPopulation());
        assertTrue(entry.isColonized());
        assertFalse(entry.isColonizable(), "Colonized worlds should not be marked colonizable targets");
        assertEquals(9.81, entry.gravity(), 0.001);
        assertEquals(12742.0, entry.diameter(), 0.001);
        assertEquals(3, entry.resourceCount());
        assertEquals("Oxygen-Nitrogen", entry.getAtmosphere());
        assertTrue(entry.hasLiquidWater());
        assertEquals("TERRESTRIAL", entry.getBodyType());
        assertEquals(3, entry.getResources().size());
    }

    @Test
    public void testPlanetaryBodyEntryFromMoon() {
        Moon luna = new Moon(
                "luna", "Luna", "Earth's natural satellite",
                7.34e22, 1.62, 384400.0, 3474.0, "None",
                false, 0.0, List.of("helium_3", "silicates"),
                List.of()
        );
        Planet earth = new Planet(
                "earth", "Earth", "Home world",
                5.97e24, 9.81, 1.0, 0.0, 12742.0, "TERRESTRIAL", "Oxygen-Nitrogen",
                true, 0.71, List.of(), List.of(luna), List.of()
        );
        SolarSystem sol = new SolarSystem(
                "sol", "Sol", "Sol system",
                0.0, 0.0, 0.0, 1.0, 1392700.0, "#fff5f0",
                List.of(earth), List.of()
        );

        PlanetaryBodyEntry entry = PlanetaryBodyEntry.fromMoon(luna, earth, sol);
        assertNotNull(entry);
        assertEquals("luna", entry.id());
        assertEquals("Luna", entry.name());
        assertEquals("sol", entry.systemId());
        assertEquals("Sol", entry.systemName());
        assertTrue(entry.isMoon());
        assertEquals("Earth", entry.parentPlanetName());
        assertEquals(0L, entry.totalPopulation());
        assertFalse(entry.isColonized());
        assertTrue(entry.isColonizable(), "Uncolonized moon with 1.62 m/s² is colonizable");
        assertEquals(1.62, entry.gravity(), 0.001);
        assertEquals(3474.0, entry.diameter(), 0.001);
        assertEquals(2, entry.resourceCount());
        assertEquals("None", entry.getAtmosphere());
        assertFalse(entry.hasLiquidWater());
        assertEquals("Moon", entry.getBodyType());
    }

    @Test
    public void testFilteringPredicates() {
        Planet earth = new Planet(
                "earth", "Earth", "Colonized",
                5.97e24, 9.81, 1.0, 0, 12742, "TERRESTRIAL", "Oxygen-Nitrogen",
                true, 0.71, List.of("iron"), List.of(),
                List.of(new Population("human", Map.of(25, 1000L)))
        );
        Planet mars = new Planet(
                "mars", "Mars", "Colonizable",
                6.41e23, 3.71, 1.5, 0, 6779, "TERRESTRIAL", "Carbon Dioxide",
                false, 0.0, List.of("iron", "nickel"), List.of(),
                List.of()
        );
        Planet jupiter = new Planet(
                "jupiter", "Jupiter", "Uninhabitable Gas Giant",
                1.89e27, 24.79, 5.2, 0, 139820, "GAS_GIANT", "Hydrogen-Helium",
                false, 0.0, List.of("hydrogen"), List.of(),
                List.of()
        );
        Moon europa = new Moon(
                "europa", "Europa", "Colonizable Moon",
                4.8e22, 1.315, 670900, 3121, "Trace",
                true, 0.1, List.of("water_ice"), List.of()
        );

        SolarSystem sol = new SolarSystem(
                "sol", "Sol", "Sol",
                0, 0, 0, 1.0, 1392700, "#ffffff",
                List.of(earth, mars, new Planet(jupiter.id(), jupiter.name(), jupiter.description(),
                        jupiter.mass(), jupiter.gravity(), jupiter.distance(), jupiter.inclination(),
                        jupiter.diameter(), jupiter.type(), jupiter.atmosphere(), jupiter.hasLiquidWater(),
                        jupiter.waterLevel(), jupiter.resources(), List.of(europa), List.of())),
                List.of()
        );

        EmpireView view = new EmpireView(null);
        view.updateData(List.of(sol), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        // Default: Colonized
        view.setCurrentFilter(EmpireView.FilterCategory.COLONIZED);
        List<PlanetaryBodyEntry> colonized = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(1, colonized.size());
        assertEquals("earth", colonized.get(0).id());

        // Uncolonized: mars, jupiter, europa
        view.setCurrentFilter(EmpireView.FilterCategory.UNCOLONIZED);
        List<PlanetaryBodyEntry> uncolonized = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(3, uncolonized.size());

        // Colonizable: mars, europa (jupiter is gas giant with 24.79 m/s²)
        view.setCurrentFilter(EmpireView.FilterCategory.COLONIZABLE);
        List<PlanetaryBodyEntry> colonizable = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(2, colonizable.size());
        assertTrue(colonizable.stream().anyMatch(b -> b.id().equals("mars")));
        assertTrue(colonizable.stream().anyMatch(b -> b.id().equals("europa")));

        // All bodies: earth, mars, jupiter, europa
        view.setCurrentFilter(EmpireView.FilterCategory.ALL_BODIES);
        List<PlanetaryBodyEntry> all = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(4, all.size());

        // Only planets: earth, mars, jupiter
        view.setCurrentFilter(EmpireView.FilterCategory.ONLY_PLANETS);
        List<PlanetaryBodyEntry> planetsOnly = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(3, planetsOnly.size());
        assertTrue(planetsOnly.stream().noneMatch(PlanetaryBodyEntry::isMoon));

        // Only moons: europa
        view.setCurrentFilter(EmpireView.FilterCategory.ONLY_MOONS);
        List<PlanetaryBodyEntry> moonsOnly = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(1, moonsOnly.size());
        assertEquals("europa", moonsOnly.get(0).id());
    }

    @Test
    public void testSortingComparators() {
        Planet p1 = new Planet("b_alpha", "Beta Planet", "", 1e24, 5.0, 1, 0, 5000, "TERRESTRIAL", "", false, 0, List.of("a", "b", "c"), List.of(), List.of(new Population("r", Map.of(20, 100L))));
        Planet p2 = new Planet("a_omega", "Alpha Planet", "", 1e24, 15.0, 1, 0, 15000, "TERRESTRIAL", "", false, 0, List.of("a"), List.of(), List.of(new Population("r", Map.of(20, 500L))));

        SolarSystem sysB = new SolarSystem("sys_b", "Zeta System", "", 0, 0, 0, 1, 1000, "#fff", List.of(p1), List.of());
        SolarSystem sysA = new SolarSystem("sys_a", "Alpha System", "", 0, 0, 0, 1, 1000, "#fff", List.of(p2), List.of());

        EmpireView view = new EmpireView(null);
        view.updateData(List.of(sysB, sysA), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        view.setCurrentFilter(EmpireView.FilterCategory.ALL_BODIES);

        // Name A-Z
        view.setCurrentSort(EmpireView.SortOption.NAME_AZ);
        List<PlanetaryBodyEntry> sortByName = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals("Alpha Planet", sortByName.get(0).name());
        assertEquals("Beta Planet", sortByName.get(1).name());

        // System name
        view.setCurrentSort(EmpireView.SortOption.SYSTEM_NAME);
        List<PlanetaryBodyEntry> sortBySystem = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals("Alpha System", sortBySystem.get(0).systemName());
        assertEquals("Zeta System", sortBySystem.get(1).systemName());

        // Population Descending
        view.setCurrentSort(EmpireView.SortOption.POPULATION_DESC);
        List<PlanetaryBodyEntry> sortByPop = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(500L, sortByPop.get(0).totalPopulation());
        assertEquals(100L, sortByPop.get(1).totalPopulation());

        // Size Descending
        view.setCurrentSort(EmpireView.SortOption.SIZE_DESC);
        List<PlanetaryBodyEntry> sortBySize = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(15000.0, sortBySize.get(0).diameter());
        assertEquals(5000.0, sortBySize.get(1).diameter());

        // Gravity Descending
        view.setCurrentSort(EmpireView.SortOption.GRAVITY_DESC);
        List<PlanetaryBodyEntry> sortByGrav = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(15.0, sortByGrav.get(0).gravity());
        assertEquals(5.0, sortByGrav.get(1).gravity());

        // Resources Descending
        view.setCurrentSort(EmpireView.SortOption.RESOURCES_DESC);
        List<PlanetaryBodyEntry> sortByRes = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(3, sortByRes.get(0).resourceCount());
        assertEquals(1, sortByRes.get(1).resourceCount());
    }

    @Test
    public void testTechnologyValidation() {
        Empire playerEmpire = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                50000.0, 0.15, List.of("sol"),
                List.of(new MinistryAssignment("interior", "diplomat", 1.2)),
                Map.of(),
                List.of("electricity", "rocketry", "geological_prospecting"),
                List.of()
        );

        ResearchProject projectDone = new ResearchProject(
                "proj_gene", "terran_confederation", "gene_technology",
                false, 5000.0, 5000.0, 10, 1.0
        );

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");
        view.updateData(List.of(), List.of(playerEmpire), List.of(), List.of(projectDone), List.of(), List.of(), List.of(), List.of(), List.of());

        assertTrue(view.isTechnologyUnlocked("electricity"));
        assertTrue(view.isTechnologyUnlocked("rocketry"));
        assertTrue(view.isTechnologyUnlocked("geological_prospecting"));
        assertTrue(view.isTechnologyUnlocked("gene_technology"), "Completed research project should unlock tech");
        assertFalse(view.isTechnologyUnlocked("antimatter"), "Unresearched tech should remain locked");
    }

    @Test
    public void testTabSwitchingAndRoot() {
        EmpireView view = new EmpireView(null);
        assertNotNull(view.getRoot());
        assertEquals(EmpireView.Tab.ECONOMY, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.CABINET);
        assertEquals(EmpireView.Tab.CABINET, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.PLANETS);
        assertEquals(EmpireView.Tab.PLANETS, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.STATIONS);
        assertEquals(EmpireView.Tab.STATIONS, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.CORPORATIONS);
        assertEquals(EmpireView.Tab.CORPORATIONS, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.MEGASTRUCTURES);
        assertEquals(EmpireView.Tab.MEGASTRUCTURES, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.ECONOMY);
        assertEquals(EmpireView.Tab.ECONOMY, view.getCurrentTab());
    }

    @Test
    public void testClickingThroughTabsWhileVisibleWithLoadedData() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameState gameState = engine.getGameState();

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");
        view.updateData(gameState);
        view.show();
        assertTrue(view.getRoot().isVisible());

        // Cycle through all tabs repeatedly while root is visible
        for (int i = 0; i < 3; i++) {
            for (EmpireView.Tab tab : EmpireView.Tab.values()) {
                assertDoesNotThrow(() -> view.selectTab(tab), "Switching to tab " + tab + " must not throw");
                assertEquals(tab, view.getCurrentTab());
            }
        }
    }

    @Test
    public void testOrbitalStationsAndElevatorsFilteringForPlayerEmpire() {
        StationModule labMod = new StationModule("mod_1", "Physics Lab", StationModule.TYPE_THEORETICAL_PHYSICS_LAB, 6, 12000.0, 50.0, 0.0, Map.of(), "scientist", 5, true);
        StationModule shipyardMod = new StationModule("mod_2", "Capital Slipway", StationModule.TYPE_CAPITAL_SLIPWAY, 12, 28000.0, 30.0, 0.0, Map.of(), "technician", 5, true);

        OrbitalStation terranStation = new OrbitalStation(
                "station_terran", "Terran Starbase Alpha", "sol", "earth",
                "terran_confederation", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50,
                List.of(labMod, shipyardMod), Map.of(),
                350.0, 80.0, 50000.0, 50000.0, 20000.0, 20000.0,
                "titanium_alloy", 15.0, true
        );

        OrbitalStation vulkanStation = new OrbitalStation(
                "station_vulkan", "Vulkan Orbital Forge", "vulcan-system", "vulcan",
                "vulkan_forge", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 60,
                List.of(shipyardMod), Map.of(),
                500.0, 120.0, 70000.0, 70000.0, 30000.0, 30000.0,
                "nanotube_composite", 20.0, true
        );

        SpaceElevator terranElevator = new SpaceElevator(
                "elev_earth", "earth", "terran_confederation",
                100000.0, 0.90, 100.0, true
        );

        SpaceElevator vulkanElevator = new SpaceElevator(
                "elev_vulcan", "vulcan", "vulkan_forge",
                150000.0, 0.95, 100.0, true
        );

        ConstructionDeploymentProject terranProj = new ConstructionDeploymentProject(
                "proj_1", "ship_1", "sol", "luna",
                "ORBITAL_STATION", 2.0, 5.0, Map.of(), false
        );

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");
        view.updateData(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(terranStation, vulkanStation),
                List.of(terranElevator, vulkanElevator),
                List.of(terranProj),
                List.of()
        );

        // When player is terran_confederation: only Terran stations & elevators returned
        List<OrbitalStation> terranStations = view.getOrbitalStationsForPlayerEmpire();
        assertEquals(1, terranStations.size());
        assertEquals("station_terran", terranStations.get(0).id());

        List<SpaceElevator> terranElevators = view.getSpaceElevatorsForPlayerEmpire();
        assertEquals(1, terranElevators.size());
        assertEquals("elev_earth", terranElevators.get(0).id());

        // Switch player empire to vulkan_forge
        view.setPlayerEmpireId("vulkan_forge");
        List<OrbitalStation> vulkanStations = view.getOrbitalStationsForPlayerEmpire();
        assertEquals(1, vulkanStations.size());
        assertEquals("station_vulkan", vulkanStations.get(0).id());

        List<SpaceElevator> vulkanElevators = view.getSpaceElevatorsForPlayerEmpire();
        assertEquals(1, vulkanElevators.size());
        assertEquals("elev_vulcan", vulkanElevators.get(0).id());
    }

    @Test
    public void testCorporationRegistryFilteringForPlayerEmpire() {
        Corporation terranCorp = new Corporation(
                "corp_terran_mining", "Asteroid Mining Syndicate", "terran_confederation", "earth",
                "EXTRACTION", 25000.0, List.of("fac_1"), List.of("ship_1"), List.of("vein_1")
        );
        Corporation siliconCorp = new Corporation(
                "corp_silicon_logic", "Silicon Logic Foundry", "silicon_hegemony", "silicon_prime",
                "COMPUTING", 50000.0, List.of("fac_core"), List.of("freighter_1"), List.of()
        );

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");
        view.updateData(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(terranCorp, siliconCorp)
        );

        // Terran player sees only Terran corporations
        List<Corporation> terranCorps = view.getCorporationsForPlayerEmpire();
        assertEquals(1, terranCorps.size());
        assertEquals("corp_terran_mining", terranCorps.get(0).id());
        assertEquals("Asteroid Mining Syndicate", terranCorps.get(0).name());
        assertEquals(25000.0, terranCorps.get(0).liquidCapitalReserves());

        // Switch to silicon hegemony
        view.setPlayerEmpireId("silicon_hegemony");
        List<Corporation> siliconCorps = view.getCorporationsForPlayerEmpire();
        assertEquals(1, siliconCorps.size());
        assertEquals("corp_silicon_logic", siliconCorps.get(0).id());

        // Switch to an empire with no corporations
        view.setPlayerEmpireId("vulkan_forge");
        List<Corporation> vulkanCorps = view.getCorporationsForPlayerEmpire();
        assertTrue(vulkanCorps.isEmpty());
    }

    @Test
    public void testEmpireViewWithGameState() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameState gameState = engine.getGameState();

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");
        assertDoesNotThrow(() -> view.updateData(gameState));

        assertFalse(view.getAllPlanetaryBodies().isEmpty());
        assertEquals(EmpireView.Tab.ECONOMY, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.CABINET);
        assertEquals(EmpireView.Tab.CABINET, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.STATIONS);
        assertEquals(EmpireView.Tab.STATIONS, view.getCurrentTab());

        view.selectTab(EmpireView.Tab.CORPORATIONS);
        assertEquals(EmpireView.Tab.CORPORATIONS, view.getCurrentTab());
        assertFalse(view.getCorporationsForPlayerEmpire().isEmpty(), "Default Terran corporations should be populated");
    }

    @Test
    public void testEmpireEconomyReportCalculationAndLedgers() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameState gameState = engine.getGameState();

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");
        view.updateData(gameState);

        EmpireView.EmpireEconomyReport report = view.calculateEmpireEconomyReport();
        assertNotNull(report);

        // Verify sovereign metrics
        assertTrue(report.treasuryCredits() > 0.0);
        assertTrue(report.totalPopulation() > 0L);
        assertTrue(report.colonizedWorldCount() >= 1);
        assertTrue(report.controlledSystemCount() >= 1);
        assertEquals(0.15, report.corporateTaxRate(), 0.001);

        // Verify incomes and revenues
        assertTrue(report.colonialTaxIncome() > 0.0);
        assertTrue(report.totalIncome() > 0.0);
        assertTrue(report.totalCosts() > 0.0);
        assertEquals(report.totalIncome() - report.totalCosts(), report.netBudgetBalance(), 0.001);

        // Verify colony entries (Earth should be present with population and tax collected)
        assertFalse(report.colonyEntries().isEmpty());
        EmpireView.ColonyEconomyEntry earthEntry = report.colonyEntries().stream()
                .filter(c -> c.bodyId().equalsIgnoreCase("earth"))
                .findFirst()
                .orElse(null);
        assertNotNull(earthEntry);
        assertEquals("Earth", earthEntry.bodyName());
        assertEquals("Sol", earthEntry.systemName());
        assertFalse(earthEntry.isMoon());
        assertTrue(earthEntry.population() > 0);
        assertTrue(earthEntry.grossOutputCredits() > 0);
        assertTrue(earthEntry.taxCollectedCredits() > 0);
        assertTrue(earthEntry.localGovernanceCostCredits() > 0);
        assertEquals(earthEntry.taxCollectedCredits() - earthEntry.localGovernanceCostCredits(), earthEntry.netContributionCredits(), 0.001);

        // Verify corporate entries
        assertFalse(report.corporateEntries().isEmpty());
        for (EmpireView.CorporateEconomyEntry corp : report.corporateEntries()) {
            assertNotNull(corp.corporationName());
            assertNotNull(corp.marketOrientation());
            assertTrue(corp.liquidCapital() >= 0);
        }
    }

    @Test
    public void testHumanControllerCommandStaging() {
        HumanController controller = new HumanController();
        EmpireView view = new EmpireView(null);
        view.setHumanController(controller);
        view.setPlayerEmpireId("terran_confederation");

        Planet mars = new Planet("mars", "Mars", "", 6.4e23, 0.38, 1.5, 0, 6779, "TERRESTRIAL", "CO2", false, 0, List.of("iron"), List.of(), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1000, "#fff", List.of(mars), List.of());
        view.updateData(List.of(sol), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        // Stage commands directly to controller as done in UI buttons
        controller.stageCommand(new StartProspectingMissionCommand("mars", "terran_confederation"));
        controller.stageCommand(new BuildFacilityCommand("mars", "solar_power_array", "terran_confederation", "PUBLIC_STATE", 50, "technician"));
        controller.stageCommand(new PlaceFacilityOnTileCommand("mars", 0, "solar_power_array", "terran_confederation", "PUBLIC_STATE", 50, "technician"));
        controller.stageCommand(new ColonizePlanetCommand("terran_confederation", "sol", "mars", "fleet_1"));
        controller.stageCommand(new StartTerraformingProjectCommand("terran_confederation", "mars", "cyanobacteria_seeding", 1.0, 288.0, Map.of()));

        assertEquals(5, controller.getCommandQueue().size());
    }

    @Test
    public void testGasGiantStatusAndDynamicGridInEmpireView() {
        Planet jupiter = new Planet("jupiter", "Jupiter", "Gas Giant", 1.89e27, 24.79, 5.2, 0, 139820, "GAS_GIANT", "Hydrogen-Helium", false, 0.0, List.of("hydrogen"), List.of(), List.of());
        Planet earth = new Planet("earth", "Earth", "Terrestrial", 5.97e24, 1.0, 1.0, 0, 12742, "TERRESTRIAL", "Oxygen-Nitrogen", true, 0.71, List.of("iron"), List.of(), List.of());
        Moon deimos = new Moon("deimos", "Deimos", "Tiny moon", 1.48e15, 0.003, 23463, 12.4, "None", false, 0.0, List.of(), List.of());

        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1000, "#fff", List.of(jupiter, earth), List.of());

        EmpireView view = new EmpireView(null);
        view.updateData(List.of(sol), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        PlanetaryBodyEntry jupiterEntry = PlanetaryBodyEntry.fromPlanet(jupiter, sol);
        PlanetaryBodyEntry earthEntry = PlanetaryBodyEntry.fromPlanet(earth, sol);
        PlanetaryBodyEntry deimosEntry = PlanetaryBodyEntry.fromMoon(deimos, earth, sol);

        view.setSelectedBody(jupiterEntry);
        assertEquals("jupiter", view.getSelectedBody().id());
        assertTrue(view.getSelectedBody().getBodyType().toUpperCase().contains("GAS"));

        com.spaceconquest.engine.biome.BiomeAdjacencyProcessor proc = new com.spaceconquest.engine.biome.BiomeAdjacencyProcessor();
        assertEquals(0, proc.generateDefaultGrid(jupiter, List.of()).totalTiles());

        com.spaceconquest.engine.biome.PlanetBiomeGrid earthGrid = proc.generateDefaultGrid(earth, List.of());
        assertEquals(40, earthGrid.totalTiles());
        assertEquals(5, earthGrid.rows());
        assertEquals(List.of(2, 10, 16, 10, 2), earthGrid.rowColumnCounts());
        assertEquals(2, earthGrid.columnsInRow(0));
        assertEquals(16, earthGrid.columnsInRow(2));
        assertEquals(2, earthGrid.columnsInRow(4));

        long polarCount = earthGrid.tiles().stream().filter(t -> com.spaceconquest.engine.biome.SurfaceTile.BIOME_POLAR_ICE.equals(t.biomeType())).count();
        long desertCount = earthGrid.tiles().stream().filter(t -> com.spaceconquest.engine.biome.SurfaceTile.BIOME_EQUATORIAL_DESERT.equals(t.biomeType())).count();
        long oceanCount = earthGrid.tiles().stream().filter(com.spaceconquest.engine.biome.SurfaceTile::isWaterCovered).count();
        assertEquals(2, polarCount, "5% polar ice");
        assertEquals(2, desertCount, "5% desert");
        assertEquals(28, oceanCount, "70% water");

        assertEquals(6, proc.generateDefaultGrid(new Planet(deimos.id(), deimos.name(), "", 1e15, deimos.gravity(), 1, 0, deimos.diameter(), "MOON", "None", false, 0, List.of(), List.of(), List.of()), List.of()).totalTiles());
    }

    @Test
    public void testNewGalaxyCreationUpdatesEmpireViewData() throws Exception {
        com.spaceconquest.engine.GalaxyGenerator generator = new com.spaceconquest.engine.GalaxyGenerator();
        GameState newGalaxyState = generator.generateGameState(6, com.spaceconquest.engine.GameStartScenario.PRE_SPACE_FLIGHT);

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");

        // Initially update with default scenario
        SpaceConquestEngine engine = new SpaceConquestEngine();
        view.updateData(engine.getGameState());

        boolean containsDefaultSol = view.getAllPlanetaryBodies().stream().anyMatch(b -> b.id().equals("earth"));
        assertTrue(containsDefaultSol, "Default scenario should include Earth");

        // Now simulate new galaxy generation and state application
        view.updateData(newGalaxyState);

        List<PlanetaryBodyEntry> updatedBodies = view.getAllPlanetaryBodies();
        assertFalse(updatedBodies.isEmpty(), "New galaxy should have planetary bodies");

        // The new galaxy's system IDs and planet IDs are from the generator (not static Sol/Earth)
        List<String> generatedSystemIds = newGalaxyState.solarSystems().stream().map(SolarSystem::id).toList();
        for (PlanetaryBodyEntry body : updatedBodies) {
            assertTrue(generatedSystemIds.contains(body.systemId()), "All bodies in EmpireView must belong to the new galaxy systems");
        }

        // Verify colonized filter returns the new starting colony
        List<PlanetaryBodyEntry> colonizedBodies = view.getFilteredAndSortedPlanetaryBodies();
        assertFalse(colonizedBodies.isEmpty(), "New galaxy should have at least 1 colonized starting body");
        assertTrue(colonizedBodies.getFirst().isColonized(), "Colonized filter must only return colonized worlds");

        // Verify all bodies filter returns all generated planets and moons
        view.setCurrentFilter(EmpireView.FilterCategory.ALL_BODIES);
        List<PlanetaryBodyEntry> allBodies = view.getFilteredAndSortedPlanetaryBodies();
        assertEquals(updatedBodies.size(), allBodies.size(), "ALL_BODIES filter should list all planets and moons from the new galaxy");
    }

    @Test
    public void testMegastructuresTabAndFilteringForPlayerEmpire() {
        com.spaceconquest.engine.megastructure.Megastructure terranDyson = new com.spaceconquest.engine.megastructure.Megastructure(
                "mega_dyson_sol", "Sol Dyson Swarm", com.spaceconquest.engine.megastructure.Megastructure.TYPE_DYSON_SWARM,
                "sol", "sol_star", "terran_confederation",
                2, 2, 10.0, 10.0, true, 50000000.0, Map.of(), 0
        );

        com.spaceconquest.engine.megastructure.Megastructure terranGateway = new com.spaceconquest.engine.megastructure.Megastructure(
                "mega_gate_sol", "Sol Hyperlane Gateway", com.spaceconquest.engine.megastructure.Megastructure.TYPE_HYPERLANE_GATEWAY,
                "sol", "deep_space", "terran_confederation",
                0, 1, 5.0, 15.0, false, 0.0, Map.of(), 0
        );

        com.spaceconquest.engine.megastructure.Megastructure vulkanRing = new com.spaceconquest.engine.megastructure.Megastructure(
                "mega_ring_vulcan", "40 Eridani Ringworld", com.spaceconquest.engine.megastructure.Megastructure.TYPE_RINGWORLD,
                "40_eridani", "star_eridani", "vulkan_forge",
                3, 3, 20.0, 20.0, true, 10000000.0, Map.of(), 50000000
        );

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");
        view.updateData(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(terranDyson, terranGateway, vulkanRing)
        );

        // When player is terran_confederation: only Terran megastructures returned
        List<com.spaceconquest.engine.megastructure.Megastructure> terranMegas = view.getMegastructuresForPlayerEmpire();
        assertEquals(2, terranMegas.size());
        assertTrue(terranMegas.stream().anyMatch(m -> m.id().equals("mega_dyson_sol")));
        assertTrue(terranMegas.stream().anyMatch(m -> m.id().equals("mega_gate_sol")));
        assertFalse(terranMegas.stream().anyMatch(m -> m.id().equals("mega_ring_vulcan")));

        // Switch player empire to vulkan_forge
        view.setPlayerEmpireId("vulkan_forge");
        List<com.spaceconquest.engine.megastructure.Megastructure> vulkanMegas = view.getMegastructuresForPlayerEmpire();
        assertEquals(1, vulkanMegas.size());
        assertEquals("mega_ring_vulcan", vulkanMegas.get(0).id());
    }

    @Test
    public void testMegastructureTechnologyGatingAtGameStart() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameState gameState = engine.getGameState();

        EmpireView view = new EmpireView(null);
        view.setPlayerEmpireId("terran_confederation");
        view.updateData(gameState);

        // At game start, stellar_megastructures should NOT be unlocked
        assertFalse(view.isTechnologyUnlocked("stellar_megastructures"),
                "Stellar megastructures requires high-tier research not unlocked at game start");

        // When research is unlocked, verification passes
        Empire empireWithTech = new Empire(
                "terran_confederation", "Terran Confederation", "human", "DEMOCRACY",
                100000.0, 0.15, List.of("sol"),
                List.of(), Map.of(),
                List.of("electricity", "industrial_production", "rocketry", "stellar_megastructures"),
                List.of()
        );
        view.updateData(
                gameState.solarSystems(), List.of(empireWithTech), gameState.systemGovernors(),
                gameState.researchProjects(), gameState.geologicalDeposits(), gameState.powerGrids(),
                gameState.industrialFacilities(), gameState.expansionProjects(), gameState.terraformingProjects(),
                gameState.orbitalStations(), gameState.spaceElevators(), gameState.constructionProjects(),
                gameState.corporations(), gameState.megastructures()
        );

        assertTrue(view.isTechnologyUnlocked("stellar_megastructures"),
                "Stellar megastructures is unlocked after research");
    }

    @Test
    public void testTutorialOnboardingDualTabSupport() {
        TacticalCombatArenaView arenaView = new TacticalCombatArenaView(null, new com.spaceconquest.engine.audio.AudioSynthesizer());
        TutorialOnboardingView tutorialView = new TutorialOnboardingView(null, arenaView);

        assertNotNull(tutorialView.getRoot());
        assertEquals(TutorialOnboardingView.Tab.TUTORIAL, tutorialView.getCurrentTab());

        tutorialView.selectTab(TutorialOnboardingView.Tab.ARENA);
        assertEquals(TutorialOnboardingView.Tab.ARENA, tutorialView.getCurrentTab());

        tutorialView.selectTab(TutorialOnboardingView.Tab.TUTORIAL);
        assertEquals(TutorialOnboardingView.Tab.TUTORIAL, tutorialView.getCurrentTab());
    }
}
