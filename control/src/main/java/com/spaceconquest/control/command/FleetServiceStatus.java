package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Immutable service explanation assembled from discarded authoritative quotes. */
public record FleetServiceStatus(String phase, String mission, String destination,
                                 List<String> blockers, List<FleetServiceEvent> history) {
    public FleetServiceStatus { blockers = List.copyOf(blockers); history = List.copyOf(history); }
    public static FleetServiceStatus inspect(GameState state, Fleet fleet, String owner) {
        if (state == null || fleet == null || !owner.equals(fleet.ownerEntityId()))
            return new FleetServiceStatus("Unavailable", "", "", List.of(), List.of());
        boolean active = FleetWeaponRecovery.active(fleet);
        var assignment = active ? fleet.interceptOrder().protection() : null;
        boolean travelling = fleet.location().inTransit() && !RecoveredParkingTravel.ready(fleet);
        String phase = !active ? "No paused weapon service" : travelling
                ? assignment.stage() == FleetProtectionAssignment.Stage.RETURNING && !FleetWeaponRecovery.needs(state, fleet)
                    ? "Returning to mission" : "Travelling for service"
                : assignment.stage() == FleetProtectionAssignment.Stage.REPAIRING ? "Waiting for paid repair"
                : assignment.stage() == FleetProtectionAssignment.Stage.RESUPPLYING ? "Waiting for weapon resupply"
                : "Waiting for funded service or return";
        String destination = fleet.location().inTransit() ? fleet.location().destination().entityId()
                : assignment == null ? "None" : assignment.serviceTargetId().isBlank() ? "Not selected" : assignment.serviceTargetId();
        var blockers = new LinkedHashSet<String>(); boolean repair = false;
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null) { blockers.add(ship.id() + ": blueprint unavailable; service requirements unknown"); continue; }
            double electronics = ship.moduleDamage().damageByMount().values().stream().mapToDouble(Double::doubleValue).sum();
            if (electronics > 0) {
                blockers.add(String.format(Locale.ROOT, "%s: damaged components %.2f points; paid repair required", ship.id(), electronics));
                repair = true;
            }
            if (assignment != null && assignment.stage() == FleetProtectionAssignment.Stage.REPAIRING
                    && ship.currentHullHealth() < ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH) repair = true;
            for (var weapon : ShipWeaponReadiness.inspect(design, ship, true).weapons()) {
                if (weapon.blocker() == ShipWeaponReadiness.Blocker.NONE) continue;
                blockers.add(ship.id() + " / " + weapon.name() + ": " + weapon.status());
                if (weapon.blocker() == ShipWeaponReadiness.Blocker.AMMUNITION)
                    blockers.add("Ammunition: " + AmmunitionResupplyQuote.inspect(state, owner, ship.id(), weapon.moduleId()).reason());
                if (weapon.blocker() == ShipWeaponReadiness.Blocker.POWER) power(state, fleet, ship, design, blockers);
                repair |= weapon.blocker() == ShipWeaponReadiness.Blocker.DISABLED || weapon.blocker() == ShipWeaponReadiness.Blocker.CONDITION;
            }
        }
        if (active && !travelling) {
            var home = FleetProtectionReturn.destination(state, fleet);
            if (home == null) blockers.add("Original mission target is no longer valid");
            else if (!fleet.location().stationaryAt(home) && !FleetReturnTravel.ready(state, fleet, home))
                blockers.add("Return travel is not funded by current propulsion fuel, electrical stores and reserves");
            if (!FleetWeaponRecovery.needs(state, fleet) && FleetWeaponRecovery.needsBeforeReturn(state, fleet))
                blockers.add("The return journey would leave insufficient firing power");
            if (FleetThreatAssessment.caution(state, fleet).action() != FleetThreatAssessment.Action.NO_CONTACT)
                blockers.add("Detected or remembered hostile contact restricts service travel");
        }
        if (repair || assignment != null && assignment.stage() == FleetProtectionAssignment.Stage.REPAIRING) {
            var quote = FleetRepairStatus.inspect(state, fleet);
            if (quote.base() == null && quote.providers().isEmpty()) blockers.add("Repair workshop unavailable at this site or aboard the fleet");
            if (quote.base() != null) {
                quote.base().blockers().forEach(reason -> blockers.add("Base repair: " + reason));
                quote.base().shortages().forEach((id, kg) -> blockers.add("Repair materials: " + id + " short by " + kg + " kg"));
            }
            quote.providers().forEach(provider -> {
                provider.blockers().forEach(reason -> blockers.add("Onboard repair: " + reason));
                provider.firstRecipeShortages().forEach((id, kg) -> blockers.add("Onboard repair materials: " + id + " short by " + kg + " kg"));
            });
        }
        var history = state.fleetServiceEvents().stream().filter(event -> owner.equals(event.ownerId()) && fleet.id().equals(event.fleetId())).toList();
        return new FleetServiceStatus(phase, active ? FleetServiceHistory.mission(fleet) : "None", destination,
                List.copyOf(blockers), history.subList(Math.max(0, history.size() - 8), history.size()));
    }
    private static void power(GameState state, Fleet fleet, ShipInstance ship, ShipDesign design, Set<String> blockers) {
        var site = fleet.location().current();
        if (site.kind() != FleetLocation.Kind.SURFACE && site.kind() != FleetLocation.Kind.DOCKED)
            blockers.add("Grid charging requires a surface or docked connection");
        var grid = state.powerGrids().stream().filter(item -> site.entityId().equals(item.entityId())).findFirst().orElse(null);
        if (grid == null || grid.currentStoredKwh() <= 0) blockers.add("Grid charge: no stored local electricity");
        else if (grid.isDeficitBrownoutActive()) blockers.add("Grid charge: local brownout prevents charging");
        var profile = design.powerProfile(ship); var power = ShipPowerProcessor.reserves(ship);
        if (profile.chargeKw() <= 0 || profile.batteryKwh() <= 0) blockers.add("Grid charge: no working battery charger");
        else if (power.chargedInputKwhToday() >= profile.chargeKw() * 24) blockers.add("Grid charge: daily charging allowance exhausted");
        if (state.empires().stream().filter(empire -> fleet.ownerEntityId().equals(empire.id())).anyMatch(empire -> empire.treasuryCredits() <= 0))
            blockers.add("Service funds unavailable");
    }
}
