package com.spaceconquest.control;

import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.ExpandFacilityCommand;
import com.spaceconquest.control.command.PlaceFacilityOnTileCommand;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.industry.PowerGenerationProcessor;
import com.spaceconquest.engine.technology.ApplicationProduction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationOptimizationIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void performanceChangesGenerationAndFutureConstructionAndSurvivesSaveLoad() throws IOException {
        GameState selected = select(baseState(), "solar_power", "performance");
        GameState building = new BuildFacilityCommand("empire", "earth", "solar_power", "technician", 1)
                .apply(selected);
        var project = building.expansionProjects().getFirst();
        assertEquals(2, project.targetTier());
        assertEquals(1_200.0, project.requiredWorkHours(), 0.001);
        assertEquals(1_200.0, project.requiredMaterialsKg().get("refined_iron"), 0.001);

        GameState switched = select(building, "solar_power", "miniaturization");
        assertEquals(project, switched.expansionProjects().getFirst(), "Existing construction keeps its quoted costs");
        assertEquals(6_000.0, generation(switched), 0.001);
        assertEquals(0.0, generation(selected), 0.001, "Tier 1 cannot run the more complex variant");
        selected = selected.withIndustrialFacilities(List.of(new IndustrialFacility("solar", "earth", "solar_power",
                "empire", IndustrialFacility.PUBLIC_STATE, 2, 100, "technician", false, 0.0)));
        assertEquals(13_800.0, generation(selected), 0.001);

        SaveGameManager saves = new SaveGameManager();
        var file = tempDir.resolve("optimization.scsave").toFile();
        saves.save(file, selected, 1, "2026-01-01T00:00:00");
        GameState loaded = saves.load(file).toGameState(selected.turn(), selected.status());
        SpaceConquestEngine engine = new SpaceConquestEngine();
        engine.applyGameState(loaded);
        assertEquals(selected.applicationOptimizations(), engine.getGameState().applicationOptimizations());
        assertEquals(13_800.0, generation(engine.getGameState()), 0.001);
    }

    @Test
    void miniaturizationReducesTileConstructionAndExpansionCostsAndEffectiveComplexity() throws IOException {
        GameState selected = select(baseState(), "solar_power", "PATH_B");
        GameState tile = new PlaceFacilityOnTileCommand("earth", 0, "solar_power", "empire",
                IndustrialFacility.PUBLIC_STATE, 100, "technician").apply(selected);
        assertEquals(425.0, tile.expansionProjects().getFirst().requiredWorkHours(), 0.001);
        assertEquals(425.0, tile.expansionProjects().getFirst().requiredMaterialsKg().get("refined_iron"), 0.001);
        GameState expansion = new ExpandFacilityCommand("solar", 2, 200.0, 0.0).apply(selected);
        assertEquals(170.0, expansion.expansionProjects().getFirst().requiredWorkHours(), 0.001);
        assertEquals(425.0, expansion.expansionProjects().getFirst().requiredMaterialsKg().get("refined_iron"), 0.001);
        var application = DataModelLoader.loadTechnologies().stream().flatMap(tech -> tech.applications().stream())
                .filter(app -> "solar_power".equals(app.id())).findFirst().orElseThrow();
        var miniaturization = ApplicationProduction.modifiers(selected, "empire", "solar_power");
        var performance = ApplicationProduction.modifiers(select(baseState(), "solar_power", "PATH_A"), "empire", "solar_power");
        assertEquals(application.costToBuildPerUnit() * 0.85, application.calculateOptimizedUnitCost(miniaturization), 0.001);
        assertEquals(Math.max(1, application.complexity() - 1), application.calculateOptimizedComplexity(miniaturization));
        assertEquals(application.complexity() + 1, application.calculateOptimizedComplexity(performance));
    }

    @Test
    void corporateProductionUsesEmpirePathAndConsumesInputsForTheExtraOutput() {
        GameState selected = select(baseState(), "water_electrolysis", "PATH_A");
        GameState corporate = selected.toBuilder()
                .corporations(List.of(new Corporation("corp", "Corp", "empire", "earth", "INDUSTRY",
                        50_000.0, List.of("electrolysis"), List.of(), List.of())))
                .industrialFacilities(List.of(new IndustrialFacility("electrolysis", "earth", "water_electrolysis",
                        "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "technician", false, 0.0)))
                .commercialHubs(List.of(new CommercialHub("hub", "earth", 0.0, 100_000.0, 10_000.0, 1.0,
                        Map.of("purified_water", new MarketOrder("purified_water", 10_000.0, 0.0, 1.0, 0.0)))))
                .marketAccounts(List.of(new MarketAccount("hub", 0.0))).build();
        var result = new IndustryMarketProcessor().process(corporate, Map.of("electrolysis", 10), Map.of());
        assertTrue(result.industryAccounts().getFirst().producedKg().isEmpty());
        assertEquals(10_000.0, result.hubs().getFirst().activeOrders().get("purified_water").supplyKg());
        corporate = corporate.withIndustrialFacilities(List.of(new IndustrialFacility("electrolysis", "earth", "water_electrolysis",
                "corp", IndustrialFacility.PRIVATE_CORPORATE, 2, 10, "technician", false, 0.0)));
        result = new IndustryMarketProcessor().process(corporate, Map.of("electrolysis", 10), Map.of());
        var account = result.industryAccounts().getFirst();
        assertEquals(888.9 * 1.15 * 1.5, account.producedKg().get("oxygen_gas"), 0.001);
        assertEquals(111.1 * 1.15 * 1.5, account.producedKg().get("hydrogen_gas"), 0.001);
        assertEquals(1_725.0, account.inputCostsCredits(), 0.001);
        assertEquals(8_275.0, result.hubs().getFirst().activeOrders().get("purified_water").supplyKg(), 0.001);
        assertEquals(1.2, ApplicationProduction.modifiersForOwner(corporate, "corp", "water_electrolysis").costMultiplier());
        assertEquals(1.0, ApplicationProduction.modifiers(corporate, "other_empire", "water_electrolysis").effectMultiplier());
    }

    @Test
    void selectionRejectsUnknownUnresearchedAndMissingPrerequisiteApplications() {
        for (List<String> knowledge : List.of(List.of("electricity"), List.of("solar_power"),
                List.of("electricity", "unknown_app"), List.of("electricity", "nuclear_power_app"))) {
            Empire original = baseState().empires().getFirst();
            Empire empire = new Empire(original.id(), original.name(), original.raceId(), original.societyStructure(),
                    original.treasuryCredits(), original.corporateTaxRate(), original.controlledSystemIds(),
                    original.ministries(), original.systemGovernorAssignments(), knowledge, original.activeShipDesignIds());
            GameState state = baseState().withEmpires(List.of(empire));
            String applicationId = knowledge.contains("unknown_app") ? "unknown_app"
                    : knowledge.contains("nuclear_power_app") ? "nuclear_power_app" : "solar_power";
            var command = new SelectOptimizationPathCommand("empire", applicationId, "PATH_A");
            assertFalse(command.validate(state), knowledge.toString());
            assertSame(state, command.apply(state));
        }
    }

    private GameState select(GameState state, String applicationId, String path) {
        var command = new SelectOptimizationPathCommand("empire", applicationId, path);
        assertTrue(command.validate(state));
        return command.apply(state);
    }

    private double generation(GameState state) {
        return new PowerGenerationProcessor().process(state, Map.of("solar", 100), Map.of())
                .generationKw().get("earth");
    }

    private GameState baseState() {
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 50_000.0,
                0.0, List.of("sol"), List.of(), Map.of(),
                List.of("electricity", "industrial_production", "solar_power", "water_electrolysis"), List.of());
        return GameState.builder().empires(List.of(empire))
                .industrialFacilities(List.of(new IndustrialFacility("solar", "earth", "solar_power", "empire",
                        IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0.0))).build();
    }
}
