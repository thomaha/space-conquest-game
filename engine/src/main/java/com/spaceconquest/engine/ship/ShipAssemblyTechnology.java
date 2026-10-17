package com.spaceconquest.engine.ship;

import java.util.*;

/** Hull-application production rules; placeholders resolve to the selected structural materials. */
public record ShipAssemblyTechnology(Map<String, Double> frameMaterialsPerKg, Map<String, Double> componentMaterialsPerKg,
        double frameWorkPerKg, double armorWorkPerKg, double componentWorkPerKg, double installationWorkPerSlot, double minimumFrameWork,
        double armorKgPerCapacityCm) {
    public ShipAssemblyTechnology {
        frameMaterialsPerKg = Map.copyOf(frameMaterialsPerKg); componentMaterialsPerKg = Map.copyOf(componentMaterialsPerKg);
        for (var recipe : List.of(frameMaterialsPerKg, componentMaterialsPerKg))
            if (recipe.isEmpty() || recipe.entrySet().stream().anyMatch(entry -> entry.getKey().isBlank() || !Double.isFinite(entry.getValue()) || entry.getValue() <= 0))
                throw new IllegalArgumentException("Invalid structural production recipe");
        for (double value : new double[]{frameWorkPerKg, armorWorkPerKg, componentWorkPerKg, installationWorkPerSlot, minimumFrameWork, armorKgPerCapacityCm})
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid assembly work");
    }
    public ShipProductionReceipt recipe(Map<String, Double> recipe, double mass, String hull, double work) {
        var materials = new HashMap<String, Double>(); recipe.forEach((id, kg) -> materials.merge("@hull".equals(id) ? hull : id, kg * mass, Double::sum));
        return new ShipProductionReceipt(materials, work);
    }
}
