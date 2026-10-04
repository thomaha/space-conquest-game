package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.logistics.LaunchService;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Purchases local surface stock and lifts it into an owned orbital transport. */
public record LoadOrbitalCargoCommand(String shipId, String sourceBodyId,
                                      String materialId, double quantityKg,
                                      LaunchService.Mode requiredMode,
                                      String requiredProviderId) implements GameCommand {
    public LoadOrbitalCargoCommand(String shipId, String sourceBodyId,
                                   String materialId, double quantityKg) {
        this(shipId, sourceBodyId, materialId, quantityKg, null, null);
    }

    public LoadOrbitalCargoCommand(String shipId, String sourceBodyId,
                                   String materialId, double quantityKg,
                                   LaunchService.Mode requiredMode) {
        this(shipId, sourceBodyId, materialId, quantityKg, requiredMode, null);
    }
    @Override
    public boolean validate(GameState state) {
        if (state == null || shipId == null || sourceBodyId == null || materialId == null
                || !Double.isFinite(quantityKg) || quantityKg <= 0.0) return false;
        Fleet fleet = fleet(state);
        ShipInstance ship = ship(fleet);
        if (ship == null || fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || !aboveSource(state, fleet)
                || !fleet.ownerEntityId().equals(ship.ownerEntityId())
                || !ConstructionMaterials.isOrbitalTransport(state, ship)
                || !fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state,
                sourceBodyId)) || ConstructionMaterials.bodyForSystem(state,
                fleet.currentSystemId(), sourceBodyId) == null) return false;
        ShipDesign design = state.shipDesigns().stream()
                .filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
        double loaded = ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        if (design == null || design.maxCargoMassKg()
                < loaded + ship.passengerCount() * 80.0 + quantityKg) return false;
        ConstructionMaterials.Purchase purchase = ConstructionMaterials.buyUpTo(state,
                sourceBodyId, ship.ownerEntityId(), Map.of(materialId, quantityKg));
        if (!purchase.covers(Map.of(materialId, quantityKg))) return false;
        return LaunchService.choose(purchase.state(), sourceBodyId,
                ship.ownerEntityId(), quantityKg, false, false, 0.0,
                requiredMode, requiredProviderId) != null;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        Fleet fleet = fleet(state);
        ShipInstance ship = ship(fleet);
        ShipDesign design = state.shipDesigns().stream()
                .filter(item -> item.id().equals(ship.designId())).findFirst().orElseThrow();
        ConstructionMaterials.Purchase purchase = ConstructionMaterials.buyUpTo(state,
                sourceBodyId, ship.ownerEntityId(), Map.of(materialId, quantityKg));
        LaunchService.Plan launch = LaunchService.choose(purchase.state(), sourceBodyId,
                ship.ownerEntityId(), quantityKg, false, false, 0.0,
                requiredMode, requiredProviderId);
        GameState paid = LaunchService.settle(purchase.state(), ship.ownerEntityId(), launch);
        List<Fleet> fleets = new ArrayList<>(paid.fleets());
        int fleetIndex = fleets.indexOf(fleet);
        List<ShipInstance> ships = new ArrayList<>(fleet.ships());
        Map<String, Double> cargo = new HashMap<>(ship.storedCargoKg());
        cargo.merge(materialId, quantityKg, Double::sum);
        ships.set(ships.indexOf(ship), new ShipInstance(ship.id(), ship.designId(),
                ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg(), Map.copyOf(cargo), ship.passengerCount(),
                ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState()));
        fleets.set(fleetIndex, fleet.withShips(ships));
        return paid.withFleets(fleets);
    }

    private Fleet fleet(GameState state) {
        return state.fleets().stream().filter(item -> item.ships().stream()
                .anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
    }

    private ShipInstance ship(Fleet fleet) {
        return fleet == null ? null : fleet.ships().stream()
                .filter(item -> shipId.equals(item.id())).findFirst().orElse(null);
    }

    private boolean aboveSource(GameState state, Fleet fleet) {
        if (fleet.location().isAt(FleetLocation.Site.orbit(sourceBodyId))) return true;
        return state.orbitalStations().stream().anyMatch(station ->
                sourceBodyId.equals(station.planetOrbitId())
                        && fleet.location().isAt(FleetLocation.Site.docked(station.id())));
    }

}
