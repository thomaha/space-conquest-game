package com.spaceconquest.engine.megastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MegastructureProcessorTest {

    private MegastructureProcessor processor;

    @BeforeEach
    public void setup() {
        processor = new MegastructureProcessor();
    }

    @Test
    public void testDysonSwarmStageAdvanceAndEnergyOutput() {
        Megastructure swarm = new Megastructure(
                "dyson_sol", "Sol Dyson Swarm", Megastructure.TYPE_DYSON_SWARM,
                "sol", "sol_star", "terran_confederation",
                0, 2, 4.0, 5.0, false, 0.0, Map.of(), 0
        );

        MegastructureProcessor.MegastructureTurnResult result = processor.processMegastructures(List.of(swarm));

        assertNotNull(result);
        assertEquals(1, result.updatedMegastructures().size());
        Megastructure updated = result.updatedMegastructures().get(0);
        assertEquals(1, updated.currentStage());
        assertTrue(updated.isOperational());
        assertEquals(25000.0, updated.energyYieldKw(), 0.01);
        assertEquals(25000.0, result.totalEnergyYieldByEmpireKw().get("terran_confederation"), 0.01);
    }

    @Test
    public void testStarLifterYieldsOreAndRadioisotopes() {
        Megastructure lifter = new Megastructure(
                "lifter_sol", "Sol Stellar Lifter", Megastructure.TYPE_STAR_LIFTER,
                "sol", "sol_star", "terran_confederation",
                2, 2, 0.0, 10.0, true, 0.0,
                Map.of("iron_ore", 4000.0, "fissile_radioisotopes", 200.0), 0
        );

        MegastructureProcessor.MegastructureTurnResult result = processor.processMegastructures(List.of(lifter));

        assertNotNull(result);
        Map<String, Double> mats = result.harvestedMaterialsByEmpireKg().get("terran_confederation");
        assertNotNull(mats);
        assertEquals(4000.0, mats.get("iron_ore"), 0.01);
        assertEquals(200.0, mats.get("fissile_radioisotopes"), 0.01);
    }

    @Test
    public void testHyperlaneGatewayInstantTransit() {
        Megastructure g1 = new Megastructure(
                "gate_sol", "Sol Gateway", Megastructure.TYPE_HYPERLANE_GATEWAY,
                "sol", "sol_star", "terran_confederation",
                1, 1, 0.0, 10.0, true, 0.0, Map.of(), 0
        );

        Megastructure g2 = new Megastructure(
                "gate_alpha", "Alpha Centauri Gateway", Megastructure.TYPE_HYPERLANE_GATEWAY,
                "alpha_centauri", "alpha_centauri_star", "terran_confederation",
                1, 1, 0.0, 10.0, true, 0.0, Map.of(), 0
        );

        List<Megastructure> gates = List.of(g1, g2);

        assertTrue(processor.canInstantTransitViaGateway("sol", "alpha_centauri", gates));
        assertFalse(processor.canInstantTransitViaGateway("sol", "sirius", gates));
    }
}
