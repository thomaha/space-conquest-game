package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Buys stock at the surface hub and loads it into a ship already on that body. */
public record LoadSurfaceCargoCommand(String shipId, String bodyId,
                                      String materialId, double quantityKg) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || shipId == null || bodyId == null || materialId == null
                || !Double.isFinite(quantityKg) || quantityKg <= 0) return false;
        Fleet fleet = state.fleets().stream().filter(item -> item.ships().stream()
                .anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
        if (fleet == null || fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || !fleet.location().isAt(FleetLocation.Site.surface(bodyId))
                || !fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state, bodyId)))
            return false;
        ShipInstance ship = fleet.ships().stream().filter(item -> shipId.equals(item.id()))
                .findFirst().orElseThrow();
        if (!fleet.ownerEntityId().equals(ship.ownerEntityId())) return false;
        ShipDesign design = state.shipDesigns().stream()
                .filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
        double loaded = ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        if (design == null || loaded + ship.passengerCount() * 80.0 + quantityKg
                > design.maxCargoMassKg()) return false;
        return ConstructionMaterials.buyUpTo(state, bodyId, ship.ownerEntityId(),
                Map.of(materialId, quantityKg)).covers(Map.of(materialId, quantityKg));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        ConstructionMaterials.Purchase purchase = ConstructionMaterials.buyUpTo(state,
                bodyId, owner(state), Map.of(materialId, quantityKg));
        List<Fleet> fleets = new ArrayList<>(purchase.state().fleets());
        for (int fleetIndex = 0; fleetIndex < fleets.size(); fleetIndex++) {
            Fleet fleet = fleets.get(fleetIndex);
            List<ShipInstance> ships = new ArrayList<>(fleet.ships());
            for (int shipIndex = 0; shipIndex < ships.size(); shipIndex++) {
                ShipInstance ship = ships.get(shipIndex);
                if (!shipId.equals(ship.id())) continue;
                Map<String, Double> cargo = new HashMap<>(ship.storedCargoKg());
                cargo.merge(materialId, quantityKg, Double::sum);
                ships.set(shipIndex, new ShipInstance(ship.id(), ship.designId(),
                        ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                        ship.currentFuelKg(), Map.copyOf(cargo), ship.passengerCount(),
                        ship.passengerRaceId(), ship.transitMode(), ship.powerState()));
                fleets.set(fleetIndex, fleet.withShips(ships));
                return purchase.state().withFleets(fleets);
            }
        }
        return state;
    }

    private String owner(GameState state) {
        return state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                .filter(ship -> shipId.equals(ship.id()))
                .map(ShipInstance::ownerEntityId).findFirst().orElse("");
    }
}
