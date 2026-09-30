package com.spaceconquest.engine;

import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.industry.IndustryProcessor;
import com.spaceconquest.engine.economy.CorporateTaxAccount;
import com.spaceconquest.engine.economy.CorporateValuation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CorporateProcessorTest {

    private final CorporateInvestmentProcessor investmentProcessor = new CorporateInvestmentProcessor();

    @Test
    public void corporateInvestmentBuildsAFactoryOverFiveDays() {
        GameState state = investmentState("AGRICULTURE", 30_000.0, "food_matrix");
        GameState invested = investmentProcessor.processCorporateInvestments(state, Map.of());
        Corporation corporation = invested.corporations().getFirst();
        assertEquals(22_000.0, corporation.liquidCapitalReserves());
        assertEquals(1, invested.industrialFacilities().size());
        assertEquals(invested.industrialFacilities().getFirst().id(), corporation.ownedFacilityIds().getFirst());
        assertEquals("industrial_soil_cultivation", invested.industrialFacilities().getFirst().applicationId());
        assertEquals(corporation.id(), invested.industrialFacilities().getFirst().ownerEntityId());
        assertEquals(0, invested.industrialFacilities().getFirst().tier());
        assertEquals(1, invested.expansionProjects().size());
        assertEquals(1, investmentProcessor.processCorporateInvestments(invested, Map.of())
                .industrialFacilities().size(), "A pending project must prevent duplicate construction");

        IndustryProcessor industry = new IndustryProcessor();
        GameState current = invested;
        for (int day = 1; day <= 5; day++) {
            current = industry.processIndustrialProduction(current);
            if (day < 5) {
                assertEquals(0, current.industrialFacilities().getFirst().tier());
                assertEquals(day / 5.0, current.industrialFacilities().getFirst().expansionProgress(), 0.001);
            }
        }
        assertTrue(current.expansionProjects().isEmpty());
        assertEquals(1, current.industrialFacilities().getFirst().tier());
        assertFalse(current.industrialFacilities().getFirst().isUndergoingExpansion());
    }

    @Test
    public void corporateFleetInvestmentQueuesAnOwnedBlueprint() {
        GameState state = investmentState("TRANSPORT", 30_000.0, "food_matrix");
        GameState invested = investmentProcessor.invest(state, "corp_test", "earth", "FLEET",
                CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
        Corporation corporation = invested.corporations().getFirst();
        assertEquals(18_000.0, corporation.liquidCapitalReserves());
        assertTrue(invested.fleets().isEmpty());
        assertEquals(1, invested.shipConstructionOrders().size());
        assertEquals("sol", invested.shipConstructionOrders().getFirst().systemId());
        assertEquals(corporation.id(), invested.shipDesigns().getFirst().ownerEntityId());
        assertTrue(invested.shipDesigns().getFirst().isProprietaryCorporateDesign());
    }

    @Test
    public void insufficientCapitalCannotCreateAnAssetOrSpendMoney() {
        GameState state = investmentState("AGRICULTURE", 100.0, "food_matrix");
        assertSame(state, investmentProcessor.invest(state, "corp_test", "earth",
                "INFRASTRUCTURE", CorporateInvestmentProcessor.INFRASTRUCTURE_COST));
        assertEquals(100.0, state.corporations().getFirst().liquidCapitalReserves());
    }

    @Test
    public void shortageWithoutProjectedOperatingProfitDoesNotTriggerAutomaticInvestment() {
        GameState state = investmentState("AGRICULTURE", 30_000.0, "food_matrix");
        CommercialHub hub = state.commercialHubs().getFirst();
        Map<String, MarketOrder> orders = new java.util.HashMap<>(hub.activeOrders());
        orders.put("food_matrix", new MarketOrder("food_matrix", 0.0, 200.0, 0.01, 0.95));
        GameState unprofitable = state.withCommercialHubs(List.of(new CommercialHub(
                hub.id(), hub.entityId(), hub.transactionTariffRate(), hub.storageCapacityKg(),
                hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), orders)));
        GameState after = investmentProcessor.processCorporateInvestments(unprofitable, Map.of());
        assertTrue(after.industrialFacilities().isEmpty());
        assertEquals(30_000.0, after.corporations().getFirst().liquidCapitalReserves());
    }

    @Test
    public void bookNetWorthCountsRealAssetsAndOutstandingTaxDebt() {
        GameState state = investmentState("AGRICULTURE", 30_000.0, "food_matrix");
        GameState invested = investmentProcessor.invest(state, "corp_test", "earth",
                "INFRASTRUCTURE", CorporateInvestmentProcessor.INFRASTRUCTURE_COST);
        assertEquals(30_000.0, CorporateValuation.value(invested,
                invested.corporations().getFirst()).netWorthCredits(), 0.001);
        GameState indebted = invested.toBuilder().corporateTaxAccounts(List.of(
                new CorporateTaxAccount("corp_test", 0.0, 500.0, 0.0, 0.0, 0.0))).build();
        assertEquals(29_500.0, CorporateValuation.value(indebted,
                indebted.corporations().getFirst()).netWorthCredits(), 0.001);
    }

    @Test
    public void waterShortageOnADryBodyBuildsIceTreatment() {
        GameState wet = investmentState("AGRICULTURE", 30_000.0, "purified_water");
        Planet dry = new Planet("earth", "Dry world", "", 1, 1, 1, 0, 0,
                "terrestrial", "none", false, 1, List.of("water_ice"), List.of(), List.of());
        SolarSystem system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1,
                "yellow", List.of(dry), List.of());
        GameState state = wet.toBuilder().solarSystems(List.of(system)).build();

        GameState invested = investmentProcessor.invest(state, "corp_test", "earth",
                "INFRASTRUCTURE", CorporateInvestmentProcessor.INFRASTRUCTURE_COST);

        assertEquals("ice_water_treatment", invested.industrialFacilities().getFirst().applicationId());
    }

    private GameState investmentState(String orientation, double cash, String resource) {
        Corporation corporation = new Corporation("corp_test", "Test corporation", "terran", "earth",
                orientation, cash, List.of(), List.of(), List.of());
        Empire empire = new Empire("terran", "Terran", "human", "Individualist", 1000.0,
                0.15, List.of("sol"), List.of(), Map.of(),
                List.of("industrial_production", "rocketry", "computers"), List.of());
        Planet earth = new Planet("earth", "Earth", "", 1, 1, 1, 0, 1,
                "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1,
                "yellow", List.of(earth), List.of());
        Map<String, MarketOrder> orders = new java.util.HashMap<>();
        orders.put(resource, new MarketOrder(resource, 5.0, 200.0, 15.0, 0.85));
        for (String material : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon")) {
            orders.put(material, new MarketOrder(material, 2_000.0, 0.0, 1.0, 0.0));
        }
        CommercialHub hub = new CommercialHub("hub_earth", "earth", 0.05, 100_000.0,
                8_005.0, 10.0, Map.copyOf(orders));
        return GameState.builder().corporations(List.of(corporation)).empires(List.of(empire))
                .solarSystems(List.of(sol)).commercialHubs(List.of(hub)).build();
    }

}
