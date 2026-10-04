package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Tick-owned electrical upkeep at the actual port while trade waits for its next viable shipment. */
final class TradePortMaintenance {
    private TradePortMaintenance() {}

    static GameState supply(GameState state, Fleet fleet) {
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || state.commercialHubs().stream().noneMatch(hub -> FleetPositioning.atHub(state, fleet, hub))) return state;
        GameState current = state;
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(current, ship);
            if (design == null || design.powerProfile() == null) continue;
            var p = design.powerProfile(); var power = ShipPowerProcessor.reserves(ship);
            var reserve = ShipArrivalReserve.check(p, power, p.essentialKw(ship, design),
                    ShipPowerProcessor.cargoKw(p, ship, design), ShipSolarEnvironment.at(state, fleet, fleet.location().current()));
            if (reserve.ready()) continue;
            String id = p.chemicalKw() > 0 ? power.chemicalMixture() : power.reactorFuel();
            var fuel = p.fuels().get(id);
            if (fuel == null || (fuel.oxidizerId() == null ? p.fissionKw() <= 0 : p.chemicalKw() <= 0)) continue;
            double usable = power.generatorMaterialsKg().getOrDefault(fuel.materialId(), 0.0) / fuel.fuelFraction();
            if (fuel.oxidizerId() != null) usable = Math.min(usable,
                    power.generatorMaterialsKg().getOrDefault(fuel.oxidizerId(), 0.0) / (1 - fuel.fuelFraction()));
            double occupied = fuel.materials(1).keySet().stream().mapToDouble(material ->
                    power.generatorMaterialsKg().getOrDefault(material, 0.0)).sum();
            double capacity = fuel.oxidizerId() == null ? p.reactorTankKg() : p.generatorTankKg();
            double kg = Math.min(capacity - occupied, Math.max(0, reserve.requiredKwh() / fuel.kwhPerKg() * 1.05 - usable));
            if (kg > 1e-5) current = ShipPowerResupply.buy(current, ship.id(), fleet.location().current().entityId(), id, kg);
        }
        return current;
    }
}
