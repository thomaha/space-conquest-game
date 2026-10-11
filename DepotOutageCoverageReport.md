# Depot outage coverage

## Price-aware refueling update

With price-aware working refills, the reviewed 70-day depot allowance retains at least 165,239 kg usable hydrolox and records no depot stockout. No further depot delivery is needed after source restoration in this finite scenario. The detailed measurements below record the earlier refueling policy.


## Findings

Ingredient-level tracing explains the remaining depot shortage in the reviewed lunar workload. With two depot safety days, usable hydrolox is empty on observations 43 through 101 and recovers on day 102. The first 43 empty observations lack liquid hydrogen alone and the following 16 lack both ingredients. On day 43 the depot still holds approximately 98,033 kg of liquid oxygen.

Of those 59 empty observations, 58 have no accessible hydrolox ingredients at the upstream hub. On day 101 the source has been restored and paid hydrogen and oxygen cargo is already inbound. Both arrive on day 102. The upstream carriers are loading at the unavailable source when the depot first empties. This evidence does not establish a persistent dispatch failure after restoration.

Liquid oxygen is also used by the chemical suppliers. Oxygen beyond the quantity that can pair with available hydrogen is a mixture-coverage diagnostic, not necessarily wasted or unusable stock. The generated report separates on-hand usable mixture, usable inventory position with actual inbound cargo, material requests, upstream inventory and carrier manifests.

## Tested depot allowance

A seventh scenario keeps the same opening inventory, prices, five ships, paid local consumption, lunar settings and 70-day source outage. Only the depot hydrolox planning safety allowance rises from two to 70 days. Opening depot hydrolox remains 450,000 kg. Larger buffers must be assembled through paid physical deliveries before the outage begins on day 30.

The existing suggestion formula uses a 120,000 kg departure allowance, 8,571.43 kg/day reviewed demand and a four-day supplier cycle. With 70 depot safety days it produces the following settings:

| Material | Reorder threshold kg | Target stock kg |
|---|---:|---:|
| Liquid hydrogen | 128,228.57 | 148,628.57 |
| Liquid oxygen | 626,057.14 | 725,657.14 |
| Total hydrolox mixture | 754,285.71 | 874,285.71 |

These are fixture-specific planning inputs rather than campaign defaults or measured historical demand.

| Measurement | Two depot safety days | 70 depot safety days |
|---|---:|---:|
| Opening usable depot hydrolox kg | 450,000 | 450,000 |
| Usable depot hydrolox before outage kg | 153,410 | 689,359 |
| Minimum usable depot hydrolox kg over 300 days | 0 | 330,000 |
| Empty depot hydrolox observations | 59 | 0 |
| Depot observations below 120,000 kg refill allowance | 75 | 0 |
| Aluminum completion day | 104 | 44 |

At the end of day 30, another 30,000 kg of paid hydrogen is inbound in the larger-buffer scenario. On-hand usable mixture and inbound inventory position remain distinct. By day 90 usable depot hydrolox is approximately 547,183 kg. The reserve never needs to reach its full 874,286 kg target to cover this workload.

## Limits and next work

The larger buffer protects this single finite outage because the source has stock and carriers can prebuild before it closes. It does not certify coverage for an outage that starts immediately, repeated outages, higher traffic or inadequate upstream stock. The source eventually exhausts its remaining oxygen after restoration and some refill requests stay open. No ongoing production is modeled, so this test does not establish sustainable supply.

The evidence supports retaining the mixed and partial cargo dispatch behavior and reviewing planning demand and outage allowances together with actual upstream availability. The next useful addition is a player-facing coverage diagnostic that distinguishes missing ingredients, paid inbound cargo and inadequate prebuild stock without treating a stock target as guaranteed service.

## Verification

`HydroloxCoverageDiagnostics` reconciles empty-day counts with the existing audit and checks that usable mixture cannot exceed physical ingredient mass or its inventory position. Missing ingredients are reported as combinations so an empty hydrogen and oxygen pair is not assigned an arbitrary single limiter.

`SuggestedLunarSupplyAuditTest` now has seven scenarios. The new outage case requires the depot to build above its 450,000 kg opening stock through paid deliveries, remain nonempty and retain at least a 120,000 kg usable refill allowance. Existing fuel, goods, money, paid freight, electricity, cargo preservation, maneuver and save/load checks still apply. Run `mvn -pl engine -Dtest=SuggestedLunarSupplyAuditTest test` with JDK 27. Generated reports now include ingredient coverage tables and `engine/target/suggested-lunar-outage-covered-report.md` records the new scenario.
