package com.spaceconquest.engine.ship;

import java.util.HashMap;

/** Pays the actual rocket-equation burn within the saved upper budget; lighter loads keep the unused fuel. */
final class OrbitalFuelAccounting {
    private OrbitalFuelAccounting() {}
    static ShipInstance pay(ShipInstance ship, ShipDesign design, OrbitalFlight.Burn burn, ShipPowerState allocatedPower) {
        double mass = design.totalDryMassKg() + ship.currentFuelKg() + allocatedPower.fuelMassKg() + ship.supplyFuelMassKg()
                + ship.passengerCount() * 80.0 + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        double required = mass * -Math.expm1(-burn.deltaVMps() / burn.exhaustVelocityMps());
        if (!Double.isFinite(required) || required > burn.propellantKg() + 1e-6
                || ship.currentFuelKg() + 1e-6 < required + burn.reserveKg()) return null;
        var cargo = new HashMap<>(ship.storedCargoKg());
        double feed = required * burn.reactorKgPerPropellantKg();
        if (feed > 0) {
            double available = cargo.getOrDefault(burn.reactorMaterialId(), 0.0);
            if (available + 1e-6 < feed) return null;
            if (available - feed <= 1e-6) cargo.remove(burn.reactorMaterialId());
            else cargo.put(burn.reactorMaterialId(), available - feed);
        }
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                Math.max(0, ship.currentFuelKg() - required), cargo, ship.passengerCount(), ship.passengerRaceId(),
                ship.transitMode(), allocatedPower, ship.supplyState());
    }
}
