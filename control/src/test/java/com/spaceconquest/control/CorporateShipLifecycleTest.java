package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CorporateShipLifecycleTest {
    @TempDir Path directory;
    private GameState world() throws Exception {
        var state = new ShipRefitQuoteTest().world(); var hub = state.commercialHubs().getFirst();
        var corporation = new Corporation("corp", "Carrier", "owner", "yard", "TRANSPORT", 1000000, List.of(), List.of(), List.of());
        return state.toBuilder().fleets(List.of()).shipDesigns(List.of()).corporations(List.of(corporation))
                .commercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0, 100000000, hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), hub.activeOrders())))
                .marketAccounts(List.of(new MarketAccount(hub.id(), 2000000))).build();
    }
    private GameState reload(GameState state, String checkpoint) throws Exception {
        var file = directory.resolve(checkpoint + ".scsave").toFile(); var saves = new SaveGameManager(directory);
        saves.save(file, state, 1, GameClock.START_TIME.plusDays(state.turn()).toString());
        var loaded = saves.load(file).toGameState(state.turn(), state.status());
        assertEquals(state.shipDesigns(), loaded.shipDesigns()); assertEquals(state.shipConstructionOrders(), loaded.shipConstructionOrders());
        assertEquals(state.shipyardLedger(), loaded.shipyardLedger()); assertEquals(state.corporations(), loaded.corporations());
        return loaded;
    }
    private GameState finish(GameState state) {
        for (int day = 0; day < 30 && !state.shipConstructionOrders().isEmpty(); day++)
            state = new ShipConstructionProcessor().process(state.withTurn(state.turn() + 1));
        assertTrue(state.shipConstructionOrders().isEmpty(), state.shipyardLedger().history().toString()); return state;
    }
    @Test void freelySizedCorporateCandidatesRespectResearchReuseCapturedHardwareAndKeepPlayerCommandsRestricted() throws Exception {
        var state = world(); var small = CorporateShipDesigner.choose(state, "corp", ShipRole.CARGO_TRANSPORT, 1);
        var large = CorporateShipDesigner.choose(state, "corp", ShipRole.CARGO_TRANSPORT, 120000);
        assertNotNull(small); assertNotNull(large); assertNotNull(small.assembly()); assertTrue(small.isProprietaryCorporateDesign());
        assertTrue(large.hullFrame().totalSlots() > small.hullFrame().totalSlots());
        assertTrue(Collections.frequency(large.equippedModuleIds(), "mod_cargo_hold_large") > 1);
        assertTrue(large.hullFrame().totalSlots() <= HullTechnology.maximumCapacity(state, "corp"));
        assertEquals(large.equippedModuleIds().size(), large.assembly().identities().stream().distinct().count());
        assertSame(small, CorporateShipDesigner.choose(state.withShipDesigns(List.of(small)), "corp", ShipRole.CARGO_TRANSPORT, 1));
        var registered = state.withShipDesigns(List.of(small));
        assertFalse(new DesignShipCommand(ShipDesignSpecification.from(large)).validate(state));
        assertFalse(new UpdateShipDesignCommand(ShipDesignSpecification.from(small)).validate(registered));
        var owner = state.empires().getFirst();
        var excluded = new Empire(owner.id(), owner.name(), owner.raceId(), "Hive Mind", owner.treasuryCredits(), 0, owner.controlledSystemIds(),
                owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds());
        assertNull(CorporateShipDesigner.choose(state.withEmpires(List.of(excluded)), "corp", ShipRole.CARGO_TRANSPORT, 1));
        var known = new ArrayList<>(owner.unlockedTechIds()); known.remove("large_hull_engineering");
        var limited = new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0, owner.controlledSystemIds(),
                owner.ministries(), owner.systemGovernorAssignments(), known, owner.activeShipDesignIds());
        var bounded = CorporateShipDesigner.choose(state.withEmpires(List.of(limited)), "corp", ShipRole.CARGO_TRANSPORT, 120000);
        assertNotNull(bounded); assertTrue(bounded.hullFrame().totalSlots() <= HullTechnology.maximumCapacity(state.withEmpires(List.of(limited)), "corp"));
        var request = ShipDesignSpecification.from(large);
        assertFalse(ShipBlueprintFactory.evaluate(state.withEmpires(List.of(limited)), request).valid());
        assertEquals(small, reload(registered, "captured").shipDesigns().getFirst());
    }
    @Test void companyFundsRealConstructionRefitReplacementAndRetirementAcrossReloads() throws Exception {
        var opening = world(); var investment = new CorporateInvestmentProcessor();
        assertFalse(investment.canInvest(opening, "corp", "missing", "FLEET", CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST));
        assertTrue(investment.canInvest(opening, "corp", "yard", "FLEET", CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST));
        var state = investment.invest(opening, "corp", "yard", "FLEET", CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
        var design = state.shipDesigns().getFirst(); var originalOrder = state.shipConstructionOrders().getFirst();
        assertNotNull(design.assembly()); assertEquals(design.assembly().total().materialsKg(), originalOrder.requiredMaterialsKg());
        state = new ShipConstructionProcessor().process(state);
        assertFalse(state.shipConstructionOrders().isEmpty()); assertTrue(state.shipConstructionOrders().getFirst().accumulatedWorkHours() > 0);
        state = finish(reload(state, "partial-build")); String sourceId = state.fleets().getFirst().ships().getFirst().id();
        assertEquals(List.of(sourceId), state.corporations().getFirst().ownedShipIds());
        assertTrue(EconomicOwnership.cash(state, "corp") < EconomicOwnership.cash(opening, "corp") - CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST);
        var modules = new ArrayList<>(design.equippedModuleIds()); modules.add("mod_cargo_hold_large");
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("corporate-upgrade", "More cargo", "corp", design.role(),
                design.hullMaterialId(), modules, design.armorMaterialId(), design.armorThicknessCm(), design.hullFrame().totalSlots() + 8,
                ShipBlueprintAssembly.identitiesFor(design, modules, "corporate-upgrade")));
        assertTrue(evaluated.valid(), evaluated.errors().toString()); var upgraded = evaluated.design();
        state = state.withShipDesigns(List.of(design, upgraded)); var quote = ShipRefitQuote.inspect(state, "corp", sourceId, upgraded.id());
        assertTrue(quote.ready(), quote.blockers().toString()); assertTrue(quote.comparison().structure().workHours() > 0);
        state = finish(reload(new QueueShipRefitCommand("corp", sourceId, upgraded.id()).apply(state), "refit"));
        assertEquals(upgraded.id(), ShipRetirementPlan.fleet(state, sourceId).ships().getFirst().designId());
        assertEquals(List.of(sourceId), state.corporations().getFirst().ownedShipIds());
        var build = new QueueShipBuildCommand("corp", upgraded.id(), "sol"); assertTrue(build.validate(state)); state = build.apply(state);
        state = state.withShipConstructionOrders(state.shipConstructionOrders().stream().map(order -> order.withReplacement(new ShipReplacementPlan(sourceId))).toList());
        state = finish(reload(state, "replacement")); String replacementId = state.shipyardLedger().replacements().get(sourceId);
        assertNotNull(replacementId); assertEquals(2, state.corporations().getFirst().ownedShipIds().size());
        assertNotNull(ShipRetirementPlan.fleet(state, sourceId));
        var replacement = ShipRetirementPlan.fleet(state, replacementId).ships().getFirst();
        state = ShipPowerResupply.buy(state, replacementId, "yard", replacement.powerState().chemicalMixture(), 1000);
        if (upgraded.powerProfile().fissionKw() > 0) state = ShipPowerResupply.buy(state, replacementId, "yard", "uranium", 10);
        var retire = new RequestAIShipRetirementCommand("corp", sourceId); assertTrue(retire.validate(state), ShipRetirementQuote.inspect(state, "corp", sourceId).blockers().toString());
        double buyerCash = state.marketAccounts().getFirst().unsettledSalesCredits();
        state = finish(reload(retire.apply(state), "retirement")); assertNull(ShipRetirementPlan.fleet(state, sourceId));
        assertNotNull(ShipRetirementPlan.fleet(state, replacementId)); assertEquals(List.of(replacementId), state.corporations().getFirst().ownedShipIds());
        assertTrue(state.shipyardLedger().replacements().isEmpty()); assertTrue(state.marketAccounts().getFirst().unsettledSalesCredits() < buyerCash);
        assertEquals(state.fleets(), reload(state, "complete").fleets());
    }
}
