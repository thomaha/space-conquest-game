package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.MoveFleetLocalCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.control.command.SetFleetStanceCommand;
import com.spaceconquest.control.command.UpdateShipDesignCommand;
import com.spaceconquest.control.command.RefuelShipCommand;
import com.spaceconquest.control.command.SetSurfaceShipyardStaffingCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.economy.HouseholdEconomyProcessor;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEmployment;
import com.spaceconquest.engine.economy.HouseholdWellbeing;
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
import com.spaceconquest.engine.ship.ShipConstructionRequirements;
import com.spaceconquest.engine.ship.ShipConstructionOrder;
import com.spaceconquest.engine.ship.ShipyardWorkCapacity;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
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
                        1, 100, "technician", false, 0.0),
                        new IndustrialFacility("earth_shipyard", "earth",
                                ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID, "emp_terran",
                                IndustrialFacility.PUBLIC_STATE, 1, 100, "industrial_worker",
                                false, 0.0)))
                .industryAccounts(List.of(IndustryAccount.empty("earth_shipyard")
                        .withPaidWorkers(100)))
                .householdAccounts(List.of(workforce("industrial_worker", 100),
                        workforce("engineer", 10)))
                .build();
    }

    @Test
    public void testDesignShipCommand() {
        ShipDesign explorer = new ShipDesign(
                "design_scout_1", "Starlight Scout", "emp_terran",
                ShipRole.EXPLORER, "carbon_nanotubes", com.spaceconquest.engine.ship.ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false), "steel", 1.0,
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
        assertEquals("earth", buildCmd.resolveYardEntity(initialState));

        GameState queued = buildCmd.apply(initialState);
        assertTrue(queued.fleets().isEmpty());
        assertEquals(1, queued.shipConstructionOrders().size());
        var estimate = ShipConstructionRequirements.estimate(cargoDesign);
        assertEquals(estimate.workUnits(),
                queued.shipConstructionOrders().getFirst().requiredWorkHours());
        assertEquals(estimate.materialsKg(),
                queued.shipConstructionOrders().getFirst().requiredMaterialsKg());
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
        var preview = moveCmd.preview(stateWithShip);
        assertNotNull(preview);
        assertTrue(preview.local().days() > 0);
        assertTrue(preview.launchCostCredits() > 0);
        GameState stateInTransit = moveCmd.apply(stateWithShip);
        assertEquals(preview.launchCostCredits(), stateWithShip.empires().getFirst().treasuryCredits()
                - stateInTransit.empires().getFirst().treasuryCredits(), 0.001);

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
    public void shipyardCapacityUsesLocalStaffingAndAutomationTechnology() {
        ShipyardWorkCapacity.Profile staffed = ShipyardWorkCapacity.forYard(initialState,
                "emp_terran", "sol", "earth");
        assertEquals(100.0, staffed.workPerDay(), 0.001);
        assertTrue(staffed.isFullyStaffed());

        GameState unstaffedState = initialState.toBuilder().householdAccounts(List.of())
                .industryAccounts(List.of(IndustryAccount.empty("earth_shipyard"))).build();
        ShipyardWorkCapacity.Profile unstaffed = ShipyardWorkCapacity.forYard(unstaffedState,
                "emp_terran", "sol", "earth");
        assertEquals(0.0, unstaffed.workPerDay(), 0.001);
        assertFalse(unstaffed.isFullyStaffed());

        Empire original = initialState.empires().getFirst();
        Empire automatedEmpire = new Empire(original.id(), original.name(), original.raceId(),
                original.societyStructure(), original.treasuryCredits(), original.corporateTaxRate(),
                original.controlledSystemIds(), original.ministries(),
                original.systemGovernorAssignments(), List.of("rocketry", "computers",
                ShipyardWorkCapacity.TECH_AUTOMATED_ASSEMBLY), original.activeShipDesignIds());
        GameState automatedState = initialState.withEmpires(List.of(automatedEmpire));
        ShipyardWorkCapacity.Profile automated = ShipyardWorkCapacity.forYard(automatedState,
                "emp_terran", "sol", "earth");
        assertEquals(125.0, automated.workPerDay(), 0.001);
        assertTrue(automated.isFullyStaffed());
    }

    @Test
    public void commercialHubAloneDoesNotQualifyAsSurfaceShipyard() {
        GameState withoutYard = initialState.toBuilder().industrialFacilities(List.of()).build();
        QueueShipBuildCommand command = new QueueShipBuildCommand(
                "emp_terran", "design_atlas_hauler", "sol");
        assertNull(command.resolveYardEntity(withoutYard));
        assertFalse(command.validate(withoutYard));
    }

    @Test
    public void surfaceShipyardRemainsAvailableAtHalfCapacityDuringTierUpgrade() {
        IndustrialFacility upgrading = new IndustrialFacility("earth_shipyard", "earth",
                ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID, "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "industrial_worker", true, 0.0);
        GameState state = initialState.toBuilder().industrialFacilities(List.of(upgrading)).build();
        QueueShipBuildCommand command = new QueueShipBuildCommand(
                "emp_terran", "design_atlas_hauler", "sol");

        assertEquals("earth", command.resolveYardEntity(state));
        ShipyardWorkCapacity.Profile profile = ShipyardWorkCapacity.forYard(
                state, "emp_terran", "sol", "earth");
        assertEquals(50.0, profile.baseWorkPerDay(), 0.001);
        assertEquals(50.0, profile.workPerDay(), 0.001);
    }

    @Test
    public void surfaceShipyardStaffingIsPersistentAndLimitedByUncommittedWorkers() {
        SetSurfaceShipyardStaffingCommand setStaff = new SetSurfaceShipyardStaffingCommand(
                "emp_terran", "earth_shipyard", 50);
        assertTrue(setStaff.validate(initialState));
        GameState understaffed = setStaff.apply(initialState);
        IndustrialFacility yard = understaffed.industrialFacilities().stream()
                .filter(facility -> "earth_shipyard".equals(facility.id())).findFirst().orElseThrow();
        assertEquals(50, yard.allocatedWorkers());
        GameState paidAtNewAllocation = understaffed.toBuilder().industryAccounts(List.of(
                IndustryAccount.empty("earth_shipyard").withPaidWorkers(50))).build();
        ShipyardWorkCapacity.Profile profile = ShipyardWorkCapacity.forYard(
                paidAtNewAllocation, "emp_terran", "sol", "earth");
        assertEquals(50.0, profile.workPerDay(), 0.001);
        assertFalse(profile.isFullyStaffed());
        assertFalse(new SetSurfaceShipyardStaffingCommand(
                "emp_terran", "earth_shipyard", 101).validate(initialState));

        IndustrialFacility secondYard = new IndustrialFacility("earth_shipyard_2", "earth",
                ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID, "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 1, 0, "industrial_worker", false, 0.0);
        GameState sharedWorkforce = initialState.toBuilder().industrialFacilities(List.of(
                initialState.industrialFacilities().getFirst(),
                initialState.industrialFacilities().get(1), secondYard)).build();
        assertEquals(0, ShipyardWorkCapacity.assignableWorkers(sharedWorkforce, secondYard));
        assertFalse(new SetSurfaceShipyardStaffingCommand(
                "emp_terran", secondYard.id(), 1).validate(sharedWorkforce));
    }

    @Test
    public void surfaceYardCapacityUsesWorkersPaidByCurrentPayroll() {
        Map<String, Integer> hired = Map.of("earth_shipyard", 37);
        var payroll = new IndustryMarketProcessor().process(initialState, Map.of(), Map.of(), hired);
        IndustryAccount yardPayroll = payroll.industryAccounts().stream()
                .filter(account -> "earth_shipyard".equals(account.facilityId()))
                .findFirst().orElseThrow();
        assertEquals(37, yardPayroll.paidWorkers());

        GameState staffedState = initialState.toBuilder()
                .industryAccounts(payroll.industryAccounts()).build();
        ShipyardWorkCapacity.Profile profile = ShipyardWorkCapacity.forYard(
                staffedState, "emp_terran", "sol", "earth");
        assertEquals(37.0, profile.workPerDay(), 0.001);
        assertFalse(profile.isFullyStaffed());
    }

    @Test
    public void constructionUsesCurrentPayrollWorkersAndStopsWhenNoWorkersArePaid() {
        QueueShipBuildCommand command = new QueueShipBuildCommand(
                "emp_terran", "design_atlas_hauler", "sol");
        GameState queued = command.apply(initialState);
        ShipConstructionProcessor processor = new ShipConstructionProcessor();

        GameState unpaid = processor.process(queued, Map.of());
        assertEquals(0.0, unpaid.shipConstructionOrders().getFirst().accumulatedWorkHours());
        GameState partiallyStaffed = processor.process(queued, Map.of("earth_shipyard", 25));
        assertEquals(25.0, partiallyStaffed.shipConstructionOrders().getFirst()
                .accumulatedWorkHours());
    }

    @Test
    public void orbitalShipyardLifecycleRequiresResearchCargoAndPaidStaffing() {
        Empire original = initialState.empires().getFirst();
        GameState researched = initialState.toBuilder().solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1.98847e30, 1.3927e6, "yellow", List.of(new Planet("earth", "Earth", "", 5.972e24, 9.81,
                149597870, 0, 12742, "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of())), List.of())))
                .empires(List.of(new Empire(original.id(),
                original.name(), original.raceId(), original.societyStructure(),
                original.treasuryCredits(), original.corporateTaxRate(),
                original.controlledSystemIds(), original.ministries(),
                original.systemGovernorAssignments(), List.of("rocketry", "computers",
                "space_stations"), original.activeShipDesignIds()))).build();
        var deployment = new com.spaceconquest.control.command.BuildOrbitalStationCommand(
                "Orbital works", "sol", "earth", "emp_terran",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 30, "steel", 1.0);
        assertTrue(deployment.validate(researched));
        assertFalse(deployment.validate(initialState));

        String stationId = "orbital_works";
        StationModule grid = new StationModule("orbital_grid", "Shipyard grid",
                StationModule.TYPE_SHIPYARD_GRID, 10, 20_000, 100, 0, Map.of(),
                "industrial_worker", 20, true);
        OrbitalStation station = new OrbitalStation(stationId, "Orbital works", "sol", "earth",
                "emp_terran", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 30, List.of(grid),
                Map.of(), 100, 100, 0, 0, 100, 100, "steel", 1.0, true,
                List.of(new Population("human", Map.of(25, 200L))));
        CommercialHub orbitalHub = new CommercialHub("orbital_market", stationId, 0,
                100_000, 0, 10, Map.of());
        ShipDesign hull = new ShipDesign("orbital_hull", "Orbital hull", "emp_terran",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 1,
                10_000, 50_000, 0, 1, 0, 0, true, false);
        var bill = ShipConstructionRequirements.estimate(hull).materialsKg();
        ShipInstance supplyShip = new ShipInstance("orbital_supply_ship", cargoDesign.id(),
                "emp_terran", 100, 0, 0, bill);
        Fleet supplyFleet = new Fleet("orbital_supply_fleet", "Orbital supply", "emp_terran",
                "sol", "", 0, 0, 0, false, "PASSIVE", List.of(supplyShip),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        HouseholdAccount workers = workforce(stationId, "industrial_worker", 200);
        GameState supplied = researched.toBuilder().orbitalStations(List.of(station))
                .commercialHubs(List.of(orbitalHub)).shipDesigns(List.of(cargoDesign, hull))
                .fleets(List.of(supplyFleet)).householdAccounts(List.of(workers)).build();

        MoveFleetLocalCommand deliver = new MoveFleetLocalCommand(supplyFleet.id(),
                FleetLocation.Kind.DOCKED, stationId);
        assertTrue(deliver.validate(supplied));
        GameState inTransit = deliver.apply(supplied);
        assertTrue(inTransit.fleets().getFirst().location().inTransit());
        GameState docked = inTransit.withFleets(new FleetProcessor().processFleetMovements(
                inTransit.fleets(), inTransit.orbitalStations(), List.of()));
        assertTrue(docked.fleets().getFirst().location()
                .isAt(FleetLocation.Site.docked(stationId)));

        QueueShipBuildCommand queue = new QueueShipBuildCommand(
                "emp_terran", hull.id(), "sol");
        assertTrue(queue.validate(docked));
        GameState queued = queue.apply(docked);
        assertEquals(stationId, queued.shipConstructionOrders().getFirst().yardBodyId());

        ShipConstructionProcessor construction = new ShipConstructionProcessor();
        GameState unpaid = construction.process(queued, Map.of());
        assertEquals(0.0, unpaid.shipConstructionOrders().getFirst().accumulatedWorkHours());
        assertTrue(unpaid.shipConstructionOrders().getFirst().consumedMaterialsKg().isEmpty());

        var payroll = new HouseholdEconomyProcessor().process(docked,
                List.of(new Race("human", "Human", "", 1, 1, "Individualist", 1, 288,
                        "carbon", "oxygen", 18, 45, "Organic", "Diverse", 80)));
        StationModule paidGrid = payroll.orbitalStations().getFirst().modules().getFirst();
        assertTrue(paidGrid.paidWorkers() > 0);
        assertTrue(paidGrid.dailyWageCostsCredits() > 0);
        GameState staffed = queued.toBuilder().orbitalStations(payroll.orbitalStations())
                .empires(payroll.empires()).fleets(docked.fleets()).build();
        GameState progressing = construction.process(staffed, payroll.paidWorkersByFacility());
        assertTrue(progressing.shipConstructionOrders().getFirst().accumulatedWorkHours() > 0);
        assertTrue(progressing.shipConstructionOrders().getFirst().consumedMaterialsKg()
                .values().stream().mapToDouble(Double::doubleValue).sum() > 0);
        double remainingCargo = progressing.fleets().getFirst().ships().getFirst()
                .storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        assertTrue(remainingCargo < bill.values().stream().mapToDouble(Double::doubleValue).sum());

        StationModule offlineGrid = new StationModule(grid.id(), grid.name(), grid.type(),
                grid.slotSize(), grid.dryMassKg(), grid.powerDrawKw(), grid.powerOutputKw(),
                grid.materialInputs(), grid.workforceProfessionId(), grid.requiredWorkers(), false)
                .withPayroll(paidGrid.paidWorkers(), paidGrid.dailyWageCostsCredits());
        OrbitalStation offlineStation = new OrbitalStation(station.id(), station.name(),
                station.systemId(), station.planetOrbitId(), station.ownerEntityId(),
                station.ownershipType(), station.totalSlots(), List.of(offlineGrid),
                station.storedCargoKg(), station.currentPowerGenerationKw(),
                station.currentPowerDemandKw(), station.currentShieldHealth(), station.maxShieldHealth(),
                station.currentHullHealth(), station.maxHullHealth(), station.armorMaterialId(),
                station.armorThicknessCm(), station.isOperational(), station.populations());
        GameState offline = progressing.toBuilder().orbitalStations(List.of(offlineStation)).build();
        double priorWork = offline.shipConstructionOrders().getFirst().accumulatedWorkHours();
        GameState paused = construction.process(offline, payroll.paidWorkersByFacility());
        assertEquals(priorWork, paused.shipConstructionOrders().getFirst().accumulatedWorkHours());
    }

    @Test
    public void orbitalShipyardCapacityIncludesInstalledModulesAndTheirStaffing() {
        String stationId = "orbital_yard";
        OrbitalStation station = new OrbitalStation(stationId, "Orbital yard", "sol", "earth",
                "emp_terran", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 60,
                List.of(new StationModule("yard_grid", "Shipyard grid",
                                StationModule.TYPE_SHIPYARD_GRID, 10, 20_000, 100, 0,
                                Map.of(), "industrial_worker", 40, true).withPayroll(40, 40.0),
                        new StationModule("capital_slipway", "Capital slipway",
                                StationModule.TYPE_CAPITAL_SLIPWAY, 20, 40_000, 100, 0,
                                Map.of(), "industrial_worker", 300, true).withPayroll(300, 300.0),
                        new StationModule("assembly", "Component assembly module",
                                StationModule.TYPE_COMPONENT_ASSEMBLY, 8, 12_000, 50, 0,
                                Map.of(), "technician", 20, true).withPayroll(20, 20.0)),
                Map.of(), 250, 150, 500, 500, 1000, 1000, "steel", 2, true, List.of());
        GameState orbitalState = initialState.toBuilder()
                .orbitalStations(List.of(station))
                .householdAccounts(List.of(workforce(stationId, "industrial_worker", 340),
                        workforce(stationId, "technician", 20)))
                .build();

        ShipyardWorkCapacity.Profile capacity = ShipyardWorkCapacity.forYard(orbitalState,
                "emp_terran", "sol", stationId);
        assertEquals("Capital slipway", capacity.yardType());
        assertEquals(450.0, capacity.baseWorkPerDay(), 0.001);
        assertEquals(450.0, capacity.workPerDay(), 0.001);
        assertTrue(capacity.isFullyStaffed());
        assertEquals(1, capacity.installedYardModules().get(StationModule.TYPE_SHIPYARD_GRID));
        assertEquals(1, capacity.installedYardModules().get(StationModule.TYPE_CAPITAL_SLIPWAY));
        assertEquals(1, capacity.installedYardModules().get(StationModule.TYPE_COMPONENT_ASSEMBLY));
        ShipyardWorkCapacity.Profile unpaid = ShipyardWorkCapacity.forYard(orbitalState,
                "emp_terran", "sol", stationId, Map.of());
        assertEquals(0.0, unpaid.workPerDay(), 0.001);
    }

    @Test
    public void simultaneousBuildsShareDailyYardCapacityInQueueOrder() {
        QueueShipBuildCommand command = new QueueShipBuildCommand(
                "emp_terran", "design_atlas_hauler", "sol");
        GameState twoOrders = command.apply(command.apply(initialState));

        GameState afterOneDay = new ShipConstructionProcessor().process(twoOrders);
        assertEquals(100.0, afterOneDay.shipConstructionOrders().getFirst().accumulatedWorkHours());
        assertEquals(0.0, afterOneDay.shipConstructionOrders().get(1).accumulatedWorkHours());
    }

    @Test
    public void materialBlockedOrderPassesUnusedDailyCapacityToNextSurfaceOrder() {
        ShipConstructionOrder blocked = new ShipConstructionOrder("blocked_order", "emp_terran",
                cargoDesign.id(), "sol", "earth", 0.0, 100.0,
                Map.of("unobtainium", 100.0), Map.of());
        ShipConstructionOrder ready = new ShipConstructionOrder("ready_order", "emp_terran",
                cargoDesign.id(), "sol", "earth", 0.0, 100.0,
                Map.of("steel", 100.0), Map.of());
        GameState queued = initialState.toBuilder()
                .shipConstructionOrders(List.of(blocked, ready)).build();

        GameState afterOneDay = new ShipConstructionProcessor().process(queued);

        assertEquals(List.of("blocked_order"), afterOneDay.shipConstructionOrders().stream()
                .map(ShipConstructionOrder::id).toList());
        assertEquals(0.0, afterOneDay.shipConstructionOrders().getFirst().accumulatedWorkHours());
        assertEquals(1, afterOneDay.fleets().size());
        assertEquals("ship_ready_order", afterOneDay.fleets().getFirst().ships().getFirst().id());
    }

    private HouseholdAccount workforce(String professionId, long count) {
        return workforce("earth", professionId, count);
    }

    private HouseholdAccount workforce(String bodyId, String professionId, long count) {
        return new HouseholdAccount(bodyId, "sol", "emp_terran", "human", professionId,
                count, 0, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0,
                HouseholdWellbeing.healthy(), new HouseholdEmployment(count, 0, 0, 0));
    }

    @Test
    void publicShipBlueprintCanBeEditedAndRemainsAvailableForConstruction() {
        ShipDesign revised = new ShipDesign(cargoDesign.id(), "Atlas Hauler Mk II",
                cargoDesign.ownerEntityId(), cargoDesign.role(), "carbon_nanotubes",
                com.spaceconquest.engine.ship.ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false), cargoDesign.armorMaterialId(),
                cargoDesign.armorThicknessCm(), cargoDesign.totalDryMassKg() + 500,
                cargoDesign.maxCargoMassKg(), cargoDesign.fuelCapacityKg(),
                cargoDesign.powerBalanceKw(), cargoDesign.calculatedStructuralIntegrity(),
                cargoDesign.minLaunchThrustRequiredN(), cargoDesign.totalThrustN(),
                cargoDesign.isValidForLaunch(), false);
        UpdateShipDesignCommand command = new UpdateShipDesignCommand(revised);

        assertTrue(command.validate(initialState));
        GameState updated = command.apply(initialState);
        assertEquals(1, updated.shipDesigns().size());
        assertEquals("Atlas Hauler Mk II", updated.shipDesigns().getFirst().name());
        assertTrue(new QueueShipBuildCommand("emp_terran", revised.id(), "sol")
                .validate(updated));
    }

    @Test
    void ionDriveRequiresSuperconductorsForDesignAndConstruction() {
        ShipDesign ion = new ShipDesign("ion", "Ion hauler", "emp_terran",
                ShipRole.CARGO_TRANSPORT, "steel", com.spaceconquest.engine.ship.ShipComponentCatalog.workbenchModules("mod_ion_drive", false),
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
        GameState unlocked = upgradeSurfaceYard(initialState.toBuilder().empires(List.of(researched)).build(), 3);
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
                com.spaceconquest.engine.ship.ShipComponentCatalog.workbenchModules("mod_chemical_rocket", true), "steel", 0, 1000,
                10000, 100, 1, 0, 0, true, false);
        assertFalse(new DesignShipCommand(podDesign).validate(researched));
        Empire stasisOwner = new Empire(old.id(), old.name(), old.raceId(),
                old.societyStructure(), old.treasuryCredits(), old.corporateTaxRate(),
                old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(),
                List.of("rocketry", "warp", PassengerStasis.TECHNOLOGY_ID), old.activeShipDesignIds());
        GameState unlocked = upgradeSurfaceYard(researched.toBuilder().empires(List.of(stasisOwner)).build(), 3);
        assertTrue(new DesignShipCommand(podDesign).validate(unlocked));
        GameState withPod = new DesignShipCommand(podDesign).apply(unlocked);
        assertEquals(100.0, com.spaceconquest.engine.industry.ConstructionMaterialCatalog
                .ship(podDesign).get("refined_aluminum"), 0.001);
        assertTrue(new QueueShipBuildCommand(old.id(), podDesign.id(), "sol")
                .validate(withPod));
    }

    private GameState upgradeSurfaceYard(GameState state, int tier) {
        return state.withIndustrialFacilities(state.industrialFacilities().stream().map(facility ->
                ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID.equals(facility.applicationId())
                        ? new IndustrialFacility(facility.id(), facility.planetId(), facility.applicationId(),
                        facility.ownerEntityId(), facility.ownershipType(), tier, facility.allocatedWorkers(),
                        facility.workerProfessionId(), facility.isUndergoingExpansion(), facility.expansionProgress())
                        : facility).toList());
    }
}
