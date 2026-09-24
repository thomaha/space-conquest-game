package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.ScanAnomalyCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.Anomaly;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ScanAnomalyCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;

    @BeforeEach
    void setUp() {
        commandQueue = new CommandQueue();
        Empire empire = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                50000.0, 0.05, List.of("sol", "alpha_centauri"),
                List.of(), Map.of(), List.of(), List.of()
        );
        ShipInstance explorerShip = new ShipInstance(
                "scout_1", "design_explorer", "terran_confederation",
                200.0, 100.0, 50.0, Map.of()
        );
        Fleet fleet = new Fleet(
                "fleet_survey_1", "1st Science Fleet", "terran_confederation",
                "alpha_centauri", "", 0.0, 0.0, 0.0, false, "PATROL", List.of(explorerShip)
        );

        initialState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .empires(List.of(empire))
                .fleets(List.of(fleet))
                .build();
    }

    @Test
    void testScanAnomalyCommandUnlocksTech() {
        Anomaly anomaly = new Anomaly(
                "anom_precursor", "alpha_centauri", Anomaly.TYPE_ANCIENT_RUINS,
                "Precursor Archive", "Encrypted data vault", 25.0, false,
                Anomaly.REWARD_TECH_UNLOCK, 0.0, "quantum_communication"
        );

        ScanAnomalyCommand cmd = new ScanAnomalyCommand("terran_confederation", "fleet_survey_1", anomaly);
        assertTrue(cmd.validate(initialState));

        GameState nextState = cmd.apply(initialState);
        Empire updatedEmpire = nextState.empires().get(0);

        assertTrue(updatedEmpire.unlockedTechIds().contains("quantum_communication"));
    }
}
