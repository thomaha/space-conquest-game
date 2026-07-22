package com.spaceconquest.frontend;

import com.almasb.fxgl.entity.Entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds the celestial bodies that can be searched, focused and inspected,
 * together with their display type and pre-formatted info text.
 */
public class GalaxyRegistry {
    private final Map<String, Entity> entities = new HashMap<>();
    private final Map<String, String> info = new HashMap<>();
    private final Map<String, String> type = new HashMap<>();

    public void clear() {
        entities.clear();
        info.clear();
        type.clear();
    }

    public void register(String name, Entity entity, String bodyType, String infoText) {
        String key = name.toLowerCase();
        entities.put(key, entity);
        type.put(key, bodyType);
        info.put(key, infoText);
    }

    public Entity get(String name) {
        return entities.get(name.toLowerCase());
    }

    public String getInfo(String name) {
        return info.get(name.toLowerCase());
    }

    public String getType(String name) {
        return type.getOrDefault(name.toLowerCase(), "body");
    }

    public Set<String> names() {
        return entities.keySet();
    }

    public Collection<Entity> entities() {
        return entities.values();
    }

    /**
     * Returns the pre-formatted info text for the given entity, or {@code null}
     * if the entity is not registered.
     */
    public String infoFor(Entity entity) {
        for (Map.Entry<String, Entity> en : entities.entrySet()) {
            if (en.getValue() == entity) {
                return info.get(en.getKey());
            }
        }
        return null;
    }
}
