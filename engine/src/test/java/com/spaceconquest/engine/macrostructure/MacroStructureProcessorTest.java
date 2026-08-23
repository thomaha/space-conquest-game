package com.spaceconquest.engine.macrostructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MacroStructureProcessorTest {

    private MacroStructureProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MacroStructureProcessor();
    }

    @Test
    void testStationRequiresActiveControlModule() {
        StationModule offlineControl = new StationModule(
                "mod_ctrl_1", "Control Room", StationModule.TYPE_CONTROL,
                6, 12000.0, 50.0, 0.0, Map.of(), "bureaucrat", 5, false
        );
        OrbitalStation station = new OrbitalStation(
                "station_1", "Alpha Station", "sol", "earth",
                "terran_confederation", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50,
                List.of(offlineControl), Map.of(), 0.0, 0.0,
                500.0, 500.0, 1000.0, 1000.0, "steel", 5.0, true
        );

        MacroStructureProcessor.StationTurnResult result = processor.processOrbitalStation(station, 0.05);
        assertNotNull(result.updatedStation());
        assertFalse(result.updatedStation().isOperational());
    }

    @Test
    void testStationPowerDeficitShedsNonEssentialLoads() {
        StationModule control = new StationModule(
                "mod_ctrl_1", "Control Room", StationModule.TYPE_CONTROL,
                6, 12000.0, 50.0, 0.0, Map.of(), "bureaucrat", 5, true
        );
        StationModule lowPower = new StationModule(
                "mod_pwr_1", "Solar Panel", StationModule.TYPE_POWER,
                10, 5000.0, 0.0, 60.0, Map.of(), "technician", 1, true
        );
        StationModule heavyFoundry = new StationModule(
                "mod_foundry_1", "Heavy Foundry", StationModule.TYPE_METALLURGY_FOUNDRY,
                16, 48000.0, 200.0, 0.0, Map.of(), "industrial_worker", 10, true
        );

        OrbitalStation station = new OrbitalStation(
                "station_1", "Alpha Station", "sol", "earth",
                "terran_confederation", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50,
                List.of(control, lowPower, heavyFoundry), Map.of(), 60.0, 250.0,
                500.0, 500.0, 1000.0, 1000.0, "steel", 5.0, true
        );

        MacroStructureProcessor.StationTurnResult result = processor.processOrbitalStation(station, 0.05);
        assertTrue(result.updatedStation().isOperational());

        // Foundry should be offline due to power deficit
        StationModule updatedFoundry = result.updatedStation().modules().stream()
                .filter(m -> m.id().equals("mod_foundry_1"))
                .findFirst()
                .orElse(null);
        assertNotNull(updatedFoundry);
        assertFalse(updatedFoundry.isOnline());
    }

    @Test
    void testCommerceModuleRequiresCivilianHangar() {
        StationModule control = new StationModule(
                "mod_ctrl_1", "Control Room", StationModule.TYPE_CONTROL,
                6, 12000.0, 50.0, 0.0, Map.of(), "bureaucrat", 5, true
        );
        StationModule power = new StationModule(
                "mod_pwr_1", "Fusion Core", StationModule.TYPE_POWER,
                10, 20000.0, 0.0, 500.0, Map.of(), "technician", 4, true
        );
        StationModule commerce = new StationModule(
                "mod_comm_1", "Trade Terminal", StationModule.TYPE_COMMERCE,
                8, 14000.0, 20.0, 0.0, Map.of(), "bureaucrat", 3, true
        );

        // Without civilian hangar -> 0 tariff collected
        OrbitalStation stationNoHangar = new OrbitalStation(
                "station_1", "Alpha Station", "sol", "earth",
                "terran_confederation", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50,
                List.of(control, power, commerce), Map.of(), 500.0, 70.0,
                500.0, 500.0, 1000.0, 1000.0, "steel", 5.0, true
        );
        MacroStructureProcessor.StationTurnResult res1 = processor.processOrbitalStation(stationNoHangar, 0.05);
        assertEquals(0.0, res1.collectedTariffCredits(), 0.001);

        // With civilian hangar -> tariff collected
        StationModule hangar = new StationModule(
                "mod_hangar_1", "Civilian Berths", StationModule.TYPE_CIVILIAN_HANGAR,
                12, 28000.0, 30.0, 0.0, Map.of(), "technician", 5, true
        );
        OrbitalStation stationWithHangar = new OrbitalStation(
                "station_1", "Alpha Station", "sol", "earth",
                "terran_confederation", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50,
                List.of(control, power, commerce, hangar), Map.of(), 500.0, 100.0,
                500.0, 500.0, 1000.0, 1000.0, "steel", 5.0, true
        );
        MacroStructureProcessor.StationTurnResult res2 = processor.processOrbitalStation(stationWithHangar, 0.05);
        assertTrue(res2.collectedTariffCredits() > 0.0);
    }

    @Test
    void testSpaceElevatorLaunchCostDiscount() {
        SpaceElevator elevator = new SpaceElevator(
                "elevator_earth", "earth", "terran_confederation",
                100000.0, 0.95, 100.0, true
        );

        double standardGravityCost = 10000.0;
        double discountedCost = processor.calculateDiscountedLaunchCost(elevator, standardGravityCost);

        assertEquals(500.0, discountedCost, 0.001); // 95% discount -> 500 credits
    }

    @Test
    void testConstructionDeploymentProgression() {
        ConstructionDeploymentProject project = new ConstructionDeploymentProject(
                "proj_station_1", "const_ship_1", "sol", "mars",
                ConstructionDeploymentProject.TYPE_ORBITAL_STATION, 1.0, 2.0, Map.of(), false
        );

        MacroStructureProcessor.ConstructionTurnResult result = processor.advanceConstructionProjects(
                List.of(project), "terran_confederation"
        );

        assertEquals(0, result.remainingProjects().size());
        assertEquals(1, result.newlyCompletedStations().size());
        assertEquals("mars", result.newlyCompletedStations().get(0).planetOrbitId());
    }
}
