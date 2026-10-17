package com.spaceconquest.control.command;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.ConstructionProgress;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.logistics.LaunchService;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoadOrbitalCargoCommandTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void kineticDefenseShipBuysAndLiftsFiniteCartridgesWithinCargoCapacity() {
        var initial = state();
        var design = new ShipDesign("cargo_design", "Kinetic guard", "emp", ShipRole.COMBAT_SHIP, "steel",
                List.of(com.spaceconquest.engine.ship.CombatModuleCatalog.KINETIC_POINT_DEFENSE), "steel", 0,
                1000, 20, 0, 1, 0, 0, true, false);
        var hub = initial.commercialHubs().getFirst();
        var orders = new java.util.HashMap<>(hub.activeOrders());
        orders.put("kinetic_pd_round", new MarketOrder("kinetic_pd_round", 15, 0, 2, 0));
        initial = initial.withShipDesigns(List.of(design)).withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(),
                hub.transactionTariffRate(), hub.storageCapacityKg(), hub.currentStoredWeightKg() + 15, hub.logisticsRangeUnits(), orders)));
        var load = new LoadOrbitalCargoCommand("cargo", "earth", "kinetic_pd_round", 15);
        assertTrue(load.validate(initial)); var paid = load.apply(initial);
        assertEquals(3, com.spaceconquest.engine.ship.ShipPointDefense.rounds(paid.fleets().getFirst().ships().getFirst()));
        assertEquals(0, paid.commercialHubs().getFirst().activeOrders().get("kinetic_pd_round").supplyKg());
        assertTrue(paid.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits() - 30);
        assertFalse(load.validate(paid));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "kinetic_pd_round", 21).validate(initial));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "refined_iron", 1).validate(initial));
    }
    @Test void armedCombatShipCanBuyAndLiftFiniteAmmunitionButCannotUseTransportOnlyCargoLoading() {
        var initial = state();
        var design = new ShipDesign("cargo_design", "Missile ship", "emp", ShipRole.COMBAT_SHIP, "steel",
                List.of(com.spaceconquest.engine.ship.CombatModuleCatalog.MISSILE), "steel", 0,
                1000, 200, 0, 1, 0, 0, true, false);
        var hub = initial.commercialHubs().getFirst();
        var orders = new java.util.HashMap<>(hub.activeOrders());
        orders.put("guided_missile", new MarketOrder("guided_missile", 100, 0, 2, 0));
        initial = initial.withShipDesigns(List.of(design)).withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(),
                hub.transactionTariffRate(), hub.storageCapacityKg(), hub.currentStoredWeightKg() + 100, hub.logisticsRangeUnits(), orders)));
        var load = new LoadOrbitalCargoCommand("cargo", "earth", "guided_missile", 100);
        assertTrue(load.validate(initial));
        var paid = load.apply(initial);
        assertEquals(100, paid.fleets().getFirst().ships().getFirst().storedCargoKg().get("guided_missile"));
        assertEquals(0, paid.commercialHubs().getFirst().activeOrders().get("guided_missile").supplyKg());
        assertTrue(paid.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits() - 200);
        assertFalse(load.validate(paid));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "guided_missile", 201).validate(initial));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "refined_iron", 1).validate(initial));
        assertEquals(100, initial.commercialHubs().getFirst().activeOrders().get("guided_missile").supplyKg());
    }
    @Test
    void surfaceShipBuysLocalSuppliesWithoutOrbitalLaunch() {
        GameState orbit = state();
        GameState surface = orbit.withFleets(List.of(orbit.fleets().getFirst()
                .withLocation(FleetLocation.at(FleetLocation.Site.surface("earth")))));
        LoadSurfaceCargoCommand load = new LoadSurfaceCargoCommand("cargo", "earth",
                "refined_iron", 20.0);
        assertTrue(load.validate(surface));
        GameState loaded = load.apply(surface);
        assertEquals(20.0, loaded.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("refined_iron"), 0.000001);
        assertEquals(80.0, loaded.commercialHubs().getFirst().activeOrders()
                .get("refined_iron").supplyKg(), 0.000001);
        assertEquals(999_980.0, loaded.empires().getFirst().treasuryCredits(), 0.000001);
        assertTrue(loaded.launchUsageKg().isEmpty());
        assertFalse(new LoadSurfaceCargoCommand("cargo", "mars", "refined_iron", 1.0)
                .validate(surface));
    }

    @Test
    void liftsOnlyPaidLocalStockIntoOwnedTransportAndConstructionConsumesManifest() throws Exception {
        GameState state = state();
        LoadOrbitalCargoCommand load = new LoadOrbitalCargoCommand("cargo", "earth",
                "refined_iron", 20.0, LaunchService.Mode.ROCKET);
        assertTrue(load.validate(state));
        GameState loaded = load.apply(state);
        assertEquals(20.0, loaded.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("refined_iron"), 0.000001);
        assertEquals(80.0, loaded.commercialHubs().getFirst().activeOrders()
                .get("refined_iron").supplyKg(), 0.000001);
        assertTrue(loaded.empires().getFirst().treasuryCredits() < 999_980.0);
        double purchaseAndLift = state.empires().getFirst().treasuryCredits()
                - loaded.empires().getFirst().treasuryCredits();
        assertEquals(purchaseAndLift, loaded.marketAccounts().getFirst()
                .unsettledSalesCredits() + loaded.corporations().getFirst()
                .liquidCapitalReserves(), 0.000001);
        assertTrue(loaded.commercialHubs().getFirst().activeOrders()
                .get("rp1_kerosene").supplyKg() < 1_000_000.0);
        assertTrue(loaded.commercialHubs().getFirst().activeOrders()
                .get("liquid_oxygen").supplyKg() < 1_000_000.0);
        assertEquals(100.0, loaded.commercialHubs().getFirst().activeOrders()
                .get("refined_iron").supplyKg() + loaded.fleets().getFirst()
                .ships().getFirst().storedCargoKg().get("refined_iron"), 0.000001);

        var saves = new com.spaceconquest.engine.SaveGameManager(directory);
        var restored = saves.load(saves.save("paid-orbital-delivery", loaded, 1,
                com.spaceconquest.engine.GameClock.START_TIME.toString()).toFile()).toGameState(loaded.turn(), loaded.status());
        assertEquals(loaded.fleets(), restored.fleets());
        assertEquals(loaded.commercialHubs(), restored.commercialHubs());
        assertEquals(loaded.launchActivities(), restored.launchActivities());
        assertEquals(loaded.launchUsageKg(), restored.launchUsageKg());
        loaded = restored;

        ConstructionProgress.Step built = ConstructionProgress.advanceOrbital(loaded, "sol",
                "earth", "emp", Map.of("refined_iron", 20.0), Map.of(), 0.0,
                1.0, 1.0);
        assertTrue(built.complete());
        assertEquals(0.0, built.state().fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("refined_iron"), 0.000001);
        assertEquals(80.0, built.state().commercialHubs().getFirst().activeOrders()
                .get("refined_iron").supplyKg(), 0.000001);
        assertEquals(20.0, loaded.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("refined_iron") - built.state().fleets().getFirst()
                .ships().getFirst().storedCargoKg().get("refined_iron"), 0.000001);
    }

    @Test
    void rejectsOverCapacityAndForeignSystemSupply() {
        GameState state = state();
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "refined_iron", 101.0)
                .validate(state));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "mars", "refined_iron", 1.0)
                .validate(state));
    }

    @Test
    void elevatorCarriesGoodsWithoutBurningRocketFuelAndHonorsDailyCapacity() {
        GameState state = state().toBuilder().spaceElevators(List.of(new SpaceElevator(
                "elevator", "earth", "provider", 20.0, 0.95, 100.0, true)))
                .powerGrids(List.of(new PowerGridState("earth", 100, 0, 100,
                        0, 0, false))).build();
        state = com.spaceconquest.engine.logistics.LaunchServiceWork.commission(state, state.spaceElevators().getFirst());
        LoadOrbitalCargoCommand load = new LoadOrbitalCargoCommand("cargo", "earth",
                "refined_iron", 20.0, LaunchService.Mode.SPACE_ELEVATOR);
        assertTrue(load.validate(state));
        GameState lifted = load.apply(state);
        assertEquals(10_000.0, lifted.commercialHubs().getFirst().activeOrders()
                .get("hydrocarbons").supplyKg(), 0.001);
        assertEquals(1_000_000.0, lifted.commercialHubs().getFirst().activeOrders()
                .get("liquid_oxygen").supplyKg(), 0.001);
        assertTrue(lifted.corporations().getFirst().liquidCapitalReserves() > 0.0);
        assertEquals(20.0, lifted.launchUsageKg().get("elevator"), 0.001);
        assertEquals(99.6, lifted.powerGrids().getFirst().netBalanceKw(), 0.001);
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "refined_iron",
                1.0, LaunchService.Mode.SPACE_ELEVATOR).validate(lifted));
    }

    @Test
    void localTravelSeparatesSurfaceTransitAndOrbitForCargoLoading() {
        GameState orbit = state();
        MoveFleetLocalCommand land = new MoveFleetLocalCommand("fleet",
                FleetLocation.Kind.SURFACE, "earth");
        GameState descending = land.apply(orbit);
        LoadOrbitalCargoCommand load = new LoadOrbitalCargoCommand("cargo", "earth",
                "refined_iron", 1.0);
        assertTrue(descending.fleets().getFirst().location().inTransit());
        assertFalse(load.validate(descending));
        FleetProcessor movement = new FleetProcessor();
        GameState surface = descending.withFleets(movement.processFleetMovements(
                descending.fleets(), List.of(), List.of()));
        assertTrue(surface.fleets().getFirst().location().isAt(
                FleetLocation.Site.surface("earth")));
        assertFalse(load.validate(surface));
        GameState ascending = new MoveFleetLocalCommand("fleet", FleetLocation.Kind.ORBIT,
                "earth").apply(surface);
        assertFalse(load.validate(ascending));
        GameState returned = ascending.withFleets(movement.processFleetMovements(
                ascending.fleets(), List.of(), List.of()));
        assertTrue(returned.fleets().getFirst().location().isAt(
                FleetLocation.Site.orbit("earth")));
        assertTrue(load.validate(returned));
    }

    @Test
    void orbitalRefuelingBuysFuelAndUsesLaunchService() {
        GameState initial = state();
        ShipDesign old = initial.shipDesigns().getFirst();
        ShipDesign chemical = new ShipDesign(old.id(), old.name(), old.ownerEntityId(),
                old.role(), old.hullMaterialId(), List.of("mod_chemical_rocket"),
                old.armorMaterialId(), old.armorThicknessCm(), old.totalDryMassKg(),
                old.maxCargoMassKg(), old.powerBalanceKw(),
                old.calculatedStructuralIntegrity(), old.minLaunchThrustRequiredN(),
                600_000, old.isValidForLaunch(), old.isProprietaryCorporateDesign());
        GameState fueledDesign = initial.toBuilder().shipDesigns(List.of(chemical)).build();
        RefuelShipCommand refuel = new RefuelShipCommand("cargo", "earth", 10.0);
        assertTrue(refuel.validate(fueledDesign));
        GameState paid = refuel.apply(fueledDesign);
        assertEquals(110.0, paid.fleets().getFirst().ships().getFirst().currentFuelKg(), 0.001);
        assertEquals(1, paid.launchActivities().size());
        assertTrue(paid.empires().getFirst().treasuryCredits()
                < fueledDesign.empires().getFirst().treasuryCredits() - 10.0);
    }

    @Test
    void massDriverCombatHullBuysFiniteSlugsAndPaysForOrbitalLoading() {
        var initial = state();
        var design = new ShipDesign("cargo_design", "Railgun", "emp", ShipRole.COMBAT_SHIP,
                "steel", List.of(com.spaceconquest.engine.ship.CombatModuleCatalog.MASS_DRIVER), "steel", 0, 1000, 50, 0, 1, 0, 0, true, false);
        var hub = initial.commercialHubs().getFirst();
        var orders = new java.util.HashMap<>(hub.activeOrders());
        orders.put("mass_driver_slug", new MarketOrder("mass_driver_slug", 40, 0, 2, 0));
        initial = initial.withShipDesigns(List.of(design)).withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(),
                hub.transactionTariffRate(), hub.storageCapacityKg(), hub.currentStoredWeightKg() + 40, hub.logisticsRangeUnits(), orders)));
        var load = new LoadOrbitalCargoCommand("cargo", "earth", "mass_driver_slug", 40);
        assertTrue(load.validate(initial));
        var paid = load.apply(initial);
        assertEquals(40, paid.fleets().getFirst().ships().getFirst().storedCargoKg().get("mass_driver_slug"));
        assertEquals(0, paid.commercialHubs().getFirst().activeOrders().get("mass_driver_slug").supplyKg());
        assertTrue(paid.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits() - 80);
        assertFalse(load.validate(paid));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "mass_driver_slug", 51).validate(initial));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "refined_iron", 1).validate(initial));
        assertEquals(40, initial.commercialHubs().getFirst().activeOrders().get("mass_driver_slug").supplyKg());
    }

    @Test
    void cannonCombatHullBuysFiniteRoundsAndPaysForOrbitalLoading() {
        var initial = state();
        var design = new ShipDesign("cargo_design", "Cannon", "emp", ShipRole.COMBAT_SHIP,
                "steel", List.of(com.spaceconquest.engine.ship.CombatModuleCatalog.GUN), "steel", 0, 1000, 50, 0, 1, 0, 0, true, false);
        var hub = initial.commercialHubs().getFirst();
        var orders = new java.util.HashMap<>(hub.activeOrders());
        orders.put("cannon_round", new MarketOrder("cannon_round", 40, 0, 2, 0));
        initial = initial.withShipDesigns(List.of(design)).withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(),
                hub.transactionTariffRate(), hub.storageCapacityKg(), hub.currentStoredWeightKg() + 40, hub.logisticsRangeUnits(), orders)));
        var load = new LoadOrbitalCargoCommand("cargo", "earth", "cannon_round", 40);
        assertTrue(load.validate(initial));
        var paid = load.apply(initial);
        assertEquals(40, paid.fleets().getFirst().ships().getFirst().storedCargoKg().get("cannon_round"));
        assertEquals(0, paid.commercialHubs().getFirst().activeOrders().get("cannon_round").supplyKg());
        assertTrue(paid.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits() - 80);
        assertFalse(load.validate(paid));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "cannon_round", 51).validate(initial));
        assertFalse(new LoadOrbitalCargoCommand("cargo", "earth", "refined_iron", 1).validate(initial));
        assertEquals(40, initial.commercialHubs().getFirst().activeOrders().get("cannon_round").supplyKg());
    }

    @Test
    void ammunitionQuoteMatchesActualPurchaseAndLiftWithoutSpendingSnapshotStores() {
        var initial = ammunitionState(10);
        var quote = AmmunitionResupplyQuote.inspect(initial, "emp", "cargo", "mod_ship_gun");
        assertTrue(quote.available()); assertEquals(5, quote.quantityKg()); assertEquals(10, quote.localStockKg());
        assertEquals(10, quote.purchaseCredits()); assertTrue(quote.liftCredits() > 0);
        var paid = quote.command().apply(initial);
        assertEquals(quote.purchaseCredits() + quote.liftCredits(),
                initial.empires().getFirst().treasuryCredits() - paid.empires().getFirst().treasuryCredits(), 1e-6);
        assertEquals(5, paid.fleets().getFirst().ships().getFirst().storedCargoKg().get("cannon_round"));
        assertEquals(10, initial.commercialHubs().getFirst().activeOrders().get("cannon_round").supplyKg());
        assertTrue(initial.fleets().getFirst().ships().getFirst().storedCargoKg().isEmpty());
        assertEquals(quote, AmmunitionResupplyQuote.inspect(initial, "emp", "cargo", "mod_ship_gun"));
        var surface = initial.withFleets(List.of(initial.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.surface("earth")))));
        var local = AmmunitionResupplyQuote.inspect(surface, "emp", "cargo", "mod_ship_gun");
        assertInstanceOf(LoadSurfaceCargoCommand.class, local.command()); assertEquals(0, local.liftCredits());
        assertEquals(10, surface.empires().getFirst().treasuryCredits() - local.command().apply(surface).empires().getFirst().treasuryCredits());
    }
    @Test
    void ammunitionQuoteExplainsStockFundsCapacityTravelAndOwnershipBlockers() {
        var initial = ammunitionState(4);
        assertTrue(AmmunitionResupplyQuote.inspect(initial, "emp", "cargo", "mod_ship_gun").reason().contains("whole round"));
        initial = ammunitionState(10);
        assertFalse(AmmunitionResupplyQuote.inspect(initial, "foreign", "cargo", "mod_ship_gun").available());
        var traveling = initial.withFleets(List.of(initial.fleets().getFirst().withLocation(initial.fleets().getFirst().location()
                .depart(FleetLocation.Site.deepSpace("goal"), 1))));
        assertTrue(AmmunitionResupplyQuote.inspect(traveling, "emp", "cargo", "mod_ship_gun").reason().contains("travel"));
        var empire = initial.empires().getFirst();
        var broke = initial.toBuilder().empires(List.of(new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(),
                0, empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(),
                empire.unlockedTechIds(), empire.activeShipDesignIds()))).build();
        assertTrue(AmmunitionResupplyQuote.inspect(broke, "emp", "cargo", "mod_ship_gun").reason().contains("funds"));
        var noLift = initial.toBuilder().industrialFacilities(List.of()).build();
        assertTrue(AmmunitionResupplyQuote.inspect(noLift, "emp", "cargo", "mod_ship_gun").reason().contains("lift service"));
        var full = new ShipInstance("cargo", "cargo_design", "emp", 100, 0, 100, Map.of("steel", 100.0));
        var atCapacity = initial.withFleets(List.of(initial.fleets().getFirst().withShips(List.of(full))));
        assertTrue(AmmunitionResupplyQuote.inspect(atCapacity, "emp", "cargo", "mod_ship_gun").reason().contains("cargo capacity"));
        var quote = AmmunitionResupplyQuote.inspect(initial, "emp", "cargo", "mod_ship_gun");
        assertFalse(quote.command().validate(ammunitionState(4)));
    }
    private GameState ammunitionState(double stock) {
        var initial = state(); var d = initial.shipDesigns().getFirst();
        var armed = new ShipDesign(d.id(), "Cannon ship", "emp", ShipRole.COMBAT_SHIP, "steel", List.of("mod_ship_gun"),
                "steel", 0, 1000, 100, 0, 1, 0, 0, true, false);
        var hub = initial.commercialHubs().getFirst(); var orders = new java.util.HashMap<>(hub.activeOrders());
        orders.put("cannon_round", new MarketOrder("cannon_round", stock, 0, 2, 0));
        return initial.withShipDesigns(List.of(armed)).withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(),
                hub.transactionTariffRate(), hub.storageCapacityKg(), hub.currentStoredWeightKg() + stock, hub.logisticsRangeUnits(), orders)));
    }
    private GameState state() {
        Planet earth = new Planet("earth", "Earth", "", 1.0, 9.81, 1.0, 0.0,
                12_742.0, "terrestrial", "breathable", true, 1.0,
                List.of(), List.of(), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1.0, 1.0, "Yellow", List.of(earth), List.of());
        CommercialHub hub = new CommercialHub("hub_earth", "earth", 0.0,
                3_000_000.0, 2_010_100.0, 10.0, Map.of("refined_iron",
                new MarketOrder("refined_iron", 100.0, 0.0, 1.0, 0.0),
                "hydrocarbons", new MarketOrder("hydrocarbons", 10_000.0, 0.0, 1.0, 0.0),
                "rp1_kerosene", new MarketOrder("rp1_kerosene", 1_000_000.0, 0.0, 2.0, 0.0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 1_000_000.0, 0.0, 2.0, 0.0)));
        ShipDesign design = new ShipDesign("cargo_design", "Cargo", "emp",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0.0,
                1_000.0, 100.0, 0.0, 1.0, 0.0, 0.0, true, false);
        Fleet fleet = new Fleet("fleet", "Freighter", "emp", "sol", "",
                0.0, 0.0, 0.0, false, "PASSIVE", List.of(new ShipInstance("cargo",
                design.id(), "emp", 100.0, 0.0, 100.0, Map.of())),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        return GameState.builder().solarSystems(List.of(sol))
                .empires(List.of(new Empire("emp", "Empire", "human", "Individualist",
                        1_000_000.0, 0.15, List.of("sol"), List.of(), Map.of(),
                        List.of(), List.of())))
                .corporations(List.of(new Corporation("provider", "Launch services", "emp",
                        "earth", "TRANSPORT", 0.0, List.of("terminal"), List.of(), List.of())))
                .commercialHubs(List.of(hub)).shipDesigns(List.of(design))
                .industrialFacilities(List.of(new IndustrialFacility("terminal", "earth",
                        "cargo_terminal", "provider", IndustrialFacility.PRIVATE_CORPORATE,
                        1, 50, "technician", false, 0.0)))
                .industryAccounts(List.of(com.spaceconquest.engine.industry.IndustryAccount.empty("terminal").withPaidWorkers(50)))
                .fleets(List.of(fleet)).build();
    }
}
