package com.spaceconquest.engine;

import com.spaceconquest.engine.industry.SurfaceMassDriver;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.market.OrbitalLiftProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MarketProcessorTest {

    private final MarketProcessor marketProcessor = new MarketProcessor();

    @Test
    public void testSpotPriceDiscovery() {
        // Balanced supply and demand
        double priceBalanced = marketProcessor.calculateSpotPrice(10.0, 100.0, 100.0);
        assertEquals(10.0, priceBalanced, 0.05);

        // Acute shortage (high demand, low supply) -> price goes up
        double priceShortage = marketProcessor.calculateSpotPrice(10.0, 10.0, 100.0);
        assertTrue(priceShortage > 10.0, "Shortage price should be greater than base price");

        // Surplus (high supply, low demand) -> price goes down
        double priceSurplus = marketProcessor.calculateSpotPrice(10.0, 200.0, 10.0);
        assertTrue(priceSurplus < 10.0, "Surplus price should be lower than base price");
    }

    @Test
    public void testShortcomingScore() {
        // No shortage when supply >= demand
        double zeroShortcoming = marketProcessor.calculateShortcomingScore(100.0, 50.0, 1.0);
        assertEquals(0.0, zeroShortcoming);

        // Shortage when demand > supply
        double acuteShortcoming = marketProcessor.calculateShortcomingScore(10.0, 100.0, 1.5);
        assertTrue(acuteShortcoming > 0.0);
        assertEquals(((100.0 - 10.0) / (10.0 + 0.001)) * 1.5, acuteShortcoming, 0.01);
    }

    @Test
    public void testPlanetaryGravityLaunchTax() {
        // Zero G and vacuum (Moon/Orbital Station)
        double zeroGLift = marketProcessor.calculateGravityLaunchTax(5000.0, 1000.0, 0.0, 0.0, null);
        assertEquals(0.0, zeroGLift);

        // Terrestrial Earth launch (gravity: 1.0 G, atmosphere: 1.0 atm)
        double earthLaunch = marketProcessor.calculateGravityLaunchTax(5000.0, 1000.0, 1.0, 1.0, null);
        assertEquals((5000.0 + 1000.0) * 1.0 * (1.0 + 1.0), earthLaunch);
        assertEquals(12000.0, earthLaunch);

        // Hyper-gravity world (gravity: 2.5 G, atmosphere: 3.0 atm)
        double hyperGravityLaunch = marketProcessor.calculateGravityLaunchTax(5000.0, 1000.0, 2.5, 3.0, null);
        assertEquals((6000.0) * 2.5 * 4.0, hyperGravityLaunch);
        assertEquals(60000.0, hyperGravityLaunch);
    }

    @Test
    public void testOrbitalLiftCostZeroGravityAndVacuum() {
        OrbitalLiftProfile profile = marketProcessor.calculateOrbitalLiftCost(5000.0, 1000.0, 0.0, 0.0);
        assertEquals(0.0, profile.deltaVRequiredMps());
        assertEquals(0.0, profile.propellantConsumedKg());
        assertEquals(0.0, profile.propellantCostCredits());
        assertEquals(0.0, profile.spaceportHandlingFeeCredits());
        assertEquals(0.0, profile.turnaroundWearCostCredits());
        assertEquals(0.0, profile.totalLiftCostCredits());
        assertFalse(profile.isInfrastructureAssisted());
        assertEquals(0.0, profile.effectiveCargoCostPerKg(1000.0));
    }

    @Test
    public void testOrbitalLiftCostTerrestrialEarth() {
        // Earth launch: dry mass 5000 kg, cargo 1000 kg, 1.0 G, 1.0 atm
        OrbitalLiftProfile profile = marketProcessor.calculateOrbitalLiftCost(5000.0, 1000.0, 1.0, 1.0);

        // Delta-V to LEO should be approximately 9400 m/s (~7900 orbital + 1200 grav loss + 300 drag)
        assertTrue(profile.deltaVRequiredMps() > 9000.0 && profile.deltaVRequiredMps() < 9600.0,
                "Earth launch delta-v should be within realistic LEO range");

        // Propellant consumption via Tsiolkovsky equation (Isp = 1000s)
        assertTrue(profile.propellantConsumedKg() > 9000.0, "Should consume proportional propellant");
        assertEquals(profile.propellantConsumedKg() * 1.0, profile.propellantCostCredits(), 0.01);

        // Municipal spaceport fee for 6 tons (6 * 5.0 = 30 credits)
        assertEquals(30.0, profile.spaceportHandlingFeeCredits(), 0.01);

        // Total cost includes propellant, spaceport fee and turnaround wear
        assertEquals(profile.propellantCostCredits() + profile.spaceportHandlingFeeCredits() + profile.turnaroundWearCostCredits(),
                profile.totalLiftCostCredits(), 0.01);
        assertTrue(profile.effectiveCargoCostPerKg(1000.0) > 0.0);
    }

    @Test
    public void testOrbitalLiftCostLunarLowGravity() {
        // Luna: gravity 0.166 G, vacuum 0.0 atm
        OrbitalLiftProfile lunaProfile = marketProcessor.calculateOrbitalLiftCost(5000.0, 1000.0, 0.166, 0.0);
        OrbitalLiftProfile earthProfile = marketProcessor.calculateOrbitalLiftCost(5000.0, 1000.0, 1.0, 1.0);

        // Lunar delta-v is vastly lower than Earth (~1870 m/s vs ~9400 m/s)
        assertTrue(lunaProfile.deltaVRequiredMps() < 2500.0);
        assertTrue(lunaProfile.totalLiftCostCredits() < earthProfile.totalLiftCostCredits() * 0.25,
                "Lunar orbital lift cost should be a fraction of Earth launch cost");
    }

    @Test
    public void testOrbitalLiftCostSurfaceMassDriverAssistance() {
        SurfaceMassDriver massDriver = new SurfaceMassDriver(
                "driver_earth_01", "earth", "emp_terran", 5.0, 1500.0, 20.0, true
        );

        OrbitalLiftProfile unassisted = marketProcessor.calculateOrbitalLiftCost(5000.0, 1000.0, 1.0, 1.0, null);
        OrbitalLiftProfile assisted = marketProcessor.calculateOrbitalLiftCost(5000.0, 1000.0, 1.0, 1.0, List.of(massDriver));

        assertTrue(assisted.isInfrastructureAssisted(), "Mass driver launch should be infrastructure assisted");
        assertTrue(assisted.gridEnergyConsumedKwh() > 0.0, "Mass driver should consume electrical grid energy");
        assertTrue(assisted.propellantConsumedKg() < unassisted.propellantConsumedKg(),
                "Mass driver should slash rocket propellant requirements for cargo");
        assertTrue(assisted.totalLiftCostCredits() < unassisted.totalLiftCostCredits(),
                "Mass driver should lower total lift cost");
    }

    @Test
    public void testOrbitalLiftCostSpaceElevatorDiscounts() {
        SpaceElevator elevator = new SpaceElevator(
                "se_earth_01", "earth", "emp_terran", 100000.0, 0.95, 100.0, true
        );

        OrbitalLiftProfile unassisted = marketProcessor.calculateOrbitalLiftCost(5000.0, 1000.0, 1.0, 1.0);
        OrbitalLiftProfile assisted = marketProcessor.calculateOrbitalLiftCost(5000.0, 1000.0, 1.0, 1.0, null, List.of(elevator));

        assertTrue(assisted.deltaVRequiredMps() < unassisted.deltaVRequiredMps() * 0.10,
                "Space elevator should cut required delta-v by ~95%");
        assertTrue(assisted.totalLiftCostCredits() < unassisted.totalLiftCostCredits() * 0.10,
                "Space elevator should slash total lift cost by ~95%");
    }

    @Test
    public void testOrbitalLiftCostSpecificImpulseImpact() {
        // Comparison: chemical drive (450s) vs advanced fusion drive (3500s)
        OrbitalLiftProfile chemical = marketProcessor.calculateOrbitalLiftCost(
                5000.0, 1000.0, 1.0, 1.0, 0.0, 450.0, 1.0, null, null, 1.0
        );
        OrbitalLiftProfile fusion = marketProcessor.calculateOrbitalLiftCost(
                5000.0, 1000.0, 1.0, 1.0, 0.0, 3500.0, 1.0, null, null, 1.0
        );

        assertTrue(fusion.propellantConsumedKg() < chemical.propellantConsumedKg() * 0.20,
                "Advanced fusion drive should require vastly less propellant mass than chemical rocketry");
        assertTrue(fusion.totalLiftCostCredits() < chemical.totalLiftCostCredits());
    }

    @Test
    public void testOrbitalLiftCostInfrastructureEfficiencySubsidy() {
        OrbitalLiftProfile baseline = marketProcessor.calculateOrbitalLiftCost(
                5000.0, 1000.0, 1.0, 1.0, 0.0, 1000.0, 1.0, null, null, 1.0
        );
        OrbitalLiftProfile highInfra = marketProcessor.calculateOrbitalLiftCost(
                5000.0, 1000.0, 1.0, 1.0, 0.0, 1000.0, 1.0, null, null, 2.0
        );

        assertTrue(highInfra.spaceportHandlingFeeCredits() < baseline.spaceportHandlingFeeCredits(),
                "Higher public infrastructure efficiency should subsidize municipal spaceport handling fees");
        assertTrue(highInfra.turnaroundWearCostCredits() < baseline.turnaroundWearCostCredits(),
                "Higher infrastructure should reduce maintenance turnaround wear costs");
    }

    @Test
    public void testTariffSkimming() {
        double tariff = marketProcessor.calculateTariff(10000.0, 0.15);
        assertEquals(1500.0, tariff, 0.001);

        double zeroTariff = marketProcessor.calculateTariff(10000.0, 0.0);
        assertEquals(0.0, zeroTariff);
    }

    @Test
    public void testUpdateCommercialHub() {
        MarketOrder initialOrder = new MarketOrder("refined_silicon", 20.0, 150.0, 10.0, 0.0);
        CommercialHub hub = new CommercialHub(
                "hub_earth",
                "earth",
                0.10,
                50000.0,
                5000.0,
                15.0,
                Map.of("refined_silicon", initialOrder)
        );

        CommercialHub updated = marketProcessor.updateHub(hub);
        assertNotNull(updated);
        MarketOrder updatedOrder = updated.activeOrders().get("refined_silicon");
        assertNotNull(updatedOrder);
        assertTrue(updatedOrder.pricePerKg() > 10.0);
        assertTrue(updatedOrder.shortcomingScore() > 0.0);
    }
}
