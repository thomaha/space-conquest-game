package com.spaceconquest.control.command;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.governance.GroundCombatProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command to launch a planetary ground invasion against a fortified world.
 */
public record InvadePlanetCommand(
        String attackerEmpireId,
        String targetSystemId,
        String targetPlanetId,
        long invadingSoldiers
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || attackerEmpireId == null || targetSystemId == null || targetPlanetId == null) {
            return false;
        }
        return invadingSoldiers > 0 && state.empires().stream().anyMatch(e -> e.id().equals(attackerEmpireId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        Empire attacker = state.empires().stream()
                .filter(e -> e.id().equals(attackerEmpireId))
                .findFirst()
                .orElse(null);
        if (attacker == null) return state;

        Race attackerRace = null;
        try {
            List<Race> races = DataModelLoader.loadRaces();
            for (Race r : races) {
                if (r.id().equalsIgnoreCase(attacker.raceId())) {
                    attackerRace = r;
                    break;
                }
            }
        } catch (IOException ignored) {}

        GroundCombatProcessor processor = new GroundCombatProcessor();
        GroundCombatProcessor.GroundCombatResult res = processor.resolveCombat(
                invadingSoldiers,
                attackerRace,
                1.20,
                500, // defender soldiers
                200, // militia
                attackerRace, // fallback
                2, // fortifications
                1, // bunkers
                1, // batteries
                false,
                "Individualist"
        );

        List<Empire> updatedEmpires = new ArrayList<>(state.empires());
        if (res.attackerWon()) {
            // Transfer control of system if not already controlled
            updatedEmpires = updatedEmpires.stream().map(e -> {
                if (e.id().equals(attackerEmpireId)) {
                    List<String> sysList = new ArrayList<>(e.controlledSystemIds());
                    if (!sysList.contains(targetSystemId)) {
                        sysList.add(targetSystemId);
                    }
                    return new Empire(
                            e.id(), e.name(), e.raceId(), e.societyStructure(),
                            e.treasuryCredits(), e.corporateTaxRate(), sysList,
                            e.ministries(), e.systemGovernorAssignments(), e.unlockedTechIds(), e.activeShipDesignIds()
                    );
                }
                return e;
            }).toList();
        }

        return state.toBuilder()
                .empires(updatedEmpires)
                .build();
    }
}
