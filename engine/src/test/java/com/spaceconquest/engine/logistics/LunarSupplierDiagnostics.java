package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.GameState;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Audit-only ledger observed immediately around logistics, excluding power and movement consumption. */
final class LunarSupplierDiagnostics {
    private static final List<String> MATERIALS = List.of("liquid_hydrogen", "liquid_oxygen", "hydrogen_gas", "refined_uranium");
    private final Map<String, Double> delivered = new TreeMap<>();
    private final Map<String, Double> freightLoaded = new TreeMap<>();
    private final Map<String, Double> freightWrittenOff = new TreeMap<>();
    private final Map<String, Double> workingPurchases = new TreeMap<>();
    private final Map<String, Integer> phases = new TreeMap<>();
    private final List<String> samples = new ArrayList<>();
    private final Set<String> sampled = new HashSet<>();
    private int dispatchSamples;
    private int mixedFuelGoodsLoads;

    int mixedFuelGoodsLoads() { return mixedFuelGoodsLoads; }

    void observe(GameState before, GameState after) {
        var dayDelivered = new HashMap<String, Double>();
        var dayLoaded = new HashMap<String, Double>();
        for (var old : before.tradeRoutes()) {
            var next = route(after, old.id());
            if (old.onboardKg() < 1e-6 && next.onboardKg() > 1e-6
                    && next.cargoManifest().keySet().stream().anyMatch(MATERIALS::contains)
                    && next.cargoManifest().keySet().stream().anyMatch(id -> id.equals("steel") || id.equals("food_matrix")))
                mixedFuelGoodsLoads++;
            double sold = next.totalVolumeMovedKg() - old.totalVolumeMovedKg();
            double reconciledSale = 0;
            for (var lot : old.cargoManifest().entrySet()) {
                double remaining = next.cargoManifest().get(lot.getKey()) == null ? 0
                        : next.cargoManifest().get(lot.getKey()).massKg();
                double physical = fleet(before, old.assignedFreighterIds().getFirst()).ships().getFirst()
                        .storedCargoKg().getOrDefault(lot.getKey(), 0.0);
                double missing = Math.max(0, lot.getValue().massKg() - physical);
                double mass = sold > 1e-6 ? Math.max(0, lot.getValue().massKg() - missing - remaining) : 0;
                freightWrittenOff.merge(old.id() + "/" + lot.getKey(),
                        Math.max(0, lot.getValue().massKg() - remaining - mass), Double::sum);
                if (mass > 0) {
                    dayDelivered.merge(old.destinationEntityId() + "/" + lot.getKey(), mass, Double::sum);
                    delivered.merge(old.id() + "/" + lot.getKey(), mass, Double::sum);
                }
                reconciledSale += mass;
            }
            assertEquals(sold, reconciledSale, 1e-4, old.id() + " delivered manifest");
            if (old.onboardKg() < 1e-6 && next.onboardKg() > 1e-6) for (var lot : next.cargoManifest().entrySet()) {
                dayLoaded.merge(next.originEntityId() + "/" + lot.getKey(), lot.getValue().massKg(), Double::sum);
                freightLoaded.merge(next.id() + "/" + lot.getKey(), lot.getValue().massKg(), Double::sum);
            }
            if (!old.id().startsWith("moon-supply")) continue;
            if (old.onboardKg() < 1e-6 && next.onboardKg() > 1e-6
                    && (dispatchSamples++ < 16 || hub(before, "moon-market").activeOrders().get("hydrogen_gas").supplyKg() < 1e-4
                    && sampled.add(old.id() + "/empty-dispatch"))) sample(before, old, next.cargoManifest().toString());
            var carrier = fleet(after, next.assignedFreighterIds().getFirst());
            String phase = !next.isActive() ? "dispatch paused" : carrier.location().inTransit() ? "in transit " + next.phase() : next.phase();
            phases.merge(next.id() + "/" + phase, 1, Integer::sum);
            if (next.isActive() && TradeRoute.LOADING.equals(next.phase()) && !carrier.location().inTransit()
                    && next.onboardKg() < 1e-6 && (after.turn() % 30 == 0 || sampled.add(next.id()))) sample(after, next, "idle");
        }
        for (String hubId : List.of("depot-market", "moon-market")) for (String material : MATERIALS) {
            String key = hubId + "/" + material;
            double purchased = hub(before, hubId).activeOrders().get(material).supplyKg()
                    + dayDelivered.getOrDefault(key, 0.0) - hub(after, hubId).activeOrders().get(material).supplyKg()
                    - dayLoaded.getOrDefault(key, 0.0);
            assertTrue(purchased >= -1e-4, "Unexplained logistics stock increase: " + key);
            workingPurchases.merge(key, Math.max(0, purchased), Double::sum);
        }
    }

    private void sample(GameState state, TradeRoute supplier, String selectedCargo) {
        var target = hub(state, "moon-market");
        var source = hub(state, supplier.originEntityId());
        var carrier = fleet(state, supplier.assignedFreighterIds().getFirst());
        double request = HubReplenishment.requestedKg(state, target, "hydrogen_gas", supplier.id());
        var candidate = supplier.withMarketChoice("hydrogen_gas", target.id());
        var single = TradeShipmentSizing.choose(state, candidate, supplier.transferAmountPerTurnKg());
        var mixed = TradeBasketPlanner.choose(state, candidate, carrier, target);
        var ranked = source.activeOrders().values().stream().filter(order -> target.activeOrders().containsKey(order.resourceId())
                        && target.activeOrders().get(order.resourceId()).pricePerKg() * .8 > order.pricePerKg()
                        && HubReplenishment.requestedKg(state, target, order.resourceId(), supplier.id()) > 0)
                .sorted(Comparator.<com.spaceconquest.engine.MarketOrder>comparingDouble(order ->
                        TradeFuelPriority.urgency(state, target, order.resourceId(), supplier.id())).reversed())
                .map(order -> String.format(Locale.ROOT, "%s=%.3f", order.resourceId(),
                        TradeFuelPriority.urgency(state, target, order.resourceId(), supplier.id()))).toList();
        var raw = new LogisticsProcessor().previewBasket(state, candidate, Map.of("hydrogen_gas", Math.min(25000, request)));
        String preview = "rejected";
        if (raw != null) {
            var quote = RoamingTradeCost.quote(state, raw.preparedFleet(), source, target);
            preview = quote == null ? "no funded voyage quote" : String.format(Locale.ROOT,
                    "accepted; cargo=%.1f kg; fuel cost=%.2f; days=%.2f; gross spread=%.2f",
                    raw.route().onboardKg(), quote.fuelCredits(), quote.days(), raw.route().onboardKg()
                            * (target.activeOrders().get("hydrogen_gas").pricePerKg() * .8 - source.activeOrders().get("hydrogen_gas").pricePerKg()));
        }
        samples.add(String.format(Locale.ROOT,
                "| %d | %s | %s | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %s | %s | %s | %s | %s |",
                state.turn(), supplier.id(), supplier.destinationEntityId(), target.activeOrders().get("hydrogen_gas").supplyKg(),
                HubReplenishment.inboundKg(state, target, "hydrogen_gas", null), request,
                source.activeOrders().get("hydrogen_gas").supplyKg(), TradeShipmentSizing.ownerCash(state, supplier.ownerEntityId()),
                HubReplenishment.buyerCashAvailable(state, target, supplier.id()), single == null ? "none" : single.shipment().route().cargoManifest(),
                mixed == null ? "none" : mixed.shipment().route().cargoManifest(), preview, ranked, selectedCargo));
    }

    void verify(GameState state) {
        for (var entry : freightLoaded.entrySet()) {
            int separator = entry.getKey().indexOf('/');
            var supplier = route(state, entry.getKey().substring(0, separator));
            var lot = supplier.cargoManifest().get(entry.getKey().substring(separator + 1));
            assertEquals(entry.getValue() - delivered.getOrDefault(entry.getKey(), 0.0)
                            - freightWrittenOff.getOrDefault(entry.getKey(), 0.0),
                    lot == null ? 0 : lot.massKg(), 1e-4, entry.getKey() + " freight ledger");
        }
    }

    String report() {
        var text = new StringBuilder("## Supplier delivery diagnostics\n\n");
        text.append("Cargo delivered by route (kg): ").append(delivered).append(".\n\n")
                .append("Freight loaded by route (kg): ").append(freightLoaded).append(".\n\n")
                .append("Paid freight written off when no longer physically aboard, separately from delivered cargo (kg): ")
                .append(freightWrittenOff).append(".\n\n")
                .append("Hub working-supply purchases excluding resale freight (kg): ").append(workingPurchases).append(".\n\n")
                .append("Supplier phase days: ").append(phases).append(".\n\n")
                .append("Mixed fuel and ordinary goods departures: ").append(mixedFuelGoodsLoads).append(".\n\n")
                .append("Dispatch and idle active supplier samples use isolated paid previews; no preview changes the simulation. Dispatch samples use the pre-logistics snapshot, so earlier routes in the same tick may change the actual selection. The raw gas basket is capped at 25,000 kg and does not require profitability.\n\n")
                .append("| Day | Route | Previous destination | Moon gas stock | Gas inbound | Gas request | Source gas | Owner cash | Buyer allowance | Profitable gas shipment | Profitable Moon basket | Raw gas preview | Material urgency | Actual selected cargo |\n")
                .append("|---:|---|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|\n")
                .append(String.join("\n", samples)).append("\n\n");
        return text.toString();
    }
}
