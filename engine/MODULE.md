# Engine module
## Engine module rules
- All assignments (e.g., scientists, fleets, resource allocations) must be strictly validated against live model data before they change the engine state.
- Keep data models decoupled from visual frameworks. No `javafx.*` or `com.almasb.fxgl.*` imports allowed in this package.

This module contains the core game logic, the static data model and state management for the Space Conquest Game.

## Key components
- `GameEngine`: Interface defining the core loop and engine operations.
- `GameState`: Represents the current state of the game world.
- `SpaceConquestEngine`: Game engine executing turn-based updates across population, market pricing, corporate investments, crime and imperial governance.
- `DataModelLoader`: Loads and caches shared catalogs from `src/main/resources` and the preserved Sol fixture from `src/main/resources/scenarios/sol`.
- `GalaxyGenerator`: Procedurally generates galaxies (solar systems, planets, moons, resources, populations).
- `StartingEconomySeeder` and `OpeningMarketSeeder`: Give generated homeworlds mixed public and corporate facilities, owner corporations, household cash, demand-sized trading capital and short opening reserves without running a simulation day. Starter food, water, fertilizer, common refining and consumer-goods capacity scale with population so tracked needs are met through the first month.
- `PopulationProcessor`: Handles population growth per race and age group.
- `MarketProcessor`: Computes spot prices, shortcoming scores, state tariffs and orbital lift costs across commercial hubs.
- `CorporateInvestmentProcessor`: Validates and converts funded shortages into real corporation-owned factories or ships and blueprints.
- `CorporateValuation`: Estimates book net worth from cash, tracked assets, priced unsold stock, paid cargo aboard assigned routes and unpaid tax.
- `CrimeProcessor`: Simulates localized crime metrics, police suppression, black market leakage from recorded daily household and industry purchases and at most one abstract pirate ship per syndicate per day.
- `GovernanceProcessor`: Computes imperial cabinet ministerial synergies, profession background bonuses and system governor localized modifiers.
- `IdeologicalAccessionManager`: Manages democratic election cycles driven by demographic shortages, autocratic sovereign appointments and Hive Mind governance bypass logic.
- `DiplomacyProcessor`: Calculates territorial influence values, manages bilateral diplomatic relation tiers and enforces trade barriers.
- `GroundCombatProcessor`: Resolves planetary ground sieges, garrison mobilization, militia conscription and governor combat bonuses.
- `ResearchProcessor`: Simulates scientific progression, weighted variance breakthrough rolls, dual-path optimization and salvage reverse engineering.
- `ShipDesignValidator`: Validates modular spaceship blueprints against structural integrity ceilings, power balance, launch thrust-to-mass barriers and nanotech complexity tiers.
- `FleetProcessor` and `LocalTravel`: Commit a loaded-mass and drive-dependent propellant budget before local departure, then advance one- or two-day journeys between surface, orbit, dock and deep-space sites before persistent warp or sublight interstellar transit. Fission and fusion maneuvers also commit carried reactor fuel when required.
- `InterstellarTravel`, `PropulsionCatalog` and `ShipFueling`: Build a fleet itinerary from each ship's design thrust, loaded mass and provisional drive exhaust velocity. RP-1/LOX, methane/LOX and hydrogen/LOX drives buy fuel and oxidizer separately and carry their combined mass in one propellant tank. Recognized chemical, fission, fusion, MPD and antimatter drives limit peak speed by tank contents, reserve braking propellant and burn the planned quantity during daily acceleration and braking. Fission additionally requires refined uranium or thorium carried as reactor fuel. Fusion uses hydrogen as reaction mass and gets better exhaust from carried deuterium or fusion pellets. Reactor fuel is bought at a local hub and committed at departure; orbital delivery uses a launch service. Legacy designs without a recognized drive still use the earlier unlimited-fuel profile.
- `ProspectingProcessor`: Simulates stochastic discovery rolls ($P_{\text{discover}}$) with diminishing returns scarcity scaling and subterranean vein mining.
- `PowerGenerationProcessor`, `PowerPlantCatalog`, `PowerProcessor` and `PowerBillingProcessor`: Buy plant-specific fuel, measure staffed local generation, balance daily grid demand and batteries, cap industrial shifts during brownouts and settle local electricity bills and plant sales.
- `CorporateProfitTaxProcessor`: Settles tax on realized profit across a corporation's facilities and assigned trade routes, carries losses and unpaid liability and credits actual payments to populated municipal accounts. Mining and other fleet income are not included yet.
- `IndustryProcessor`: Advances facility expansion projects and retains mass-driver calculations; its former estimated profit calculation has been removed.
- `IndustryMarketProcessor` and `IndustryRecipeCatalog`: Gate material recipes on technology and workers, consume local inputs, deplete ore deposits and deliver output within modeled demand, hub cash and storage limits. Surface-water treatment requires liquid water and ice treatment consumes mined ice. Power plants add their daily fuel needs to local market demand. Private and public facilities settle credit costs and proceeds; Hive grid facilities use assigned workers and physical stock without credit settlement.
- `TacticalCombatProcessor`: Resolves multi-round tactical space combat with range brackets, shield absorption, carrier strike wings and ablative armor mitigation.
- `OrbitalBombardmentProcessor`: Resolves orbital bombardment weapons (kinetic dart pods, nuclear fission warheads, antimatter planet-crackers).
- `ColonizationProcessor`: Validates environmental habitability parameters and deploys colony ships to seed virgin worlds.
- `WarpNetwork`: Generates and pathfinds connected galactic hyperlane and warp corridor networks.
- `AnomalyProcessor`: Resolves sensor surveys on derelict starships, precursor ruins and subspace phenomena.
- `MacroStructureProcessor`: Advances material-backed daily work for orbital stations, station modules, space elevators and construction vessel deployments. A completed commerce module opens a commercial hub on its station.
- `EspionageProcessor`: Manages sleeper agent infiltration, covert intelligence missions, counter-espionage detection and syndicate extortion.
- `RefinementProcessor`: Executes chemical and metallurgical refinement recipes and evaluates colony living standard satisfaction.
- `TerraformingProcessor`: Advances material-backed geoengineering work and simulates planetary atmospheric composition shifts and biological terraforming cycles.
- `MegastructureProcessor`: Resolves multi-stage stellar engineering projects, Dyson spheres, star lifters and instantaneous hyperlane gateways.
- `SystemEconomyProcessor`: Simulates turn-based public sector budgets across Education, Law and order, Health and welfare, Infrastructure and Planetary militias, calculating sector efficiency indices, dynamic profession headcounts, happiness modifiers and accumulated militia investments.
- `GalacticCommunityProcessor`: Simulates legislative voting cycles, weighted democratic power calculations, resolution enactments and economic sanction enforcement.
- `VictoryConditionChecker`: Evaluates scenario victory objectives across domination, economic monopoly, megastructure ascension and diplomatic federation.
- `AudioSynthesizer`: Procedural sound generator providing audio feedback cues for UI, combat, warp transit and galactic senate sessions.
- `GameClock`: Owns the campaign calendar, selected era start date, speed and pause state. Real-time pulses accumulate partial days and return the number of daily turns due; yearly demographic and five-year election boundaries derive from the same calendar.
- `LogisticsProcessor`: Advances assigned cargo ships between hubs, buys physical cargo with owner cash at loading, settles estimated ground export lift into the origin hub's trading account and sells cargo against destination hub cash and storage on arrival, with configured destination transit tariffs and realized route results.
- `SensorProcessor`: Calculates sensor detection cones, uncovers uncharted star systems, detects foreign fleets and reveals hidden anomalies.
- `BiomeAdjacencyProcessor`: Calculates dynamic diameter-based grid dimensions, non-rigid spherical latitude biome allocations with reduced polar row places, gas giant states, direct deposit colocation, high-voltage power couplings and industrial pollution degradation.
- `CustomEmpireBuilder`: Validates genetic trait budgets, instantiates custom species bio-architectures and registers customized sovereign empires.
- `CohortFragmentationProcessor`: Simulates single-education cohort fragmentation across colonies, multi-profession facility staffing bottlenecks, administrative bureaucrat allocations and upward social mobility retraining.
- `PlanetaryMunicipalProcessor`: Calculates local public revenues, sector-funded wages, means-tested welfare expenses, reserves and persistent debt, then settles system transfers and selected public industry support. Welfare uses unspent health and welfare allocation before adding to local expenses.
- `HouseholdEconomyProcessor`: Derives household groups from live age-group populations, caps public service payroll within its own sector allocation, pays industry workers from corporate or facility balances, collects income tax, tops up purchasing power for available basic goods and expected electricity and purchases available market goods.
- `ImperialFinanceCoordinator`: Records treasury movements during the simulation day, carries imperial debt and applies later cash to outstanding obligations.

## Data model records
| Record | Resource file | Description |
| --- | --- | --- |
| `SolarSystem`, `Planet`, `Moon`, `AsteroidBelt` | `scenarios/sol/solar_systems.json` | Preserved Sol scenario bodies and their hierarchy |
| `Race` | `races.json` | Playable and NPC races |
| `Material` | `materials.json` | Raw materials, minerals, degenerate matter, quantum substrates and refined resources |
| `Technology`, `TechnicalApplication` | `technologies.json` | Technology tree with applications |
| `StarProperty` | `star_properties.json` | Hertzsprung-Russell mapping of star mass to spectral type and color |
| `Profession` | `professions.json` | Professions the population can be trained for |
| `Population` | part of `scenarios/sol/solar_systems.json` | Scenario population per race and age group |
| `Empire` | `scenarios/sol/empires.json` | Preserved scenario empires and state treasuries |
| `Corporation` | `scenarios/sol/corporations.json` | Preserved scenario corporations and holdings |
| `MinistryPortfolio` | `ministries.json` | Imperial ministerial portfolio definitions and efficiency curves |
| `CommercialHub` | runtime state / save | Commercial trading hubs, orders, tariffs and logistics ranges |
| `TradeRoute` | runtime state / save | Assigned cargo routes with physical shipment phase, capitalized cargo cost and daily and cumulative trading results |
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
| `Fleet` and `FleetLocation` | runtime state / save | Deployed ships sharing an exact site, local travel progress, warp transit and stance |
| `WarpLane` | runtime state / save | Connected hyperlane and warp corridors between star systems |
| `Anomaly` | runtime state / save | Deep-space points of interest, derelicts and cosmic anomalies |
| `GeologicalDeposit` | runtime state / save | Subterranean mineral veins, remaining volume and discovery status |
| `PowerGridState` | runtime state / save | Connected planetary energy grid balances and battery storage buffers |
| `IndustrialFacility` | runtime state / save | Scalable manufacturing facilities, workforce assignments and ownership types |
| `FacilityExpansionProject` | runtime state / save | Active facility tier expansion projects and work hour progress |
| `SurfaceMassDriver` | runtime state / save | Surface-to-orbit catapult arrays for zero-g orbital cargo transfer |
| `OrbitalStation` | runtime state / save | Space stations in planetary orbit or deep space with modular slots |
| `StationModule` | runtime / catalog | Specialized station modules for command, power, foundries and hangars |
| `SpaceElevator` | runtime state / save | Powered surface-to-orbit provider with daily payload capacity for goods and passenger ships |
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
| `HouseholdAccount`, `MarketAccount`, `IndustryAccount` | runtime state / save | Persistent household savings, hub trading cash, facility stock, public operating balances and subsidy policy and daily transaction figures. Generated hubs start with trading capital and pay producers 80% of retail spot price. |

## Physical units and standard measurement system
All simulation mechanics, celestial data definitions and calculations standardize on the International System of Units (SI) to prevent unit conversion discrepancies across subsystems:
- **gravitational acceleration:** Measured in meters per second squared (m/s²). Earth standard gravity is 9.81 m/s², while species preferred gravity and celestial body surface gravity use this unit directly.
- **mass:** Measured in kilograms (kg). Spaceship dry mass, loaded cargo, material stockpiles and resource yields use kilograms.
- **force and thrust:** Measured in Newtons (N). Propulsion outputs, thruster ratings and launch force barriers use Newtons.
- **temperature:** Measured in Kelvin (K). Ambient celestial surface temperatures, thermal tolerances and atmospheric entry friction use Kelvin.
- **power and energy:** Measured in kilowatts (kW) and kilowatt-hours (kWh). Power generation, facility power draw and battery buffers use kilowatts.
- **distance and diameter:** Celestial diameters and orbital radii use kilometers (km); generated star system coordinates use light-years for interstellar distances.
- **pressure:** Measured in standard atmospheres (atm) or kilopascals (kPa) for planetary gas envelopes and atmospheric drag calculations.

## Conventions
- Game component properties should be read from resource files where practical. The current simulation still has hard-coded values and catalogs.
- Prefer immutable Java records for data models and snapshots. Current `GameState` nested collections are not all defensively copied and some live model objects are mutable.
- Create new snapshots with `GameState.builder()` and modify an existing snapshot with `toBuilder()` or a `with...` method. The removed partial positional constructors could silently discard newer fields.
- `DataModelLoader` caches loaded lists; call `DataModelLoader.clearCache()` to force a reload.
- unknown JSON properties are ignored, so data files can be extended before the records are updated.

## Current integration gaps

- Generated campaigns use the starting era's calendar and opening economy. The no-argument engine constructor starts with no world; `SpaceConquestEngine.fromSolScenario()` explicitly loads the preserved static Sol fixture. Public facilities receive opening operating capital from their empire, then keep their own sale revenue and pay wages, inputs, fuel, power and maintenance; opted-in loss-making facilities may receive local infrastructure and industry support. Power plants deliver measured grid electricity and industrial households need electricity as a tier 1 good. Industry pays for powered shifts before production. Stored battery electricity has no owner ledger and is not billed yet. Cargo terminals provide rocket lift using purchased RP-1 and liquid oxygen, mass-driver facilities lift goods using available power and elevators lift goods or ships carrying passengers. Provider throughput resets each daily turn. Mass drivers and elevators reserve one hour of power per launch; their prepaid generator-backed electricity is settled through the daily grid and corporate launch earnings enter profit tax. The terminal is distinct from `CommercialHub`. Facility types and refinement recipes do not yet share one application catalog.
- Local industry and household purchases do not pay a transport charge, and same-body industry sales do not pay a gross transaction tariff. VAT is unimplemented. `CorporateProfitTaxProcessor` assesses realized profit across each corporation's facilities, space elevators and assigned trade routes after carried losses and records unpaid tax on the corporation's persistent tax account; paid tax enters a populated local municipal account. Mining and other fleet income are not included yet.
- `SpaceConquestEngine` owns the live world collections and runs the turn processors. `GameState` is the transfer shape, but it is not yet a fully isolated immutable snapshot. `applyGameState` restores couriers and local and imperial balance sheets.
- Market demand is refreshed from current residents and active recipe inputs before price processing, system budgets, household purchases, material industry transactions and municipal accounting. `CitizenCohort` and `ColonyDemographics` are derived for the household pass but are not persistent; `PopulationProcessor` still updates the older age-group model. Household accounts persist filled jobs, unemployment pressure, material and electricity coverage, rolling wellbeing and annual shortage stress. Basic shortages reduce survival and births at the annual demographic update. Household purchases deplete market supply and fund hub purchases from producers. Opening and retail stock have no seller identity. Explicit healthcare, detailed qualifications and wage competition remain future work; household profession shares persist and unemployed workers retrain gradually for funded vacancies.
- `WarpNetwork` pathfinding and `TacticalCombatProcessor` exist, but ordinary fleet movement does not route through the network or initiate tactical combat. Generated anomalies are not passed into the normal sensor turn.
- Corporate investments create corresponding world entities. New factories begin as tier-zero construction sites and complete after 500 work hours of daily project progress; corporate ship orders wait for daily work and material purchases at their selected ground or orbital yard. Generic bills also gate stations, elevators and megastructure stages. Ground projects consume stock only from their own body's hub. Space projects consume stock from a station hub at the build site or from an owner's cargo or construction ship at that site. Cargo loading buys from a source body's hub and pays an available launch provider. Fleet locations distinguish surface, orbit, dock and deep-space sites with daily local journey progress. Explicit offworld passenger bookings remove residents from the source population and keep a ship manifest until arrival at the named surface destination; free seats do not attract passengers. Automated routes require an owned cargo ship, buy stock from the origin hub, carry it aboard and sell it on arrival to a destination hub with sufficient cash and storage. Routes buy local-maneuver fuel at an accessible hub when needed and include that purchase in daily operating results. Route results also account for cargo purchase, launch cost, wholesale sale and configured destination tariff. Paid cargo aboard a route contributes to corporate book net worth. Local travel time and maneuver requirements remain provisional; shipyard capacity, component-level receipts, construction labor and crew remain unimplemented. Automatic corporate fleet arbitrage and mining income were removed because they created credits without paid transactions or extraction. Stock ownership, dividends and capital issuance are future work.
- The old list-only `LogisticsProcessor.processTradeRoutes` overload remains deprecated for compatibility tests. It is not called by the game tick and still computes instant transfers if invoked directly.
- Terraforming projects now buy physical inputs before daily progress, but the resulting atmospheric composition is still recalculated in the processor and not persisted in `GameState`.
- `SaveGame.fromGameState` and `SaveGame.toGameState` map all current snapshot fields, while the calendar and speed remain separate save metadata. The data model table describes intended runtime and save ownership, although not every newer live field is covered by the save format. System debt is an aggregate of local debt and has no separate ledger.
