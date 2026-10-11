package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipWeaponRangeTest {
    private static final double HOURS = TacticalCombatProcessor.SECONDS_PER_ROUND / 3600;
    private ShipDesign design(String owner, String... weapons) {
        var modules = new ArrayList<>(List.of(weapons)); modules.add(SensorModuleCatalog.BASIC);
        double hotel = 2 + modules.stream().map(ShipComponentCatalog::module).mapToDouble(ShipModule::powerDrawKw).sum();
        var power = new ShipPowerProfile(0, 0, 10000, 0, 0, 0, 0, 10, hotel, 0, 0, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6_000_000)));
        return new ShipDesign(owner, owner, owner, ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                1000, 1000, 1000, 100, 100, 0, 1000, false, false, ShipManufacturingProfile.baseline(), power);
    }
    private ShipInstance ship(String owner) {
        return new ShipInstance(owner + "-ship", owner, owner, 10000, 0, 100, Map.of())
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
    }
    private Fleet at(String owner, double x) {
        var zero = new LocalSiteGeometry.Point(0, 0, 0);
        var trajectory = new VectorFlight.Trajectory(new LocalSiteGeometry.Point(x, 0, 0), zero,
                new LocalSiteGeometry.Point(1, 0, 0), new LocalSiteGeometry.Point(-1, 0, 0), 1, 10);
        var vector = trajectory.at(0); var end = trajectory.at(trajectory.totalSeconds()).position();
        var geometry = new LocalSiteGeometry.Leg(vector.position(), end, VectorFlight.norm(VectorFlight.subtract(end, vector.position())));
        var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, false, null, vector);
        var fleet = new Fleet(owner, owner, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship(owner)));
        return fleet.withLocation(fleet.location().depart(FleetLocation.Site.deepSpace("goal-" + owner), 1).withFlight(flight))
                .withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
    }
    private Empire empire(String owner) {
        return new Empire(owner, owner, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }
    private GameState world(double meters) {
        return GameState.builder().empires(List.of(empire("own"), empire("enemy")))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of())))
                .shipDesigns(List.of(design("own", CombatModuleCatalog.GUN, CombatModuleCatalog.LASER), design("enemy", CombatModuleCatalog.GUN)))
                .fleets(List.of(at("own", 0), at("enemy", meters)))
                .diplomaticRelations(List.of(new DiplomaticRelation("own", "enemy", "TOTAL_WAR", 0))).build();
    }

    @Test void eachInclusiveBoundaryFundsOnlyReachableMountsAndOutsidePaysNoSalvo() {
        for (var id : List.of(CombatModuleCatalog.GUN, CombatModuleCatalog.LASER, CombatModuleCatalog.MASS_DRIVER)) {
            var design = design("own", id); var ship = ship("own"); var module = CombatModuleCatalog.module(id);
            double range = module.operationalStats().get("weaponRangeMeters");
            var inside = ShipCombatPower.round(ship, design, HOURS, true, false, range);
            assertEquals(module.operationalStats().get("weaponDamage"), inside.firepower(), 1e-9);
            assertEquals(module.operationalStats().get("weaponSalvoKwh"), inside.paidWeaponKwh(), 1e-9);
            var outside = ShipCombatPower.round(ship, design, HOURS, true, false, range + 1);
            assertEquals(0, outside.firepower()); assertEquals(0, outside.requestedWeaponKwh());
            assertEquals(1 - design.powerProfile().hotelKw() * HOURS / 6_000_000, outside.ship().generatorFuelMassKg(), 1e-12);
        }
    }
    @Test void mixedAndDamagedMountsUseTheirOwnRangeAndExactEligibleSalvoCost() {
        var design = design("own", CombatModuleCatalog.GUN, CombatModuleCatalog.LASER, CombatModuleCatalog.MASS_DRIVER);
        var source = ship("own"); var paid = ShipCombatPower.round(source, design, HOURS, true, false, 150000);
        assertEquals(380, paid.firepower()); assertEquals(20, paid.requestedWeaponKwh()); assertEquals(20, paid.paidWeaponKwh());
        assertEquals(1 - (design.powerProfile().hotelKw() * HOURS + 20) / 6_000_000, paid.ship().generatorFuelMassKg(), 1e-12);
        var damaged = source.withModuleDamage(new ShipModuleDamage(Map.of(1, 100.0, 2, 50.0)));
        var partial = ShipCombatPower.round(damaged, design, HOURS, true, false, 150000);
        assertEquals(110, partial.firepower()); assertEquals(6, partial.requestedWeaponKwh());
        assertEquals(0, ShipCombatPower.round(damaged, design, HOURS, true, false, 500001).firepower());
        assertEquals(1, source.generatorFuelMassKg());
    }
    @Test void actualSeparationPermitsLongRangeFireBeforeRendezvousButDetectionAloneDoesNotStartBattle() {
        var far = world(1000001); var own = far.fleets().getFirst(); var enemy = far.fleets().getLast();
        assertTrue(SensorCoverage.detects(far, "own", enemy)); assertEquals(1000001, ShipWeaponRange.separation(far, own, enemy));
        var resolver = new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42)));
        assertTrue(resolver.resolveEncounters(far).fleetEngagements().isEmpty());
        var near = world(750000); var result = resolver.resolveEncounters(near);
        assertEquals(1, result.fleetEngagements().size());
        assertEquals("WEAPON_RANGE", result.fleetEngagements().getFirst().siteKind());
        assertEquals(10000, FleetOrganization.find(result, "own").ships().getFirst().currentHullHealth());
        assertTrue(FleetOrganization.find(result, "enemy").ships().getFirst().currentHullHealth() < 10000);
        assertTrue(FleetOrganization.find(result, "own").location().inTransit());
        assertEquals(near.fleets().getFirst().location(), FleetOrganization.find(result, "own").location());
    }
}
