package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Funded orbital round trips with conditional farm asks propagated into the carrier's purchase price. */
class OffworldSupplyComparisonTest {
    @Test void localFoodQuotesCompeteWithActualPaidOffworldRoundTripsWithoutForcedProfitability() throws Exception {
        var report = new StringBuilder("# Local food and offworld delivery comparison\n\n")
                .append("Each source price is a current installed farm quote with its own inputs, power, maintenance, replacement allowance, 25% corporate tax and target margin. ")
                .append("The identical application supplies a hypothetical local destination alternative at that quote. These are conditional prices, not guaranteed farm earnings. ")
                .append("A separate orbital route starts with 2,500 kg finite food stock at the quoted source price, empty working tanks, a charged battery and a funded private carrier. ")
                .append("Its destination bid is the carrier's cost-covering supply quote. Actual logistics, travel, fuel purchases, services, corporate tax and reserve transfers run until it returns. ")
                .append("No household demand is consumed during this route probe. Source food is seeded orbital inventory; ground production and surface lift are not executed here. ")
                .append("The separate surface-export audit measures lift costs. Local farm costs are held equal across sites to isolate freight; site-specific enclosure costs remain application/site constraints.\n\n")
                .append("| Source application | Destination orbit | Local conditional retail/kg | Imported conditional retail/kg | Delivered kg | Round-trip days | Carrier operating result | Actual tax | Services paid | Held replacement cash | Outcome |\n")
                .append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        int completed = 0;
        for (String application : List.of("industrial_soil_cultivation", "hydroponic_growth_arrays", "automated_biosphere_macro_farms")) {
            var local = BusinessTestWorld.create(application); var hub = local.commercialHubs().getFirst();
            var forecast = GridSupplyPricing.forecast(local);
            var food = FacilitySupplyPricing.offers(local, hub, forecast.tariffs().get("site"), forecast.productionEnergyKwh(), "producer")
                    .stream().filter(o -> o.materialId().equals("food_matrix")).findFirst().orElseThrow();
            for (String destination : List.of("moon", "mars")) if (run(application, destination, food.retailPricePerKg(), report)) completed++;
        }
        assertTrue(completed >= 3, "Each source application must complete at least one real return voyage");
        report.append("\nThe wholesale spread is applied at each marketplace once. Carrier quotes include a complete empty return, paid daily operation, maintenance, capital allowance and its own tax and target margin. ")
                .append("Actual results can differ because daily travel rounds time, working reserves remain aboard and cash-basis route receipts recognize purchases before consumption. ")
                .append("No assertion requires positive profits or changes coefficients to obtain them. Orbital imported prices exclude a ground export and a destination surface landing.\n");
        Files.createDirectories(Path.of("target")); Files.writeString(Path.of("target/offworld-supply-comparison.md"), report);
    }
    private boolean run(String application, String body, double sourcePrice, StringBuilder report) throws Exception {
        var state = privateCarrier(new FoodImportCostAuditTest().world(new FoodImportCostAuditTest.Scenario("mod_fission_thruster", body)));
        var hubs = new ArrayList<>(state.commercialHubs()); hubs.set(0, price(hubs.getFirst(), sourcePrice)); state = state.withCommercialHubs(hubs);
        var source = hubs.getFirst(); var destination = hubs.getLast();
        var offer = TradeSupplyPricing.quote(state, state.fleets().getFirst(), source, destination, "food_matrix");
        if (offer == null) {
            report.append(String.format(Locale.ROOT, "| %s | %s | %.4f | — | 0 | — | — | — | — | — | No complete funded outward-and-return offer |\n",
                    application, body, sourcePrice));
            assertEquals(0, destination.activeOrders().get("food_matrix").supplyKg());
            return false;
        }
        assertEquals(.25, offer.profitTaxRate());
        double receipts = offer.quantityKg() * offer.retailPricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
        assertEquals(receipts * SupplyPricingPolicy.current().targetNetMargin(), receipts - offer.operatingCosts()
                - .25 * (receipts - offer.operatingCosts()) - offer.capitalReserve(), 1e-6);
        hubs.set(1, price(destination, offer.retailPricePerKg())); state = state.withCommercialHubs(hubs);
        int days = 0; double paidTax = 0;
        do {
            state = state.withTurn(state.turn() + 1);
            state = new LogisticsProcessor().processTradeRoutes(state).state();
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()));
            var settled = BusinessSettlement.closeDay(state); state = settled.state();
            paidTax += settled.collectedByBody().values().stream().mapToDouble(Double::doubleValue).sum(); days++;
            assertTrue(state.corporations().getFirst().liquidCapitalReserves() >= 0);
            assertTrue(state.fleets().getFirst().ships().getFirst().currentFuelKg() >= 0);
            assertTrue(state.fleets().getFirst().location().localFlight() == null || !state.fleets().getFirst().location().localFlight().interrupted());
        } while (days < 12000 && (state.tradeRoutes().getFirst().totalVolumeMovedKg() < 2500
                || !FleetPositioning.atHub(state, state.fleets().getFirst(), source)));
        assertTrue(days < 12000, "Funded shipment and empty return must complete");
        assertEquals(2500, state.commercialHubs().getLast().activeOrders().get("food_matrix").supplyKg(), 1e-6);
        assertEquals(0, state.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg(), 1e-6);
        var book = state.businessLedger().account("carrier");
        assertEquals(paidTax, book.cumulativeTaxPaidCredits(), 1e-6);
        assertTrue(book.cumulativeServicesPaidCredits() > 0);
        report.append(String.format(Locale.ROOT, "| %s | %s | %.4f | %.4f | %.2f | %d | %.2f | %.2f | %.2f | %.2f | Returned |\n", application,
                body, sourcePrice, offer.retailPricePerKg(), state.tradeRoutes().getFirst().totalVolumeMovedKg(), days,
                book.cumulativeOperatingResultCredits(), paidTax, book.cumulativeServicesPaidCredits(), book.replacementReserveCredits()));
        return true;
    }
    private CommercialHub price(CommercialHub hub, double price) {
        var orders = new HashMap<>(hub.activeOrders()); var food = orders.get("food_matrix");
        orders.put("food_matrix", new MarketOrder("food_matrix", food.supplyKg(), food.demandKg(), price, 0));
        return hub.withInventory(0, hub.currentStoredWeightKg(), orders);
    }
    private GameState privateCarrier(GameState state) {
        var owner = state.empires().getFirst(); var fleet = state.fleets().getFirst(); var ship = fleet.ships().getFirst();
        var taxed = new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), .25,
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds());
        var firm = new Corporation("carrier", "Carrier", owner.id(), "earth", "TRANSPORT", 1e9, List.of(), List.of(ship.id()), List.of());
        var owned = new ShipInstance(ship.id(), ship.designId(), firm.id(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg(), ship.storedCargoKg()).withPowerState(ship.powerState());
        var carrier = new Fleet(fleet.id(), fleet.name(), firm.id(), fleet.currentSystemId(), "", 0, 0, 0, false, "PASSIVE", List.of(owned), fleet.location());
        var route = new TradeRoute("route", "Food import", firm.id(), "source", "target", "food_matrix", 2500, 0, 1e9, List.of(ship.id()), 0, true);
        return state.toBuilder().empires(List.of(taxed)).corporations(List.of(firm)).fleets(List.of(carrier)).tradeRoutes(List.of(route)).build();
    }
}
