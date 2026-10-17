# AI power investment

Empire AI now turns repeated local electricity deficits into public power-plant construction or expansion. The controller submits commands and the command queue revalidates them against current state before staging a project. No electricity, fuel or materials are granted by the planning step.

## Shortage observation and selection

EmpirePowerPlanner observes negative grid balances on controlled planets and moons. An application defines how many consecutive daily observations are needed; the current baseline is two. Repeated snapshots in the same turn count once. A resolved deficit, a skipped observation day or a turn rewind resets the relevant window. Observation memory belongs to the controller, so reopening a campaign begins a fresh window rather than restoring an unverified shortage history.

The planner compares researched, environmentally feasible plants and expansions using useful additional generation relative to committed construction cash, opening operating capital and one day's fuel cost. Current application performance and complexity modifiers affect output and the required tier. Expansion requires a matching state-owned plant with a full paid staffing receipt and funded operating account. New plants require unallocated working-age population under the provisional headcount check. Actual payroll remains authoritative.

The selected body's pending power projects prevent another project there, including when a corporate plant is already under construction. The planner proposes one candidate per update. Commands also reject missing research, lost territorial control, cleared shortages, unavailable materials, insufficient treasury funds, missing fuel and inadequate operating reserves. New projects need an affordable full local material bill in addition to their opening operating capital. Construction buys materials later and can pause if conditions change.

An active brownout also checks reconstructed demand from actual paid facility workers, household needs and launches. A delivered grid balance of zero after load shedding therefore does not hide a physical shortage. Unpaid job allocations do not add forecast industrial demand. [MultiSystemEconomy.md](MultiSystemEconomy.md) follows this through funded expansion and useful output in the normal daily loop.

## Application technology values

Each of the eight generation applications in `engine/src/main/resources/technologies.json` now contains a `powerPlant` definition. It supplies kilowatts per tier, required workers and profession, foundation gate, fuel material and daily quantity, environmental rule, atmospheric solar transmission, per-tier construction materials, separate construction and expansion work, opening operating capital and shortage observation count. The initial values preserve existing output, fuel, material and capital defaults, with 500 hours for construction and 200 hours for each expansion tier. No full balance pass was performed.

PowerPlantCatalog reads those definitions. PowerGenerationProcessor uses the same output, fuel and environment data that investment queries use. BuildFacilityCommand, ExpandFacilityCommand and ConstructionMaterialCatalog use the plant's work and materials. Ordinary public plant construction receives the application-defined operating capital. Research modifiers still scale construction cost, work, complexity and generation. Resource edits take effect when the application catalog is loaded on restart.

## Bounded verification

AIPowerInvestmentTest follows repeated shortage observations, queued construction, duplicate rejection, actual material purchases, a partial-construction disk checkpoint, paid staffing and measured generation that clears a brownout. It also verifies a staffed plant's incremental expansion quote, combustion fuel consumption, environment and staffing blockers, stale command rejection and controller integration.

PowerPlantApplicationDataTest changes application JSON values in an isolated catalog snapshot and verifies actual output, partial staffing and paid fuel consumption. Existing generation, research eligibility, industry commands and research-to-production tests cover the surrounding paths.

## Remaining limits

This planner serves public plants in societies with the existing paid household economy. Operating-capital replenishment is now implemented for viable public facilities through [AIOperatingCapital.md](AIOperatingCapital.md). Hive grid allocation, orbital generation investment and battery construction remain separate work. New-plant staffing estimates use working-age population minus allocated facility jobs; they do not predict profession training, competing public jobs or future migration. Completion still uses provisional facility construction work capacity and plants remain subject to actual payroll and fuel after commissioning. Ranking uses current conditions rather than a full investment return forecast. Expansion retains the existing temporary output penalty. No campaign-wide economy or weapon balancing was performed.
