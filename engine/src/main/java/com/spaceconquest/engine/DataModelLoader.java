package com.spaceconquest.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DataModelLoader {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<SolarSystem> loadSolarSystems() throws IOException {
        try (InputStream is = DataModelLoader.class.getClassLoader().getResourceAsStream("solar_systems.json")) {
            if (is == null) {
                throw new IOException("Resource not found: solar_systems.json");
            }
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }

    public static List<Race> loadRaces() throws IOException {
        try (InputStream is = DataModelLoader.class.getClassLoader().getResourceAsStream("races.json")) {
            if (is == null) {
                throw new IOException("Resource not found: races.json");
            }
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }

    public static List<Material> loadMaterials() throws IOException {
        try (InputStream is = DataModelLoader.class.getClassLoader().getResourceAsStream("materials.json")) {
            if (is == null) {
                throw new IOException("Resource not found: materials.json");
            }
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }

    public static List<Technology> loadTechnologies() throws IOException {
        try (InputStream is = DataModelLoader.class.getClassLoader().getResourceAsStream("technologies.json")) {
            if (is == null) {
                throw new IOException("Resource not found: technologies.json");
            }
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }

    public static List<StarProperty> loadStarProperties() throws IOException {
        try (InputStream is = DataModelLoader.class.getClassLoader().getResourceAsStream("star_properties.json")) {
            if (is == null) {
                throw new IOException("Resource not found: star_properties.json");
            }
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }

    public static SolarSystem loadSolarSystem(String id) throws IOException {
        return loadSolarSystems().stream()
            .filter(ss -> ss.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new IOException("Solar system not found: " + id));
    }
}
