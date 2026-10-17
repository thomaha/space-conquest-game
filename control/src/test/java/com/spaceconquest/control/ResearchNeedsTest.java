package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchNeedsTest {
    private GameState knowledge(GameState state, String... ids) {
        var old = state.empires().getFirst(); var known = new ArrayList<>(old.unlockedTechIds()); known.addAll(List.of(ids));
        return state.withEmpires(List.of(new Empire(old.id(), old.name(), old.raceId(), old.societyStructure(), old.treasuryCredits(),
                old.corporateTaxRate(), old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(), known, old.activeShipDesignIds())));
    }
    private StartResearchCommand choice(GameState state) {
        return (StartResearchCommand) EmpireResearchPlanner.plan(state, state.empires().getFirst()).stream()
                .filter(command -> command instanceof StartResearchCommand).findFirst().orElseThrow();
    }
    @Test void corporateFactoryWithMissingApplicationTakesPriorityOverGenericDiscoveries() {
        var state = knowledge(new ResearchLifecycleTest().world(), "industrial_production");
        state = state.toBuilder().corporations(List.of(new Corporation("corp", "Corp", "research", "research-world", "INDUSTRY", 1000, List.of("factory"), List.of(), List.of())))
                .industrialFacilities(List.of(new IndustrialFacility("factory", "research-world", "water_electrolysis", "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "technician", false, 0))).build();
        assertEquals("water_electrolysis", choice(state).targetTechOrAppId()); assertTrue(choice(state).validate(state));
    }
    @Test void localBrownoutCanRequestPaidRefinementAndChoosePerformanceOnlyWhenItFits() throws Exception {
        var state = new ResearchLifecycleTest().world().withIndustrialFacilities(List.of(new IndustrialFacility("solar", "research-world", "solar_power", "research",
                IndustrialFacility.PUBLIC_STATE, 2, 100, "technician", false, 0)))
                .withPowerGrids(List.of(new PowerGridState("research-world", 6000, 7000, -1000, 0, 0, true)));
        assertEquals("solar_power", choice(state).targetTechOrAppId());
        var project = new ResearchProject("completed", "research", "solar_power", true, 500, 500, 5, 1);
        state = state.withApplicationOptimizations(ApplicationRefinement.completed(state.applicationOptimizations(), project, new ResearchProcessor().rollBreakthrough(.1)));
        var plan = EmpireResearchPlanner.plan(state, state.empires().getFirst());
        assertEquals("PATH_A", ((SelectOptimizationPathCommand) plan.stream().filter(command -> command instanceof SelectOptimizationPathCommand).findFirst().orElseThrow()).pathChoice());
        state = state.withIndustrialFacilities(List.of(new IndustrialFacility("solar", "research-world", "solar_power", "research", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0)));
        var solar = DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream())
                .filter(app -> app.id().equals("solar_power")).findFirst().orElseThrow();
        assertEquals("PATH_B", new EmpireResearchNeeds(state, state.empires().getFirst()).optimizationPath(solar));
        state = state.withApplicationOptimizations(ApplicationRefinement.completed(List.of(), project, new ResearchProcessor().rollBreakthrough(.8)));
        var queue = new CommandQueue(); EmpireResearchPlanner.plan(state, state.empires().getFirst()).forEach(queue::submit);
        var resolved = queue.drainAndExecute(state);
        assertEquals(1, ApplicationProduction.modifiers(resolved, "research", "solar_power").effectMultiplier());
        assertNull(resolved.applicationOptimizations().getFirst().pendingOutcome());
    }
    @Test void paidFactoryInputShortagePointsToTheProducingApplication() {
        var state = knowledge(new ResearchLifecycleTest().world(), "industrial_production", "pyro_iron_smelting");
        state = state.withIndustrialFacilities(List.of(new IndustrialFacility("smelt", "research-world", "pyro_iron_smelting", "research", IndustrialFacility.PUBLIC_STATE, 1, 100, "industrial_worker", false, 0)))
                .withIndustryAccounts(List.of(new IndustryAccount("smelt", Map.of(), Map.of(), Map.of(), 0, 10, 0, 0, 0, 0, 1000, false, 0, 0, 100)))
                .withCommercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1000, 1000, 10, Map.of("iron_ore", new MarketOrder("iron_ore", 1000, 0, 1, 0)))));
        assertEquals("carbon_mining", choice(state).targetTechOrAppId());
    }
    @Test void emptyInstalledWeaponMagazinesPrioritizeTheirApplicationDefinedAmmunitionRecipe() {
        var state = knowledge(new ResearchLifecycleTest().world(), "industrial_production", "ship_cannons");
        var module = CombatModuleCatalog.module(CombatModuleCatalog.GUN);
        var design = new ShipDesign("gun", "Gun", "research", ShipRole.COMBAT_SHIP, "steel", List.of(module.id()), "steel", 1, 10000, 1000, 0, 0, 10, 0, 0, true, false);
        var ship = new ShipInstance("ship", "gun", "research", 1000, 0, 0, Map.of());
        state = state.withShipDesigns(List.of(design)).withFleets(List.of(new Fleet("fleet", "Fleet", "research", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship))));
        assertEquals("cannon_ammunition_mfg", choice(state).targetTechOrAppId());
    }
}
