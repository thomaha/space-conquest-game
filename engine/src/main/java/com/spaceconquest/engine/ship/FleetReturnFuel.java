package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.List;

/** Reuses lossless compatible sharing and verifies donors can still fund their own return. */
public final class FleetReturnFuel {
    private FleetReturnFuel() {}
    public static GameState share(GameState state, Fleet fleet, FleetLocation.Site destination) {
        if (!ShipSupplyTransfer.stationary(fleet) && !RecoveredParkingTravel.ready(fleet)) return state;
        var result = state;
        for (var receiver : fleet.ships()) {
            var candidate = result;
            for (var donor : fleet.ships()) {
                if (donor.id().equals(receiver.id())) continue;
                var current = FleetOrganization.find(candidate, fleet.id());
                var receiving = current.ships().stream().filter(ship -> ship.id().equals(receiver.id())).findFirst().orElseThrow();
                if (FleetReturnTravel.ready(candidate, current.withShips(List.of(receiving)), destination)) { result = candidate; break; }
                var supplied = supply(candidate, current, donor.id(), receiving, ShipSupplyTransfer.Source.TANK, destination);
                var suppliedFleet = FleetOrganization.find(supplied, fleet.id());
                var donorAfter = suppliedFleet.ships().stream().filter(ship -> ship.id().equals(donor.id())).findFirst().orElseThrow();
                var receiverAfter = suppliedFleet.ships().stream().filter(ship -> ship.id().equals(receiver.id())).findFirst().orElseThrow();
                if (FleetReturnTravel.ready(supplied, suppliedFleet.withShips(List.of(donorAfter)), destination)) candidate = supplied;
                if (candidate == supplied && FleetReturnTravel.ready(candidate, suppliedFleet.withShips(List.of(receiverAfter)), destination)) {
                    result = candidate; break;
                }
            }
        }
        return result;
    }
    public static GameState supply(GameState state, Fleet receiverFleet, String donorId, ShipInstance receiver,
                                    ShipSupplyTransfer.Source source, FleetLocation.Site destination) {
        var design = FleetSupplySimulation.design(state, receiver);
        if (design == null) return state;
        var current = state;
        var power = design.powerProfile();
        if (power != null && !Double.isFinite(FleetReturnTravel.minimumFuel(state, receiverFleet, receiver, destination)))
            for (String fuel : List.of(ShipPowerProcessor.reserves(receiver).chemicalMixture(), ShipPowerProcessor.reserves(receiver).reactorFuel()))
            current = transfer(current, receiverFleet, donorId, receiver.id(), source, ShipSupplyTransfer.Destination.ELECTRICAL_FUEL, fuel, 1, .2);
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive == null) return current;
        var actualFleet = FleetOrganization.find(current, receiverFleet.id());
        var actualReceiver = actualFleet.ships().stream().filter(ship -> ship.id().equals(receiver.id())).findFirst().orElseThrow();
        double needed = FleetReturnTravel.minimumFuel(current, actualFleet, actualReceiver, destination);
        double fraction = Double.isFinite(needed) ? needed / Math.max(1, design.fuelCapacityKg()) : 1;
        double donorReserve = .2;
        var donorFleet = current.fleets().stream().filter(fleet -> fleet.ships().stream().anyMatch(ship -> ship.id().equals(donorId))).findFirst().orElseThrow();
        if (donorFleet.id().equals(receiverFleet.id())) {
            var donor = donorFleet.ships().stream().filter(ship -> ship.id().equals(donorId)).findFirst().orElseThrow();
            var donorDesign = FleetSupplySimulation.design(current, donor);
            double floor = FleetReturnTravel.minimumFuel(current, donorFleet, donor, destination);
            donorReserve = Double.isFinite(floor) ? Math.min(1, floor / Math.max(1, donorDesign.fuelCapacityKg())) : 1;
        }
        current = transfer(current, receiverFleet, donorId, receiver.id(), source, ShipSupplyTransfer.Destination.PROPELLANT,
                drive.moduleId(), Math.max(1e-6, Math.min(1, fraction)), donorReserve);
        var feedSource = source == ShipSupplyTransfer.Source.TANK ? ShipSupplyTransfer.Source.CARGO : source;
        for (var feed : PropulsionCatalog.reactorFuels(drive.moduleId())) current = transfer(current, receiverFleet, donorId, receiver.id(),
                feedSource, ShipSupplyTransfer.Destination.DRIVE_REACTOR, feed.materialId(), Math.max(1e-6, Math.min(1, fraction)), donorReserve);
        return current;
    }
    private static GameState transfer(GameState state, Fleet receiver, String donor, String ship,
                                      ShipSupplyTransfer.Source source, ShipSupplyTransfer.Destination destination,
                                      String fuel, double target, double reserve) {
        var request = new FleetFuelSharing.Request(receiver.ownerEntityId(), receiver.id(), List.of(donor), List.of(ship), source,
                destination, fuel, target, reserve);
        var plan = FleetFuelSharing.preview(state, request);
        return FleetFuelSharing.apply(state, request, plan.transfers());
    }
}
