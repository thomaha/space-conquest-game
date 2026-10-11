# Same-body parking transfers (Draft)

## Status and scope

Stations now have distinct saved orbitalPhaseRadians at campaign day zero. Their circular position and velocity use one shared ephemeris for sensor coverage, docking, phasing, geometry and native parking departure. Equal altitude does not mean equal position. Save version 54 retains station phase through population, power and module updates. Older station records derive a stable distinct phase from system, body and station IDs. A previously saved approach that no longer matches its station's migrated position is safely interrupted and retains actual coast for replanning.

Implemented provisionally: Owned fleets can transfer between circular coplanar parking orbits around the same known planet or moon. The planner uses the body's catalog mass and saved parking altitudes. It includes departure and circularization burns plus a separately funded final approach. New docking orders use StationRendezvous to match station phase and velocity. Different-altitude arrivals fund native departure and circularization before handing over to the saved finite-burn approach. Same-altitude docking uses vector contact directly. Undocking and older saved native docking keep their one-event semantics. Inclination changes and low-thrust spirals remain unsupported.

ParkingOrbitTravel integrates with LocalTravel, orbital electrical accounting, tick-owned movement, trade previews and the existing fleet copy paths. Save version 42 identifies the extended itinerary semantics. Existing interplanetary flights and legacy journeys retain their saved motion and budgets.

RecoveredParkingTravel connects completed circular body recovery to that same planner. It qualifies actual coplanar prograde position and velocity and the saved body frame, including cancellation before the first approach burn. LocalTravel and queued MoveFleetLocalCommand can then plan docking or a new altitude around that body. Active burns, eccentric drift, stellar coast and destinations around other bodies remain outside this adapter.

The recovered source phase comes from actual position instead of the deterministic body phase. An optional sourceMotion on the immutable itinerary preserves exact position and velocity until the first funded event. Source-coast updates run on the simulation tick; UI status and sensor reads consume the saved point directly. Failed approaches continue the physical coast and cancellation restores it without refunding fuel. Cancellation after paid circularization also retains actual phase and spent stores. Free parking arrivals keep their physical circular vector coast so a following maneuver can be planned without a phase reset. Recovered docking uses StationRendezvous and the shared gravity-frame shooting solver. It predicts the station with the same circular ephemeris used by sensors and budgets two finite burns from actual position and velocity. A bounded search may reject distant phases even when another physical solution exists. Contact is checked at burn completion within one meter and 0.001 m/s before the daily remainder; missed contact, cancellation, lost power or a removed or moved station leaves actual gravitational coast. Main fuel is paid during thrust and committed reactor feed is paid at departure. Existing vector records retain the approach through save/load with the campaign epoch restored.

## Funded orbital phasing

When the direct finite-burn approach cannot find a funded route, StationRendezvous tries OrbitalPhasing from an actual circular coast at the station's radius. Waiting in the same circular orbit cannot close a phase gap. A paid departure burn instead enters a temporary higher or lower elliptical orbit whose period brings the ship back to the station. A paid final burn matches the station's full velocity. This fallback also applies to the saved approach after ordinary planetary, lunar or parking capture.

The bounded search considers one to three revolutions and three station-period offsets, with a seven-day ceiling. Ellipse seeds must clear the body and remain inside the saved gravity frame. A numerical correction solves the two finite burns against the shared TwoBodyFlight propagator, checking actual final position and velocity. Each burn is limited to 2% of the source circular period and the slowest loaded ship's acceleration. Every member must fund rocket-equation main fuel, compatible drive feed, protected reserve and electricity for the complete burn and coast schedule. A failed search rejects the order without charging stores.

Phasing retains the existing saved vector trajectory and actual tick-owned position and velocity, requiring no save schema change. Main fuel is consumed only during thrust. Cancellation freezes the remaining thrust and retains the actual elliptical coast and spent fuel, without docking or restoring circular motion. That eccentric state needs funded orbital recovery before ordinary parking planning. Direct and post-capture previews identify the longer phasing coast. The planner remains bounded and does not promise every reachable rendezvous or handle inclination changes.

OrbitalPhasingTest covers opposite-side Earth and Moon rendezvous, a lower lunar phasing orbit, quoted fuel and battery agreement, real docking after save/load, cancellation after the paid departure burn and rejection with empty tanks.

BodyFleetRendezvous can reuse the same phasing solver for a returning escort and an owned convoy parked at the same body-orbit radius. The target uses the ordinary parking ephemeris rather than a station phase. Return accepts a plan only when its continued circular motion still matches the convoy at the coordinated movement tick; final reattachment checks actual position and velocity. ParkedEscortReturnTest covers paid approach and return after convoy evacuation. See [FleetProtection.md](FleetProtection.md).

## Activation and geometry

- Resolve docked station altitude from the actual station and free parking altitude from FleetLocation.Site. Unspecified legacy parking altitude defaults to 500 km.
- Known same-planet endpoints use parking planning when either endpoint declares an altitude or the fleet has a recognized main drive. Recognized orders that fail cannot fall back to straight-line travel.
- Parking radius is the planet's radius plus altitude. Both endpoints must be positive and inside the existing planetary sphere-of-influence approximation.
- Different-radius transfers use a planet-centered Hohmann ellipse with the usual circular-to-transfer departure burn and transfer-to-circular arrival burn. The stellar orbital velocity does not fund a parking maneuver.
- The initial parking direction is deterministic from the body ID and campaign epoch. Departure is immediate; the prototype assumes circular orbital envelopes rather than solving individual station phase. This is a declared approximation and creates no claim of an exact rendezvous solution.
- New equal-radius docking uses the representative circular source state and a finite-burn station rendezvous without artificial escape or capture. Native undocking and legacy saved approaches retain the one-hour and 10 m/s event.
- Selecting the same free parking orbit with an explicit default altitude creates no maneuver. Changing metadata is not a reason to consume insurance fuel.

## Fuel, power and arrival

- Every fleet member must fund its own loaded-mass rocket-equation consumption, compatible reactor feed, auxiliary electricity and protected contingency reserve.
- Departure and circularization estimates must fit the existing 10% parking-period impulse limit. Each maneuver also retains the six-hour ceiling. Unsupported drives or loads receive an explicit blocker.
- Different-altitude docked arrivals save the actual vector approach, its physical budgets and its destination illumination envelope. Capture hands over at the next tick boundary, accounting for the intervening circular coast and services. Movement does not advance the approach a second time in that tick. Free-orbit arrivals retain the existing minimal positive arrival allowance.
- Use the shared conservative solar and eclipse envelope at the parent's stellar distance. Main thrust remains distinct from auxiliary electricity. Supplies, freight preservation and tank conditioning continue during the real scheduled phases.
- Forecasts and ticks use the same saved events. Main fuel and drive reactor feed are consumed only by a funded event and actual lighter mass retains unused fuel. Fleet events are atomic: an unfunded member blocks the maneuver for every member.
- Reaching the target radius does not award circularization or docking. Missing circularization fuel retains the actual planet-centered ellipse with a failed encounter. Missing approach funding retains the funded parking state.
- A successfully captured parking fleet can cancel its remaining order and issue a fresh funded same-altitude approach. Cancellation preserves body and altitude and does not award docking.

## Persistence and display

Save version 53 adds optional docking to native itineraries. OrbitalDocking saves handover timing, the physical approach and its illumination envelope. Quotes include capture and actual docking fuel and power rather than the old fixed approach allowance. Split and merge operations partition all per-ship budgets. Old records default docking to null and keep their original paid events. Restore the actual campaign epoch for station ephemeris validation.

The immutable orbital itinerary now accepts three-event transfers or a one-event equal-altitude approach. Same source and target body IDs identify planet-centered state without adding another mutable motion model. Existing fleet splits and merges partition and combine the per-ship budgets while retaining common timing. Reload uses the frozen gravity, radii and event cursor rather than the replacement turn or catalog changes.

Save version 52 adds optional sourceMotion for recovered parking departures. Older itineraries default it to null and retain their prior behavior. Retention, formation joins and save/load preserve the physical source coast alongside per-ship future maneuver budgets.

Fleet diagrams label planet-centered parking approximations and show the parent planet at the center. Departure and arrival orbit markers replace heliocentric endpoint markers. The single-event approach draws no fictitious transfer arc. New native docking orders label the final station rendezvous and its saved completion time. The native diagram shows the transfer envelope; physical docking follows in the body frame.

## Verification and tuning

OrdinaryStationDockingTest verifies ordinary parking, Mars and Moon arrivals with paid physical docking, quoted fuel and battery agreement, save/load before and after capture, depleted approach fuel or power, lost stations, cancellation and saved budgets through fleet splits and merges. Chemical native integration tests cover funded free parking separately. The mixed freight fixture reaches its Mars hub through a funded post-capture station approach and sells the whole mixed load. Earlier trade audit results used the former fixed docking allowance and do not certify new physical docking costs or reachability. StationEphemerisTest checks paid movement between distinct same-altitude stations and position continuity after reload.

ParkingOrbitTravelTest verifies paid hydrolox transfers between 2,000 km and 400,000 km Earth parking orbits in both directions, forecast/tick agreement, retained mixed cargo and actual arrival altitude. It also covers unfunded circularization, continued gravity-driven motion, paid same-altitude docking and undocking, reload, one-event fleet organization and atomic failure of a mixed-readiness fleet.

RecoveredParkingTravelTest verifies Earth and Moon docking through queued commands, unchanged source position and velocity, save/load, forecast and tick fuel agreement, actual-phase altitude departures, physical free-orbit arrival, cancellation before a maneuver and after paid circularization, insufficient reserves, real electrical failure, missed position or velocity contact and removal of the station. No new balance fixtures or drive coefficients are introduced.

The tested loaded chemical freighter is rejected for a 500 km to 400,000 km transfer by the finite-burn limit. Parking outside the sphere of influence is rejected too. These results do not justify changing drive coefficients or inventing free high-orbit access. Component balance and finite-burn integration need further measurements.
