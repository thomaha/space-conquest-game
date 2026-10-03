package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerResupply;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipSolarEnvironment;

/** Stages array changes without stopping a spacecraft; atmospheric maneuvers cannot deploy arrays. */
public record SetShipSolarArraysCommand(String shipId, boolean deployed) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && state.fleets().stream().filter(fleet -> !deployed || !fleet.location().inTransit()
                || !ShipSolarEnvironment.atmosphericTravel(state, fleet, fleet.location().destination()))
                .flatMap(fleet -> fleet.ships().stream())
                .anyMatch(ship -> ship.id().equals(shipId) && state.shipDesigns().stream().anyMatch(design ->
                        design.id().equals(ship.designId()) && design.powerProfile() != null && design.powerProfile().solarKw() > 0));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var ship = state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                .filter(item -> item.id().equals(shipId)).findFirst().orElseThrow();
        var power = ShipPowerProcessor.reserves(ship);
        return ShipPowerResupply.replace(state, ship.withPowerState(new ShipPowerState(power.generatorMaterialsKg(),
                power.chemicalMixture(), power.reactorFuel(), power.batteryChargeKwh(), deployed, power.arrayCondition(),
                power.orientationFraction(), power.unmetEssentialHours(), power.unmetDriveKwh(), power.unmetCargoKwh(),
                power.lastUnmetEssentialKwh(), power.chargedInputKwhToday(), power.cargoPreservation())));
    }
}
