package com.spaceconquest.engine.ship;

/** Final daily main-tank venting after power and any interrupted movement have been resolved. */
public final class MainTankThermalLoss {
    private MainTankThermalLoss() {}

    public static Fleet advanceDay(com.spaceconquest.engine.GameState state, Fleet fleet) {
        return fleet.withShips(fleet.ships().stream().map(ship -> advanceDay(ship, FleetSupplySimulation.design(state, ship))).toList());
    }

    public static ShipInstance advanceDay(ShipInstance ship, ShipDesign design) {
        if (design == null || !design.equippedModuleIds().contains(ChemicalFreighterCatalog.MAIN_TANK)
                || design.powerProfile() == null || ship.powerState() == null || ship.currentFuelKg() <= 0) return ship;
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive == null) return ship;
        double essential = design.powerProfile().essentialKw(ship, design);
        double exposure = essential <= 0 ? 0 : Math.clamp(ship.powerState().lastUnmetEssentialKwh() / essential, 0, 24);
        double fraction = ChemicalFreighterCatalog.unconditionedDailyLoss(drive.moduleId());
        double lost = ship.currentFuelKg() * -Math.expm1(Math.log1p(-fraction) * exposure / 24);
        if (lost <= 0) return ship;
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                Math.max(0, ship.currentFuelKg() - lost), ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(),
                ship.transitMode(), ship.powerState(), ship.supplyState());
    }
}
