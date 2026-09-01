#### Planetary abundance and initial composition modifiers

A planetary body’s total resource blueprint is dictated directly by its host star type, using the `systemCompositionModifiers` established in the star configuration profiles.

- **The baseline rule:** It is physically possible to extract *any* element or material from *any* terrestrial or barren planet. However, the host star type sets the global availability baseline. For instance, a young **O-Class Blue Star** generates a high `heavyMetalAbundance` modifier, shifting the planet's default core to favor massive deposits of `iron_ore` or `titanium_ore`.
- **The Earth emulation:** Like modern-day Earth, a planet features highly visible surface materials (such as `silicates`, `limestone` or `water_ice`) alongside localized, high-concentration subterranean **Veins** of rare metals or fuel components.
- **The knowledge gap:** At the start of a colony's initialization, the empire possesses a highly restricted planetary asset map. Only a small handful of basic surface veins are tagged as `"isDiscovered": true`. The remaining volume of the planet's resources is hidden within an unmapped crust matrix variable.

#### 2. Dynamic prospecting and diminishing returns

To locate new resource sources, empires or private corporations must deploy active survey missions. This mechanism uses the `prospector` or `scientist` professions alongside remote-sensing applications to run a stochastic (probabilistic) discovery roll every game turn.

- **The discovery probability formula:** The mathematical chance ($P_{\text{discover}}$) of a prospecting mission successfully locating a new, high-concentration vein decays dynamically as the colony approaches its maximum geological discovery threshold:

$$P_{\text{discover}} = \text{Base Tech Efficiency} \times \text{Staff Training Modifier} \times \left(1.0 - \frac{\sum \text{Discovered Vein Volumes}}{\text{Total Estimated Planet Volume}} \right)^{\alpha}$$

- **The scarcity scaling factor ($\alpha$):** This exponent scales directly based on the planet's structural type. The more minerals that have already been discovered, relative to what the planet's composition baseline dictates is physically present, the harder it becomes to find more.
- **Private vs. public prospecting:**
    - *Public state missions:* Consume public treasury credits and state work hours. Discovered veins are flagged as public property, allowing state-owned *Mine Ships* or public installations to extract them freely.
    - *Private corporate missions:* Autonomous private corporations allocate their own aggregated wealth to fund regional prospecting. When a corporation discovers a hidden vein, they claim exclusive extraction rights, utilizing their own private *Mine Ships* to process the raw elements for B2B market arbitrage.

#### High-grade veins vs. crust-average background extraction

When extracting materials, the game engine calculates production costs by checking if the local miners are pulling from a defined, high-concentration **Vein** or forcing **Crust-Average Background Mining**.

- **High-grade veins (targeted mining):**
    - *Mechanic:* Pulling from a discovered vein (such as an active `uranium_ore` or `platinum` deposit).
    - *Economic profile:* Low work hour cost, minimal electricity draw and standard complexity requirements. This represents the primary, hyper-profitable stage of a colony's industrial cycle.
- **Crust-average background mining (sifting the slag):**
    - *Mechanic:* Triggered when a player or corporation demands a material that has no active, discovered veins on that planet or when all high-grade veins of that resource have hit complete depletion.
    - *Economic profile:* To extract rare materials from common rock crust without a concentrated vein, processing facilities must run brute-force chemical leaching and mass-spectrometry sorting.
    - *The financial barrier:* This action is **exponentially expensive**. It scales the required work hours per unit, material costs and electricity demands by an inverse abundance factor:

$$\text{Background Extraction Cost Modifier} = \left( \frac{1.0}{\text{Global Element Rarity Modifier}} \right) \times \text{Facility Complexity Rating}$$

- **The gameplay loop:** If an empire urgently needs `tungsten` to build capital ship anti-ship orbital batteries, but Earth has no discovered tungsten veins remaining, they can still mine it from rare earth composites where it could exist as a minor part. However, the background processing cost will be astronomically high, consuming massive amounts of energy and workforce hours to filter through millions of kilograms of base dirt just to isolate a few grams of pure tungsten. This mechanical wall naturally drives empires and private corporations to look outward—building *Construction Ships* to drop mining stations onto zero-G asteroids or launching *Explorers* to prospect raw, untouched systems across the galactic rim.
