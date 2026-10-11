# Lunar tanker comparison report

## Result

The best tested catalog fission tanker uses three standard main tanks, a large cargo hold and continuous reactor electricity. It fits all 30 medium-frame slots without a battery. Nine paid tanker round trips support three goods cycles by the same hydrolox freighter. The complete measured chain produces a 3,244.48-credit consumption surplus over 188 simulation days, before capital and excluded upstream costs.

The controlled original tanker loses 12,648.23 credits and needs 69 tanker round trips over 908 days. Both use the same payload search and replenishment policy. The larger-tank design improves this operating result by 15,892.71 credits. The earlier [round-trip audit](LunarRoundTripReport.md) used a fixed 3,000 kg net tanker payload and fixed depot batches, giving a larger 18,336.59-credit loss. The controlled comparison also improves the original design's payload and avoids redundant stock deliveries.

## Controlled comparison

All eight configurations use existing catalog components, 0.5 cm steel armor, fission research and the same Earth 2,000 km to Moon 500 km geometry. Tanker protected reserves remain 5% and the goods freighter retains 20%. No new technology, altered thrust, larger hull or free lunar propellant is introduced.

The bounded payload search tries hydrolox in 1,000 kg increments and return hydrogen in 1,000 kg increments. Every delivery also carries 50 kg uranium. Each candidate includes paid outward loading, a projected funded arrival, sale into the initially empty lunar market, paid local return supplies and a complete return readiness check. Selection minimizes consumed propellant and uranium value per kilogram of net hydrolox delivered. The grid does not prove global optimality.

| Configuration | Slots | Net hydrolox kg/trip | Return hydrogen cargo kg/trip | Quoted consumption cost per hydrolox kg | Actual tanker round trips | All round trips | Days | Three-cycle consumption result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Original two tanks and vault with battery | 28 | 4,000 | 18,000 | 0.06916 | 69 | 94 | 908 | -12,648.23 |
| Larger hold with original tanks and battery | 28 | 4,000 | 18,000 | 0.06916 | 69 | 94 | 908 | -12,648.22 |
| Two tanks and vault without battery | 26 | 8,000 | 16,000 | 0.03334 | 36 | 61 | 512 | -3,136.04 |
| Three tanks and vault with battery | 32 | Rejected: exceeds hull slots | | | | | | |
| Three tanks and vault without battery | 30 | 18,000 | 11,000 | 0.01778 | 15 | 40 | 260 | 1,642.53 |
| Three tanks and large hold without battery | 30 | 32,000 | 17,000 | 0.01119 | 9 | 34 | 188 | 3,244.48 |
| Four tanks and compact hold without battery | 29 | 2,000 | 0 | 0.15712 | 135 | 160 | 1,700 | -35,979.33 |
| One tank and vault without battery | 22 | No funded grid payload | | | | | | |

Each accepted design completes three actual goods delivery and empty-return cycles. The same tanker returns after every supply delivery. Its final delivery shrinks to the current lunar requirement, so net payload in the table is the selected maximum rather than every actual load. Depot batches also shrink to the current combined tanker and freighter requirement. Lunar supplies retained from a previous cycle remain available for later paid purchases.

## Why it improves

Adding only cargo space leaves the original main-fuel bottleneck in place. Removing the battery saves mass while the existing fueled electrical reactor continues to cover every load. Using those slots for a third main tank raises main capacity from 30,000 kg to 45,000 kg. The large hold then permits a 49,050 kg nominal supply load: 32,000 kg hydrolox, 17,000 kg hydrogen gas and 50 kg uranium, alongside separately purchased working drive feed.

The hydrolox remains at the Moon for the goods freighter. Hydrogen gas and part of the uranium fund the tanker's empty return. Four main tanks with a compact hold can carry both directions' propulsion from Earth but deliver too little net hydrolox to be efficient. These results show why both working-fuel capacity and useful delivered mass matter.

## Accounting and verification

Every configuration starts with the same finite stocks: 630,000 kg hydrolox mixture, 1,000,000 kg RP-1 mixture and 7,500 kg goods at Earth 500 km, plus 10,000,000 kg hydrogen gas and 20,000 kg uranium at Earth 2,000 km. The larger nuclear opening stocks let the inefficient compact-hold case complete its comparison. Unused opening source inventory is excluded from economic credit. No stock or cash is injected after opening.

Source propellant costs 0.005 credits/kg, uranium costs 1 credit/kg and goods cost 0.8 credits/kg. Lunar goods pay 2.4 credits/kg after the existing wholesale share. Three goods shipments earn 18,000 credits against 6,000 credits of goods inputs. Internal depot and fuel resales are excluded from economic revenue. Consumption results subtract consumed propellant, reactor feed and paid commissioning electricity. An independent reconciliation subtracts source acquisitions then adds retained supplies at their acquisition price.

Every committed outbound flight and completed cycle survives save/load. Actual ticks check total money conservation, nonnegative buyer accounts, per-material fuel conservation, protected reserves on completed goods returns, funded electricity, cargo preservation and successful maneuvers. The comparison exposed map-order rounding in derived cargo totals after reload; `TradeRoute` now sums manifest lots in stable material order so exact reload equality holds.

Run `mvn test` with JDK 27. `LunarRoundTripAuditTest` writes the detailed comparison to `engine/target/lunar-tanker-comparison.md`.

The full Maven suite passes 795 tests with no failures, errors or skipped tests. The diff whitespace check also passes.

## Limits and next comparison

This is a finite operating comparison with explicit supply dispatch. It excludes source production, surface lift, delivery of opening nuclear stocks to Earth 2,000 km, ship and station construction, wages, depreciation, taxes and autonomous procurement. The documented circular parking and patched lunar encounter approximations still apply. A positive result at these fixture prices does not establish full campaign profitability or indefinite sustainability.

The [return-cargo comparison](LunarReturnCargoReport.md) measures fifteen paid load and price cases against empty returns. It includes the loaded lunar return and onward delivery from the Earth depot to 500 km parking. Full 2,500 kg return loads sold at a posted price of 3 credits/kg add 11,633.37 credits across three cycles; small low-margin loads can reduce the baseline result.
