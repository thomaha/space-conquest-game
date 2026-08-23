package com.spaceconquest.engine.scenario;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.Race;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds validated custom sovereign empires and races from a CustomEmpireProfile.
 */
public class CustomEmpireBuilder {

    public static final int MAX_TRAIT_POINTS = 5;

    public boolean validateProfile(CustomEmpireProfile profile) {
        if (profile == null) return false;
        if (profile.empireId() == null || profile.empireId().trim().isEmpty()) return false;
        if (profile.empireName() == null || profile.empireName().trim().isEmpty()) return false;

        // Validate ethics points
        if (profile.ethics() != null && !profile.ethics().isValid()) {
            return false;
        }

        // Validate trait points
        int pointsSpent = calculateTraitPointsSpent(profile.selectedTraitIds());
        return pointsSpent <= MAX_TRAIT_POINTS;
    }

    public int calculateTraitPointsSpent(List<String> traitIds) {
        if (traitIds == null) return 0;
        int total = 0;
        Map<String, SpeciesTrait> traitMap = getTraitMap();
        for (String id : traitIds) {
            SpeciesTrait t = traitMap.get(id);
            if (t != null) {
                total += t.pointCost();
            }
        }
        return total;
    }

    public Race buildRace(CustomEmpireProfile profile) {
        if (profile == null) profile = CustomEmpireProfile.createDefault();

        double intel = 1.0;
        double strength = 1.0;
        int lifespan = 80;
        int fertileStart = 16;
        int fertileEnd = 50;

        String nutrientType = "Organic";
        String atmosphere = "Oxygen";
        String chem = "Carbon";

        if (CustomEmpireProfile.BIO_SILICON_LITHOVORE.equalsIgnoreCase(profile.biochemicalType())) {
            chem = "Silicon";
            nutrientType = "Rock";
            atmosphere = "Nitrogen";
            intel = 1.1;
            strength = 1.4;
            lifespan = 250;
        } else if (CustomEmpireProfile.BIO_GASEOUS_BREATHER.equalsIgnoreCase(profile.biochemicalType())) {
            chem = "Hydrogen-Methane";
            nutrientType = "Gas";
            atmosphere = "Methane";
            intel = 1.2;
            strength = 0.6;
            lifespan = 120;
        }

        Map<String, SpeciesTrait> traitMap = getTraitMap();
        for (String tId : profile.selectedTraitIds()) {
            SpeciesTrait t = traitMap.get(tId);
            if (t != null) {
                intel += t.researchModifier();
                strength += t.defenseModifier() + (t.productionModifier() * 0.5);
            }
        }

        String society = switch (profile.governmentForm()) {
            case CustomEmpireProfile.GOV_HIVE_MIND -> "Hive mind";
            case CustomEmpireProfile.GOV_AUTOCRACY, CustomEmpireProfile.GOV_CORPORATE_OLIGARCHY -> "Collectivist";
            default -> "Individualist";
        };

        return new Race(
                profile.speciesId(),
                profile.speciesName(),
                "Custom biological phenotype created via imperial species bio-architect.",
                Math.max(0.5, intel),
                Math.max(0.5, strength),
                society,
                profile.optimalGravity(),
                profile.optimalTemperatureKelvin(),
                chem,
                atmosphere,
                fertileStart,
                fertileEnd,
                nutrientType,
                "Diverse",
                lifespan
        );
    }

    public Empire buildEmpire(CustomEmpireProfile profile) {
        if (profile == null) profile = CustomEmpireProfile.createDefault();

        double startingTreasury = 50000.0 + profile.startingTreasuryBonus();
        double corporateTax = switch (profile.governmentForm()) {
            case CustomEmpireProfile.GOV_CORPORATE_OLIGARCHY -> 0.05;
            case CustomEmpireProfile.GOV_AUTOCRACY -> 0.20;
            case CustomEmpireProfile.GOV_HIVE_MIND -> 0.0;
            default -> 0.12;
        };

        String society = switch (profile.governmentForm()) {
            case CustomEmpireProfile.GOV_HIVE_MIND -> "Hive Mind";
            case CustomEmpireProfile.GOV_AUTOCRACY -> "Authoritarian";
            case CustomEmpireProfile.GOV_CORPORATE_OLIGARCHY -> "Corporate Oligarchy";
            default -> "Individualist";
        };

        List<MinistryAssignment> ministries = List.of(
                new MinistryAssignment("portfolio_research", "Dr. " + profile.speciesName() + " Lead", 0.90),
                new MinistryAssignment("portfolio_economy", "Director of Treasury", 0.85),
                new MinistryAssignment("portfolio_defense", "Grand Marshal", 0.90)
        );

        return new Empire(
                profile.empireId(),
                profile.empireName(),
                profile.speciesId(),
                society,
                startingTreasury,
                corporateTax,
                List.of(),
                ministries,
                Map.of(),
                List.of("tech_fission", "tech_sublight_navigation"),
                List.of("design_pioneer_scout")
        );
    }

    public GameState applyToGameState(GameState state, CustomEmpireProfile profile) {
        if (state == null || profile == null) return state;

        Empire customEmpire = buildEmpire(profile);
        List<Empire> updatedEmpires = new ArrayList<>(state.empires());

        // Remove if exists then add
        updatedEmpires.removeIf(e -> e.id().equals(customEmpire.id()));
        updatedEmpires.add(customEmpire);

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                updatedEmpires,
                state.corporations(),
                state.commercialHubs(),
                state.shadowSyndicates(),
                state.diplomaticRelations(),
                state.systemGovernors(),
                state.researchProjects(),
                state.technologyExchangeRoutes(),
                state.shipDesigns(),
                state.fleets(),
                state.geologicalDeposits(),
                state.powerGrids(),
                state.industrialFacilities(),
                state.expansionProjects(),
                state.orbitalStations(),
                state.spaceElevators(),
                state.constructionProjects(),
                state.sleeperAgents(),
                state.espionageOperations(),
                state.pirateBases(),
                state.terraformingProjects(),
                state.megastructures(),
                state.galacticCommunity(),
                state.tradeRoutes(),
                state.fogOfWarStates()
        );
    }

    private Map<String, SpeciesTrait> getTraitMap() {
        return SpeciesTrait.getStandardTraits().stream()
                .collect(java.util.stream.Collectors.toMap(SpeciesTrait::id, t -> t));
    }
}
