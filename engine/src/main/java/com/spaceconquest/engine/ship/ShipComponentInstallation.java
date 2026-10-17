package com.spaceconquest.engine.ship;

/** Identity of freely chosen equipment, rather than a predefined hardpoint. */
public record ShipComponentInstallation(String id, ShipModule hardware, String applicationId, ShipProductionReceipt production, double installationWorkHours) {
    public ShipComponentInstallation {
        if (id == null || id.isBlank() || hardware == null || production == null || !Double.isFinite(installationWorkHours) || installationWorkHours < 0)
            throw new IllegalArgumentException("Invalid component installation");
        applicationId = applicationId == null ? "" : applicationId;
    }
}
