package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provisional fleet distribution with explicit selections and protected supplier reserves. */
public final class FleetFuelSharing {
    private static final double EPSILON = 1e-8;
    public record Request(String ownerEntityId, String receiverFleetId, List<String> donorShipIds,
                          List<String> receiverShipIds, ShipSupplyTransfer.Source source,
                          ShipSupplyTransfer.Destination destination, String fuelId,
                          double targetFraction, double donorPropellantReserveFraction) {
        public Request {
            donorShipIds = copy(donorShipIds);
            receiverShipIds = copy(receiverShipIds);
        }
        private static List<String> copy(List<String> ids) {
            return ids == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(ids));
        }
    }
    public record Transfer(String donorShipId, String receiverShipId, double quantityKg) {}
    public record Balance(String shipId, boolean supplier, double propellantKg,
                          double electricalFuelKg, double supplyFuelKg, double cargoKg, double shortfallKg) {}
    public record Plan(List<Transfer> transfers, List<Balance> balances, List<String> explanations) {
        public Plan {
            transfers = List.copyOf(transfers);
            balances = List.copyOf(balances);
            explanations = List.copyOf(explanations);
        }
        public boolean available() { return !transfers.isEmpty(); }
        public double totalKg() { return transfers.stream().mapToDouble(Transfer::quantityKg).sum(); }
    }
    private record Evaluation(GameState state, Plan plan) {}
    private FleetFuelSharing() {}

    public static Plan preview(GameState state, Request request) { return evaluate(state, request).plan(); }

    /** Rebuilds the preview against live state; changed allocations reject the whole command. */
    public static GameState apply(GameState state, Request request, List<Transfer> approvedTransfers) {
        var result = evaluate(state, request);
        return result.plan().available() && result.plan().transfers().equals(approvedTransfers) ? result.state() : state;
    }

    private static Evaluation evaluate(GameState state, Request request) {
        String problem = problem(state, request);
        if (problem != null) return new Evaluation(state, new Plan(List.of(), List.of(), List.of(problem)));
        GameState next = state;
        List<Transfer> transfers = new ArrayList<>();
        List<String> explanations = new ArrayList<>();
        for (String donorId : request.donorShipIds()) {
            var donor = ship(state, donorId);
            var design = design(state, donor);
            if (design.powerProfile() == null) explanations.add(donorId + ": legacy electrical endurance is unknown; this supplier cannot donate.");
            if (donor.currentFuelKg() < design.fuelCapacityKg() * request.donorPropellantReserveFraction())
                explanations.add(donorId + ": propulsion stock is already below the selected reserve; sharing will not reduce it further.");
        }
        for (String receiverId : request.receiverShipIds()) {
            double desired = target(state, receiverId, request);
            if (desired <= 0) {
                explanations.add(receiverId + ": incompatible fuel or no compatible receiving capacity.");
                continue;
            }
            for (String donorId : request.donorShipIds()) {
                double missing = desired - stored(next, receiverId, request);
                if (missing <= EPSILON) break;
                double maximum = Math.min(missing, available(next, donorId, receiverId, request));
                maximum = Math.min(maximum, freeCapacity(next, receiverId, request));
                if (maximum <= EPSILON) continue;
                double quantity = safeQuantity(state, next, donorId, receiverId, request, maximum);
                if (quantity <= EPSILON) continue;
                next = transfer(next, donorId, receiverId, request, quantity);
                transfers.add(new Transfer(donorId, receiverId, quantity));
            }
            double missing = Math.max(0, desired - stored(next, receiverId, request));
            if (missing > EPSILON) explanations.add(receiverId + ": target not reached; stock, compatibility, capacity or supplier reserves limit sharing.");
        }
        List<Balance> balances = new ArrayList<>();
        for (String id : request.donorShipIds()) balances.add(balance(next, id, true, request, 0));
        for (String id : request.receiverShipIds()) balances.add(balance(next, id, false, request,
                Math.max(0, target(state, id, request) - stored(next, id, request))));
        if (transfers.isEmpty()) explanations.add("No fuel can be shared while respecting the selected supplier reserves.");
        return new Evaluation(next, new Plan(transfers, balances, explanations));
    }

    private static String problem(GameState state, Request request) {
        if (state == null || request == null || request.ownerEntityId() == null || request.fuelId() == null
                || request.source() == null || request.destination() == null) return "A complete fuel sharing request is required.";
        if (!Double.isFinite(request.targetFraction()) || request.targetFraction() <= 0 || request.targetFraction() > 1
                || !Double.isFinite(request.donorPropellantReserveFraction()) || request.donorPropellantReserveFraction() < 0
                || request.donorPropellantReserveFraction() > 1) return "Fill and reserve percentages must be within their allowed ranges.";
        if (!validIds(request.donorShipIds()) || !validIds(request.receiverShipIds())) return "Select distinct suppliers and receivers.";
        if (request.donorShipIds().stream().anyMatch(request.receiverShipIds()::contains)) return "A ship cannot supply and receive in the same request.";
        Fleet receiverFleet = state.fleets().stream().filter(fleet -> Objects.equals(fleet.id(), request.receiverFleetId()))
                .findFirst().orElse(null);
        if (receiverFleet == null || !request.ownerEntityId().equals(receiverFleet.ownerEntityId())) return "Select an owned receiving fleet.";
        if (state.fleets().stream().filter(fleet -> Objects.equals(fleet.id(), request.receiverFleetId())).count() != 1)
            return "Fleet membership is ambiguous.";
        List<String> all = new ArrayList<>(request.donorShipIds());
        all.addAll(request.receiverShipIds());
        for (String id : all) {
            if (state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> Objects.equals(id, ship.id())).count() != 1)
                return "Selected ship membership changed or is ambiguous.";
            Fleet fleet = fleet(state, id);
            ShipInstance ship = ship(state, id);
            if (!request.ownerEntityId().equals(fleet.ownerEntityId()) || !request.ownerEntityId().equals(ship.ownerEntityId()))
                return "All selected ships must share the receiving fleet's owner.";
            if (request.receiverShipIds().contains(id) && !fleet.id().equals(receiverFleet.id())) return "Receivers must belong to the selected fleet.";
            if (!ShipSupplyTransfer.coLocated(fleet, receiverFleet)) return "Sharing requires a stationary shared site or verified coasting contact.";
            var design = design(state, ship);
            if (design == null || !validStock(ship) || !Double.isFinite(design.fuelCapacityKg()) || design.fuelCapacityKg() < 0
                    || !Double.isFinite(design.maxCargoMassKg()) || design.maxCargoMassKg() < 0
                    || ship.currentFuelKg() > design.fuelCapacityKg()) return "A selected ship has unavailable design or invalid reserves.";
            if (ship.supplyState().order() != null) return "Cancel the selected ship's scheduled delivery before manual sharing.";
            if (state.fleets().stream().anyMatch(item -> item.flightMotion() != null && item.flightMotion().trajectory() != null
                    && item.flightMotion().trajectory().rescueOrder() != null
                    && (item.id().equals(fleet.id()) || item.flightMotion().trajectory().rescueOrder().targetFleetId().equals(fleet.id()))))
                return "Pending rescue supplies must remain reserved until contact.";
        }
        if (request.destination() == ShipSupplyTransfer.Destination.DRIVE_REACTOR && request.source() == ShipSupplyTransfer.Source.TANK)
            return "Drive reactor feed must come from cargo or dedicated supply storage.";
        if (request.source() == ShipSupplyTransfer.Source.CARGO && state.tradeRoutes().stream()
                .anyMatch(route -> route.isActive() && route.assignedFreighterIds().stream().anyMatch(request.donorShipIds()::contains)))
            return "Assigned trade carriers cannot donate their freight; cancel the route first.";
        return null;
    }

    private static boolean validIds(List<String> ids) {
        return !ids.isEmpty() && ids.stream().noneMatch(id -> id == null || id.isBlank()) && new HashSet<>(ids).size() == ids.size();
    }
    private static boolean validStock(ShipInstance ship) {
        return Double.isFinite(ship.currentFuelKg()) && ship.currentFuelKg() >= 0
                && ship.storedCargoKg().values().stream().allMatch(value -> Double.isFinite(value) && value >= 0)
                && ShipPowerProcessor.reserves(ship).generatorMaterialsKg().values().stream().allMatch(value -> Double.isFinite(value) && value >= 0);
    }
    private static double safeQuantity(GameState original, GameState state, String donor, String receiver, Request request, double maximum) {
        if (safe(original, state, donor, receiver, request, maximum)) return maximum;
        double low = 0, high = maximum;
        for (int step = 0; step < 40; step++) {
            double middle = (low + high) / 2;
            if (safe(original, state, donor, receiver, request, middle)) low = middle;
            else high = middle;
        }
        return low;
    }
    private static boolean safe(GameState original, GameState state, String donorId, String receiverId, Request request, double kg) {
        GameState next = transfer(state, donorId, receiverId, request, kg);
        if (next == state) return false;
        ShipInstance before = ship(original, donorId), after = ship(next, donorId);
        ShipDesign design = design(original, before);
        if (design.powerProfile() == null) return false; // Unknown electrical endurance cannot protect a supplier.
        double floor = Math.min(before.currentFuelKg(), Math.max(design.fuelCapacityKg() * request.donorPropellantReserveFraction(),
                fleet(original, donorId).fuelPolicy().reserveKg(original, fleet(original, donorId), before)));
        if (after.currentFuelKg() < floor) return false;
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive == null) return false;
        var feed = PropulsionCatalog.preferredReactorFuel(drive, before);
        if (feed != null && feed.kgPerPropellantKg() > 0) {
            double protectedKg = Math.min(before.storedCargoKg().getOrDefault(feed.materialId(), 0.0),
                    after.currentFuelKg() * feed.kgPerPropellantKg());
            if (after.storedCargoKg().getOrDefault(feed.materialId(), 0.0) < protectedKg) return false;
        }
        Fleet donorFleet = fleet(next, donorId);
        var environment = ShipArrivalReserve.environment(next, donorFleet, donorFleet.location().current(), donorFleet.hasInterstellarOrder());
        var profile = design.powerProfile();
        if (!ShipArrivalReserve.check(profile, ShipPowerProcessor.reserves(after), profile.essentialKw(before, design),
                ShipPowerProcessor.cargoKw(profile, before, design), environment).ready()) return false;
        if (donorFleet.hasInterstellarOrder() || donorFleet.location().inTransit()) {
            // Interrupted donors must still support their own physical or prepaid local recovery.
            if (donorFleet.flightMotion() == null) return false;
            if (!FlightRecoveryReadiness.check(next, donorFleet.withShips(List.of(after))).ready()) return false;
        }
        return true;
    }

    private static GameState transfer(GameState state, String donor, String receiver, Request request, double kg) {
        return ShipSupplyTransfer.transfer(state, donor, receiver, request.source(), request.destination(), request.fuelId(), kg);
    }
    private static double target(GameState state, String id, Request request) {
        ShipInstance ship = ship(state, id);
        ShipDesign design = design(state, ship);
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        return switch (request.destination()) {
            case PROPELLANT -> drive != null && drive.moduleId().equals(request.fuelId()) ? design.fuelCapacityKg() * request.targetFraction() : 0;
            case ELECTRICAL_FUEL -> {
                var profile = design.powerProfile();
                var fuel = profile == null ? null : profile.fuels().get(request.fuelId());
                var power = ShipPowerProcessor.reserves(ship);
                if (fuel != null && compartmentMass(state, ship, request.fuelId()) > 0
                        && !request.fuelId().equals(fuel.oxidizerId() == null ? power.reactorFuel() : power.chemicalMixture())) yield 0;
                yield fuel == null ? 0 : (fuel.oxidizerId() == null
                        ? profile.fissionKw() > 0 ? profile.reactorTankKg() : 0
                        : profile.chemicalKw() > 0 ? profile.generatorTankKg() : 0) * request.targetFraction();
            }
            case DRIVE_REACTOR -> {
                var feed = drive == null ? null : PropulsionCatalog.reactorFuel(drive.moduleId(), request.fuelId());
                yield feed == null ? 0 : design.fuelCapacityKg() * feed.kgPerPropellantKg() * request.targetFraction();
            }
        };
    }
    private static double stored(GameState state, String id, Request request) {
        var ship = ship(state, id);
        return switch (request.destination()) {
            case PROPELLANT -> ship.currentFuelKg();
            case DRIVE_REACTOR -> ship.storedCargoKg().getOrDefault(request.fuelId(), 0.0);
            case ELECTRICAL_FUEL -> compartmentMass(state, ship, request.fuelId());
        };
    }
    private static double freeCapacity(GameState state, String id, Request request) {
        if (request.destination() != ShipSupplyTransfer.Destination.DRIVE_REACTOR) return Double.POSITIVE_INFINITY;
        var ship = ship(state, id);
        return Math.max(0, design(state, ship).maxCargoMassKg() - ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum());
    }
    private static double compartmentMass(GameState state, ShipInstance ship, String fuelId) {
        var profile = design(state, ship).powerProfile();
        var fuel = profile == null ? null : profile.fuels().get(fuelId);
        if (fuel == null) return 0;
        return ShipPowerProcessor.reserves(ship).generatorMaterialsKg().entrySet().stream()
                .filter(entry -> profile.fuels().values().stream().filter(item -> (item.oxidizerId() == null) == (fuel.oxidizerId() == null))
                        .anyMatch(item -> entry.getKey().equals(item.materialId()) || entry.getKey().equals(item.oxidizerId())))
                .mapToDouble(Map.Entry::getValue).sum();
    }
    private static Map<String, Double> materials(GameState state, String id, Request request) {
        var design = design(state, ship(state, id));
        return switch (request.destination()) {
            case PROPELLANT -> {
                var drive = PropulsionCatalog.drive(request.fuelId());
                yield drive == null ? Map.of() : drive.propellantMaterials(1);
            }
            case DRIVE_REACTOR -> Map.of(request.fuelId(), 1.0);
            case ELECTRICAL_FUEL -> {
                var fuel = design.powerProfile() == null ? null : design.powerProfile().fuels().get(request.fuelId());
                yield fuel == null ? Map.of() : fuel.materials(1);
            }
        };
    }
    private static double available(GameState state, String donorId, String receiverId, Request request) {
        ShipInstance donor = ship(state, donorId);
        if (request.source() == ShipSupplyTransfer.Source.TANK) return request.destination() == ShipSupplyTransfer.Destination.PROPELLANT
                ? donor.currentFuelKg() : compartmentMass(state, donor, request.fuelId());
        // Material ratios come from the receiver's equipment, not the supplier's own drive.
        return cargoFuel(state, donor, receiverId, request);
    }
    private static double cargoFuel(GameState state, ShipInstance donor, String receiverId, Request request) {
        Map<String, Double> materials = materials(state, receiverId, request);
        if (materials.isEmpty()) return 0;
        var stock = request.source() == ShipSupplyTransfer.Source.SUPPLY_TANK ? donor.supplyState().materialsKg() : donor.storedCargoKg();
        return materials.entrySet().stream().mapToDouble(entry -> stock.getOrDefault(entry.getKey(), 0.0) / entry.getValue())
                .min().orElse(0);
    }
    private static Balance balance(GameState state, String id, boolean supplier, Request request, double shortfall) {
        var ship = ship(state, id);
        return new Balance(id, supplier, ship.currentFuelKg(), ship.generatorFuelMassKg(), ship.supplyFuelMassKg(),
                ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum(), shortfall);
    }
    private static ShipInstance ship(GameState state, String id) {
        return fleet(state, id).ships().stream().filter(ship -> ship.id().equals(id)).findFirst().orElseThrow();
    }
    private static Fleet fleet(GameState state, String id) {
        return state.fleets().stream().filter(fleet -> fleet.ships().stream().anyMatch(ship -> ship.id().equals(id))).findFirst().orElseThrow();
    }
    private static ShipDesign design(GameState state, ShipInstance ship) {
        return state.shipDesigns().stream().filter(design -> design.id().equals(ship.designId())).findFirst().orElse(null);
    }
}
