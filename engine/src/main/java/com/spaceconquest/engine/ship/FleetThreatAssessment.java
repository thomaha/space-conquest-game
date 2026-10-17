package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import java.util.List;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Conservative local assessment using current contacts and immediately available own forces. */
public final class FleetThreatAssessment {
    public static final double OVERWHELMING_RATIO = 2;
    public static final double THREAT_RADIUS_METERS = 100_000_000;
    public enum Action { NO_CONTACT, HOLD, ESCAPE }
    public record Assessment(Action action, double friendlyStrength, double hostileStrength,
                             List<String> friendlyFleetIds, List<String> hostileFleetIds) {
        public Assessment {
            friendlyFleetIds = List.copyOf(friendlyFleetIds);
            hostileFleetIds = List.copyOf(hostileFleetIds);
        }
    }
    private FleetThreatAssessment() {}

    public static boolean available(Fleet fleet) {
        return !fleet.ships().isEmpty() && !fleet.isInWarp() && !fleet.hasInterstellarOrder()
                && (!fleet.location().inTransit() || MovingFleetEscape.supported(fleet)) && fleet.flightMotion() == null;
    }

    public static List<Fleet> support(GameState state, Fleet protectedFleet) {
        return state.fleets().stream().filter(fleet -> available(fleet)
                && protectedFleet.ownerEntityId().equals(fleet.ownerEntityId())
                && protectedFleet.currentSystemId().equals(fleet.currentSystemId())
                && MovingFleetEscape.together(state, protectedFleet, fleet)).toList();
    }

    public static double distance(GameState state, Fleet fleet, FleetLocation location) {
        var first = SensorPosition.at(state, fleet.currentSystemId(), fleet.location());
        var last = SensorPosition.at(state, fleet.currentSystemId(), location);
        return first == null || last == null ? Double.POSITIVE_INFINITY : norm(subtract(first, last));
    }

    public static Assessment assess(GameState state, Fleet protectedFleet) {
        var own = support(state, protectedFleet);
        var enemies = state.fleets().stream().filter(fleet -> !fleet.ships().isEmpty()
                && protectedFleet.currentSystemId().equals(fleet.currentSystemId())
                && DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(new DiplomacyProcessor().getDiplomaticTier(
                    protectedFleet.ownerEntityId(), fleet.ownerEntityId(), state.diplomaticRelations()))
                && SensorCoverage.detects(state, protectedFleet.ownerEntityId(), fleet)
                && distance(state, fleet, protectedFleet.location()) <= THREAT_RADIUS_METERS).toList();
        double friendly = engagementStrength(state, own, enemies);
        double hostile = engagementStrength(state, enemies, own.isEmpty() ? List.of(protectedFleet) : own);
        var action = enemies.isEmpty() ? Action.NO_CONTACT
                : hostile > friendly * OVERWHELMING_RATIO ? Action.ESCAPE : Action.HOLD;
        return new Assessment(action, friendly, hostile, own.stream().map(Fleet::id).toList(),
                enemies.stream().map(Fleet::id).toList());
    }

    /** Safety policy only. Target acquisition and escape trajectory planning continue to use live assessment. */
    public static Assessment caution(GameState state, Fleet fleet) {
        var live = assess(state, fleet);
        var remembered = com.spaceconquest.engine.galaxy.SensorContactMemory.threats(state, fleet, THREAT_RADIUS_METERS);
        if (remembered.isEmpty()) return live;
        // Unknown current range must not turn last-known potential strength into confirmed firing reach.
        double friendly = strength(state, support(state, fleet));
        double hostile = strength(state, state.fleets().stream().filter(
                member -> live.hostileFleetIds().contains(member.id())).toList()) + remembered.stream().mapToDouble(
                com.spaceconquest.engine.galaxy.SensorContact::observedStrength).sum();
        var action = live.action() == Action.ESCAPE || hostile > friendly * OVERWHELMING_RATIO ? Action.ESCAPE : Action.HOLD;
        return new Assessment(action, friendly, hostile, live.friendlyFleetIds(), live.hostileFleetIds());
    }

    /** Quotes the same next-round firing budget as combat, retaining current defense and never spending stores. */
    public static double strength(GameState state, List<Fleet> fleets) {
        return strength(state, fleets, null);
    }

    /** Reachable funded strength against actual opponents, counting each fleet's salvo only once. */
    public static double engagementStrength(GameState state, List<Fleet> fleets, List<Fleet> opponents) {
        return strength(state, fleets, opponents);
    }

    private static double strength(GameState state, List<Fleet> fleets, List<Fleet> opponents) {
        double firepower = 0, endurance = 0;
        for (var fleet : fleets) {
            double meters = opponents == null ? 0 : opponents.stream()
                    .mapToDouble(target -> ShipWeaponRange.separation(state, fleet, target)).min().orElse(Double.POSITIVE_INFINITY);
            for (var ship : fleet.ships()) {
                if (ShipRefitPlan.reserved(state, ship.id()) || ship.currentHullHealth() <= 0) continue;
                var design = state.shipDesigns().stream().filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
                var stats = com.spaceconquest.engine.combat.ShipCombatStats.of(design, ship);
                double stance = com.spaceconquest.engine.combat.ShipCombatStats.stanceModifier(fleet.fleetStance());
                double funded = ShipCombatPower.fundedFirepower(ship, design,
                        com.spaceconquest.engine.combat.TacticalCombatProcessor.SECONDS_PER_ROUND / 3600, stance > 0, meters);
                var battery = state.auxiliaryForces().batteries().stream().filter(b -> b.ownerEntityId().equals(fleet.ownerEntityId())
                        && b.battery().combatEntityId().equals(ship.id()) && fleet.location().isAt(FleetLocation.Site.surface(b.battery().planetId())))
                        .map(com.spaceconquest.engine.combat.AuxiliaryForces.OwnedBattery::battery).findFirst().orElse(null);
                if (battery != null) funded = battery.isOperational() && Double.isFinite(meters) && stance > 0
                        ? battery.stores().fire(battery.weapon(), 1, meters).hits().stream().mapToDouble(com.spaceconquest.engine.ship.WeaponHit::damage).sum()
                                * ship.moduleDamage().condition(0) : 0;
                firepower += funded * stance;
                endurance += stats.endurance();
            }
        }
        return Math.sqrt(firepower * endurance);
    }
}
