package com.spaceconquest.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the static game data model from the JSON property files in the resources folder.
 * Loaded lists are cached, since the property files are immutable during a game session.
 */
public class DataModelLoader {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Map<String, List<?>> cache = new ConcurrentHashMap<>();

    private DataModelLoader() {
    }

    public static List<SolarSystem> loadSolarSystems() throws IOException {
        return load("solar_systems.json", new TypeReference<>() {});
    }

    public static List<Race> loadRaces() throws IOException {
        return load("races.json", new TypeReference<>() {});
    }

    public static List<Material> loadMaterials() throws IOException {
        return load("materials.json", new TypeReference<>() {});
    }

    public static List<Technology> loadTechnologies() throws IOException {
        return load("technologies.json", new TypeReference<>() {});
    }

    public static List<StarProperty> loadStarProperties() throws IOException {
        return load("star_properties.json", new TypeReference<>() {});
    }

    public static List<Profession> loadProfessions() throws IOException {
        return load("professions.json", new TypeReference<>() {});
    }

    public static List<Empire> loadEmpires() throws IOException {
        return load("empires.json", new TypeReference<>() {});
    }

    public static List<Corporation> loadCorporations() throws IOException {
        return load("corporations.json", new TypeReference<>() {});
    }

    public static List<MinistryPortfolio> loadMinistries() throws IOException {
        return load("ministries.json", new TypeReference<>() {});
    }

    public static Empire loadEmpire(String id) throws IOException {
        return loadEmpires().stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IOException("Empire not found: " + id));
    }

    public static Corporation loadCorporation(String id) throws IOException {
        return loadCorporations().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IOException("Corporation not found: " + id));
    }

    public static MinistryPortfolio loadMinistry(String id) throws IOException {
        return loadMinistries().stream()
                .filter(m -> m.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IOException("Ministry portfolio not found: " + id));
    }

    public static SolarSystem loadSolarSystem(String id) throws IOException {
        return loadSolarSystems().stream()
                .filter(ss -> ss.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IOException("Solar system not found: " + id));
    }

    /**
     * Clears the resource cache. Intended for tests and for reloading modified data files.
     */
    public static void clearCache() {
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> load(String resource, TypeReference<List<T>> type) throws IOException {
        List<?> cached = cache.get(resource);
        if (cached != null) {
            return (List<T>) cached;
        }
        try (InputStream is = DataModelLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resource);
            }
            List<T> values = objectMapper.readValue(is, type);
            cache.put(resource, values);
            return values;
        }
    }
}
