package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Cosmetic snapshot estimates and mixture-preserving stock calculations. Never purchases or mutates state. */
public final class HubFuelStockSuggestions {
    public record Fuel(String id, String label, Map<String, Double> fractions) {
        public Fuel {
            fractions = Map.copyOf(fractions);
            if (fractions.isEmpty() || fractions.values().stream().anyMatch(value -> !Double.isFinite(value) || value <= 0)
                    || Math.abs(fractions.values().stream().mapToDouble(Double::doubleValue).sum() - 1) > 1e-9)
                throw new IllegalArgumentException("Fuel ingredients must have positive fractions totaling one.");
        }
        @Override public String toString() { return label; }
    }
    public record Inputs(double departureKg, double dailyKg, double cycleDays, double safetyDays) {
        public Inputs {
            for (double value : new double[]{departureKg, dailyKg, cycleDays, safetyDays})
                if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Use finite nonnegative planning inputs.");
            if (cycleDays <= 0 || departureKg + dailyKg <= 0)
                throw new IllegalArgumentException("Enter a positive supplier cycle and departure quantity or daily demand.");
        }
    }
    public record Estimate(double departureKg, double dailyKg, double cycleDays, String explanation) {}
    public record Suggestion(Map<String, HubStockTarget> targets, double reorderMixtureKg, double targetMixtureKg) {
        public Suggestion { targets = Map.copyOf(targets); }
    }
    private HubFuelStockSuggestions() {}

    public static List<Fuel> fuels() {
        var fuels = new LinkedHashMap<Map<String, Double>, Fuel>();
        for (String id : PropulsionCatalog.MAIN_DRIVE_IDS) {
            var drive = PropulsionCatalog.drive(id);
            var fractions = drive.propellantMaterials(1);
            fuels.putIfAbsent(fractions, new Fuel(id, drive.propellantLabel(), fractions));
            for (var feed : PropulsionCatalog.reactorFuels(id))
                fuels.putIfAbsent(Map.of(feed.materialId(), 1.0), new Fuel(feed.materialId(), feed.materialId(), Map.of(feed.materialId(), 1.0)));
        }
        for (var fuel : ShipPowerProfile.capture(List.of()).fuels().values()) {
            var fractions = fuel.materials(1);
            fuels.putIfAbsent(fractions, new Fuel(fuel.materialId(), String.join(" + ", fractions.keySet().stream().sorted().toList()), fractions));
        }
        return List.copyOf(fuels.values());
    }

    public static Suggestion calculate(Fuel fuel, Inputs inputs) {
        double reorder = inputs.departureKg() + inputs.dailyKg() * (inputs.cycleDays() + inputs.safetyDays());
        double target = reorder + Math.max(inputs.departureKg(), inputs.dailyKg() * inputs.cycleDays());
        if (!Double.isFinite(target)) throw new IllegalArgumentException("Planning quantities are too large.");
        var targets = new HashMap<String, HubStockTarget>();
        fuel.fractions().forEach((id, fraction) -> targets.put(id, new HubStockTarget(reorder * fraction, target * fraction)));
        return new Suggestion(targets, reorder, target);
    }

    /** Local travel previews can be expensive; callers must run this off the JavaFX application thread. */
    public static Estimate estimate(GameState state, CommercialHub hub, Fuel fuel) {
        double departure = 0, daily = 0, cycle = 0;
        var history = new StringJoiner(" ");
        for (var ingredient : fuel.fractions().entrySet()) {
            var order = hub.activeOrders().get(ingredient.getKey());
            if (order != null && Double.isFinite(order.demandKg())) daily = Math.max(daily, order.demandKg() / ingredient.getValue());
            var average = hub.stockHistory().average(ingredient.getKey(), state.turn());
            daily = Math.max(daily, average.dailyWithdrawalKg() / ingredient.getValue());
            history.add(average.observedDays() == 0 ? ingredient.getKey() + ": no completed usage history."
                    : String.format(Locale.ROOT, "%s: %.3f kg/day withdrawn and %.3f kg/day delivered over %d observed days in the last 30 completed days. %s",
                    ingredient.getKey(), average.dailyWithdrawalKg(), average.dailyDeliveryKg(), average.observedDays(),
                    average.stockoutDays() > 0 ? average.stockoutDays() + " stockout days; usage may understate demand." : ""));
        }
        for (var fleet : state.fleets()) {
            boolean associated = FleetPositioning.atHub(state, fleet, hub) || state.tradeRoutes().stream().anyMatch(route ->
                    route.isActive() && (hub.id().equals(route.originEntityId()) || hub.id().equals(route.destinationEntityId()))
                    && fleet.ships().stream().anyMatch(ship -> route.assignedFreighterIds().contains(ship.id())));
            if (!associated) continue;
            var quantities = new HashMap<String, Double>();
            for (var ship : fleet.ships()) fullRefill(state, ship).forEach((id, kg) -> quantities.merge(id, kg, Double::sum));
            double fleetMixture = fuel.fractions().entrySet().stream().mapToDouble(item ->
                    quantities.getOrDefault(item.getKey(), 0.0) / item.getValue()).min().orElse(0);
            departure = Math.max(departure, fleetMixture);
        }
        int examined = 0, planned = 0;
        for (var route : state.tradeRoutes().stream().sorted(Comparator.comparing(TradeRoute::id)).toList()) {
            if (!route.isActive() || !hub.id().equals(route.destinationEntityId())) continue;
            if (examined++ >= 8) break;
            var source = state.commercialHubs().stream().filter(item -> item.id().equals(route.originEntityId())).findFirst().orElse(null);
            var fleet = state.fleets().stream().filter(item -> item.ships().stream()
                    .anyMatch(ship -> route.assignedFreighterIds().contains(ship.id()))).findFirst().orElse(null);
            if (source == null || fleet == null || !source.activeOrders().keySet().containsAll(fuel.fractions().keySet())) continue;
            var from = FleetPositioning.hubSite(state, source);
            var to = FleetPositioning.hubSite(state, hub);
            String system = FleetPositioning.systemForHub(state, source);
            if (from == null || to == null || from.equals(to) || !Objects.equals(system, FleetPositioning.systemForHub(state, hub))
                    || !Objects.equals(system, fleet.currentSystemId()) || fleet.hasInterstellarOrder()) continue;
            var outward = LocalTravel.plan(state, fleet.withLocation(FleetLocation.at(from)), to);
            var back = LocalTravel.plan(state, fleet.withLocation(FleetLocation.at(to)), from);
            if (outward == null || back == null) continue;
            double days = Math.ceil(outward.days()) + Math.ceil(back.days()) + 2;
            if (Double.isFinite(days)) { cycle = Math.max(cycle, days); planned++; }
        }
        return new Estimate(departure, Math.max(0, daily), cycle,
                "Departure: largest associated fleet full refill, not a voyage quote. Demand: maximum of modeled demand and measured ingredient withdrawals converted to mixture kg/day. "
                        + history + " Withdrawals include local use and outbound freight. Current day excluded; unobserved days are not counted. "
                        + "Supplier cycle: " + planned + " local route previews (up to 8), outward + return + 2 handling days. "
                        + "Both legs use current equipment, cargo, fuel and orbital phase independently; stock, cash and return resupply are not certified. "
                        + (cycle > 0 ? "Review delays and adjust inputs." : "No cycle estimate available; enter supplier turnaround days manually."));
    }

    private static Map<String, Double> fullRefill(GameState state, ShipInstance ship) {
        var quantities = new HashMap<String, Double>();
        var design = FleetSupplySimulation.design(state, ship);
        if (design == null) return quantities;
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive != null) {
            drive.propellantMaterials(design.fuelCapacityKg()).forEach((id, kg) -> quantities.merge(id, kg, Double::sum));
            var feed = PropulsionCatalog.preferredReactorFuel(drive, ship);
            if (feed != null && feed.kgPerPropellantKg() > 0)
                quantities.merge(feed.materialId(), design.fuelCapacityKg() * feed.kgPerPropellantKg(), Double::sum);
        }
        var profile = design.powerProfile();
        if (profile != null) {
            var power = ShipPowerProcessor.reserves(ship);
            for (String id : List.of(power.chemicalMixture(), power.reactorFuel())) {
                var fuel = profile.fuels().get(id);
                if (fuel == null || (fuel.oxidizerId() == null ? profile.fissionKw() <= 0 : profile.chemicalKw() <= 0)) continue;
                fuel.materials(fuel.oxidizerId() == null ? profile.reactorTankKg() : profile.generatorTankKg())
                        .forEach((material, kg) -> quantities.merge(material, kg, Double::sum));
            }
        }
        return quantities;
    }
}
