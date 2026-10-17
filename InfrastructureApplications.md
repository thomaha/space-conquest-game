# Infrastructure applications

Launch services now read capabilities from researchable applications in technologies.json. They remain services rather than bulk material recipes.

| Facility or asset | Owning application | Data consumed |
|---|---|---|
| Cargo terminal | cargo_terminal under rocketry | Construction bill and work, profession, payload capacity, supported traffic, launch vehicle mass, propulsion reference and fuel inventory target |
| Mass driver | mass_driver_launch_tracks | Construction bill and work, profession, payload capacity, supported traffic, electricity requirement and service fee coefficients |
| Space elevator | space_elevators | Construction bill and work rate, maximum commissioned capacity, electricity per payload, service fee and captured lift discount |
| Surface shipyard | space_stations | Construction bill and work, surface capability, ship construction capability, workforce, throughput and manufacturing complexity |
| Orbital yards | Their existing yard applications | Ship construction capability, workforce, throughput, physical installation costs and manufacturing complexity |

Facility type keys are data values. Launch selection no longer branches on cargo-terminal or mass-driver names. Yard consumers discover surface facilities from the surface capability. Orbital construction work uses the declared ship construction capability rather than a list of named yard modules. The remaining public surface-yard constant identifies legacy scenario/test data and does not select runtime behavior.

New facility construction and infrastructure expansion require the owning application and its research prerequisites. Elevator construction follows the same rule for direct orders and construction-ship deployment. Unknown facilities receive no invented construction bill. New surface yards use the construction work already declared on their yard application.

The construction selector lists service facility types and surface yards from application data. AI yard discovery and orbital expansion candidates use the same yard capabilities and research checks. Freight users share the ordinary LaunchService selection path. Public AI can now build a missing ship-launch provider for an existing carrier and a funded remote buyer. This remains a local investment decision rather than terminal or elevator network planning. See [AI supply-chain investment](AISupplyChains.md).

Launch quotes retain immutable material quantities for their selected propellant mixture. Settlement rechecks actual fuel stock, provider ownership, power and remaining daily payload capacity before spending funds. Existing buyer payments, provider receipts, metered electricity and corporate tax settlement remain in place. Saved launch usage and activity prevent a reload from restoring spent capacity.

## Paid work and service prices

Each launch application defines work units per kilogram and a ProductionEquipment specification: machinery work rate, reference crew, profession, operator skill, autonomy and parallel lines. Installed facilities capture that equipment. Shared execution combines paid operators with skill and automation, then limits work by the declared physical payload capacity. Partial crews reduce capacity. Automation supplies its declared fraction without requiring human payroll but still consumes the application's launch resources.

Launches precede daily payroll in the simulation. The most recent settled crew receipt therefore supports the following service period. Allocating workers does not create a paid receipt. Payroll charges actual employer cash and caps service jobs at installed crew capacity. Daily launch usage consumes the supported work and remains saved with the paid crew and equipment. Failed payroll removes human capacity from the next period; unused human work does not accumulate between periods.

New elevators commission an ordinary service facility with the same asset ID. Its operating account, captured equipment and crew use ordinary household payroll, maintenance, tax and replacement settlement. Public opening working capital transfers from the owner's available treasury under application reserve rules. The tether's commissioned throughput remains an independent physical limit; expanding its companion facility cannot upgrade the tether.

Service fees allocate payroll, maintenance and capital replacement over supported payload capacity, add electricity costs and use the shared after-tax margin policy. Application handling fees remain a floor. Supplier payments, payroll, maintenance, profit tax and replacement transfers are real cash movements; reserves are not expenses. These quotes assume enough use of the quoted capacity to cover its fixed costs. Low utilization can still lose money. Profitability was not balanced.

The industry view renders paid crew, allocation, skill, automation, available work and quoted costs from snapshots. Public staffing changes go through SetLaunchServiceStaffingCommand. Increasing allocation cannot invent paid capacity before payroll.

Rocket lift now uses the selected drive's exhaust velocity. Previously the lift calculator used its generic default even when the terminal selected chemical propulsion. The correction changes fuel requirements and invalidates economics based on that old default. Cargo-loading fixtures now provide sufficient actual propellant. No profitability targets or production yields were balanced in this pass.

## Verification and limits

The initial infrastructure migration passed 126 tests across 23 suites. The staffing pass passed 114 tests across 19 suites. Its verification covers actual payroll, partial and unfunded crews, skill, automation, maintenance payments, corporate tax, replacement funding, queued UI controls and current-format reload. The complete repository suite was not run.

InfrastructureApplicationLifecycleTest covers paid application research, discovery gates, material-funded construction, launch settlement and current-format disk reload. It also checks elevator capacity bounds, missing construction definitions and rejection of reused driver capacity or unavailable power.

LoadOrbitalCargoCommandTest verifies actual purchased material entering a receiving ship and then being consumed by orbital construction. Its paid delivery now crosses a disk save/load boundary. LaunchServiceAccountingTest covers provider receipts, electricity payments and corporate tax. Existing shared-yard, yard investment and manufacturing lifecycle tests exercise the other service consumers.

A return-planning regression was fixed in LogisticsProcessor. Driven ships without an electrical profile previously skipped refuelling before the return-safety gate. Preflight now funds and checks ships with propulsion as well. All five LogisticsProcessorTest cases pass, including the previously failing paid-propellant route. The affected surface freight delivery case also passes with enough actual chemical propellant.

Planning complete new service networks, detailed machinery wear and facility construction labor remain incomplete. Construction still uses provisional work allowances. These applications therefore remain marked partial.

Shared trajectory and settlement calculations remain engine rules. The named opening scenario lineup, baseline propulsion definitions, other station-module behavior and unrelated megastructure construction tables remain separate migration work. Historical business and offworld reports have not been regenerated for the corrected rocket exhaust calculation.
