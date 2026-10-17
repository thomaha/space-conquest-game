package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.market.MarketDemandProcessor;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.technology.ApplicationIntegration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Repeatable catalog review and physical factory-to-market accounting probes. */
class ProductionChainAuditTest {
    @Test
    void auditCatalogClosureAndRecipeEconomics() throws Exception {
        var technologies = DataModelLoader.loadTechnologies();
        var materials = DataModelLoader.loadMaterials();
        var applications = technologies.stream().flatMap(t -> t.applications().stream()).toList();
        var recipes = IndustryRecipeCatalog.all().stream()
                .sorted(Comparator.comparing(IndustryRecipeCatalog.Recipe::applicationId)).toList();
        var materialIds = new TreeSet<String>();
        materials.forEach(m -> assertTrue(materialIds.add(m.id()), "Duplicate material: " + m.id()));
        var researchIds = new TreeSet<String>();
        technologies.forEach(t -> assertTrue(researchIds.add(t.id()), "Duplicate foundation: " + t.id()));
        applications.forEach(a -> assertTrue(researchIds.add(a.id()), "Duplicate research ID: " + a.id()));
        for (var technology : technologies) {
            assertTrue(researchIds.containsAll(technology.requiredTechnologies()), technology.id());
            for (var app : technology.applications()) {
                assertTrue(researchIds.containsAll(app.requiredTechnologies()), app.id());
                assertTrue(materialIds.containsAll(app.requiredMaterials()), app.id());
            }
        }
        var reachable = closure(recipes, Set.of());
        var natural = new TreeSet<String>();
        materials.stream().filter(Material::foundInNature).forEach(m -> natural.add(m.id()));
        var conditional = closure(recipes, natural);
        var produced = new TreeSet<String>();
        recipes.forEach(r -> produced.addAll(r.outputsKg().keySet()));
        var unavailable = new TreeSet<>(materialIds);
        unavailable.removeAll(conditional);
        var noRecipe = new TreeSet<>(materialIds);
        noRecipe.removeAll(produced);
        var counts = new EnumMap<ApplicationIntegration.Status, Integer>(ApplicationIntegration.Status.class);
        applications.forEach(a -> counts.merge(ApplicationIntegration.describe(a).status(), 1, Integer::sum));
        var report = new StringBuilder("# Production catalog audit\n\n");
        report.append("Catalog: ").append(technologies.size()).append(" foundations, ")
                .append(applications.size()).append(" applications, ").append(materials.size())
                .append(" materials and ").append(recipes.size()).append(" production recipes, including cultivation.\n\n")
                .append("Consumer classifications reported by ApplicationIntegration: ").append(counts).append(". ")
                .append("These are metadata classifications, not proof of complete production chains.\n\n")
                .append("Reachable through industrial recipes with all research, facilities, power, labor and funding assumed: ")
                .append(reachable.size()).append(" materials. Deposit extraction, surface liquid water and declared atmospheric intake are assumed where required.\n\n")
                .append("Reachable when every natural material is additionally supplied by a hypothetical accessible small-body deposit and mining ship: ")
                .append(conditional.size()).append(" materials. This is a conditional upper bound, not a generated-campaign guarantee.\n\n")
                .append("Still unreachable under that upper bound: ").append(unavailable).append(".\n\n")
                .append("No industrial output recipe (includes natural goods potentially accessible to ships): ")
                .append(noRecipe).append(".\n\n");
        var researchReachable = new TreeSet<String>();
        boolean changed;
        do {
            changed = false;
            for (var technology : technologies) {
                if (researchReachable.containsAll(technology.requiredTechnologies()))
                    changed |= researchReachable.add(technology.id());
                for (var app : technology.applications())
                    if (researchReachable.contains(technology.id()) && researchReachable.containsAll(app.requiredTechnologies()))
                        changed |= researchReachable.add(app.id());
            }
        } while (changed);
        var researchBlocked = new TreeSet<>(researchIds); researchBlocked.removeAll(researchReachable);
        report.append("Research nodes unreachable from an empty knowledge set under catalog prerequisites: ")
                .append(researchBlocked).append(".\n\n");
        appendRecipes(report, recipes, researchIds, materialIds, reachable, conditional);
        appendApplications(report, technologies, conditional);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/production-chain-audit.md"), report);
    }

    private TreeSet<String> closure(List<IndustryRecipeCatalog.Recipe> recipes, Set<String> seeds) {
        var result = new TreeSet<>(seeds);
        boolean changed;
        do {
            changed = false;
            for (var recipe : recipes) if (result.containsAll(recipe.inputsKg().keySet()))
                changed |= result.addAll(recipe.outputsKg().keySet());
        } while (changed);
        return result;
    }

    private void appendRecipes(StringBuilder report, List<IndustryRecipeCatalog.Recipe> recipes,
                               Set<String> researchIds, Set<String> materialIds,
                               Set<String> reachable, Set<String> conditional) {
        report.append("## Recipe checks and baseline economics\n\n")
                .append("One complete recipe batch at tier 1-equivalent staffing; cultivation batches span their declared growing interval. Power includes the recipe's minimum operating tier. ")
                .append("Prices are MarketProcessor base credits/kg, not current spot prices. Sales assume all outputs sell at the 80% wholesale share. ")
                .append("Contribution excludes wages, maintenance, capital, transport and tax. Byproduct sales are optimistic. ")
                .append("Staffing is reference operator headcount for work-unit recipes and legacy headcount per daily batch otherwise. No research improvements are applied.\n\n")
                .append("| Recipe | Catalog application | Missing inputs: industrial / conditional | Reference staffing | kWh/batch | Input credits | Wholesale credits | Before labor and power | Input kg / output kg |\n")
                .append("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |\n");
        var appIds = new HashSet<String>();
        try {
            DataModelLoader.loadTechnologies().forEach(t -> t.applications().forEach(a -> appIds.add(a.id())));
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        for (var recipe : recipes) {
            assertTrue(researchIds.containsAll(recipe.requiredTechnologies()), recipe.applicationId());
            assertTrue(materialIds.containsAll(recipe.inputsKg().keySet()), recipe.applicationId());
            assertTrue(materialIds.containsAll(recipe.outputsKg().keySet()), recipe.applicationId());
            var missing = new TreeSet<>(recipe.inputsKg().keySet()); missing.removeAll(reachable);
            var blocked = new TreeSet<>(recipe.inputsKg().keySet()); blocked.removeAll(conditional);
            double input = value(recipe.inputsKg()), sales = value(recipe.outputsKg()) * IndustryMarketProcessor.WHOLESALE_SHARE;
            var work = ProductionWork.definition(recipe.applicationId());
            report.append("| ").append(recipe.applicationId()).append(" | ").append(appIds.contains(recipe.applicationId()))
                    .append(" | ").append(missing).append(" / ").append(blocked).append(" | ")
                    .append(work == null ? recipe.workersPerBatch() : work.equipment().referenceOperators()).append(" | ")
                    .append(number(work == null ? recipe.powerDrawKw() * 24 * recipe.minTier() : work.energyKwhPerBatch()))
                    .append(" | ").append(number(input)).append(" | ").append(number(sales))
                    .append(" | ").append(number(sales - input)).append(" | ")
                    .append(number(sum(recipe.inputsKg()))).append(" / ").append(number(sum(recipe.outputsKg()))).append(" |\n");
        }
    }

    private void appendApplications(StringBuilder report, List<Technology> technologies, Set<String> conditional) {
        report.append("\n## Application material coverage\n\n")
                .append("Declared requiredMaterials are names without quantities. Hardware receipts can differ and need separate review.\n\n")
                .append("| Foundation | Application | Reported consumer | Declared materials lacking even a conditional supply chain |\n")
                .append("| --- | --- | --- | --- |\n");
        for (var technology : technologies) for (var app : technology.applications()) {
            var missing = new TreeSet<>(app.requiredMaterials()); missing.removeAll(conditional);
            report.append("| ").append(technology.id()).append(" | ").append(app.id()).append(" | ")
                    .append(ApplicationIntegration.describe(app).consumer()).append(" | ").append(missing).append(" |\n");
        }
    }

    @Test
    void everyIndustrialRecipeTransfersPaidOutputToTheMarketWithoutCreatingMoney() throws Exception {
        var tech = DataModelLoader.loadTechnologies();
        var knowledge = new ArrayList<String>();
        tech.forEach(t -> { knowledge.add(t.id()); t.applications().forEach(a -> knowledge.add(a.id())); });
        var systems = DataModelLoader.loadSolarSystems();
        var environment = GameState.builder().solarSystems(systems).build();
        var sites = new ArrayList<String>();
        systems.forEach(system -> system.planets().forEach(planet -> {
            sites.add(planet.id()); planet.moons().forEach(moon -> sites.add(moon.id()));
        }));
        for (var recipe : IndustryRecipeCatalog.all()) {
            String body = sites.stream().filter(id -> recipe.infrastructure() == null || recipe.infrastructure().permitsSite(environment, id))
                    .filter(id -> recipe.farming() == null || recipe.farming().availableHectares(environment, id) > 0)
                    .filter(id -> ProcessInput.environmentAvailable(environment, id, recipe.inputSources())).findFirst()
                    .orElseThrow(() -> new AssertionError("No compatible audit site for " + recipe.applicationId()));
            int tier = recipe.minTier();
            var work = ProductionWork.definition(recipe.applicationId());
            var facility = new IndustrialFacility("factory", body, recipe.applicationId(), "corp",
                    IndustrialFacility.PRIVATE_CORPORATE, tier, recipe.workersPerBatch(),
                    work == null ? "industrial_worker" : work.equipment().operatorProfession(), false, 0);
            var orders = new HashMap<String, MarketOrder>();
            recipe.outputsKg().keySet().forEach(id -> orders.put(id, new MarketOrder(id, 0, 1e8, MarketProcessor.basePricePerKg(id), 0)));
            recipe.inputsKg().forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg * 1000, 1e8, MarketProcessor.basePricePerKg(id), 0)));
            double stored = orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum();
            var deposits = new ArrayList<GeologicalDeposit>();
            recipe.inputSources().stream().filter(input -> input.source() == ProcessInput.Source.DEPOSIT).forEach(input -> deposits.add(
                    new GeologicalDeposit("deposit_" + input.materialId(), body, input.materialId(), 1e9, 1e9, 1, true, "corp")));
            var state = GameState.builder().solarSystems(systems)
                    .empires(List.of(new Empire("empire", "Empire", "human", "Individualist", 0, 0,
                            List.of(), List.of(), Map.of(), knowledge, List.of())))
                    .corporations(List.of(new Corporation("corp", "Corp", "empire", body, "INDUSTRY", 1e12,
                            List.of("factory"), List.of(), List.of())))
                    .industrialFacilities(List.of(facility)).geologicalDeposits(deposits)
                    .commercialHubs(List.of(new CommercialHub("hub", body, 0, 1e12, stored, 10, orders)))
                    .marketAccounts(List.of(new MarketAccount("hub", 1e12))).build();
            var refreshed = state.withCommercialHubs(new MarketDemandProcessor().refresh(state, DataModelLoader.loadRaces()));
            for (String id : recipe.outputsKg().keySet())
                if (com.spaceconquest.engine.market.MarketStockpilePolicy.baseTargetKg(id) == 0 && !recipe.inputsKg().containsKey(id))
                    assertEquals(0, refreshed.commercialHubs().getFirst().activeOrders().get(id).demandKg(), recipe.applicationId());
            var result = new IndustryMarketProcessor().process(state, Map.of("factory", recipe.workersPerBatch()), Map.of());
            for (int day = 1; recipe.farming() != null && day < recipe.farming().growingDays(); day++) {
                state = state.toBuilder().turn(day).empires(result.empires()).corporations(result.corporations())
                        .commercialHubs(result.hubs()).marketAccounts(result.marketAccounts()).industryAccounts(result.industryAccounts()).build();
                result = new IndustryMarketProcessor().process(state, Map.of("factory", recipe.workersPerBatch()), Map.of());
            }
            var account = result.industryAccounts().getFirst();
            for (String id : recipe.outputsKg().keySet()) {
                double output = account.producedKg().getOrDefault(id, 0.0);
                assertTrue(output > 0, recipe.applicationId() + ": " + id);
                assertEquals(output, account.soldKg().getOrDefault(id, 0.0), 1e-6, recipe.applicationId());
                double initial = orders.get(id).supplyKg();
                double consumed = recipe.inputsKg().getOrDefault(id, 0.0)
                        * output / recipe.outputsKg().get(id);
                assertEquals(initial - consumed + output,
                        result.hubs().getFirst().activeOrders().get(id).supplyKg(), 1e-6, recipe.applicationId());
            }
            double money = result.corporations().getFirst().liquidCapitalReserves()
                    + result.marketAccounts().getFirst().unsettledSalesCredits();
            assertEquals(2e12 - account.maintenanceCostsCredits(), money, 0.01, recipe.applicationId());
            assertTrue(account.unsoldStockKg().isEmpty(), recipe.applicationId());
        }
    }

    private double value(Map<String, Double> amounts) {
        return amounts.entrySet().stream().mapToDouble(e -> e.getValue() * MarketProcessor.basePricePerKg(e.getKey())).sum();
    }
    private double sum(Map<String, Double> amounts) { return amounts.values().stream().mapToDouble(Double::doubleValue).sum(); }
    private String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
