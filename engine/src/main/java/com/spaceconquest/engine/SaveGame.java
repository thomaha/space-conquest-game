package com.spaceconquest.engine;

import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.ImperialBalanceSheet;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.economy.CorporateTaxAccount;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.espionage.EspionageOperation;
import com.spaceconquest.engine.espionage.PirateBase;
import com.spaceconquest.engine.espionage.SleeperAgent;
import com.spaceconquest.engine.galaxy.FogOfWarState;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * A complete snapshot of a game session, as written to and read from a save file.
 */
public record SaveGame(
        int version,
        String savedAt,
        int gameSpeed,
        String gameTime,
        String campaignStartTime,
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
        List<CourierShip> courierShips,
        List<PlanetaryBalanceSheet> planetaryBalanceSheets,
        List<ImperialBalanceSheet> imperialBalanceSheets,
        List<HouseholdAccount> householdAccounts,
        List<MarketAccount> marketAccounts,
        List<IndustryAccount> industryAccounts,
        List<CorporateTaxAccount> corporateTaxAccounts
) {
    public static final int CURRENT_VERSION = 16;

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
        if (systemEconomies == null) systemEconomies = List.of();
        if (courierShips == null) courierShips = List.of();
        if (planetaryBalanceSheets == null) planetaryBalanceSheets = List.of();
        if (imperialBalanceSheets == null) imperialBalanceSheets = List.of();
        if (householdAccounts == null) householdAccounts = List.of();
        if (marketAccounts == null) marketAccounts = List.of();
        if (industryAccounts == null) industryAccounts = List.of();
        if (corporateTaxAccounts == null) corporateTaxAccounts = List.of();
    }

    public static SaveGame fromGameState(GameState state, String savedAt, int gameSpeed, String gameTime) {
        return fromGameState(state, savedAt, gameSpeed, gameTime, GameClock.START_TIME.toString());
    }

    public static SaveGame fromGameState(GameState state, String savedAt, int gameSpeed,
                                         String gameTime, String campaignStartTime) {
        return new SaveGame(
                CURRENT_VERSION, savedAt, gameSpeed, gameTime, campaignStartTime,
                state.solarSystems(), state.empires(), state.corporations(), state.commercialHubs(),
                state.shadowSyndicates(), state.diplomaticRelations(), state.systemGovernors(), state.researchProjects(),
                state.technologyExchangeRoutes(), state.shipDesigns(), state.fleets(), state.geologicalDeposits(),
                state.powerGrids(), state.industrialFacilities(), state.expansionProjects(), state.orbitalStations(),
                state.spaceElevators(), state.constructionProjects(), state.sleeperAgents(), state.espionageOperations(),
                state.pirateBases(), state.terraformingProjects(), state.megastructures(), state.galacticCommunity(),
                state.tradeRoutes(), state.fogOfWarStates(), state.systemEconomies(), state.courierShips(),
                state.planetaryBalanceSheets(), state.imperialBalanceSheets(),
                state.householdAccounts(), state.marketAccounts(), state.industryAccounts(),
                state.corporateTaxAccounts()
        );
    }

    public LocalDateTime resolvedCampaignStartTime() {
        if (campaignStartTime != null) {
            try {
                return LocalDateTime.parse(campaignStartTime);
            } catch (DateTimeParseException ignored) {
                // Older or damaged metadata falls back to the calendar used by its save version.
            }
        }
        return version < 13 ? LocalDateTime.of(2200, 1, 1, 8, 0) : GameClock.START_TIME;
    }

    public GameState toGameState(long turn, String status) {
        return new GameState(
                turn, status, solarSystems, empires,
                corporations, commercialHubs, shadowSyndicates, diplomaticRelations,
                systemGovernors, researchProjects, technologyExchangeRoutes, shipDesigns,
                fleets, geologicalDeposits, powerGrids, industrialFacilities,
                expansionProjects, orbitalStations, spaceElevators, constructionProjects,
                sleeperAgents, espionageOperations, pirateBases, terraformingProjects,
                megastructures, galacticCommunity, tradeRoutes, fogOfWarStates,
                systemEconomies, courierShips, planetaryBalanceSheets, imperialBalanceSheets,
                householdAccounts, marketAccounts, industryAccounts, corporateTaxAccounts
        );
    }
}
