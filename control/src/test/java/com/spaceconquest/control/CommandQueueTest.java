package com.spaceconquest.control;

import com.spaceconquest.control.command.AppointMinisterCommand;
import com.spaceconquest.control.command.AssignGovernorCommand;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.CorporateInvestCommand;
import com.spaceconquest.control.command.NationalizeAssetCommand;
import com.spaceconquest.control.command.SetDiplomaticTierCommand;
import com.spaceconquest.control.command.SetTariffRateCommand;
import com.spaceconquest.control.command.SubsidizeCorporationCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.CourierShip;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.economy.ImperialBalanceSheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CommandQueueTest {

    private CommandQueue commandQueue;
    private GameState initialState;

    @BeforeEach
    public void setUp() {
        commandQueue = new CommandQueue();

        Empire empire = new Empire(
                "terran",
                "Terran Confederation",
                "human",
                "Individualist",
                50000.0,
                0.15,
                List.of("sol"),
                List.of(),
                Map.of(),
                List.of("rocketry", "industrial_production", "computers"),
                List.of()
        );

        Empire silicon = new Empire(
                "silicon",
                "Silicon Hegemony",
                "silicon_core",
                "Collectivist",
                40000.0,
                0.20,
                List.of("alpha"),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );

        Corporation corp = new Corporation(
                "corp_mining",
                "Belt Mining Corp",
                "terran",
                "earth",
                "EXTRACTION",
                20000.0,
                List.of("facility_1"),
                List.of("ship_1"),
                List.of()
        );

        CommercialHub hub = new CommercialHub(
                "hub_earth",
                "earth",
                0.10,
                50000.0,
                5000.0,
                15.0,
                Map.of()
        );

        initialState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .empires(List.of(empire, silicon))
                .corporations(List.of(corp))
                .commercialHubs(List.of(hub))
                .industrialFacilities(List.of(new com.spaceconquest.engine.industry.IndustrialFacility(
                        "facility_1", "earth", "mining_outpost", "corp_mining",
                        com.spaceconquest.engine.industry.IndustrialFacility.PRIVATE_CORPORATE,
                        1, 100, "miner", false, 0.0)))
                .solarSystems(List.of(new com.spaceconquest.engine.SolarSystem("sol", "Sol", "",
                        0, 0, 0, 1, 1, "yellow",
                        List.of(new com.spaceconquest.engine.Planet("earth", "Earth", "",
                                1, 1, 1, 0, 1, "terrestrial", "breathable", true, 1,
                                List.of(), List.of(), List.of())), List.of())))
                .build();
    }

    @Test
    public void testSetTariffRateCommandExecution() {
        commandQueue.submit(new SetTariffRateCommand("terran", "hub_earth", 0.05));
        GameState updated = commandQueue.drainAndExecute(initialState);

        assertNotNull(updated);
        assertEquals(0.05, updated.commercialHubs().getFirst().transactionTariffRate(), 0.001);
    }

    @Test
    public void testCommandPreservesUnrelatedState() {
        CourierShip courier = new CourierShip("courier_1", "terran", 125.0, "sol", "alpha", 2, false);
        ImperialBalanceSheet balance = new ImperialBalanceSheet("terran", 1, 10.0, 20.0, 5.0, 15.0);
        GameState state = initialState.toBuilder()
                .courierShips(List.of(courier))
                .imperialBalanceSheets(List.of(balance))
                .build();

        commandQueue.submit(new SetTariffRateCommand("terran", "hub_earth", 0.05));
        GameState updated = commandQueue.drainAndExecute(state);

        assertEquals(0.05, updated.commercialHubs().getFirst().transactionTariffRate(), 0.001);
        assertEquals(List.of(courier), updated.courierShips());
        assertEquals(List.of(balance), updated.imperialBalanceSheets());
    }

    @Test
    public void testSubsidizeCorporationCommandExecution() {
        commandQueue.submit(new SubsidizeCorporationCommand("terran", "corp_mining", 10000.0));
        GameState updated = commandQueue.drainAndExecute(initialState);

        assertNotNull(updated);
        Empire updatedEmpire = updated.empires().stream().filter(e -> e.id().equals("terran")).findFirst().orElseThrow();
        Corporation updatedCorp = updated.corporations().stream().filter(c -> c.id().equals("corp_mining")).findFirst().orElseThrow();

        assertEquals(40000.0, updatedEmpire.treasuryCredits(), 0.001);
        assertEquals(30000.0, updatedCorp.liquidCapitalReserves(), 0.001);
    }

    @Test
    public void testCommandSpendingAppearsInNextImperialBalanceSheet() {
        SpaceConquestEngine engine = SpaceConquestEngine.fromSolScenario();
        engine.reset(initialState);
        commandQueue.submit(new SubsidizeCorporationCommand("terran", "corp_mining", 10000.0));
        commandQueue.processCommands(engine);
        engine.stepTurn();

        var balance = engine.getGameState().imperialBalanceSheets().stream()
                .filter(sheet -> "terran".equals(sheet.empireId())).findFirst().orElseThrow();
        assertEquals(10000.0, balance.expenditureCredits(), 0.001);
    }

    @Test
    public void testAppointMinisterAndGovernorCommands() {
        commandQueue.submit(new AppointMinisterCommand("terran", "ministry_technology_application", "scientist"));
        commandQueue.submit(new AssignGovernorCommand("terran", "sol", "miner"));
        GameState updated = commandQueue.drainAndExecute(initialState);

        assertNotNull(updated);
        Empire updatedEmpire = updated.empires().stream().filter(e -> e.id().equals("terran")).findFirst().orElseThrow();
        assertFalse(updatedEmpire.ministries().isEmpty());
        assertEquals("scientist", updatedEmpire.ministries().getFirst().assignedCitizenProfessionId());

        assertFalse(updated.systemGovernors().isEmpty());
        assertEquals("sol", updated.systemGovernors().getFirst().solarSystemId());
        assertEquals("miner", updated.systemGovernors().getFirst().professionId());
    }

    @Test
    public void testSetDiplomaticTierCommandExecution() {
        commandQueue.submit(new SetDiplomaticTierCommand("terran", "silicon", "COMMERCIAL_ALLIANCE"));
        GameState updated = commandQueue.drainAndExecute(initialState);

        assertNotNull(updated);
        assertFalse(updated.diplomaticRelations().isEmpty());
        assertEquals("COMMERCIAL_ALLIANCE", updated.diplomaticRelations().getFirst().tier());
        assertEquals(0.50, updated.diplomaticRelations().getFirst().mutualTariffDiscount(), 0.001);
    }

    @Test
    public void testCorporateInvestAndNationalizeAssetCommands() {
        // Corporate investment: place a funded ship construction order
        commandQueue.submit(new CorporateInvestCommand("corp_mining", "earth", "FLEET", 12000.0));
        GameState postInvest = commandQueue.drainAndExecute(initialState);

        Corporation corpPostInvest = postInvest.corporations().getFirst();
        assertEquals(8000.0, corpPostInvest.liquidCapitalReserves(), 0.001);
        assertEquals(1, corpPostInvest.ownedShipIds().size());
        assertEquals(1, postInvest.shipConstructionOrders().size());
        assertTrue(postInvest.fleets().isEmpty());

        // State nationalization of the facility
        commandQueue.submit(new NationalizeAssetCommand("terran", "corp_mining", "facility_1"));
        GameState postNationalize = commandQueue.drainAndExecute(postInvest);

        Corporation corpPostNat = postNationalize.corporations().getFirst();
        assertTrue(corpPostNat.ownedFacilityIds().isEmpty());
        assertEquals("terran", postNationalize.industrialFacilities().getFirst().ownerEntityId());
        assertEquals(com.spaceconquest.engine.industry.IndustrialFacility.PUBLIC_STATE,
                postNationalize.industrialFacilities().getFirst().ownershipType());
    }

    @Test
    public void nationalizingShipTransfersThePhysicalVessel() {
        String shipId = "ship_1";
        var physicalShip = new com.spaceconquest.engine.ship.ShipInstance(shipId, "design_1",
                "corp_mining", 1_000.0, 100.0, 100.0, Map.of());
        var physicalFleet = new com.spaceconquest.engine.ship.Fleet("fleet_1", "Mining fleet",
                "corp_mining", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(physicalShip));
        GameState invested = initialState.toBuilder().fleets(List.of(physicalFleet)).build();
        GameState nationalized = new NationalizeAssetCommand("terran", "corp_mining", shipId)
                .apply(invested);
        assertFalse(nationalized.corporations().getFirst().ownedShipIds().contains(shipId));
        assertTrue(nationalized.fleets().stream().anyMatch(fleet -> "terran".equals(fleet.ownerEntityId())
                && fleet.ships().stream().anyMatch(ship -> shipId.equals(ship.id())
                && "terran".equals(ship.ownerEntityId()))));
    }

    @Test
    public void testInvalidCommandIsRejectedGracefully() {
        // Attempting subsidy with more credits than treasury
        commandQueue.submit(new SubsidizeCorporationCommand("terran", "corp_mining", 999999.0));
        GameState updated = commandQueue.drainAndExecute(initialState);

        // State remains unchanged
        assertEquals(50000.0, updated.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(20000.0, updated.corporations().getFirst().liquidCapitalReserves(), 0.001);
    }
}
