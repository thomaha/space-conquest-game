package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorporateProfitTaxProcessorTest {
    private final CorporateProfitTaxProcessor processor = new CorporateProfitTaxProcessor();

    @Test
    void taxesRealizedNetProfitAfterLossCarryforwardAndCreditsLocalBody() {
        GameState state = state(500.0,
                List.of(account("factory", 200.0, 0.0, 0.0),
                        account("mine", 0.0, 50.0, 0.0)),
                new CorporateTaxAccount("corp", 25.0, 0.0, 0.0, 0.0, 0.0));

        var result = processor.process(state);
        CorporateTaxAccount tax = result.accounts().getFirst();
        assertEquals(125.0, tax.taxableProfitCredits(), 0.001);
        assertEquals(25.0, tax.assessedTaxCredits(), 0.001);
        assertEquals(25.0, tax.paidTaxCredits(), 0.001);
        assertEquals(0.0, tax.lossCarryforwardCredits(), 0.001);
        assertEquals(475.0, result.corporations().getFirst().liquidCapitalReserves(), 0.001);
        assertEquals(25.0, result.collectedByBody().get("earth"), 0.001);
    }

    @Test
    void unpaidTaxStaysWithCorporationAndLaterCashPaysItWithoutRetaxingLoss() {
        GameState first = state(5.0, List.of(account("factory", 100.0, 0.0, 0.0)),
                new CorporateTaxAccount("corp", 0.0, 0.0, 0.0, 0.0, 0.0));
        var assessed = processor.process(first);
        assertEquals(15.0, assessed.accounts().getFirst().unpaidTaxCredits(), 0.001);
        assertEquals(5.0, assessed.collectedByBody().get("earth"), 0.001);

        Corporation funded = new Corporation("corp", "Corp", "empire", "earth", "INDUSTRY",
                20.0, List.of("factory", "mine"), List.of(), List.of());
        var settled = processor.process(first.toBuilder().corporations(List.of(funded))
                .industryAccounts(List.of(account("factory", 0.0, 30.0, 0.0)))
                .corporateTaxAccounts(assessed.accounts()).build());
        assertEquals(0.0, settled.accounts().getFirst().taxableProfitCredits(), 0.001);
        assertEquals(30.0, settled.accounts().getFirst().lossCarryforwardCredits(), 0.001);
        assertEquals(15.0, settled.accounts().getFirst().paidTaxCredits(), 0.001);
        assertEquals(5.0, settled.corporations().getFirst().liquidCapitalReserves(), 0.001);
    }

    @Test
    void unpopulatedFacilityProfitIsCreditedToPopulatedHeadquarters() {
        var result = processor.process(state(100.0,
                List.of(account("mine", 100.0, 0.0, 0.0)),
                new CorporateTaxAccount("corp", 0.0, 0.0, 0.0, 0.0, 0.0)));

        assertEquals(Map.of("earth", 20.0), result.collectedByBody());
    }

    @Test
    void taxRemainsDueWhenNoMunicipalityCanReceiveIt() {
        GameState state = state(100.0, List.of(account("factory", 100.0, 0.0, 0.0)),
                new CorporateTaxAccount("corp", 0.0, 0.0, 0.0, 0.0, 0.0))
                .toBuilder().solarSystems(List.of()).build();

        var result = processor.process(state);
        assertEquals(20.0, result.accounts().getFirst().unpaidTaxCredits(), 0.001);
        assertEquals(0.0, result.accounts().getFirst().paidTaxCredits(), 0.001);
        assertEquals(100.0, result.corporations().getFirst().liquidCapitalReserves(), 0.001);
        assertEquals(Map.of(), result.collectedByBody());
    }

    private GameState state(double reserves, List<IndustryAccount> accounts, CorporateTaxAccount previous) {
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 0.0,
                0.2, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        Corporation corporation = new Corporation("corp", "Corp", "empire", "earth", "INDUSTRY",
                reserves, List.of("factory", "mine"), List.of(), List.of());
        IndustrialFacility factory = facility("factory", "earth");
        IndustrialFacility mine = facility("mine", "moon");
        Planet earth = new Planet("earth", "Earth", "", 1.0, 1.0, 1.0, 0.0, 1.0,
                "TERRESTRIAL", "BREATHABLE", true, 0.7, List.of(), List.of(),
                List.of(new Population("human", Map.of(25, 100L))));
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0.0, 0.0, 0.0,
                1.0, 1.0, "Yellow", List.of(earth), List.of());
        return GameState.builder().empires(List.of(empire)).corporations(List.of(corporation))
                .solarSystems(List.of(sol))
                .industrialFacilities(List.of(factory, mine)).industryAccounts(accounts)
                .corporateTaxAccounts(List.of(previous)).build();
    }

    private IndustrialFacility facility(String id, String body) {
        return new IndustrialFacility(id, body, "consumer_goods_mfg", "corp",
                IndustrialFacility.PRIVATE_CORPORATE, 1, 100, "industrial_worker", false, 0.0);
    }

    private IndustryAccount account(String id, double sales, double inputs, double wages) {
        return new IndustryAccount(id, Map.of(), Map.of(), Map.of(), inputs, wages, sales, 0.0, 0.0, 0.0);
    }
}
