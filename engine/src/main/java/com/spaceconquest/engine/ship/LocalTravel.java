package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.LaunchService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plans and commits a provisional maneuver budget for one local journey. */
public final class LocalTravel {
    private static final double DOCKING_DELTA_V_MPS = 10.0;
    private static final double ORBITAL_DELTA_V_MPS = 30.0;
    private static final double DEEP_SPACE_DELTA_V_MPS = 50.0;
    private static final double EPSILON_KG = 0.000001;

    public record Plan(double days, Map<String, Double> propellantKg,
                       Map<String, InterstellarTravel.ReactorFuelUse> reactorFuelKg) {
        public Plan {
            propellantKg = Map.copyOf(propellantKg);
            reactorFuelKg = Map.copyOf(reactorFuelKg);
        }
    }

    private LocalTravel() {}

    public static Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination) {
        if (state == null || fleet == null || destination == null
                || fleet.location().inTransit() || fleet.location().current().equals(destination))
            return null;
        FleetLocation.Site origin = fleet.location().current();
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
            if (design.powerProfile() != null && design.equippedModuleIds().contains("mod_ion_drive")
                    && ShipPowerProcessor.poweredThrust(design, ship,
                    ShipSolarEnvironment.journey(state, fleet, destination)) * days * InterstellarTravel.SECONDS_PER_DAY
                    < deltaV * mass) return null;
            if (!Double.isFinite(mass) || mass <= 0.0) return null;
            double required = mass * -Math.expm1(-deltaV / exhaust);
            if (!Double.isFinite(required) || required <= 0.0
                    || ship.currentFuelKg() + EPSILON_KG < required) return null;
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
        List<ShipInstance> ships = fleet.ships().stream().map(ship -> {
            double used = plan.propellantKg().getOrDefault(ship.id(), 0.0);
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
        return fleet.withShips(ships).withLocation(fleet.location().depart(destination, plan.days()));
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
