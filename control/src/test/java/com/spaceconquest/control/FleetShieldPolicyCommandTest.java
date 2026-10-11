package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetShieldPolicyCommandTest {
    @TempDir Path directory;
    private GameState world() {
        var ships = List.of(new ShipInstance("a", "design", "owner", 1000, 7, 100, Map.of()),
                new ShipInstance("b", "design", "owner", 1000, 7, 100, Map.of()));
        return GameState.builder().fleets(List.of(new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", ships))).build();
    }
    @Test void queuedOwnedPolicyChangesIntentWithoutSpendingOrChangingTravel() {
        var state = world(); var command = new SetFleetShieldPolicyCommand("owner", "fleet", FleetShieldPolicy.Mode.ALWAYS_CHARGE);
        var queue = new CommandQueue(); queue.submit(command);
        assertEquals(FleetShieldPolicy.Mode.AUTOMATIC, state.fleets().getFirst().shieldPolicy());
        var after = queue.drainAndExecute(state);
        assertEquals(FleetShieldPolicy.Mode.ALWAYS_CHARGE, after.fleets().getFirst().shieldPolicy());
        assertEquals(state.fleets().getFirst(), after.fleets().getFirst().withShieldPolicy(FleetShieldPolicy.Mode.AUTOMATIC));
        for (var invalid : List.of(new SetFleetShieldPolicyCommand("foreign", "fleet", FleetShieldPolicy.Mode.CONSERVE_ENERGY),
                new SetFleetShieldPolicyCommand("owner", "missing", FleetShieldPolicy.Mode.CONSERVE_ENERGY),
                new SetFleetShieldPolicyCommand("owner", "fleet", null))) {
            assertFalse(invalid.validate(state)); assertSame(state, invalid.apply(state));
        }
    }
    @Test void saveAndCopiesRetainPolicyAndOlderRecordsDefaultAutomatic() throws Exception {
        var state = new SetFleetShieldPolicyCommand("owner", "fleet", FleetShieldPolicy.Mode.CONSERVE_ENERGY).apply(world());
        var original = state.fleets().getFirst();
        var renamed = FleetOrganization.rename(state, "owner", "fleet", "Renamed");
        var split = FleetOrganization.split(renamed, "owner", "fleet", List.of("a"), "split", "Split");
        assertEquals(2, split.fleets().size());
        for (var fleet : split.fleets()) assertEquals(FleetShieldPolicy.Mode.CONSERVE_ENERGY, fleet.shieldPolicy());
        assertEquals(FleetShieldPolicy.Mode.CONSERVE_ENERGY, original.withStance("AGGRESSIVE").withShips(original.ships())
                .withLocation(original.location()).withFuelPolicy(FleetFuelPolicy.automatic()).withJourneyPropulsion(Map.of())
                .withInterceptOrder(null).withGuardOrder(null).withEscortOrder(null).withInterruptedTravel().shieldPolicy());
        var manager = new SaveGameManager(directory); var file = directory.resolve("policy.scsave").toFile();
        manager.save(file, split, 1, "2027-01-01T08:00:00");
        assertEquals(split.fleets(), manager.load(file).toGameState(0, "RUNNING").fleets());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var json = mapper.valueToTree(original);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("shieldPolicy");
        assertEquals(FleetShieldPolicy.Mode.AUTOMATIC, mapper.treeToValue(json, Fleet.class).shieldPolicy());
    }
}
