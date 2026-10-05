package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.LaunchService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plans physical space travel or a compatible abstract surface/legacy maneuver. */
public final class LocalTravel {
    private static final double DOCKING_DELTA_V_MPS = 10.0;
    private static final double ORBITAL_DELTA_V_MPS = 30.0;
    private static final double DEEP_SPACE_DELTA_V_MPS = 50.0;
    private static final double EPSILON_KG = 0.000001;

    public record Plan(double days, Map<String, Double> propellantKg,
                       Map<String, InterstellarTravel.ReactorFuelUse> reactorFuelKg, LocalSpaceTravel.Plan physical,
                       OrbitalFlight.Itinerary orbital) {
        public Plan(double days, Map<String, Double> propellantKg,
                    Map<String, InterstellarTravel.ReactorFuelUse> reactorFuelKg, LocalSpaceTravel.Plan physical) {
            this(days, propellantKg, reactorFuelKg, physical, null);
        }
        public Plan(double days, Map<String, Double> propellantKg,
                    Map<String, InterstellarTravel.ReactorFuelUse> reactorFuelKg) {
            this(days, propellantKg, reactorFuelKg, null);
        }
        public Plan {
            propellantKg = Map.copyOf(propellantKg);
            reactorFuelKg = Map.copyOf(reactorFuelKg);
        }
    }

    private LocalTravel() {}

    /** An explicit onward-leg allocation changes peak speed without inventing additional fuel. */
    public static Plan planRetaining(GameState state, Fleet fleet, FleetLocation.Site destination, double reserveFraction) {
        if (!Double.isFinite(reserveFraction) || reserveFraction < 0 || reserveFraction >= 1) return null;
        var plan = plan(state, fleet, destination);
        if (plan == null || plan.physical() == null || reserveFraction == 0) return plan;
        Map<String, Double> reserve = new HashMap<>();
        fleet.ships().forEach(ship -> reserve.put(ship.id(), ship.currentFuelKg() * reserveFraction));
        var physical = LocalSpaceTravel.plan(state, fleet, destination, reserve);
        return physical == null ? null : new Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
    }

    public static Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination) {
        if (state == null || fleet == null || destination == null
                || fleet.location().inTransit() || fleet.location().current().equals(destination))
            return null;
        FleetLocation.Site origin = fleet.location().current();
        if (OrbitalTravel.applies(state, fleet, destination)) return OrbitalTravel.preview(state, fleet, destination).plan();
        if (!state.solarSystems().isEmpty() && origin.kind() != FleetLocation.Kind.SURFACE && destination.kind() != FleetLocation.Kind.SURFACE
                && !fleet.ships().isEmpty() && fleet.ships().stream().allMatch(ship -> {
                    var blueprint = design(state, ship);
                    return blueprint != null && PropulsionCatalog.mainDrive(blueprint.equippedModuleIds()) != null;
                })) {
            var physical = LocalSpaceTravel.plan(state, fleet, destination);
            return physical == null ? null : new Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
        }
        double days = FleetLocation.travelDays(origin, destination);
        double deltaV = deltaV(origin, destination);
        Map<String, Double> propellant = new HashMap<>();
        Map<String, InterstellarTravel.ReactorFuelUse> reactor = new HashMap<>();
        for (ShipInstance ship : fleet.ships()) {
            ShipDesign design = design(state, ship);
            if (design == null || !Double.isFinite(ship.currentFuelKg())
                    || ship.currentFuelKg() < 0.0
                    || ship.currentFuelKg() > design.fuelCapacityKg() + EPSILON_KG)
                return null;
            PropulsionCatalog.Drive drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive == null || deltaV == 0.0) continue;
            PropulsionCatalog.ReactorFuel fuel = PropulsionCatalog.availableReactorFuel(drive, ship);
            if (!PropulsionCatalog.reactorFuels(drive.moduleId()).isEmpty() && fuel == null)
                return null;
            double exhaust = drive.exhaustVelocityMps()
                    * (fuel == null ? 1.0 : fuel.exhaustMultiplier());
            double mass = wetMass(design, ship);
            if (!Double.isFinite(mass) || mass <= 0.0) return null;
            double thrust = ShipPowerProcessor.poweredThrust(design, ship,
                    ShipSolarEnvironment.journey(state, fleet, destination));
            if (!Double.isFinite(thrust) || thrust <= 0.0) return null;
            // Initial wet mass bounds the burn conservatively as propellant is consumed.
            double maneuverDays = deltaV / thrust * mass / InterstellarTravel.SECONDS_PER_DAY;
            if (!Double.isFinite(maneuverDays)) return null;
            days = Math.max(days, Math.ceil(maneuverDays));
            double required = mass * -Math.expm1(-deltaV / exhaust);
            if (!Double.isFinite(required) || required <= 0.0
                    || ship.currentFuelKg() + EPSILON_KG < required + fleet.fuelPolicy().reserveKg(state, fleet, ship)) return null;
            propellant.put(ship.id(), required);
            if (fuel != null && fuel.kgPerPropellantKg() > 0.0) {
                double reactorKg = required * fuel.kgPerPropellantKg();
                if (ship.storedCargoKg().getOrDefault(fuel.materialId(), 0.0)
                        + EPSILON_KG < reactorKg) return null;
                reactor.put(ship.id(), new InterstellarTravel.ReactorFuelUse(
                        fuel.materialId(), reactorKg));
            }
        }
        return new Plan(days, propellant, reactor);
    }

    /** Returns the fuel needed after refueling with a default reactor feed. */
    public static double requiredPropellantKg(ShipDesign design, ShipInstance ship,
                                              FleetLocation.Site origin,
                                              FleetLocation.Site destination) {
        PropulsionCatalog.Drive drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive == null) return 0.0;
        PropulsionCatalog.ReactorFuel fuel = PropulsionCatalog.availableReactorFuel(drive, ship);
        if (fuel == null && !PropulsionCatalog.reactorFuels(drive.moduleId()).isEmpty())
            fuel = PropulsionCatalog.reactorFuels(drive.moduleId()).getFirst();
        double exhaust = drive.exhaustVelocityMps()
                * (fuel == null ? 1.0 : fuel.exhaustMultiplier());
        return wetMass(design, ship) * -Math.expm1(-deltaV(origin, destination) / exhaust);
    }

    public static Fleet depart(Fleet fleet, FleetLocation.Site destination, Plan plan) {
        if (plan.orbital() != null) return fleet.withLocation(fleet.location().depart(destination, plan.days())
                .withOrbitalFlight(new OrbitalFlight(plan.orbital(), 0, 0, OrbitalFlight.Status.WAITING,
                        plan.orbital().maneuvers().size() == 1 ? "Preparing funded docking or undocking approach"
                                : plan.orbital().parkingTransfer() ? "Preparing parking transfer" : "Waiting for launch window")));
        List<ShipInstance> ships = fleet.ships().stream().map(ship -> {
            double used = plan.physical() == null ? plan.propellantKg().getOrDefault(ship.id(), 0.0) : 0;
            InterstellarTravel.ReactorFuelUse reactor = plan.reactorFuelKg().get(ship.id());
            Map<String, Double> cargo = new HashMap<>(ship.storedCargoKg());
            if (reactor != null) {
                double remaining = Math.max(0.0,
                        cargo.getOrDefault(reactor.materialId(), 0.0) - reactor.quantityKg());
                if (remaining <= EPSILON_KG) cargo.remove(reactor.materialId());
                else cargo.put(reactor.materialId(), remaining);
            }
            return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                    ship.currentHullHealth(), ship.currentShieldHealth(),
                    Math.max(0.0, ship.currentFuelKg() - used), Map.copyOf(cargo),
                    ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
        }).toList();
        var location = fleet.location().depart(destination, plan.days());
        if (plan.physical() != null) location = location.withFlight(new LocalFlight(plan.physical().geometry(),
                plan.physical().trajectory().at(0), plan.propellantKg(), plan.physical().propulsion(), false, false));
        return fleet.withShips(ships).withLocation(location);
    }

    /** Immutable arrival projection for planning a subsequent leg; does not advance the live fleet. */
    public static Fleet arrivalPreview(GameState state, Fleet fleet, FleetLocation.Site destination, Plan plan) {
        if (plan.orbital() == null) return projectedArrival(LocalSpacePowerForecast.consume(state, fleet, destination, plan), destination, plan);
        return fleet.withShips(fleet.ships().stream().map(ship -> OrbitalPowerAccounting.projection(ship,
                FleetSupplySimulation.design(state, ship), plan.orbital(), 0, 0).ship()).toList()).withLocation(FleetLocation.at(destination));
    }

    /** Conservative budget-only projection; full electrical orbital prediction uses arrivalPreview. */
    public static Fleet projectedArrival(Fleet fleet, FleetLocation.Site destination, Plan plan) {
        if (plan.orbital() != null) return OrbitalFlightProcessor.projectedArrival(fleet, destination, plan);
        Fleet departure = depart(fleet, destination, plan);
        if (plan.physical() != null) departure = LocalFlightProcessor.advance(departure, plan.days() * 24, false);
        return departure.withLocation(FleetLocation.at(destination));
    }

    public static LaunchService.Plan surfaceLaunchPlan(GameState state, Fleet fleet) {
        if (fleet.location().current().kind() != FleetLocation.Kind.SURFACE) return null;
        double dryMass = fleet.ships().stream().mapToDouble(ship -> {
            ShipDesign design = design(state, ship);
            return design == null ? 0.0 : design.totalDryMassKg();
        }).sum();
        if (dryMass <= 0.0) return null;
        double payload = fleet.ships().stream().mapToDouble(ship ->
                ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum()
                        + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg() + ship.passengerCount() * 80.0).sum();
        return LaunchService.choose(state, fleet.location().current().entityId(),
                fleet.ownerEntityId(), Math.max(1.0, payload),
                fleet.ships().stream().anyMatch(ship -> ship.passengerCount() > 0),
                true, dryMass);
    }

    private static ShipDesign design(GameState state, ShipInstance ship) {
        return state.shipDesigns().stream().filter(item -> ship.designId().equals(item.id()))
                .findFirst().orElse(null);
    }

    private static double wetMass(ShipDesign design, ShipInstance ship) {
        return design.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg()
                + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum()
                + ship.passengerCount() * 80.0;
    }

    private static double deltaV(FleetLocation.Site origin, FleetLocation.Site destination) {
        if (origin.entityId().equals(destination.entityId())
                && (origin.kind() == FleetLocation.Kind.SURFACE
                    || destination.kind() == FleetLocation.Kind.SURFACE)) return 0.0;
        if (origin.kind() == FleetLocation.Kind.DEEP_SPACE
                || destination.kind() == FleetLocation.Kind.DEEP_SPACE)
            return DEEP_SPACE_DELTA_V_MPS;
        if (origin.kind() == FleetLocation.Kind.DOCKED
                || destination.kind() == FleetLocation.Kind.DOCKED)
            return DOCKING_DELTA_V_MPS;
        return ORBITAL_DELTA_V_MPS;
    }
}
