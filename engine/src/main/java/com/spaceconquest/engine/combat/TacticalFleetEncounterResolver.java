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

/**
 * Strategic encounter adapter using the current tactical processor provisionally.
 * Replace this implementation when tactical combat is overhauled.
 */
public final class TacticalFleetEncounterResolver implements FleetEncounterResolver {
    private static final int MAX_SAVED_ENGAGEMENTS = 1_000;
    private final TacticalCombatProcessor combatProcessor;
    private final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();

    public TacticalFleetEncounterResolver(TacticalCombatProcessor combatProcessor) {
        this.combatProcessor = combatProcessor;
    }

    @Override
    public GameState resolveEncounters(GameState state) {
        if (state == null || state.fleets().size() < 2) return state;
        List<Fleet> fleets = new ArrayList<>(state.fleets());
        List<FleetEngagementRecord> history = new ArrayList<>(state.fleetEngagements());
        Set<String> engagedFleetIds = new HashSet<>();
        List<Fleet> ordered = fleets.stream().filter(this::canEngage)
                .sorted(Comparator.comparing(Fleet::id)).toList();

        for (int i = 0; i < ordered.size(); i++) {
            Fleet attacker = findFleet(fleets, ordered.get(i).id());
            if (attacker == null || engagedFleetIds.contains(attacker.id())) continue;
            for (int j = i + 1; j < ordered.size(); j++) {
                Fleet defender = findFleet(fleets, ordered.get(j).id());
                if (defender == null || engagedFleetIds.contains(defender.id())
                        || !sameSite(attacker, defender)
                        || !atWar(attacker.ownerEntityId(), defender.ownerEntityId(), state.diplomaticRelations())) {
                    continue;
                }
                TacticalCombatProcessor.CombatEngagementResult result = combatProcessor.resolveFleetEngagement(
                        attacker, defender, state.shipDesigns());
                replaceFleet(fleets, result.survivingAttackerFleet());
                replaceFleet(fleets, result.survivingDefenderFleet());
                engagedFleetIds.add(attacker.id());
                engagedFleetIds.add(defender.id());
                history.add(toRecord(state.turn(), attacker, defender, result));
                break;
            }
        }
        fleets.removeIf(fleet -> fleet.ships().isEmpty());
        if (history.size() > MAX_SAVED_ENGAGEMENTS) {
            history = new ArrayList<>(history.subList(history.size() - MAX_SAVED_ENGAGEMENTS, history.size()));
        }
        return state.toBuilder().fleets(fleets).fleetEngagements(history).build();
    }

    private boolean canEngage(Fleet fleet) {
        return fleet != null && !fleet.ships().isEmpty() && !fleet.isInWarp()
                && !fleet.hasInterstellarOrder() && !fleet.location().inTransit();
    }

    private boolean sameSite(Fleet first, Fleet second) {
        return first.currentSystemId().equals(second.currentSystemId())
                && first.location().current().equals(second.location().current());
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

    private FleetEngagementRecord toRecord(long turn, Fleet attacker, Fleet defender,
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
        return new FleetEngagementRecord(turn, attacker.currentSystemId(), site.kind().name(), site.entityId(),
                attacker.id(), attacker.ownerEntityId(), defender.id(), defender.ownerEntityId(),
                result.winnerOwnerEntityId(), destroyedShips, result.roundReports().size());
    }
}
