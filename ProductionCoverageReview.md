# Production coverage review

Implemented on 16 October 2026. The catalog now contains 187 researchable applications, 123 materials and 100 production recipes: 96 factory processes and four cultivation processes. All production recipes own their infrastructure, process work, installed equipment, input/output specifications and operating policies through application data. This completes the 13 legacy recipe migrations and four partial ammunition definitions identified in the earlier factory review.

The generated [coverage report](ProductionCoverage.md) records construction and input paths, elemental checks, missing material sources and applications without operational consumers. The [catalog audit](ProductionCatalogAudit.md) retains per-recipe resource and baseline cost figures. These checks do not establish profitability or automatic campaign access to every deposit, research node or facility.

## Application-owned production

Propellant processing, consumer goods, luxury goods, fusion pellets, advanced synthesis and ammunition now use the same generic typed-work execution as the other factories. Java propellant/refinement recipe tables and consumer-product investment mappings have been removed. The older refinement API projects application data. Factory selection and worker professions come from process definitions; generator professions come from power applications. An unconfigured generic smelter is no longer a buildable replacement for researched process equipment.

New declared paths cover 13 finite resource extractions, 12 composition-aware separations and nine fabrication processes. These include tungsten and manganese, cobalt and arsenic, lithium, lead, tin, zinc, magnesium, beryllium, phosphorus, gallium, individual rare earths, sodium, graphene, carbon nanotubes, ceramics and semiconductor materials. Gallium has a declared aggregate ore source rather than being recovered from feedstock containing no gallium. Grades, yields, work, electricity and construction coefficients remain provisional model definitions.

Separation uses the existing elemental refinery executor. It retains unrecovered mass in facility-owned tailings and can produce multiple metals from one feedstock. It does not assume that all coproducts sell. No material-specific execution branch was added.

## Material accounting corrections

The migrated ammunition processes use refined tungsten rather than treating raw tungsten ore as pure metal. Packaged ammunition and consumer-goods compositions now reflect their declared input bills. Metamaterial and quantum spin-glass recipes consume the elements present in their outputs. Boron nitride consumes enough boron-bearing ore. An antimatter containment cell is explicitly empty containment hardware and does not manufacture antimatter.

Water electrolysis and isotope separation now match the catalog's 11% hydrogen and 89% oxygen water composition. The existing isotope keys are normalized to their parent element for the audit's elemental balance check; this is not a nuclear reaction simulator.

Outdoor cultivation declares atmospheric carbon dioxide and nitrogen intake alongside its existing purchased nutrients and local water sources. The shared source model requires an atmosphere, while the cultivation application retains its atmosphere and site restrictions. Atmospheric intake is not purchased stock or an infinite geological deposit. Global atmospheric depletion, detailed gas concentrations and crop-specific nitrogen fixation are not simulated. Enclosed farms retain their purchased utility inputs.

## Installed capacity and construction

Making large opening factories' process electricity explicit exposed tens of thousands of identical generator records in the opening scenario. Parallel generation now uses the existing immutable equipment pool. Application ratings determine aggregate crew, fuel and output. Capital, maintenance and opening operating cash scale with installed units. The same installed-unit count survives ordinary snapshots and current-format saves.

Expansion bills and work now cover every installed equipment line, including pooled generators. Player and corporate projects and AI power quotes use the scaled material requirements. Expanding a pooled facility cannot obtain all its additional capacity for a single line's bill. No generator output or fuel coefficient was changed to obtain a desired economic result.

## Coverage and remaining gaps

Under graph assumptions of all research, compatible sites, discovered source deposits, equipment, workers, power and funding, 108 materials have an industrial path. Allowing the declared natural materials as accessible ship-mining sources raises the conditional upper bound to 117 of 123. Every production recipe's purchased inputs and construction materials have an industrial path.

Six bulk materials remain outside that upper bound: fluoride tailings, hyperdense neutronium, tachyon condensate, programmable matter, polymorphic nanofluid and radioisotopic thermoelectric matrices. Fluoride-rich elemental residue already remains in refinery tailings but is not sold as a separate bulk commodity. The other five need defined processes or feedstocks. They are reported as gaps rather than receiving invented production capabilities.

Application support metadata remains distinct from producibility. Mining hardware and declared shipyard capabilities now report their real consumers. Applications without operational consumers remain explicitly planned. Launch capabilities and service construction gates were subsequently migrated; see [Infrastructure applications](InfrastructureApplications.md). The named opening scenario lineup remains migration work. This pass does not claim that every descriptive technology effect is implemented.

## Verification

Validation on JDK 27 passed 145 focused tests across 26 suites in the affected engine, control and frontend checks. The full repository suite was not run. Earlier three-year economics reports retain the catalog assumptions used when they were generated; they were not recalibrated in this pass.

The coverage audit checks all 100 recipes for owned definitions, known materials, obtainable purchased and construction inputs and absence of elemental creation. The physical catalog audit executes every recipe through real factory-to-market settlement with funded buyers and declared source conditions.

Command lifecycle checks cover paid discovery, construction bills, actual factory payroll, supplied settled electricity budgets, finite ore, mixed refinery outputs, retained residue and current-format reload. Consumer goods and liquid hydrogen reach market stock; tungsten ore reaches refined metal and then manufactured ammunition. Separate operating and power tests exercise actual metered electricity. Generator pooling is compared against separate units for resource and output equivalence. Profitability balancing remains deferred.
