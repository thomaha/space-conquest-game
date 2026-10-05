# Local space travel (Draft)

## Status and scope

Draft: The initial implementation now uses these physical rules for recognized main drives between known space sites. Coefficients and representative geometry remain subject to tuning. Surface transfers, unrecognized legacy propulsion and older prepaid local itineraries retain the abstract maneuver model. Isolated engine fixtures without any system geometry also retain that compatibility path; a known system with an unknown physical site is rejected.

The first physical implementation covers travel between orbital sites, docked stations and system-space rendezvous points within one star system. Surface launch and landing remain separate phases using existing provider rules. Warp and interstellar trajectories retain their existing models.

## Provisional system geometry

- Use meters for trajectory calculations. Planet distances and moon distances in source data are kilometers; planet distances are measured from the star and moon distances from their parent planet.
- The data has orbital radii and planetary inclination but no saved orbital phase or ephemeris. Assign a deterministic circular-orbit phase from each stable body ID, with inclination interpreted in degrees. This is a representative geometry rather than a claim about actual orbital positions or launch windows. Adding or reordering another body does not move an existing one.
- Place moon centers relative to their parent planet, using the moon's own orbital radius. A moon's planet-relative radius must never be interpreted as its distance from the star.
- A body's nominal orbital site is 500 km above its surface. Stations with a known parent body use that body's orbital envelope and a station-specific phase. Stations without a parent use an explicit provisional system-space rendezvous envelope.
- The generic system-space rendezvous is outside the outermost known planetary orbit, with a 1 AU minimum stellar radius and a further 0.1 AU departure offset. Named body rendezvous sites use that body's orbital location. Named star rendezvous sites use one stellar diameter rather than the star's center. Unknown or invalid sites reject physical planning.
- Straight-line endpoint distance sets the first trajectory length. Geometry is frozen when an order departs and saved with that order. Orbit motion, gravity wells, collisions, obstacles, lateral navigation and departure windows require later rules. Zero-length travel between distinct facilities uses a provisional 1 km docking separation.

## Fleet trajectory and reserves

- A fleet shares one accelerate, coast and brake trajectory. Its acceleration is limited by the lowest available thrust divided by initial wet mass among its members. Wet mass includes main propellant, electrical fuel, bulk supplies, cargo and passengers. Use conservative fixed acceleration during each planned itinerary; evolving-mass thrust is a later refinement.
- Every member needs a recognized main drive. MPD thrust depends on captured efficiency and available electricity. Batteries may bridge eclipses but do not count as an unlimited sustained power source. Unrecognized legacy designs retain their identified compatibility mode rather than receiving invented physical capabilities.
- Start a new stationary local trajectory with zero relative velocity and finish with zero relative velocity at the destination. Initial site orbital velocities are not yet modeled. Keep the existing sublight speed ceiling as a shared upper bound.
- Limit peak speed by the smallest supported total velocity change divided by two among the members. Each ship's supported velocity change follows its effective exhaust velocity and physically usable carried propellant. Nuclear drive feed must also cover that propellant. If the supported peak is below the distance-limited peak, coast for the remaining distance.
- For distance `d`, acceleration `a` and peak speed `v`, the fastest funded stationary trajectory uses `v = min(speedCeiling, fleetDeltaV / 2, sqrt(a * d))`. Each burn lasts `v / a`; coasting lasts `max(0, (d - v^2 / a) / v)`. A one-kilometer docking separation does not impose a full day of thrust.
- New stationary departures reduce peak speed to conserve fuel while preserving the fastest funded plan's scheduled arrival day. With deadline `T` just before that day's end, use the smaller root `v = 2 * d / (T + sqrt(T^2 - 4 * d / a))`. Keep the fastest peak when there is no usable slack or the reduced plan would change the scheduled day. This policy minimizes burn impulse within the current fixed-acceleration model and adds no passenger or hotel-service ticks. It is provisional; recovery continues to use the fastest funded trajectory from actual motion.
- Every ship's planned main propellant covers both burns: `initialWetMass * (1 - exp(-2 * v / effectiveExhaust))`. Freeze selected propulsion and its effective exhaust for the journey. Commit required drive reactor feed once and consume main propellant during actual powered phases.
- An explicitly queued sublight crossing provisionally retains half the main tank during its local departure. Automated next-port travel retains two thirds during departure and at least half the remaining fuel for the destination approach. These allocations cap local peak speed and extend coasting; the complete physical and electrical preview must still pass. They are tuning rules and do not require a return itinerary.
- No free acceleration or powered braking is permitted after propellant or drive electricity runs out. Coasting uses no drive propellant or drive electricity, while essential services and occupied cargo holds still need power.
- Carrying surplus fuel is desirable insurance for unexpected maneuvering, diversions and combat, especially during war. Conserve fuel consumed by the planned journey while retaining useful uncommitted reserves. Reserve fuel still contributes to loaded mass. Saved contingency policies now protect capacity-based main-tank targets in addition to consecutive-leg allocations; explicit emergency orders can use that stock. See [ContingencyFuel.md](ContingencyFuel.md).

## Electricity and passengers

- Electrical readiness covers acceleration, coasting and braking separately plus a 48-hour essential and cargo arrival reserve. Local solar generation uses the shared stellar-strength and distance rule and bounded illumination cycles. Atmospheric phases suppress solar under the existing rule.
- Integrate the actual burn phases rather than charging drive power throughout every travel day. Hotel loads and cargo preservation continue during coasting. Include the remainder of the final daily tick consistently so forecast fuel use matches live accounting.
- Booked passenger ships need species-specific stores for the scheduled daily ticks. Separately issued approach, landing or later travel orders get their own checks. A return itinerary is optional. Actual cargo and supplies remain aboard until tick consumption or a paid transfer.
- An automated trader funds its next port leg through controlled braking and approach before buying and dispatching a load. Unknown destination purchases and tanker rescue are not assumed. A slower coast may be valid but must fund hotel loads, cargo preservation and passenger consumption for its entire duration.

## Interruption and recovery

- Persist local endpoint sites, frozen geometry, traveled distance, relative velocity, elapsed trajectory time and each ship's remaining burn budget. Keep local flight state distinct from any queued interstellar crossing.
- On an outage, advance every member only to the first unavailable powered interval. Preserve actual position and velocity at that instant. Later ticks retain ballistic motion without thrust; a power failure during coast does not delete velocity or grant controlled arrival.
- Recovery plans from current position and velocity using current physical reserves and a new funded braking check. It must not rewind progress, restart from rest, burn already consumed fuel again or silently restore the original trajectory. Overshoot must not claim arrival; turning back requires a future recovery rule unless a reverse trajectory is explicitly supported.
- Preserve local state across save/load, fleet splitting and merging and ship replacement paths. A split inherits motion and partitions budgets by ship ID without changing velocity. Dropping a slow ship does not grant immediate acceleration.
- Existing saves without physical local state continue their prepaid abstract journeys. They must not be migrated into trajectories that consume a second departure budget. New saved state requires an explicit save version update and backward-compatible record construction.

## Integration and acceptance

1. Build and test pure site geometry and immutable trajectory previews.
2. Add saved local flight state and preserve it through all location and fleet copies.
3. Integrate tick-owned burn phases, electricity, drift and funded local recovery.
4. Update local commands, queued crossings, passenger checks and trade pricing to use the same physical plan.
5. Rerun the trade and passenger audits and record measured results. Do not retune fuel or invent stock solely to preserve the old delivery counts.

Tests must cover moon-relative geometry, deterministic positions, shorter and longer routes, loaded fleets, mixed propulsion, coasting, braking reserves, eclipse and propellant failure, interruption, save/load, fleet organization and forecast/tick agreement. All previews remain bounded analytic calculations outside heavy JavaFX work.

## Initial verification

- Save version 39 stores `LocalFlight` in `FleetLocation`, including frozen endpoints, motion and per-ship propulsion commitments. Older local locations load without this field and retain their prepaid abstract progress.
- The full Maven suite passes 709 tests. Physical local coverage checks fuel-efficient cruising without a later scheduled arrival, phase fuel conservation, forecast/tick electricity agreement, solar electric departure from free stations, mixed loaded fleets, drift, empty braking tanks, overshoot rejection, funded recovery, split/merge and save/load including older locations without physical state. Booked passenger commands check actual scheduled days through local departure and a queued crossing.
- The nine 360-day trade scenarios complete without interrupted voyages. The catalog production probes complete eight local shipments for each tested drive and reject unfunded one-light-year crossings. Measured results are recorded in [TradeReliabilityReport.md](TradeReliabilityReport.md), [TradePropulsionReport.md](TradePropulsionReport.md) and [ShipEnduranceReport.md](ShipEnduranceReport.md).
- Current catalog chemical planet-to-moon probes take 8–11 scheduled days. The physical recovery probe takes nine days after a stationary electrical supply transfer. These are measured fixture results, not balance targets.
- Complete local resupply checks now cover fixed-route empty returns as well as loaded departures. Optional paid main top-ups are retained only when a safe local plan loses scheduled travel days. In the nearby-port audit this restores 120 fixed and 180 roaming deliveries, with no interrupted voyages or stationary outages. Roaming main fuel use is 1,901.3 kg over 360 days compared with 180,000 kg before economical cruising. The earlier 70-day fixed-route readiness wait disappears. No production stocks or coefficients were adjusted to restore delivery counts. Carried surplus is useful contingency stock; saved reserve targets account for owner diplomacy while route-specific hazards remain a future refinement.
- Exact orbit propagation, gravity, collision avoidance, launch windows, reverse recovery after overshoot and physical surface-to-surface itineraries remain future work. Existing solar envelopes remain conservative approximations along the frozen route. Tanker rendezvous and scheduled supply events retain their existing interstellar scope.

## Sol-scale verification

[LocalTradeDistanceReport.md](LocalTradeDistanceReport.md) adds sixteen loaded catalog station probes using Earth, Moon, Mars and Jupiter geometry. Ten completed voyages match forecast day, main fuel, electrical fuel and battery charge, preserve their mixed cargo through a loaded 60-day wait and then sell it. Six unsupported departures are rejected. Smaller chemical baskets and legal array slot budgets have separate regression coverage. No forecast or tick discrepancy was observed and no production coefficient was changed. The report records long travel durations and equipment limits as candidates for future balance work.

## Planetary transfer refinement

[PlanetaryTravelTuning.md](PlanetaryTravelTuning.md) records the next proposed orbital comparison, velocity-change bounds and required parking-orbit, timing and fuel rules. This draft does not change the current planner or saved trajectories.

The [read-only orbital report](PlanetaryTransferReport.md) now compares Mars and Jupiter parking transfers with current straight-line durations. It explicitly separates main-fuel feasibility, reactor feed and short-burn validity. These records do not establish electrical endurance or change movement.

The [parking and tank extension](ParkingOrbitTankReport.md) records maneuver-feasible fission Mars examples and continued chemical limits. Changing a comparison endpoint does not move an existing base, waive local access costs or authorize a new trajectory.

## Orbital impulse prototype

Save version 41 adds an exclusive orbital flight alongside existing straight-line local flight state. Explicitly configured planetary parking endpoints and conditioned chemical freighters use the orbital planner. Waiting, coast and maneuver events share daily electricity accounting; only paid escape, capture and approach events alter controlled motion. Cancellation, missing capture fuel and save/load preserve the physical state. Existing stored trajectories retain their own model. The complete activation rules, approximations and unsupported recovery geometry are in [OrbitalTransfers.md](OrbitalTransfers.md).
