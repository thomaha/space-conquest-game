package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Records committed service transitions. Calling a quote cannot alter the supplied snapshot. */
public final class FleetServiceHistory {
    private FleetServiceHistory() {}
    public static GameState orders(GameState before, GameState after) {
        if (before == after) return after;
        var events = new ArrayList<>(after.fleetServiceEvents());
        for (var old : before.fleets()) {
            var current = FleetOrganization.find(after, old.id());
            if (current == null) continue;
            boolean was = FleetWeaponRecovery.active(old), now = FleetWeaponRecovery.active(current);
            if (!was && now) add(events, after, current, FleetServiceEvent.Kind.MISSION_PAUSED, mission(current));
            if (now && current.location().inTransit() && !current.location().equals(old.location())) {
                var stage = current.interceptOrder().protection().stage();
                add(events, after, current, stage == FleetProtectionAssignment.Stage.RETURNING
                        ? FleetServiceEvent.Kind.RETURN_STARTED : FleetServiceEvent.Kind.SERVICE_DEPARTURE,
                        "Destination " + current.location().destination().entityId());
            }
            if (was && !now) add(events, after, current,
                    current.guardOrder() != null || current.escortOrder() != null || current.interceptOrder() != null
                            ? FleetServiceEvent.Kind.MISSION_RESUMED : FleetServiceEvent.Kind.RECOVERY_COMPLETED,
                    mission(current));
        }
        return events.equals(after.fleetServiceEvents()) ? after : after.toBuilder().fleetServiceEvents(trim(events)).build();
    }
    public static GameState command(GameState before, GameState after, String fleetId) {
        if (before == after) return after;
        var recorded = orders(before, after);
        var old = FleetOrganization.find(before, fleetId); var fleet = FleetOrganization.find(after, fleetId);
        if (old == null || fleet == null) return recorded;
        var resources = new TreeMap<String, Double>(); double charge = 0;
        for (var ship : fleet.ships()) {
            var source = old.ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElse(null);
            if (source == null) continue;
            var design = FleetSupplySimulation.design(after, ship);
            var ammunition = design == null ? Set.<String>of() : design.combatModules().values().stream()
                    .map(ShipModule::ammunitionId).filter(id -> !id.isBlank()).collect(java.util.stream.Collectors.toSet());
            var cargo = new HashMap<String, Double>();
            ship.storedCargoKg().forEach((id, kg) -> { if (ammunition.contains(id)) cargo.put(id, kg); });
            gained(resources, source.storedCargoKg(), cargo);
            gained(resources, ShipPowerProcessor.reserves(source).generatorMaterialsKg(), ShipPowerProcessor.reserves(ship).generatorMaterialsKg());
            charge += Math.max(0, ShipPowerProcessor.reserves(ship).chargedInputKwhToday()
                    - ShipPowerProcessor.reserves(source).chargedInputKwhToday());
        }
        if (resources.isEmpty() && charge <= 0) return recorded;
        double credits = Math.max(0, cash(before, fleet.ownerEntityId()) - cash(after, fleet.ownerEntityId()));
        var events = new ArrayList<>(recorded.fleetServiceEvents());
        events.add(new FleetServiceEvent(after.turn(), fleet.ownerEntityId(), fleet.id(), FleetServiceEvent.Kind.SUPPLIES_PURCHASED,
                "Actual local service at " + fleet.location().current().entityId(), resources, charge, credits));
        return recorded.toBuilder().fleetServiceEvents(trim(events)).build();
    }
    private static void gained(Map<String, Double> result, Map<String, Double> before, Map<String, Double> after) {
        after.forEach((id, kg) -> { double gain = kg - before.getOrDefault(id, 0.0); if (gain > 0) result.merge(id, gain, Double::sum); });
    }
    private static double cash(GameState state, String owner) {
        return state.empires().stream().filter(item -> owner.equals(item.id())).mapToDouble(com.spaceconquest.engine.Empire::treasuryCredits)
                .findFirst().orElseGet(() -> state.corporations().stream().filter(item -> owner.equals(item.id()))
                        .mapToDouble(com.spaceconquest.engine.Corporation::liquidCapitalReserves).findFirst().orElse(0));
    }
    private static void add(List<FleetServiceEvent> events, GameState state, Fleet fleet, FleetServiceEvent.Kind kind, String detail) {
        events.add(new FleetServiceEvent(state.turn(), fleet.ownerEntityId(), fleet.id(), kind, detail, Map.of(), 0, 0));
    }
    private static List<FleetServiceEvent> trim(List<FleetServiceEvent> events) {
        return List.copyOf(events.subList(Math.max(0, events.size() - 2000), events.size()));
    }
    public static String mission(Fleet fleet) {
        var intent = FleetWeaponRecovery.active(fleet) ? fleet.interceptOrder().protection().repairIntent() : null;
        var guard = intent == null ? fleet.guardOrder() : intent.guard();
        var escort = intent == null ? fleet.escortOrder() : intent.escort();
        var pursuit = intent == null ? fleet.interceptOrder() : intent.intercept();
        if (guard != null) return "Guard " + guard.targetId();
        if (escort != null) return "Escort " + escort.escortFleetId();
        if (pursuit != null && pursuit.protection() == null) return "Pursue " + pursuit.targetFleetId();
        return "Return to original site";
    }
}
