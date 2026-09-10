package com.spaceconquest.engine.market;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.industry.SurfaceMassDriver;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes market pricing, shortcoming scores (S_m), transaction tariffs,
 * and planetary gravity export taxes across commercial hubs.
 */
public class MarketProcessor {

    private static final double EPSILON = 0.001;
    private static final double DEFAULT_BASE_PRICE = 10.0;

    /**
     * Standard acceleration due to gravity on Earth in m/s^2.
     */
    public static final double STANDARD_GRAVITY_G0 = 9.80665;

    /**
     * Baseline terrestrial planet radius in meters (Earth radius).
     */
    public static final double DEFAULT_PLANETARY_RADIUS_M = 6_371_000.0;

    /**
     * Standard specific impulse in seconds for baseline nuclear thermal/fission cargo propulsion.
     */
    public static final double DEFAULT_SPECIFIC_IMPULSE_SEC = 1000.0;

    /**
     * Default propellant spot price in credits per kg.
     */
    public static final double DEFAULT_PROPELLANT_PRICE_PER_KG = 1.0;

    /**
     * Municipal spaceport handling fee per metric ton in credits.
     */
    public static final double DEFAULT_SPACEPORT_FEE_PER_TON = 5.0;

    /**
     * Ascent gravity loss factor in m/s per standard G.
     */
    public static final double GRAVITY_LOSS_PER_G_MPS = 1200.0;

    /**
     * Atmospheric drag loss coefficient in m/s per atmosphere of pressure.
     */
    public static final double DRAG_LOSS_PER_ATM_MPS = 300.0;

    /**
     * Calculates the spot price per kilogram for a commodity based on local supply and demand.
     *
     * @param basePrice baseline resource price
     * @param supplyKg  available local supply in kg
     * @param demandKg  local demand in kg
     * @return calculated spot price per kg
     */
    public double calculateSpotPrice(double basePrice, double supplyKg, double demandKg) {
        if (basePrice <= 0) {
            basePrice = DEFAULT_BASE_PRICE;
        }
        double ratio = (demandKg - supplyKg) / (supplyKg + demandKg + 1.0);
        double multiplier = Math.max(0.1, 1.0 + ratio);
        return Math.round(basePrice * multiplier * 100.0) / 100.0;
    }

    /**
     * Calculates the Shortcoming Score (S_m) for a resource.
     * Formula: S_m = ((Local Demand - Local Supply) / (Local Supply + epsilon)) * Market Price Modifier
     *
     * @param supplyKg           local supply in kg
     * @param demandKg           local demand in kg
     * @param marketPriceModifier market price multiplier
     * @return calculated shortcoming score S_m
     */
    public double calculateShortcomingScore(double supplyKg, double demandKg, double marketPriceModifier) {
        if (demandKg <= supplyKg) {
            return 0.0;
        }
        double deficitRatio = (demandKg - supplyKg) / (supplyKg + EPSILON);
        return Math.max(0.0, deficitRatio * marketPriceModifier);
    }

    /**
     * Calculates the surface-to-orbit orbital lift cost profile based on astrodynamics,
     * propellant expenditure via the Tsiolkovsky equation, atmospheric drag, municipal spaceport fees
     * and ground-assisted launch infrastructure.
     *
     * @param dryMassKg                dry structural mass of the vessel in kg
     * @param cargoMassKg              mass of loaded cargo in kg
     * @param surfaceGravity           surface gravity (in standard Gs or m/s^2)
     * @param atmosphericPressure      atmospheric pressure in standard atmospheres (0.0 for vacuum)
     * @param planetaryDiameterKm      planetary diameter in km (<= 0 for standard Earth scaling)
     * @param specificImpulseSec       engine specific impulse in seconds (<= 0 for standard default)
     * @param propellantPricePerKg     propellant spot price in credits/kg (<= 0 for standard default)
     * @param massDrivers              active surface mass driver catapults on the planet
     * @param spaceElevators           active space elevators on the planet
     * @param infrastructureEfficiency public system infrastructure efficiency factor (<= 0 for standard 1.0)
     * @return detailed breakdown of orbital lift costs and physical metrics
     */
    public OrbitalLiftProfile calculateOrbitalLiftCost(
            double dryMassKg,
            double cargoMassKg,
            double surfaceGravity,
            double atmosphericPressure,
            double planetaryDiameterKm,
            double specificImpulseSec,
            double propellantPricePerKg,
            List<SurfaceMassDriver> massDrivers,
            List<SpaceElevator> spaceElevators,
            double infrastructureEfficiency
    ) {
        double safeDryMass = Math.max(0.0, dryMassKg);
        double safeCargoMass = Math.max(0.0, cargoMassKg);
        double totalMassKg = safeDryMass + safeCargoMass;

        if (totalMassKg <= 0.0 || surfaceGravity <= 0.0) {
            return new OrbitalLiftProfile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        double gravityG = (surfaceGravity > 4.0) ? (surfaceGravity / STANDARD_GRAVITY_G0) : surfaceGravity;
        double gravityMps2 = (surfaceGravity > 4.0) ? surfaceGravity : (surfaceGravity * STANDARD_GRAVITY_G0);

        double effectiveAtmo = Math.max(0.0, atmosphericPressure);
        double isp = (specificImpulseSec > 0.0) ? specificImpulseSec : DEFAULT_SPECIFIC_IMPULSE_SEC;
        double fuelPrice = (propellantPricePerKg > 0.0) ? propellantPricePerKg : DEFAULT_PROPELLANT_PRICE_PER_KG;
        double infraEff = (infrastructureEfficiency > 0.0) ? infrastructureEfficiency : 1.0;

        double radiusM = (planetaryDiameterKm > 0.0)
                ? (planetaryDiameterKm * 1000.0) / 2.0
                : (DEFAULT_PLANETARY_RADIUS_M * Math.max(0.1, Math.sqrt(gravityG)));

        double vOrbital = Math.sqrt(gravityMps2 * radiusM);
        double deltaVGravLoss = GRAVITY_LOSS_PER_G_MPS * gravityG;
        double deltaVDrag = DRAG_LOSS_PER_ATM_MPS * effectiveAtmo * Math.sqrt(Math.max(0.01, gravityG));
        double totalDeltaVMps = vOrbital + deltaVGravLoss + deltaVDrag;

        if (spaceElevators != null) {
            for (SpaceElevator elevator : spaceElevators) {
                if (elevator.isOperational() && elevator.transitThroughputCapacityKgPerTurn() >= safeCargoMass) {
                    double discount = elevator.surfaceToOrbitCostDiscount();
                    double discountedDeltaV = totalDeltaVMps * (1.0 - discount);
                    double propellantKg = totalMassKg * (Math.exp(discountedDeltaV / (isp * STANDARD_GRAVITY_G0)) - 1.0);
                    double propellantCost = propellantKg * fuelPrice;
                    double gridEnergyKwh = (totalMassKg * gravityMps2 * (radiusM * 0.05)) / 3_600_000.0;
                    double spaceportFee = (totalMassKg / 1000.0) * (DEFAULT_SPACEPORT_FEE_PER_TON * (1.0 - discount)) / infraEff;
                    double totalCost = propellantCost + spaceportFee;
                    return new OrbitalLiftProfile(discountedDeltaV, propellantKg, propellantCost, gridEnergyKwh, spaceportFee, 0.0, totalCost);
                }
            }
        }

        double gridEnergyKwh = 0.0;
        double massDriverFee = 0.0;
        double rocketMassKg = totalMassKg;

        if (massDrivers != null) {
            for (SurfaceMassDriver driver : massDrivers) {
                if (driver.isActive() && driver.maxPayloadTonsPerTurn() * 1000.0 >= safeCargoMass) {
                    double cargoTons = safeCargoMass / 1000.0;
                    massDriverFee = cargoTons * driver.launchCostPerTonCredits();
                    double kineticEnergyJoules = 0.5 * safeCargoMass * Math.pow(vOrbital, 2);
                    gridEnergyKwh = (kineticEnergyJoules / 3_600_000.0) / 0.85;
                    rocketMassKg = safeDryMass + (safeCargoMass * 0.10);
                    break;
                }
            }
        }

        double massRatio = Math.exp(totalDeltaVMps / (isp * STANDARD_GRAVITY_G0));
        double propellantKg = rocketMassKg * (massRatio - 1.0);
        double propellantCost = propellantKg * fuelPrice;
        double spaceportFee = (totalMassKg / 1000.0) * DEFAULT_SPACEPORT_FEE_PER_TON / infraEff;
        double wearCost = (totalMassKg / 1000.0) * (2.0 * gravityG + 5.0 * effectiveAtmo) / infraEff;
        double totalCost = propellantCost + spaceportFee + wearCost + massDriverFee;

        return new OrbitalLiftProfile(totalDeltaVMps, propellantKg, propellantCost, gridEnergyKwh, spaceportFee, wearCost, totalCost);
    }

    public OrbitalLiftProfile calculateOrbitalLiftCost(
            double dryMassKg,
            double cargoMassKg,
            double surfaceGravity,
            double atmosphericPressure,
            List<SurfaceMassDriver> massDrivers,
            List<SpaceElevator> spaceElevators
    ) {
        return calculateOrbitalLiftCost(dryMassKg, cargoMassKg, surfaceGravity, atmosphericPressure, 0.0, 0.0, 0.0, massDrivers, spaceElevators, 1.0);
    }

    public OrbitalLiftProfile calculateOrbitalLiftCost(
            double dryMassKg,
            double cargoMassKg,
            double surfaceGravity,
            double atmosphericPressure,
            List<SurfaceMassDriver> massDrivers
    ) {
        return calculateOrbitalLiftCost(dryMassKg, cargoMassKg, surfaceGravity, atmosphericPressure, 0.0, 0.0, 0.0, massDrivers, null, 1.0);
    }

    public OrbitalLiftProfile calculateOrbitalLiftCost(
            double dryMassKg,
            double cargoMassKg,
            double surfaceGravity,
            double atmosphericPressure,
            double propellantPricePerKg,
            List<SurfaceMassDriver> massDrivers,
            List<SpaceElevator> spaceElevators
    ) {
        return calculateOrbitalLiftCost(dryMassKg, cargoMassKg, surfaceGravity, atmosphericPressure, 0.0, 0.0, propellantPricePerKg, massDrivers, spaceElevators, 1.0);
    }

    public OrbitalLiftProfile calculateOrbitalLiftCost(
            double dryMassKg,
            double cargoMassKg,
            double surfaceGravity,
            double atmosphericPressure
    ) {
        return calculateOrbitalLiftCost(dryMassKg, cargoMassKg, surfaceGravity, atmosphericPressure, 0.0, 0.0, 0.0, null, null, 1.0);
    }

    /**
     * Calculates the planetary blast-off gravity tax for exporting cargo from a celestial body.
     * Formula: Launch Cost = (Dry Mass + Stored Cargo Mass) * Gravity * (1 + Atmospheric Pressure)
     * If an active SurfaceMassDriver is present, the tax is significantly reduced (Point 3).
     *
     * @param dryMassKg           dry structural mass of the vessel in kg
     * @param cargoMassKg         mass of loaded cargo in kg
     * @param surfaceGravity      surface gravity in standard Gs
     * @param atmosphericPressure atmospheric pressure in atmospheres
     * @param massDrivers         list of mass drivers on the planet
     * @return launch tax in credits
     * @deprecated Replaced by {@link #calculateOrbitalLiftCost(double, double, double, double, List, List)}
     * which models realistic physics-based propellant consumption, atmospheric drag, and port fees.
     */
    @Deprecated
    public double calculateGravityLaunchTax(
            double dryMassKg,
            double cargoMassKg,
            double surfaceGravity,
            double atmosphericPressure,
            List<SurfaceMassDriver> massDrivers
    ) {
        double effectiveGravity = Math.max(0.0, surfaceGravity);
        double effectiveAtmosphere = Math.max(0.0, atmosphericPressure);
        double baselineTax = (dryMassKg + cargoMassKg) * effectiveGravity * (1.0 + effectiveAtmosphere);

        // Surface Mass Driver bypass (Point 3)
        if (massDrivers != null) {
            for (SurfaceMassDriver driver : massDrivers) {
                if (driver.isActive() && driver.maxPayloadTonsPerTurn() * 1000.0 >= cargoMassKg) {
                    // Reduces the tax by 90% if a catapult is used
                    return baselineTax * 0.10;
                }
            }
        }

        return baselineTax;
    }

    /**
     * Calculates the transaction tariff skimmed by the sovereign state.
     * Formula: Tariff = Gross Transaction Value * Transaction Tariff Rate
     *
     * @param grossTransactionValue gross credit value of the trade
     * @param tariffRate            tariff rate (0.0 to 1.0)
     * @return collected tariff credits
     */
    public double calculateTariff(double grossTransactionValue, double tariffRate) {
        return Math.max(0.0, grossTransactionValue * Math.max(0.0, tariffRate));
    }

    /**
     * Refreshes active market orders and recalculates spot prices and shortcoming scores for all hubs.
     *
     * @param hubs list of commercial hubs to process
     * @return updated list of commercial hubs
     */
    public List<CommercialHub> updateCommercialHubs(List<CommercialHub> hubs) {
        if (hubs == null) {
            return List.of();
        }
        return hubs.stream().map(this::updateHub).toList();
    }

    /**
     * Updates an individual commercial hub's market orders.
     *
     * @param hub commercial hub to update
     * @return updated commercial hub
     */
    public CommercialHub updateHub(CommercialHub hub) {
        Map<String, MarketOrder> updatedOrders = new HashMap<>();
        for (Map.Entry<String, MarketOrder> entry : hub.activeOrders().entrySet()) {
            String resourceId = entry.getKey();
            MarketOrder order = entry.getValue();
            double spotPrice = calculateSpotPrice(DEFAULT_BASE_PRICE, order.supplyKg(), order.demandKg());
            double priceModifier = spotPrice / DEFAULT_BASE_PRICE;
            double shortcoming = calculateShortcomingScore(order.supplyKg(), order.demandKg(), priceModifier);

            updatedOrders.put(resourceId, new MarketOrder(
                    resourceId,
                    order.supplyKg(),
                    order.demandKg(),
                    spotPrice,
                    shortcoming
            ));
        }

        return new CommercialHub(
                hub.id(),
                hub.entityId(),
                hub.transactionTariffRate(),
                hub.storageCapacityKg(),
                hub.currentStoredWeightKg(),
                hub.logisticsRangeUnits(),
                updatedOrders
        );
    }
}
