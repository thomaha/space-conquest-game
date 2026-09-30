package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.market.OrbitalLiftProfile;
import com.spaceconquest.engine.ship.ShipDesign;

import java.util.List;

/** Estimates the paid lift cost of moving a cargo load off a body's surface. */
public final class OrbitalFreightCost {
    private OrbitalFreightCost() {}

    private record Body(String id, double gravity, String atmosphere, double diameter) {}

    public static double credits(GameState state, String bodyId, ShipDesign design,
                                 double cargoKg) {
        if (state == null || bodyId == null || design == null || cargoKg <= 0.0)
            return Double.NaN;
        OrbitalLiftProfile profile = profile(state, bodyId, design.totalDryMassKg(),
                cargoKg, MarketProcessor.DEFAULT_PROPELLANT_PRICE_PER_KG);
        return profile == null ? Double.NaN : profile.totalLiftCostCredits();
    }

    public static OrbitalLiftProfile profile(GameState state, String bodyId, double dryMassKg,
                                             double cargoKg, double fuelPricePerKg) {
        if (state == null || bodyId == null || cargoKg <= 0.0) return null;
        Body body = state.solarSystems().stream().flatMap(system -> system.planets().stream())
                .flatMap(planet -> {
                    List<Body> bodies = new java.util.ArrayList<>();
                    bodies.add(new Body(planet.id(), planet.gravity(), planet.atmosphere(),
                            planet.diameter()));
                    planet.moons().forEach(moon -> bodies.add(new Body(moon.id(),
                            moon.gravity(), moon.atmosphere(), moon.diameter())));
                    return bodies.stream();
                }).filter(item -> bodyId.equals(item.id())).findFirst().orElse(null);
        if (body == null) return null;
        double pressure = "breathable".equalsIgnoreCase(body.atmosphere()) ? 1.0 : 0.0;
        return new MarketProcessor().calculateOrbitalLiftCost(dryMassKg,
                cargoKg, body.gravity(), pressure, body.diameter(), 0.0, fuelPricePerKg,
                List.of(), List.of(), 1.0);
    }
}
