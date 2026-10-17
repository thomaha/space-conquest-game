package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.macrostructure.StationModule;
import java.util.*;

/** Immutable discarded quote. Inspecting never purchases, reserves a ship or changes its model. */
public record ShipRefitQuote(String shipId, String sourceDesignId, String targetDesignId, String yardId, String providerId,
        ShipModelComparison comparison, ShipModuleDamage retainedDamage, Double materialCostCredits, double serviceFeeCredits,
        double estimatedYardOperatingCredits, double workDays, double queuedDays, List<String> blockers) {
    public ShipRefitQuote { blockers = List.copyOf(blockers); }
    public boolean ready() { return comparison != null && !comparison.noChange() && blockers.isEmpty(); }
    public Double estimatedCostCredits() { return materialCostCredits == null ? null : materialCostCredits + serviceFeeCredits + estimatedYardOperatingCredits; }
    public static ShipRefitQuote inspect(GameState state, String owner, String shipId, String targetId) {
        if (state == null || owner == null || shipId == null || targetId == null) return blocked(shipId, targetId, "Choose an owned ship and target model.");
        var fleet = state.fleets().stream().filter(item -> owner.equals(item.ownerEntityId()) && item.ships().stream().anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
        var home = EconomicOwnership.empireFor(state, owner);
        if (fleet == null || home == null || !owner.equals(home.id()) && !EconomicOwnership.corporationsAllowed(home)) return blocked(shipId, targetId, "Ship ownership or society does not permit this refit.");
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id()) && owner.equals(item.ownerEntityId())).findFirst().orElse(null);
        var source = ship == null ? null : FleetSupplySimulation.design(state, ship);
        var target = state.shipDesigns().stream().filter(model -> targetId.equals(model.id()) && owner.equals(model.ownerEntityId())).findFirst().orElse(null);
        if (source == null || target == null || source.assembly() == null || target.assembly() == null || source.hullFrame() == null || target.hullFrame() == null)
            return blocked(shipId, targetId, "Owned source and target models need captured assemblies and hulls.");
        var changes = ShipModelComparison.compare(source, target); var mapped = ship.moduleDamage().remap(source, target, changes.retainedIdentities());
        var reasons = new ArrayList<String>(); String yard = fleet.location().current().entityId();
        if (ship.currentHullHealth() <= 0) reasons.add("Destroyed ships cannot be refitted.");
        if (ShipRefitPlan.reserved(state, shipId)) reasons.add("This ship already has a refit order.");
        if (fleet.guardOrder() != null || fleet.escortOrder() != null || fleet.interceptOrder() != null)
            reasons.add("Release the fleet's guard, escort, interception or repair mission before refitting.");
        if (ShipMiningProcessor.committed(state, shipId)) reasons.add("Cancel mining before refitting this ship.");
        if (!home.controlledSystemIds().contains(fleet.currentSystemId())) reasons.add("The ship is outside controlled territory.");
        if (!FleetReturnTravel.available(fleet) || fleet.isInWarp() || fleet.location().inTransit()) reasons.add("Finish current travel before refitting.");
        if (fleet.location().current().kind() != FleetLocation.Kind.DOCKED && fleet.location().current().kind() != FleetLocation.Kind.SURFACE) reasons.add("Dock or land at an accessible shipyard first.");
        if (state.shipyardLedger().deployments().containsKey(shipId)) reasons.add("Finish the pending commissioning deployment before refitting.");
        if (!ship.supplyState().orders().isEmpty() || state.tradeRoutes().stream().anyMatch(route -> route.carrierCommitted() && route.assignedFreighterIds().contains(shipId))) reasons.add("Release the ship's committed supply or trade service before refitting.");
        if (!researched(state, owner, target, home)) reasons.add("Target model requires unavailable research or engineering capability.");
        reasons.addAll(com.spaceconquest.engine.ship.ShipRefitStores.blockers(state, ship, source, target, changes, mapped));
        if (changes.noChange()) return new ShipRefitQuote(shipId, source.id(), targetId, yard, "", changes, mapped, 0.0, 0, 0, 0, 0, List.of("No equipment or structural refit is required."));
        String provider = provider(state, owner, fleet.currentSystemId(), yard, target);
        if (provider == null) return new ShipRefitQuote(shipId, source.id(), targetId, yard, "", changes, mapped, null, 0, 0, Double.POSITIVE_INFINITY, 0,
                append(reasons, "No operational accessible yard at this site supports the target complexity."));
        var capacity = ShipyardWorkCapacity.forYard(state, provider, fleet.currentSystemId(), yard);
        double days = capacity.workPerDay() > 0 ? changes.total().workHours() / capacity.workPerDay() : Double.POSITIVE_INFINITY;
        double pending = state.shipConstructionOrders().stream().filter(order -> fleet.currentSystemId().equals(order.systemId()) && yard.equals(order.yardBodyId()))
                .mapToDouble(order -> Math.max(0, order.requiredWorkHours() - order.accumulatedWorkHours())).sum();
        double queue = capacity.workPerDay() > 0 ? pending / capacity.workPerDay() : Double.POSITIVE_INFINITY;
        if (!capacity.operational() || capacity.workPerDay() <= 0) reasons.add("Yard work is unavailable because of staffing, power or operating state.");
        if (!provider.equals(owner) && days + queue > ShipyardAccessPolicy.current().maximumQueuedWorkDays()) reasons.add("The service yard's admitted queue would be exceeded.");
        var contract = provider.equals(owner) ? null : new ShipyardContract(provider, ShipyardAccessPolicy.current().feeCreditsPerWorkUnit(), 0);
        double fee = contract == null ? 0 : changes.total().workHours() * contract.feeCreditsPerWorkUnit();
        var held = ShipyardPayments.pay(state, owner, contract, changes.total().workHours()); Double materialCost = null;
        if (held == null) reasons.add("The quoted service fee is not funded.");
        else {
            var purchase = materialPreview(held, fleet, owner, shipId, changes.total().materialsKg());
            if (purchase.covers(changes.total().materialsKg())) materialCost = EconomicOwnership.cash(held, owner) - EconomicOwnership.cash(purchase.state(), owner);
            else reasons.add("The complete material bill is not funded from accessible stock.");
        }
        double operations = provider.equals(owner) && Double.isFinite(days) ? Math.ceil(days) * operatingCost(state, provider, yard) : 0;
        if (materialCost != null && EconomicOwnership.cash(state, owner) < materialCost + fee + operations && !"Hive Mind".equalsIgnoreCase(home.societyStructure()))
            reasons.add("Current funds do not cover materials, service and the estimated ongoing yard operating cost.");
        return new ShipRefitQuote(shipId, source.id(), targetId, yard, provider, changes, mapped, materialCost, fee, operations, days, queue, reasons);
    }
    private static boolean researched(GameState state, String owner, ShipDesign target, Empire home) {
        var modules = target.equippedModuleIds(); var known = home.unlockedTechIds();
        return HullTechnology.canBuild(state, owner, target.hullFrame()) && CombatModuleCatalog.researched(modules, known) && PropulsionCatalog.researched(modules, known)
                && ShipComponentCatalog.powerResearched(modules, known) && SensorModuleCatalog.researched(modules, known)
                && ChemicalFreighterCatalog.researched(modules, known) && ShipRepairModuleCatalog.researched(modules, known) && ShipMiningModuleCatalog.researched(modules, known);
    }
    /** Discarded forecast excludes cargo whose carrier is reserved for another service. */
    private static ConstructionMaterials.Purchase materialPreview(GameState state, Fleet fleet, String owner, String shipId, Map<String, Double> bill) {
        var reserved = new HashSet<>(state.shipyardLedger().deployments().keySet());
        reserved.add(shipId);
        state.shipConstructionOrders().stream().filter(order -> order.refit() != null).forEach(order -> reserved.add(order.refit().shipId()));
        state.tradeRoutes().stream().filter(route -> route.carrierCommitted()).forEach(route -> reserved.addAll(route.assignedFreighterIds()));
        state.fleets().stream().flatMap(item -> item.ships().stream()).filter(ship -> !ship.supplyState().orders().isEmpty()).forEach(ship -> reserved.add(ship.id()));
        var preview = state.withFleets(state.fleets().stream().map(item -> item.withShips(item.ships().stream().filter(ship -> !reserved.contains(ship.id())).toList())).toList());
        return fleet.location().current().kind() == FleetLocation.Kind.DOCKED
                ? ConstructionMaterials.buyOrbitalUpTo(preview, fleet.currentSystemId(), fleet.location().current().entityId(), owner, bill, Double.MAX_VALUE)
                : ConstructionMaterials.buyUpTo(preview, fleet.location().current().entityId(), owner, bill);
    }
    static String provider(GameState state, String owner, String system, String yard, ShipDesign target) {
        var candidates = new TreeSet<String>();
        state.orbitalStations().stream().filter(station -> yard.equals(station.id()) && system.equals(station.systemId()) && station.isOperational()
                && (station.hasModuleType(StationModule.TYPE_CAPITAL_SLIPWAY) || station.hasModuleType(StationModule.TYPE_SHIPYARD_GRID))).forEach(station -> candidates.add(station.ownerEntityId()));
        state.industrialFacilities().stream().filter(facility -> yard.equals(facility.planetId()) && facility.tier() > 0
                && com.spaceconquest.engine.ship.ShipyardTechnology.isSurface(facility.applicationId())).forEach(facility -> candidates.add(facility.ownerEntityId()));
        return candidates.stream().filter(provider -> ShipyardAccess.permitted(state, owner, provider, system)
                && ShipManufacturingCapacity.forYard(state, provider, system, yard) >= ShipManufacturingCapacity.requiredComplexity(target))
                .sorted(Comparator.comparingInt(provider -> provider.equals(owner) ? 0 : 1)).findFirst().orElse(null);
    }
    static double operatingCost(GameState state, String provider, String yard) {
        var station = state.orbitalStations().stream().filter(item -> yard.equals(item.id())).findFirst().orElse(null);
        if (station != null) return station.modules().stream().filter(ShipyardWorkCapacity::isYardWorkforceModule).mapToDouble(StationModule::dailyWageCostsCredits).sum();
        var facilities = state.industrialFacilities().stream().filter(item -> yard.equals(item.planetId()) && provider.equals(item.ownerEntityId())
                && com.spaceconquest.engine.ship.ShipyardTechnology.isSurface(item.applicationId())).map(IndustrialFacility::id).toList();
        return state.industryAccounts().stream().filter(account -> facilities.contains(account.facilityId()))
                .mapToDouble(account -> account.wageCostsCredits() + account.powerCostsCredits() + account.maintenanceCostsCredits()).sum();
    }
    private static List<String> append(List<String> reasons, String value) { var copy = new ArrayList<>(reasons); copy.add(value); return List.copyOf(copy); }
    private static ShipRefitQuote blocked(String ship, String target, String reason) {
        return new ShipRefitQuote(ship, "", target, "", "", null, ShipModuleDamage.empty(), null, 0, 0, Double.POSITIVE_INFINITY, 0, List.of(reason));
    }
}
