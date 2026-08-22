package com.spaceconquest.engine;

import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.espionage.EspionageOperation;
import com.spaceconquest.engine.espionage.PirateBase;
import com.spaceconquest.engine.espionage.SleeperAgent;
import com.spaceconquest.engine.galaxy.FogOfWarState;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.technology.TechnologyExchangeRoute;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;

import java.util.List;

/**
 * A complete snapshot of a game session, as written to and read from a save file.
 */
public record SaveGame(
        int version,
        String savedAt,
        int gameSpeed,
        String gameTime,
        List<SolarSystem> solarSystems,
        List<Empire> empires,
        List<Corporation> corporations,
        List<CommercialHub> commercialHubs,
        List<ShadowSyndicate> shadowSyndicates,
        List<DiplomaticRelation> diplomaticRelations,
        List<SystemGovernor> systemGovernors,
        List<ResearchProject> researchProjects,
        List<TechnologyExchangeRoute> technologyExchangeRoutes,
        List<ShipDesign> shipDesigns,
        List<Fleet> fleets,
        List<GeologicalDeposit> geologicalDeposits,
        List<PowerGridState> powerGrids,
        List<IndustrialFacility> industrialFacilities,
        List<FacilityExpansionProject> expansionProjects,
        List<OrbitalStation> orbitalStations,
        List<SpaceElevator> spaceElevators,
        List<ConstructionDeploymentProject> constructionProjects,
        List<SleeperAgent> sleeperAgents,
        List<EspionageOperation> espionageOperations,
        List<PirateBase> pirateBases,
        List<GeoengineeringProject> terraformingProjects,
        List<Megastructure> megastructures,
        GalacticCommunity galacticCommunity,
        List<TradeRoute> tradeRoutes,
        List<FogOfWarState> fogOfWarStates
) {
    public static final int CURRENT_VERSION = 8;

    public SaveGame {
        if (solarSystems == null) solarSystems = List.of();
        if (empires == null) empires = List.of();
        if (corporations == null) corporations = List.of();
        if (commercialHubs == null) commercialHubs = List.of();
        if (shadowSyndicates == null) shadowSyndicates = List.of();
        if (diplomaticRelations == null) diplomaticRelations = List.of();
        if (systemGovernors == null) systemGovernors = List.of();
        if (researchProjects == null) researchProjects = List.of();
        if (technologyExchangeRoutes == null) technologyExchangeRoutes = List.of();
        if (shipDesigns == null) shipDesigns = List.of();
        if (fleets == null) fleets = List.of();
        if (geologicalDeposits == null) geologicalDeposits = List.of();
        if (powerGrids == null) powerGrids = List.of();
        if (industrialFacilities == null) industrialFacilities = List.of();
        if (expansionProjects == null) expansionProjects = List.of();
        if (orbitalStations == null) orbitalStations = List.of();
        if (spaceElevators == null) spaceElevators = List.of();
        if (constructionProjects == null) constructionProjects = List.of();
        if (sleeperAgents == null) sleeperAgents = List.of();
        if (espionageOperations == null) espionageOperations = List.of();
        if (pirateBases == null) pirateBases = List.of();
        if (terraformingProjects == null) terraformingProjects = List.of();
        if (megastructures == null) megastructures = List.of();
        if (tradeRoutes == null) tradeRoutes = List.of();
        if (fogOfWarStates == null) fogOfWarStates = List.of();
    }

    public SaveGame(
            int version,
            String savedAt,
            int gameSpeed,
            String gameTime,
            List<SolarSystem> solarSystems
    ) {
        this(version, savedAt, gameSpeed, gameTime, solarSystems,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of());
    }

    public SaveGame(
            int version,
            String savedAt,
            int gameSpeed,
            String gameTime,
            List<SolarSystem> solarSystems,
            List<Empire> empires,
            List<Corporation> corporations,
            List<CommercialHub> commercialHubs,
            List<ShadowSyndicate> shadowSyndicates,
            List<DiplomaticRelation> diplomaticRelations,
            List<SystemGovernor> systemGovernors
    ) {
        this(version, savedAt, gameSpeed, gameTime, solarSystems,
                empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of());
    }

    public SaveGame(
            int version,
            String savedAt,
            int gameSpeed,
            String gameTime,
            List<SolarSystem> solarSystems,
            List<Empire> empires,
            List<Corporation> corporations,
            List<CommercialHub> commercialHubs,
            List<ShadowSyndicate> shadowSyndicates,
            List<DiplomaticRelation> diplomaticRelations,
            List<SystemGovernor> systemGovernors,
            List<ResearchProject> researchProjects,
            List<TechnologyExchangeRoute> technologyExchangeRoutes,
            List<ShipDesign> shipDesigns,
            List<Fleet> fleets,
            List<GeologicalDeposit> geologicalDeposits,
            List<PowerGridState> powerGrids,
            List<IndustrialFacility> industrialFacilities,
            List<FacilityExpansionProject> expansionProjects
    ) {
        this(version, savedAt, gameSpeed, gameTime, solarSystems,
                empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes,
                shipDesigns, fleets, geologicalDeposits, powerGrids, industrialFacilities, expansionProjects,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of());
    }

    public SaveGame(
            int version,
            String savedAt,
            int gameSpeed,
            String gameTime,
            List<SolarSystem> solarSystems,
            List<Empire> empires,
            List<Corporation> corporations,
            List<CommercialHub> commercialHubs,
            List<ShadowSyndicate> shadowSyndicates,
            List<DiplomaticRelation> diplomaticRelations,
            List<SystemGovernor> systemGovernors,
            List<ResearchProject> researchProjects,
            List<TechnologyExchangeRoute> technologyExchangeRoutes,
            List<ShipDesign> shipDesigns,
            List<Fleet> fleets,
            List<GeologicalDeposit> geologicalDeposits,
            List<PowerGridState> powerGrids,
            List<IndustrialFacility> industrialFacilities,
            List<FacilityExpansionProject> expansionProjects,
            List<OrbitalStation> orbitalStations,
            List<SpaceElevator> spaceElevators,
            List<ConstructionDeploymentProject> constructionProjects,
            List<SleeperAgent> sleeperAgents,
            List<EspionageOperation> espionageOperations,
            List<PirateBase> pirateBases,
            List<GeoengineeringProject> terraformingProjects,
            List<Megastructure> megastructures,
            GalacticCommunity galacticCommunity
    ) {
        this(version, savedAt, gameSpeed, gameTime, solarSystems,
                empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes,
                shipDesigns, fleets, geologicalDeposits, powerGrids, industrialFacilities, expansionProjects,
                orbitalStations, spaceElevators, constructionProjects, sleeperAgents, espionageOperations, pirateBases,
                terraformingProjects, megastructures, galacticCommunity, List.of(), List.of());
    }
}
