package com.spaceconquest.engine;

import com.spaceconquest.engine.economy.SystemEconomy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GameStateTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGameStateDefaults() {
        GameState state = new GameState();
        assertEquals(0, state.turn());
        assertEquals("INITIALIZING", state.status());
        assertNotNull(state.solarSystems());
        assertNotNull(state.empires());
        assertNotNull(state.corporations());
        assertNotNull(state.commercialHubs());
        assertNotNull(state.shadowSyndicates());
        assertNotNull(state.diplomaticRelations());
        assertNotNull(state.systemGovernors());
    }

    @Test
    public void testSaveGameSerializationRoundTrip() throws IOException {
        SaveGameManager manager = new SaveGameManager(tempDir);

        Empire empire = new Empire(
                "terran",
                "Terran Confederation",
                "human",
                "Individualist",
                50000.0,
                0.15,
                List.of("sol"),
                List.of(new MinistryAssignment("ministry_industry_refining", "miner", 1.25)),
                Map.of("sol", "gov_1"),
                List.of("tech_1"),
                List.of("ship_1")
        );

        Corporation corp = new Corporation(
                "corp_1",
                "Mining Syndicate",
                "terran",
                "earth",
                "EXTRACTION",
                25000.0,
                List.of("fac_1"),
                List.of("ship_miner"),
                List.of("vein_1")
        );

        CommercialHub hub = new CommercialHub(
                "hub_1",
                "earth",
                0.05,
                100000.0,
                2500.0,
                10.0,
                Map.of("iron_ore", new MarketOrder("iron_ore", 100.0, 50.0, 10.0, 0.0))
        );

        ShadowSyndicate shadow = new ShadowSyndicate(
                "syndicate_1",
                "Red Sun Syndicate",
                "terran",
                "sol",
                5000.0,
                List.of("rogue_1")
        );

        DiplomaticRelation relation = new DiplomaticRelation(
                "terran",
                "silicon",
                "COMMERCIAL_ALLIANCE",
                0.50
        );

        SystemGovernor governor = new SystemGovernor(
                "gov_1",
                "Governor Marcus",
                "sol",
                "miner",
                1.15,
                0.20
        );

        SystemEconomy economy = new SystemEconomy(
                "sol",
                "terran",
                0.30, 0.20, 0.20, 0.15, 0.15,
                4500.0,
                8000.0,
                1.5, 1.0, 1.0, 0.75, 0.75,
                50, 30, 80, 40, 100, 150, 120, 500, 0.10
        );

        GameState state = new GameState(
                5,
                "RUNNING",
                List.of(),
                List.of(empire),
                List.of(corp),
                List.of(hub),
                List.of(shadow),
                List.of(relation),
                List.of(governor),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(economy)
        );

        File saveFile = tempDir.resolve("test_save.scsave").toFile();
        manager.save(saveFile, state, 1, "2026-08-22T00:00:00Z");

        SaveGame loaded = manager.load(saveFile);
        assertNotNull(loaded);
        assertEquals(SaveGame.CURRENT_VERSION, loaded.version());
        assertEquals("2026-08-22T00:00:00Z", loaded.gameTime());
        assertEquals(1, loaded.empires().size());
        assertEquals("terran", loaded.empires().getFirst().id());
        assertEquals(1, loaded.corporations().size());
        assertEquals("corp_1", loaded.corporations().getFirst().id());
        assertEquals(1, loaded.commercialHubs().size());
        assertEquals("hub_1", loaded.commercialHubs().getFirst().id());
        assertEquals(1, loaded.shadowSyndicates().size());
        assertEquals("syndicate_1", loaded.shadowSyndicates().getFirst().id());
        assertEquals(1, loaded.diplomaticRelations().size());
        assertEquals("COMMERCIAL_ALLIANCE", loaded.diplomaticRelations().getFirst().tier());
        assertEquals(1, loaded.systemGovernors().size());
        assertEquals("gov_1", loaded.systemGovernors().getFirst().id());
        assertEquals(1, loaded.systemEconomies().size());
        assertEquals("sol", loaded.systemEconomies().getFirst().systemId());
        assertEquals(0.30, loaded.systemEconomies().getFirst().educationAllocation(), 0.001);
        assertEquals(4500.0, loaded.systemEconomies().getFirst().totalBudgetCredits(), 0.001);
        assertEquals(8000.0, loaded.systemEconomies().getFirst().accumulatedMilitiaInvestment(), 0.001);
    }
}
