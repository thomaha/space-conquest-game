package com.spaceconquest.engine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes and reads game saves as JSON files, using the same structure as the
 * static data property files in the resources folder.
 */
public class SaveGameManager {

    public static final String SAVE_EXTENSION = ".scsave";

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path saveDirectory;

    public SaveGameManager() {
        this(Paths.get("saves"));
    }

    public SaveGameManager(Path saveDirectory) {
        this.saveDirectory = saveDirectory;
    }

    public Path getSaveDirectory() {
        return saveDirectory;
    }

    /**
     * Saves the given galaxy to the given file.
     */
    public void save(File file, List<SolarSystem> solarSystems, int gameSpeed, String gameTime) throws IOException {
        if (solarSystems == null) {
            throw new IOException("Nothing to save: no galaxy loaded");
        }
        save(file, GameState.builder().solarSystems(solarSystems).build(), gameSpeed, gameTime);
    }

    /**
     * Saves the given galaxy to a file with the given name inside the save directory.
     */
    public Path save(String name, List<SolarSystem> solarSystems, int gameSpeed, String gameTime) throws IOException {
        File target = withExtension(saveDirectory.resolve(name).toFile());
        save(target, solarSystems, gameSpeed, gameTime);
        return target.toPath();
    }

    /**
     * Saves the given game state to the given file.
     */
    public void save(File file, GameState gameState, int gameSpeed, String gameTime) throws IOException {
        if (gameState == null || gameState.solarSystems() == null) {
            throw new IOException("Nothing to save: no game state loaded");
        }
        File target = withExtension(file);
        if (target.getParentFile() != null) {
            Files.createDirectories(target.getParentFile().toPath());
        }
        SaveGame save = SaveGame.fromGameState(gameState, Instant.now().toString(), gameSpeed, gameTime);
        mapper.writeValue(target, save);
    }

    /**
     * Saves the given game state to a file with the given name inside the save directory.
     */
    public Path save(String name, GameState gameState, int gameSpeed, String gameTime) throws IOException {
        File target = withExtension(saveDirectory.resolve(name).toFile());
        save(target, gameState, gameSpeed, gameTime);
        return target.toPath();
    }

    /**
     * Performs a quick save to the standard quicksave slot.
     */
    public Path quickSave(GameState gameState, int gameSpeed, String gameTime) throws IOException {
        return save("quicksave", gameState, gameSpeed, gameTime);
    }

    /**
     * Performs an auto save to the standard autosave slot.
     */
    public Path autoSave(GameState gameState, int gameSpeed, String gameTime) throws IOException {
        return save("autosave", gameState, gameSpeed, gameTime);
    }

    /**
     * Deletes a save file by name.
     */
    public boolean deleteSave(String name) {
        File target = withExtension(saveDirectory.resolve(name).toFile());
        if (target.exists() && target.isFile()) {
            return target.delete();
        }
        return false;
    }

    /**
     * Loads a save from the given name inside the save directory.
     */
    public SaveGame load(String name) throws IOException {
        File target = withExtension(saveDirectory.resolve(name).toFile());
        return load(target);
    }

    /**
     * Loads a save from the given file.
     */
    public SaveGame load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Save file not found: " + file);
        }
        SaveGame save = mapper.readValue(file, SaveGame.class);
        if (save == null || save.solarSystems() == null) {
            throw new IOException("Invalid save file: " + file);
        }
        if (save.version() > SaveGame.CURRENT_VERSION) {
            throw new IOException("Save file version " + save.version() + " is not supported");
        }
        return save;
    }

    /**
     * Lists all save files in the save directory, newest first.
     */
    public List<Path> listSaves() throws IOException {
        if (!Files.isDirectory(saveDirectory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(saveDirectory)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(SAVE_EXTENSION))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .toList();
        }
    }

    private static File withExtension(File file) {
        if (file.getName().endsWith(SAVE_EXTENSION)) {
            return file;
        }
        return file.getParentFile() == null
                ? new File(file.getName() + SAVE_EXTENSION)
                : new File(file.getParentFile(), file.getName() + SAVE_EXTENSION);
    }
}
