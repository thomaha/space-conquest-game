package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Concrete mission shortages, with existing ships, queued builds and commissioning travel counted once. */
public final class EmpireFleetNeeds {
    private EmpireFleetNeeds() {}
    public record Need(String systemId, ShipDeploymentIntent.Kind kind, String targetId, int missingShips, String reason) {
        public ShipDeploymentIntent intent(String owner, long turn) {
            return new ShipDeploymentIntent(owner, kind, targetId, "", turn, reason);
        }
    }
    public static List<Need> assess(GameState state, Empire empire) {
        var policy = FleetNeedsPolicy.current(); var result = new ArrayList<Need>();
        boolean war = state.diplomaticRelations().stream().anyMatch(relation -> "TOTAL_WAR".equals(relation.tier())
                && (empire.id().equals(relation.empireAId()) || empire.id().equals(relation.empireBId())));
        for (String system : empire.controlledSystemIds().stream().sorted().toList()) {
            var stations = state.orbitalStations().stream().filter(station -> system.equals(station.systemId())
                    && empire.id().equals(station.ownerEntityId()) && station.isOperational()).sorted(Comparator.comparing(com.spaceconquest.engine.macrostructure.OrbitalStation::id)).toList();
            var friendly = state.fleets().stream().filter(fleet -> empire.id().equals(fleet.ownerEntityId()) && system.equals(fleet.currentSystemId())
                    && !fleet.hasInterstellarOrder() && !fleet.isInWarp() && fleet.ships().stream().noneMatch(ship -> state.shipyardLedger().deployments().containsKey(ship.id())))
                    .sorted(Comparator.comparing(Fleet::id)).toList();
            int coverage = friendly.stream().filter(fleet -> fleet.escortOrder() == null).mapToInt(fleet -> combatShips(state, fleet)).sum();
            coverage += commitments(state, empire, system, null, false);
            int threats = state.fleets().stream().filter(fleet -> system.equals(fleet.currentSystemId()) && hostile(state, empire, fleet)
                    && (SensorCoverage.detects(state, empire.id(), fleet) || state.fogOfWarStates().stream().anyMatch(fog -> empire.id().equals(fog.empireId()) && fog.isFleetDetected(fleet.id()))))
                    .mapToInt(fleet -> (int) fleet.ships().stream().filter(ship -> ship.currentHullHealth() > 0).count()).sum();
            int desired = Math.min(policy.maximumShipsPerSystem(), (stations.isEmpty() ? 0 : policy.defenseShipsPerSystem())
                    + (war ? policy.wartimeReserveShips() : 0) + threats * policy.shipsPerDetectedEnemy());
            var reinforcement = friendly.stream().filter(AIShipDeploymentIntent::idle).filter(fleet -> combatShips(state, fleet) > 0).findFirst().orElse(null);
            if (desired > coverage && (!stations.isEmpty() || reinforcement != null)) {
                String target = reinforcement != null ? reinforcement.id() : stations.getFirst().id();
                var kind = reinforcement != null ? ShipDeploymentIntent.Kind.REINFORCEMENT : ShipDeploymentIntent.Kind.DEFENSE;
                result.add(new Need(system, kind, target, desired - coverage, "Fleet shortage in " + system + ": " + (desired - coverage) + " defense or reinforcement ships"));
            }
            for (var convoy : friendly) if (cargo(state, convoy) && (AIShipDeploymentIntent.idle(convoy) || convoy.escortOrder() != null && FleetReturnTravel.available(convoy))) {
                int escorts = convoy.escortOrder() == null ? 0 : combatShips(state, convoy.withShips(convoy.ships().stream()
                        .filter(ship -> convoy.escortOrder().shipIds().contains(ship.id())).toList()));
                escorts += commitments(state, empire, system, convoy.id(), true);
                if (escorts < policy.escortsPerConvoy()) result.add(new Need(system, ShipDeploymentIntent.Kind.ESCORT, convoy.id(),
                        policy.escortsPerConvoy() - escorts, "Uncovered convoy " + convoy.id() + " in " + system));
            }
        }
        return result.stream().sorted(Comparator.comparingInt(Need::missingShips).reversed().thenComparing(Need::systemId).thenComparing(Need::targetId)).toList();
    }
    private static int commitments(GameState state, Empire empire, String system, String target, boolean escort) {
        var intents = new ArrayList<ShipDeploymentIntent>();
        state.shipConstructionOrders().stream().filter(order -> empire.id().equals(order.ownerEntityId()) && order.deployment() != null)
                .forEach(order -> intents.add(order.deployment()));
        state.shipyardLedger().deployments().forEach((id, intent) -> {
            if (state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).anyMatch(ship -> id.equals(ship.id()) && ship.currentHullHealth() > 0)) intents.add(intent);
        });
        int count = (int) intents.stream().filter(intent -> empire.id().equals(intent.ownerId()) && system.equals(StrategicDeploymentPlanner.system(state, intent))
                && (escort ? intent.kind() == ShipDeploymentIntent.Kind.ESCORT && target.equals(intent.targetId())
                : intent.kind() == ShipDeploymentIntent.Kind.DEFENSE || intent.kind() == ShipDeploymentIntent.Kind.REINFORCEMENT)).count();
        // Ordinary already ordered crossings also reserve capacity at their destination.
        if (!escort) count += state.fleets().stream().filter(fleet -> empire.id().equals(fleet.ownerEntityId()) && system.equals(fleet.targetSystemId())
                && fleet.hasInterstellarOrder() && fleet.ships().stream().noneMatch(ship -> state.shipyardLedger().deployments().containsKey(ship.id())))
                .mapToInt(fleet -> combatShips(state, fleet)).sum();
        return count;
    }
    private static boolean hostile(GameState state, Empire empire, Fleet fleet) {
        return state.diplomaticRelations().stream().anyMatch(relation -> "TOTAL_WAR".equals(relation.tier())
                && (empire.id().equals(relation.empireAId()) && fleet.ownerEntityId().equals(relation.empireBId())
                || empire.id().equals(relation.empireBId()) && fleet.ownerEntityId().equals(relation.empireAId())));
    }
    public static int combatShips(GameState state, Fleet fleet) {
        return (int) fleet.ships().stream().filter(ship -> ship.currentHullHealth() > 0).filter(ship -> state.shipDesigns().stream().anyMatch(design ->
                ship.designId().equals(design.id()) && EmpireFleetPlanner.combat(design.role()) && CombatModuleCatalog.weaponDamage(design) > 0)).count();
    }
    private static boolean cargo(GameState state, Fleet fleet) {
        return fleet.ships().stream().anyMatch(ship -> state.shipDesigns().stream().anyMatch(design -> ship.designId().equals(design.id()) && ShipRole.CARGO_TRANSPORT.equals(design.role())));
    }
    public static boolean urgentApplication(GameState state, Empire empire, TechnicalApplication app) {
        boolean installed = state.shipDesigns().stream().filter(design -> empire.id().equals(design.ownerEntityId()) && EmpireFleetPlanner.combat(design.role()))
                .flatMap(design -> design.equippedModuleIds().stream()).anyMatch(module -> app.id().equals(ShipApplicationProduction.applicationId(module)));
        boolean structural = state.shipDesigns().stream().anyMatch(design -> empire.id().equals(design.ownerEntityId()) && EmpireFleetPlanner.combat(design.role())
                && design.hullFrame() != null && app.id().equals(design.hullFrame().applicationId()));
        return installed || structural || app.combatModule() != null && app.combatModule().operationalStats().getOrDefault("weaponDamage", 0.0) > 0;
    }
}
