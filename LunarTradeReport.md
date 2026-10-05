# Paid lunar trade report

## Scope

LunarTradeAuditTest runs 26 deterministic Earth–Moon shipment scenarios using catalog chemical freighters. Ships begin empty and purchase real main propellant, grid charging and mixed cargo at their source port. Ordinary stations are fixture-built at 2,000 km Earth parking and 500 km Moon parking. Six rejection probes use 500 km Earth parking. These fixtures do not grant stations or stock to a campaign.

The detailed generated report is `engine/target/lunar-trade-report.md`. Source markets have finite physical supplies and destination buyers have finite cash. Each accepted shipment buys 1,250 kg steel and 1,250 kg food at 1 credit/kg. The destination posts 3 credits/kg and the existing 80% wholesale share produces 6,000 credits of sales. Reserve policies protect 5%, 10% or 20% of main-tank capacity.

Fuel prices of 1 and 0.01 credits/kg are sensitivity inputs. They do not change catalog balance. Commissioning charge pays a separate port operator and withdraws stored grid energy. Solar panels supply auxiliary electricity during travel while chemical fuel supplies thrust.

## Physical results

Sixteen of 26 scenarios complete funded docking and sell their complete mixed shipment. All accepted journeys in this sample take six simulation days including the actual phase wait, coast and approach.

| Drive | Supported reserves at the ordinary ports | Main consumed kg | Main remaining kg |
|---|---|---:|---:|
| RP-1 chemical | 5% and 10% | 104,222 | 15,778 |
| Methalox chemical | 5% and 10% | 99,326 | 20,674 |
| Hydrolox chemical | 5%, 10% and 20% | 88,295 | 31,705 |

Both directions have the same measured consumption in this circular coplanar fixture. The day-365 hydrolox departure changes phase waiting but still arrives on the sixth daily tick. Quotes match actual maneuver and electrical consumption. No cargo is sold before docking and the selected reserve survives arrival.

The hydrolox 20% case retains about 7,705 kg above its 24,000 kg protected reserve. Extra carried fuel remains usable insurance; the planner does not buy only the absolute minimum or spend excess fuel unnecessarily.

All six 500 km Earth cases fail the provisional 10% parking-period impulse screen. RP-1 and methalox also reject the ordinary-port 20% cases for insufficient usable main propellant. Readiness now reports the actual orbital blocker instead of describing every rejection as a propellant or reactor-feed shortage. Raising the departure orbit needs a separately funded journey and is not included in these trade rows.

## Economics and autonomous decisions

At 1 credit/kg fuel every accepted shipment loses money when consumed fuel replacement cost is counted. At 0.01 credit/kg the hydrolox example earns approximately 2,606 credits after cargo, consumed propellant and initial charging costs. Its cash change is about 2,289 credits because the unused purchased fuel remains aboard as inventory.

The route ledger records 3,500 credits of realized cargo margin. Main fuel and commissioning charge were bought before route assignment, so that ledger is not a complete voyage result. Ship and station construction, wages, depreciation, replenishment, interest, tariffs and exact shadows are outside this audit.

For both directions, RoamingTradePlanner rejects the tested fuel loss even when the tanks are already full and selects the profitable two-good basket without spending live cash during candidate search. The trader funds only its selected next port. After selling, it can wait at the destination without a mandatory return order or assumed future supplies.

## Partial sales and lunar resupply

A separate lifecycle check limits the lunar buyer's cash so only part of the mixed manifest sells. The remaining 625 kg and its 625-credit cost basis survive save/load with the actual ship cargo and parking state. The fleet stays at port until the buyer can pay for the remaining goods. Completing the sale clears the manifest without creating a return journey.

Lunar-port refueling then withdraws actual compatible market stock and charges the owner for the purchased quantity. It fills available tank capacity without moving the ship. Fuel replenishment and a future destination remain separate decisions.

The full Maven suite passes 781 tests with no failures, errors or skipped tests. The diff whitespace check also passes.

## Remaining work

The [staged lunar trade report](StagedLunarTradeReport.md) now verifies paid access from 500 km Earth parking to a 2,000 km depot, actual depot fuel purchases and cargo loading before lunar delivery. Fourteen of eighteen probes complete the seven-day combined journey. Empty roaming traders can select one intermediate parking port on their current body when the combined projected trade is profitable, then recheck markets on arrival. Cargo-loaded access and broader intermediate-port searches remain future work. Exact station rendezvous, finite-duration gravity integration, ballistic rescue and transfers between unrelated moons retain their documented provisional limits. See [LunarTransfers.md](LunarTransfers.md).
