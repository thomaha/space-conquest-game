package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;

/** Revalidates owned saved hardware and commits both ends of a finite supply operation on the tick. */
public record ResupplyAuxiliaryWeaponCommand(String ownerId, Kind kind, String auxiliaryId,
                                             String sourceShipId, int rounds, double chargeKwh) implements GameCommand {
    public enum Kind { LOAD_WING, BUY_BATTERY_AMMUNITION, CHARGE_BATTERY }
    @Override public boolean validate(GameState state) { return project(state) != state; }
    @Override public GameState apply(GameState state) { return project(state); }
    private GameState project(GameState state) {
        if (state == null || ownerId == null || kind == null || auxiliaryId == null || rounds < 0
                || !Double.isFinite(chargeKwh) || chargeKwh < 0 || rounds == 0 && chargeKwh == 0) return state;
        try {
            var forces = state.auxiliaryForces();
            if (kind == Kind.LOAD_WING) {
                var wing = forces.wings().stream().filter(w -> w.id().equals(auxiliaryId)).findFirst().orElse(null);
                var fleet = sourceFleet(state);
                if (wing == null || fleet == null || !wing.carrierShipId().equals(sourceShipId)) return state;
                var loaded = AuxiliaryWeaponResupply.loadWing(wing, source(fleet), rounds, chargeKwh);
                return replaceSource(state, fleet, loaded.carrier()).toBuilder().auxiliaryForces(forces.withWing(loaded.wing())).build();
            }
            var owned = forces.batteries().stream().filter(b -> b.battery().id().equals(auxiliaryId)
                    && ownerId.equals(b.ownerEntityId())).findFirst().orElse(null);
            if (owned == null || owned.battery().hullHealth() <= 0) return state;
            if (kind == Kind.BUY_BATTERY_AMMUNITION) {
                if (chargeKwh != 0 || rounds <= 0 || sourceShipId != null) return state;
                var bought = AuxiliaryWeaponResupply.buyBatteryAmmunition(state, ownerId, owned.battery(), rounds);
                return bought.purchased() ? bought.state().toBuilder().auxiliaryForces(forces.withBattery(bought.battery())).build() : state;
            }
            var fleet = sourceFleet(state);
            if (rounds != 0 || fleet == null) return state;
            var charged = AuxiliaryWeaponResupply.chargeBattery(state, ownerId, owned.battery(), sourceShipId, chargeKwh);
            return replaceSource(state, fleet, charged.source()).toBuilder().auxiliaryForces(forces.withBattery(charged.battery())).build();
        } catch (IllegalArgumentException | ArithmeticException ex) { return state; }
    }
    private Fleet sourceFleet(GameState state) {
        if (sourceShipId == null) return null;
        return state.fleets().stream().filter(f -> ownerId.equals(f.ownerEntityId()) && !f.hasInterstellarOrder()
                && !f.location().inTransit() && f.ships().stream().anyMatch(s -> sourceShipId.equals(s.id())
                && ownerId.equals(s.ownerEntityId()) && s.currentHullHealth() > 0)).findFirst().orElse(null);
    }
    private ShipInstance source(Fleet fleet) {
        return fleet.ships().stream().filter(s -> sourceShipId.equals(s.id())).findFirst().orElseThrow();
    }
    private GameState replaceSource(GameState state, Fleet fleet, ShipInstance updated) {
        var replacement = fleet.withShips(fleet.ships().stream().map(s -> s.id().equals(updated.id()) ? updated : s).toList());
        return state.withFleets(state.fleets().stream().map(f -> f.id().equals(fleet.id()) ? replacement : f).toList());
    }
}
