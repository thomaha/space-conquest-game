package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import java.util.*;
import java.io.*;

/** Mining hardware and operating limits loaded exclusively from technological applications. */
public final class ShipMiningModuleCatalog {
    private record Entry(String foundation, TechnicalApplication application) {}
    private static final class Loaded {
        static final Map<String, Entry> ENTRIES = read();
        private static Map<String, Entry> read() {
            try {
                var entries = new LinkedHashMap<String, Entry>();
                for (var technology : DataModelLoader.loadTechnologies()) for (var app : technology.applications()) {
                    var module = app.miningModule(); if (module == null) continue;
                    for (String stat : List.of("miningRateKgPerTurn", "maximumGravityMps2", "maximumDiameterKm"))
                        if (!Double.isFinite(module.operationalStats().getOrDefault(stat, Double.NaN)) || module.operationalStats().get(stat) <= 0)
                            throw new IllegalArgumentException("Missing mining capability " + stat + " in " + app.id());
                    if (module.powerDrawKw() <= 0 || module.slotCost() <= 0 || module.dryMassKg() <= 0 || module.materialCostsKg().isEmpty()
                            || app.costToBuildPerUnit() <= 0 || entries.put(module.id(), new Entry(technology.id(), app)) != null)
                        throw new IllegalArgumentException("Invalid mining hardware in " + app.id());
                }
                return Collections.unmodifiableMap(entries);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    private ShipMiningModuleCatalog() {}
    public static List<String> ids() { return List.copyOf(Loaded.ENTRIES.keySet()); }
    public static TechnicalApplication application(String id) { var entry = Loaded.ENTRIES.get(id); return entry == null ? null : entry.application(); }
    public static ShipModule module(String id) { var app = application(id); return app == null ? null : app.miningModule(); }
    public static boolean researched(List<String> modules, List<String> known) {
        return modules.stream().allMatch(id -> {
            var entry = Loaded.ENTRIES.get(id);
            return entry == null || known.contains(entry.foundation()) && known.contains(entry.application().id())
                    && known.containsAll(entry.application().requiredTechnologies());
        });
    }
}
