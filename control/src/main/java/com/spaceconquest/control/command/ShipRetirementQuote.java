package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Read-only paid dismantling and funded market recovery estimate at the current yard. */
public record ShipRetirementQuote(String shipId, String sourceDesignId, String yardId, String providerId,
        ShipRetirementTechnology rules, ShipRetirementSalvage.Sale sale, double workHours,
        double estimatedDays, double serviceFeeCredits, List<String> blockers) {
    public ShipRetirementQuote { blockers = List.copyOf(blockers); }
    public boolean ready() { return rules != null && sale != null && sale.ready() && blockers.isEmpty(); }
    public static ShipRetirementQuote inspect(GameState state, String owner, String shipId) {
        var fleet = state == null || shipId == null ? null : ShipRetirementPlan.fleet(state, shipId);
        var home = state == null ? null : EconomicOwnership.empireFor(state, owner);
        if (fleet == null || home == null || !owner.equals(fleet.ownerEntityId())
                || !owner.equals(home.id()) && !EconomicOwnership.corporationsAllowed(home)) return blocked(shipId, "Owned ship and permitted society are required.");
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id()) && owner.equals(item.ownerEntityId())).findFirst().orElse(null);
        var design = ship == null ? null : FleetSupplySimulation.design(state, ship);
        if (design == null || !owner.equals(design.ownerEntityId()) || design.assembly() == null || design.hullFrame() == null) return blocked(shipId, "Owned captured hull and assembly are required.");
        var reasons = new ArrayList<>(ShipRetirementPlan.blockers(state, fleet, ship));
        if (ShipRefitPlan.reserved(state, shipId)) reasons.add("The ship already has yard work.");
        if (!home.controlledSystemIds().contains(fleet.currentSystemId())) reasons.add("An accessible yard in controlled territory is required.");
        if (fleet.location().current().kind() != FleetLocation.Kind.DOCKED && fleet.location().current().kind() != FleetLocation.Kind.SURFACE) reasons.add("Dock or land at the yard first.");
        if (ship.currentFuelKg() > 0 && PropulsionCatalog.mainDrive(design.equippedModuleIds()) == null) reasons.add("Unload unidentified main fuel first.");
        String yard = fleet.location().current().entityId(); String provider = ShipRefitQuote.provider(state, owner, fleet.currentSystemId(), yard, design);
        if (provider == null) return blocked(shipId, "No operational accessible yard supports this hardware.");
        var rules = HullTechnology.retirementTechnology(design.hullFrame());
        double work = Math.max(1, design.assembly().total().workHours() * rules.dismantlingWorkFraction());
        var capacity = ShipyardWorkCapacity.forYard(state, provider, fleet.currentSystemId(), yard);
        double queued = state.shipConstructionOrders().stream().filter(order -> yard.equals(order.yardBodyId()) && fleet.currentSystemId().equals(order.systemId()))
                .mapToDouble(order -> Math.max(0, order.requiredWorkHours() - order.accumulatedWorkHours())).sum();
        double days = capacity.workPerDay() > 0 ? (work + queued) / capacity.workPerDay() : Double.POSITIVE_INFINITY;
        if (!capacity.operational() || capacity.workPerDay() <= 0) reasons.add("Funded yard capacity is unavailable.");
        if (!owner.equals(provider) && days > ShipyardAccessPolicy.current().maximumQueuedWorkDays()) reasons.add("Service queue is too long.");
        double fee = owner.equals(provider) ? 0 : work * ShipyardAccessPolicy.current().feeCreditsPerWorkUnit();
        if (EconomicOwnership.cash(state, owner) < fee) reasons.add("Current funds cannot cover dismantling fees; future salvage proceeds are not advance funding.");
        var sale = ShipRetirementSalvage.quote(state, yard, ShipRetirementSalvage.materials(design, ship, rules)); reasons.addAll(sale.blockers());
        return new ShipRetirementQuote(shipId, design.id(), yard, provider, rules, sale, work, days, fee, reasons);
    }
    private static ShipRetirementQuote blocked(String ship, String reason) {
        return new ShipRetirementQuote(ship, "", "", "", null, null, 0, Double.POSITIVE_INFINITY, 0, List.of(reason));
    }
}
