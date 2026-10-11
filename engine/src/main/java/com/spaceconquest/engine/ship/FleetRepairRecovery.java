package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeLegReadiness;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import java.util.*;

/** Bounded repair quotes preserve protection intent and never commit forecast purchases or work. */
public final class FleetRepairRecovery {
    public static final double RETURN_HULL_FRACTION = .8;
    private FleetRepairRecovery() {}

    public static GameState recover(GameState state, Fleet fleet, FleetLocation.Site destination) {
        if (need(state, fleet) <= 1e-9 || FleetThreatAssessment.assess(state, fleet).action() != FleetThreatAssessment.Action.NO_CONTACT)
            return state;
        var powered = ShipPowerProcessor.advanceDay(state.withFleets(List.of(fleet))).getFirst();
        if (need(state, powered) + 1e-9 < need(state, fleet))
            return FleetReturnTravel.replace(state, FleetReturnTravel.stage(fleet, FleetProtectionAssignment.Stage.REPAIRING, ""));
        var bases = state.orbitalStations().stream().filter(base -> safe(state, fleet, base))
                .sorted(Comparator.comparingDouble((OrbitalStation base) -> FleetThreatAssessment.distance(state, fleet,
                        FleetLocation.at(FleetLocation.Site.docked(base.id())))).thenComparing(OrbitalStation::id)).toList();
        for (var base : bases) {
            var site = FleetLocation.Site.docked(base.id());
            boolean docked = fleet.location().stationaryAt(site);
            var plan = docked ? null : FleetReturnTravel.localPlan(state, fleet, site);
            if (!docked && plan == null) continue;
            var arrival = docked ? fleet : LocalTravel.arrivalPreview(state, fleet, site, plan);
            var projected = FleetReturnTravel.replace(state.withTurn(state.turn() + (docked ? 0 : (long) Math.ceil(plan.days()))), arrival);
            var repaired = BaseRepairProcessor.advanceDay(projected);
            if (need(repaired, FleetOrganization.find(repaired, fleet.id())) + 1e-9 >= need(projected, arrival)) continue;
            var hub = repaired.commercialHubs().stream().filter(item -> base.id().equals(item.entityId())).findFirst().orElse(null);
            var supplied = hub == null ? repaired : TradeLegReadiness.refillAtPort(repaired, FleetOrganization.find(repaired, fleet.id()), hub);
            if (!FleetReturnTravel.ready(supplied, FleetOrganization.find(supplied, fleet.id()), destination)) continue;
            var ordered = FleetReturnTravel.stage(fleet, FleetProtectionAssignment.Stage.REPAIRING, base.id());
            return FleetReturnTravel.replace(state, docked ? ordered : LocalTravel.depart(ordered, site, plan));
        }
        return state;
    }

    /** A lost or exhausted service releases the fleet to ordinary funded return and fuel recovery. */
    public static GameState release(GameState state, Fleet fleet, String target) {
        var hub = state.commercialHubs().stream().filter(item -> target.equals(item.entityId())).findFirst().orElse(null);
        var result = hub != null && FleetPositioning.atHub(state, fleet, hub) ? TradeLegReadiness.refillAtPort(state, fleet, hub) : state;
        return FleetReturnTravel.replace(result, FleetReturnTravel.stage(FleetOrganization.find(result, fleet.id()),
                FleetProtectionAssignment.Stage.RETURNING, ""));
    }

    static double need(GameState state, Fleet fleet) {
        double result = 0;
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || ship.currentHullHealth() <= 0 || !fleet.ownerEntityId().equals(ship.ownerEntityId())) continue;
            result += Math.max(0, ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH * RETURN_HULL_FRACTION - ship.currentHullHealth());
            for (int mount = 0; mount < design.equippedModuleIds().size(); mount++)
                if (ShipModuleDamage.damageableModule(design.equippedModuleIds().get(mount)) != null)
                    result += ship.moduleDamage().damageByMount().getOrDefault(mount, 0.0);
        }
        return result;
    }

    private static boolean safe(GameState state, Fleet fleet, OrbitalStation base) {
        if (!fleet.currentSystemId().equals(base.systemId()) || BaseRepairCatalog.capacity(state, base) <= 1e-9
                || !BaseRepairCatalog.accessible(state, base, fleet.ownerEntityId())) return false;
        return state.fleets().stream().filter(enemy -> FleetInterceptProcessor.contact(state, fleet, enemy))
                .noneMatch(enemy -> FleetThreatAssessment.distance(state, enemy, FleetLocation.at(FleetLocation.Site.docked(base.id())))
                        <= FleetThreatAssessment.THREAT_RADIUS_METERS);
    }
}
