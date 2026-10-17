# Research and investment handoff

Research discoveries and refinements now reach corporate factory investment and new AI ship orders through existing construction paths. No weapon values or research outcome values were balanced in this change.

## Factory investment

Corporate investment previously ranked shortages before checking whether the best recipe was researched or feasible. An unavailable opportunity could block a smaller useful one. Selection now filters research, liquid-water requirements, discovered extraction deposits and pending construction first. Automatic investment still requires positive projected profit and the existing shortage threshold.

Consumer manufacturing can discover non-extractive production recipes from the application catalog, including ammunition. A matching operating factory is expanded using the current application's cost and complexity requirements. New factories retain tier-zero construction sites. The startup charge does not provide materials: construction purchases them from the local hub and pauses when stock or funds are unavailable.

## Ship procurement

Ship manufacturing profiles capture linked application modifiers alongside frozen manufacturing values. Corporate orders select current component variants and reuse an equivalent blueprint where available. Empire AI orders evaluate a researched revision of the selected component choices and reuse a matching current revision. Validation still checks demand, duplicate orders, research, yard capacity and affordability before registering a revision.

Existing blueprints, pending construction quotes and built ships retain their original values. Revision metadata and completed ships survive the version 1 save round trip. Empire AI orders require an affordable full material quote; corporate orders retain their existing startup charge and subsequent partial material purchases.

## Bounded verification

`ResearchInvestmentLifecycleTest` follows paid scientist work, application discovery, automatic investment, actual material purchases, completed construction, paid factory workers, measured solar generation, grid allocation and ammunition production. An unresearched but more attractive shortage does not block the researched recipe. Pending construction does not create duplicate projects and a subsequent investment expands the existing facility after a disk checkpoint.

`AIShipResearchRevisionTest` verifies improved thrust in a newly captured order, unchanged existing hardware and pending quotes, equivalent revision reuse, material-funded commissioning and disk persistence. An unavailable material quote creates neither a revision nor an order. `CorporateProcessorTest` covers researched corporate variants, unchanged earlier quotes, recipe eligibility and factory construction requirements.

## Remaining limits

These are bounded handoff checks rather than a full weapon balance or campaign economy assessment. Factory construction still uses provisional daily work capacity and aggregate bills. Investment profit estimates remain provisional. Public power-plant planning is now implemented with separate bounded checks in [AIPowerInvestment.md](AIPowerInvestment.md). Autonomous corporate transport routes, broad fleet procurement strategy and automatic selection of newly researched weapon loadouts remain future work. Empire fleet planning retains its existing initial trader and wartime military needs.
