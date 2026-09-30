package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Material;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IndustryRecipeCatalogTest {
    @Test
    void liveRecipeGoodsAndPlantFuelsExistInMaterialCatalog() throws IOException {
        Set<String> materials = new HashSet<>(DataModelLoader.loadMaterials().stream()
                .map(Material::id).toList());
        Set<String> used = new HashSet<>();
        for (IndustryRecipeCatalog.Recipe recipe : IndustryRecipeCatalog.all()) {
            used.addAll(recipe.inputsKg().keySet());
            used.addAll(recipe.outputsKg().keySet());
        }
        for (PowerPlantCatalog.Plant plant : PowerPlantCatalog.all().values()) {
            if (plant.fuelMaterialId() != null) used.add(plant.fuelMaterialId());
        }
        used.removeAll(materials);
        assertTrue(used.isEmpty(), "Missing live recipe materials: " + used);
    }

    @Test
    void consumerGoodsRequireRefinedRareEarthsFromADepletingOreChain() {
        IndustryRecipeCatalog.Recipe consumer = IndustryRecipeCatalog.find("consumer_goods_mfg");
        IndustryRecipeCatalog.Recipe refinery = IndustryRecipeCatalog.find("rare_earth_refining");
        IndustryRecipeCatalog.Recipe mine = IndustryRecipeCatalog.find("rare_earth_fluorides_mining");
        assertEquals(1.0, consumer.inputsKg().get("refined_rare_earths"));
        assertEquals(1000.0, refinery.inputsKg().get("rare_earth_fluorides"));
        assertEquals(800.0, refinery.outputsKg().get("refined_rare_earths"));
        assertEquals(25.0, mine.outputsKg().get("rare_earth_fluorides"));
        assertTrue(mine.extractsDeposit());
    }

    @Test
    void reactorFuelsAndFusionIsotopesHaveMaterialRecipes() {
        assertEquals(100.0, IndustryRecipeCatalog.find("uranium_refining")
                .outputsKg().get("refined_uranium"));
        assertEquals(80.0, IndustryRecipeCatalog.find("thorium_refining")
                .outputsKg().get("refined_thorium"));
        assertTrue(IndustryRecipeCatalog.find("thorium_ore_mining").extractsDeposit());
        assertEquals(0.15, IndustryRecipeCatalog.find("water_isotope_separation")
                .outputsKg().get("deuterium_gas"));
        assertTrue(IndustryRecipeCatalog.find("fuel_fusion_pellets")
                .inputsKg().containsKey("deuterium_gas"));
    }
}
