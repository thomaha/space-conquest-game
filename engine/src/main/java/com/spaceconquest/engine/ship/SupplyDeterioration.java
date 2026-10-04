package com.spaceconquest.engine.ship;
import java.util.HashMap;
/** Cryogenic supply containment is an essential load; final daily outages cause physical boil-off. */
public final class SupplyDeterioration {
    private SupplyDeterioration() {}
    public static ShipInstance advanceDay(ShipInstance ship, ShipDesign design) {
        var supply = ship.supplyState();
        if (supply.materialsKg().isEmpty()) return ship;
        double essential = design.powerProfile().essentialKw(ship, design);
        double hours = essential <= 0 ? 0 : Math.clamp(ship.powerState().lastUnmetEssentialKwh() / essential, 0, 24);
        var stock = new HashMap<>(supply.materialsKg());
        var exposure = new HashMap<String, Double>();
        var losses = new HashMap<String, Double>();
        stock.forEach((id, mass) -> {
            var rule = CargoDeterioration.rule(id);
            if (rule == null || mass <= 0) return;
            double previous = supply.preservation().exposureHours().getOrDefault(id, 0.0);
            double next = previous + hours;
            double uncovered = Math.max(0, next - rule.graceHours()) - Math.max(0, previous - rule.graceHours());
            double lost = mass * -Math.expm1(Math.log1p(-rule.dailyLossFraction()) * uncovered / 24);
            exposure.put(id, next);
            if (lost > 0) losses.put(id, Math.min(mass, lost));
        });
        losses.forEach((id, kg) -> stock.put(id, Math.max(0, stock.get(id) - kg)));
        return ship.withSupplyState(supply.withMaterials(stock).withPreservation(new CargoPreservationState(exposure, losses)));
    }
}
