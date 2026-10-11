package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import java.io.IOException;
import java.util.List;

/** Saved delivery suspension with unchanged paid lots; resumed travel never loads or settles freight. */
public final class TradeEscape {
    public record Result(GameState state, TradeRoute route) {}
    private TradeEscape() {}

    public static boolean associated(TradeRoute route, Fleet fleet) {
        return route.assignedFreighterIds().stream().anyMatch(id -> fleet.ships().stream().anyMatch(ship -> id.equals(ship.id())));
    }

    public static boolean canPause(GameState state, TradeRoute route, Fleet fleet) {
        return fleet.ownerEntityId().equals(route.ownerEntityId()) && !route.assignedFreighterIds().isEmpty()
                && route.assignedFreighterIds().stream().allMatch(id -> fleet.ships().stream().anyMatch(ship -> id.equals(ship.id())))
                && (TradeRoute.DELIVERING.equals(route.phase()) || TradeRoute.RETURNING.equals(route.phase())
                    || TradeRoute.RETURNING_CARGO.equals(route.phase()) || route.escapePaused())
                && fleet.ships().stream().filter(ship -> route.assignedFreighterIds().getFirst().equals(ship.id()))
                .anyMatch(ship -> state.shipDesigns().stream().anyMatch(design -> ship.designId().equals(design.id())
                    && ShipRole.CARGO_TRANSPORT.equals(design.role())));
    }

    public static GameState pause(GameState state, List<String> fleetIds) {
        return state.withTradeRoutes(state.tradeRoutes().stream().map(route -> state.fleets().stream()
                .anyMatch(fleet -> fleetIds.contains(fleet.id()) && associated(route, fleet) && canPause(state, route, fleet))
                ? route.withPhase(route.onboardKg() > 1e-6 ? TradeRoute.PAUSED_DELIVERY : TradeRoute.PAUSED_RETURN)
                    .withStatus("Delivery paused for coordinated escape; paid cargo retained.") : route).toList());
    }

    public static Result advance(GameState state, TradeRoute route, Fleet fleet, ShipInstance carrier) {
        var retained = route.withAvailableCargo(carrier.storedCargoKg());
        if (retained.onboardKg() <= 1e-6) retained = retained.withPhase(TradeRoute.PAUSED_RETURN);
        else retained = retained.withPhase(route.phase());
        if (fleet.hasInterstellarOrder() || fleet.isInWarp() || fleet.location().inTransit() && !RecoveredParkingTravel.ready(fleet))
            return waitAt(state, retained, "Escape or recovery in progress; paid cargo retained.");
        if (FleetThreatAssessment.assess(state, fleet).action() == FleetThreatAssessment.Action.ESCAPE)
            return waitAt(state, retained, "Waiting for safety before resuming delivery.");
        var buyer = hub(state, retained.destinationEntityId()); var source = hub(state, retained.originEntityId());
        if (retained.onboardKg() > 1e-6 && retained.isActive() && safePort(state, fleet, buyer)) {
            var resumed = dispatch(state, retained, fleet, buyer, TradeRoute.DELIVERING);
            if (resumed != null) return resumed;
        }
        if (safePort(state, fleet, source)) {
            if (FleetPositioning.atHub(state, fleet, source))
                return new Result(state, retained.onboardKg() > 1e-6
                        ? retained.withPhase(TradeRoute.RETURNING_CARGO).withStatus("Paid cargo held at source; waiting for a viable delivery.")
                        : retained.withPhase(TradeRoute.LOADING).withStatus("Returned safely after escape."));
            var returned = dispatch(state, retained, fleet, source,
                    retained.onboardKg() > 1e-6 ? TradeRoute.RETURNING_CARGO : TradeRoute.RETURNING);
            if (returned != null) return returned;
        }
        return waitAt(state, retained, "Waiting: neither delivery nor return can be funded safely; paid cargo retained.");
    }

    private static Result waitAt(GameState state, TradeRoute route, String status) {
        return new Result(state, route.withStatus(status));
    }

    private static CommercialHub hub(GameState state, String id) {
        return state.commercialHubs().stream().filter(hub -> id.equals(hub.id())).findFirst().orElse(null);
    }

    private static boolean safePort(GameState state, Fleet fleet, CommercialHub hub) {
        if (hub == null || FleetPositioning.systemForHub(state, hub) == null) return false;
        var site = FleetPositioning.hubSite(state, hub);
        if (site == null || site.kind() == FleetLocation.Kind.DOCKED && state.orbitalStations().stream().noneMatch(station ->
                site.entityId().equals(station.id()) && station.isOperational())) return false;
        var observer = new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), FleetPositioning.systemForHub(state, hub), "",
                0, 0, 0, false, "PASSIVE", fleet.ships(), FleetLocation.at(site));
        return state.fleets().stream().filter(enemy -> FleetInterceptProcessor.contact(state, observer, enemy))
                .noneMatch(enemy -> FleetThreatAssessment.distance(state, enemy, FleetLocation.at(site)) <= FleetThreatAssessment.THREAT_RADIUS_METERS);
    }

    private static Result dispatch(GameState state, TradeRoute route, Fleet fleet, CommercialHub port, String phase) {
        if (FleetPositioning.atHub(state, fleet, port)) return new Result(state, route.withPhase(phase).withStatus("Delivery resumed with paid cargo."));
        var destination = FleetPositioning.hubSite(state, port);
        if (!fleet.location().inTransit()) {
            var movedState = new LogisticsProcessor().moveToward(state, fleet.id(), port);
            var movedFleet = FleetOrganization.find(movedState, fleet.id());
            if (!movedFleet.location().inTransit() && !movedFleet.hasInterstellarOrder()) return null;
            return new Result(movedState, route.withPhase(phase).withOperatingCost(Math.max(0,
                    balance(state, route.ownerEntityId()) - balance(movedState, route.ownerEntityId())))
                    .withStatus("Resuming funded travel with cargo ownership retained."));
        }
        if (!fleet.currentSystemId().equals(FleetPositioning.systemForHub(state, port))) return null;
        var plan = LocalTravel.plan(state, fleet, destination);
        if (plan == null || !ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, destination, plan, null))) return null;
        if (state.passengerManifests().stream().anyMatch(manifest -> fleet.ships().stream().anyMatch(ship -> ship.id().equals(manifest.shipId())))) {
            try {
                if (!PassengerTransitProcessor.canSustainJourney(state, fleet, DataModelLoader.loadRaces(), plan.days())) return null;
            } catch (IOException unavailableSpecies) { return null; }
        }
        var moved = LocalTravel.depart(fleet, destination, plan);
        return new Result(state.withFleets(state.fleets().stream().map(item -> item.id().equals(fleet.id()) ? moved : item).toList()),
                route.withPhase(phase).withStatus(TradeRoute.DELIVERING.equals(phase)
                        ? "Resuming delivery of the existing paid load." : "Returning safely with cargo ownership retained."));
    }

    private static double balance(GameState state, String owner) {
        return state.corporations().stream().filter(company -> owner.equals(company.id())).mapToDouble(Corporation::liquidCapitalReserves)
                .findFirst().orElseGet(() -> state.empires().stream().filter(empire -> owner.equals(empire.id()))
                    .mapToDouble(Empire::treasuryCredits).findFirst().orElse(0));
    }
}
