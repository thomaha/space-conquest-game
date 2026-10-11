package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.market.HubStockHistory;
import com.spaceconquest.engine.ship.FleetPositioning;
import com.spaceconquest.engine.ship.ShipRole;
import java.util.*;

/** Read-only stock coverage, without voyage simulation or implied delivery guarantees. */
public final class HubFuelCoverage {
    public record Ingredient(String materialId, double fraction, double onHandKg, double inboundKg,
                             boolean configured, double reorderKg, double targetKg, boolean refillRequested,
                             double requestedKg, double sourceStockKg, HubStockHistory.Average history) {}
    public record Suppliers(int directedRoutes, int roamingRoutes, int unavailableRoutes) {}
    public record Coverage(String fuelId, String label, double usableKg, double positionUsableKg,
                           List<String> limitingIngredients, List<Ingredient> ingredients,
                           Suppliers suppliers, List<String> warnings) {
        public Coverage {
            limitingIngredients = List.copyOf(limitingIngredients);
            ingredients = List.copyOf(ingredients);
            warnings = List.copyOf(warnings);
        }
    }
    private HubFuelCoverage() {}

    public static Coverage inspect(GameState state, CommercialHub hub, HubFuelStockSuggestions.Fuel fuel) {
        var ingredients = fuel.fractions().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(item -> ingredient(state, hub, item.getKey(), item.getValue())).toList();
        double usable = ingredients.stream().mapToDouble(item -> item.onHandKg() / item.fraction()).min().orElse(0);
        double position = ingredients.stream().mapToDouble(item -> (item.onHandKg() + item.inboundKg()) / item.fraction()).min().orElse(0);
        var limiting = ingredients.stream().filter(item -> Math.abs(item.onHandKg() / item.fraction() - usable)
                <= Math.max(1e-6, usable * 1e-9)).map(Ingredient::materialId).toList();
        var suppliers = suppliers(state, hub);
        var warnings = new ArrayList<String>();
        if (usable < 1e-4) warnings.add("No usable fuel on hand. Missing: " + String.join(", ", limiting) + ".");
        for (var item : ingredients) {
            if (item.history().stockoutDays() > 0)
                warnings.add(item.materialId() + ": " + item.history().stockoutDays()
                        + " observed stockout days; measured withdrawals may understate demand.");
            if (item.configured() && item.onHandKg() + 1e-6 < item.reorderKg())
                warnings.add("Below reorder threshold on hand: " + item.materialId() + ".");
            if (item.requestedKg() > 1e-6 && item.sourceStockKg() <= 1e-6)
                warnings.add("No stock at an accessible source hub: " + item.materialId() + ".");
        }
        if (ingredients.stream().anyMatch(item -> item.requestedKg() > 1e-6) && suppliers.directedRoutes() == 0) {
            warnings.add(suppliers.roamingRoutes() == 0 ? "No active supplier route with an assigned cargo ship."
                    : "Only potential roaming suppliers; none is currently directed to this hub.");
        }
        if (suppliers.unavailableRoutes() > 0)
            warnings.add("Some supplier routes are inactive or lack an available cargo ship or source port.");
        return new Coverage(fuel.id(), fuel.label(), usable, position, limiting, ingredients, suppliers, warnings);
    }

    private static Ingredient ingredient(GameState state, CommercialHub hub, String material, double fraction) {
        var order = hub.activeOrders().get(material);
        var target = hub.stockTargets().get(material);
        double source = state.commercialHubs().stream().filter(other -> !other.id().equals(hub.id()) && accessible(state, other))
                .map(other -> other.activeOrders().get(material)).filter(Objects::nonNull)
                .mapToDouble(item -> Math.max(0, item.supplyKg())).sum();
        return new Ingredient(material, fraction, order == null ? 0 : order.supplyKg(),
                HubReplenishment.inboundKg(state, hub, material, null), target != null,
                target == null ? 0 : target.reorderThresholdKg(), order == null && target == null ? 0 : HubReplenishment.targetStockKg(hub, material),
                target != null && target.refillRequested(), HubReplenishment.requestedKg(state, hub, material, null), source,
                hub.stockHistory().average(material, state.turn()));
    }

    private static Suppliers suppliers(GameState state, CommercialHub hub) {
        int directed = 0, roaming = 0, unavailable = 0;
        for (var route : state.tradeRoutes()) {
            if (!route.isActive() && !route.destinationEntityId().equals(hub.id())) continue;
            if (!route.roaming() && (!route.destinationEntityId().equals(hub.id()) || route.originEntityId().equals(hub.id()))) continue;
            var source = state.commercialHubs().stream().filter(item -> item.id().equals(route.originEntityId())).findFirst().orElse(null);
            if (!route.isActive() || source == null || !accessible(state, source) || !hasCarrier(state, route)) {
                unavailable++;
            } else if (route.destinationEntityId().equals(hub.id()) && !route.originEntityId().equals(hub.id())) {
                directed++;
            } else if (route.roaming()) roaming++;
        }
        return new Suppliers(directed, roaming, unavailable);
    }

    private static boolean accessible(GameState state, CommercialHub hub) {
        return FleetPositioning.hubSite(state, hub) != null && state.orbitalStations().stream()
                .noneMatch(station -> station.id().equals(hub.entityId()) && !station.isOperational());
    }

    private static boolean hasCarrier(GameState state, TradeRoute route) {
        if (route.assignedFreighterIds().isEmpty() || state.empires().stream().noneMatch(item -> item.id().equals(route.ownerEntityId()))
                && state.corporations().stream().noneMatch(item -> item.id().equals(route.ownerEntityId()))) return false;
        String assigned = route.assignedFreighterIds().getFirst();
        return state.fleets().stream().filter(fleet -> fleet.ownerEntityId().equals(route.ownerEntityId()))
                .flatMap(fleet -> fleet.ships().stream()).anyMatch(ship -> ship.id().equals(assigned)
                        && ship.ownerEntityId().equals(route.ownerEntityId()) && state.shipDesigns().stream()
                        .anyMatch(design -> design.id().equals(ship.designId()) && ShipRole.CARGO_TRANSPORT.equalsIgnoreCase(design.role())));
    }
}
