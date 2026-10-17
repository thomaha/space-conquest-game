package com.spaceconquest.engine.ship;

import java.util.*;

/** Required preparation without dropping, converting or inventing stored resources. */
public final class ShipRefitStores {
    private ShipRefitStores() {}
    public static List<String> blockers(com.spaceconquest.engine.GameState state, ShipInstance ship, ShipDesign source, ShipDesign target,
                                        ShipModelComparison changes, ShipModuleDamage mapped) {
        var result = new ArrayList<>(blockers(ship, source, target, changes, mapped));
        long craft = state.auxiliaryForces().wings().stream().filter(wing -> ship.id().equals(wing.carrierShipId()))
                .mapToLong(com.spaceconquest.engine.combat.CarrierWing::maxCraftCount).sum();
        if (craft > mapped.capability(target, "hangarCraftCapacity")) result.add("Transfer strike craft before reducing their hangar capacity.");
        if (state.auxiliaryForces().orders().stream().anyMatch(order -> ship.id().equals(order.carrierShipId())))
            result.add("Finish pending strike craft construction before refitting their carrier.");
        return List.copyOf(result);
    }
    public static List<String> blockers(ShipInstance ship, ShipDesign source, ShipDesign target, ShipModelComparison changes, ShipModuleDamage mapped) {
        var result = new ArrayList<String>(); double cargo = ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        if (ship.passengerCount() > 0) result.add("Disembark passengers before refit work.");
        if (cargo > target.maxCargoMassKg() + 1e-6) result.add(String.format(Locale.ROOT, "Unload %.2f kg of cargo to fit the target model.", cargo - target.maxCargoMassKg()));
        var oldDrive = PropulsionCatalog.mainDrive(source.equippedModuleIds()); var newDrive = PropulsionCatalog.mainDrive(target.equippedModuleIds());
        boolean replacedTank = changes.components().stream().anyMatch(change -> removed(change) && change.source().hardware().operationalStats().getOrDefault("fuelCapacityKg", 0.0) > 0);
        if (ship.currentFuelKg() > 0 && (replacedTank || oldDrive == null || newDrive == null || !oldDrive.propellantMaterials(1).equals(newDrive.propellantMaterials(1))))
            result.add("Drain existing main propellant before replacing its tank or changing to an incompatible drive mixture.");
        else if (ship.currentFuelKg() > target.fuelCapacityKg() + 1e-6) result.add("Unload main propellant exceeding the target tank capacity.");
        if (!ShipSupplyCatalog.fits(target, ship.supplyState().materialsKg())) result.add("Unload incompatible or excess dedicated supply inventory.");
        var power = ShipPowerProcessor.reserves(ship); var profile = target.powerProfile(ship.withModuleDamage(mapped));
        if (profile == null) { result.add("Target model lacks captured electrical equipment."); return List.copyOf(result); }
        boolean batteryReplaced = changes.components().stream().anyMatch(change -> removed(change) && change.source().hardware().operationalStats().getOrDefault("batteryKwh", 0.0) > 0);
        if (power.batteryChargeKwh() > 0 && batteryReplaced) result.add("Discharge or externally store battery charge before replacing charged battery hardware.");
        else if (power.batteryChargeKwh() > profile.batteryKwh() + 1e-6) result.add("Discharge battery energy exceeding the target capacity.");
        boolean storageReplaced = changes.components().stream().anyMatch(change -> removed(change) && (change.source().hardware().operationalStats().getOrDefault("generatorTankKg", 0.0) > 0
                || change.source().hardware().operationalStats().getOrDefault("reactorTankKg", 0.0) > 0));
        if (storageReplaced && power.fuelMassKg() > 0) result.add("Unload working generator supplies before replacing their storage hardware.");
        double chemical = 0, reactor = 0;
        for (var entry : power.generatorMaterialsKg().entrySet()) {
            if (entry.getValue() <= 0) continue;
            var fuel = profile.fuels().values().stream().filter(item -> item.materials(1).containsKey(entry.getKey())).findFirst().orElse(null);
            if (fuel == null) result.add("Unload generator material unsupported by the target model: " + entry.getKey());
            else if (fuel.oxidizerId() == null) reactor += entry.getValue(); else chemical += entry.getValue();
        }
        if (chemical > profile.generatorTankKg() + 1e-6 || reactor > profile.reactorTankKg() + 1e-6) result.add("Unload generator supplies exceeding target compartment capacity.");
        if (ship.currentShieldHealth() > mapped.capability(target, "shieldCapacity") + 1e-6) result.add("Reduce shield charge to the target model's retained capacity before refit.");
        return List.copyOf(result);
    }
    private static boolean removed(ShipModelComparison.ComponentChange change) {
        return change.kind() == ShipModelComparison.Kind.REMOVED || change.kind() == ShipModelComparison.Kind.REPLACED;
    }
}
