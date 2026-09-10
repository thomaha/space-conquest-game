# Engine module
## Engine Module Rules
- All assignments (e.g., scientists, fleets, resource allocations) must be strictly validated against live model data here.
- Keep data models decoupled from visual frameworks. No `javafx.*` or `com.almasb.fxgl.*` imports allowed in this package.

This module contains the core game logic, the static data model and state management for the Space Conquest Game.

## Key components
- `GameEngine`: Interface defining the core loop and engine operations.
- `GameState`: Represents the current state of the game world.
- `SpaceConquestEngine`: Game engine executing turn-based updates across population, market pricing, corporate investments, crime and imperial governance.
- `DataModelLoader`: Loads and caches all static data from the JSON property files in `src/main/resources`.
- `GalaxyGenerator`: Procedurally generates galaxies (solar systems, planets, moons, resources, populations).
- `PopulationProcessor`: Handles population growth per race and age group.
- `MarketProcessor`: Computes spot prices, shortcoming scores, state tariffs and orbital lift costs across commercial hubs.
- `CorporateInvestmentProcessor`: Directs autonomous corporate capital allocations into facilities, training and ships based on market shortcomings.
- `CorporateFleetProcessor`: Executes autonomous corporate cargo trade arbitrage and asteroid mining operations.
- `CrimeProcessor`: Simulates localized crime metrics, police suppression, black market leakage and autonomous pirate fleet construction.
- `GovernanceProcessor`: Computes imperial cabinet ministerial synergies, profession background bonuses and system governor localized modifiers.
- `IdeologicalAccessionManager`: Manages democratic election cycles driven by demographic shortages, autocratic sovereign appointments and Hive Mind governance bypass logic.
- `DiplomacyProcessor`: Calculates territorial influence values, manages bilateral diplomatic relation tiers and enforces trade barriers.
- `GroundCombatProcessor`: Resolves planetary ground sieges, garrison mobilization, militia conscription and governor combat bonuses.
- `ResearchProcessor`: Simulates scientific progression, weighted variance breakthrough rolls, dual-path optimization and salvage reverse engineering.
- `ShipDesignValidator`: Validates modular spaceship blueprints against structural integrity ceilings, power balance, launch thrust-to-mass barriers and nanotech complexity tiers.
- `FleetProcessor`: Simulates sub-light localized movements, FTL warp bubble transits, fuel consumption and sensor ranges.
- `ProspectingProcessor`: Simulates stochastic discovery rolls ($P_{\text{discover}}$) with diminishing returns scarcity scaling and subterranean vein mining.
- `PowerProcessor`: Resolves turn-based planetary power balances, battery buffers and emergency brownout load shedding.
- `IndustryProcessor`: Manages recipe throughput, facility expansion pipelines (-50% output penalty) and public vs corporate ownership profit routing.
- `TacticalCombatProcessor`: Resolves multi-round tactical space combat with range brackets, shield absorption, carrier strike wings and ablative armor mitigation.
- `OrbitalBombardmentProcessor`: Resolves orbital bombardment weapons (kinetic dart pods, nuclear fission warheads, antimatter planet-crackers).
- `ColonizationProcessor`: Validates environmental habitability parameters and deploys colony ships to seed virgin worlds.
- `WarpNetwork`: Generates and pathfinds connected galactic hyperlane and warp corridor networks.
- `AnomalyProcessor`: Resolves sensor surveys on derelict starships, precursor ruins and subspace phenomena.
- `MacroStructureProcessor`: Resolves orbital space stations, modular station facilities, space elevators and construction vessel deployment pipelines.
- `EspionageProcessor`: Manages sleeper agent infiltration, covert intelligence missions, counter-espionage detection and syndicate extortion.
- `RefinementProcessor`: Executes chemical and metallurgical refinement recipes and evaluates colony living standard satisfaction.
- `TerraformingProcessor`: Simulates planetary atmospheric composition shifts, geoengineering macro-projects and biological terraforming cycles.
- `MegastructureProcessor`: Resolves multi-stage stellar engineering projects, Dyson spheres, star lifters and instantaneous hyperlane gateways.
- `SystemEconomyProcessor`: Simulates turn-based public sector budgets across Education, Law and order, Health and welfare, Infrastructure and Planetary militias, calculating sector efficiency indices, dynamic profession headcounts, happiness modifiers and accumulated militia investments.
- `GalacticCommunityProcessor`: Simulates legislative voting cycles, weighted democratic power calculations, resolution enactments and economic sanction enforcement.
- `VictoryConditionChecker`: Evaluates scenario victory objectives across domination, economic monopoly, megastructure ascension and diplomatic federation.
- `AudioSynthesizer`: Procedural sound generator providing audio feedback cues for UI, combat, warp transit and galactic senate sessions.
- `GameClock`: Manages real-time speed scaling, pause states, simulated hour progression and turn increments.
- `LogisticsProcessor`: Simulates automated cargo trade routes, warehouse inventory balancing and transit tariffs.
- `SensorProcessor`: Calculates sensor detection cones, uncovers uncharted star systems, detects foreign fleets and reveals hidden anomalies.
- `BiomeAdjacencyProcessor`: Calculates dynamic diameter-based grid dimensions, non-rigid spherical latitude biome allocations with reduced polar row places, gas giant states, direct deposit colocation, high-voltage power couplings and industrial pollution degradation.
- `CustomEmpireBuilder`: Validates genetic trait budgets, instantiates custom species bio-architectures and registers customized sovereign empires.
- `CohortFragmentationProcessor`: Simulates single-education cohort fragmentation across colonies, multi-profession facility staffing bottlenecks, administrative bureaucrat allocations and upward social mobility retraining.

## Data model records
| Record | Resource file | Description |
| --- | --- | --- |
| `SolarSystem`, `Planet`, `Moon`, `AsteroidBelt` | `solar_systems.json` | Celestial bodies and their hierarchy |
| `Race` | `races.json` | Playable and NPC races |
| `Material` | `materials.json` | Raw materials, minerals, degenerate matter, quantum substrates and refined resources |
| `Technology`, `TechnicalApplication` | `technologies.json` | Technology tree with applications |
| `StarProperty` | `star_properties.json` | Hertzsprung-Russell mapping of star mass to spectral type and color |
| `Profession` | `professions.json` | Professions the population can be trained for |
| `Population` | part of `solar_systems.json` | Population per race, segregated by age group |
| `Empire` | `empires.json` | Sovereign empires, state treasuries, society structures and portfolio assignments |
| `Corporation` | `corporations.json` | Autonomous private corporations, capital reserves and asset holdings |
| `MinistryPortfolio` | `ministries.json` | Imperial ministerial portfolio definitions and efficiency curves |
| `CommercialHub` | runtime state / save | Commercial trading hubs, orders, tariffs and logistics ranges |
| `TradeRoute` | runtime state / save | Automated cargo transport supply routes between commercial hubs |
| `FogOfWarState` | runtime state / save | Explored systems, scanned planets, detected foreign fleets and discovered anomalies |
| `SurfaceTile` | runtime state / save | Discrete planetary surface tiles, regional biomes, deposits and constructed facilities |
| `PlanetBiomeGrid` | runtime state / save | 2D surface grid layout and orthogonal adjacency topology |
| `SpeciesTrait` | runtime / catalog | Positive and negative biological traits modifying empire research, production and growth |
| `IdeologicalEthics` | runtime / configuration | Multi-axis philosophical ethics distribution and societal governance biases |
| `CustomEmpireProfile` | runtime / configuration | Complete custom sovereign empire configuration profile |
| `SystemGovernor` | runtime state / save | Solar system governors providing economic and crime suppression bonuses |
| `ShadowSyndicate` | runtime state / save | Illicit corporate factions and rogue pirate fleets |
| `DiplomaticRelation` | runtime state / save | Bilateral diplomatic relations and mutual tariff discounts |
| `DiplomaticPact` | runtime state / save | Ratified bilateral treaties and accords across sovereign empires |
| `DiplomaticProposal` | runtime state / save | Bilateral treaty proposals undergoing ratification |
| `CasusBelli` | runtime state / save | Legitimate war justification and grievance score tracking |
| `ResearchProject` | runtime state / save | Active scientific research projects, accumulated points and assigned scientists |
| `ResearchVarianceResult` | runtime calculation | Stochastic breakthrough multipliers and complexity shifts |
| `TechnologyExchangeRoute` | runtime state / save | Bilateral technology sharing routes and scientist training speed bonuses |
| `ShipHullFrame` | runtime / catalog | Starframe structural skeleton blueprints and slot capacities |
| `ShipModule` | runtime / catalog | Functional spaceship modules, stats, power draw and material requirements |
| `ShipDesign` | runtime state / save | Physics-validated custom spaceship blueprints |
| `ShipInstance` | runtime state / save | Instantiated operational spacecraft platforms with passenger stasis modes |
| `Fleet` | runtime state / save | Deployed naval, cargo and mining fleets with warp transits and stances |
| `WarpLane` | runtime state / save | Connected hyperlane and warp corridors between star systems |
| `Anomaly` | runtime state / save | Deep-space points of interest, derelicts and cosmic anomalies |
| `GeologicalDeposit` | runtime state / save | Subterranean mineral veins, remaining volume and discovery status |
| `PowerGridState` | runtime state / save | Connected planetary energy grid balances and battery storage buffers |
| `IndustrialFacility` | runtime state / save | Scalable manufacturing facilities, workforce assignments and ownership types |
| `FacilityExpansionProject` | runtime state / save | Active facility tier expansion projects and work hour progress |
| `SurfaceMassDriver` | runtime state / save | Surface-to-orbit catapult arrays for zero-g orbital cargo transfer |
| `OrbitalStation` | runtime state / save | Space stations in planetary orbit or deep space with modular slots |
| `StationModule` | runtime / catalog | Specialized station modules for command, power, foundries and hangars |
| `SpaceElevator` | runtime state / save | Megastructures reducing surface-to-orbit launch costs to near zero |
| `ConstructionDeploymentProject` | runtime state / save | Assembly progress of macro-structures by construction vessels |
| `CarrierWing` | runtime state / save | Specialized fighter and bomber wings deployed from carriers |
| `PlanetaryDefenseBattery` | runtime state / save | Surface-to-orbit anti-ship kinetic and laser batteries |
| `SleeperAgent` | runtime state / save | Infiltrated covert operatives embedded in colonies and corporations |
| `EspionageOperation` | runtime state / save | Active covert missions including tech theft and power sabotage |
| `PirateBase` | runtime state / save | Hidden rogue asteroid bases and illicit capital pools |
| `RefinementRecipe` | runtime / catalog | Multi-stage chemical and metallurgical refinement recipes |
| `AtmosphericComposition` | runtime state / save | Atmospheric gas ratios, surface pressure, equilibrium temperature and biome classifications |
| `GeoengineeringProject` | runtime state / save | Planetary terraforming projects, solar mirrors, greenhouse gas factories and biological cultures |
| `Megastructure` | runtime state / save | Grand stellar engineering megastructures, Dyson swarms, star lifters and hyperlane gateways |
| `SystemEconomy` | runtime state / save | Solar system public economy tracking sector allocations, budgets, efficiency indices, employee headcounts and accumulated militia investments |
| `GalacticResolution` | runtime state / save | Pan-galactic senate resolutions, treaties, charters and voting tallies |
| `GalacticSanction` | runtime state / save | Enforced economic trade embargoes, asset freezes and military intervention mandates |
| `GalacticCommunity` | runtime state / save | Galactic senate legislative assembly, member registries and enacted charters |
| `CampaignSetup` | runtime / configuration | Customizable scenario settings, victory conditions and starting technology tiers |
| `OrbitalLiftProfile` | runtime calculation | Surface-to-orbit launch cost breakdown including propellant, delta-v, spaceport and turnaround wear fees |
| `CitizenCohort`, `ColonyDemographics` | runtime state / save | Single-education citizen cohort fragments, continuous weighted capacity units and colony demographic compositions |

## Physical units and standard measurement system
All simulation mechanics, celestial data definitions and calculations standardize on the International System of Units (SI) to prevent unit conversion discrepancies across subsystems:
- **gravitational acceleration:** Measured in meters per second squared (m/s²). Earth standard gravity is 9.81 m/s², while species preferred gravity and celestial body surface gravity use this unit directly.
- **mass:** Measured in kilograms (kg). Spaceship dry mass, loaded cargo, material stockpiles and resource yields use kilograms.
- **force and thrust:** Measured in Newtons (N). Propulsion outputs, thruster ratings and launch force barriers use Newtons.
- **temperature:** Measured in Kelvin (K). Ambient celestial surface temperatures, thermal tolerances and atmospheric entry friction use Kelvin.
- **power and energy:** Measured in kilowatts (kW) and kilowatt-hours (kWh). Power generation, facility power draw and battery buffers use kilowatts.
- **distance and diameter:** Measured in kilometers (km) for celestial diameters, orbital radii and star system positions, with astronomical units (AU) or light years for interstellar distances.
- **pressure:** Measured in standard atmospheres (atm) or kilopascals (kPa) for planetary gas envelopes and atmospheric drag calculations.

## Conventions
- all game component properties are read from property files in the resources folder; no hard-coded game values.
- data model classes are immutable Java records mapped by Jackson.
- `DataModelLoader` caches loaded lists; call `DataModelLoader.clearCache()` to force a reload.
- unknown JSON properties are ignored, so data files can be extended before the records are updated.
