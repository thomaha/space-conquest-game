package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CombatSensorContactTest {
    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }
    private ShipDesign design(String owner, double output) {
        var profile = new ShipPowerProfile(0, 0, output, 0, 0, 0, 0, 10, 14, 0, 0, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6_000_000)));
        return new ShipDesign(owner, owner, owner, ShipRole.COMBAT_SHIP, "steel",
                List.of(SensorModuleCatalog.BASIC, CombatModuleCatalog.GUN), "steel", 0,
                1000, 1000, 1000, 100, 100, 0, 1000, false, false, ShipManufacturingProfile.baseline(), profile);
    }
    private Fleet fleet(String owner, boolean sensor) {
        var ship = new ShipInstance(owner + "-ship", owner, owner, 10000, 0, 100, Map.of("cannon_round", 1000.0))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        if (!sensor) ship = ship.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        return new Fleet(owner, owner, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship))
                .withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
    }
    private GameState world(boolean ownSensor, boolean enemySensor, double ownOutput) {
        return GameState.builder().empires(List.of(empire("own"), empire("enemy")))
                .shipDesigns(List.of(design("own", ownOutput), design("enemy", 1000)))
                .fleets(List.of(fleet("own", ownSensor), fleet("enemy", enemySensor)))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of())))
                .diplomaticRelations(List.of(new DiplomaticRelation("own", "enemy", "TOTAL_WAR", 0))).build();
    }
    private GameState fight(GameState state) {
        return new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42))).resolveEncounters(state);
    }

    @Test void staleIdsAndRecentObservationsCannotStartAnEncounterWithoutLiveCoverage() {
        var state = world(false, false, 1000); var own = state.fleets().getFirst();
        var position = SensorPosition.at(state, "sol", own.location());
        var remembered = new SensorContact("enemy", "enemy", "sol", position, 0, 100000);
        state = state.withFogOfWarStates(List.of(new FogOfWarState("own", List.of("sol"), List.of(), List.of("enemy"),
                List.of(), List.of(), List.of(remembered))));
        assertEquals(FleetThreatAssessment.Action.ESCAPE, FleetThreatAssessment.caution(state, own).action());
        var result = fight(state);
        assertTrue(result.fleetEngagements().isEmpty()); assertEquals(state.fleets(), result.fleets());
    }

    @Test void blindSidePaysUpkeepButNoSalvoWhileLiveOpponentKeepsFiring() {
        var initial = world(true, false, 1000); var result = fight(initial);
        assertEquals(1, result.fleetEngagements().size());
        var own = FleetOrganization.find(result, "own"); var enemy = FleetOrganization.find(result, "enemy");
        assertEquals(10000, own.ships().getFirst().currentHullHealth());
        assertTrue(enemy.ships().getFirst().currentHullHealth() < 10000);
        assertTrue(enemy.ships().getFirst().generatorFuelMassKg() < 1);
        assertEquals(10000, initial.fleets().getLast().ships().getFirst().currentHullHealth());
    }

    @Test void actualPowerSheddingRemovesLiveTargetingOnSubsequentRounds() throws Exception {
        // An application-defined energy-only test variant can fire with scarce output before sensors are shed.
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode variant = mapper.valueToTree(design("own", 5));
        ((com.fasterxml.jackson.databind.node.ObjectNode) variant.get("combatModules").get(CombatModuleCatalog.GUN)).put("ammunitionId", "");
        var initial = world(true, true, 5).withShipDesigns(List.of(mapper.treeToValue(variant, ShipDesign.class), design("enemy", 1000)));
        var rounds = new AtomicInteger();
        var own = initial.fleets().getFirst(); var enemy = initial.fleets().getLast().withStance(TacticalCombatProcessor.STANCE_EVASIVE_RETREAT);
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(own, enemy, initial.shipDesigns(),
                (a, b) -> { rounds.incrementAndGet(); return new TacticalCombatProcessor.Withdrawal(a, b, false); },
                (a, b) -> SensorCoverage.detects(initial.withFleets(List.of(a, b)), a.ownerEntityId(), b));
        assertTrue(rounds.get() > 1);
        assertTrue(result.survivingAttackerFleet().ships().getFirst().powerState().shedServices().contains(ShipServicePower.SENSORS));
        assertTrue(result.roundReports().getFirst().attackerDamageDealt() > 0);
        assertTrue(result.roundReports().stream().skip(1).allMatch(round -> round.attackerDamageDealt() == 0));
        assertTrue(result.visualRounds().stream().skip(1).allMatch(round -> round.eventLogs().stream()
                .anyMatch(text -> text.contains("own: firing held"))));
    }

    @Test void sharedObserverKeepsFiringAndLosingThenReacquiringCoverageChangesOnlyFollowingRounds() {
        var initial = world(false, true, 1000); var own = initial.fleets().getFirst(); var enemy = initial.fleets().getLast();
        var observer = new Fleet("observer", "Observer", "own", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(
                new ShipInstance("observer-ship", "own", "own", 10000, 0, 0, Map.of())
                        .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0))));
        var rounds = new AtomicInteger();
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(own, enemy, initial.shipDesigns(),
                (a, b) -> { rounds.incrementAndGet(); return new TacticalCombatProcessor.Withdrawal(a, b, false); },
                (a, b) -> SensorCoverage.detects(initial.withFleets(rounds.get() == 1 ? List.of(a, b) : List.of(a, b, observer)), a.ownerEntityId(), b));
        assertTrue(result.roundReports().get(0).attackerDamageDealt() > 0);
        assertEquals(0, result.roundReports().get(1).attackerDamageDealt());
        assertTrue(result.roundReports().get(2).attackerDamageDealt() > 0);
        assertEquals(10000, observer.ships().getFirst().currentHullHealth());
    }

    @Test void losingBothViewsEndsFurtherRoundsWithoutSpendingAnotherSalvo() {
        var initial = world(true, true, 1000); var rounds = new AtomicInteger();
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(initial.fleets().getFirst(), initial.fleets().getLast(),
                initial.shipDesigns(), (a, b) -> { rounds.incrementAndGet(); return new TacticalCombatProcessor.Withdrawal(a, b, false); },
                (a, b) -> rounds.get() == 0 && SensorCoverage.detects(initial.withFleets(List.of(a, b)), a.ownerEntityId(), b));
        assertEquals(1, result.roundReports().size());
        assertTrue(result.visualRounds().getFirst().eventLogs().contains("Both fleets lost live sensor contact; firing ends."));
        assertEquals(1 - (14.0 / 60 + 1) / 6_000_000, result.survivingAttackerFleet().ships().getFirst().generatorFuelMassKg(), 1e-12);
    }
}
