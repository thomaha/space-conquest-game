package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIShipResearchRevisionTest {
    @TempDir Path directory;
    private GameState world() {
        var state = new SensorModuleTest().world(List.of("rocketry", "rocket_engines", "electricity", "industrial_production"));
        var modules = ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false);
        var design = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("old", "Old", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5));
        var cargo = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("civilian", "Civilian", "owner", ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", .5));
        assertTrue(design.valid(), design.errors().toString()); assertTrue(cargo.valid(), cargo.errors().toString());
        var yard = state.orbitalStations().getFirst(); var oldModule = yard.modules().getFirst();
        var module = oldModule.withPayroll(ShipyardWorkCapacity.requiredWorkers(oldModule), 0); // Settled yard staffing fixture.
        yard = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), yard.planetOrbitId(), yard.ownerEntityId(), yard.ownershipType(), yard.totalSlots(),
                List.of(module), yard.storedCargoKg(), 100, 0, 0, 0, 100, 100, "steel", 0, true);
        var stock = new HashMap<String, MarketOrder>();
        ShipConstructionRequirements.estimate(design.design()).materialsKg().forEach((id, kg) -> stock.put(id, new MarketOrder(id, kg * 5, 0, 1, 0)));
        var hub = new CommercialHub("yard-hub", "yard", 0, 10000000, 1000000, 10, stock);
        var existing = new ShipInstance("existing", "civilian", "owner", 1000, 0, 100, Map.of());
        return state.withShipDesigns(List.of(design.design(), cargo.design())).toBuilder().orbitalStations(List.of(yard)).commercialHubs(List.of(hub))
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)))
                .fleets(List.of(new Fleet("civilian-fleet", "Civilian", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(existing), FleetLocation.at(FleetLocation.Site.docked("yard"))))).build();
    }
    @Test void newAIOrdersUseResearchedBlueprintsWhileExistingShipsAndPendingQuotesStayFrozen() throws Exception {
        var initial = world(); var old = initial.shipDesigns().getFirst(); var existing = initial.fleets().getFirst().ships().getFirst();
        var request = new RequestAIShipBuildCommand("owner", "old", "sol"); assertTrue(request.validate(initial));
        var pending = request.apply(initial); var firstOrder = pending.shipConstructionOrders().getFirst();
        var path = new SelectOptimizationPathCommand("owner", "rocket_engines", "PATH_A");
        var changedWithPendingOrder = path.apply(pending); assertSame(changedWithPendingOrder, request.apply(changedWithPendingOrder));
        assertEquals(firstOrder, changedWithPendingOrder.shipConstructionOrders().getFirst()); assertEquals(old, changedWithPendingOrder.shipDesigns().getFirst());
        var researched = path.apply(initial); var queue = new CommandQueue(); queue.submit(request); queue.submit(request);
        var ordered = queue.drainAndExecute(researched); assertEquals(1, ordered.shipConstructionOrders().size());
        var order = ordered.shipConstructionOrders().getFirst(); assertNotEquals(old.id(), order.designId());
        var revision = ordered.shipDesigns().stream().filter(design -> order.designId().equals(design.id())).findFirst().orElseThrow();
        assertTrue(revision.totalThrustN() > old.totalThrustN()); assertFalse(ShipApplicationProduction.developmentChanged(ordered, revision));
        assertEquals(ShipConstructionRequirements.estimate(revision).materialsKg(), order.requiredMaterialsKg());
        assertEquals(old, ordered.shipDesigns().getFirst()); assertEquals(existing, ordered.fleets().getFirst().ships().getFirst());
        var repeated = request.apply(ordered.toBuilder().shipConstructionOrders(List.of()).build());
        assertEquals(revision.id(), repeated.shipConstructionOrders().getFirst().designId());
        assertEquals(ordered.shipDesigns(), repeated.shipDesigns());
        double money = ordered.empires().getFirst().treasuryCredits(); var built = ordered;
        for (int day = 0; day < 20 && !built.shipConstructionOrders().isEmpty(); day++) built = new ShipConstructionProcessor().process(built);
        assertTrue(built.shipConstructionOrders().isEmpty()); assertTrue(built.empires().getFirst().treasuryCredits() < money);
        assertTrue(built.fleets().stream().flatMap(fleet -> fleet.ships().stream()).anyMatch(ship -> revision.id().equals(ship.designId())));
        assertEquals(existing, built.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> ship.id().equals("existing")).findFirst().orElseThrow());
        var manager = new SaveGameManager(directory); var file = directory.resolve("revision.scsave").toFile(); manager.save(file, built, 1, GameClock.START_TIME.toString());
        var loaded = manager.load(file).toGameState(built.turn(), built.status()); assertEquals(built.shipDesigns(), loaded.shipDesigns()); assertEquals(built.fleets(), loaded.fleets());
    }
    @Test void anUnaffordableRevisionDoesNotRegisterAFreeDesignOrOrder() {
        var state = new SelectOptimizationPathCommand("owner", "rocket_engines", "PATH_A").apply(world());
        var empty = state.withCommercialHubs(List.of()); var request = new RequestAIShipBuildCommand("owner", "old", "sol");
        assertFalse(request.validate(empty)); assertSame(empty, request.apply(empty));
    }
}
