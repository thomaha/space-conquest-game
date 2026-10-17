package com.spaceconquest.engine.industry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PowerPlantApplicationDataTest {
    @Test void applicationValuesDriveActualOutputStaffingAndPaidFuelWithoutCodeChanges() throws Exception {
        var mapper = new ObjectMapper();
        var app = DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream())
                .filter(application -> application.id().equals("combustion_power")).findFirst().orElseThrow();
        ObjectNode data = mapper.valueToTree(app); var plantData = (ObjectNode) data.get("powerPlant");
        plantData.put("kwPerTier", 8000); plantData.put("requiredWorkers", 200); plantData.put("fuelKgPerTierDay", 40);
        var tuned = mapper.treeToValue(data, TechnicalApplication.class).powerPlant();
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1000, 0, List.of(), List.of(), Map.of(), List.of("electricity", "combustion_power"), List.of());
        var facility = new IndustrialFacility("plant", "world", app.id(), "owner", IndustrialFacility.PUBLIC_STATE, 1, 200, "technician", false, 0);
        var hub = new CommercialHub("hub", "world", 0, 1000, 500, 10, Map.of("hydrocarbons", new MarketOrder("hydrocarbons", 500, 0, 2, 0)));
        var state = GameState.builder().empires(List.of(empire)).industrialFacilities(List.of(facility)).commercialHubs(List.of(hub))
                .industryAccounts(List.of(IndustryAccount.empty("plant").withOperatingCash(1000))).build();
        var generator = new PowerGenerationProcessor(Map.of(app.id(), tuned));
        var full = generator.process(state, Map.of("plant", 200), Map.of("plant", 100.0));
        assertEquals(8000, full.generationKw().get("world")); assertEquals(80, full.accounts().getFirst().inputCostsCredits());
        assertEquals(460, full.hubs().getFirst().activeOrders().get("hydrocarbons").supplyKg());
        var half = generator.process(state, Map.of("plant", 100), Map.of("plant", 50.0));
        assertEquals(4000, half.generationKw().get("world")); assertEquals(40, half.accounts().getFirst().inputCostsCredits());
        assertEquals(100, half.accounts().getFirst().paidWorkers());
    }
    @Test void everyPlantIsDefinedByAnApplicationWithValidConstructionInputsAndProfession() throws Exception {
        var materials = DataModelLoader.loadMaterials().stream().map(Material::id).toList();
        var professions = DataModelLoader.loadProfessions().stream().map(Profession::id).toList();
        var apps = DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream()).toList();
        assertEquals(8, PowerPlantCatalog.all().size());
        for (var entry : PowerPlantCatalog.all().entrySet()) {
            assertEquals(entry.getValue(), apps.stream().filter(app -> entry.getKey().equals(app.id())).findFirst().orElseThrow().powerPlant());
            assertTrue(materials.containsAll(entry.getValue().constructionMaterialsKg().keySet()));
            assertTrue(professions.contains(entry.getValue().workerProfessionId()));
        }
    }
}
