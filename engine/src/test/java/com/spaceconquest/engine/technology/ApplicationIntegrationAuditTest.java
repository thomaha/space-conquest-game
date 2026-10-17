package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationIntegrationAuditTest {
    @Test void generatedOpeningIndustryHasItsApplicationsForEveryStartingEraAndOwner() throws Exception {
        for (var scenario : GameStartScenario.values()) {
            var state = new GalaxyGenerator().generateGameState(2, 1, scenario);
            for (var facility : state.industrialFacilities()) {
                var ownerId = state.corporations().stream().filter(corporation -> corporation.id().equals(facility.ownerEntityId()))
                        .map(Corporation::empireId).findFirst().orElse(facility.ownerEntityId());
                var empire = state.empires().stream().filter(value -> ownerId.equals(value.id())).findFirst().orElseThrow();
                assertTrue(ApplicationProduction.isAvailable(empire, facility.applicationId()), scenario + ": " + facility.applicationId());
            }
            assertFalse(state.empires().getFirst().unlockedTechIds().contains("plasma_weapons"));
        }
    }
    @Test void everyApplicationHasAnHonestConsumerClassificationAndCatalogProductionRequiresItsDiscovery() throws Exception {
        var catalog = DataModelLoader.loadTechnologies(); var ids = new HashSet<String>();
        var counts = new EnumMap<ApplicationIntegration.Status, Integer>(ApplicationIntegration.Status.class);
        var rows = new StringBuilder("| Application | Support | Consumer | Current behavior |\n| --- | --- | --- | --- |\n");
        for (var technology : catalog) for (var app : technology.applications()) {
            assertTrue(ids.add(app.id()), "Duplicate application ID " + app.id());
            var support = ApplicationIntegration.describe(app); assertFalse(support.description().isBlank());
            counts.merge(support.status(), 1, Integer::sum);
            if (support.status() == ApplicationIntegration.Status.PLANNED) assertEquals("None", support.consumer());
            var knowledge = new ArrayList<>(app.requiredTechnologies()); knowledge.add(technology.id());
            var empire = empire(knowledge);
            var recipe = IndustryRecipeCatalog.find(app.id());
            if (recipe != null) {
                knowledge.addAll(recipe.requiredTechnologies()); knowledge.removeIf(app.id()::equals); empire = empire(knowledge);
                assertFalse(IndustryRecipeCatalog.isUnlocked(recipe, empire), app.id() + " must require its own discovery");
                knowledge.add(app.id()); assertTrue(IndustryRecipeCatalog.isUnlocked(recipe, empire(knowledge)), app.id());
            }
            rows.append("| `").append(app.id()).append("` | ").append(support.status().name().toLowerCase(Locale.ROOT))
                    .append(" | ").append(support.consumer()).append(" | ").append(support.description()).append(" |\n");
        }
        assertTrue(ids.contains("rare_earth_byproduct_recovery"));
        String intro = "# Application integration audit\n\nReviewed on 16 October 2026 against the live catalog and production consumers. "
                + ids.size() + " applications: " + counts.getOrDefault(ApplicationIntegration.Status.AVAILABLE, 0)
                + " available, " + counts.getOrDefault(ApplicationIntegration.Status.PARTIAL, 0) + " partial and "
                + counts.getOrDefault(ApplicationIntegration.Status.PLANNED, 0) + " planned.\n\n"
                + "Available means an operational consumer uses application-defined hardware. Partial means a documented subset works. "
                + "Planned means no operational consumer uses that application ID; similarly named foundation-gated mechanics do not establish application support. "
                + "Descriptive affected factors are design intent rather than automatic bonuses. The technology page displays these same classifications and AI avoids planned applications.\n\n"
                + "New facility construction, recipe changes, recipe production and grid generation require catalog application discovery and prerequisites. "
                + "Every material-production recipe belongs to a catalog application. Launch services and surface shipyard construction use owning application research gates. "
                + "Some baseline ship equipment remains foundation-gated; its researched application controls improved variants rather than unlocking the baseline. "
                + "Existing ship capabilities remain frozen. No weapon values were balanced.\n\n"
                + "Verification includes paid scientist progress, command unlock gates, material-funded construction, generation and actual disk save/load. "
                + "Facility construction labor remains the existing provisional daily work allowance. Production and generation tests supply settled worker allocations to the real processors.\n\n";
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/application-integration-audit.md"), intro + rows);
    }
    private Empire empire(List<String> knowledge) {
        return new Empire("owner", "Owner", "human", "Individualist", 100000, 0,
                List.of("sol"), List.of(), Map.of(), List.copyOf(knowledge), List.of());
    }
}
