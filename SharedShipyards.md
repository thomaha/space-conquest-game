# Shared shipyards

Eligible corporations can commission their owned researched designs at public or other corporate shipyards within their home empire. They retain the resulting ships and pay materials, working fuel and service fees from company capital. The yard supplies its actual paid workforce and manufacturing capacity; access transfers neither yard nor blueprint ownership.

## Access and admission

ShipyardAccess uses society_economy_rules.json and shipyard_access_policy.json. Customer and provider must belong to the same empire and the system must remain controlled by that empire. External access is limited to eligible corporate customers. Public and corporate offers can be enabled independently. Foreign yards and public borrowing of private yards are outside this pass.

QueueShipBuildCommand selects a compatible operational orbital or surface yard. Offers with settled work capacity take precedence over yards awaiting staff. Owned yards are preferred among ready offers, then shorter queues and stable IDs break ties. External offers require paid staffing, sufficient manufacturing complexity, an affordable total fee and backlog within the configured work-day limit. Backlog includes ship and auxiliary construction at the site. Chemical freighters requiring orbital construction retain that restriction.

ShipManufacturingCapacity.forOwner includes permitted service yards when evaluating corporate blueprint manufacturing capability. Research and blueprint ownership remain separate gates. Existing corporate investment captures a service contract at a valid domestic yard even before opening payroll settles; actual construction still waits for paid work.

## Contracts and payment

An external order captures a ShipyardContract with provider owner ID, credits per work unit and cumulative fees paid. Own-yard orders retain a null contract and pay no service fee to themselves. The optional contract round-trips in current version 1 saves and remains attached to partial progress.

ShipConstructionProcessor uses provider staffing and manufacturing capacity. All ship customers share the existing daily site work ledger; auxiliary construction receives its remaining capacity. Material shortages leave unused work available to subsequent orders. ContractShipConstruction temporarily holds the current work fee while the customer buys materials, pays only for physically supported work and refunds the unused hold within that same immutable step. Intermediate holds are never published. Zero work earns zero fees and exact transfers conserve cash.

The captured provider must still own the yard and access must remain permitted. Lost control, revoked eligibility, absent staff or a transferred yard pauses work without fees. Consumed materials, progress and prior payments remain on the order. Resumption uses the captured fee rather than repricing the contract.

## AI investment

FacilityTransportBuildQuote uses provider paid capacity and includes the full service fee in its discarded affordability purchase. The customer must still retain its reserve while funding the material bill and subsequent useful shipment. Corporate estimated payback includes service fees in construction cost. Only the order and researched revision are committed; normal daily processing settles earned installments.

Commissioning retains customer ownership and updates its corporate ship registry. Supply delivery and repositioning retain company payers. Public and private providers receive service income directly in their capital balance.

## Configurable policy

| Setting | Initial value | Purpose |
| --- | ---: | --- |
| publicYardsOpen | true | Domestic corporations may use public yards |
| corporateYardsOpen | true | Domestic corporations may use other corporate yards |
| feeCreditsPerWorkUnit | 2 | Rate captured on new external contracts |
| maximumQueuedWorkDays | 30 | Estimated backlog limit including the new external order |

Values load at startup. Fee edits affect new contracts; saved contracts retain their rate. These are limited-test settings and do not rebalance technological application capabilities.

## Verification and limits

SharedShipyardTest checks public and private providers, exact fee income, cash conservation and customer ownership. Two companies share a yard without exceeding daily capacity; partial fees and provider identity survive disk reload. Other cases cover a full queue, cash and material shortages, unpaid staff, access restrictions, surface yards and changed ownership. A normal engine lifecycle saves a company order at a public yard, commissions its ship and restores paid factory output through delivery and return.

Queue estimates assume current staffing and omit future delays. This pass adds domestic access, not yard construction or cross-empire contracts. Commissioned orders leave the queue while actual work and fee receipts remain in the bounded shipyard history. Corporate service earnings and orbital yard wages now participate in profit tax through a saved settlement checkpoint. See [ShipyardAccounting.md](ShipyardAccounting.md).
