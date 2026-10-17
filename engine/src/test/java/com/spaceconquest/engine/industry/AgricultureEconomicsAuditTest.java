package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.market.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Finite, repeatable production and household settlement probes rather than guaranteed profitable farms. */
class AgricultureEconomicsAuditTest {
    private static final int DAYS = 360;
    private record Scenario(String application, boolean starved) {}
    private static final class Measurement {
        final Scenario scenario;
        int harvests, idleDays;
        double food, consumed, unmet, wages, materials, power, maintenance, revenue, oxygenSold, residueSold;
        double construction, closingFood, closingOxygen, retainedResidue, nonFoodCredits, gridCashResult, gridFuelKg, minCash = Double.MAX_VALUE;
        Measurement(Scenario scenario) { this.scenario = scenario; }
    }

    @Test void severalGrowingCyclesPayRealWorkersAndPowerAndSellOnlyAgainstConsumerDemand() throws Exception {
        var rows = new ArrayList<Measurement>();
        for (String application : List.of("industrial_soil_cultivation", "hydroponic_growth_arrays", "automated_biosphere_macro_farms"))
            rows.add(run(new Scenario(application, false)));
        rows.add(run(new Scenario("hydroponic_growth_arrays", true)));
        assertTrue(rows.subList(0, 3).stream().allMatch(r -> r.harvests >= 3 && r.food > 0 && r.wages > 0 && r.power > 0));
        assertTrue(rows.stream().allMatch(r -> r.residueSold == 0 && r.minCash >= 0));
        assertTrue(rows.get(3).harvests < rows.get(1).harvests);
        assertTrue(rows.get(3).idleDays > rows.get(1).idleDays);
        assertTrue(rows.get(1).retainedResidue > 0);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/agriculture-economics-report.md"), report(rows));
    }

    @Test void supplyPricesAndMeteredTariffsFundRepeatedRealHarvestsWithoutFreeProducts() throws Exception {
        var rows = new ArrayList<Measurement>();
        for (String application : List.of("industrial_soil_cultivation", "hydroponic_growth_arrays", "automated_biosphere_macro_farms"))
            rows.add(run(new Scenario(application, false), true));
        assertTrue(rows.stream().allMatch(r -> r.harvests >= 3 && r.gridCashResult > 0 && r.residueSold == 0));
        Files.writeString(Path.of("target/supply-pricing-economy-report.md"), report(rows)
                .replace("Four 360-day probes", "Three 360-day probes with active supply-offer pricing and cost-covering metered electricity")
                .replace("Input reserves cover 20 cycles except the shortage case's 2.25 nitrate cycles.", "Input reserves cover 20 cycles.")
                .replace("# Agricultural production and demand audit", "# Supply pricing and agricultural operations")
                .replace("The fixed electricity tariff is not proof of a cost-covering grid.", "The metered tariff targets provider costs and reserves at the forecast load.")
                .replace("Prices use the existing spot-price rule, not assumed premium contracts.", "Scarcity prices read finite installed supply offers and dynamic generator tariffs. No premium contracts or guaranteed farm sales are assumed."));
    }

    private Measurement run(Scenario scenario) throws Exception {
        return run(scenario, false);
    }
    private Measurement run(Scenario scenario, boolean priced) throws Exception {
        var row = new Measurement(scenario); var state = world(scenario); var races = DataModelLoader.loadRaces();
        for (var f : state.industrialFacilities()) if (!f.id().equals("generator"))
            row.construction += ConstructionMaterialCatalog.facility(f.applicationId(), 1).entrySet().stream()
                    .mapToDouble(e -> e.getValue() * MarketProcessor.basePricePerKg(e.getKey())).sum();
        for (int day = 0; day < DAYS; day++) {
            state = state.withTurn(day);
            state = state.withCommercialHubs(new MarketDemandProcessor().refresh(state, races));
            state = state.withCommercialHubs(priced ? new MarketProcessor().updateCommercialHubs(state, state.commercialHubs())
                    : state.commercialHubs().stream().map(new MarketProcessor()::updateHub).toList());
            double beforeFood = stock(state, "food_matrix");
            var payroll = new HouseholdEconomyProcessor().process(state, races);
            state = state.toBuilder().householdAccounts(payroll.householdAccounts()).empires(payroll.empires())
                    .corporations(payroll.corporations()).commercialHubs(payroll.commercialHubs())
                    .marketAccounts(payroll.marketAccounts()).industryAccounts(payroll.industryAccounts()).build();
            row.consumed += beforeFood - stock(state, "food_matrix");
            row.unmet += state.householdAccounts().stream().mapToDouble(a -> a.unmetBasicKg().getOrDefault("food_matrix", 0.0)).sum();
            state = productionDay(state, payroll, priced);
            var hub = state.commercialHubs().getFirst();
            var farm = account(state, "farm");
            if (!farm.producedKg().isEmpty()) row.harvests++;
            if (farm.inputCostsCredits() <= 1e-8) row.idleDays++;
            for (var a : state.industryAccounts()) if (!a.facilityId().equals("generator")) {
                row.food += a.producedKg().getOrDefault("food_matrix", 0.0);
                row.wages += a.wageCostsCredits(); row.materials += a.inputCostsCredits();
                row.power += a.powerCostsCredits(); row.maintenance += a.maintenanceCostsCredits(); row.revenue += a.salesCredits();
                row.oxygenSold += a.soldKg().getOrDefault("oxygen_gas", 0.0);
                row.residueSold += a.soldKg().getOrDefault("cultivation_residue", 0.0);
                row.nonFoodCredits += a.soldKg().entrySet().stream().filter(e -> !e.getKey().equals("food_matrix"))
                        .mapToDouble(e -> e.getValue() * hub.activeOrders().get(e.getKey()).pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE).sum();
                row.minCash = Math.min(row.minCash, a.operatingCashCredits());
            }
            assertTrue(state.commercialHubs().getFirst().activeOrders().values().stream().allMatch(o -> o.supplyKg() >= -1e-7));
        }
        row.closingFood = stock(state, "food_matrix"); row.closingOxygen = stock(state, "oxygen_gas");
        row.retainedResidue = account(state, "farm").unsoldStockKg().getOrDefault("cultivation_residue", 0.0);
        row.gridCashResult = account(state, "generator").operatingCashCredits() - 1e7;
        row.gridFuelKg = 10000 - stock(state, "refined_uranium");
        assertEquals(14000 + row.food,
                row.consumed + row.closingFood + state.industryAccounts().stream()
                .mapToDouble(a -> a.unsoldStockKg().getOrDefault("food_matrix", 0.0)).sum(), 1e-5);
        return row;
    }

    private GameState productionDay(GameState state, HouseholdEconomyProcessor.TurnResult payroll, boolean priced) {
        var power = new PowerGenerationProcessor().process(state, payroll.paidWorkersByFacility(), payroll.wagesByFacility());
        state = state.toBuilder().empires(power.empires()).corporations(power.corporations()).commercialHubs(power.hubs())
                .marketAccounts(power.marketAccounts()).industryAccounts(power.industryAccounts()).build();
        var balance = new PowerProcessor().balanceDay(state, power.generationKw(), payroll.paidWorkersByFacility());
        var billed = new PowerBillingProcessor().process(state, balance, power.accounts(), payroll.paidWorkersByFacility(),
                priced ? GridSupplyPricing.tariffs(state, power.accounts(), balance) : Map.of("site", .02));
        state = state.toBuilder().empires(billed.empires()).corporations(billed.corporations())
                .householdAccounts(billed.households()).powerGrids(billed.grids()).industryAccounts(billed.industryAccounts()).build();
        var result = new IndustryMarketProcessor().process(state, billed.poweredWorkers(), payroll.wagesByFacility(),
                payroll.paidWorkersByFacility(), billed.productionEnergy());
        return state.toBuilder().empires(result.empires()).corporations(result.corporations()).commercialHubs(result.hubs())
                .marketAccounts(result.marketAccounts()).industryAccounts(result.industryAccounts()).build();
    }

    private GameState world(Scenario scenario) throws Exception {
        boolean outdoor = scenario.application().equals("industrial_soil_cultivation");
        var population = new Population("human", Map.of(25, 1000L));
        var planet = new Planet("site", "Audit site", "", 5.97e24, 9.81, SolarRadiation.AU_KM, 0, 12742,
                outdoor ? "terrestrial" : "barren", outdoor ? "nitrogen_oxygen" : "none", outdoor, outdoor ? .3 : 0,
                List.of(), List.of(), List.of(population));
        var knowledge = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { knowledge.add(t.id()); t.applications().forEach(a -> knowledge.add(a.id())); });
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1e8, 0, List.of("sol"), List.of(), Map.of(), knowledge, List.of());
        var recipe = IndustryRecipeCatalog.find(scenario.application());
        var facilities = List.of(new IndustrialFacility("farm", "site", scenario.application(), "owner", "PUBLIC_STATE", 1,
                        recipe.work().equipment().referenceOperators(), "farmer", false, 0),
                new IndustrialFacility("factory", "site", "food_processing", "owner", "PUBLIC_STATE", 1, 10, "industrial_worker", false, 0),
                new IndustrialFacility("generator", "site", "nuclear_power_app", "owner", "PUBLIC_STATE", 1, 100, "technician", false, 0));
        var orders = new HashMap<String, MarketOrder>();
        var inputs = new HashMap<>(recipe.inputsKg()); inputs.put("refined_uranium", 1.0);
        inputs.forEach((id, kg) -> orders.put(id, new MarketOrder(id,
                id.equals("refined_uranium") ? 10000 : kg * (scenario.starved() && id.equals("nitrates") ? 2.25 : 20),
                0, MarketProcessor.basePricePerKg(id), 0)));
        orders.put("food_matrix", new MarketOrder("food_matrix", 14000, 0, 4, 0));
        orders.put("oxygen_gas", new MarketOrder("oxygen_gas", 2000, 0, 2, 0));
        recipe.outputsKg().keySet().forEach(id -> orders.putIfAbsent(id, new MarketOrder(id, 0, 0, MarketProcessor.basePricePerKg(id), 0)));
        var hub = new CommercialHub("hub", "site", 0, 1e8, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders);
        var households = List.of(household("farmer", 100), household("industrial_worker", 100), household("technician", 800));
        return GameState.builder().solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0,
                        SolarRadiation.SOLAR_MASS_KG, 1392700, "yellow", List.of(planet), List.of())))
                .empires(List.of(empire)).industrialFacilities(facilities).commercialHubs(List.of(hub))
                .marketAccounts(List.of(new MarketAccount("hub", 1e7))).householdAccounts(households)
                .industryAccounts(facilities.stream().map(f -> IndustryAccount.empty(f.id()).withOperatingCash(1e7)).toList()).build();
    }

    private HouseholdAccount household(String profession, long count) {
        return new HouseholdAccount("site", "sol", "owner", "human", profession, count, 1e6,
                0, 0, 0, 0, Map.of(), 1, 1, 0, 0, null, null);
    }
    private IndustryAccount account(GameState state, String id) { return state.industryAccounts().stream().filter(a -> a.facilityId().equals(id)).findFirst().orElseThrow(); }
    private double stock(GameState state, String id) { var o = state.commercialHubs().getFirst().activeOrders().get(id); return o == null ? 0 : o.supplyKg(); }

    private String report(List<Measurement> rows) {
        var out = new StringBuilder("# Agricultural production and demand audit\n\n");
        out.append("Four 360-day probes use 1,000 ordinary human residents, real household purchases and profession payroll, finite opening stocks, application work and timed harvests, real fueled generation and power billing. Each has one tier-1 farm and one food-processing factory. All research is known. No goods or money are replenished externally. Public operating cash is seeded and subsidies are disabled. Input reserves cover 20 cycles except the shortage case's 2.25 nitrate cycles. Food opens at 14,000 kg and oxygen at 2,000 kg. Construction is a quoted sunk material bill, not a simulated build in these operating probes.\n\n")
                .append("| Application | Nitrate shortage | Harvests | Idle days | Food produced kg | Household food bought kg | Unmet food kg | Closing market food kg | Oxygen sold kg | Retained cultivation residue kg |\n|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var r : rows) out.append(String.format(Locale.ROOT, "| %s | %s | %d | %d | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f |\n",
                r.scenario.application(), r.scenario.starved(), r.harvests, r.idleDays, r.food, r.consumed, r.unmet, r.closingFood, r.oxygenSold, r.retainedResidue));
        out.append("\n## Actual operating transactions\n\nFarm and food factory combined; internal crop transfers retain the market spread. Generator fuel, wages and capital belong to the grid provider and are not added again to the farms' paid electricity.\n\n| Application | Shortage | Materials | Wages | Electricity | Maintenance | Sales | Cash operating result | Construction materials at baseline | Minimum facility cash |\n|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var r : rows) out.append(String.format(Locale.ROOT, "| %s | %s | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f |\n",
                r.scenario.application(), r.scenario.starved(), r.materials, r.wages, r.power, r.maintenance, r.revenue,
                r.revenue - r.materials - r.wages - r.power - r.maintenance, r.construction, r.minCash));
        out.append("\n## Local food cost and capital sensitivity\n\nAll operating costs minus actual non-food receipts divided by all food physically produced, including retained unsold food. Processing crop purchases and farm crop receipts retain the real market spread. Capital sensitivity spreads only the quoted farm and processor construction material bill over 5, 10 or 20 years of 360 operating days, without interest or salvage. Work, land and construction service are additional unpriced costs. These are scenario unit costs, not universal application break-even prices.\n\n| Application | Shortage | Operating credits/kg food | With 5-year capital | With 10-year capital | With 20-year capital | Retail break-even with 10-year capital |\n|---|---|---:|---:|---:|---:|---:|\n");
        for (var r : rows) {
            double unit = (r.materials + r.wages + r.power + r.maintenance - r.nonFoodCredits) / r.food;
            out.append(String.format(Locale.ROOT, "| %s | %s | %.2f | %.2f | %.2f | %.2f | %.2f |\n", r.scenario.application(),
                    r.scenario.starved(), unit, unit + r.construction / 5 / r.food, unit + r.construction / 10 / r.food,
                    unit + r.construction / 20 / r.food, (unit + r.construction / 10 / r.food) / IndustryMarketProcessor.WHOLESALE_SHARE));
        }
        out.append("\n## Grid provider funding\n\nThe fixed electricity tariff is not proof of a cost-covering grid. These are actual generator operating-cash changes after paid fuel, wages, maintenance and electricity receipts; no capital amortization is included. They are reported separately to avoid counting both fuel and purchased farm electricity as the same farm expense.\n\n| Application | Shortage | Generator fuel consumed kg | Generator operating-cash change |\n|---|---|---:|---:|\n");
        for (var r : rows) out.append(String.format(Locale.ROOT, "| %s | %s | %.2f | %.2f |\n", r.scenario.application(), r.scenario.starved(), r.gridFuelKg, r.gridCashResult));
        return out.append("\nNo cultivation residue is bought without a downstream consumer. Oxygen sales follow actual breathing and stockpile demand, not the farm's own output. Physical shortages can coexist with funded workers. Prices use the existing spot-price rule, not assumed premium contracts. Fixed population, opening savings, seeded reserves and a well-funded existing generator make this a bounded operating audit rather than a self-starting campaign. No transport occurs within these local probes; delivered imports are measured separately. Unsold stock is retained without modeled spoilage or producer storage costs. Public maintenance is charged; private maintenance remains an integration gap. Reproduction: `mvn -pl engine -Dtest=AgricultureEconomicsAuditTest test` with JDK 27.\n").toString();
    }
}
