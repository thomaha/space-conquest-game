package com.spaceconquest.engine.ship;

/** Live electrical hardware capability applied to the immutable blueprint rating. */
public final class ShipElectricalDamage {
    private ShipElectricalDamage() {}

    public static boolean hardware(String id) {
        return ShipComponentCatalog.POWER_MODULE_IDS.contains(id) || ShipComponentCatalog.BATTERY_ID.equals(id);
    }

    private static double condition(ShipDesign design, ShipInstance ship, String id) {
        double rated = 0, working = 0;
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            if (!id.equals(design.equippedModuleIds().get(mount))) continue;
            var module = CombatModuleCatalog.installedModule(design, id);
            double weight = ShipComponentCatalog.BATTERY_ID.equals(id)
                    ? module.operationalStats().get("batteryKwh") : module.powerOutputKw();
            rated += weight; working += weight * ship.moduleDamage().condition(mount);
        }
        return rated > 0 ? working / rated : 1; // Older synthetic electrical profiles retain compatibility.
    }

    public static ShipPowerProfile profile(ShipDesign design, ShipInstance ship) {
        var p = design.powerProfile();
        if (p == null) return null;
        double battery = condition(design, ship, ShipComponentCatalog.BATTERY_ID);
        return new ShipPowerProfile(p.solarKw() * condition(design, ship, ShipComponentCatalog.SOLAR_ARRAY_ID),
                p.chemicalKw() * condition(design, ship, ShipComponentCatalog.CHEMICAL_GENERATOR_ID),
                p.fissionKw() * condition(design, ship, ShipComponentCatalog.FISSION_REACTOR_ID),
                p.batteryKwh() * battery, p.chargeKw() * battery, p.dischargeKw() * battery,
                p.generatorTankKg(), p.reactorTankKg(), hotelKw(design, ship, p), p.driveKw(), p.cargoKw(),
                p.electricDriveEfficiency(), p.fuels(), ShipServicePower.live(design, ship, capturedShare(design, p)));
    }

    private static double hotelKw(ShipDesign design, ShipInstance ship, ShipPowerProfile captured) {
        double lost = 0;
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            String id = design.equippedModuleIds().get(mount);
            var module = CombatModuleCatalog.installedModule(design, id);
            if (module == null || PropulsionCatalog.drive(id) != null
                    || module.operationalStats().containsKey("cargoCapacityKg") || "cryogenic_stasis_pod".equals(id)) continue;
            if (CombatModuleCatalog.module(id) != null || SensorModuleCatalog.module(id) != null
                    || ShipRepairModuleCatalog.module(id) != null)
                lost += module.powerDrawKw() * (1 - ship.moduleDamage().condition(mount));
        }
        // Preserve the baseline and uncatalogued loads. Older reduced profiles scale only their captured share.
        return Math.max(0, captured.hotelKw() - lost * capturedShare(design, captured));
    }

    private static double capturedShare(ShipDesign design, ShipPowerProfile captured) {
        double rated = design.equippedModuleIds().stream().map(id -> CombatModuleCatalog.installedModule(design, id))
                .filter(java.util.Objects::nonNull).filter(module -> PropulsionCatalog.drive(module.id()) == null
                        && !module.operationalStats().containsKey("cargoCapacityKg") && !"cryogenic_stasis_pod".equals(module.id()))
                .mapToDouble(ShipModule::powerDrawKw).sum();
        return rated > 0 ? Math.min(1, Math.max(0, captured.hotelKw() - 2) / rated) : 0;
    }

    public static boolean damaged(com.spaceconquest.engine.GameState state, Fleet fleet) {
        return fleet.ships().stream().anyMatch(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null) return false;
            for (int mount = 0; mount < design.equippedModuleIds().size(); mount++)
                if (hardware(design.equippedModuleIds().get(mount)) && ship.moduleDamage().condition(mount) < 1) return true;
            return false;
        });
    }
    /** Damage can discard inaccessible energy; repairing hardware cannot recreate it. */
    public static ShipInstance clampCharge(ShipDesign design, ShipInstance ship) {
        if (design == null || design.powerProfile() == null || ship.powerState() == null) return ship;
        var p = ship.powerState(); double capacity = design.powerProfile(ship).batteryKwh();
        if (p.batteryChargeKwh() <= capacity) return ship;
        return ship.withPowerState(new ShipPowerState(p.generatorMaterialsKg(), p.chemicalMixture(), p.reactorFuel(),
                capacity, p.arraysDeployed(), p.arrayCondition(), p.orientationFraction(), p.unmetEssentialHours(),
                p.unmetDriveKwh(), p.unmetCargoKwh(), p.lastUnmetEssentialKwh(), p.chargedInputKwhToday(),
                p.cargoPreservation(), p.rescueStatus(), p.shedServices()));
    }
}
