package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.ship.*;

/** Paid cross-system deployment and saved transit handling, independent of local mission assignment. */
final class CrossSystemDeployment {
    private CrossSystemDeployment() {}
    static GameState transit(GameState state, Fleet fleet, String shipId, ShipDeploymentIntent intent) {
        if (intent.transitSystemId().isBlank()) return null;
        if (intent.transitComplete()) return fleet.hasInterstellarOrder() || fleet.isInWarp() ? update(state, shipId, null) : null;
        if (fleet.hasInterstellarOrder() || fleet.isInWarp()) {
            if (!intent.transitSystemId().equals(fleet.targetSystemId())) return update(state, shipId, null);
            return update(state, shipId, intent.status(Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                    ? "Deployment crossing interrupted; retaining mission" : "Deployment crossing to " + intent.transitSystemId()));
        }
        if (fleet.location().inTransit()) return update(state, shipId, intent.status("Waiting for deployment movement"));
        return update(state, shipId, fleet.currentSystemId().equals(intent.transitSystemId()) ? intent.arrived() : null);
    }
    /** Null means ordinary local assignment can continue. */
    static GameState deploy(GameState state, Fleet fleet, String shipId, ShipDeploymentIntent intent) {
        var target = StrategicDeploymentPlanner.resolve(state, intent);
        if (target == null) {
            var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
            var design = FleetSupplySimulation.design(state, ship);
            String fallback = fleet.location().current().kind() == FleetLocation.Kind.DEEP_SPACE ? "" : fleet.location().current().entityId();
            var replacement = StrategicDeploymentPlanner.choose(state, design, fleet.currentSystemId(), fallback);
            return replacement == null ? update(state, shipId, intent.status("Waiting for an owned deployment target"))
                    : update(state, shipId, replacement.status("Deployment target changed; mission revalidated"));
        }
        if (target.system().equals(fleet.currentSystemId())) {
            if (intent.kind() == ShipDeploymentIntent.Kind.DEFENSE && !fleet.location().stationaryAt(target.site())) {
                var plan = FleetReturnTravel.localPlan(state, fleet, target.site());
                if (plan == null) return update(state, shipId, intent.status("Waiting for funded destination approach"));
                var arrival = LocalTravel.arrivalPreview(state, fleet, target.site(), plan);
                if (FleetWeaponRecovery.needs(FleetReturnTravel.replace(state, arrival), arrival)) return update(state, shipId, intent.status("Waiting for arrival combat stores"));
                var move = new MoveFleetLocalCommand(fleet.id(), target.site().kind(), target.site().entityId());
                return move.validate(state) ? update(move.apply(state), shipId, intent.status("Approaching deployment site"))
                        : update(state, shipId, intent.status("Waiting for destination approach"));
            }
            return null;
        }
        var quote = ready(state, fleet, target);
        if (quote != null) return depart(state, fleet, shipId, intent, target);
        var prepared = prepare(state, fleet);
        if (prepared != state && EconomicOwnership.cash(prepared, intent.ownerId()) >= DeploymentPolicy.current().minimumTreasuryCredits()) {
            var readyFleet = FleetOrganization.find(prepared, fleet.id()); var funded = ready(prepared, readyFleet, target);
            if (funded != null) return update(prepared, shipId, intent.status("Paid deployment travel reserves prepared"));
        }
        return update(state, shipId, intent.status("Waiting for funded crossing, destination approach and combat reserves"));
    }
    private static MoveFleetCommand.DeparturePreview ready(GameState state, Fleet fleet, StrategicDeploymentPlanner.Target target) {
        var policy = DeploymentPolicy.current(); var protectedFleet = protect(fleet);
        var projected = FleetReturnTravel.replace(state, protectedFleet); var command = new MoveFleetCommand(fleet.id(), target.system());
        var quote = command.preview(projected, false);
        if (quote == null || !quote.ready() || quote.totalDays() > policy.maximumTravelDays()
                || EconomicOwnership.cash(state, fleet.ownerEntityId()) - quote.launchCostCredits() < policy.minimumTreasuryCredits()) return null;
        return FleetDeploymentArrival.project(projected, protectedFleet, target.system(), quote.local(), quote.crossing(), target.site(), policy.arrivalPropellantFraction()) == null ? null : quote;
    }
    private static Fleet protect(Fleet fleet) {
        double reserve = DeploymentPolicy.current().arrivalPropellantFraction();
        return fleet.withFuelPolicy(fleet.fuelPolicy().protectBoth(new FleetFuelPolicy(reserve, reserve, reserve)));
    }
    private static GameState depart(GameState state, Fleet fleet, String shipId, ShipDeploymentIntent intent,
                                   StrategicDeploymentPlanner.Target target) {
        var protectedState = FleetReturnTravel.replace(state, protect(fleet));
        var command = new MoveFleetCommand(fleet.id(), target.system());
        return command.validate(protectedState) ? update(command.apply(protectedState), shipId, intent.transit(target.system(), "Deployment crossing staged to " + target.system())) : state;
    }
    private static GameState prepare(GameState state, Fleet fleet) {
        var current = state; String source = fleet.location().current().entityId();
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(current, ship);
            double missing = design.fuelCapacityKg() - ship.currentFuelKg();
            if (missing > 0 && PropulsionCatalog.mainDrive(design.equippedModuleIds()) != null) current = new RefuelShipCommand(ship.id(), source, missing).apply(current);
            var profile = design.powerProfile(ship); var power = ShipPowerProcessor.reserves(ship);
            if (profile == null) continue;
            for (String feed : java.util.List.of(power.chemicalMixture(), power.reactorFuel())) {
                var fuel = profile.fuels().get(feed); if (fuel == null) continue;
                boolean reactor = fuel.oxidizerId() == null;
                double occupied = power.generatorMaterialsKg().entrySet().stream().filter(entry -> profile.fuels().values().stream().filter(item -> (item.oxidizerId() == null) == reactor)
                        .anyMatch(item -> entry.getKey().equals(item.materialId()) || entry.getKey().equals(item.oxidizerId()))).mapToDouble(java.util.Map.Entry::getValue).sum();
                double room = (reactor ? profile.reactorTankKg() : profile.generatorTankKg()) - occupied;
                if (room > 0) current = new ResupplyShipPowerCommand(ship.id(), source, feed, room).apply(current);
            }
            double input = Math.max(0, profile.batteryKwh() - power.batteryChargeKwh()) / ShipPowerProcessor.CHARGING_EFFICIENCY;
            if (Double.isFinite(input) && input > 0) current = new ChargeShipBatteryCommand(ship.id(), source, input).apply(current);
        }
        return current;
    }
    private static GameState update(GameState state, String shipId, ShipDeploymentIntent intent) {
        return java.util.Objects.equals(intent, state.shipyardLedger().deployments().get(shipId)) ? state
                : state.toBuilder().shipyardLedger(state.shipyardLedger().deployment(shipId, intent)).build();
    }
}
