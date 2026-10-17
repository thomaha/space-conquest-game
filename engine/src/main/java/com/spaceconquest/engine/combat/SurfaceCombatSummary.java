package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Saved actual paid launches and net damage; this receipt does not estimate damage from weapon balance values. */
public record SurfaceCombatSummary(List<Battery> batteries, double fleetHullLost, double fleetShieldLost,
        double fleetComponentDamageAdded, boolean fleetWithdrew) {
    public SurfaceCombatSummary { batteries = List.copyOf(batteries); }
    public record Battery(String id, String weaponApplicationId, int paidShots, Map<String, Double> ammunitionSpentKg,
                          double energySpentKwh, double hullLost, double componentDamageAdded, double finalHull, double finalCondition) {
        public Battery { ammunitionSpentKg = Map.copyOf(ammunitionSpentKg); }
    }
    public static SurfaceCombatSummary capture(List<PlanetaryDefenseBattery> before, Fleet fleet,
            TacticalCombatProcessor.CombatEngagementResult result, boolean withdrew) {
        var receipts = new ArrayList<Battery>();
        for (var battery : before) {
            var after = result.survivingPlanetaryBatteries().stream().filter(b -> b.id().equals(battery.id())).findFirst().orElseThrow();
            int shots = result.roundReports().stream().flatMap(r -> r.detailedActions().stream())
                    .filter(a -> "SURFACE_BATTERY".equals(a.phase()) && battery.id().equals(a.sourceEntityId())).mapToInt(TacticalCombatProcessor.CombatActionReport::paidShots).sum();
            var ammunition = new TreeMap<String, Double>();
            battery.stores().ammunitionKg().forEach((id, kg) -> {
                double spent = kg - after.stores().ammunitionKg().getOrDefault(id, 0.0); if (spent > 0) ammunition.put(id, spent);
            });
            receipts.add(new Battery(battery.id(), battery.weaponApplicationId(), shots, ammunition,
                    Math.max(0, battery.stores().energyKwh() - after.stores().energyKwh()), Math.max(0, battery.hullHealth() - after.hullHealth()),
                    Math.max(0, damage(after.moduleDamage()) - damage(battery.moduleDamage())), after.hullHealth(), after.moduleDamage().condition(0)));
        }
        double hull = 0, shield = 0, electronics = 0;
        for (var ship : fleet.ships()) {
            var survivor = result.survivingAttackerFleet() == null ? null : result.survivingAttackerFleet().ships().stream()
                    .filter(s -> s.id().equals(ship.id())).findFirst().orElse(null);
            hull += Math.max(0, ship.currentHullHealth() - (survivor == null ? 0 : survivor.currentHullHealth()));
            shield += Math.max(0, ship.currentShieldHealth() - (survivor == null ? 0 : survivor.currentShieldHealth()));
            if (survivor != null) electronics += Math.max(0, damage(survivor.moduleDamage()) - damage(ship.moduleDamage()));
        }
        return new SurfaceCombatSummary(receipts, hull, shield, electronics, withdrew);
    }
    private static double damage(ShipModuleDamage damage) { return damage.damageByMount().values().stream().mapToDouble(Double::doubleValue).sum(); }
}
