package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.control.command.SetFleetStanceCommand;
import com.spaceconquest.control.command.RefuelShipCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipConstructionProcessor;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.industry.IndustrialFacility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ShipCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;
    private ShipDesign cargoDesign;

    @BeforeEach
    public void setUp() {
        commandQueue = new CommandQueue();
        cargoDesign = new ShipDesign(
                "design_atlas_hauler", "Atlas Hauler", "emp_terran",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 1.0,
                10000.0, 20000.0, 100.0, 1.5, 100000.0, 500000.0, true, false
        );

        initialState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .shipDesigns(List.of(cargoDesign))
                .empires(List.of(new Empire("emp_terran", "Terran", "human", "Individualist",
                        1_000_000.0, 0.15, List.of("sol"), List.of(), Map.of(),
                        List.of("rocketry", "computers"), List.of())))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1,
                        "yellow", List.of(new Planet("earth", "Earth", "", 1, 1, 1, 0, 1,
                        "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of())),
                        List.of()),
                        new SolarSystem("alpha_centauri", "Alpha Centauri", "", 1, 1, 1,
                                1, 1, "yellow", List.of(), List.of())))
                .commercialHubs(List.of(new CommercialHub("hub", "earth", 0.0, 200_000.0,
                        100_000.0, 10.0, Map.of(
                        "steel", new MarketOrder("steel", 20_000.0, 0, 1, 0),
                        "refined_copper", new MarketOrder("refined_copper", 10_000.0, 0, 1, 0),
                        "silicon", new MarketOrder("silicon", 10_000.0, 0, 1, 0),
                        "rp1_kerosene", new MarketOrder("rp1_kerosene", 50_000.0, 0, 2, 0),
                        "liquid_oxygen", new MarketOrder("liquid_oxygen", 50_000.0, 0, 2, 0)))))
                .industrialFacilities(List.of(new IndustrialFacility("earth_launch", "earth",
                        "cargo_terminal", "emp_terran", IndustrialFacility.PUBLIC_STATE,
                        1, 100, "technician", false, 0.0)))
                .build();
    }

    @Test
    public void testDesignShipCommand() {
        ShipDesign explorer = new ShipDesign(
                "design_scout_1", "Starlight Scout", "emp_terran",
                ShipRole.EXPLORER, "carbon_nanotubes", List.of(), "steel", 1.0,
                5000.0, 500.0, 50.0, 2.0, 50000.0, 250000.0, true, false
        );

        DesignShipCommand cmd = new DesignShipCommand(explorer);
        assertTrue(cmd.validate(initialState));

        GameState updated = cmd.apply(initialState);
        assertEquals(2, updated.shipDesigns().size());
        assertTrue(updated.shipDesigns().stream().anyMatch(d -> d.id().equals("design_scout_1")));
    }

    @Test
    public void testQueueShipBuildCommand() {
        QueueShipBuildCommand buildCmd = new QueueShipBuildCommand("emp_terran", "design_atlas_hauler", "sol");
        assertTrue(buildCmd.validate(initialState));

        GameState queued = buildCmd.apply(initialState);
        assertTrue(queued.fleets().isEmpty());
        assertEquals(1, queued.shipConstructionOrders().size());
        GameState stateWithShip = new ShipConstructionProcessor().process(queued);
        stateWithShip = new ShipConstructionProcessor().process(stateWithShip);
        assertFalse(stateWithShip.fleets().isEmpty());
        Fleet fleet = stateWithShip.fleets().getFirst();
        assertEquals("emp_terran", fleet.ownerEntityId());
        assertEquals("sol", fleet.currentSystemId());
        assertEquals(1, fleet.ships().size());
        assertEquals("design_atlas_hauler", fleet.ships().getFirst().designId());
    }

    @Test
    public void testMoveFleetAndSetStanceCommands() {
        QueueShipBuildCommand buildCmd = new QueueShipBuildCommand("emp_terran", "design_atlas_hauler", "sol");
        GameState stateWithShip = buildCmd.apply(initialState);
        stateWithShip = new ShipConstructionProcessor().process(stateWithShip);
        stateWithShip = new ShipConstructionProcessor().process(stateWithShip);
        String fleetId = stateWithShip.fleets().getFirst().id();

        MoveFleetCommand moveCmd = new MoveFleetCommand(fleetId, "alpha_centauri", 50.0, 50.0);
        assertTrue(moveCmd.validate(stateWithShip));
        GameState stateInTransit = moveCmd.apply(stateWithShip);

        Fleet transitFleet = stateInTransit.fleets().getFirst();
        assertFalse(transitFleet.isInWarp());
        assertEquals(Fleet.MODE_SUBLIGHT, transitFleet.interstellarMode());
        assertTrue(transitFleet.interstellarTravelDays() > 600.0);
        assertTrue(transitFleet.interstellarTravelDays() < 800.0);
        assertEquals("alpha_centauri", transitFleet.targetSystemId());
        Fleet departing = new FleetProcessor().processFleetMovements(
                stateInTransit.fleets(), List.of(), List.of()).getFirst();
        assertTrue(departing.location().inTransit());

        SetFleetStanceCommand stanceCmd = new SetFleetStanceCommand(fleetId, "PATROL");
        assertTrue(stanceCmd.validate(stateInTransit));
        GameState statePatrol = stanceCmd.apply(stateInTransit);

        assertEquals("PATROL", statePatrol.fleets().getFirst().fleetStance());
        assertEquals(transitFleet.interstellarAccelerationMps2(),
                statePatrol.fleets().getFirst().interstellarAccelerationMps2(), 0.000001);
        assertEquals(transitFleet.interstellarDistanceMeters(),
                statePatrol.fleets().getFirst().interstellarDistanceMeters(), 0.001);
    }

    @Test
    public void corporateBlueprintCannotBeBuiltByEmpireOrFreeCorporateQueue() {
        Corporation corp = new Corporation("corp_atlas", "Atlas Logistics", "emp_terran", "earth",
                "TRANSPORT", 50_000.0, List.of(), List.of(), List.of());
        ShipDesign proprietary = new ShipDesign("corp_design", "Atlas hauler", corp.id(),
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 1.0,
                10_000.0, 20_000.0, 100.0, 1.5, 100_000.0, 500_000.0, true, true);
        GameState state = initialState.toBuilder().corporations(List.of(corp))
                .shipDesigns(List.of(cargoDesign, proprietary)).build();
        assertFalse(new QueueShipBuildCommand("emp_terran", proprietary.id(), "sol").validate(state));
        assertFalse(new QueueShipBuildCommand(corp.id(), proprietary.id(), "sol").validate(state));
        assertFalse(new QueueShipBuildCommand(corp.id(), cargoDesign.id(), "sol").validate(state));
        assertFalse(new DesignShipCommand(proprietary).validate(state));
        assertFalse(new DesignShipCommand(cargoDesign).validate(state));
    }

    @Test
    void ionDriveRequiresSuperconductorsForDesignAndConstruction() {
        ShipDesign ion = new ShipDesign("ion", "Ion hauler", "emp_terran",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_ion_drive"),
                "steel", 1, 10_000, 20_000, 100, 1, 0, 450_000, true, false);
        assertFalse(new DesignShipCommand(ion).validate(initialState));
        GameState withDesign = initialState.toBuilder()
                .shipDesigns(List.of(cargoDesign, ion)).build();
        assertFalse(new QueueShipBuildCommand("emp_terran", ion.id(), "sol")
                .validate(withDesign));
        Empire owner = initialState.empires().getFirst();
        Empire researched = new Empire(owner.id(), owner.name(), owner.raceId(),
                owner.societyStructure(), owner.treasuryCredits(), owner.corporateTaxRate(),
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(),
                List.of("rocketry", "computers", "superconductors"), owner.activeShipDesignIds());
        GameState unlocked = initialState.toBuilder().empires(List.of(researched)).build();
        assertTrue(new DesignShipCommand(ion).validate(unlocked));
        assertTrue(new QueueShipBuildCommand("emp_terran", ion.id(), "sol")
                .validate(unlocked.toBuilder().shipDesigns(List.of(cargoDesign, ion)).build()));
    }

    @Test
    void refuelingConsumesMarketStockAndOwnerCredits() {
        ShipDesign chemical = new ShipDesign("chemical", "Rocket", "emp_terran",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_chemical_rocket"),
                "steel", 0, 10_000, 20_000, 500, 100, 1, 0, 500_000, true, false);
        ShipInstance empty = new ShipInstance("ship", chemical.id(), "emp_terran",
                100, 0, 0, Map.of());
        Fleet fleet = new Fleet("fleet", "Fuel test", "emp_terran", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(empty),
                FleetLocation.at(FleetLocation.Site.surface("earth")));
        CommercialHub oldHub = initialState.commercialHubs().getFirst();
        Map<String, MarketOrder> orders = new java.util.HashMap<>(oldHub.activeOrders());
        orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 300, 0, 2, 0));
        orders.put("liquid_oxygen", new MarketOrder("liquid_oxygen", 300, 0, 2, 0));
        CommercialHub hub = new CommercialHub(oldHub.id(), oldHub.entityId(),
                oldHub.transactionTariffRate(), oldHub.storageCapacityKg(),
                oldHub.currentStoredWeightKg() + 600, oldHub.logisticsRangeUnits(), orders);
        GameState stocked = initialState.toBuilder().shipDesigns(List.of(chemical))
                .fleets(List.of(fleet)).commercialHubs(List.of(hub)).build();
        RefuelShipCommand command = new RefuelShipCommand("ship", "earth", 100);
        assertTrue(command.validate(stocked));
        GameState paid = command.apply(stocked);
        assertEquals(100, paid.fleets().getFirst().ships().getFirst().currentFuelKg(), 0.001);
        assertEquals(272, paid.commercialHubs().getFirst().activeOrders()
                .get("rp1_kerosene").supplyKg(), 0.001);
        assertEquals(228, paid.commercialHubs().getFirst().activeOrders()
                .get("liquid_oxygen").supplyKg(), 0.001);
        assertEquals(999_800, paid.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(200, paid.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
        assertFalse(new RefuelShipCommand("ship", "earth", 501).validate(stocked));
        assertFalse(new RefuelShipCommand("ship", "mars", 100).validate(stocked));
    }

    @Test
    void fissionRefuelingAlsoBuysRadioactiveReactorFuel() {
        ShipDesign fission = new ShipDesign("fission", "Thermal rocket", "emp_terran",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_fission_thruster"),
                "steel", 0, 10_000, 20_000, 500, 100, 1, 0, 850_000, true, false);
        ShipInstance empty = new ShipInstance("ship", fission.id(), "emp_terran",
                100, 0, 0, Map.of());
        Fleet fleet = new Fleet("fleet", "Reactor test", "emp_terran", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(empty),
                FleetLocation.at(FleetLocation.Site.surface("earth")));
        CommercialHub old = initialState.commercialHubs().getFirst();
        Map<String, MarketOrder> orders = new java.util.HashMap<>(old.activeOrders());
        orders.put("hydrogen_gas", new MarketOrder("hydrogen_gas", 500, 0, 2, 0));
        orders.put("refined_uranium", new MarketOrder("refined_uranium", 10, 0, 100, 0));
        orders.put("refined_thorium", new MarketOrder("refined_thorium", 10, 0, 80, 0));
        CommercialHub hub = new CommercialHub(old.id(), old.entityId(),
                old.transactionTariffRate(), old.storageCapacityKg(),
                old.currentStoredWeightKg() + 520, old.logisticsRangeUnits(), orders);
        GameState stocked = initialState.toBuilder().shipDesigns(List.of(fission))
                .fleets(List.of(fleet)).commercialHubs(List.of(hub)).build();
        RefuelShipCommand uranium = new RefuelShipCommand("ship", "earth", 100,
                "refined_uranium");
        assertTrue(uranium.validate(stocked));
        GameState paid = uranium.apply(stocked);
        ShipInstance fueled = paid.fleets().getFirst().ships().getFirst();
        assertEquals(100, fueled.currentFuelKg(), 0.001);
        assertEquals(0.1, fueled.storedCargoKg().get("refined_uranium"), 0.000001);
        assertEquals(9.9, paid.commercialHubs().getFirst().activeOrders()
                .get("refined_uranium").supplyKg(), 0.000001);
        MoveFleetCommand move = new MoveFleetCommand("fleet", "alpha_centauri");
        assertTrue(move.validate(paid));
        GameState departing = move.apply(paid);
        assertFalse(departing.launchActivities().isEmpty());
        assertTrue(departing.commercialHubs().getFirst().activeOrders()
                .get("liquid_oxygen").supplyKg() < paid.commercialHubs().getFirst()
                .activeOrders().get("liquid_oxygen").supplyKg());
        assertTrue(departing.fleets().getFirst().ships().getFirst().currentFuelKg() < 100.0);
        assertTrue(departing.fleets().getFirst().ships().getFirst().storedCargoKg()
                .getOrDefault("refined_uranium", 0.0) < 0.1);
        assertFalse(new RefuelShipCommand("ship", "earth", 100, "steel")
                .validate(stocked));
        assertTrue(new RefuelShipCommand("ship", "earth", 100, "refined_thorium")
                .validate(stocked));
        GameState thorium = new RefuelShipCommand("ship", "earth", 100,
                "refined_thorium").apply(stocked);
        GameState toppedUp = new RefuelShipCommand("ship", "earth", 100).apply(thorium);
        assertEquals(0.24, toppedUp.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("refined_thorium"), 0.000001);
        assertFalse(toppedUp.fleets().getFirst().ships().getFirst()
                .storedCargoKg().containsKey("refined_uranium"));
    }

    @Test
    void fusionRefuelingUsesHydrogenAndCanBuyDeuteriumSeparately() {
        ShipDesign fusion = new ShipDesign("fusion", "Fusion rocket", "emp_terran",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_fusion_drive"),
                "steel", 0, 10_000, 20_000, 500, 100, 1, 0, 850_000, true, false);
        ShipInstance empty = new ShipInstance("ship", fusion.id(), "emp_terran",
                100, 0, 0, Map.of());
        Fleet fleet = new Fleet("fleet", "Fusion test", "emp_terran", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(empty),
                FleetLocation.at(FleetLocation.Site.surface("earth")));
        CommercialHub old = initialState.commercialHubs().getFirst();
        Map<String, MarketOrder> orders = new java.util.HashMap<>(old.activeOrders());
        orders.put("hydrogen_gas", new MarketOrder("hydrogen_gas", 500, 0, 2, 0));
        orders.put("deuterium_gas", new MarketOrder("deuterium_gas", 10, 0, 1000, 0));
        CommercialHub hub = new CommercialHub(old.id(), old.entityId(),
                old.transactionTariffRate(), old.storageCapacityKg(),
                old.currentStoredWeightKg() + 510, old.logisticsRangeUnits(), orders);
        GameState stocked = initialState.toBuilder().shipDesigns(List.of(fusion))
                .fleets(List.of(fleet)).commercialHubs(List.of(hub)).build();
        GameState paid = new RefuelShipCommand("ship", "earth", 100, "deuterium_gas")
                .apply(stocked);
        assertEquals(100, paid.fleets().getFirst().ships().getFirst().currentFuelKg(), 0.001);
        assertEquals(2, paid.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("deuterium_gas"), 0.000001);
        assertEquals(400, paid.commercialHubs().getFirst().activeOrders()
                .get("hydrogen_gas").supplyKg(), 0.000001);
        assertEquals(8, paid.commercialHubs().getFirst().activeOrders()
                .get("deuterium_gas").supplyKg(), 0.000001);
    }

    @Test
    void warpResearchChoosesWarpAndStasisPodRequiresSeparateResearch() {
        Empire old = initialState.empires().getFirst();
        Empire warpOwner = new Empire(old.id(), old.name(), old.raceId(),
                old.societyStructure(), old.treasuryCredits(), old.corporateTaxRate(),
                old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(),
                List.of("warp"), old.activeShipDesignIds());
        GameState researched = initialState.toBuilder().empires(List.of(warpOwner)).build();
        GameState warpBuild = new QueueShipBuildCommand(old.id(), cargoDesign.id(), "sol")
                .apply(researched);
        warpBuild = new ShipConstructionProcessor().process(warpBuild);
        warpBuild = new ShipConstructionProcessor().process(warpBuild);
        Fleet warpFleet = warpBuild.fleets().getFirst();
        GameState warpOrdered = new MoveFleetCommand(warpFleet.id(), "alpha_centauri")
                .apply(warpBuild);
        assertEquals(Fleet.MODE_WARP, warpOrdered.fleets().getFirst().interstellarMode());
        assertEquals(4.0, warpOrdered.fleets().getFirst().interstellarTravelDays(), 0.001);
        ShipDesign podDesign = new ShipDesign("pod_design", "Stasis transport",
                old.id(), ShipRole.CARGO_TRANSPORT, "steel",
                List.of(PassengerStasis.MODULE_ID), "steel", 0, 1000,
                10000, 100, 1, 0, 0, true, false);
        assertFalse(new DesignShipCommand(podDesign).validate(researched));
        Empire stasisOwner = new Empire(old.id(), old.name(), old.raceId(),
                old.societyStructure(), old.treasuryCredits(), old.corporateTaxRate(),
                old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(),
                List.of("warp", PassengerStasis.TECHNOLOGY_ID), old.activeShipDesignIds());
        GameState unlocked = researched.toBuilder().empires(List.of(stasisOwner)).build();
        assertTrue(new DesignShipCommand(podDesign).validate(unlocked));
        GameState withPod = new DesignShipCommand(podDesign).apply(unlocked);
        assertEquals(100.0, com.spaceconquest.engine.industry.ConstructionMaterialCatalog
                .ship(podDesign).get("refined_aluminum"), 0.001);
        assertTrue(new QueueShipBuildCommand(old.id(), podDesign.id(), "sol")
                .validate(withPod));
    }
}
