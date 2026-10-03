package com.spaceconquest.control;

import com.spaceconquest.control.command.ChargeShipBatteryCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.ResupplyShipPowerCommand;
import com.spaceconquest.control.command.SetShipSolarArraysCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.CargoPreservationState;
import com.spaceconquest.engine.ship.ShipPowerResupply;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShipPowerCommandTest {
    @Test void purchasesChargingAndDeploymentPreserveCargoExposure() {
        var exposure = new CargoPreservationState(Map.of("food_matrix", 42.0), Map.of("food_matrix", 5.0));
        var chemical = state(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, true);
        var ship = chemical.fleets().getFirst().ships().getFirst();
        chemical = ShipPowerResupply.replace(chemical, ship.withPowerState(ship.powerState().withCargoPreservation(exposure)));
        var purchased = new ResupplyShipPowerCommand("ship", "yard", "rp1", 100).apply(chemical);
        assertNotSame(chemical, purchased);
        assertEquals(exposure, purchased.fleets().getFirst().ships().getFirst().powerState().cargoPreservation());
        var solar = state(ShipComponentCatalog.SOLAR_ARRAY_ID, true);
        ship = solar.fleets().getFirst().ships().getFirst();
        solar = ShipPowerResupply.replace(solar, ship.withPowerState(ship.powerState().withCargoPreservation(exposure)));
        var charged = new ChargeShipBatteryCommand("ship", "yard", 100).apply(solar);
        assertNotSame(solar, charged);
        assertEquals(exposure, charged.fleets().getFirst().ships().getFirst().powerState().cargoPreservation());
        var stowed = new SetShipSolarArraysCommand("ship", false).apply(charged);
        assertNotSame(charged, stowed);
        assertEquals(exposure, stowed.fleets().getFirst().ships().getFirst().powerState().cargoPreservation());
    }
    private GameState state(String powerId, boolean researched) {
        var technologies = researched ? List.of("rocketry", "electricity", "solar_power", "nuclear_fission", "warp")
                : List.of("rocketry");
        var empire = new Empire("empire", "Empire", "human", "Individualist", 100_000, 0,
                List.of("a"), List.of(), Map.of(), technologies, List.of());
        var corp = new Corporation("corp", "Company", "empire", "yard", "LOGISTICS", 100_000,
                List.of(), List.of("ship"), List.of());
        var yard = new OrbitalStation("yard", "Yard", "a", "earth", "empire",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 20, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
        var orders = Map.of("rp1_kerosene", new MarketOrder("rp1_kerosene", 100_000, 0, 2, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 100_000, 0, 3, 0),
                "liquid_methane", new MarketOrder("liquid_methane", 100_000, 0, 2, 0),
                "refined_uranium", new MarketOrder("refined_uranium", 1000, 0, 10, 0));
        var state = GameState.builder().empires(List.of(empire)).corporations(List.of(corp)).orbitalStations(List.of(yard))
                .commercialHubs(List.of(new CommercialHub("hub", "yard", 0, 1_000_000, 0, 1, orders)))
                .powerGrids(List.of(new PowerGridState("yard", 100, 20, 80, 10_000, 3000, false)))
                .solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of()),
                        new SolarSystem("b", "B", "", 1, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of()))).build();
        var modules = ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false, powerId);
        var profile = ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList());
        var design = new ShipDesign("design", "Ship", "empire", ShipRole.EXPLORER, "steel", modules, "steel", 0,
                30_000, 30_000, 15_000, 400, 1, 0, 600_000, false, false, ShipManufacturingProfile.baseline(), profile);
        var ship = new ShipInstance("ship", "design", "corp", 100, 0, 15_000, Map.of()).withPowerState(ShipPowerState.empty());
        var fleet = new Fleet("fleet", "Fleet", "corp", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("yard")));
        return state.withShipDesigns(List.of(design)).withFleets(List.of(fleet));
    }

    @Test void paidMixturePurchaseDebitsBothMaterialsAndPreservesPropulsionAndFreight() {
        var initial = state(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, true);
        var command = new ResupplyShipPowerCommand("ship", "yard", "rp1", 1000);
        assertTrue(command.validate(initial));
        var paid = command.apply(initial);
        var ship = paid.fleets().getFirst().ships().getFirst();
        assertEquals(Map.of("rp1_kerosene", 280.0, "liquid_oxygen", 720.0), ship.powerState().generatorMaterialsKg());
        assertEquals(15_000, ship.currentFuelKg());
        assertTrue(ship.storedCargoKg().isEmpty());
        assertEquals(100_000 - 280 * 2 - 720 * 3, paid.corporations().getFirst().liquidCapitalReserves());
        assertEquals(100_000 - 280, paid.commercialHubs().getFirst().activeOrders().get("rp1_kerosene").supplyKg());
        assertEquals(100_000 - 720, paid.commercialHubs().getFirst().activeOrders().get("liquid_oxygen").supplyKg());
        assertEquals(0, initial.fleets().getFirst().ships().getFirst().generatorFuelMassKg());
    }

    @Test void missingOxidizerOrFullCompartmentRejectsTheWholePurchase() {
        var initial = state(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, true);
        var hub = initial.commercialHubs().getFirst();
        var orders = new HashMap<>(hub.activeOrders());
        orders.remove("liquid_oxygen");
        var dry = initial.toBuilder().commercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0,
                hub.storageCapacityKg(), 0, 1, orders))).build();
        assertSame(dry, new ResupplyShipPowerCommand("ship", "yard", "rp1", 1000).apply(dry));
        assertSame(initial, new ResupplyShipPowerCommand("ship", "yard", "rp1", 15_001).apply(initial));
        assertSame(initial, new ResupplyShipPowerCommand("ship", "yard", "rp1", Double.NaN).apply(initial));
    }

    @Test void aNonemptyCompartmentCannotChangeMixturesAndWrongSourceCannotRefuel() {
        var initial = state(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, true);
        var stocked = new ResupplyShipPowerCommand("ship", "yard", "rp1", 1000).apply(initial);
        assertSame(stocked, new ResupplyShipPowerCommand("ship", "yard", "methalox", 1000).apply(stocked));
        assertSame(stocked, new ResupplyShipPowerCommand("ship", "other_system", "rp1", 1000).apply(stocked));
        assertSame(initial, new ResupplyShipPowerCommand("ship", "yard", "uranium", 1).apply(initial));
    }

    @Test void reactorElectricityUsesADedicatedFeedAndActuallyConsumesIt() {
        var initial = state(ShipComponentCatalog.FISSION_REACTOR_ID, true);
        var stocked = new ResupplyShipPowerCommand("ship", "yard", "uranium", 1).apply(initial);
        assertEquals(1, stocked.fleets().getFirst().ships().getFirst().generatorFuelMassKg());
        var powered = ShipPowerProcessor.advanceDay(stocked).getFirst().ships().getFirst();
        assertEquals(1 - 5 * 24 / 6_000_000.0, powered.generatorFuelMassKg(), 1e-12);
        assertEquals(0, powered.powerState().lastUnmetEssentialKwh());
        assertTrue(powered.storedCargoKg().isEmpty());
    }

    @Test void gridChargingMovesRealEnergyPaysTheProviderAndAppliesLosses() {
        var initial = state(ShipComponentCatalog.SOLAR_ARRAY_ID, true);
        var command = new ChargeShipBatteryCommand("ship", "yard", 100);
        assertTrue(command.validate(initial));
        var charged = command.apply(initial);
        assertEquals(90, charged.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh());
        assertEquals(2900, charged.powerGrids().getFirst().currentStoredKwh());
        assertEquals(99_998, charged.corporations().getFirst().liquidCapitalReserves());
        assertEquals(100_002, charged.empires().getFirst().treasuryCredits());
        assertSame(charged, new ChargeShipBatteryCommand("ship", "yard", 500).apply(charged));
        var noGrid = initial.toBuilder().powerGrids(List.of()).build();
        assertSame(noGrid, command.apply(noGrid));
    }

    @Test void repeatedChargingCannotExceedTheDailyChargeRate() {
        var initial = state(ShipComponentCatalog.SOLAR_ARRAY_ID, true);
        var old = initial.shipDesigns().getFirst();
        var p = old.powerProfile();
        var limited = new ShipPowerProfile(p.solarKw(), 0, 0, p.batteryKwh(), 1, p.dischargeKw(),
                0, 0, p.hotelKw(), p.driveKw(), p.cargoKw(), p.electricDriveEfficiency(), p.fuels());
        var design = new ShipDesign(old.id(), old.name(), old.ownerEntityId(), old.role(), old.hullMaterialId(),
                old.equippedModuleIds(), old.armorMaterialId(), 0, old.totalDryMassKg(), old.maxCargoMassKg(),
                old.fuelCapacityKg(), old.powerBalanceKw(), 1, 0, old.totalThrustN(), false, false,
                old.manufacturingProfile(), limited);
        var ready = initial.withShipDesigns(List.of(design));
        var first = new ChargeShipBatteryCommand("ship", "yard", 20).apply(ready);
        assertNotSame(ready, first);
        assertSame(first, new ChargeShipBatteryCommand("ship", "yard", 5).apply(first));
        assertTrue(new ChargeShipBatteryCommand("ship", "yard", 4).validate(first));
    }

    @Test void solarRequiresBothResearchEntriesAndSpaceVoyagesCanChangeArrays() {
        var request = new ShipDesignSpecification("solar", "Solar ship", "empire", ShipRole.EXPLORER, "steel",
                ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false, ShipComponentCatalog.SOLAR_ARRAY_ID), "steel", 0);
        var early = state(ShipComponentCatalog.SOLAR_ARRAY_ID, false);
        assertFalse(ShipBlueprintFactory.evaluate(early, request).valid());
        var ready = state(ShipComponentCatalog.SOLAR_ARRAY_ID, true);
        assertTrue(ShipBlueprintFactory.evaluate(ready, request).valid());
        var stow = new SetShipSolarArraysCommand("ship", false);
        assertTrue(stow.validate(ready));
        assertFalse(stow.apply(ready).fleets().getFirst().ships().getFirst().powerState().arraysDeployed());
        var inTransit = ready.withFleets(List.of(ready.fleets().getFirst().withLocation(
                ready.fleets().getFirst().location().depart(FleetLocation.Site.deepSpace(), 2))));
        assertTrue(stow.validate(inTransit));
        assertTrue(new SetShipSolarArraysCommand("ship", true).validate(inTransit));
        assertFalse(new ChargeShipBatteryCommand("ship", "yard", 100).validate(inTransit));
        assertFalse(new ResupplyShipPowerCommand("ship", "yard", "rp1", 100).validate(inTransit));
    }

    @Test void departureRetainsFailureDetailsAndCannotExecuteWithoutElectricalReserves() {
        var initial = state(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, true);
        var move = new MoveFleetCommand("fleet", "b");
        var preview = move.preview(initial);
        assertNotNull(preview);
        assertFalse(preview.ready());
        assertFalse(move.validate(initial));
        assertSame(initial, move.apply(initial));
        var stocked = new ResupplyShipPowerCommand("ship", "yard", "rp1", 4000).apply(initial);
        assertTrue(move.validate(stocked));
        var moved = move.apply(stocked);
        assertTrue(moved.fleets().getFirst().hasInterstellarOrder());
        assertFalse(new ResupplyShipPowerCommand("ship", "yard", "rp1", 10).validate(moved));
    }
}
