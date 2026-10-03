package com.spaceconquest.engine.ship;

import java.util.List;
import java.util.Map;

/** Frozen electrical equipment values. All coefficients are provisional game balance values. */
public record ShipPowerProfile(double solarKw, double chemicalKw, double fissionKw,
                               double batteryKwh, double chargeKw, double dischargeKw,
                               double generatorTankKg, double reactorTankKg,
                               double hotelKw, double driveKw, double cargoKw, double electricDriveEfficiency,
                               Map<String, Fuel> fuels) {
    public record Fuel(String materialId, String oxidizerId, double fuelFraction, double kwhPerKg) {
        public Fuel {
            if (materialId == null || materialId.isBlank() || !Double.isFinite(fuelFraction)
                    || fuelFraction <= 0 || fuelFraction > 1 || oxidizerId != null && fuelFraction >= 1
                    || !Double.isFinite(kwhPerKg) || kwhPerKg <= 0)
                throw new IllegalArgumentException("Invalid electrical fuel profile");
        }
        public Map<String, Double> materials(double kg) {
            return oxidizerId == null ? Map.of(materialId, kg)
                    : Map.of(materialId, kg * fuelFraction, oxidizerId, kg * (1 - fuelFraction));
        }
    }

    public ShipPowerProfile {
        fuels = Map.copyOf(fuels);
        for (double value : new double[]{solarKw, chemicalKw, fissionKw, batteryKwh, chargeKw,
                dischargeKw, generatorTankKg, reactorTankKg, hotelKw, driveKw, cargoKw, electricDriveEfficiency})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid power profile");
        if (electricDriveEfficiency > 1) throw new IllegalArgumentException("Invalid thruster efficiency");
    }

    public static ShipPowerProfile capture(List<ShipModule> modules) {
        double solar = 0, chemical = 0, fission = 0, capacity = 0, charge = 0, discharge = 0;
        double tank = 0, reactorTank = 0, drive = 0, cargo = 0, hotel = 2, efficiency = .65;
        for (ShipModule module : modules) {
            switch (module.id()) {
                case ShipComponentCatalog.SOLAR_ARRAY_ID -> solar += module.powerOutputKw();
                case ShipComponentCatalog.CHEMICAL_GENERATOR_ID -> chemical += module.powerOutputKw();
                case ShipComponentCatalog.FISSION_REACTOR_ID -> fission += module.powerOutputKw();
                default -> { }
            }
            capacity += module.operationalStats().getOrDefault("batteryKwh", 0.0);
            charge += module.operationalStats().getOrDefault("chargeKw", 0.0);
            discharge += module.operationalStats().getOrDefault("dischargeKw", 0.0);
            tank += module.operationalStats().getOrDefault("generatorTankKg", 0.0);
            reactorTank += module.operationalStats().getOrDefault("reactorTankKg", 0.0);
            efficiency = module.operationalStats().getOrDefault("thrusterEfficiency", efficiency);
            if (PropulsionCatalog.drive(module.id()) != null) drive += module.powerDrawKw();
            else if (module.operationalStats().containsKey("cargoCapacityKg")) cargo += module.powerDrawKw();
            else if (!"cryogenic_stasis_pod".equals(module.id())) hotel += module.powerDrawKw();
        }
        return new ShipPowerProfile(solar, chemical, fission, capacity, charge, discharge,
                tank, reactorTank, hotel, drive, cargo, efficiency, Map.of(
                "rp1", new Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008),
                "methalox", new Fuel("liquid_methane", "liquid_oxygen", .22, .924),
                "hydrolox", new Fuel("liquid_hydrogen", "liquid_oxygen", .17, 1.7),
                "uranium", new Fuel("refined_uranium", null, 1, 6_000_000),
                "thorium", new Fuel("refined_thorium", null, 1, 5_000_000)));
    }

    public double essentialKw(ShipInstance ship, ShipDesign design) {
        if (ship.passengerCount() == 0) return hotelKw;
        if (!ShipInstance.MODE_CRYOGENIC_STASIS.equals(ship.transitMode()))
            return hotelKw + ship.passengerCount();
        int capacity = com.spaceconquest.engine.habitation.PassengerStasis.capacity(design);
        long pods = design.equippedModuleIds().stream().filter("cryogenic_stasis_pod"::equals).count();
        double perPod = capacity > 0 && pods > 0 ? (double) capacity / pods : 100;
        return hotelKw + 80 * Math.ceil(ship.passengerCount() / perPod);
    }
}
