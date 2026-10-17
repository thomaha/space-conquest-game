# Multi-system economy probe

The bounded scenario runs 60 normal SpaceConquestEngine days with economic-slider, power-investment and operating-capital planners. It starts one empire with two populated systems, finite local stock, physical profession populations, household wallets, corporate capital and public facility accounts. There are no recurring fixture grants or stock replacements.

The richer system has a corporate ammunition factory and funded power. The struggling system starts with municipal debt, empty public power and ammunition accounts, missing local steel and a funded corporate steel refinery whose power demand exceeds the installed plant. The normal daily loop handles markets, payroll, construction, generation, electricity billing, production, taxes, municipal transfers and imperial settlement. Research, fleet procurement and cabinet planning are outside this focused decision mix.

## Integration fixes

The first run never constructed additional power and the public factory never produced. Electricity billing saves delivered demand after load shedding, so a grid can have zero net deficit while retaining an active brownout. PowerInvestmentQuote now reconstructs requested load from actual paid facility workers, household demand and launches when that flag is active. Allocated but unpaid job slots do not manufacture a power shortage. The existing staffed plant can then receive funded expansion through ordinary construction.

The first run also reversed the rich system's tax direction five times. Full tax steps and simultaneous budget changes repeatedly crossed fiscal balance. Tax correction now estimates taxable income from actual personal tax receipts, includes the proposed budget change and retains the existing maximum tax step. A configurable `fiscalDeadbandFraction` in economic_policy.json holds tax and budget when the imbalance is small relative to the current budget. This is an integration safeguard rather than a full economy balance pass.

## Observed results

After those fixes, the run records 32 days of public ammunition production, 60 days of positive rich-system fiscal balance, 60 days of contributions from the rich system and 60 days of subsidies to the struggling system. There are 26 operating-recovery days and two construction days. The struggling system's existing plant reaches tier 2 without a duplicate facility. Rich-system tax has no direction reversals.

The central treasury ends at approximately 1.375 million credits from an opening 1.5 million and never crosses the configured retained floor. No imperial debt is recorded. Local debt is allowed and ends at approximately 1,124 credits in the struggling system. The final public factory blocker is `NO_DEMAND`: its modeled local stock target is filled, so continued recovery is not justified merely because its cash later falls.

The test asserts exact command-stage treasury transfers, normalized shares, finite budgets, paid output, both transfer directions, funded expansion, no duplicate facilities and bounded tax reversals. A version 1 disk checkpoint after day 30 runs beside the uninterrupted path through day 60. Treasury, system policies, project counts and public output match. The checkpoint aligns with the five-day review cadence; controller scheduling memory remains unsaved.

Daily receipts and totals are generated at `control/target/multi-system-economy.md`. The fixture and assertions are in MultiSystemEconomyWorld and MultiSystemEconomyTest. Focused tests separately cover load-shed demand based on paid workers and tax correction near fiscal balance.

## Scope and limits

This is a deterministic handoff test with substantial finite opening wealth and enough input stock for the test window. It does not establish long-term corporate profitability, equilibrium prices, a universal treasury floor guarantee or complete debt elimination. Later stock depletion, profession training, additional systems, arbitrary reload timing and other AI spending can change outcomes. Weapon values and application capabilities were not rebalanced.
