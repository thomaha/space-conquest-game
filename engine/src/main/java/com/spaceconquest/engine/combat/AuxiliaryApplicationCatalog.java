package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Auxiliary infrastructure definitions and development gates loaded from technologies.json. */
public final class AuxiliaryApplicationCatalog {
    private record Definition(String technology, TechnicalApplication app) {}
    private static final Map<String, Definition> DEFINITIONS = load();
    private AuxiliaryApplicationCatalog() {}
    private static Map<String, Definition> load() {
        var result = new LinkedHashMap<String, Definition>();
        try {
            for (var tech : DataModelLoader.loadTechnologies()) for (var app : tech.applications())
                if (app.auxiliaryHardware() != null) {
                    CombatApplicationValidation.validate(app);
                    if (!Double.isFinite(app.costToBuildPerUnit()) || app.costToBuildPerUnit() <= 0 || app.complexity() <= 0
                            || result.putIfAbsent(app.id(), new Definition(tech.id(), app)) != null)
                        throw new IllegalArgumentException("Invalid auxiliary application: " + app.id());
                }
        } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot load auxiliary applications", ex); }
        return Map.copyOf(result);
    }
    public static List<TechnicalApplication> applications() { return DEFINITIONS.values().stream().map(Definition::app).toList(); }
    public static TechnicalApplication application(String id) {
        var definition = DEFINITIONS.get(id); return definition == null ? null : definition.app();
    }
    public static AuxiliaryHardware baseline(String kind) {
        var app = applications().stream().filter(a -> a.auxiliaryHardware().kind().equals(kind)).findFirst().orElseThrow();
        return new AuxiliaryHardware(kind, app.auxiliaryHardware().materialCostsKg(), app.auxiliaryHardware().stats(), app.id());
    }
    public static boolean researched(GameState state, String owner, String id) {
        var definition = DEFINITIONS.get(id);
        return definition != null && state.empires().stream().anyMatch(e -> e.id().equals(owner)
                && e.unlockedTechIds().contains(definition.technology()) && e.unlockedTechIds().contains(id)
                && e.unlockedTechIds().containsAll(definition.app().requiredTechnologies()));
    }
    public static AuxiliaryHardware capture(GameState state, String owner, String id) {
        var app = application(id); var hardware = app.auxiliaryHardware(); var stats = new HashMap<>(hardware.stats());
        var modifiers = ApplicationProduction.modifiersForOwner(state, owner, id);
        for (String key : app.effectStats()) stats.computeIfPresent(key, (ignored, value) -> value * modifiers.effectMultiplier());
        return new AuxiliaryHardware(hardware.kind(), hardware.materialCostsKg(), stats, app.id());
    }
    public static int hangarCapacity(GameState state, Fleet fleet, ShipInstance ship) {
        var design = state.shipDesigns().stream().filter(d -> d.id().equals(ship.designId())).findFirst().orElse(null);
        return design == null || !fleet.ownerEntityId().equals(ship.ownerEntityId()) ? 0
                : (int) Math.floor(ship.moduleDamage().capability(design, "hangarCraftCapacity"));
    }
}
