package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Analytic, forward-only interception along an existing sublight corridor. */
public final class RescueRendezvous {
    private RescueRendezvous() {}

    public static boolean drifting(Fleet fleet) {
        return fleet != null && Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                && fleet.hasInterstellarOrder() && !fleet.location().inTransit() && !fleet.isInWarp()
                && fleet.interstellarDistanceMeters() > 0 && fleet.flightMotion() != null
                && fleet.location().isAt(FleetLocation.Site.deepSpace());
    }

    public static boolean contact(Fleet first, Fleet second) {
        return drifting(first) && drifting(second) && sameCorridor(first, second)
                && Math.abs(first.flightMotion().positionMeters() - second.flightMotion().positionMeters()) <= 1
                && Math.abs(first.flightMotion().velocityMps() - second.flightMotion().velocityMps()) <= .001;
    }

    private static boolean sameCorridor(Fleet first, Fleet second) {
        return Objects.equals(first.currentSystemId(), second.currentSystemId())
                && Objects.equals(first.targetSystemId(), second.targetSystemId())
                && Objects.equals(first.ownerEntityId(), second.ownerEntityId())
                && Math.abs(first.interstellarDistanceMeters() - second.interstellarDistanceMeters()) < .001;
    }

    public static FlightRecovery.Plan plan(GameState state, String fleetId, RescueOrder order) {
        if (state == null || fleetId == null || order == null) return null;
        Fleet rescuer = find(state, fleetId), target = find(state, order.targetFleetId());
        if (rescuer == null || !drifting(target) || fleetId.equals(target.id()) || rescuer.ships().isEmpty()
                || !Objects.equals(rescuer.ownerEntityId(), target.ownerEntityId())
                || !Objects.equals(rescuer.currentSystemId(), target.currentSystemId())) return null;
        boolean launch = ShipSupplyTransfer.stationary(rescuer) && !rescuer.hasInterstellarOrder()
                && rescuer.location().isAt(FleetLocation.Site.deepSpace());
        if (!launch && (!drifting(rescuer) || !sameCorridor(rescuer, target))) return null;
        if (state.solarSystems().stream().noneMatch(system -> system.id().equals(target.currentSystemId()))
                || state.solarSystems().stream().noneMatch(system -> system.id().equals(target.targetSystemId()))) return null;
        if (rescuer.ships().stream().noneMatch(ship -> ship.id().equals(order.donorShipId()))
                || target.ships().stream().noneMatch(ship -> ship.id().equals(order.receiverShipId()))) return null;
        Fleet reserved = reservedSupplies(state, rescuer, target, order);
        if (reserved == null) return null;
        var motion = launch ? new FlightMotion(0, 0, null, 0) : rescuer.flightMotion();
        double gap = target.flightMotion().positionMeters() - motion.positionMeters();
        double velocity = target.flightMotion().velocityMps(), initial = motion.velocityMps();
        if (gap <= 1 || initial > velocity || velocity >= InterstellarTravel.MAX_CRUISE_MPS) return null;
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        Map<String, Double> masses = new HashMap<>(), exhausts = new HashMap<>();
        Map<String, PropulsionCatalog.ReactorFuel> reactors = new HashMap<>();
        for (ShipInstance ship : rescuer.ships()) {
            var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
            var available = reserved.ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
            if (design == null || design.powerProfile() == null || !ship.ownerEntityId().equals(rescuer.ownerEntityId())
                    || !Double.isFinite(ship.currentFuelKg()) || ship.currentFuelKg() < 0
                    || ship.currentFuelKg() > design.fuelCapacityKg() || ship.currentHullHealth() <= 0
                    || ship.storedCargoKg().values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)) return null;
            double mass = design.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg()
                    + ship.passengerCount() * 80.0 + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
            if (!Double.isFinite(mass) || design.totalDryMassKg() <= 0 || mass <= available.currentFuelKg()) return null;
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive == null) return null;
            var reactor = PropulsionCatalog.availableReactorFuel(drive, available);
            if (!PropulsionCatalog.reactorFuels(drive.moduleId()).isEmpty() && reactor == null) return null;
            double exhaust = drive.exhaustVelocityMps() * (reactor == null ? 1 : reactor.exhaustMultiplier());
            var powered = new ShipInstance(available.id(), available.designId(), available.ownerEntityId(),
                    available.currentHullHealth(), available.currentShieldHealth(), available.currentFuelKg(),
                    ship.storedCargoKg(), available.passengerCount(), available.passengerRaceId(),
                    available.transitMode(), available.powerState(), available.supplyState());
            acceleration = Math.min(acceleration, ShipPowerProcessor.poweredThrust(design, powered, ShipSolarEnvironment.DARK) / mass);
            // Keep a tiny physical remainder so repeated phase subtraction cannot consume an earmarked tank donation.
            double usableFuel = Math.max(0, available.currentFuelKg() - rescuer.fuelPolicy().reserveKg(state, rescuer, ship) - .000001);
            deltaV = Math.min(deltaV, exhaust * Math.log(mass / (mass - usableFuel)));
            masses.put(ship.id(), mass); exhausts.put(ship.id(), exhaust);
            if (reactor != null) reactors.put(ship.id(), reactor);
        }
        if (!Double.isFinite(acceleration) || acceleration <= 0 || deltaV <= velocity - initial) return null;
        double peak = Math.min(InterstellarTravel.MAX_CRUISE_MPS, Math.min((deltaV + initial + velocity) / 2,
                velocity + Math.sqrt(acceleration * gap + .5 * (velocity - initial) * (velocity - initial))));
        if (peak <= velocity) return null;
        double covered = ((peak - velocity) * (peak - velocity) - .5 * (velocity - initial) * (velocity - initial)) / acceleration;
        double coast = Math.max(0, (gap - covered) / (peak - velocity));
        if (!Double.isFinite(coast) || !Double.isFinite((peak - initial) / acceleration)
                || !Double.isFinite((peak - velocity) / acceleration)) return null;
        var trajectory = new FlightMotion.Trajectory(motion.positionMeters(), initial, acceleration, peak,
                (peak - initial) / acceleration, coast, (peak - velocity) / acceleration, velocity, order);
        if (!Double.isFinite(trajectory.totalSeconds())) return null;
        Map<String, Double> fuel = new HashMap<>();
        Map<String, InterstellarTravel.ReactorFuelUse> reactorFuel = new HashMap<>();
        Map<String, JourneyPropulsion> propulsion = new HashMap<>();
        for (ShipInstance ship : rescuer.ships()) {
            double quantity = masses.get(ship.id()) * -Math.expm1(-(2 * peak - initial - velocity) / exhausts.get(ship.id()));
            var available = reserved.ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
            if (quantity > available.currentFuelKg() + .000001) return null;
            fuel.put(ship.id(), Math.min(quantity, available.currentFuelKg()));
            var reactor = reactors.get(ship.id());
            var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElseThrow();
            propulsion.put(ship.id(), JourneyPropulsion.capture(ship, PropulsionCatalog.mainDrive(design.equippedModuleIds()), reactor, quantity).withProtectedPropellant(rescuer.fuelPolicy().reserveKg(state, rescuer, ship)));
            if (reactor != null && reactor.kgPerPropellantKg() > 0) reactorFuel.put(ship.id(),
                    new InterstellarTravel.ReactorFuelUse(reactor.materialId(), quantity * reactor.kgPerPropellantKg()));
        }
        var plan = new FlightRecovery.Plan(trajectory, fuel, reactorFuel, propulsion);
        return FlightRecovery.electricallyReady(state, rescuer, plan, reserved) ? plan : null;
    }

    private static Fleet reservedSupplies(GameState state, Fleet rescuer, Fleet target, RescueOrder order) {
        Fleet contact = corridor(rescuer, target, Fleet.MODE_POWER_INTERRUPTED,
                new FlightMotion(target.flightMotion().positionMeters(), target.flightMotion().velocityMps(), null, 0), Map.of());
        var preview = state.withFleets(state.fleets().stream().map(item -> item.id().equals(rescuer.id()) ? contact : item).toList());
        var supplied = transfer(preview, order);
        return supplied == preview ? null : find(supplied, rescuer.id());
    }

    public static Fleet depart(Fleet rescuer, Fleet target, FlightRecovery.Plan plan) {
        return FlightRecovery.depart(corridor(rescuer, target, Fleet.MODE_POWER_INTERRUPTED,
                plan.trajectory().at(0), Map.of()), plan);
    }

    /** Runs after every fleet's power and motion update, avoiding fleet-order-dependent docking. */
    public static List<Fleet> complete(GameState state, List<Fleet> fleets) {
        GameState current = state.withFleets(fleets);
        for (Fleet original : state.fleets()) {
            var motion = original.flightMotion();
            if (!Fleet.MODE_RECOVERY.equals(original.interstellarMode()) || motion == null
                    || motion.trajectory() == null || motion.trajectory().rescueOrder() == null) continue;
            Fleet advanced = find(current, original.id());
            if (advanced != null && Fleet.MODE_POWER_INTERRUPTED.equals(advanced.interstellarMode())
                    && advanced.flightMotion() != null && advanced.flightMotion().trajectory() == null)
                current = recordStatus(current, original.id(), motion.trajectory().rescueOrder(),
                        RescueStatus.Phase.POWER_FAILED, "Rescue failed: power loss stopped the intercept. Supplies were not transferred.");
        }
        for (Fleet fleet : fleets) {
            var motion = fleet.flightMotion();
            if (motion == null || motion.trajectory() == null || motion.trajectory().rescueOrder() == null
                    || !Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())) continue;
            var order = motion.trajectory().rescueOrder();
            if (motion.elapsedSeconds() + .000001 < motion.trajectory().totalSeconds()) continue;
            Fleet target = find(current, order.targetFleetId());
            boolean matched = target != null && contact(find(current, fleet.id()), target)
                    && target.ships().stream().anyMatch(ship -> ship.id().equals(order.receiverShipId()));
            GameState supplied = matched ? transfer(current, order) : current;
            RescueStatus.Phase phase = !matched ? RescueStatus.Phase.MISSED_CONTACT
                    : supplied == current ? RescueStatus.Phase.TRANSFER_REJECTED : RescueStatus.Phase.DELIVERED;
            String explanation = switch (phase) {
                case MISSED_CONTACT -> "Rescue missed contact: the target is absent or its position, velocity or ownership changed. Supplies were not transferred.";
                case TRANSFER_REJECTED -> "Rescue reached contact but supplies were not transferred: donor stock, ship location, compatibility or receiver capacity changed.";
                default -> "Rescue delivered the requested supplies. Onward travel requires a separate recovery check.";
            };
            current = recordStatus(supplied, fleet.id(), order, phase, explanation);
            Fleet live = find(current, fleet.id());
            var cleared = corridor(live, live, Fleet.MODE_POWER_INTERRUPTED,
                    new FlightMotion(motion.positionMeters(), motion.velocityMps(), null, 0), live.interstellarFuelBudgetKg());
            current = current.withFleets(current.fleets().stream().map(item -> item.id().equals(live.id()) ? cleared : item).toList());
        }
        return current.fleets();
    }

    public static GameState recordStatus(GameState state, String rescuerId, RescueOrder order,
                                         RescueStatus.Phase phase, String explanation) {
        Fleet rescuer = find(state, rescuerId);
        if (rescuer == null) return state;
        var status = new RescueStatus(rescuerId, order, phase, state.turn(), explanation);
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.withShips(fleet.ships().stream()
                .map(ship -> (ship.id().equals(order.donorShipId()) || ship.id().equals(order.receiverShipId()))
                        && ship.ownerEntityId().equals(rescuer.ownerEntityId())
                        ? ship.withPowerState(ShipPowerProcessor.reserves(ship).withRescueStatus(status)) : ship).toList())).toList());
    }

    private static GameState transfer(GameState state, RescueOrder order) {
        return ShipSupplyTransfer.transfer(state, order.donorShipId(), order.receiverShipId(), order.source(),
                order.destination(), order.fuelId(), order.quantityKg());
    }
    public static Fleet find(GameState state, String id) {
        return state.fleets().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }
    private static Fleet corridor(Fleet fleet, Fleet target, String mode, FlightMotion motion, Map<String, Double> budget) {
        return new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), target.currentSystemId(), target.targetSystemId(),
                fleet.coordinateX(), fleet.coordinateY(), Math.clamp(motion.positionMeters() / target.interstellarDistanceMeters(), 0, 1),
                false, fleet.fleetStance(), fleet.ships(), FleetLocation.at(FleetLocation.Site.deepSpace()), mode,
                target.interstellarTravelDays(), target.interstellarDistanceMeters(), target.interstellarAccelerationMps2(),
                0, target.interstellarPeakSpeedMps(), budget, motion, fleet.journeyPropulsion()).withFuelPolicy(fleet.fuelPolicy());
    }
}
