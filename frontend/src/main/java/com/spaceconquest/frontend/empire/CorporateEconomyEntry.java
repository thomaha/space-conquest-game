package com.spaceconquest.frontend.empire;

public record CorporateEconomyEntry(
        String corporationId,
        String corporationName,
        String hqEntityId,
        String marketOrientation,
        double liquidCapital,
        int ownedFacilitiesCount,
        double profitTaxPaid
) {}
