package com.spaceconquest.frontend;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.ApplicationIntegration;
import java.util.*;

/** Concrete player benefits from the same catalogs used by operational consumers. */
final class ResearchBenefitText {
    private ResearchBenefitText() {}
    static String describe(TechnicalApplication app, List<Material> materials) {
        var benefits = new ArrayList<String>();
        if (PowerPlantCatalog.find(app.id()) != null) benefits.add("Build: " + app.name() + " power plant.");
        var recipe = IndustryRecipeCatalog.find(app.id());
        if (recipe != null) benefits.add("Build: " + app.name() + " facility. Produces: "
                + String.join(", ", recipe.outputsKg().keySet().stream().sorted().map(id -> materials.stream()
                .filter(material -> id.equals(material.id())).map(Material::name).findFirst().orElse(id.replace('_', ' '))).toList()) + ".");
        if (app.combatModule() != null) benefits.add("Ship equipment: " + app.combatModule().name() + ".");
        if (app.auxiliaryHardware() != null) benefits.add("Build: " + app.name() + ".");
        var modules = ShipApplicationProduction.moduleIdsForApplication(app.id()).stream().map(ShipComponentCatalog::module)
                .filter(Objects::nonNull).map(ShipModule::name).distinct().toList();
        if (!modules.isEmpty()) benefits.add("Improves new ship components: " + String.join(", ", modules) + ".");
        if (benefits.isEmpty()) benefits.add(ApplicationIntegration.describe(app).status() == ApplicationIntegration.Status.PLANNED
                ? "Usable benefit: none yet." : "Current upgrade: " + ApplicationIntegration.describe(app).description());
        return String.join("\n", benefits);
    }
}
