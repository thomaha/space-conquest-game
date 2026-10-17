# Composition-aware refinery recovery

The rare-earth refinery now resolves its products from elemental input composition. Research can improve gold and silver recovery on the existing compatible separation line. Products enter ordinary factory inventory and become available through funded marketplace purchases. Unsold products remain stock; unrecovered elements remain in saved tailings.

## Process and research

Refinery construction, scaling, investment and byproduct metadata now also belong to the researchable application. Generic physical source inputs and installed equipment pools are described in [ProcessSources.md](ProcessSources.md). Other unmigrated factory tables remain a separate backlog.

Factories are generic input-processing-output facilities. Application data must define every specific factory name, input, output, process cost and capability. The shared executor implements generic conservation, resource constraints and settlement. The `RefineryProcess` record is a reusable composition-recovery operation rather than a hardcoded rare-earth factory type. Older factory lists, fixed recipe tables and ID-based behavior elsewhere remain migration gaps; this first chain does not complete that migration.

The `rare_earth_refining` application owns its process definition in [technologies.json](engine/src/main/resources/technologies.json). Its initial equipment uses typed separation work units, rated capacity and operator proficiency as described in [ProductionWork.md](ProductionWork.md). The `rare_earth_byproduct_recovery` application requires that base application and unlocks an improved operating method. Both profiles require the same installed equipment. Discovery changes the recovery method without granting new machinery.

The current process accepts `rare_earth_fluorides`. Its catalog composition is interpreted as mass percentages and captured when fresh material is consumed. A baseline batch uses 1,000 kg feedstock containing 300 kg lanthanum, 300 kg cerium, 200 kg neodymium, 197.9 kg fluorine, 2 kg silver and 0.1 kg gold. These are provisional game values, not a measured ore assay or a validated chemical process.

| Requirement or result per 1,000 kg fresh feedstock | Baseline method | Improved method |
| --- | ---: | ---: |
| Separation work units | 1,000 | 1,200 |
| Process electricity | 6,000 kWh | 7,500 kWh |
| Water | 100 kg | 120 kg |
| Carbon | 5 kg | 6 kg |
| Mixed rare-earth product | 800 kg | 800 kg |
| Silver recovery | 25% / 0.5 kg | 75% / 1.5 kg |
| Gold recovery | 10% / 0.01 kg | 70% / 0.07 kg |
| Retained wet tailings | 304.49 kg | 324.43 kg |

The improved method recovers more valuable trace material but consumes more reagents, work and electricity. At unchanged equipment capacity it processes fewer batches per day. Its greater wet residue mass comes from the extra reagent intake. Baseline intake is 1,105 kg and equals 800.51 kg products plus 304.49 kg residue. Every element is conserved separately, including the water and carbon inputs. The current model records elemental inventories rather than molecular species or reaction chemistry.

Recovery fractions are bounded from zero to one. Products compete for actual available elements in an explicit order; each product cannot independently claim the full feedstock mass. Generic research performance and foundation improvements affect work requirements through the existing production model. They do not multiply recovery fractions or create elements.

## Retained tailings and reprocessing

Each facility account retains immutable residue lots with their remaining elemental quantities and a latest process receipt. Receipts include actual intake, purchased inputs, product quantities, residue and the active recovery application. These records survive save/load and ownership changes. History is bounded to the latest receipt rather than an accumulating transaction log.

The refinery panel allows the owner to select fresh feedstock or an actual retained tailings lot. Selection is staged through `SetRefineryInputCommand` and revalidated on the simulation tick. Corporate owners use their affiliated empire's research. A facility cannot select another facility's residue, including residue belonging to the same empire. Snapshot guards reject stale UI selections.

Reprocessing consumes or splits the selected lot and applies recovery to its remaining assay. It buys only the required reagents rather than purchasing fresh ore again. Work, electricity, cash and reagent availability constrain the completed fraction. The new residue receives a new identity. A depleted selected source blocks production until the owner selects another source, preventing accidental switching to fresh feedstock or replaying an exhausted lot.

The initial retained-tailings capacity is 100,000 kg per installed separation line, with the per-line value defined on the application. A facility pools the finite storage of its captured line count. Storage constraints limit actual processing and can stop it. Reprocessing also checks net residue growth from reagents. Tailings disposal, transport and dedicated storage expansion are further work.

## Marketplace and resource settlement

Actual production uses the shared work-capacity and finite grid-energy model. Input purchases and electricity must fit the owner's combined funds. Monetary electricity payment reaches generators through ordinary settlement. Partial batches scale inputs, products and residue together. Hive facilities use physical stock and power without monetary charges.

Gold, silver and the mixed rare-earth product use the existing saleable-material inventory and funded market-transfer path. Marketplace stock increases only when a real transfer occurs. A market without sufficient funds leaves product in factory stock; it does not discard it or create owner revenue. Selling retained product later does not rerun refining. Tailings are separate facility-held material and are not silently offered as products.

## Current scope and remaining calibration

Bulk marketplace goods still have a standard catalog composition. Fresh batches capture that composition at intake; geological grade variation and tradeable assayed feedstock lots are not implemented. Retained tailings do preserve their actual remaining composition.

The primary output remains the existing mixed `refined_rare_earths` product used by downstream recipes. Separate lanthanum, cerium and neodymium products require further definitions and consumers. Pure elemental outputs are named gold and silver. Old generic `fluoride_tailings` stock has no captured assay and is not automatically converted into these retained lots.

Reagent quantities, trace grades, work requirements, equipment prices and research costs remain provisional. The simulation now charges the declared physical inputs and current market prices, but this does not establish campaign profitability or real-world refinery economics. Calibration against a specified real feedstock and industrial process remains necessary before claiming that level of realism.

## Verification

`RefineryProcessTest` checks elemental and mass conservation, partial batches, improved recovery, different retained assays, finite resources, paid power, market transfer, unsold inventory, storage limits, depleted inputs, disk reload and a normal Hive engine tick. `RefineryLifecycleTest` follows funded research through saved-tailings reprocessing and checks queued ownership and source validation. `RefineryRecoveryCardTest` checks snapshot rendering, command staging and stale-selection protection.

Scoped verification passed 171 tests across 30 engine, control and frontend suites with no failures, errors or skipped tests. It also covers existing industry recipes, power allocation and payment, research integration, AI investment and supply, saves and frontend views. The final profile-validation and UI-label changes passed a focused rerun. This does not replace the deferred full campaign balance audit.
