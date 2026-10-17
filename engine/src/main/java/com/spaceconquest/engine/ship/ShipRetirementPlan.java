package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import java.util.*;

/** Saved dismantling reservation with frozen recovery rules and live surviving hardware. */
public record ShipRetirementPlan(String shipId, String sourceDesignId, ShipRetirementTechnology rules, String replacementShipId, double minimumReplacementCondition) {
    public ShipRetirementPlan {
        Objects.requireNonNull(rules);
        if (shipId == null || shipId.isBlank() || sourceDesignId == null || sourceDesignId.isBlank()) throw new IllegalArgumentException("Retirement source required");
        if (!Double.isFinite(minimumReplacementCondition) || minimumReplacementCondition < 0 || minimumReplacementCondition > 1) throw new IllegalArgumentException("Invalid replacement condition");
        if (replacementShipId != null && (replacementShipId.isBlank() || minimumReplacementCondition <= 0)) throw new IllegalArgumentException("Replacement retirement requires a condition threshold");
    }
    public ShipRetirementPlan(String shipId, String sourceDesignId, ShipRetirementTechnology rules) { this(shipId, sourceDesignId, rules, null, 0); }
    public ShipRetirementPlan withReplacement(String id, double minimumCondition) { return new ShipRetirementPlan(shipId, sourceDesignId, rules, id, minimumCondition); }
    public static Fleet fleet(GameState state, String shipId) {
        return state.fleets().stream().filter(fleet -> fleet.ships().stream().anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
    }
    public static List<String> blockers(GameState state, Fleet fleet, ShipInstance ship) {
        var reasons = new ArrayList<String>();
        if (ShipMiningProcessor.committed(state, ship.id())) reasons.add("Cancel mining before retiring this ship.");
        if (ship.currentHullHealth() <= 0) reasons.add("Destroyed ships require wreck salvage rather than retirement.");
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit() || fleet.isInWarp()
                || fleet.guardOrder() != null || fleet.escortOrder() != null || fleet.interceptOrder() != null) reasons.add("Finish travel and release the fleet mission first.");
        if (ship.passengerCount() > 0) reasons.add("Disembark passengers first.");
        if (state.tradeRoutes().stream().anyMatch(route -> route.carrierCommitted() && route.assignedFreighterIds().contains(ship.id()))) reasons.add("Finish committed trade or supply service first.");
        if (state.fleets().stream().flatMap(item -> item.ships().stream()).flatMap(item -> item.supplyState().orders().stream())
                .anyMatch(order -> ship.id().equals(order.supplierShipId()) || ship.id().equals(order.receiverShipId()) || order.memberShipIds().contains(ship.id()))) reasons.add("Finish scheduled supply transfers first.");
        if (state.shipyardLedger().deployments().containsKey(ship.id())) reasons.add("Finish commissioning deployment first.");
        if (state.commercialHubs().stream().anyMatch(hub -> hub.reservedTradeFuel().containsKey(ship.id()))) reasons.add("Claim prepaid return fuel at its reserved port before retirement.");
        if (state.auxiliaryForces().wings().stream().anyMatch(wing -> ship.id().equals(wing.carrierShipId()))
                || state.auxiliaryForces().orders().stream().anyMatch(order -> ship.id().equals(order.carrierShipId()))) reasons.add("Transfer or dismantle strike craft and finish their construction first.");
        return List.copyOf(reasons);
    }
    public String problem(GameState state, ShipConstructionOrder order, ShipDesign design) {
        var fleet = fleet(state, shipId);
        var ship = fleet == null ? null : fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElse(null);
        if (ship == null || !sourceDesignId.equals(ship.designId()) || !order.ownerEntityId().equals(ship.ownerEntityId())
                || !order.ownerEntityId().equals(fleet.ownerEntityId()) || !order.systemId().equals(fleet.currentSystemId())
                || !order.yardBodyId().equals(fleet.location().current().entityId())) return "Retirement ship or yard unavailable";
        var reasons = blockers(state, fleet, ship); if (!reasons.isEmpty()) return reasons.getFirst();
        if ((fleet.location().current().kind() != FleetLocation.Kind.DOCKED && fleet.location().current().kind() != FleetLocation.Kind.SURFACE)
                || !ShipyardAccess.permitted(state, order.ownerEntityId(), ShipyardAccess.provider(order), order.systemId())) return "Retirement yard access unavailable";
        if (replacementShipId != null && !ShipReplacementPlan.operational(state, shipId, replacementShipId, minimumReplacementCondition)) return "Operational replacement unavailable";
        var sale = ShipRetirementSalvage.quote(state, order.yardBodyId(), ShipRetirementSalvage.materials(design, ship, rules));
        return sale.ready() ? null : sale.blockers().getFirst();
    }
    public GameState complete(GameState state, ShipConstructionOrder order, ShipDesign design) {
        var original = fleet(state, shipId); var ship = original.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
        var sale = ShipRetirementSalvage.quote(state, order.yardBodyId(), ShipRetirementSalvage.materials(design, ship, rules));
        var current = ShipRetirementSalvage.settle(state, order.ownerEntityId(), sale);
        current = ShipyardAccounting.record(current, order, 0, 0, true, "Retired " + shipId + "; recovered materials kg " + new TreeMap<>(sale.materialsKg())
                + "; buyer paid " + sale.buyerPaymentCredits() + "; owner proceeds " + sale.credits() + "; tariff " + sale.tariffCredits());
        var fleets = current.fleets().stream().map(fleet -> fleet.id().equals(original.id())
                ? fleet.withShips(fleet.ships().stream().filter(item -> !shipId.equals(item.id())).toList()) : fleet)
                .filter(fleet -> !fleet.id().equals(original.id()) || !fleet.ships().isEmpty()).toList();
        var ledger = current.shipyardLedger(); var replacements = new HashMap<>(ledger.replacements());
        replacements.entrySet().removeIf(entry -> shipId.equals(entry.getKey()) || shipId.equals(entry.getValue()));
        return current.toBuilder().fleets(fleets).shipyardLedger(new ShipyardLedger(ledger.history(), ledger.cumulativeProfit(), ledger.lastPayrollTurn(), ledger.deployments(), replacements))
                .corporations(current.corporations().stream().map(corp -> !corp.ownedShipIds().contains(shipId) ? corp : new Corporation(corp.id(), corp.name(), corp.empireId(),
                        corp.headquartersEntityId(), corp.marketOrientation(), corp.liquidCapitalReserves(), corp.ownedFacilityIds(), corp.ownedShipIds().stream().filter(id -> !shipId.equals(id)).toList(), corp.claimedVeinIds())).toList()).build();
    }
}
