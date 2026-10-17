package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Finite deposit extraction after the ordinary daily electrical budget has paid mining work. */
public final class ShipMiningProcessor {
    private ShipMiningProcessor() {}
    public static boolean committed(GameState state, String shipId) {
        var order = state.shipMiningState().orders().get(shipId);
        return order != null && !"Cancelled".equals(order.status());
    }
    public static Fleet fleet(GameState state, String shipId) { return ShipRetirementPlan.fleet(state, shipId); }
    public static GeologicalDeposit deposit(GameState state, String id) {
        return state.geologicalDeposits().stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
    }
    public static String problem(GameState state, String owner, String shipId, String depositId) {
        var fleet = fleet(state, shipId); var home = EconomicOwnership.empireFor(state, owner); var deposit = deposit(state, depositId);
        if (fleet == null || home == null || !owner.equals(fleet.ownerEntityId())
                || !owner.equals(home.id()) && !EconomicOwnership.corporationsAllowed(home)) return "Owner unavailable";
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
        var design = FleetSupplySimulation.design(state, ship);
        if (design == null || design.assembly() == null || !owner.equals(ship.ownerEntityId()) || !owner.equals(design.ownerEntityId())
                || ship.currentHullHealth() <= 0 || design.powerProfile() == null) return "Captured mining ship unavailable";
        if (deposit == null || !deposit.isDiscovered() || !Double.isFinite(deposit.remainingVolumeKg()) || deposit.remainingVolumeKg() <= 0
                || !Double.isFinite(deposit.concentrationModifier()) || deposit.concentrationModifier() <= 0) return "Deposit undiscovered or depleted";
        String system = ConstructionMaterials.systemForBody(state, deposit.planetId());
        if (system == null || !home.controlledSystemIds().contains(system)
                || deposit.ownerEntityId() != null && !deposit.ownerEntityId().isBlank() && !owner.equals(deposit.ownerEntityId())) return "Deposit access unavailable";
        if (!system.equals(fleet.currentSystemId()) || fleet.hasInterstellarOrder() || fleet.location().underway()
                || !(fleet.location().stationaryAt(FleetLocation.Site.deepSpace(deposit.planetId()))
                || fleet.location().stationaryAt(FleetLocation.Site.orbit(deposit.planetId()))
                || fleet.location().stationaryAt(FleetLocation.Site.surface(deposit.planetId())))) return "Travel to the deposit first";
        if (ShipRefitPlan.reserved(state, shipId) || fleet.guardOrder() != null || fleet.escortOrder() != null || fleet.interceptOrder() != null
                || !"PASSIVE".equalsIgnoreCase(fleet.fleetStance()) || ship.passengerCount() > 0 || !ship.supplyState().orders().isEmpty()
                || state.shipyardLedger().deployments().containsKey(shipId)
                || state.tradeRoutes().stream().anyMatch(route -> route.carrierCommitted() && route.assignedFreighterIds().contains(shipId))) return "Release other ship commitments first";
        if (!ShipMiningModuleCatalog.researched(design.equippedModuleIds(), home.unlockedTechIds())) return "Mining research unavailable";
        if (hardware(state, design, ship, deposit.planetId()).isEmpty()) return "No healthy mining equipment supports this body";
        if (room(design, ship) <= 0) return "Cargo hold full";
        return null;
    }
    private static List<ShipModule> hardware(GameState state, ShipDesign design, ShipInstance ship, String body) {
        var result = new ArrayList<ShipModule>();
        for (int mount = 0; mount < design.assembly().installations().size(); mount++) {
            var module = design.assembly().installations().get(mount).hardware();
            if (module.operationalStats().containsKey("miningRateKgPerTurn") && ship.moduleDamage().condition(mount) > 0 && supports(state, body, module))
                result.add(new ShipModule(module.id(), module.name(), module.slotSize(), module.slotCost(), module.dryMassKg(),
                        module.powerDrawKw() * ship.moduleDamage().condition(mount), 0, 0, module.complexityLevel(), module.materialCostsKg(),
                        Map.of("miningRateKgPerTurn", module.operationalStats().get("miningRateKgPerTurn") * ship.moduleDamage().condition(mount))));
        }
        return result;
    }
    public static boolean supports(GameState state, String body, ShipModule module) {
        double gravity = Double.NaN, diameter = Double.NaN; boolean airless = false;
        for (var system : state.solarSystems()) {
            if (system.asteroidBelts().stream().anyMatch(belt -> body.equals(belt.id()))) return true;
            for (var planet : system.planets()) {
                if (body.equals(planet.id())) { gravity = planet.gravity(); diameter = planet.diameter(); airless = airless(planet.atmosphere()); }
                for (var moon : planet.moons()) if (body.equals(moon.id())) { gravity = moon.gravity(); diameter = moon.diameter(); airless = airless(moon.atmosphere()); }
            }
        }
        return airless && Double.isFinite(gravity) && gravity >= 0 && Double.isFinite(diameter) && diameter > 0
                && gravity <= module.operationalStats().getOrDefault("maximumGravityMps2", 0.0)
                && diameter <= module.operationalStats().getOrDefault("maximumDiameterKm", 0.0);
    }
    private static boolean airless(String value) { return value != null && ("none".equalsIgnoreCase(value) || "vacuum".equalsIgnoreCase(value)); }
    private static double room(ShipDesign design, ShipInstance ship) {
        double capacity = java.util.stream.IntStream.range(0, design.assembly().installations().size()).mapToDouble(index ->
                design.assembly().installations().get(index).hardware().operationalStats().getOrDefault("cargoCapacityKg", 0.0) * ship.moduleDamage().condition(index)).sum();
        return Math.max(0, capacity - ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum());
    }
    public static double powerKw(GameState state, Fleet fleet, ShipInstance ship) {
        var order = state.shipMiningState().orders().get(ship.id());
        if (order == null || !committed(state, ship.id()) || order.lastWorkedTurn() >= state.turn()
                || problem(state, order.ownerId(), ship.id(), order.depositId()) != null) return 0;
        return hardware(state, FleetSupplySimulation.design(state, ship), ship, deposit(state, order.depositId()).planetId()).stream().mapToDouble(ShipModule::powerDrawKw).sum();
    }
    public static GameState powerAndAdvance(GameState state) {
        var powered = state.withFleets(ShipPowerProcessor.advanceDay(state));
        var current = powered;
        for (var order : state.shipMiningState().orders().values().stream().sorted(Comparator.comparing(ShipMiningOrder::shipId)).toList()) {
            if (!committed(state, order.shipId()) || order.lastWorkedTurn() >= state.turn()) continue;
            String problem = problem(current, order.ownerId(), order.shipId(), order.depositId());
            double extracted = 0;
            if (problem == null) {
                var fleet = fleet(current, order.shipId()); var ship = fleet.ships().stream().filter(item -> order.shipId().equals(item.id())).findFirst().orElseThrow();
                var design = FleetSupplySimulation.design(current, ship); var deposit = deposit(current, order.depositId());
                var modules = hardware(current, design, ship, deposit.planetId()); double kw = modules.stream().mapToDouble(ShipModule::powerDrawKw).sum();
                double funded = Math.clamp(1 - ShipPowerProcessor.reserves(ship).unmetDriveKwh() / (kw * 24), 0, 1);
                extracted = Math.min(room(design, ship), Math.min(deposit.remainingVolumeKg(), modules.stream()
                        .mapToDouble(module -> module.operationalStats().get("miningRateKgPerTurn")).sum() * deposit.concentrationModifier() * funded));
                if (extracted > 0) {
                    var cargo = new HashMap<>(ship.storedCargoKg()); cargo.merge(deposit.materialId(), extracted, Double::sum);
                    var loaded = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(), cargo,
                            ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
                    current = FleetReturnTravel.replace(current, fleet.withShips(fleet.ships().stream().map(item -> ship.id().equals(item.id()) ? loaded : item).toList()));
                    double remaining = deposit.remainingVolumeKg() - extracted;
                    current = current.toBuilder().geologicalDeposits(current.geologicalDeposits().stream().map(item -> deposit.id().equals(item.id())
                            ? new GeologicalDeposit(item.id(), item.planetId(), item.materialId(), item.initialVolumeKg(), remaining, item.concentrationModifier(), item.isDiscovered(), item.ownerEntityId()) : item).toList()).build();
                }
                problem = extracted > 0 ? "Extracting" : "Waiting for electrical power";
            }
            current = current.toBuilder().shipMiningState(current.shipMiningState().with(order.shipId(), new ShipMiningOrder(order.ownerId(), order.shipId(), order.depositId(),
                    state.turn(), order.extractedKg() + extracted, problem))).build();
        }
        return current;
    }
}
