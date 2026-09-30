package com.spaceconquest.engine;

import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.combat.FleetEngagementRecord;
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
import com.spaceconquest.engine.governance.WarDeclarationRecord;
import com.spaceconquest.engine.governance.DiplomaticProposal;
import com.spaceconquest.engine.habitation.PassengerManifest;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.logistics.LaunchServiceActivity;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipConstructionOrder;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.technology.TechnologyExchangeRoute;
import com.spaceconquest.engine.terraforming.AtmosphericComposition;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the complete simulation state at a specific turn. Collection components are
 * immutable defensive copies; contained model objects may still be mutable.
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
        List<ShipConstructionOrder> shipConstructionOrders,
        List<Fleet> fleets,
        List<PassengerManifest> passengerManifests,
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
        List<CorporateTaxAccount> corporateTaxAccounts,
        Map<String, Double> launchUsageKg,
        List<LaunchServiceActivity> launchActivities,
        List<WarDeclarationRecord> warDeclarations,
        List<DiplomaticProposal> diplomaticProposals,
        List<FleetEngagementRecord> fleetEngagements
) {
    public GameState {
        solarSystems = immutableList(solarSystems);
        empires = immutableList(empires);
        corporations = immutableList(corporations);
        commercialHubs = immutableList(commercialHubs);
        shadowSyndicates = immutableList(shadowSyndicates);
        diplomaticRelations = immutableList(diplomaticRelations);
        systemGovernors = immutableList(systemGovernors);
        researchProjects = immutableList(researchProjects);
        technologyExchangeRoutes = immutableList(technologyExchangeRoutes);
        shipDesigns = immutableList(shipDesigns);
        shipConstructionOrders = immutableList(shipConstructionOrders);
        fleets = immutableList(fleets);
        passengerManifests = immutableList(passengerManifests);
        geologicalDeposits = immutableList(geologicalDeposits);
        powerGrids = immutableList(powerGrids);
        industrialFacilities = immutableList(industrialFacilities);
        expansionProjects = immutableList(expansionProjects);
        orbitalStations = immutableList(orbitalStations);
        spaceElevators = immutableList(spaceElevators);
        constructionProjects = immutableList(constructionProjects);
        sleeperAgents = immutableList(sleeperAgents);
        espionageOperations = immutableList(espionageOperations);
        pirateBases = immutableList(pirateBases);
        terraformingProjects = immutableList(terraformingProjects);
        megastructures = immutableList(megastructures);
        tradeRoutes = immutableList(tradeRoutes);
        fogOfWarStates = immutableList(fogOfWarStates);
        systemEconomies = immutableList(systemEconomies);
        courierShips = immutableList(courierShips);
        planetaryBalanceSheets = immutableList(planetaryBalanceSheets);
        imperialBalanceSheets = immutableList(imperialBalanceSheets);
        householdAccounts = immutableList(householdAccounts);
        marketAccounts = immutableList(marketAccounts);
        industryAccounts = immutableList(industryAccounts);
        corporateTaxAccounts = immutableList(corporateTaxAccounts);
        launchUsageKg = immutableMap(launchUsageKg);
        launchActivities = immutableList(launchActivities);
        warDeclarations = immutableList(warDeclarations);
        diplomaticProposals = immutableList(diplomaticProposals);
        fleetEngagements = immutableList(fleetEngagements);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public GameState() {
        this(new Builder());
    }

    private GameState(Builder builder) {
        this(
                builder.turn, builder.status, builder.solarSystems, builder.empires,
                builder.corporations, builder.commercialHubs, builder.shadowSyndicates, builder.diplomaticRelations,
                builder.systemGovernors, builder.researchProjects, builder.technologyExchangeRoutes, builder.shipDesigns,
                builder.shipConstructionOrders, builder.fleets, builder.passengerManifests,
                builder.geologicalDeposits, builder.powerGrids, builder.industrialFacilities,
                builder.expansionProjects, builder.orbitalStations, builder.spaceElevators, builder.constructionProjects,
                builder.sleeperAgents, builder.espionageOperations, builder.pirateBases, builder.terraformingProjects,
                builder.megastructures, builder.galacticCommunity, builder.tradeRoutes, builder.fogOfWarStates,
                builder.systemEconomies, builder.courierShips, builder.planetaryBalanceSheets, builder.imperialBalanceSheets,
                builder.householdAccounts, builder.marketAccounts, builder.industryAccounts,
                builder.corporateTaxAccounts, builder.launchUsageKg, builder.launchActivities,
                builder.warDeclarations, builder.diplomaticProposals, builder.fleetEngagements
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private long turn;
        private String status = "INITIALIZING";
        private List<SolarSystem> solarSystems;
        private List<Empire> empires;
        private List<Corporation> corporations;
        private List<CommercialHub> commercialHubs;
        private List<ShadowSyndicate> shadowSyndicates;
        private List<DiplomaticRelation> diplomaticRelations;
        private List<SystemGovernor> systemGovernors;
        private List<ResearchProject> researchProjects;
        private List<TechnologyExchangeRoute> technologyExchangeRoutes;
        private List<ShipDesign> shipDesigns;
        private List<ShipConstructionOrder> shipConstructionOrders;
        private List<Fleet> fleets;
        private List<PassengerManifest> passengerManifests;
        private List<GeologicalDeposit> geologicalDeposits;
        private List<PowerGridState> powerGrids;
        private List<IndustrialFacility> industrialFacilities;
        private List<FacilityExpansionProject> expansionProjects;
        private List<OrbitalStation> orbitalStations;
        private List<SpaceElevator> spaceElevators;
        private List<ConstructionDeploymentProject> constructionProjects;
        private List<SleeperAgent> sleeperAgents;
        private List<EspionageOperation> espionageOperations;
        private List<PirateBase> pirateBases;
        private List<GeoengineeringProject> terraformingProjects;
        private List<Megastructure> megastructures;
        private GalacticCommunity galacticCommunity;
        private List<TradeRoute> tradeRoutes;
        private List<FogOfWarState> fogOfWarStates;
        private List<SystemEconomy> systemEconomies;
        private List<CourierShip> courierShips;
        private List<PlanetaryBalanceSheet> planetaryBalanceSheets;
        private List<ImperialBalanceSheet> imperialBalanceSheets;
        private List<HouseholdAccount> householdAccounts;
        private List<MarketAccount> marketAccounts;
        private List<IndustryAccount> industryAccounts;
        private List<CorporateTaxAccount> corporateTaxAccounts;
        private Map<String, Double> launchUsageKg;
        private List<LaunchServiceActivity> launchActivities;
        private List<WarDeclarationRecord> warDeclarations;
        private List<DiplomaticProposal> diplomaticProposals;
        private List<FleetEngagementRecord> fleetEngagements;

        public Builder() {}

        private Builder(GameState state) {
            this.turn = state.turn();
            this.status = state.status();
            this.solarSystems = state.solarSystems();
            this.empires = state.empires();
            this.corporations = state.corporations();
            this.commercialHubs = state.commercialHubs();
            this.shadowSyndicates = state.shadowSyndicates();
            this.diplomaticRelations = state.diplomaticRelations();
            this.systemGovernors = state.systemGovernors();
            this.researchProjects = state.researchProjects();
            this.technologyExchangeRoutes = state.technologyExchangeRoutes();
            this.shipDesigns = state.shipDesigns();
            this.shipConstructionOrders = state.shipConstructionOrders();
            this.fleets = state.fleets();
            this.passengerManifests = state.passengerManifests();
            this.geologicalDeposits = state.geologicalDeposits();
            this.powerGrids = state.powerGrids();
            this.industrialFacilities = state.industrialFacilities();
            this.expansionProjects = state.expansionProjects();
            this.orbitalStations = state.orbitalStations();
            this.spaceElevators = state.spaceElevators();
            this.constructionProjects = state.constructionProjects();
            this.sleeperAgents = state.sleeperAgents();
            this.espionageOperations = state.espionageOperations();
            this.pirateBases = state.pirateBases();
            this.terraformingProjects = state.terraformingProjects();
            this.megastructures = state.megastructures();
            this.galacticCommunity = state.galacticCommunity();
            this.tradeRoutes = state.tradeRoutes();
            this.fogOfWarStates = state.fogOfWarStates();
            this.systemEconomies = state.systemEconomies();
            this.courierShips = state.courierShips();
            this.planetaryBalanceSheets = state.planetaryBalanceSheets();
            this.imperialBalanceSheets = state.imperialBalanceSheets();
            this.householdAccounts = state.householdAccounts();
            this.marketAccounts = state.marketAccounts();
            this.industryAccounts = state.industryAccounts();
            this.corporateTaxAccounts = state.corporateTaxAccounts();
            this.launchUsageKg = state.launchUsageKg();
            this.launchActivities = state.launchActivities();
            this.warDeclarations = state.warDeclarations();
            this.diplomaticProposals = state.diplomaticProposals();
            this.fleetEngagements = state.fleetEngagements();
        }

        public Builder turn(long value) { this.turn = value; return this; }
        public Builder status(String value) { this.status = value; return this; }
        public Builder solarSystems(List<SolarSystem> value) { this.solarSystems = value; return this; }
        public Builder empires(List<Empire> value) { this.empires = value; return this; }
        public Builder corporations(List<Corporation> value) { this.corporations = value; return this; }
        public Builder commercialHubs(List<CommercialHub> value) { this.commercialHubs = value; return this; }
        public Builder shadowSyndicates(List<ShadowSyndicate> value) { this.shadowSyndicates = value; return this; }
        public Builder diplomaticRelations(List<DiplomaticRelation> value) { this.diplomaticRelations = value; return this; }
        public Builder systemGovernors(List<SystemGovernor> value) { this.systemGovernors = value; return this; }
        public Builder researchProjects(List<ResearchProject> value) { this.researchProjects = value; return this; }
        public Builder technologyExchangeRoutes(List<TechnologyExchangeRoute> value) { this.technologyExchangeRoutes = value; return this; }
        public Builder shipDesigns(List<ShipDesign> value) { this.shipDesigns = value; return this; }
        public Builder shipConstructionOrders(List<ShipConstructionOrder> value) { this.shipConstructionOrders = value; return this; }
        public Builder fleets(List<Fleet> value) { this.fleets = value; return this; }
        public Builder passengerManifests(List<PassengerManifest> value) { this.passengerManifests = value; return this; }
        public Builder geologicalDeposits(List<GeologicalDeposit> value) { this.geologicalDeposits = value; return this; }
        public Builder powerGrids(List<PowerGridState> value) { this.powerGrids = value; return this; }
        public Builder industrialFacilities(List<IndustrialFacility> value) { this.industrialFacilities = value; return this; }
        public Builder expansionProjects(List<FacilityExpansionProject> value) { this.expansionProjects = value; return this; }
        public Builder orbitalStations(List<OrbitalStation> value) { this.orbitalStations = value; return this; }
        public Builder spaceElevators(List<SpaceElevator> value) { this.spaceElevators = value; return this; }
        public Builder constructionProjects(List<ConstructionDeploymentProject> value) { this.constructionProjects = value; return this; }
        public Builder sleeperAgents(List<SleeperAgent> value) { this.sleeperAgents = value; return this; }
        public Builder espionageOperations(List<EspionageOperation> value) { this.espionageOperations = value; return this; }
        public Builder pirateBases(List<PirateBase> value) { this.pirateBases = value; return this; }
        public Builder terraformingProjects(List<GeoengineeringProject> value) { this.terraformingProjects = value; return this; }
        public Builder megastructures(List<Megastructure> value) { this.megastructures = value; return this; }
        public Builder galacticCommunity(GalacticCommunity value) { this.galacticCommunity = value; return this; }
        public Builder tradeRoutes(List<TradeRoute> value) { this.tradeRoutes = value; return this; }
        public Builder fogOfWarStates(List<FogOfWarState> value) { this.fogOfWarStates = value; return this; }
        public Builder systemEconomies(List<SystemEconomy> value) { this.systemEconomies = value; return this; }
        public Builder courierShips(List<CourierShip> value) { this.courierShips = value; return this; }
        public Builder planetaryBalanceSheets(List<PlanetaryBalanceSheet> value) { this.planetaryBalanceSheets = value; return this; }
        public Builder imperialBalanceSheets(List<ImperialBalanceSheet> value) { this.imperialBalanceSheets = value; return this; }
        public Builder householdAccounts(List<HouseholdAccount> value) { this.householdAccounts = value; return this; }
        public Builder marketAccounts(List<MarketAccount> value) { this.marketAccounts = value; return this; }
        public Builder industryAccounts(List<IndustryAccount> value) { this.industryAccounts = value; return this; }
        public Builder corporateTaxAccounts(List<CorporateTaxAccount> value) { this.corporateTaxAccounts = value; return this; }
        public Builder launchUsageKg(Map<String, Double> value) { this.launchUsageKg = value; return this; }
        public Builder launchActivities(List<LaunchServiceActivity> value) { this.launchActivities = value; return this; }
        public Builder warDeclarations(List<WarDeclarationRecord> value) { this.warDeclarations = value; return this; }
        public Builder diplomaticProposals(List<DiplomaticProposal> value) { this.diplomaticProposals = value; return this; }
        public Builder fleetEngagements(List<FleetEngagementRecord> value) { this.fleetEngagements = value; return this; }

        public GameState build() {
            return new GameState(this);
        }
    }

    public GameState withTurn(long value) {
        return toBuilder().turn(value).build();
    }

    public GameState withStatus(String value) {
        return toBuilder().status(value).build();
    }

    public GameState withSolarSystems(List<SolarSystem> value) {
        return toBuilder().solarSystems(value).build();
    }

    public GameState withEmpires(List<Empire> value) {
        return toBuilder().empires(value).build();
    }

    public GameState withCorporations(List<Corporation> value) {
        return toBuilder().corporations(value).build();
    }

    public GameState withCommercialHubs(List<CommercialHub> value) {
        return toBuilder().commercialHubs(value).build();
    }

    public GameState withShadowSyndicates(List<ShadowSyndicate> value) {
        return toBuilder().shadowSyndicates(value).build();
    }

    public GameState withDiplomaticRelations(List<DiplomaticRelation> value) {
        return toBuilder().diplomaticRelations(value).build();
    }

    public GameState withSystemGovernors(List<SystemGovernor> value) {
        return toBuilder().systemGovernors(value).build();
    }

    public GameState withResearchProjects(List<ResearchProject> value) {
        return toBuilder().researchProjects(value).build();
    }

    public GameState withTechnologyExchangeRoutes(List<TechnologyExchangeRoute> value) {
        return toBuilder().technologyExchangeRoutes(value).build();
    }

    public GameState withShipDesigns(List<ShipDesign> value) {
        return toBuilder().shipDesigns(value).build();
    }

    public GameState withShipConstructionOrders(List<ShipConstructionOrder> value) {
        return toBuilder().shipConstructionOrders(value).build();
    }

    public GameState withFleets(List<Fleet> value) {
        return toBuilder().fleets(value).build();
    }

    public GameState withGeologicalDeposits(List<GeologicalDeposit> value) {
        return toBuilder().geologicalDeposits(value).build();
    }

    public GameState withPowerGrids(List<PowerGridState> value) {
        return toBuilder().powerGrids(value).build();
    }

    public GameState withIndustrialFacilities(List<IndustrialFacility> value) {
        return toBuilder().industrialFacilities(value).build();
    }

    public GameState withExpansionProjects(List<FacilityExpansionProject> value) {
        return toBuilder().expansionProjects(value).build();
    }

    public GameState withOrbitalStations(List<OrbitalStation> value) {
        return toBuilder().orbitalStations(value).build();
    }

    public GameState withSpaceElevators(List<SpaceElevator> value) {
        return toBuilder().spaceElevators(value).build();
    }

    public GameState withConstructionProjects(List<ConstructionDeploymentProject> value) {
        return toBuilder().constructionProjects(value).build();
    }

    public GameState withSleeperAgents(List<SleeperAgent> value) {
        return toBuilder().sleeperAgents(value).build();
    }

    public GameState withEspionageOperations(List<EspionageOperation> value) {
        return toBuilder().espionageOperations(value).build();
    }

    public GameState withPirateBases(List<PirateBase> value) {
        return toBuilder().pirateBases(value).build();
    }

    public GameState withTerraformingProjects(List<GeoengineeringProject> value) {
        return toBuilder().terraformingProjects(value).build();
    }

    public GameState withMegastructures(List<Megastructure> value) {
        return toBuilder().megastructures(value).build();
    }

    public GameState withGalacticCommunity(GalacticCommunity value) {
        return toBuilder().galacticCommunity(value).build();
    }

    public GameState withTradeRoutes(List<TradeRoute> value) {
        return toBuilder().tradeRoutes(value).build();
    }

    public GameState withFogOfWarStates(List<FogOfWarState> value) {
        return toBuilder().fogOfWarStates(value).build();
    }

    public GameState withSystemEconomies(List<SystemEconomy> value) {
        return toBuilder().systemEconomies(value).build();
    }

    public GameState withCourierShips(List<CourierShip> value) {
        return toBuilder().courierShips(value).build();
    }

    public GameState withPlanetaryBalanceSheets(List<PlanetaryBalanceSheet> value) {
        return toBuilder().planetaryBalanceSheets(value).build();
    }

    public GameState withImperialBalanceSheets(List<ImperialBalanceSheet> value) {
        return toBuilder().imperialBalanceSheets(value).build();
    }

    public GameState withHouseholdAccounts(List<HouseholdAccount> value) {
        return toBuilder().householdAccounts(value).build();
    }

    public GameState withMarketAccounts(List<MarketAccount> value) {
        return toBuilder().marketAccounts(value).build();
    }

    public GameState withIndustryAccounts(List<IndustryAccount> value) {
        return toBuilder().industryAccounts(value).build();
    }

    public GameState withCorporateTaxAccounts(List<CorporateTaxAccount> value) {
        return toBuilder().corporateTaxAccounts(value).build();
    }

    public List<AtmosphericComposition> atmosphericCompositions() {
        return solarSystems.stream()
                .flatMap(system -> system.planets().stream())
                .map(planet -> {
                    boolean breathable = PopulationProcessor.hasAmbientBreathableOxygen(planet.atmosphere());
                    Map<String, Double> gasRatios = breathable
                            ? Map.of("oxygen_gas", 0.21, "nitrogen_gas", 0.78, "carbon_dioxide", 0.01)
                            : Map.of("nitrogen_gas", 0.60, "carbon_dioxide", 0.30, "toxic_aerosols", 0.10);
                    return new AtmosphericComposition(planet.id(), gasRatios,
                            breathable ? 1.0 : 0.4, breathable ? 288.0 : 220.0,
                            1.0, 10.0,
                            breathable ? AtmosphericComposition.BIOME_BREATHABLE_TERRESTRIAL
                                    : AtmosphericComposition.BIOME_BARREN,
                            breathable);
                })
                .toList();
    }
}
