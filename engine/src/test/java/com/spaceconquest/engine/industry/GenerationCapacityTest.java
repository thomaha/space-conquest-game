package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.market.FacilitySupplyPricing;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenerationCapacityTest {
    @Test void pooledApplicationUnitsPreserveRatedFuelOutputCrewCapitalAndMaintenance() throws Exception {
        var base = BusinessTestWorld.create("hydroponic_growth_arrays"); var original = base.industrialFacilities().getLast();
        var plant = PowerPlantCatalog.find(original.applicationId());
        var separate = new ArrayList<IndustrialFacility>(); var crews = new HashMap<String, Integer>(); var wages = new HashMap<String, Double>();
        for (int i = 0; i < 4; i++) {
            var facility = new IndustrialFacility("plant-" + i, original.planetId(), original.applicationId(), original.ownerEntityId(), original.ownershipType(),
                    1, plant.requiredWorkers(), plant.workerProfessionId(), false, 0);
            separate.add(facility); crews.put(facility.id(), plant.requiredWorkers()); wages.put(facility.id(), 1000.0);
        }
        var pool = GenerationCapacity.pool(separate.getFirst(), 4);
        var many = base.withIndustrialFacilities(separate); var one = base.withIndustrialFacilities(List.of(pool));
        var executor = new PowerGenerationProcessor();
        var a = executor.process(many, crews, wages, false);
        var b = executor.process(one, Map.of(pool.id(), pool.allocatedWorkers()), Map.of(pool.id(), 4000.0), false);
        assertEquals(a.generationKw(), b.generationKw()); assertEquals(a.hubs(), b.hubs()); assertEquals(a.corporations(), b.corporations());
        assertEquals(a.accounts().stream().mapToDouble(IndustryAccount::wageCostsCredits).sum(), b.accounts().getFirst().wageCostsCredits());
        assertEquals(4L * plant.requiredWorkers(), GenerationCapacity.workers(pool, plant));
        var hub = base.commercialHubs().getFirst();
        assertEquals(separate.stream().mapToDouble(f -> FacilitySupplyPricing.capital(many, hub, f)).sum(), FacilitySupplyPricing.capital(one, hub, pool), 1e-8);
        assertEquals(separate.stream().mapToDouble(FacilitySupplyPricing::declaredMaintenance).sum(), FacilitySupplyPricing.declaredMaintenance(pool), 1e-8);
        var partial = executor.process(one, Map.of(pool.id(), plant.requiredWorkers()), Map.of(), false);
        assertEquals(b.generationKw().get("site") / 4, partial.generationKw().get("site"), 1e-8);
    }
}
