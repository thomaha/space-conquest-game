package com.spaceconquest.engine;

import com.spaceconquest.engine.audio.AudioSynthesizer;
import com.spaceconquest.engine.combat.ColonizationProcessor;
import com.spaceconquest.engine.combat.OrbitalBombardmentProcessor;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import com.spaceconquest.engine.combat.FleetEncounterResolver;
import com.spaceconquest.engine.combat.FleetEngagementRecord;
import com.spaceconquest.engine.combat.TacticalFleetEncounterResolver;
import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.community.GalacticCommunityProcessor;
import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.ImperialBalanceSheet;
import com.spaceconquest.engine.economy.ImperialFinanceCoordinator;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEconomyProcessor;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.economy.CorporateProfitTaxProcessor;
import com.spaceconquest.engine.economy.CorporateTaxAccount;
import com.spaceconquest.engine.economy.PlanetaryMunicipalProcessor;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.economy.SystemEconomyProcessor;
import com.spaceconquest.engine.espionage.EspionageOperation;
import com.spaceconquest.engine.espionage.EspionageProcessor;
import com.spaceconquest.engine.espionage.PirateBase;
import com.spaceconquest.engine.espionage.SleeperAgent;
import com.spaceconquest.engine.galaxy.FogOfWarState;
import com.spaceconquest.engine.galaxy.SensorProcessor;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.governance.GovernanceProcessor;
import com.spaceconquest.engine.governance.GroundCombatProcessor;
import com.spaceconquest.engine.governance.IdeologicalAccessionManager;
import com.spaceconquest.engine.governance.TerritoryProcessor;
import com.spaceconquest.engine.governance.WarDeclarationRecord;
import com.spaceconquest.engine.governance.DiplomaticProposal;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.industry.IndustryProcessor;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.habitation.PassengerManifest;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import com.spaceconquest.engine.industry.PowerGenerationProcessor;
import com.spaceconquest.engine.industry.PowerBillingProcessor;
import com.spaceconquest.engine.industry.PowerProcessor;
import com.spaceconquest.engine.industry.ProspectingProcessor;
import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import com.spaceconquest.engine.logistics.LogisticsProcessor;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.logistics.LaunchServiceActivity;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.MacroStructureProcessor;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.market.CrimeProcessor;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.market.MarketDemandProcessor;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.megastructure.MegastructureProcessor;
import com.spaceconquest.engine.scenario.CampaignSetup;
import com.spaceconquest.engine.scenario.VictoryConditionChecker;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.ResearchProcessor;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.technology.ApplicationOptimization;
import com.spaceconquest.engine.technology.ResearchTickProcessor;
import com.spaceconquest.engine.technology.TechnologyExchangeRoute;
import com.spaceconquest.engine.terraforming.AtmosphericComposition;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;
import com.spaceconquest.engine.terraforming.TerraformingProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpaceConquestEngine implements GameEngine {
    private static final Logger logger = LogManager.getLogger(SpaceConquestEngine.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long turn = 0;
    private List<SolarSystem> solarSystems = new ArrayList<>();
    private List<Race> races = new ArrayList<>();
    private List<Material> materials = new ArrayList<>();
    private List<Empire> empires = new ArrayList<>();
    private List<Corporation> corporations = new ArrayList<>();
    private List<CommercialHub> commercialHubs = new ArrayList<>();
    private List<ShadowSyndicate> shadowSyndicates = new ArrayList<>();
    private List<DiplomaticRelation> diplomaticRelations = new ArrayList<>();
    private List<SystemGovernor> systemGovernors = new ArrayList<>();
    private List<MinistryPortfolio> ministryPortfolios = new ArrayList<>();
    private List<ResearchProject> researchProjects = new ArrayList<>();
    private List<TechnologyExchangeRoute> technologyExchangeRoutes = new ArrayList<>();
    private List<ShipDesign> shipDesigns = new ArrayList<>();
    private List<ShipConstructionOrder> shipConstructionOrders = new ArrayList<>();
    private List<Fleet> fleets = new ArrayList<>();
    private List<GeologicalDeposit> geologicalDeposits = new ArrayList<>();
    private List<PowerGridState> powerGrids = new ArrayList<>();
    private List<PassengerManifest> passengerManifests = new ArrayList<>();
    private List<IndustrialFacility> industrialFacilities = new ArrayList<>();
    private List<FacilityExpansionProject> expansionProjects = new ArrayList<>();
    private List<OrbitalStation> orbitalStations = new ArrayList<>();
    private List<SpaceElevator> spaceElevators = new ArrayList<>();
    private List<ConstructionDeploymentProject> constructionProjects = new ArrayList<>();
    private List<SleeperAgent> sleeperAgents = new ArrayList<>();
    private List<EspionageOperation> espionageOperations = new ArrayList<>();
    private List<PirateBase> pirateBases = new ArrayList<>();
    private List<GeoengineeringProject> terraformingProjects = new ArrayList<>();
    private List<Megastructure> megastructures = new ArrayList<>();
    private GalacticCommunity galacticCommunity;
    private List<TradeRoute> tradeRoutes = new ArrayList<>();
    private List<FogOfWarState> fogOfWarStates = new ArrayList<>();
    private List<CourierShip> courierShips = new ArrayList<>();
    private List<SystemEconomy> systemEconomies = new ArrayList<>();
    private List<PlanetaryBalanceSheet> planetaryBalanceSheets = new ArrayList<>();
    private List<ImperialBalanceSheet> imperialBalanceSheets = new ArrayList<>();
    private List<HouseholdAccount> householdAccounts = new ArrayList<>();
    private List<MarketAccount> marketAccounts = new ArrayList<>();
    private Map<String, Double> launchUsageKg = new HashMap<>();
    private List<LaunchServiceActivity> launchActivities = new ArrayList<>();
    private List<WarDeclarationRecord> warDeclarations = new ArrayList<>();
    private List<DiplomaticProposal> diplomaticProposals = new ArrayList<>();
    private List<FleetEngagementRecord> fleetEngagements = new ArrayList<>();
    private List<ApplicationOptimization> applicationOptimizations = new ArrayList<>();
    private List<IndustryAccount> industryAccounts = new ArrayList<>();
    private List<CorporateTaxAccount> corporateTaxAccounts = new ArrayList<>();
    private final ImperialFinanceCoordinator imperialFinance = new ImperialFinanceCoordinator();

    private final PopulationProcessor populationProcessor = new PopulationProcessor();
    private final MarketProcessor marketProcessor = new MarketProcessor();
    private final MarketDemandProcessor marketDemandProcessor = new MarketDemandProcessor();
    private final CorporateInvestmentProcessor corporateInvestmentProcessor = new CorporateInvestmentProcessor();
    private final LogisticsProcessor logisticsProcessor = new LogisticsProcessor();
    private final SensorProcessor sensorProcessor = new SensorProcessor();
    private final CrimeProcessor crimeProcessor = new CrimeProcessor();
    private final SystemEconomyProcessor systemEconomyProcessor = new SystemEconomyProcessor();
    private final PlanetaryMunicipalProcessor planetaryMunicipalProcessor = new PlanetaryMunicipalProcessor();
    private final HouseholdEconomyProcessor householdEconomyProcessor = new HouseholdEconomyProcessor();
    private final GovernanceProcessor governanceProcessor = new GovernanceProcessor();
    private final IdeologicalAccessionManager accessionManager = new IdeologicalAccessionManager(governanceProcessor);
    private final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();
    private final TerritoryProcessor territoryProcessor = new TerritoryProcessor(diplomacyProcessor);
    private final GroundCombatProcessor groundCombatProcessor = new GroundCombatProcessor();
    private final ResearchProcessor researchProcessor = new ResearchProcessor();
    private final FleetProcessor fleetProcessor = new FleetProcessor();
    private final ProspectingProcessor prospectingProcessor = new ProspectingProcessor();
    private final PowerProcessor powerProcessor = new PowerProcessor();
    private final PowerGenerationProcessor powerGenerationProcessor = new PowerGenerationProcessor();
    private final CorporateProfitTaxProcessor corporateProfitTaxProcessor = new CorporateProfitTaxProcessor();
    private final IndustryProcessor industryProcessor = new IndustryProcessor();
    private final IndustryMarketProcessor industryMarketProcessor = new IndustryMarketProcessor();
    private final TacticalCombatProcessor tacticalCombatProcessor = new TacticalCombatProcessor();
    private final FleetEncounterResolver fleetEncounterResolver = new TacticalFleetEncounterResolver(tacticalCombatProcessor);
    private final OrbitalBombardmentProcessor orbitalBombardmentProcessor = new OrbitalBombardmentProcessor();
    private final ColonizationProcessor colonizationProcessor = new ColonizationProcessor();
    private final MacroStructureProcessor macroStructureProcessor = new MacroStructureProcessor();
    private final EspionageProcessor espionageProcessor = new EspionageProcessor();
    private final RefinementProcessor refinementProcessor = new RefinementProcessor();
    private final TerraformingProcessor terraformingProcessor = new TerraformingProcessor();
    private final MegastructureProcessor megastructureProcessor = new MegastructureProcessor();
    private final GalacticCommunityProcessor galacticCommunityProcessor = new GalacticCommunityProcessor();
    private final VictoryConditionChecker victoryConditionChecker = new VictoryConditionChecker();
    private final AudioSynthesizer audioSynthesizer = new AudioSynthesizer();
    private final GameClock gameClock = new GameClock();
    private CampaignSetup campaignSetup = CampaignSetup.createDefault();

    public SpaceConquestEngine() {
        try {
            races = DataModelLoader.loadRaces();
            materials = DataModelLoader.loadMaterials();
            ministryPortfolios = DataModelLoader.loadMinistries();
        } catch (java.io.IOException e) {
            logger.error("Failed to load catalogs", e);
        }
    }

    /** Preserves the former Sol and Earth fixture as an explicit scenario. */
    public static SpaceConquestEngine fromSolScenario() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        engine.loadSolScenario();
        return engine;
    }

    private void loadSolScenario() {
        try {
            solarSystems = DataModelLoader.loadSolarSystems();
            empires = DataModelLoader.loadEmpires();
            corporations = DataModelLoader.loadCorporations();
            for (SolarSystem sys : solarSystems) {
                for (Planet p : sys.planets()) {
                    if (!p.populations().isEmpty()) {
                        commercialHubs.add(new CommercialHub(
                                "hub_" + p.id(),
                                p.id(),
                                0.05,
                                500000.0,
                                50000.0,
                                15.0,
                                Map.of()
                        ));
                    }
                }
            }
            systemEconomies = initializeDefaultSystemEconomies(solarSystems, empires);
            planetaryBalanceSheets = planetaryMunicipalProcessor.createOpeningBalanceSheets(getGameState());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load the Sol scenario", e);
        }
    }

    public SpaceConquestEngine(GameStartScenario scenario) {
        try {
            gameClock.startNewCampaign((scenario == null ? GameStartScenario.PRE_SPACE_FLIGHT : scenario).startTime());
            races = DataModelLoader.loadRaces();
            materials = DataModelLoader.loadMaterials();
            ministryPortfolios = DataModelLoader.loadMinistries();
            GalaxyGenerator generator = new GalaxyGenerator();
            GameState generated = generator.generateGameState(10, scenario);
            applyGameStateInternal(generated);
        } catch (java.io.IOException e) {
            logger.error("Failed to load scenario data", e);
        }
    }

    public SpaceConquestEngine(SaveGame saveGame) {
        try {
            gameClock.startNewCampaign(saveGame.resolvedCampaignStartTime());
            try {
                gameClock.restore(java.time.LocalDateTime.parse(saveGame.gameTime()), gameClock.getSpeed());
            } catch (java.time.format.DateTimeParseException | NullPointerException | IllegalArgumentException ignored) {
                // Legacy saves may have no usable clock value; their state still loads at turn zero.
            }
            races = DataModelLoader.loadRaces();
            materials = DataModelLoader.loadMaterials();
            ministryPortfolios = DataModelLoader.loadMinistries();
            applyGameStateInternal(saveGame.toGameState(gameClock.getCurrentTurn(), "STOPPED"));
        } catch (java.io.IOException e) {
            logger.error("Failed to load save data", e);
        }
    }

    @Override
    public void start() {
        running.set(true);
        logger.info("GameEngine started");
    }

    @Override
    public void stop() {
        running.set(false);
        logger.info("GameEngine stopped");
    }

    @Override
    public void update() {
        stepTurn();
    }

    public void stepTurn() {
        gameClock.stepTurn();
        runDailyTurn();
    }

    /** Processes one day already counted by the authoritative clock. */
    public void processScheduledTurn() {
        if (turn >= gameClock.getCurrentTurn()) {
            throw new IllegalStateException("No scheduled day is waiting to be processed");
        }
        runDailyTurn();
    }

    private void runDailyTurn() {
        turn++;
        diplomaticProposals = diplomacyProcessor.expirePendingProposals(diplomaticProposals, turn);
        imperialFinance.beginTurn();
        logger.info("Advancing to turn {}", turn);

        // Demographic age brackets are measured in years, unlike daily work turns.
        if (gameClock.beginsNewYearAtTurn(turn)) {
            updatePopulations();
        }

        updateGovernance();
        updateResearch();
        updateMarketsAndEconomy();
        updateMacroStructures();
        updateEspionage();
        updateFleets();
        updateTerraforming();
        updateMegastructures();
        updateGalacticCommunity();
        updateCouriers();
        ImperialFinanceCoordinator.Settlement settlement = imperialFinance.settle(turn, empires, imperialBalanceSheets);
        empires = settlement.empires();
        imperialBalanceSheets = settlement.balanceSheets();
        launchUsageKg = new HashMap<>();
        launchActivities = new ArrayList<>();
    }
    private void updateCouriers() {
        if (courierShips.isEmpty()) return;
        List<Empire> beforeTreasuries = empires;
        List<CourierShip> remainingCouriers = new ArrayList<>();
        Map<String, Double> empireTreasuryDeltas = new HashMap<>();
        Map<String, Double> corpReserveDeltas = new HashMap<>();

        for (CourierShip ship : courierShips) {
            CourierShip updated = ship.withReducedTravel();
            if (updated.hasArrived()) {
                // Determine if owner is an empire or a corporation
                boolean foundOwner = false;
                for (Empire emp : empires) {
                    if (emp.id().equals(ship.ownerEmpireId())) {
                        empireTreasuryDeltas.merge(emp.id(), ship.credits(), Double::sum);
                        foundOwner = true;
                        break;
                    }
                }
                if (!foundOwner) {
                    for (Corporation corp : corporations) {
                        if (corp.id().equals(ship.ownerEmpireId())) {
                            corpReserveDeltas.merge(corp.id(), ship.credits(), Double::sum);
                            break;
                        }
                    }
                }
            } else if (!updated.isIntercepted()) {
                remainingCouriers.add(updated);
            }
        }

        courierShips = remainingCouriers;
        // Apply deliveries to empires
        if (!empireTreasuryDeltas.isEmpty()) {
            empires = empires.stream().map(emp -> {
                double delta = empireTreasuryDeltas.getOrDefault(emp.id(), 0.0);
                if (delta == 0) return emp;
                return new Empire(
                        emp.id(), emp.name(), emp.raceId(), emp.societyStructure(),
                        emp.treasuryCredits() + delta, emp.corporateTaxRate(), emp.controlledSystemIds(),
                        emp.ministries(), emp.systemGovernorAssignments(), emp.unlockedTechIds(), emp.activeShipDesignIds()
                );
            }).toList();
        }
        // Apply deliveries to corporations
        if (!corpReserveDeltas.isEmpty()) {
            corporations = corporations.stream().map(corp -> {
                double delta = corpReserveDeltas.getOrDefault(corp.id(), 0.0);
                if (delta == 0) return corp;
                return new Corporation(
                        corp.id(), corp.name(), corp.empireId(),
                        corp.headquartersEntityId(), corp.marketOrientation(),
                        corp.liquidCapitalReserves() + delta, corp.ownedFacilityIds(),
                        corp.ownedShipIds(), corp.claimedVeinIds()
                );
            }).toList();
        }
        imperialFinance.recordTreasuryChanges(beforeTreasuries, empires);
    }

    private void updateTerraforming() {
        if (terraformingProjects.isEmpty()) return;
        List<Empire> beforeConstruction = empires;
        GameState construction = terraformingProcessor.processConstruction(getGameState());
        if (construction.terraformingProjects().stream().anyMatch(project -> project.isCompleted()
                && terraformingProjects.stream().anyMatch(old -> old.id().equals(project.id())
                && !old.isCompleted()))) audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_TERRAFORM_COMPLETE);
        terraformingProjects = construction.terraformingProjects();
        empires = construction.empires(); corporations = construction.corporations();
        commercialHubs = construction.commercialHubs(); marketAccounts = construction.marketAccounts();
        imperialFinance.recordTreasuryChanges(beforeConstruction, empires);
    }

    private void updateMegastructures() {
        if (megastructures.isEmpty()) return;
        List<Empire> beforeConstruction = empires;
        GameState construction = megastructureProcessor.processMegastructures(getGameState());
        megastructures = construction.megastructures(); empires = construction.empires();
        corporations = construction.corporations(); commercialHubs = construction.commercialHubs();
        fleets = construction.fleets();
        marketAccounts = construction.marketAccounts();
        imperialFinance.recordTreasuryChanges(beforeConstruction, empires);
    }

    private void updateGalacticCommunity() {
        if (galacticCommunity == null) return;
        Map<String, Double> gdpMap = new HashMap<>();
        Map<String, Double> fleetMap = new HashMap<>();
        for (Empire emp : empires) {
            gdpMap.put(emp.id(), emp.treasuryCredits());
            fleetMap.put(emp.id(), 1000.0);
        }
        GalacticCommunityProcessor.CommunityTurnResult commRes = galacticCommunityProcessor.processSenateSession(
                galacticCommunity, empires, gdpMap, fleetMap, turn
        );
        galacticCommunity = commRes.updatedCommunity();
        if (!commRes.newlyPassedResolutions().isEmpty()) {
            audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_SENATE_GAVEL);
        }
    }

    private void updateMacroStructures() {
        List<OrbitalStation> updatedStations = new ArrayList<>();
        double totalStationTariff = 0.0;
        double totalStationResearch = 0.0;
        for (OrbitalStation station : orbitalStations) {
            MacroStructureProcessor.StationTurnResult res = macroStructureProcessor.processOrbitalStation(station, 0.05);
            if (res.updatedStation() != null) {
                updatedStations.add(res.updatedStation());
                totalStationTariff += res.collectedTariffCredits();
                totalStationResearch += res.generatedResearchPoints();
            }
        }
        orbitalStations = updatedStations;
        List<Empire> beforeConstruction = empires;
        GameState construction = macroStructureProcessor.advanceConstructionProjects(getGameState());
        constructionProjects = construction.constructionProjects();
        orbitalStations = construction.orbitalStations();
        spaceElevators = construction.spaceElevators();
        fleets = construction.fleets();
        empires = construction.empires(); corporations = construction.corporations();
        commercialHubs = construction.commercialHubs(); marketAccounts = construction.marketAccounts();
        imperialFinance.recordTreasuryChanges(beforeConstruction, empires);
    }

    private void updateEspionage() {
        EspionageProcessor.EspionageTurnResult espRes = espionageProcessor.processEspionageTurn(
                espionageOperations, sleeperAgents, pirateBases, empires
        );
        espionageOperations = espRes.updatedOperations();
        sleeperAgents = espRes.updatedAgents();
        pirateBases = espRes.updatedBases();
    }

    private void updateIndustryAndPower(HouseholdEconomyProcessor.TurnResult payroll) {
        List<Empire> beforeConstruction = empires;
        GameState construction = industryProcessor.processIndustrialProduction(getGameState(),
                payroll.paidWorkersByFacility());
        industrialFacilities = construction.industrialFacilities();
        expansionProjects = construction.expansionProjects();
        shipConstructionOrders = construction.shipConstructionOrders(); fleets = construction.fleets();
        empires = construction.empires();
        corporations = construction.corporations();
        commercialHubs = construction.commercialHubs();
        marketAccounts = construction.marketAccounts();
        imperialFinance.recordTreasuryChanges(beforeConstruction, empires);
        List<Empire> beforeFuel = empires;
        PowerGenerationProcessor.Result power = powerGenerationProcessor.process(getGameState(),
                payroll.paidWorkersByFacility(), payroll.wagesByFacility());
        empires = power.empires();
        corporations = power.corporations();
        commercialHubs = power.hubs();
        marketAccounts = power.marketAccounts();
        industryAccounts = power.industryAccounts();
        imperialFinance.recordTreasuryChanges(beforeFuel, empires);
        PowerProcessor.DayResult balance = powerProcessor.balanceDay(getGameState(),
                power.generationKw(), payroll.paidWorkersByFacility());
        List<Empire> beforeLaunchPowerSettlement = empires;
        PowerBillingProcessor.Result billing = new PowerBillingProcessor().process(getGameState(),
                balance, power.accounts(), payroll.paidWorkersByFacility());
        empires = billing.empires();
        corporations = billing.corporations();
        householdAccounts = billing.households();
        powerGrids = billing.grids();
        industryAccounts = billing.industryAccounts();
        launchActivities = new ArrayList<>(billing.launchActivities());
        imperialFinance.recordTreasuryChanges(beforeLaunchPowerSettlement, empires);
        imperialFinance.recordIndustryFlows(billing.imperialReceipts(), billing.imperialExpenses());
        IndustryMarketProcessor.TurnResult marketResult = industryMarketProcessor.process(
                getGameState(), billing.poweredWorkers(), payroll.wagesByFacility(),
                payroll.paidWorkersByFacility());
        empires = marketResult.empires();
        corporations = marketResult.corporations();
        commercialHubs = marketResult.hubs();
        marketAccounts = marketResult.marketAccounts();
        geologicalDeposits = marketResult.deposits();
        Map<String, IndustryAccount> accounts = new LinkedHashMap<>();
        for (IndustryAccount account : marketResult.industryAccounts()) accounts.put(account.facilityId(),
                account.withPowerCost(billing.facilityPowerCosts().getOrDefault(account.facilityId(), 0.0)));
        for (IndustryAccount account : power.accounts()) accounts.computeIfPresent(account.facilityId(),
                (id, settled) -> settled.withDailyTransactions(account.withPowerSale(
                        billing.plantSales().getOrDefault(id, 0.0))
                        .withMaintenanceCost(settled.maintenanceCostsCredits())));
        industryAccounts = List.copyOf(accounts.values());
        imperialFinance.recordIndustryFlows(marketResult.imperialReceipts(), marketResult.imperialExpenses());
    }

    private void updateFleets() {
        fleets = fleetProcessor.processFleetMovements(fleets, orbitalStations, diplomaticRelations);
        GameState arrivals = PassengerTransitProcessor.advanceDay(getGameState(), races);
        GameState engagements = fleetEncounterResolver.resolveEncounters(arrivals);
        fleets = engagements.fleets();
        solarSystems = arrivals.solarSystems();
        passengerManifests = arrivals.passengerManifests();
        fleetEngagements = new ArrayList<>(engagements.fleetEngagements());
        fogOfWarStates = sensorProcessor.updateSensorCoverage(
                empires, solarSystems, fleets, shipDesigns, orbitalStations,
                List.of(), pirateBases, fogOfWarStates
        );
    }

    private void updateResearch() {
        var result = new ResearchTickProcessor().process(getGameState(), races, researchProcessor);
        researchProjects = new ArrayList<>(result.projects());
        empires = new ArrayList<>(result.empires());
        applicationOptimizations = new ArrayList<>(result.optimizations());
    }

    private void updateGovernance() {
        if (ministryPortfolios.isEmpty()) {
            try {
                ministryPortfolios = DataModelLoader.loadMinistries();
            } catch (java.io.IOException e) {
                logger.error("Failed to load ministries during update", e);
            }
        }

        // 1. Recalculate imperial cabinet ministerial efficiencies
        empires = empires.stream()
                .map(empire -> governanceProcessor.updateEmpireCabinet(empire, ministryPortfolios))
                .toList();

        // 2. Update dynamic territorial control based on range of influence (I_v)
        empires = territoryProcessor.updateTerritorialControl(getGameState());

        // 3. Democratic election cycles every five calendar years.
        if (gameClock.beginsElectionYearAtTurn(turn) && !commercialHubs.isEmpty()) {
            Map<String, Double> shortages = calculateAverageShortages();
            empires = empires.stream()
                    .map(empire -> {
                        if ("Individualist".equalsIgnoreCase(empire.societyStructure())) {
                            return accessionManager.runDemocraticElection(empire, shortages, ministryPortfolios);
                        }
                        return empire;
                    })
                    .toList();
        }
    }

    private Map<String, Double> calculateAverageShortages() {
        Map<String, Double> shortages = new HashMap<>();
        for (CommercialHub hub : commercialHubs) {
            for (Map.Entry<String, MarketOrder> entry : hub.activeOrders().entrySet()) {
                String res = entry.getKey();
                double score = entry.getValue().shortcomingScore();
                shortages.merge(res, score, Math::max);
            }
        }
        return shortages;
    }

    private void updatePopulations() {
        solarSystems = solarSystems.stream()
            .map(system -> {
                String empireId = empires.stream()
                        .filter(empire -> empire.controlledSystemIds().contains(system.id()))
                        .map(Empire::id)
                        .findFirst().orElse(null);
                return new SolarSystem(
                        system.id(), system.name(), system.description(), system.x(), system.y(), system.z(),
                        system.sunMass(), system.sunDiameter(), system.sunColor(),
                        system.planets().stream().map(planet -> updatePlanet(planet, empireId)).toList(),
                        system.asteroidBelts().stream()
                                .map(belt -> updateAsteroidBelt(belt, empireId)).toList()
                );
            })
            .toList();
        orbitalStations = orbitalStations.stream().map(station -> {
            String empireId = empires.stream().filter(empire ->
                    empire.controlledSystemIds().contains(station.systemId()))
                    .map(Empire::id).findFirst().orElse(null);
            List<Population> grown = station.populations().stream()
                    .map(population -> updatePopulation(population, station.id(), empireId)).toList();
            return station.withPopulations(fitOrbitalCapacity(grown, station.habitationCapacity()));
        }).toList();
        householdAccounts = householdAccounts.stream()
                .map(HouseholdAccount::withResetAnnualShortfall).toList();
    }

    private void updateMarketsAndEconomy() {
        commercialHubs = marketProcessor.updateCommercialHubs(
                marketDemandProcessor.refresh(getGameState(), races));
        ensureMarketAccounts();
        Map<String, Double> trustPenalties = activeCorporateTrustPenalties();
        GameState invested = corporateInvestmentProcessor.processCorporateInvestments(getGameState(), trustPenalties);
        corporations = invested.corporations();
        industrialFacilities = invested.industrialFacilities();
        expansionProjects = invested.expansionProjects();
        shipDesigns = invested.shipDesigns();
        fleets = invested.fleets();

        // 4. Automated Trade & Logistics Routes
        LogisticsProcessor.FreightResult logisticsResult =
                logisticsProcessor.processTradeRoutes(getGameState());
        List<Empire> beforeTreasuries = empires;
        tradeRoutes = logisticsResult.state().tradeRoutes();
        commercialHubs = logisticsResult.state().commercialHubs();
        marketAccounts = logisticsResult.state().marketAccounts();
        powerGrids = logisticsResult.state().powerGrids();
        launchUsageKg = new HashMap<>(logisticsResult.state().launchUsageKg());
        launchActivities = new ArrayList<>(logisticsResult.state().launchActivities());
        fleets = logisticsResult.state().fleets();
        empires = logisticsResult.state().empires();
        imperialFinance.recordTreasuryChanges(beforeTreasuries, empires);
        corporations = logisticsResult.state().corporations();

        SystemEconomyProcessor.SystemEconomyTurnResult economyResult = systemEconomyProcessor.processSystemEconomies(getGameState());
        systemEconomies = economyResult.updatedEconomies();
        empires = economyResult.updatedEmpires();

        HouseholdEconomyProcessor.TurnResult householdResult = householdEconomyProcessor.process(getGameState(), races);
        householdAccounts = householdResult.householdAccounts();
        orbitalStations = householdResult.orbitalStations();
        marketAccounts = householdResult.marketAccounts();
        commercialHubs = householdResult.commercialHubs();
        corporations = householdResult.corporations();
        industryAccounts = householdResult.industryAccounts();
        List<Empire> beforeIndustryPayroll = empires;
        empires = householdResult.empires();
        imperialFinance.recordTreasuryChanges(beforeIndustryPayroll, empires);

        updateIndustryAndPower(householdResult);
        CorporateProfitTaxProcessor.Result profitTax = corporateProfitTaxProcessor.process(getGameState());
        corporations = profitTax.corporations();
        corporateTaxAccounts = profitTax.accounts();

        PlanetaryMunicipalProcessor.MunicipalTurnResult municipalResult =
                planetaryMunicipalProcessor.processMunicipalFinances(getGameState(), householdResult,
                        profitTax.collectedByBody());
        planetaryBalanceSheets = municipalResult.balanceSheets();
        industryAccounts = municipalResult.industryAccounts();
        if (municipalResult.dispatchedCouriers() != null && !municipalResult.dispatchedCouriers().isEmpty()) {
            courierShips.addAll(municipalResult.dispatchedCouriers());
        }
        empires = municipalResult.updatedEmpires();
        imperialFinance.recordMunicipal(planetaryBalanceSheets, empires, municipalResult.newImperialDebtCredits());

        GameState currentState = getGameState();
        CrimeProcessor.CrimeResult crimeResult = crimeProcessor.processCrime(currentState);
        empires = crimeResult.empires();
        shadowSyndicates = crimeResult.shadowSyndicates();
    }

    private Planet updatePlanet(Planet p, String empireId) {
        return new Planet(
            p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(), 
            p.inclination(), p.diameter(), p.type(), p.atmosphere(), p.hasLiquidWater(), 
            p.waterLevel(), p.resources(),
            p.moons().stream().map(moon -> updateMoon(moon, empireId)).toList(),
            p.populations().stream().map(pop -> updatePopulation(pop, p.id(), empireId)).toList()
        );
    }

    private void ensureMarketAccounts() {
        java.util.Set<String> funded = marketAccounts.stream()
                .map(MarketAccount::hubId).collect(java.util.stream.Collectors.toSet());
        List<MarketAccount> updated = new ArrayList<>(marketAccounts);
        for (CommercialHub hub : commercialHubs) {
            if (!funded.add(hub.id())) continue;
            updated.add(new MarketAccount(hub.id(), 100_000.0));
        }
        marketAccounts = updated;
    }
    private Moon updateMoon(Moon m, String empireId) {
        return new Moon(
            m.id(), m.name(), m.description(), m.mass(), m.gravity(), m.distance(),
            m.diameter(), m.atmosphere(), m.hasLiquidWater(), m.waterLevel(), m.resources(),
            m.populations().stream().map(pop -> updatePopulation(pop, m.id(), empireId)).toList()
        );
    }
    private AsteroidBelt updateAsteroidBelt(AsteroidBelt ab, String empireId) {
        return new AsteroidBelt(
            ab.id(), ab.name(), ab.description(), ab.resources(),
            ab.populations().stream().map(pop -> updatePopulation(pop, ab.id(), empireId)).toList()
        );
    }
    private Population updatePopulation(Population pop, String bodyId, String empireId) {
        Race race = races.stream()
            .filter(r -> r.id().equals(pop.raceId()))
            .findFirst()
            .orElse(null);
        if (race == null) return pop;
        double weightedStress = 0.0;
        long countedPeople = 0L;
        for (HouseholdAccount account : householdAccounts) {
            if (!bodyId.equals(account.bodyId()) || !pop.raceId().equals(account.raceId())) continue;
            weightedStress += account.wellbeing().annualAverageShortfall() * account.headcount();
            countedPeople += account.headcount();
        }
        double averageStress = countedPeople == 0L ? 0.0 : weightedStress / countedPeople;
        double warStress = activeCivilianWarStress(empireId);
        return populationProcessor.advanceYears(pop, race, 1, 1, List.of(), averageStress + warStress);
    }

    private List<Population> fitOrbitalCapacity(List<Population> populations, long capacity) {
        long total = populations.stream().mapToLong(Population::totalCount).sum();
        if (total <= capacity || total == 0) return populations;
        double ratio = (double) capacity / total;
        List<Population> fitted = new ArrayList<>();
        long assigned = 0;
        for (Population population : populations) {
            Map<Integer, Long> ages = new HashMap<>();
            for (var age : population.ageGroups().entrySet()) {
                long count = (long) Math.floor(age.getValue() * ratio);
                if (count > 0) ages.put(age.getKey(), count);
                assigned += count;
            }
            fitted.add(new Population(population.raceId(), ages));
        }
        long remainder = capacity - assigned;
        for (int index = 0; index < fitted.size() && remainder > 0; index++, remainder--) {
            Population population = fitted.get(index);
            Map<Integer, Long> ages = new HashMap<>(population.ageGroups());
            int age = ages.keySet().stream().findFirst().orElse(18);
            ages.merge(age, 1L, Long::sum);
            fitted.set(index, new Population(population.raceId(), ages));
        }
        return List.copyOf(fitted);
    }

    double activeCivilianWarStress(String empireId) {
        if (empireId == null) return 0.0;
        return warDeclarations.stream()
                .filter(declaration -> empireId.equals(declaration.initiatorEmpireId()))
                .filter(declaration -> DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(
                        diplomacyProcessor.getDiplomaticTier(declaration.initiatorEmpireId(),
                                declaration.targetEmpireId(), diplomaticRelations)))
                .mapToDouble(declaration -> Math.max(0.0, -declaration.civilianHappinessPenalty()))
                .max().orElse(0.0);
    }

    Map<String, Double> activeCorporateTrustPenalties() {
        Map<String, Double> penalties = new HashMap<>();
        for (WarDeclarationRecord declaration : warDeclarations) {
            if (!DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(diplomacyProcessor.getDiplomaticTier(
                    declaration.initiatorEmpireId(), declaration.targetEmpireId(), diplomaticRelations))) {
                continue;
            }
            if (declaration.corporateTrustPenalty() < 0.0) {
                penalties.merge(declaration.initiatorEmpireId(), declaration.corporateTrustPenalty(), Math::min);
            }
        }
        return penalties;
    }
    @Override
    public synchronized GameState getGameState() {
        return new GameState(
                turn, running.get() ? "RUNNING" : "STOPPED", solarSystems, empires,
                corporations, commercialHubs, shadowSyndicates, diplomaticRelations,
                systemGovernors, researchProjects, technologyExchangeRoutes, shipDesigns,
                shipConstructionOrders, fleets, passengerManifests, geologicalDeposits,
                powerGrids, industrialFacilities, expansionProjects, orbitalStations,
                spaceElevators, constructionProjects, sleeperAgents, espionageOperations,
                pirateBases, terraformingProjects, megastructures, galacticCommunity,
                tradeRoutes, fogOfWarStates, systemEconomies, courierShips,
                planetaryBalanceSheets, imperialBalanceSheets, householdAccounts, marketAccounts,
                industryAccounts, corporateTaxAccounts, launchUsageKg, launchActivities,
                warDeclarations, diplomaticProposals, fleetEngagements, applicationOptimizations
        );
    }

    public ResearchProcessor getResearchProcessor() { return researchProcessor; }

    public FleetProcessor getFleetProcessor() { return fleetProcessor; }

    public ProspectingProcessor getProspectingProcessor() { return prospectingProcessor; }

    public PowerProcessor getPowerProcessor() { return powerProcessor; }

    public IndustryProcessor getIndustryProcessor() { return industryProcessor; }

    public TacticalCombatProcessor getTacticalCombatProcessor() { return tacticalCombatProcessor; }

    public OrbitalBombardmentProcessor getOrbitalBombardmentProcessor() { return orbitalBombardmentProcessor; }

    public ColonizationProcessor getColonizationProcessor() { return colonizationProcessor; }

    public MacroStructureProcessor getMacroStructureProcessor() { return macroStructureProcessor; }

    public EspionageProcessor getEspionageProcessor() { return espionageProcessor; }

    public RefinementProcessor getRefinementProcessor() { return refinementProcessor; }

    public TerraformingProcessor getTerraformingProcessor() {
        return terraformingProcessor;
    }

    public MegastructureProcessor getMegastructureProcessor() {
        return megastructureProcessor;
    }

    public SystemEconomyProcessor getSystemEconomyProcessor() {
        return systemEconomyProcessor;
    }

    public GalacticCommunityProcessor getGalacticCommunityProcessor() {
        return galacticCommunityProcessor;
    }

    public VictoryConditionChecker getVictoryConditionChecker() {
        return victoryConditionChecker;
    }

    public AudioSynthesizer getAudioSynthesizer() {
        return audioSynthesizer;
    }

    public CampaignSetup getCampaignSetup() {
        return campaignSetup;
    }

    public void setCampaignSetup(CampaignSetup campaignSetup) {
        this.campaignSetup = campaignSetup;
    }

    public List<GeoengineeringProject> getTerraformingProjects() {
        return terraformingProjects;
    }

    public List<Megastructure> getMegastructures() {
        return megastructures;
    }

    public GalacticCommunity getGalacticCommunity() {
        return galacticCommunity;
    }

    public void setGalacticCommunity(GalacticCommunity galacticCommunity) {
        this.galacticCommunity = galacticCommunity;
    }

    public GameClock getGameClock() {
        return gameClock;
    }

    public synchronized List<Planet> getAllPlanets() {
        List<Planet> all = new ArrayList<>();
        for (SolarSystem sys : solarSystems) {
            all.addAll(sys.planets());
        }
        return all;
    }

    public synchronized List<AtmosphericComposition> getAtmospheres() {
        return getGameState().atmosphericCompositions();
    }

    public synchronized void applyGameState(GameState state) {
        applyGameStateInternal(state);
    }

    private void applyGameStateInternal(GameState state) {
        if (state == null) return;
        this.turn = state.turn();
        this.solarSystems = new ArrayList<>(state.solarSystems());
        this.empires = new ArrayList<>(state.empires());
        this.corporations = new ArrayList<>(state.corporations());
        this.commercialHubs = new ArrayList<>(state.commercialHubs());
        this.shadowSyndicates = new ArrayList<>(state.shadowSyndicates());
        this.diplomaticRelations = new ArrayList<>(state.diplomaticRelations());
        this.systemGovernors = new ArrayList<>(state.systemGovernors());
        this.researchProjects = new ArrayList<>(state.researchProjects());
        this.technologyExchangeRoutes = new ArrayList<>(state.technologyExchangeRoutes());
        this.shipDesigns = new ArrayList<>(state.shipDesigns()); this.shipConstructionOrders = new ArrayList<>(state.shipConstructionOrders());
        this.fleets = new ArrayList<>(state.fleets());
        this.passengerManifests = new ArrayList<>(state.passengerManifests());
        this.geologicalDeposits = new ArrayList<>(state.geologicalDeposits());
        this.powerGrids = new ArrayList<>(state.powerGrids());
        this.industrialFacilities = new ArrayList<>(state.industrialFacilities());
        this.expansionProjects = new ArrayList<>(state.expansionProjects());
        this.orbitalStations = new ArrayList<>(state.orbitalStations());
        this.spaceElevators = new ArrayList<>(state.spaceElevators());
        this.constructionProjects = new ArrayList<>(state.constructionProjects());
        this.sleeperAgents = new ArrayList<>(state.sleeperAgents());
        this.espionageOperations = new ArrayList<>(state.espionageOperations());
        this.pirateBases = new ArrayList<>(state.pirateBases());
        this.terraformingProjects = new ArrayList<>(state.terraformingProjects());
        this.megastructures = new ArrayList<>(state.megastructures());
        this.galacticCommunity = state.galacticCommunity();
        this.tradeRoutes = new ArrayList<>(state.tradeRoutes());
        this.fogOfWarStates = new ArrayList<>(state.fogOfWarStates());
        if (state.systemEconomies() != null && !state.systemEconomies().isEmpty()) {
            this.systemEconomies = new ArrayList<>(state.systemEconomies());
        } else {
            this.systemEconomies = initializeDefaultSystemEconomies(this.solarSystems, this.empires);
        }
        if (state.planetaryBalanceSheets() != null) {
            this.planetaryBalanceSheets = new ArrayList<>(state.planetaryBalanceSheets());
        } else {
            this.planetaryBalanceSheets = new ArrayList<>();
        }
        this.courierShips = new ArrayList<>(state.courierShips());
        this.imperialBalanceSheets = new ArrayList<>(state.imperialBalanceSheets());
        this.householdAccounts = new ArrayList<>(state.householdAccounts());
        this.marketAccounts = new ArrayList<>(state.marketAccounts());
        this.industryAccounts = new ArrayList<>(state.industryAccounts());
        this.corporateTaxAccounts = new ArrayList<>(state.corporateTaxAccounts());
        this.launchUsageKg = new HashMap<>(state.launchUsageKg());
        this.launchActivities = new ArrayList<>(state.launchActivities());
        this.warDeclarations = new ArrayList<>(state.warDeclarations());
        this.diplomaticProposals = new ArrayList<>(state.diplomaticProposals());
        this.fleetEngagements = new ArrayList<>(state.fleetEngagements());
        this.applicationOptimizations = new ArrayList<>(state.applicationOptimizations());
    }

    public synchronized void recordCommandTreasuryChanges(List<Empire> before, List<Empire> after) {
        imperialFinance.recordCommands(before, after);
    }

    public List<PlanetaryBalanceSheet> getPlanetaryBalanceSheets() {
        return planetaryBalanceSheets;
    }

    public void setPlanetaryBalanceSheets(List<PlanetaryBalanceSheet> planetaryBalanceSheets) {
        this.planetaryBalanceSheets = planetaryBalanceSheets != null ? new ArrayList<>(planetaryBalanceSheets) : new ArrayList<>();
    }

    public List<SystemEconomy> getSystemEconomies() {
        return systemEconomies;
    }

    public void setSystemEconomies(List<SystemEconomy> systemEconomies) {
        this.systemEconomies = systemEconomies != null ? new ArrayList<>(systemEconomies) : new ArrayList<>();
    }

    private List<SystemEconomy> initializeDefaultSystemEconomies(List<SolarSystem> systems, List<Empire> empiresList) {
        List<SystemEconomy> list = new ArrayList<>();
        if (systems == null) return list;
        for (SolarSystem sys : systems) {
            long totalPop = 0;
            if (sys.planets() != null) {
                for (Planet p : sys.planets()) {
                    if (p.populations() != null) {
                        for (Population pop : p.populations()) {
                            totalPop += pop.totalCount();
                        }
                    }
                    if (p.moons() != null) {
                        for (Moon m : p.moons()) {
                            if (m.populations() != null) {
                                for (Population pop : m.populations()) {
                                    totalPop += pop.totalCount();
                                }
                            }
                        }
                    }
                }
            }
            if (totalPop > 0) {
                String ownerEmpireId = "terran_confederation";
                if (empiresList != null) {
                    for (Empire emp : empiresList) {
                        if (emp.controlledSystemIds() != null && emp.controlledSystemIds().contains(sys.id())) {
                            ownerEmpireId = emp.id();
                            break;
                        }
                    }
                }
                list.add(SystemEconomy.createDefault(sys.id(), ownerEmpireId, totalPop));
            }
        }
        return list;
    }

    public List<TradeRoute> getTradeRoutes() {
        return tradeRoutes;
    }

    public void setTradeRoutes(List<TradeRoute> tradeRoutes) {
        this.tradeRoutes = tradeRoutes != null ? new ArrayList<>(tradeRoutes) : new ArrayList<>();
    }

    public List<FogOfWarState> getFogOfWarStates() {
        return fogOfWarStates;
    }

    public void setFogOfWarStates(List<FogOfWarState> fogOfWarStates) {
        this.fogOfWarStates = fogOfWarStates != null ? new ArrayList<>(fogOfWarStates) : new ArrayList<>();
    }

    public LogisticsProcessor getLogisticsProcessor() {
        return logisticsProcessor;
    }

    public SensorProcessor getSensorProcessor() {
        return sensorProcessor;
    }

    public void reset(GameState state) {
        applyGameState(state);
        if (state != null) gameClock.alignTurn(state.turn());
    }
}
