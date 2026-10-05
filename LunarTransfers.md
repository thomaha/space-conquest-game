# Planet–moon transfers (Draft)

## Implementation status

Implemented provisionally: Circular coplanar transfers between a known planet's parking orbit and parking around one of its own moons use the parent's gravity for the transfer coast and the moon's gravity for lunar escape or capture. Chemical and thermal thrust remain separate from auxiliary electricity. Exact encounter geometry, inclination changes, low-thrust spirals and transfers between unrelated moons need later planners.

LunarTransferComparison provides read-only geometry and OrbitalTravel uses it for funded operational plans. Save version 43 retains an explicit gravity frame alongside the frozen coast and maneuver budgets. Moon parking, station altitude validation, cancellation, fleet organization and the diagram resolve the actual parent or moon consistently. Older saves with no frame infer the previous planetary parking or stellar transfer semantics.

## Transfer geometry

- Resolve the moon from its actual parent planet. Catalog moon distance is parent-relative, not a distance from the star.
- Derive deterministic circular lunar phase from the system, parent and moon IDs plus the campaign epoch. Planetary parking direction uses the existing deterministic parking approximation.
- Use a parent-centered Hohmann ellipse between the actual parent parking radius and the moon's orbital envelope. Include phase waiting so the predicted moon reaches the encounter location.
- In the outward direction, pay the parent parking departure velocity change and lunar capture from the calculated parent-relative excess velocity. In the inward direction, pay lunar escape and parent parking circularization. A final station approach is separately funded.
- Use the moon's mass and declared lunar parking radius for lunar escape, capture and parking-period checks. Do not substitute the parent's mass or treat lunar arrival as free circularization.
- Both parking sites must remain inside their relevant sphere-of-influence approximations. The parent parking radius must be smaller than the moon's orbital radius minus its sphere-of-influence radius; higher parent orbits require a separate geometry treatment.
- The coast and lunar gravity-well budget are a patched two-body game approximation. Finite sphere crossings, flyby deflection and exact station phasing are not implied.

## Fuel, power and failures

- Validate every fleet member's loaded mass, main propellant, drive reactor feed and protected reserve. Retain the 10% parking-period impulse criterion and six-hour maneuver ceiling.
- Solar generation follows the parent planet's stellar distance. Waiting, cargo preservation, life support and propulsion auxiliaries consume real supplies and electricity through the same saved timing.
- A base-to-base journey must fund its final docking approach. Return planning remains optional and excess carried fuel remains insurance.
- Missing lunar capture continues the parent-centered ballistic trajectory without docking. Missing parent circularization also retains the actual parent-centered coast. Gravity may change velocity without fuel but engine acceleration or braking needs a funded maneuver.
- After lunar capture, cancellation must preserve the moon-centered parking orbit and altitude. Same-moon parking transfers and paid docking need the moon's own gravity and sphere-of-influence limit.
- Saved active trajectories retain their original frame, masses, radii, phases and event cursor through reload and fleet organization. Older interplanetary and parking flights keep their existing semantics.

## Comparison and acceptance

The [lunar transfer report](LunarTransferReport.md) measures Earth–Moon transfers in both directions across three Earth altitudes, three Moon altitudes, three chemical freighter presets, a thermal reference and 5%, 10% and 20% reserves. Of 216 rows, 152 pass all maneuver screens. The report records launch waiting, coast duration, departure and capture velocity changes, propellant use and finite-burn rejection. Geometry tests separately reject unsupported gravity-well limits. Comparison seeds are not free campaign supplies and passing a maneuver screen is not proof of paid voyage readiness or profitability.

LunarTravelIntegrationTest verifies paid outward and inward journeys, forecast/tick agreement, missing capture, captured cancellation, Moon parking gravity, one-event undocking, reload and actual fleet split/merge. LunarStationCommandTest and frontend tests verify lunar altitude validation, queued construction and parent-centered versus moon-centered diagram labels. No route with recognized unsupported lunar geometry may silently use legacy straight-line travel.

The initial paid hydrolox fixture carries 2,500 kg of mixed cargo between 2,000 km Earth parking and 500 km Moon parking. Its solar array supplies auxiliary electricity while chemical fuel pays all three maneuvers. The same loaded freighter at 500 km Earth parking fails the provisional finite-burn limit. This is a catalog tuning result rather than a general prohibition on low Earth orbit departure.

The diagram displays the parent-centered lunar envelope during a transfer, including after capture while the approach is pending. It does not resolve motion inside the lunar parking orbit until a new Moon parking maneuver is planned. Successful capture retains the correct lunar body and altitude even though this small-scale motion is omitted.

The [paid lunar trade report](LunarTradeReport.md) adds 26 actual shipment scenarios in both directions. Sixteen complete funded mixed-cargo delivery while preserving their selected reserves. Automated selection counts consumed fuel even when tanks are already full. Partial sales and lunar-port replenishment preserve physical stock, owner cash and the remaining manifest through reload. Traders can wait at the destination without committing to a return leg.

The [staged lunar trade report](StagedLunarTradeReport.md) verifies an empty paid climb from 500 km Earth parking to a 2,000 km depot before refueling, buying mixed cargo and departing for the Moon. Fourteen of eighteen probes complete both legs in seven simulation days. The measured economics include consumed fuel on both legs. Empty roaming traders can also choose a profitable intermediate parking port on their current body and recheck the lunar trade on actual depot arrival. Ports remain finite-stock fixtures; surface access, cargo-loaded staging, longer port chains and port replenishment are separate work.
