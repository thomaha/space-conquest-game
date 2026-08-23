package com.spaceconquest.engine.governance;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.MinistryPortfolio;
import com.spaceconquest.engine.SystemGovernor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes imperial cabinet ministerial synergies, profession background bonuses,
 * and system governor localized modifiers across solar systems.
 */
public class GovernanceProcessor {

    public static final double DEFAULT_SYNERGY_MODIFIER = 1.25;
    public static final double DEFAULT_BUREAUCRAT_BASE_MODIFIER = 1.10;
    public static final double DEFAULT_BASELINE_MODIFIER = 1.00;

    /**
     * Calculates the efficiency multiplier for a minister assigned to a portfolio.
     *
     * @param portfolioId  identifier of the ministry portfolio
     * @param professionId profession background of the assigned minister
     * @param portfolios   available ministry portfolio definitions
     * @param isHiveMind   whether the empire is a Hive Mind (bypasses bonuses, returns 1.0)
     * @return calculated efficiency multiplier
     */
    public double calculateMinisterEfficiency(
            String portfolioId,
            String professionId,
            List<MinistryPortfolio> portfolios,
            boolean isHiveMind
    ) {
        if (isHiveMind) {
            return DEFAULT_BASELINE_MODIFIER;
        }
        if (portfolioId == null || professionId == null) {
            return DEFAULT_BASELINE_MODIFIER;
        }

        MinistryPortfolio portfolio = portfolios != null
                ? portfolios.stream().filter(p -> p.id().equals(portfolioId)).findFirst().orElse(null)
                : null;

        if (portfolio == null) {
            if ("bureaucrat".equalsIgnoreCase(professionId)) {
                return DEFAULT_BUREAUCRAT_BASE_MODIFIER;
            }
            return DEFAULT_BASELINE_MODIFIER;
        }

        if (portfolio.optimalProfessionIds().stream().anyMatch(id -> id.equalsIgnoreCase(professionId))) {
            return portfolio.synergyEfficiencyModifier() > 0 ? portfolio.synergyEfficiencyModifier() : DEFAULT_SYNERGY_MODIFIER;
        }

        if ("bureaucrat".equalsIgnoreCase(professionId)) {
            return portfolio.baseEfficiencyModifier() > 0 ? portfolio.baseEfficiencyModifier() : DEFAULT_BUREAUCRAT_BASE_MODIFIER;
        }

        return DEFAULT_BASELINE_MODIFIER;
    }

    /**
     * Calculates localized industrial and economic efficiency bonus from a system governor.
     *
     * @param governor       the appointed system governor
     * @param systemIndustry dominant industry focus of the solar system (e.g. "MINING", "AGRICULTURE", "TECH")
     * @param isHiveMind     whether the empire is a Hive Mind (bypasses governors)
     * @return efficiency multiplier
     */
    public double calculateGovernorEfficiency(SystemGovernor governor, String systemIndustry, boolean isHiveMind) {
        if (isHiveMind || governor == null) {
            return DEFAULT_BASELINE_MODIFIER;
        }
        if (systemIndustry == null || governor.professionId() == null) {
            return DEFAULT_BASELINE_MODIFIER;
        }

        boolean matches = switch (systemIndustry.toUpperCase()) {
            case "MINING", "EXTRACTION" -> "miner".equalsIgnoreCase(governor.professionId()) || "industrial_worker".equalsIgnoreCase(governor.professionId());
            case "AGRICULTURE", "FARMING" -> "farmer".equalsIgnoreCase(governor.professionId());
            case "TECHNOLOGY", "RESEARCH" -> "scientist".equalsIgnoreCase(governor.professionId());
            case "DEFENSE", "MILITARY" -> "soldier".equalsIgnoreCase(governor.professionId());
            default -> "bureaucrat".equalsIgnoreCase(governor.professionId());
        };

        if (matches) {
            return governor.efficiencyBonus() > 0 ? governor.efficiencyBonus() : 1.15;
        }
        return DEFAULT_BASELINE_MODIFIER;
    }

    /**
     * Updates an empire's ministerial assignments with recalculated efficiency scores.
     *
     * @param empire     the sovereign empire
     * @param portfolios configured ministry portfolios
     * @return updated empire with refreshed ministry efficiency values
     */
    public Empire updateEmpireCabinet(Empire empire, List<MinistryPortfolio> portfolios) {
        if (empire == null) return null;

        boolean isHiveMind = "Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure());

        if (isHiveMind) {
            return new Empire(
                    empire.id(),
                    empire.name(),
                    empire.raceId(),
                    empire.societyStructure(),
                    empire.treasuryCredits(),
                    empire.corporateTaxRate(),
                    empire.controlledSystemIds(),
                    List.of(),
                    Map.of(),
                    empire.unlockedTechIds(),
                    empire.activeShipDesignIds()
            );
        }

        List<MinistryAssignment> updated = new ArrayList<>();
        for (MinistryAssignment assignment : empire.ministries()) {
            double efficiency = calculateMinisterEfficiency(
                    assignment.portfolioId(),
                    assignment.assignedCitizenProfessionId(),
                    portfolios,
                    false
            );
            updated.add(new MinistryAssignment(
                    assignment.portfolioId(),
                    assignment.assignedCitizenProfessionId(),
                    efficiency
            ));
        }

        return new Empire(
                empire.id(),
                empire.name(),
                empire.raceId(),
                empire.societyStructure(),
                empire.treasuryCredits(),
                empire.corporateTaxRate(),
                empire.controlledSystemIds(),
                updated,
                empire.systemGovernorAssignments(),
                empire.unlockedTechIds(),
                empire.activeShipDesignIds()
        );
    }
}
