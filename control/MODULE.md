# Control module

This module handles input, autonomous decision agents and game command execution pipelines for players and AI controllers.

## Key components
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
- `EmpireAIController`: Autonomous decision agent managing imperial cabinets, governors, corporate subsidies, diplomacy, senate votes and research.
- `CorporateInvestCommand`: Uses the engine investment processor to validate and create a real corporation-owned asset on a controlled body.
- `ShadowSyndicateAIController`: Autonomous decision agent monitoring shadow capital pools and pirate operations.

## Current integration gaps

- `CommandQueue` stages commands for the next turn, but command validation is uneven. Ship build validation rejects corporation-owned blueprints and free corporate builds. Ship design registration rejects duplicate IDs and corporation-owned submissions through the player command. Registration and edits calculate physics from recognized components and catalog materials using live state. Registration and construction gate recognized propulsion modules on researched technology, including MPD ion drives on superconductors. `RefuelShipCommand` checks local access, tank capacity, reactor-fuel storage, market stock, owner funds and orbital launch availability before buying propellant and any selected reactor fuel. Controller identity authorization and migration of legacy blueprints remain open.
- Commands that change the world now use `GameState.toBuilder()` or a `with...` method to retain unrelated state fields. Validation and persistence behavior remain uneven across individual commands.
- `SelectOptimizationPathCommand` requires a catalog application, completed application research, its parent technology and explicit prerequisites. Recorded research outcomes permit one matching decision: optimized success enables a path choice while other outcomes use `ResolveApplicationResearchCommand`. Accepted modifiers accumulate and discarded prototypes preserve the prior version. Legacy researched applications retain earlier baseline path switching until a recorded refinement. `StartResearchCommand` allows 500-point application refinements, blocks unresolved outcomes and validates scientist allocations against actual system headcounts. Facility construction, expansions and corporate orders use accumulated costs; new facility projects quote the minimum tier required by the chosen variant. `TargetSubsystemCommand` still does not store its target. `DeclareWarCommand` records its calculated justification and penalties in the game snapshot and save file. While total war remains active, civilian penalties add demographic stress and corporate trust penalties can block corporate investment.
- Autonomous corporate investment runs once inside the engine turn. The duplicate control-layer corporation AI was removed.
- `QueueShipBuildCommand` creates a material-backed work-hour order; ships appear only after it completes. Surface yard staffing allocations are persisted through `SetSurfaceShipyardStaffingCommand`; orbital yard modules hire and pay workers from station population. Both yard types use actual paid workers for construction capacity. Finished corporate-yard purchases described in [ShipDesign.md](../ShipDesign.md) remain open.
- The frontend currently instantiates empire AI for a hard-coded ID; the shadow syndicate AI logs its decisions without staging corresponding commands.
