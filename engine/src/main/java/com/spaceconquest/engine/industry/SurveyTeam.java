package com.spaceconquest.engine.industry;

/** Persistent installed field equipment and cumulative coverage; a completed pass starts another pass. */
public record SurveyTeam(String id, String empireId, String bodyId, String applicationId,
        SurveyCapability capability, double installationRemaining, double progressWork,
        long completedPasses, long lastWorkedTurn, double totalWork, String blocker,
        String fleetId, java.util.Map<String, Long> coveragePasses) {
    public SurveyTeam(String id, String empireId, String bodyId, String applicationId,
            SurveyCapability capability, double installationRemaining, double progressWork,
            long completedPasses, long lastWorkedTurn, double totalWork, String blocker) {
        this(id, empireId, bodyId, applicationId, capability, installationRemaining, progressWork,
                completedPasses, lastWorkedTurn, totalWork, blocker, "", java.util.Map.of());
    }
    public SurveyTeam {
        if (id == null || empireId == null || bodyId == null || applicationId == null || capability == null
                || !Double.isFinite(installationRemaining) || installationRemaining < 0
                || !Double.isFinite(progressWork) || progressWork < 0 || progressWork >= capability.workPerPass()
                || completedPasses < 0 || !Double.isFinite(totalWork) || totalWork < 0)
            throw new IllegalArgumentException("Invalid survey team");
        blocker = blocker == null ? "" : blocker;
        fleetId = fleetId == null ? "" : fleetId;
        coveragePasses = coveragePasses == null ? java.util.Map.of() : java.util.Map.copyOf(coveragePasses);
        if (capability.mode() == SurveyCapability.Mode.ORBITAL && fleetId.isBlank()
                || capability.mode() == SurveyCapability.Mode.SURFACE && !fleetId.isBlank()
                || coveragePasses.values().stream().anyMatch(passes -> passes < 0))
            throw new IllegalArgumentException("Invalid survey deployment");
    }
    /** Payroll adapter only. This job never enters the factory production collection. */
    public IndustrialFacility payrollJob() {
        var gear = capability.equipment();
        return new IndustrialFacility(id, bodyId, applicationId, empireId, IndustrialFacility.PUBLIC_STATE,
                1, (int) Math.min(Integer.MAX_VALUE, gear.referenceCrew()), gear.operatorProfession(), false, 0, gear);
    }
}
