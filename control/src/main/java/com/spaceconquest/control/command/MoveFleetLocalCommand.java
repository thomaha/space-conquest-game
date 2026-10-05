package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.logistics.LaunchService;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.LocalTravel;
import com.spaceconquest.engine.ship.ShipPowerForecast;

import java.util.ArrayList;
import java.util.List;

/** Starts a timed journey between two sites in the fleet's current system. */
public record MoveFleetLocalCommand(String fleetId, FleetLocation.Kind targetKind,
                                    String targetEntityId, boolean emergencyOverride, Double targetParkingAltitudeKm) implements GameCommand {
    public MoveFleetLocalCommand(String fleetId, FleetLocation.Kind targetKind, String targetEntityId, boolean emergencyOverride) {
        this(fleetId, targetKind, targetEntityId, emergencyOverride, null);
    }
    public MoveFleetLocalCommand(String fleetId, FleetLocation.Kind targetKind, String targetEntityId) {
        this(fleetId, targetKind, targetEntityId, false);
    }
    private Fleet planningFleet(Fleet fleet) {
        return emergencyOverride ? fleet.withFuelPolicy(new com.spaceconquest.engine.ship.FleetFuelPolicy(0, 0, 0)) : fleet;
    }
    @Override
    public boolean validate(GameState state) {
        if (state == null || fleetId == null || targetKind == null || targetEntityId == null)
            return false;
        if (targetParkingAltitudeKm != null && (targetKind != FleetLocation.Kind.ORBIT
                || !Double.isFinite(targetParkingAltitudeKm) || targetParkingAltitudeKm <= 0)) return false;
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id()))
                .findFirst().orElse(null);
        if (fleet == null || fleet.isInWarp() || fleet.location().inTransit()
                || fleet.targetSystemId() != null && !fleet.targetSystemId().isBlank()) return false;
        if (state.tradeRoutes().stream().filter(route -> route.isActive()
                || !TradeRoute.LOADING.equals(route.phase()))
                .flatMap(route -> route.assignedFreighterIds().stream())
                .anyMatch(id -> fleet.ships().stream().anyMatch(ship -> id.equals(ship.id()))))
            return false;
        FleetLocation.Site destination = destination();
        if (fleet.location().isAt(destination)) return false;
        var plan = LocalTravel.plan(state, planningFleet(fleet), destination);
        if (plan == null || !ShipPowerForecast.ready(
                ShipPowerForecast.departure(state, fleet, destination, plan, null))) return false;
        if (!PassengerDepartureReadiness.ready(state, fleet, plan.days())) return false;
        if (fleet.location().current().kind() == FleetLocation.Kind.SURFACE
                && LocalTravel.surfaceLaunchPlan(state, fleet) == null) return false;
        if (targetKind == FleetLocation.Kind.DEEP_SPACE)
            return FleetLocation.Site.deepSpace().equals(destination)
                    || targetEntityId.equals(fleet.currentSystemId() + "_star")
                    || state.megastructures().stream().anyMatch(mega ->
                    fleet.currentSystemId().equals(mega.systemId())
                            && targetEntityId.equals(mega.targetCelestialId()));
        if (targetKind == FleetLocation.Kind.DOCKED)
            return state.orbitalStations().stream().anyMatch(station ->
                    station.id().equals(targetEntityId)
                            && station.systemId().equals(fleet.currentSystemId()));
        return fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state,
                targetEntityId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        Fleet departing = state.fleets().stream().filter(item -> fleetId.equals(item.id()))
                .findFirst().orElseThrow();
        LaunchService.Plan launch = departing.location().current().kind()
                == FleetLocation.Kind.SURFACE
                ? LocalTravel.surfaceLaunchPlan(state, departing) : null;
        GameState paid = launch == null ? state
                : LaunchService.settle(state, departing.ownerEntityId(), launch);
        List<Fleet> fleets = new ArrayList<>(paid.fleets());
        for (int index = 0; index < fleets.size(); index++) {
            Fleet fleet = fleets.get(index);
            if (!fleetId.equals(fleet.id())) continue;
            FleetLocation.Site destination = destination();
            LocalTravel.Plan plan = LocalTravel.plan(paid, planningFleet(fleet), destination);
            fleets.set(index, LocalTravel.depart(fleet, destination, plan));
            break;
        }
        return paid.withFleets(fleets);
    }

    private FleetLocation.Site destination() {
        return new FleetLocation.Site(targetKind, targetEntityId, targetParkingAltitudeKm);
    }
}
