package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.SaveGame;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.logistics.LogisticsProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CargoDeteriorationTest {
    @TempDir Path directory;
    private ShipDesign design(double solar, double cargoKw) {
        var profile = new ShipPowerProfile(solar, 0, 0, 500, 100, 100, 0, 0, 2, 10, cargoKw, .65, Map.of());
        return new ShipDesign("design", "Ship", "owner", ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                1000, 100, 1000, 100, 1, 0, 2000, false, false, ShipManufacturingProfile.baseline(), profile);
    }
    private GameState state(double solar, double cargoKw, Map<String, Double> cargo) {
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 500, cargo).withPowerState(ShipPowerState.empty());
        return GameState.builder().shipDesigns(List.of(design(solar, cargoKw)))
                .solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of())))
                .fleets(List.of(new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship)))).build();
    }
    private GameState tick(GameState state) { return state.withFleets(ShipPowerProcessor.advanceDay(state)); }
    private ShipInstance ship(GameState state) { return state.fleets().getFirst().ships().getFirst(); }

    @Test void graceAndMaterialRatesRemoveOnlyPhysicalVulnerableCargo() {
        var initial = state(0, 10, Map.of("food_matrix", 100.0, "agricultural_biomass", 100.0,
                "liquid_oxygen", 100.0, "liquid_hydrogen", 100.0, "steel", 100.0, "refined_uranium", 1.0));
        var first = tick(initial);
        assertEquals(100, ship(first).storedCargoKg().get("food_matrix"));
        assertEquals(100 * Math.pow(.98, .75), ship(first).storedCargoKg().get("liquid_oxygen"), 1e-9);
        assertEquals(100 * Math.pow(.97, .75), ship(first).storedCargoKg().get("liquid_hydrogen"), 1e-9);
        var second = tick(first);
        assertEquals(95, ship(second).storedCargoKg().get("food_matrix"), 1e-9);
        assertEquals(5, ship(second).powerState().cargoPreservation().lostKgToday().get("food_matrix"), 1e-9);
        assertEquals(100, ship(second).storedCargoKg().get("agricultural_biomass"));
        assertEquals(98, ship(tick(second)).storedCargoKg().get("agricultural_biomass"), 1e-9);
        assertEquals(100, ship(second).storedCargoKg().get("steel"));
        assertEquals(1, ship(second).storedCargoKg().get("refined_uranium"));
        assertEquals(500, ship(second).currentFuelKg());
        assertEquals(100, ship(initial).storedCargoKg().get("food_matrix"));
    }

    @Test void partialSupplyAccumulatesEquivalentHoursAndRestorationStopsFurtherLoss() {
        var initial = state(7, 10, Map.of("food_matrix", 100.0));
        var first = tick(initial);
        assertEquals(12, ship(first).powerState().cargoPreservation().exposureHours().get("food_matrix"), 1e-9);
        var second = tick(first);
        assertEquals(100, ship(second).storedCargoKg().get("food_matrix"));
        var third = tick(second);
        assertEquals(100 * Math.sqrt(.95), ship(third).storedCargoKg().get("food_matrix"), 1e-9);
        double restoredSupply = 2 + ShipPowerProcessor.cargoKw(third.shipDesigns().getFirst().powerProfile(),
                ship(third), third.shipDesigns().getFirst());
        var restored = tick(third.withShipDesigns(List.of(design(restoredSupply, 10))));
        assertEquals(ship(third).storedCargoKg(), ship(restored).storedCargoKg());
        assertEquals(36, ship(restored).powerState().cargoPreservation().exposureHours().get("food_matrix"), 1e-9);
        assertTrue(ship(restored).powerState().cargoPreservation().lostKgToday().isEmpty());
        var failedAgain = tick(restored.withShipDesigns(List.of(design(0, 10))));
        assertEquals(ship(restored).storedCargoKg().get("food_matrix") * .95,
                ship(failedAgain).storedCargoKg().get("food_matrix"), 1e-9);
    }

    @Test void emptyPoolsClearExposureAndStableCargoDoesNotCreateExposure() {
        var initial = tick(tick(state(0, 10, Map.of("food_matrix", 100.0))));
        var old = ship(initial);
        var empty = ShipPowerResupply.replace(initial, new ShipInstance(old.id(), old.designId(), old.ownerEntityId(),
                100, 0, 500, Map.of("steel", 100.0), 0, "", old.transitMode(), old.powerState()));
        var next = tick(empty);
        assertTrue(ship(next).powerState().cargoPreservation().exposureHours().isEmpty());
        assertTrue(ship(next).powerState().cargoPreservation().lostKgToday().isEmpty());
    }

    @Test void legacyDesignsAndUnmodeledCargoLoadsDoNotInventDeterioration() {
        var initial = state(0, 0, Map.of("food_matrix", 100.0));
        assertEquals(100, ship(tick(tick(initial))).storedCargoKg().get("food_matrix"));
        var d = initial.shipDesigns().getFirst();
        var legacy = new ShipDesign(d.id(), d.name(), "owner", d.role(), "steel", List.of(), "steel", 0,
                1000, 2000, 1000, 100, 1, 0, 2000, false, false);
        assertEquals(initial.fleets(), ShipPowerProcessor.advanceDay(initial.withShipDesigns(List.of(legacy))));
    }

    @Test void outageReplayAppliesCargoLossOnceUsingFinalStoppedDriveAccounting() {
        var initial = state(0, 10, Map.of("food_matrix", 100.0));
        var power = new ShipPowerState(Map.of(), "rp1", "uranium", 12, false, 1, 1, 0, 0, 0, 0, 0,
                new CargoPreservationState(Map.of("food_matrix", 24.0), Map.of()));
        var fleet = initial.fleets().getFirst().withShips(List.of(ship(initial).withPowerState(power)))
                .withLocation(new FleetLocation(FleetLocation.Site.deepSpace(), FleetLocation.Site.deepSpace("a_star"), 0, 2));
        var next = tick(initial.withFleets(List.of(fleet)));
        double unpowered = 24 - 12.0 / 22;
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, next.fleets().getFirst().interstellarMode());
        assertEquals(24 + unpowered, ship(next).powerState().cargoPreservation().exposureHours().get("food_matrix"), 1e-9);
        assertEquals(100 * Math.pow(.95, unpowered / 24), ship(next).storedCargoKg().get("food_matrix"), 1e-9);
    }

    @Test void electricalPreviewsAndStateCopiesPreserveExposureWithoutConsumingCargo() {
        var initial = tick(tick(state(0, 10, Map.of("food_matrix", 100.0))));
        var ship = ship(initial);
        var preservation = ship.powerState().cargoPreservation();
        var result = ShipPowerProcessor.interval(initial.shipDesigns().getFirst().powerProfile(), ship.powerState(),
                24, 0, 2, 10, 0);
        assertEquals(preservation, result.state().cargoPreservation());
        assertEquals(preservation, ship.powerState().withChargeInputToday(5).cargoPreservation());
        assertEquals(preservation, ship.powerState().resetPassengerOutage().cargoPreservation());
        assertEquals(95, ship.storedCargoKg().get("food_matrix"), 1e-9);
    }

    @Test void reloadPreservesExposureAndTheNextTickMatchesUninterruptedProcessing() throws Exception {
        var first = tick(state(0, 10, Map.of("food_matrix", 100.0)));
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("cargo.scsave").toFile();
        manager.save(file, first, 1, "2027-01-01T08:00:00");
        var save = manager.load(file);
        assertEquals(SaveGame.CURRENT_VERSION, save.version());
        var loaded = save.toGameState(0, "RUNNING");
        assertEquals(first.fleets(), loaded.fleets());
        assertEquals(tick(first).fleets(), tick(loaded).fleets());
        var mapper = new ObjectMapper();
        var json = mapper.readTree(file);
        ((ObjectNode) json.path("fleets").get(0).path("ships").get(0).path("powerState")).remove("cargoPreservation");
        ((ObjectNode) json).put("version", 29);
        mapper.writeValue(file, json);
        var older = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(CargoPreservationState.empty(), ship(older).powerState().cargoPreservation());
        assertEquals(ship(first).storedCargoKg(), ship(older).storedCargoKg());
        assertEquals(ship(first).currentFuelKg(), ship(older).currentFuelKg());
    }

    @Test void freightLossWritesOffCostOnceWithoutClaimingSalesOrDelivery() {
        var route = new TradeRoute("route", "Route", "owner", "origin", "destination", "food_matrix", 100, 0, 1000,
                List.of("ship"), 0, true).withLoadedCargo(100, 200);
        var spoiled = route.withAvailableCargo(95);
        assertEquals(95, spoiled.onboardKg());
        assertEquals(190, spoiled.onboardCostCredits());
        assertEquals(-10, spoiled.dailyOperatingResultCredits());
        assertEquals(-10, spoiled.cumulativeOperatingResultCredits());
        assertEquals(0, spoiled.totalVolumeMovedKg());
        assertSame(spoiled, spoiled.withAvailableCargo(95));
        var delivered = spoiled.withDeliveredCargo(95, 300, 0);
        assertEquals(100, delivered.cumulativeOperatingResultCredits());
        assertEquals(TradeRoute.RETURNING, spoiled.withAvailableCargo(0).phase());
        assertEquals(-200, spoiled.withAvailableCargo(0).cumulativeOperatingResultCredits());
    }

    @Test void logisticsReconcilesSpoiledPhysicalFreightAndDoesNotWriteItOffAgain() {
        var initial = state(0, 10, Map.of("food_matrix", 100.0));
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1000, 0, List.of("a"),
                List.of(), Map.of(), List.of(), List.of());
        var route = new TradeRoute("route", "Food", "owner", "origin", "destination", "food_matrix", 100, 0, 1000,
                List.of("ship"), 0, true).withLoadedCargo(100, 200);
        var spoiled = tick(tick(initial.toBuilder().empires(List.of(empire)).tradeRoutes(List.of(route)).build()));
        var logistics = new LogisticsProcessor();
        var reconciled = logistics.processTradeRoutes(spoiled).state();
        assertEquals(95, reconciled.tradeRoutes().getFirst().onboardKg(), 1e-9);
        assertEquals(190, reconciled.tradeRoutes().getFirst().onboardCostCredits(), 1e-9);
        assertEquals(-10, reconciled.tradeRoutes().getFirst().dailyOperatingResultCredits(), 1e-9);
        assertEquals(spoiled.empires(), reconciled.empires());
        assertEquals(0, reconciled.tradeRoutes().getFirst().totalVolumeMovedKg());
        var next = logistics.processTradeRoutes(reconciled).state();
        assertEquals(0, next.tradeRoutes().getFirst().dailyOperatingResultCredits());
        assertEquals(-10, next.tradeRoutes().getFirst().cumulativeOperatingResultCredits(), 1e-9);
        var spoiledAgain = logistics.processTradeRoutes(tick(next)).state();
        assertEquals(90.25, spoiledAgain.tradeRoutes().getFirst().onboardKg(), 1e-9);
        assertEquals(-9.5, spoiledAgain.tradeRoutes().getFirst().dailyOperatingResultCredits(), 1e-9);
    }
}
