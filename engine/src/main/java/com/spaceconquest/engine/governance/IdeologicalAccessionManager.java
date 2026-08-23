package com.spaceconquest.engine.governance;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.MinistryPortfolio;
import com.spaceconquest.engine.SystemGovernor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages democratic periodic election cycles driven by demographic shortages,
 * autocratic direct sovereign appointments, and Hive Mind governance bypass logic.
 */
public class IdeologicalAccessionManager {

    private final GovernanceProcessor governanceProcessor;

    public IdeologicalAccessionManager() {
        this(new GovernanceProcessor());
    }

    public IdeologicalAccessionManager(GovernanceProcessor governanceProcessor) {
        this.governanceProcessor = governanceProcessor;
    }

    /**
     * Executes a democratic election cycle for an empire based on regional shortages and demographic priorities.
     *
     * @param empire     the sovereign empire
     * @param shortages  mapping of resource category to shortage severity score
     * @param portfolios available ministry portfolios
     * @return updated empire with newly elected cabinet
     */
    public Empire runDemocraticElection(
            Empire empire,
            Map<String, Double> shortages,
            List<MinistryPortfolio> portfolios
    ) {
        if (empire == null) return null;

        if ("Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure())) {
            // Hive minds bypass elections and ministries completely
            return empire;
        }

        List<MinistryAssignment> newCabinet = new ArrayList<>();
        if (portfolios != null) {
            for (MinistryPortfolio portfolio : portfolios) {
                String electedProfession = selectElectedProfession(portfolio, shortages);
                double efficiency = governanceProcessor.calculateMinisterEfficiency(
                        portfolio.id(),
                        electedProfession,
                        portfolios,
                        false
                );
                newCabinet.add(new MinistryAssignment(portfolio.id(), electedProfession, efficiency));
            }
        }

        return new Empire(
                empire.id(),
                empire.name(),
                empire.raceId(),
                empire.societyStructure(),
                empire.treasuryCredits(),
                empire.corporateTaxRate(),
                empire.controlledSystemIds(),
                newCabinet,
                empire.systemGovernorAssignments(),
                empire.unlockedTechIds(),
                empire.activeShipDesignIds()
        );
    }

    /**
     * Elects a democratic system governor for a solar system based on critical regional resource shortages.
     *
     * @param systemId   the solar system ID
     * @param shortages  resource shortage metrics in the system
     * @return elected system governor record
     */
    public SystemGovernor electSystemGovernor(String systemId, Map<String, Double> shortages) {
        String electedProfession = "bureaucrat";
        double highestShortage = 0.0;

        if (shortages != null) {
            for (Map.Entry<String, Double> entry : shortages.entrySet()) {
                if (entry.getValue() > highestShortage && entry.getValue() > 0.20) {
                    highestShortage = entry.getValue();
                    String key = entry.getKey().toLowerCase();
                    if (key.contains("food") || key.contains("grain") || key.contains("nutrient")) {
                        electedProfession = "farmer";
                    } else if (key.contains("ore") || key.contains("mineral") || key.contains("iron") || key.contains("silicon")) {
                        electedProfession = "miner";
                    } else if (key.contains("tech") || key.contains("science")) {
                        electedProfession = "scientist";
                    } else if (key.contains("defense") || key.contains("pirate") || key.contains("war")) {
                        electedProfession = "soldier";
                    }
                }
            }
        }

        return new SystemGovernor(
                "gov_" + systemId + "_" + electedProfession,
                "Elected Governor (" + electedProfession + ")",
                systemId,
                electedProfession,
                1.15,
                0.20
        );
    }

    /**
     * Directly appoints a minister in an autocratic (collectivist) government.
     *
     * @param empire       the empire
     * @param portfolioId  portfolio post to assign
     * @param professionId appointed citizen profession
     * @param portfolios   configured portfolios
     * @return updated empire
     */
    public Empire appointMinister(
            Empire empire,
            String portfolioId,
            String professionId,
            List<MinistryPortfolio> portfolios
    ) {
        if (empire == null) return null;
        if ("Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure())) {
            return empire;
        }

        List<MinistryAssignment> cabinet = new ArrayList<>(empire.ministries());
        cabinet.removeIf(m -> m.portfolioId().equals(portfolioId));

        double efficiency = governanceProcessor.calculateMinisterEfficiency(
                portfolioId,
                professionId,
                portfolios,
                false
        );
        cabinet.add(new MinistryAssignment(portfolioId, professionId, efficiency));

        return new Empire(
                empire.id(),
                empire.name(),
                empire.raceId(),
                empire.societyStructure(),
                empire.treasuryCredits(),
                empire.corporateTaxRate(),
                empire.controlledSystemIds(),
                cabinet,
                empire.systemGovernorAssignments(),
                empire.unlockedTechIds(),
                empire.activeShipDesignIds()
        );
    }

    /**
     * Directly appoints a system governor in an autocratic (collectivist) government.
     *
     * @param empire       the empire
     * @param systemId     solar system ID
     * @param professionId appointed governor profession background
     * @return created SystemGovernor
     */
    public SystemGovernor appointSystemGovernor(Empire empire, String systemId, String professionId) {
        if (empire == null || "Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure())) {
            return null;
        }

        return new SystemGovernor(
                "gov_" + systemId + "_" + professionId,
                "Appointed Governor (" + professionId + ")",
                systemId,
                professionId,
                1.15,
                0.20
        );
    }

    private String selectElectedProfession(MinistryPortfolio portfolio, Map<String, Double> shortages) {
        if (shortages != null && !shortages.isEmpty()) {
            if (portfolio.id().contains("agricultural") && shortages.getOrDefault("food", 0.0) > 0.3) {
                return "farmer";
            }
            if (portfolio.id().contains("industry") && (shortages.getOrDefault("silicon", 0.0) > 0.3 || shortages.getOrDefault("iron", 0.0) > 0.3)) {
                return "miner";
            }
            if (portfolio.id().contains("technology") && shortages.getOrDefault("technology", 0.0) > 0.3) {
                return "scientist";
            }
            if (portfolio.id().contains("defense") && shortages.getOrDefault("defense", 0.0) > 0.3) {
                return "soldier";
            }
        }
        // If optimal professions exist, pick the primary optimal or bureaucrat
        return portfolio.optimalProfessionIds().isEmpty() ? "bureaucrat" : portfolio.optimalProfessionIds().getFirst();
    }
}
