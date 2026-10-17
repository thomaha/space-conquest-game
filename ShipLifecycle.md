# AI ship maintenance and replacement

## Current decisions

ShipLifecyclePlanner compares repair, refit and replacement for idle owned ships at controlled ports. Public fleets and transport corporations in compatible societies use the same paid workflows with their own ownership and funds. Ships committed to travel, trade, supply, commissioning or another refit are excluded. Each empire proposes at most one decision per review with bounded ship and model previews.

Repair estimates cover the fleet's complete hull and component damage using captured hardware material receipts. Available workshops must have the full material bill aboard or the base must fund it. Current funded daily repair capacity estimates downtime. The selected command requests the existing repair workflow; normal engine days consume supplies and perform repairs.

Refit compares owned models of the same role and a researched revision of the existing blueprint. It retains the designer's chosen hull capacity and individual installation identities. Quotes include actual materials, removal and installation work, structural changes, service fees, operating estimates and queued work. Refit uses the existing saved paid order and preserves the same ship.

Replacement queues an ordinary paid new build when it offers a better affordable recovery. The original ship remains intact. Pending orders record the source ship and commissioning records the completed replacement in ShipyardLedger. These links survive version 1 saves and suppress duplicate replacement decisions while both ships remain alive under the same owner. Construction retains the normal deployment handoff. Retirement now waits for replacement readiness and preserved coverage, then uses paid yard dismantling and funded recovery. See [ShipRetirement.md](ShipRetirement.md).

## Decision settings and hardware

`engine/src/main/resources/ship_lifecycle_policy.json` controls preview bounds, treasury reserve, minimum gain, acceptable hull condition, refit/build cost ratio, downtime limits and capability weights. The comparison normalizes condition-adjusted weapon, shield, thrust, sensor and electrical capability against the healthy source model. Cargo contributes to civilian roles. Cost and estimated downtime per gain rank feasible alternatives.

These values express AI preferences. Hull limits, hardware capabilities, material bills and refit work coefficients remain in technological applications and captured blueprints. No fixed frigate or battleship size or hardpoint arrangement is introduced. Hive public industry follows existing material-based funding without a monetary treasury floor; incompatible societies cannot use corporate decisions.

## Synchronization and limits

Inspection uses discarded immutable snapshots. RequestAIShipLifecycleCommand recomputes the choice at the command tick before requesting repair or staging ordinary construction. The planner reserves the selected fleet from competing assignments during that review. Existing yard, materials, ownership and mission gates apply again to actual orders and installments.

Estimates use current prices, staffing and funded capacity. They do not guarantee future completion dates. The current planner does not release active missions, unload incompatible stores or travel to a different yard to make a refit feasible. Replacement retains the original ship without inventing salvage proceeds. Structural geometry and a full balance pass remain future work.

## Limited verification

ShipLifecyclePlannerTest covers repair/refit/replacement choices, configurable preferences, discarded previews, tick rejection after funding changes, fleet reservations, corporate service fees, society eligibility, collective material funding and replacement links through partial construction and save/reload. Existing repair and paid refit tests verify the underlying consumption and completion workflows.

The scoped engine, control and frontend regression also covers custom hull design, captured assembly, shared yard accounting, deployment, orbital travel, paid return fuel, active backhaul recovery and refit UI behavior. Longer price-grid audits are separate from this limited lifecycle verification.

Verification passed 190 regression tests across 32 suites plus both depot replenishment checks and the three-cycle lunar round-trip check. The lunar scenario completed 117 paid physical round trips with reload and money/material conservation checks. This is feasibility verification; its measured operating loss is retained rather than tuned away.
