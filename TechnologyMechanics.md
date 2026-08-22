### Technological application and research mechanics

The research system handles scientific progression through a non-deterministic, application-driven model. Rather than utilizing a static, linear tech tree where outcomes are completely predictable, completing a research cycle unlocks localized possibilities for practical application. Evolving a technology requires balancing structural complexity constraints against a stochastically weighted breakthrough loop.

#### 1. Core technology and application schema

The game engine segregates scientific knowledge into macro **Foundational Areas** and granular **Technical Applications** using the following data-driven configurations:

##### Foundational technology area properties
Every primary science discipline (such as *Atomic Fission*, *Electricity* or *Nanotechnology*) is tracked as a structural root anchor:
*   **Id:** Unique string identifier (e.g., `nuclear_fission`, `gene_technology`).
*   **Name and description:** Definitive localization and narrative logs detailing the field.
*   **Discovery complexity:** An integer rating mapping how challenging the root area is to discover from an unresearched state.
*   **Required prerequisites:** A structural list of prerequisite foundational IDs that must be fully un-locked before this area can be actively analyzed.

##### Technical application properties
Once a foundational area is discovered, it grants immediate or researchable access to practical manufacturing blueprints (such as *Fission Engines*, *Solar Power* or *Bioreactor Tissue Printing*):
*   **Id and name:** Unique identifiers mapping the implementation blueprint.
*   **Required technologies:** The precise level threshold of foundational areas required to execute manufacturing.
*   **Affected systemic factors:** A strict key-value list mapping the exact attributes modified by the application (e.g., `powerProduction`, `combatStrength`, `shipSubLightVelocity`).
*   **Base manufacturing unit cost:** Measured as a flat resource receipt from the **60-80 materials list** and a flat allocation of workforce hours per unit.
*   **Production complexity:** An integer rating defining the infrastructure tier required to build the item.


#### 2. The weighted variance randomness system

To enforce realistic engineering limits, researching an upgrade to an existing technical application triggers a hidden, turn-based **Weighted Variance Simulation**. The research does not automatically grant a uniform increase; instead, it rolls against a probability table to determine the precise outcome modifier ($R_{\text{variance}}$):

| Research outcome title | Probability | Base effect modifier | Material/work cost modifier | Production complexity shift |
| :--- | :---: | :---: | :---: | :---: |
| **Critical breakthrough** | $0.05$ | $+20\%$ | $-15\%$ | No Change |
| **Optimized success** | $0.45$ | *Player Selects Path A or Path B (See Section 3)* | Variable |
| **Incremental gain** | $0.40$ | $+10\%$ | $+5\%$ | $+1$ Complexity |
| **Flawed design setback** | $0.10$ | $+5\%$ | $+25\%$ | $+2$ Complexity |

*   **The flawed design dilemma:** Rolling a *Flawed Design Setback* represents a volatile prototype phase. The application technically improves its base performance output by a minor margin, but its resource costs spike severely. The player must actively choose whether to scrap the flawed blueprint iteration entirely or commit further empire research hours to refine and optimize the baseline code.

#### 3. Dual-path optimization framework

When a player achieves an **Optimized Success** or intentionally invests resource hours into refining a stable prototype, they must choose between two mutually exclusive engineering development paths:

##### Path A: Performance up-scaling (the cutting edge)
The engineering goal focus is pushing the maximum physical limitations of the technology, disregarding economic overhead or factory scaling limits:
*   **Mathematical effect:** $\text{New Output Effect} = \text{Current Effect} \times (1.15 \times R_{\text{variance}})$
*   **Economic cost penalty:** $\text{New Unit Cost (Work/Mat)} = \text{Current Cost} \times (1.20 \times R_{\text{variance}})$
*   **Complexity inflation:** $\text{New Production Complexity} = \text{Current Complexity} + \text{Round}(1 \times R_{\text{variance}})$
*   *Strategic result:* Creates an elite, high-performance module or facility. It executes operations with extreme efficiency but demands the rarest elements from your vaults and can only be manufactured in the empire's most sophisticated, high-tier production complexes.

##### Path B: Efficiency down-scaling (miniaturization & mass production)
The engineering goal focus is streamlining the assembly line layout, optimizing material feeds and rendering the technology reliable and accessible:
*   **Mathematical effect:** $\text{New Output Effect} = \text{Current Effect} \times 1.00$ *(Performance remains constant)*
*   **Economic cost reduction:** $\text{New Unit Cost (Work/Mat)} = \text{Current Cost} \times (0.85 \times R_{\text{variance}})$
*   **Complexity deflation:** $\text{New Production Complexity} = \text{Current Complexity} - \text{Round}(1 \times R_{\text{variance}}) \quad [\text{Minimum Cap: 1}]$
*   *Strategic result:* Streams an advanced technology down into a cheap, mass-producible commodity commodity. Even basic, low-complexity frontier mining stations or rustic moons can independently churn these units out using common refined elements.

#### 4. The production complexity ceiling
Every technical application carries a strict **Production Complexity** value that interfaces directly with your planetary and space station assets. To manufacture an item, the local space entity must possess a production facility or shipyard module whose complexity rating meets or exceeds the item's requirement:

*   **The tier limitations:** A newly researched *Warp Drive* or *Antimatter Power Facility* initializes at a massive **Complexity Level 9**. It can only be built inside a hyper-advanced, expensive capital mega-engineering slipway or automated foundry matrix.
*   **The miniaturization loop:** If an empire needs to field deep-space fleets from low-tech frontier colonies, they must dedicate research cycles to *Efficiency Down-Scaling*. By dragging the Warp Drive's complexity level down from a $9$ to a $3$ through iterative down-scaling upgrades, they turn it into a compact, easily fabricated module template, enabling simple shipyards to mass-produce warp-capable vessels.
*   **The workforce factor:** The absolute baseline turnaround time for completing a research project is heavily modified by the allocation and average training efficiency of the local `scientist` profession, scaled directly by the *Intelligence* attributes of the resident species.

### Reverse engineering, salvage and technology exchange
Technology cannot be instantly materialized or absorbed through a magical click. Discovering advanced foreign hardware, recovering battlefield debris or exchanging data packets with alien civilizations does not grant an empire an unresearched blueprint outright. Instead, foreign assets are treated as high-value physical or intellectual reference payloads that inject a variable volume of **Progress Vectors** into an empire's research grid.

#### 1. The reverse engineering paradigm: intact vs. destroyed salvage
When a fleet secures foreign hardware, the game engine calculates the information yield based on the physical state of the asset at the moment of capture. Intact components provide exponentially deeper insights than shattered debris:

*   **Intact captures (maximum information yield):**
    *   *Triggers:* Forcefully boarding and capturing an enemy ship via the `soldier` profession or invading and capturing a planetary surface area slot containing operational foreign production facilities.
    *   *Systemic factors:* Grants the maximum possible injection of progress points. Because the micro-circuitry layouts, fuel containment lines and internal settings remain completely operational, your `scientists` can run active diagnostic passes over the hardware.
*   **Shattered wreckage (fragmented data yield):**
    *   *Triggers:* Assigning a *Construction Ship* or specialized salvage platform to filter through the debris fields of a standard space battle.
    *   *Systemic factors:* Yields a highly restricted, minor injection of progress points. The components are severely warped, melted or chemically degraded, forcing research labs to reconstruct missing systems using brute-force atomic sounding.
*   **The salvage volume multiplier:** Finding *more* units of a specific component scales your research speed exponentially. Capturing a single intact enemy *Plasma Blaster* provides a basic structural reference; capturing or salvaging ten of them across a prolonged campaign allows multiple research laboratories to run destructive stress tests simultaneously, rapidly compressing the time required to crack the technology.

#### 2. The biochemical translation barrier
Because different species operate on entirely incompatible physical and mental architectures, directly inserting a foreign module template into your own ship designer interface is structurally impossible:

*   **The design friction:** A high-complexity component built by the **Silicon Core** is scaled for a crystalline, high-gravity lithovore workforce and runs on unique material tolerances. It cannot be bolted onto a **Human** individualist starframe without tearing the lightweight hull apart or short-circuiting the oxygen-based avionics networks.
*   **The research translation modifier:** When analyzing foreign technology, the reverse engineering speed is modified by the biochemical distance between the creating race and the analyzing race. If a Human lab analyzes an *Advanced Fusion Propulsion Drive* built by another carbon-based species, the translation modifier is neutral ($1.0$). If analyzing a component built by a *Synthetic Machine* or a gaseous *Ammonia Entity*, a severe translation penalty is applied. This penalty reflects the massive workforce hours required to completely rebuild the software code, interface ports and safety parameters to match your species' own biochemical metrics.

#### 3. Interstellar technology exchange and foreign accords
When sovereign empires maintain peaceful diplomatic relations (such as a *Commercial Alliance* or *Integrated Federation*), they can legally negotiate **Technology Exchange Accords** inside local commerce hubs:

*   **No direct blueprint transfers:** Exchanging technology data packages does not instantly unlock a module or a facility blueprint for the receiving empire. Instead, it functions as a targeted intellectual catalyst.
*   **The progress injection loop:** When a foreign ally shares their research data for an application like *High-Impulse MPD Thrusters*, it establishes a permanent **Research Sub-Route** inside your empire's technology screen. The shared data automatically pre-populates a significant percentage of the target application's progress bar (e.g., injecting 30% to 50% of the required data vectors).
*   **Accelerated learning matrix:** While actively researching a shared technology sub-route, your local `scientist` profession receives a significant training speed modifier. Your team is effectively building upon a proven theoretical framework rather than starting from an unmapped, primitive state, allowing your factories to hit the physical manufacturing phase much faster.
