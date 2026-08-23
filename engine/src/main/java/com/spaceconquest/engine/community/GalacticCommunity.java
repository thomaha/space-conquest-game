package com.spaceconquest.engine.community;

import java.util.List;

/**
 * Represents the pan-galactic interstellar governing assembly, member registry and legislative records.
 *
 * @param id                    unique community identifier
 * @param name                  assembly display title
 * @param memberEmpireIds       list of member empire IDs
 * @param activeResolutions     currently debated resolutions in active voting session
 * @param passedResolutions     enacted permanent or active interstellar laws
 * @param activeSanctions       currently enforced economic and military sanctions
 * @param senateSessionInterval turns between legislative senate sessions
 * @param nextSenateSessionTurn turn number of next scheduled legislative assembly
 */
public record GalacticCommunity(
        String id,
        String name,
        List<String> memberEmpireIds,
        List<GalacticResolution> activeResolutions,
        List<GalacticResolution> passedResolutions,
        List<GalacticSanction> activeSanctions,
        int senateSessionInterval,
        long nextSenateSessionTurn
) {
    public GalacticCommunity {
        if (memberEmpireIds == null) memberEmpireIds = List.of();
        if (activeResolutions == null) activeResolutions = List.of();
        if (passedResolutions == null) passedResolutions = List.of();
        if (activeSanctions == null) activeSanctions = List.of();
        if (senateSessionInterval <= 0) senateSessionInterval = 10;
    }

    public boolean isMember(String empireId) {
        return memberEmpireIds.contains(empireId);
    }

    public boolean isLawActive(String resolutionType) {
        return passedResolutions.stream().anyMatch(r -> r.type().equalsIgnoreCase(resolutionType)
                && GalacticResolution.STATUS_PASSED.equalsIgnoreCase(r.status()));
    }

    public boolean isSanctionActive(String targetEmpireId, String sanctionType) {
        return activeSanctions.stream().anyMatch(s -> s.targetEmpireId().equalsIgnoreCase(targetEmpireId)
                && s.sanctionType().equalsIgnoreCase(sanctionType));
    }
}
