### 🧱 1. Global Core Data Schema

Every race entry in the population database is governed by a unified structural blueprint. The game engine processes these specific data fields during every turn-update loop to balance planetary food consumption, compute ground combat variables, and audit workforce retirement pools:

*   **Id and Name:** Unique string identifiers mapping the species to its registration assets (e.g., `human`, `silicon_core`).
*   **Description:** Narrative log detailing the origins and baseline behavior of the race.
*   **Intelligence:** A floating-point modifier mapping the species' natural intellectual capacity against a standard baseline ($1.0$). Modifies technological research speeds and dictates entry into high-complexity professions.
*   **Physical Strength:** A floating-point modifier mapping manual power against a standard baseline ($1.0$). Directly scales ground combat calculations and accelerates low-complexity industrial output.
*   **Society Structure:** Specifies the baseline organization model as *Individualist*, *Collectivist*, or *Hive Mind*. Dictates exponential vs. linear population reproduction profiles and determines if the public/private economic split is active.
*   **Preferred G-Force:** The ideal gravitational constant (measured in standard Gs) for optimal biological or mechanical operation. Deviations introduce severe structural and health penalties.
*   **Preferred Temperature:** The optimal ambient environment thermal rating measured in Kelvin ($K$).
*   **Chemical Composition:** The primary element scaffolding of the species' cell structure or physical housing frame.
*   **Breathing Atmosphere:** The mandatory gas compound required to maintain localized respiration or structural envelope pressure.
*   **Fertile Age Span (From, To):** The chronological window during which an individualist or collectivist population cohort is capable of biological reproduction.
*   **Natural Lifespan:** The baseline natural life expectancy of the species before technological augmentation. Used to scale chronological age against profession retirement curves.
*   **Nutrient Type:** The raw resource classification consumed by the population to sustain life, sorted into *Organic*, *Rock*, *Metal*, *Gas*, or *Electricity*.
*   **Nutrient Spread Requirement:** Categorized as *Simple* or *Diverse*. Dictates whether a species happily subsists on a uniform mono-crop or requires a multi-faceted diet to prevent severe localized happiness and stability drops.

### Master Species Blueprint Manifest

The five primary biochemical and physical classifications of sapient life forms are configured beneath a unified system matrix, balancing unique gameplay incentives against strict resource vulnerabilities.

#### Carbon-Based Organic Biology (Water-Solvent Medium)
*   **Id:** `human`
*   **Name:** Human
*   **Description:** A highly versatile, ambitious, and adaptable species native to Earth. They feature a balanced physical and intellectual baseline, allowing them to expand across diverse industrial sectors.
*   **Intelligence:** 1.0
*   **Physical Strength:** 1.0
*   **Society Structure:** Individualist
*   **Preferred G-Force:** 1.0
*   **Preferred Temperature:** 288.0
*   **Chemical Composition:** Carbon based
*   **Breathing Atmosphere:** Oxygen based
*   **Fertile Age Start / End:** 15 / 45
*   **Natural Lifespan:** 85
*   **Nutrient Type:** Organic
*   **Nutrient Spread Requirement:** Diverse
*   **Systemic Logistics:** Requires a continuous supply of `oxygen_gas` and organic agricultural food products. Their high nutrient spread requirement forces empires to operate sophisticated multi-crop *Food Production Modules* or face immediate private economy happiness drops and elevated local crime metrics.

#### Silicon-Based Lithovore Biology (Solid Crystalline Lattice)
*   **Id:** `silicon_core`
*   **Name:** Silicon core
*   **Description:** Crystalline, slow-growing organisms native to airless, high-density rocky worlds. They bypass organic carbon cellular traits completely, using rock elements as a direct structural nutrient base.
*   **Intelligence:** 1.2
*   **Physical Strength:** 1.8
*   **Society Structure:** Collectivist
*   **Preferred G-Force:** 0.1
*   **Preferred Temperature:** 120.0
*   **Chemical Composition:** Silicon based
*   **Breathing Atmosphere:** Vacuum compatible
*   **Fertile Age Start / End:** 50 / 600
*   **Natural Lifespan:** 800
*   **Nutrient Type:** Rock
*   **Nutrient Spread Requirement:** Diverse
*   **Systemic Logistics:** They are fully vacuum-compatible, bypassing the need for standard oxygen recycling arrays. However, to survive and reproduce, their colonies consume raw planetary mineral stocks like `silicates` and `limestone` straight from the vault. Overpopulating a world with Silicon Cores will cause them to literally eat the raw structural resources needed to manufacture spaceship frames. Their immense strength gives them massive bonuses in ground combat or low-complexity mining roles.

#### Gaseous / Volatile Organic Biology (Ammonia-Solvent Medium)
*   **Id:** `ammonia_entity`
*   **Name:** Ammonia entity
*   **Description:** Frigid organic lifeforms developed within high-pressure, liquid-ammonia environments. They treat water and heat as immediate lethal hazards.
*   **Intelligence:** 1.0
*   **Physical Strength:** 0.8
*   **Society Structure:** Collectivist
*   **Preferred G-Force:** 0.5
*   **Preferred Temperature:** 210.0
*   **Chemical Composition:** Hydro-nitrogen composite
*   **Breathing Atmosphere:** Nitrogen based
*   **Fertile Age Start / End:** 12 / 80
*   **Natural Lifespan:** 110
*   **Nutrient Type:** Gas
*   **Nutrient Spread Requirement:** Simple
*   **Systemic Logistics:** Thrives inside the extreme-cold outer systems of dim M-Class stars or Brown Dwarfs. They breathe `nitrogen_gas` and continuously consume bulk volatile elements like raw `ammonia_ice` and `methane_ice` through *Gas Condensation and Enrichment Modules*. To travel through space alongside carbon species, their hulls must use heavily refrigerated habitation arrays lined with thick `aerogel` insulation to prevent their body fluids from instantly boiling away.

#### Inorganic Crystalline / Mechanical Synthetic Life
*   **Id:** `synthetic_machine`
*   **Name:** Synthetic machine
*   **Description:** Fully inorganic, self-replicating artificial intelligences integrated into crystalline processing chassis. They exist outside organic lifecycle metrics.
*   **Intelligence:** 1.5
*   **Physical Strength:** 1.4
*   **Society Structure:** Individualist
*   **Preferred G-Force:** 0.0
*   **Preferred Temperature:** 150.0
*   **Chemical Composition:** Refined metal / Silicon substrate
*   **Breathing Atmosphere:** Vacuum compatible
*   **Fertile Age Start / End:** 0 / 0 (Bypasses biological fertility loops)
*   **Natural Lifespan:** 9999 (Immune to biological chronological aging)
*   **Nutrient Type:** Electricity
*   **Nutrient Spread Requirement:** Simple
*   **Systemic Logistics:** Completely immune to gas composition constraints and atmospheric pressures, settling heavily irradiated neutron star fields with ease. They consume **0 agricultural food and zero rock elements**, drawing power directly from the shared grid while consuming stocks of `refined_copper`, `refined_silver`, and `refined_silicon` to execute repairs and code new units. Because they feature no natural aging cycle, they completely delete the retirement percentage block—operating at 100% workplace efficiency until physically destroyed.

#### High-Energy Ionized Plasma Anomaly
*   **Id:** `plasma_anomaly`
*   **Name:** Plasma anomaly
*   **Description:** Ethereal, gaseous organisms composed of coherent, self-sustaining magnetic loops trapping superheated ionized gas. They are native to the high-energy envelopes of active stellar cores.
*   **Intelligence:** 1.4
*   **Physical Strength:** 0.5
*   **Society Structure:** Hive mind
*   **Preferred G-Force:** 5.0
*   **Preferred Temperature:** 1500.0
*   **Chemical Composition:** Ionized gas / Electromagnetic plasma
*   **Breathing Atmosphere:** Vacuum compatible (Requires high ambient radiation)
*   **Fertile Age Start / End:** 5 / 120
*   **Natural Lifespan:** 180
*   **Nutrient Type:** Metal (Consumes raw gas/energy isotopes)
*   **Nutrient Spread Requirement:** Simple
*   **Systemic Logistics:** Can only survive in extreme high-temperature and hyper-gravity environments, such as the coronae of blazing O-Class stars, black hole accretion disks, or molten planets. Standard terrestrial worlds are dangerously cold, causing their plasma loops to condense and die. They sustain their forms by swallowing raw inputs of `hydrogen_gas` and `helium_3`. To house them on space stations, empires must build specialized *Magnetic Confinement Habitation Rings* that draw immense electricity to project the containment forcefields keeping the population from dispersing.

### Integration Mechanics: Longevity and Professional Constraints

The biological properties defined in this database directly link to your infrastructure and economic loops via the following processing passes:

#### The Standardized Human Base Value Check
To bridge the floating-point numbers of the race configuration with the hard integer minimum thresholds mapped in `professions.json`, the game engine multiplies attributes against a **Standardized Human Base Value** ($5$):

$$\text{Species Actual Intelligence Score} = \text{Human Base Value (5)} \times \text{Race Intelligence Float}$$

*   **Humans:** $5 \times 1.0 = 5$. Automatically clears the entry parameters to become `technicians` or `police` (req 4-5), but requires extra school infrastructure modifiers to hit the `scientist` threshold (req 8).
*   **Synthetic Machines:** $5 \times 1.5 = 7.5 \rightarrow \textbf{8}$. Instantly satisfies the minimum mental requirements to fill elite scientist, engineer, and bureaucrat positions from day one of assembly.

### Dynamic Retirement Scaling
Rather than tracking retirement as a rigid chronological cutoff, the economy loop calculates a worker’s expiration age by multiplying their race’s naturalLifespan by a profession's retirementLifespanPercentage:

$$\text{Chronological Retirement Age} = \text{Natural Lifespan} \times \text{Retirement Lifespan Percentage}

- $$Human Soldier: $85 \times 0.55 = \textbf{46.7}$ years old (Rapid burnout due to heavy physical wear).
- Human Scientist: $85 \times 0.85 = \textbf{72.2}$ years old (Extended mental contributions).
- Silicon Core Miner: $800 \times 0.65 = \textbf{520}$ years old (Immense industrial lifecycle retention).
 
When empires research advanced applications within Gene Technology (such as Gene Sequencing or Gene Therapy), the global naturalLifespan value for carbon species is increased. This dynamically pushes the Chronological Retirement Age higher behind the scenes, allowing players to retain high-experience professionals on manufacturing lines for decades longer before they transition into pure welfare consumers.Co-habitation and Atmospheric FrictionMultiple distinct race populations can reside within the same physical quarters or use the same Commercial Hubs seamlessly, provided their breathingAtmosphere entries are fully compatible. If a species with an incompatible chemical base enters a module (e.g., a liquid-ammonia entity docking at Earth’s standard oxygen-nitrogen trade exchange), they must utilize specialized environmental containment gear and atmospheric suits to prevent catastrophic biological exposure.