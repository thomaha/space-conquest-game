# Production work units

All existing deposit-extraction recipes, ordinary metal refining and alloys, rare-earth separation and four water-processing applications now use typed production work units. A recipe's required work is independent of the number, skill or species of its operators. Installed equipment and its operating crew determine the work rate. Other industrial recipes retain their legacy production calculation until they receive explicit process definitions. See [MetalProduction.md](MetalProduction.md) for the latest migration and energy allowances.

## Task and capacity

[technologies.json](engine/src/main/resources/technologies.json) defines the initial iron-chain recipes' process type, work per batch, process energy per batch, minimum processing interval and installed equipment on researchable applications. Rare-earth separation defines the same work data within its application-owned recovery profiles. The former separate work resource has been removed. Work units are an abstract measure within a process type, not time, energy or a universal conversion between unrelated tasks. See [FactoryApplications.md](FactoryApplications.md) for the migration and remaining exceptions.

[ProductionEquipment](engine/src/main/java/com/spaceconquest/engine/industry/ProductionEquipment.java) is an immutable captured facility record containing equipment identity, supported process type, rated work units per day, reference operator count, required profession, autonomous fraction and current operator proficiency. The reference crew is an operating requirement, not the definition of task work. There is no multiplication of output by arbitrarily many workers.

For example, steel alloying requires 1,000 baseline alloying work units for a batch consuming 980 kg iron and 20 kg carbon and yielding 1,000 kg steel. The initial line can perform 1,000 units per day with ten reference-proficiency industrial operators. Five such operators support half its controlled capacity. Twenty do not exceed its installed rating. At half proficiency, twenty operators are needed to support that same rating. These are initial game settings rather than measured real-world staffing estimates.

Effective staffing is:

`autonomous fraction + (1 - autonomous fraction) × clamp(operators × skill / reference operators, 0, 1)`

The resulting work rate is equipment rating multiplied by effective staffing and the existing tier/expansion capacity scale. Skill is measured relative to reference proficiency and is bounded by the captured record. The initial profiles have proficiency 1 and no autonomous capacity. An autonomous line can operate its autonomous share without workers but still requires real materials and electrical energy. Equipment and proficiency survive save/load, expansion, ownership changes and recipe-preserving commands.

This introduces the representation and simulation support for automation and proficiency. It does not grant researched automation upgrades, add a training command or provide a player control that freely changes installed equipment or skill. New research, purchase and training paths must use ordinary tick-validated commands.

## Shared quotes and execution

Captured equipment includes an installed parallel line count. Total rated work, reference crew and processing-interval limits scale with that count. Opening assets may commission a pool of lines without creating one facility entity for each line. Assigning workers cannot change installed line count. Source-bound processing and the water energy calibration are described in [ProcessSources.md](ProcessSources.md).

[ProductionWork](engine/src/main/java/com/spaceconquest/engine/industry/ProductionWork.java) supplies discarded immutable quotes for supported recipes. Research performance and existing foundation improvements reduce effective process work per batch; they do not bypass installed work capacity or reduce the physical bill per completed batch. Every extra batch still requires its inputs and process energy. The existing factory manufacturing-complexity gate remains authoritative.

Market demand, corporate investment estimates, public operating-readiness inspection and real production use this work model. Power allocation uses the same capacity quote, additionally bounded by currently stocked inputs or deposits. Nominal power previews without a world snapshot use the captured equipment and baseline process requirements.

The grid reserves a finite daily energy budget. Actual production then rechecks remaining stock and the owner's combined material and electricity affordability. Multiple factories cannot spend the same stock or exceed their allocated electricity. Unused reservations do not create output, debit process electricity or pay a generator. Capacity released by an input or funding shortfall is not reassigned to another factory in the same day.

Monetary production pays generators for the generated share of actual process electricity at the existing tariff. Stored battery electricity retains the existing unpriced-storage policy. Generator proceeds reach actual owner accounts and the ordinary generation receipts. Final grid consumption and storage are recomputed from actual usage. Hive facilities use allocated operators and real stock and power without monetary charges.

The older processor overloads accept already settled powered-worker counts as their supplied-power contract. The coordinated engine tick uses explicit finite energy budgets and actual payment. Use that full entry point when verifying physical power and cash conservation.

The minimum processing interval caps a continuous line's batch rate independently of crew and work rating. These first processes have subdaily intervals. This is not a persistent discrete-batch maturation queue: multi-day chemical reactions that must withhold all output until completion still need explicit in-process lots and residence-time state.

## Mixed materials and refinery products

Factories may consume multiple materials and produce multiple saleable materials and byproducts. Required work and energy apply once to the process batch. If only half a batch is feasible, every input and every output scales by that fraction. A refinery is not restricted to one feedstock or one product.

The iron smelting test follows 500 kg ore plus 56.25 kg carbon through half a batch, producing 350 kg iron and 206.25 kg carbon dioxide. Both outputs retain separate inventories and market transfers while sharing the same 2,000 kWh process bill.

The rare-earth refinery now resolves its batch receipt from elemental intake and application-defined recovery fractions. Unrecovered material and reagent elements remain in saved tailings. The same work, energy, partial-batch and multi-output settlement applies. Fresh bulk material captures standard catalog composition at intake; retained residue keeps its actual remaining assay. Geological ore-grade variation and tradeable assayed lots remain further work. See [RefineryRecovery.md](RefineryRecovery.md).

Pure elemental products use their element names, such as gold and silver. Refining describes the process. Ore, concentrate, mixtures and tailings remain distinct inputs or outputs where modeled.

### Research improves byproduct recovery

Refinery applications may improve recovery of secondary materials as well as the primary product. Better separation, capture or purification can turn previously discarded material into useful outputs. Recovery is a separate capability from work rate, automation and energy efficiency; a productivity improvement must not multiply every material yield automatically.

Each supported product has an application-defined recovery fraction bounded between zero and one. Recovered quantity equals the amount of that substance available in the actual feedstock multiplied by the installed process's recovery fraction. Improvements increase useful output by reducing the corresponding unrecovered stream. Account for reagents and chemical transformations where the output is a compound rather than the same element.

The first supported refinery uses 1,000 kg feedstock containing 2 kg silver and yields 0.5 kg silver at 25% recovery or 1.5 kg at 75% recovery. The extra kilogram comes from material previously left in the residue. These are provisional game values rather than a measured real-world ore grade.

Research may unlock recovery of an additional trace element or raise an existing recovery fraction. Applications define any additional process work, electricity, reagents, purification requirements and compatible equipment. Changes requiring new machinery need a paid installation or conversion; discovery alone does not replace installed equipment. Software or operating-method improvements may apply to compatible equipment when their application explicitly permits it.

Recovered byproducts become actual factory inventory and use the ordinary funded market-transfer path. A useful output does not imply a buyer or free transport. Previously discarded material can be reprocessed only if a real retained waste or tailings lot still exists with its remaining composition, ownership and quantity. Reprocessing consumes that lot and pays its own work and resource costs, preventing repeated recovery from the original feedstock.

These recovery rules are implemented for rare-earth separation, including gold and silver products, retained compositional tailings and paid reprocessing. Other refineries still need their own profiles and physical definitions. Improved methods explicitly retain compatible equipment; research requiring different machinery still needs a paid installation path.

## Equipment and construction

New supported facilities capture their initial equipment from the process definition. Missing equipment fields in version 1 saves obtain the corresponding baseline equipment; explicit saved equipment retains its rating, automation and proficiency. Physical equipment cannot be obtained by changing a generic facility's recipe. Typed facilities reject recipe changes into incompatible processes and legacy factory types. Actual paid conversion projects remain future work.

The industry construction list exposes iron smelting, steel alloying and rare-earth separation and selects the correct operator profession for supported processes. Empire industry cards display process type, baseline batch work and energy, equipment capacity, proficiency and autonomous share from immutable data. The refinery panel also shows the active recovery method, its costs, products and retained residue.

The migrated iron-chain applications now define their construction bills, abstract work and provisional daily construction capacity. Pending projects retain legacy serialized field names containing `Hours`; the migrated values are not person hours. Shared construction crews, shipyard assembly, other generic factory bills, specialized machine purchases, maintenance consumables and factory-family conversions remain tasks identified by the [production review](TechnologyProductionReview.md).

## Initial physical calibration

The smelting profile uses 4,000 kWh per 1,000 kg ore and 112.5 kg carbon, yielding 700 kg iron plus its existing byproduct. Steel alloying uses 1,000 kWh per 1,000 kg steel. Combining the iron needed by the steel recipe gives approximately 6.6 MWh per tonne of steel before mining, transport and other upstream costs.

That is an initial electrical-equivalent game approximation. [worldsteel reports 20.95 GJ/t, about 5.82 MWh/t, of total crude-steel energy in 2024](https://worldsteel.org/wider-sustainability/sustainability-indicators/). Its industrial process boundary and thermal/electrical energy split differ from these simplified game recipes. The comparison establishes an approximate scale rather than a validated engineering model. Ore extraction retains its previous baseline energy pending a specific mining calibration. Operator counts and equipment/construction prices are still provisional; no complete iron-chain profitability or campaign balance is claimed.

## Verification

ProductionWorkTest checks equipment ceilings, skill-dependent staffing, automation without unpaid human work, research and process compatibility, continuous processing-rate limits, proportional multi-input/multi-output production, finite stock and power sharing, combined material/power funding, generator payment, grid consumption, battery treatment and disk reload. A normal engine tick also verifies Hive staffing and material transfer with real generation.

ProductionWorkLifecycleTest starts with actual funded scientist capacity, researches smelting, stages material-funded factory construction, removes opening iron stock and follows a finite ore deposit into smelting and market steel through repeated disk reloads. Separate paid-power tests verify electrical payment; the chain fixture explicitly supplies its electricity budgets. The lifecycle test also checks that recipe commands cannot replace paid machinery and that expansion retains captured equipment and skill.

The existing audit continues to probe every industrial recipe's physical output and market transfer with supplied inputs. Its cost table now reads explicit per-batch energy for work-unit recipes. Broader regression includes research optimization, power investment, public operating accounts, industry commands, engine ticks, saves and frontend empire rendering. This is a scoped correctness check rather than the deferred full campaign audit.

The initial work-unit scope passed 153 tests across 25 engine, control and frontend suites with no failures, errors or skipped tests. Subsequent refinery verification adds elemental conservation, research, retained-lot reprocessing, market settlement, saves and snapshot UI checks as described in [RefineryRecovery.md](RefineryRecovery.md). The full Maven suite and campaign balance audit were not run.
