package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import java.nio.file.*;
import java.util.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Three years of real tick settlement, with finite deposits and independent businesses at each stage. */
class BusinessEconomicsAuditTest {
    private static final int DAYS = 1080;
    @Test void fullLocalFoodChainsReportActualCashTaxesReservesAndShortagesOverThreeYears() throws Exception {
        var output = new StringBuilder("# Three-year business operation audit\n\n")
                .append("Each scenario runs 1,080 simulation days with independent extractors, a farm, a food processor and an electricity provider. ")
                .append("All research is available. Population starts at 1,000 and follows ordinary campaign updates. Each firm starts with 10 million credits; ")
                .append("the market and government each start with 100 million. Opening farm inputs cover 1.5 cycles, reactor fuel 30 days and discovered deposits are finite. The reactor-ore quarry uses tier 2 to support the reactor's fuel requirement. ")
                .append("One initial harvest is prefunded. Later explicit harvest and CO₂ orders share remaining market money with ordinary automatic long-lead procurement. ")
                .append("There is no external replenishment. Government basic-needs support and its debt remain ordinary simulation behavior, not proof of fiscal sustainability.\n\n");
        var oldLevel = org.apache.logging.log4j.LogManager.getLogger(SpaceConquestEngine.class).getLevel();
        Configurator.setLevel(SpaceConquestEngine.class.getName(), Level.WARN);
        try {
            for (String application : List.of("industrial_soil_cultivation", "hydroponic_growth_arrays", "automated_biosphere_macro_farms"))
                run(application, output);
        } finally { Configurator.setLevel(SpaceConquestEngine.class.getName(), oldLevel); }
        output.append("\n## Interpretation\n\nPositive asks are not guaranteed sales. Idle days, shortages, unsold coproducts and unfunded invoices remain visible. ")
                .append("Reserve transfers remain assets and are shown separately from after-tax operating profit. Maintenance and transport services are paid into a service clearing account; ")
                .append("a detailed maintenance-parts or service-worker sector is not simulated. The enclosed chains obtain CO₂ as a paid iron-smelting coproduct; retained iron and other residue are not assumed sold. ")
                .append("These small, initially wealthy economies are operating probes, not balanced campaign presets. Capital construction was seeded, not performed in this audit. ")
                .append("All deliveries below are local; offworld transport is covered by the separate funded logistics and import tests.\n");
        Files.createDirectories(Path.of("target")); Files.writeString(Path.of("target/business-economics-report.md"), output);
    }
    private void run(String application, StringBuilder output) throws Exception {
        var initial = SupplyContractLifecycleTest.contractHarvest(chain(application));
        var engine = new SpaceConquestEngine(42); engine.applyGameState(initial);
        var diagnostics = new BusinessConstraintAudit();
        var produced = new HashMap<String, Double>(); var sold = new HashMap<String, Double>();
        var paused = new HashMap<String, Integer>(); double unmet = 0, bought = 0; int harvests = 0;
        var trace = new StringBuilder("day\tgrowth_batches\tpaid_farmers\tfuel_kg\toperating_decision\n");
        for (int day = 1; day <= DAYS; day++) {
            var state = engine.getGameState();
            if (day % 30 == 0) {
                state = replenishHarvest(state);
                state = replenishCarbonDioxide(state);
                engine.applyGameState(state);
            }
            engine.stepTurn(); state = engine.getGameState();
            diagnostics.record(state);
            if (day <= 150 || day % 30 == 0) {
                var farmer = state.industryAccounts().stream().filter(a -> a.facilityId().equals("producer")).findFirst().orElseThrow();
                trace.append(day).append('\t').append(farmer.farmingState().cultivatedBatches()).append('\t').append(farmer.paidWorkers()).append('\t')
                        .append(state.commercialHubs().getFirst().activeOrders().get("refined_uranium").supplyKg()).append('\t')
                        .append(state.businessLedger().operatingDecisions().get("producer")).append('\n');
            }
            for (var account : state.industryAccounts()) {
                produced.merge(account.facilityId(), account.producedKg().values().stream().mapToDouble(Double::doubleValue).sum(), Double::sum);
                sold.merge(account.facilityId(), account.soldKg().values().stream().mapToDouble(Double::doubleValue).sum(), Double::sum);
                if (account.facilityId().equals("producer") && account.producedKg().getOrDefault("food_matrix", 0.0) > 0) harvests++;
            }
            state.businessLedger().operatingDecisions().forEach((id, decision) -> { if (!decision.operate()) paused.merge(id, 1, Integer::sum); });
            unmet += state.householdAccounts().stream().mapToDouble(a -> a.unmetBasicKg().getOrDefault("food_matrix", 0.0)).sum();
            bought += state.householdAccounts().stream().mapToDouble(a -> a.headcount() * .1 - a.unmetBasicKg().getOrDefault("food_matrix", 0.0)).sum();
            assertTrue(state.corporations().stream().allMatch(c -> Double.isFinite(c.liquidCapitalReserves()) && c.liquidCapitalReserves() >= 0));
            assertTrue(state.marketAccounts().stream().allMatch(a -> a.unsettledSalesCredits() >= 0));
            assertTrue(state.geologicalDeposits().stream().allMatch(d -> d.remainingVolumeKg() >= 0 && d.remainingVolumeKg() <= d.initialVolumeKg()));
            if (day == 360) {
                var file = Path.of("target/business-audit-" + application + ".scsave").toFile();
                var saves = new SaveGameManager(Path.of("target")); saves.save(file, state, 1, GameClock.START_TIME.toString());
                engine.applyGameState(saves.load(file).toGameState(state.turn(), state.status()));
            }
        }
        var end = engine.getGameState();
        Files.writeString(Path.of("target/business-audit-" + application + ".tsv"), trace);
        Files.writeString(Path.of("target/business-constraints-" + application + ".tsv"), diagnostics.trace());
        assertTrue(harvests > 0, application); assertTrue(bought > 0); assertTrue(end.businessLedger().serviceClearingCredits() > 0);
        String title = application.replace('_', ' ');
        output.append("## ").append(Character.toUpperCase(title.charAt(0))).append(title.substring(1)).append("\n\n")
                .append(String.format(Locale.ROOT, "Harvests: %d. Household food bought: %,.2f kg. Unmet food: %,.2f kg. Closing food stock: %,.2f kg.\n\n",
                        harvests, bought, unmet, end.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg()))
                .append("| Business | Produced kg | Marketed kg | Paused days | Operating result | Tax paid | After-tax result | Held replacement cash | Spendable cash | Unsold kg |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var firm : end.corporations()) {
            var book = end.businessLedger().account(firm.id()); String facility = firm.ownedFacilityIds().getFirst();
            double unsold = end.industryAccounts().stream().filter(a -> a.facilityId().equals(facility)).flatMap(a -> a.unsoldStockKg().values().stream())
                    .mapToDouble(Double::doubleValue).sum();
            output.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %d | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f |\n", facility,
                    produced.getOrDefault(facility, 0.0), sold.getOrDefault(facility, 0.0), paused.getOrDefault(facility, 0), book.cumulativeOperatingResultCredits(),
                    book.cumulativeTaxPaidCredits(), book.cumulativeOperatingResultCredits() - book.cumulativeTaxPaidCredits(),
                    book.replacementReserveCredits(), firm.liquidCapitalReserves(), unsold));
        }
        double debt = end.planetaryBalanceSheets().stream().mapToDouble(PlanetaryBalanceSheet::outstandingDebtCredits).sum();
        output.append(String.format(Locale.ROOT, "\nService clearing cash: %.2f. Unpaid invoices: %.2f. Held purchase escrow: %.2f. Municipal debt: %.2f.\n\n",
                end.businessLedger().serviceClearingCredits(), end.businessLedger().unpaidExpenses().values().stream().mapToDouble(Double::doubleValue).sum(),
                end.businessLedger().escrowCredits(), debt));
        output.append(diagnostics.report(DAYS));
    }
    private GameState replenishHarvest(GameState state) {
        long turn = state.turn();
        boolean committed = state.businessLedger().contracts().stream().anyMatch(c -> c.facilityId().equals("producer") && c.openAt(turn));
        var food = state.commercialHubs().getFirst().activeOrders().get("food_matrix");
        if (committed || food.supplyKg() >= food.demandKg() * 30) return state;
        var hub = state.commercialHubs().getFirst(); var forecast = GridSupplyPricing.forecast(state);
        for (var offer : FacilitySupplyPricing.offers(state, hub, forecast.tariffs().getOrDefault("site", .02), forecast.productionEnergyKwh(), "producer")) {
            long days = Math.max(1, (long) Math.ceil(offer.deliveryDays()));
            state = SupplyProcurement.accept(state, "renewal-" + state.turn() + "-" + offer.materialId(), "producer", "hub", offer.materialId(),
                    offer.quantityKg() * days, offer.retailPricePerKg() * .8 * 1.1, state.turn() + days + 7);
        }
        return state;
    }
    private GameState replenishCarbonDioxide(GameState state) {
        var hub = state.commercialHubs().getFirst(); var order = hub.activeOrders().get("carbon_dioxide_gas");
        if (order == null || order.supplyKg() >= order.demandKg() * 30 || state.businessLedger().contracts().stream()
                .anyMatch(c -> c.facilityId().equals("carbon-dioxide") && c.openAt(state.turn()))) return state;
        var forecast = GridSupplyPricing.forecast(state);
        var offer = FacilitySupplyPricing.offers(state, hub, forecast.tariffs().getOrDefault("site", .02), forecast.productionEnergyKwh(),
                "carbon-dioxide", "carbon_dioxide_gas").stream().filter(o -> o.materialId().equals("carbon_dioxide_gas")).findFirst().orElse(null);
        if (offer == null) return state;
        return SupplyProcurement.accept(state, "co2-" + state.turn(), "carbon-dioxide", "hub", "carbon_dioxide_gas",
                Math.min(offer.quantityKg() * 30, order.demandKg() * 30 - order.supplyKg()), offer.retailPricePerKg() * .8 * 1.1, state.turn() + 30);
    }
    private GameState chain(String application) throws Exception {
        var state = BusinessTestWorld.create(application); var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        var farm = IndustryRecipeCatalog.find(application);
        farm.inputsKg().forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg * 1.5, kg / farm.farming().growingDays(), MarketProcessor.basePricePerKg(id), 0)));
        orders.put("refined_uranium", new MarketOrder("refined_uranium", 300, 10, 100, 0));
        var facilities = new ArrayList<>(state.industrialFacilities()); var firms = new ArrayList<>(state.corporations()); var deposits = new ArrayList<GeologicalDeposit>();
        var applications = new LinkedHashMap<>(Map.of("nitrates", "nitrates_mining", "phosphates", "phosphates_mining", "potash", "potash_mining",
                "reactor-ore", "uranium_ore_mining", "reactor-fuel", "uranium_refining"));
        if (farm.inputsKg().containsKey("carbon_dioxide_gas")) {
            applications.put("water", "surface_water_treatment"); applications.put("iron-ore", "mining_outpost");
            applications.put("carbon", "carbon_mining"); applications.put("carbon-dioxide", "pyro_iron_smelting");
        }
        for (var entry : applications.entrySet()) {
            var recipe = IndustryRecipeCatalog.find(entry.getValue()); assertNotNull(recipe, entry.getValue());
            var equipment = ProductionWork.initialEquipment(entry.getValue());
            facilities.add(new IndustrialFacility(entry.getKey(), "site", entry.getValue(), entry.getKey() + "-owner", IndustrialFacility.PRIVATE_CORPORATE, entry.getKey().equals("reactor-ore") ? 2 : 1,
                    equipment == null ? recipe.workersPerBatch() : equipment.referenceOperators(), equipment == null ? "industrial_worker" : equipment.operatorProfession(), false, 0));
            firms.add(new Corporation(entry.getKey() + "-owner", entry.getKey(), "owner", "site", "AUDIT", 1e7, List.of(entry.getKey()), List.of(), List.of()));
            recipe.inputsKg().forEach((id, kg) -> orders.putIfAbsent(id, new MarketOrder(id, kg * 2, 0, MarketProcessor.basePricePerKg(id), 0)));
            recipe.outputsKg().keySet().forEach(id -> orders.putIfAbsent(id, new MarketOrder(id, 0, 0, MarketProcessor.basePricePerKg(id), 0)));
            for (var input : recipe.inputSources()) if (input.source() == ProcessInput.Source.DEPOSIT)
                deposits.add(new GeologicalDeposit(entry.getKey() + "-deposit", "site", input.materialId(), 1e8, 1e8, 1, true, entry.getKey() + "-owner"));
        }
        return state.toBuilder().industrialFacilities(facilities).corporations(firms).geologicalDeposits(deposits).commercialHubs(List.of(
                hub.withInventory(0, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), orders)))
                .industryAccounts(facilities.stream().map(f -> IndustryAccount.empty(f.id())).toList()).build();
    }
}
