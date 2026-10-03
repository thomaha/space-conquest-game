package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionProgress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Completes ship orders only after daily work and the entire physical bill are settled. */
public final class ShipConstructionProcessor {
    public GameState process(GameState state) {
        return process(state, null);
    }

    public GameState process(GameState state, Map<String, Integer> paidWorkersByFacility) {
        GameState current = state;
        List<ShipConstructionOrder> remaining = new ArrayList<>();
        List<ShipConstructionOrder> completed = new ArrayList<>();
        List<Fleet> fleets = new ArrayList<>(state.fleets());
        Map<String, Double> remainingDailyCapacity = new HashMap<>();
        for (ShipConstructionOrder order : state.shipConstructionOrders()) {
            current = current.withFleets(fleets);
            ShipDesign design = state.shipDesigns().stream()
                    .filter(item -> order.designId().equals(item.id())
                            && order.ownerEntityId().equals(item.ownerEntityId()))
                    .findFirst().orElse(null);
            if (design == null) {
                remaining.add(order);
                continue;
            }
            if (ShipManufacturingCapacity.forYard(current, order.ownerEntityId(), order.systemId(), order.yardBodyId())
                    < ShipManufacturingCapacity.requiredComplexity(design)) {
                remaining.add(order);
                continue;
            }
            boolean orbital = current.orbitalStations().stream()
                    .anyMatch(station -> order.yardBodyId().equals(station.id())
                            && order.systemId().equals(station.systemId()));
            String yardKey = order.systemId() + "/" + order.yardBodyId();
            Double availableCapacity = remainingDailyCapacity.get(yardKey);
            if (availableCapacity == null) {
                availableCapacity = ShipyardWorkCapacity.forYard(current, order.ownerEntityId(),
                        order.systemId(), order.yardBodyId(), paidWorkersByFacility).workPerDay();
            }
            double dailyWork = availableCapacity;
            ConstructionProgress.Step step = orbital
                    ? ConstructionProgress.advanceOrbital(current, order.systemId(),
                    order.yardBodyId(), order.ownerEntityId(), order.requiredMaterialsKg(),
                    order.consumedMaterialsKg(), order.accumulatedWorkHours(),
                    order.requiredWorkHours(), dailyWork)
                    : ConstructionProgress.advance(current, order.yardBodyId(),
                    order.ownerEntityId(), order.requiredMaterialsKg(),
                    order.consumedMaterialsKg(), order.accumulatedWorkHours(),
                    order.requiredWorkHours(), dailyWork);
            double usedWork = Math.max(0.0, step.workHours() - order.accumulatedWorkHours());
            remainingDailyCapacity.put(yardKey, Math.max(0.0, dailyWork - usedWork));
            current = step.state();
            fleets = new ArrayList<>(current.fleets());
            if (step.complete()) {
                commission(fleets, order, orbital, design.powerProfile() != null);
                current = current.withFleets(fleets);
                String sourceBodyId = orbital ? current.orbitalStations().stream()
                        .filter(station -> order.yardBodyId().equals(station.id()))
                        .map(station -> station.planetOrbitId()).findFirst().orElse(null)
                        : order.yardBodyId();
                if (sourceBodyId != null && PropulsionCatalog.mainDrive(
                        design.equippedModuleIds()) != null) {
                    current = ShipFueling.refuel(current, shipId(order), sourceBodyId,
                            Math.min(100.0, design.fuelCapacityKg()));
                    fleets = new ArrayList<>(current.fleets());
                }
                completed.add(order);
            } else {
                remaining.add(new ShipConstructionOrder(order.id(), order.ownerEntityId(),
                        order.designId(), order.systemId(), order.yardBodyId(),
                        step.workHours(), order.requiredWorkHours(),
                        order.requiredMaterialsKg(), step.consumedKg()));
            }
        }
        List<Corporation> corporations = current.corporations().stream().map(corporation -> {
            List<String> shipIds = new ArrayList<>(corporation.ownedShipIds());
            for (ShipConstructionOrder order : completed) {
                if (corporation.id().equals(order.ownerEntityId())) shipIds.add(shipId(order));
            }
            return shipIds.size() == corporation.ownedShipIds().size() ? corporation
                    : new Corporation(corporation.id(), corporation.name(), corporation.empireId(),
                    corporation.headquartersEntityId(), corporation.marketOrientation(),
                    corporation.liquidCapitalReserves(), corporation.ownedFacilityIds(),
                    shipIds, corporation.claimedVeinIds());
        }).toList();
        return current.toBuilder().shipConstructionOrders(remaining).fleets(fleets)
                .corporations(corporations).build();
    }

    private void commission(List<Fleet> fleets, ShipConstructionOrder order, boolean orbital, boolean electrical) {
        ShipInstance ship = new ShipInstance(shipId(order), order.designId(),
                order.ownerEntityId(), 1000.0, 500.0, 0.0, java.util.Map.of());
        if (electrical) ship = ship.withPowerState(ShipPowerState.empty());
        FleetLocation.Site yard = orbital
                ? FleetLocation.Site.docked(order.yardBodyId())
                : FleetLocation.Site.surface(order.yardBodyId());
        fleets.add(new Fleet("fleet_" + UUID.randomUUID(), "Task Force " + order.systemId(),
                order.ownerEntityId(), order.systemId(), "", 0.0, 0.0, 0.0,
                false, "PASSIVE", List.of(ship), FleetLocation.at(yard)));
    }

    private String shipId(ShipConstructionOrder order) {
        return "ship_" + order.id();
    }
}
