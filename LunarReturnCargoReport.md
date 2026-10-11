# Lunar return-cargo report

## Result

Paid return cargo improves the [efficient tanker supply chain](LunarTankerComparisonReport.md) when its margin covers both the loaded lunar return and onward Earth handling. Three full 2,500 kg return loads sold at a posted Earth price of 3 credits/kg add 11,633.37 credits to the empty-return result. The complete chain earns a 14,888.36-credit consumption surplus before excluded production and capital costs.

The same hydrolox freighter carries the outbound goods and return aluminum. The same RP-1 supplier handles onward delivery from the Earth 2,000 km depot to the 500 km port. All ships complete physical paid journeys. Return cargo is not assumed to be free and depot receipt is not counted as final low-Earth delivery.

## Physical comparison

The improved fission tanker retains three standard main tanks, a large hold and continuous paid reactor electricity. Tanker reserves remain 5% and freighter reserves remain 20%. Source prices, initial stocks, outbound goods and replenishment policy match the tanker comparison. Each case also starts with a finite 7,500 kg aluminum stock at the Moon's 500 km port, priced at 0.8 credits/kg. Unused opening lunar aluminum earns no economic credit.

After each outward goods sale, the freighter purchases 500 kg, 1,250 kg or 2,500 kg aluminum and makes its loaded return to the Earth depot. The RP-1 supplier refuels at the 500 km Earth port, makes a funded empty climb to the depot, purchases the returned aluminum and delivers it to the 500 km buyer. These depot resales are internal transfers. No separate port handling fee applies in these parking-port fixtures; the added handling propulsion is included.

Every loaded case completes 37 round trips over 194 days. The empty-return baseline completes 34 round trips over 188 days and earns 3,254.99 credits after consuming 8,722.79 credits of propulsion and reactor supplies. The added three round trips and six days belong to the low-Earth handling service. Lunar return cargo uses the freighter's existing return journey.

## Price and load sensitivity

Final Earth sales pay the existing 80% wholesale share. A posted price of 1 credit/kg therefore pays 0.8 credits/kg, exactly matching the lunar purchase price before transport. The incremental result compares the complete three-cycle chain with the empty-return baseline.

| Return cargo kg/cycle | Earth posted price | Three-cycle chain result | Increment over empty returns |
|---|---:|---:|---:|
| 500 | 1.00 | 2,912.10 | -342.89 |
| 500 | 1.25 | 3,212.10 | -42.89 |
| 500 | 1.50 | 3,512.10 | 257.11 |
| 500 | 2.00 | 4,112.10 | 857.11 |
| 500 | 3.00 | 5,312.10 | 2,057.11 |
| 1,250 | 1.00 | 2,903.19 | -351.79 |
| 1,250 | 1.25 | 3,653.19 | 398.21 |
| 1,250 | 1.50 | 4,403.19 | 1,148.21 |
| 1,250 | 2.00 | 5,903.19 | 2,648.21 |
| 1,250 | 3.00 | 8,903.19 | 5,648.21 |
| 2,500 | 1.00 | 2,888.36 | -366.63 |
| 2,500 | 1.25 | 4,388.36 | 1,133.37 |
| 2,500 | 1.50 | 5,888.36 | 2,633.37 |
| 2,500 | 2.00 | 8,888.36 | 5,633.37 |
| 2,500 | 3.00 | 14,888.36 | 11,633.37 |

| Return cargo kg/cycle | Added consumed supplies across three cycles | Break-even Earth posted price |
|---|---:|---:|
| 500 | 342.89 | 1.28574 |
| 1,250 | 351.79 | 1.11726 |
| 2,500 | 366.63 | 1.06111 |

The break-even price is `(0.8 + added transport cost / total return cargo) / 0.8`. It is the price at which return cargo stops reducing the baseline result, not the price at which the entire chain breaks even. Values use fixed measured consumption and exclude other costs. Full loads spread the handling trip over more cargo. A 500 kg load at a posted price of 1.25 credits/kg fails to cover its added transport even though the complete chain remains profitable.

## Accounting and verification

All sixteen cases include three outbound goods cycles with the same freighter and finite paid lunar fuel delivery. Fifteen cases add return cargo and one measures empty returns. Economic results include outbound goods inputs, lunar aluminum purchases, final low-Earth wholesale sales, consumed working propellant, drive reactor feed, electrical reactor fuel and commissioning electricity. Internal resales contribute no net revenue. Source-acquisition cost plus retained supplies at their acquisition prices independently reconciles to the consumption result.

Each committed flight survives save/load. Loaded lunar returns and final low-Earth deliveries also reload after arrival. Tests check conserved total money, per-material fuel conservation, aluminum conservation, complete delivered manifests, protected freighter fuel reserves, funded electricity, cargo preservation and successful maneuvers. Isolated backhaul previews reject missing lunar stock, exhausted depot buyer cash and an insolvent trader without partial cargo or cash changes.

Run `mvn test` with JDK 27. `LunarRoundTripAuditTest` writes the detailed results to `engine/target/lunar-return-cargo-report.md`.

The engine regression reports include the refreshed return-cargo audit and price-aware refueling checks.

## Limits and next step

These are explicit paid contracts rather than autonomous return-cargo selection. Aluminum production and its delivery to the opening lunar parking stock remain excluded, alongside source production, surface lift, opening nuclear-stock lift, ship and station construction, wages, depreciation, taxes and other overhead. The documented circular parking and patched lunar encounter approximations still apply. The results establish finite repeated operations at stated fixture prices rather than full campaign profitability.

The [autonomous backhaul report](AutonomousBackhaulReport.md) verifies three actual return and onward-handling handoffs through the existing roaming and intermediate-port planners. A depot posted quote of 1.25 credits/kg gives both carriers enough margin at the fixture prices. With price-aware refills the autonomous chain records 14,346.05 credits over 202 days; explicit contracts record 14,888.36 credits over 194 days. Each service independently quotes its own funded transport.
