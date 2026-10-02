package com.spaceconquest.control.command;

import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEmployment;
import com.spaceconquest.engine.habitation.PassengerManifest;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetaryInvasionCommandTest {
    private final ShipDesign transportDesign = new ShipDesign("troop_design", "Troop transport", "terran",
            ShipRole.TROOP_TRANSPORT, "steel", List.of(), "steel", 0.0, 1000.0, 20000.0,
            100.0, 1.0, 1000.0, 100.0, 500.0, true, false);

    @Test
    void troopsMustBeLoadedAndDeliveredBeforeInvasionTransfersSystemControl() {
        GameState state = scenario();
        LoadTroopsCommand load = new LoadTroopsCommand("army", "transport", "human", 100, "target");
        assertTrue(load.validate(state));
        GameState loaded = load.apply(state);
        PassengerManifest deployment = loaded.passengerManifests().getFirst();
        assertTrue(deployment.combatDeployment());
        assertEquals(100, deployment.headcount());
        assertEquals(50, population(loaded, "source", "human"));
        assertEquals(50, loaded.householdAccounts().getFirst().headcount());

        Fleet arrived = new Fleet("army", "Army", "terran", "enemy", "", 0, 0, 0,
                false, "PASSIVE", loaded.fleets().getFirst().ships(),
                FleetLocation.at(FleetLocation.Site.surface("target")));
        GameState atTarget = PassengerTransitProcessor.disembarkArrivals(loaded.withFleets(List.of(arrived)));
        assertTrue(atTarget.passengerManifests().getFirst().combatDeployment());
        assertEquals(100, atTarget.fleets().getFirst().ships().getFirst().passengerCount());
        GameState resolved = new InvadePlanetCommand("terran", "enemy", "target", "army", "transport")
                .apply(atTarget);

        assertEquals(List.of("home", "enemy"), resolved.empires().getFirst().controlledSystemIds());
        assertTrue(resolved.empires().get(1).controlledSystemIds().isEmpty());
        assertEquals(100, population(resolved, "target", "human"));
        assertEquals(0, resolved.fleets().getFirst().ships().getFirst().passengerCount());
        assertTrue(resolved.passengerManifests().isEmpty());
    }

    @Test
    void troopLoadingAndInvasionRequireDeclaredWarAndCorrectTarget() {
        GameState peace = scenario().withDiplomaticRelations(List.of());
        assertFalse(new LoadTroopsCommand("army", "transport", "human", 100, "target").validate(peace));

        GameState state = scenario();
        GameState loaded = new LoadTroopsCommand("army", "transport", "human", 100, "target").apply(state);
        Fleet arrived = new Fleet("army", "Army", "terran", "enemy", "", 0, 0, 0,
                false, "PASSIVE", loaded.fleets().getFirst().ships(),
                FleetLocation.at(FleetLocation.Site.surface("target")));
        GameState atTarget = loaded.withFleets(List.of(arrived));
        assertFalse(new InvadePlanetCommand("terran", "home", "target", "army", "transport")
                .validate(atTarget));
        assertTrue(new InvadePlanetCommand("terran", "enemy", "target", "army", "transport")
                .validate(atTarget));
    }

    @Test
    void localSoldierGarrisonCanRepelAndSufferCasualties() {
        GameState base = scenario();
        HouseholdAccount defenderSoldiers = new HouseholdAccount("target", "enemy", "silicon",
                "silicon_core", "soldier", 100, 0, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0, null,
                new HouseholdEmployment(100, 100, 0, 0));
        GameState defended = base.toBuilder().householdAccounts(List.of(
                base.householdAccounts().getFirst(), defenderSoldiers)).build();
        GameState loaded = new LoadTroopsCommand("army", "transport", "human", 100, "target").apply(defended);
        Fleet arrived = new Fleet("army", "Army", "terran", "enemy", "", 0, 0, 0,
                false, "PASSIVE", loaded.fleets().getFirst().ships(),
                FleetLocation.at(FleetLocation.Site.surface("target")));
        GameState atTarget = loaded.withFleets(List.of(arrived));

        GameState resolved = new InvadePlanetCommand("terran", "enemy", "target", "army", "transport")
                .apply(atTarget);

        assertEquals(List.of("enemy"), resolved.empires().get(1).controlledSystemIds());
        HouseholdAccount remainingGarrison = resolved.householdAccounts().stream()
                .filter(account -> account.bodyId().equals("target")).findFirst().orElseThrow();
        assertTrue(remainingGarrison.employment().publicWorkers() > 0);
        assertTrue(remainingGarrison.employment().publicWorkers() < 100);
        assertEquals(0, resolved.fleets().getFirst().ships().getFirst().passengerCount());
        assertTrue(resolved.passengerManifests().isEmpty());
    }

    private GameState scenario() {
        Planet home = planet("source", "human", 150);
        Planet target = planet("target", "silicon_core", 500);
        SolarSystem homeSystem = new SolarSystem("home", "Home", "", 0, 0, 0, 1, 1,
                "yellow", List.of(home), List.of());
        SolarSystem enemySystem = new SolarSystem("enemy", "Enemy", "", 10, 0, 0, 1, 1,
                "yellow", List.of(target), List.of());
        Empire attacker = empire("terran", "human", List.of("home"));
        Empire defender = empire("silicon", "silicon_core", List.of("enemy"));
        Fleet army = new Fleet("army", "Army", "terran", "home", "", 0, 0, 0,
                false, "PASSIVE", List.of(new ShipInstance("transport", "troop_design", "terran",
                1000, 0, 0, Map.of())), FleetLocation.at(FleetLocation.Site.surface("source")));
        HouseholdAccount soldiers = new HouseholdAccount("source", "home", "terran", "human", "soldier",
                150, 0, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0, null,
                new HouseholdEmployment(150, 100, 0, 0));
        return GameState.builder().turn(1).solarSystems(List.of(homeSystem, enemySystem))
                .empires(List.of(attacker, defender)).fleets(List.of(army)).shipDesigns(List.of(transportDesign))
                .householdAccounts(List.of(soldiers))
                .diplomaticRelations(List.of(new DiplomaticRelation("terran", "silicon", "TOTAL_WAR", 0)))
                .build();
    }

    private Planet planet(String id, String raceId, long population) {
        return new Planet(id, id, "", 1e20, 1, 1, 0, 1000, "Terrestrial", "Oxygen",
                true, 0.5, List.of(), List.of(), List.of(new Population(raceId, Map.of(25, population))));
    }

    private Empire empire(String id, String race, List<String> systems) {
        return new Empire(id, id, race, "Collectivist", 1000, 0.05, systems,
                List.of(), Map.of(), List.of(), List.of());
    }

    private long population(GameState state, String planetId, String raceId) {
        return state.solarSystems().stream().flatMap(system -> system.planets().stream())
                .filter(planet -> planetId.equals(planet.id()))
                .flatMap(planet -> planet.populations().stream())
                .filter(group -> raceId.equals(group.raceId())).mapToLong(Population::totalCount).sum();
    }
}
