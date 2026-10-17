# Generic process inputs and water production

Rare-earth separation now defines construction, scaling, investment, site constraints and byproduct classification on its researchable application. Surface water treatment, ice water treatment, electrolysis and water isotope separation now also define their complete recipes, equipment, work and factory metadata in [technologies.json](engine/src/main/resources/technologies.json). Their former Java recipe definitions and named water-source checks have been removed.

## Declared input sources

`productionRecipe.inputSources` is an immutable list of material quantities per batch. Each entry declares a source, material ID and kilograms per batch. Multiple materials and multiple sources can be required together.

| Source | Availability and settlement |
| --- | --- |
| `MARKET` | Purchases the declared quantity from the local hub using actual stock and owner funds |
| `DEPOSIT` | Consumes discovered local deposits of the declared material, independent of output names |
| `SURFACE_LIQUID_WATER` | Requires a water-bearing planetary or lunar surface and supplies the declared water intake |

The purchased subset must agree with `inputsKg`; contradictory definitions fail validation. Deposit quantities are grouped by material, bounded against actual remaining mass and consumed across eligible deposits in stable ID order. Several factories share the updated settlement ledger and cannot spend the same deposit twice. A mixed process is limited by every required source rather than selecting its first product as the resource to extract.

Surface water currently uses the world's existing liquid-water presence flag. It does not deplete a finite reservoir, simulate recharge or model impurities. Its material quantity is an explicit clean-water-equivalent intake assumption. Purchased ice and water remain physical market commodities. Hydrology and assayed raw-water mixtures require further data and simulation support.

Production, source-aware power requests, public readiness checks, public construction, tile placement and corporate investment share these requirements. Gravity constraints remain application-defined. The industry panel distinguishes purchased inputs from local source inputs.

Corporate planning discovers eligible applications by output and declared business orientation, filters research and local sources and compares input and electrical cost per unit of the requested product. Input prices use the local market with catalog price fallback. Existing profit, affordability and construction checks still apply. This selection is a bounded baseline comparison rather than a complete optimization of byproduct revenue, active recovery profiles or transport networks.

## Water processes and physical costs

| Process | Intake per baseline batch | Products | Electricity |
| --- | --- | --- | ---: |
| Surface treatment | 1,000 kg surface-water equivalent | 1,000 kg purified water | 0.4 kWh |
| Ice treatment | 1,000 kg purchased ice | 950 kg purified water and 50 kg unprocessed ice | 150 kWh |
| Electrolysis | 1,000 kg purchased purified water | 888.9 kg oxygen and 111.1 kg hydrogen | 6,100 kWh |
| Isotope separation | 1,000 kg purchased purified water | 888.9 kg oxygen, 110.95 kg hydrogen and 0.15 kg deuterium | 192,000 kWh |

Ice treatment's remaining ice is an explicit recoverable output rather than vanished mass. Saleable output enters factory inventory and follows ordinary funded market settlement. Unsold products remain factory stock. Partial batches scale all inputs, products, source consumption and process energy together.

Surface treatment's initial scale follows the [EPA surface-water system example](https://www.epa.gov/sites/default/files/2015-04/documents/epa816f13004.pdf): 1,500 kWh per million US gallons is approximately 0.40 kWh per cubic metre. Assuming one tonne per cubic metre gives the game's 0.4 kWh per tonne. The EPA boundary includes conveyance and distribution as well as treatment; the game uses one coarse process allowance and does not reproduce that service network.

Electrolysis uses about 54.9 kWh per kilogram of hydrogen at the declared output. That is close to the [DOE PEM electrolysis system status of 55 kWh/kg hydrogen](https://www.energy.gov/cmei/fuels/technical-targets-proton-exchange-membrane-electrolysis). This supports an initial electrical scale, not a validated factory price or a complete model of water purification, compression and thermal management.

The ice allowance is provisional and does not vary with feedstock temperature or heat recovery. Isotope separation retains its previous 192,000 kWh-per-batch electrical equivalent and remains uncalibrated. Staffing, equipment prices and construction bills also remain provisional. The declared energy is paid for each actual batch instead of applying a fixed load to arbitrarily large worker-driven output.

## Installed parallel equipment

`ProductionEquipment.parallelLines` captures the number of installed identical lines. Rated work and reference crew scale with that count. Each line retains the application's processing-interval limit. Automation and proficiency affect supported work rate while material and energy requirements remain proportional to completed batches.

Opening workforce plans commission the corresponding line count into one immutable facility equipment pool. They no longer create a separate facility record for every line. Assigning more workers to an existing operating facility does not increase its installed line count. New public and corporate construction captures the application's declared starting equipment.

Refinery retained storage is 100,000 kg per installed baseline separation line in the current application. Actual capacity multiplies that application value by the captured line count. This preserves the finite storage of parallel lines when they share one facility account. Residues and market products retain separate inventories and reprocessing uses their actual remaining assay.

## Verification and remaining migration

New tests cover mixed market, deposit and surface requirements, multiple deposits per material, competing factories, dry-source rejection, proportional ice and gas outputs, installed parallel staffing, processing limits and current-format serialization. A funded research lifecycle builds a water plant, pays its physical construction bill, reloads current state and delivers produced water into the marketplace. Corporate tests cover wet and dry sites and selection of a researched alternative.

Generated first-month economy checks verify actual production after short opening reserves run out. Refinery conservation, paid power, market funding and snapshot UI regressions remain part of scoped verification. Older development-save compatibility is outside the current scope.

Scoped verification passed 218 tests across 37 engine, control and frontend suites with no failures, errors or skipped tests. A final focused check also validates that invalid or insufficient mixed-deposit requests cannot partially consume or create resources. The full Maven suite and full campaign balance audit were not run.

The initial water migration brought eight of the then 53 industrial recipes to complete application-owned process and factory definitions. Subsequent extraction and metal migration is recorded in [MetalProduction.md](MetalProduction.md). Remaining name-based power priorities, generic infrastructure allowances and parts of opening workforce planning are recorded in [FactoryApplications.md](FactoryApplications.md).
