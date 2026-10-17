# Application-defined factories

The current catalog has 100 application-owned production recipes: 96 factory processes and four cultivation processes. Propellant, consumer, advanced-synthesis and ammunition migrations are complete. See [ProductionCoverageReview.md](ProductionCoverageReview.md) and the generated [coverage report](ProductionCoverage.md). Earlier counts below describe historical migration stages.

Factories are generic input-processing-output facilities. Researchable applications own their specific capabilities. Completed migrations cover all existing extraction recipes, ordinary metal refining and alloys, rare-earth separation and four water-processing applications. Their recipes, installed equipment, work requirements, construction bills and scaling rules come from [technologies.json](engine/src/main/resources/technologies.json). [MetalProduction.md](MetalProduction.md) records the latest migration and calibration limits.

## Implemented iron chain

`mining_outpost` and `alloy_steel` are explicit researchable applications alongside `pyro_iron_smelting`. Steel alloying requires smelting discovery. Knowing the industrial production foundation alone does not unlock extraction or alloying. Generated opening industry receives prerequisite-checked knowledge of its prebuilt assets, including corporate assets. Older development-save compatibility is outside the current scope.

Each application's `productionRecipe` defines its input and output quantities, prerequisites, improvements, minimum tier and deposit-extraction requirement. Its `work` defines process identity, work per batch, energy, processing interval and initial equipment. Its `factory` defines:

- Construction work per tier, expansion work per added tier and construction work capacity per day
- Physical construction materials and the corporate investment charge
- Tier capacity growth and capacity retained during expansion
- Manufacturing complexity offset and capacity gained per tier
- Brownout shedding and process environment constraints
- Byproduct classification and eligible corporate business orientations

Application names supply the migrated factories' display names. Operator professions come from the equipment definition. Fixed recipes can consume and produce multiple materials; composition-aware recovery remains supported through the existing reusable `refineryProcess` definition. Common executors enforce finite stock, money, deposits and electricity rather than branching on iron or steel factory IDs.

The separate `production_work.json` file has been removed. `DataModelLoader.loadProductionWork()` now projects ordinary work definitions from applications. The older refinement API also projects the migrated smelting and alloying recipes from application data, including their names, byproducts and energy. It has no second Java definition of these processes.

## Construction and operation

Public construction, tile placement, expansion and corporate investment read the migrated application requirements. Supplying an arbitrary expansion-work amount cannot reduce the declared work. Corporate construction and expansion now charge materials for the actual number of tiers being added. Paid pending projects retain their quoted work and material bills through saves; installed equipment retains its captured capacity, automation and proficiency.

Construction currently advances through a provisional per-project daily work allowance defined on the application. It is not a measured worker-hour estimate or a model of competition among construction crews. Fields named `requiredWorkHours` and `accumulatedWorkHours` still have their historical Java names but hold abstract construction work for migrated facilities. The UI labels these as work units. A later shared construction-equipment and staffing model can replace this allowance without redefining the task as person hours.

Placement and production enforce declared gravity constraints when a planetary, lunar or station measurement is available. Unknown-body compatibility fixtures retain the existing permissive placement behavior. The migrated smelter's inherited minimum is 1 m/s², not 1 g. Other environment properties such as atmosphere, temperature and pollution still need definitions and consumers.

The opening-economy setup commissions parallel application-defined equipment when its workforce plan exceeds one line's crew requirement. Captured `parallelLines` pools those installed lines in one facility record with proportional capacity, crew requirements and processing limits. Refinery storage scales by installed line count. Adding workers to an operating factory does not install more lines. This corrects the refinery bottleneck that otherwise exhausted the opening rare-earth reserve and stopped consumer-goods production.

## Remaining service and scenario exceptions

All material-production recipes now have complete application definitions. Java recipe tables and consumer-product mappings have been removed. The UI discovers factory processes and operator professions from application data.

| Location | Remaining exception | Migration needed |
|---|---|---|
| Launch services and yards | Paid work and service prices consume application capabilities | Complete network investment and detailed wear; see [Infrastructure applications](InfrastructureApplications.md) |
| StartingEconomySeeder | Named opening lineup and upstream staffing calculations | Scenario selection data and generic supply-chain planning |
| Future materials and planned applications | Undefined production or operational effects | Explicit feedstocks, process requirements and consumers before claiming support |

Power and shipyards use separate application capabilities. Installed generator pools preserve aggregate staffing, output, fuel, maintenance and capital. Expansion work and material bills scale with every captured equipment line.

## Verification and next migration

The iron lifecycle test now researches all three applications using funded scientists, builds material-funded facilities, removes opening iron stock and follows finite ore into market steel across disk reloads. It also verifies that an expansion command cannot override application work or replace captured equipment. New application-data tests cover an unrecognized process ID, declared power limits, gravity constraints, immutable construction data and order-independent starting prerequisites.

Broader regression covers material and market settlement, power, research, corporate investment, generated campaign starts, saved state and frontend views. The generated first-month checks exercise actual production after the short opening reserves run out. This remains scoped verification rather than a full campaign profitability audit.

The initial iron-chain scope passed 210 tests across 35 engine, control and frontend suites with no failures, errors or skipped tests. Subsequent water and source verification is recorded in [ProcessSources.md](ProcessSources.md). The full Maven suite was not run.

The propellant, advanced-synthesis and ammunition migration is complete. Current coverage, evidence and remaining service and material gaps are recorded in [ProductionCoverageReview.md](ProductionCoverageReview.md). Physical coefficients remain provisional and profitability balancing is deferred.
