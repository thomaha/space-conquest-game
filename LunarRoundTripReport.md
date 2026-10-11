# Lunar round-trip report

## Scope and result

LunarRoundTripAuditTest closes the return loop left open by the [depot replenishment audit](DepotReplenishmentReport.md). The same catalog hydrolox freighter delivers three 2,500 kg mixed-goods shipments from Earth 2,000 km parking to Moon 500 km parking. It purchases physically delivered lunar propellant and returns empty to Earth after every sale. The same RP-1 depot supplier and fission lunar tanker also complete every return.

The measured chain is physically feasible but loses 18,336.59 credits over three cycles. Its supply design requires thirty tanker round trips per goods shipment. This is a reproducible feasible design, not a search for the cheapest fleet. It does not establish that all lunar trade requires return cargo or must lose money.

## Paid replenishment

The Moon starts without fuel. Opening stock is finite: Earth 500 km holds 630,000 kg hydrolox mixture, 1,000,000 kg RP-1 mixture and 7,500 kg mixed goods. Earth 2,000 km holds 5,000,000 kg hydrogen gas and 10,000 kg uranium. No stock or cash is injected after opening. Hydrogen gas for the fission drive is distinct from liquid hydrogen in the hydrolox mixture.

All three blueprints use existing catalog components and fit the 30-slot medium frame. The RP-1 supplier has two standard main tanks, a 30,000 kg cargo vault, two solar arrays and a battery. The fission tanker has two standard main tanks, a 30,000 kg cargo vault, a fission electrical reactor, its separate feed compartment and a battery. The hydrolox freighter retains its compact 2,500 kg hold and 120,000 kg main tank. Protected main reserves are 5% for suppliers and 20% for the goods freighter.

Each cycle begins with seven 30,000 kg hydrolox deliveries and one 2,500 kg goods delivery from low Earth parking to the depot. The tanker then makes thirty lunar deliveries, each carrying 3,000 kg hydrolox mixture, 17,000 kg hydrogen gas and 50 kg uranium. After selling these supplies, it buys local hydrogen and reactor feed to fund its actual empty return. Only the hydrolox portion remains available for the goods freighter. Main tanks are refilled through paid local purchases at their Earth origins before departure.

Each cycle completes 39 round trips: eight depot supply trips, thirty lunar tanker trips and one goods trip. The three cycles complete 117 round trips over 1,164 simulation days and sell 7,500 kg of goods. Each ship starts with an empty main tank and electrical stores. Initial battery charging and reactor reserves are purchased. Solar generation then supports the chemical ships while the tanker consumes separately purchased electrical reactor feed.

Contracts are explicitly dispatched through immutable paid shipment previews. Ordinary route processing handles docking, sales, local return refueling and physical returns. This does not add autonomous procurement or change the roaming planner's profitability rules.

## Economics

Source propellant costs 0.005 credits/kg, uranium costs 1 credit/kg and goods cost 0.8 credits/kg. Moon goods have a posted price of 3 credits/kg and pay the existing 80% wholesale share, giving 18,000 credits of total sales. Internal fuel and depot resales are excluded from economic revenue. Acquired input cost measures source withdrawals at acquisition prices. Retained fuel includes ship stores and delivered port inventory at the same source prices; unused opening source inventory receives no credit.

| Completed cycle | Day | Completed round trips | Consumed fuel cost | Acquired inputs | Retained fuel value | Consumption result |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 388 | 39 | 10,101.08 | 13,410.61 | 1,309.52 | -6,134.42 |
| 2 | 776 | 78 | 20,202.17 | 26,675.52 | 2,473.35 | -12,235.50 |
| 3 | 1,164 | 117 | 30,303.25 | 39,940.44 | 3,637.18 | -18,336.59 |

The consumption result independently reconciles to 18,000 credits of goods sales minus 6,000 credits of goods inputs, 30,303.25 credits of consumed propellant and uranium and 33.33 credits of commissioning electricity. It also equals goods sales minus acquired inputs and electricity plus retained fuel. This is an inventory-adjusted operating measurement rather than a cash-return claim.

| Material | Physically consumed kg | Source value |
|---|---:|---:|
| Liquid hydrogen | 89,368.07 | 446.84 |
| Liquid oxygen | 895,669.13 | 4,478.35 |
| Hydrogen gas | 4,080,811.22 | 20,404.06 |
| RP-1 kerosene | 178,633.26 | 893.17 |
| Refined uranium | 4,080.85 | 4,080.85 |

Empty returns and lunar fuel delivery overturn the positive one-way result. Additional trade would need at least 18,336.59 credits of net contribution across these cycles to cover the measured gap, plus its own incremental transport costs. Return cargo, improved tanker configurations and lunar production are candidate follow-up comparisons rather than assumed revenue or free supplies.

## Verification and limits

The follow-up [tanker comparison](LunarTankerComparisonReport.md) tests eight catalog configurations with unchanged research, prices and reserves. Its best measured design reduces lunar tanker round trips to nine and produces a 3,244.48-credit consumption surplus over three goods cycles. It also applies a shared payload search and current-stock replenishment policy to the original design for a controlled comparison.

Every committed outbound order and completed cycle survives save/load with fleets, routes, markets, accounts, empire treasuries and grid stores preserved. Every completed trip ends with the actual same ship docked at its origin and no onboard route cargo. The audit checks conserved total money, nonnegative buyer accounts and per-material source withdrawals against physical consumption and retained supplies. All measured ticks avoid essential power deficits, cargo power deficits, cargo loss and failed orbital maneuvers.

A separate test sends the goods freighter to an empty lunar fuel market. Its fixed route remains in RETURNING after sale. Reload preserves the wait and subsequent ticks neither buy partial supplies nor change market stock or cash. Local stock exhaustion and inaccessible Earth stock cannot supply a free return.

Run `mvn test` with JDK 27. The generated detail is `engine/target/lunar-round-trip-report.md`.

The full Maven suite passes 794 tests with no failures, errors or skipped tests. The diff whitespace check also passes.

Source production, surface lift, delivery of the opening nuclear supplies to Earth 2,000 km, ship and station construction, wages, depreciation, taxes and autonomous supply scheduling remain excluded. The run uses the documented circular parking and patched lunar encounter approximations. Finite fixtures establish repeated funded trips, not indefinite sustainability or optimized economy balance.
