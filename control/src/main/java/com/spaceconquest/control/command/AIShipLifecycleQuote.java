package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Discarded bounded comparison of real paid recovery paths at the current yard. */
public record AIShipLifecycleQuote(Action action, String fleetId, String shipId, ShipDesign target,
        double estimatedCredits, double downtimeDays, double gainFraction, double score, String reason) {
    public enum Action { REPAIR, REFIT, REPLACE }
    public static AIShipLifecycleQuote inspect(GameState state, String owner, String shipId) {
        return inspect(state, owner, shipId, ShipLifecyclePolicy.current());
    }
    public static AIShipLifecycleQuote inspect(GameState state, String owner, String shipId, ShipLifecyclePolicy policy) {
        if (state == null || owner == null || shipId == null) return null;
        var fleet = state.fleets().stream().filter(item -> owner.equals(item.ownerEntityId()) && item.ships().stream().anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
        var home = EconomicOwnership.empireFor(state, owner);
        if (fleet == null || home == null || !owner.equals(home.id()) && !EconomicOwnership.corporationsAllowed(home)
                || !home.controlledSystemIds().contains(fleet.currentSystemId()) || !AIShipDeploymentIntent.idle(fleet)
                || ShipRefitPlan.reserved(state, fleet) || fleet.ships().stream().anyMatch(ship -> ShipReplacementPlan.committed(state, ship.id())
                || state.shipyardLedger().deployments().containsKey(ship.id()) || !ship.supplyState().orders().isEmpty())
                || state.shipConstructionOrders().stream().anyMatch(order -> order.retirement() != null && fleet.ships().stream()
                        .anyMatch(ship -> ship.id().equals(order.retirement().replacementShipId())))
                || state.tradeRoutes().stream().anyMatch(route -> route.carrierCommitted() && route.assignedFreighterIds().stream().anyMatch(id -> fleet.ships().stream().anyMatch(ship -> id.equals(ship.id()))))) return null;
        if (fleet.location().current().kind() != FleetLocation.Kind.DOCKED && fleet.location().current().kind() != FleetLocation.Kind.SURFACE) return null;
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id()) && owner.equals(item.ownerEntityId())).findFirst().orElse(null);
        var source = ship == null ? null : FleetSupplySimulation.design(state, ship);
        if (source == null || !owner.equals(source.ownerEntityId())) return null;
        var choices = new ArrayList<AIShipLifecycleQuote>();
        double baseline = utility(source, source, ship, policy);
        var repair = repair(state, fleet, ship, source, baseline, policy); if (repair != null) choices.add(repair);
        var targets = new ArrayList<ShipDesign>(); targets.add(AIShipResearchRevision.current(state, source));
        state.shipDesigns().stream().filter(model -> owner.equals(model.ownerEntityId()) && Objects.equals(source.role(), model.role())
                && !source.id().equals(model.id())).sorted(Comparator.comparing(ShipDesign::id)).limit(Math.max(0, policy.maximumModelPreviews() - 1)).forEach(targets::add);
        for (var target : targets) {
            var registered = AIShipResearchRevision.register(state, target);
            var replacement = replacement(registered, fleet, ship, source, target, baseline, policy);
            var refit = ShipRefitQuote.inspect(registered, owner, shipId, target.id());
            if (ship.currentHullHealth() / ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH >= policy.minimumRefitHullFraction() && refit.ready()) {
                double credits = refit.estimatedCostCredits(), days = refit.workDays() + refit.queuedDays();
                var mapped = ship.withModuleDamage(refit.retainedDamage());
                double gain = gain(baseline, utility(source, target, mapped, policy), policy);
                if (acceptable(state, owner, credits, days, gain, policy) && (replacement == null
                        || credits <= replacement.estimatedCredits() * policy.maximumRefitBuildCostFraction()))
                    choices.add(choice(Action.REFIT, fleet, ship, target, credits, days, gain, policy, "Paid hardware improvement preserves this hull"));
            }
            if (replacement != null) choices.add(replacement);
        }
        return choices.stream().min(Comparator.comparingDouble(AIShipLifecycleQuote::score).thenComparing(item -> item.action().ordinal())
                .thenComparing(item -> item.target().id())).orElse(null);
    }
    private static AIShipLifecycleQuote repair(GameState state, Fleet fleet, ShipInstance ship, ShipDesign source, double baseline, ShipLifecyclePolicy policy) {
        var estimate = ShipRepairEstimate.inspect(state, fleet); double points = estimate.points();
        if (points <= 0) return null;
        var status = FleetRepairStatus.inspect(state, fleet); var base = status.base();
        double daily = (estimate.onboardCovered() ? status.fundedOnboardPoints() : 0)
                + (base == null || !base.blockers().isEmpty() || estimate.baseCredits() == null ? 0 : base.sharedPoints());
        if (daily <= 0) return null;
        double days = points / daily, credits = estimate.onboardCovered() && status.fundedOnboardPoints() > 0 ? 0 : estimate.baseCredits();
        var healthy = new ShipInstance(ship.id(), source.id(), ship.ownerEntityId(), ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH, 0, 0, Map.of());
        double gain = gain(baseline, utility(source, source, healthy, policy), policy);
        return acceptable(state, ship.ownerEntityId(), credits, days, gain, policy)
                ? choice(Action.REPAIR, fleet, ship, source, credits, days, gain, policy, "Funded workshop recovery costs less than the alternatives") : null;
    }
    private static AIShipLifecycleQuote replacement(GameState state, Fleet fleet, ShipInstance ship, ShipDesign source, ShipDesign target, double baseline, ShipLifecyclePolicy policy) {
        if (state.shipConstructionOrders().stream().anyMatch(order -> ship.ownerEntityId().equals(order.ownerEntityId()))) return null;
        var healthy = new ShipInstance(ship.id(), target.id(), ship.ownerEntityId(), ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH, 0, 0, Map.of());
        double gain = gain(baseline, utility(source, target, healthy, policy), policy);
        if (gain < policy.minimumGainFraction()) return null;
        var build = new QueueShipBuildCommand(ship.ownerEntityId(), target.id(), fleet.currentSystemId());
        if (!build.validate(state)) return null;
        var offer = build.resolveYard(state); var estimate = ShipConstructionRequirements.estimate(target);
        var capacity = ShipyardWorkCapacity.forYard(state, offer.providerOwnerId(), fleet.currentSystemId(), offer.yardId());
        if (!capacity.operational() || capacity.workPerDay() <= 0) return null;
        double days = estimate.workUnits() / capacity.workPerDay() + offer.queuedDays();
        double fee = offer.contract() == null ? 0 : estimate.workUnits() * offer.contract().feeCreditsPerWorkUnit();
        var held = ShipyardPayments.pay(state, ship.ownerEntityId(), offer.contract(), estimate.workUnits()); if (held == null) return null;
        var purchase = state.orbitalStations().stream().anyMatch(station -> offer.yardId().equals(station.id()))
                ? ConstructionMaterials.buyOrbitalUpTo(held, fleet.currentSystemId(), offer.yardId(), ship.ownerEntityId(), estimate.materialsKg(), Double.MAX_VALUE)
                : ConstructionMaterials.buyUpTo(held, offer.yardId(), ship.ownerEntityId(), estimate.materialsKg());
        if (!purchase.covers(estimate.materialsKg())) return null;
        double credits = EconomicOwnership.cash(held, ship.ownerEntityId()) - EconomicOwnership.cash(purchase.state(), ship.ownerEntityId()) + fee
                + (offer.contract() == null ? Math.ceil(days) * ShipRefitQuote.operatingCost(state, ship.ownerEntityId(), offer.yardId()) : 0);
        return acceptable(state, ship.ownerEntityId(), credits, days, gain, policy)
                ? choice(Action.REPLACE, fleet, ship, target, credits, days, gain, policy, "Funded new build restores capability; original ship is retained") : null;
    }
    private static boolean acceptable(GameState state, String owner, double cost, double days, double gain, ShipLifecyclePolicy policy) {
        var home = EconomicOwnership.empireFor(state, owner);
        boolean collective = home != null && owner.equals(home.id()) && home.societyStructure() != null
                && home.societyStructure().toLowerCase(Locale.ROOT).contains("hive");
        return Double.isFinite(cost) && Double.isFinite(days) && days <= policy.maximumDowntimeDays() && gain >= policy.minimumGainFraction()
                && (collective || EconomicOwnership.cash(state, owner) >= cost + policy.treasuryReserveCredits());
    }
    private static double gain(double baseline, double improved, ShipLifecyclePolicy policy) { return (improved - baseline) / Math.max(policy.utilityFloor(), baseline); }
    private static AIShipLifecycleQuote choice(Action action, Fleet fleet, ShipInstance ship, ShipDesign target, double cost, double days, double gain, ShipLifecyclePolicy policy, String reason) {
        return new AIShipLifecycleQuote(action, fleet.id(), ship.id(), target, cost, days, gain, (cost + days * policy.downtimeCreditsPerDay()) / Math.max(policy.utilityFloor(), gain), reason);
    }
    private static double utility(ShipDesign source, ShipDesign design, ShipInstance ship, ShipLifecyclePolicy policy) {
        double total = 0;
        for (var weight : policy.capabilityWeights().entrySet()) {
            if (EmpireFleetPlanner.combat(source.role()) && weight.getKey().equals("cargoCapacityKg")
                    || !EmpireFleetPlanner.combat(source.role()) && (weight.getKey().equals("weaponDamage") || weight.getKey().equals("shieldCapacity"))) continue;
            double healthy = capability(source, new ShipInstance("baseline", source.id(), source.ownerEntityId(), ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH, 0, 0, Map.of()), weight.getKey());
            double value = capability(design, ship, weight.getKey());
            total += weight.getValue() * (healthy > 0 ? value / healthy : value > 0 ? 1 : 0);
        }
        return total * Math.clamp(ship.currentHullHealth() / ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH, 0, 1);
    }
    private static double capability(ShipDesign design, ShipInstance ship, String key) {
        return switch (key) {
            case "cargoCapacityKg" -> design.maxCargoMassKg();
            case "thrustN" -> design.totalThrustN() * PropulsionDamage.condition(design, ship);
            case "sensorRangeKm" -> SensorModuleCatalog.profile(design, ship).rangeKm();
            case "batteryKwh" -> design.powerProfile() == null ? 0 : design.powerProfile(ship).batteryKwh();
            case "generatorKw" -> design.powerProfile() == null ? 0 : design.powerProfile(ship).chemicalKw() + design.powerProfile(ship).fissionKw() + design.powerProfile(ship).solarKw();
            default -> ship.moduleDamage().capability(design, key);
        };
    }
}
