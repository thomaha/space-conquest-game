# AI transport construction

Empire AI can order cargo ships after persistent facility supply shortages remain unserved. It considers eligible affiliated transport corporations before public investment. Corporate builds and cargo purchases use company capital; public builds use the empire treasury. A forecast never creates a real ship, gives it free fuel or transfers money between owners.

## Eligibility and capacity

EconomicOwnership reads society_economy_rules.json. Individualist and Collectivist societies permit corporations in these transport and queued construction paths; Hive Mind societies do not. Unknown society names do not implicitly permit private enterprise. A transport company must belong to the governing empire and have TRANSPORT orientation. Foreign designs and foreign companies cannot supply the owning empire's construction order.

Existing corporate cargo ships participate in facility supply planning and generic civilian service. Their actual fleet and blueprint ownership determine capacity rather than the corporation's ship ID list alone. Live inbound cargo and saved staging claims already reduce shortages. An available carrier that can fund a useful shipment or repositioning blocks a stale construction request. Pending eligible cargo orders also block new affiliated investment, including after reload.

TransportConstructionPlanner observes distinct consecutive shortage turns, resets on gaps or resolution and proposes at most one build per call. Existing service proposals take precedence. It tries eligible companies' owned cargo designs before empire designs, with a resource-defined candidate limit. Generic public trading bootstrap remains available when there is no facility supply backlog but accounts for eligible corporate ships and pending orders.

## Construction and commercial checks

RequestAIFacilityTransportBuildCommand rechecks current needs, affiliation, carrier limits, pending orders and useful existing capacity on the command tick. It obtains the current researched revision through AIShipResearchRevision and validates ordinary QueueShipBuildCommand ownership and technology gates. The builder needs an owned or permitted service yard with settled paid work capacity and sufficient manufacturing complexity.

FacilityTransportBuildQuote discards the full service fee, material purchase and commissioned-ship service preview. Both the whole build and a useful paid shipment from the yard must be affordable with the owning entity's cash reserve retained. Companies can use permitted staffed public or corporate yards while retaining their blueprint and ship ownership; see [SharedShipyards.md](SharedShipyards.md). The current preview requires the selected yard to be a viable cargo source; automatic yard investment and construction justified solely by repositioning from another yard are not included.

Corporate service requires a positive operating-profit forecast. TradeOperatingProfit includes purchased cargo, the replacement cost of consumed carried fuel, launch costs and the secured empty return. Empty staging also includes replacement cost. The build's expected profit must cover its material cost within the configured number of repeated shipments. These are estimates under current prices and demand, not guaranteed future earnings. Public strategic shipments do not require profit.

Only the researched revision and ordinary construction order are committed. ShipConstructionProcessor subsequently buys and consumes physical materials with the owner's funds and advances paid yard work before commissioning. Existing orders, ships and designs remain frozen. Prices, stock and funding can change during construction, in which case the ordinary processor can wait. No new construction escrow or market reservation is introduced.

The completed ship retains corporate or empire ownership. EmpireLogisticsPlanner can select corporate cargo ships for commercially viable supply and repositioning, with corporate routes, fuel reservations and payments preserved through source arrival and save/reload. Construction, logistics and corporate receipts use existing version 1 save fields.

## Configurable policy

transport_construction_policy.json contains the limited-test defaults:

| Setting | Value | Purpose |
| --- | ---: | --- |
| observationTurns | 4 | Distinct consecutive unmet-need observations |
| maximumCarriersPerEmpire | 4 | Live affiliated cargo ship ceiling |
| maximumBuildPreviews | 8 | Construction candidate checks per planner call |
| maximumBuildCostCredits | 500000 | Full material purchase cost ceiling |
| empireReserveCredits | 10000 | Public capital retained after projected purchases |
| corporateReserveCredits | 10000 | Private capital retained after projected purchases |
| minimumOperatingProfitCredits | 1 | Minimum commercial shipment margin |
| maximumPaybackShipments | 20 | Maximum estimated repeated shipments to recover build cost |

logistics_policy.json still controls shipment reserves, staging duration and departure spending. Blueprint capabilities, production requirements and recipe rates remain application-defined. These settings are provisional and load at startup; restart after resource edits.

## Bounded verification

AITransportConstructionTest exercises both company-funded and public construction. Each lifecycle saves the pending order, rejects duplicate builds after reload, commissions through normal engine ticks, dispatches paid cargo and verifies factory output and physical steel conservation through construction and recipe consumption. Corporate loading leaves the empire treasury unchanged on that command. Corporate commissioning also updates the company's ship registry.

Other checks cover unprofitable private shipments, missing private capital, lost material stock, foreign design use, affiliated pending orders, a newly available corporate carrier, observation gaps and the carrier ceiling. Individualist and Collectivist permissions and Hive Mind rejection are verified. A separate corporate staging scenario saves the empty trip and checks source arrival and loading with the company still the payer.

The fixture uses an explicit destination stock target large enough to support commercially useful freight. A tiny shipment can be physically useful but fail the private profit gate; the public planner can cover such strategic needs when it has an eligible design, yard and budget. This is a limited functionality check, not a full economy balance pass.
