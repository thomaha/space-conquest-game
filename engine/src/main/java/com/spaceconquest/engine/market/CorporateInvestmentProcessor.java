package com.spaceconquest.engine.market;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.MarketOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates market shortcomings (S_m) across accessible commercial hubs and directs
 * autonomous corporate capital investments into infrastructure, workforce training, and fleet expansion.
 */
public class CorporateInvestmentProcessor {

    public static final double INFRASTRUCTURE_COST = 8000.0;
    public static final double WORKFORCE_TRAINING_COST = 2500.0;
    public static final double SHIP_PROCUREMENT_COST = 12000.0;

    public static final double SHORTCOMING_INFRASTRUCTURE_THRESHOLD = 0.50;
    public static final double SHORTCOMING_FLEET_THRESHOLD = 0.40;
    public static final double SHORTCOMING_TRAINING_THRESHOLD = 0.30;

    /**
     * Evaluates and applies autonomous corporate investments across all active corporations.
     *
     * @param corporations list of corporations
     * @param hubs         list of active commercial hubs
     * @return updated list of corporations after investments
     */
    public List<Corporation> processCorporateInvestments(List<Corporation> corporations, List<CommercialHub> hubs) {
        if (corporations == null) {
            return List.of();
        }
        if (hubs == null || hubs.isEmpty()) {
            return corporations;
        }

        return corporations.stream()
                .map(corp -> evaluateAndInvest(corp, hubs))
                .toList();
    }

    /**
     * Evaluates market shortcomings for a single corporation and executes appropriate investments.
     *
     * @param corporation the corporation evaluating opportunities
     * @param hubs        accessible commercial hubs
     * @return updated corporation record
     */
    public Corporation evaluateAndInvest(Corporation corporation, List<CommercialHub> hubs) {
        double maxShortcoming = findMaxShortcoming(corporation, hubs);
        double capital = corporation.liquidCapitalReserves();
        List<String> updatedFacilities = new ArrayList<>(corporation.ownedFacilityIds());
        List<String> updatedShips = new ArrayList<>(corporation.ownedShipIds());

        // Check ship procurement
        if (maxShortcoming >= SHORTCOMING_FLEET_THRESHOLD && capital >= SHIP_PROCUREMENT_COST) {
            capital -= SHIP_PROCUREMENT_COST;
            String shipType = "TRANSPORT".equalsIgnoreCase(corporation.marketOrientation())
                    ? "cargo_transport_" + (updatedShips.size() + 1)
                    : "mine_ship_" + (updatedShips.size() + 1);
            updatedShips.add(shipType);
        }

        // Check infrastructure investment
        if (maxShortcoming >= SHORTCOMING_INFRASTRUCTURE_THRESHOLD && capital >= INFRASTRUCTURE_COST) {
            capital -= INFRASTRUCTURE_COST;
            String facilityId = "facility_" + corporation.marketOrientation().toLowerCase() + "_" + (updatedFacilities.size() + 1);
            updatedFacilities.add(facilityId);
        }

        // Check workforce training
        if (maxShortcoming >= SHORTCOMING_TRAINING_THRESHOLD && capital >= WORKFORCE_TRAINING_COST) {
            capital -= WORKFORCE_TRAINING_COST;
        }

        return new Corporation(
                corporation.id(),
                corporation.name(),
                corporation.empireId(),
                corporation.headquartersEntityId(),
                corporation.marketOrientation(),
                capital,
                updatedFacilities,
                updatedShips,
                corporation.claimedVeinIds()
        );
    }

    /**
     * Finds the maximum shortcoming score for resources matching the corporation's orientation.
     *
     * @param corporation the corporation
     * @param hubs        accessible commercial hubs
     * @return highest shortcoming score found
     */
    public double findMaxShortcoming(Corporation corporation, List<CommercialHub> hubs) {
        double max = 0.0;
        for (CommercialHub hub : hubs) {
            for (MarketOrder order : hub.activeOrders().values()) {
                if (matchesOrientation(corporation.marketOrientation(), order.resourceId())) {
                    if (order.shortcomingScore() > max) {
                        max = order.shortcomingScore();
                    }
                }
            }
        }
        return max;
    }

    private boolean matchesOrientation(String orientation, String resourceId) {
        if (orientation == null || resourceId == null) {
            return true;
        }
        String o = orientation.toUpperCase();
        String r = resourceId.toLowerCase();
        return switch (o) {
            case "EXTRACTION" -> r.contains("ore") || r.contains("mineral") || r.contains("raw") || r.contains("silicon") || r.contains("iron");
            case "METALLURGY" -> r.contains("alloy") || r.contains("steel") || r.contains("refined") || r.contains("plate") || r.contains("silicon");
            case "AGRICULTURE" -> r.contains("food") || r.contains("grain") || r.contains("protein") || r.contains("nutrient");
            case "TRANSPORT" -> true;
            case "CONSUMER" -> r.contains("goods") || r.contains("consumer") || r.contains("electronics");
            default -> true;
        };
    }
}
