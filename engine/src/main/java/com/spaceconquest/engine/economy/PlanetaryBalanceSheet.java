package com.spaceconquest.engine.economy;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Immutable municipal balance sheet recording local public revenues, operating expenditures,
 * net fiscal balance, and uncollected treasury reserves for a single celestial body.
 *
 * @param planetId                       celestial body identifier (planet or moon)
 * @param systemId                       solar system identifier
 * @param empireId                       sovereign empire identifier
 * @param grossPlanetaryProduct          total economic value generated (wages + corporate output)
 * @param incomeTaxRevenue               personal income taxes collected from citizen cohorts
 * @param corporateTariffRevenue         tariffs levied on local corporate trade and production
 * @param dockingFeeRevenue              commercial hub docking and spaceport handling fees
 * @param totalRevenueCredits            sum of all municipal public revenues
 * @param workforceSalaries              salaries paid to state personnel (police, soldiers, bureaucrats, medics, teachers, scientists)
 * @param facilityMaintenanceCosts       operating maintenance for public state-owned facilities
 * @param publicWelfareExpenditures      pension payments for retired cohorts and unemployment relief
 * @param infrastructureUpkeepCosts      power grid and life-support maintenance costs
 * @param totalExpenditureCredits        sum of all municipal public expenditures
 * @param netBalanceCredits              net fiscal balance (totalRevenueCredits - totalExpenditureCredits)
 * @param uncollectedLocalCredits        liquid credits stored locally awaiting courier transport or electronic transfer
 * @param centralSubsidyReceivedCredits  central treasury subsidy granted under the signed system policy
 * @param publicSectorFundingCredits      this body's share of the system's daily public budget
 * @param empireTransferCredits           signed transfer sent to the empire (negative for a subsidy received)
 * @param outstandingDebtCredits          unpaid municipal obligations carried into the next day
 */
public record PlanetaryBalanceSheet(
        @JsonProperty("planetId") String planetId,
        @JsonProperty("systemId") String systemId,
        @JsonProperty("empireId") String empireId,
        @JsonProperty("grossPlanetaryProduct") double grossPlanetaryProduct,
        @JsonProperty("incomeTaxRevenue") double incomeTaxRevenue,
        @JsonProperty("corporateTariffRevenue") double corporateTariffRevenue,
        @JsonProperty("dockingFeeRevenue") double dockingFeeRevenue,
        @JsonProperty("totalRevenueCredits") double totalRevenueCredits,
        @JsonProperty("workforceSalaries") double workforceSalaries,
        @JsonProperty("facilityMaintenanceCosts") double facilityMaintenanceCosts,
        @JsonProperty("publicWelfareExpenditures") double publicWelfareExpenditures,
        @JsonProperty("infrastructureUpkeepCosts") double infrastructureUpkeepCosts,
        @JsonProperty("totalExpenditureCredits") double totalExpenditureCredits,
        @JsonProperty("netBalanceCredits") double netBalanceCredits,
        @JsonProperty("uncollectedLocalCredits") double uncollectedLocalCredits,
        @JsonProperty("centralSubsidyReceivedCredits") double centralSubsidyReceivedCredits,
        @JsonProperty("publicSectorFundingCredits") double publicSectorFundingCredits,
        @JsonProperty("empireTransferCredits") double empireTransferCredits,
        @JsonProperty("outstandingDebtCredits") double outstandingDebtCredits
) {
    public PlanetaryBalanceSheet(
            String planetId, String systemId, String empireId, double grossPlanetaryProduct,
            double incomeTaxRevenue, double corporateTariffRevenue, double dockingFeeRevenue,
            double totalRevenueCredits, double workforceSalaries, double facilityMaintenanceCosts,
            double publicWelfareExpenditures, double infrastructureUpkeepCosts,
            double totalExpenditureCredits, double netBalanceCredits, double uncollectedLocalCredits,
            double centralSubsidyReceivedCredits, double publicSectorFundingCredits, double empireTransferCredits
    ) {
        this(planetId, systemId, empireId, grossPlanetaryProduct, incomeTaxRevenue,
                corporateTariffRevenue, dockingFeeRevenue, totalRevenueCredits, workforceSalaries,
                facilityMaintenanceCosts, publicWelfareExpenditures, infrastructureUpkeepCosts,
                totalExpenditureCredits, netBalanceCredits, uncollectedLocalCredits,
                centralSubsidyReceivedCredits, publicSectorFundingCredits, empireTransferCredits, 0.0);
    }

    public PlanetaryBalanceSheet(
            String planetId, String systemId, String empireId, double grossPlanetaryProduct,
            double incomeTaxRevenue, double corporateTariffRevenue, double dockingFeeRevenue,
            double totalRevenueCredits, double workforceSalaries, double facilityMaintenanceCosts,
            double publicWelfareExpenditures, double infrastructureUpkeepCosts,
            double totalExpenditureCredits, double netBalanceCredits,
            double uncollectedLocalCredits, double centralSubsidyReceivedCredits
    ) {
        this(planetId, systemId, empireId, grossPlanetaryProduct, incomeTaxRevenue,
                corporateTariffRevenue, dockingFeeRevenue, totalRevenueCredits, workforceSalaries,
                facilityMaintenanceCosts, publicWelfareExpenditures, infrastructureUpkeepCosts,
                totalExpenditureCredits, netBalanceCredits, uncollectedLocalCredits,
                centralSubsidyReceivedCredits, 0.0, 0.0, 0.0);
    }

    public PlanetaryBalanceSheet {
        Objects.requireNonNull(planetId, "planetId cannot be null");
        systemId = systemId != null ? systemId : "";
        empireId = empireId != null ? empireId : "";
        if (outstandingDebtCredits < 0.0) throw new IllegalArgumentException("Debt cannot be negative");
    }

    /**
     * Checks if the municipality ran a fiscal deficit this turn.
     */
    public boolean isDeficit() {
        return netBalanceCredits < 0.0;
    }

    /**
     * Checks if the municipality achieved fiscal self-sufficiency or surplus this turn.
     */
    public boolean isSelfSufficient() {
        return netBalanceCredits >= 0.0;
    }

    public PlanetaryBalanceSheet withOutstandingDebt(double debtCredits) {
        return new PlanetaryBalanceSheet(planetId, systemId, empireId, grossPlanetaryProduct,
                incomeTaxRevenue, corporateTariffRevenue, dockingFeeRevenue, totalRevenueCredits,
                workforceSalaries, facilityMaintenanceCosts, publicWelfareExpenditures,
                infrastructureUpkeepCosts, totalExpenditureCredits, netBalanceCredits,
                uncollectedLocalCredits, centralSubsidyReceivedCredits, publicSectorFundingCredits,
                empireTransferCredits, debtCredits);
    }

    /**
     * Creates an empty zero-credit balance sheet for uncolonized worlds or hive mind command economies.
     */
    public static PlanetaryBalanceSheet createEmpty(String planetId, String systemId, String empireId) {
        return new PlanetaryBalanceSheet(
                planetId, systemId, empireId,
                0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        );
    }
}
