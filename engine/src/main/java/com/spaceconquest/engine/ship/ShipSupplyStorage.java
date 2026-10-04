package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.logistics.LaunchService;
import java.util.HashMap;
import java.util.Map;

/** Physical purchases and withdrawals for dedicated supply compartments. */
public final class ShipSupplyStorage {
    private ShipSupplyStorage() {}
    public static GameState buy(GameState state, String shipId, String bodyId, String materialId, double kg) {
        if (state == null || shipId == null || bodyId == null || materialId == null || !Double.isFinite(kg) || kg <= 0) return state;
        var fleet = state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
        if (fleet == null || !ShipSupplyTransfer.stationary(fleet) || fleet.hasInterstellarOrder() || fleet.location().inTransit()) return state;
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
        var design = state.shipDesigns().stream().filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
        if (design == null || !ship.ownerEntityId().equals(fleet.ownerEntityId()) || ship.supplyState().order() != null) return state;
        Map<String, Double> stock = new HashMap<>(ship.supplyState().materialsKg());
        stock.merge(materialId, kg, Double::sum);
        if (!ShipSupplyCatalog.fits(design, stock)) return state;
        String systemId = ConstructionMaterials.systemForBody(state, bodyId);
        if (systemId == null) systemId = state.orbitalStations().stream().filter(station -> station.id().equals(bodyId))
                .map(station -> station.systemId()).findFirst().orElse(null);
        if (!fleet.currentSystemId().equals(systemId)) return state;
        boolean orbit = fleet.location().isAt(FleetLocation.Site.orbit(bodyId)) || state.orbitalStations().stream()
                .anyMatch(station -> bodyId.equals(station.planetOrbitId()) && fleet.location().isAt(FleetLocation.Site.docked(station.id())));
        if (!orbit && !fleet.location().isAt(FleetLocation.Site.surface(bodyId)) && !fleet.location().isAt(FleetLocation.Site.docked(bodyId))) return state;
        var purchase = ConstructionMaterials.buyUpTo(state, bodyId, ship.ownerEntityId(), Map.of(materialId, kg));
        if (!purchase.covers(Map.of(materialId, kg))) return state;
        GameState paid = purchase.state();
        if (orbit) {
            var launch = LaunchService.choose(paid, bodyId, ship.ownerEntityId(), kg, false, false, 0);
            if (launch == null) return state;
            paid = LaunchService.settle(paid, ship.ownerEntityId(), launch);
        }
        return ShipPowerResupply.replace(paid, ship.withSupplyState(ship.supplyState().withMaterials(stock)));
    }
    public static ShipInstance withdraw(ShipInstance ship, ShipDesign design, Map<String, Double> materials) {
        if (!ShipSupplyCatalog.fits(design, ship.supplyState().materialsKg()) || ShipSupplyCatalog.transferKgPerHour(design) <= 0) return null;
        var stock = new HashMap<>(ship.supplyState().materialsKg());
        for (var entry : materials.entrySet()) {
            if (stock.getOrDefault(entry.getKey(), 0.0) + 1e-8 < entry.getValue()) return null;
            double remaining = Math.max(0, stock.getOrDefault(entry.getKey(), 0.0) - entry.getValue());
            if (remaining == 0) stock.remove(entry.getKey()); else stock.put(entry.getKey(), remaining);
        }
        return ship.withSupplyState(ship.supplyState().withMaterials(stock));
    }
    /** Pure electrical delivery used only after the caller establishes contact and reserve safety. */
    public static ShipInstance electricalReceiver(ShipInstance ship, ShipDesign design, String fuelId, double kg) {
        var profile = design.powerProfile();
        var fuel = profile == null ? null : profile.fuels().get(fuelId);
        if (fuel == null || !Double.isFinite(kg) || kg <= 0) return null;
        boolean reactor = fuel.oxidizerId() == null;
        if (reactor ? profile.fissionKw() <= 0 : profile.chemicalKw() <= 0) return null;
        var power = ShipPowerProcessor.reserves(ship);
        double occupied = power.generatorMaterialsKg().entrySet().stream().filter(entry -> profile.fuels().values().stream()
                .filter(item -> (item.oxidizerId() == null) == reactor).anyMatch(item -> entry.getKey().equals(item.materialId())
                        || entry.getKey().equals(item.oxidizerId()))).mapToDouble(Map.Entry::getValue).sum();
        if (occupied > 0 && !fuelId.equals(reactor ? power.reactorFuel() : power.chemicalMixture())
                || occupied + kg > (reactor ? profile.reactorTankKg() : profile.generatorTankKg())) return null;
        var materials = new HashMap<>(power.generatorMaterialsKg());
        fuel.materials(kg).forEach((id, amount) -> materials.merge(id, amount, Double::sum));
        return ship.withPowerState(new ShipPowerState(materials, reactor ? power.chemicalMixture() : fuelId,
                reactor ? fuelId : power.reactorFuel(), power.batteryChargeKwh(), power.arraysDeployed(), power.arrayCondition(),
                power.orientationFraction(), power.unmetEssentialHours(), power.unmetDriveKwh(), power.unmetCargoKwh(),
                power.lastUnmetEssentialKwh(), power.chargedInputKwhToday(), power.cargoPreservation(), power.rescueStatus()));
    }
}
