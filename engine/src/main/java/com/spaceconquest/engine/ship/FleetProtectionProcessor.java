package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import java.util.*;

/** Tick-owned threat response and funded coordinated escape for guards and escorted convoys. */
public final class FleetProtectionProcessor {
    public enum Outcome { NO_CONTACT, HOLD, INTERCEPTING, CONTINUE_TO_SAFETY, ESCAPE_BLOCKED, ESCAPING }
    public record Decision(FleetThreatAssessment.Assessment assessment, Outcome outcome,
                           FleetLocation.Site destination, Map<String, LocalTravel.Plan> plans, GameState interceptState) {
        public Decision { plans = Map.copyOf(plans); }
        public Decision(FleetThreatAssessment.Assessment assessment, Outcome outcome, FleetLocation.Site destination,
                        Map<String, LocalTravel.Plan> plans) { this(assessment, outcome, destination, plans, null); }
    }
    private FleetProtectionProcessor() {}

    public static Decision decide(GameState state, Fleet fleet, List<Race> races) {
        return decide(state, fleet, races, false);
    }

    private static Decision decide(GameState state, Fleet fleet, List<Race> races, boolean combat) {
        var assessment = FleetThreatAssessment.assess(state, fleet);
        if (!combat && assessment.action() == FleetThreatAssessment.Action.HOLD) {
            var intercept = FleetProtectionIntercept.preview(state, fleet, assessment);
            if (intercept != null) return new Decision(assessment, Outcome.INTERCEPTING, null, Map.of(), intercept);
        }
        if (assessment.action() != FleetThreatAssessment.Action.ESCAPE)
            return new Decision(assessment, assessment.action() == FleetThreatAssessment.Action.NO_CONTACT
                    ? Outcome.NO_CONTACT : Outcome.HOLD, null, Map.of());
        var group = FleetThreatAssessment.support(state, fleet);
        if (!FleetThreatAssessment.available(fleet) || group.isEmpty() || group.stream().anyMatch(member -> committed(state, member, combat)))
            return new Decision(assessment, Outcome.ESCAPE_BLOCKED, null, Map.of());
        if (fleet.location().inTransit() && group.stream().allMatch(member -> MovingFleetEscape.continuesSafely(state, member, assessment, races)))
            return new Decision(assessment, Outcome.CONTINUE_TO_SAFETY, fleet.location().destination(), Map.of());
        for (var destination : destinations(state, fleet, assessment)) {
            var plans = new LinkedHashMap<String, LocalTravel.Plan>();
            for (var member : group) {
                var plan = member.location().inTransit() ? MovingFleetEscape.plan(state, member, destination) : LocalTravel.plan(state, member, destination);
                if (plan == null || plan.orbital() != null && plan.orbital().waitSeconds() > 0
                        || !ShipPowerForecast.ready(ShipPowerForecast.departure(state, member, destination, plan, null))
                        || !PassengerTransitProcessor.canSustainJourney(state, member, races, plan.days())
                        || member.location().inTransit() && !MovingFleetEscape.improvesSafety(state, member, plan, assessment)) break;
                plans.put(member.id(), plan);
            }
            if (plans.size() == group.size()) return new Decision(assessment, Outcome.ESCAPING, destination, plans);
        }
        return new Decision(assessment, Outcome.ESCAPE_BLOCKED, null, Map.of());
    }

    private static boolean committed(GameState state, Fleet fleet, boolean combat) {
        return fleet.location().current().kind() == FleetLocation.Kind.SURFACE
                || fleet.interceptOrder() != null && !automaticProtection(fleet)
                    && (!combat || fleet.interceptOrder().protection() != null)
                || fleet.ships().stream().anyMatch(ship -> !ship.supplyState().orders().isEmpty())
                || state.tradeRoutes().stream().filter(route -> route.isActive() || route.onboardKg() > 0)
                .anyMatch(route -> com.spaceconquest.engine.logistics.TradeEscape.associated(route, fleet)
                    && !com.spaceconquest.engine.logistics.TradeEscape.canPause(state, route, fleet));
    }

    /** Combat withdrawal overrides pursuit but retains its intent and all real departure gates. */
    public static GameState withdrawFromCombat(GameState state, Fleet fleet, List<Race> races) {
        var decision = decide(state, fleet, races, true);
        if (decision.outcome() != Outcome.ESCAPING) return state;
        var paused = com.spaceconquest.engine.logistics.TradeEscape.pause(state, new ArrayList<>(decision.plans().keySet()));
        return paused.withFleets(paused.fleets().stream().map(member -> decision.plans().containsKey(member.id())
                ? escape(paused, member, decision.destination(), decision.plans().get(member.id())) : member).toList());
    }

    private static List<FleetLocation.Site> destinations(GameState state, Fleet fleet, FleetThreatAssessment.Assessment assessment) {
        var sites = new ArrayList<FleetLocation.Site>();
        var body = MovingFleetEscape.body(state, fleet);
        state.orbitalStations().stream().filter(station -> station.isOperational()
                && fleet.ownerEntityId().equals(station.ownerEntityId()) && fleet.currentSystemId().equals(station.systemId())
                && (body == null ? station.planetOrbitId() == null || station.planetOrbitId().isBlank()
                    || station.systemId().equals(station.planetOrbitId()) : body.id().equals(station.planetOrbitId())))
                .forEach(station -> sites.add(FleetLocation.Site.docked(station.id())));
        if (body != null) for (double altitude : new double[]{500, 1000, 2000, 4000, 8000})
            sites.add(FleetLocation.Site.orbit(body.id(), altitude));
        return sites.stream().distinct().filter(site -> !site.equals(fleet.location().current()) || fleet.location().inTransit() && body != null)
                .filter(site -> assessment.hostileFleetIds().stream().map(id -> FleetOrganization.find(state, id)).allMatch(enemy ->
                        FleetThreatAssessment.distance(state, enemy, FleetLocation.at(site))
                                > FleetThreatAssessment.distance(state, enemy, fleet.location()) + 1_000_000))
                .filter(site -> state.fleets().stream().filter(enemy -> FleetInterceptProcessor.contact(state, fleet, enemy))
                        .noneMatch(enemy -> !enemy.location().inTransit() && enemy.location().isAt(site)))
                .sorted(Comparator.comparingDouble((FleetLocation.Site site) -> assessment.hostileFleetIds().stream()
                        .map(id -> FleetOrganization.find(state, id)).mapToDouble(enemy -> FleetThreatAssessment.distance(state, enemy, FleetLocation.at(site)))
                        .min().orElse(0)).reversed().thenComparing(FleetLocation.Site::toString)).limit(8).toList();
    }

    public static GameState advance(GameState state, List<Race> races) {
        var handled = new HashSet<String>();
        var result = state;
        for (var fleet : state.fleets().stream().sorted(Comparator.comparing(Fleet::id)).toList()) {
            if (handled.contains(fleet.id()) || !FleetThreatAssessment.available(fleet)
                    || (fleet.guardOrder() == null || !fleet.guardOrder().validTarget(state, fleet))
                        && fleet.escortOrder() == null && !automaticProtection(fleet) && !hasDetachedEscort(result, fleet)) continue;
            var decision = decide(result, FleetOrganization.find(result, fleet.id()), races);
            handled.addAll(decision.assessment().friendlyFleetIds());
            if (decision.outcome() == Outcome.INTERCEPTING) { result = decision.interceptState(); continue; }
            if (decision.outcome() == Outcome.CONTINUE_TO_SAFETY) {
                var members = decision.assessment().friendlyFleetIds();
                result = result.withFleets(result.fleets().stream().map(member -> members.contains(member.id()) && automaticProtection(member)
                        ? member.withInterceptOrder(member.interceptOrder().withProtection(member.interceptOrder().protection().afterEscape()))
                        : member).toList());
                continue;
            }
            if (decision.outcome() != Outcome.ESCAPING) continue;
            result = com.spaceconquest.engine.logistics.TradeEscape.pause(result, new ArrayList<>(decision.plans().keySet()));
            var departing = result;
            result = result.withFleets(result.fleets().stream().map(member -> decision.plans().containsKey(member.id())
                    ? escape(departing, member, decision.destination(), decision.plans().get(member.id()))
                    : member).toList());
        }
        return result;
    }

    private static Fleet depart(Fleet fleet, FleetLocation.Site destination, LocalTravel.Plan plan) {
        return fleet.location().inTransit() ? MovingFleetEscape.depart(fleet, destination, plan) : LocalTravel.depart(fleet, destination, plan);
    }
    private static Fleet escape(GameState state, Fleet fleet, FleetLocation.Site destination, LocalTravel.Plan plan) {
        var guard = fleet.guardOrder();
        var departed = depart(fleet.withGuardOrder(null).withInterceptOrder(null), destination, plan);
        if (automaticProtection(fleet)) return departed.withInterceptOrder(fleet.interceptOrder().withProtection(fleet.interceptOrder().protection().afterEscape()));
        if (guard == null || !guard.validTarget(state, fleet)) {
            var assignment = new FleetProtectionAssignment(null, "", fleet.id(), fleet.location().current(),
                    FleetProtectionAssignment.Stage.RETURNING, "", true, FleetRepairIntent.capture(fleet));
            return departed.withInterceptOrder(new FleetInterceptOrder(fleet.ownerEntityId(), fleet.id(), fleet.currentSystemId(), assignment));
        }
        var assignment = new FleetProtectionAssignment(guard, "", fleet.id(), guard.site(),
                FleetProtectionAssignment.Stage.RETURNING, "", true);
        return departed.withInterceptOrder(new FleetInterceptOrder(fleet.ownerEntityId(), guard.targetId(), fleet.currentSystemId(), assignment));
    }
    private static boolean automaticProtection(Fleet fleet) {
        var order = fleet.interceptOrder();
        if (order == null || order.protection() == null) return false;
        var assignment = order.protection();
        return (assignment.guard() != null || !assignment.convoyFleetId().isBlank() || assignment.repairIntent() != null)
                && assignment.stage() != FleetProtectionAssignment.Stage.SUPPLYING;
    }
    private static boolean hasDetachedEscort(GameState state, Fleet convoy) {
        return state.fleets().stream().anyMatch(escort -> !escort.id().equals(convoy.id())
                && convoy.ownerEntityId().equals(escort.ownerEntityId())
                && escort.ships().stream().anyMatch(ship -> ship.currentHullHealth() > 0)
                && automaticProtection(escort) && convoy.id().equals(escort.interceptOrder().protection().convoyFleetId()));
    }
}
