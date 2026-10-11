package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.ShipCombatStats;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FundedThreatAssessmentTest {
    private static final double HOURS = TacticalCombatProcessor.SECONDS_PER_ROUND / 3600;
    private ShipDesign design(String id, double reactor, double discharge, boolean shield, String... weapons) {
        var modules = new ArrayList<>(weapons.length == 0 ? List.of(CombatModuleCatalog.GUN) : List.of(weapons));
        modules.add(SensorModuleCatalog.BASIC);
        if (shield) modules.add(CombatModuleCatalog.SHIELD);
        double hotel = 2 + modules.stream().map(ShipComponentCatalog::module).mapToDouble(ShipModule::powerDrawKw).sum();
        var power = new ShipPowerProfile(0, 0, reactor, 10000, discharge, discharge, 0, 10,
                hotel, 0, 0, .65, Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6000)));
        return new ShipDesign(id, id, id.equals("enemy") ? "enemy" : "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), power);
    }
    private ShipInstance ship(String id, ShipDesign design, String owner, double fuel, double battery, double shields) {
        return new ShipInstance(id, design.id(), owner, 1000, shields, 100, Map.of())
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", fuel), "rp1", "uranium", battery, false, 1, 1, 0, 0, 0, 0, 0));
    }
    private Fleet fleet(String id, String owner, ShipInstance ship) {
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
    }
    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }
    private GameState world(ShipDesign own, ShipDesign hostile, ShipInstance first, ShipInstance last) {
        return GameState.builder().shipDesigns(List.of(own, hostile)).empires(List.of(empire("owner"), empire("enemy")))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of())))
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)))
                .fleets(List.of(fleet("own", "owner", first), fleet("hostile", "enemy", last))).build();
    }

    private Fleet at(Fleet fleet, double x) {
        var zero = new LocalSiteGeometry.Point(0, 0, 0);
        var trajectory = new VectorFlight.Trajectory(new LocalSiteGeometry.Point(x, 0, 0), zero,
                new LocalSiteGeometry.Point(1, 0, 0), new LocalSiteGeometry.Point(-1, 0, 0), 1, 10);
        var vector = trajectory.at(0); var end = trajectory.at(trajectory.totalSeconds()).position();
        var geometry = new LocalSiteGeometry.Leg(vector.position(), end, VectorFlight.norm(VectorFlight.subtract(end, vector.position())));
        var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, false, null, vector);
        return fleet.withLocation(FleetLocation.at(FleetLocation.Site.deepSpace())
                .depart(FleetLocation.Site.deepSpace("goal-" + fleet.id()), 1).withFlight(flight));
    }

    @Test void outrangedCannonFleetChoosesEscapeWithoutSpendingStores() {
        var own = design("own", 10000, 100, false, CombatModuleCatalog.GUN, CombatModuleCatalog.GUN, CombatModuleCatalog.GUN);
        var enemy = design("enemy", 10000, 100, false, CombatModuleCatalog.LASER);
        var state = world(own, enemy, ship("first", own, "owner", 10, 0, 0), ship("last", enemy, "enemy", 10, 0, 0));
        state = state.withFleets(List.of(at(state.fleets().getFirst(), 0), at(state.fleets().getLast(), 750000)));
        var before = state.fleets(); var assessment = FleetThreatAssessment.assess(state, before.getFirst());
        assertEquals(FleetThreatAssessment.Action.ESCAPE, assessment.action());
        assertEquals(0, assessment.friendlyStrength());
        assertEquals(Math.sqrt(160 * 1000), assessment.hostileStrength(), 1e-9);
        assertTrue(FleetThreatAssessment.strength(state, List.of(before.getFirst())) > FleetThreatAssessment.strength(state, List.of(before.getLast())));
        assertEquals(assessment, FleetThreatAssessment.assess(state, before.getFirst()));
        assertEquals(before, state.fleets());
        var distant = state.withFleets(List.of(before.getFirst(), at(before.getLast(), 1000001)));
        var outside = FleetThreatAssessment.assess(distant, distant.fleets().getFirst());
        assertEquals(FleetThreatAssessment.Action.HOLD, outside.action());
        assertEquals(0, outside.friendlyStrength()); assertEquals(0, outside.hostileStrength());
    }

    @Test void mixedMountAssessmentCountsOnlyReachableSalvoOnceAcrossOpponents() {
        var own = design("own", 10000, 100, false, CombatModuleCatalog.GUN, CombatModuleCatalog.MASS_DRIVER, CombatModuleCatalog.LASER);
        var enemy = design("enemy", 10000, 100, false, CombatModuleCatalog.LASER);
        var state = world(own, enemy, ship("first", own, "owner", 10, 0, 0), ship("last", enemy, "enemy", 10, 0, 0));
        var friendly = at(state.fleets().getFirst(), 0); var hostile = at(state.fleets().getLast(), 150000);
        state = state.withFleets(List.of(friendly, hostile));
        var assessment = FleetThreatAssessment.assess(state, friendly);
        assertEquals(FleetThreatAssessment.Action.HOLD, assessment.action());
        assertEquals(Math.sqrt(380 * 1000), assessment.friendlyStrength(), 1e-9);
        assertEquals(Math.sqrt(460 * 1000), FleetThreatAssessment.strength(state, List.of(friendly)), 1e-9);
        assertEquals(assessment.friendlyStrength(), FleetThreatAssessment.engagementStrength(state, List.of(friendly),
                List.of(hostile, at(hostile, 750000))), 1e-9);
        assertEquals(Math.sqrt(160 * 1000), FleetThreatAssessment.engagementStrength(state, List.of(friendly), List.of(at(hostile, 750000))), 1e-9);
        assertEquals(0, FleetThreatAssessment.engagementStrength(state, List.of(friendly), List.of()));
    }

    @Test void outputShortfallChangesHoldToEscapeAndBatterySupportRestoresHold() {
        var own = design("own", 20, 100, false); var enemy = design("enemy", 74, 100, false);
        var first = ship("first", own, "owner", 10, 0, 0); var last = ship("last", enemy, "enemy", 10, 0, 0);
        var weak = world(own, enemy, first, last);
        var assessed = FleetThreatAssessment.assess(weak, weak.fleets().getFirst());
        assertEquals(FleetThreatAssessment.Action.ESCAPE, assessed.action());
        assertEquals(Math.sqrt(8 * 1000), assessed.friendlyStrength(), 1e-9);
        assertEquals(Math.sqrt(80 * 1000), assessed.hostileStrength(), 1e-9);
        var restored = world(own, enemy, ship("first", own, "owner", 10, 100, 0), last);
        var funded = FleetThreatAssessment.assess(restored, restored.fleets().getFirst());
        assertEquals(FleetThreatAssessment.Action.HOLD, funded.action());
        assertEquals(funded.friendlyStrength(), funded.hostileStrength(), 1e-9);
        assertEquals(List.of("hostile"), assessed.hostileFleetIds());
    }

    @Test void weakHostileGenerationAndSpentBatteriesDoNotCauseAnInventedOverwhelmingThreat() {
        var own = design("own", 20, 100, false); var enemy = design("enemy", 20, 100, false);
        var first = ship("first", own, "owner", 10, 0, 0);
        var powered = world(own, enemy, first, ship("last", enemy, "enemy", 10, 100, 0));
        assertEquals(FleetThreatAssessment.Action.ESCAPE, FleetThreatAssessment.assess(powered, powered.fleets().getFirst()).action());
        var depleted = world(own, enemy, first, ship("last", enemy, "enemy", 10, 0, 0));
        assertEquals(FleetThreatAssessment.Action.HOLD, FleetThreatAssessment.assess(depleted, depleted.fleets().getFirst()).action());
        var empty = world(own, enemy, first, ship("last", enemy, "enemy", 0, 0, 0));
        var assessment = FleetThreatAssessment.assess(empty, empty.fleets().getFirst());
        assertEquals(FleetThreatAssessment.Action.HOLD, assessment.action()); assertEquals(0, assessment.hostileStrength());
    }

    @Test void finiteFuelAndBatteryEnergyBoundStrengthToTheActualNextSalvo() {
        for (boolean battery : List.of(false, true)) {
            var own = design("own", battery ? 0 : 100, 100, false); var enemy = design("enemy", 74, 100, false);
            var first = ship("first", own, "owner", battery ? 0 : .8 / 6000, battery ? .8 : 0, 0);
            var state = world(own, enemy, first, ship("last", enemy, "enemy", 10, 0, 0));
            double expected = 80 * (.8 - 14 * HOURS);
            assertEquals(expected, ShipCombatPower.fundedFirepower(first, own, HOURS, true), 1e-5);
            assertEquals(Math.sqrt(expected * 1000), FleetThreatAssessment.strength(state, List.of(state.fleets().getFirst())), 1e-5);
            assertEquals(ShipCombatPower.fundedFirepower(first, own, HOURS, true),
                    ShipCombatPower.round(first, own, HOURS, true).firepower(), 1e-9);
        }
    }

    @Test void repeatedAssessmentsLeaveAllStoresAndCurrentShieldProtectionUntouched() {
        var own = design("own", 114, 100, true); var enemy = design("enemy", 74, 100, false);
        var first = ship("first", own, "owner", 10, 100, 100);
        var state = world(own, enemy, first, ship("last", enemy, "enemy", 10, 0, 0));
        var before = state.fleets(); var assessment = FleetThreatAssessment.assess(state, before.getFirst());
        assertEquals(Math.sqrt(80 * 1100), assessment.friendlyStrength(), 1e-9);
        for (int repeat = 0; repeat < 5; repeat++) assertEquals(assessment, FleetThreatAssessment.assess(state, before.getFirst()));
        assertEquals(before, state.fleets()); assertSame(first, state.fleets().getFirst().ships().getFirst());
        assertEquals(100, first.currentShieldHealth()); assertEquals(10, first.generatorFuelMassKg());
        assertEquals(100, first.powerState().batteryChargeKwh());
        assertTrue(ShipCombatPower.round(first, own, HOURS, true).ship().currentShieldHealth() > 100);
    }

    @Test void mountDamageStanceAndMissingHardwareRemainPartOfFundedAssessment() {
        var own = design("own", 74, 100, false); var enemy = design("enemy", 74, 100, false);
        var first = ship("first", own, "owner", 10, 0, 0).withModuleDamage(new ShipModuleDamage(Map.of(0, 50.0)));
        var state = world(own, enemy, first, ship("last", enemy, "enemy", 10, 0, 0));
        var fleet = state.fleets().getFirst();
        assertEquals(Math.sqrt(40 * 1000), FleetThreatAssessment.strength(state, List.of(fleet)), 1e-9);
        assertEquals(Math.sqrt(40 * 1.25 * 1000), FleetThreatAssessment.strength(state,
                List.of(fleet.withStance(TacticalCombatProcessor.STANCE_AGGRESSIVE_BRAWL))), 1e-9);
        assertEquals(0, FleetThreatAssessment.strength(state, List.of(fleet.withStance(TacticalCombatProcessor.STANCE_EVASIVE_RETREAT))));
        assertEquals(0, FleetThreatAssessment.strength(state, List.of(fleet.withShips(List.of(first.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))))))));
        var unknown = new ShipInstance("unknown", "missing", "owner", 1000, 200, 100, Map.of());
        assertEquals(0, FleetThreatAssessment.strength(state, List.of(fleet.withShips(List.of(unknown)))));
    }

    @Test void contactLossStillPreventsAssessmentAndLegacyHardwareKeepsItsCompatibilityPath() {
        var own = design("own", 74, 100, false); var enemy = design("enemy", 74, 100, false);
        var first = ship("first", own, "owner", 10, 0, 0);
        var state = world(own, enemy, first, ship("last", enemy, "enemy", 10, 0, 0));
        var unavailable = first.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 1, 0));
        var hidden = state.withFleets(List.of(state.fleets().getFirst().withShips(List.of(unavailable)), state.fleets().getLast()));
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.assess(hidden, hidden.fleets().getFirst()).action());
        var legacy = new ShipDesign("legacy", "Legacy", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(CombatModuleCatalog.GUN),
                "steel", 0, 1000, 1000, 1000, 100, 10, 0, 1000, false, false);
        var oldShip = new ShipInstance("old", "legacy", "owner", 1000, 0, 100, Map.of());
        var old = state.withShipDesigns(List.of(legacy));
        assertEquals(Math.sqrt(ShipCombatStats.of(legacy, oldShip).firepower() * 1000),
                FleetThreatAssessment.strength(old, List.of(fleet("old", "owner", oldShip))), 1e-9);
    }
}
