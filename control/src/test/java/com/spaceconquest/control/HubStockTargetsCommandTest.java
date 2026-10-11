package com.spaceconquest.control;

import com.spaceconquest.control.command.SetHubStockTargetsCommand;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HubStockTargetsCommandTest {
    private GameState world() {
        var hub = new CommercialHub("hub", "station", 0, 1000, 0, 10,
                Map.of("steel", new MarketOrder("steel", 0, 0, 2, 0)));
        var source = new CommercialHub("source", "elsewhere", 0, 1000, 1000, 10, Map.of(
                "liquid_hydrogen", new MarketOrder("liquid_hydrogen", 170, 0, .01, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 830, 0, .01, 0)));
        var station = new OrbitalStation("station", "Station", "sol", "", "owner", "PUBLIC_STATE", 10,
                List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000, 0,
                List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        return GameState.builder().empires(List.of(owner)).orbitalStations(List.of(station))
                .commercialHubs(List.of(hub, source)).build();
    }

    @Test void replacesAllIngredientsAtomicallyWithoutTransientOvercommitment() {
        var state = world();
        var hub = state.commercialHubs().getFirst().withStockTargets(Map.of(
                "liquid_hydrogen", new HubStockTarget(400, 800), "liquid_oxygen", new HubStockTarget(50, 100),
                "steel", new HubStockTarget(50, 100))).observeDay(state.turn());
        state = state.withCommercialHubs(List.of(hub, state.commercialHubs().getLast()));
        var command = new SetHubStockTargetsCommand("owner", "hub", Map.of(
                "liquid_hydrogen", new HubStockTarget(50, 100, true), "liquid_oxygen", new HubStockTarget(400, 800)));
        assertTrue(command.validate(state));
        var next = command.apply(state);
        var targets = next.commercialHubs().getFirst().stockTargets();
        assertEquals(new HubStockTarget(50, 100), targets.get("liquid_hydrogen"));
        assertEquals(new HubStockTarget(400, 800), targets.get("liquid_oxygen"));
        assertEquals(hub.stockTargets().get("steel"), targets.get("steel"));
        assertEquals(0, next.commercialHubs().getFirst().activeOrders().get("liquid_hydrogen").supplyKg());
        assertEquals(state.empires(), next.empires());
        assertEquals(hub.stockHistory(), next.commercialHubs().getFirst().stockHistory());
        assertThrows(UnsupportedOperationException.class, () -> command.targets().clear());
    }

    @Test void rejectsWholeGroupWhenCapacityOrAuthorizationChangesBeforeTheTick() {
        var state = world();
        var command = new SetHubStockTargetsCommand("owner", "hub", Map.of(
                "liquid_hydrogen", new HubStockTarget(100, 170), "liquid_oxygen", new HubStockTarget(500, 830)));
        assertTrue(command.validate(state));
        var changed = state.withCommercialHubs(List.of(state.commercialHubs().getFirst()
                .withStockTargets(Map.of("steel", new HubStockTarget(1, 1))), state.commercialHubs().getLast()));
        assertFalse(command.validate(changed));
        assertSame(changed, command.apply(changed));
        var foreign = new SetHubStockTargetsCommand("foreign", "hub", command.targets());
        assertFalse(foreign.validate(state));
        assertSame(state, foreign.apply(state));
        var unknown = new SetHubStockTargetsCommand("owner", "hub", Map.of(
                "liquid_hydrogen", new HubStockTarget(100, 170), "unknown", new HubStockTarget(10, 20)));
        assertSame(state, unknown.apply(state));
        assertFalse(new SetHubStockTargetsCommand("owner", "hub", Map.of()).validate(state));
    }
}
