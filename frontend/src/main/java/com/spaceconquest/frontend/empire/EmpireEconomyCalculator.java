package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SystemGovernor;
import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.frontend.PlanetaryBodyEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmpireEconomyCalculator {
    private static final Logger logger = LogManager.getLogger(EmpireEconomyCalculator.class);

    public static EmpireEconomyReport calculateEmpireEconomyReport(EmpireView view) {
        ensureFallbackDataLoaded(view);
        Empire playerEmpire = view.getPlayerEmpire();
        double treasury = playerEmpire != null ? playerEmpire.treasuryCredits() : 100_000.0;
        double corpTaxRate = playerEmpire != null ? playerEmpire.corporateTaxRate() : 0.15;
        List<String> controlledSystems = playerEmpire != null ? playerEmpire.controlledSystemIds() : List.of();

        List<PlanetaryBodyEntry> allBodies = view.getAllPlanetaryBodies();
        List<PlanetaryBodyEntry> playerColonies = allBodies.stream()
                .filter(PlanetaryBodyEntry::isColonized)
                .filter(b -> controlledSystems.isEmpty() || controlledSystems.contains(b.systemId()))
                .toList();

        long totalPop = playerColonies.stream().mapToLong(PlanetaryBodyEntry::totalPopulation).sum();
        int colonizedCount = playerColonies.size();
        int controlledSysCount = calculateControlledSystemCount(playerEmpire, playerColonies);

        List<ColonyEconomyEntry> colonyLedger = new ArrayList<>();
        double totalColonialTax = calculateColonyLedger(view, playerColonies, colonyLedger);
        double totalLocalGovCost = colonyLedger.stream().mapToDouble(ColonyEconomyEntry::localGovernanceCostCredits).sum();

        List<IndustrialFacility> playerFacilities = getPlayerPublicFacilities(view);
        double stateIndustryIncome = playerFacilities.stream()
                .mapToDouble(f -> 350.0 * f.getEffectiveThroughputMultiplier())
                .sum();

        List<CorporateEconomyEntry> corporateLedger = new ArrayList<>();
        double totalCorporateTariffs = calculateCorporateLedger(view, corpTaxRate, corporateLedger);

        double spaceElevatorIncome = calculateSpaceElevatorIncome(view);
        double miningRoyalties = calculateMiningRoyalties(view, playerColonies);

        double totalIncome = totalColonialTax + stateIndustryIncome + totalCorporateTariffs + spaceElevatorIncome + miningRoyalties;
        double governanceExpenses = totalLocalGovCost + (controlledSysCount * 300.0);
        double ministryBudgets = calculateMinistryBudgets(playerEmpire);
        double infraMaintenance = playerFacilities.stream().mapToDouble(f -> 80.0 * f.tier()).sum() + (view.getPowerGrids().size() * 50.0);
        double stationMaintenance = view.getOrbitalStations().stream().filter(s -> s.ownerEntityId().equalsIgnoreCase(view.getPlayerEmpireId())).count() * 400.0;
        double researchSubsidies = view.getResearchProjects().stream().filter(p -> p.empireId().equals(view.getPlayerEmpireId())).count() * 200.0;
        double terraformingSubsidies = view.getTerraformingProjects().stream().filter(p -> p.ownerEmpireId().equals(view.getPlayerEmpireId())).count() * 300.0;

        double totalCosts = governanceExpenses + ministryBudgets + infraMaintenance + stationMaintenance + researchSubsidies + terraformingSubsidies;
        double netBalance = totalIncome - totalCosts;

        return new EmpireEconomyReport(
                treasury, corpTaxRate, totalPop, colonizedCount, controlledSysCount,
                totalColonialTax, stateIndustryIncome, totalCorporateTariffs, spaceElevatorIncome,
                miningRoyalties, totalIncome, governanceExpenses, ministryBudgets, infraMaintenance,
                stationMaintenance, researchSubsidies, terraformingSubsidies, totalCosts, netBalance,
                colonyLedger, corporateLedger
        );
    }

    private static int calculateControlledSystemCount(Empire empire, List<PlanetaryBodyEntry> colonies) {
        int count = (int) colonies.stream().map(PlanetaryBodyEntry::systemId).distinct().count();
        if (empire != null && !empire.controlledSystemIds().isEmpty()) {
            count = Math.max(count, empire.controlledSystemIds().size());
        }
        return count;
    }

    private static double calculateColonyLedger(EmpireView view, List<PlanetaryBodyEntry> colonies, List<ColonyEconomyEntry> ledger) {
        double totalTax = 0.0;
        Map<String, PlanetaryBalanceSheet> sheetMap = new HashMap<>();
        if (view != null && view.getPlanetaryBalanceSheets() != null) {
            for (PlanetaryBalanceSheet sheet : view.getPlanetaryBalanceSheets()) {
                sheetMap.put(sheet.planetId().toLowerCase(), sheet);
            }
        }

        for (PlanetaryBodyEntry colony : colonies) {
            long pop = colony.totalPopulation();
            PlanetaryBalanceSheet sheet = sheetMap.get(colony.id().toLowerCase());
            double grossOutput;
            double taxCollected;
            double localGov;
            double net;

            if (sheet != null) {
                grossOutput = sheet.grossPlanetaryProduct();
                taxCollected = sheet.totalRevenueCredits();
                localGov = sheet.totalExpenditureCredits();
                net = sheet.netBalanceCredits();
            } else {
                grossOutput = pop * 0.005;
                taxCollected = grossOutput * 0.10;
                SystemEconomy systemEconomy = view.getSystemEconomies().stream().filter(se -> se.systemId().equals(colony.systemId())).findFirst().orElse(null);
                double systemBudget = systemEconomy != null ? systemEconomy.totalBudgetCredits() : (pop * 0.002);
                long systemPop = colonies.stream().filter(c -> c.systemId().equals(colony.systemId())).mapToLong(PlanetaryBodyEntry::totalPopulation).sum();
                localGov = (systemBudget * (systemPop > 0 ? (double) pop / systemPop : 1.0)) + 200.0;
                net = taxCollected - localGov;
            }

            ledger.add(new ColonyEconomyEntry(colony.id(), colony.name(), colony.systemName(), colony.isMoon(), pop, grossOutput, taxCollected, localGov, net));
            totalTax += taxCollected;
        }
        return totalTax;
    }

    private static double calculateCorporateLedger(EmpireView view, double taxRate, List<CorporateEconomyEntry> ledger) {
        double totalTariffs = 0.0;
        for (Corporation corp : view.getCorporationsForPlayerEmpire()) {
            double estimatedTariff = (corp.liquidCapitalReserves() * 0.001) + (corp.ownedFacilityIds().size() * 120.0 * taxRate);
            ledger.add(new CorporateEconomyEntry(corp.id(), corp.name(), corp.headquartersEntityId(), corp.marketOrientation(), corp.liquidCapitalReserves(), corp.ownedFacilityIds().size(), estimatedTariff));
            totalTariffs += estimatedTariff;
        }
        return totalTariffs;
    }

    private static double calculateSpaceElevatorIncome(EmpireView view) {
        return view.getSpaceElevators().stream().filter(e -> e.ownerEntityId().equalsIgnoreCase(view.getPlayerEmpireId())).mapToDouble(e -> e.isOperational() ? (e.transitThroughputCapacityKgPerTurn() * 0.00005) : 0.0).sum();
    }

    private static double calculateMiningRoyalties(EmpireView view, List<PlanetaryBodyEntry> colonies) {
        return view.getDeposits().stream().filter(d -> colonies.stream().anyMatch(c -> c.id().equalsIgnoreCase(d.planetId()))).count() * 150.0;
    }

    private static double calculateMinistryBudgets(Empire empire) {
        if (empire == null || empire.ministries() == null) return 5 * 250.0;
        return empire.ministries().stream().mapToDouble(ma -> 250.0 * ma.calculatedEfficiencyModifier()).sum();
    }

    private static List<IndustrialFacility> getPlayerPublicFacilities(EmpireView view) {
        return view.getFacilities().stream().filter(f -> f.ownerEntityId().equalsIgnoreCase(view.getPlayerEmpireId()) && "PUBLIC_STATE".equalsIgnoreCase(f.ownershipType())).toList();
    }

    public static SystemEconomyReport calculateSystemEconomyReport(EmpireView view, String systemId) {
        ensureFallbackDataLoaded(view);

        SolarSystem system = view.getSystems().stream()
                .filter(s -> s.id().equals(systemId))
                .findFirst()
                .orElse(null);

        String systemName = system != null ? system.name() : (systemId != null ? systemId : "Unknown");
        Empire playerEmpire = view.getPlayerEmpire();
        String empireId = playerEmpire != null ? playerEmpire.id() : view.getPlayerEmpireId();

        long systemPop = 0;
        int colonizedCount = 0;
        List<String> systemBodyIds = new ArrayList<>();
        if (system != null && system.planets() != null) {
            for (Planet p : system.planets()) {
                systemBodyIds.add(p.id());
                long pPop = 0;
                if (p.populations() != null) {
                    for (Population pop : p.populations()) pPop += pop.totalCount();
                }
                if (pPop > 0) {
                    systemPop += pPop;
                    colonizedCount++;
                }
                if (p.moons() != null) {
                    for (Moon m : p.moons()) {
                        systemBodyIds.add(m.id());
                        long mPop = 0;
                        if (m.populations() != null) {
                            for (Population pop : m.populations()) mPop += pop.totalCount();
                        }
                        if (mPop > 0) {
                            systemPop += mPop;
                            colonizedCount++;
                        }
                    }
                }
            }
        }

        double grossOutput = systemPop * 0.005;

        SystemEconomy economy = view.getSystemEconomies().stream()
                .filter(se -> se.systemId().equals(systemId))
                .findFirst()
                .orElse(null);

        if (economy == null) {
            economy = SystemEconomy.createDefault(systemId, empireId, systemPop);
        }

        double currentTaxRate = economy.taxRate();
        double colonialTaxes = grossOutput * currentTaxRate;

        if (view.getPlanetaryBalanceSheets() != null && !view.getPlanetaryBalanceSheets().isEmpty()) {
            double aggGross = 0.0;
            double aggTaxes = 0.0;
            boolean foundAny = false;
            for (PlanetaryBalanceSheet sheet : view.getPlanetaryBalanceSheets()) {
                if (systemBodyIds.contains(sheet.planetId()) || systemId.equalsIgnoreCase(sheet.systemId())) {
                    aggGross += sheet.grossPlanetaryProduct();
                    aggTaxes += sheet.totalRevenueCredits();
                    foundAny = true;
                }
            }
            if (foundAny) {
                grossOutput = aggGross;
                colonialTaxes = aggTaxes;
            }
        }

        double corporateTariffs = 0.0;
        double playerCorpTaxRate = playerEmpire != null ? playerEmpire.corporateTaxRate() : 0.15;
        for (Corporation corp : view.getCorporationsForPlayerEmpire()) {
            if (systemBodyIds.contains(corp.headquartersEntityId())) {
                corporateTariffs += (corp.liquidCapitalReserves() * 0.001) + (corp.ownedFacilityIds().size() * 120.0 * playerCorpTaxRate);
            }
        }

        long elevatorsInSystem = view.getSpaceElevators().stream()
                .filter(se -> systemBodyIds.contains(se.planetId()) && se.ownerEntityId().equalsIgnoreCase(empireId))
                .count();
        double elevatorFees = elevatorsInSystem * 500.0;

        long depositsInSystem = view.getDeposits().stream()
                .filter(d -> systemBodyIds.contains(d.planetId()))
                .count();
        double miningRoyalties = depositsInSystem * 150.0;

        double stateIndustryIncome = view.getFacilities().stream()
                .filter(f -> systemBodyIds.contains(f.planetId()) && empireId.equalsIgnoreCase(f.ownerEntityId()) && "PUBLIC_STATE".equalsIgnoreCase(f.ownershipType()))
                .mapToDouble(f -> 350.0 * f.getEffectiveThroughputMultiplier())
                .sum();

        double totalRevenues = colonialTaxes + corporateTariffs + elevatorFees + miningRoyalties + stateIndustryIncome;

        double publicSectorFunding = economy.totalBudgetCredits();
        double governorAdmin = view.getSystemGovernors().stream().anyMatch(g -> g.solarSystemId().equals(systemId)) ? 300.0 : 0.0;
        long stationsInSystem = view.getOrbitalStations().stream()
                .filter(st -> (systemId.equals(st.systemId()) || systemBodyIds.contains(st.planetOrbitId())) && empireId.equalsIgnoreCase(st.ownerEntityId()))
                .count();
        double stationMaint = stationsInSystem * 400.0;

        double totalExpenditures = publicSectorFunding + governorAdmin + stationMaint;
        double netBalance = totalRevenues - totalExpenditures;

        return new SystemEconomyReport(
                systemId, systemName, empireId, systemPop, colonizedCount,
                grossOutput, colonialTaxes, corporateTariffs, elevatorFees,
                miningRoyalties, stateIndustryIncome, totalRevenues,
                publicSectorFunding, governorAdmin, stationMaint,
                totalExpenditures, netBalance, currentTaxRate, economy
        );
    }

    private static void ensureFallbackDataLoaded(EmpireView view) {
        if (view.getEmpires().isEmpty()) {
            try {
                view.getEmpires().addAll(DataModelLoader.loadEmpires());
            } catch (IOException e) {
                logger.error("Failed to load empires fallback data", e);
            }
        }
        if (view.getCorporations().isEmpty()) {
            try {
                view.getCorporations().addAll(DataModelLoader.loadCorporations());
            } catch (IOException e) {
                logger.error("Failed to load corporate registry fallback data", e);
            }
        }
    }
}
