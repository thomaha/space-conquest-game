package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reservations and membership checks for immutable timed fleet supplies. */
public final class FleetSupplySimulation {
    private FleetSupplySimulation() {}
    public static boolean hasOrders(Fleet fleet) { return fleet.ships().stream().anyMatch(ship -> ship.supplyState().order() != null); }
    public static List<FleetSupplyOrder> orders(Fleet fleet) {
        return fleet.ships().stream().flatMap(ship -> ship.supplyState().orders().stream()).toList();
    }
    public static Map<String, Double> materials(ShipDesign design, FleetSupplyOrder order) {
        return materials(design, order.destination(), order.fuelId(), order.quantityKg());
    }
    private static Map<String, Double> materials(ShipDesign design, ShipSupplyTransfer.Destination destination, String id, double kg) {
        if (design == null || design.powerProfile() == null) return null;
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        return switch (destination) {
            case PROPELLANT -> drive != null && drive.moduleId().equals(id) && kg <= design.fuelCapacityKg()
                    ? drive.propellantMaterials(kg) : null;
            case DRIVE_REACTOR -> drive != null && PropulsionCatalog.reactorFuel(drive.moduleId(), id) != null
                    && PropulsionCatalog.reactorFuel(drive.moduleId(), id).kgPerPropellantKg() > 0 && kg <= design.maxCargoMassKg()
                    ? Map.of(id, kg) : null;
            case ELECTRICAL_FUEL -> {
                var p = design.powerProfile(); var fuel = p.fuels().get(id);
                yield fuel != null && (fuel.oxidizerId() == null ? p.fissionKw() > 0 && kg <= p.reactorTankKg()
                        : p.chemicalKw() > 0 && kg <= p.generatorTankKg()) ? fuel.materials(kg) : null;
            }
        };
    }
    public static GameState configure(GameState state, String supplierId, String receiverId, String fuelId, double kg, double delay) {
        return configure(state, supplierId, receiverId, fuelId, kg, delay, ShipSupplyTransfer.Destination.ELECTRICAL_FUEL);
    }
    public static GameState configure(GameState state, String supplierId, String receiverId, String fuelId, double kg,
                                       double delay, ShipSupplyTransfer.Destination destination) {
        if (state == null || supplierId == null || receiverId == null || fuelId == null || supplierId.equals(receiverId)
                || !Double.isFinite(kg) || kg <= 0 || !Double.isFinite(delay) || delay < 0 || destination == null) return state;
        var fleet = state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> ship.id().equals(supplierId))).findFirst().orElse(null);
        if (fleet == null || fleet.hasInterstellarOrder() || fleet.location().inTransit() || fleet.isInWarp()
                || !fleet.location().isAt(FleetLocation.Site.deepSpace()) || orders(fleet).size() >= 64) return state;
        var supplier = find(fleet, supplierId); var receiver = find(fleet, receiverId);
        if (receiver == null || !fleet.ownerEntityId().equals(supplier.ownerEntityId()) || !fleet.ownerEntityId().equals(receiver.ownerEntityId())
                || state.fleets().stream().flatMap(item -> item.ships().stream())
                .filter(ship -> supplierId.equals(ship.id()) || receiverId.equals(ship.id())).count() != 2) return state;
        var sd = design(state, supplier); var rd = design(state, receiver);
        double rate = ShipSupplyCatalog.transferKgPerHour(sd);
        var requested = materials(rd, destination, fuelId, kg);
        if (sd == null || sd.powerProfile() == null || rate <= 0 || requested == null) return state;
        Map<String, Double> reserved = new HashMap<>(requested);
        for (var existing : supplier.supplyState().orders()) {
            double end = existing.coastDelayHours() + existing.quantityKg() / rate;
            if (delay < end - 1e-9 && existing.coastDelayHours() < delay + kg / rate - 1e-9) return state;
            var target = find(fleet, existing.receiverShipId());
            var stock = target == null ? null : materials(design(state, target), existing);
            if (stock == null) return state;
            stock.forEach((id, quantity) -> reserved.merge(id, quantity, Double::sum));
        }
        if (ShipSupplyStorage.withdraw(supplier, sd, reserved) == null || state.tradeRoutes().stream().anyMatch(route -> route.isActive()
                && route.assignedFreighterIds().stream().anyMatch(id -> supplierId.equals(id) || receiverId.equals(id)))) return state;
        var scheduled = new ArrayList<>(supplier.supplyState().orders());
        scheduled.add(new FleetSupplyOrder(fleet.id(), fleet.ownerEntityId(), supplierId, receiverId,
                fleet.ships().stream().map(ShipInstance::id).sorted().toList(), fuelId, kg, delay, destination));
        return ShipPowerResupply.replace(state, supplier.withSupplyState(supplier.supplyState().withOrders(scheduled,
                "Scheduled coasting delivery; reserved stock covers all pending orders.").withTimeline(null)));
    }
    public static GameState cancel(GameState state, String supplierId) { return cancel(state, supplierId, null); }
    public static GameState cancel(GameState state, String supplierId, FleetSupplyOrder selected) {
        if (state == null || supplierId == null) return state;
        var supplier = state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> supplierId.equals(ship.id())).findFirst().orElse(null);
        if (supplier == null || supplier.supplyState().order() == null || selected != null && !supplier.supplyState().orders().contains(selected)) return state;
        var remaining = selected == null ? List.<FleetSupplyOrder>of() : supplier.supplyState().orders().stream().filter(order -> !order.equals(selected)).toList();
        return ShipPowerResupply.replace(state, supplier.withSupplyState(supplier.supplyState().withOrders(remaining, "Scheduled delivery cancelled.")));
    }
    public static Fleet reconcile(Fleet fleet) {
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            if (ship.supplyState().order() == null) return ship;
            boolean valid = ship.supplyState().orders().stream().allMatch(order -> order.fleetId().equals(fleet.id())
                    && order.ownerEntityId().equals(fleet.ownerEntityId()) && order.supplierShipId().equals(ship.id())
                    && order.ownerEntityId().equals(ship.ownerEntityId())
                    && order.memberShipIds().equals(fleet.ships().stream().map(ShipInstance::id).sorted().toList())
                    && find(fleet, order.receiverShipId()) != null && order.ownerEntityId().equals(find(fleet, order.receiverShipId()).ownerEntityId()))
                    && !Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                    && (!Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()) || fleet.flightMotion().trajectory().rescueOrder() == null);
            return valid ? ship : ship.withSupplyState(ship.supplyState().withOrder(null,
                    "Scheduled delivery cancelled: membership, ownership or itinerary changed."));
        }).toList());
    }
    public static Fleet planned(Fleet fleet, InterstellarTravel.Plan plan) {
        return new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), fleet.currentSystemId(), "forecast_destination", 0, 0, 0,
                false, fleet.fleetStance(), InterstellarTravel.commitReactorFuel(fleet, plan).ships(), fleet.location(), plan.mode(), plan.days(),
                plan.distanceMeters(), plan.accelerationMps2(), 0, plan.peakSpeedMps(), plan.fuelBudgetKg(), null, plan.propulsion());
    }
    public static double totalHours(Fleet fleet) {
        return InterstellarTravel.travelSeconds(fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(), fleet.interstellarPeakSpeedMps()) / 3600;
    }
    private static ShipInstance find(Fleet fleet, String id) { return fleet.ships().stream().filter(ship -> ship.id().equals(id)).findFirst().orElse(null); }
    public static ShipDesign design(GameState state, ShipInstance ship) {
        return state.shipDesigns().stream().filter(design -> design.id().equals(ship.designId())).findFirst().orElse(null);
    }
}
