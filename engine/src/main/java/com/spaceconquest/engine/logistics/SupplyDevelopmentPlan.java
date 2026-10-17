package com.spaceconquest.engine.logistics;

/** Saved public procurement intent. Buyer funding is transferred once; ordinary assets retain their own accounts. */
public record SupplyDevelopmentPlan(String id, String empireId, String sourceHubId, String destinationHubId,
                                    String materialId, double quantityKg, double buyerFundingCredits, long createdTurn,
                                    String fleetId, boolean closed) {
    public SupplyDevelopmentPlan {
        if (id == null || id.isBlank() || empireId == null || sourceHubId == null || destinationHubId == null
                || sourceHubId.equals(destinationHubId) || materialId == null || materialId.isBlank()
                || !Double.isFinite(quantityKg) || quantityKg <= 0 || !Double.isFinite(buyerFundingCredits) || buyerFundingCredits < 0
                || createdTurn < 0 || fleetId == null) throw new IllegalArgumentException("Invalid supply development plan");
    }
    public String routeId() { return "development-" + id; }
    public SupplyDevelopmentPlan withFleet(String fleet) {
        return new SupplyDevelopmentPlan(id, empireId, sourceHubId, destinationHubId, materialId, quantityKg, buyerFundingCredits, createdTurn, fleet, closed);
    }
    public SupplyDevelopmentPlan finish() {
        return new SupplyDevelopmentPlan(id, empireId, sourceHubId, destinationHubId, materialId, quantityKg, buyerFundingCredits, createdTurn, fleetId, true);
    }
}
