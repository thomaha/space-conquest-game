package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.List;

/** Tick-owned electrical upkeep at the actual port while trade waits for its next viable shipment. */
final class TradePortMaintenance {
    // Provisional dwell insurance, separate from the 48-hour arrival requirement.
    static final double DWELL_RESERVE_HOURS = 60 * 24;
    private TradePortMaintenance() {}

    static GameState supply(GameState state, Fleet fleet) {
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || state.commercialHubs().stream().noneMatch(hub -> FleetPositioning.atHub(state, fleet, hub))) return state;
        GameState current = state;
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(current, ship);
            if (design == null || design.powerProfile() == null) continue;
            var p = design.powerProfile();
            var initial = ShipPowerProcessor.reserves(ship);
            var environment = ShipSolarEnvironment.at(state, fleet, fleet.location().current());
            for (String id : List.of(initial.chemicalMixture(), initial.reactorFuel())) {
                var updated = current.fleets().stream().flatMap(item -> item.ships().stream())
                        .filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
                var power = ShipPowerProcessor.reserves(updated);
                var reserve = ShipArrivalReserve.check(p, power, p.essentialKw(updated, design),
                        ShipPowerProcessor.cargoKw(p, updated, design), environment, DWELL_RESERVE_HOURS);
                if (reserve.ready()) break;
                var fuel = p.fuels().get(id);
                if (fuel == null || (fuel.oxidizerId() == null ? p.fissionKw() <= 0 : p.chemicalKw() <= 0)) continue;
                double usable = power.generatorMaterialsKg().getOrDefault(fuel.materialId(), 0.0) / fuel.fuelFraction();
                if (fuel.oxidizerId() != null) usable = Math.min(usable,
                        power.generatorMaterialsKg().getOrDefault(fuel.oxidizerId(), 0.0) / (1 - fuel.fuelFraction()));
                double occupied = fuel.materials(1).keySet().stream().mapToDouble(material ->
                        power.generatorMaterialsKg().getOrDefault(material, 0.0)).sum();
                double capacity = fuel.oxidizerId() == null ? p.reactorTankKg() : p.generatorTankKg();
                double kg = Math.min(capacity - occupied, Math.max(0, reserve.requiredKwh() / fuel.kwhPerKg() * 1.05 - usable));
                if (kg > 1e-5) current = TradeLegReadiness.buyLargest(current, ship.id(),
                        fleet.location().current().entityId(), kg, id);
            }
        }
        return current;
    }
}
