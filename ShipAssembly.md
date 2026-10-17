# Captured ship installations and production receipts

## Individual component identities

New ShipBlueprintFactory designs retain a ShipBlueprintAssembly with one ShipComponentInstallation for every chosen module. Each installation has its own ID, full immutable hardware snapshot, linked application, manufacturing receipt and installation work. Repeated copies of a module have distinct identities. These identities track equipment and do not prescribe weapon hardpoints, class sizes or module counts.

ShipDesignSpecification can carry explicit installation IDs when revising or reordering a model. ShipDesignSpecification.from preserves them. The workbench and AI revision paths retain matching installations and allocate new identities for added equipment. Default matching uses the first remaining copy of the same module type; explicit identities are needed to distinguish a particular duplicate during a future refit selection. Malformed, duplicate or incomplete identity lists fail blueprint validation.

ShipModuleDamage.remap is a pure comparison helper. It maps current index-based damage into a target model using installation identity and identical captured hardware. Retained hardware keeps its damage even after reordering. Changed hardware under the same application or module ID is replacement equipment and does not inherit the removed part's damage. Active combat still stores damage by mount index. No existing ship changes model or receives repairs through this helper; applying it will belong to the paid refit completion path.

## Separate frozen production receipts

ShipProductionReceipt contains immutable material quantities and standardized work hours. The assembly retains separate receipts for the frame, armor, each manufactured component and integration. ShipConstructionRequirements sums those captured receipts for new blueprints. It does not apply the old aggregate manufacturing multiplier again.

HullTechnology.assemblyTechnology reads production rules from technological applications. The baseline spacecraft application supplies frame and generic component recipes, per-mass work, per-capacity installation work, minimum frame work and a provisional armor mass coefficient. Larger hull engineering can supply its own rules or inherit the baseline assembly rules. Frame research cost modifiers affect frame receipts, and component application cost modifiers affect only that component's receipt. Captured frame development is included in research revision detection, including cost changes that leave physical frame capacity and strength unchanged.

Components with explicit material receipts use those receipts. Combat module manufacturing work comes from its technological application's costToBuildPerUnit. Components without explicit material receipts use the structural application's generic recipe and per-mass work rule. These input quantities remain provisional manufacturing definitions; detailed material density and physical structural geometry are separate future work.

The armor estimate for an application-defined frame now uses chosen hull capacity and thickness rather than occupied module slots. Replacing or removing equipment inside the same hull does not alter its external armor bill. Enlarging the hull increases the separate structural and armor requirements. This is still a capacity-based approximation rather than a geometric surface-area model.

Receipts contain quantities and work rather than market prices. Construction purchases actual local stock at current prices, pays finite yard labor and service fees and retains ordinary shortage behavior. A frozen order's material bill survives research changes and disk reload. The designer shows work separately for frame, armor, equipment and installation alongside its total material estimate.

## Existing aggregate designs

Aggregate constructors and current corporate templates without an assembly keep their existing production path. The captured-combat material helper is now used by ShipConstructionRequirements for those designs, closing the earlier live-catalog receipt gap. These records do not gain invented historical installation receipts. Damage remapping requires captured source and target assemblies and rejects records without them.

Current saves retain the new assembly records in the existing version 1 format. Existing blueprint editing restrictions still prevent changing the configuration of a built ship or pending order by editing its referenced model.

## Limited verification

ShipAssemblyTest verifies distinct duplicate installations, explicit reordering with retained damage, changed hardware replacement, a weapon swap with unchanged frame and armor receipts, component-only research cost changes, frozen estimates, captured aggregate weapon receipts, structural enlargement, invalid identities and normal paid partial construction through disk reload. It also verifies that a frame-only cost refinement is detected while the old design and retained equipment stay unchanged.

The related design, research, shipyard, economic, logistics, travel and combat lifecycle checks pass. The campaign combat fixture explicitly drops its source production assembly when constructing a synthetic equipment-only variant, so incompatible receipts cannot masquerade as a valid captured build.

## Refit comparison

The pure comparison and current-yard quote are now implemented, with a background preview in the fleet view. They preserve unchanged equipment through an explicit identity mapping and price changed components, structural work and armor work separately. See [ShipRefits.md](ShipRefits.md).

## Next step

Saved paid refit orders now follow the quote and change the same ship after paid completion. Downtime and partial progress survive reload. Salvage, cancellation and AI refit selection remain future work. See [ShipRefits.md](ShipRefits.md).
