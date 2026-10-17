package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustryRecipeCatalog;
import com.spaceconquest.engine.industry.PowerPlantCatalog;
import com.spaceconquest.engine.technology.ApplicationProduction;

/** Shared research gate for new facilities and recipe changes, including corporate technology ownership. */
final class FacilityResearchEligibility {
    private FacilityResearchEligibility() {}
    static boolean allows(GameState state, String ownerId, String applicationId) {
        String empireId = state.corporations().stream().filter(corporation -> corporation.id().equals(ownerId))
                .map(corporation -> corporation.empireId()).findFirst().orElse(ownerId);
        var empire = state.empires().stream().filter(value -> value.id().equals(empireId)).findFirst().orElse(null);
        if (empire == null || !ApplicationProduction.isAvailable(empire, applicationId)) return false;
        var recipe = IndustryRecipeCatalog.find(applicationId);
        if (recipe != null) return IndustryRecipeCatalog.isUnlocked(recipe, empire);
        var plant = PowerPlantCatalog.find(applicationId);
        if (plant != null) return empire.unlockedTechIds().contains("electricity")
                && empire.unlockedTechIds().contains(plant.requiredTechnology());
        var service = com.spaceconquest.engine.logistics.InfrastructureService.find(applicationId);
        String capabilityApplication = service == null ? com.spaceconquest.engine.ship.ShipyardTechnology.application(applicationId)
                : service.applicationId();
        return capabilityApplication != null && ApplicationProduction.isAvailable(empire, capabilityApplication);
    }
}
