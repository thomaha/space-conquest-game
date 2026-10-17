package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.io.*;
import java.util.*;

/** Application-defined structural capacity, independent of ship roles and class names. */
public record HullTechnology(boolean baseline, int defaultCapacity, int maximumCapacity,
        double frameKgPerCapacity, double baseStructuralStrength, double structuralStrengthMultiplier, ShipAssemblyTechnology assemblyTechnology,
        ShipRefitTechnology refitTechnology, ShipRetirementTechnology retirementTechnology) {
    public HullTechnology(boolean baseline, int defaultCapacity, int maximumCapacity, double frameKgPerCapacity,
            double baseStructuralStrength, double structuralStrengthMultiplier, ShipAssemblyTechnology assemblyTechnology, ShipRefitTechnology refitTechnology) {
        this(baseline, defaultCapacity, maximumCapacity, frameKgPerCapacity, baseStructuralStrength, structuralStrengthMultiplier,
                assemblyTechnology, refitTechnology, null);
    }
    public HullTechnology {
        if (defaultCapacity < 1 || maximumCapacity < defaultCapacity || !Double.isFinite(frameKgPerCapacity) || frameKgPerCapacity <= 0
                || !Double.isFinite(baseStructuralStrength) || baseStructuralStrength <= 0
                || !Double.isFinite(structuralStrengthMultiplier) || structuralStrengthMultiplier <= 0)
            throw new IllegalArgumentException("Invalid hull engineering capability");
    }
    private record Entry(String foundation, TechnicalApplication application) {}
    private static final class Loaded {
        static final List<Entry> ENTRIES = read();
        static List<Entry> read() {
            try {
                var result = new ArrayList<Entry>();
                for (var technology : DataModelLoader.loadTechnologies()) for (var app : technology.applications())
                    if (app.hullTechnology() != null) result.add(new Entry(technology.id(), app));
                if (result.stream().filter(entry -> entry.application().hullTechnology().baseline()).count() != 1)
                    throw new IllegalArgumentException("Exactly one baseline hull application is required");
                return List.copyOf(result);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    public static int initialCapacity() {
        return Loaded.ENTRIES.stream().filter(entry -> entry.application().hullTechnology().baseline()).findFirst().orElseThrow().application().hullTechnology().defaultCapacity();
    }
    public static ShipAssemblyTechnology assemblyTechnology(ShipHullFrame frame) {
        var baseline = Loaded.ENTRIES.stream().filter(entry -> entry.application().hullTechnology().baseline()).findFirst().orElseThrow().application().hullTechnology().assemblyTechnology();
        var specific = Loaded.ENTRIES.stream().filter(entry -> entry.application().id().equals(frame.applicationId()))
                .map(entry -> entry.application().hullTechnology().assemblyTechnology()).filter(Objects::nonNull).findFirst().orElse(baseline);
        if (specific == null) throw new IllegalStateException("Missing application assembly rules");
        return specific;
    }
    public static ShipRefitTechnology refitTechnology(ShipHullFrame frame) {
        var baseline = Loaded.ENTRIES.stream().filter(entry -> entry.application().hullTechnology().baseline()).findFirst().orElseThrow().application().hullTechnology().refitTechnology();
        var specific = Loaded.ENTRIES.stream().filter(entry -> entry.application().id().equals(frame.applicationId()))
                .map(entry -> entry.application().hullTechnology().refitTechnology()).filter(Objects::nonNull).findFirst().orElse(baseline);
        if (specific == null) throw new IllegalStateException("Missing application refit rules");
        return specific;
    }
    public static ShipRetirementTechnology retirementTechnology(ShipHullFrame frame) {
        var baseline = Loaded.ENTRIES.stream().filter(entry -> entry.application().hullTechnology().baseline()).findFirst().orElseThrow().application().hullTechnology().retirementTechnology();
        var specific = Loaded.ENTRIES.stream().filter(entry -> entry.application().id().equals(frame.applicationId()))
                .map(entry -> entry.application().hullTechnology().retirementTechnology()).filter(Objects::nonNull).findFirst().orElse(baseline);
        if (specific == null) throw new IllegalStateException("Missing application retirement rules");
        return specific;
    }
    private static List<Entry> available(GameState state, String ownerId) {
        var owner = state == null ? null : com.spaceconquest.engine.economy.EconomicOwnership.empireFor(state, ownerId);
        if (owner == null) return List.of();
        return Loaded.ENTRIES.stream().filter(entry -> entry.application().hullTechnology().baseline()
                || owner.unlockedTechIds().contains(entry.foundation()) && owner.unlockedTechIds().contains(entry.application().id())
                && owner.unlockedTechIds().containsAll(entry.application().requiredTechnologies())).toList();
    }
    private static int limit(GameState state, String ownerId, Entry entry) {
        double effect = ApplicationProduction.modifiersForOwner(state, ownerId, entry.application().id()).effectMultiplier();
        double limit = Math.floor(entry.application().hullTechnology().maximumCapacity() * effect);
        if (!Double.isFinite(limit) || limit < 1 || limit > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid researched hull capacity");
        return (int) limit;
    }
    public static int maximumCapacity(GameState state, String ownerId) {
        return available(state, ownerId).stream().mapToInt(entry -> limit(state, ownerId, entry)).max().orElse(0);
    }
    public static boolean canBuild(GameState state, String ownerId, ShipHullFrame frame) {
        return frame == null || available(state, ownerId).stream().anyMatch(entry -> entry.application().id().equals(frame.applicationId())
                && frame.totalSlots() <= limit(state, ownerId, entry));
    }
    public static ShipHullFrame frame(GameState state, String ownerId, String material, int capacity) {
        if (capacity < 1) return null;
        var entry = available(state, ownerId).stream().filter(item -> capacity <= limit(state, ownerId, item))
                .max(Comparator.comparingDouble(item -> item.application().hullTechnology().structuralStrengthMultiplier()
                        * ApplicationProduction.modifiersForOwner(state, ownerId, item.application().id()).effectMultiplier())).orElse(null);
        if (entry == null) return null;
        var data = entry.application().hullTechnology();
        double effect = ApplicationProduction.modifiersForOwner(state, ownerId, entry.application().id()).effectMultiplier();
        return new ShipHullFrame("hull_" + capacity, "Custom hull", capacity, material, capacity * data.frameKgPerCapacity(),
                data.baseStructuralStrength(), entry.application().id(), limit(state, ownerId, entry), data.structuralStrengthMultiplier() * effect);
    }
}
