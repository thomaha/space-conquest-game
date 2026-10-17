package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.industry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceBatteryLifecycleTest {
    @TempDir Path directory;
    private PlanetaryDefenseBattery battery(GameState state) { return state.auxiliaryForces().batteries().getFirst().battery(); }
    private GameState reload(GameState state, String name) throws Exception {
        var manager = new SaveGameManager(directory); var file = manager.save(name, state, 1, GameClock.START_TIME.toString());
        var loaded = manager.load(file.toFile()).toGameState(0, "RUNNING");
        assertEquals(state.auxiliaryForces(), loaded.auxiliaryForces()); assertEquals(state.fleets(), loaded.fleets());
        assertEquals(state.fleetEngagements(), loaded.fleetEngagements()); assertEquals(state.commercialHubs(), loaded.commercialHubs());
        return loaded;
    }
    private GameState world() throws Exception {
        var state = new SurfaceBatteryCampaignTest().world(CombatModuleCatalog.ION); var owner = state.empires().getFirst();
        var funded = new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 1e6, 0, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        var mapper = new ObjectMapper(); var support = state.shipDesigns().getFirst(); ObjectNode json = mapper.valueToTree(support);
        var storage = ShipComponentCatalog.module(ShipComponentCatalog.BATTERY_ID);
        ((ArrayNode) json.get("equippedModuleIds")).add(storage.id());
        ((ObjectNode) json.get("powerProfile")).put("chargeKw", storage.operationalStats().get("chargeKw"))
                .put("batteryKwh", storage.operationalStats().get("batteryKwh"));
        state = state.withShipDesigns(List.of(mapper.treeToValue(json, ShipDesign.class), state.shipDesigns().getLast()));
        var stock = new HashMap<String, MarketOrder>(); DataModelLoader.loadMaterials().forEach(m -> stock.put(m.id(), new MarketOrder(m.id(), 1e6, 0, 1, 0)));
        return state.withEmpires(List.of(funded, state.empires().getLast())).toBuilder()
                .commercialHubs(List.of(new CommercialHub("hub", "earth", 0, 1e9, 1e8, 10, stock)))
                .industrialFacilities(List.of(new IndustrialFacility("yard", "earth", "surface_shipyard", "own", "PUBLIC_STATE", 4, 100, "industrial_worker", false, 0)))
                .powerGrids(List.of(new PowerGridState("earth", 0, 0, 0, 1000, 1000, false)))
                .industryAccounts(List.of(new IndustryAccount("yard", Map.of(), Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 100))).build();
    }
    @Test void savedIonEncounterPartialRepairPaidResupplyAndRematchRetainExactReceipts() throws Exception {
        var initial = world(); var encounter = SurfaceBatteryEncounterResolver.resolve(initial, new TacticalCombatProcessor(new Random(42)));
        assertEquals(initial.auxiliaryForces().batteries().getFirst().battery().hullHealth(), battery(encounter).hullHealth());
        assertEquals(0, battery(encounter).moduleDamage().condition(0)); assertTrue(battery(encounter).stores().ammunitionKg().isEmpty());
        var loaded = reload(encounter, "after-ion"); var receipt = loaded.fleetEngagements().getFirst();
        var partial = BatteryMaintenance.advance(loaded, new HashMap<>(Map.of("sol/earth", 25.0)), Map.of("yard", 100));
        assertTrue(battery(partial).moduleDamage().condition(0) > 0 && battery(partial).moduleDamage().condition(0) < 1);
        assertTrue(partial.empires().getFirst().treasuryCredits() < loaded.empires().getFirst().treasuryCredits());
        var restored = new ShipConstructionProcessor().process(reload(partial, "partial-repair"));
        assertEquals(1, battery(restored).moduleDamage().condition(0)); assertEquals(battery(loaded).stores(), battery(restored).stores());
        var bought = AuxiliaryWeaponResupply.buyBatteryAmmunition(restored, "own", battery(restored), 1); assertTrue(bought.purchased());
        var supplied = bought.state().toBuilder().auxiliaryForces(bought.state().auxiliaryForces().withBattery(bought.battery())).build();
        double charge = battery(supplied).weapon().powerDrawKw() / 60 + WeaponSalvo.of(battery(supplied).weapon()).energyKwh();
        double gridBefore = supplied.powerGrids().getFirst().currentStoredKwh();
        supplied = ShipBatteryCharging.charge(supplied, supplied.fleets().getFirst().ships().getFirst().id(), "earth", charge / ShipPowerProcessor.CHARGING_EFFICIENCY);
        assertEquals(charge / ShipPowerProcessor.CHARGING_EFFICIENCY, gridBefore - supplied.powerGrids().getFirst().currentStoredKwh(), 1e-9);
        var sourceFleet = supplied.fleets().getFirst(); var source = sourceFleet.ships().getFirst();
        var charged = AuxiliaryWeaponResupply.chargeBattery(supplied, "own", battery(supplied), source.id(), charge);
        assertEquals(charge, source.powerState().batteryChargeKwh() - charged.source().powerState().batteryChargeKwh(), 1e-9);
        supplied = supplied.withFleets(List.of(sourceFleet.withShips(List.of(charged.source())), supplied.fleets().getLast()))
                .toBuilder().auxiliaryForces(supplied.auxiliaryForces().withBattery(charged.battery())).build();
        var ready = reload(supplied, "resupplied");
        var rematch = SurfaceBatteryEncounterResolver.resolve(ready, new TacticalCombatProcessor(new Random(42)));
        assertEquals(2, rematch.fleetEngagements().size()); assertEquals(receipt, rematch.fleetEngagements().getFirst());
        assertEquals(1, rematch.fleetEngagements().getLast().surfaceCombat().batteries().getFirst().paidShots());
        assertTrue(battery(rematch).stores().ammunitionKg().isEmpty()); reload(rematch, "rematch");
        assertThrows(UnsupportedOperationException.class, () -> receipt.surfaceCombat().batteries().clear());
    }
    @Test void olderHistoryWithoutReceiptsLoadsWithoutInventedCostsOrWithdrawal() throws Exception {
        var encounter = SurfaceBatteryEncounterResolver.resolve(world(), new TacticalCombatProcessor(new Random(42)));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ObjectNode json = mapper.valueToTree(SaveGame.fromGameState(encounter, "now", 1, GameClock.START_TIME.toString()));
        json.put("version", com.spaceconquest.engine.SaveGame.CURRENT_VERSION); ((ObjectNode) json.get("fleetEngagements").get(0)).remove("surfaceCombat");
        var old = mapper.treeToValue(json, SaveGame.class).toGameState(0, "RUNNING");
        assertNull(old.fleetEngagements().getFirst().surfaceCombat()); assertEquals(encounter.auxiliaryForces(), old.auxiliaryForces());
    }
}
