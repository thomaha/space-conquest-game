# Partial mixed cargo supply

## Dispatch behavior

When the first greedy basket has no profitable safe departure, the planner now tries alternative urgent fuels with ordinary goods for the same receiving hub. Each alternative seeds up to half the available capacity with one urgent fuel, adds other requested goods and offers remaining capacity to that fuel. It can carry less than the seed allowance or depart with unused space. The half-capacity seed is a search candidate, not a minimum load or a shipment requirement.

Up to eight urgent fuels are considered. Every candidate is bought and evaluated as one shared loaded voyage using the existing source reserves, destination requests, inbound commitments, storage, cash, launch, propulsion and electricity checks. Failed previews do not spend live funds. Existing bounded quantity reductions allow partial loads. A fuel-only alternative remains available if its mixed candidate fails. Successful alternatives are compared by actual threshold-deficit relief and then profit per travel day. An already viable first basket retains its existing selection behavior.

This is a bounded heuristic rather than a complete optimization over all quantities and mixtures. It does not require filling a ship or filling any material's request. Small ordinary goods loads can travel with tens of tonnes of fuel and multiple fuels can share the hold.

## Lunar comparison

The same six 300-day scenarios retain their finite opening inventory, prices, ships, paid local consumption, periodic saves and stated outages. Only dispatch candidate selection changes. The prior dispatcher measurements are retained in [SuggestedLunarSupplyReport.md](SuggestedLunarSupplyReport.md).

| Policy | Previous completion day | Revised completion day | Previous lunar hydrogen empty days | Revised lunar hydrogen empty days | Revised minimum lunar hydrogen kg |
|---|---:|---:|---:|---:|---:|
| Manual control | 47 | 44 | 248 | 0 | 7,806 |
| Snapshot demand with manual missing timing | 47 | 47 | 228 | 48 | Less than 0.0001 |
| Reviewed demand, two safety days | 46 | 44 | 248 | 0 | 59,137 |
| Reviewed demand with supplier dispatch pause | 46 | 44 | 228 | 0 | 51,846 |
| Same pause with 30 safety days | 46 | 44 | 228 | 0 | 93,383 |
| Reviewed demand with source outage | 104 | 104 | 170 | 0 | 41,171 |

These measurements describe one finite workload and are not service guarantees. Market iteration can change some lots and minima slightly between runs. All reviewed scenarios now require observed mixed fuel and ordinary goods departures rather than requiring the former hydrogen depletion. Physical fuel, goods, aluminum, money, paid delivery manifests and written-off freight remain reconciled.

In the reviewed case a day-34 supplier carries approximately 35,416 kg hydrogen gas, 14,218 kg liquid hydrogen, 125 kg steel and 125 kg food. The other supplier also departs with hydrogen and partial goods. These are actual paid manifests, not independent nominal capacity calculations. The small goods quantities reflect the bounded loaded-voyage sizing rather than a full-load rule.

Lunar hydrolox still falls below the largest full-refill allowance. The reviewed source-outage case now has 59 depot hydrolox empty observations versus 29 previously, despite still completing the aluminum batch on day 104. Extra downstream service changes upstream withdrawals; the dispatch improvement does not solve every buffer shortage. The snapshot case retains zero modeled demand and still reaches empty lunar hydrogen on 48 observations.

The subsequent [DepotOutageCoverageReport.md](DepotOutageCoverageReport.md) traces those 59 depot observations by ingredient and adds a seventh scenario. With the same opening stock and a declared 70-day depot safety allowance, paid prebuild avoids depot depletion and the aluminum batch completes on day 44. The original six-scenario comparison above retains its existing settings.

## Verification

`TradeFuelPriorityTest` covers an initially rejected greedy basket that finds another fuel together with a small goods load and a mixed 8 kg departure with a 20 kg route limit. Existing coverage retains priority, paid inbound, funding and unsafe-travel checks. `SuggestedLunarSupplyAuditTest` records actual mixed departures across reviewed policies and preserves its physical accounting checks. The reviewed case records 17 mixed fuel and ordinary goods departures. `mvn test` with JDK 27 passes all 844 tests with no failures, errors or skipped tests.
