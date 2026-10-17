package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FleetNeedsLifecycleTest {
    @TempDir Path directory;
    private GameState world() throws Exception {
        var known = new ArrayList<String>(DataModelLoader.loadTechnologies().stream().map(Technology::id).toList());
        var modules = ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false);
        modules.stream().map(ShipApplicationProduction::applicationId).filter(Objects::nonNull).forEach(known::add);
        var state = new SensorModuleTest().world(known); var evaluation = ShipBlueprintFactory.evaluate(state,
                new ShipDesignSpecification("template", "Military template", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5));
        assertTrue(evaluation.valid(), evaluation.errors().toString());
        var yard = state.orbitalStations().getFirst(); var module = yard.modules().getFirst();
        module = module.withPayroll(ShipyardWorkCapacity.requiredWorkers(module), 0);
        yard = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), "", yard.ownerEntityId(), yard.ownershipType(), yard.totalSlots(), List.of(module),
                Map.of(), 100, 0, 0, 0, 100, 100, "steel", 0, true);
        var stock = new HashMap<String, MarketOrder>();
        DataModelLoader.loadMaterials().forEach(material -> stock.put(material.id(), new MarketOrder(material.id(), 1000000, 0, 1, 0)));
        state = ResearchTestWorld.staffed(state.withShipDesigns(List.of(evaluation.design())));
        return state.toBuilder().orbitalStations(List.of(yard)).commercialHubs(List.of(new CommercialHub("hub", "yard", 0, 10000000, 10000000, 10, stock)))
                .powerGrids(List.of(new PowerGridState("yard", 10000, 0, 10000, 100000, 100000, false)))
                .fleets(List.of(new Fleet("existing", "Existing unarmed ship", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                        List.of(new ShipInstance("old-ship", "template", "owner", 1000, 0, 100, Map.of())), FleetLocation.at(FleetLocation.Site.docked("yard"))))).build();
    }
    private GameState discover(GameState state) throws Exception {
        var commands = EmpireResearchPlanner.plan(state, state.empires().getFirst());
        var start = (StartResearchCommand) commands.stream().filter(command -> command instanceof StartResearchCommand).findFirst().orElseThrow();
        var app = DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream())
                .filter(application -> application.id().equals(start.targetTechOrAppId())).findFirst().orElseThrow();
        assertTrue(start.isApplication()); assertNotNull(app.combatModule());
        assertTrue(app.combatModule().operationalStats().getOrDefault("weaponDamage", 0.0) > 0);
        assertTrue(start.decision().reason().contains("shortage"));
        var researching = start.apply(state); var project = researching.researchProjects().getFirst();
        // Seed prior legitimate progress to keep this a limited final paid-workforce installment probe.
        researching = researching.withResearchProjects(List.of(new ResearchProject(project.id(), project.empireId(), project.targetTechOrAppId(), true,
                project.requiredPoints() - 1, project.requiredPoints(), project.assignedScientists(), project.speedModifier(), project.decision())));
        var result = new ResearchTickProcessor().process(researching, List.of(), new ResearchProcessor(new Random(42)));
        assertTrue(result.projects().isEmpty());
        return researching.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).researchHistory(result.history()).build();
    }
    @Test void shortageDrivesApplicationResearchPaidConstructionReloadAndGuardAssignment() throws Exception {
        var initial = world(); assertEquals(1, EmpireFleetNeeds.assess(initial, initial.empires().getFirst()).size());
        assertTrue(FleetNeedsConstructionPlanner.plan(initial, initial.empires().getFirst()).isEmpty());
        var researched = discover(initial); var oldShip = initial.fleets().getFirst().ships().getFirst();
        var request = FleetNeedsConstructionPlanner.plan(researched, researched.empires().getFirst()).getFirst();
        var queue = new CommandQueue(); var first = queue.submitTracked(request); var duplicate = queue.submitTracked(request);
        var ordered = queue.drainAndExecute(researched); assertEquals(CommandOutcome.EXECUTED, first.join()); assertEquals(CommandOutcome.REJECTED, duplicate.join());
        var order = ordered.shipConstructionOrders().getFirst(); assertEquals("yard", order.deployment().targetId());
        assertTrue(EmpireFleetNeeds.assess(ordered, ordered.empires().getFirst()).isEmpty());
        assertEquals(initial.shipDesigns().getFirst(), ordered.shipDesigns().getFirst());
        var file = directory.resolve("fleet-need.scsave").toFile(); var saves = new SaveGameManager(directory);
        saves.save(file, ordered, 1, GameClock.START_TIME.toString()); var built = saves.load(file).toGameState(0, ordered.status());
        double money = built.empires().getFirst().treasuryCredits();
        for (int day = 0; day < 30 && !built.shipConstructionOrders().isEmpty(); day++) built = new ShipConstructionProcessor().process(built.withTurn(day));
        assertTrue(built.shipConstructionOrders().isEmpty()); assertTrue(built.empires().getFirst().treasuryCredits() < money);
        assertTrue(EmpireFleetNeeds.assess(built, built.empires().getFirst()).isEmpty());
        String shipId = "ship_" + order.id(); var deploy = new DeployAIShipCommand("owner", shipId);
        for (int step = 0; step < 10 && built.shipyardLedger().deployments().containsKey(shipId); step++) built = deploy.apply(built);
        assertFalse(built.shipyardLedger().deployments().containsKey(shipId), built.shipyardLedger().deployments().toString());
        var stationed = built.fleets().stream().filter(fleet -> fleet.ships().stream().anyMatch(ship -> ship.id().equals(shipId))).findFirst().orElseThrow();
        assertNotNull(stationed.guardOrder()); assertFalse(FleetWeaponRecovery.needs(built, stationed));
        assertEquals(oldShip, FleetOrganization.find(built, "existing").ships().getFirst());
        assertTrue(EmpireFleetNeeds.assess(built, built.empires().getFirst()).isEmpty());
    }
    @Test void vanishedDemandAndMissingStockRejectQueuedBuildWithoutRegisteringDesign() throws Exception {
        var state = discover(world()); var request = FleetNeedsConstructionPlanner.plan(state, state.empires().getFirst()).getFirst();
        var empty = state.withCommercialHubs(List.of()); assertFalse(request.validate(empty)); assertSame(empty, request.apply(empty));
        var lost = state.toBuilder().orbitalStations(List.of()).build(); assertFalse(request.validate(lost)); assertSame(lost, request.apply(lost));
        var owner = state.empires().getFirst();
        var poor = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), FleetNeedsPolicy.current().treasuryReserveCredits(),
                owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        assertFalse(request.validate(poor)); assertSame(poor, request.apply(poor));
    }
    @Test void travellingCommitmentCountsAtItsDestinationAndDestroyedShipsReleaseDemand() throws Exception {
        var state = discover(world()); var ordered = FleetNeedsConstructionPlanner.plan(state, state.empires().getFirst()).getFirst().apply(state);
        var order = ordered.shipConstructionOrders().getFirst(); var ship = new ShipInstance("incoming", order.designId(), "owner", 1000, 0, 100, Map.of());
        var travelling = new Fleet("incoming-fleet", "Incoming", "owner", "elsewhere", "sol", 0, 0, 0, true, "PASSIVE", List.of(ship));
        var committed = ordered.withShipConstructionOrders(List.of()).withFleets(List.of(travelling));
        assertTrue(EmpireFleetNeeds.assess(committed, committed.empires().getFirst()).isEmpty());
        var destroyed = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 0, 0, 0, Map.of());
        var uncovered = committed.withFleets(List.of(travelling.withShips(List.of(destroyed))));
        assertFalse(EmpireFleetNeeds.assess(uncovered, uncovered.empires().getFirst()).isEmpty());
    }
    @Test void escortShortageCreatesPaidEscortDesignAndStopsAfterPhysicalConvoyAssignment() throws Exception {
        var researched = discover(world());
        var firstBuild = FleetNeedsConstructionPlanner.plan(researched, researched.empires().getFirst()).getFirst().apply(researched);
        var combat = firstBuild.shipDesigns().getLast(); var template = firstBuild.shipDesigns().getFirst();
        var cargo = ShipBlueprintFactory.evaluate(researched, new ShipDesignSpecification("cargo", "Cargo", "owner", ShipRole.CARGO_TRANSPORT,
                "steel", template.equippedModuleIds(), "steel", .5)).design(); assertNotNull(cargo);
        var defender = new Fleet("defender", "Defender", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("defender-ship", combat.id(), "owner", 1000, 0, 100, Map.of())), FleetLocation.at(FleetLocation.Site.docked("yard")));
        var convoy = new Fleet("convoy", "Convoy", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("cargo-ship", cargo.id(), "owner", 1000, 0, 100, Map.of())), FleetLocation.at(FleetLocation.Site.docked("yard")));
        var designs = new ArrayList<>(firstBuild.shipDesigns()); designs.add(cargo);
        var state = firstBuild.withShipConstructionOrders(List.of()).withShipDesigns(designs).withFleets(List.of(defender, convoy));
        var needs = EmpireFleetNeeds.assess(state, state.empires().getFirst()); assertEquals(1, needs.size()); assertEquals(ShipDeploymentIntent.Kind.ESCORT, needs.getFirst().kind());
        var ordered = FleetNeedsConstructionPlanner.plan(state, state.empires().getFirst()).getFirst().apply(state);
        var order = ordered.shipConstructionOrders().getFirst(); assertEquals("convoy", order.deployment().targetId());
        assertEquals(ShipRole.ESCORT, ordered.shipDesigns().stream().filter(design -> order.designId().equals(design.id())).findFirst().orElseThrow().role());
        assertTrue(EmpireFleetNeeds.assess(ordered, ordered.empires().getFirst()).isEmpty());
        var built = ordered;
        for (int day = 0; day < 30 && !built.shipConstructionOrders().isEmpty(); day++) built = new ShipConstructionProcessor().process(built.withTurn(day));
        assertTrue(built.shipConstructionOrders().isEmpty()); var command = new DeployAIShipCommand("owner", "ship_" + order.id());
        for (int step = 0; step < 10 && built.shipyardLedger().deployments().containsKey("ship_" + order.id()); step++) built = command.apply(built);
        assertNotNull(FleetOrganization.find(built, "convoy").escortOrder());
        assertTrue(EmpireFleetNeeds.assess(built, built.empires().getFirst()).isEmpty());
    }
}
