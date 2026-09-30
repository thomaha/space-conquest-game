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
    void liftsOnlyPaidLocalStockIntoOwnedTransportAndConstructionConsumesManifest() {
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
                .get("rp1_kerosene").supplyKg() < 10_000.0);
        assertTrue(loaded.commercialHubs().getFirst().activeOrders()
                .get("liquid_oxygen").supplyKg() < 10_000.0);
        assertEquals(100.0, loaded.commercialHubs().getFirst().activeOrders()
                .get("refined_iron").supplyKg() + loaded.fleets().getFirst()
                .ships().getFirst().storedCargoKg().get("refined_iron"), 0.000001);

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
        LoadOrbitalCargoCommand load = new LoadOrbitalCargoCommand("cargo", "earth",
                "refined_iron", 20.0, LaunchService.Mode.SPACE_ELEVATOR);
        assertTrue(load.validate(state));
        GameState lifted = load.apply(state);
        assertEquals(10_000.0, lifted.commercialHubs().getFirst().activeOrders()
                .get("hydrocarbons").supplyKg(), 0.001);
        assertEquals(10_000.0, lifted.commercialHubs().getFirst().activeOrders()
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

    private GameState state() {
        Planet earth = new Planet("earth", "Earth", "", 1.0, 9.81, 1.0, 0.0,
                12_742.0, "terrestrial", "breathable", true, 1.0,
                List.of(), List.of(), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1.0, 1.0, "Yellow", List.of(earth), List.of());
        CommercialHub hub = new CommercialHub("hub_earth", "earth", 0.0,
                40_000.0, 30_100.0, 10.0, Map.of("refined_iron",
                new MarketOrder("refined_iron", 100.0, 0.0, 1.0, 0.0),
                "hydrocarbons", new MarketOrder("hydrocarbons", 10_000.0, 0.0, 1.0, 0.0),
                "rp1_kerosene", new MarketOrder("rp1_kerosene", 10_000.0, 0.0, 2.0, 0.0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 10_000.0, 0.0, 2.0, 0.0)));
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
                        1, 10, "technician", false, 0.0)))
                .fleets(List.of(fleet)).build();
    }
}
