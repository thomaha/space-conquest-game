package com.spaceconquest.engine;

import com.spaceconquest.engine.industry.IndustryAccount;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedEconomyLongHorizonTest {
    @Test
    void fourMonthBalanceAuditTracksLiveSupplyAndNonnegativeInventory() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.PRE_SPACE_FLIGHT);
        GameState opening = engine.getGameState();
        String home = opening.solarSystems().getFirst().planets().stream()
                .filter(planet -> !planet.populations().isEmpty()).findFirst().orElseThrow().id();
        CommercialHub openingHub = opening.commercialHubs().stream()
                .filter(hub -> home.equals(hub.entityId())).findFirst().orElseThrow();
        double openingNitrates = openingHub.activeOrders().get("nitrates").supplyKg();
        double openingWater = openingHub.activeOrders().get("purified_water").supplyKg();
        double minedNitrates = 0.0;
        double treatedWater = 0.0;
        double minimumBasicCoverage = 1.0;
        double minimumSecondaryCoverage = 1.0;
        boolean shortageReported = false;
        for (int day = 1; day <= 120; day++) {
            engine.stepTurn();
            GameState state = engine.getGameState();
            for (CommercialHub hub : state.commercialHubs()) {
                double inventory = hub.activeOrders().values().stream()
                        .mapToDouble(MarketOrder::supplyKg).sum();
                assertEquals(inventory, hub.currentStoredWeightKg(),
                        Math.max(0.01, inventory * 0.000001), "day " + day + " hub " + hub.id());
                assertTrue(inventory >= -0.001 && inventory <= hub.storageCapacityKg() + 0.001);
                for (MarketOrder order : hub.activeOrders().values()) {
                    assertTrue(Double.isFinite(order.supplyKg()) && order.supplyKg() >= -0.001);
                }
            }
            for (var deposit : state.geologicalDeposits()) {
                assertTrue(deposit.remainingVolumeKg() >= -0.001);
                assertTrue(deposit.remainingVolumeKg() <= deposit.initialVolumeKg() + 0.001);
            }
            Map<String, IndustrialFacilityRef> facilities = state.industrialFacilities().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            facility -> facility.id(), facility -> new IndustrialFacilityRef(
                                    facility.planetId(), facility.applicationId())));
            for (IndustryAccount account : state.industryAccounts()) {
                IndustrialFacilityRef facility = facilities.get(account.facilityId());
                if (facility == null || !home.equals(facility.bodyId())) continue;
                if ("nitrates_mining".equals(facility.applicationId())) {
                    minedNitrates += account.producedKg().getOrDefault("nitrates", 0.0);
                }
                if ("surface_water_treatment".equals(facility.applicationId())) {
                    treatedWater += account.producedKg().getOrDefault("purified_water", 0.0);
                }
            }
            for (var household : state.householdAccounts()) {
                if (!home.equals(household.bodyId()) || household.headcount() <= 0) continue;
                if (!household.unmetBasicKg().isEmpty()) minimumBasicCoverage = 0.0;
                minimumSecondaryCoverage = Math.min(minimumSecondaryCoverage,
                        household.secondaryNeedsMetFraction());
                if (!shortageReported && (!household.unmetBasicKg().isEmpty()
                        || household.secondaryNeedsMetFraction() < 0.999)) {
                    double foodStock = state.commercialHubs().stream()
                            .filter(hub -> home.equals(hub.entityId())).findFirst().orElseThrow()
                            .activeOrders().get("food_matrix").supplyKg();
                    System.out.printf("First need shortfall day %d household %s basic %s secondary %.3f "
                                    + "savings %.0f wage %.0f welfare %.0f food stock %.0f%n",
                            day, household.key(), household.unmetBasicKg(),
                            household.secondaryNeedsMetFraction(), household.savingsCredits(),
                            household.wageIncomeCredits(), household.welfareIncomeCredits(), foodStock);
                    shortageReported = true;
                }
            }
        }
        assertTrue(minedNitrates > openingNitrates,
                "The homeworld must produce more nitrates than its opening reserve");
        assertTrue(treatedWater > openingWater,
                "The homeworld must purify more water than its opening reserve");
        assertEquals(1.0, minimumBasicCoverage, 0.001,
                "Basic needs should remain funded and supplied through the four-month audit");
        System.out.printf("Four-month homeworld audit: basic coverage floor %.2f, secondary floor %.2f, "
                + "mined nitrates %.0f kg, treated water %.0f kg%n",
                minimumBasicCoverage, minimumSecondaryCoverage, minedNitrates, treatedWater);
    }

    private record IndustrialFacilityRef(String bodyId, String applicationId) {}
}
