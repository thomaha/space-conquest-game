# Autonomous backhaul report

## Price-aware refueling update

With price-aware working refills, the three-handoff autonomous chain records 14,346.05 credits over 202 days. The explicit contract comparison records 14,888.36 credits over 194 days. Both retain independent quote/tick and conservation checks. The detailed measurements below record the earlier refueling policy.


## Result

The existing roaming and intermediate-port planners complete three paid lunar return-cargo handoffs using current market stock and ordinary logistics processing. The hydrolox freighter selects aluminum for the Earth 2,000 km depot. The RP-1 supplier then chooses an empty climb from Earth 500 km, buys that aluminum at the depot and delivers it to the low-Earth buyer.

All three cycles finish in 194 simulation days and deliver 7,500 kg of aluminum to Earth 500 km. The chain earns the same 14,877.85-credit consumption surplus as the [explicit return-cargo audit](LunarReturnCargoReport.md), before excluded upstream and capital costs. This validates existing planner behavior; it does not introduce a new production pricing rule or autonomous upstream procurement.

## Prices and physical access

The Moon sells aluminum for 0.8 credits/kg. The depot posts 1.25 credits/kg, paying the existing 80% wholesale share of 1 credit/kg to the lunar carrier. The low-Earth buyer posts 3 credits/kg and pays 2.4 credits/kg. The handler purchases at the depot's full 1.25-credit offer. Depot resales are internal transfers and do not inflate consolidated revenue.

At the previous depot quote of 1 credit/kg, its wholesale bid only equals the lunar purchase price. The lunar carrier therefore declines the trade after counting propulsion. A profitable complete contracted chain does not guarantee that each independent carrier receives enough margin to choose its own leg.

The loaded hydrolox freighter cannot bypass the depot in this fixture: direct Moon-to-Earth-500 km readiness rejects the provisional 10% parking-period impulse limit. The audit retains that physical constraint. The supplier's empty access and loaded handling journeys are quoted together by the intermediate-port planner.

## Actual autonomous cycles

| Completed cycle | Campaign day | Lunar carrier quote profit | Handler combined quote profit | Low-Earth aluminum delivered kg | Chain consumption result |
|---|---:|---:|---:|---:|---:|
| 1 | 66 | 58.52528 | 2,760.28345 | 2,500 | 4,850.83 |
| 2 | 130 | 58.52528 | 2,760.28345 | 5,000 | 9,864.34 |
| 3 | 194 | 58.52528 | 2,760.28345 | 7,500 | 14,877.85 |

The separate paid-voyage check measures 441.47484 credits of consumed supplies during the lunar return and an actual carrier profit of 58.52516 credits. The small difference from the quote includes the idle tanker's electrical fuel during those ticks. The handler quote includes both its empty climb and loaded delivery and agrees with actual consumption within 0.02 credits.

Outbound mixed goods and lunar fuel deliveries remain explicitly dispatched. At the Moon, the test activates the trader's roaming route without specifying return cargo or a destination. After the actual depot sale, it activates the supplier's roaming route at low Earth. The planners select the material, shipment quantity and next port. Routes are deactivated after loaded departure to bound each measurement; committed shipments still finish. No carrier is teleported or replenished without payment.

## Selection sensitivity

Fifteen isolated first-arrival probes vary the shipment cap and depot posted price. These are lunar-carrier choices, not promises that a separate handler will later buy or deliver the cargo.

| Shipment cap kg | Depot posted price | Lunar carrier choice | Expected voyage profit |
|---|---:|---|---:|
| 500 | 1.00 | Wait | 0.00 |
| 500 | 1.25 | Wait | 0.00 |
| 500 | 1.50 | Wait | 0.00 |
| 500 | 2.00 | Wait | 0.00 |
| 500 | 3.00 | Earth depot | 363.96 |
| 1,250 | 1.00 | Wait | 0.00 |
| 1,250 | 1.25 | Wait | 0.00 |
| 1,250 | 1.50 | Earth depot | 61.92 |
| 1,250 | 2.00 | Earth depot | 561.92 |
| 1,250 | 3.00 | Earth depot | 1,561.92 |
| 2,500 | 1.00 | Wait | 0.00 |
| 2,500 | 1.25 | Earth depot | 58.53 |
| 2,500 | 1.50 | Earth depot | 558.53 |
| 2,500 | 2.00 | Earth depot | 1,558.53 |
| 2,500 | 3.00 | Earth depot | 3,558.53 |

The roaming planner prices the complete optional voyage. The earlier explicit-contract audit measured the incremental cost of loading a return journey that was already required. These answer different questions, so their acceptance thresholds should not be interchangeable.

## Verification and limits

Paid isolated selection leaves the live snapshot unchanged. Ordinary logistics processing commits the selected cargo and departure. Active lunar returns, empty handling climbs, loaded handling departures and completed cycles survive exact save/load checks. Tests conserve total money, each fuel material and all aluminum while preserving cargo, electrical supply, successful maneuvers and the freighter's 20% main-fuel reserve.

An exhausted depot buyer rejects the lunar trade. An exhausted final Earth buyer rejects the handler's combined staging quote. A lunar carrier can still sell to a funded depot when the separate final buyer is insolvent: its own paid next-leg contract does not guarantee downstream demand or reserve another owner's cash. The handler rechecks actual depot stock and buyer funds on arrival rather than committing a remote purchase in advance.

Run `mvn test` with JDK 27. `LunarRoundTripAuditTest` writes `engine/target/autonomous-backhaul-report.md` for quote/tick agreement and `engine/target/autonomous-backhaul-chain-report.md` for the repeated handoff and price probes.

Source production, surface lift, opening nuclear-stock lift, capital, wages, depreciation, taxes, other overhead and autonomous upstream supply remain excluded. Initial source inventories and buyer accounts are finite fixtures. The documented circular parking and patched lunar encounter approximations still apply.

The [active backhaul recovery audit](ActiveBackhaulRecoveryReport.md) now verifies depletion, price changes and exhausted buyer cash with both routes continuously active. Two handoffs deliver 5,000 kg, including recovery after a price change during the handler's empty climb. Neither route is reset or deactivated.
