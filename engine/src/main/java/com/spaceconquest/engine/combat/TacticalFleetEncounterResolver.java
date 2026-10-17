package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import com.spaceconquest.engine.Race;

/**
 * Strategic encounter adapter using the current tactical processor provisionally.
 * Replace this implementation when tactical combat is overhauled.
 */
public final class TacticalFleetEncounterResolver implements FleetEncounterResolver {
    private static final int MAX_SAVED_ENGAGEMENTS = 1_000;
    private final TacticalCombatProcessor combatProcessor;
    private final Supplier<List<Race>> races;
    private final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();

    public TacticalFleetEncounterResolver(TacticalCombatProcessor combatProcessor) {
        this(combatProcessor, List::of);
    }

    public TacticalFleetEncounterResolver(TacticalCombatProcessor combatProcessor, Supplier<List<Race>> races) {
        this.combatProcessor = combatProcessor;
        this.races = races;
    }

    @Override
    public GameState resolveEncounters(GameState state) {
        if (state == null) return null;
        List<Fleet> fleets = new ArrayList<>(state.fleets().stream().map(fleet -> fleet.guardOrder() != null
                && !fleet.guardOrder().validTarget(state, fleet) ? fleet.withGuardOrder(null) : fleet).toList());
        fleets.replaceAll(fleet -> fleet.interceptOrder() != null
                && fleet.interceptOrder().protection() == null
                && !com.spaceconquest.engine.ship.FleetInterceptProcessor.contact(state, fleet,
                com.spaceconquest.engine.ship.FleetOrganization.find(state, fleet.interceptOrder().targetFleetId()))
                ? fleet.withInterceptOrder(null) : fleet);
        List<FleetEngagementRecord> history = new ArrayList<>(state.fleetEngagements());
        GameState[] context = {state};
        Set<String> engagedFleetIds = new HashSet<>();
        List<Fleet> ordered = fleets.stream().filter(this::canEngage)
                .sorted(Comparator.comparingInt((Fleet fleet) -> fleet.interceptOrder() != null ? 0 : fleet.guardOrder() != null ? 1 : 2)
                        .thenComparing(Fleet::id)).toList();

        for (int i = 0; i < ordered.size(); i++) {
            Fleet attacker = findFleet(fleets, ordered.get(i).id());
            if (attacker == null || engagedFleetIds.contains(attacker.id())) continue;
            for (int j = attacker.interceptOrder() == null ? i + 1 : 0; j < ordered.size(); j++) {
                Fleet defender = findFleet(fleets, ordered.get(j).id());
                if (defender == null || attacker.id().equals(defender.id()) || engagedFleetIds.contains(defender.id())
                        || attacker.interceptOrder() != null && !attacker.interceptOrder().targetFleetId().equals(defender.id())
                        || !atWar(attacker.ownerEntityId(), defender.ownerEntityId(), state.diplomaticRelations())
                        || !(contact(context[0], fleets, attacker, defender) && reaches(context[0], attacker, defender))
                        && !(contact(context[0], fleets, defender, attacker) && reaches(context[0], defender, attacker))) {
                    continue;
                }
                TacticalCombatProcessor.CombatEngagementResult result = combatProcessor.forState(context[0]).resolveFleetEngagementWithCarrierWings(
                        attacker, defender, state.shipDesigns(), context[0].auxiliaryForces().wingsFor(attacker),
                        context[0].auxiliaryForces().wingsFor(defender), null, TacticalCombatProcessor.TARGET_SUBSYSTEM_ALL,
                        (first, second) -> withdraw(context, fleets, first, second),
                        (first, second) -> contact(context[0], fleets, first, second),
                        (first, second) -> com.spaceconquest.engine.ship.ShipWeaponRange.separation(context[0], first, second));
                replaceFleet(fleets, result.survivingAttackerFleet());
                replaceFleet(fleets, result.survivingDefenderFleet());
                context[0] = context[0].toBuilder().auxiliaryForces(context[0].auxiliaryForces().afterCombat(result, fleets)).build();
                engagedFleetIds.add(attacker.id());
                engagedFleetIds.add(defender.id());
                history.add(toRecord(state, attacker, defender, result));
                break;
            }
        }
        fleets.removeIf(fleet -> fleet.ships().isEmpty());
        GameState afterCombat = state.withFleets(fleets);
        fleets.replaceAll(fleet -> fleet.guardOrder() != null && !fleet.guardOrder().validTarget(afterCombat, fleet)
                ? fleet.withGuardOrder(null) : fleet);
        if (history.size() > MAX_SAVED_ENGAGEMENTS) {
            history = new ArrayList<>(history.subList(history.size() - MAX_SAVED_ENGAGEMENTS, history.size()));
        }
        return SurfaceBatteryEncounterResolver.resolve(com.spaceconquest.engine.ship.FleetInterceptProcessor.finishContacts(
                context[0].toBuilder().fleets(fleets).fleetEngagements(history).build()), combatProcessor, races.get());
    }

    private TacticalCombatProcessor.Withdrawal withdraw(GameState[] context, List<Fleet> fleets, Fleet first, Fleet second) {
        replaceFleet(fleets, first);
        replaceFleet(fleets, second);
        var current = context[0].withFleets(List.copyOf(fleets));
        var escaped = com.spaceconquest.engine.ship.FleetProtectionProcessor.withdrawFromCombat(current, first, races.get());
        if (escaped == current)
            escaped = com.spaceconquest.engine.ship.FleetProtectionProcessor.withdrawFromCombat(current, second, races.get());
        if (escaped == current) return new TacticalCombatProcessor.Withdrawal(first, second, false);
        fleets.clear();
        fleets.addAll(escaped.fleets());
        context[0] = escaped;
        return new TacticalCombatProcessor.Withdrawal(findFleet(fleets, first.id()), findFleet(fleets, second.id()), true);
    }

    private boolean contact(GameState state, List<Fleet> fleets, Fleet observer, Fleet target) {
        var current = state.withFleets(fleets.stream().map(fleet -> fleet.id().equals(observer.id()) ? observer
                : fleet.id().equals(target.id()) ? target : fleet).toList());
        return com.spaceconquest.engine.galaxy.SensorCoverage.detects(current, observer.ownerEntityId(), target);
    }

    private boolean reaches(GameState state, Fleet fleet, Fleet target) {
        return com.spaceconquest.engine.ship.ShipWeaponRange.reaches(state, fleet, target)
                || state.auxiliaryForces().reaches(fleet, com.spaceconquest.engine.ship.ShipWeaponRange.separation(state, fleet, target));
    }

    private boolean canEngage(Fleet fleet) {
        return fleet != null && !fleet.ships().isEmpty() && !fleet.isInWarp()
                && !fleet.hasInterstellarOrder();
    }

    private boolean atWar(String firstEmpireId, String secondEmpireId, List<DiplomaticRelation> relations) {
        if (firstEmpireId == null || secondEmpireId == null || firstEmpireId.equals(secondEmpireId)) return false;
        return DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(
                diplomacyProcessor.getDiplomaticTier(firstEmpireId, secondEmpireId, relations));
    }

    private Fleet findFleet(List<Fleet> fleets, String id) {
        return fleets.stream().filter(fleet -> fleet.id().equals(id)).findFirst().orElse(null);
    }

    private void replaceFleet(List<Fleet> fleets, Fleet updated) {
        if (updated == null) return;
        for (int i = 0; i < fleets.size(); i++) {
            if (fleets.get(i).id().equals(updated.id())) {
                fleets.set(i, updated);
                return;
            }
        }
    }

    private FleetEngagementRecord toRecord(GameState state, Fleet attacker, Fleet defender,
            TacticalCombatProcessor.CombatEngagementResult result) {
        Set<String> survivorIds = new HashSet<>();
        if (result.survivingAttackerFleet() != null) {
            result.survivingAttackerFleet().ships().stream().map(ShipInstance::id).forEach(survivorIds::add);
        }
        if (result.survivingDefenderFleet() != null) {
            result.survivingDefenderFleet().ships().stream().map(ShipInstance::id).forEach(survivorIds::add);
        }
        List<String> destroyedShips = new ArrayList<>();
        attacker.ships().stream().map(ShipInstance::id).filter(id -> !survivorIds.contains(id)).forEach(destroyedShips::add);
        defender.ships().stream().map(ShipInstance::id).filter(id -> !survivorIds.contains(id)).forEach(destroyedShips::add);
        FleetLocation.Site site = attacker.location().current();
        boolean ranged = attacker.location().inTransit() || defender.location().inTransit() || !site.equals(defender.location().current());
        boolean rendezvous = com.spaceconquest.engine.ship.MovingFleetRendezvous.matches(state, attacker, defender)
                || com.spaceconquest.engine.ship.MovingFleetRendezvous.matches(state, defender, attacker);
        return new FleetEngagementRecord(state.turn(), attacker.currentSystemId(), ranged ? rendezvous ? "RENDEZVOUS" : "WEAPON_RANGE" : site.kind().name(),
                ranged ? defender.id() : site.entityId(),
                attacker.id(), attacker.ownerEntityId(), defender.id(), defender.ownerEntityId(),
                result.winnerOwnerEntityId(), destroyedShips, result.roundReports().size(), null, FleetEngagementRecord.targeting(result));
    }
}
