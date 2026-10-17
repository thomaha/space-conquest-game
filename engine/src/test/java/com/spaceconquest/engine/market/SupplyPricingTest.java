package com.spaceconquest.engine.market;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyPricingTest {
    @Test void computationalDustDoesNotSetAFullMarketReplacementPrice() throws Exception {
        var state = world(); var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        var nitrate = orders.get("nitrates"); orders.put("nitrates", new MarketOrder("nitrates", 1e-20, nitrate.demandKg(), nitrate.pricePerKg(), 0));
        var exhausted = hub.withInventory(0, hub.currentStoredWeightKg(), orders);
        assertTrue(FacilitySupplyPricing.offers(state.withCommercialHubs(List.of(exhausted)), exhausted, .5).isEmpty());
        orders = new HashMap<>(hub.activeOrders());
        double buffer = 100 * (30 + FacilitySupplyPricing.offers(state, hub, .5).stream()
                .filter(o -> o.materialId().equals("food_matrix")).mapToDouble(SupplyOffer::deliveryDays).max().orElse(0));
        double stocked = buffer - Math.ulp(buffer);
        orders.put("food_matrix", new MarketOrder("food_matrix", stocked, 100, 4, 0));
        var filled = state.withCommercialHubs(List.of(hub.withInventory(0, hub.currentStoredWeightKg() + stocked, orders)));
        assertEquals(new MarketProcessor().calculateSpotPrice(4, stocked, 100),
                new MarketProcessor().updateCommercialHubs(filled, filled.commercialHubs()).getFirst().activeOrders().get("food_matrix").pricePerKg());
    }

    @Test void plantsOwnedByOneBusinessPoolCostsBeforeQuotingTheirElectricity() throws Exception {
        var state = world(); var generator = state.industrialFacilities().getLast();
        var second = new IndustrialFacility("small-plant", generator.planetId(), generator.applicationId(), generator.ownerEntityId(),
                generator.ownershipType(), generator.tier(), generator.allocatedWorkers(), generator.workerProfessionId(), false, 0);
        state = state.toBuilder().industrialFacilities(List.of(generator, second)).build();
        var accounts = List.of(new IndustryAccount(generator.id(), Map.of(), Map.of(), Map.of(), 100, 1000, 0, 0, 100000, 0),
                new IndustryAccount(second.id(), Map.of(), Map.of(), Map.of(), 0, 1000, 0, 0, 1, 0));
        var balance = new PowerProcessor.DayResult(List.of(new PowerGridState("site", 100001.0 / 24, 100001.0 / 24, 0, 0, 0, false)), Map.of());
        double reserve = 2 * FacilitySupplyPricing.capital(state, state.commercialHubs().getFirst(), generator)
                / SupplyPricingPolicy.current().capitalRecoveryDays();
        double costs = 2100 + 2 * FacilitySupplyPricing.declaredMaintenance(generator);
        double expected = SupplyPricingPolicy.current().requiredReceipts(costs, reserve, .25) / 100001;
        assertEquals(expected, GridSupplyPricing.tariffs(state, accounts, balance).get("site"), 1e-10);
        var idlePlant = new IndustryAccount(second.id(), Map.of(), Map.of(), Map.of(), 0, 1000, 0, 0, 0, 0);
        var idleBalance = new PowerProcessor.DayResult(List.of(new PowerGridState("site", 100000.0 / 24, 100000.0 / 24, 0, 0, 0, false)), Map.of());
        assertEquals(SupplyPricingPolicy.current().requiredReceipts(costs, reserve, .25) / 100000,
                GridSupplyPricing.tariffs(state, List.of(accounts.getFirst(), idlePlant), idleBalance).get("site"), 1e-10);
    }

    @Test void targetMarginSurvivesProfitTaxAndCapitalWithoutDeductingCapitalFromTaxableEarnings() {
        var policy = SupplyPricingPolicy.current();
        double receipts = policy.requiredReceipts(100, 20, .25);
        double tax = (receipts - 100) * .25;
        assertEquals(receipts * policy.targetNetMargin(), receipts - 100 - tax - 20, 1e-10);
        assertTrue(policy.requiredReceipts(100, 20, .4) > receipts);
        assertEquals(Double.POSITIVE_INFINITY, policy.requiredReceipts(100, 20, 1));
    }

    @Test void realizedCorporateTaxSettlementPreservesTheQuotedNetMarginAndHonorsExistingLosses() throws Exception {
        var state = world(); var system = state.solarSystems().getFirst(); var p = system.planets().getFirst();
        var inhabited = new Planet(p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(), p.inclination(), p.diameter(),
                p.type(), p.atmosphere(), p.hasLiquidWater(), p.waterLevel(), p.resources(), p.moons(), List.of(new Population("human", Map.of(25, 1L))));
        double receipts = SupplyPricingPolicy.current().requiredReceipts(100, 20, .25);
        state = state.toBuilder().solarSystems(List.of(new SolarSystem(system.id(), system.name(), system.description(), system.x(), system.y(), system.z(),
                system.sunMass(), system.sunDiameter(), system.sunColor(), List.of(inhabited), system.asteroidBelts())))
                .industryAccounts(List.of(new IndustryAccount("farm", Map.of(), Map.of(), Map.of(), 100, 0, receipts, 0, 0, 0))).build();
        var taxed = new CorporateProfitTaxProcessor().process(state);
        double paid = taxed.accounts().getFirst().paidTaxCredits();
        assertEquals((receipts - 100) * .25, paid, 1e-9);
        assertEquals(receipts * SupplyPricingPolicy.current().targetNetMargin(), receipts - 100 - paid - 20, 1e-9);
        assertEquals(paid, taxed.collectedByBody().get("site"), 1e-9);
        var sheltered = new CorporateProfitTaxProcessor().process(state.toBuilder().corporateTaxAccounts(
                List.of(new CorporateTaxAccount("corp", 1000, 0, 0, 0, 0))).build());
        assertEquals(0, sheltered.accounts().getFirst().assessedTaxCredits());
    }

    @Test void mixedPrimaryOutputsShareOneBillAndSpeculativeByproductsDoNotReduceIt() throws Exception {
        var state = world(); var hub = state.commercialHubs().getFirst();
        var offers = FacilitySupplyPricing.offers(state, hub, .5);
        assertEquals(Set.of("food_matrix", "harvested_crops"), offers.stream().map(SupplyOffer::materialId).collect(java.util.stream.Collectors.toSet()));
        double receipts = offers.stream().mapToDouble(o -> o.quantityKg() * o.retailPricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE).sum();
        double operating = offers.stream().mapToDouble(SupplyOffer::operatingCosts).sum();
        double reserve = offers.stream().mapToDouble(SupplyOffer::capitalReserve).sum();
        assertEquals(receipts * SupplyPricingPolicy.current().targetNetMargin(), receipts - operating - (receipts - operating) * .25 - reserve, 1e-7);
        var orders = new HashMap<>(hub.activeOrders());
        orders.put("oxygen_gas", new MarketOrder("oxygen_gas", 0, 1e9, MarketProcessor.basePricePerKg("oxygen_gas"), 0));
        orders.put("cultivation_residue", new MarketOrder("cultivation_residue", 0, 1e9, 1e6, 0));
        var changed = hub.withInventory(0, hub.currentStoredWeightKg(), orders);
        assertEquals(offers, FacilitySupplyPricing.offers(state.withCommercialHubs(List.of(changed)), changed, .5));
    }

    @Test void inputPricesCarryUpstreamMarginsOnceAndMissingInputsOrFundsRemoveTheOffer() throws Exception {
        var state = world(); var hub = state.commercialHubs().getFirst();
        double first = totalReceipts(FacilitySupplyPricing.offers(state, hub, .5));
        var orders = new HashMap<>(hub.activeOrders()); var co2 = orders.get("carbon_dioxide_gas");
        orders.put(co2.resourceId(), new MarketOrder(co2.resourceId(), co2.supplyKg(), co2.demandKg(), co2.pricePerKg() + 1, 0));
        var changed = hub.withInventory(0, hub.currentStoredWeightKg(), orders);
        double second = totalReceipts(FacilitySupplyPricing.offers(state.withCommercialHubs(List.of(changed)), changed, .5));
        double addedInput = IndustryRecipeCatalog.find("hydroponic_growth_arrays").inputsKg().get(co2.resourceId()) / 28;
        assertEquals(addedInput * (1 - .25) / (1 - .25 - SupplyPricingPolicy.current().targetNetMargin()), second - first, 1e-7);
        orders.put("nitrates", new MarketOrder("nitrates", 0, 100, 2, 0)); changed = hub.withInventory(0, hub.currentStoredWeightKg(), orders);
        assertTrue(FacilitySupplyPricing.offers(state.withCommercialHubs(List.of(changed)), changed, .5).isEmpty());
        var c = state.corporations().getFirst();
        var poor = state.toBuilder().corporations(List.of(new Corporation(c.id(), c.name(), c.empireId(), c.headquartersEntityId(),
                c.marketOrientation(), 0, c.ownedFacilityIds(), c.ownedShipIds(), c.claimedVeinIds()))).build();
        assertTrue(FacilitySupplyPricing.offers(poor, hub, .5).isEmpty());
    }

    @Test void scarceFoodCanExceedTheOldCapWhileQuotesNeverCreateStockCashOrGuaranteedSales() throws Exception {
        var state = world(); var hubs = new MarketProcessor().updateCommercialHubs(state, state.commercialHubs());
        assertTrue(hubs.getFirst().activeOrders().get("food_matrix").pricePerKg() > 8);
        assertEquals(0, hubs.getFirst().activeOrders().get("food_matrix").supplyKg());
        assertEquals(1e7, state.marketAccounts().getFirst().unsettledSalesCredits());
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        orders.put("food_matrix", new MarketOrder("food_matrix", 1e6, 100, 4, 0));
        var abundant = state.withCommercialHubs(List.of(hub.withInventory(0, hub.currentStoredWeightKg() + 1e6, orders)));
        assertTrue(new MarketProcessor().updateCommercialHubs(abundant, abundant.commercialHubs()).getFirst()
                .activeOrders().get("food_matrix").pricePerKg() < 4);
        assertTrue(state.industryAccounts().stream().allMatch(a -> a.soldKg().isEmpty()));
    }

    @Test void anInstalledFarmWithoutAvailableGridEnergyCannotAdvertiseProduction() throws Exception {
        var state = world(); var farm = state.industrialFacilities().getFirst();
        var unpowered = state.toBuilder().industrialFacilities(List.of(farm)).build();
        assertTrue(FacilitySupplyPricing.offers(unpowered, unpowered.commercialHubs().getFirst(), .02).isEmpty());
        var price = new MarketProcessor().updateCommercialHubs(unpowered, unpowered.commercialHubs()).getFirst()
                .activeOrders().get("food_matrix").pricePerKg();
        assertTrue(price <= 8);
        assertEquals(0, unpowered.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg());
    }

    @Test void meteredGridPaymentsCoverProviderCostsTaxAndCapitalAtTheQuotedLoad() throws Exception {
        var state = world(); var f = state.industrialFacilities().getFirst();
        var generator = new IndustrialFacility("generator", "site", "nuclear_power_app", "corp", "PRIVATE_CORPORATE", 1, 100, "technician", false, 0);
        state = state.toBuilder().industrialFacilities(List.of(f, generator)).build();
        var workers = Map.of(f.id(), 4, generator.id(), 100); var wages = Map.of(f.id(), 40.0, generator.id(), 2500.0);
        var power = new PowerGenerationProcessor().process(state, workers, wages);
        var ready = state.toBuilder().corporations(power.corporations()).commercialHubs(power.hubs()).marketAccounts(power.marketAccounts())
                .industryAccounts(power.industryAccounts()).build();
        var balance = new PowerProcessor().balanceDay(ready, power.generationKw(), workers);
        var tariffs = GridSupplyPricing.tariffs(ready, power.accounts(), balance);
        assertTrue(tariffs.get("site") > .02);
        var billing = new PowerBillingProcessor().process(ready, balance, power.accounts(), workers);
        var result = new IndustryMarketProcessor().process(ready.toBuilder().corporations(billing.corporations()).industryAccounts(billing.industryAccounts()).build(),
                billing.poweredWorkers(), wages, workers, billing.productionEnergy());
        double received = result.productionPlantSales().get("generator");
        double cost = power.accounts().getFirst().inputCostsCredits() + 2500 + FacilitySupplyPricing.declaredMaintenance(generator);
        double reserves = FacilitySupplyPricing.capital(ready, ready.commercialHubs().getFirst(), generator) / SupplyPricingPolicy.current().capitalRecoveryDays();
        assertEquals(received * SupplyPricingPolicy.current().targetNetMargin(), received - cost - .25 * (received - cost) - reserves, 1e-6);
        assertEquals(received, result.industryAccounts().stream().filter(a -> a.facilityId().equals("farm")).findFirst().orElseThrow().powerCostsCredits(), 1e-6);
    }

    private double totalReceipts(List<SupplyOffer> offers) { return offers.stream().mapToDouble(o -> o.quantityKg() * o.retailPricePerKg() * .8).sum(); }
    private GameState world() throws Exception {
        var known = new ArrayList<String>(); DataModelLoader.loadTechnologies().forEach(t -> { known.add(t.id()); t.applications().forEach(a -> known.add(a.id())); });
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1e8, .25, List.of("sol"), List.of(), Map.of(), known, List.of());
        var corporation = new Corporation("corp", "Grower", "owner", "site", "AGRICULTURE", 1e8, List.of("farm"), List.of(), List.of());
        var planet = new Planet("site", "Dry site", "", 5.97e24, 9.81, SolarRadiation.AU_KM, 0, 12742, "barren", "none", false, 0, List.of(), List.of(), List.of());
        var recipe = IndustryRecipeCatalog.find("hydroponic_growth_arrays"); var orders = new HashMap<String, MarketOrder>();
        recipe.inputsKg().forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg * 20, 100, MarketProcessor.basePricePerKg(id), 0)));
        orders.put("refined_uranium", new MarketOrder("refined_uranium", 10000, 0, 100, 0));
        orders.put("food_matrix", new MarketOrder("food_matrix", 0, 100, 4, 0));
        orders.put("harvested_crops", new MarketOrder("harvested_crops", 0, 100, 2, 0));
        var hub = new CommercialHub("hub", "site", 0, 1e8, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders);
        var facility = new IndustrialFacility("farm", "site", recipe.applicationId(), "corp", "PRIVATE_CORPORATE", 1, 4, "farmer", false, 0);
        return GameState.builder().solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700,
                        "yellow", List.of(planet), List.of()))).empires(List.of(empire)).corporations(List.of(corporation))
                .industrialFacilities(List.of(facility, new IndustrialFacility("generator", "site", "nuclear_power_app", "corp", "PRIVATE_CORPORATE", 1, 100, "technician", false, 0)))
                .commercialHubs(List.of(hub)).marketAccounts(List.of(new MarketAccount("hub", 1e7))).build();
    }
}
