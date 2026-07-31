package com.spaceconquest.engine;

import java.util.List;

/**
 * A complete snapshot of a game session, as written to and read from a save file.
 *
 * @param version      save file format version
 * @param savedAt      ISO-8601 timestamp of when the game was saved
 * @param gameSpeed    game speed setting that was active when saving
 * @param gameTime     ISO-8601 in-game date and time
 * @param solarSystems the galaxy, following the same structure as solar_systems.json
 */
public record SaveGame(
        int version,
        String savedAt,
        int gameSpeed,
        String gameTime,
        List<SolarSystem> solarSystems
) {
    public static final int CURRENT_VERSION = 1;
}
