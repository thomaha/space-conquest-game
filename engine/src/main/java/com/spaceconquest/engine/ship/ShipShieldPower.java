package com.spaceconquest.engine.ship;

import java.util.*;

/** Bounded daily quotes share burn/coast accounting with execution and protect required onward power. */
public final class ShipShieldPower {
    public record Phase(double hours, double driveKw, ShipSolarEnvironment environment, ShipServicePower.Activity activity) {
        public Phase(double hours, double driveKw, ShipSolarEnvironment environment) {
            this(hours, driveKw, environment, ShipServicePower.Activity.CRUISE);
        }
        public Phase { java.util.Objects.requireNonNull(activity); }
    }
    public record Budget(ShipInstance ship, ShipInstance firstDayShip, boolean ready, double journeyKwh, double firstFailureHour) {}
    private record Step(ShipInstance ship, boolean ready, double demand, double failure) {}
    private ShipShieldPower() {}

    static Budget segment(ShipInstance ship, ShipDesign design, Phase phase, double startHour,
                          java.util.function.Predicate<ShipInstance> safe) {
        var selected = execute(ship, design, List.of(phase), 0, startHour);
        double requested = ShipShieldRecharge.requestedKw(design, ship, phase.hours());
        if (requested > 1e-9 && selected.ready() && safe.test(selected.ship())) {
            var full = execute(ship, design, List.of(phase), requested, startHour);
            if (full.ready() && safe.test(full.ship())) selected = full;
            else {
                double low = 0, high = requested;
                for (int attempt = 0; attempt < 12; attempt++) {
                    double middle = (low + high) / 2;
                    var partial = execute(ship, design, List.of(phase), middle, startHour);
                    if (partial.ready() && safe.test(partial.ship())) { low = middle; selected = partial; }
                    else high = middle;
                }
            }
        }
        return new Budget(selected.ship(), selected.ship(), selected.ready(), selected.demand(), selected.failure());
    }

    public static Budget forecast(ShipInstance original, ShipDesign design, List<Phase> phases, ShipSolarEnvironment arrival) {
        return forecast(original, design, phases, arrival, true);
    }

    public static Budget forecast(ShipInstance original, ShipDesign design, List<Phase> phases, ShipSolarEnvironment arrival, boolean recharge) {
        if (design == null || design.powerProfile() == null) return new Budget(original, original, true, 0, Double.POSITIVE_INFINITY);
        var current = original.withPowerState(ShipPowerProcessor.reserves(original));
        var remaining = new ArrayList<>(phases.stream().filter(phase -> phase.hours() > 1e-9).toList());
        ShipInstance first = null; double energy = 0, firstFailure = Double.POSITIVE_INFINITY;
        for (int day = 0; !remaining.isEmpty() && day < 64; day++) {
            var today = takeDay(remaining);
            current = current.withPowerState(OrbitalPowerAccounting.startDay(ShipPowerProcessor.reserves(current)));
            var selected = execute(current, design, today, 0);
            double requested = recharge ? ShipShieldRecharge.requestedKw(design, current, today.stream().mapToDouble(Phase::hours).sum()) : 0;
            if (requested > 1e-9 && selected.ready() && onward(selected.ship(), design, remaining, arrival, today.getLast().activity())) {
                var full = execute(current, design, today, requested);
                if (full.ready() && onward(full.ship(), design, remaining, arrival, today.getLast().activity())) selected = full;
                else {
                    double low = 0, high = requested;
                    for (int attempt = 0; attempt < 12; attempt++) {
                        double middle = (low + high) / 2; var partial = execute(current, design, today, middle);
                        if (partial.ready() && onward(partial.ship(), design, remaining, arrival, today.getLast().activity())) { low = middle; selected = partial; }
                        else high = middle;
                    }
                }
            }
            current = selected.ship().withPowerState(selected.ship().powerState().withChargeInputToday(0)); energy += selected.demand();
            if (first == null) { first = current; firstFailure = selected.failure(); }
            if (!selected.ready()) return new Budget(current, first, false, energy, firstFailure);
            if (!recharge || ShipShieldRecharge.requestedKw(design, current, 24) <= 1e-9 || day == 63) {
                double elapsed = 0;
                for (var phase : remaining) {
                    var p = design.powerProfile(current); double essential = p.essentialKw(current, design), cargo = ShipPowerProcessor.cargoKw(p, current, design);
                    var base = ShipLocalPowerForecast.check(p, current.powerState(), phase.hours(), phase.environment(), essential, cargo, phase.driveKw(), elapsed, phase.activity());
                    current = current.withPowerState(base.state()); energy += (essential + cargo + phase.driveKw()) * phase.hours();
                    elapsed += phase.hours();
                    if (!base.ready()) return new Budget(current, first, false, energy, firstFailure);
                }
                remaining.clear();
            }
        }
        return new Budget(current, first == null ? current : first, remaining.isEmpty(), energy, firstFailure);
    }

    private static List<Phase> takeDay(ArrayList<Phase> remaining) {
        var today = new ArrayList<Phase>(); double hours = 24;
        while (hours > 1e-9 && !remaining.isEmpty()) {
            var phase = remaining.removeFirst(); double length = Math.min(hours, phase.hours());
            today.add(new Phase(length, phase.driveKw(), phase.environment(), phase.activity())); hours -= length;
            if (phase.hours() > length + 1e-9) remaining.addFirst(new Phase(phase.hours() - length, phase.driveKw(), phase.environment(), phase.activity()));
        }
        return today;
    }

    private static boolean onward(ShipInstance ship, ShipDesign design, List<Phase> remaining, ShipSolarEnvironment arrival, ShipServicePower.Activity activity) {
        var p = design.powerProfile(ship); var power = ship.powerState(); double elapsed = 0;
        for (var phase : remaining) {
            var budget = ShipLocalPowerForecast.check(p, power, phase.hours(), phase.environment(), p.essentialKw(ship, design),
                    ShipPowerProcessor.cargoKw(p, ship, design), phase.driveKw(), elapsed, phase.activity());
            if (!budget.ready()) return false;
            power = budget.state(); elapsed += phase.hours();
        }
        return ShipArrivalReserve.check(p, power, p.essentialKw(ship, design), ShipPowerProcessor.cargoKw(p, ship, design), arrival,
                ShipPowerProcessor.ARRIVAL_RESERVE_HOURS, activity).ready();
    }

    private static Step execute(ShipInstance original, ShipDesign design, List<Phase> phases, double rechargeKw) {
        return execute(original, design, phases, rechargeKw, 0);
    }

    private static Step execute(ShipInstance original, ShipDesign design, List<Phase> phases, double rechargeKw, double startHour) {
        var current = original; var p = design.powerProfile(original); double elapsed = startHour, energy = 0, failure = Double.POSITIVE_INFINITY;
        boolean ready = true;
        for (var phase : phases) {
            double end = elapsed + phase.hours(); var environment = phase.environment();
            double cycle = environment.lightHours() + environment.darkHours();
            while (elapsed < end - 1e-9) {
                double position = elapsed % cycle; boolean light = position < environment.lightHours();
                double hours = Math.min(end - elapsed, (light ? environment.lightHours() : cycle) - position);
                var allocation = ShipPowerProcessor.allocate(p, current.powerState(), hours,
                        light ? ShipPowerProcessor.solarKw(p, current.powerState(), environment) : 0,
                        p.essentialKw(current, design), ShipPowerProcessor.cargoKw(p, current, design), phase.driveKw(), rechargeKw, ShipServicePower.SHIELDS, phase.activity());
                var interval = allocation.interval();
                energy += (p.essentialKw(current, design) + ShipPowerProcessor.cargoKw(p, current, design) + phase.driveKw()) * hours + allocation.rechargeKwh();
                current = ShipShieldRecharge.apply(current.withPowerState(interval.state()), design, allocation.rechargeKwh());
                ready &= interval.supplied(); failure = Math.min(failure, elapsed - startHour + interval.firstUnpoweredHour()); elapsed += hours;
            }
        }
        return new Step(current, ready, energy, failure);
    }
}
