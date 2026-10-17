# Supply costs and business margins

Implemented on 16 October 2026. The live daily market update now reads local production offers and a bounded set of physical import offers. Shortage prices can exceed the old limit of twice a commodity's global base price. Electricity settlement also uses the local generators' costs rather than assuming every provider can sell sustainably at 0.02 credits/kWh.

Each independent producer or carrier quotes its own target profit after operating expenses, applicable corporate profit tax and capital reserves. Purchased inputs enter once at the local market price, which already contains upstream margins and the existing wholesale spread. There is no extra markup added for every earlier step in the supply chain.

## Economic policy and taxes

The shared [pricing policy](engine/src/main/resources/supply_pricing.json) currently sets a 10% target net margin and 3,600 operating days for capital recovery. Transport additionally pays 25 credits/day for general operation and 0.01% of its quoted construction-material value per day for maintenance. These are provisional economic assumptions, not measured crew counts or factory-specific properties. Names, recipes, equipment and construction specifications continue to come from application data.

For operating costs `O` including paid maintenance and carrier services, replacement-capital allowances `K`, corporate profit-tax rate `t` and target net margin `m`, required business receipts are:

`R = ((1 - t) × O + K) / (1 - t - m)`

This leaves `R - O - t × (R - O) - K = m × R`. The current corporate tax ledger taxes realized operating profit and does not deduct these capital reserves. Treating reserves as a tax deduction would understate the required price. Regimes where `t + m >= 1` cannot supply the requested margin through a finite quote.

Corporate rates come from the owner's affiliated empire. Public facilities have no corporate profit tax but retain the common target surplus. Quotes use the normal statutory rate instead of spending an existing loss carryforward in advance. Actual assessment, loss carryforwards, payment constraints and unpaid liabilities remain with `CorporateProfitTaxProcessor`. Transit tariffs are separate from profit tax and are included before comparing carrier receipts with the target.

The producer's required receipts are converted to a retail offer through the existing 80% wholesale settlement share. That spread remains the marketplace convention; a complete retailer expense and tax model is not introduced here.

## Local production offers

`FacilitySupplyPricing` reads installed capacity, captured equipment, operator work rates, research, site constraints, finite input stock and available electricity. It caps output by the shared power forecast and current funding. A farm or factory without available process energy cannot advertise that output as currently supportable supply.

Daily cost includes bought inputs, allocated staffing, electricity, declared upkeep and a replacement-capital allowance. Construction materials are valued at current local prices where available, with catalog base prices as the fallback. Application investment credits are included where defined. This is a replacement-cost allowance, not a historical invoice or a completed capital-depreciation ledger.

For mixed output, the complete bill is allocated once across demanded primary products using fixed baseline-value weights. Each product does not receive the full bill independently. Declared byproducts receive no speculative revenue credit unless supported by a funded contract or explicitly requested while validating a funded buyer. Actual sales of useful byproducts still earn money through ordinary settlement. Output without a buyer does not create its own demand.

Recipes still awaiting application migration use their existing generic catalog batching, power and construction conventions. No new named factory branch or hardcoded process table was added. Those quotes remain limited by the accuracy of the legacy definitions; moving the remaining definitions onto researchable applications is still necessary.

Maintenance and carrier services now settle from actual cash before profit tax. Replacement funding moves available after-tax earnings into a separate held balance and catches up accumulated unfunded daily targets. See [BusinessOperations.md](BusinessOperations.md) for invoices, operating decisions, reserve release and funded local contracts.

Offers express daily supportable capacity and a first-delivery delay. Farm delay reflects its growing period and any saved paid growth. The quote is not a sale, deposited inventory or a guaranteed future harvest. Continuing inputs, funding, staffing and utility supply remain necessary until actual completion.

## Import offers and commercial decisions

`TradeSupplyPricing` uses discarded, paid shipment previews against real source stock, a compatible idle carrier, carrier money, buyer money and storage. It includes purchased cargo, consumed working fuel, launch service, the quoted empty return and the carrier's own operating and capital reserves. Unused fuel reserves remain assets rather than being charged again as consumed voyage expense.

Import capacity is the shipment quantity divided by the complete outward-and-return duration. A 2,500 kg delivery is therefore not treated as 2,500 kg of daily supply. The offer retains the outward delivery delay. The model currently assumes an empty return; profitable backhaul could improve a real route's result.

Automatic market pricing checks up to two import candidates per short-stocked hub, in deterministic commodity-priority order, within the same star system. Explicit quote functions retain the existing route model's broader capabilities. This bound prevents ordinary market refresh from becoming an unbounded route search. Discovery of unbuilt carriers and automatic interstellar supply-price discovery remain future work.

Below the 30-day inventory buffer plus the available local first-delivery delay, market pricing sorts available local and delivered daily capacities by price and uses the marginal quoted ask for daily replenishment. This is a replacement-cost signal before stocks are exhausted, not a claim that today's buyers face an immediate physical shortage. Ample stock allows the ordinary price to fall. No supplier offer means the existing spot calculation remains a fallback; it does not manufacture a viable supply chain.

Investment scoring now uses the local electricity quote, declared upkeep, capital recovery, corporate tax and target margin. Expected sales are also bounded by available market cash and the proposed input purchases. Shipment sizing, mixed-load selection and corporate facility supply use a surplus after their quoted return costs, taxes and target reserves. A positive gross trading spread alone is no longer enough.

These are nonbinding forecasts. Multiple offers can compete for the same cash, inputs or carrier. Commands and ordinary settlement revalidate the chosen action. No goods, workers, fleet movement, money or tax payment is committed by merely inspecting an offer.

## Electricity through the supply chain

`GridSupplyPricing` forecasts funded local generation and demand. Plants with the same economic owner form one supplier: their costs and generation are pooled before pricing. The uniform tariff covers each independent provider's expected share of sales under the existing proportional generator-payment model, including wages, fuel, upkeep, capital reserves and its applicable tax. A low-output plant does not independently dictate the tariff for its owner's entire generating portfolio. This is not a merit-order dispatch optimizer; an expensive independent provider can still raise the local tariff.

Normal generation follows demand and finite battery headroom, scaling fuel consumption with dispatch. Unfueled capacity cannot curtail available generation. Actual billing uses that day's generated quantities and paid operators. Typed work carries the quoted tariff in its immutable daily energy budget and pays only for work actually done. Household basic-needs budgeting, operating readiness, launch-service quotes, factory investment and ship battery charging read the same local price convention. Stored electrical energy retains its existing unpriced draw policy. Fixed-price billing can also settle an explicit quoted tariff independently of the default cost forecast.

Expected sales can fall short if customers lack money, inputs disappear or generation changes. Higher asks do not guarantee profitable operation. Existing funded purchases, unsold stock, growing time, storage, tax collection and owner cash constraints continue to determine actual results. Public subsidies remain explicit.

## Verification and limits

Validation on JDK 27 passed 188 focused tests across 23 suites: 174 tests in the final engine, control and frontend run plus the separately completed 14-test generated-startup suite. This includes current-format save/load, funded logistics, tax settlement, utility billing, production and immutable UI rendering. The entire repository test suite was not run.

Tests check the tax equation against actual corporate tax assessment and collection, loss carryforwards, one-time upstream input pricing, joint-output cost allocation, no speculative residue income, missing resources, unfunded or unpowered offers and prices above the former food cap. Actual grid billing checks that metered receipts cover quoted costs, tax, reserves and the target margin at the tested funded load. Import checks cover real carrier constraints, return costs, buyer liquidity and inspection without stock creation.

Generated campaign checks retain basic goods, electricity and paid physical production checks. Optional purchases must be paid and manufactured goods must actually reach the market; universal daily luxury consumption is not guaranteed. The non-human fiscal test checks conservation of local cash and explicit debt across ticks rather than assuming every opening budget balances. Government budget calibration remains separate from business target margins.

The new [360-day operating report](SupplyPricingEconomyReport.md) records repeated soil, hydroponic and biosphere harvests with active supply pricing and metered utility costs. The generator gains operating cash in these funded examples. Farms can still lose money because seasonal receipts, underused processing capacity and unsold products are real constraints. The earlier [agricultural economics review](AgricultureEconomics.md) remains a historical baseline with its original fixed-tariff cost figures.

The target margin, asset life, operating allowances and legacy recipe scales remain provisional. Financing interest, competitive wage formation, retailer operating costs, dedicated crew accounts, detailed depreciation and binding multi-party procurement contracts are not yet modeled. An offer is a conditional operating estimate rather than proof of a self-starting or indefinitely profitable campaign.
