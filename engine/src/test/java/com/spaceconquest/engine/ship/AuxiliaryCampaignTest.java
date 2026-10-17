package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuxiliaryCampaignTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path saveDirectory;
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private CarrierWing wing() {
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        return new CarrierWing("wing", "carrier", "INTERCEPTOR_FIGHTER", 1, 1, 100,
                CombatModuleCatalog.application(weapon.id()).id(), "CAPITAL_SHIPS")
                .withStores(new AuxiliaryWeaponStores(Map.of(weapon.ammunitionId(), weapon.operationalStats().get("ammunitionMassKg")),
                        weapon.powerDrawKw() / 60 + weapon.operationalStats().get("weaponSalvoKwh")));
    }
    private GameState world(boolean sensors) {
        var a = fixture.design("own", 1000, SensorModuleCatalog.BASIC);
        var d = fixture.design("enemy", 1000);
        var carrier = fixture.ship("carrier", a, 0);
        if (!sensors) carrier = carrier.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        var battery = new PlanetaryDefenseBattery("battery", "earth", wing().weaponApplicationId(), 1, true);
        return GameState.builder().shipDesigns(List.of(a, d))
                .empires(List.of(empire("own"), empire("enemy")))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of())))
                .fleets(List.of(fixture.fleet("own", List.of(carrier)), fixture.fleet("enemy", List.of(fixture.ship("target", d, 0)))))
                .diplomaticRelations(List.of(new DiplomaticRelation("own", "enemy", "TOTAL_WAR", 0)))
                .auxiliaryForces(new AuxiliaryForces(List.of(wing()), List.of(new AuxiliaryForces.OwnedBattery("own", battery)))).build();
    }
    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }
    @Test void campaignWingOnlyEncounterSpendsSavedStoresAndCannotFireAgain() throws Exception {
        var state = world(true);
        var resolver = new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42)));
        var result = resolver.resolveEncounters(state);
        assertEquals(1, result.fleetEngagements().size());
        assertTrue(result.fleets().getLast().ships().getFirst().currentHullHealth() < 1000);
        assertTrue(result.auxiliaryForces().wings().getFirst().stores().ammunitionKg().isEmpty());
        assertEquals(0, result.auxiliaryForces().wings().getFirst().stores().energyKwh(), 1e-9);
        var again = resolver.resolveEncounters(result);
        assertEquals(result.fleets(), again.fleets()); assertEquals(1, again.fleetEngagements().size());
        assertEquals(1, state.auxiliaryForces().wings().getFirst().stores().ammunitionKg().size());
        assertEquals(state.auxiliaryForces().batteries(), result.auxiliaryForces().batteries());
        var manager = new SaveGameManager(saveDirectory);
        var file = manager.save("depleted", result, 1, GameClock.START_TIME.toString());
        assertEquals(result.auxiliaryForces(), manager.load(file.toFile()).toGameState(0, "STOPPED").auxiliaryForces());
    }
    @Test void blindAndPeacefulFleetsDoNotSpendAndDestroyedHostsLoseTheirWings() {
        var resolver = new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42)));
        var blind = world(false); assertEquals(blind.auxiliaryForces(), resolver.resolveEncounters(blind).auxiliaryForces());
        var peaceful = world(true).withDiplomaticRelations(List.of());
        assertEquals(peaceful.auxiliaryForces(), resolver.resolveEncounters(peaceful).auxiliaryForces());
        var state = world(true);
        var result = new TacticalCombatProcessor.CombatEngagementResult("enemy", null, state.fleets().getLast(), List.of());
        assertTrue(state.auxiliaryForces().afterCombat(result, List.of(state.fleets().getLast())).wings().isEmpty());
    }
    @Test void saveRoundTripCopiesRetainStoresAndOldSavesDefaultToEmpty() throws Exception {
        var mapper = new ObjectMapper(); var state = world(true);
        var manager = new SaveGameManager(saveDirectory);
        var file = manager.save("auxiliary", state, 1, GameClock.START_TIME.toString());
        var restored = manager.load(file.toFile()).toGameState(2, "STOPPED");
        assertEquals(state.auxiliaryForces(), restored.auxiliaryForces());
        assertEquals(state.auxiliaryForces(), state.withFleets(state.fleets()).auxiliaryForces());
        ObjectNode old = (ObjectNode) mapper.readTree(file.toFile()); old.remove("auxiliaryForces"); old.put("version", com.spaceconquest.engine.SaveGame.CURRENT_VERSION);
        mapper.writeValue(file.toFile(), old);
        assertEquals(AuxiliaryForces.empty(), manager.load(file.toFile()).toGameState(0, "STOPPED").auxiliaryForces());
        assertThrows(UnsupportedOperationException.class, () -> restored.auxiliaryForces().wings().clear());
        assertThrows(IllegalArgumentException.class, () -> new AuxiliaryForces(List.of(wing(), wing()), List.of()));
        var engine = new SpaceConquestEngine(); engine.applyGameState(restored);
        assertEquals(restored.auxiliaryForces(), engine.getGameState().auxiliaryForces());
    }
}
