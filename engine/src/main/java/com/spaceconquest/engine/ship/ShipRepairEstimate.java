package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Complete fleet repair bill from captured surviving hardware, with discarded base funding. */
public record ShipRepairEstimate(double points, Map<String, Double> materialsKg, Double baseCredits, boolean onboardCovered) {
    public ShipRepairEstimate { materialsKg = Map.copyOf(materialsKg); }
    public static ShipRepairEstimate inspect(GameState state, Fleet fleet) {
        double points = 0; var bill = new HashMap<String, Double>(); var onboard = new HashMap<String, Double>();
        var providers = FleetRepairStatus.inspect(state, fleet).providers().stream().filter(provider -> provider.availablePoints() > 0)
                .map(FleetRepairStatus.Provider::shipId).collect(java.util.stream.Collectors.toSet());
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || ship.currentHullHealth() <= 0 || !fleet.ownerEntityId().equals(ship.ownerEntityId())) continue;
            double hull = Math.max(0, ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH - ship.currentHullHealth());
            points += hull; if (hull > 0) bill.merge(design.hullMaterialId(), hull, Double::sum);
            for (var entry : ship.moduleDamage().damageByMount().entrySet()) {
                if (entry.getKey() >= design.equippedModuleIds().size()) continue;
                var module = ShipModuleDamage.damageableModule(design, design.equippedModuleIds().get(entry.getKey()));
                if (module == null || module.materialCostsKg().isEmpty()) continue;
                double damage = Math.min(ShipModuleDamage.DURABILITY, entry.getValue()); points += damage;
                module.materialCostsKg().forEach((id, kg) -> bill.merge(id, kg * damage / ShipModuleDamage.DURABILITY, Double::sum));
            }
            if (providers.contains(ship.id())) ship.storedCargoKg().forEach((id, kg) -> onboard.merge(id, kg, Double::sum));
        }
        Double credits = null;
        if (points > 0 && fleet.location().current().kind() == FleetLocation.Kind.DOCKED) {
            var normalized = new HashMap<String, Double>(); double total = points; bill.forEach((id, kg) -> normalized.put(id, kg / total));
            var quote = BaseRepairPayment.quote(state, fleet.location().current().entityId(), fleet.ownerEntityId(), normalized, points);
            if (quote.points() + 1e-6 >= points) credits = quote.credits();
        }
        boolean covered = !bill.isEmpty() && bill.entrySet().stream().allMatch(entry -> onboard.getOrDefault(entry.getKey(), 0.0) + 1e-6 >= entry.getValue());
        return new ShipRepairEstimate(points, bill, credits, covered);
    }
}
