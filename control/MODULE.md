# Control module

Mass-driver point-defense application research requires completed matching mass_drivers. Blueprint gates require pd_mass_drivers and owned electromagnetic weapon development. LoadOrbitalCargoCommand permits equipped combat ships to buy and lift kinetic cartridges while retaining stock, funds, lift and capacity gates and transport-only access for other goods. Focused research, construction and loading tests cover these paths. Existing surface loading and save fields suffice. See [CombatModules.md](../CombatModules.md#kinetic-point-defense).

Existing application research validation requires completed laser_weapons before pd_lasers can develop. Blueprint validation separately requires that defensive application and matching owned laser research before laser point-defense construction. CombatModuleTest checks both gates, captured electrical demand, receipts and saved designs. No generic defensive technology or foreign weapon development substitutes. See [CombatModules.md](../CombatModules.md#weapon-specific-point-defense).

LoadOrbitalCargoCommand admits launcher-equipped ships for guided missile ammunition while retaining transport restrictions for other cargo. Stock, funds, local access, lift services, passenger mass and capacity remain validated on the command tick. Existing surface loading remains unchanged. CombatModuleTest and LoadOrbitalCargoCommandTest cover researched construction and finite paid ammunition loading. See [CombatModules.md](../CombatModules.md#missile-weapons).

BodyFleetStandOffTest checks actual planetary and lunar vector and native coast arrival at a firing offset with matched target velocity, paid braking and unchanged movement through cancellation, lost coverage and save/load. Ranged history distinguishes WEAPON_RANGE from physical rendezvous. Unfunded departure preserves waiting intent and stores. See [InterceptOrders.md](../InterceptOrders.md#moving-stand-off-around-a-body).

FleetStandOffApproachTest uses separately owned cannon and researched laser designs to check actual stand-off arrival range and paid capture fuel. Dry-tank rejection, moving-target exclusion, queued interception, cancellation and save/load retain existing command boundaries and committed physical motion. See [InterceptOrders.md](../InterceptOrders.md#funded-stand-off-parking).

Protection tests place immediate threats within installed weapon reach. ProtectionInterceptTest checks that stronger out-of-range forces do not trigger range-based escape but still block a weak guard's approach using full funded potential. Moving escape fixtures retain actual observed position and velocity for the nearby hostile coast. TradeEscapeTest uses an interrupted paid approach and a reachable hostile coast, retaining purchased cargo and stores. Paid-cargo return is checked after a funded resumed leg reaches its port before settlement. Its unavailable-buyer case removes the market while retaining physical port geometry for return. See [CombatModules.md](../CombatModules.md#shared-assessment-and-combat-inputs).

InterceptFleetCommandTest installs cannon hardware for its encounter fixture and checks that an already reachable target at a distinct orbit does not start another approach or spend resources. BodyFleetRendezvousTest starts launches outside cannon range and retains paid physical matching checks. Velocity mismatch prevents rendezvous while live weapon reach can still permit combat; a blind hunter can still be attacked by a detected opponent. No command grants firing reach or changes actual motion directly. See [CombatModules.md](../CombatModules.md#weapon-engagement-ranges).

SetFleetShieldPolicyCommand validates a unique owned fleet and non-null recharge policy before changing immutable intent on the simulation tick. It spends no energy and changes no itinerary or shield charge. FleetShieldPolicyCommandTest verifies queued execution, foreign and missing fleet rejection, save/load, older automatic defaults and policy retention through copies, rename and split. See [CombatModules.md](../CombatModules.md#fleet-shield-policy).

Guard and interception cancellation commands recognize ordinary orders paused by repair recovery and remove saved restoration intent. Paused escort detachment validates and applies the existing split against restored snapshot membership while keeping both fragments' actual paid travel. OrdinaryFleetRepairCommandTest covers cancellations, foreign-owner rejection, paid progress and stance supersession. All mutations remain tick-owned commands. See [FleetProtection.md](../FleetProtection.md).

AddStationModuleCommand gates base repair workshop construction on owned electricity, industrial production and space stations research. It uses canonical slots, mass, power and engineer requirements instead of caller-supplied free-workshop values. BaseRepairModuleTest verifies research rejection and physical construction projects. See [BaseRepairs.md](../BaseRepairs.md).

ShipRepairModuleTest verifies owned research gates for workshops, rejection despite foreign research, power demand, mass and construction receipts. Ordinary blueprint commands validate installation and older designs retain their hardware. Repairs execute on the engine tick without direct frontend mutations or cross-empire blueprint grants. See [ShipRepairs.md](../ShipRepairs.md).

CombatModuleTest verifies owned research gates for installed weapons and shields, rejection despite foreign research, advanced hardware, electrical demand, physical construction receipts and persistence. Design and update commands use ShipBlueprintFactory validation. Automatic defensive interception requires positive available offensive strength, including persistent mount condition through ShipCombatStats. Cargo loading, nationalization and passenger-mode commands preserve ShipModuleDamage when copying ships. Tactical and protection fixtures explicitly install their hardware. No blueprint or research sharing is introduced. See [CombatModules.md](../CombatModules.md).

ParkedEscortReturnTest verifies tick-owned physical Earth and Moon rendezvous from recovered coast to owned parked convoys. It checks initial phase mismatch, actual paid approach, saved escort identity, preserved convoy stores, save/load, empty-tank and different-body waiting and missed contact after convoy movement. A real funded evacuation to a parking refuge completes later physical escort reattachment. OrbitalPhasingTest protects the existing station phasing behavior. No frontend mutation path or save schema is added. See [FleetProtection.md](../FleetProtection.md).

DetachedEscortConvoyTest verifies automatic convoy evacuation while its escort pursues or recovers, exclusion of absent escort strength, paid movement, save/load, empty-tank waiting and explicit-order precedence. Removed, empty, foreign and supply-only relationships cannot authorize convoy escape. A moving convoy retains actual state through evacuation and later physical escort reattachment at its new refuge. No frontend mutation path or new schema is introduced. See [FleetProtection.md](../FleetProtection.md).

AutomaticPursuitWithdrawalTest verifies real paid defensive pursuit followed by withdrawal after enemy reinforcement or friendly damage. It checks retained guard and detached escort identity, actual motion continuity, paid feed, persistence, eventual guard restoration and atomic rejection for a dry member or nearby explicit pursuit. Supply transport orders remain blocking. Explicit player intercepts retain precedence. See [FleetProtection.md](../FleetProtection.md).

RenewedProtectionThreatTest verifies repeated retreat for waiting and traveling automatic protection returns, preserved original guard and convoy assignments, actual course-change continuity, blocked escape, safer-route continuation, save/load and eventual restoration. Explicit intercepts retain precedence. Physical circular guard restoration checks the correct body and radius and keeps actual motion. No new frontend mutation path or save schema is introduced. See [FleetProtection.md](../FleetProtection.md).

GuardEscapeReturnTest verifies retained guard assignments through paid coordinated escape and funded return, save/load, weaker remaining danger, known danger at home despite a quiet refuge, lost ownership, shared fuel on the actual recovered coast, insufficient-fuel waiting and explicit stand-down. Save version 56 records escape-return provenance without changing old pursuit-return semantics. The engine applies recovery on the simulation tick; no new frontend mutation path is introduced. See [FleetProtection.md](../FleetProtection.md).

ProtectionInterceptTest covers tick-owned defensive guard and idle escort pursuit with actual funded movement, persistence, freight retention, insufficient-force hold and shared corporate sensor revocation. ProtectionReturnTest covers conserved fuel sharing, pooled donors, recovery splits, paid base refills through a real engine tick, physical transport delivery and reattachment to a moved convoy. Saved protection survives pursuit cancellation while explicit player intercept cancellation retains its existing semantics. These mechanics use ordinary travel, purchases and fleet organization without adding a frontend mutation path. See [FleetProtection.md](../FleetProtection.md).

MovingEscortReturnTest verifies tick-owned reattachment to a still-moving owned convoy, paid later braking, save/load, missed contact and replanning from actual drift, shared-fuel returns and recovery splitting. Earth and Moon body coasts and native stellar maneuver budgets use the existing physical rendezvous planners. Friendly contact is independent of hostile combat authorization. A real engine tick verifies that encounter cleanup retains return intent and completes reattachment. No new frontend command or mutation path is introduced. See [FleetProtection.md](../FleetProtection.md).

MovingFleetProtectionTest covers real paid moving convoy course changes, native ellipse state conversion, gravity-free refuge travel, save/load, escort retention, blocked alternatives and continuation toward safety. It checks actual proximity rather than shared departure labels. No additional queued player action or UI physics planner is introduced. See [FleetProtection.md](../FleetProtection.md).

FleetProtectionTest checks threat-based protection decisions, coordinated paid escape, persistence, escort retention, unavailable reinforcements, damaged ships, lost sensor coverage and shared corporate contacts. Explicit player intercept commands retain their existing meaning. See [FleetProtection.md](../FleetProtection.md).

InterceptFleetCommand also accepts interrupted body-frame coasts and missed body captures. CancelOrbitalTravelCommand clears pursuit intent when stopping gravity-vector thrust. BodyFleetRendezvousTest covers paid Earth and Moon interception, persistence, cancellation, altered motion and lost shared corporate coverage through ordinary simulation ticks.

ProposeDiplomaticPactCommand and ResolveDiplomaticProposalCommand support reciprocal SENSOR_DATA_SHARING between two empires or a corporation and an empire, requiring explicit receiver acceptance. RevokeSensorSharingCommand checks either signatory. Total-war transitions revoke affected sensor grants through corporate affiliation too. SensorSharingTest verifies current shared hardware and negotiation guards without granting unrelated relation tiers or blueprints. StationEphemerisTest checks actual source station state and paid rendezvous between distinct same-altitude ports.

Existing queued MoveFleetLocalCommand validation includes funded orbital phasing when a direct station approach fails. OrbitalPhasingTest verifies higher Earth and Moon orbits, a lower lunar orbit, actual paid docking, forecast agreement, save/load, cancellation and empty-tank rejection. No separate command or direct frontend mutation is added.

InterceptFleetCommand validates owned idle military hulls, current detected same-system wartime targets and conflicting commitments. CancelInterceptFleetCommand clears onward pursuit while retaining a committed physical journey. InterceptFleetCommandTest checks funded parking travel, current contacts, cancellation, persistence and target priority. See [InterceptOrders.md](../InterceptOrders.md).

MovingFleetRendezvousTest verifies paid moving contacts through real coordinated power and movement ticks, rejection of unreachable or unfunded approaches, sensor loss, changed motion, cancellation and save/load. No new UI-side physical planner or blueprint sharing is introduced.

The same tests now cover three-dimensional sideways approaches, interrupted vector burns and funded recovery through RecoverFleetTravelCommand. Cancellation retains the vector trajectory and older flight JSON defaults its optional vector state to null.

This module handles input, autonomous decision agents and game command execution pipelines for players and AI controllers.

OrbitalRendezvousTest verifies funded stellar coast interception, numerical agreement with analytic orbital motion, power failure, cancellation and save/load with independently owned advanced blueprints and yards. RecoverStellarTravelCommand validates ownership, funded gravity-aware recovery and passenger supplies for a selected circular stellar orbit. CancelOrbitalTravelCommand also cancels stellar vector thrust without advancing time. StellarRecoveryTest verifies real fuel and power ticks, circular arrival and continued coast, failure, cancellation and persistence. Ordinary RecoverFleetTravelCommand continues to reject stellar states that require this explicit orbit choice.

## Key components

MoveFleetLocalCommand now quotes and queues ordinary station docking through the shared physical approach after native parking or interbody capture. Saved version 53 approach budgets remain partitioned through fleet organization. Cancellation during the vector approach preserves actual position, velocity and spent stores; a circular canceled coast can be replanned directly. OrdinaryStationDockingTest checks Earth, Mars and Moon arrivals, saved approaches, quote agreement, low fuel or power, lost stations and split/merge. The chemical freight fixture rejects an unfunded physical approach without dispatching or selling its mixed load.

MoveFleetLocalCommand permits a completed circular body coast to plan ordinary funded docking or same-body altitude travel through RecoveredParkingTravel. It retains the existing trade commitment, fuel, power and passenger gates. CancelOrbitalTravelCommand restores physical source coast before a maneuver or after funded circularization. RecoveredParkingTravelTest verifies Earth and Moon queued physical station rendezvous, actual-phase departure, persistence at the restored campaign epoch, paid accounting, missed position or velocity contact, lost stations, power failure and cancellation. Stellar and cross-body departures from recovered vectors remain outside this parking adapter.

FailedStellarCaptureRecoveryTest verifies real capture rejection, queued circular recovery, physical state preservation, paid movement and save/load before and after conversion. RecoverStellarTravelCommand supports both interrupted stellar vectors and failed atomic stellar captures. Active controlled coasts require cancellation first; waiting, captured and planetary or lunar states are rejected by the stellar planner.

RecoverOrbitalTravelCommand uses the single OrbitalRecovery planner for stellar, planetary and lunar gravity frames. It rechecks ownership, optional expected frame, actual reserves, electrical endurance and passenger supplies on execution. RecoverStellarTravelCommand remains a stellar-only compatibility entry point. OrbitalRecoveryTest verifies paid Earth and Moon recovery, physical sensor coordinates, cancellation, power failure, frame persistence, bounds, stale frame rejection and older gravity JSON. Planetary and lunar combat interception are separate unsupported operations.

- Sensor modules are checked against owner research during blueprint registration and ship construction. AddStationModuleCommand uses authoritative sensor mass, slots and power values and queues the normal material-backed station assembly. ScanSystemCommand requires a live owned sensor in the target system. See [SensorModules.md](../SensorModules.md).

- `SetHubStockTargetCommand` configures or removes per-material reorder thresholds and target stock at hubs in controlled systems. It validates finite quantities, known market materials and combined storage targets on the command tick. A new local material entry begins with zero stock. See [HubStockTargets.md](../HubStockTargets.md).

- `SetHubStockTargetsCommand` validates a reviewed group against live ownership, known materials and combined replacement capacity, then applies every ingredient atomically. It preserves unrelated targets and starts new market entries with zero stock.

- Local commands and queued crossings use physical local plans and projected arrival reserves. Sublight crossings provisionally retain half the main tank for the crossing during local departure. Physical local recovery uses current motion and funds a new braking plan; it checks electricity, booked passengers and any retained crossing budget. Older prepaid local and warp recovery keep their existing progress. See [LocalTravel.md](../LocalTravel.md).
- `PassengerDepartureReadiness` shares cached species checks across local movement, crossing and recovery commands. Local duration and crossing duration are rounded separately to match daily ticks; recovery uses only the remaining itinerary or replacement trajectory. No fixed four-day local allowance remains. Checks require each booked carrier's own stores and leave failed commands unchanged. `PassengerJourneyReadinessTest` covers long local travel, queued crossings, stasis, local and warp resumption, physical recovery and save/load.
- `CreateTradeRouteCommand` offers optional roaming mode with an initial port and no predefined destination. Fixed-origin mode remains the default. Ownership, carrier commitments and physical route constraints still validate on the command tick. Engine planning can carry several goods within the shared load limit. Cancellation preserves mode and every carried manifest lot's quantity and paid cost.
- Departure authorization funds the selected next leg without requiring a return itinerary. Return readiness remains cosmetic. Automated trade uses engine next-port checks and tick-owned local purchases; it never assumes remote resupply or fuel-free reduced-speed propulsion. See [FleetSupply.md](../FleetSupply.md).
- `BuyShipSupplyFuelCommand`, `ScheduleFleetSupplyCommand` and `CancelFleetSupplyCommand` validate bulk purchases and combined coasting electrical, propellant and reactor feed reservations. Suppliers can append deliveries and cancel an individual order or their entire schedule. Live checks include reserved stock, pump conflicts, later deliveries, endurance and braking safety. `MoveFleetCommand` provides destination stores and an immediate return preview separately from its authoritative departure validation. See [FleetSupply.md](../FleetSupply.md).
- `ShareFleetFuelCommand` stages an immutable request and reviewed transfer list. Live validation rejects changed allocations atomically and preserves supplier reserves. See [FleetSupply.md](../FleetSupply.md).
- `TransferFleetShipsCommand`, `MergeFleetsCommand`, `SplitFleetCommand` and `RenameFleetCommand` validate live ownership, unique membership, selected ships, location and mission commitments before changing fleets. Removed ships form a new fleet or join an existing one; empty merged sources disappear. Ship assignments and paid per-ship travel budgets remain intact.
- `RescueBalanceAuditTest` generates [RescueBalanceReport.md](../RescueBalanceReport.md) from validated catalog designs and bounded power, motion, cargo and passenger ticks. It compares intercept time, fuel use, outage and nutrition survival, outcome and onward recovery readiness. Long accepted approaches remain analytic plan-only cases.
- `RescueFleetCommand` validates a same-owner sublight intercept and requested cargo or tank donation against live reserves. It stages physical departure with reserved electrical endurance and reuses the tick-owned atomic transfer at contact. Failed live validation leaves state unchanged.
- `ShipEnduranceAuditTest` builds validated catalog ships and compares departure readiness, journey fuel use and arrival battery charge against bounded engine ticks. It writes a deterministic Markdown balance report covering chemical mixtures, stellar illumination, atmospheric transfers, passenger loads, crossing endurance and emergency resupply recovery. Real 1-light-year crossings remain analytic plan-only cases. See [the generated balance report](../ShipEnduranceReport.md) for reproduction and provisional tuning candidates.
- `TransferShipSuppliesCommand`: Revalidates donor stock, shared site, stationary status, ownership, fuel compatibility and receiver capacity at execution. Transfers preserve outage history and travel commitments; recovery still requires its own readiness check.
- `RecoverFleetTravelCommand`: Validates a replacement sublight itinerary from actual drift motion against remaining propellant, reactor fuel and electrical supply including arrival reserves. It also replans physical local drift or resumes older prepaid local journeys or researched warp itineraries after checking the remaining electrical budget and any queued crossing. Physical sublight replanning commits new propulsion reactor fuel; older local and warp resumption preserves consumed resources and existing commitments. Stance changes and nationalization preserve drift and recovery motion.
- `MoveFleetCommand.preview`: Returns immutable departure details including local maneuver fuel, crossing time, braking fuel, any launch charge and electrical readiness. An electrically invalid departure retains its diagnostic preview and cannot execute. Local movement commands also check electrical supply and the essential-load arrival reserve.
- `ResupplyShipPowerCommand`, `ChargeShipBatteryCommand` and `SetShipSolarArraysCommand`: Stage separate electrical fuel purchases, paid charging from real local grid storage and array changes during space travel without stopping. Atmospheric transfers reject deployment. Stowing an array can reduce available power and interrupt travel on the next tick. Passenger transit mode changes are restricted to idle fleets.
- `AddStationModuleCommand`: Solar-array module construction requires electricity, solar power and space station research. Its captured output is a reference rating at Sol and 1 AU; station processing applies live stellar illumination.
- Player blueprint registration, editing and ship construction require nuclear fission research for an equipped fission reactor. Solar arrays require electricity and solar power research. Chemical auxiliary generators allow early rocket designs without nuclear research. Early rocket lifecycle tests buy electrical supplies separately and cover all three propulsion mixtures and resumed sublight travel after save/load.
- `Controller`: Interface for observing game state updates.
- `HumanController`: Player controller for receiving game state updates and staging interactive commands.
- `CommandQueue`: Thread-safe staging queue validating and executing game commands during turn transitions. Optional command receipts report execution, rejection or cancellation; an aborted batch completes receipts exceptionally. Receipts are transient and are cancelled when queued submissions are cleared. It reports actual treasury changes to the engine for imperial daily accounting.
- `GameCommand`: Interface for validated state mutation commands.
- `SetTariffRateCommand`: Command adjusting transaction tariff rates at commercial hubs.
- `SubsidizeCorporationCommand`: Command transferring state treasury credits to subsidize corporations.
- `AssignGovernorCommand`: Command appointing system governors to solar systems.
- `AppointMinisterCommand`: Command assigning ministers to imperial cabinet portfolios.
- `SetDiplomaticTierCommand`: Command establishing or updating bilateral diplomatic relation tiers.
- `NationalizeAssetCommand`: Command allowing sovereign empires to nationalize corporate assets.
- `CorporateInvestCommand`: Command for corporations to invest capital in infrastructure or ships.
- `StartResearchCommand`: Command initiating or reassigning scientific research projects.
- Research targets must exist in the catalog with the correct foundational or application type and meet their prerequisites. Scientist allocations use the owner's recorded system headcounts.
- `SelectOptimizationPathCommand`: Command selecting performance or miniaturization optimization paths.
- `ResolveApplicationResearchCommand`: Command accepting an application research improvement or discarding its prototype.
- `ReverseEngineerSalvageCommand`: Command injecting progress vectors from foreign component salvage.
- `DesignShipCommand` and `UpdateShipDesignCommand`: Rebuild player blueprints from component choices against live research and manufacturing capacity. Submitted physical stats and manufacturing metadata are ignored. Designs referenced by ships or active orders cannot be edited.
- `QueueShipBuildCommand`: Command queuing spacecraft construction at a compatible yard using its modules, actual paid staffing and technology-adjusted work capacity.
- `SetSurfaceShipyardStaffingCommand`: Command setting an owned surface shipyard's persistent worker allocation within the remaining local workforce.
- `LoadOrbitalCargoCommand`: Command buying material at a body's hub and lifting it into an owned cargo or construction ship after capacity and cost checks.
- `MoveFleetCommand`: Command paying a surface launch provider when needed, committing local maneuver fuel and queuing a deep-space departure followed by researched warp or thrust-and-loaded-mass-based sublight travel.
- `MoveFleetLocalCommand`: Command validating a maneuver fuel budget and queuing a timed movement to a body's surface or orbit, a station dock or a named deep-space site.
- `SetFleetStanceCommand`: Command setting fleet tactical stances.
- `BuildFacilityCommand`: Command constructing planetary industrial facilities.
- `ExpandFacilityCommand`: Command queuing facility tier scaling upgrades.
- `StartProspectingMissionCommand`: Command initiating stochastic geological prospecting surveys.
- `BombardPlanetCommand`: Command executing orbital bombardment using kinetic darts, nuclear fission or planet-crackers.
- `InvadePlanetCommand`: Command launching planetary ground sieges and surface invasions.
- `LoadTroopsCommand`: Command boarding available local soldier workers onto an owned troop transport for an enemy planet during total war.
- `ColonizePlanetCommand`: Command deploying colony ships to seed virgin worlds.
- `EnactMartialLawCommand`: Command enacting emergency planetary martial law.
- `SetPassengerTransitModeCommand`: Command toggling conscious vs cryogenic stasis passenger transport.
- `LoadPassengersCommand`: Books real residents on a surface ship for an explicit offworld destination and stores their age groups in a manifest until arrival.
- `LaunchMassDriverPayloadCommand`: Purchases local goods and launches them into an owned orbital transport through a built mass-driver facility.
- `LoadOrbitalCargoCommand`: Purchases ground stock for a ship in orbit and pays an available rocket, mass-driver or elevator launch provider.
- `LoadSurfaceCargoCommand`: Purchases local hub stock into a ship already on the body's surface without an orbital lift fee.
- `ScanAnomalyCommand`: Command directing science fleets to investigate deep-space anomalies.
- `ProposeDiplomaticPactCommand`: Command dispatching bilateral treaty proposals to foreign states.
- `ResolveDiplomaticProposalCommand`: Command accepting or rejecting a pending proposal on behalf of its receiver.
- `WithdrawDiplomaticProposalCommand`: Command allowing a proposal's sender to withdraw it before it is resolved or expires.
- `DeclareWarCommand`: Command formally declaring war with casus belli justification tracking.
- `EndWarCommand`: Command ending an active bilateral war and restoring neutral relations.
- `BuildOrbitalStationCommand`: Command deploying new orbital space stations with initial power and control modules.
- `BuildSpaceElevatorCommand`: Command constructing planetary space elevator tethers to reduce launch costs to near zero.
- `AddStationModuleCommand`: Command queuing material-backed module assembly for orbital stations.
- `DeployConstructionShipCommand`: Command ordering construction vessels to assemble macro-structures at target coordinates.
- `InfiltrateAgentCommand`: Command embedding sleeper operatives in foreign colonies and corporations.
- `LaunchCovertOperationCommand`: Command initiating covert sabotage, technology theft and false-flag operations.
- `ExtortSupplyLineCommand`: Command directing syndicate pirate bases to extort private corporate supply lines.
- `SetFacilityRecipeCommand`: Command configuring the active chemical or metallurgical refinement recipe of a facility.
- `DistributeConsumerGoodsCommand`: Command supplying consumer goods to colonies to raise living standards and lower crime.
- `StartTerraformingProjectCommand`: Command funding and queuing material-backed planetary geoengineering or biological seeding.
- `BuildMegastructureCommand`: Command initiating construction of Dyson swarms, stellar lifters, ringworlds and hyperlane gateways.
- `ProposeResolutionCommand`: Command introducing legislative charters, treaties and economic sanctions to the Galactic Senate.
- `VoteResolutionCommand`: Command casting democratic votes on active Galactic Senate resolutions.
- `CreateTradeRouteCommand`: Command establishing an automated cargo route between real hubs with an available owned freighter.
- `CancelTradeRouteCommand`: Command deactivating an automated cargo supply route.
- `SetSystemEconomyBudgetCommand`: Command configuring nonnegative public sector shares, total funding, income tax and the signed empire contribution rate for a controlled solar system.
- `SetPublicIndustrySubsidyCommand`: Command letting an empire opt an owned state facility into or out of local infrastructure and industry support.
- `ScanSystemCommand`: Command directing sensor arrays or explorer fleets to deep-scan an uncharted solar system.
- `PlaceFacilityOnTileCommand`: Command constructing an industrial processing facility positioned directly on a surface biome tile.
- `TargetSubsystemCommand`: Command setting the tactical subsystem target priority (warp drive, weapons, shields, engines) during fleet battles.
- `CreateCustomEmpireCommand`: Command instantiating and registering a custom sovereign empire and species archetype into the game state.
- `EmpireAIController`: Autonomous decision agent managing imperial cabinets, governors, corporate subsidies, diplomacy, senate votes, research and initial fleet service.
- `CorporateInvestCommand`: Uses the engine investment processor to validate and create a real corporation-owned asset on a controlled body.
- `ShadowSyndicateAIController`: Autonomous decision agent monitoring shadow capital pools and pirate operations.

## Current integration gaps

- `CommandQueue` stages commands for the next turn, but command validation is uneven. Ship build validation rejects corporation-owned blueprints and free corporate builds. Ship design registration rejects duplicate IDs and corporation-owned submissions through the player command. Registration and edits calculate physics from recognized components and catalog materials using live state. Registration and construction gate recognized propulsion modules on researched technology, including MPD ion drives on superconductors. `RefuelShipCommand` checks local access, tank capacity, reactor-fuel storage, market stock, owner funds and orbital launch availability before buying propellant and any selected reactor fuel. Controller identity authorization and migration of legacy blueprints remain open.
- Commands that change the world now use `GameState.toBuilder()` or a `with...` method to retain unrelated state fields. Validation and persistence behavior remain uneven across individual commands.
- `SelectOptimizationPathCommand` requires a catalog application, completed application research, its parent technology and explicit prerequisites. Recorded research outcomes permit one matching decision: optimized success enables a path choice while other outcomes use `ResolveApplicationResearchCommand`. Accepted modifiers accumulate and discarded prototypes preserve the prior version. Legacy researched applications retain earlier baseline path switching until a recorded refinement. `StartResearchCommand` allows 500-point application refinements, blocks unresolved outcomes and validates scientist allocations against actual system headcounts. Facility construction, expansions and corporate orders use accumulated costs; new facility projects quote the minimum tier required by the chosen variant. `TargetSubsystemCommand` still does not store its target. `DeclareWarCommand` records its calculated justification and penalties in the game snapshot and save file. While total war remains active, civilian penalties add demographic stress and corporate trust penalties can block corporate investment.
- Autonomous corporate investment runs once inside the engine turn. The duplicate control-layer corporation AI was removed.
- `QueueShipBuildCommand` creates a material-backed work-hour order; ships appear only after it completes. Surface yard staffing allocations are persisted through `SetSurfaceShipyardStaffingCommand`; orbital yard modules hire and pay workers from station population. Both yard types use actual paid workers for construction capacity. Finished corporate-yard purchases described in [ShipDesign.md](../ShipDesign.md) remain open.
- The frontend instantiates empire AI for each empire except the player empire; the shadow syndicate AI logs its decisions without staging corresponding commands.

## AI fleet service

EmpireFleetPlanner stages ordinary commands for an initial trader when a public market replenishment opportunity exists or an initial combat ship during total war. It considers only the empire's own blueprints and compatible owned yards with the full construction material bill available and affordable. Empires do not share or copy blueprints. RequestAIShipBuildCommand rechecks ownership, need and material affordability at execution and permits at most one pending build per empire. Construction still consumes materials and pays through the existing engine processor.

AssignAIShipServiceCommand assigns an idle owned cargo ship at a suitable hub to roaming trade. Military fleets remain idle until an explicit order is given; automatic patrol assignment was removed. The command requires owner-matched blueprints and preserves travel orders, committed cargo routes and non-passive fleet stances. Sensor-dependent patrols are deferred. Broader fleet sizing and finished corporate-yard purchases remain open. EmpireFleetPlannerTest checks blueprint isolation, queued construction, commissioning, duplicate prevention and live service guards without a balance simulation.

## Guard object orders

GuardObjectCommand queues a stationary hold order for an owned station, a body in a controlled system or an owned stationary fleet at the guard's exact current physical site. At least one military hull with an owner-matched blueprint is required. Travel and cargo commitments block assignment. CancelGuardObjectCommand checks ownership before removing the order. Movement or a replacement stance clears it; disappearance, loss of control or target departure clears it during encounter processing. Fleet formation changes also clear guard assignments. Separate-fleet pursuit and sensor-based interception remain open.

The existing encounter resolver gives guards priority over other fleets when selecting same-site total-war encounters. This protects fleets sharing that site without adding detection ranges, free movement or planetary and station damage rules. GuardObjectCommandTest checks queued ownership validation, cancellation, body and fleet targets, encounter priority, daily retention and save compatibility.

## Escort convoys

Engine protection can pause an escorted trader's delivery or return for a funded escape without another player command. TradeEscapeTest verifies actual paid mixed freight, persisted suspension, eventual single settlement, cargo retention after cancellation or port loss and unchanged purchases when escape is unfunded. See [FleetProtection.md](../FleetProtection.md).

EscortFleetCommand joins all ships of an idle military fleet to an owned fleet at the same site through existing formation validation. Each escort hull needs an owner-matched military blueprint. Guard orders, existing escort assignments, rescue commitments and the escort's cargo-route commitments block attachment. The protected fleet retains its ID, name and cargo route; the escort fleet's identity and surviving ship IDs persist in FleetEscortOrder. Only one escort group per convoy is currently supported.

The combined convoy uses ordinary movement and combat. Its slowest member sets travel pace and each member needs actual fuel and electrical readiness. CancelEscortFleetCommand restores surviving escorts as their original fleet using the ordinary split rules, retaining physical motion and paid budgets even during travel. The protected fleet's prior stance is restored unless it has been explicitly changed. No surviving escorts or no surviving protected ships ends the recorded assignment. EscortFleetCommandTest covers queue validation, cargo route preservation, shared travel, fuel rejection, paid midflight detachment, losses and save compatibility.

## Chemical freighter construction

QueueShipBuildCommand rechecks conditioned-tank and compact-hold research and compatible mixture volume. Designs containing the conditioned tank require an owned operational orbital yard with an online slipway, adequate manufacturing complexity and a commercial hub; ground fallback is blocked. ChemicalFreighterLifecycleTest covers paid fueling, current access, outages and save/load. See [ChemicalFreighterDraft.md](../ChemicalFreighterDraft.md).

## Orbital travel commands

MoveFleetLocalCommand preserves an explicit planetary parking altitude and uses the authoritative LocalTravel planner. BuildOrbitalStationCommand validates the selected altitude and persists it through construction. CancelOrbitalTravelCommand leaves a waiting fleet at its real base, retains captured parking altitude or cancels future maneuvers while physical coast continues. Paid fuel and grid charging remain available at an orbital order's actual waiting base. See [OrbitalTransfers.md](../OrbitalTransfers.md).

The same local move command also validates planet-centered parking transfers and paid same-altitude approaches through LocalTravel. Cancelling after funded circularization retains the actual orbit for a new separately funded docking command. See [ParkingOrbitTransfers.md](../ParkingOrbitTransfers.md).

Lunar endpoints use the same authoritative local command path. Station construction validates the moon's own sphere of influence and preserves the requested altitude. Captured cancellation retains lunar parking while missed capture keeps the parent-centered trajectory. See [LunarTransfers.md](../LunarTransfers.md).
