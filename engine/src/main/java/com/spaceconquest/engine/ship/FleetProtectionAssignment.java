package com.spaceconquest.engine.ship;

/** Saved original protection and recovery intent, carried by an automatic intercept order. */
public record FleetProtectionAssignment(FleetGuardOrder guard, String convoyFleetId, String homeFleetId,
                                        FleetLocation.Site origin, Stage stage, String serviceTargetId, boolean escaped,
                                        FleetRepairIntent repairIntent, boolean weaponService) {
    public enum Stage { PURSUING, RETURNING, REFUELING, SUPPLYING, REPAIRING, RESUPPLYING }
    public FleetProtectionAssignment(FleetGuardOrder guard, String convoyFleetId, String homeFleetId,
                                     FleetLocation.Site origin, Stage stage, String serviceTargetId, boolean escaped,
                                     FleetRepairIntent repairIntent) {
        this(guard, convoyFleetId, homeFleetId, origin, stage, serviceTargetId, escaped, repairIntent, false);
    }
    public FleetProtectionAssignment(FleetGuardOrder guard, String convoyFleetId, String homeFleetId,
                                     FleetLocation.Site origin, Stage stage, String serviceTargetId, boolean escaped) {
        this(guard, convoyFleetId, homeFleetId, origin, stage, serviceTargetId, escaped, null);
    }
    public FleetProtectionAssignment(FleetGuardOrder guard, String convoyFleetId, String homeFleetId,
                                     FleetLocation.Site origin, Stage stage, String serviceTargetId) {
        this(guard, convoyFleetId, homeFleetId, origin, stage, serviceTargetId, false);
    }
    public FleetProtectionAssignment {
        if (homeFleetId == null || homeFleetId.isBlank() || origin == null || stage == null)
            throw new IllegalArgumentException("Protection recovery requires its original fleet and site");
        convoyFleetId = convoyFleetId == null ? "" : convoyFleetId;
        serviceTargetId = serviceTargetId == null ? "" : serviceTargetId;
    }
    public FleetProtectionAssignment at(Stage value, String target) {
        return new FleetProtectionAssignment(guard, convoyFleetId, homeFleetId, origin, value, target, escaped, repairIntent, weaponService);
    }
    public FleetProtectionAssignment afterEscape() {
        return new FleetProtectionAssignment(guard, convoyFleetId, homeFleetId, origin, Stage.RETURNING, "", true, repairIntent, weaponService);
    }
    public FleetProtectionAssignment withRepairIntent(FleetRepairIntent value) {
        return new FleetProtectionAssignment(value == null ? guard : value.guard(), convoyFleetId, homeFleetId, origin,
                stage, serviceTargetId, escaped, value, weaponService);
    }
    public FleetProtectionAssignment forWeapons() {
        return new FleetProtectionAssignment(guard, convoyFleetId, homeFleetId, origin, stage,
                serviceTargetId, escaped, repairIntent, true);
    }
}
