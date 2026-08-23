package com.spaceconquest.engine.community;

import com.spaceconquest.engine.Empire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates legislative voting cycles, weighted democratic power calculations,
 * resolution enactments and economic sanction enforcement across the Galactic Senate.
 */
public class GalacticCommunityProcessor {

    public record CommunityTurnResult(
            GalacticCommunity updatedCommunity,
            List<GalacticResolution> newlyPassedResolutions,
            List<GalacticSanction> newlyEnactedSanctions,
            Map<String, Double> votingWeights
    ) {}

    /**
     * Computes the democratic voting weight for an empire based on diplomatic influence, GDP and fleet power.
     */
    public double calculateVotingWeight(Empire empire, double economicGdp, double navalFleetPower) {
        if (empire == null) return 1.0;
        double baseWeight = 100.0;
        double gdpComponent = Math.max(0.0, economicGdp) * 0.001;
        double fleetComponent = Math.max(0.0, navalFleetPower) * 0.01;
        return Math.round((baseWeight + gdpComponent + fleetComponent) * 10.0) / 10.0;
    }

    /**
     * Advances senate legislative sessions, tallies resolution votes and manages sanction lifecycles.
     */
    public CommunityTurnResult processSenateSession(
            GalacticCommunity community,
            List<Empire> empires,
            Map<String, Double> empireGdpMap,
            Map<String, Double> fleetPowerMap,
            long currentTurn
    ) {
        if (community == null) {
            return new CommunityTurnResult(null, List.of(), List.of(), Map.of());
        }

        Map<String, Double> weights = new HashMap<>();
        for (Empire emp : empires) {
            double gdp = empireGdpMap != null ? empireGdpMap.getOrDefault(emp.id(), 10000.0) : 10000.0;
            double fleet = fleetPowerMap != null ? fleetPowerMap.getOrDefault(emp.id(), 500.0) : 500.0;
            weights.put(emp.id(), calculateVotingWeight(emp, gdp, fleet));
        }

        List<GalacticResolution> remainingActive = new ArrayList<>();
        List<GalacticResolution> passedList = new ArrayList<>(community.passedResolutions());
        List<GalacticResolution> newlyPassed = new ArrayList<>();
        List<GalacticSanction> activeSanctions = new ArrayList<>(community.activeSanctions());
        List<GalacticSanction> newlyEnacted = new ArrayList<>();

        // 1. Process active resolutions
        for (GalacticResolution res : community.activeResolutions()) {
            int turnsLeft = res.sessionTurnsLeft() - 1;
            if (turnsLeft <= 0) {
                // Tally votes
                double ayeWeight = 0.0;
                double nayWeight = 0.0;

                for (String memberId : community.memberEmpireIds()) {
                    String vote = res.votes().getOrDefault(memberId, GalacticResolution.VOTE_ABSTAIN);
                    double w = weights.getOrDefault(memberId, 100.0);
                    if (GalacticResolution.VOTE_AYE.equalsIgnoreCase(vote)) {
                        ayeWeight += w;
                    } else if (GalacticResolution.VOTE_NAY.equalsIgnoreCase(vote)) {
                        nayWeight += w;
                    }
                }

                if (ayeWeight > nayWeight) {
                    GalacticResolution passedRes = new GalacticResolution(
                            res.id(), res.title(), res.type(), res.proposerEmpireId(),
                            res.targetEmpireId(), 0, GalacticResolution.STATUS_PASSED, res.votes()
                    );
                    passedList.add(passedRes);
                    newlyPassed.add(passedRes);

                    // Check if resolution spawns a sanction
                    if (GalacticResolution.TYPE_SANCTION_EMBARGO.equalsIgnoreCase(res.type())) {
                        GalacticSanction sanction = new GalacticSanction(
                                "sanction_embargo_" + res.targetEmpireId() + "_" + currentTurn,
                                res.targetEmpireId(), GalacticSanction.TYPE_TRADE_EMBARGO,
                                res.id(), 0.50, false, false, 20
                        );
                        activeSanctions.add(sanction);
                        newlyEnacted.add(sanction);
                    } else if (GalacticResolution.TYPE_SANCTION_FREEZE.equalsIgnoreCase(res.type())) {
                        GalacticSanction sanction = new GalacticSanction(
                                "sanction_freeze_" + res.targetEmpireId() + "_" + currentTurn,
                                res.targetEmpireId(), GalacticSanction.TYPE_ASSET_FREEZE,
                                res.id(), 0.25, true, false, 15
                        );
                        activeSanctions.add(sanction);
                        newlyEnacted.add(sanction);
                    } else if (GalacticResolution.TYPE_MILITARY_INTERVENTION.equalsIgnoreCase(res.type())) {
                        GalacticSanction sanction = new GalacticSanction(
                                "sanction_intervention_" + res.targetEmpireId() + "_" + currentTurn,
                                res.targetEmpireId(), GalacticSanction.TYPE_MILITARY_INTERVENTION,
                                res.id(), 0.0, false, true, 30
                        );
                        activeSanctions.add(sanction);
                        newlyEnacted.add(sanction);
                    }
                }
            } else {
                remainingActive.add(new GalacticResolution(
                        res.id(), res.title(), res.type(), res.proposerEmpireId(),
                        res.targetEmpireId(), turnsLeft, res.status(), res.votes()
                ));
            }
        }

        // 2. Decrement and filter sanctions
        List<GalacticSanction> remainingSanctions = new ArrayList<>();
        for (GalacticSanction s : activeSanctions) {
            if (s.turnsRemaining() > 1) {
                remainingSanctions.add(new GalacticSanction(
                        s.id(), s.targetEmpireId(), s.sanctionType(),
                        s.resolutionId(), s.tradeTariffPenaltyRate(),
                        s.isAssetFreezeActive(), s.isMilitaryInterventionAuthorized(),
                        s.turnsRemaining() - 1
                ));
            } else if (s.turnsRemaining() < 0) {
                // Permanent until repealed
                remainingSanctions.add(s);
            }
        }

        long nextSession = community.nextSenateSessionTurn();
        if (currentTurn >= nextSession) {
            nextSession = currentTurn + community.senateSessionInterval();
        }

        GalacticCommunity updatedCommunity = new GalacticCommunity(
                community.id(), community.name(), community.memberEmpireIds(),
                remainingActive, passedList, remainingSanctions,
                community.senateSessionInterval(), nextSession
        );

        return new CommunityTurnResult(updatedCommunity, newlyPassed, newlyEnacted, weights);
    }
}
