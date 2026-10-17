package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Paid transfer of goods already at the ship's docked orbital hub. */
public record LoadDockedCargoCommand(String shipId, String stationId, String materialId, double quantityKg) implements GameCommand {
    private Fleet fleet(GameState state) { return state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null); }
    @Override public boolean validate(GameState state) {
        if (com.spaceconquest.engine.ship.ShipRefitPlan.reserved(state, shipId)) return false;
        if (state == null || shipId == null || stationId == null || materialId == null || !Double.isFinite(quantityKg) || quantityKg <= 0) return false;
        var fleet = fleet(state);
        if (fleet == null || fleet.hasInterstellarOrder() || fleet.location().inTransit() || !fleet.location().stationaryAt(FleetLocation.Site.docked(stationId))
                || state.orbitalStations().stream().noneMatch(station -> stationId.equals(station.id()) && fleet.currentSystemId().equals(station.systemId()) && station.isOperational())) return false;
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow(); var design = FleetSupplySimulation.design(state, ship);
        if (!fleet.ownerEntityId().equals(ship.ownerEntityId()) || design == null || !ship.ownerEntityId().equals(design.ownerEntityId())
                || ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum() + ship.passengerCount() * 80.0 + quantityKg > design.maxCargoMassKg()) return false;
        return ConstructionMaterials.buyUpTo(state, stationId, ship.ownerEntityId(), Map.of(materialId, quantityKg)).covers(Map.of(materialId, quantityKg));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var fleet = fleet(state); var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
        var paid = ConstructionMaterials.buyUpTo(state, stationId, ship.ownerEntityId(), Map.of(materialId, quantityKg)).state();
        var cargo = new HashMap<>(ship.storedCargoKg()); cargo.merge(materialId, quantityKg, Double::sum);
        var loaded = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(), cargo,
                ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
        return FleetReturnTravel.replace(paid, fleet.withShips(fleet.ships().stream().map(item -> shipId.equals(item.id()) ? loaded : item).toList()));
    }
}
