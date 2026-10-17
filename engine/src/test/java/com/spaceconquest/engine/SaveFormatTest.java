package com.spaceconquest.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SaveFormatTest {
    @TempDir Path directory;
    @Test void freshFormatStartsAtOneAndCurrentStateRoundTripsWithoutVersionMigrations() throws Exception {
        var manager = new SaveGameManager(directory); var state = GameState.builder().build();
        var file = manager.save("current", state, 2, GameClock.START_TIME.toString());
        assertEquals(1, new ObjectMapper().readTree(file.toFile()).get("version").intValue());
        var save = manager.load(file.toFile()); assertEquals(1, save.version());
        assertEquals(state, save.toGameState(state.turn(), state.status()));
        assertEquals(GameClock.START_TIME, save.resolvedCampaignStartTime());
    }
    @Test void onlyAnExplicitIntegralCurrentVersionIsAcceptedBeforePayloadDeserialization() throws Exception {
        var manager = new SaveGameManager(directory); var file = directory.resolve("unsupported.scsave");
        for (String version : new String[]{"-1", "0", "2", "9", "22", "64", "71", "2147483648", "1.5", "1.0", "\"1\"", "null"}) {
            Files.writeString(file, "{\"version\":" + version + ",\"solarSystems\":\"invalid payload\"}");
            var error = assertThrows(IOException.class, () -> manager.load(file.toFile()), version);
            assertTrue(error.getMessage().contains("Unsupported save format version"), version);
        }
        Files.writeString(file, "{\"solarSystems\":[]}");
        assertTrue(assertThrows(IOException.class, () -> manager.load(file.toFile())).getMessage().contains("(missing)"));
        var mapper = new ObjectMapper(); var json = mapper.valueToTree(SaveGame.fromGameState(GameState.builder().build(), "now", 1, "now"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("version", 71);
        assertThrows(IOException.class, () -> mapper.treeToValue(json, SaveGame.class));
    }
}
