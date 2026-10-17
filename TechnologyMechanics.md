### Technological application and research mechanics

The research system handles scientific progression through a non-deterministic, application-driven model. Rather than utilizing a static, linear tech tree where outcomes are completely predictable, completing a research cycle unlocks localized possibilities for practical application. Evolving a technology requires balancing structural complexity constraints against a stochastically weighted breakthrough loop.

#### Current implementation

Completed application research retains its rolled outcome until the owner makes a decision. Optimized success enables performance or miniaturization; other outcomes can be accepted or discarded. Accepted improvements accumulate output and cost multipliers and complexity shifts across research cycles, with application complexity floored at 1. Each outcome can be consumed once. Refining a researched application starts a project at its catalog refinement cost and requires actual available scientists. Pending decisions block another project for that application. Outcomes, accumulated modifiers and resolved cycle counts survive save/load.

Daily research runs after public payroll settles. Non-Hive capacity comes from paid public scientist households on controlled populated bodies, bounded by current working-age residents and education funding. Hive capacity comes from physical working-age scientist cohorts bounded by the system's scientist allocation. Empty systems no longer receive a minimum scientist quota. Requested assignments persist during shortages; available workers are shared proportionally across eligible projects. Output uses the working scientists' species intelligence and local education level. Losing population, ownership or funding stops the affected work without discarding earned progress.

The technology page can update an existing assignment even when every scientist is reserved. Assigning zero pauses a project; cancellation discards its progress. These actions stage validated tick commands. Completion rechecks catalog identity and prerequisites: unknown saved targets are removed and known ineligible projects remain suspended. The summary shows funded capacity and requested assignments, while technology cards show actual research costs and application unlocks.

All catalog foundations and applications have explicit `researchPoints`; applications also have `refinementResearchPoints`. These values are independent of manufacturing complexity and material or work costs. Existing projects retain their original point quote across reassignment, pause and save/load. Missing fields in older catalogs use the defaults in `engine/src/main/resources/research_policy.json`. That resource also defines outcome probabilities, improvement multipliers, optimization paths and exchange grant and speed values. The probability table and path percentages below describe current defaults. Edit resource values for future balancing; this change does not attempt full balance.

Empire AI develops eligible applications as well as foundations, uses its actual available scientist capacity, resumes paused projects and resolves pending outcomes through the same commands as players. It prioritizes missing application discoveries on owned public or corporate factories, local power deficits, empty inputs at paid factories and ammunition shortages on actual living fleet ships. Fuel shortages also identify producing recipes. Power choices use the same environmental feasibility query as generation. War still raises combat applications and existing fleet components provide additional context. These choices do not supply stock, pay construction or retrofit hardware.

Foundation research follows prerequisite paths toward applications with operational consumers, rather than selecting unrelated fields just because they are cheap. Urgent refinements can compete with new discoveries; otherwise useful discoveries and their prerequisites precede general refinement. Pending optimized outcomes favor performance for urgent needs when the result fits current affected factory capacity, otherwise miniaturization. Flawed outcomes and improvements that would exceed existing factory capacity are discarded. Scientist limits, catalog point quotes and outcome values are unchanged. The [research era progression review](ResearchEraProgression.md) records bounded starting-era checks and remaining limits.

Research projects retain their original actor, decision reason and intended application goal. AI discovery, prerequisite and refinement choices explain the need observed when chosen; reassignment and pause do not overwrite that original explanation. The technology page separately queries current conditions for the goal, so a resolved power shortage does not rewrite the historical choice. Current funding and completion estimates remain separate from strategic context.

Successful commands and actual completion append immutable research receipts. These cover starts, assignment changes, pauses, resumes, cancellation, completion, accepted or discarded outcomes and selected optimization paths. Failed commands, unchanged assignments and repeated consumed outcomes add no receipt. Each empire retains its latest 32 events and the page displays the latest 12 for its owner. Active project explanations survive history eviction. Receipts and project explanations persist in the version 1 format as optional fields.

Normal play does not expose other empires' research. Developers can enable a read-only AI research inspector with the JVM property `-Dspaceconquest.debug.research=true`. It selects an empire from the supplied snapshot and shows its active choices, current context and recent events without research action controls. This is a debugging view rather than a diplomatic information source. Focused tests verify owner filtering, historical reasons versus changed conditions, bounded immutable receipts, actual outcome decisions and disk reload; no additional campaign run was required.

An empire can voluntarily share a known target with an ally under an existing commercial alliance or integrated federation. The recipient must satisfy research prerequisites. A valid route offers one opening progress grant when a new discovery project starts and accelerates subsequent funded scientist work; it grants no immediate unlock. Resume and reassignment do not repeat the grant, refinement gets no opening grant and multiple providers use the strongest offer rather than adding bonuses. Source knowledge, receiver prerequisites and diplomatic eligibility are rechecked each tick. Either participant can end a route. Ending or suspending one preserves previously earned progress. Routes and project progress survive save/load; separate paid exchange negotiations remain future work.

Reverse engineering is disabled in both commands and the player page until an owned physical salvage inventory and one-time consumption receipts exist. Caller-supplied component claims no longer award points. The salvage mechanics below remain design targets.

The [application integration audit](ApplicationIntegrationAudit.md) covers all 121 applications: 13 use application-defined hardware, 60 have partial operational support and 48 remain planned. The technology page displays the same consumer classification and explains the supported subset. Descriptive affected factors are design intent rather than automatic global modifiers. AI skips applications without an operational consumer. Baseline ship propulsion and reactor equipment retain their existing foundation gates; their applications improve newly captured variants. Future applications remain available for deliberate player research with their lack of current effects disclosed.

The research page also shows blocked foundations and applications with named missing prerequisites, rather than hiding them. Application benefits identify actual factory outputs, power plants, installed hardware and linked ship components from their operational catalogs. Foundation discovery opens application research; it does not grant those application discoveries automatically. Disabled start and refinement actions explain existing projects, exhausted assignments, absent funded scientists or unresolved outcomes. Starting research reports a queued request whose assignment is revalidated next tick.

Active projects show funded scientist equivalents, points per day, the currently eligible exchange speed bonus and estimated whole days remaining. ResearchFunding supplies the same staffing, species, education, ministry, speed and strongest-route calculation to both daily progression and snapshot previews. Paused, unfunded or prerequisite-blocked projects have a reason and no finite completion estimate. Completed eligible projects await next-tick unlock and outcome processing. Estimates assume current conditions persist; they use the last settled payroll and can change when the next daily economy pass settles staffing and funding. Previewing an exchange does not award another opening grant or roll an outcome.

Catalog-backed facility construction, tile placement, recipe changes, production and grid generation require the owner's application discovery and its parent and explicit prerequisites. Corporate facilities use their empire's knowledge. A weapon or future application cannot be installed as a factory recipe. Recipe changes require an operational facility with adequate manufacturing capacity. Legacy recipe IDs outside the application catalog retain their existing prerequisites; generic smelter, cargo-terminal and surface-yard construction still use the industrial production foundation. Generated empires receive the known catalog applications of their prebuilt starting industry, without unlocking unrelated future applications. Existing saves with unsupported research gaps pause affected production until the missing application is researched.

Existing researched applications without recorded outcomes retain their earlier path-selection behavior until they complete a refinement cycle. Their path selection replaces the previous baseline variant. New recorded outcomes require the matching decision rather than allowing free repeated upgrades. Selecting a path also requires the parent technology and explicit application prerequisites.

Performance increases facility throughput and power generation; miniaturization retains the current output while reducing costs and complexity. New construction and expansion quotes use the accumulated material and work multiplier. Existing projects keep their original quotes. Extra industrial batches consume their full recipe inputs and deplete discovered deposits normally.

The technology view displays the selected path, effective unit work cost and adjusted application complexity. Recognized main drives now link to their research applications. The player ship designer applies optimized thrust and component complexity, then captures a manufacturing cost multiplier weighted by each linked module's share of blueprint dry mass. New ship orders and their previews use that captured multiplier. Operational shipyards enforce complexity limits from their applications in `technologies.json`: surface yards start at 5 and gain one per tier, orbital grids support 7 and capital slipways support 10. Path changes do not alter existing blueprint stats or quotes. AI can stage a paid yard upgrade and a new researched blueprint revision when needed; existing ships and orders remain frozen.

The ship designer's fission reactor links to `fission_reactors`. Performance increases its power output by 15%; miniaturization keeps baseline output. Both paths adjust component complexity and its mass-weighted contribution to the manufacturing quote. The blueprint captures the resulting net power balance. Ship component baseline stats remain provisional and are separate from the application's catalog complexity.

Corporate procurement captures current application development for each new proprietary order and reuses an equivalent captured blueprint when available. It selects a fission drive only with nuclear fission research, otherwise a chemical rocket. Empire AI orders rebuild the selected component choices with current application modifiers when those modifiers have changed, subject to design validity, yard capacity and material affordability. Existing blueprints, pending quotes and built ships retain their captured values. This does not automatically select new weapon loadouts or retrofit ships.

Corporate factory investment filters researched recipes, environmental requirements, discovered deposits and pending projects before ranking profitable opportunities. Consumer manufacturing can select non-extractive recipes from the application catalog, including ammunition. A matching operating factory receives an expansion rather than a duplicate factory. Projects charge startup capital and purchase actual construction materials over subsequent turns; paid workers, recipe inputs and available grid power still gate useful output. See [ResearchInvestmentHandoff.md](ResearchInvestmentHandoff.md) for the bounded integration checks and remaining limits.

Empire AI also responds to repeated deficits on controlled planetary and lunar grids by staging public plant construction or expansion through revalidated commands. Plant output, staffing, fuel, environmental rules, construction materials and work, operating capital and observation thresholds come from each generation application's `powerPlant` definition. Research, available local materials, treasury funds and operating reserves gate the proposal; actual construction, payroll and fuel determine delivered electricity. Existing staffed plants can receive an incremental expansion. See [AIPowerInvestment.md](AIPowerInvestment.md) for the bounded checks and current planning limits.

Public factories and plants with an `operatingReserve` application policy can receive AI treasury recovery when cash is the remaining operational blocker. Reserve days, refill thresholds, maximum facility cash, retained treasury cash and public maintenance are data values. Snapshot readiness checks research, capacity, potential trained workers, local inputs or fuel, physical power and useful demand. A queued command rechecks eligibility and transfers existing credits; subsequent payroll and purchases remain authoritative. Recovery precedes additional power construction. See [AIOperatingCapital.md](AIOperatingCapital.md) for receipts, bounded tests and limits.

Cargo vaults and corporate large holds link to the provisional `pressurized_cargo_holds` application under interstellar shipping optimization. Their capacity, complexity and cost changes are captured in new blueprints. Stasis pods link to `cryogenic_stasis_pod`: performance increases supported passengers, rounded down to whole seats. Captured stasis capacity is enforced during passenger loading and mode changes; older blueprints retain 100 passengers per pod. Later refinements do not change existing ship capacities.

General facilities use provisional specialized manufacturing limits from `facility_manufacturing_limits.json`. Tier 1 supports the application's baseline complexity; each extra tier adds one level. Production and fuel demand pause when the selected variant exceeds capacity. New public, tile and corporate facility projects quote the minimum tier needed for their selected variant. Existing projects retain their target tier and quote; they may need a later expansion if application complexity rises. Recipe IDs absent from the application catalog use a baseline complexity of 1. These rules can be overridden per application in the resource catalog.

The recognized drive, reactor, cargo and stasis components now have application links. Other future component catalogs, detailed material receipts, variance-factor balancing and research-dependent nanotechnology tiers remain future work. The sections below describe the target mechanics.

#### 1. Core technology and application schema

The game engine segregates scientific knowledge into macro **Foundational Areas** and granular **Technical Applications** using the following data-driven configurations:

##### Foundational technology area properties
Every primary science discipline (such as *Atomic Fission*, *Electricity* or *Nanotechnology*) is tracked as a structural root anchor:
- **Id:** Unique string identifier (e.g., `nuclear_fission`, `gene_technology`).
- **Name and description:** Definitive localization and narrative logs detailing the field.
- **Discovery complexity:** An integer rating mapping how challenging the root area is to discover from an unresearched state.
- **Required prerequisites:** A structural list of prerequisite foundational IDs that must be fully un-locked before this area can be actively analyzed.

##### Technical application properties
Once a foundational area is discovered, it grants immediate or researchable access to practical manufacturing blueprints (such as *Fission Engines*, *Solar Power* or *Bioreactor Tissue Printing*):
- **Id and name:** Unique identifiers mapping the implementation blueprint.
- **Required technologies:** The precise level threshold of foundational areas required to execute manufacturing.
- **Affected systemic factors:** A strict key-value list mapping the exact attributes modified by the application (e.g., `powerProduction`, `combatStrength`, `shipSubLightVelocity`).
- **Base manufacturing unit cost:** Measured as a flat resource receipt from the **60-80 materials list** and a flat allocation of workforce hours per unit.
- **Production complexity:** An integer rating defining the infrastructure tier required to build the item.


#### 2. The weighted variance randomness system

To enforce realistic engineering limits, researching an upgrade to an existing technical application triggers a hidden, turn-based **Weighted Variance Simulation**. The research does not automatically grant a uniform increase; instead, it rolls against a probability table to determine the precise outcome modifier ($R_{\text{variance}}$):

| Research outcome title | Probability | Base effect modifier | Material/work cost modifier | Production complexity shift |
| :--- | :---: | :---: | :---: | :---: |
| **Critical breakthrough** | $0.05$ | $+20\%$ | $-15\%$ | No Change |
| **Optimized success** | $0.45$ | *Player Selects Path A or Path B (See Section 3)* | Variable |
| **Incremental gain** | $0.40$ | $+10\%$ | $+5\%$ | $+1$ Complexity |
| **Flawed design setback** | $0.10$ | $+5\%$ | $+25\%$ | $+2$ Complexity |

- **The flawed design dilemma:** Rolling a *Flawed Design Setback* represents a volatile prototype phase. The application technically improves its base performance output by a minor margin, but its resource costs spike severely. The player must actively choose whether to scrap the flawed blueprint iteration entirely or commit further empire research hours to refine and optimize the baseline code.

#### 3. Dual-path optimization framework

When a player achieves an **Optimized Success** or intentionally invests resource hours into refining a stable prototype, they must choose between two mutually exclusive engineering development paths:

##### Path A: Performance up-scaling (the cutting edge)
The engineering goal focus is pushing the maximum physical limitations of the technology, disregarding economic overhead or factory scaling limits:
- **Mathematical effect:** $\text{New Output Effect} = \text{Current Effect} \times (1.15 \times R_{\text{variance}})$
- **Economic cost penalty:** $\text{New Unit Cost (Work/Mat)} = \text{Current Cost} \times (1.20 \times R_{\text{variance}})$
- **Complexity inflation:** $\text{New Production Complexity} = \text{Current Complexity} + \text{Round}(1 \times R_{\text{variance}})$
- *Strategic result:* Creates an elite, high-performance module or facility. It executes operations with extreme efficiency but demands the rarest elements from your vaults and can only be manufactured in the empire's most sophisticated, high-tier production complexes.

##### Path B: Efficiency down-scaling (miniaturization and mass production)
The engineering goal focus is streamlining the assembly line layout, optimizing material feeds and rendering the technology reliable and accessible:
- **Mathematical effect:** $\text{New Output Effect} = \text{Current Effect} \times 1.00$ *(Performance remains constant)*
- **Economic cost reduction:** $\text{New Unit Cost (Work/Mat)} = \text{Current Cost} \times (0.85 \times R_{\text{variance}})$
- **Complexity deflation:** $\text{New Production Complexity} = \text{Current Complexity} - \text{Round}(1 \times R_{\text{variance}}) \quad [\text{Minimum Cap: 1}]$
- *Strategic result:* Streams an advanced technology down into a cheap, mass-producible commodity. Even basic, low-complexity frontier mining stations or rustic moons can independently churn these units out using common refined elements.

#### 4. The production complexity ceiling
Every technical application carries a strict **Production Complexity** value that interfaces directly with your planetary and space station assets. To manufacture an item, the local space entity must possess a production facility or shipyard module whose complexity rating meets or exceeds the item's requirement:

- **The tier limitations:** A newly researched *Warp Drive* or *Antimatter Power Facility* initializes at a massive **Complexity Level 9**. It can only be built inside a hyper-advanced, expensive capital mega-engineering slipway or automated foundry matrix.
- **The miniaturization loop:** If an empire needs to field deep-space fleets from low-tech frontier colonies, they must dedicate research cycles to *Efficiency Down-Scaling*. By dragging the Warp Drive's complexity level down from a $9$ to a $3$ through iterative down-scaling upgrades, they turn it into a compact, easily fabricated module template, enabling simple shipyards to mass-produce warp-capable vessels.
- **The workforce factor:** The absolute baseline turnaround time for completing a research project is heavily modified by the allocation and average training efficiency of the local `scientist` profession, scaled directly by the *Intelligence* attributes of the resident species.

### Reverse engineering, salvage and technology exchange
Technology cannot be instantly materialized or absorbed through a magical click. Discovering advanced foreign hardware, recovering battlefield debris or exchanging data packets with alien civilizations does not grant an empire an unresearched blueprint outright. Instead, foreign assets are treated as high-value physical or intellectual reference payloads that inject a variable volume of **Progress Vectors** into an empire's research grid.

#### 1. The reverse engineering paradigm: intact vs. destroyed salvage
When a fleet secures foreign hardware, the game engine calculates the information yield based on the physical state of the asset at the moment of capture. Intact components provide exponentially deeper insights than shattered debris:

- **Intact captures (maximum information yield):**
    - *Triggers:* Forcefully boarding and capturing an enemy ship via the `soldier` profession or invading and capturing a planetary surface area slot containing operational foreign production facilities.
    - *Systemic factors:* Grants the maximum possible injection of progress points. Because the micro-circuitry layouts, fuel containment lines and internal settings remain completely operational, your `scientists` can run active diagnostic passes over the hardware.
- **Shattered wreckage (fragmented data yield):**
    - *Triggers:* Assigning a *Construction Ship* or specialized salvage platform to filter through the debris fields of a standard space battle.
    - *Systemic factors:* Yields a highly restricted, minor injection of progress points. The components are severely warped, melted or chemically degraded, forcing research labs to reconstruct missing systems using brute-force atomic sounding.
- **The salvage volume multiplier:** Finding *more* units of a specific component scales your research speed exponentially. Capturing a single intact enemy *Plasma Blaster* provides a basic structural reference; capturing or salvaging ten of them across a prolonged campaign allows multiple research laboratories to run destructive stress tests simultaneously, rapidly compressing the time required to crack the technology.

#### 2. The biochemical translation barrier
Because different species operate on entirely incompatible physical and mental architectures, directly inserting a foreign module template into your own ship designer interface is structurally impossible:

- **The design friction:** A high-complexity component built by the **Silicon Core** is scaled for a crystalline, high-gravity lithovore workforce and runs on unique material tolerances. It cannot be bolted onto a **Human** individualist starframe without tearing the lightweight hull apart or short-circuiting the oxygen-based avionics networks.
- **The research translation modifier:** When analyzing foreign technology, the reverse engineering speed is modified by the biochemical distance between the creating race and the analyzing race. If a Human lab analyzes an *Advanced Fusion Propulsion Drive* built by another carbon-based species, the translation modifier is neutral ($1.0$). If analyzing a component built by a *Synthetic Machine* or a gaseous *Ammonia Entity*, a severe translation penalty is applied. This penalty reflects the massive workforce hours required to completely rebuild the software code, interface ports and safety parameters to match your species' own biochemical metrics.

#### 3. Interstellar technology exchange and foreign accords
When sovereign empires maintain peaceful diplomatic relations (such as a *Commercial Alliance* or *Integrated Federation*), they can legally negotiate **Technology Exchange Accords** inside local commerce hubs:

- **No direct blueprint transfers:** Exchanging technology data packages does not instantly unlock a module or a facility blueprint for the receiving empire. Instead, it functions as a targeted intellectual catalyst.
- **The progress injection loop:** When a foreign ally shares their research data for an application like *High-Impulse MPD Thrusters*, it establishes a permanent **Research Sub-Route** inside your empire's technology screen. The shared data automatically pre-populates a significant percentage of the target application's progress bar (e.g., injecting 30% to 50% of the required data vectors).
- **Accelerated learning matrix:** While actively researching a shared technology sub-route, your local `scientist` profession receives a significant training speed modifier. Your team is effectively building upon a proven theoretical framework rather than starting from an unmapped, primitive state, allowing your factories to hit the physical manufacturing phase much faster.
