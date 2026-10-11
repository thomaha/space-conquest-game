# Suggested lunar fuel buffers audit

## Price-aware refueling update

The reviewed 70-day depot allowance now preserves at least 165,239 kg usable hydrolox through the source outage and records no depot stockout. Stock remains sufficient without additional depot deliveries after restoration. Restoration does not require a purchase when the inventory position already covers demand. The detailed measurements below record the earlier refueling policy.


This report records the dispatcher before alternative mixed-fuel baskets were introduced. Current behavior and the comparison with this baseline are in [MixedCargoSupplyReport.md](MixedCargoSupplyReport.md). The generated audit files under `engine/target/` now describe the current dispatcher.

## Findings

Planner-generated targets complete the finite 7,500 kg aluminum backhaul batch with the same opening inventory as the manually tuned control. They do not guarantee maintained on-hand fuel. Every tested policy reaches zero lunar hydrogen, including the manual control. A 70-day source outage also exhausts usable depot hydrolox for 29 daily observations before upstream deliveries resume.

The formula is useful for reviewing demand and delay coverage but this audit does not justify treating its output as a certified service level. Larger safety allowances improve depot coverage in the dispatch-delay case without eliminating lunar hydrogen depletion. No production formula, campaign defaults or automatic application behavior change in this audit.

## Comparable scenarios

Each scenario runs for 300 days with the same five ships, prices, source stocks and paid local goods consumption. Opening depot hydrolox remains 450,000 kg and lunar hydrolox remains 300,000 kg. Lunar hydrogen starts at 160,000 kg and uranium at 600 kg. Suggestions change stock settings only. Larger targets must fill through paid physical deliveries; smaller targets do not discard excess opening stock.

| Policy | Aluminum completion day | Minimum usable depot hydrolox kg | Days depot hydrolox was empty | Days lunar hydrogen was empty |
|---|---:|---:|---:|---:|
| Manual control | 47 | 273,410 | 0 | 248 |
| Snapshot demand with manual missing timing | 47 | 107,350 | 0 | 228 |
| Reviewed demand, two safety days | 46 | 109,055 | 0 | 248 |
| Reviewed demand with 30-day supplier dispatch pause | 46 | 109,055 | 0 | 228 |
| Same pause with 30 safety days | 46 | 330,000 | 0 | 228 |
| Reviewed demand with 70-day source outage | 104 | Less than 0.001 | 29 | 170 |

Empty means less than 0.0001 kg of usable fuel at the end of a daily tick. Hydrolox availability is limited by its scarcer ingredient. Completion is checked every tick when the final low-Earth hub holds all 7,500 kg of aluminum. It is distinct from a logged route event in the earlier [ThresholdLunarSupplyReport.md](ThresholdLunarSupplyReport.md).

All lunar hydrolox minima remain roughly 37,000–38,000 kg, substantially below the 120,000 kg full-refill planning allowance. This does not establish that a real departure needed a full tank at that moment. Likewise, an empty market does not by itself prove a stranded supplier: working tanks and paid inbound cargo are separate inventory. The stationary supplier return checks find no failed next-leg resupply preview in these runs. They do not cover every possible consumer, unselected trade or shortage during a tick.

## Planning inputs

The opening snapshot has zero modeled fuel demand and no plannable supplier cycle because carriers have not yet bought main propellant. The snapshot case therefore retains zero demand and explicitly supplies the missing turnaround inputs: four days for depot supply and fourteen days for lunar supply. It is not a completely automatic recommendation.

The reviewed cases budget one 120,000 kg hydrolox refill per fourteen days at each hub. Lunar hydrogen and uranium budgets cover two largest associated-fleet refill allowances per fourteen days. These are declared workload assumptions rather than measurements inferred from later audit results. The planner supplies the departure allowances from the opening snapshot: 120,000 kg hydrolox, 45,000 kg hydrogen and 145 kg uranium.

| Hub and fuel | Reviewed daily demand kg | Cycle days | Reorder with two safety days kg | Target with two safety days kg |
|---|---:|---:|---:|---:|
| Depot hydrolox mixture | 8,571.43 | 4 | 171,428.57 | 291,428.57 |
| Moon hydrolox mixture | 8,571.43 | 14 | 257,142.86 | 377,142.86 |
| Moon hydrogen gas | 6,428.57 | 14 | 147,857.14 | 237,857.14 |
| Moon refined uranium | 20.71 | 14 | 476.43 | 766.43 |

The calculation uses `reorder = departure + demand * (cycle + safety)` and adds `max(departure, demand * cycle)` for the target. Hydrolox totals are split into 17% liquid hydrogen and 83% liquid oxygen. The zero-demand case requests twice the departure allowance as its target and gets no additional coverage from safety days. With 30 safety days, the depot hydrolox target rises to 531,428.57 kg and lunar hydrolox to 617,142.86 kg; the initial stock remains unchanged.

## Stress conditions and interpretation

The dispatch-delay scenarios disable new loads on the depot supplier and both lunar suppliers for days 10–39. Existing flights, electricity use and committed deliveries continue. The routes reactivate on day 40 with no cargo or journey resets. The roaming Earth handler remains active and can replenish the depot. This is a supplier dispatch outage rather than a slower physical flight or a blockade of every supply path.

The source-outage scenario moves the remaining low-Earth hydrolox into a tracked inaccessible reserve on day 30 and restores it on day 100. No material is created or destroyed by the transfer. Five depot deliveries occur after restoration and the final aluminum batch completes on day 104. Two safety days are not intended to insure a 70-day source outage; recovery does not establish uninterrupted coverage.

Finishing the aluminum batch proves only the three finite handoffs. It does not establish sustainable supply for continuous new production. Lunar hydrogen depletion in both manual and suggested cases also means that target arithmetic alone cannot establish maintained on-hand reserves. Supplier delivery capacity, competing cargo selection, committed inbound quantities and the suppliers' own purchases all interact with procurement.

The supplier service coverage measurements below separate actual deliveries, withdrawals and committed inbound cargo. They expose missed profitable hydrogen opportunities and guide the next dispatch comparison before changing the formula or raising defaults.

Final route diagnostics also distinguish policies that leave both lunar suppliers loading at the depot from policies whose suppliers remain on funded return flights. No common stalled-return cause is established. Fixed-origin market selection can change destinations before basket priority runs, so a route's configured destination alone should not be interpreted as a guaranteed fuel service commitment. The tested final destinations remain the lunar hub.

## Supplier throughput investigation

The audit now observes hub inventory immediately before and after logistics, separately from daily power and movement. It records paid freight loaded, cargo delivered, missing freight written off, hub purchases for working supplies, actual committed hydrogen inbound and supplier phase days. Freight loaded minus delivered and written off must equal the final paid manifest. Physical cargo limits delivered mass and the sum must match each route's recorded delivery increment. Uranium resale cargo can also be consumed as drive feed and written off; it must not be counted as a hub delivery. Hub working-supply purchases are the observed stock decrease plus delivered cargo minus freight loaded; the ledger rejects unexplained stock increases.

In the reviewed two-day safety case, one tanker delivers approximately 997,666 kg of hydrogen over 300 days. Lunar working-supply purchases total approximately 1,157,666 kg. The 160,000 kg opening stock supplies the difference and lunar hydrogen is empty on 248 daily observations. This is a throughput shortfall despite continuing deliveries. Each lunar tanker spends 271 observations in transit and 29 in the loading phase. Loading observations include the tick that completes a return and do not by themselves establish a blocked dispatch.

Paid alternatives expose a selection gap before hydrogen empties. Around day 34, hydrogen has roughly 90,000 kg on hand, no inbound cargo and an outstanding request of roughly 148,000 kg. The source holds more than 9.8 million kg of gas and both the carrier owner and receiving hub have roughly one million credits available. An isolated hydrogen shipment of roughly 49,883 kg is profitable and ready to depart, yet the greedy basket returns no profitable selection and actual suppliers depart with only 50 or 325 kg of ordinary goods. Around day 47 the same pattern occurs with roughly 38,000 kg of gas remaining. Exact goods and stock values can vary slightly with market iteration order.

The basket ranks normalized ingredient shortages before margins. In the day-34 snapshot, both hydrolox ingredient urgencies are about 0.515 versus 0.391 for hydrogen gas; by day 47 these are about 0.855 versus 0.742. The greedy basket fills capacity in that order, then searches only scaled versions of that composition. A rejected composition does not cause it to try another urgent fuel. The legacy single-good selection can consequently win with a small goods load while a feasible hydrogen shipment remains unexamined. A separate 25,000 kg hydrogen preview has a 675-credit gross spread and approximately 204 credits of consumed working-fuel cost for a six-day outward leg. These are next-leg quotes, not a full round-trip profit guarantee.

The next dispatch change should evaluate alternative profitable urgent-fuel candidates when the first greedy composition fails, then compare their actual shortage relief with other funded shipments. Increasing target values alone does not address this selection gap. These observations do not prove that the revised dispatch policy would maintain every buffer; that needs a new comparison with the same opening stocks and competing paid cargo.

Dispatch alternatives are evaluated on the pre-logistics snapshot without changing live state. Earlier routes in the same tick may alter later suppliers' actual choices, so alternatives are evidence of available opportunities at the sampled snapshot rather than an exact reconstruction of every route decision. No common stranded-return cause or source and funding shortage is established for these sampled normal-policy opportunities.

## Verification

`SuggestedLunarSupplyAuditTest` checks six scenarios using the existing paid physical tick. It reconciles every tracked fuel against consumption, goods against paid local purchases, aluminum inventory and total money. Electricity remains funded, cargo is preserved and maneuvers succeed. Save/load every 30 days preserves settings and commitments. Inbound cargo cannot exceed the remaining target even when opening stock is above a newly lowered target.

Run `mvn test` with JDK 27. Detailed settings, coverage counts, events, supplier ledgers, sampled paid alternatives and final route cargo diagnostics are written under `engine/target/` as `suggested-lunar-manual-report.md`, `suggested-lunar-snapshot-report.md`, `suggested-lunar-reviewed-report.md`, `suggested-lunar-delay-report.md`, `suggested-lunar-covered-delay-report.md` and `suggested-lunar-shortage-report.md`.
