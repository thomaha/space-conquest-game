package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.EmpireFleetPlanner;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.PowerBillingProcessor;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Requotes live readiness and commits one ordinary funded service action per request. */
public record ServiceAICombatFleetCommand(String ownerEntityId, String fleetId) implements GameCommand {
    @Override public boolean validate(GameState state) { return service(state) != state; }
    @Override public GameState apply(GameState state) { return FleetServiceHistory.command(state, service(state), fleetId); }

    private GameState service(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null
                || state.empires().stream().noneMatch(empire -> ownerEntityId.equals(empire.id()))) return state;
        var fleet = FleetOrganization.find(state, fleetId);
        if (fleet == null || ShipRefitPlan.reserved(state, fleet) || !ownerEntityId.equals(fleet.ownerEntityId()) || fleet.ships().isEmpty()
                || !FleetReturnTravel.available(fleet)
                || fleet.ships().stream().anyMatch(ship -> !ownerEntityId.equals(ship.ownerEntityId())
                    || ship.currentHullHealth() <= 0 || ship.passengerCount() > 0 || !ship.supplyState().orders().isEmpty()
                    || FleetSupplySimulation.design(state, ship) == null
                    || !ownerEntityId.equals(FleetSupplySimulation.design(state, ship).ownerEntityId()))) return state;
        var ships = fleet.ships().stream().filter(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            return design != null && ownerEntityId.equals(design.ownerEntityId()) && EmpireFleetPlanner.combat(design.role());
        }).sorted(Comparator.comparing(ShipInstance::id)).toList();
        if (ships.isEmpty()) return state;
        if (!available(state, fleet)) return state;
        if (ships.stream().anyMatch(ship -> readiness(state, ship).weapons().stream().anyMatch(weapon ->
                weapon.blocker() == ShipWeaponReadiness.Blocker.DISABLED || weapon.blocker() == ShipWeaponReadiness.Blocker.CONDITION))
                || ShipElectricalDamage.damaged(state, fleet) && ships.stream().anyMatch(ship -> readiness(state, ship).weapons().stream()
                        .anyMatch(weapon -> weapon.blocker() == ShipWeaponReadiness.Blocker.POWER))) {
            var repair = FleetRepairOrders.advance(state, fleetId);
            if (repair != state) {
                if (fleet.guardOrder() == null && fleet.escortOrder() == null && fleet.interceptOrder() == null) return repair;
                var repairing = FleetOrganization.find(repair, fleetId);
                return FleetReturnTravel.replace(repair, repairing.withInterceptOrder(repairing.interceptOrder()
                        .withProtection(repairing.interceptOrder().protection().forWeapons())));
            }
        }
        boolean mission = fleet.guardOrder() != null || fleet.escortOrder() != null
                || fleet.interceptOrder() != null && fleet.interceptOrder().protection() == null;
        var prepared = mission && FleetWeaponRecovery.needs(state, fleet)
                ? FleetReturnTravel.replace(state, FleetWeaponRecovery.pause(fleet)) : state;
        return resupply(prepared, FleetOrganization.find(prepared, fleetId), ships);
    }

    private GameState resupply(GameState state, Fleet fleet, List<ShipInstance> ships) {
        var local = supply(state, ships);
        if (local != state) return local;
        if (!FleetWeaponRecovery.needsBeforeReturn(state, fleet)) return state;
        if (FleetWeaponRecovery.active(fleet) && fleet.interceptOrder().protection().stage() == FleetProtectionAssignment.Stage.REPAIRING) return state;
        if (FleetThreatAssessment.assess(state, fleet).action() != FleetThreatAssessment.Action.NO_CONTACT) return state;
        var stations = state.orbitalStations().stream().filter(station -> station.isOperational()
                && ownerEntityId.equals(station.ownerEntityId()) && fleet.currentSystemId().equals(station.systemId()))
                .sorted(Comparator.comparingDouble((com.spaceconquest.engine.macrostructure.OrbitalStation station) ->
                        FleetThreatAssessment.distance(state, fleet, FleetLocation.at(FleetLocation.Site.docked(station.id()))))
                        .thenComparing(com.spaceconquest.engine.macrostructure.OrbitalStation::id)).toList();
        for (var station : stations) {
            var command = new MoveFleetLocalCommand(fleetId, FleetLocation.Kind.DOCKED, station.id());
            if (!command.validate(state)) continue;
            var site = FleetLocation.Site.docked(station.id());
            if (state.fleets().stream().filter(enemy -> FleetInterceptProcessor.contact(state, fleet, enemy))
                    .anyMatch(enemy -> FleetThreatAssessment.distance(state, enemy, FleetLocation.at(site))
                            <= FleetThreatAssessment.THREAT_RADIUS_METERS)) continue;
            var plan = FleetReturnTravel.localPlan(state, fleet, site);
            if (plan == null) continue;
            var arrival = LocalTravel.arrivalPreview(state, fleet, site, plan);
            var projected = FleetReturnTravel.replace(state.withTurn(state.turn() + (long) Math.ceil(plan.days())), arrival);
            var arrivingShips = ships.stream().map(ship -> arrival.ships().stream()
                    .filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow()).toList();
            var supplied = quoteSupply(projected, arrivingShips);
            if (supplied == projected || FleetWeaponRecovery.active(fleet)
                    && !returnFunded(supplied, FleetOrganization.find(supplied, fleetId))) continue;
            var departing = command.apply(state);
            if (FleetWeaponRecovery.active(fleet)) {
                var assignment = fleet.interceptOrder().protection().at(FleetProtectionAssignment.Stage.RESUPPLYING, station.id());
                var moved = FleetOrganization.find(departing, fleetId).withInterceptOrder(fleet.interceptOrder().withProtection(assignment));
                departing = FleetReturnTravel.replace(departing, moved);
            }
            return departing;
        }
        return state;
    }

    private boolean available(GameState state, Fleet fleet) {
        return (FleetWeaponRecovery.active(fleet) || fleet.guardOrder() != null || fleet.escortOrder() != null
                || fleet.interceptOrder() != null && fleet.interceptOrder().protection() == null
                || "PASSIVE".equalsIgnoreCase(fleet.fleetStance()) && fleet.interceptOrder() == null)
                && state.tradeRoutes().stream().noneMatch(route -> (route.isActive() || route.onboardKg() > 0
                    || !TradeRoute.LOADING.equals(route.phase())) && route.assignedFreighterIds().stream()
                        .anyMatch(id -> fleet.ships().stream().anyMatch(ship -> id.equals(ship.id()))))
                && state.fleets().stream().flatMap(item -> item.ships().stream()).noneMatch(ship -> ship.powerState() != null
                    && ship.powerState().rescueStatus() != null && ship.powerState().rescueStatus().phase() == RescueStatus.Phase.APPROACHING
                    && (fleet.id().equals(ship.powerState().rescueStatus().rescuerFleetId())
                        || fleet.id().equals(ship.powerState().rescueStatus().order().targetFleetId())));
    }

    private ShipWeaponReadiness readiness(GameState state, ShipInstance ship) {
        return ShipWeaponReadiness.inspect(FleetSupplySimulation.design(state, ship), ship, true);
    }

    private GameState supply(GameState state, List<ShipInstance> ships) {
        var fleet = FleetOrganization.find(state, fleetId);
        boolean returnReserve = FleetWeaponRecovery.active(fleet) && !FleetWeaponRecovery.needs(state, fleet)
                && FleetWeaponRecovery.needsBeforeReturn(state, fleet);
        for (var ship : ships) {
            var readiness = readiness(state, ship);
            for (var weapon : readiness.weapons()) {
                if (weapon.blocker() != ShipWeaponReadiness.Blocker.AMMUNITION) continue;
                var quote = AmmunitionResupplyQuote.inspect(state, ownerEntityId, ship.id(), weapon.moduleId());
                if (quote.available()) return quote.command().apply(state);
            }
            if (!returnReserve && readiness.weapons().stream().noneMatch(weapon -> weapon.blocker() == ShipWeaponReadiness.Blocker.POWER)) continue;
            var charged = charge(state, ship);
            if (charged != state) return charged;
            var design = FleetSupplySimulation.design(state, ship);
            var profile = design.powerProfile(ship);
            var power = ShipPowerProcessor.reserves(ship);
            for (String feed : List.of(power.chemicalMixture(), power.reactorFuel())) {
                var fuel = profile.fuels().get(feed);
                if (fuel == null) continue;
                boolean reactor = fuel.oxidizerId() == null;
                double occupied = power.generatorMaterialsKg().entrySet().stream().filter(entry -> profile.fuels().values().stream()
                        .filter(item -> (item.oxidizerId() == null) == reactor).anyMatch(item -> entry.getKey().equals(item.materialId())
                                || entry.getKey().equals(item.oxidizerId()))).mapToDouble(Map.Entry::getValue).sum();
                double room = (reactor ? profile.reactorTankKg() : profile.generatorTankKg()) - occupied;
                String source = fleet.location().current().entityId();
                double amount = fuelAvailable(state, source, fuel, room);
                var paid = new ResupplyShipPowerCommand(ship.id(), source, feed, amount).apply(state);
                if (paid != state) {
                    var supplied = FleetOrganization.find(paid, fleetId).ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
                    int before = readiness.weapons().stream().mapToInt(ShipWeaponReadiness.Weapon::fundedShots).sum();
                    if (readiness(paid, supplied).weapons().stream().mapToInt(ShipWeaponReadiness.Weapon::fundedShots).sum() > before
                            || returnReserve && returnFunded(paid, FleetOrganization.find(paid, fleetId))) return paid;
                }
            }
        }
        return state;
    }

    private boolean returnFunded(GameState state, Fleet fleet) {
        var destination = FleetProtectionReturn.destination(state, fleet);
        return destination != null && FleetReturnTravel.ready(state, fleet, destination)
                && !FleetWeaponRecovery.needsBeforeReturn(state, fleet);
    }

    /** Bounded discarded purchases include application-defined shared magazine requirements. */
    private GameState quoteSupply(GameState state, List<ShipInstance> ships) {
        double requests = 2;
        for (var ship : ships) for (String id : FleetSupplySimulation.design(state, ship).equippedModuleIds()) {
            var module = CombatModuleCatalog.module(FleetSupplySimulation.design(state, ship), id);
            if (module != null && !module.ammunitionId().isBlank()) requests += module.operationalStats().getOrDefault(
                    "pointDefenseCapacity", module.operationalStats().getOrDefault("launchesPerRound",
                            module.operationalStats().getOrDefault("shotsPerRound", 1.0)));
        }
        var result = state;
        // Diagnostic work cap, independent of actual weapon capacity.
        for (int pass = 0; pass < Math.min(10000, requests); pass++) {
            var currentFleet = FleetOrganization.find(result, fleetId);
            var ids = ships.stream().map(ShipInstance::id).toList();
            var next = supply(result, currentFleet.ships().stream().filter(ship -> ids.contains(ship.id())).toList());
            if (next == result) break;
            result = next;
        }
        return result;
    }

    private double fuelAvailable(GameState state, String source, ShipPowerProfile.Fuel fuel, double room) {
        var hub = state.commercialHubs().stream().filter(item -> source.equals(item.entityId())).findFirst().orElse(null);
        if (hub == null || room <= 0) return 0;
        double unitPrice = 0;
        for (var ingredient : fuel.materials(1).entrySet()) {
            var order = hub.activeOrders().get(ingredient.getKey());
            if (order == null || !Double.isFinite(order.pricePerKg()) || order.pricePerKg() < 0
                    || !Double.isFinite(order.supplyKg())) return 0;
            room = Math.min(room, order.supplyKg() / ingredient.getValue());
            unitPrice += order.pricePerKg() * ingredient.getValue();
        }
        double cash = state.empires().stream().filter(empire -> ownerEntityId.equals(empire.id())).findFirst().orElseThrow().treasuryCredits();
        return unitPrice > 0 ? Math.min(room, cash / unitPrice) : room;
    }

    private GameState charge(GameState state, ShipInstance ship) {
        var design = FleetSupplySimulation.design(state, ship);
        var profile = design.powerProfile(ship);
        var power = ShipPowerProcessor.reserves(ShipElectricalDamage.clampCharge(design, ship));
        var fleet = FleetOrganization.find(state, fleetId);
        String source = fleet.location().current().entityId();
        var grid = state.powerGrids().stream().filter(item -> source.equals(item.entityId())).findFirst().orElse(null);
        if (grid == null) return state;
        double cash = state.empires().stream().filter(empire -> ownerEntityId.equals(empire.id())).findFirst().orElseThrow().treasuryCredits();
        double input = Math.min((profile.batteryKwh() - power.batteryChargeKwh()) / ShipPowerProcessor.CHARGING_EFFICIENCY,
                Math.min(profile.chargeKw() * 24 - power.chargedInputKwhToday(),
                        Math.min(grid.currentStoredKwh(), cash / PowerBillingProcessor.PRICE_PER_KWH)));
        return new ChargeShipBatteryCommand(ship.id(), source, input).apply(state);
    }
}
