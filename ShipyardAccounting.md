# Shipyard income accounting

Shipyard services now retain actual work, earned fees, paid orbital yard wages, commissioning and stalled-work receipts in an immutable ShipyardLedger. The ledger is part of GameState and current version 1 saves, so completed orders can leave the queue without deleting their payment history.

## History and tax settlement

ShipConstructionProcessor records physically supported work and the fee already transferred by ContractShipConstruction. It also records actual orbital yard payroll once per simulation turn. These receipts do not transfer money a second time. Blocked work records its reason with zero earned fee. Paid workforce can still cost money while a yard has no work.

Display history is bounded by maximumHistoryEntries in shipyard_access_policy.json, initially 256. Cumulative service results remain separate from this display window, so trimming old rows does not discard income needed for tax settlement.

CorporateProfitTaxProcessor adds new service earnings minus recorded orbital yard wages to the corporation's existing realized result. CorporateTaxAccount saves the last settled cumulative shipyard result. Replaying or reloading already settled shipyard receipts creates no new assessed tax. Existing loss carryforward, unpaid liabilities and municipal receipt allocation remain authoritative.

Surface-yard expenses already appear in IndustryAccount.realizedResultCredits and are deducted there exactly once. Orbital workforce expenses were not represented by owned industrial facilities and now enter through the service ledger. Client material purchases and hull capital spending are not yard service revenue and are not added to the provider's taxable result. Actual manufacturing sales remain in their existing industry accounts.

Only actual paid expense receipts are recognized. This pass does not invent power costs for station equipment that has no corresponding cash-billing receipt or introduce depreciation accounting. Other industry and trading tax streams retain their existing daily processing behavior; the saved shipyard checkpoint specifically prevents replay of this new service stream.

## Economy view

The empire economy view contains a Shipyard services and construction card. It displays up to ten affiliated queued orders with progress, customer, provider, fees paid and staffing or saved construction blockers. It shows the twelve most recent affiliated work and payment receipts, including completed orders, then current profit-tax assessment, payments and carried losses for up to six yard operators.

ShipyardDiagnostics reads immutable snapshot data and settled work capacity. The card performs no purchase, movement forecast or state mutation. Foreign history is filtered using customer and provider affiliation. Surface operating costs continue to appear in the ordinary industry economy data.

## Limited verification

ShipyardAccountingTest runs normal engine turns through a profitable captured contract, partial-payment checkpoint and commissioning. Reloaded receipts, corporate balances and tax accounts match uninterrupted ticks. Other cases verify zero-income stalled work, earnings retained after display trimming, no reassessment after reload, surface costs deducted once and orbital loss carryforward.

ShipyardEconomyCardTest verifies completed-payment and tax rendering, owner filtering and blocked queue diagnostics without changing snapshots. Shared yard, corporate transport, construction, save and related economic regression suites remain covered. No weapon or technological application capabilities are rebalanced.
