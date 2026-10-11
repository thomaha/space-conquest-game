# Commercial hub stock targets

Supplier priority measures net public fuel added after protected return-fuel purchases, including withdrawals of scarce ingredients outside the freight manifest. A profitable full load that consumes scarce destination stock is compared with three smaller protected alternatives. Mixed and partial shipments remain valid. With price-aware working refills, the measured steady run serves 11,650 of 12,000 kg requested goods and records no customer return waits. Upstream depot shortages remain; thresholds do not certify sustained supplier throughput.

## Configuration

Cheap full refills require enough public stock for the fleet's working fuel plus each configured reorder buffer. Expensive or scarce ports use bounded complete-leg sizing with a small safety margin. Paid private return allocations use the same policy; they remain outside public supply and count toward physical hub storage. The relative cheap-price comparison uses compatible refill costs at stocked operational ports, with a 10% allowance above the lowest offer.

The commercial hub view offers a material selector, reorder threshold and target stock for hubs in the player's controlled systems. Quantities are kilograms of individual materials. Fuel mixtures need a separate setting for each ingredient in the appropriate ratio. Settings are queued through `SetHubStockTargetCommand` and revalidated on the next simulation tick.

The material selector includes goods listed at other commercial hubs, allowing a depot to request fuel before stocking it or hosting a ship that needs it. The command creates a zero-stock market entry when needed. Setting both values to zero removes the override and restores the existing automatic stockpile policy.

Thresholds must be finite and nonnegative. A target must be at least its threshold, and the sum of configured material targets cannot exceed hub storage capacity. Use a positive threshold to trigger replenishment before stock reaches zero. Fuel planning suggestions are available below the manual controls and include measured withdrawal history.

## Suggested fuel buffers

Choose a recognized fuel mixture or reactor material under Suggested fuel buffers. Estimate inputs supplies editable values from the injected game state. Preview suggestion shows each ingredient's reorder and target quantities. Queue suggested targets submits the entire mixture as one command for the next tick. Editing a planning input or choosing another fuel invalidates the review and requires a new preview. Manual per-material settings remain available.

| Planning input | Estimate and meaning |
|---|---|
| Departure kg | Largest full refill among fleets at the hub or assigned to active routes touching it, including compatible propulsion, electrical fuel and drive reactor feed. This is a conservative atomic purchase allowance rather than a planned voyage burn. |
| Demand kg/day | The higher of modeled market demand and rolling measured withdrawals, converted to mixture kilograms using the largest ingredient rate divided by its mixture fraction. Withdrawals include local use and outbound freight. Review or replace the estimate, especially after stockouts or traffic changes. |
| Supplier cycle days | Longest available local outward and return preview plus two handling days among up to eight incoming active routes. Both legs use the carrier's current equipment, cargo and fuel independently at the current orbital phase. Missing plans and interstellar suppliers require manual input. |
| Safety days | Editable delay allowance, initially two days. This is a provisional planning choice. |

The formula is `reorder = departure + daily demand * (supplier cycle + safety)`. The target adds `max(departure, daily demand * supplier cycle)` to the reorder quantity. Both totals are split into ingredient kilograms using catalog mixture fractions. The whole suggestion must fit hub storage alongside retained targets; quantities are never silently reduced to fit.

For example, a 120,000 kg departure allowance, 5,000 kg/day demand, 14-day cycle and two safety days suggest a 200,000 kg reorder threshold and 320,000 kg target for hydrolox. The ingredient thresholds are 34,000 kg liquid hydrogen and 166,000 kg liquid oxygen; targets are 54,400 kg and 265,600 kg respectively.

Travel estimates run off the JavaFX application thread and stale responses cannot overwrite edited inputs. Estimates do not reserve inventory or funds, authorize travel or certify supplier capacity. Both positive supplier cycle time and positive departure allowance or demand are required before preview. Source availability, repeated delivery throughput, cash, safe travel and changes in orbital phase still affect actual service. A zero demand estimate does not prove there will be no future consumption.

`SetHubStockTargetsCommand` validates all reviewed ingredients and combined replacement storage against live tick state, then updates them atomically. If ownership, known materials or remaining storage invalidate the group before execution, none of its settings apply. New local market entries begin with zero stock. Existing save version 44 already stores the resulting targets; planning inputs and estimates are cosmetic and are not saved.

## Fuel coverage display

Each commercial hub shows a read-only fuel coverage selector above its stock settings. The selected fuel is retained when the view refreshes on a simulation tick. Fuels are offered when at least one ingredient is listed or configured at the hub; missing companion ingredients remain visible as zero stock. The initial selection favors fuels with more configured ingredients.

Usable fuel on hand is the smallest ingredient stock divided by its catalog mixture fraction. Usable fuel with paid inbound applies the same calculation to stock plus physical cargo already assigned to this hub. These totals are calculated separately, so hydrogen and oxygen aboard different incoming ships are not treated as currently purchasable fuel. Each ingredient shows its on-hand stock, paid inbound, reorder threshold, target, outstanding request and known stock at other accessible hubs. Automatic stockpile policies are identified without inventing a reorder threshold.

Warnings identify absent or limiting ingredients, on-hand stock below reorder, missing source stock and supplier routes that are inactive or lack a cargo ship or source port. Directed routes are separated from other potential roaming routes. Roaming service is advisory and can choose another hub. Paid inbound from a paused route still counts because already committed cargo can continue to arrive.

Source stock excludes hubs with no physical trading site or a nonoperational station. It does not reserve stock, check a funded voyage or certify repeated throughput. Route counts use active assignments with an owned cargo ship; prices, cash, cargo capacity and travel readiness still decide actual dispatch. Coverage runs off the JavaFX thread, returns immutable records and discards outdated responses after a fuel selection changes. It does not queue commands or change game state.

## Measured demand history

Each hub records gross withdrawals and deliveries per material, including ship fuel purchases, launch fuel, freight, construction, industrial transactions and household purchases. Equal withdrawals and deliveries on the same day remain separate quantities. Opening stock and changes to prices or target settings do not count as deliveries. The deprecated list-only logistics API has no clock and does not record movements.

Fuel coverage shows rolling withdrawal and delivery rates, observed days and stockout days for each ingredient. Rates use observed days in the last 30 completed simulation days. The current day is excluded, quiet observed days count as zero and missing observations are not invented. Storage is bounded to that window plus the current day. Newly loaded legacy hubs need time to accumulate observations.

A stockout day means stock was zero at a daily observation or transaction boundary, even if replenishment arrived later that day. It is a warning that observed withdrawals may understate demand, not a measurement of lost requests or hours without fuel. Buffer estimates retain the modeled demand floor and display stockout warnings. Players still review editable inputs and preview targets before queuing them; history never changes targets automatically.

## Supplier behavior

Inventory position is current market stock plus paid cargo committed to that destination. When this position falls strictly below the reorder threshold, the hub requests replenishment toward its target. This request persists across ticks and saves until actual stock reaches the target, allowing several small deliveries to complete one refill.

For example, with 9 kg on hand, a 10 kg reorder threshold and a 40 kg target, a 20 kg supplier loads before any consumer uses the stock. Its committed cargo reduces the remaining request to 11 kg. After that cargo arrives, the persistent request still permits the remaining 11 kg even though stock now exceeds the reorder threshold.

| Condition | Behavior |
|---|---|
| Inventory position equals or exceeds threshold without an active refill | Wait |
| Inventory position falls below threshold | Request enough to reach target |
| A partial delivery raises stock above threshold | Continue the existing refill toward target |
| Committed cargo covers the remaining target | Wait for delivery |
| Actual stock reaches target | End the refill request |
| Later consumption drops inventory below threshold | Start another refill |

Fixed-origin and roaming traders use these requests instead of the configured material's daily demand cap. Roaming suppliers retain their paid voyage profit and physical readiness checks. Settings create demand without creating stock, cash, ships or supplier routes; an assigned active carrier, available source stock and funded safe travel are still required. Route shipment caps, source reserves and destination caps continue to apply.

### Fuel delivery priority

Suppliers give advance priority to recognized propulsion and electrical fuels, oxidizers and reactor feed with explicit stock targets when current stock plus committed inbound cargo falls below the reorder threshold. Mixed loads allocate these scarce fuels before higher-margin ordinary goods, then use remaining capacity for profitable freight. Fixed-origin suppliers apply this within the destination selected by their existing market-choice logic; their configured destination is not a strict service commitment. Roaming suppliers also compare fuel relief across destinations.

Roaming selection ranks the fraction of configured fuel threshold deficits covered by the actual shipment before destination refill availability and expected profit per day. Each material's contribution is capped at its outstanding deficit. Positive whole-voyage profit, buyer and supplier funds, source reserves, storage and safe travel remain required. Priority does not subsidize a loss-making trip. Inbound deliveries can remove urgency before they arrive, preventing duplicate priority shipments. Once the threshold is covered, an unfinished refill toward target continues with ordinary profit ranking.

Fuel recognition follows the propulsion and electrical catalogs. Materials without explicit targets retain normal ranking. Zero thresholds never receive advance priority. Separate mixture ingredient settings remain necessary; this policy ranks ingredient shortages and does not guarantee that every atomic mixture purchase can be funded immediately. Supplier return-fuel purchases still occur before freight loading and remain protected by the existing travel gates.

## Commitments and persistence

Sequential route processing exposes each selected loaded shipment immediately to later suppliers in the same tick. Committed inbound cargo reduces material requests and available shared storage. Its expected wholesale value also reduces the buyer cash available for additional dispatches. This is a planning allowance rather than cash escrow: actual sales still recheck physical storage, prices and funds on arrival.

The hub purchases an arriving trader's committed freight into its own inventory. Current consumer demand, replenishment targets and route material stock caps do not cap that arrival purchase. They control new dispatches. A target reduction during transit therefore does not leave paid cargo waiting aboard a trader for the market to consume existing stock. Mixed loads and tiny lots can sell completely and the route clears its manifest before returning or choosing its next trade. Physical storage, other committed cargo and actual buyer funds remain authoritative limits; purchases do not create space, stock or credits.

Inbound quantities use the smaller of a route's manifest and its carrier's actual cargo. Lost cargo does not reserve inventory. Cancelling a loaded route still counts its cargo because the committed shipment continues to delivery. Empty repositioning and unselected previews reserve nothing.

Save version 45 retains immutable per-hub material settings, refill intent and daily stock history. Older hubs without settings retain automatic stockpile behavior and missing history starts empty. Existing hub-copy paths preserve these records through pricing, demand refresh, fuel purchases, industry, construction and household transactions.

## Verification

`HubReplenishmentTest` covers proactive fuel shipping with no waiting consumer, threshold boundaries, several loads per refill, another refill after consumption, competing suppliers in one tick, cash shortage recovery, inbound storage and cash allowances, lost cargo, cancellation and save/load. `HubStockTargetCommandTest` covers authorization, invalid quantities, shared target capacity and materials not yet stocked locally. `HubStockTargetCardTest` checks queued UI edits without snapshot mutation and hides controls for foreign hubs.

Run `mvn test` with JDK 27.

`HubStockHistoryTest` verifies gross same-day flows, changing traffic, a bounded rolling window, quiet and missing days, stockout recovery, mixture estimates, paid fuel purchases, physical freight, authoritative ticks and save compatibility. `HubFuelCoverageCardTest` verifies displayed rates and stockout warnings without changing the snapshot.

`HubBulkPurchaseTest` verifies full mixed-load purchases above current targets and route stock caps, zero consumer demand, legacy automatic stock policy, cancelled committed shipments, target reductions in flight and physically lost cargo. Wholesale payment and delivery history remain reconciled.

`MeasuredLunarSupplyAuditTest` applies suggestions after sixty days of paid traffic and compares growth, supplier dispatch pauses and a larger safety allowance over five 240-day runs. History reconciles correctly and full-load hub purchases clear the previously unsold freight. Paid destination fuel reservations now eliminate customer return waits in all five scenarios; unfunded new trips wait at their source. Ingredient imbalance and demand suppressed by blocked departures remain material limits. See [MeasuredLunarSupplyReport.md](MeasuredLunarSupplyReport.md).

`TradeFuelPriorityTest` checks scarce fuel against higher-margin goods for fixed and roaming suppliers, spare cargo capacity, restored thresholds, refill continuation, committed inbound cargo, unprofitable fuel and rejection when funds are unavailable.

`HubFuelStockSuggestionsTest` checks mixture ratios, cycle and delay coverage, full-fleet allowances, missing route estimates, invalid inputs and overflow. `HubStockTargetsCommandTest` verifies atomic replacement and live rejection without partial edits. The hub card tests verify review, queued mixture edits, storage rejection, invalidation after editing and asynchronous estimates.

The 300-day Earth–Moon integration audit keeps five supplier and trading routes active, checks committed inbound quantities and recovers after a 70-day source interruption. Low-threshold and expensive-fuel controls distinguish stock coverage from commercial viability. Example settings and limitations are recorded in [ThresholdLunarSupplyReport.md](ThresholdLunarSupplyReport.md).

The [suggested-buffer audit](SuggestedLunarSupplyReport.md) applies actual planner outputs with identical opening inventory. Six scenarios compare manual targets, snapshot demand, reviewed demand, a supplier dispatch pause and a source outage. They complete the finite backhaul batch but do not maintain every fuel reserve, so suggestions remain planning estimates rather than guarantees against stockouts.
