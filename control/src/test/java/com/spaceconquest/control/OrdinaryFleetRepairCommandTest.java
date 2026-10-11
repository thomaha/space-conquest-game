package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrdinaryFleetRepairCommandTest {
    private GameState world() {
        var design = new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel",
                List.of(CombatModuleCatalog.GUN, "mod_chemical_rocket"), "steel", 0, 1000, 1000, 1000, 100, 1, 0, 1000, true, false);
        var ship = new ShipInstance("ship", "design", "owner", 1000, 0, 100, Map.of())
                .withModuleDamage(new ShipModuleDamage(Map.of(0, 20.0)));
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("port")));
        var station = new OrbitalStation("port", "Port", "sol", "earth", "owner", "PUBLIC_STATE", 30,
                List.of(BaseRepairCatalog.module().withPayroll(2, 0)), Map.of(), 100, 50, 0, 0, 100, 100, "steel", 0, true);
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1000, 0, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        return GameState.builder().empires(List.of(empire)).shipDesigns(List.of(design)).fleets(List.of(fleet)).orbitalStations(List.of(station))
                .commercialHubs(List.of(new CommercialHub("hub", "port", 0, 10000, 100, 10,
                        Map.of("steel", new MarketOrder("steel", 100, 0, 1, 0))))).build();
    }
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "fleet"); }

    @Test void cancelingPausedGuardCannotRestoreItAfterRepairs() {
        var initial = world(); var guard = new GuardObjectCommand("owner", "fleet", FleetGuardOrder.TargetType.STATION, "port");
        assertTrue(guard.validate(initial));
        var waiting = FleetInterceptProcessor.advance(guard.apply(initial));
        var command = new CancelGuardObjectCommand("owner", "fleet"); assertTrue(command.validate(waiting));
        assertFalse(new CancelGuardObjectCommand("foreign", "fleet").validate(waiting));
        var canceled = command.apply(waiting);
        assertNull(fleet(canceled).interceptOrder().protection().guard());
        var finished = FleetInterceptProcessor.advance(BaseRepairProcessor.advanceDay(canceled));
        assertNull(fleet(finished).guardOrder()); assertNull(fleet(finished).interceptOrder());
        assertEquals("PASSIVE", fleet(finished).fleetStance());
    }

    @Test void cancelingPausedPursuitRemovesOnlyTheSavedTargetAndRepairCanFinish() {
        var initial = world(); initial = initial.withFleets(List.of(fleet(initial).withInterceptOrder(new FleetInterceptOrder("owner", "oldTarget", "sol"))));
        var waiting = FleetInterceptProcessor.advance(initial);
        var canceled = new CancelInterceptFleetCommand("owner", "fleet").apply(waiting);
        assertEquals(FleetProtectionAssignment.Stage.REPAIRING, fleet(canceled).interceptOrder().protection().stage());
        assertNull(fleet(canceled).interceptOrder().protection().repairIntent().intercept());
        var finished = FleetInterceptProcessor.advance(BaseRepairProcessor.advanceDay(canceled));
        assertNull(fleet(finished).interceptOrder());
        assertEquals(ShipModuleDamage.empty(), fleet(finished).ships().getFirst().moduleDamage());
    }

    @Test void pausedEscortCanDetachWithoutLosingItsPaidJourneyOrShipCondition() {
        var initial = world(); var original = fleet(initial);
        var protectedShip = new ShipInstance("protected", "design", "owner", 1000, 0, 100, Map.of());
        var order = new FleetEscortOrder("owner", "escort", "Escort", "PASSIVE", List.of("ship"));
        initial = initial.withFleets(List.of(original.withShips(List.of(original.ships().getFirst(), protectedShip)).withStance("ESCORT").withEscortOrder(order)));
        var waiting = FleetInterceptProcessor.advance(initial);
        // Cancellation during a paid leg keeps the same physical progress for both fragments.
        var target = FleetLocation.Site.deepSpace(); var plan = FleetReturnTravel.localPlan(waiting, fleet(waiting), target); assertNotNull(plan);
        waiting = waiting.withFleets(List.of(LocalTravel.depart(fleet(waiting), target, plan)));
        var paid = fleet(waiting); var command = new CancelEscortFleetCommand("owner", "fleet"); assertTrue(command.validate(waiting));
        assertFalse(new CancelEscortFleetCommand("foreign", "fleet").validate(waiting));
        var detached = command.apply(waiting); var escort = FleetOrganization.find(detached, "escort");
        assertNotNull(escort); assertNull(fleet(detached).escortOrder()); assertNull(escort.interceptOrder());
        assertEquals(paid.location(), escort.location()); assertEquals(paid.location(), fleet(detached).location());
        assertEquals(paid.ships().getFirst(), escort.ships().getFirst());
        assertEquals(List.of("ship"), escort.ships().stream().map(ShipInstance::id).toList());
    }

    @Test void newStanceSupersedesSavedIntentAndKeepsActualRepairJourney() {
        var waiting = FleetInterceptProcessor.advance(world()); var original = fleet(waiting);
        var changed = new SetFleetStanceCommand("fleet", "AGGRESSIVE").apply(waiting);
        assertEquals("AGGRESSIVE", fleet(changed).fleetStance()); assertNull(fleet(changed).interceptOrder());
        assertEquals(original.location(), fleet(changed).location()); assertEquals(original.ships(), fleet(changed).ships());
    }
}
