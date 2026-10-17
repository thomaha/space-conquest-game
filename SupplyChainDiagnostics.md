# Supply-chain diagnostics and procurement timing

Implemented on 16 October 2026. This pass explains constraints, checks seasonal accounting and extends funded procurement timing. It does not tune wages, yields, prices, taxes, capital life or target margins to make test businesses profitable. Loss-making examples remain useful tests.

## Measured operating constraints

Private operating decisions now capture immutable constraint records on the simulation tick. The industry view renders those saved records without running production or route forecasts on the JavaFX thread. Each observation identifies the resource, required quantity and available quantity. Current-format save/load retains the observations.

Checks cover purchased materials, finite discovered deposits, process electricity, working capital, market storage, research, manufacturing capacity and work or site restrictions. Generator checks show rated fuel and funding needs separately from curtailed generation. The audit also compares requested workers with actual paid workers. Capacity constraints may overlap and can reduce output without stopping it; they are not presented as mutually exclusive causes of every idle day.

Electricity requirements are checked for the batches that available inputs could support. A zero power allocation caused by missing material is not evidence of a grid shortage. A commercial pause can separately record expected receipts below the required receipts. Available stocks, buyer spending and market liquidity still determine whether a conditional ask becomes a sale.

## Upstream delivery time

`SupplyLeadTimes` reads installed researched application recipes. Parallel input branches use the longest upstream delay while alternative local producers use the shortest declared chain. A process then adds its own processing or remaining growing time. Pricing and funded procurement use that estimate with the existing 30-day buffer rather than considering only the final process's delay.

The calculation is bounded to eight upstream stages and 360 days. It is a conditional scheduling estimate rather than a production reservation: it does not create missing resources, workers, cash or transport. Unknown external suppliers add no invented capability and cyclic recipes remain a bounded estimate. Import travel is quoted separately by the physical route model. Quantity, storage, supplier capacity and buyer cash are revalidated when accepting an order.

Automatic procurement can also fund inputs demanded by installed researched processes. Declared byproducts can be explicitly quoted to that buyer and then accepted against real escrow. Merely considering a candidate does not credit byproduct revenue, change stock or pay the supplier. This closes the gap where a needed coproduct had no funded buyer and its upstream process therefore never started. Each hub still accepts at most two automatic orders per day. Input and long-lead orders use current replacement asks rather than requiring a stale spot bid to fund future production.

## Seasonal accounting evidence

The updated [three-year business audit](BusinessEconomicsReport.md) counts constraints for each facility and reports farm receipts between harvests. Every daily input, wage, electricity and maintenance payment is included, along with actual corporate tax, the daily capital allowance and actual reserve transfers. The audit checks the sum of operating receipts against the persistent business ledger throughout all three 1,080-day runs.

These are calendar windows rather than inventory-lot costing. Later sales of earlier harvests can enter another window. The unfinished closing window is shown with zero harvest. Capital allowances describe replacement requirements; held reserve transfers remain assets and are excluded from operating profit and tax deductions. The report exposes losses without asserting that a harvest must earn a positive margin.

Daily numeric traces are generated in `engine/target/business-constraints-<application>.tsv`. The Markdown report records overlapping constraint counts so missing inputs, unmet staffing and weak funded demand can be investigated independently of balancing.

## Local food versus imports

The [offworld comparison](OffworldSupplyComparison.md) propagates a conditional source farm ask into a separate carrier quote, including the marketplace spread, actual propulsion model, empty return, operating services, maintenance, carrier capital and its own tax and target margin. Available lunar routes execute actual funded outward deliveries and return voyages. Unavailable Mars offers remain unavailable alternatives.

Source stock and installed capital are seeded for these route probes. They do not execute surface farming, lift or destination landing and do not simulate an importing colony's household purchases. Source prices are conditional quotes rather than proof of realized upstream profits. The [surface-export report](FoodSurfaceExportReport.md) separately records paid lift costs. The route comparison isolates freight and holds local process costs equal; a full site-specific colony comparison still requires its own land, enclosure, energy and supply data.

No economic coefficients are changed in this pass. A complete coordinated supply-chain optimizer, product-lot cost ledger and a calibrated colony economy remain future work.

## Verification

On JDK 27, 133 focused tests across 19 suites pass. Checks include constraint persistence and immutable UI rendering, independent material and electricity limits, upstream lead times, funded byproduct orders, actual return voyages, daily seasonal receipt identities and generated starting economies. All three local audits run 1,080 days with a current-format reload. The full repository suite was not run.
