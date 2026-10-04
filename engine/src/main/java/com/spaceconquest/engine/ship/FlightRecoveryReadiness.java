package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only explanations for the same physical and electrical gates used by a recovery command. */
public final class FlightRecoveryReadiness {
    public record Check(FlightRecovery.Plan plan, List<String> blockers) {
        public Check { blockers = List.copyOf(blockers); }
        public boolean ready() { return plan != null && blockers.isEmpty(); }
    }
    private FlightRecoveryReadiness() {}

    public static Check check(GameState state, Fleet fleet) {
        if (state == null || fleet == null || !Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                || fleet.location().inTransit() || !fleet.hasInterstellarOrder() || fleet.interstellarDistanceMeters() <= 0)
            return new Check(null, List.of("Physical recovery requires an interrupted sublight crossing."));
        List<String> blockers = new ArrayList<>();
        if (state.solarSystems().stream().noneMatch(system -> system.id().equals(fleet.targetSystemId())))
            blockers.add("The destination system is unavailable.");
        var motion = FlightRecovery.motion(fleet);
        double remaining = fleet.interstellarDistanceMeters() - motion.positionMeters();
        if (remaining <= 0) blockers.add("The fleet has drifted beyond its destination; forward recovery cannot return it.");
        if (fleet.ships().isEmpty()) blockers.add("The fleet has no ships.");
        for (ShipInstance ship : fleet.ships()) {
            var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
            if (design == null || !ship.ownerEntityId().equals(fleet.ownerEntityId())) {
                blockers.add(ship.id() + ": blueprint or fleet ownership is unavailable.");
                continue;
            }
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive == null) {
                blockers.add(ship.id() + ": a recognized physical drive is required.");
                continue;
            }
            if (ship.currentFuelKg() <= 0) blockers.add(ship.id() + ": main-drive propellant is empty.");
            var reactor = PropulsionCatalog.availableReactorFuel(drive, ship);
            if (!PropulsionCatalog.reactorFuels(drive.moduleId()).isEmpty() && reactor == null)
                blockers.add(ship.id() + ": carry compatible drive reactor feed in cargo ("
                        + PropulsionCatalog.reactorFuels(drive.moduleId()).stream().map(PropulsionCatalog.ReactorFuel::materialId)
                        .reduce((first, next) -> first + " or " + next).orElse("") + ").");
            double mass = design.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg()
                    + ship.passengerCount() * 80.0 + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
            double thrust = ShipPowerProcessor.poweredThrust(design, ship, ShipSolarEnvironment.DARK);
            if (thrust <= 0) blockers.add(ship.id() + ": no powered thrust; check drive electricity and equipment.");
            else if (remaining > 0 && motion.velocityMps() * motion.velocityMps() * mass / (2 * thrust) > remaining)
                blockers.add(ship.id() + ": remaining distance is too short for powered braking at current mass and thrust.");
            double exhaust = drive.exhaustVelocityMps() * (reactor == null ? 1 : reactor.exhaustMultiplier());
            double brakingKg = mass * -Math.expm1(-motion.velocityMps() / exhaust);
            if (ship.currentFuelKg() > 0 && ship.currentFuelKg() + .000001 < brakingKg)
                blockers.add(String.format(Locale.ROOT, "%s: braking alone needs at least %.3f kg propellant; %.3f kg aboard.",
                        ship.id(), brakingKg, ship.currentFuelKg()));
        }
        var plan = FlightRecovery.plan(state, fleet);
        if (plan == null) {
            if (blockers.isEmpty()) blockers.add("Remaining propulsion reserves or travel data cannot support an acceleration and braking itinerary.");
            return new Check(null, blockers);
        }
        for (ShipInstance ship : fleet.ships()) {
            if (!FlightRecovery.electricallyReady(state, fleet.withShips(List.of(ship)), plan))
                blockers.add(String.format(Locale.ROOT,
                        "%s: electrical fuel, battery charge or output is insufficient for %.2f days of travel plus the 48-hour essential and cargo reserve.",
                        ship.id(), plan.trajectory().totalSeconds() / 86400));
        }
        return new Check(plan, blockers);
    }
}
