package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.logistics.LaunchService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Purchases drive-specific propellant from a local hub and fills a ship tank. */
public final class ShipFueling {
    private ShipFueling() {}

    public static boolean canRefuel(GameState state, String shipId, String bodyId,
                                    double quantityKg) {
        return refuel(state, shipId, bodyId, quantityKg, null) != state;
    }

    public static boolean canRefuel(GameState state, String shipId, String bodyId,
                                    double quantityKg, String reactorFuelId) {
        return refuel(state, shipId, bodyId, quantityKg, reactorFuelId) != state;
    }

    public static GameState refuel(GameState state, String shipId, String bodyId,
                                   double quantityKg) {
        return refuel(state, shipId, bodyId, quantityKg, null);
    }

    public static GameState refuel(GameState state, String shipId, String bodyId,
                                   double quantityKg, String reactorFuelId) {
        if (state == null || shipId == null || bodyId == null
                || !Double.isFinite(quantityKg) || quantityKg <= 0.0) return state;
        Fleet fleet = state.fleets().stream().filter(item -> item.ships().stream()
                .anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
        String sourceSystemId = ConstructionMaterials.systemForBody(state, bodyId);
        if (sourceSystemId == null) sourceSystemId = state.orbitalStations().stream()
                .filter(station -> bodyId.equals(station.id()))
                .map(station -> station.systemId()).findFirst().orElse(null);
        if (fleet == null || fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || !fleet.currentSystemId().equals(sourceSystemId))
            return state;
        ShipInstance ship = fleet.ships().stream().filter(item -> shipId.equals(item.id()))
                .findFirst().orElse(null);
        ShipDesign design = ship == null ? null : state.shipDesigns().stream()
                .filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
        PropulsionCatalog.Drive drive = design == null ? null
                : PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (ship == null || design == null || drive == null
                || !fleet.ownerEntityId().equals(ship.ownerEntityId())
                || !Double.isFinite(ship.currentFuelKg()) || ship.currentFuelKg() < 0.0
                || ship.currentFuelKg() + quantityKg > design.fuelCapacityKg() + 0.000001)
            return state;
        PropulsionCatalog.ReactorFuel reactorFuel = reactorFuelId == null
                ? PropulsionCatalog.preferredReactorFuel(drive, ship)
                : PropulsionCatalog.reactorFuel(drive.moduleId(), reactorFuelId);
        if (reactorFuelId != null && reactorFuel == null) return state;
        double reactorKg = reactorFuel == null ? 0.0 : Math.max(0.0,
                (ship.currentFuelKg() + quantityKg) * reactorFuel.kgPerPropellantKg()
                        - ship.storedCargoKg().getOrDefault(reactorFuel.materialId(), 0.0));
        double cargoKg = ship.storedCargoKg().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        if (cargoKg + reactorKg > design.maxCargoMassKg() + 0.000001) return state;
        boolean surface = fleet.location().isAt(FleetLocation.Site.surface(bodyId));
        boolean stationHub = fleet.location().isAt(FleetLocation.Site.docked(bodyId));
        boolean orbit = fleet.location().isAt(FleetLocation.Site.orbit(bodyId))
                || state.orbitalStations().stream().anyMatch(station ->
                bodyId.equals(station.planetOrbitId())
                        && fleet.location().isAt(FleetLocation.Site.docked(station.id())));
        if (!surface && !orbit && !stationHub) return state;
        Map<String, Double> request = new HashMap<>();
        request.putAll(drive.propellantMaterials(quantityKg));
        if (reactorKg > 0.0) request.merge(reactorFuel.materialId(), reactorKg, Double::sum);
        ConstructionMaterials.Purchase purchase = ConstructionMaterials.buyUpTo(state,
                bodyId, ship.ownerEntityId(), request);
        if (!purchase.covers(request)) return state;
        GameState paid = purchase.state();
        if (orbit) {
            LaunchService.Plan launch = LaunchService.choose(paid, bodyId,
                    ship.ownerEntityId(), quantityKg + reactorKg, false, false, 0.0);
            if (launch == null) return state;
            paid = LaunchService.settle(paid, ship.ownerEntityId(), launch);
        }
        List<Fleet> fleets = new ArrayList<>(paid.fleets());
        List<ShipInstance> ships = new ArrayList<>(fleet.ships());
        Map<String, Double> cargo = new HashMap<>(ship.storedCargoKg());
        if (reactorKg > 0.0) cargo.merge(reactorFuel.materialId(), reactorKg, Double::sum);
        ships.set(ships.indexOf(ship), new ShipInstance(ship.id(), ship.designId(),
                ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg() + quantityKg, Map.copyOf(cargo),
                ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState()));
        fleets.set(fleets.indexOf(fleet), fleet.withShips(ships));
        return paid.withFleets(fleets);
    }
}
