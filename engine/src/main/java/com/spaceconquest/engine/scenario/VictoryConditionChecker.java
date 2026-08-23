package com.spaceconquest.engine.scenario;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.community.GalacticResolution;
import com.spaceconquest.engine.megastructure.Megastructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates whether an empire or player has fulfilled scenario victory objectives.
 */
public class VictoryConditionChecker {

    public record VictoryCheckResult(
            boolean isVictoryAchieved,
            String winningEmpireId,
            String victoryConditionType,
            String summary
    ) {}

    /**
     * Checks if any empire in the current GameState has achieved the campaign victory goal.
     */
    public VictoryCheckResult evaluateVictory(
            GameState state,
            CampaignSetup setup,
            GalacticCommunity community,
            List<Megastructure> megastructures
    ) {
        if (state == null || setup == null) {
            return new VictoryCheckResult(false, "", "", "Simulation in progress");
        }

        String condition = setup.victoryConditionType();

        return switch (condition) {
            case CampaignSetup.VICTORY_DOMINATION -> checkDomination(state, setup);
            case CampaignSetup.VICTORY_ECONOMIC_MONOPOLY -> checkEconomicMonopoly(state, setup);
            case CampaignSetup.VICTORY_MEGASTRUCTURE_ASCENSION -> checkMegastructureAscension(state, megastructures);
            case CampaignSetup.VICTORY_DIPLOMATIC_FEDERATION -> checkDiplomaticFederation(state, community);
            default -> new VictoryCheckResult(false, "", condition, "No victory criteria met");
        };
    }

    private VictoryCheckResult checkDomination(GameState state, CampaignSetup setup) {
        Map<String, Integer> populatedPlanetsByEmpire = new HashMap<>();
        int totalPopulated = 0;

        for (SolarSystem sys : state.solarSystems()) {
            for (Planet p : sys.planets()) {
                if (!p.populations().isEmpty()) {
                    totalPopulated++;
                    String race = p.populations().get(0).raceId();
                    String owner = state.empires().stream()
                            .filter(e -> e.raceId().equalsIgnoreCase(race) || e.controlledSystemIds().contains(sys.id()))
                            .map(Empire::id)
                            .findFirst()
                            .orElse("unclaimed");
                    populatedPlanetsByEmpire.merge(owner, 1, Integer::sum);
                }
            }
        }

        if (totalPopulated == 0) {
            return new VictoryCheckResult(false, "", CampaignSetup.VICTORY_DOMINATION, "No colonized planets");
        }

        for (Map.Entry<String, Integer> entry : populatedPlanetsByEmpire.entrySet()) {
            double share = ((double) entry.getValue() / totalPopulated) * 100.0;
            if (share >= setup.targetVictoryThreshold()) {
                return new VictoryCheckResult(
                        true, entry.getKey(), CampaignSetup.VICTORY_DOMINATION,
                        "Empire " + entry.getKey() + " controls " + Math.round(share) + "% of all inhabited worlds."
                );
            }
        }

        return new VictoryCheckResult(false, "", CampaignSetup.VICTORY_DOMINATION, "Domination target not reached");
    }

    private VictoryCheckResult checkEconomicMonopoly(GameState state, CampaignSetup setup) {
        for (Empire emp : state.empires()) {
            if (emp.treasuryCredits() >= setup.targetVictoryThreshold() * 1000.0) {
                return new VictoryCheckResult(
                        true, emp.id(), CampaignSetup.VICTORY_ECONOMIC_MONOPOLY,
                        "Empire " + emp.id() + " accumulated " + emp.treasuryCredits() + " credits in treasury reserves."
                );
            }
        }
        return new VictoryCheckResult(false, "", CampaignSetup.VICTORY_ECONOMIC_MONOPOLY, "Economic reserve target not reached");
    }

    private VictoryCheckResult checkMegastructureAscension(GameState state, List<Megastructure> megastructures) {
        if (megastructures == null || megastructures.isEmpty()) {
            return new VictoryCheckResult(false, "", CampaignSetup.VICTORY_MEGASTRUCTURE_ASCENSION, "No megastructures constructed");
        }

        for (Megastructure mega : megastructures) {
            if (mega.isFullyConstructed() && (
                    Megastructure.TYPE_DYSON_SPHERE.equalsIgnoreCase(mega.type())
                            || Megastructure.TYPE_RINGWORLD.equalsIgnoreCase(mega.type())
            )) {
                return new VictoryCheckResult(
                        true, mega.ownerEmpireId(), CampaignSetup.VICTORY_MEGASTRUCTURE_ASCENSION,
                        "Empire " + mega.ownerEmpireId() + " successfully completed " + mega.name() + "."
                );
            }
        }

        return new VictoryCheckResult(false, "", CampaignSetup.VICTORY_MEGASTRUCTURE_ASCENSION, "Grand megastructure incomplete");
    }

    private VictoryCheckResult checkDiplomaticFederation(GameState state, GalacticCommunity community) {
        if (community == null || state.empires().isEmpty()) {
            return new VictoryCheckResult(false, "", CampaignSetup.VICTORY_DIPLOMATIC_FEDERATION, "Galactic Community not formed");
        }

        long passedCount = community.passedResolutions().stream()
                .filter(r -> GalacticResolution.STATUS_PASSED.equalsIgnoreCase(r.status()))
                .count();

        double memberShare = ((double) community.memberEmpireIds().size() / state.empires().size()) * 100.0;

        if (passedCount >= 3 && memberShare >= 70.0) {
            String leader = community.memberEmpireIds().get(0);
            return new VictoryCheckResult(
                    true, leader, CampaignSetup.VICTORY_DIPLOMATIC_FEDERATION,
                    "Galactic Senate unified " + Math.round(memberShare) + "% of interstellar civilizations with " + passedCount + " enacted charters."
            );
        }

        return new VictoryCheckResult(false, "", CampaignSetup.VICTORY_DIPLOMATIC_FEDERATION, "Diplomatic federation threshold not met");
    }
}
