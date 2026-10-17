package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlledCultivationTest {
    @Test void purchasedUtilitiesHarvestsAndDeclaredLossesConserveAllCatalogElements() throws Exception {
        var materials = new HashMap<String, Material>(); DataModelLoader.loadMaterials().forEach(m -> materials.put(m.id(), m));
        for (String id : List.of("hydroponic_growth_arrays", "automated_biosphere_macro_farms")) {
            var recipe = IndustryRecipeCatalog.find(id); var controlled = recipe.farming().controlled();
            assertNull(recipe.factory()); controlled.validateRecipe(recipe);
            assertTrue(materials.keySet().containsAll(recipe.infrastructure().constructionMaterialsKg().keySet()));
            var balance = new TreeMap<String, Double>();
            recipe.inputsKg().forEach((m, kg) -> materials.get(m).composition().forEach((e, pct) -> balance.merge(e, kg * pct / 100, Double::sum)));
            var outputs = new HashMap<>(recipe.outputsKg()); controlled.environmentalLossesKg().forEach((m, kg) -> outputs.merge(m, kg, Double::sum));
            outputs.forEach((m, kg) -> materials.get(m).composition().forEach((e, pct) -> balance.merge(e, -kg * pct / 100, Double::sum)));
            balance.forEach((element, kg) -> assertEquals(0, kg, 1e-7, id + ": " + element));
            assertEquals(controlled.makeupWaterKgPerBatch(), recipe.inputsKg().get(controlled.waterMaterialId()), 1e-7);
        }
        var hydro = IndustryRecipeCatalog.find("hydroponic_growth_arrays").farming().controlled();
        var advanced = IndustryRecipeCatalog.find("automated_biosphere_macro_farms").farming().controlled();
        assertEquals(2000, hydro.makeupWaterKgPerBatch(), 1e-7);
        assertEquals(1500, advanced.makeupWaterKgPerBatch() / 4, 1e-7);
    }

    @Test void sealedCultivationUsesSolidGroundWithoutOutdoorClimateAndRejectsUnknownSites() throws Exception {
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).build();
        assertFalse(FarmingDefinition.canInstall(state, "industrial_soil_cultivation", "moon", "farm", 1, null));
        for (String id : List.of("hydroponic_growth_arrays", "automated_biosphere_macro_farms")) {
            assertTrue(FarmingDefinition.canInstall(state, id, "moon", "farm", 1, null));
            assertFalse(FarmingDefinition.canInstall(state, id, "unknown", "farm", 1, null));
            assertFalse(FarmingDefinition.canInstall(state, id, "jupiter", "farm", 1, null));
        }
    }

    @Test void orbitalSpaceIsFiniteAcrossPooledLinesTiersAndPendingFarms() {
        var station = new OrbitalStation("station", "Station", "sol", "earth", "owner", "PUBLIC_STATE", 6,
                List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
        var state = GameState.builder().orbitalStations(List.of(station)).build();
        assertTrue(FarmingDefinition.canInstall(state, "hydroponic_growth_arrays", "station", "a", 1, null));
        assertFalse(FarmingDefinition.canInstall(state, "automated_biosphere_macro_farms", "station", "a", 1, null));
        var first = new IndustrialFacility("a", "station", "hydroponic_growth_arrays", "owner", "PUBLIC_STATE", 0, 4, "farmer", true, 0);
        state = state.toBuilder().industrialFacilities(List.of(first)).build();
        assertFalse(FarmingDefinition.canInstall(state, first.applicationId(), "station", "b", 1, null));
        assertTrue(FarmingDefinition.canInstall(state, first.applicationId(), "station", "a", 2, null));
        assertFalse(FarmingDefinition.canInstall(state, first.applicationId(), "station", "a", 3, null));
        var e = first.productionEquipment();
        var twoLines = new ProductionEquipment(e.id(), e.processType(), e.workUnitsPerDay(), e.referenceOperators(), e.operatorProfession(), e.autonomousFraction(), 1, 2);
        assertFalse(FarmingDefinition.canInstall(state, first.applicationId(), "station", "a", 1, twoLines));
    }
    @Test void declaredAutomationRunsOnlyItsShareOfAStillTimedAndPoweredGrowingCycle() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e8, 0, List.of(), List.of(), Map.of(),
                List.of("industrial_production", "electricity", "computers", "robotics", "hydroponic_growth_arrays", "automated_biosphere_macro_farms"), List.of());
        var state = GameState.builder().empires(List.of(owner)).solarSystems(DataModelLoader.loadSolarSystems()).build();
        for (String id : List.of("hydroponic_growth_arrays", "automated_biosphere_macro_farms")) {
            var recipe = IndustryRecipeCatalog.find(id);
            var farm = new IndustrialFacility("farm", "moon", id, "owner", "PUBLIC_STATE", 1, 4, "farmer", false, 0);
            var automatic = ProductionWork.quote(state, farm, 0); var staffed = ProductionWork.quote(state, farm, 4);
            assertEquals(recipe.work().equipment().autonomousFraction() / recipe.farming().growingDays(), automatic.batchesPerDay(), 1e-12);
            assertEquals(1.0 / recipe.farming().growingDays(), staffed.batchesPerDay(), 1e-12);
            assertEquals(automatic.batchesPerDay() * recipe.work().energyKwhPerBatch(), automatic.energyKwh(), 1e-8);
        }
    }

    @Test void legacyStationModuleCannotCreateFoodWithoutPaidCultivation() {
        var control = new com.spaceconquest.engine.macrostructure.StationModule("control", "Control", com.spaceconquest.engine.macrostructure.StationModule.TYPE_CONTROL,
                1, 1, 0, 0, Map.of(), "bureaucrat", 0, true);
        var ring = new com.spaceconquest.engine.macrostructure.StationModule("ring", "Old ring",
                com.spaceconquest.engine.macrostructure.StationModule.TYPE_HYDROPONIC_FOOD,
                4, 1, 0, 0, Map.of(), "farmer", 0, true);
        var station = new OrbitalStation("station", "Station", "sol", "earth", "owner", "PUBLIC_STATE", 6,
                List.of(control, ring), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
        var result = new com.spaceconquest.engine.macrostructure.MacroStructureProcessor().processOrbitalStation(station, 0);
        assertTrue(result.updatedStation().isOperational());
        assertTrue(result.producedMaterialsKg().isEmpty());
    }
}

