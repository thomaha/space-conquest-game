package com.spaceconquest.engine;

import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.economy.SystemEconomy;
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
 * Immutable snapshot of the complete simulation state at a specific turn.
 */
public record GameState(
        long turn,
        String status,
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
        List<FogOfWarState> fogOfWarStates,
        List<SystemEconomy> systemEconomies,
        List<CourierShip> courierShips
) {
    public GameState {
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
        if (systemEconomies == null) systemEconomies = List.of();
        if (courierShips == null) courierShips = List.of();
    }

    public GameState() {
        this(0, "INITIALIZING", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    public GameState(long turn, String status, List<SolarSystem> solarSystems) {
        this(turn, status, solarSystems, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    public GameState(
            long turn,
            String status,
            List<SolarSystem> solarSystems,
            List<Empire> empires,
            List<Corporation> corporations,
            List<CommercialHub> commercialHubs,
            List<ShadowSyndicate> shadowSyndicates,
            List<DiplomaticRelation> diplomaticRelations,
            List<SystemGovernor> systemGovernors,
            List<ResearchProject> researchProjects,
            List<TechnologyExchangeRoute> technologyExchangeRoutes
    ) {
        this(turn, status, solarSystems, empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    public GameState(
            long turn,
            String status,
            List<SolarSystem> solarSystems,
            List<Empire> empires,
            List<Corporation> corporations,
            List<CommercialHub> commercialHubs,
            List<ShadowSyndicate> shadowSyndicates,
            List<DiplomaticRelation> diplomaticRelations,
            List<SystemGovernor> systemGovernors
    ) {
        this(turn, status, solarSystems, empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    public GameState(
            long turn,
            String status,
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
            List<Fleet> fleets
    ) {
        this(turn, status, solarSystems, empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes,
                shipDesigns, fleets, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    public GameState(
            long turn,
            String status,
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
        this(turn, status, solarSystems, empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes,
                shipDesigns, fleets, geologicalDeposits, powerGrids, industrialFacilities, expansionProjects,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    public GameState(
            long turn,
            String status,
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
            List<PirateBase> pirateBases
    ) {
        this(turn, status, solarSystems, empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes,
                shipDesigns, fleets, geologicalDeposits, powerGrids, industrialFacilities, expansionProjects,
                orbitalStations, spaceElevators, constructionProjects, sleeperAgents, espionageOperations, pirateBases,
                List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    public GameState(
            long turn,
            String status,
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
        this(turn, status, solarSystems, empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes,
                shipDesigns, fleets, geologicalDeposits, powerGrids, industrialFacilities, expansionProjects,
                orbitalStations, spaceElevators, constructionProjects, sleeperAgents, espionageOperations, pirateBases,
                terraformingProjects, megastructures, galacticCommunity, List.of(), List.of(), List.of(), List.of());
    }

    public GameState(
            long turn,
            String status,
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
        this(turn, status, solarSystems, empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes,
                shipDesigns, fleets, geologicalDeposits, powerGrids, industrialFacilities, expansionProjects,
                orbitalStations, spaceElevators, constructionProjects, sleeperAgents, espionageOperations, pirateBases,
                terraformingProjects, megastructures, galacticCommunity, tradeRoutes, fogOfWarStates, List.of(), List.of());
    }

    public GameState(
            long turn,
            String status,
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
            List<FogOfWarState> fogOfWarStates,
            List<SystemEconomy> systemEconomies
    ) {
        this(turn, status, solarSystems, empires, corporations, commercialHubs, shadowSyndicates,
                diplomaticRelations, systemGovernors, researchProjects, technologyExchangeRoutes,
                shipDesigns, fleets, geologicalDeposits, powerGrids, industrialFacilities, expansionProjects,
                orbitalStations, spaceElevators, constructionProjects, sleeperAgents, espionageOperations, pirateBases,
                terraformingProjects, megastructures, galacticCommunity, tradeRoutes, fogOfWarStates, systemEconomies, List.of());
    }

    public GameState withEmpires(List<Empire> newEmpires) {
        return new GameState(
                turn, status, solarSystems, newEmpires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withCorporations(List<Corporation> newCorps) {
        return new GameState(
                turn, status, solarSystems, empires, newCorps, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withCommercialHubs(List<CommercialHub> newHubs) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, newHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withDiplomaticRelations(List<DiplomaticRelation> newRelations) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, newRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withSystemGovernors(List<SystemGovernor> newGovs) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, newGovs, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withResearchProjects(List<ResearchProject> newProjects) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, newProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withTerraformingProjects(List<GeoengineeringProject> newProjects) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, newProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withMegastructures(List<Megastructure> newMegastructures) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, newMegastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withGalacticCommunity(GalacticCommunity newCommunity) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, newCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withTradeRoutes(List<TradeRoute> newRoutes) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                newRoutes, fogOfWarStates, systemEconomies, courierShips
        );
    }

    public GameState withFogOfWarStates(List<FogOfWarState> newFow) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, newFow, systemEconomies, courierShips
        );
    }

    public GameState withSystemEconomies(List<SystemEconomy> newEconomies) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, newEconomies, courierShips
        );
    }

    public GameState withCourierShips(List<CourierShip> newCouriers) {
        return new GameState(
                turn, status, solarSystems, empires, corporations, commercialHubs,
                shadowSyndicates, diplomaticRelations, systemGovernors, researchProjects,
                technologyExchangeRoutes, shipDesigns, fleets, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, newCouriers
        );
    }
}
