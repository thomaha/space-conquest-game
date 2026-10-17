package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.ship.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded actual mixed fleet damage, finite charge and paid electronics recovery. */
class MixedFleetRecoveryScenarioTest {
    private final AICombatServiceTest fixture = new AICombatServiceTest();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final List<String> WEAPONS = List.of(CombatModuleCatalog.LASER,
            CombatModuleCatalog.PARTICLE_BEAM, CombatModuleCatalog.ION, CombatModuleCatalog.MASS_DRIVER);
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "fleet"); }
    private GameState opening() throws Exception {
        var state = fixture.world(CombatModuleCatalog.LASER, 10000, FleetLocation.Site.docked("port"));
        var designs = new ArrayList<ShipDesign>(); var ships = new ArrayList<ShipInstance>();
        for (String id : WEAPONS) {
            var module = CombatModuleCatalog.module(id);
            var world = fixture.world(id, 2 + module.powerDrawKw(), FleetLocation.Site.docked("port"));
            ObjectNode json = mapper.valueToTree(world.shipDesigns().getFirst()); json.put("id", id);
            designs.add(mapper.treeToValue(json, ShipDesign.class));
            ships.add(new ShipInstance(id, id, "ai", 1000, 0, 100, module.ammunitionId().isBlank() ? Map.of()
                    : Map.of(module.ammunitionId(), 10 * module.operationalStats().get("ammunitionMassKg")))
                    .withPowerState(new ShipPowerState(Map.of("refined_uranium", 10.0), "rp1", "uranium",
                            8, false, 1, 1, 0, 0, 0, 0, 0)));
        }
        state = state.withShipDesigns(designs).withFleets(List.of(fleet(state).withShips(ships)))
                .toBuilder().powerGrids(List.of(new PowerGridState("port", 100, 0, 100, 1000, 1000, false))).build();
        return new GuardObjectCommand("ai", "fleet", FleetGuardOrder.TargetType.STATION, "port").apply(state);
    }
    private GameState damage(GameState state) throws Exception {
        var world = fixture.world(CombatModuleCatalog.ION, 10000, FleetLocation.Site.docked("port"));
        ObjectNode json = mapper.valueToTree(world.shipDesigns().getFirst()); json.put("id", "enemy"); json.put("ownerEntityId", "other");
        json.set("equippedModuleIds", mapper.valueToTree(Collections.nCopies(4, CombatModuleCatalog.ION)));
        ((ObjectNode) json.get("powerProfile")).put("hotelKw", 2 + 4 * CombatModuleCatalog.module(CombatModuleCatalog.ION).powerDrawKw());
        var enemyDesign = mapper.treeToValue(json, ShipDesign.class);
        var enemy = new Fleet("enemy", "Enemy", "other", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("enemy", "enemy", "other", 1000000000, 0, 0, Map.of())
                        .withPowerState(world.fleets().getFirst().ships().getFirst().powerState())),
                FleetLocation.at(FleetLocation.Site.docked("port")));
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(enemyDesign); int[] rounds = {0};
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(state), enemy, designs,
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, ++rounds[0] >= 10), (a, b) -> true, (a, b) -> 0);
        var trace = new StringBuilder("# Mixed fleet combat output\n\nSeed 42; at most ten rounds.\n\n");
        result.roundReports().forEach(round -> {
            trace.append("## Round ").append(round.roundNumber()).append("\n\n");
            round.detailedActions().forEach(action -> trace.append("- ").append(action.description()).append('\n'));
            trace.append('\n');
        });
        Files.writeString(Path.of("target", "mixed-fleet-combat.md"), trace);
        assertTrue(result.roundReports().stream().flatMap(round -> round.detailedActions().stream())
                .anyMatch(action -> action.phase().equals("WEAPON_HIT") && action.description().contains("electronics damage")
                        && !action.description().contains("electronics damage 0.000")));
        return state.withFleets(List.of(result.survivingAttackerFleet()));
    }
    private Map<String, Double> repairRecipe(GameState state) {
        var recipe = new TreeMap<String, Double>();
        for (var ship : fleet(state).ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            ship.moduleDamage().damageByMount().forEach((mount, points) ->
                    ShipModuleDamage.damageableModule(design, design.equippedModuleIds().get(mount)).materialCostsKg()
                            .forEach((id, kg) -> recipe.merge(id, kg * points / ShipModuleDamage.DURABILITY, Double::sum)));
        }
        return Map.copyOf(recipe);
    }
    @Test void actualIonDamageRequiresPaidMaterialsAndFiniteChargeBeforeTheMixedGuardCanResume() throws Exception {
        var initial = opening(); var damaged = damage(initial); var recipe = repairRecipe(damaged);
        assertEquals(4, fleet(damaged).ships().size()); assertFalse(recipe.isEmpty());
        assertTrue(fleet(damaged).ships().stream().allMatch(ship -> ship.currentHullHealth() == 1000));
        assertTrue(fleet(damaged).ships().stream().anyMatch(ship -> ship.moduleDamage().damageByMount().values().contains(100.0)));
        var service = new ServiceAICombatFleetCommand("ai", "fleet"); var queue = new CommandQueue();
        queue.submit(service); var paused = queue.drainAndExecute(damaged);
        assertTrue(FleetWeaponRecovery.active(fleet(paused))); assertNull(fleet(paused).guardOrder());
        var shortage = paused.toBuilder().commercialHubs(List.of()).build();
        var blocked = BaseRepairProcessor.advanceDay(shortage);
        assertEquals(fleet(shortage).ships(), fleet(blocked).ships()); assertEquals(shortage.empires(), blocked.empires());
        var orders = new TreeMap<String, MarketOrder>();
        recipe.forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg + 1, 0, 1, 0)));
        var ready = paused.toBuilder().commercialHubs(List.of(new CommercialHub("repair", "port", 0,
                10000, 10000, 10, orders))).build();
        var current = ready; int days = 0;
        while (fleet(current).ships().stream().anyMatch(ship -> !ship.moduleDamage().damageByMount().isEmpty()) && days++ < 25)
            current = BaseRepairProcessor.advanceDay(current).withTurn(current.turn() + 1);
        assertTrue(days < 25, "Bounded repair must finish");
        assertTrue(fleet(current).ships().stream().allMatch(ship -> ship.moduleDamage().equals(ShipModuleDamage.empty())));
        var repaired = current; int purchases = 0;
        while (FleetWeaponRecovery.needs(current, fleet(current)) && purchases++ < 12) {
            queue.submit(service); current = queue.drainAndExecute(current);
        }
        assertTrue(purchases < 12); assertFalse(FleetWeaponRecovery.needs(current, fleet(current)));
        var completed = FleetProtectionReturn.advance(current);
        assertEquals(fleet(initial).guardOrder(), fleet(completed).guardOrder());
        recipe.forEach((id, kg) -> assertEquals(1, repaired.commercialHubs().getFirst().activeOrders().get(id).supplyKg(), 1e-6, id));
        double points = fleet(damaged).ships().stream().flatMap(ship -> ship.moduleDamage().damageByMount().values().stream())
                .mapToDouble(Double::doubleValue).sum();
        assertEquals(ready.empires().getFirst().treasuryCredits() - recipe.values().stream().mapToDouble(Double::doubleValue).sum()
                - points * BaseRepairCatalog.LABOR_CREDITS_PER_POINT, repaired.empires().getFirst().treasuryCredits(), 1e-6);
        double input = completed.fleetServiceEvents().stream().mapToDouble(FleetServiceEvent::gridInputKwh).sum();
        assertTrue(input > 0); assertEquals(ready.powerGrids().getFirst().currentStoredKwh()
                - completed.powerGrids().getFirst().currentStoredKwh(), input, 1e-6);
        assertEquals(FleetServiceEvent.Kind.MISSION_RESUMED, completed.fleetServiceEvents().getLast().kind());
        assertTrue(fleet(initial).ships().stream().allMatch(ship -> ship.moduleDamage().equals(ShipModuleDamage.empty())));
        Files.writeString(Path.of("target", "mixed-fleet-recovery.md"), "# Mixed fleet recovery output\n\n"
                + "Seed 42; at most ten combat rounds and 25 repair days. Four surviving weapon ships.\n\n"
                + "Ion damage removed " + points + " electronics points and zero hull health.\n\n"
                + "Paid repair materials kg: " + new TreeMap<>(recipe) + ". Repair days: " + days + ".\n\n"
                + "Actual grid input: " + input + " kWh. Guard mission restored.\n");
    }
}
