package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;

/** Bounded 48-hour arrival envelope. Local arrivals begin with the worst eclipse phase. */
public final class ShipArrivalReserve {
    public record Check(ShipPowerState remaining, boolean ready, double requiredKwh) {}
    private ShipArrivalReserve() {}

    public static ShipSolarEnvironment environment(GameState state, Fleet fleet, FleetLocation.Site site,
                                                   boolean interstellar) {
        if (interstellar || site == null) return ShipSolarEnvironment.DARK;
        boolean known = switch (site.kind()) {
            case SURFACE, ORBIT -> fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state, site.entityId()));
            case DOCKED -> state.orbitalStations().stream().anyMatch(station -> site.entityId().equals(station.id())
                    && fleet.currentSystemId().equals(station.systemId()));
            case DEEP_SPACE -> site.equals(FleetLocation.Site.deepSpace()) || site.entityId().equals(fleet.currentSystemId() + "_star")
                    || fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state, site.entityId()))
                    || state.megastructures().stream().anyMatch(mega -> fleet.currentSystemId().equals(mega.systemId())
                    && site.entityId().equals(mega.targetCelestialId()));
        };
        return known ? ShipSolarEnvironment.at(state, fleet.withLocation(FleetLocation.at(site)), site) : ShipSolarEnvironment.DARK;
    }

    public static Check check(ShipPowerProfile profile, ShipPowerState initial, double essentialKw,
                               double cargoKw, ShipSolarEnvironment environment) {
        ShipPowerState current = initial.withChargeInputToday(0);
        double elapsed = 0, period = environment.lightHours() + environment.darkHours();
        boolean ready = true;
        while (elapsed < ShipPowerProcessor.ARRIVAL_RESERVE_HOURS - 1e-9) {
            double phase = elapsed % period;
            boolean dark = phase < environment.darkHours();
            double phaseRemaining = (dark ? environment.darkHours() : period) - phase;
            double dayRemaining = 24 - elapsed % 24;
            double hours = Math.min(phaseRemaining, Math.min(dayRemaining, ShipPowerProcessor.ARRIVAL_RESERVE_HOURS - elapsed));
            var step = ShipPowerProcessor.interval(profile, current, hours,
                    dark ? 0 : ShipPowerProcessor.solarKw(profile, current, environment), essentialKw, cargoKw, 0);
            ready &= step.supplied();
            current = step.state();
            elapsed += hours;
            if (Math.abs(elapsed % 24) < 1e-9) current = current.withChargeInputToday(0);
        }
        return new Check(current, ready, (essentialKw + cargoKw) * ShipPowerProcessor.ARRIVAL_RESERVE_HOURS);
    }
}
