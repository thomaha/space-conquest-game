package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarRadiation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tick-owned electrical accounting. Preview uses these same pure interval calculations. */
public final class ShipPowerProcessor {
    public static final double CHARGING_EFFICIENCY = .9;
    public static final double ARRIVAL_RESERVE_HOURS = 48;
    public record Interval(ShipPowerState state, double generatedKwh, double unmetEssentialKwh,
                           double unmetDriveKwh, double unmetCargoKwh, double firstUnpoweredHour) {
        public boolean supplied() { return unmetEssentialKwh + unmetDriveKwh + unmetCargoKwh < .000001; }
    }

    private ShipPowerProcessor() {}

    public static ShipPowerState reserves(ShipInstance ship) {
        return ship.powerState() == null ? ShipPowerState.empty() : ship.powerState();
    }

    public static double solarKw(ShipPowerProfile profile, ShipPowerState state, ShipSolarEnvironment environment) {
        return !state.arraysDeployed() ? 0 : SolarRadiation.outputKw(profile.solarKw(),
                environment.fluxRelativeToEarth() * state.arrayCondition() * state.orientationFraction());
    }

    public static double cargoKw(ShipPowerProfile profile, ShipInstance ship, ShipDesign design) {
        double preservationMass = ship.storedCargoKg().entrySet().stream()
                .filter(entry -> Double.isFinite(entry.getValue()) && entry.getValue() > 0)
                .mapToDouble(entry -> entry.getValue() * CargoDeterioration.loadFactor(entry.getKey())).sum();
        double utilization = design.maxCargoMassKg() > 0 ? Math.clamp(preservationMass / design.maxCargoMassKg(), 0, 1)
                : preservationMass > 0 ? 1 : 0;
        return profile.cargoKw() * (.1 + .9 * utilization);
    }

    public static Interval interval(ShipPowerProfile profile, ShipPowerState original, double hours,
                                    double solarKw, double essentialKw, double cargoKw, double driveKw) {
        for (double value : new double[]{hours, solarKw, essentialKw, cargoKw, driveKw})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid electrical interval");
        Map<String, Double> materials = new HashMap<>(original.generatorMaterialsKg());
        double load = essentialKw + cargoKw + driveKw, solar = Math.min(load, solarKw);
        double charge = Math.min(original.batteryChargeKwh(), profile.batteryKwh());
        double essentialMissing = 0, cargoMissing = 0, driveMissing = 0, generated = 0, remaining = hours;
        double firstUnpowered = Double.POSITIVE_INFINITY;
        var chemicalFuel = profile.fuels().get(original.chemicalMixture());
        var reactorFuel = profile.fuels().get(original.reactorFuel());
        // Each event exhausts one of two fuel feeds or the battery. Work is independent of journey length.
        while (remaining > 1e-9) {
            double chemicalEnergy = fuelEnergy(chemicalFuel, materials), reactorEnergy = fuelEnergy(reactorFuel, materials);
            double chemical = chemicalEnergy > 1e-9 ? Math.min(profile.chemicalKw(), Math.max(0, load - solar)) : 0;
            double reactor = reactorEnergy > 1e-9 ? Math.min(profile.fissionKw(), Math.max(0, load - solar - chemical)) : 0;
            double battery = charge > 1e-9 ? Math.min(profile.dischargeKw(), Math.max(0, load - solar - chemical - reactor)) : 0;
            double duration = remaining;
            if (chemical > 0) duration = Math.min(duration, chemicalEnergy / chemical);
            if (reactor > 0) duration = Math.min(duration, reactorEnergy / reactor);
            if (battery > 0) duration = Math.min(duration, charge / battery);
            double available = solar + chemical + reactor + battery;
            if (available + .000001 < essentialKw
                    || driveKw > 0 && available + .000001 < essentialKw + cargoKw + driveKw)
                firstUnpowered = Math.min(firstUnpowered, hours - remaining);
            essentialMissing += Math.max(0, essentialKw - available) * duration;
            available = Math.max(0, available - essentialKw);
            cargoMissing += Math.max(0, cargoKw - available) * duration;
            available = Math.max(0, available - cargoKw);
            driveMissing += Math.max(0, driveKw - available) * duration;
            generated += solar * duration + generate(chemicalFuel, materials, chemical * duration)
                    + generate(reactorFuel, materials, reactor * duration);
            charge = Math.max(0, charge - battery * duration);
            remaining = Math.max(0, remaining - duration);
        }
        double input = solarKw <= load ? 0 : Math.min(Math.max(0, profile.batteryKwh() - charge) / CHARGING_EFFICIENCY,
                Math.min(Math.max(0, profile.chargeKw() * 24 - original.chargedInputKwhToday()),
                Math.min(profile.chargeKw(), Math.max(0, solarKw - load)) * hours));
        charge += input * CHARGING_EFFICIENCY;
        ShipPowerState next = new ShipPowerState(materials, original.chemicalMixture(), original.reactorFuel(),
                charge, original.arraysDeployed(), original.arrayCondition(), original.orientationFraction(),
                original.unmetEssentialHours() + (essentialKw == 0 ? 0 : essentialMissing / essentialKw),
                original.unmetDriveKwh() + driveMissing, original.unmetCargoKwh() + cargoMissing,
                original.lastUnmetEssentialKwh() + essentialMissing, original.chargedInputKwhToday() + input,
                original.cargoPreservation(), original.rescueStatus());
        return new Interval(next, generated + input, essentialMissing, driveMissing, cargoMissing, firstUnpowered);
    }

    private static double generate(ShipPowerProfile.Fuel fuel, Map<String, Double> materials, double requestedKwh) {
        if (fuel == null || requestedKwh <= 0) return 0;
        double kg = materials.getOrDefault(fuel.materialId(), 0.0) / fuel.fuelFraction();
        if (fuel.oxidizerId() != null) kg = Math.min(kg,
                materials.getOrDefault(fuel.oxidizerId(), 0.0) / (1 - fuel.fuelFraction()));
        double used = Math.min(kg, requestedKwh / fuel.kwhPerKg());
        fuel.materials(used).forEach((id, quantity) -> materials.put(id,
                Math.max(0, materials.getOrDefault(id, 0.0) - quantity)));
        return used * fuel.kwhPerKg();
    }

    private static double fuelEnergy(ShipPowerProfile.Fuel fuel, Map<String, Double> materials) {
        if (fuel == null) return 0;
        double kg = materials.getOrDefault(fuel.materialId(), 0.0) / fuel.fuelFraction();
        if (fuel.oxidizerId() != null) kg = Math.min(kg,
                materials.getOrDefault(fuel.oxidizerId(), 0.0) / (1 - fuel.fuelFraction()));
        return kg * fuel.kwhPerKg();
    }

    public static double poweredThrust(ShipDesign design, ShipInstance ship, ShipSolarEnvironment environment) {
        ShipPowerProfile profile = design.powerProfile();
        if (profile == null || !design.equippedModuleIds().contains("mod_ion_drive")) return design.totalThrustN();
        ShipPowerState state = reserves(ship);
        double supply = solarKw(profile, state, environment);
        if (fuelAvailable(profile, state, state.chemicalMixture())) supply += profile.chemicalKw();
        if (fuelAvailable(profile, state, state.reactorFuel())) supply += profile.fissionKw();
        // Sustained trajectories cannot assume a finite battery supplies the entire burn.
        double propulsion = Math.min(profile.driveKw(), Math.max(0,
                supply - profile.essentialKw(ship, design) - cargoKw(profile, ship, design)));
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        return drive == null ? 0 : Math.min(design.totalThrustN(),
                2 * profile.electricDriveEfficiency() * propulsion * 1000 / drive.exhaustVelocityMps());
    }

    private static boolean fuelAvailable(ShipPowerProfile profile, ShipPowerState state, String id) {
        ShipPowerProfile.Fuel fuel = profile.fuels().get(id);
        return fuel != null && fuel.materials(1).keySet().stream()
                .allMatch(material -> state.generatorMaterialsKg().getOrDefault(material, 0.0) > 0);
    }

    public static List<Fleet> advanceDay(GameState state) {
        var updated = state.fleets().stream().map(fleet -> {
            fleet = FleetSupplySimulation.reconcile(fleet);
            if (FleetPropulsionSupply.hasOrder(fleet) && (Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode())
                    || Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()))) return FleetPropulsionSupply.advanceDay(state, fleet);
            double fueledHours = FlightFuelLimits.availableHours(fleet, 24);
            Tick first = account(state, fleet, fueledHours);
            double failure = Math.min(fueledHours < 24 ? fueledHours : Double.POSITIVE_INFINITY, first.firstUnpoweredHour());
            boolean active = fleet.hasInterstellarOrder() || fleet.location().inTransit();
            boolean interrupted = Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode());
            boolean recovery = Fleet.MODE_RECOVERY.equals(fleet.interstellarMode());
            if (active && failure < 24 && !interrupted) {
                Tick stopped = account(state, fleet, failure);
                return FleetSupplySimulation.reconcile(FlightRecovery.advance(fleet.withShips(stopped.ships()), failure));
            }
            Fleet powered = fleet.withShips(first.ships());
            return interrupted || recovery ? FlightRecovery.advance(powered, interrupted ? 0 : 24) : powered;
        }).toList();
        return RescueRendezvous.complete(state, updated);
    }

    private record Tick(List<ShipInstance> ships, double firstUnpoweredHour) {}

    private static Tick account(GameState state, Fleet fleet, double stopHour) {
        double[] failure = {Double.POSITIVE_INFINITY};
        List<ShipInstance> ships = fleet.ships().stream().map(ship -> {
            ShipDesign design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId()))
                    .findFirst().orElse(null);
            if (design == null || design.powerProfile() == null) return ship;
            ShipPowerState prior = reserves(ship);
            ShipPowerState current = new ShipPowerState(prior.generatorMaterialsKg(), prior.chemicalMixture(),
                    prior.reactorFuel(), prior.batteryChargeKwh(), prior.arraysDeployed(), prior.arrayCondition(),
                    prior.orientationFraction(), prior.unmetEssentialHours(), 0, 0, 0, prior.chargedInputKwhToday(),
                    prior.cargoPreservation(), prior.rescueStatus());
            ShipSolarEnvironment environment = fleet.hasInterstellarOrder() && !fleet.location().inTransit()
                    ? ShipSolarEnvironment.DARK : fleet.location().inTransit()
                    ? ShipSolarEnvironment.journey(state, fleet, fleet.location().destination())
                    : ShipSolarEnvironment.at(state, fleet, fleet.location().current());
            var profile = design.powerProfile();
            java.util.TreeSet<Double> split = new java.util.TreeSet<>(boundaries(fleet, environment));
            split.add(stopHour);
            List<Double> boundaries = List.copyOf(split);
            for (int index = 1; index < boundaries.size(); index++) {
                double start = boundaries.get(index - 1), end = boundaries.get(index), middle = (start + end) / 2;
                boolean sunlight = middle % (environment.lightHours() + environment.darkHours()) < environment.lightHours();
                var step = interval(profile, current, end - start,
                        sunlight ? solarKw(profile, current, environment) : 0,
                        profile.essentialKw(ship, design), cargoKw(profile, ship, design),
                        middle < stopHour && burningAt(fleet, middle) ? profile.driveKw() : 0);
                failure[0] = Math.min(failure[0], start + step.firstUnpoweredHour());
                current = step.state();
            }
            return CargoDeterioration.advanceDay(ship.withPowerState(new ShipPowerState(current.generatorMaterialsKg(), current.chemicalMixture(),
                    current.reactorFuel(), current.batteryChargeKwh(), current.arraysDeployed(), current.arrayCondition(),
                    current.orientationFraction(), current.unmetEssentialHours(), current.unmetDriveKwh(),
                    current.unmetCargoKwh(), current.lastUnmetEssentialKwh(), 0, current.cargoPreservation(), current.rescueStatus())), design);
        }).toList();
        return new Tick(ships, failure[0]);
    }

    private static List<Double> boundaries(Fleet fleet, ShipSolarEnvironment environment) {
        java.util.TreeSet<Double> times = new java.util.TreeSet<>(List.of(0.0, 24.0));
        double period = environment.lightHours() + environment.darkHours();
        for (double start = 0; start < 24; start += period) {
            times.add(Math.min(24, start + environment.lightHours()));
            times.add(Math.min(24, start + period));
        }
        if (fleet.hasInterstellarOrder() && !fleet.location().inTransit()
                && Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode()) && fleet.interstellarAccelerationMps2() > 0) {
            double burn = fleet.interstellarPeakSpeedMps() / fleet.interstellarAccelerationMps2() / 3600;
            double total = InterstellarTravel.travelSeconds(fleet.interstellarDistanceMeters(),
                    fleet.interstellarAccelerationMps2(), fleet.interstellarPeakSpeedMps()) / 3600;
            for (double boundary : new double[]{burn, total - burn, total})
                times.add(Math.clamp(boundary - fleet.interstellarElapsedDays() * 24, 0, 24));
        }
        if (Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()) && fleet.flightMotion() != null) {
            var motion = fleet.flightMotion();
            var trajectory = motion.trajectory();
            for (double boundary : new double[]{trajectory.accelerationSeconds(), trajectory.brakingStart(), trajectory.totalSeconds()})
                times.add(Math.clamp((boundary - motion.elapsedSeconds()) / 3600, 0, 24));
        }
        return List.copyOf(times);
    }

    private static boolean burningAt(Fleet fleet, double hour) {
        if (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())) return false;
        if (Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()))
            return fleet.flightMotion().trajectory().burning(fleet.flightMotion().elapsedSeconds() + hour * 3600);
        if (fleet.location().inTransit()) return true;
        if (!fleet.hasInterstellarOrder()) return false;
        if (!Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode())) return true;
        if (fleet.interstellarAccelerationMps2() <= 0) return false;
        double burn = fleet.interstellarPeakSpeedMps() / fleet.interstellarAccelerationMps2() / 3600;
        double total = InterstellarTravel.travelSeconds(fleet.interstellarDistanceMeters(),
                fleet.interstellarAccelerationMps2(), fleet.interstellarPeakSpeedMps()) / 3600;
        double elapsed = fleet.interstellarElapsedDays() * 24 + hour;
        return elapsed < burn || elapsed >= total - burn && elapsed < total;
    }

}
