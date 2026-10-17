package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipyardTechnology;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfrastructureServiceDataTest {
    @Test void serviceTypesHaveUniqueOwningApplicationsAndRealMaterialBills() throws Exception {
        var technologies = DataModelLoader.loadTechnologies();
        var materials = DataModelLoader.loadMaterials().stream().map(m -> m.id()).collect(java.util.stream.Collectors.toSet());
        var applications = technologies.stream().flatMap(t -> t.applications().stream()).toList();
        assertEquals(3, InfrastructureService.all().size());
        for (var entry : InfrastructureService.all().entrySet()) {
            var service = entry.getValue().capability();
            assertTrue(applications.stream().anyMatch(a -> a.id().equals(entry.getValue().applicationId())
                    && service.equals(a.infrastructureService())));
            assertEquals(service.infrastructure().constructionMaterialsKg(), ConstructionMaterialCatalog.facility(entry.getKey(), 1));
            assertTrue(materials.containsAll(service.infrastructure().constructionMaterialsKg().keySet()));
            if (service.driveId() != null) assertNotNull(PropulsionCatalog.drive(service.driveId()));
        }
        for (var entry : ShipyardTechnology.allYards().entrySet()) {
            assertNotNull(ShipyardTechnology.application(entry.getKey()));
            assertTrue(materials.containsAll(entry.getValue().materialsKg().keySet()));
        }
        assertNull(InfrastructureService.find("unconfigured_facility"));
    }
}
