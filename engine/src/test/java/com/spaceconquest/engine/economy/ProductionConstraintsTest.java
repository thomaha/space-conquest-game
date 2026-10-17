package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConstraintsTest {
    @TempDir Path directory;
    @Test void simultaneousMeasuredConstraintsAreImmutableAndPersistWithTheOperatingDecision() throws Exception {
        var state = BusinessTestWorld.create("hydroponic_growth_arrays"); var hub = state.commercialHubs().getFirst();
        var orders = new HashMap<>(hub.activeOrders()); var carbon = orders.get("carbon_dioxide_gas");
        orders.put(carbon.resourceId(), new MarketOrder(carbon.resourceId(), 0, carbon.demandKg(), carbon.pricePerKg(), 0));
        var firm = state.corporations().getFirst();
        var poor = new Corporation(firm.id(), firm.name(), firm.empireId(), firm.headquartersEntityId(), firm.marketOrientation(), 0,
                firm.ownedFacilityIds(), firm.ownedShipIds(), firm.claimedVeinIds());
        var companies = new ArrayList<>(state.corporations()); companies.set(0, poor);
        state = state.toBuilder().corporations(companies).commercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0,
                hub.currentStoredWeightKg(), hub.currentStoredWeightKg(), 10, orders))).build();
        var constraints = ProductionConstraints.inspect(state, state.industrialFacilities().getFirst(), new GridSupplyPricing.Forecast(Map.of("site", .5), Map.of()));
        assertTrue(constraints.stream().anyMatch(c -> c.kind().equals("Material") && c.resource().equals("carbon_dioxide_gas") && c.available() == 0));
        assertFalse(constraints.stream().anyMatch(c -> c.kind().equals("Electricity")), "Missing inputs must not masquerade as a grid shortage");
        var supplied = BusinessTestWorld.create("hydroponic_growth_arrays");
        assertTrue(ProductionConstraints.inspect(supplied, supplied.industrialFacilities().getFirst(),
                new GridSupplyPricing.Forecast(Map.of("site", .5), Map.of())).stream().anyMatch(c -> c.kind().equals("Electricity")));
        assertTrue(constraints.stream().anyMatch(c -> c.kind().equals("Working capital")));
        assertTrue(constraints.stream().anyMatch(c -> c.kind().equals("Market storage")));
        assertThrows(UnsupportedOperationException.class, () -> constraints.clear());
        var plan = BusinessOperatingPlan.prepare(state);
        assertFalse(plan.businessLedger().operatingDecisions().get("producer").constraints().isEmpty());
        var saves = new SaveGameManager(directory); var file = directory.resolve("constraints.scsave").toFile();
        saves.save(file, plan, 1, GameClock.START_TIME.toString());
        assertEquals(plan.businessLedger(), saves.load(file).toGameState(plan.turn(), plan.status()).businessLedger());
        assertEquals(0, plan.corporations().getFirst().liquidCapitalReserves());
    }
    @Test void localLeadTimesIncludeParallelUpstreamStagesWithoutCreatingSupply() throws Exception {
        var state = BusinessTestWorld.create("hydroponic_growth_arrays");
        var facilities = List.of(facility("ore", "mining_outpost"), facility("carbon", "carbon_mining"),
                facility("metal", "pyro_iron_smelting"), facility("steel", "alloy_steel"));
        state = state.withIndustrialFacilities(facilities);
        var times = SupplyLeadTimes.local(state, state.commercialHubs().getFirst());
        assertEquals(1, times.get("iron_ore")); assertEquals(2, times.get("refined_iron")); assertEquals(3, times.get("steel"));
        assertEquals(2, times.get("carbon_dioxide_gas"));
        assertTrue(state.geologicalDeposits().isEmpty());
        assertTrue(FacilitySupplyPricing.offers(state, state.commercialHubs().getFirst(), .02).isEmpty());
        var owner = state.empires().getFirst();
        var unknown = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(),
                owner.treasuryCredits(), owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(),
                owner.systemGovernorAssignments(), List.of(), List.of())));
        assertTrue(SupplyLeadTimes.local(unknown, unknown.commercialHubs().getFirst()).isEmpty());
    }
    private IndustrialFacility facility(String id, String application) {
        var recipe = IndustryRecipeCatalog.find(application);
        return new IndustrialFacility(id, "site", application, "producer-owner", IndustrialFacility.PRIVATE_CORPORATE, 1,
                recipe.work().equipment().referenceOperators(), recipe.work().equipment().operatorProfession(), false, 0);
    }
}
