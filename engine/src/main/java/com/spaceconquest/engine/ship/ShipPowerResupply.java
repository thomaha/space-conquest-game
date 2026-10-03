package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.logistics.LaunchService;
import java.util.HashMap;
import java.util.Map;

/** Atomic paid electrical fuel purchases. Does not borrow freight or main-drive reserves. */
public final class ShipPowerResupply {
    private ShipPowerResupply() {}

    public static GameState buy(GameState state, String shipId, String bodyId, String fuelId, double kg) {
        if (state == null || shipId == null || bodyId == null || fuelId == null || !Double.isFinite(kg) || kg <= 0)
            return state;
        Fleet fleet = state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> ship.id().equals(shipId)))
                .findFirst().orElse(null);
        if (fleet == null || fleet.hasInterstellarOrder() || fleet.location().inTransit()) return state;
        ShipInstance ship = fleet.ships().stream().filter(item -> item.id().equals(shipId)).findFirst().orElseThrow();
        ShipDesign design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
        if (design == null || design.powerProfile() == null || !ship.ownerEntityId().equals(fleet.ownerEntityId())) return state;
        ShipPowerProfile profile = design.powerProfile();
        ShipPowerProfile.Fuel fuel = profile.fuels().get(fuelId);
        if (fuel == null) return state;
        ShipPowerState power = ShipPowerProcessor.reserves(ship);
        boolean reactor = fuel.oxidizerId() == null;
        if (reactor ? profile.fissionKw() <= 0 : profile.chemicalKw() <= 0) return state;
        String selected = reactor ? power.reactorFuel() : power.chemicalMixture();
        Map<String, Double> stored = new HashMap<>(power.generatorMaterialsKg());
        // Changing feed requires an empty compartment, including leftover oxidizer.
        double occupied = compartmentMass(profile, power, reactor);
        if (!fuelId.equals(selected) && occupied > .000001) return state;
        if (occupied + kg > (reactor ? profile.reactorTankKg() : profile.generatorTankKg()) + .000001) return state;
        String sourceSystem = ConstructionMaterials.systemForBody(state, bodyId);
        if (sourceSystem == null) sourceSystem = state.orbitalStations().stream().filter(station -> station.id().equals(bodyId))
                .map(station -> station.systemId()).findFirst().orElse(null);
        if (!fleet.currentSystemId().equals(sourceSystem)) return state;
        boolean surface = fleet.location().isAt(FleetLocation.Site.surface(bodyId));
        boolean dock = fleet.location().isAt(FleetLocation.Site.docked(bodyId));
        boolean orbit = fleet.location().isAt(FleetLocation.Site.orbit(bodyId))
                || state.orbitalStations().stream().anyMatch(station -> bodyId.equals(station.planetOrbitId())
                && fleet.location().isAt(FleetLocation.Site.docked(station.id())));
        if (!surface && !dock && !orbit) return state;
        var request = fuel.materials(kg);
        var purchase = ConstructionMaterials.buyUpTo(state, bodyId, ship.ownerEntityId(), request);
        if (!purchase.covers(request)) return state;
        GameState paid = purchase.state();
        if (orbit) {
            var launch = LaunchService.choose(paid, bodyId, ship.ownerEntityId(), kg, false, false, 0);
            if (launch == null) return state;
            paid = LaunchService.settle(paid, ship.ownerEntityId(), launch);
        }
        request.forEach((material, amount) -> stored.merge(material, amount, Double::sum));
        var next = new ShipPowerState(stored, reactor ? power.chemicalMixture() : fuelId,
                reactor ? fuelId : power.reactorFuel(), power.batteryChargeKwh(), power.arraysDeployed(),
                power.arrayCondition(), power.orientationFraction(), power.unmetEssentialHours(),
                power.unmetDriveKwh(), power.unmetCargoKwh(), power.lastUnmetEssentialKwh(), power.chargedInputKwhToday(),
                power.cargoPreservation(), power.rescueStatus());
        return replace(paid, ship.withPowerState(next));
    }

    private static double compartmentMass(ShipPowerProfile profile, ShipPowerState state, boolean reactor) {
        return state.generatorMaterialsKg().entrySet().stream().filter(entry -> profile.fuels().values().stream()
                .filter(fuel -> (fuel.oxidizerId() == null) == reactor)
                .anyMatch(fuel -> entry.getKey().equals(fuel.materialId()) || entry.getKey().equals(fuel.oxidizerId())))
                .mapToDouble(Map.Entry::getValue).sum();
    }

    public static GameState replace(GameState state, ShipInstance updated) {
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.withShips(fleet.ships().stream()
                .map(ship -> ship.id().equals(updated.id()) ? updated : ship).toList())).toList());
    }

    /** Route automation pays for a conservative maneuver and arrival reserve before leaving its local hub. */
    public static GameState prepareLocal(GameState state, Fleet fleet, FleetLocation.Site destination) {
        GameState current = state;
        double hours = Math.ceil(FleetLocation.travelDays(fleet.location().current(), destination)) * 24;
        String source = fleet.location().current().entityId();
        for (ShipInstance ship : fleet.ships()) {
            ShipDesign design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
            if (design == null || design.powerProfile() == null) continue;
            var profile = design.powerProfile();
            var power = ShipPowerProcessor.reserves(ship);
            String feed = profile.chemicalKw() > 0 ? power.chemicalMixture() : power.reactorFuel();
            var fuel = profile.fuels().get(feed);
            if (profile.chemicalKw() + profile.fissionKw() <= 0 || fuel == null) continue;
            double energy = (profile.essentialKw(ship, design) + ShipPowerProcessor.cargoKw(profile, ship, design)
                    + profile.driveKw()) * hours + (profile.essentialKw(ship, design)
                    + ShipPowerProcessor.cargoKw(profile, ship, design)) * ShipPowerProcessor.ARRIVAL_RESERVE_HOURS;
            double needed = energy / fuel.kwhPerKg() * 1.05 - compartmentMass(profile, power, fuel.oxidizerId() == null);
            if (needed > .000001) current = buy(current, ship.id(), source, feed, needed);
        }
        return current;
    }
}
