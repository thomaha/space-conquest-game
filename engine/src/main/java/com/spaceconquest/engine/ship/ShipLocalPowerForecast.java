package com.spaceconquest.engine.ship;

import java.util.HashMap;
import java.util.Map;

/** Repeats stable daily illumination budgets analytically between fuel and battery boundaries. */
final class ShipLocalPowerForecast {
    record Budget(ShipPowerState state, boolean ready) {}
    private record Day(ShipPowerState state, boolean ready, double minimum, double maximum) {}
    private record Change(double battery, Map<String, Double> fuel) {}

    private ShipLocalPowerForecast() {}

    static Budget check(ShipPowerProfile profile, ShipPowerState initial, double hours,
                        ShipSolarEnvironment environment, double essential, double cargo, double drive) {
        return check(profile, initial, hours, environment, essential, cargo, drive, 0);
    }

    static Budget check(ShipPowerProfile profile, ShipPowerState initial, double hours,
                        ShipSolarEnvironment environment, double essential, double cargo, double drive, double startHour) {
        double solar = ShipPowerProcessor.solarKw(profile, initial, environment);
        if (solar == 0) {
            var result = ShipPowerProcessor.interval(profile, initial, hours, 0, essential, cargo, drive);
            return new Budget(Math.floor(startHour / 24) < Math.floor((startHour + hours) / 24)
                    ? result.state().withChargeInputToday(0) : result.state(), result.supplied());
        }
        ShipPowerState current = initial;
        Change previous = null;
        // Only generator-feed exhaustion and battery boundaries change a stable cycle.
        // This bound also prevents malformed/custom illumination from making a preview expensive.
        for (int event = 0; hours > 1e-9 && event < 64; event++) {
            ShipPowerState start = current;
            double dayPosition = startHour % 24;
            double duration = Math.min(24 - dayPosition, hours);
            Day day = day(profile, start, duration, environment, solar, essential, cargo, drive, startHour);
            current = day.state(); hours -= duration; startHour += duration;
            if (!day.ready()) return new Budget(current, false);
            Change change = change(start, current);
            double cycles = duration == 24 && same(previous, change)
                    ? repeatable(profile, start, day, change, Math.floor(hours / 24)) : 0;
            if (cycles > 0) {
                current = repeat(current, change, cycles);
                hours = Math.max(0, hours - cycles * 24);
                startHour += cycles * 24;
            }
            if (startHour % 24 < 1e-9) current = current.withChargeInputToday(0);
            previous = duration == 24 ? change : null;
        }
        return new Budget(current, hours <= 1e-9);
    }

    private static Day day(ShipPowerProfile profile, ShipPowerState initial, double hours,
                           ShipSolarEnvironment environment, double solar, double essential,
                           double cargo, double drive, double startHour) {
        ShipPowerState current = initial;
        double minimum = current.batteryChargeKwh(), maximum = minimum, elapsed = 0;
        double period = environment.lightHours() + environment.darkHours();
        for (int phase = 0; elapsed < hours && phase < 64; phase++) {
            double position = (startHour + elapsed) % period;
            boolean light = position < environment.lightHours();
            double length = (light ? environment.lightHours() : period) - position;
            double duration = Math.min(hours - elapsed, length);
            if (duration <= 0) return new Day(current, false, minimum, maximum);
            var step = ShipPowerProcessor.interval(profile, current, duration, light ? solar : 0,
                    essential, cargo, drive);
            current = step.state(); elapsed += duration;
            minimum = Math.min(minimum, current.batteryChargeKwh());
            maximum = Math.max(maximum, current.batteryChargeKwh());
            if (!step.supplied()) return new Day(current, false, minimum, maximum);
        }
        return new Day(current, elapsed >= hours, minimum, maximum);
    }

    private static Change change(ShipPowerState before, ShipPowerState after) {
        Map<String, Double> used = new HashMap<>();
        before.generatorMaterialsKg().forEach((material, amount) ->
                used.put(material, amount - after.generatorMaterialsKg().getOrDefault(material, 0.0)));
        return new Change(after.batteryChargeKwh() - before.batteryChargeKwh(), Map.copyOf(used));
    }

    private static boolean same(Change first, Change second) {
        return first != null && close(first.battery(), second.battery())
                && first.fuel().keySet().equals(second.fuel().keySet())
                && first.fuel().entrySet().stream().allMatch(entry ->
                close(entry.getValue(), second.fuel().get(entry.getKey())));
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= 1e-10 * Math.max(1, Math.max(Math.abs(first), Math.abs(second)));
    }

    private static double repeatable(ShipPowerProfile profile, ShipPowerState start, Day day,
                                     Change change, double cycles) {
        ShipPowerState current = day.state();
        for (var used : change.fuel().entrySet()) {
            if (used.getValue() > 0) cycles = Math.min(cycles,
                    Math.floor(current.generatorMaterialsKg().getOrDefault(used.getKey(), 0.0) / used.getValue()) - 1);
        }
        double offset = current.batteryChargeKwh() - start.batteryChargeKwh();
        if (change.battery() < 0) cycles = Math.min(cycles,
                Math.floor((day.minimum() + offset) / -change.battery()) - 1);
        if (change.battery() > 0) cycles = Math.min(cycles,
                Math.floor((profile.batteryKwh() - day.maximum() - offset) / change.battery()) - 1);
        return Math.max(0, cycles);
    }

    private static ShipPowerState repeat(ShipPowerState current, Change change, double cycles) {
        Map<String, Double> materials = new HashMap<>(current.generatorMaterialsKg());
        change.fuel().forEach((material, used) -> materials.computeIfPresent(material,
                (key, amount) -> Math.max(0, amount - used * cycles)));
        return new ShipPowerState(materials, current.chemicalMixture(), current.reactorFuel(),
                Math.max(0, current.batteryChargeKwh() + change.battery() * cycles), current.arraysDeployed(),
                current.arrayCondition(), current.orientationFraction(), current.unmetEssentialHours(),
                current.unmetDriveKwh(), current.unmetCargoKwh(), current.lastUnmetEssentialKwh(),
                current.chargedInputKwhToday(), current.cargoPreservation(), current.rescueStatus());
    }
}
