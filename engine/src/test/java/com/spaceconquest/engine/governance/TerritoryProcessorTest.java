package com.spaceconquest.engine.governance;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TerritoryProcessorTest {

    private TerritoryProcessor territoryProcessor;
    private DiplomacyProcessor diplomacyProcessor;

    @BeforeEach
    public void setUp() {
        diplomacyProcessor = new DiplomacyProcessor();
        territoryProcessor = new TerritoryProcessor(diplomacyProcessor);
    }

    @Test
    public void testDynamicTerritorialControl() {
        // Setup systems
        SolarSystem sys1 = new SolarSystem("sys1", "Sol", "", 0, 0, 0, 1, 1, "yellow", 
                List.of(new Planet("p1", "Earth", "", 1, 1, 1, 0, 12742, "terrestrial", "", true, 0.7, List.of(), List.of(),
                        List.of(new Population("human", Map.of(20, 8_000_000_000L))))), 
                List.of());
        
        SolarSystem sys2 = new SolarSystem("sys2", "Alpha Centauri", "", 4, 0, 0, 1, 1, "yellow", 
                List.of(new Planet("p2", "Centauri Prime", "", 1, 1, 1, 0, 12000, "terrestrial", "", true, 0.5, List.of(), List.of(),
                        List.of(new Population("silicon", Map.of(20, 5_000_000_000L))))), 
                List.of());

        List<SolarSystem> systems = List.of(sys1, sys2);

        // Setup economies to link populations to empires
        com.spaceconquest.engine.economy.SystemEconomy eco1 = com.spaceconquest.engine.economy.SystemEconomy.createDefault("sys1", "terran", 8_000_000_000L);
        com.spaceconquest.engine.economy.SystemEconomy eco2 = com.spaceconquest.engine.economy.SystemEconomy.createDefault("sys2", "silicon_core", 5_000_000_000L);
        List<com.spaceconquest.engine.economy.SystemEconomy> economies = List.of(eco1, eco2);

        // Setup empires
        Empire terran = new Empire("terran", "Terran Confederation", "human", "Individualist", 1000, 0.1, List.of(), List.of(), Map.of(), List.of(), List.of());
        Empire silicon = new Empire("silicon_core", "Silicon Core", "silicon", "Collectivist", 1000, 0.1, List.of(), List.of(), Map.of(), List.of(), List.of());
        List<Empire> empires = List.of(terran, silicon);

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(systems)
                .empires(empires)
                .systemEconomies(economies)
                .build();

        // 1. Initial check: control should be assigned based on population
        List<Empire> updatedEmpires = territoryProcessor.updateTerritorialControl(state);
        
        Empire updatedTerran = updatedEmpires.stream().filter(e -> e.id().equals("terran")).findFirst().orElseThrow();
        Empire updatedSilicon = updatedEmpires.stream().filter(e -> e.id().equals("silicon_core")).findFirst().orElseThrow();

        assertTrue(updatedTerran.controlledSystemIds().contains("sys1"));
        assertFalse(updatedTerran.controlledSystemIds().contains("sys2"));
        assertTrue(updatedSilicon.controlledSystemIds().contains("sys2"));
        assertFalse(updatedSilicon.controlledSystemIds().contains("sys1"));

        // 2. Add military presence to flip sys2 to Terran control
        // Silicon has 5B pop -> log10(5B) ~ 9.7 influence
        // Terran needs to beat 9.7 with just military hangar (hangar mod = count * 0.5)
        // Let's add a Terran station in sys2 with 20 military hangars -> 10.0 influence
        StationModule hangar = new StationModule("h1", "Hangar", StationModule.TYPE_MILITARY_HANGAR, 1, 100, 10, 0, Map.of(), "soldier", 10, true);
        java.util.ArrayList<StationModule> modules = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) modules.add(hangar);
        
        OrbitalStation terranStation = new OrbitalStation("s1", "Forward Base", "sys2", "p2", "terran", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50, modules, Map.of(), 100, 100, 1000, 1000, 1000, 1000, "steel", 10, true);
        
        GameState stateWithMilitary = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(systems)
                .empires(empires)
                .orbitalStations(List.of(terranStation))
                .systemEconomies(economies)
                .build();

        List<Empire> militaryUpdatedEmpires = territoryProcessor.updateTerritorialControl(stateWithMilitary);
        Empire terranWithMilitary = militaryUpdatedEmpires.stream().filter(e -> e.id().equals("terran")).findFirst().orElseThrow();
        
        assertTrue(terranWithMilitary.controlledSystemIds().contains("sys1"));
        assertTrue(terranWithMilitary.controlledSystemIds().contains("sys2"), "Terran should now control sys2 due to superior military influence");
    }
}
