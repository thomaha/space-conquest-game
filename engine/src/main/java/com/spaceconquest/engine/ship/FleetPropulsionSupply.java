package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded power and physical phases for timed supplies and replacement braking plans. */
public final class FleetPropulsionSupply {
    public record Result(Fleet fleet, double firstUnpoweredHour, Map<String, Double> fuelUsedKg,
                         Map<String, Double> demandKwh, List<String> problems) {
        public Result {
            fuelUsedKg = Map.copyOf(fuelUsedKg); demandKwh = Map.copyOf(demandKwh); problems = List.copyOf(problems);
        }
    }
    public record Forecast(List<ShipPowerForecast.Readiness> electrical, double days, Fleet arrival) {
        public Forecast { electrical = List.copyOf(electrical); }
    }
    private FleetPropulsionSupply() {}

    public static boolean hasOrder(Fleet fleet) { return FleetSupplySimulation.hasOrders(fleet); }
    private static FleetSupplyOrder order(GameState state, Fleet fleet) {
        return FleetSupplySimulation.orders(fleet).stream().min(java.util.Comparator
                .comparingDouble((FleetSupplyOrder item) -> completion(state, fleet, item))
                .thenComparing(item -> item.destination() == ShipSupplyTransfer.Destination.DRIVE_REACTOR ? 0 : 1)
                .thenComparing(FleetSupplyOrder::supplierShipId).thenComparing(FleetSupplyOrder::receiverShipId)).orElse(null);
    }

    private static Fleet physical(Fleet fleet) {
        if (Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()) && hasOrder(fleet)
                && fleet.ships().getFirst().supplyState().timeline() == null) {
            var old = fleet.flightMotion();
            return fleet.withShips(fleet.ships().stream().map(ship -> ship.withSupplyState(ship.supplyState().withTimeline(
                    new FleetSupplyTimeline(old.elapsedSeconds() / 3600, old.trajectory().accelerationSeconds() / 3600)))).toList());
        }
        if (!Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode())) return fleet;
        double burn = fleet.interstellarPeakSpeedMps() / fleet.interstellarAccelerationMps2();
        double total = FleetSupplySimulation.totalHours(fleet) * 3600;
        var trajectory = new FlightMotion.Trajectory(0, 0, fleet.interstellarAccelerationMps2(),
                fleet.interstellarPeakSpeedMps(), burn, Math.max(0, total - 2 * burn), burn);
        double elapsed = fleetElapsed(fleet);
        fleet = fleet.withShips(fleet.ships().stream().map(ship -> ship.withSupplyState(ship.supplyState()
                .withTimeline(new FleetSupplyTimeline(elapsed, burn / 3600)))).toList());
        return motion(fleet, Fleet.MODE_RECOVERY, trajectory.at(fleet.interstellarElapsedDays() * 86400),
                fleet.interstellarFuelBudgetKg(), fleet.journeyPropulsion());
    }

    public static double completionHour(GameState state, Fleet fleet) {
        var order = order(state, fleet);
        if (order == null || fleet.flightMotion() == null || fleet.flightMotion().trajectory() == null) return Double.NaN;
        double completion = completion(state, fleet, order);
        var trajectory = fleet.flightMotion().trajectory();
        double now = clock(fleet), offset = now - fleet.flightMotion().elapsedSeconds() / 3600;
        return completion <= offset + trajectory.brakingStart() / 3600
                && completion >= offset + trajectory.accelerationSeconds() / 3600 - 1e-8
                && completion > fleet.ships().getFirst().supplyState().timeline().coastingStartHours() ? completion : Double.NaN;
    }
    private static double fleetElapsed(Fleet fleet) { return fleet.interstellarElapsedDays() * 24; }
    private static double clock(Fleet fleet) {
        var timeline = fleet.ships().getFirst().supplyState().timeline();
        return timeline == null ? fleetElapsed(fleet) : timeline.elapsedHours();
    }
    private static double completion(GameState state, Fleet fleet, FleetSupplyOrder order) {
        var supplier = find(fleet, order.supplierShipId());
        if (supplier == null || supplier.supplyState().timeline() == null) return Double.POSITIVE_INFINITY;
        double rate = ShipSupplyCatalog.transferKgPerHour(FleetSupplySimulation.design(state, supplier));
        return supplier.supplyState().timeline().coastingStartHours() + order.coastDelayHours()
                + order.quantityKg() / rate;
    }

    /** Power and motion both advance here; FleetProcessor leaves the resulting recovery itinerary alone. */
    public static Fleet advanceDay(GameState state, Fleet fleet) {
        var reset = fleet.withShips(fleet.ships().stream().map(ship -> {
            var p = ShipPowerProcessor.reserves(ship);
            return ship.withPowerState(new ShipPowerState(p.generatorMaterialsKg(), p.chemicalMixture(), p.reactorFuel(),
                    p.batteryChargeKwh(), p.arraysDeployed(), p.arrayCondition(), p.orientationFraction(),
                    p.unmetEssentialHours(), 0, 0, 0, p.chargedInputKwhToday(), p.cargoPreservation(), p.rescueStatus()));
        }).toList());
        var result = run(state, reset, 24);
        return result.fleet().withShips(result.fleet().ships().stream().map(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            return design == null ? ship : CargoDeterioration.advanceDay(ship.withPowerState(
                    ShipPowerProcessor.reserves(ship).withChargeInputToday(0)), design);
        }).toList());
    }

    public static Result run(GameState state, Fleet initial, double hours) {
        return run(state, initial, hours, true);
    }
    private static Result run(GameState state, Fleet initial, double hours, boolean validateContinuation) {
        if (!Double.isFinite(hours) || hours < 0) throw new IllegalArgumentException("Invalid supply duration");
        Fleet current = physical(initial);
        double elapsed = 0, failure = Double.POSITIVE_INFINITY;
        Map<String, Double> used = new HashMap<>(), demand = new HashMap<>();
        List<String> problems = new ArrayList<>();
        // Each iteration reaches a phase boundary, delivery or first outage. No voyage-day loop.
        while (elapsed < hours - 1e-9) {
            var pending = order(state, current);
            double delivery = pending == null ? Double.NaN : completionHour(state, current);
            double now = clock(current);
            if (pending != null && (!Double.isFinite(delivery) || delivery < now - 1e-8)) {
                problems.add("Scheduled delivery does not fit the remaining coasting window.");
                current = clear(current, problems.getLast());
                pending = null;
            }
            if (pending != null && Math.abs(delivery - now) <= 1e-8) {
                Fleet supplied = problems.isEmpty() ? deliver(state, current, pending, now, validateContinuation) : null;
                if (supplied == null) {
                    problems.add("Scheduled delivery failed: stock, capacity, reactor feed, braking geometry or electrical endurance changed.");
                    current = clear(current, problems.getLast());
                } else current = supplied;
                continue;
            }
            double stepHours = Math.min(hours - elapsed, nextBoundary(current));
            if (pending != null) stepHours = Math.min(stepHours, delivery - now);
            var step = power(state, current, stepHours);
            double fueledHours = FlightFuelLimits.availableHours(current, stepHours);
            double stop = Math.min(fueledHours, step.firstUnpoweredHour());
            if (stop < stepHours && !Fleet.MODE_POWER_INTERRUPTED.equals(current.interstellarMode())
                    && current.hasInterstellarOrder()) {
                double powered = stop;
                var stopped = power(state, current, powered);
                merge(used, stopped.fuelUsedKg()); merge(demand, stopped.demandKwh());
                problems.addAll(stopped.problems());
                current = advanceMotion(stopped.fleet(), powered);
                failure = Math.min(failure, elapsed + powered);
                if (current.hasInterstellarOrder()) current = interrupt(current);
                if (hasOrder(current)) problems.add("Propellant or electrical supply failed before scheduled delivery.");
                current = clear(current, "Scheduled delivery cancelled: thrust unavailable before contact.");
                elapsed += powered;
                continue;
            }
            merge(used, step.fuelUsedKg()); merge(demand, step.demandKwh()); problems.addAll(step.problems());
            failure = Math.min(failure, elapsed + step.firstUnpoweredHour());
            current = advanceMotion(step.fleet(), stepHours);
            elapsed += stepHours;
        }
        // A delivery exactly at the requested endpoint is processed once, including save/tick boundaries.
        var pending = order(state, current);
        while (pending != null && current.flightMotion() != null
                && Math.abs(completionHour(state, current) - clock(current)) <= 1e-8) {
            var supplied = problems.isEmpty() ? deliver(state, current, pending, clock(current), validateContinuation) : null;
            if (supplied == null) {
                problems.add("Scheduled delivery failed: stock, capacity, reactor feed, braking geometry or electrical endurance changed.");
                current = clear(current, problems.getLast());
            } else current = supplied;
            pending = order(state, current);
        }
        return new Result(current, failure, used, demand, problems.stream().distinct().toList());
    }

    private static double nextBoundary(Fleet fleet) {
        if (!Fleet.MODE_RECOVERY.equals(fleet.interstellarMode())) return Double.POSITIVE_INFINITY;
        var m = fleet.flightMotion(); var t = m.trajectory();
        for (double seconds : new double[]{t.accelerationSeconds(), t.brakingStart(), t.totalSeconds()})
            if (seconds > m.elapsedSeconds() + 1e-8) return (seconds - m.elapsedSeconds()) / 3600;
        return Double.POSITIVE_INFINITY;
    }

    private static Result power(GameState state, Fleet fleet, double hours) {
        List<ShipInstance> ships = new ArrayList<>();
        Map<String, Double> used = new HashMap<>(), demand = new HashMap<>();
        List<String> problems = new ArrayList<>(); double failure = Double.POSITIVE_INFINITY;
        boolean burning = Fleet.MODE_RECOVERY.equals(fleet.interstellarMode())
                && fleet.flightMotion().trajectory().burning(fleet.flightMotion().elapsedSeconds() + hours * 1800);
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || design.powerProfile() == null) {
                problems.add(ship.id() + ": propulsion supply requires modeled electrical equipment."); ships.add(ship); continue;
            }
            var p = design.powerProfile(); var reserves = ShipPowerProcessor.reserves(ship);
            double essential = p.essentialKw(ship, design), cargo = ShipPowerProcessor.cargoKw(p, ship, design);
            var step = ShipPowerProcessor.interval(p, reserves, hours, 0, essential, cargo, burning ? p.driveKw() : 0);
            failure = Math.min(failure, step.firstUnpoweredHour());
            if (!step.supplied()) problems.add(ship.id() + ": electrical supply cannot fund essential services, cargo preservation and active propulsion.");
            used.put(ship.id(), reserves.fuelMassKg() - step.state().fuelMassKg());
            demand.put(ship.id(), hours * (essential + cargo + (burning ? p.driveKw() : 0)));
            ships.add(ship.withPowerState(step.state()));
        }
        return new Result(fleet.withShips(ships), failure, used, demand, problems);
    }

    private static Fleet advanceMotion(Fleet fleet, double hours) {
        fleet = tickClock(fleet, hours);
        if (!fleet.hasInterstellarOrder()) return fleet;
        var previous = fleet.flightMotion();
        if (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode()))
            return motion(fleet, Fleet.MODE_POWER_INTERRUPTED, new FlightMotion(previous.positionMeters()
                    + previous.velocityMps() * hours * 3600, previous.velocityMps(), null, 0),
                    fleet.interstellarFuelBudgetKg(), fleet.journeyPropulsion());
        var t = previous.trajectory(); double seconds = previous.elapsedSeconds() + hours * 3600;
        double fraction = (t.impulse(seconds) - t.impulse(previous.elapsedSeconds())) / t.impulse(t.totalSeconds());
        var budget = fleet.interstellarFuelBudgetKg();
        var ships = fleet.ships().stream().map(ship -> withFuel(ship,
                Math.max(0, ship.currentFuelKg() - budget.getOrDefault(ship.id(), 0.0) * fraction))).toList();
        if (seconds + 1e-8 >= t.totalSeconds()) return new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(),
                fleet.targetSystemId(), "", fleet.coordinateX(), fleet.coordinateY(), 0, false, fleet.fleetStance(), ships,
                FleetLocation.at(FleetLocation.Site.deepSpace()));
        return motion(fleet.withShips(ships), Fleet.MODE_RECOVERY, t.at(seconds), fleet.interstellarFuelBudgetKg(), fleet.journeyPropulsion());
    }

    private static Fleet tickClock(Fleet fleet, double hours) {
        double elapsed = clock(fleet) + hours;
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            var old = ship.supplyState().timeline();
            return old == null ? ship : ship.withSupplyState(ship.supplyState()
                    .withTimeline(new FleetSupplyTimeline(elapsed, old.coastingStartHours())));
        }).toList());
    }

    private static Fleet deliver(GameState state, Fleet fleet, FleetSupplyOrder order, double hour, boolean validateContinuation) {
        var supplier = find(fleet, order.supplierShipId()); var receiver = find(fleet, order.receiverShipId());
        if (supplier == null || receiver == null || supplier.currentHullHealth() <= 0 || receiver.currentHullHealth() <= 0
                || !order.fleetId().equals(fleet.id()) || !order.ownerEntityId().equals(fleet.ownerEntityId())
                || !order.memberShipIds().equals(fleet.ships().stream().map(ShipInstance::id).sorted().toList())) return null;
        var design = FleetSupplySimulation.design(state, receiver);
        var drive = design == null ? null : PropulsionCatalog.mainDrive(design.equippedModuleIds());
        var materials = FleetSupplySimulation.materials(design, order);
        if (drive == null || materials == null) return null;
        ShipInstance supplied;
        if (order.destination() == ShipSupplyTransfer.Destination.PROPELLANT) {
            if (receiver.currentFuelKg() + order.quantityKg() > design.fuelCapacityKg() + 1e-6) return null;
            supplied = withFuel(receiver, receiver.currentFuelKg() + order.quantityKg());
        } else if (order.destination() == ShipSupplyTransfer.Destination.ELECTRICAL_FUEL) {
            supplied = ShipSupplyStorage.electricalReceiver(receiver, design, order.fuelId(), order.quantityKg());
            if (supplied == null) return null;
        } else {
            var saved = fleet.journeyPropulsion().get(receiver.id());
            if (saved == null || !order.fuelId().equals(saved.reactorFeedId())
                    || receiver.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum() + order.quantityKg() > design.maxCargoMassKg()) return null;
            var cargo = new HashMap<>(receiver.storedCargoKg());
            cargo.merge(order.fuelId(), order.quantityKg(), Double::sum);
            supplied = new ShipInstance(receiver.id(), receiver.designId(), receiver.ownerEntityId(), receiver.currentHullHealth(),
                    receiver.currentShieldHealth(), receiver.currentFuelKg(), cargo, receiver.passengerCount(), receiver.passengerRaceId(),
                    receiver.transitMode(), receiver.powerState(), receiver.supplyState());
        }
        var depleted = ShipSupplyStorage.withdraw(supplier, FleetSupplySimulation.design(state, supplier), materials);
        if (depleted == null) return null;
        depleted = depleted.withSupplyState(depleted.supplyState().withOrders(depleted.supplyState().orders().stream()
                .filter(item -> !item.equals(order)).toList(), "Delivered " + order.quantityKg() + " kg " + order.destination()
                + " to " + receiver.id() + " during coasting. Replanned the remaining coast and powered braking."));
        var candidate = replace(replace(fleet, depleted), supplied);
        var replacement = FleetSupplyReplanning.plan(state, candidate);
        if (replacement == null || validateContinuation && !continuationReady(state, replacement, hour)) return null;
        return replacement;
    }

    private static Fleet interrupt(Fleet fleet) {
        var m = fleet.flightMotion();
        return motion(fleet, Fleet.MODE_POWER_INTERRUPTED, new FlightMotion(m.positionMeters(), m.velocityMps(), null, 0),
                fleet.interstellarFuelBudgetKg(), fleet.journeyPropulsion());
    }
    private static Fleet clear(Fleet fleet, String explanation) {
        return fleet.withShips(fleet.ships().stream().map(ship -> ship.supplyState().order() == null ? ship
                : ship.withSupplyState(ship.supplyState().withOrder(null, explanation))).toList());
    }
    static Fleet motion(Fleet fleet, String mode, FlightMotion motion, Map<String, Double> budget,
                        Map<String, JourneyPropulsion> propulsion) {
        return new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), fleet.currentSystemId(), fleet.targetSystemId(),
                fleet.coordinateX(), fleet.coordinateY(), Math.clamp(motion.positionMeters() / fleet.interstellarDistanceMeters(), 0, 1),
                false, fleet.fleetStance(), fleet.ships(), fleet.location(), mode, fleet.interstellarTravelDays(),
                fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(), fleet.interstellarElapsedDays(),
                fleet.interstellarPeakSpeedMps(), budget, motion, propulsion);
    }
    static ShipInstance withFuel(ShipInstance ship, double fuel) {
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                fuel, ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
    }
    private static ShipInstance find(Fleet fleet, String id) { return fleet.ships().stream().filter(ship -> ship.id().equals(id)).findFirst().orElse(null); }
    private static Fleet replace(Fleet fleet, ShipInstance ship) { return fleet.withShips(fleet.ships().stream().map(item -> item.id().equals(ship.id()) ? ship : item).toList()); }
    static void merge(Map<String, Double> target, Map<String, Double> other) { other.forEach((id, value) -> target.merge(id, value, Double::sum)); }

    private static double remainingHours(Fleet fleet) {
        return Fleet.MODE_RECOVERY.equals(fleet.interstellarMode())
                ? Math.max(0, (fleet.flightMotion().trajectory().totalSeconds() - fleet.flightMotion().elapsedSeconds()) / 3600) : 0;
    }
    private static Result project(GameState state, Fleet fleet) {
        Fleet current = physical(fleet);
        Map<String, Double> used = new HashMap<>(), demand = new HashMap<>();
        List<String> problems = new ArrayList<>(); double failure = Double.POSITIVE_INFINITY;
        while (hasOrder(current)) {
            double completion = completionHour(state, current);
            double hours = Double.isFinite(completion) ? Math.max(0, Math.min(completion - clock(current), remainingHours(current)))
                    : remainingHours(current);
            var step = run(state, current, hours, false);
            merge(used, step.fuelUsedKg()); merge(demand, step.demandKwh()); problems.addAll(step.problems());
            failure = Math.min(failure, step.firstUnpoweredHour());
            if (FleetSupplySimulation.orders(step.fleet()).size() >= FleetSupplySimulation.orders(current).size()) {
                problems.add("Pending deliveries cannot be reached before braking.");
                current = clear(step.fleet(), problems.getLast()); break;
            }
            current = step.fleet();
        }
        double hours = Math.max(0, Math.ceil((clock(current) + remainingHours(current)) / 24) * 24 - clock(current));
        var tail = run(state, current, hours, false);
        merge(used, tail.fuelUsedKg()); merge(demand, tail.demandKwh()); problems.addAll(tail.problems());
        failure = Math.min(failure, tail.firstUnpoweredHour());
        return new Result(tail.fleet(), failure, used, demand, problems.stream().distinct().toList());
    }
    private static boolean continuationReady(GameState state, Fleet fleet, double hour) {
        if (!hasOrder(fleet)) return FleetSupplyReplanning.electricallyReady(state, fleet, hour);
        var projected = project(state, fleet);
        return projected.problems().isEmpty() && !Double.isFinite(projected.firstUnpoweredHour())
                && !projected.fleet().hasInterstellarOrder() && arrivalReady(state, projected.fleet());
    }
    private static boolean arrivalReady(GameState state, Fleet fleet) {
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            var p = design == null ? null : design.powerProfile();
            if (p == null || !ShipArrivalReserve.check(p, ShipPowerProcessor.reserves(ship), p.essentialKw(ship, design),
                    ShipPowerProcessor.cargoKw(p, ship, design), ShipSolarEnvironment.DARK).ready()) return false;
        }
        return true;
    }
    public static Forecast forecast(GameState state, Fleet fleet, InterstellarTravel.Plan crossing) {
        var result = project(state, FleetSupplySimulation.planned(fleet, crossing));
        List<ShipPowerForecast.Readiness> checks = new ArrayList<>();
        for (var ship : result.fleet().ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || design.powerProfile() == null) {
                checks.add(new ShipPowerForecast.Readiness(ship.id(), false, false, 0, 0, 0, 0, "Scheduled supply requires modeled electrical equipment."));
                continue;
            }
            var p = design.powerProfile(); var reserves = ShipPowerProcessor.reserves(ship);
            var reserve = ShipArrivalReserve.check(p, reserves, p.essentialKw(ship, design),
                    ShipPowerProcessor.cargoKw(p, ship, design), ShipSolarEnvironment.DARK);
            boolean ready = result.problems().isEmpty() && !Double.isFinite(result.firstUnpoweredHour())
                    && !result.fleet().hasInterstellarOrder() && reserve.ready();
            checks.add(new ShipPowerForecast.Readiness(ship.id(), true, ready, result.demandKwh().getOrDefault(ship.id(), 0.0), reserve.requiredKwh(),
                    reserves.batteryChargeKwh(), result.fuelUsedKg().getOrDefault(ship.id(), 0.0), ready
                    ? "Scheduled coasting deliveries fund the journey, powered braking and 48-hour arrival reserve."
                    : !result.problems().isEmpty() ? String.join(" ", result.problems()) : "The journey cannot fund arrival reserves."));
        }
        return new Forecast(checks, clock(result.fleet()) / 24, result.fleet());
    }
}
