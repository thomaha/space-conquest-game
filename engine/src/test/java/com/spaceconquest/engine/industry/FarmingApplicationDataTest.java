package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingApplicationDataTest {
    @Test void farmsAreApplicationOwnedRenewableCultivationAndExcludedFromTheFactoryRefinementCatalog() throws Exception {
        for (var application : DataModelLoader.loadTechnologies().stream().flatMap(t -> t.applications().stream()).toList()) {
            var recipe = application.productionRecipe();
            if (recipe == null || recipe.farming() == null) continue;
            assertNull(recipe.factory()); assertNotNull(recipe.work()); assertFalse(recipe.extractsDeposit());
            assertEquals(recipe, IndustryRecipeCatalog.find(application.id()));
            assertTrue(RefinementProcessor.STANDARD_RECIPES.stream().noneMatch(r -> r.id().equals(application.id())));
            assertNotNull(application.operatingReserve());
        }
        for (String id : List.of("food_processing", "biomass_processing")) {
            assertNull(IndustryRecipeCatalog.find(id).farming()); assertNotNull(IndustryRecipeCatalog.find(id).factory());
        }
    }

    @Test void foodAndBiomassFactoriesRetainAllInputElementsInTheirMixedOutputs() throws Exception {
        var materials = new HashMap<String, Material>(); DataModelLoader.loadMaterials().forEach(m -> materials.put(m.id(), m));
        for (String id : List.of("food_processing", "biomass_processing")) {
            var recipe = IndustryRecipeCatalog.find(id); var balance = new TreeMap<String, Double>();
            recipe.inputsKg().forEach((m, kg) -> materials.get(m).composition().forEach((e, pct) -> balance.merge(e, kg * pct / 100, Double::sum)));
            recipe.outputsKg().forEach((m, kg) -> materials.get(m).composition().forEach((e, pct) -> balance.merge(e, -kg * pct / 100, Double::sum)));
            balance.forEach((element, kg) -> assertEquals(0, kg, 1e-8, id + ": " + element));
        }
    }

    @Test void skillAndAutomationAffectOperatingWorkButCannotShortenBiologicalGrowth() throws Exception {
        var recipe = IndustryRecipeCatalog.find("industrial_soil_cultivation");
        var knowledge = List.of("industrial_production", recipe.applicationId());
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e8, 0, List.of(), List.of(), Map.of(), knowledge, List.of());
        var equipment = recipe.work().equipment();
        var automatic = new ProductionEquipment("automatic", equipment.processType(), 1e6, 1, "farmer", 1, 2);
        var facility = new IndustrialFacility("farm", "earth", recipe.applicationId(), "owner", IndustrialFacility.PUBLIC_STATE, 1, 0, "farmer", false, 0, automatic);
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner)).industrialFacilities(List.of(facility)).build();
        assertEquals(1.0 / recipe.farming().growingDays(), ProductionWork.quote(state, facility, 0).batchesPerDay(), 1e-12);
        var manual = new IndustrialFacility("farm", "earth", recipe.applicationId(), "owner", IndustrialFacility.PUBLIC_STATE, 1, 1, "farmer", false, 0);
        assertEquals(0, ProductionWork.quote(state, manual, 0).batchesPerDay());
        assertEquals(1.0 / recipe.farming().growingDays(), ProductionWork.quote(state, manual, 1).batchesPerDay(), 1e-12);
        var learnerEquipment = new ProductionEquipment("learner", equipment.processType(), equipment.workUnitsPerDay(), 1, "farmer", 0, .5);
        var learner = new IndustrialFacility("farm", "earth", recipe.applicationId(), "owner", IndustrialFacility.PUBLIC_STATE, 1, 1, "farmer", false, 0, learnerEquipment);
        assertEquals(.5 / recipe.farming().growingDays(), ProductionWork.quote(state, learner, 1).batchesPerDay(), 1e-12);
        assertFalse(recipe.farming().permits(state, new IndustrialFacility("dry", "moon", recipe.applicationId(), "owner", IndustrialFacility.PUBLIC_STATE, 1, 1, "farmer", false, 0)));
    }

    @Test void baselineProcessingPricesCoverMaterialsAndElectricityBeforeStaffingAndInvestment() {
        for (String id : List.of("food_processing", "biomass_processing")) {
            var recipe = IndustryRecipeCatalog.find(id);
            double input = recipe.inputsKg().entrySet().stream().mapToDouble(e -> e.getValue()
                    * com.spaceconquest.engine.market.MarketProcessor.basePricePerKg(e.getKey())).sum();
            double sales = recipe.outputsKg().entrySet().stream().filter(e -> !recipe.factory().byproductMaterialIds().contains(e.getKey()))
                    .mapToDouble(e -> e.getValue() * com.spaceconquest.engine.market.MarketProcessor.basePricePerKg(e.getKey()) * IndustryMarketProcessor.WHOLESALE_SHARE).sum();
            assertTrue(sales > input + recipe.work().energyKwhPerBatch() * PowerBillingProcessor.PRICE_PER_KWH, id);
        }
    }
}
