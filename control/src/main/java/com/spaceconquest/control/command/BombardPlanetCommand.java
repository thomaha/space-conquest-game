package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.combat.OrbitalBombardmentProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to execute orbital planetary bombardment (kinetic darts, fission warheads, or planet-cracker).
 */
public record BombardPlanetCommand(
        String attackerEmpireId,
        String targetSystemId,
        String targetPlanetId,
        String bombType
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || attackerEmpireId == null || targetSystemId == null || targetPlanetId == null) {
            return false;
        }
        return state.solarSystems().stream().anyMatch(s -> s.id().equals(targetSystemId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        SolarSystem targetSys = state.solarSystems().stream()
                .filter(s -> s.id().equals(targetSystemId))
                .findFirst()
                .orElse(null);
        if (targetSys == null) return state;

        Planet targetPlanet = targetSys.planets().stream()
                .filter(p -> p.id().equals(targetPlanetId))
                .findFirst()
                .orElse(null);
        if (targetPlanet == null) return state;

        Empire attackerEmpire = state.empires().stream()
                .filter(e -> e.id().equals(attackerEmpireId))
                .findFirst()
                .orElse(null);

        OrbitalBombardmentProcessor processor = new OrbitalBombardmentProcessor();
        OrbitalBombardmentProcessor.BombardmentResult res = processor.executeBombardment(
                bombType, targetPlanet, targetSys, attackerEmpire
        );

        List<SolarSystem> updatedSystems = new ArrayList<>();
        for (SolarSystem sys : state.solarSystems()) {
            if (sys.id().equals(targetSystemId)) {
                updatedSystems.add(res.updatedSystem());
            } else {
                updatedSystems.add(sys);
            }
        }

        return state.toBuilder()
                .solarSystems(updatedSystems)
                .build();
    }
}
