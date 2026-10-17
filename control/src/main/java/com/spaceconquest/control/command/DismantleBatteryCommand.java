package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;

/** Removes owned hardware and its stores on the command tick; no speculative salvage is credited. */
public record DismantleBatteryCommand(String ownerId, String batteryId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && ownerId != null && batteryId != null && state.auxiliaryForces().batteries().stream()
                .anyMatch(b -> ownerId.equals(b.ownerEntityId()) && batteryId.equals(b.battery().id())
                        && BatteryMaintenance.accessible(state, ownerId, b.battery()));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var forces = state.auxiliaryForces();
        return state.toBuilder().auxiliaryForces(new AuxiliaryForces(forces.wings(),
                forces.batteries().stream().filter(b -> !batteryId.equals(b.battery().id())).toList(), forces.orders())).build();
    }
}
