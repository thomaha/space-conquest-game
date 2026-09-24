package com.spaceconquest.engine.market;

/**
 * Breakdown of physical and operational costs for lifting a vessel and its cargo from a celestial body's surface into orbit.
 *
 * @param deltaVRequiredMps           total delta-v required to reach orbit in m/s (orbital velocity + gravity loss + atmospheric drag)
 * @param propellantConsumedKg        propellant mass consumed in kg via the Tsiolkovsky rocket equation
 * @param propellantCostCredits       cost of the consumed propellant in universal credits
 * @param gridEnergyConsumedKwh       electrical energy consumed by ground-assisted infrastructure (e.g. mass driver or elevator tether) in kWh
 * @param spaceportHandlingFeeCredits municipal spaceport handling fee in universal credits
 * @param turnaroundWearCostCredits   vehicle turnaround wear, inspection, and thermal protection maintenance cost in universal credits
 * @param totalLiftCostCredits        total operational and physical cost of the orbital lift in universal credits
 */
public record OrbitalLiftProfile(
        double deltaVRequiredMps,
        double propellantConsumedKg,
        double propellantCostCredits,
        double gridEnergyConsumedKwh,
        double spaceportHandlingFeeCredits,
        double turnaroundWearCostCredits,
        double totalLiftCostCredits
) {
    public OrbitalLiftProfile(
            double deltaVRequiredMps,
            double propellantConsumedKg,
            double propellantCostCredits,
            double gridEnergyConsumedKwh,
            double spaceportHandlingFeeCredits,
            double totalLiftCostCredits
    ) {
        this(deltaVRequiredMps, propellantConsumedKg, propellantCostCredits, gridEnergyConsumedKwh, spaceportHandlingFeeCredits, 0.0, totalLiftCostCredits);
    }

    /**
     * Indicates whether the launch is ground-assisted by mass drivers or space elevators.
     *
     * @return true if ground electrical infrastructure was utilized
     */
    public boolean isInfrastructureAssisted() {
        return gridEnergyConsumedKwh > 0.0;
    }

    /**
     * Calculates the effective cost per kilogram of transported cargo.
     *
     * @param cargoMassKg cargo mass in kilograms
     * @return credit cost per kg, or 0.0 if cargo mass is non-positive
     */
    public double effectiveCargoCostPerKg(double cargoMassKg) {
        if (cargoMassKg <= 0.0) {
            return 0.0;
        }
        return totalLiftCostCredits / cargoMassKg;
    }
}
