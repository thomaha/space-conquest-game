package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.*;

/** One freshly revalidated paid commissioning handoff step. Pending intent reserves the ship. */
public record DeployAIShipCommand(String ownerId, String shipId) implements GameCommand {
    @Override public boolean validate(GameState state) { return state != null && step(state) != state; }
    @Override public GameState apply(GameState state) { return state == null ? null : step(state); }
    private GameState step(GameState state) {
        if (ownerId == null || shipId == null) return state;
        var intent = state.shipyardLedger().deployments().get(shipId);
        if (intent == null || ownerId == null || !ownerId.equals(intent.ownerId())) return state;
        var fleet = state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
        var home = EconomicOwnership.empireFor(state, ownerId);
        if (fleet == null || home == null || !ownerId.equals(fleet.ownerEntityId()) || fleet.ships().stream().noneMatch(ship -> shipId.equals(ship.id()) && ship.currentHullHealth() > 0)
                || !ownerId.equals(home.id()) && !EconomicOwnership.corporationsAllowed(home)) return clear(state);
        if (fleet.guardOrder() != null || fleet.escortOrder() != null || fleet.interceptOrder() != null
                || state.tradeRoutes().stream().anyMatch(route -> route.carrierCommitted() && route.assignedFreighterIds().contains(shipId))) return clear(state);
        var crossing = CrossSystemDeployment.transit(state, fleet, shipId, intent);
        if (crossing != null) return crossing;
        if (fleet.hasInterstellarOrder() || fleet.isInWarp()) return clear(state);
        if (!AIShipDeploymentIntent.idle(fleet)) return status(state, intent, "Waiting for current movement");
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow(); var design = FleetSupplySimulation.design(state, ship);
        if (design == null || !ownerId.equals(design.ownerEntityId())) return clear(state);
        if (EmpireFleetPlanner.combat(design.role())) {
            var service = new ServiceAICombatFleetCommand(ownerId, fleet.id());
            if (service.validate(state)) return status(service.apply(state), intent, "Preparing paid weapon and operating stores");
            if (FleetWeaponRecovery.needs(state, fleet)) return status(state, intent, "Waiting for paid weapon supplies");
            double reserve = design.fuelCapacityKg() * DeploymentPolicy.current().propellantReserveFraction();
            if (!intent.transitComplete() && PropulsionCatalog.mainDrive(design.equippedModuleIds()) != null && ship.currentFuelKg() < reserve) {
                var refuel = new RefuelShipCommand(shipId, fleet.location().current().entityId(), reserve - ship.currentFuelKg());
                return refuel.validate(state) ? status(refuel.apply(state), intent, "Preparing paid propellant") : status(state, intent, "Waiting for paid propellant");
            }
            var deployment = CrossSystemDeployment.deploy(state, fleet, shipId, intent);
            return deployment == null ? military(state, fleet, intent) : deployment;
        }
        var released = clear(state);
        if (intent.kind() == ShipDeploymentIntent.Kind.SUPPLY) {
            var need = FacilitySupplyNeeds.shortages(released, home, LogisticsPolicy.current()).stream()
                    .filter(item -> intent.targetId().equals(item.hubId()) && intent.materialId().equals(item.materialId())).findFirst().orElse(null);
            if (need == null) {
                var replacement = AIShipDeploymentIntent.choose(released, design, fleet.currentSystemId(), fleet.location().current().entityId());
                return replacement == null ? released : update(state, replacement.status("Supply need changed; mission revalidated"));
            }
            var command = new RequestAIFacilitySupplyCommand(ownerId, intent.targetId(), intent.materialId(), shipId, intent.createdTurn());
            if (command.validate(released)) return command.apply(released);
            // Ordinary paid repositioning retains the target and protected return in its saved route.
            for (var hub : released.commercialHubs().stream().sorted(java.util.Comparator.comparing(CommercialHub::id)).limit(LogisticsPolicy.current().maximumShipmentPreviews()).toList()) {
                var reposition = new RepositionAIFacilitySupplyCommand(ownerId, intent.targetId(), intent.materialId(), shipId, hub.id(), intent.createdTurn());
                if (reposition.validate(released)) return reposition.apply(released);
            }
            return status(state, intent, "Waiting for funded supply departure and return");
        }
        var trade = new AssignAIShipServiceCommand(ownerId, fleet.id());
        if (trade.validate(released)) {
            var staged = trade.apply(released); var route = staged.tradeRoutes().getLast();
            var selection = com.spaceconquest.engine.logistics.RoamingTradePlanner.choose(staged, route, fleet);
            if (selection.shipment() != null) {
                var departure = selection.shipment(); double cash = EconomicOwnership.cash(departure.state(), ownerId);
                if (cash >= LogisticsPolicy.current().treasuryReserveCredits()
                        && EconomicOwnership.cash(state, ownerId) - cash <= LogisticsPolicy.current().maximumDepartureCostCredits())
                    return departure.state().withTradeRoutes(departure.state().tradeRoutes().stream()
                            .map(item -> item.id().equals(route.id()) ? departure.route().withStatus(selection.explanation()) : item).toList());
            }
        }
        return status(state, intent, "Waiting for useful funded trade departure");
    }
    private GameState military(GameState state, Fleet fleet, ShipDeploymentIntent intent) {
        if (intent.kind() == ShipDeploymentIntent.Kind.ESCORT || intent.kind() == ShipDeploymentIntent.Kind.REINFORCEMENT) {
            var target = FleetOrganization.find(state, intent.targetId());
            if (target != null && !fleet.id().equals(target.id()) && ownerId.equals(target.ownerEntityId()) && fleet.currentSystemId().equals(target.currentSystemId()) && AIShipDeploymentIntent.idle(target)
                    && target.ships().stream().noneMatch(ship -> state.shipyardLedger().deployments().containsKey(ship.id()))) {
                GameCommand assignment = intent.kind() == ShipDeploymentIntent.Kind.ESCORT ? new EscortFleetCommand(ownerId, fleet.id(), target.id()) : new MergeFleetsCommand(ownerId, fleet.id(), target.id());
                if (assignment.validate(state)) return clear(assignment.apply(state));
                return move(state, fleet, target.location().current(), intent);
            }
            return update(state, new ShipDeploymentIntent(ownerId, ShipDeploymentIntent.Kind.DEFENSE, fleet.location().current().entityId(), "", state.turn(), "Original fleet unavailable; reassigned to defense"));
        }
        var type = state.orbitalStations().stream().anyMatch(station -> intent.targetId().equals(station.id()) && ownerId.equals(station.ownerEntityId()) && station.isOperational())
                ? FleetGuardOrder.TargetType.STATION : FleetGuardOrder.TargetType.BODY;
        var guard = new GuardObjectCommand(ownerId, fleet.id(), type, intent.targetId());
        if (guard.validate(state)) return clear(guard.apply(state));
        if (!intent.targetId().equals(fleet.location().current().entityId())) return update(state, new ShipDeploymentIntent(ownerId,
                ShipDeploymentIntent.Kind.DEFENSE, fleet.location().current().entityId(), "", state.turn(), "Defense site changed; mission revalidated"));
        return status(state, intent, "Waiting for a valid owned defense site");
    }
    private GameState move(GameState state, Fleet fleet, FleetLocation.Site site, ShipDeploymentIntent intent) {
        var plan = FleetReturnTravel.localPlan(state, fleet, site);
        if (plan == null) return status(state, intent, "Waiting for funded mission travel");
        var arrival = LocalTravel.arrivalPreview(state, fleet, site, plan); var projected = FleetReturnTravel.replace(state, arrival);
        if (FleetWeaponRecovery.needs(projected, arrival)) return status(state, intent, "Waiting for operating stores after mission travel");
        var move = new MoveFleetLocalCommand(fleet.id(), site.kind(), site.entityId());
        return move.validate(state) ? status(move.apply(state), intent, "Travelling to deployment target") : status(state, intent, "Waiting for mission rendezvous");
    }
    private GameState clear(GameState state) { return state.toBuilder().shipyardLedger(state.shipyardLedger().deployment(shipId, null)).build(); }
    private GameState status(GameState state, ShipDeploymentIntent intent, String reason) { return update(state, intent.status(reason)); }
    private GameState update(GameState state, ShipDeploymentIntent intent) {
        return intent.equals(state.shipyardLedger().deployments().get(shipId)) ? state : state.toBuilder().shipyardLedger(state.shipyardLedger().deployment(shipId, intent)).build();
    }
}
