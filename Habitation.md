#### Biochemical Profiles and Nutrient Consumption

Populations are completely segregated by race, and their survival loops are determined by their core chemical compositions established in the `races.json` configuration:

*   **Carbon-Based Oxygen Breathers (Humans, Vulkans):**
    *   *Consumption Loop:* Continuously deplete stored `oxygen_gas` and require organic food tracking assets from *Food Production Modules*.
    *   *The Diversity Multiplier:* If their `nutrientSpreadRequirement` is set to "Diverse" (e.g., Humans), they demand a mix of plant, animal, or synthetic proteins. Fulfilling this diversity requirement multiplies local private economy happiness and accelerates growth. Forcing them to subsist on basic mono-crop sludge hits happiness variables, slowing down training speeds and generating crime.
*   **Silicon-Based Lithovores (Silicon Core):**
    *   *Consumption Loop:* Completely ignore standard agricultural food and oxygen reserves. They require no oxygen gas and are fully vacuum-compatible.
    *   *The Mineral Drain:* To survive and grow, they consume raw planetary mineral stocks like `silicates` and `limestone` directly from the entity's storage vaults. Mismanaging inventory means an overpopulated Silicon Core colony will literally eat the structural materials required to build your starship hulls.
*   **Gaseous and Alternative Biochemicals:**
    *   *Consumption Loop:* Absorb atmospheric vapors. They continuously drain stored volumes of volatile elements such as `methane_ice` or `ammonia_ice`, requiring *Gas Condensation and Enrichment Modules* to maintain their unique breathing pressure loops.

#### Demographic Growth and the Hive Mind Exception

Population reproduction is evaluated based on the active `societyStructure` configuration of the empire:

*   **Individualist and Collectivist Societies:**
    *   *Growth Profile:* Follows an exponential expansion curve bounded by resource abundance, local habitation space, and the specific `fertileAgeSpan` variables of the race.
    *   *The Retirement Burden:* When a citizen cohort crosses their profession's `retirementLifespanPercentage`, they stop generating work hours. They remain inside the habitation module as pure consumers, requiring food and public welfare credits until they hit the end of their natural lifespan.
    *   *Medical Intervention:* Researching advanced *Gene Technology* (like *Gene Editing* or *Gene Therapy*) increases a race's baseline lifespan. This delays retirement burnout and allows high-experience `scientists` and `engineers` to remain active on production lines for decades longer.

*   **Hive Mind Societies:**
    *   *Growth Profile:* **Follows a strict linear reproduction model.** Drones do not reproduce independently based on age brackets. Population growth is determined entirely by the number of active, fertile **Queens** present on the space entity.
    *   *Zero-Overhead Mechanics:* Hive mind drones have no retirement age, no private wallets, and no happiness parameters. They do not consume diverse diets or luxury consumer goods. They require a flat, uniform allocation of base organic or mineral nutrients from the state grid to maintain physical function, operating with absolute command efficiency until exterminated.

#### Spacecraft Stasis Controls

When transporting population cohorts across the galactic map inside *Troop Transports* or *Passenger Transports*, players must manage the state of the passengers:

*   **Conscious Transit (Passenger Cabins):** Workers or migrants remain fully conscious. They consume food, deplete oxygen gases, and generate happiness checks every travel turn. This forces long-range transport ships to dedicate valuable slot capacity to large storage modules and recycling arrays.
*   **Cryogenic Stasis Transit (Troop Transport Bays):** Biological functions are completely suspended using cryogenic stasis applications. While inside stasis chambers, troops or colonists consume **0 food, 0 water, and 0 breathing gases**. This eliminates life support weight scaling constraints, allowing compact military vessels to haul thousands of soldiers across deep space without crashing the ship's logistics grid.