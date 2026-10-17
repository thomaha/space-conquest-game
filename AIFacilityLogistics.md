# AI facility logistics

Empire AI can replenish missing recipe inputs and generation fuel with public or eligible affiliated corporate cargo ships. It observes a persistent shortage, previews a real paid shipment and submits a command that rechecks the current state before dispatch. Companies use their own funds and require commercial viability. Actual production still requires paid staff, operating cash and electricity.

## Need and shipment selection

FacilitySupplyNeeds reads supported public and owned corporate facilities in controlled systems. Requirements use the application recipe, development modifiers and plant fuel definitions. Prior paid workers and trained idle workers bound potential staffing, with current residents and operational station status also required. Locked applications, unsupported manufacturing complexity and dry-body surface water treatment cannot create requests. Generation fuel takes priority over recipe inputs. Hive Mind economies are excluded from this monetary planner.

Existing hub stock targets remain authoritative. Otherwise logistics_policy.json supplies the reorder and target days. Actual inbound paid cargo counts toward the stock position, including a shipment whose route is inactive. Saved repositioning claims also count for AI planning but remain distinct from paid inbound cargo. This prevents another AI request from claiming the same need before loading.

The planner considers idle empire-owned single-ship cargo fleets at controlled hubs. It can dispatch from the current surplus hub or reposition to another controlled source. It preserves local demand reserves, explicit source targets and other saved source claims, then checks cargo capacity, source inventory, destination buying funds and physically funded travel. The ordinary LogisticsProcessor preview also secures the return with carried stores or a paid destination fuel reservation. Failed previews discard cargo purchases, fuel purchases and proposed stock targets.

RequestAIFacilitySupplyCommand recomputes the quote on the command tick. Departure spending and the remaining treasury must satisfy resource-defined limits. The resulting fixed route is inactive while DELIVERING: it finishes this paid delivery and return but does not automatically load another shipment. TradeRoute.carrierCommitted prevents another route from assigning its ship until the return finishes.

## Repositioning and saved claims

RepositionAIFacilitySupplyCommand checks both the empty trip and a discarded forecast of the subsequent paid shipment. Only the actual empty departure and its local supply purchases are committed. A projected arrival is never applied to the game. Goods remain in the source market and cargo remains empty during staging.

The inactive SUPPLY_REPOSITIONING route records the source, final destination, material and claimed quantity. Its optional FacilitySupplyReservation records expiry, actual staging spending, the combined departure cost cap and the treasury floor. Normal logistics ticks continue the funded trip toward the source. Other AI facility shipments subtract the claimed source surplus and destination requirement. This is a planning reservation: ordinary buyers can still consume the goods and changing markets can invalidate the eventual shipment.

At the source, EmpireLogisticsPlanner queues LoadAIFacilitySupplyCommand. It removes its own claim from a discarded quote, checks the current shortage and loads no more than its reserved amount. The fresh shipment must still fund actual cargo, delivery and protected return within current and saved limits. Successful loading replaces the staging route with the paid delivery using the same ID. Failed loading releases both claims without committing purchases.

Expiry, missing ports, lost control or an unaffordable next leg release the reservation. Release preserves already paid fleet motion; it does not teleport or cancel physical travel. Existing route commitment guards protect the carrier while the reservation is live. The route status card displays the saved staging explanation without running a forecast on the JavaFX thread.

Reservations round-trip through the optional trade-route field in the current version 1 save. Controller recreation restarts shortage observations but source-arrival loading needs no new observation window. Eligible companies retain their own route ownership, fuel reservations and payments through staging and loading.

## Configurable policy

The initial logistics_policy.json values are limited test settings:

| Setting | Value | Purpose |
| --- | ---: | --- |
| shortageObservationTurns | 2 | Distinct consecutive observations before dispatch |
| reorderDays | 1 | Default reorder threshold |
| targetDays | 3 | Default destination stock target |
| sourceReserveDays | 3 | Retained source demand reserve |
| minimumDeliveryDays | 1 | Minimum useful cargo quantity |
| maximumDepartureCostCredits | 50000 | Combined empty staging, cargo and departure/return funding cap |
| treasuryReserveCredits | 10000 | Minimum treasury after dispatch |
| maximumShipmentPreviews | 8 | Bound on physical shipment previews per planner call |
| maximumRepositionDays | 30 | Maximum forecast empty-trip duration and saved reservation lifetime |

Production rates, recipes and generation fuel remain defined by technological applications. Logistics thresholds can be tuned independently without changing production code. Resources load at startup; restart after editing them.

## Bounded verification

AIFacilityLogisticsTest saves a paid inter-system shipment and advances the loaded game through 25 normal engine days. It verifies delivery of 12 kg steel, 15 kg of paid ammunition production, steel conservation through recipe consumption and the carrier's completed return. It writes control/target/ai-facility-logistics.csv with daily movement, stock, staffing, cash and generation receipts.

Additional checks cover duplicate queued requests, stale ownership, insufficient treasury, retained source reserves, inbound cargo, missing travel fuel, occupied carriers, absent residents, generation-fuel priority, explicit stock targets and observation resets. Existing TradeReturnFuelTest verifies unavailable return-fuel rejection and private paid reservations. The controller reserves a feasible carrier during the observation window so generic roaming assignment does not take it first.

AIFacilityRepositionTest starts the carrier at the needy destination, saves the empty trip and compares source arrival with uninterrupted travel. It verifies empty cargo during staging, actual propellant consumption, freshly paid loading, useful factory output and the completed return. Additional checks cover competing carrier claims, source surplus overbooking, stock changes at arrival, treasury and control changes, expiry with paid motion retained and configurable duration and spending limits.

## Current limits

Supply dispatch uses available cargo ships at controlled hubs and can reposition them. A separate persistent-shortage planner can construct corporate or public cargo ships; see [AITransportConstruction.md](AITransportConstruction.md). It does not retask player routes. It chooses the first feasible candidate in priority order rather than optimizing an entire transport network. One command is proposed per supply planner call and dispatch remains subject to later market and travel conditions. Buyer funding can change before unloading; the normal route then waits or returns according to existing logistics rules. Pending source-arrival commands take priority over new shortage observations.

Orbital facilities can receive shipments when staffed and funded. Existing automatic operating-capital recovery still handles planetary and lunar facilities; this pass does not extend that recovery to stations. This is a limited feasibility check, not full weapon or economy balance.
