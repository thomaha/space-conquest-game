package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure fleet membership changes. Ship state and paid travel commitments remain intact. */
public final class FleetOrganization {
    private FleetOrganization() {}

    public static String transferProblem(GameState state, String owner, String sourceId, String targetId, List<String> ids) {
        String problem = selectionProblem(state, owner, sourceId, ids);
        if (problem != null) return problem;
        if (Objects.equals(sourceId, targetId)) return "Choose a different destination fleet.";
        Fleet source = find(state, sourceId), target = find(state, targetId);
        problem = fleetProblem(state, owner, target);
        if (problem != null) return problem;
        if (rescueCommitted(state, targetId)) return "Finish or interrupt the active rescue before changing its fleet membership.";
        if (!together(source, target)) return "Fleets must share a site, the same journey or velocity-matched coasting contact.";
        var joined = new ArrayList<>(target.ships());
        source.ships().stream().filter(ship -> ids.contains(ship.id())).forEach(joined::add);
        return routeProblem(state, joined);
    }

    public static String splitProblem(GameState state, String owner, String sourceId, List<String> ids, String newId, String name) {
        String problem = selectionProblem(state, owner, sourceId, ids);
        if (problem != null) return problem;
        if (!validName(name)) return "Enter a fleet name of 1 to 100 characters.";
        if (newId == null || newId.isBlank() || find(state, newId) != null) return "The new fleet ID must be unique.";
        if (ids.size() >= find(state, sourceId).ships().size()) return "Leave at least one ship in the original fleet; a single ship already travels alone.";
        return null;
    }

    public static GameState transfer(GameState state, String owner, String sourceId, String targetId, List<String> ids) {
        if (transferProblem(state, owner, sourceId, targetId, ids) != null) return state;
        Fleet source = find(state, sourceId), target = find(state, targetId);
        var moved = source.ships().stream().filter(ship -> ids.contains(ship.id())).toList();
        var remaining = source.ships().stream().filter(ship -> !ids.contains(ship.id())).toList();
        var joined = new ArrayList<>(target.ships());
        joined.addAll(moved);
        Map<String, Double> budget = new HashMap<>(target.interstellarFuelBudgetKg());
        moved.forEach(ship -> {
            if (source.interstellarFuelBudgetKg().containsKey(ship.id()))
                budget.put(ship.id(), source.interstellarFuelBudgetKg().get(ship.id()));
        });
        Map<String, JourneyPropulsion> propulsion = new HashMap<>(target.journeyPropulsion());
        moved.forEach(ship -> { if (source.journeyPropulsion().containsKey(ship.id())) propulsion.put(ship.id(), source.journeyPropulsion().get(ship.id())); });
        var fleets = new ArrayList<Fleet>();
        for (Fleet fleet : state.fleets()) {
            if (fleet.id().equals(sourceId)) {
                if (!remaining.isEmpty()) fleets.add(copy(source, source.id(), source.name(), remaining, source.interstellarFuelBudgetKg()));
            } else if (fleet.id().equals(targetId)) fleets.add(copy(target.withJourneyPropulsion(propulsion), target.id(), target.name(), joined, budget));
            else fleets.add(fleet);
        }
        return state.withFleets(fleets);
    }

    public static GameState split(GameState state, String owner, String sourceId, List<String> ids, String newId, String name) {
        if (splitProblem(state, owner, sourceId, ids, newId, name) != null) return state;
        Fleet source = find(state, sourceId);
        var fleets = new ArrayList<>(state.fleets());
        fleets.set(fleets.indexOf(source), copy(source, source.id(), source.name(), source.ships().stream()
                .filter(ship -> !ids.contains(ship.id())).toList(), source.interstellarFuelBudgetKg()));
        fleets.add(copy(source, newId, name.strip(), source.ships().stream().filter(ship -> ids.contains(ship.id())).toList(),
                source.interstellarFuelBudgetKg()));
        return state.withFleets(fleets);
    }

    public static String renameProblem(GameState state, String owner, String fleetId, String name) {
        String problem = fleetProblem(state, owner, state == null ? null : find(state, fleetId));
        return problem != null ? problem : validName(name) ? null : "Enter a fleet name of 1 to 100 characters.";
    }
    public static GameState rename(GameState state, String owner, String fleetId, String name) {
        if (renameProblem(state, owner, fleetId, name) != null) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(fleetId)
                ? copy(fleet, fleet.id(), name.strip(), fleet.ships(), fleet.interstellarFuelBudgetKg()) : fleet).toList());
    }

    private static String selectionProblem(GameState state, String owner, String sourceId, List<String> ids) {
        String problem = fleetProblem(state, owner, state == null ? null : find(state, sourceId));
        if (problem != null) return problem;
        if (rescueCommitted(state, sourceId)) return "Finish or interrupt the active rescue before changing its fleet membership.";
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size())
            return "Select distinct ships from the source fleet.";
        Fleet source = find(state, sourceId);
        if (ids.stream().anyMatch(id -> source.ships().stream().noneMatch(ship -> ship.id().equals(id))))
            return "A selected ship is no longer in the source fleet.";
        return null;
    }

    private static String fleetProblem(GameState state, String owner, Fleet fleet) {
        if (state == null || owner == null || fleet == null || !owner.equals(fleet.ownerEntityId()) || fleet.ships().isEmpty())
            return "Choose a nonempty fleet owned by you.";
        if (state.fleets().stream().filter(item -> item.id().equals(fleet.id())).count() != 1
                || fleet.ships().stream().anyMatch(ship -> !owner.equals(ship.ownerEntityId())
                || state.fleets().stream().flatMap(item -> item.ships().stream()).filter(item -> item.id().equals(ship.id())).count() != 1))
            return "Fleet ownership or ship membership is inconsistent.";
        return null;
    }

    private static String routeProblem(GameState state, List<ShipInstance> ships) {
        Set<String> ids = new HashSet<>();
        ships.forEach(ship -> ids.add(ship.id()));
        long routes = state.tradeRoutes().stream().filter(route -> route.isActive())
                .filter(route -> route.assignedFreighterIds().stream().anyMatch(ids::contains)).count();
        return routes > 1 ? "Cancel one of the active trade routes before combining their carriers into one fleet." : null;
    }

    private static boolean rescueCommitted(GameState state, String fleetId) {
        return state.fleets().stream().anyMatch(fleet -> fleet.flightMotion() != null && fleet.flightMotion().trajectory() != null
                && fleet.flightMotion().trajectory().rescueOrder() != null
                && (fleet.id().equals(fleetId) || fleet.flightMotion().trajectory().rescueOrder().targetFleetId().equals(fleetId)));
    }

    /** Membership may change in flight only when the two fleets have the exact same itinerary and progress. */
    public static boolean together(Fleet source, Fleet target) {
        if (!Objects.equals(source.ownerEntityId(), target.ownerEntityId())
                || !Objects.equals(source.currentSystemId(), target.currentSystemId())) return false;
        if (RescueRendezvous.contact(source, target)) return true;
        if (idle(source) && idle(target)) return source.location().current().equals(target.location().current());
        return Objects.equals(source.targetSystemId(), target.targetSystemId()) && source.location().equals(target.location())
                && source.interstellarMode().equals(target.interstellarMode()) && source.isInWarp() == target.isInWarp()
                && source.transitProgress() == target.transitProgress() && Objects.equals(source.flightMotion(), target.flightMotion())
                && source.interstellarDistanceMeters() == target.interstellarDistanceMeters()
                && source.interstellarAccelerationMps2() == target.interstellarAccelerationMps2()
                && source.interstellarPeakSpeedMps() == target.interstellarPeakSpeedMps()
                && source.interstellarElapsedDays() == target.interstellarElapsedDays()
                && source.interstellarTravelDays() == target.interstellarTravelDays()
                && source.coordinateX() == target.coordinateX() && source.coordinateY() == target.coordinateY();
    }
    private static boolean idle(Fleet fleet) {
        return !fleet.hasInterstellarOrder() && !fleet.isInWarp() && !fleet.location().inTransit()
                && fleet.flightMotion() == null && fleet.transitProgress() == 0 && fleet.interstellarMode().isBlank();
    }
    private static boolean validName(String name) { return name != null && !name.isBlank() && name.strip().length() <= 100; }
    public static Fleet find(GameState state, String id) {
        return state.fleets().stream().filter(fleet -> Objects.equals(fleet.id(), id)).findFirst().orElse(null);
    }
    private static Fleet copy(Fleet fleet, String id, String name, List<ShipInstance> ships, Map<String, Double> budget) {
        Map<String, Double> retained = new HashMap<>();
        Map<String, JourneyPropulsion> propulsion = new HashMap<>();
        ships.forEach(ship -> { if (fleet.journeyPropulsion().containsKey(ship.id())) propulsion.put(ship.id(), fleet.journeyPropulsion().get(ship.id())); });
        ships.forEach(ship -> { if (budget.containsKey(ship.id())) retained.put(ship.id(), budget.get(ship.id())); });
        return new Fleet(id, name, fleet.ownerEntityId(), fleet.currentSystemId(), fleet.targetSystemId(),
                fleet.coordinateX(), fleet.coordinateY(), fleet.transitProgress(), fleet.isInWarp(), fleet.fleetStance(), ships,
                fleet.location(), fleet.interstellarMode(), fleet.interstellarTravelDays(), fleet.interstellarDistanceMeters(),
                fleet.interstellarAccelerationMps2(), fleet.interstellarElapsedDays(), fleet.interstellarPeakSpeedMps(), retained, fleet.flightMotion(), propulsion);
    }
}
