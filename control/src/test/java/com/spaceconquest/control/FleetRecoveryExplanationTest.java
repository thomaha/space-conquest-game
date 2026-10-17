package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.FleetServiceStatus;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetRecoveryExplanationTest {
    private final AICombatServiceTest fixture = new AICombatServiceTest();
    private final ObjectMapper mapper = new ObjectMapper().configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private GameState damaged() {
        var state = fixture.world(CombatModuleCatalog.LASER, 10000, FleetLocation.Site.docked("port"));
        var ship = state.fleets().getFirst().ships().getFirst();
        return ShipPowerResupply.replace(state, ship.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))));
    }
    private GameState station(GameState state, boolean staffed, boolean powered) throws Exception {
        var stations = new ArrayList<OrbitalStation>();
        for (var original : state.orbitalStations()) {
            if (!original.id().equals("port")) { stations.add(original); continue; }
            ObjectNode json = mapper.valueToTree(original);
            json.set("modules", mapper.valueToTree(List.of(BaseRepairCatalog.module().withPayroll(staffed ? 2 : 0, 0))));
            json.put("currentPowerGenerationKw", powered ? 100 : 0);
            stations.add(mapper.treeToValue(json, OrbitalStation.class));
        }
        return state.toBuilder().orbitalStations(stations).build();
    }
    private List<String> blockers(GameState state) {
        return FleetServiceStatus.inspect(state, state.fleets().getFirst(), "ai").blockers();
    }
    @Test void staffingAndStationPowerHaveDistinctAuthoritativeBlockers() throws Exception {
        var state = damaged();
        assertTrue(blockers(station(state, false, true)).contains("Base repair: No paid repair workshop staff"));
        var unpowered = blockers(station(state, true, false));
        assertTrue(unpowered.contains("Base repair: Repair station power shortage"));
        assertFalse(unpowered.contains("Base repair: No paid repair workshop staff"));
        assertFalse(blockers(state).stream().anyMatch(text -> text.contains("staff") || text.contains("station power shortage")));
        assertEquals(100, state.fleets().getFirst().ships().getFirst().moduleDamage().damageByMount().get(0));
        assertTrue(state.fleetServiceEvents().isEmpty(), "Inspection cannot record service or purchase supplies");
    }
    @Test void componentDamageOutsideWeaponMountsStillShowsPaidRepairRequirements() {
        var state = fixture.world(CombatModuleCatalog.LASER, 10000, FleetLocation.Site.docked("port"));
        var ship = state.fleets().getFirst().ships().getFirst();
        state = ShipPowerResupply.replace(state, ship.withModuleDamage(new ShipModuleDamage(Map.of(1, 25.0))));
        assertTrue(blockers(state).stream().anyMatch(text -> text.contains("damaged components 25.00")));
        assertEquals(25, state.fleets().getFirst().ships().getFirst().moduleDamage().damageByMount().get(1));
    }
    @Test void unknownHardwareCannotBePresentedAsReady() {
        var state = damaged().withShipDesigns(List.of());
        assertTrue(blockers(state).stream().anyMatch(text -> text.contains("blueprint unavailable")));
        assertTrue(FleetServiceStatus.inspect(state, state.fleets().getFirst(), "foreign").blockers().isEmpty());
    }
}
