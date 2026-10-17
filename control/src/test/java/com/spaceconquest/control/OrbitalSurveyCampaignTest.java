package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalSurveyCampaignTest {
    @TempDir Path directory;
    @Test void explorerBuysEquipmentTravelsAndMapsUnsettledBodiesContinuouslyAcrossSave() throws Exception {
        var state = world(true); var command = new AdvanceOrbitalSurveyCommand("owner", "fleet");
        assertTrue(command.validate(state));
        double silicon = stock(state, "silicon");
        state = command.apply(state);
        assertEquals(silicon - 10, stock(state, "silicon"), 1e-6);
        assertEquals(1, state.surveyTeams().size());
        assertEquals(Set.of("fleet"), SurveyProgramPlanner.reservedFleets(state, state.empires().getFirst()));
        assertTrue(state.fleets().getFirst().location().stationaryAt(FleetLocation.Site.docked("port-a")));
        state = command.apply(state);
        assertTrue(state.fleets().getFirst().location().inTransit(), state.fleets() + "; plan=" + LocalTravel.plan(state, state.fleets().getFirst(), FleetLocation.Site.orbit("planet-a")) + "; orbital=" + OrbitalTravel.preview(state, state.fleets().getFirst(), FleetLocation.Site.orbit("planet-a")));
        assertEquals(0, state.surveyTeams().getFirst().totalWork());
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state); boolean reloaded = false;
        for (int day = 0; day < 140; day++) {
            state = engine.getGameState();
            state = command.apply(state); engine.applyGameState(state); engine.stepTurn();
            state = engine.getGameState();
            var team = state.surveyTeams().getFirst();
            if (!reloaded && team.progressWork() > 0) {
                var saves = new SaveGameManager(directory);
                var loaded = saves.load(saves.save("orbital", state, 42, GameClock.START_TIME.toString()).toFile()).toGameState(state.turn(), state.status());
                assertEquals(state.surveyTeams(), loaded.surveyTeams());
                assertEquals(state.fleets(), loaded.fleets());
                engine.applyGameState(loaded); reloaded = true;
            }
            if (team.completedPasses() >= 1 && !OrbitalSurveyOperations.targets(state, "owner").contains("planet-b")) {
                state = state.toBuilder().fogOfWarStates(List.of(new FogOfWarState("owner", List.of("a", "b"),
                        List.of("planet-a", "planet-b"), List.of(), List.of(), List.of()))).build();
                engine.applyGameState(state);
            }
            if (team.coveragePasses().size() >= 2) break;
        }
        state = engine.getGameState(); var team = state.surveyTeams().getFirst();
        assertTrue(reloaded, team.toString());
        assertTrue(team.coveragePasses().size() >= 2, team + "; " + state.fleets());
        assertEquals(1, state.geologicalDeposits().size());
        assertTrue(state.surveyTeams().getFirst().completedPasses() >= 2);
    }
    @Test void noEnergyNoTravelOrBrokenSensorsNeverProduceSurveyWork() throws Exception {
        var state = new AdvanceOrbitalSurveyCommand("owner", "fleet").apply(world());
        var fleet = state.fleets().getFirst(); var team = state.surveyTeams().getFirst();
        var target = fleet.withLocation(FleetLocation.at(FleetLocation.Site.orbit(team.bodyId())));
        state = state.withFleets(List.of(target)).toBuilder().turn(1).build();
        double battery = target.ships().getFirst().powerState().batteryChargeKwh();
        var worked = SurveyOperations.advance(state, Map.of());
        assertEquals(100, worked.surveyTeams().getFirst().totalWork());
        assertEquals(battery - 10, worked.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh(), 1e-6);
        var ship = target.ships().getFirst().withPowerState(ShipPowerState.empty());
        var empty = SurveyOperations.advance(state.withFleets(List.of(target.withShips(List.of(ship)))), Map.of());
        assertEquals(0, empty.surveyTeams().getFirst().totalWork());
        assertTrue(empty.surveyTeams().getFirst().blocker().contains("electrical"));
        var docked = SurveyOperations.advance(state.withFleets(List.of(fleet)), Map.of());
        assertEquals(0, docked.surveyTeams().getFirst().totalWork());
        assertTrue(docked.surveyTeams().getFirst().blocker().contains("Travel"));
        var noDesign = SurveyOperations.advance(state.withShipDesigns(List.of()), Map.of());
        assertEquals(0, noDesign.surveyTeams().getFirst().totalWork());
        assertTrue(noDesign.surveyTeams().getFirst().blocker().contains("sensors"));
    }
    @Test void newlyKnownWorldsAreSelectedWithoutLookingAtTheirHiddenResources() throws Exception {
        var state = new AdvanceOrbitalSurveyCommand("owner", "fleet").apply(world()); var team = state.surveyTeams().getFirst();
        var other = state.solarSystems().get(1);
        assertFalse(OrbitalSurveyOperations.targets(state, "owner").contains("planet-b"));
        var covered = new SurveyTeam(team.id(), team.empireId(), team.bodyId(), team.applicationId(), team.capability(),
                0, 0, 1, 0, 1200, "", team.fleetId(), Map.of("planet-a", 1L));
        state = state.toBuilder().surveyTeams(List.of(covered)).fogOfWarStates(List.of(new FogOfWarState("owner",
                List.of("a", other.id()), List.of("planet-a", "planet-b"), List.of(), List.of(), List.of(), List.of()))).build();
        assertTrue(OrbitalSurveyOperations.targets(state, "owner").contains("planet-b"));
        var moved = OrbitalSurveyOperations.retarget(state, "owner", "fleet", "planet-b");
        assertEquals("planet-b", moved.surveyTeams().getFirst().bodyId());
        var withoutHidden = OrbitalSurveyOperations.retarget(state.toBuilder().geologicalDeposits(List.of()).build(), "owner", "fleet", "planet-b");
        assertEquals(moved.surveyTeams(), withoutHidden.surveyTeams());
    }
    @Test void missingPhysicalKitOrResearchCannotStartAndMissingShipRequestsOrdinaryConstruction() throws Exception {
        var state = world(true); var command = new AdvanceOrbitalSurveyCommand("owner", "fleet");
        assertFalse(command.validate(state.withCommercialHubs(List.of())));
        var empty = state.withCommercialHubs(List.of()); assertSame(empty, command.apply(empty));
        var owner = state.empires().getFirst();
        var known = owner.unlockedTechIds().stream().filter(id -> !id.equals("orbital_hyperspectral_mapping")).toList();
        var unavailable = state.withEmpires(List.of(owner(owner, known, owner.controlledSystemIds())));
        assertFalse(command.validate(unavailable));
        assertFalse(EmpireFleetPlanner.needsShip(state, owner, state.shipDesigns().getFirst()));
        assertTrue(EmpireFleetPlanner.needsShip(state.withFleets(List.of()), owner, state.shipDesigns().getFirst()));
    }
    private GameState world() throws Exception { return world(false); }
    private GameState world(boolean withMoon) throws Exception {
        var state = new AIFacilityLogisticsTest().world(); var old = state.empires().getFirst();
        var known = new ArrayList<String>(); DataModelLoader.loadTechnologies().forEach(t -> { known.add(t.id()); t.applications().forEach(a -> known.add(a.id())); });
        var owner = owner(old, known, List.of("a"));
        var systems = state.solarSystems().stream().map(sys -> new SolarSystem(sys.id(), sys.name(), sys.description(),
                sys.x(), sys.y(), sys.z(), 1.989e30, 1392700, "yellow", sys.planets().stream().map(p ->
                new Planet(p.id(), p.name(), p.description(), 5.97e24, 9.81, 149600000, 0, 12742,
                        "terrestrial", "breathable", true, 1, p.resources(), withMoon && sys.id().equals("a") ?
                        List.of(new Moon("moon-a", "Moon", "", 7.34e22, 1.62, 384400, 3474, "none", false, 0, List.of(), List.of()))
                        : List.of(), p.populations())).toList(), sys.asteroidBelts())).toList();
        var profile = new ShipPowerProfile(100, 0, 0, 10000, 100, 100, 0, 0, 1, 2, 0, .65, Map.of());
        var design = new ShipDesign("explorer", "Explorer", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket", SensorModuleCatalog.BASIC), "steel", 0, 1000, 0, 1000000, 100,
                1, 0, 20000000, false, false, ShipManufacturingProfile.baseline(), profile, new ShipSensorProfile(2e6, 20));
        var power = new ShipPowerState(Map.of(), "rp1", "uranium", 10000, true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("explorer-ship", design.id(), "owner", 100, 0, 1000000, Map.of()).withPowerState(power);
        var fleet = state.fleets().getFirst().withShips(List.of(ship));
        var stations = state.orbitalStations().stream().map(st -> new com.spaceconquest.engine.macrostructure.OrbitalStation(
                st.id(), st.name(), st.systemId(), st.systemId().equals("a") ? "planet-a" : "planet-b", st.ownerEntityId(),
                st.ownershipType(), st.totalSlots(), st.modules(), st.storedCargoKg(), st.currentPowerGenerationKw(), st.currentPowerDemandKw(),
                st.currentShieldHealth(), st.maxShieldHealth(), st.currentHullHealth(), st.maxHullHealth(), st.armorMaterialId(),
                st.armorThicknessCm(), st.isOperational(), st.populations(), 500.0, st.orbitalPhaseRadians())).toList();
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        for (String material : List.of("silicon", "industrial_ceramics", "silicates")) orders.put(material, new MarketOrder(material, 100, 0, 1, 0));
        return state.toBuilder().solarSystems(systems).empires(List.of(owner)).industrialFacilities(List.of()).industryAccounts(List.of())
                .orbitalStations(stations).shipDesigns(List.of(design)).fleets(List.of(fleet)).commercialHubs(List.of(hub.withInventory(0, 1e6, orders)))
                .geologicalDeposits(List.of(new GeologicalDeposit("unsettled", "planet-b", "refined_iron", 100, 100, 1, false, "")))
                .fogOfWarStates(List.of()).build();
    }
    private double stock(GameState state, String id) { return state.commercialHubs().getFirst().activeOrders().get(id).supplyKg(); }
    private Empire owner(Empire e, List<String> known, List<String> systems) {
        return new Empire(e.id(), e.name(), e.raceId(), e.societyStructure(), e.treasuryCredits(), e.corporateTaxRate(), systems,
                e.ministries(), e.systemGovernorAssignments(), known, e.activeShipDesignIds());
    }
}
