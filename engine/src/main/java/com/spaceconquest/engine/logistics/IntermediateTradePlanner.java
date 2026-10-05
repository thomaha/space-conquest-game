package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.Comparator;
import java.util.Locale;

/** Bounded one-stop lookahead for empty traders accessing another parking port on the same body. */
final class IntermediateTradePlanner {
    record Selection(GameState state, TradeRoute route, double profitCredits, double days) {}
    private static final int MAX_PORTS = 8;
    private IntermediateTradePlanner() {}

    static Selection choose(GameState state, TradeRoute route, Fleet fleet) {
        if (!route.roaming() || !route.isActive() || route.onboardKg() > 0 || fleet.ships().size() != 1
                || !route.assignedFreighterIds().contains(fleet.ships().getFirst().id())
                || fleet.ships().getFirst().passengerCount() > 0
                || fleet.ships().getFirst().storedCargoKg().values().stream().anyMatch(kg -> kg > 1e-6)
                || fleet.location().inTransit() || fleet.hasInterstellarOrder() || FleetSupplySimulation.hasOrders(fleet)) return null;
        var source = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.originEntityId())).findFirst().orElse(null);
        if (source == null || !FleetPositioning.atHub(state, fleet, source)) return null;
        var parking = OrbitalTravel.parking(state, fleet.currentSystemId(), fleet.location().current());
        if (parking == null || fleet.location().current().kind() != FleetLocation.Kind.DOCKED) return null;
        var ports = state.commercialHubs().stream().filter(hub -> compatible(state, fleet, source, parking, hub))
                .sorted(Comparator.<CommercialHub>comparingDouble(hub -> Math.abs(
                        OrbitalTravel.parking(state, fleet.currentSystemId(), FleetPositioning.hubSite(state, hub)).altitudeKm()
                                - parking.altitudeKm())).thenComparing(CommercialHub::id)).limit(MAX_PORTS).toList();
        Selection best = null;
        for (var depot : ports) {
            var candidate = evaluate(state, route, fleet, source, depot);
            if (candidate != null && (best == null || candidate.profitCredits() / candidate.days()
                    > best.profitCredits() / best.days())) best = candidate;
        }
        return best;
    }

    private static boolean compatible(GameState state, Fleet fleet, CommercialHub source,
                                       OrbitalTravel.Parking from, CommercialHub hub) {
        if (hub.id().equals(source.id()) || !fleet.currentSystemId().equals(FleetPositioning.systemForHub(state, hub))) return false;
        var site = FleetPositioning.hubSite(state, hub);
        var to = site == null ? null : OrbitalTravel.parking(state, fleet.currentSystemId(), site);
        return site != null && site.kind() == FleetLocation.Kind.DOCKED && to != null
                && from.body().id().equals(to.body().id())
                && state.orbitalStations().stream().anyMatch(station -> station.id().equals(hub.entityId()) && station.isOperational());
    }

    private static Selection evaluate(GameState state, TradeRoute route, Fleet fleet,
                                       CommercialHub source, CommercialHub depot) {
        var paid = TradeLegReadiness.prepare(state, fleet, depot);
        var prepared = find(paid, fleet.id());
        if (!TradeLegReadiness.inspect(paid, prepared, depot).ready()) return null;
        var site = FleetPositioning.hubSite(paid, depot);
        var plan = LocalTravel.plan(paid, prepared, site);
        if (plan == null || plan.orbital() == null || !plan.orbital().parkingTransfer()) return null;
        var quote = RoamingTradeCost.quote(paid, prepared, source, depot);
        if (quote == null || quote.days() > 30) return null;
        var arrival = LocalTravel.arrivalPreview(paid, prepared, site, plan);
        var projected = replace(paid, arrival).withTurn(paid.turn() + (long) quote.days());
        var next = RoamingTradePlanner.choose(projected, route.atNewOrigin(depot.id()), arrival);
        if (next.shipment() == null) return null;
        double profit = next.profitCredits() - quote.fuelCredits() - quote.launchCredits();
        double days = quote.days() + next.days();
        if (!Double.isFinite(profit) || profit <= 1e-6 || !Double.isFinite(days) || days <= 0) return null;
        // Only the current-port supplies and first leg are committed. Remote quotes reserve nothing.
        var moved = replace(paid, LocalTravel.depart(prepared, site, plan));
        var committed = route.withRepositioning(depot.id()).withOperatingCost(Math.max(0,
                TradeShipmentSizing.ownerCash(state, route.ownerEntityId()) - TradeShipmentSizing.ownerCash(paid, route.ownerEntityId())))
                .withStatus(String.format(Locale.ROOT,
                        "Staging via %s for a possible trade to %s: expected combined profit %,.1f credits over %,.0f days. Recheck markets on arrival.",
                        depot.id(), next.shipment().route().destinationEntityId(), profit, days));
        return new Selection(moved, committed, profit, days);
    }

    private static Fleet find(GameState state, String id) {
        return state.fleets().stream().filter(fleet -> fleet.id().equals(id)).findFirst().orElseThrow();
    }
    private static GameState replace(GameState state, Fleet replacement) {
        return state.withFleets(state.fleets().stream()
                .map(fleet -> fleet.id().equals(replacement.id()) ? replacement : fleet).toList());
    }
}
