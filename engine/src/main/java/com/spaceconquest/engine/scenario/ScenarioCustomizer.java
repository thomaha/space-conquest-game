package com.spaceconquest.engine.scenario;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.community.GalacticResolution;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedurally configures a new game world and starting empires according to campaign setup options.
 */
public class ScenarioCustomizer {

    /**
     * Creates an initialized GameState customized with the selected campaign scenario parameters.
     */
    public GameState createCustomGameState(CampaignSetup setup) {
        if (setup == null) {
            setup = CampaignSetup.createDefault();
        }

        List<SolarSystem> systems = List.of();
        List<Empire> loadedEmpires = List.of();
        List<com.spaceconquest.engine.Corporation> loadedCorps = List.of();

        try {
            systems = DataModelLoader.loadSolarSystems();
            loadedEmpires = DataModelLoader.loadEmpires();
            loadedCorps = DataModelLoader.loadCorporations();
        } catch (java.io.IOException e) {
            // Fallback to empty defaults
        }

        List<Empire> customizedEmpires = new ArrayList<>();

        double startingCreditsBonus = switch (setup.startingTechTier()) {
            case 2 -> 25000.0;
            case 3 -> 75000.0;
            case 4 -> 150000.0;
            default -> 0.0;
        };

        for (Empire emp : loadedEmpires) {
            double initialTreasury = emp.treasuryCredits() + startingCreditsBonus;
            customizedEmpires.add(new Empire(
                    emp.id(), emp.name(), emp.raceId(), emp.societyStructure(),
                    initialTreasury, emp.corporateTaxRate(), emp.controlledSystemIds(),
                    emp.ministries(), emp.systemGovernorAssignments(),
                    emp.unlockedTechIds(), emp.activeShipDesignIds()
            ));
        }

        // Initialize Galactic Senate Assembly if multiple empires exist
        List<String> memberIds = customizedEmpires.stream().map(Empire::id).toList();
        GalacticCommunity initialCommunity = new GalacticCommunity(
                "galactic_senate",
                "Grand Interstellar Senate",
                memberIds,
                List.of(
                        new GalacticResolution(
                                "res_anti_piracy_init",
                                "Pan-Galactic Anti-Piracy Treaty",
                                GalacticResolution.TYPE_ANTI_PIRACY,
                                memberIds.isEmpty() ? "terran_confederation" : memberIds.get(0),
                                "",
                                5,
                                GalacticResolution.STATUS_PROPOSED,
                                java.util.Map.of()
                        )
                ),
                List.of(),
                List.of(),
                10,
                10
        );

        return GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(systems)
                .empires(customizedEmpires)
                .corporations(loadedCorps)
                .build();
    }
}
