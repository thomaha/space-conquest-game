package com.spaceconquest.engine.industry;

/** Captured installed capacity and current operator proficiency; neither measures task work in hours. */
public record ProductionEquipment(String id, String processType, double workUnitsPerDay,
                                  int referenceOperators, String operatorProfession,
                                  double autonomousFraction, double operatorSkill, int parallelLines) {
    public ProductionEquipment(String id, String processType, double workUnitsPerDay, int referenceOperators,
                               String operatorProfession, double autonomousFraction, double operatorSkill) {
        this(id, processType, workUnitsPerDay, referenceOperators, operatorProfession, autonomousFraction, operatorSkill, 1);
    }
    public ProductionEquipment {
        if (id == null || id.isBlank() || processType == null || processType.isBlank()
                || !Double.isFinite(workUnitsPerDay) || workUnitsPerDay <= 0 || referenceOperators <= 0
                || operatorProfession == null || operatorProfession.isBlank()
                || !Double.isFinite(autonomousFraction) || autonomousFraction < 0 || autonomousFraction > 1
                || !Double.isFinite(operatorSkill) || operatorSkill < 0 || operatorSkill > 2
                || parallelLines <= 0 || !Double.isFinite(workUnitsPerDay * parallelLines))
            throw new IllegalArgumentException("Invalid production equipment");
    }

    public double staffedFraction(int operators) {
        return autonomousFraction + (1 - autonomousFraction)
                * Math.clamp(Math.max(0, operators) * operatorSkill / referenceCrew(), 0, 1);
    }
    public double ratedWorkPerDay() { return workUnitsPerDay * parallelLines; }
    public long referenceCrew() { return (long) referenceOperators * parallelLines; }
}
