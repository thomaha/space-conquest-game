package com.spaceconquest.engine.governance;

import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.Race;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates territorial influence value (I_v), manages bilateral diplomatic relation tiers,
 * and enforces interstellar trade barriers and xenobiological nutrient compatibility rules.
 */
public class DiplomacyProcessor {

    public static final String TOTAL_WAR = "TOTAL_WAR";
    public static final String COLD_WAR = "COLD_WAR";
    public static final String NEUTRAL = "NEUTRAL";
    public static final String COMMERCIAL_ALLIANCE = "COMMERCIAL_ALLIANCE";
    public static final String INTEGRATED_FEDERATION = "INTEGRATED_FEDERATION";

    /**
     * Calculates the Influence Value (I_v) projected by a populated colony or solar system.
     * Formula: I_v = (log10(Total System Population) * Module Development Factor) + Military Hangar Modifier
     * Systems without population project 0 influence.
     *
     * @param totalPopulation        total population count across all entities in system
     * @param moduleDevelopmentFactor development level multiplier (e.g. 1.0 to 3.0)
     * @param militaryHangarModifier  military fleet presence modifier
     * @return calculated territorial influence value
     */
    public double calculateInfluenceValue(
            long totalPopulation,
            double moduleDevelopmentFactor,
            double militaryHangarModifier
    ) {
        if (totalPopulation <= 0) {
            return 0.0;
        }
        double popLog = Math.log10(Math.max(1.0, (double) totalPopulation));
        double factor = moduleDevelopmentFactor > 0 ? moduleDevelopmentFactor : 1.0;
        return (popLog * factor) + Math.max(0.0, militaryHangarModifier);
    }

    /**
     * Returns the default mutual tariff discount associated with a diplomatic tier.
     *
     * @param tier diplomatic tier
     * @return tariff discount (0.0 to 1.0)
     */
    public double getDefaultTariffDiscount(String tier) {
        if (tier == null) return 0.0;
        return switch (tier.toUpperCase()) {
            case COMMERCIAL_ALLIANCE -> 0.50;
            case INTEGRATED_FEDERATION -> 1.00;
            default -> 0.00;
        };
    }

    /**
     * Sets or transitions the diplomatic tier between two empires.
     *
     * @param empireAId        first empire ID
     * @param empireBId        second empire ID
     * @param newTier          target diplomatic tier
     * @param currentRelations existing relations list
     * @return updated list of diplomatic relations
     */
    public List<DiplomaticRelation> setDiplomaticTier(
            String empireAId,
            String empireBId,
            String newTier,
            List<DiplomaticRelation> currentRelations
    ) {
        List<DiplomaticRelation> updated = new ArrayList<>(currentRelations != null ? currentRelations : List.of());
        updated.removeIf(r -> (r.empireAId().equals(empireAId) && r.empireBId().equals(empireBId))
                || (r.empireAId().equals(empireBId) && r.empireBId().equals(empireAId)));

        double discount = getDefaultTariffDiscount(newTier);
        updated.add(new DiplomaticRelation(empireAId, empireBId, newTier.toUpperCase(), discount));
        return updated;
    }

    /**
     * Finds the bilateral diplomatic tier between two empires (defaults to NEUTRAL if not specified).
     *
     * @param empireAId        first empire ID
     * @param empireBId        second empire ID
     * @param currentRelations existing relations list
     * @return active diplomatic tier string
     */
    public String getDiplomaticTier(String empireAId, String empireBId, List<DiplomaticRelation> currentRelations) {
        if (empireAId == null || empireBId == null || empireAId.equals(empireBId)) {
            return INTEGRATED_FEDERATION;
        }
        if (currentRelations != null) {
            for (DiplomaticRelation relation : currentRelations) {
                if ((relation.empireAId().equals(empireAId) && relation.empireBId().equals(empireBId))
                        || (relation.empireAId().equals(empireBId) && relation.empireBId().equals(empireAId))) {
                    return relation.tier();
                }
            }
        }
        return NEUTRAL;
    }

    /**
     * Checks if cross-border trade of a specific resource is biologically compatible between two species.
     * Non-food commodities trade freely. Food and biological nutrients require matching nutrient types
     * unless a Xenobiology Lab is present on the transit corridor.
     *
     * @param resourceId        material or commodity ID being traded
     * @param exporterRace      race of exporting empire
     * @param importerRace      race of importing empire
     * @param hasXenobiologyLab whether a Xenobiology Lab space station module is active
     * @return true if trade is permitted, false if blocked by nutrient incompatibility
     */
    public boolean isTradePermitted(
            String resourceId,
            Race exporterRace,
            Race importerRace,
            boolean hasXenobiologyLab
    ) {
        if (resourceId == null) return false;
        String r = resourceId.toLowerCase();

        boolean isNutrient = r.contains("food") || r.contains("nutrient") || r.contains("grain")
                || r.contains("protein") || r.contains("meat") || r.contains("organics");

        if (!isNutrient) {
            // Raw inorganic elements, alloys, and components trade freely
            return true;
        }

        if (exporterRace == null || importerRace == null) {
            return false;
        }

        if (hasXenobiologyLab) {
            // Xenobiology lab converts incompatible biological nutrients safely
            return true;
        }

        return exporterRace.nutrientType().equalsIgnoreCase(importerRace.nutrientType());
    }

    public record WarDeclarationResult(
            boolean isJustified,
            double civilianHappinessPenalty,
            double corporateTrustPenalty,
            String justificationSummary
    ) {}

    /**
     * Evaluates the domestic and corporate stability impact of declaring war on a foreign empire.
     */
    public WarDeclarationResult evaluateWarDeclarationImpact(
            com.spaceconquest.engine.Empire initiator,
            com.spaceconquest.engine.Empire target,
            List<CasusBelli> casusBelliList,
            List<DiplomaticPact> activePacts
    ) {
        if (initiator == null || target == null) {
            return new WarDeclarationResult(false, 0.0, 0.0, "Invalid empire references");
        }

        boolean hasJustification = false;
        String reason = "Unprovoked aggression";

        if (casusBelliList != null) {
            for (CasusBelli cb : casusBelliList) {
                if (cb.holderEmpireId().equals(initiator.id())
                        && cb.targetEmpireId().equals(target.id())
                        && !cb.isExpired()) {
                    hasJustification = true;
                    reason = "Justified war: " + cb.justificationType();
                    break;
                }
            }
        }

        double happinessPenalty = 0.0;
        double trustPenalty = 0.0;

        boolean isDemocratic = "Individualist".equalsIgnoreCase(initiator.societyStructure());
        if (isDemocratic && !hasJustification) {
            happinessPenalty = -0.40; // Citizen opinion crashes
        }

        if (activePacts != null) {
            for (DiplomaticPact pact : activePacts) {
                if (pact.isActive() && pact.involves(initiator.id()) && pact.involves(target.id())) {
                    trustPenalty -= 50.0; // Breaking ratified pact drops interstellar trust
                    break;
                }
            }
        }

        return new WarDeclarationResult(hasJustification, happinessPenalty, trustPenalty, reason);
    }

    /**
     * Evaluates an AI empire's decision on accepting or rejecting a proposed diplomatic pact.
     */
    public boolean evaluateProposalAcceptance(
            DiplomaticProposal proposal,
            com.spaceconquest.engine.Empire sender,
            com.spaceconquest.engine.Empire receiver,
            List<DiplomaticRelation> relations,
            double militaryStrengthRatio
    ) {
        if (proposal == null || receiver == null || sender == null) {
            return false;
        }

        // Hive mind refuses all civilian/trade treaties
        boolean isHive = "Hive Mind".equalsIgnoreCase(receiver.societyStructure())
                || "Hive mind".equalsIgnoreCase(receiver.societyStructure());
        if (isHive) {
            return false;
        }

        String currentTier = getDiplomaticTier(sender.id(), receiver.id(), relations);
        if (TOTAL_WAR.equalsIgnoreCase(currentTier)) {
            return false; // Will not negotiate during total war
        }

        // If in commercial alliance or integrated federation, generally accept mutual accords
        if (COMMERCIAL_ALLIANCE.equalsIgnoreCase(currentTier) || INTEGRATED_FEDERATION.equalsIgnoreCase(currentTier)) {
            return true;
        }

        // Neutral state: evaluate based on military balance and type
        if (DiplomaticPact.MUTUAL_TRADE_AGREEMENT.equalsIgnoreCase(proposal.proposalType())) {
            return true; // Favorable to private corporate economy
        }

        if (DiplomaticPact.DEFENSIVE_PACT.equalsIgnoreCase(proposal.proposalType())) {
            return militaryStrengthRatio >= 0.70; // Only ally with reasonably strong partners
        }

        return true;
    }
}
