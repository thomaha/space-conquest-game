# Ship refit orders and quotes

## Current scope

The fleet view now contains a Ship refit preview panel. The player selects an owned ship and target model and requests a background estimate at the ship's current site. Inspection produces immutable comparison, cost, work, duration and preparation results. It does not spend funds, consume cargo, reserve a ship, create an order or change a ship's model.

The Start paid refit action now stages QueueShipRefitCommand. It revalidates the live quote at the simulation tick and captures the target blueprint, retained equipment mapping, physical bill and work in an ordinary ShipConstructionOrder with ShipRefitPlan metadata. Model names and roles still impose no fixed sizes or hardpoints. Source and target models need captured assemblies and hull definitions; aggregate models without that information cannot be refitted reliably.

## Paid work and downtime

Refits share the same finite paid yard work as construction. Each installment consumes actual accessible materials and settles actual service fees. Missing materials, funds, yard access or workforce pause progress. The order's bill, work, contract and retained mapping persist in current version 1 saves. No separate simulation loop hook or ship status field is needed: the saved order reserves its existing ship.

Release guard, escort, interception, repair and committed trade or supply missions before starting. The ship stays at its current yard. Its fleet cannot depart or change membership while a member is reserved. The ship cannot load new cargo, take supplies, recharge shields, provide onboard repair or fire direct weapons, missiles, point defense or carrier wings. Other unreserved ships can still fight and the refitting ship remains vulnerable. Ordinary electrical upkeep and cargo deterioration continue.

Completion updates the same ship in its existing fleet. Hull health, compatible actual cargo, fuel, electrical reserves and dedicated supplies are preserved. Retained hardware takes its current damage through the saved identity mapping, including damage received after the quote. Newly paid replacement hardware starts without old hardware damage. Completion creates no additional ship and grants no fuel, ammunition or charge. Structural and armor changes follow the same paid lifecycle and the conservative full-frame estimates described below.

## Equipment comparison

ShipModelComparison identifies retained, replaced, added and removed installations. Explicit identities are matched first and reserved before other matching, preserving the selected copy when several identical components exist. Independently named models can retain identical captured hardware even when their installation IDs differ. Remaining same-type hardware with changed captured properties is replacement equipment. The comparison returns an explicit one-to-one target-to-source identity mapping for retained parts.

ShipModuleDamage can use that mapping to preserve actual damage on retained equipment. Newly manufactured replacement hardware does not inherit the removed component's damage. Inspection leaves the source ship's hull, equipment, fuel, charge and cargo unchanged. Name or role differences alone do not require an equipment refit.

Replacement and added parts use their target model's captured production receipts. Retained equipment is not manufactured again. Removal has application-defined work but grants no credit or material refund. A removed weapon and added weapon can appear as two operations when their identities differ; the bill still contains only the new hardware plus removal and installation effort.

## Structural effort

Structural comparison considers chosen hull capacity, frame mass, structural strength and material. Research limits, model names and production-price changes do not by themselves rebuild an unchanged physical frame. Armor work follows physical capacity, plating material, thickness and captured armor quantities.

The structural change fraction is the largest relative difference in capacity, frame mass or strength. Hull-application refit data supplies a structural work multiplier and growth exponent. Required structural work is the target frame's captured manufacturing work multiplied by the base rebuild factor plus the powered change fraction. Larger departures from the source model require more work. Retained systems also incur reconnection work during structural rebuilding.

Because the model does not yet describe individual structural sections, a changed frame conservatively quotes the full target frame material receipt. Changed armor similarly uses the target plating receipt and adds removal work for the old plating. This makes enlargement or material replacement a major rebuild rather than a cheap aggregate mass adjustment. Component-only refits leave the frame and armor bills unchanged.

ShipRefitTechnology lives inside hull technological application data. The baseline spacecraft application defines removal work, installation work, structural rebuilding, armor removal and systems reconnection. Other structural applications may override those values or inherit the baseline. Weapon capabilities, recipes and manufacturing work remain captured from their weapon applications.

## Yard, funding and preparation

ShipRefitQuote validates ownership, society permissions, current territory, target research, actual ship location and yard manufacturing capacity. Current travel, committed supply or trade service and a pending commissioning mission require completion or release before work. Passengers must disembark before refit work.

The quote uses actual paid yard capacity for work days and separately reports existing queued construction workload. A corporate client may use a permitted public or corporate service yard and pays its own service fee. Existing shared-yard admission bounds remain active. The target model must be owned by the customer; the quote does not grant access to a foreign blueprint.

Material feasibility uses a discarded ordinary construction purchase after holding the prospective service fee. Orbital forecasts can consume real uncommitted owned transport cargo, but exclude carriers reserved for trade, supply or commissioning deployment. The returned material credit estimate is incremental cash outlay; already owned materials can cover inputs without another purchase. A complete bill that cannot be funded from accessible stock has no full material price result rather than displaying a partial purchase as the full cost.

Owned-yard operating estimates use current settled orbital yard payroll or surface yard wages, power and maintenance over the rounded work period. External service fees cover the client's service estimate without adding provider payroll again. Orbital station overhead, future staffing changes, changing prices and later delays are not fully forecast. Duration and operating costs are estimates rather than guaranteed completion schedules.

Preparation checks identify excess cargo, incompatible main propellant, nonempty storage being replaced, dedicated supply incompatibility, excess working generator stores, charged battery replacement and shield capacity reductions. No resource is silently deleted or converted. Retained equipment damage is considered when checking target electrical and shield capacity. Weapon supplies and charging remain separate from hardware construction.

## User interface and synchronization

ShipRefitCard shows component changes, separate equipment, removal/installation, structure and armor work, material quantities, service fees, estimated operating costs and blockers. Ship and target selectors omit foreign assets. Quote calculation runs in the background. Request identity and snapshot identity prevent late results from replacing a newer selection or a later simulation state. Start paid refit uses a tracked command and reports its tick outcome. Saved orders show completion progress. Rendering and quoting do not mutate game state.

## Limited verification

ShipRefitQuoteTest checks a funded weapon swap, damage retention, progressively greater enlargement work, matching independently named models, duplicate identity priority, insufficient funds, unavailable stock, lost research, foreign ownership, required cargo unloading, charged battery replacement, incompatible drive fuel, commissioning reservations and queued yard workload. Corporate quotes preserve customer funds during inspection and reject incompatible societies. Configurable work coefficients alter effort without changing equipment material quantities. Reserved carrier cargo cannot fund another refit.

ShipRefitCardTest checks the work and cost breakdown, ownership filtering, failed background calculations, stale request results and snapshot changes. The existing assembly, design, research, construction, economic, logistics, travel and campaign combat checks also pass. These are lifecycle and feasibility checks rather than a full balance pass.

ShipRefitOrderTest checks tick validation, duplicate rejection, same-ship completion, preserved damage and stores, shared capacity, paid partial work through reload, shortage recovery, downtime gates, supplied weapons held offline and corporate material and fee settlement without duplicate ownership.

The initial scoped refit regression passed 419 tests across 69 suites. A later lifecycle regression passed 190 tests across 32 engine, control and frontend suites, including active backhaul recovery, paid return fuel, custom hulls and saved refits. The full suite and longer price-grid balance audits are not claimed to pass. The stale technology count and aggregate workshop receipt expectations found during verification were corrected. See [TradeDeparturePlanning.md](TradeDeparturePlanning.md) for the subsequent logistics work.

## Next stage

AI repair/refit/replacement selection uses bounded funded comparisons for idle owned ships. See [ShipLifecycle.md](ShipLifecycle.md). Existing-ship cancellation now preserves consumed inputs and earned fees while releasing the current ship without restoring damage. Paid retirement recovers surviving captured materials through a funded local market. See [ShipRetirement.md](ShipRetirement.md). More precise structural section estimates, reusable removed components and private recovery storage remain future work. A lost or inaccessible ship leaves its paid order blocked for review until cancellation; it does not refund resources or create a replacement ship.
