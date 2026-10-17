package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIConstructionPriorityTest {
    @TempDir Path directory;
    private ShipDesign combat(ShipDesign base, String id, String weapon) {
        var hardware = CombatModuleCatalog.module(weapon);
        if (weapon.equals(CombatModuleCatalog.ION)) {
            // Equal raw damage and accuracy isolate the captured electronic-disabling contribution.
            var stats = new HashMap<>(hardware.operationalStats()); var laser = CombatModuleCatalog.module(CombatModuleCatalog.LASER);
            stats.put("weaponDamage", laser.operationalStats().get("weaponDamage")); stats.put("accuracy", laser.operationalStats().get("accuracy"));
            hardware = new ShipModule(hardware.id(), hardware.name(), hardware.slotSize(), hardware.slotCost(), hardware.dryMassKg(), hardware.powerDrawKw(),
                    hardware.powerOutputKw(), hardware.thrustOutputN(), hardware.complexityLevel(), hardware.materialCostsKg(), stats, hardware.ammunitionId());
        }
        return new ShipDesign(id, id, "owner", ShipRole.COMBAT_SHIP, base.hullMaterialId(), List.of(weapon), base.armorMaterialId(), base.armorThicknessCm(),
                base.totalDryMassKg(), base.maxCargoMassKg(), base.fuelCapacityKg(), base.powerBalanceKw(), base.calculatedStructuralIntegrity(), base.minLaunchThrustRequiredN(),
                base.totalThrustN(), base.isValidForLaunch(), false, base.manufacturingProfile(), base.powerProfile(), base.sensorProfile(), Map.of(weapon, hardware));
    }
    private GameState world() throws Exception {
        var state = new AIShipyardInvestmentTest().world("owner").withShipConstructionOrders(List.of()); var base = state.shipDesigns().getFirst();
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(combat(base, "ion", CombatModuleCatalog.ION)); designs.add(combat(base, "laser", CombatModuleCatalog.LASER));
        return state.toBuilder().shipDesigns(designs).diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
    }
    private ShipConstructionOrder order(String id, String design, String customer, double hours, Map<String, Double> bill) {
        return new ShipConstructionOrder(id, customer, design, "a", "port-a", 0, hours, bill, Map.of(), customer.equals("owner") ? null : new ShipyardContract("owner", 2, 0));
    }
    @Test void urgentDisablingCapabilityGetsPaidWorkAheadOfEarlierCommercialContracts() throws Exception {
        var state = world().withShipConstructionOrders(List.of(order("commercial", "corporate-design", "corp", 1000, Map.of("steel", 1000.0)),
                order("laser", "laser", "owner", 1000, Map.of("steel", 1000.0)), order("ion", "ion", "owner", 1000, Map.of("steel", 1000.0))));
        var command = new PrioritizeAIShipConstructionCommand("owner"); var ranked = command.apply(state);
        assertEquals("ion", ranked.shipConstructionOrders().getFirst().id()); assertEquals(state.empires(), ranked.empires());
        assertEquals(state.corporations(), ranked.corporations()); assertEquals(state.commercialHubs(), ranked.commercialHubs());
        assertSame(ranked, command.apply(ranked));
        var built = new ShipConstructionProcessor().process(ranked);
        assertEquals(100, built.shipConstructionOrders().getFirst().accumulatedWorkHours(), .001);
        assertEquals(100, built.shipConstructionOrders().stream().mapToDouble(ShipConstructionOrder::accumulatedWorkHours).sum(), .001);
        assertEquals(0, built.shipConstructionOrders().stream().filter(item -> item.id().equals("commercial")).findFirst().orElseThrow().yardContract().paidFeesCredits());
        assertTrue(built.shipConstructionOrders().getFirst().priority().reason().contains("threat reduction"));
    }
    @Test void anUnfundedUrgentOrderCannotBlockPaidCorporateWork() throws Exception {
        var state = world().withShipConstructionOrders(List.of(order("blocked", "ion", "owner", 1000, Map.of("missing", 1000.0)),
                order("commercial", "corporate-design", "corp", 1000, Map.of("steel", 1000.0))));
        var ranked = new PrioritizeAIShipConstructionCommand("owner").apply(state); assertEquals("commercial", ranked.shipConstructionOrders().getFirst().id());
        var result = new ShipConstructionProcessor().process(ranked); var contract = result.shipConstructionOrders().getFirst();
        assertEquals(100, contract.accumulatedWorkHours()); assertEquals(200, contract.yardContract().paidFeesCredits());
        var blocked = result.shipConstructionOrders().getLast(); assertFalse(blocked.priority().ready()); assertEquals(0, blocked.priority().waitingTurns());
    }
    @Test void continuousUrgentArrivalsCannotStarveASavedCommercialOrder() throws Exception {
        var state = world().withShipConstructionOrders(List.of(order("commercial", "corporate-design", "corp", 1000, Map.of("steel", 1000.0))));
        var command = new PrioritizeAIShipConstructionCommand("owner"); boolean protectedWork = false;
        for (int day = 0; day <= ConstructionPriorityPolicy.current().starvationWaitTurns() + 1; day++) {
            var orders = new ArrayList<>(state.shipConstructionOrders()); orders.add(order("urgent-" + day, "ion", "owner", 1000, Map.of("steel", 1000.0)));
            state = command.apply(state.withTurn(day).withShipConstructionOrders(orders));
            assertSame(state, command.apply(state));
            boolean protectedFirst = state.shipConstructionOrders().getFirst().id().equals("commercial") && state.shipConstructionOrders().getFirst().priority().protectedTurn();
            if (day == 3) {
                var file = directory.resolve("priority.scsave").toFile(); var saves = new SaveGameManager(directory);
                saves.save(file, state, 1, GameClock.START_TIME.plusDays(day).toString()); var restored = saves.load(file).toGameState(day, state.status());
                assertEquals(state.shipConstructionOrders(), restored.shipConstructionOrders()); state = restored;
            }
            state = new ShipConstructionProcessor().process(state);
            var commercial = state.shipConstructionOrders().stream().filter(item -> item.id().equals("commercial")).findFirst().orElseThrow();
            if (protectedFirst) { assertTrue(commercial.accumulatedWorkHours() > 0); assertEquals(0, commercial.priority().waitingTurns()); assertFalse(commercial.priority().protectedTurn()); protectedWork = true; }
        }
        assertTrue(protectedWork);
    }
    @Test void essentialSupplyAndNearCompletionInfluenceReadyQueues() throws Exception {
        var state = world().toBuilder().diplomaticRelations(List.of()).build().withShipConstructionOrders(List.of(
                order("combat", "ion", "owner", 1000, Map.of("steel", 1000.0)), order("supply", "design", "owner", 1000, Map.of("steel", 1000.0))));
        var command = new PrioritizeAIShipConstructionCommand("owner"); assertEquals("supply", command.apply(state).shipConstructionOrders().getFirst().id());
        state = state.toBuilder().industrialFacilities(List.of()).build().withShipConstructionOrders(List.of(order("long", "laser", "owner", 1000, Map.of("steel", 1000.0)),
                order("short", "laser", "owner", 100, Map.of("steel", 100.0))));
        assertEquals("short", command.apply(state).shipConstructionOrders().getFirst().id());
    }
    @Test void normalCampaignTicksHonorTheSavedOrderAndPayOnlyActualContractWork() throws Exception {
        var state = world().withShipConstructionOrders(List.of(order("commercial", "corporate-design", "corp", 1000, Map.of("steel", 1000.0)),
                order("urgent", "ion", "owner", 1000, Map.of("steel", 1000.0))));
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state); var queue = new CommandQueue();
        queue.submit(new PrioritizeAIShipConstructionCommand("owner")); queue.processCommands(engine); engine.stepTurn();
        var built = engine.getGameState(); var urgent = built.shipConstructionOrders().getFirst();
        assertEquals("urgent", urgent.id()); assertEquals(100, urgent.accumulatedWorkHours(), .001); assertEquals(0, urgent.priority().waitingTurns());
        assertEquals(0, built.shipConstructionOrders().getLast().yardContract().paidFeesCredits());
        assertEquals(100, built.shipyardLedger().history().stream().filter(row -> row.turn() == built.turn()).mapToDouble(ShipyardReceipt::workUnits).sum(), .001);
    }
    @Test void replacementAndApplicationCapturedChangesAffectScoresWithoutChangingHardware() throws Exception {
        var state = world().withShipConstructionOrders(List.of(order("build", "ion", "owner", 1000, Map.of("steel", 1000.0))));
        var command = new PrioritizeAIShipConstructionCommand("owner"); double baseline = command.apply(state).shipConstructionOrders().getFirst().priority().score();
        var damaged = new Fleet("damaged", "Damaged", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("damaged-ship", "ion", "owner", 1, 0, 0, Map.of())));
        var replaced = command.apply(state.withFleets(List.of(damaged))); assertTrue(replaced.shipConstructionOrders().getFirst().priority().score() > baseline);
        assertEquals(state.shipDesigns(), replaced.shipDesigns()); assertEquals(1, replaced.fleets().getFirst().ships().getFirst().currentHullHealth());
    }
}
