package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.InterstellarTravel;
import com.spaceconquest.engine.ship.LocalTravel;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipPowerForecast;
import com.spaceconquest.engine.ship.FleetPropulsionSupply;
import com.spaceconquest.engine.ship.FleetReturnReserve;
import com.spaceconquest.engine.logistics.LaunchService;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to order researched warp travel or a slower sublight crossing.
 */
public record MoveFleetCommand(
        String fleetId,
        String targetSystemId,
        double targetX,
        double targetY,
        boolean emergencyOverride
) implements GameCommand {

    public record DeparturePreview(LocalTravel.Plan local, InterstellarTravel.Plan crossing,
                                   double launchCostCredits, List<ShipPowerForecast.Readiness> electrical,
                                   double scheduledArrivalDays, FleetReturnReserve.Preview returnReserve,
                                   List<FuelReservePreview> fuelReserves, boolean emergencyOverride) {
        public DeparturePreview(LocalTravel.Plan local, InterstellarTravel.Plan crossing,
                                double launchCostCredits, List<ShipPowerForecast.Readiness> electrical,
                                double scheduledArrivalDays, FleetReturnReserve.Preview returnReserve) {
            this(local, crossing, launchCostCredits, electrical, scheduledArrivalDays, returnReserve, List.of(), false);
        }
        public DeparturePreview(LocalTravel.Plan local, InterstellarTravel.Plan crossing,
                                double launchCostCredits, List<ShipPowerForecast.Readiness> electrical) {
            this(local, crossing, launchCostCredits, electrical, 0, null);
        }
        public DeparturePreview { electrical = List.copyOf(electrical); fuelReserves = List.copyOf(fuelReserves); }
        public boolean ready() { return ShipPowerForecast.ready(electrical); }
        public double totalDays() {
            return scheduledArrivalDays > 0 ? scheduledArrivalDays
                    : (local == null ? 0 : Math.ceil(local.days())) + Math.ceil(crossing.days());
        }
    }

    public MoveFleetCommand(String fleetId, String targetSystemId, double targetX, double targetY) {
        this(fleetId, targetSystemId, targetX, targetY, false);
    }
    public MoveFleetCommand(String fleetId, String targetSystemId, boolean emergencyOverride) {
        this(fleetId, targetSystemId, 0, 0, emergencyOverride);
    }
    public MoveFleetCommand(String fleetId, String targetSystemId) {
        this(fleetId, targetSystemId, 0.0, 0.0);
    }

    @Override
    public boolean validate(GameState state) {
        var departure = preview(state, false);
        return departure != null && departure.ready();
    }

    /** Uses the same snapshot checks and departure fuel commitments as command execution. */
    public DeparturePreview preview(GameState state) {
        return preview(state, true);
    }
    public DeparturePreview preview(GameState state, boolean includeReturn) {
        if (state == null || fleetId == null) {
            return null;
        }
        if (targetSystemId == null || state.solarSystems().stream()
                .noneMatch(system -> targetSystemId.equals(system.id()))) return null;
        Fleet selectedFleet = state.fleets().stream().filter(item -> fleetId.equals(item.id()))
                .findFirst().orElse(null);
        if (selectedFleet == null) return null;
        Fleet fleet = planningFleet(selectedFleet);
        boolean valid = state.solarSystems().stream()
                .anyMatch(system -> system.id().equals(fleet.currentSystemId()))
                && !targetSystemId.equals(fleet.currentSystemId())
                && !fleet.hasInterstellarOrder() && !fleet.isInWarp()
                && !fleet.location().inTransit()
                && state.tradeRoutes().stream().filter(route -> route.isActive()
                        || !TradeRoute.LOADING.equals(route.phase()))
                        .flatMap(route -> route.assignedFreighterIds().stream())
                        .noneMatch(id -> fleet.ships().stream()
                                .anyMatch(ship -> id.equals(ship.id())));
        if (!valid) return null;
        LaunchService.Plan launch = fleet.location().current().kind() == FleetLocation.Kind.SURFACE
                ? LocalTravel.surfaceLaunchPlan(state, fleet) : null;
        if (fleet.location().current().kind() == FleetLocation.Kind.SURFACE && launch == null) return null;
        FleetLocation.Site deepSpace = FleetLocation.Site.deepSpace();
        LocalTravel.Plan local = fleet.location().isAt(deepSpace) ? null : localPlan(state, fleet);
        if (!fleet.location().isAt(deepSpace) && local == null) return null;
        Fleet departure = local == null ? fleet : LocalTravel.projectedArrival(fleet, deepSpace, local);
        InterstellarTravel.Plan plan = InterstellarTravel.plan(state, departure, targetSystemId);
        if (plan == null) return null;
        var supply = local == null && Fleet.MODE_SUBLIGHT.equals(plan.mode()) && FleetPropulsionSupply.hasOrder(fleet)
                ? FleetPropulsionSupply.forecast(state, fleet, plan) : null;
        var power = supply == null ? ShipPowerForecast.departure(state, fleet, deepSpace, local, plan) : supply.electrical();
        var projection = !includeReturn ? null : supply != null ? supply : local == null && Fleet.MODE_SUBLIGHT.equals(plan.mode())
                && plan.propulsion().size() == fleet.ships().size() && fleet.ships().stream().allMatch(ship ->
                state.shipDesigns().stream().anyMatch(design -> design.id().equals(ship.designId()) && design.powerProfile() != null))
                ? FleetPropulsionSupply.forecast(state, fleet, plan) : null;
        var reserve = includeReturn ? FleetReturnReserve.preview(state, fleet, targetSystemId, projection) : null;
        var preview = new DeparturePreview(local, plan,
                LaunchService.payerOperatingCost(state, launch, fleet.ownerEntityId()), power, supply == null ? 0 : supply.days(), reserve,
                FuelReservePreview.inspect(state, selectedFleet, local == null ? java.util.Map.of() : local.propellantKg(),
                        plan.fuelBudgetKg(), supply == null ? null : supply.arrival()), emergencyOverride);
        return PassengerDepartureReadiness.ready(state, departure, preview.totalDays()) ? preview : null;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        Fleet selected = state.fleets().stream().filter(item -> fleetId.equals(item.id()))
                .findFirst().orElseThrow();
        LaunchService.Plan launch = selected.location().current().kind()
                == FleetLocation.Kind.SURFACE
                ? LocalTravel.surfaceLaunchPlan(state, selected) : null;
        GameState paid = launch == null ? state
                : LaunchService.settle(state, selected.ownerEntityId(), launch);
        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet fleet : paid.fleets()) {
            if (fleet.id().equals(fleetId)) {
                Fleet departure = localDeparture(paid, planningFleet(fleet));
                Fleet projected = departure.location().localFlight() == null ? departure
                        : com.spaceconquest.engine.ship.LocalFlightProcessor.advance(departure,
                        departure.location().travelDays() * 24, false).withLocation(FleetLocation.at(FleetLocation.Site.deepSpace()));
                InterstellarTravel.Plan plan = InterstellarTravel.plan(paid, projected,
                        targetSystemId);
                Fleet fueled = InterstellarTravel.commitReactorFuel(departure, plan);
                updatedFleets.add(new Fleet(
                        fleet.id(),
                        fleet.name(),
                        fleet.ownerEntityId(),
                        fleet.currentSystemId(),
                        targetSystemId,
                        fleet.coordinateX(),
                        fleet.coordinateY(),
                        0.0,
                        false,
                        fleet.fleetStance(),
                        fueled.ships(), departure.location(),
                        plan.mode(), plan.days(), plan.distanceMeters(),
                        plan.accelerationMps2(), 0.0,
                        plan.peakSpeedMps(), plan.fuelBudgetKg(), null, plan.propulsion()
                ).withFuelPolicy(fleet.fuelPolicy()));
            } else {
                updatedFleets.add(fleet);
            }
        }

        return paid.toBuilder()
                .fleets(updatedFleets)
                .build();
    }

    private Fleet planningFleet(Fleet fleet) {
        return emergencyOverride ? fleet.withFuelPolicy(new com.spaceconquest.engine.ship.FleetFuelPolicy(0, 0, 0)) : fleet;
    }

    private Fleet localDeparture(GameState state, Fleet fleet) {
        FleetLocation.Site deepSpace = FleetLocation.Site.deepSpace();
        if (fleet.location().isAt(deepSpace)) return fleet;
        LocalTravel.Plan plan = localPlan(state, fleet);
        return plan == null ? null : LocalTravel.depart(fleet, deepSpace, plan);
    }

    private LocalTravel.Plan localPlan(GameState state, Fleet fleet) {
        var deepSpace = FleetLocation.Site.deepSpace();
        var crossing = InterstellarTravel.plan(state, fleet.withLocation(FleetLocation.at(deepSpace)), targetSystemId);
        double reserve = crossing != null && Fleet.MODE_SUBLIGHT.equals(crossing.mode()) ? .5 : 0;
        return LocalTravel.planRetaining(state, fleet, deepSpace, reserve);
    }
}
