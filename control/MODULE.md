# Control module

This module handles input, autonomous decision agents and game command execution pipelines for players and AI controllers.

## Key components
- `Controller`: Interface for observing game state updates.
- `HumanController`: Player controller for receiving game state updates and staging interactive commands.
- `CommandQueue`: Thread-safe staging queue validating and executing game commands during turn transitions. It reports actual treasury changes to the engine for imperial daily accounting.
- `GameCommand`: Interface for validated state mutation commands.
- `SetTariffRateCommand`: Command adjusting transaction tariff rates at commercial hubs.
- `SubsidizeCorporationCommand`: Command transferring state treasury credits to subsidize corporations.
- `AssignGovernorCommand`: Command appointing system governors to solar systems.
- `AppointMinisterCommand`: Command assigning ministers to imperial cabinet portfolios.
- `SetDiplomaticTierCommand`: Command establishing or updating bilateral diplomatic relation tiers.
- `NationalizeAssetCommand`: Command allowing sovereign empires to nationalize corporate assets.
- `CorporateInvestCommand`: Command for corporations to invest capital in infrastructure or ships.
- `StartResearchCommand`: Command initiating or reassigning scientific research projects.
- `SelectOptimizationPathCommand`: Command selecting performance or miniaturization optimization paths.
- `ReverseEngineerSalvageCommand`: Command injecting progress vectors from foreign component salvage.
- `DesignShipCommand`: Command registering physics-validated spaceship blueprints.
- `QueueShipBuildCommand`: Command manufacturing and commissioning spacecraft into active fleets.
- `MoveFleetCommand`: Command ordering sub-light repositioning or FTL warp transits.
- `SetFleetStanceCommand`: Command setting fleet tactical stances.
- `BuildFacilityCommand`: Command constructing planetary industrial facilities.
- `ExpandFacilityCommand`: Command queuing facility tier scaling upgrades.
- `StartProspectingMissionCommand`: Command initiating stochastic geological prospecting surveys.
- `BombardPlanetCommand`: Command executing orbital bombardment using kinetic darts, nuclear fission or planet-crackers.
- `InvadePlanetCommand`: Command launching planetary ground sieges and surface invasions.
- `ColonizePlanetCommand`: Command deploying colony ships to seed virgin worlds.
- `EnactMartialLawCommand`: Command enacting emergency planetary martial law.
- `SetPassengerTransitModeCommand`: Command toggling conscious vs cryogenic stasis passenger transport.
- `LoadPassengersCommand`: Command embarking passenger and troop cohorts onto transport spacecraft.
- `LaunchMassDriverPayloadCommand`: Command launching mineral freight from surface mass drivers into orbit.
- `ScanAnomalyCommand`: Command directing science fleets to investigate deep-space anomalies.
- `ProposeDiplomaticPactCommand`: Command dispatching bilateral treaty proposals to foreign states.
- `DeclareWarCommand`: Command formally declaring war with casus belli justification tracking.
- `BuildOrbitalStationCommand`: Command deploying new orbital space stations with initial power and control modules.
- `BuildSpaceElevatorCommand`: Command constructing planetary space elevator tethers to reduce launch costs to near zero.
- `AddStationModuleCommand`: Command installing specialized functional modules onto orbital stations.
- `DeployConstructionShipCommand`: Command ordering construction vessels to assemble macro-structures at target coordinates.
- `InfiltrateAgentCommand`: Command embedding sleeper operatives in foreign colonies and corporations.
- `LaunchCovertOperationCommand`: Command initiating covert sabotage, technology theft and false-flag operations.
- `ExtortSupplyLineCommand`: Command directing syndicate pirate bases to extort private corporate supply lines.
- `SetFacilityRecipeCommand`: Command configuring the active chemical or metallurgical refinement recipe of a facility.
- `DistributeConsumerGoodsCommand`: Command supplying consumer goods to colonies to raise living standards and lower crime.
- `StartTerraformingProjectCommand`: Command funding and initiating planetary atmospheric geoengineering and biological seeding.
- `BuildMegastructureCommand`: Command initiating construction of Dyson swarms, stellar lifters, ringworlds and hyperlane gateways.
- `ProposeResolutionCommand`: Command introducing legislative charters, treaties and economic sanctions to the Galactic Senate.
- `VoteResolutionCommand`: Command casting democratic votes on active Galactic Senate resolutions.
- `CreateTradeRouteCommand`: Command establishing automated cargo logistics supply routes between commercial hubs.
- `CancelTradeRouteCommand`: Command deactivating an automated cargo supply route.
- `SetSystemEconomyBudgetCommand`: Command configuring nonnegative public sector shares, total funding, income tax and the signed empire contribution rate for a controlled solar system.
- `ScanSystemCommand`: Command directing sensor arrays or explorer fleets to deep-scan an uncharted solar system.
- `PlaceFacilityOnTileCommand`: Command constructing an industrial processing facility positioned directly on a surface biome tile.
- `TargetSubsystemCommand`: Command setting the tactical subsystem target priority (warp drive, weapons, shields, engines) during fleet battles.
- `CreateCustomEmpireCommand`: Command instantiating and registering a custom sovereign empire and species archetype into the game state.
- `EmpireAIController`: Autonomous decision agent managing imperial cabinets, governors, corporate subsidies, diplomacy, senate votes and research.
- `CorporationAIController`: Autonomous decision agent evaluating market shortcomings, investing in facilities and generating proprietary ship blueprints.
- `ShadowSyndicateAIController`: Autonomous decision agent monitoring shadow capital pools and pirate operations.

## Current integration gaps

- `CommandQueue` stages commands for the next turn, but command validation is uneven. For example, ship build validation checks that a design exists without checking its owner and ship design registration does not protect another owner's design ID.
- Commands that change the world now use `GameState.toBuilder()` or a `with...` method to retain unrelated state fields. Validation and persistence behavior remain uneven across individual commands.
- Some commands compute a result without persisting it: `SelectOptimizationPathCommand` returns the original state, `TargetSubsystemCommand` does not store the target and `DeclareWarCommand` discards its calculated diplomatic impact.
- `CorporationAIController` can create a corporation-owned proprietary cargo design, but the build command does not enforce owner-only construction. It currently attempts a design only when no cargo design exists anywhere, rather than evaluating each corporation's own needs and designs.
- `QueueShipBuildCommand` immediately commissions a ship. The intended construction order, work-hour progress and shipyard queue described in [ShipDesign.md](../ShipDesign.md) are not implemented.
- The frontend currently instantiates empire and corporation AI for hard-coded IDs; the shadow syndicate AI logs its decisions without staging corresponding commands.
