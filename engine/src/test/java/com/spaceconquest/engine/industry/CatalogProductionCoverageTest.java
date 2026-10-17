package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.ApplicationIntegration;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Catalog coverage checks distinguish declared production from actual lifecycle evidence and future effects. */
class CatalogProductionCoverageTest {
    @Test void everyProductionApplicationOwnsItsFactoryAndHasAConservedObtainableMaterialPath() throws Exception {
        var technologies = DataModelLoader.loadTechnologies();
        var materials = new TreeMap<String, Material>(); DataModelLoader.loadMaterials().forEach(m -> materials.put(m.id(), m));
        var apps = new TreeMap<String, TechnicalApplication>(); technologies.forEach(t -> t.applications().forEach(a -> apps.put(a.id(), a)));
        var recipes = IndustryRecipeCatalog.all().stream().sorted(Comparator.comparing(IndustryRecipeCatalog.Recipe::applicationId)).toList();
        var reachable = new TreeSet<String>();
        boolean changed;
        do {
            changed = false;
            for (var recipe : recipes) if (reachable.containsAll(recipe.inputsKg().keySet())) changed |= reachable.addAll(recipe.outputsKg().keySet());
        } while (changed);
        var report = new StringBuilder("# Production coverage and remaining gaps\n\n")
                .append("All research, installed equipment, site access, discovered declared deposits, power, workers and cash are assumed for graph closure. ")
                .append("A graph path is not proof of funded construction, resource discovery or profitable campaign operation. ")
                .append("Every catalog recipe also runs through the physical market-settlement test in ProductionChainAuditTest. ")
                .append("Separate command lifecycle tests cover research, material-funded construction, reload and actual delivered products.\n\n")
                .append("| Application | Definition | Missing purchased inputs | Missing construction materials | Elemental check |\n")
                .append("|---|---|---|---|---|\n");
        var violations = new ArrayList<String>();
        for (var recipe : recipes) {
            var app = apps.get(recipe.applicationId()); assertNotNull(app, recipe.applicationId());
            assertEquals(app.productionRecipe(), recipe);
            assertNotNull(recipe.infrastructure(), recipe.applicationId());
            assertNotNull(ProductionWork.definition(recipe.applicationId()), recipe.applicationId());
            assertNotNull(app.operatingReserve(), recipe.applicationId());
            var missing = new TreeSet<>(recipe.inputsKg().keySet()); missing.removeAll(reachable);
            var construction = new TreeSet<>(recipe.infrastructure().constructionMaterialsKg().keySet()); construction.removeAll(reachable);
            assertTrue(missing.isEmpty(), recipe.applicationId() + " missing inputs " + missing);
            assertTrue(construction.isEmpty(), recipe.applicationId() + " missing construction " + construction);
            var inputMass = new HashMap<String, Double>();
            recipe.inputSources().forEach(source -> addElements(inputMass, materials.get(source.materialId()), source.kgPerBatch()));
            var outputs = new HashMap<String, Double>(); recipe.outputsKg().forEach((id, kg) -> addElements(outputs, materials.get(id), kg));
            var created = new TreeMap<String, Double>();
            outputs.forEach((element, kg) -> { double excess = kg - inputMass.getOrDefault(element, 0.0);
                if (excess > Math.max(1e-7, kg * 1e-8)) created.put(element, excess); });
            if (!created.isEmpty()) violations.add(recipe.applicationId() + " creates " + created);
            report.append("| ").append(recipe.applicationId()).append(" | ").append(recipe.farming() == null ? "Factory" : "Cultivation")
                    .append(" | ").append(missing).append(" | ").append(construction).append(" | ").append(created.isEmpty() ? "Conserved" : created).append(" |\n");
        }
        report.append("\n## Materials without a bulk industrial market path\n\n| Material | Natural source declared | Applications declaring the material |\n|---|---|---|\n");
        for (var material : materials.values()) if (!reachable.contains(material.id())) {
            var consumers = apps.values().stream().filter(a -> a.requiredMaterials().contains(material.id())).map(TechnicalApplication::id).toList();
            report.append("| ").append(material.id()).append(" | ").append(material.foundInNature()).append(" | ").append(consumers).append(" |\n");
        }
        report.append("\n## Declared hardware and construction requirements without industrial supply\n\n| Application | Missing materials |\n|---|---|\n");
        for (var app : apps.values()) {
            var needs = new TreeSet<>(app.requiredMaterials());
            if (app.combatModule() != null) needs.addAll(app.combatModule().materialCostsKg().keySet());
            if (app.miningModule() != null) needs.addAll(app.miningModule().materialCostsKg().keySet());
            if (app.auxiliaryHardware() != null) needs.addAll(app.auxiliaryHardware().materialCostsKg().keySet());
            if (app.powerPlant() != null) needs.addAll(app.powerPlant().constructionMaterialsKg().keySet());
            if (app.shipyardTechnology() != null) app.shipyardTechnology().yards().values().forEach(y -> needs.addAll(y.materialsKg().keySet()));
            assertTrue(materials.keySet().containsAll(needs), app.id() + " references an undefined material");
            needs.removeAll(reachable);
            if (!needs.isEmpty()) report.append("| ").append(app.id()).append(" | ").append(needs).append(" |\n");
        }
        report.append("\n## Applications with no operational consumer\n\n");
        apps.values().stream().filter(a -> ApplicationIntegration.describe(a).status() == ApplicationIntegration.Status.PLANNED)
                .forEach(a -> report.append("- ").append(a.id()).append('\n'));
        report.append("\nFluoride-rich residue is retained as elemental refinery tailings, not credited as a sale of the fluoride_tailings bulk commodity. ")
                .append("Hyperdense neutronium, tachyon condensate, programmable matter, polymorphic nanofluid and radioisotopic matrices still need defined production processes or feedstocks. ")
                .append("An antimatter containment cell is empty hardware and does not create antimatter. ")
                .append("Power, shipyards and launch services use separate application capabilities. The named opening scenario lineup remains a migration gap. ")
                .append("No profitability assertion is made.\n");
        Files.createDirectories(Path.of("target")); Files.writeString(Path.of("target/production-coverage.md"), report);
        assertTrue(violations.isEmpty(), "Elemental creation: " + violations);
    }
    private void addElements(Map<String, Double> result, Material material, double kg) {
        assertNotNull(material);
        assertEquals(100, material.composition().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6, material.id());
        // The current bulk catalog represents hydrogen and helium isotopes as separate element keys.
        material.composition().forEach((element, percent) -> result.merge(element.replaceAll("[0-9]+$", ""), kg * percent / 100, Double::sum));
    }
}
