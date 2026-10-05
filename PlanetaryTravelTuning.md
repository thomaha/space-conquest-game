# Planetary transfer tuning (Draft)

## Status and recommendation

Draft: The first circular coplanar orbital prototype is implemented for configured planetary parking sites. Its equipment and balance targets remain provisional. Existing local routes retain their compatibility model. Chemical propulsion supplies early main thrust and solar arrays supply auxiliary electricity; drive ratings have not been increased to force shorter planetary trips.

The ship designer now offers solar arrays with a chemical auxiliary generator or a researched fission reactor. Batteries and the appropriate electrical tank are included once. These choices use the same catalog construction path as the measured hybrid probes. Blueprint previews and tick commands still enforce research, hull slots and material requirements. Editing restores the whole selected electrical combination.

## What the measurements establish

The [local distance audit](LocalTradeDistanceReport.md) uses a frozen Earth-to-Mars station separation of 119,975,168 km. The loaded chemical solar ship takes 2,609 days. Its [fully stocked hybrid counterpart](AuxiliaryPowerReport.md) takes 3,848 days. Backup mass extends the journey even though neither ship burns generator feed on that route.

LocalSpaceTravel starts with zero velocity along the route and finishes at zero velocity. The carried propellant funds both burns. Planetary orbital velocity, moving arrival sites and gravitational motion are absent. Increasing thrust mainly shortens burns after a long voyage becomes limited by total available velocity change; it does not provide the missing cruise velocity.

For the current stationary endpoint model, peak speed is at least distance divided by elapsed time. The total required velocity change is at least twice that peak. Using the catalog RP-1 drive's 3,400 m/s exhaust velocity gives these optimistic lower bounds:

| Illustrative duration | Peak speed floor | Total velocity change floor | Consumed fraction of initial wet mass floor |
|---|---:|---:|---:|
| 180 days | 7,714 m/s | 15,429 m/s | 98.93% |
| 365 days | 3,804 m/s | 7,609 m/s | 89.33% |

These are mathematical bounds for hypothetical durations, not proposed accepted targets. Calculation: `v >= distance / seconds`, `deltaV >= 2 * v` and `consumedFraction >= 1 - exp(-deltaV / 3400)`. Finite acceleration makes the bounds stricter. Dry structure, cargo, electrical equipment and protected reserve must fit in the remaining wet mass. Changing the exhaust rating simply to reach these durations would change chemical propulsion's capability throughout the game.

## Proposed initial orbital model

- Limit the first implementation to transfers between known planetary orbital envelopes around the same star. Keep same-body docking, surface service phases and unsupported itineraries on their explicitly identified existing paths. Moon transfers need a later parent-relative gravity treatment.
- Derive deterministic circular body motion from saved simulation time, catalog orbital radius and stellar mass. Preserve a stable initial phase per body ID. Positions and velocities must be reproduced after save/load and independent of body-list ordering. This is a game approximation rather than a real ephemeris.
- Start with a bounded two-body coast solution and explicit departure and arrival velocity changes. Evaluate a fixed candidate set of launch times and transfers, then reject unsupported geometry or fuel budgets. Do not grant a ship a straight-line boost by adding orbital speed to its existing peak-speed allowance.
- Match the destination's predicted position and velocity at arrival. Reaching a location alone does not establish a safe docking. Departure from a planetary parking orbit and capture into the destination parking orbit require explicit gravity-well budgets; a star-centered transfer alone cannot claim base-to-base affordability.
- Keep rocket-equation mass accounting, drive-compatible feed and actual acceleration limits. An impulsive maneuver approximation requires an explicit finite-burn validity criterion. Low-thrust electric drives need a separate supported planner or a clear unsupported result.
- Compare supported candidates by funded arrival time, consumed fuel and owner policy. The earliest arrival day can still conserve maneuver fuel within its scheduling allowance. Return planning remains optional. Surplus carried fuel remains permissible insurance rather than a mandate to buy an exact minimum.

## Orders, power and failures

- Waiting for a departure opportunity is an explicit phase. Crew supplies, cargo preservation and auxiliary power continue to consume real stores while waiting. A base may sell local compatible supplies through normal paid transactions; waiting does not create free fuel or charging.
- Forecast auxiliary electricity throughout waiting, departure, coast and capture. Use conservative distance-dependent stellar flux and destination eclipses until exact shadow geometry is supported. A chemical burn's electrical demand remains auxiliary demand.
- Freeze an accepted transfer's solution and its initial epoch in immutable saved journey state. Movement, power, supplies, recovery and previews must consume the same phase timing and remaining budgets. Existing saved straight-line journeys retain their own model.
- Fuel exhaustion stops commanded thrust immediately. Gravity may change ballistic velocity without engine fuel under the orbital model, but no capture, braking or arrival is credited without a funded maneuver. Preserve the actual position and velocity for recovery and rescue.
- Fleet members share a trajectory limited by their supported burn acceleration and budgets. Split, merge, combat and recovery copies must preserve the new state. A fleet cannot erase spent fuel or change its orbital velocity by reorganizing.

## Implementation sequence and acceptance

1. Add a read-only orbital transfer comparison using repository stellar and planetary data. Report candidate phase, wait, coast time, departure and capture budgets and rejection reason alongside the current straight-line result. Establish whether current catalog ships can fund any candidate before revising equipment.
2. Define the parking-orbit, finite-burn and supported-geometry rules. Compare several deterministic phase arrangements rather than one convenient launch position. Set desired early-game journey durations after reviewing these results.
3. Implemented with user permission: Immutable saved transfer state and coordinated movement, power, supply, recovery checks and fleet copy handling. Ballistic recovery remains explicitly unsupported.
4. Verify conservation, missed arrival and unpowered motion, save/load timing, fleet reorganization, paid resupply, cargo preservation and forecast/tick agreement. Re-run loaded Moon, Mars and Jupiter probes and distinguish unsupported routes from funded ones.
5. Tune component mass, cargo sizes, main tank capacities and reserve horizons using these measurements. Keep protected contingency fuel and powered capture requirements throughout. Larger fuel capacity or staged propulsion needs its own explicit catalog and construction rules.

The read-only comparison in step 1 is now implemented by PlanetaryTransferComparison and exercised by 72 catalog probes. See [PlanetaryTransferReport.md](PlanetaryTransferReport.md). All current blueprints fail at least one maneuver check at the provisional 500 km parking altitude: chemical and fission thermal ships lack main propellant while MPD cannot perform impulsive burns. The parking and tank extension is now measured in [ParkingOrbitTankReport.md](ParkingOrbitTankReport.md). It validates 160 catalog configurations and finds six maneuver-feasible two-tank fission Mars cases. Chemical ships still need a better supported fuel fraction. The next decision is defining smaller freighter or larger-tank hull equipment before promising chemical planetary service, alongside an operational orbital prototype for the viable fission configuration. No drive coefficient, elapsed-day unit or fuel capacity is changed.

## Chemical equipment study

The selected small-cargo direction is now implemented with provisional catalog, thermal and orbital-construction rules in [ChemicalFreighterDraft.md](ChemicalFreighterDraft.md). Its 2,500 kg hold and 120,000 kg tank family supports the verified high-orbit chemical Mars voyage without modifying current drives or frame data. Actual parking altitudes and saved orbital journeys persist in save version 41.

## Operational prototype

[OrbitalTransfers.md](OrbitalTransfers.md) records the implemented impulse prototype with saved parking state, launch windows, phase execution and shared power and fuel accounting. CircularOrbitalEphemeris and HohmannCoast provide its motion foundation. Actual high-orbit chemical and reactor-powered thermal voyages agree with forecasts. Configured endpoints activate orbital planning and older endpoints retain explicit compatibility behavior. Recovery outside supported parking sites and detailed planetary encounter geometry remain future work.
