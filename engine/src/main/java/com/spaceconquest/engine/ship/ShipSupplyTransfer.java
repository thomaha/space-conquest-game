package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Provisional, lossless resupply at a shared site or coasting contact. Quantities are total mixture mass. */
public final class ShipSupplyTransfer {
    public enum Source {
        CARGO, TANK, SUPPLY_TANK;
        @Override public String toString() { return switch (this) {
            case CARGO -> "Cargo stock"; case TANK -> "Working fuel tank"; case SUPPLY_TANK -> "Dedicated supply tanks";
        }; }
    }
    public enum Destination { ELECTRICAL_FUEL, PROPELLANT, DRIVE_REACTOR }

    private ShipSupplyTransfer() {}

    public static GameState transfer(GameState state, String donorId, String receiverId, Source source,
                                     Destination destination, String fuelId, double kg) {
        if (state == null || donorId == null || receiverId == null || donorId.equals(receiverId)
                || source == null || destination == null || fuelId == null || !Double.isFinite(kg) || kg <= 0)
            return state;
        Fleet donorFleet = fleet(state, donorId), receiverFleet = fleet(state, receiverId);
        if (donorFleet == null || receiverFleet == null || !coLocated(donorFleet, receiverFleet)) return state;
        if (!RescueRendezvous.contact(donorFleet, receiverFleet) && !knownSite(state, donorFleet)) return state;
        ShipInstance donor = ship(donorFleet, donorId), receiver = ship(receiverFleet, receiverId);
        if (!Objects.equals(donor.ownerEntityId(), donorFleet.ownerEntityId())
                || !Objects.equals(receiver.ownerEntityId(), receiverFleet.ownerEntityId())) return state;
        ShipDesign donorDesign = design(state, donor), receiverDesign = design(state, receiver);
        if (donorDesign == null || receiverDesign == null) return state;
        if (donor.supplyState().order() != null || receiver.supplyState().order() != null) return state;
        Map<String, Double> materials;
        ShipInstance supplied;
        switch (destination) {
            case ELECTRICAL_FUEL -> {
                var profile = receiverDesign.powerProfile();
                var fuel = profile == null ? null : profile.fuels().get(fuelId);
                if (fuel == null) return state;
                boolean reactor = fuel.oxidizerId() == null;
                if (reactor ? profile.fissionKw() <= 0 : profile.chemicalKw() <= 0) return state;
                var power = ShipPowerProcessor.reserves(receiver);
                double occupied = compartmentMass(profile, power, reactor);
                String selected = reactor ? power.reactorFuel() : power.chemicalMixture();
                if (!selected.equals(fuelId) && occupied > 0
                        || occupied + kg > (reactor ? profile.reactorTankKg() : profile.generatorTankKg())) return state;
                materials = fuel.materials(kg);
                supplied = receiver.withPowerState(withMaterials(power, add(power.generatorMaterialsKg(), materials),
                        reactor ? power.chemicalMixture() : fuelId, reactor ? fuelId : power.reactorFuel()));
            }
            case PROPELLANT -> {
                var drive = PropulsionCatalog.mainDrive(receiverDesign.equippedModuleIds());
                if (drive == null || !drive.moduleId().equals(fuelId) || !validFuel(receiver)
                        || receiver.currentFuelKg() + kg > receiverDesign.fuelCapacityKg()) return state;
                materials = drive.propellantMaterials(kg);
                supplied = copy(receiver, receiver.currentFuelKg() + kg, receiver.storedCargoKg());
            }
            case DRIVE_REACTOR -> {
                var drive = PropulsionCatalog.mainDrive(receiverDesign.equippedModuleIds());
                if (drive == null || PropulsionCatalog.reactorFuel(drive.moduleId(), fuelId) == null
                        || source == Source.TANK || !validStock(receiver.storedCargoKg())
                        || mass(receiver.storedCargoKg()) + kg > receiverDesign.maxCargoMassKg()) return state;
                materials = Map.of(fuelId, kg);
                supplied = copy(receiver, receiver.currentFuelKg(), add(receiver.storedCargoKg(), materials));
            }
            default -> throw new IllegalStateException("Unknown supply destination");
        }
        ShipInstance depleted = withdraw(donor, donorDesign, source, destination, fuelId, kg, materials);
        if (depleted == null) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.withShips(fleet.ships().stream()
                .map(item -> item.id().equals(donorId) ? depleted : item.id().equals(receiverId) ? supplied : item)
                .toList())).toList());
    }

    /** Physical transfer requires a shared stationary site or velocity-matched corridor contact. */
    public static boolean coLocated(Fleet donor, Fleet receiver) {
        return Objects.equals(donor.ownerEntityId(), receiver.ownerEntityId())
                && (RescueRendezvous.contact(donor, receiver) || stationary(donor) && stationary(receiver)
                && Objects.equals(donor.currentSystemId(), receiver.currentSystemId())
                && donor.location().current().equals(receiver.location().current()));
    }

    /** Interrupted local journeys with zero progress are still at their departure site. */
    public static boolean stationary(Fleet fleet) {
        if (fleet.isInWarp() || fleet.transitProgress() != 0 || fleet.interstellarElapsedDays() != 0
                || fleet.flightMotion() != null) return false;
        boolean interrupted = Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode());
        return (!fleet.hasInterstellarOrder() || interrupted)
                && (!fleet.location().inTransit() || interrupted && fleet.location().progress() == 0);
    }

    private static boolean knownSite(GameState state, Fleet fleet) {
        if (state.solarSystems().stream().noneMatch(system -> system.id().equals(fleet.currentSystemId()))) return false;
        var site = fleet.location().current();
        return switch (site.kind()) {
            case SURFACE, ORBIT -> fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state, site.entityId()));
            case DOCKED -> state.orbitalStations().stream().anyMatch(station -> station.id().equals(site.entityId())
                    && station.systemId().equals(fleet.currentSystemId()));
            case DEEP_SPACE -> site.equals(FleetLocation.Site.deepSpace())
                    || site.entityId().equals(fleet.currentSystemId() + "_star")
                    || state.megastructures().stream().anyMatch(mega -> mega.systemId().equals(fleet.currentSystemId())
                    && mega.targetCelestialId().equals(site.entityId()));
        };
    }

    private static ShipInstance withdraw(ShipInstance donor, ShipDesign design, Source source,
                                          Destination destination, String fuelId, double kg,
                                          Map<String, Double> materials) {
        if (source == Source.SUPPLY_TANK) return ShipSupplyStorage.withdraw(donor, design, materials);
        if (source == Source.CARGO) {
            var cargo = subtract(donor.storedCargoKg(), materials);
            return cargo == null ? null : copy(donor, donor.currentFuelKg(), cargo);
        }
        if (destination == Destination.PROPELLANT) {
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive == null || !drive.propellantMaterials(kg).equals(materials)
                    || !validFuel(donor) || donor.currentFuelKg() < kg) return null;
            return copy(donor, donor.currentFuelKg() - kg, donor.storedCargoKg());
        }
        var profile = design.powerProfile();
        var fuel = profile == null ? null : profile.fuels().get(fuelId);
        if (fuel == null || !fuel.materials(kg).equals(materials)) return null;
        var power = ShipPowerProcessor.reserves(donor);
        if (!fuelId.equals(fuel.oxidizerId() == null ? power.reactorFuel() : power.chemicalMixture())) return null;
        var remaining = subtract(power.generatorMaterialsKg(), materials);
        return remaining == null ? null : donor.withPowerState(withMaterials(power, remaining,
                power.chemicalMixture(), power.reactorFuel()));
    }

    private static ShipPowerState withMaterials(ShipPowerState power, Map<String, Double> materials,
                                                 String chemical, String reactor) {
        return new ShipPowerState(materials, chemical, reactor, power.batteryChargeKwh(), power.arraysDeployed(),
                power.arrayCondition(), power.orientationFraction(), power.unmetEssentialHours(),
                power.unmetDriveKwh(), power.unmetCargoKwh(), power.lastUnmetEssentialKwh(), power.chargedInputKwhToday(),
                power.cargoPreservation(), power.rescueStatus());
    }

    private static double compartmentMass(ShipPowerProfile profile, ShipPowerState power, boolean reactor) {
        return power.generatorMaterialsKg().entrySet().stream().filter(entry -> profile.fuels().values().stream()
                .filter(fuel -> (fuel.oxidizerId() == null) == reactor)
                .anyMatch(fuel -> entry.getKey().equals(fuel.materialId()) || entry.getKey().equals(fuel.oxidizerId())))
                .mapToDouble(Map.Entry::getValue).sum();
    }

    private static Map<String, Double> add(Map<String, Double> stock, Map<String, Double> materials) {
        var result = new HashMap<>(stock);
        materials.forEach((id, amount) -> result.merge(id, amount, Double::sum));
        return result;
    }

    private static Map<String, Double> subtract(Map<String, Double> stock, Map<String, Double> materials) {
        if (!validStock(stock) || materials.entrySet().stream()
                .anyMatch(entry -> stock.getOrDefault(entry.getKey(), 0.0) < entry.getValue())) return null;
        var result = new HashMap<>(stock);
        materials.forEach((id, amount) -> {
            double remaining = result.get(id) - amount;
            if (remaining == 0) result.remove(id); else result.put(id, remaining);
        });
        return result;
    }

    private static boolean validStock(Map<String, Double> stock) {
        return stock.values().stream().allMatch(value -> Double.isFinite(value) && value >= 0);
    }
    private static boolean validFuel(ShipInstance ship) {
        return Double.isFinite(ship.currentFuelKg()) && ship.currentFuelKg() >= 0;
    }
    private static double mass(Map<String, Double> stock) { return stock.values().stream().mapToDouble(Double::doubleValue).sum(); }
    private static ShipInstance copy(ShipInstance ship, double fuel, Map<String, Double> cargo) {
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                ship.currentShieldHealth(), fuel, cargo, ship.passengerCount(), ship.passengerRaceId(),
                ship.transitMode(), ship.powerState(), ship.supplyState());
    }
    private static Fleet fleet(GameState state, String shipId) {
        return state.fleets().stream().filter(fleet -> fleet.ships().stream().anyMatch(ship -> shipId.equals(ship.id())))
                .findFirst().orElse(null);
    }
    private static ShipInstance ship(Fleet fleet, String id) {
        return fleet.ships().stream().filter(ship -> id.equals(ship.id())).findFirst().orElseThrow();
    }
    private static ShipDesign design(GameState state, ShipInstance ship) {
        return state.shipDesigns().stream().filter(design -> ship.designId().equals(design.id())).findFirst().orElse(null);
    }
}
