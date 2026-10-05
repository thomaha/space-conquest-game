# Staged lunar trade report

## Scope

StagedLunarTradeAuditTest starts an empty catalog chemical freighter at a 500 km Earth station. It buys 120,000 kg of compatible main propellant and 500 kWh of battery charge, then funds departure, circularization and docking at a 2,000 km Earth depot. At the depot it purchases replacement propellant and a mixed shipment of 1,250 kg steel plus 1,250 kg food. The final destination is a station at 500 km Moon parking.

All three stations and their opening supplies are explicit test fixtures. Fuel stocks, stored grid electricity and market buyer funds are finite. Purchases use the existing physical stock and cash settlement paths. This audit does not grant ports or supplies to a generated campaign.

The generated scenario table is `engine/target/staged-lunar-trade-report.md`. Eighteen probes cover three chemical drives, 5%, 10% and 20% reserves and fuel prices of 1 and 0.01 credits/kg. Fuel prices are sensitivity inputs rather than new balance settings. The measured table deliberately tops the main tank back up at the depot; separate checks exercise the trader's existing automatic next-leg purchases, which may retain the remaining fuel without buying more when it is sufficient.

## Paid access and delivery

All eighteen ships reach the Earth depot in one simulation day while retaining their selected reserve. Fourteen then complete lunar docking and sell all cargo after six more days. Total elapsed time is seven simulation days, including phase waiting and the daily tick remainder.

| Drive | Depot access fuel kg | Lunar fuel kg | Main remaining kg | Supported reserves |
|---|---:|---:|---:|---|
| RP-1 chemical | 30,493.21 | 104,222.49 | 15,777.51 | 5% and 10% |
| Methalox chemical | 28,291.09 | 99,325.53 | 20,674.47 | 5% and 10% |
| Hydrolox chemical | 23,787.73 | 88,294.94 | 31,705.06 | 5%, 10% and 20% |

RP-1 and methalox reject the lunar basket at 20% reserves even after the depot top-up. Their depot climb still completes and their purchased fuel remains aboard. Rejected lunar baskets purchase no cargo and do not start a journey. Hydrolox retains about 7,705 kg above its 24,000 kg protected reserve at lunar arrival.

The empty climb establishes paid access from low orbit to the supported lunar departure orbit. Cargo is purchased at the depot, so this result does not establish a cargo-loaded climb or transport from Earth's surface. Station construction and the delivery of replenishment stock to either Earth port remain separate costs.

## Complete journey economics

The shipment costs 2,500 credits and sells for 6,000 credits against the Moon market's posted 3 credits/kg price and existing 80% wholesale share. Initial grid charging costs approximately 11.11 credits. The consumption result subtracts cargo, consumed propellant on both legs and initial charging. Cash change additionally pays for unused fuel retained aboard; retained fuel is inventory rather than a refund.

| Drive | Consumption result at 1 credit/kg fuel | Consumption result at 0.01 credits/kg fuel | Cash change at 0.01 credits/kg fuel |
|---|---:|---:|---:|
| RP-1 chemical | -131,226.81 | 2,141.73 | 1,983.96 |
| Methalox chemical | -124,127.73 | 2,212.72 | 2,005.98 |
| Hydrolox chemical | -108,593.79 | 2,368.06 | 2,051.01 |

These are supported-basket results under the stated fixtures. All accepted cases lose money at 1 credit/kg fuel. The cheap-fuel hydrolox result is about 238 credits below the earlier prepared-port lunar audit because the depot climb consumes additional fuel. Ship and port construction, wages, depreciation, replenishment, interest and taxes are excluded.

With the explicit top-up before loading, the route ledger records only the 3,500-credit cargo margin. When the existing trader purchases a top-up during loading, its route ledger records that purchase as operating expenditure. Neither ledger captures the complete staged journey by itself.

## Verification and limits

The audit compares parking forecasts with actual fuel and battery stores, reloads the active parking order and reloads the lunar flight after its first daily tick. Reload preserves fleet budgets, reserve policy, cargo manifest, markets, treasury and grid storage. Daily checks reject failed maneuvers and essential electrical deficits. Sales cannot occur before lunar docking and final cargo and manifests are exhausted. No return journey is required after selling.

A separate failure case rejects refueling from a remote depot, then reaches that depot with a paid partial tank. Depleted depot fuel prevents a top-up and the lunar basket; the ship remains docked with its existing fuel, cash and unloaded cargo preserved.

Run `mvn test` with JDK 27 to reproduce the audit. Existing circular coplanar geometry, impulse screens and approximate station rendezvous still apply.

## Automatic intermediate stops

When no direct profitable shipment is available, an empty single-ship roaming trader can now evaluate up to eight operational parking ports on its current body. Candidates are ordered by altitude difference then port ID. This first implementation supports one stop with no passengers, carried cargo or scheduled supply rendezvous. It limits depot access to thirty scheduled days and does not search surface access, other bodies or chains of intermediate ports.

IntermediateTradePlanner projects actual funded parking arrival, remaining fuel and battery stores and the arrival turn before evaluating the depot's onward trade. Expected profit includes consumed access fuel even when the main tank was filled earlier. A profitable depot trade can therefore be rejected when reaching it would erase the margin. The combined result ranks accepted stops by expected profit per scheduled day; direct profitable shipments retain priority.

Only current-port supply purchases and the first departure commit on the trade tick. The saved `REPOSITIONING` phase retains the actual origin and chosen depot without cargo or claimed delivery. Depot cargo, fuel and buyer cash are only quoted during lookahead. On funded docking, the depot becomes the new origin and the trader rechecks current prices, stock, funds and physical readiness before loading. Changed markets cause waiting. Cancelling the route allows the already committed parking flight to finish, then prevents an onward purchase. Failed parking burns retain the interrupted phase and cannot award depot access.

Seven additional lifecycle tests cover automatic low Earth orbit to depot to Moon delivery, reload of both active legs, rejection of expensive access with prepaid fuel, finite stock and funds, changed markets, cancellation, direct-trade preference, deterministic depot selection, loaded-ship exclusion and failed departure. The hydrolox fixture completes its seven-day mixed shipment and retains at least 24,000 kg of protected main fuel. This establishes automatic use of existing finite-stock fixture ports; it does not build or replenish them.

The full Maven suite passes 790 tests with no failures, errors or skipped tests. The diff whitespace check also passes.
