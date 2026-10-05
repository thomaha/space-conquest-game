# Same-planet parking transfers (Draft)

## Status and scope

Implemented provisionally: Owned fleets can transfer between circular coplanar parking orbits around the same known planet. The planner uses the planet's catalog mass and the actual saved parking altitudes. It includes departure and circularization burns plus a separately funded final approach. Same-altitude docking or undocking uses one funded approach event. Exact station phasing, inclination changes, low-thrust spirals and moon-centered transfers remain unsupported.

ParkingOrbitTravel integrates with LocalTravel, orbital electrical accounting, tick-owned movement, trade previews and the existing fleet copy paths. Save version 42 identifies the extended itinerary semantics. Existing interplanetary flights and legacy journeys retain their saved motion and budgets.

## Activation and geometry

- Resolve docked station altitude from the actual station and free parking altitude from FleetLocation.Site. Unspecified legacy parking altitude defaults to 500 km.
- Known same-planet endpoints use parking planning when either endpoint declares an altitude or the fleet uses the conditioned chemical freighter tank. Recognized orders that fail cannot fall back to straight-line travel.
- Parking radius is the planet's radius plus altitude. Both endpoints must be positive and inside the existing planetary sphere-of-influence approximation.
- Different-radius transfers use a planet-centered Hohmann ellipse with the usual circular-to-transfer departure burn and transfer-to-circular arrival burn. The stellar orbital velocity does not fund a parking maneuver.
- The initial parking direction is deterministic from the body ID and campaign epoch. Departure is immediate; the prototype assumes circular orbital envelopes rather than solving individual station phase. This is a declared approximation and creates no claim of an exact rendezvous solution.
- Equal-radius approaches retain circular motion and reserve one hour before the funded 10 m/s docking or undocking event. They do not invent a radial transfer or add artificial escape and capture events.
- Selecting the same free parking orbit with an explicit default altitude creates no maneuver. Changing metadata is not a reason to consume insurance fuel.

## Fuel, power and arrival

- Every fleet member must fund its own loaded-mass rocket-equation consumption, compatible reactor feed, auxiliary electricity and protected contingency reserve.
- Departure and circularization estimates must fit the existing 10% parking-period impulse limit. Each maneuver also retains the six-hour ceiling. Unsupported drives or loads receive an explicit blocker.
- Different-altitude docked arrivals retain the existing one-hour and 10 m/s final approach allowance. Free-orbit arrivals retain the existing minimal positive arrival allowance; this is provisional accounting rather than a solved station maneuver.
- Use the shared conservative solar and eclipse envelope at the parent's stellar distance. Main thrust remains distinct from auxiliary electricity. Supplies, freight preservation and tank conditioning continue during the real scheduled phases.
- Forecasts and ticks use the same saved events. Main fuel and drive reactor feed are consumed only by a funded event and actual lighter mass retains unused fuel. Fleet events are atomic: an unfunded member blocks the maneuver for every member.
- Reaching the target radius does not award circularization or docking. Missing circularization fuel retains the actual planet-centered ellipse with a failed encounter. Missing approach funding retains the funded parking state.
- A successfully captured parking fleet can cancel its remaining order and issue a fresh funded same-altitude approach. Cancellation preserves body and altitude and does not award docking.

## Persistence and display

The immutable orbital itinerary now accepts three-event transfers or a one-event equal-altitude approach. Same source and target body IDs identify planet-centered state without adding another mutable motion model. Existing fleet splits and merges partition and combine the per-ship budgets while retaining common timing. Reload uses the frozen gravity, radii and event cursor rather than the replacement turn or catalog changes.

Fleet diagrams label planet-centered parking approximations and show the parent planet at the center. Departure and arrival orbit markers replace heliocentric endpoint markers. The single-event approach draws no fictitious transfer arc. Exact station phasing remains explicitly omitted.

## Verification and tuning

ParkingOrbitTravelTest verifies paid hydrolox transfers between 2,000 km and 400,000 km Earth parking orbits in both directions, forecast/tick agreement, retained mixed cargo and actual arrival altitude. It also covers unfunded circularization, continued gravity-driven motion, paid same-altitude docking and undocking, reload, one-event fleet organization and atomic failure of a mixed-readiness fleet.

The tested loaded chemical freighter is rejected for a 500 km to 400,000 km transfer by the finite-burn limit. Parking outside the sphere of influence is rejected too. These results do not justify changing drive coefficients or inventing free high-orbit access. Component balance and finite-burn integration need further measurements.
