package com.spaceconquest.engine;

import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.industry.IndustryProcessor;
import com.spaceconquest.engine.economy.CorporateTaxAccount;
import com.spaceconquest.engine.economy.CorporateValuation;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipConstructionRequirements;
import com.spaceconquest.engine.ship.ShipManufacturingCapacity;
import com.spaceconquest.engine.technology.ApplicationOptimization;
import com.spaceconquest.engine.technology.ResearchProcessor;
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
        assertTrue(invested.shipDesigns().getFirst().equippedModuleIds().contains("mod_chemical_rocket"));
        assertTrue(PropulsionCatalog.researched(invested.shipDesigns().getFirst().equippedModuleIds(),
                invested.empires().getFirst().unlockedTechIds()));
    }

    @Test
    public void corporateBlueprintsCaptureTheirEmpiresDriveOptimization() {
        GameState baseline = investmentProcessor.invest(fissionInvestmentState(), "corp_test", "earth",
                "FLEET", CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
        var baseDesign = baseline.shipDesigns().getFirst();
        for (String path : List.of("PATH_A", "PATH_B")) {
            var modifiers = new ResearchProcessor().evaluateOptimizationPath(path, 1.0);
            GameState selected = fissionInvestmentState().toBuilder().applicationOptimizations(List.of(
                    new ApplicationOptimization("terran", "fission_engines", path, modifiers))).build();
            GameState invested = investmentProcessor.invest(selected, "corp_test", "earth", "FLEET",
                    CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
            var design = invested.shipDesigns().getFirst();
            var order = invested.shipConstructionOrders().getFirst();
            var quote = ShipConstructionRequirements.estimate(design);
            assertEquals(baseDesign.totalThrustN() * modifiers.effectMultiplier(), design.totalThrustN(), 0.001);
            assertEquals(4 + modifiers.complexityShift(), ShipManufacturingCapacity.requiredComplexity(design));
            assertEquals(1.0 + 6_000.0 / 25_000.0 * (modifiers.costMultiplier() - 1.0),
                    design.manufacturingProfile().costMultiplier(), 0.000001);
            assertEquals(quote.workUnits(), order.requiredWorkHours());
            assertEquals(quote.materialsKg(), order.requiredMaterialsKg());
            assertEquals(18_000.0, invested.corporations().getFirst().liquidCapitalReserves());
        }
    }

    @Test
    public void corporateCargoOptimizationCapturesCapacityAndBuildCosts() {
        GameState state = withApplicationResearch(fissionInvestmentState(), "pressurized_cargo_holds");
        var improvement = new ResearchProcessor().evaluateOptimizationPath("PATH_A", 1.0);
        state = state.toBuilder().applicationOptimizations(List.of(
                new ApplicationOptimization("terran", "pressurized_cargo_holds", "PATH_A", improvement))).build();
        GameState invested = investmentProcessor.invest(state, "corp_test", "earth", "FLEET",
                CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
        var design = invested.shipDesigns().getFirst();
        assertEquals(57_500.0, design.maxCargoMassKg(), 0.001);
        assertEquals(325_000.0, design.minLaunchThrustRequiredN(), 0.001);
        assertEquals(850_000.0, design.totalThrustN());
        assertEquals(1.0 + 2_000.0 / 25_000.0 * 0.20, design.manufacturingProfile().costMultiplier(), 0.000001);
        assertEquals(ShipConstructionRequirements.estimate(design).workUnits(),
                invested.shipConstructionOrders().getFirst().requiredWorkHours());
    }

    @Test
    public void corporateFactoryConstructionQuotesTheTierRequiredByItsSelectedVariant() {
        GameState state = withApplicationResearch(investmentState("AGRICULTURE", 30_000.0, "food_matrix"),
                "industrial_soil_cultivation");
        state = state.toBuilder().applicationOptimizations(List.of(new ApplicationOptimization("terran",
                "industrial_soil_cultivation", "PATH_A", new ResearchProcessor().evaluateOptimizationPath("PATH_A", 1.0)))).build();
        GameState invested = investmentProcessor.invest(state, "corp_test", "earth", "INFRASTRUCTURE",
                CorporateInvestmentProcessor.INFRASTRUCTURE_COST);
        var quote = invested.expansionProjects().getFirst();
        assertEquals(2, quote.targetTier());
        assertEquals(1_200.0, quote.requiredWorkHours(), 0.001);
        assertEquals(1_200.0, quote.requiredMaterialsKg().get("refined_iron"), 0.001);
    }

    @Test
    public void subsequentCorporateOrdersKeepTheExistingBlueprintQuoteAfterPathChanges() {
        var research = new ResearchProcessor();
        GameState selected = fissionInvestmentState().toBuilder().applicationOptimizations(List.of(
                new ApplicationOptimization("terran", "fission_engines", "PATH_A",
                        research.evaluateOptimizationPath("PATH_A", 1.0)))).build();
        GameState invested = investmentProcessor.invest(selected, "corp_test", "earth", "FLEET",
                CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
        var design = invested.shipDesigns().getFirst();
        var firstOrder = invested.shipConstructionOrders().getFirst();
        assertFalse(investmentProcessor.canInvest(invested, "corp_test", "earth", "FLEET",
                CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST));
        GameState switched = invested.toBuilder().shipConstructionOrders(List.of()).applicationOptimizations(List.of(
                new ApplicationOptimization("terran", "fission_engines", "PATH_B",
                        research.evaluateOptimizationPath("PATH_B", 1.0)))).build();
        GameState reordered = investmentProcessor.invest(switched, "corp_test", "earth", "FLEET",
                CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
        assertEquals(List.of(design), reordered.shipDesigns());
        assertEquals(firstOrder.requiredWorkHours(), reordered.shipConstructionOrders().getFirst().requiredWorkHours());
        assertEquals(firstOrder.requiredMaterialsKg(), reordered.shipConstructionOrders().getFirst().requiredMaterialsKg());
    }

    @Test
    public void corporateProcurementRejectsAnExistingBlueprintWithAnUnresearchedDrive() {
        GameState fission = investmentProcessor.invest(fissionInvestmentState(), "corp_test", "earth", "FLEET",
                CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
        GameState unresearched = investmentState("TRANSPORT", 30_000.0, "food_matrix")
                .withShipDesigns(fission.shipDesigns());
        assertSame(unresearched, investmentProcessor.invest(unresearched, "corp_test", "earth", "FLEET",
                CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST));
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

    private GameState fissionInvestmentState() {
        GameState state = investmentState("TRANSPORT", 30_000.0, "food_matrix");
        Empire empire = new Empire("terran", "Terran", "human", "Individualist", 1000.0,
                0.15, List.of("sol"), List.of(), Map.of(),
                List.of("industrial_production", "rocketry", "computers", "nuclear_fission", "fission_engines"), List.of());
        return state.toBuilder().empires(List.of(empire)).build();
    }

    private GameState withApplicationResearch(GameState state, String applicationId) {
        Empire empire = state.empires().getFirst();
        var knowledge = new java.util.ArrayList<>(empire.unlockedTechIds());
        knowledge.add(applicationId);
        return state.withEmpires(List.of(new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(),
                empire.treasuryCredits(), empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(),
                empire.systemGovernorAssignments(), knowledge, empire.activeShipDesignIds())));
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
