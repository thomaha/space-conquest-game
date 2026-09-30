package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.logistics.LaunchService;

/** Launches purchased ground freight to a real ship in orbit using an installed mass driver. */
public record LaunchMassDriverPayloadCommand(
        String massDriverId,
        String ownerEntityId,
        String materialId,
        double payloadTons,
        String targetShipId
) implements GameCommand {
    public LaunchMassDriverPayloadCommand(String massDriverId, String materialId,
                                          double payloadTons, String targetShipId) {
        this(massDriverId, null, materialId, payloadTons, targetShipId);
    }

    /** Former power-guess command cannot identify an orbital receiver. */
    @Deprecated(forRemoval = true)
    public LaunchMassDriverPayloadCommand(String massDriverId, String ownerEntityId,
                                          String materialId, double payloadTons,
                                          double ignoredAvailablePowerKw) {
        this(massDriverId, ownerEntityId, materialId, payloadTons, (String) null);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || massDriverId == null || materialId == null || targetShipId == null
                || !Double.isFinite(payloadTons) || payloadTons <= 0.0) return false;
        IndustrialFacility driver = state.industrialFacilities().stream()
                .filter(item -> massDriverId.equals(item.id())
                        && "mass_driver".equals(item.applicationId()) && item.tier() > 0)
                .findFirst().orElse(null);
        if (driver == null) return false;
        boolean ownerMatches = ownerEntityId == null || state.fleets().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .anyMatch(ship -> targetShipId.equals(ship.id())
                        && ownerEntityId.equals(ship.ownerEntityId()));
        return ownerMatches && new LoadOrbitalCargoCommand(targetShipId, driver.planetId(),
                materialId, payloadTons * 1000.0, LaunchService.Mode.MASS_DRIVER,
                driver.id()).validate(state);
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        IndustrialFacility driver = state.industrialFacilities().stream()
                .filter(item -> massDriverId.equals(item.id())).findFirst().orElseThrow();
        return new LoadOrbitalCargoCommand(targetShipId, driver.planetId(), materialId,
                payloadTons * 1000.0, LaunchService.Mode.MASS_DRIVER, driver.id()).apply(state);
    }
}