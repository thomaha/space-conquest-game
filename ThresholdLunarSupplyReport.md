# Threshold lunar supply audit

## Price-aware refueling update

Price-aware refills allow the low-buffer scenario to serve eight lunar departures while depot usable hydrolox remains above its reorder point but below a full 120,000 kg tank. No depot supply is needed in that case. The expensive-fuel control completes repeated lunar departures and aluminum backhaul while preserving safe returns. The detailed measurements below record the earlier refueling policy.


## Findings

Configurable stock targets and fuel delivery priority support repeated autonomous Earth–Moon trade with finite inventory and continuously active suppliers. The nominal fixture records all three 2,500 kg aluminum backhauls at low Earth orbit by day 47. A second run restores upstream delivery after a 70-day source interruption without resetting routes. Both run for 300 days with five active routes and periodic save/load checks.

These results validate the replenishment mechanism under the stated fixture conditions. They do not establish universal buffer sizes or uninterrupted departure readiness. Low thresholds can still exhaust usable fuel. Before fuel priority, the expensive-fuel fixture completed one backhaul; it now completes all three by day 99 through changed paid cargo choices. High fuel prices still constrain which shipments are viable.

## Example settings and capacity

| Hub and material | Reorder threshold kg | Target stock kg |
|---|---:|---:|
| Earth depot hydrolox mixture | 360,000 | 450,000 |
| Moon hydrolox mixture | 240,000 | 300,000 |
| Moon hydrogen gas | 120,000 | 160,000 |
| Moon refined uranium | 400 | 600 |
| Moon steel | 1,000 | 1,250 |
| Moon food matrix | 1,000 | 1,250 |

The UI configures individual materials. Split each hydrolox value into liquid hydrogen and liquid oxygen using the drive's mixture ratio; the table expresses their combined usable mixture mass. Usable mixture is limited by the scarcer ingredient rather than their summed inventory.

The fixture starts with the configured fuel buffers filled and 1,050,000 kg of hydrolox at the low-Earth source. An RP-1 supplier carries up to 30,000 kg per depot shipment. Two fission lunar suppliers each have 50,000 kg cargo capacity. The hydrolox trader has a 2,500 kg hold and a 120,000 kg main tank. A separate RP-1 handler carries returning aluminum onward to low Earth orbit and can also supply depot fuel.

Observed loaded delivery times are one day for low-Earth handling and depot replenishment and six days for lunar delivery. Suppliers also need return travel and their own fuel purchases. Buffer selection must cover outstanding atomic fuel purchases, competing consumers and this complete replenishment cycle. The settings are test examples; no campaign balance defaults change.

## Comparisons

| Scenario | Final Earth aluminum kg | Day recording all 7,500 kg at final hub | Source wait days | Depot deliveries after day 100 |
|---|---:|---:|---:|---:|
| 80% hydrolox reorder thresholds | 7,500 | 47 | 0 | 0 |
| 80% thresholds with source interruption | 7,500 | 47 | 69 | 4 |
| 20% hydrolox reorder thresholds | 7,500 | 47 | 0 | 0 |
| 80% thresholds with expensive fuel | 7,500 | 99 | 0 | 1 |

The interruption moves the remaining source hydrolox to an inaccessible tracked warehouse on day 30 and restores it on day 100. Existing buffers allow the aluminum handoffs to finish before restoration. Four subsequent depot deliveries demonstrate resumed supply rather than completion of a second aluminum production batch. Inventory is conserved through both transfers. The event table records stock at route events; the completion column is the first logged event with the full batch at the final hub.

Source wait counts include days when either source mixture ingredient is depleted, the supplier is loading and the depot has a request. The 70-day interruption produces 69 such waiting ticks because the supplier is not loading on every interruption tick. The finite source is not continuously replenished.

| Scenario | Minimum usable depot hydrolox kg | Minimum usable Moon hydrolox kg |
|---|---:|---:|
| 80% thresholds | 273,410 | 37,587 |
| 80% thresholds with source interruption | 229,055 | 37,587 |
| 20% thresholds | 0 | 37,547 |
| Expensive fuel | 229,055 | 155,316 |

Before fuel priority, the source interruption nearly exhausted usable depot fuel while substantial unmatched ingredient inventory remained. Prioritizing below-threshold fuel improves the observed minimum to 229,055 kg in the same fixture. This confirms recovery and better stock coverage but does not prove immediate readiness for every possible departure.

The low-threshold control completes the finite backhaul batch but later reaches zero usable depot hydrolox. It therefore demonstrates depleted reserves rather than slower completion of these three handoffs. Arrival-time previews find unavailable next-leg fuel in both nominal and low-threshold runs. These previews also include arrivals after the finite aluminum stock is exhausted and do not measure waiting time for a required commercial shipment.

The expensive-fuel control now completes three aluminum backhauls. Scarce fuel takes cargo space before competing small goods loads and the freighter later carries profitable mixed goods and fuel. The prior one-backhaul result was therefore a limitation of greedy cargo selection under these prices rather than proof that further service was intrinsically uneconomic. Available stock alone still does not establish profitable service.

Nominal liquid hydrogen and liquid oxygen prices are 0.0001, 0.0015 and 0.005 credits/kg at low Earth, the depot and the Moon respectively. The expensive control uses 0.005, 0.02 and 0.04. Nominal RP-1 kerosene is 0.0001 credits/kg while the expensive control uses 0.005. Fission hydrogen costs 0.005 at the depot and 0.04 at the Moon; uranium costs 1 and 3. Aluminum prices are 0.8 at the Moon, 1.25 at the depot and 3 at final Earth in nominal runs and 0.8, 3.5 and 8 in the expensive control. These fixed inputs isolate supply and economic limits; they are not a whole-chain profitability calculation.

## Verification and limits

Every tick checks that routes remain active, physical maneuvers succeed and no freight disappears. Configured materials never exceed target stock when actual inventory and committed inbound freight are combined. Opening and final inventory reconcile against physical fuel use and paid local goods consumption. Aluminum and total money are conserved. Save/load occurs every 30 days.

Local goods demand is an explicit paid purchase of up to 25 kg of each material per day. The fixture excludes initial inventory production and lift, capital costs, overhead and autonomous household or industrial demand. Sources are finite. Fission suppliers also replenish fuel used by their own return journeys, so continued supplier cycling does not by itself prove continuing external trade demand.

Supplier cargo selection now prioritizes the normalized fraction of configured below-threshold fuel deficits relieved by the shipment before ordinary profit ranking. Mixed baskets allocate scarce fuel first and use spare capacity for profitable goods. Fixed routes apply this within their configured destination while roaming suppliers also compare destinations. See [HubStockTargets.md](HubStockTargets.md) for the policy. Suppliers still purchase their own safe voyage fuel before freight loading. Ingredient priorities and finite buffers cannot guarantee every atomic fuel purchase or prevent starvation when source stock, funds or profitable travel are unavailable.

Run `mvn test` with JDK 27. `ThresholdLunarSupplyAuditTest` writes the four event reports under `engine/target/`: `threshold-lunar-supply-report.md`, `threshold-lunar-shortage-report.md`, `threshold-lunar-low-buffer-report.md` and `threshold-lunar-price-control-report.md`.

The follow-up [SuggestedLunarSupplyReport.md](SuggestedLunarSupplyReport.md) compares planner-generated targets with the same finite opening stocks. It measures actual completion ticks, usable fuel depletion and stationary supplier return readiness separately. Completing a finite backhaul batch does not certify maintained on-hand buffers or sustainable continuous demand.
