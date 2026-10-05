# Operational orbital transfers (Draft)

## Status and scope

The first operational impulse prototype is implemented and tested. CircularOrbitalEphemeris derives synthetic planetary positions and velocities from campaign elapsed days. HohmannCoast computes the star-centered coast. OrbitalTravel plans authoritative budgets and OrbitalFlightProcessor resolves funded events during daily electrical accounting.

The user approved coordinated model and integration changes. Save version 41 now retains station and construction parking altitudes, fleet parking sites and immutable orbital flight state. Movement, power, fleet organization and save/load preserve the itinerary. Unsupported ballistic recovery and supply rendezvous return explicit results.

The first operational prototype supports prograde circular coplanar transfers between distinct planets around one star with short chemical or thermal burns. Planetary inclinations are deliberately projected into a common plane for this approximation. Low-thrust transfers, equal-radius interplanetary transfers and arbitrary free-space rendezvous require separate planners. Supported planetary routes must not silently fall back to a stationary straight-line calculation when their orbital fuel budget fails.

Save version 42 extends the same saved event model to [same-planet parking transfers](ParkingOrbitTransfers.md). Different altitudes use planet-centered departure and circularization burns followed by a funded approach. Equal-altitude docking or undocking uses one paid approach event. Exact station phasing remains provisional. Existing interplanetary itineraries retain their motion and budgets.

Save version 43 supports [planet–moon transfers](LunarTransfers.md) with an explicit saved gravity frame. Parent-centered coast and Moon-centered parking use their respective gravity. The [paid lunar trade audit](LunarTradeReport.md) checks actual mixed shipments, departure readiness, funded docking and destination sales in both directions.

The mathematical coast uses mean motion and Kepler's equation, as described in the [University of Bremen orbital mechanics paper hosted by ESA](https://indico.esa.int/event/111/contributions/273/attachments/381/424/2016-03-07_submission_icatt.pdf). The prototype's simplifications and operational limits below are provisional game rules.

## Epoch and moving bodies

- Campaign day zero has a deterministic phase derived from the system ID and planet ID. This is a synthetic game ephemeris rather than the real Solar System's calendar configuration.
- One simulation turn equals one elapsed campaign day. Fractional phase events use seconds within that day. Display-clock pulses must not change simulation geometry before the coordinated tick.
- Planetary radius uses catalog distance in kilometres converted to metres. Angular rate is `sqrt(G * stellarMass / radius^3)` and circular velocity is tangent to the radius vector.
- Phase derivation is independent of planet-list order. Save/load must restore the campaign epoch consistently with the clock; loading with an arbitrary turn must not change an active transfer's frozen solution.
- Freeze accepted stellar mass, orbit radii, departure direction, departure epoch and maneuver parameters. Later catalog tuning must not retroactively rewrite an active flight.

## Parking orbits and bases

- Add an explicit finite parking altitude in kilometres for a station orbiting a planet. Existing stations and generic legacy planetary orbit sites default to 500 km.
- New station construction must validate the chosen altitude above the surface and within the existing sphere-of-influence approximation. High-altitude bases must actually be constructed there; previews cannot substitute convenient benchmark altitudes for real stations.
- Fleet parking state must retain its body and altitude after capture and through docking, undocking, split, merge, combat and save/load. A captured 8,000 km orbit cannot become a 500 km orbit without a funded transfer.
- Surface export, same-body orbit changes and the final station rendezvous remain separately funded operations. Matching a planet's position is insufficient to claim docking with its station.
- The measured 100,000 km Earth to 8,000 km Mars example is a test fixture. It creates no free station, fuel stock or yard in a campaign.

## Planning and fleet limits

- Compute the next Hohmann launch opportunity from the actual epoch and relative planetary phase. Waiting is visible in the preview and saved as part of the order.
- Check each member's loaded mass, chosen exhaust velocity, main propellant, protected contingency reserve and compatible reactor feed. Freeze per-ship commitments while conserving physical stock.
- Departure escape and destination capture use the declared parking radius and planet mass. Star-centered excess velocity alone cannot stand in for either gravity-well maneuver.
- Retain the provisional 10% parking-period maximum for an impulsive maneuver estimate. Every ship must pass both maneuver limits. Unsupported low-thrust drives receive an explicit rejection.
- Shared maneuver timing follows the limiting fleet member. Fleet organization must not accelerate a paid itinerary or erase consumption. A split retains the same current phase and motion with partitioned per-ship budgets.
- Return planning is advisory. Validate and fund the next destination leg without requiring a return route. Extra carried fuel remains allowed insurance and protected fuel is consumed only under the existing explicit emergency policy.
- Within supported candidates prefer earlier funded arrival and conserve fuel for equivalent arrival schedules. A waiting order does not reserve future market stock or assume future purchases will succeed.

## Phase execution

The saved phase sequence is waiting at the departure base, funded escape, heliocentric coast, funded capture and separately funded station approach. The approach uses a provisional one-hour rendezvous allowance and 10 m/s maneuver budget. Detailed station phasing and local planetary orbit motion are not propagated. Reported position and velocity after capture represent the planetary orbital envelope; the actual parking body and altitude are retained separately.

The initial short-burn prototype uses an explicitly labelled impulse approximation. Its measured burn duration still constrains validity and charges the full electrical auxiliary requirement. The additional six-hour maneuver ceiling bounds this first prototype. Auxiliary energy is charged atomically without claiming extra solar generation or charging time. A conservative dark peak check includes essential services and cargo; normal daily services remain separately accounted. Before a maneuver event, verify the entire maneuver's actual propellant, reactor feed and electrical allocation atomically. Only a funded maneuver changes velocity. Actual loaded mass determines consumption within the frozen upper budget, so electrical feed consumption or lighter cargo preserves unused fuel. A heavier changed load that exceeds the saved budget rejects the maneuver and needs replanning. This avoids treating a partially funded impulse as a completed escape or capture. Mid-burn continuous thrust and gravity integration need a later solver; they must not be implied by this approximation.

Resolve phase boundaries at their fractional day epochs. A daily tick can cross several boundaries but each burn is charged once. Orbital motion advances in the power processor and the movement processor does not advance it a second time. Waiting, coast and arrival services use the remaining portions of the same daily electrical budget. Preview and simulation must share event ordering and illumination assumptions.

Coast follows the transfer ellipse rather than constant speed or linearly interpolated position. Reaching the planned encounter does not grant a free capture. An unfunded capture retains physical velocity and records a missed encounter. HohmannCoast can continue the unmodified star-centered ellipse beyond the encounter; planetary flyby deflection requires a separate gravity treatment and must be identified as unsupported in the initial prototype.

## Electricity, freight and supplies

- Chemical and thermal main thrust is distinct from auxiliary electricity. Solar panels remain usable while traveling through space and retain stellar-strength and inverse-square distance scaling.
- Initially use conservative flux at the larger endpoint radius and the existing conservative eclipse envelope for both forecasts and ticks. This sacrifices some available generation while retaining a bounded shared calculation. Exact shadows and variable coast generation can be added together later.
- Waiting, conditioned tanks, passengers and cargo consume real electricity. No phase creates free power, propellant or food.
- Check full waiting and voyage endurance plus the applicable destination reserve. During waiting, ordinary paid port replenishment may improve actual stores; recompute readiness before escape. A failed check leaves the fleet at its real departure base.
- Existing main-tank outage venting remains once per day after power and maneuver resolution. Capture and recovery use the remaining physical fuel rather than the original commitment. Dedicated tanker stock and ordinary mixed freight remain separate stores.
- Scheduled supply transfers cannot execute merely because their timer elapsed. Reject unsupported orbital rendezvous clearly. Only a verified shared site or velocity-matched encounter permits transfer.

## Failure and recovery

- No propellant means no commanded acceleration or braking. Orbital gravity may change velocity without fuel. Do not apply the old straight-line drift model to orbital state.
- An electrical outage during coast does not halt gravitational motion. It may damage cargo or passengers and vent conditioned fuel under the existing rules. The next maneuver requires actual electrical readiness.
- Preserve epoch, two-dimensional position and velocity, current phase, executed maneuvers and remaining stores on interruption. Cancellation cannot teleport a ship back to its departure base or into the destination orbit.
- Recovery must plan from that physical state and charge a new maneuver only once. Unsupported recovery geometry returns an explanatory result while ballistic evolution continues; it must not reuse a stale Hohmann arrival or award docking.
- Existing straight-line local flights and interstellar journeys keep their saved model. New orbital state must be mutually exclusive with local straight-line state.

## Coordinated implementation

1. Implemented: Deterministic circular ephemeris and analytic transfer coast with conservation and moving-encounter tests.
2. Implemented: Immutable parking and orbital-flight state, backward-compatible defaults and save version 41. Station, construction, household and fleet copy paths retain the new fields.
3. Implemented: Authoritative planning through LocalTravel and queued MoveFleetLocalCommand, shared event accounting, power forecasts and explicit failure reporting.
4. Implemented: Saved waiting, queued cancellation, capture failure, membership preservation and explicit unsupported ballistic recovery and supply rendezvous. Waiting cancellation leaves the real departure base; cancelling after capture retains the declared target parking orbit.
5. Implemented: Asynchronous local previews, orbital status and cancellation controls plus selectable station construction altitude. Automated trade uses the same LocalTravel path, bounded arrival projection and funded next-port checks.
6. Verified: Paid high-orbit chemical voyage, reactor-powered thermal voyage, forecast and tick accounting, save/load, cancellation, fleet organization and insufficient capture fuel. Economic profitability and broader catalog balance remain tuning work.

## Acceptance checks

- Circular body motion has the expected radius and speed and repeats after its orbital period.
- Inward and outward launch windows meet the moving target at the scheduled encounter. Relative arrival speed remains nonzero before capture.
- Unpowered coast conserves orbital energy and angular momentum and is independent of tick subdivision.
- Forecast and actual ticks agree on epoch, consumed propellant, reactor feed, electricity, remaining reserves and cargo.
- Missing capture fuel or power never produces arrival or docking. Outages and cancellation preserve actual motion.
- Save/load, fleet splits and permitted merges preserve spent fuel, parking altitude and future events without duplicate burns.
- Existing 500 km stations retain their actual low-orbit budgets. The high-orbit benchmark requires real high-orbit endpoint state.

OrbitalTransferGeometryTest verifies independent motion. OrbitalTravelIntegrationTest verifies funded operational voyages, failures, mass-sensitive burns, waiting purchases and persistence. OrbitalControlsTest verifies queued UI construction and cancellation. Existing compatibility routes remain covered by the full suite.

## Activation and compatibility

The prototype activates between known planetary parking sites when either endpoint declares an altitude or a fleet uses the conditioned chemical freighter tank. Distinct planets use stellar transfer geometry and same-planet endpoints use parking transfer geometry. New station construction declares its altitude and the local UI declares 500 km for generic planetary orbit destinations. Existing stations and older orbit sites without that field retain the explicitly identified legacy path; their effective parking altitude is 500 km and no historical flight is rewritten. Recognized orbital orders that fail cannot fall back to the legacy straight-line calculation.

Fuel, electrical feed and real stored grid charging can be bought through ordinary paid commands while a fleet is waiting at its source. Escape rechecks complete remaining main-fuel and electrical readiness. A failed waiting order stays at the base and must be cancelled and replanned for a new opportunity. Return planning remains optional. No high-altitude campaign bases or opening fuel stores are added automatically.

## Paid trade measurements

[OrbitalTradeReport.md](OrbitalTradeReport.md) records fourteen paid mixed-shipment scenarios using actual launch windows, fuel purchases, grid charging, preserved freight and funded docking. Eight complete their selected reserve target. The seeded day-zero window adds about 611 waiting days to the 259-day coast; starting on campaign day 365 reduces the total from 870 to 505 days. Hydrolox retains about 791 kg above the 20% reserve after approach.

Fuel prices in this audit are explicit sensitivity inputs. At 1 credit/kg the tested cargo margin cannot cover consumed propellant; at 0.01 credit/kg the hydrolox case passes that limited consumption screen. Automated selection rejects the former even with prepaid full tanks and selects a mixed basket in the latter. The route ledger's cargo margin excludes supplies purchased before assignment and must not be read as complete voyage profitability. Construction, wages, replenishment, interest and detailed shadow geometry remain outside the measurements. Equipment and price tuning still require broader campaign evidence.

Waiting fleets now retain UI access to main fuel, electrical feed and paid grid charging at their actual source. Purchases are queued and conserve port stock and stored grid energy. The same controls are unavailable after escape. Adding cargo or other mass can invalidate a frozen maneuver budget, so a changed load may require cancellation and replanning.

## Journey display

Active orbital fleet cards include a canvas diagram derived from the frozen itinerary. OrbitalJourneySnapshot supplies immutable points, current fleet and endpoint positions and maneuver timing. Sampling runs on a background preview worker; the JavaFX thread only renders the resulting snapshot. The diagram changes only when a new tick snapshot reaches the view and does not advance simulation time.

The orange arc is the planned coast and the gray ellipse is the ballistic orbit that continues after a missed capture. A white fleet ring identifies normal motion and a red ring identifies a failed order. Endpoint markers move on their frozen circular orbital envelopes. Waiting tracks the source envelope, capture tracks the target envelope and an unfunded capture retains the actual transfer-ellipse state. Completed maneuvers are distinguished from scheduled or unexecuted events; a drawn encounter does not establish docking.

The display labels parking altitudes but does not draw the small planetary parking orbit or exact station phasing. It uses the saved solution rather than newly loaded catalog masses or a replacement campaign turn. The diagram is an inspection aid for the existing impulse model and introduces no new navigation, supply or recovery capability.
