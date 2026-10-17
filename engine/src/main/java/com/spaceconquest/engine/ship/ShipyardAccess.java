package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.macrostructure.StationModule;
import java.util.*;

/** A domestic service offer never transfers blueprint, ship or yard ownership. */
public final class ShipyardAccess {
    private ShipyardAccess() {}
    public record Offer(String yardId, String providerOwnerId, ShipyardContract contract, double queuedDays) {}
    public static boolean permitted(GameState state, String customer, String provider, String system) {
        return permitted(state, customer, provider, system, ShipyardAccessPolicy.current());
    }
    public static boolean permitted(GameState state, String customer, String provider, String system, ShipyardAccessPolicy policy) {
        var home = EconomicOwnership.empireFor(state, customer); var yardHome = EconomicOwnership.empireFor(state, provider);
        if (home == null || yardHome == null || !home.id().equals(yardHome.id()) || !home.controlledSystemIds().contains(system)) return false;
        if (customer.equals(provider)) return true;
        if (customer.equals(home.id()) || !EconomicOwnership.corporationsAllowed(home)) return false;
        return provider.equals(home.id()) ? policy.publicYardsOpen() : policy.corporateYardsOpen();
    }
    public static String provider(ShipConstructionOrder order) { return order.yardContract() == null ? order.ownerEntityId() : order.yardContract().providerOwnerId(); }
    /** Existing corporate investment may stage a build before opening payroll has settled. */
    public static ShipyardContract contractAt(GameState state, String customer, String system, String yard, ShipDesign design) {
        var providers = new LinkedHashSet<String>();
        state.orbitalStations().stream().filter(station -> system.equals(station.systemId()) && yard.equals(station.id())).forEach(station -> providers.add(station.ownerEntityId()));
        state.industrialFacilities().stream().filter(facility -> yard.equals(facility.planetId()) && com.spaceconquest.engine.ship.ShipyardTechnology.isSurface(facility.applicationId()))
                .forEach(facility -> providers.add(facility.ownerEntityId()));
        if (providers.contains(customer)) return null;
        return providers.stream().filter(provider -> permitted(state, customer, provider, system)
                && ShipManufacturingCapacity.forYard(state, provider, system, yard) >= ShipManufacturingCapacity.requiredComplexity(design))
                .map(provider -> new ShipyardContract(provider, ShipyardAccessPolicy.current().feeCreditsPerWorkUnit(), 0)).findFirst().orElse(null);
    }
    public static Offer select(GameState state, String customer, String system, ShipDesign design) {
        if (design == null) return null;
        var candidates = new ArrayList<Offer>();
        for (var station : state.orbitalStations()) {
            if (!system.equals(station.systemId()) || !station.isOperational() || !station.modules().stream().anyMatch(module -> {
                var capability = ShipyardTechnology.yard(module.type());
                return capability != null && capability.buildsShips() && capability.baseComplexity() > 0;
            }) || ConstructionMaterials.orbitalHubEntity(state, system, station.id()) == null) continue;
            var offer = offer(state, customer, station.ownerEntityId(), system, station.id(), design); if (offer != null) candidates.add(offer);
        }
        if (!design.equippedModuleIds().contains(ChemicalFreighterCatalog.MAIN_TANK)) for (var facility : state.industrialFacilities()) {
            if (!com.spaceconquest.engine.ship.ShipyardTechnology.isSurface(facility.applicationId()) || facility.tier() <= 0
                    || !system.equals(ConstructionMaterials.systemForBody(state, facility.planetId()))) continue;
            var offer = offer(state, customer, facility.ownerEntityId(), system, facility.planetId(), design); if (offer != null) candidates.add(offer);
        }
        return candidates.stream().min(Comparator.comparingInt((Offer value) -> Double.isFinite(value.queuedDays()) ? 0 : 1)
                .thenComparingInt(value -> customer.equals(value.providerOwnerId()) ? 0 : 1)
                .thenComparingDouble(Offer::queuedDays).thenComparing(Offer::yardId).thenComparing(Offer::providerOwnerId)).orElse(null);
    }
    private static Offer offer(GameState state, String customer, String provider, String system, String yard, ShipDesign design) {
        if (!permitted(state, customer, provider, system) || ShipManufacturingCapacity.forYard(state, provider, system, yard) < ShipManufacturingCapacity.requiredComplexity(design)) return null;
        double capacity = ShipyardWorkCapacity.forYard(state, provider, system, yard).workPerDay();
        double remaining = state.shipConstructionOrders().stream().filter(order -> system.equals(order.systemId()) && yard.equals(order.yardBodyId()))
                .mapToDouble(order -> Math.max(0, order.requiredWorkHours() - order.accumulatedWorkHours())).sum();
        remaining += state.auxiliaryForces().orders().stream().filter(order -> system.equals(order.systemId()) && yard.equals(order.bodyId()))
                .mapToDouble(order -> Math.max(0, order.requiredHours() - order.completedHours())).sum();
        double work = ShipConstructionRequirements.estimate(design).workUnits();
        double days = capacity > 0 ? (remaining + work) / capacity : Double.POSITIVE_INFINITY;
        if (!customer.equals(provider) && (capacity <= 0 || days > ShipyardAccessPolicy.current().maximumQueuedWorkDays())) return null;
        var contract = customer.equals(provider) ? null : new ShipyardContract(provider, ShipyardAccessPolicy.current().feeCreditsPerWorkUnit(), 0);
        if (contract != null && EconomicOwnership.cash(state, customer) < work * contract.feeCreditsPerWorkUnit()) return null;
        return new Offer(yard, provider, contract, days);
    }
}
