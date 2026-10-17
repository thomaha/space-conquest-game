package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import com.spaceconquest.engine.ship.*;

/** Replacement values use captured equipment and current construction inputs, never named factory tables. */
public final class BusinessAssets {
    private BusinessAssets() {}
    public static double facilityValue(GameState state, IndustrialFacility facility) {
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(facility.planetId())).findFirst().orElse(null);
        return FacilitySupplyPricing.capital(state, hub, facility);
    }
    public static boolean carrier(GameState state, ShipInstance ship) {
        var design = FleetSupplySimulation.design(state, ship);
        return design != null && ShipRole.CARGO_TRANSPORT.equals(design.role());
    }
    public static double shipValue(GameState state, Fleet fleet, ShipInstance ship) {
        var design = FleetSupplySimulation.design(state, ship);
        if (design == null) return 0;
        var hub = state.commercialHubs().stream().filter(h -> FleetPositioning.atHub(state, fleet, h)).findFirst().orElse(null);
        return ShipConstructionRequirements.estimate(design).materialsKg().entrySet().stream().mapToDouble(e -> e.getValue()
                * (hub != null && hub.activeOrders().containsKey(e.getKey()) ? hub.activeOrders().get(e.getKey()).pricePerKg()
                : MarketProcessor.basePricePerKg(e.getKey()))).sum();
    }
    public static double ownerValue(GameState state, String owner) {
        double factories = state.industrialFacilities().stream().filter(f -> f.ownerEntityId().equals(owner)
                && !IndustrialFacility.HIVE_GRID.equals(f.ownershipType())).mapToDouble(f -> facilityValue(state, f)).sum();
        return factories + state.fleets().stream().filter(f -> f.ownerEntityId().equals(owner)).mapToDouble(f -> f.ships().stream()
                .filter(s -> carrier(state, s)).mapToDouble(s -> shipValue(state, f, s)).sum()).sum();
    }
}
