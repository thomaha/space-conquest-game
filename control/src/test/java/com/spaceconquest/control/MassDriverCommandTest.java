package com.spaceconquest.control;

import com.spaceconquest.control.command.LaunchMassDriverPayloadCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MassDriverCommandTest {
    @Test
    void installedDriverMovesPaidGoodsToOrbitalShipAndPaysItsOwner() {
        Planet earth = new Planet("earth", "Earth", "", 1, 9.81, 1, 0,
                12_742, "terrestrial", "breathable", true, 1,
                List.of(), List.of(), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1, 1, "Yellow", List.of(earth), List.of());
        Empire empire = new Empire("emp", "Empire", "human", "Individualist",
                0, 0.1, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        Corporation carrier = new Corporation("corp", "Carrier", "emp", "earth",
                "TRANSPORT", 10_000, List.of(), List.of("ship"), List.of());
        IndustrialFacility driver = new IndustrialFacility("driver", "earth", "mass_driver",
                "emp", IndustrialFacility.PUBLIC_STATE, 1, 10, "technician", false, 0);
        CommercialHub hub = new CommercialHub("hub", "earth", 0, 10_000, 2_000, 10,
                Map.of("iron_ore", new MarketOrder("iron_ore", 2_000, 0, 1, 0)));
        ShipDesign design = new ShipDesign("design", "Cargo", "corp",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                1_000, 2_000, 0, 1, 0, 0, true, true);
        Fleet fleet = new Fleet("fleet", "Carrier", "corp", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("ship", "design",
                "corp", 100, 0, 100, Map.of())),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        GameState state = GameState.builder().turn(1).status("RUNNING")
                .solarSystems(List.of(sol)).empires(List.of(empire))
                .corporations(List.of(carrier)).industrialFacilities(List.of(driver))
                .commercialHubs(List.of(hub)).shipDesigns(List.of(design))
                .fleets(List.of(fleet)).powerGrids(List.of(new PowerGridState("earth",
                        1_000, 500, 500, 0, 0, false))).build();
        LaunchMassDriverPayloadCommand launch = new LaunchMassDriverPayloadCommand(
                "driver", "corp", "iron_ore", 1.0, "ship");
        assertTrue(launch.validate(state));
        GameState moved = launch.apply(state);
        assertEquals(1_000, moved.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("iron_ore"), 0.001);
        assertEquals(1_000, moved.commercialHubs().getFirst().activeOrders()
                .get("iron_ore").supplyKg(), 0.001);
        assertEquals(8_999.5, moved.corporations().getFirst().liquidCapitalReserves(), 0.001);
        assertEquals(0.0, moved.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(0.5, moved.industryAccounts().getFirst().operatingCashCredits(), 0.001);
        assertEquals(1_000, moved.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
        assertEquals(250, moved.powerGrids().getFirst().netBalanceKw(), 0.001);
        assertFalse(new LaunchMassDriverPayloadCommand(
                "driver", "corp", "iron_ore", 1.0, 300.0).validate(state));
    }
}
