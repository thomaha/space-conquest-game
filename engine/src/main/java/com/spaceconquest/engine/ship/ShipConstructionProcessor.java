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
    public static final double COMMISSIONED_HULL_HEALTH = 1000;
    public GameState process(GameState state) {
        return process(state, null);
    }

    public GameState process(GameState state, Map<String, Integer> paidWorkersByFacility) {
        GameState current = state.toBuilder().shipyardLedger(state.shipyardLedger().payroll(state)).build();
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
                current = ShipyardAccounting.record(current, order, 0, 0, false, "Blueprint unavailable");
                remaining.add(order);
                continue;
            }
            String provider = ShipyardAccess.provider(order);
            String refitProblem = order.retirement() != null ? order.retirement().problem(current, order, design)
                    : order.refit() == null ? null : order.refit().problem(current, order, design);
            if (refitProblem != null) {
                current = ShipyardAccounting.record(current, order, 0, 0, false, refitProblem);
                remaining.add(order);
                continue;
            }
            if (order.yardContract() != null && !ShipyardAccess.permitted(current, order.ownerEntityId(), provider, order.systemId())
                    || ShipManufacturingCapacity.forYard(current, provider, order.systemId(), order.yardBodyId())
                    < ShipManufacturingCapacity.requiredComplexity(design)) {
                current = ShipyardAccounting.record(current, order, 0, 0, false, "Yard access or manufacturing capacity unavailable");
                remaining.add(order);
                continue;
            }
            boolean orbital = current.orbitalStations().stream()
                    .anyMatch(station -> order.yardBodyId().equals(station.id())
                            && order.systemId().equals(station.systemId()));
            String yardKey = order.systemId() + "/" + order.yardBodyId();
            Double availableCapacity = remainingDailyCapacity.get(yardKey);
            if (availableCapacity == null) {
                availableCapacity = ShipyardWorkCapacity.forYard(current, provider,
                        order.systemId(), order.yardBodyId(), paidWorkersByFacility).workPerDay();
            }
            double dailyWork = availableCapacity;
            var progress = ContractShipConstruction.advance(current, order, dailyWork, orbital);
            ConstructionProgress.Step step = progress.progress();
            double usedWork = Math.max(0.0, step.workHours() - order.accumulatedWorkHours());
            remainingDailyCapacity.put(yardKey, Math.max(0.0, dailyWork - usedWork));
            current = step.state();
            double fee = order.yardContract() == null ? 0 : usedWork * order.yardContract().feeCreditsPerWorkUnit();
            String reason = workStatus(order, step.complete(), usedWork, dailyWork);
            current = ShipyardAccounting.record(current, order, usedWork, fee, step.complete(), reason);
            fleets = new ArrayList<>(current.fleets());
            if (step.complete()) {
                if (order.retirement() != null) {
                    current = order.retirement().complete(current, order, design);
                    fleets = new ArrayList<>(current.fleets());
                }
                else if (order.refit() == null) commission(fleets, order, orbital, design);
                else {
                    GameState completionState = current;
                    fleets.replaceAll(fleet -> order.refit().complete(completionState, fleet, design));
                }
                if (order.deployment() != null) current = current.toBuilder().shipyardLedger(current.shipyardLedger()
                        .deployment(shipId(order), order.deployment().status("Awaiting paid deployment"))).build();
                if (order.replacement() != null) current = current.toBuilder().shipyardLedger(current.shipyardLedger()
                        .replacement(order.replacement().sourceShipId(), shipId(order))).build();
                current = current.withFleets(fleets);
                String sourceBodyId = order.yardBodyId();
                if (order.refit() == null && order.retirement() == null && sourceBodyId != null && PropulsionCatalog.mainDrive(
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
                        order.requiredMaterialsKg(), step.consumedKg(), progress.contract(), order.priority() == null ? null : usedWork > 0 ? order.priority().worked() : order.priority(), order.deployment(), order.refit(), order.replacement(), order.retirement()));
            }
        }
        List<Corporation> corporations = current.corporations().stream().map(corporation -> {
            List<String> shipIds = new ArrayList<>(corporation.ownedShipIds());
            for (ShipConstructionOrder order : completed) {
                if (order.refit() == null && order.retirement() == null && corporation.id().equals(order.ownerEntityId())) shipIds.add(shipId(order));
            }
            return shipIds.size() == corporation.ownedShipIds().size() ? corporation
                    : new Corporation(corporation.id(), corporation.name(), corporation.empireId(),
                    corporation.headquartersEntityId(), corporation.marketOrientation(),
                    corporation.liquidCapitalReserves(), corporation.ownedFacilityIds(),
                    shipIds, corporation.claimedVeinIds());
        }).toList();
        return com.spaceconquest.engine.combat.AuxiliaryConstruction.advance(current.toBuilder().shipConstructionOrders(remaining).fleets(fleets)
                .corporations(corporations).build(), remainingDailyCapacity, paidWorkersByFacility);
    }

    private String workStatus(ShipConstructionOrder order, boolean complete, double usedWork, double dailyWork) {
        if (usedWork <= 0) return dailyWork <= 0 ? "Queued or missing paid workforce" : "Waiting for materials or funds";
        if (order.retirement() != null) return complete ? "Retired and recovered materials sold" : "Paid dismantling";
        if (order.refit() != null) return complete ? "Refit completed" : "Paid refit";
        return complete ? "Commissioned" : "Paid construction";
    }
    private void commission(List<Fleet> fleets, ShipConstructionOrder order, boolean orbital, ShipDesign design) {
        ShipInstance ship = new ShipInstance(shipId(order), order.designId(),
                order.ownerEntityId(), COMMISSIONED_HULL_HEALTH, CombatModuleCatalog.shieldCapacity(design), 0.0, java.util.Map.of());
        if (design.powerProfile() != null) ship = ship.withPowerState(ShipPowerState.empty());
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
