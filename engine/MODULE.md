# Engine module
## Engine module rules
- All assignments (e.g., scientists, fleets, resource allocations) must be strictly validated against live model data before they change the engine state.
- Keep data models decoupled from visual frameworks. No `javafx.*` or `com.almasb.fxgl.*` imports allowed in this package.

This module contains the core game logic, the static data model and state management for the Space Conquest Game.

## Key components
- `GameEngine`: Interface defining the core loop and engine operations.
- `GameState`: Represents the current state of the game world.
- `SpaceConquestEngine`: Game engine executing turn-based updates across population, market pricing, corporate investments, crime and imperial governance.
- `DataModelLoader`: Loads and caches all static data from the JSON property files in `src/main/resources`.
- `GalaxyGenerator`: Procedurally generates galaxies (solar systems, planets, moons, resources, populations).
- `StartingEconomySeeder`: Gives generated homeworlds mixed public and corporate facilities, owner corporations, household cash and opening market stock without running a simulation day. Starter food and consumer-goods capacity and opening input reserves scale with population so tracked needs are met through the first month.
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
- `PowerGenerationProcessor`, `PowerPlantCatalog`, `PowerProcessor` and `PowerBillingProcessor`: Buy plant-specific fuel, measure staffed local generation, balance daily grid demand and batteries, cap industrial shifts during brownouts and settle local electricity bills and plant sales.
- `CorporateProfitTaxProcessor`: Settles tax on realized profit across a corporation's facilities, carries losses and unpaid liability and credits actual payments to populated municipal accounts. Fleet and other corporate income is not included yet.
- `IndustryProcessor`: Advances facility expansion projects and retains mass-driver calculations; its former estimated profit calculation has been removed.
- `IndustryMarketProcessor` and `IndustryRecipeCatalog`: Gate material recipes on technology and paid workers, buy inputs from local hubs, deplete ore deposits, deliver output within hub cash and storage limits and record actual owner proceeds.
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
- `GameClock`: Owns the campaign calendar, selected era start date, speed and pause state. Real-time pulses accumulate partial days and return the number of daily turns due; yearly demographic and five-year election boundaries derive from the same calendar.
- `LogisticsProcessor`: Simulates automated cargo trade routes, warehouse inventory balancing and transit tariffs.
- `SensorProcessor`: Calculates sensor detection cones, uncovers uncharted star systems, detects foreign fleets and reveals hidden anomalies.
- `BiomeAdjacencyProcessor`: Calculates dynamic diameter-based grid dimensions, non-rigid spherical latitude biome allocations with reduced polar row places, gas giant states, direct deposit colocation, high-voltage power couplings and industrial pollution degradation.
- `CustomEmpireBuilder`: Validates genetic trait budgets, instantiates custom species bio-architectures and registers customized sovereign empires.
- `CohortFragmentationProcessor`: Simulates single-education cohort fragmentation across colonies, multi-profession facility staffing bottlenecks, administrative bureaucrat allocations and upward social mobility retraining.
- `PlanetaryMunicipalProcessor`: Calculates local public revenues, expenses, reserves and persistent debt, then settles system contributions and subsidies.
- `HouseholdEconomyProcessor`: Derives household groups from live age-group populations, pays funded public and private jobs, collects income tax and purchases available market goods.
- `ImperialFinanceCoordinator`: Records treasury movements during the simulation day, carries imperial debt and applies later cash to outstanding obligations.

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
| `PlanetaryBalanceSheet` | runtime state / save | Localized municipal accounting record tracking gross planetary product, public revenues, operational costs, uncollected liquid reserves and central subsidies |
| `ImperialBalanceSheet` | runtime state / save | Last-day central treasury receipts, expenses, debt and principal repayment |
| `HouseholdAccount`, `MarketAccount`, `IndustryAccount` | runtime state / save | Persistent household savings, hub trading cash, facility stock and daily transaction figures |

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
- Game component properties should be read from resource files where practical. The current simulation still has hard-coded values and catalogs.
- Prefer immutable Java records for data models and snapshots. Current `GameState` nested collections are not all defensively copied and some live model objects are mutable.
- Create new snapshots with `GameState.builder()` and modify an existing snapshot with `toBuilder()` or a `with...` method. The removed partial positional constructors could silently discard newer fields.
- `DataModelLoader` caches loaded lists; call `DataModelLoader.clearCache()` to force a reload.
- unknown JSON properties are ignored, so data files can be extended before the records are updated.

## Current integration gaps

- Generated campaigns use the starting era's calendar and opening economy. The default static Sol fixture loaded by the no-argument engine constructor is separate from scenario generation. Power plants deliver measured grid electricity, buy different fuels and receive local electricity sales. Industrial households need electricity as a tier 1 good. Industry pays for powered shifts before production. Stored battery electricity has no owner ledger and is not billed yet. Cargo terminals have no metered surface-to-orbit service. The terminal is distinct from `CommercialHub`; existing interplanetary fleet and route trading does not use it and that wider freight design remains unsettled. Facility types and refinement recipes do not yet share one application catalog.
- Local industry and household purchases do not pay a transport charge, and same-body industry sales do not pay a gross transaction tariff. VAT is unimplemented. `CorporateProfitTaxProcessor` assesses realized profit across each corporation's facilities after carried losses and records unpaid tax on the corporation's persistent tax account; paid tax enters a populated local municipal account. Fleet and other corporate income is not included yet.
- `SpaceConquestEngine` owns the live world collections and runs the turn processors. `GameState` is the transfer shape, but it is not yet a fully isolated immutable snapshot. `applyGameState` restores couriers and local and imperial balance sheets.
- Market processing precedes system budgets, household purchases, material industry transactions and municipal accounting in the live turn. `CitizenCohort` and `ColonyDemographics` are derived for the household pass but are not persistent; `PopulationProcessor` still updates the older age-group model. Household purchases deplete market supply and fund hub purchases from producers, but desired demand does not yet feed the spot-price formula. Opening and retail stock have no seller identity. Unmet needs do not yet change health, happiness or population growth.
- `WarpNetwork` pathfinding and `TacticalCombatProcessor` exist, but ordinary fleet movement does not route through the network or initiate tactical combat. Generated anomalies are not passed into the normal sensor turn.
- `CorporateInvestmentProcessor` can record planned asset IDs without creating the corresponding world entities. Engine corporate investment also overlaps with the control-layer corporation AI.
- `SaveGame.fromGameState` and `SaveGame.toGameState` map all current snapshot fields, while the calendar and speed remain separate save metadata. The data model table describes intended runtime and save ownership, although not every newer live field is covered by the save format. System debt is an aggregate of local debt and has no separate ledger.
