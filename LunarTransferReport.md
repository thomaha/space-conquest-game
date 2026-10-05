# Lunar transfer report

## Scope

LunarTransferAuditTest measures 216 read-only catalog loads at campaign day zero: both Earth–Moon directions, Earth parking at 500, 2,000 and 100,000 km, Moon parking at 100, 500 and 8,000 km and protected main-tank reserves of 5%, 10% and 20%. Each ship carries 2,500 kg of mixed cargo and a full catalog main tank. The fission thermal reference also carries 50 kg of drive reactor feed and 100 kg of electrical feed.

The generated detail is `engine/target/lunar-transfer-report.md`. These seeded comparison stores are not free campaign supplies. The maneuver screen excludes voyage electricity, launch access, the final approach and profitability.

## Maneuver results

| Catalog drive | Passing rows | Total rows |
|---|---:|---:|
| RP-1 chemical | 30 | 54 |
| Methalox chemical | 32 | 54 |
| Hydrolox chemical | 36 | 54 |
| Fission thermal reference | 54 | 54 |
| Total | 152 | 216 |

The loaded chemical presets fail the provisional impulse screen from 500 km Earth parking. Higher parking improves finite-burn feasibility but access to that orbit needs its own funded journey. Some chemical loads also fail protected reserve checks. A passing thermal reference is a maneuver result with reactor feed included, not an electrical or economic acceptance result.

For 2,000 km Earth parking and 500 km Moon parking, the hydrolox comparison requires approximately 2,754 m/s at Earth and 764 m/s at the Moon. Coast duration is 5.012 days in either direction and day-zero waiting is about 0.026 days. The two main comparison burns require approximately 88,130 kg; the operational planner separately funds approach fuel and electricity.

## Paid operational acceptance

LunarTravelIntegrationTest purchases main propellant and grid charging at the real source port before sending the hydrolox freighter between the same parking altitudes. Both directions arrive on the forecast tick with matching actual fuel and battery charge while preserving mixed cargo. Solar arrays supply auxiliary electricity and the chemical drive supplies thrust.

Further checks cover missed capture with continued parent-centered ballistic motion, reload with identical next-tick behavior, captured cancellation preserving Moon parking, Moon-centered parking transfers, paid one-event undocking and actual fleet split/merge. Command and frontend checks validate lunar station altitudes and queued construction plus gravity-frame labels. The full Maven suite passes 778 tests with no failures, errors or skipped tests.

## Remaining model limits

Circular coplanar phase waiting and patched two-body gravity are provisional. Exact sphere crossings, lunar flyby deflection, inclination changes, station phase and local parking motion within the lunar transfer diagram are omitted. Transfers between unrelated moons or from parent parking beyond the supported lunar encounter boundary are rejected. Ballistic recovery needs a later funded planner. See [LunarTransfers.md](LunarTransfers.md).
