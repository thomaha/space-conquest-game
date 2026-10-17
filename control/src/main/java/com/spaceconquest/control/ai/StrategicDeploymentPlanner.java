package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import java.util.*;

/** Scores owned deployment sites using known threats, friendly capacity and already committed reinforcements. */
public final class StrategicDeploymentPlanner {
    private StrategicDeploymentPlanner() {}
    public record Target(ShipDeploymentIntent.Kind kind, String id, String system, FleetLocation.Site site, double score) {}
    public static ShipDeploymentIntent choose(GameState state, ShipDesign design, String origin, String fallback) {
        var home = EconomicOwnership.empireFor(state, design.ownerEntityId());
        if (home == null || !home.id().equals(design.ownerEntityId())) return null;
        var policy = DeploymentPolicy.current(); var candidates = new ArrayList<Target>();
        for (var station : state.orbitalStations()) if (home.id().equals(station.ownerEntityId()) && station.isOperational() && home.controlledSystemIds().contains(station.systemId()))
            candidates.add(new Target(ShipDeploymentIntent.Kind.DEFENSE, station.id(), station.systemId(), FleetLocation.Site.docked(station.id()), policy.defenseWeight()));
        for (var fleet : state.fleets()) {
            if (!home.id().equals(fleet.ownerEntityId()) || !home.controlledSystemIds().contains(fleet.currentSystemId()) || !AIShipDeploymentIntent.idle(fleet)
                    || fleet.ships().stream().anyMatch(ship -> state.shipyardLedger().deployments().containsKey(ship.id()))) continue;
            if (ShipRole.ESCORT.equals(design.role()) && role(state, fleet, false))
                candidates.add(new Target(ShipDeploymentIntent.Kind.ESCORT, fleet.id(), fleet.currentSystemId(), fleet.location().current(), policy.escortWeight()));
            else if (role(state, fleet, true)) candidates.add(new Target(ShipDeploymentIntent.Kind.REINFORCEMENT, fleet.id(), fleet.currentSystemId(), fleet.location().current(), policy.reinforcementWeight()));
        }
        if (!fallback.isBlank() && home.controlledSystemIds().contains(origin) && candidates.stream().noneMatch(item -> item.system().equals(origin))) candidates.add(new Target(ShipDeploymentIntent.Kind.DEFENSE, fallback, origin,
                state.orbitalStations().stream().anyMatch(station -> station.id().equals(fallback)) ? FleetLocation.Site.docked(fallback) : FleetLocation.Site.surface(fallback), policy.defenseWeight()));
        if (fallback.isBlank() && home.controlledSystemIds().contains(origin) && candidates.stream().noneMatch(item -> item.system().equals(origin)))
            state.solarSystems().stream().filter(system -> origin.equals(system.id())).flatMap(system -> system.planets().stream())
                    .findFirst()
                    .ifPresent(planet -> candidates.add(new Target(ShipDeploymentIntent.Kind.DEFENSE, planet.id(), origin, FleetLocation.Site.surface(planet.id()), policy.defenseWeight())));
        var systemScores = new HashMap<String, Double>();
        var ranked = candidates.stream().map(item -> new Target(item.kind(), item.id(), item.system(), item.site(), item.score()
                        + systemScores.computeIfAbsent(item.system(), system -> score(state, home, origin, new Target(item.kind(), item.id(), system, item.site(), 0)))))
                .sorted(Comparator.comparingDouble(Target::score).reversed().thenComparingInt(item -> item.id().equals(fallback) ? 0 : 1).thenComparing(Target::system).thenComparing(Target::id)).toList();
        var local = ranked.stream().filter(item -> origin.equals(item.system())).findFirst().orElse(null);
        var selected = ranked.stream().limit(policy.maximumTargetPreviews()).filter(item -> resolve(state,
                new ShipDeploymentIntent(home.id(), item.kind(), item.id(), "", state.turn(), "Target preview")) != null).findFirst().orElse(null);
        if (selected == null) return null;
        if (local != null && !origin.equals(selected.system()) && selected.score() < local.score() + policy.remotePriorityMargin()) selected = local;
        return new ShipDeploymentIntent(home.id(), selected.kind(), selected.id(), "", state.turn(), "Planned " + selected.kind().name().toLowerCase(Locale.ROOT) + " in " + selected.system());
    }
    private static double score(GameState state, Empire home, String origin, Target target) {
        var policy = DeploymentPolicy.current();
        long hostile = state.fleets().stream().filter(fleet -> target.system().equals(fleet.currentSystemId()) && !fleet.ships().isEmpty()
                && state.diplomaticRelations().stream().anyMatch(relation -> "TOTAL_WAR".equals(relation.tier())
                && (home.id().equals(relation.empireAId()) && fleet.ownerEntityId().equals(relation.empireBId())
                || home.id().equals(relation.empireBId()) && fleet.ownerEntityId().equals(relation.empireAId())))
                && (SensorCoverage.detects(state, home.id(), fleet) || state.fogOfWarStates().stream().anyMatch(fog -> home.id().equals(fog.empireId()) && fog.isFleetDetected(fleet.id()))))
                .flatMap(fleet -> fleet.ships().stream()).filter(ship -> ship.currentHullHealth() > 0).count();
        long friendly = state.fleets().stream().filter(fleet -> home.id().equals(fleet.ownerEntityId()) && target.system().equals(fleet.currentSystemId()) && role(state, fleet, true))
                .flatMap(fleet -> fleet.ships().stream()).filter(ship -> ship.currentHullHealth() > 0).count();
        long incoming = state.shipyardLedger().deployments().values().stream().filter(intent -> home.id().equals(intent.ownerId()) && military(intent.kind())
                && target.system().equals(system(state, intent))).count();
        incoming += state.shipConstructionOrders().stream().filter(order -> order.deployment() != null && home.id().equals(order.ownerEntityId())
                && military(order.deployment().kind()) && target.system().equals(system(state, order.deployment()))).count();
        var source = state.solarSystems().stream().filter(item -> origin.equals(item.id())).findFirst().orElse(null);
        var destination = state.solarSystems().stream().filter(item -> target.system().equals(item.id())).findFirst().orElse(null);
        double distance = source == null || destination == null ? 0 : Math.sqrt(Math.pow(source.x() - destination.x(), 2)
                + Math.pow(source.y() - destination.y(), 2) + Math.pow(source.z() - destination.z(), 2));
        return target.score() + hostile * policy.detectedThreatWeight() - friendly * policy.friendlyForceWeight()
                - incoming * policy.incomingWeight() - Math.log1p(distance) * policy.distanceWeight();
    }
    private static boolean military(ShipDeploymentIntent.Kind kind) { return kind == ShipDeploymentIntent.Kind.DEFENSE || kind == ShipDeploymentIntent.Kind.ESCORT || kind == ShipDeploymentIntent.Kind.REINFORCEMENT; }
    private static boolean role(GameState state, Fleet fleet, boolean combat) {
        return fleet.ships().stream().anyMatch(ship -> state.shipDesigns().stream().anyMatch(design -> ship.designId().equals(design.id())
                && (combat ? EmpireFleetPlanner.combat(design.role()) : ShipRole.CARGO_TRANSPORT.equals(design.role()))));
    }
    public static String system(GameState state, ShipDeploymentIntent intent) {
        if (!intent.transitSystemId().isBlank()) return intent.transitSystemId();
        var fleet = FleetOrganization.find(state, intent.targetId());
        return fleet == null ? ConstructionMaterials.systemForBody(state, intent.targetId()) : fleet.currentSystemId();
    }
    public static Target resolve(GameState state, ShipDeploymentIntent intent) {
        var home = EconomicOwnership.empireFor(state, intent.ownerId()); if (home == null) return null;
        if (intent.kind() == ShipDeploymentIntent.Kind.REINFORCEMENT || intent.kind() == ShipDeploymentIntent.Kind.ESCORT) {
            var fleet = FleetOrganization.find(state, intent.targetId());
            if (fleet == null || !intent.ownerId().equals(fleet.ownerEntityId()) || !home.controlledSystemIds().contains(fleet.currentSystemId()) || !AIShipDeploymentIntent.idle(fleet)
                    || fleet.ships().stream().anyMatch(ship -> state.shipyardLedger().deployments().containsKey(ship.id()))) return null;
            return new Target(intent.kind(), fleet.id(), fleet.currentSystemId(), fleet.location().current(), 0);
        }
        var station = state.orbitalStations().stream().filter(item -> intent.targetId().equals(item.id()) && intent.ownerId().equals(item.ownerEntityId()) && item.isOperational()
                && home.controlledSystemIds().contains(item.systemId())).findFirst().orElse(null);
        if (station != null) return new Target(intent.kind(), station.id(), station.systemId(), FleetLocation.Site.docked(station.id()), 0);
        var system = ConstructionMaterials.systemForBody(state, intent.targetId());
        if (system == null || !home.controlledSystemIds().contains(system)) return null;
        // A lost station cannot be silently interpreted as an owned celestial body.
        if (state.orbitalStations().stream().anyMatch(item -> intent.targetId().equals(item.id()))) return null;
        return new Target(intent.kind(), intent.targetId(), system, FleetLocation.Site.surface(intent.targetId()), 0);
    }
}
