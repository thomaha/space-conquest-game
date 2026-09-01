package com.spaceconquest.engine;

import com.spaceconquest.engine.audio.AudioSynthesizer;
import com.spaceconquest.engine.combat.ColonizationProcessor;
import com.spaceconquest.engine.combat.OrbitalBombardmentProcessor;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.community.GalacticCommunityProcessor;
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
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryProcessor;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.industry.PowerProcessor;
import com.spaceconquest.engine.industry.ProspectingProcessor;
import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import com.spaceconquest.engine.logistics.LogisticsProcessor;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.MacroStructureProcessor;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.market.CorporateFleetProcessor;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.market.CrimeProcessor;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.megastructure.MegastructureProcessor;
import com.spaceconquest.engine.scenario.CampaignSetup;
import com.spaceconquest.engine.scenario.VictoryConditionChecker;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.technology.ResearchProcessor;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.technology.ResearchVarianceResult;
import com.spaceconquest.engine.technology.TechnologyExchangeRoute;
import com.spaceconquest.engine.terraforming.AtmosphericComposition;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;
import com.spaceconquest.engine.terraforming.TerraformingProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
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
    private List<Fleet> fleets = new ArrayList<>();
    private List<GeologicalDeposit> geologicalDeposits = new ArrayList<>();
    private List<PowerGridState> powerGrids = new ArrayList<>();
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

    private final PopulationProcessor populationProcessor = new PopulationProcessor();
    private final MarketProcessor marketProcessor = new MarketProcessor();
    private final CorporateInvestmentProcessor corporateInvestmentProcessor = new CorporateInvestmentProcessor();
    private final CorporateFleetProcessor corporateFleetProcessor = new CorporateFleetProcessor(marketProcessor);
    private final LogisticsProcessor logisticsProcessor = new LogisticsProcessor();
    private final SensorProcessor sensorProcessor = new SensorProcessor();
    private final CrimeProcessor crimeProcessor = new CrimeProcessor();
    private final SystemEconomyProcessor systemEconomyProcessor = new SystemEconomyProcessor();
    private final GovernanceProcessor governanceProcessor = new GovernanceProcessor();
    private final IdeologicalAccessionManager accessionManager = new IdeologicalAccessionManager(governanceProcessor);
    private final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();
    private final TerritoryProcessor territoryProcessor = new TerritoryProcessor(diplomacyProcessor);
    private final GroundCombatProcessor groundCombatProcessor = new GroundCombatProcessor();
    private final ResearchProcessor researchProcessor = new ResearchProcessor();
    private final FleetProcessor fleetProcessor = new FleetProcessor();
    private final ProspectingProcessor prospectingProcessor = new ProspectingProcessor();
    private final PowerProcessor powerProcessor = new PowerProcessor();
    private final IndustryProcessor industryProcessor = new IndustryProcessor();
    private final TacticalCombatProcessor tacticalCombatProcessor = new TacticalCombatProcessor();
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
            solarSystems = DataModelLoader.loadSolarSystems();
            races = DataModelLoader.loadRaces();
            materials = DataModelLoader.loadMaterials();
            empires = DataModelLoader.loadEmpires();
            corporations = DataModelLoader.loadCorporations();
            ministryPortfolios = DataModelLoader.loadMinistries();
            commercialHubs = new ArrayList<>();
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
        } catch (java.io.IOException e) {
            logger.error("Failed to load data", e);
        }
    }

    public SpaceConquestEngine(GameStartScenario scenario) {
        try {
            races = DataModelLoader.loadRaces();
            materials = DataModelLoader.loadMaterials();
            ministryPortfolios = DataModelLoader.loadMinistries();
            GalaxyGenerator generator = new GalaxyGenerator();
            GameState generated = generator.generateGameState(10, scenario);
            applyGameState(generated);
        } catch (java.io.IOException e) {
            logger.error("Failed to load scenario data", e);
        }
    }

    public SpaceConquestEngine(SaveGame saveGame) {
        try {
            races = DataModelLoader.loadRaces();
            materials = DataModelLoader.loadMaterials();
            ministryPortfolios = DataModelLoader.loadMinistries();
            solarSystems = saveGame.solarSystems();
            empires = saveGame.empires();
            corporations = saveGame.corporations();
            commercialHubs = saveGame.commercialHubs();
            shadowSyndicates = saveGame.shadowSyndicates();
            diplomaticRelations = saveGame.diplomaticRelations();
            systemGovernors = saveGame.systemGovernors();
            researchProjects = saveGame.researchProjects();
            technologyExchangeRoutes = saveGame.technologyExchangeRoutes();
            shipDesigns = saveGame.shipDesigns();
            fleets = saveGame.fleets();
            geologicalDeposits = saveGame.geologicalDeposits();
            powerGrids = saveGame.powerGrids();
            industrialFacilities = saveGame.industrialFacilities();
            expansionProjects = saveGame.expansionProjects();
            orbitalStations = saveGame.orbitalStations();
            spaceElevators = saveGame.spaceElevators();
            constructionProjects = saveGame.constructionProjects();
            sleeperAgents = saveGame.sleeperAgents();
            espionageOperations = saveGame.espionageOperations();
            pirateBases = saveGame.pirateBases();
            terraformingProjects = saveGame.terraformingProjects();
            megastructures = saveGame.megastructures();
            galacticCommunity = saveGame.galacticCommunity();
            tradeRoutes = saveGame.tradeRoutes();
            fogOfWarStates = saveGame.fogOfWarStates();
            if (saveGame.systemEconomies() != null && !saveGame.systemEconomies().isEmpty()) {
                systemEconomies = new ArrayList<>(saveGame.systemEconomies());
            } else {
                systemEconomies = initializeDefaultSystemEconomies(solarSystems, empires);
            }
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
        turn++;
        gameClock.stepTurn();
        logger.info("Advancing to turn {}", turn);

        // 1. Advance demographics, births, mortality
        updatePopulations();

        // 2. Update Governance, ministerial efficiencies & elections
        updateGovernance();

        // 3. Update Research progress & breakthrough checks
        updateResearch();

        // 4. Update Markets, Corporate Investments, Logistics, Crime
        updateMarketsAndEconomy();

        // 5. Update Power Grids, Industrial Facilities, Expansions
        updateIndustryAndPower();

        // 6. Update Space Stations & Macro-structures
        updateMacroStructures();

        // 7. Update Covert Espionage & Syndicate Operations
        updateEspionage();

        // 8. Update Fleets movements and FTL warps
        updateFleets();

        // 9. Update Planetary Terraforming & Atmospheric Geoengineering
        updateTerraforming();

        // 10. Update Stellar Megastructures & Energy Harvesting
        updateMegastructures();

        // 11. Update Galactic Senate & Interstellar Legislation
        updateGalacticCommunity();

        // 12. Update Courier Ship logistics and delivery
        updateCouriers();

        // 13. Trigger audio turn cue
        audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_TURN_ADVANCE);
    }

    private void updateCouriers() {
        if (courierShips.isEmpty()) return;

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
    }

    private void updateTerraforming() {
        if (terraformingProjects.isEmpty()) return;

        List<GeoengineeringProject> remainingProjects = new ArrayList<>();
        for (GeoengineeringProject proj : terraformingProjects) {
            AtmosphericComposition currentAtmo = new AtmosphericComposition(
                    proj.planetId(),
                    Map.of("oxygen_gas", 0.05, "nitrogen_gas", 0.60, "carbon_dioxide", 0.25, "toxic_aerosols", 0.10),
                    proj.targetPressureAtm() > 0 ? proj.targetPressureAtm() * 0.8 : 0.5,
                    proj.targetTemperatureK() > 0 ? proj.targetTemperatureK() * 0.9 : 250.0,
                    1.2, 25.0, AtmosphericComposition.BIOME_BARREN, false
            );
            TerraformingProcessor.TerraformingTurnResult res = terraformingProcessor.processPlanetTerraforming(
                    currentAtmo, List.of(proj)
            );
            if (res.updatedProjects() != null) {
                remainingProjects.addAll(res.updatedProjects());
            }
            if (res.biomeTransformed() && res.updatedAtmosphere().isBreathable()) {
                audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_TERRAFORM_COMPLETE);
            }
        }
        terraformingProjects = remainingProjects;
    }

    private void updateMegastructures() {
        if (megastructures.isEmpty()) return;

        MegastructureProcessor.MegastructureTurnResult megaRes = megastructureProcessor.processMegastructures(megastructures);
        megastructures = megaRes.updatedMegastructures();
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

        // Advance construction deployment projects
        MacroStructureProcessor.ConstructionTurnResult constrRes = macroStructureProcessor.advanceConstructionProjects(
                constructionProjects,
                !empires.isEmpty() ? empires.get(0).id() : "state"
        );
        constructionProjects = constrRes.remainingProjects();
        if (!constrRes.newlyCompletedStations().isEmpty()) {
            orbitalStations.addAll(constrRes.newlyCompletedStations());
        }
        if (!constrRes.newlyCompletedElevators().isEmpty()) {
            spaceElevators.addAll(constrRes.newlyCompletedElevators());
        }
    }

    private void updateEspionage() {
        EspionageProcessor.EspionageTurnResult espRes = espionageProcessor.processEspionageTurn(
                espionageOperations, sleeperAgents, pirateBases, empires
        );
        espionageOperations = espRes.updatedOperations();
        sleeperAgents = espRes.updatedAgents();
        pirateBases = espRes.updatedBases();
    }

    private void updateIndustryAndPower() {
        // 1. Balance power grids
        List<PowerGridState> balancedGrids = new ArrayList<>();
        boolean globalBrownout = false;
        for (PowerGridState grid : powerGrids) {
            PowerGridState balanced = powerProcessor.balanceGrid(
                    grid.entityId(),
                    grid.totalGenerationKw(),
                    grid.totalDemandKw(),
                    grid.currentStoredKwh(),
                    grid.batteryCapacityKwh()
            );
            if (balanced.isDeficitBrownoutActive()) {
                globalBrownout = true;
            }
            balancedGrids.add(balanced);
        }
        powerGrids = balancedGrids;

        // 2. Load shedding during brownouts
        List<IndustrialFacility> activeFacilities = powerProcessor.applyEmergencyLoadShedding(industrialFacilities, globalBrownout);

        // 3. Process Industrial Production Cycles & Ownership routing
        IndustryProcessor.IndustryTurnResult result = industryProcessor.processIndustrialProduction(
                activeFacilities,
                expansionProjects,
                empires,
                corporations,
                systemEconomies,
                solarSystems,
                0.05
        );

        industrialFacilities = result.updatedFacilities();
        expansionProjects = result.remainingProjects();
        empires = result.updatedEmpires();
        corporations = result.updatedCorporations();

        // Point 1: Collect newly spawned couriers
        if (result.spawnedCouriers() != null) {
            courierShips.addAll(result.spawnedCouriers());
        }
    }

    private void updateFleets() {
        fleets = fleetProcessor.processFleetMovements(fleets, orbitalStations, diplomaticRelations);
        fogOfWarStates = sensorProcessor.updateSensorCoverage(
                empires, solarSystems, fleets, shipDesigns, orbitalStations,
                List.of(), pirateBases, fogOfWarStates
        );
    }

    private void updateResearch() {
        List<ResearchProject> updatedProjects = new ArrayList<>();
        Map<String, List<String>> newlyUnlockedTechs = new HashMap<>();

        for (ResearchProject project : researchProjects) {
            Empire empire = empires.stream()
                    .filter(e -> e.id().equals(project.empireId()))
                    .findFirst()
                    .orElse(null);
            Race race = (empire != null) ? races.stream()
                    .filter(r -> r.id().equalsIgnoreCase(empire.raceId()))
                    .findFirst()
                    .orElse(null) : null;

            ResearchProject advanced = researchProcessor.advanceProject(project, race, empire, technologyExchangeRoutes);
            if (advanced.isComplete()) {
                ResearchVarianceResult outcome = researchProcessor.rollBreakthrough();
                logger.info("Research complete for empire {} on {}: outcome {}",
                        project.empireId(), project.targetTechOrAppId(), outcome.outcomeType());
                newlyUnlockedTechs.computeIfAbsent(project.empireId(), k -> new ArrayList<>())
                        .add(project.targetTechOrAppId());
            } else {
                updatedProjects.add(advanced);
            }
        }

        researchProjects = updatedProjects;

        if (!newlyUnlockedTechs.isEmpty()) {
            empires = empires.stream().map(emp -> {
                List<String> toAdd = newlyUnlockedTechs.get(emp.id());
                if (toAdd != null && !toAdd.isEmpty()) {
                    List<String> combinedTechs = new ArrayList<>(emp.unlockedTechIds());
                    for (String t : toAdd) {
                        if (!combinedTechs.contains(t)) {
                            combinedTechs.add(t);
                        }
                    }
                    return new Empire(
                            emp.id(), emp.name(), emp.raceId(), emp.societyStructure(),
                            emp.treasuryCredits(), emp.corporateTaxRate(), emp.controlledSystemIds(),
                            emp.ministries(), emp.systemGovernorAssignments(), combinedTechs, emp.activeShipDesignIds()
                    );
                }
                return emp;
            }).toList();
        }
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

        // 3. Democratic periodic election cycles (every 5 turns for Individualist societies)
        if (turn % 5 == 0 && !commercialHubs.isEmpty()) {
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
            .map(ss -> new SolarSystem(
                ss.id(), ss.name(), ss.description(), ss.x(), ss.y(), ss.z(),
                ss.sunMass(), ss.sunDiameter(), ss.sunColor(),
                ss.planets().stream().map(this::updatePlanet).toList(),
                ss.asteroidBelts().stream().map(this::updateAsteroidBelt).toList()
            ))
            .toList();
    }

    private void updateMarketsAndEconomy() {
        // 1. Update Market pricing and shortcomings
        commercialHubs = marketProcessor.updateCommercialHubs(commercialHubs);

        // 2. Autonomous Corporate Investments
        // Calculate trust penalties for investment freeze (Point 2)
        Map<String, Double> trustPenalties = new HashMap<>();
        // In a real scenario, these would come from recent war declarations or events
        // For now, we initialize an empty map
        corporations = corporateInvestmentProcessor.processCorporateInvestments(corporations, commercialHubs, trustPenalties);

        // 3. Corporate Fleet Logistics and Arbitrage
        Map<String, Double> gravityMap = new HashMap<>();
        Map<String, Double> atmosphereMap = new HashMap<>();
        for (SolarSystem sys : solarSystems) {
            for (Planet p : sys.planets()) {
                gravityMap.put(p.id(), p.gravity());
                atmosphereMap.put(p.id(), 1.0); // baseline atmosphere factor
                for (Moon m : p.moons()) {
                    gravityMap.put(m.id(), m.gravity());
                    atmosphereMap.put(m.id(), 0.0);
                }
            }
        }
        CorporateFleetProcessor.CorporateFleetResult fleetResult = corporateFleetProcessor.processFleetOperations(
                corporations, commercialHubs, diplomaticRelations, gravityMap, atmosphereMap
        );
        corporations = fleetResult.corporations();
        commercialHubs = fleetResult.commercialHubs();

        // 4. Automated Trade & Logistics Routes
        LogisticsProcessor.LogisticsResult logisticsResult = logisticsProcessor.processTradeRoutes(
                tradeRoutes, commercialHubs, empires, corporations
        );
        tradeRoutes = logisticsResult.updatedTradeRoutes();
        commercialHubs = logisticsResult.updatedCommercialHubs();
        empires = logisticsResult.updatedEmpires();
        corporations = logisticsResult.updatedCorporations();

        // 5. System Economies & Public Sector Budgeting
        SystemEconomyProcessor.SystemEconomyTurnResult economyResult = systemEconomyProcessor.processSystemEconomies(getGameState());
        systemEconomies = economyResult.updatedEconomies();
        empires = economyResult.updatedEmpires();

        // 6. Crime and Black Market Leakage
        GameState currentState = getGameState();
        CrimeProcessor.CrimeResult crimeResult = crimeProcessor.processCrime(currentState);
        empires = crimeResult.empires();
        shadowSyndicates = crimeResult.shadowSyndicates();
    }

    private Planet updatePlanet(Planet p) {
        return new Planet(
            p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(), 
            p.inclination(), p.diameter(), p.type(), p.atmosphere(), p.hasLiquidWater(), 
            p.waterLevel(), p.resources(),
            p.moons().stream().map(this::updateMoon).toList(),
            p.populations().stream().map(this::updatePopulation).toList()
        );
    }

    private Moon updateMoon(Moon m) {
        return new Moon(
            m.id(), m.name(), m.description(), m.mass(), m.gravity(), m.distance(),
            m.diameter(), m.atmosphere(), m.hasLiquidWater(), m.waterLevel(), m.resources(),
            m.populations().stream().map(this::updatePopulation).toList()
        );
    }

    private AsteroidBelt updateAsteroidBelt(AsteroidBelt ab) {
        return new AsteroidBelt(
            ab.id(), ab.name(), ab.description(), ab.resources(),
            ab.populations().stream().map(this::updatePopulation).toList()
        );
    }

    private Population updatePopulation(Population pop) {
        Race race = races.stream()
            .filter(r -> r.id().equals(pop.raceId()))
            .findFirst()
            .orElse(null);
        if (race == null) return pop;
        return populationProcessor.advanceYears(pop, race, 1);
    }

    @Override
    public GameState getGameState() {
        return new GameState(
                turn,
                running.get() ? "RUNNING" : "STOPPED",
                solarSystems,
                empires,
                corporations,
                commercialHubs,
                shadowSyndicates,
                diplomaticRelations,
                systemGovernors,
                researchProjects,
                technologyExchangeRoutes,
                shipDesigns,
                fleets,
                geologicalDeposits,
                powerGrids,
                industrialFacilities,
                expansionProjects,
                orbitalStations,
                spaceElevators,
                constructionProjects,
                sleeperAgents,
                espionageOperations,
                pirateBases,
                terraformingProjects,
                megastructures,
                galacticCommunity,
                tradeRoutes,
                fogOfWarStates,
                systemEconomies
        );
    }

    public ResearchProcessor getResearchProcessor() {
        return researchProcessor;
    }

    public FleetProcessor getFleetProcessor() {
        return fleetProcessor;
    }

    public ProspectingProcessor getProspectingProcessor() {
        return prospectingProcessor;
    }

    public PowerProcessor getPowerProcessor() {
        return powerProcessor;
    }

    public IndustryProcessor getIndustryProcessor() {
        return industryProcessor;
    }

    public TacticalCombatProcessor getTacticalCombatProcessor() {
        return tacticalCombatProcessor;
    }

    public OrbitalBombardmentProcessor getOrbitalBombardmentProcessor() {
        return orbitalBombardmentProcessor;
    }

    public ColonizationProcessor getColonizationProcessor() {
        return colonizationProcessor;
    }

    public MacroStructureProcessor getMacroStructureProcessor() {
        return macroStructureProcessor;
    }

    public EspionageProcessor getEspionageProcessor() {
        return espionageProcessor;
    }

    public RefinementProcessor getRefinementProcessor() {
        return refinementProcessor;
    }

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

    public List<Planet> getAllPlanets() {
        List<Planet> all = new ArrayList<>();
        for (SolarSystem sys : solarSystems) {
            all.addAll(sys.planets());
        }
        return all;
    }

    public List<AtmosphericComposition> getAtmospheres() {
        List<AtmosphericComposition> atmoList = new ArrayList<>();
        for (SolarSystem sys : solarSystems) {
            for (Planet p : sys.planets()) {
                boolean isBreathable = p.atmosphere() != null && !p.atmosphere().equalsIgnoreCase("none") && !p.atmosphere().equalsIgnoreCase("toxic");
                atmoList.add(new AtmosphericComposition(
                        p.id(),
                        isBreathable ? Map.of("oxygen_gas", 0.21, "nitrogen_gas", 0.78, "carbon_dioxide", 0.01)
                                : Map.of("nitrogen_gas", 0.60, "carbon_dioxide", 0.30, "toxic_aerosols", 0.10),
                        isBreathable ? 1.0 : 0.4,
                        isBreathable ? 288.0 : 220.0,
                        1.0, 10.0,
                        isBreathable ? AtmosphericComposition.BIOME_BREATHABLE_TERRESTRIAL : AtmosphericComposition.BIOME_BARREN,
                        isBreathable
                ));
            }
        }
        return atmoList;
    }

    public void applyGameState(GameState state) {
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
        this.shipDesigns = new ArrayList<>(state.shipDesigns());
        this.fleets = new ArrayList<>(state.fleets());
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
    }
}
