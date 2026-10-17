package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Repeatable bounded campaign probes; all weapon costs and ranges come from captured applications. */
class SurfaceMixedWeaponScenarioTest {
    private GameState world(double altitudeKm, String... defense) {
        var state = new SurfaceBatteryCampaignTest().world(defense); var batteries = new ArrayList<AuxiliaryForces.OwnedBattery>();
        for (String id : List.of(CombatModuleCatalog.LASER, CombatModuleCatalog.PARTICLE_BEAM, CombatModuleCatalog.ION, CombatModuleCatalog.MASS_DRIVER)) {
            var weapon = CombatModuleCatalog.module(id); var salvo = WeaponSalvo.of(weapon);
            var ammo = weapon.ammunitionId().isBlank() ? Map.<String, Double>of()
                    : Map.of(weapon.ammunitionId(), 2 * salvo.shots() * weapon.operationalStats().get("ammunitionMassKg"));
            var battery = new PlanetaryDefenseBattery(id, "earth", CombatModuleCatalog.application(id).id(), 1, true)
                    .withStores(new AuxiliaryWeaponStores(ammo, 2 * (weapon.powerDrawKw() / 60 + salvo.energyKwh())));
            batteries.add(new AuxiliaryForces.OwnedBattery("own", battery));
        }
        var target = state.fleets().getLast().withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", altitudeKm)));
        return state.toBuilder().fleets(List.of(state.fleets().getFirst(), target)).auxiliaryForces(new AuxiliaryForces(List.of(), batteries)).build();
    }
    private GameState fight(GameState state) { return SurfaceBatteryEncounterResolver.resolve(state, new TacticalCombatProcessor(new Random(42))); }
    @Test void mixedSurfaceSalvosProduceRepeatableReceiptsWithAndWithoutPointDefense() throws Exception {
        var report = new StringBuilder("# Mixed surface weapon scenarios\n\nSeed 42; two funded salvos per battery; bounded campaign encounters. Application values remain unchanged.\n\n");
        double close = CombatModuleCatalog.module(CombatModuleCatalog.LASER).operationalStats().get("weaponRangeMeters") / 2000;
        for (String defense : List.of("", CombatModuleCatalog.LASER_POINT_DEFENSE, CombatModuleCatalog.ION_POINT_DEFENSE)) {
            var state = defense.isBlank() ? world(close) : world(close, defense); var result = fight(state);
            assertEquals(result, fight(state)); assertEquals(1, result.fleetEngagements().size());
            var history = result.fleetEngagements().getFirst(); assertTrue(history.rounds() <= 10);
            assertEquals(4, history.surfaceCombat().batteries().size());
            assertTrue(history.surfaceCombat().batteries().stream().allMatch(b -> b.paidShots() > 0 && b.energySpentKwh() > 0));
            assertTrue(history.surfaceCombat().fleetHullLost() > 0, "Instant beams must still damage targets with projectile defense");
            assertTrue(history.surfaceCombat().fleetComponentDamageAdded() > 0 || result.fleets().size() == 1);
            assertTrue(state.auxiliaryForces().batteries().stream().allMatch(b -> b.battery().stores().energyKwh() > 0));
            report.append("## ").append(defense.isBlank() ? "No point defense" : CombatModuleCatalog.application(defense).name()).append("\n\n")
                    .append(String.format(Locale.ROOT, "%d rounds; net fleet hull loss %.2f; shield loss %.2f; withdrew: %s.\n\n",
                            history.rounds(), history.surfaceCombat().fleetHullLost(), history.surfaceCombat().fleetShieldLost(), history.surfaceCombat().fleetWithdrew()))
                    .append("| Battery application | Paid shots | Energy spent (kWh) | Ammunition spent (kg) | Hull lost | Component damage added |\n| --- | --- | --- | --- | --- | --- |\n");
            for (var receipt : history.surfaceCombat().batteries()) report.append(String.format(Locale.ROOT,
                    "| %s | %d | %.2f | %s | %.2f | %.2f |\n", receipt.weaponApplicationId(), receipt.paidShots(), receipt.energySpentKwh(),
                    receipt.ammunitionSpentKg(), receipt.hullLost(), receipt.componentDamageAdded()));
            report.append('\n');
        }
        var directory = Path.of("target"); Files.createDirectories(directory); Files.writeString(directory.resolve("mixed-surface-weapon-scenarios.md"), report);
    }
    @Test void longerSurfaceRangeHoldsLasersWhileKineticsBypassStandingShields() {
        double range = CombatModuleCatalog.module(CombatModuleCatalog.ION).operationalStats().get("weaponRangeMeters") / 2000;
        var state = world(range, Collections.nCopies(8, CombatModuleCatalog.SHIELD).toArray(String[]::new));
        var fleet = state.fleets().getLast(); var ship = fleet.ships().getFirst();
        var charged = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                CombatModuleCatalog.shieldCapacity(state.shipDesigns().getLast()), ship.currentFuelKg(), ship.storedCargoKg()).withPowerState(ship.powerState());
        state = state.withFleets(List.of(state.fleets().getFirst(), fleet.withShips(List.of(charged))));
        var result = fight(state); var summary = result.fleetEngagements().getFirst().surfaceCombat();
        assertEquals(0, summary.batteries().stream().filter(b -> b.id().equals(CombatModuleCatalog.LASER)).findFirst().orElseThrow().paidShots());
        assertTrue(summary.fleetHullLost() > 0, summary.toString());
        assertTrue(result.fleets().getLast().ships().getFirst().currentShieldHealth() > 0, summary.toString());
        assertTrue(summary.batteries().stream().filter(b -> !b.id().equals(CombatModuleCatalog.LASER)).allMatch(b -> b.paidShots() == 2));
    }
}
