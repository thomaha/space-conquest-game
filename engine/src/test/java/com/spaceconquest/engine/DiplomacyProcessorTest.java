package com.spaceconquest.engine;

import com.spaceconquest.engine.governance.DiplomacyProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DiplomacyProcessorTest {

    private DiplomacyProcessor diplomacyProcessor;
    private Race humanRace;
    private Race siliconRace;

    @BeforeEach
    public void setUp() throws IOException {
        diplomacyProcessor = new DiplomacyProcessor();
        List<Race> races = DataModelLoader.loadRaces();
        humanRace = races.stream().filter(r -> r.id().equals("human")).findFirst().orElseThrow();
        siliconRace = races.stream().filter(r -> r.id().equals("silicon_core")).findFirst().orElseThrow();
    }

    @Test
    public void testTerritorialInfluenceFormula() {
        // Populated Earth: 8 billion citizens, 1.5 module factor, 0.5 hangar mod
        long pop = 8_000_000_000L;
        double influence = diplomacyProcessor.calculateInfluenceValue(pop, 1.5, 0.5);
        double expected = (Math.log10((double) pop) * 1.5) + 0.5;
        assertEquals(expected, influence, 0.001);
        assertTrue(influence > 10.0);

        // Unpopulated colony / deep space asteroid -> 0.0 influence
        double zeroInfluence = diplomacyProcessor.calculateInfluenceValue(0, 1.5, 0.5);
        assertEquals(0.0, zeroInfluence);
    }

    @Test
    public void testDiplomaticTierTransitionsAndTariffDiscounts() {
        List<DiplomaticRelation> relations = List.of();

        // 1. Initial baseline is Neutral
        String defaultTier = diplomacyProcessor.getDiplomaticTier("terran", "silicon", relations);
        assertEquals(DiplomacyProcessor.NEUTRAL, defaultTier);
        assertEquals(0.0, diplomacyProcessor.getDefaultTariffDiscount(DiplomacyProcessor.NEUTRAL));

        // 2. Transition to Commercial Alliance -> 50% discount
        relations = diplomacyProcessor.setDiplomaticTier("terran", "silicon", DiplomacyProcessor.COMMERCIAL_ALLIANCE, relations);
        assertEquals(DiplomacyProcessor.COMMERCIAL_ALLIANCE, diplomacyProcessor.getDiplomaticTier("terran", "silicon", relations));
        assertEquals(0.50, relations.getFirst().mutualTariffDiscount(), 0.001);

        // 3. Transition to Integrated Federation -> 100% discount
        relations = diplomacyProcessor.setDiplomaticTier("terran", "silicon", DiplomacyProcessor.INTEGRATED_FEDERATION, relations);
        assertEquals(DiplomacyProcessor.INTEGRATED_FEDERATION, diplomacyProcessor.getDiplomaticTier("terran", "silicon", relations));
        assertEquals(1.00, relations.getFirst().mutualTariffDiscount(), 0.001);

        // 4. Transition to Total War -> 0% discount
        relations = diplomacyProcessor.setDiplomaticTier("terran", "silicon", DiplomacyProcessor.TOTAL_WAR, relations);
        assertEquals(DiplomacyProcessor.TOTAL_WAR, diplomacyProcessor.getDiplomaticTier("terran", "silicon", relations));
        assertEquals(0.00, relations.getFirst().mutualTariffDiscount(), 0.001);
    }

    @Test
    public void testNutrientCompatibilityTradeBarrier() {
        // 1. Inorganic freight (titanium / silicon) is always compatible
        assertTrue(diplomacyProcessor.isTradePermitted("titanium", humanRace, siliconRace, "", "", false, 0));
        assertTrue(diplomacyProcessor.isTradePermitted("refined_silicon", humanRace, siliconRace, "", "", false, 0));

        // 2. Organic food between humans (both Organic) is compatible
        assertTrue(diplomacyProcessor.isTradePermitted("organic_food", humanRace, humanRace, "", "", false, 0));

        // 3. Organic food exported to Silicon Core (Rock nutrient) without Xenobiology Lab is blocked
        assertFalse(diplomacyProcessor.isTradePermitted("organic_food", humanRace, siliconRace, "", "", false, 0));

        // 4. With Xenobiology Lab active, nutrient trade is permitted
        assertTrue(diplomacyProcessor.isTradePermitted("organic_food", humanRace, siliconRace, "", "", true, 10));
    }
}
