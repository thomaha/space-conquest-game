package com.spaceconquest.engine.ship;

import java.util.HashMap;
import java.util.Map;

/** Provisional cargo preservation rules. Dedicated electrical and propulsion tanks are independent. */
public final class CargoDeterioration {
    public record Rule(double graceHours, double dailyLossFraction) {}
    private static final Map<String, Rule> RULES = Map.of(
            "food_matrix", new Rule(24, .05),
            "agricultural_biomass", new Rule(48, .02),
            "liquid_oxygen", new Rule(6, .02),
            "liquid_methane", new Rule(6, .02),
            "liquid_hydrogen", new Rule(6, .03));

    private CargoDeterioration() {}
    public static Rule rule(String materialId) { return RULES.get(materialId); }
    public static double loadFactor(String materialId) {
        if (rule(materialId) == null) return 0;
        if ("agricultural_biomass".equals(materialId)) return .5;
        return materialId.startsWith("liquid_") ? 2 : 1;
    }

    /** Applies one day's final accounting, after any stopped-flight replay, never during a preview. */
    public static ShipInstance advanceDay(ShipInstance ship, ShipDesign design) {
        if (ship.powerState() == null || design.powerProfile() == null) return ship;
        var power = ship.powerState();
        double cargoKw = ShipPowerProcessor.cargoKw(design.powerProfile(), ship, design);
        double hours = cargoKw <= 0 ? 0 : Math.clamp(power.unmetCargoKwh() / cargoKw, 0, 24);
        var cargo = new HashMap<>(ship.storedCargoKg());
        Map<String, Double> exposure = new HashMap<>(), losses = new HashMap<>();
        cargo.forEach((id, mass) -> {
            Rule rule = rule(id);
            if (rule == null || !Double.isFinite(mass) || mass <= 0) return;
            double previous = power.cargoPreservation().exposureHours().getOrDefault(id, 0.0);
            double next = previous + hours;
            double exposed = Math.max(0, next - rule.graceHours()) - Math.max(0, previous - rule.graceHours());
            double loss = mass * -Math.expm1(Math.log1p(-rule.dailyLossFraction()) * exposed / 24);
            if (loss > 0) losses.put(id, Math.min(mass, loss));
            exposure.put(id, next);
        });
        losses.forEach((id, loss) -> cargo.put(id, Math.max(0, cargo.get(id) - loss)));
        var preserved = power.withCargoPreservation(new CargoPreservationState(exposure, losses));
        return SupplyDeterioration.advanceDay(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                ship.currentShieldHealth(), ship.currentFuelKg(), cargo, ship.passengerCount(), ship.passengerRaceId(),
                ship.transitMode(), preserved, ship.supplyState()), design);
    }
}
