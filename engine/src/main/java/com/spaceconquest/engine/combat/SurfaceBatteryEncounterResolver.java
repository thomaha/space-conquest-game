package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Fixed surface targets use ordinary impacts, electronic damage and projectile flight without adding campaign fleets. */
public final class SurfaceBatteryEncounterResolver {
    private SurfaceBatteryEncounterResolver() {}
    public static GameState resolve(GameState state, TacticalCombatProcessor processor) {
        return resolve(state, processor, List.of());
    }
    private record Site(String owner, String body) {}
    public static GameState resolve(GameState state, TacticalCombatProcessor processor, List<Race> races) {
        var current = state; var engaged = new HashSet<String>();
        var groups = new LinkedHashMap<Site, List<AuxiliaryForces.OwnedBattery>>();
        state.auxiliaryForces().batteries().stream().sorted(Comparator.comparing(b -> b.battery().id()))
                .filter(b -> b.battery().isOperational() && b.battery().hullHealth() > 0)
                .forEach(b -> groups.computeIfAbsent(new Site(b.ownerEntityId(), b.battery().planetId()), ignored -> new ArrayList<>()).add(b));
        for (var entry : groups.entrySet()) {
            var site = entry.getKey(); var group = entry.getValue();
            String system = ConstructionMaterials.systemForBody(current, site.body());
            if (system == null) continue;
            var surface = target(group, system);
            for (var fleet : current.fleets().stream().sorted(Comparator.comparing(Fleet::id)).toList()) {
                if (fleet.isInWarp() || fleet.hasInterstellarOrder() || fleet.ships().isEmpty()
                        || engaged.contains(fleet.id()) || !system.equals(fleet.currentSystemId()) || !war(current, site.owner(), fleet.ownerEntityId())) continue;
                double meters = ShipWeaponRange.separation(current, fleet, surface);
                boolean batteryContact = SensorCoverage.detects(current, site.owner(), fleet);
                boolean fleetContact = SensorCoverage.detects(current, fleet.ownerEntityId(), surface);
                boolean batteryReach = Double.isFinite(meters) && group.stream().map(AuxiliaryForces.OwnedBattery::battery)
                        .anyMatch(b -> b.moduleDamage().condition(0) > 0 && !b.stores().fire(b.weapon(), 1, meters).hits().isEmpty());
                boolean fleetReach = ShipWeaponRange.reaches(current, fleet, surface) || current.auxiliaryForces().reaches(fleet, meters);
                if (!(batteryContact && batteryReach) && !(fleetContact && fleetReach)) continue;
                var context = new GameState[]{current}; var designs = new ArrayList<>(current.shipDesigns()); group.forEach(b -> designs.add(design(b)));
                boolean[] withdrew = {false};
                var result = processor.forState(current).resolveSurfaceEngagement(fleet, surface, designs,
                        current.auxiliaryForces().wingsFor(fleet), List.of(), group.stream().map(AuxiliaryForces.OwnedBattery::battery).toList(),
                        TacticalCombatProcessor.TARGET_SUBSYSTEM_ALL, (attacker, defender, batteries) -> {
                            var decision = withdraw(context, attacker, defender, batteries, designs, races); withdrew[0] |= decision.departed(); return decision;
                        },
                        (observer, enemy) -> liveContact(context[0], fleet.id(), observer, enemy),
                        (first, second) -> ShipWeaponRange.separation(context[0], first, second));
                current = context[0]; engaged.add(fleet.id());
                var survivors = result.survivingAttackerFleet();
                var fleets = current.fleets().stream().map(f -> f.id().equals(fleet.id()) ? survivors : f)
                        .filter(f -> f != null && !f.ships().isEmpty()).toList();
                var forces = current.auxiliaryForces().afterCombat(result, fleets);
                for (var battery : result.survivingPlanetaryBatteries()) forces = forces.withBattery(battery);
                var history = new ArrayList<>(current.fleetEngagements());
                var destroyed = fleet.ships().stream().filter(s -> survivors == null || survivors.ships().stream().noneMatch(v -> v.id().equals(s.id())))
                        .map(ShipInstance::id).toList();
                history.add(new FleetEngagementRecord(current.turn(), system, "SURFACE_BATTERY", site.body(), fleet.id(), fleet.ownerEntityId(),
                        surface.id(), site.owner(), result.winnerOwnerEntityId(), destroyed, result.roundReports().size(),
                        SurfaceCombatSummary.capture(group.stream().map(AuxiliaryForces.OwnedBattery::battery).toList(), fleet, result, withdrew[0]),
                        FleetEngagementRecord.targeting(result)));
                if (history.size() > 1000) history.removeFirst();
                current = current.toBuilder().fleets(fleets).auxiliaryForces(forces).fleetEngagements(history).build();
                break;
            }
        }
        return current;
    }
    private static TacticalCombatProcessor.Withdrawal withdraw(GameState[] context, Fleet attacker, Fleet defender,
            List<PlanetaryDefenseBattery> batteries, List<ShipDesign> designs, List<Race> races) {
        var real = context[0]; var forces = real.auxiliaryForces();
        for (var battery : batteries) forces = forces.withBattery(battery);
        var fleets = new ArrayList<>(real.fleets().stream().map(f -> f.id().equals(attacker.id()) ? attacker : f).toList()); fleets.add(defender);
        var preview = real.toBuilder().fleets(fleets).shipDesigns(designs).auxiliaryForces(forces).build();
        var escaped = FleetProtectionProcessor.withdrawFromCombat(preview, attacker, races);
        if (escaped == preview) return new TacticalCombatProcessor.Withdrawal(attacker, defender, false);
        context[0] = escaped.toBuilder().shipDesigns(real.shipDesigns())
                .fleets(escaped.fleets().stream().filter(f -> !f.id().equals(defender.id())).toList()).build();
        return new TacticalCombatProcessor.Withdrawal(FleetOrganization.find(context[0], attacker.id()), defender, true);
    }
    private static boolean war(GameState state, String a, String b) {
        return !a.equals(b) && DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(new DiplomacyProcessor().getDiplomaticTier(a, b, state.diplomaticRelations()));
    }
    private static boolean liveContact(GameState state, String fleetId, Fleet observer, Fleet enemy) {
        var survivingFleet = observer.id().equals(fleetId) ? observer : enemy;
        var current = state.withFleets(state.fleets().stream().map(f -> f.id().equals(fleetId) ? survivingFleet : f).toList());
        return SensorCoverage.detects(current, observer.ownerEntityId(), enemy);
    }
    private static ShipDesign design(AuxiliaryForces.OwnedBattery owned) {
        var battery = owned.battery(); var weapon = battery.weapon();
        return new ShipDesign(battery.combatEntityId(), "Surface battery", owned.ownerEntityId(), ShipRole.COMBAT_SHIP, "steel",
                List.of(weapon.id()), "steel", battery.hardware().value("armorThicknessCm"),
                battery.hardware().materialCostsKg().values().stream().mapToDouble(Double::doubleValue).sum() + weapon.dryMassKg(),
                0, 0, 0, battery.hardware().value("hullHealth") / 100, 0, 0, false, false,
                ShipManufacturingProfile.baseline(), ShipPowerProfile.capture(List.of(weapon)), ShipSensorProfile.capture(List.of()), Map.of(weapon.id(), weapon));
    }
    private static Fleet target(List<AuxiliaryForces.OwnedBattery> group, String system) {
        var first = group.getFirst();
        var shells = group.stream().map(owned -> {
            var battery = owned.battery();
            return new ShipInstance(battery.combatEntityId(), battery.combatEntityId(), owned.ownerEntityId(),
                    battery.hullHealth(), 0, 0, Map.of()).withPowerState(ShipPowerState.empty()).withModuleDamage(battery.moduleDamage());
        }).toList();
        return new Fleet("surface-defense-" + first.ownerEntityId() + "-" + first.battery().planetId(), "Surface defenses", first.ownerEntityId(), system,
                "", 0, 0, 0, false, "PASSIVE", shells, FleetLocation.at(FleetLocation.Site.surface(first.battery().planetId())))
                .withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
    }
}
