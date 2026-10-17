# Funded business operations

Implemented on 16 October 2026. Daily operation now settles maintenance and carrier services, pays corporate profit tax and transfers available after-tax earnings into a separate replacement reserve. Local supply contracts hold buyer money until physical delivery. Private facilities can reduce staffing or pause when expected sales cannot cover costs. See [SupplyPricing.md](SupplyPricing.md) for the quote equation and [BusinessEconomicsReport.md](BusinessEconomicsReport.md) for three-year results.

## Expenses and replacement funding

Facility maintenance comes from the application's operating-reserve definition. Private owners pay from corporate cash and public facilities use their funded operating accounts. Carrier maintenance applies while idle; general carrier operation is charged during travel or active transport commitments. Payments enter an explicit service clearing balance. Cash shortages leave saved invoices rather than allowing overdrafts or silently cancelling the expense. An invoice remains the original owner's obligation when an asset changes hands or disappears.

Paid services reduce realized operating profit before corporate tax. Replacement funding is an asset transfer after tax, not an expense or a second tax deduction. The daily funding target accumulates toward the current replacement value over the policy's 3,600-day life. Profitable harvest days can catch up earlier unfunded installments. Contributions require positive after-tax earnings, available cash and no unpaid service invoices. Held reserves are excluded from ordinary spendable cash. An authorized owner can explicitly release them back to cash; this creates no income. Automated replacement spending is not implemented.

The immutable business ledger saves accumulated operating results, tax payments, service payments, held replacement cash, unfunded targets, invoices, contracts and current operating decisions. Closing the same day twice cannot repeat settlement. Current-format save/load preserves these accounts. Older development saves require no migration.

## Private operating decisions

Before hiring, each private process compares full, half and quarter allocated staffing against available inputs, electricity, buyer cash, spot demand and funded contracts. A viable smaller operation requests fewer workers. Otherwise the process pauses hiring and production while retaining its allocated equipment and staffing settings. Existing inventory can still sell. Standing maintenance continues during a pause.

Accepted funded contracts continue to guide production when prices rise and the target margin is lost, provided physical inputs, working capital and power remain available. Contract escrow is buyer money for delivery rather than a producer advance. It cannot buy missing inputs for the supplier. Public operation retains explicit funding policy. These forecasts do not guarantee profitability, continuous supply or fulfillment.

## Funded local supply contracts

The industry view can queue a local order specifying a material, quantity, wholesale price and delivery deadline of up to 360 days. Tick validation checks buyer authority, installed researched capacity, supportable inputs and electricity, first-delivery delay, existing commitments, buyer storage and actual buyer money. The price must cover the current wholesale ask. Acceptance moves real market cash into escrow without creating inventory or paying the supplier.

Actual produced stock fulfills matching local contracts before ordinary spot sales. Only delivered quantities enter market stock and pay the supplier. Storage and physical stock are rechecked. Expiry, cancellation or an invalidated supplier refunds only remaining escrow. Partial deliveries remain paid. There are no default penalties, producer advances or remote carrier contracts. Automatic commitments are bounded to two orders per hub per day. They serve explicit stock targets, inputs demanded by researched production and output with a delivery delay above one day. Replenishment includes estimated upstream processing delays plus a 30-day buffer. See [SupplyChainDiagnostics.md](SupplyChainDiagnostics.md) for measured constraints, funded byproduct candidates and seasonal receipts. Orders require storage and real buyer cash. Immediate replenishment also requires a market bid covering the ask. Input and long-lead orders use the current replacement quote because a stale spot price on opening inventory cannot fund a future harvest.

Mixed outputs share the process bill. A specifically funded byproduct buyer can support a quote without inventing demand for its primary coproduct. Unfunded byproducts receive no speculative revenue credit. Actual coproduct stock remains conserved and can sell only through normal funded settlement.

## Electricity and numerical stability

Normal generation follows local demand and finite battery headroom. Application-defined fuel intake scales with dispatched generation. Unfueled capacity cannot curtail an available generator. Shared fuel, owner cash, research, operators and environment still constrain actual delivery. Uniform owner-portfolio tariffs retain the existing proportional payment model; merit-order dispatch, ramping and minimum stable loads are not modeled.

Quotes ignore computationally negligible process capacity and buffer gaps relative to their normal scale. Near-empty input dust and machine-precision inventory gaps must not turn a full daily payroll into an astronomical commodity ask. This does not discard small trace-element yields from normal batches.

## Evidence and remaining work

Validation on JDK 27 passed 186 focused tests across 27 suites in the affected engine, control and frontend checks. Coverage includes actual expense and tax settlement, reserve conservation, affordable long-lead orders, partial delivery and refunds, current-format reload, generated starts, transport, application-defined power and queued UI actions. The full repository suite was not run.

The [three-year report](BusinessEconomicsReport.md) follows finite extraction, farming, processing, power, household purchases, expenses, taxes, reserves and a current-format reload. Each case runs 1,080 days. The outdoor chain supplies its tracked food demand but several businesses lose money. Both enclosed cases harvest and sell repeatedly yet leave substantial household shortages. Positive margins at selected stages are not proof that the whole chain is sustainable.

The probes start with installed capital, extensive research and wealthy firms. Construction funding, coordinated long-term procurement, coproduct buyers, mothballing and balanced campaign budgets remain separate work. The clearing balance represents abstract maintenance and transport providers; physical maintenance parts and service-worker wages are not simulated. The marketplace wholesale spread still lacks a complete retailer expense and tax ledger. Policy coefficients remain provisional. Factory execution remains generic and consumes application definitions rather than named factory rules.
