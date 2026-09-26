#### Biochemical profiles and nutrient consumption

Populations are completely segregated by race and their survival loops are determined by their core chemical compositions established in the `races.json` configuration:

- **Carbon-based oxygen breathers (humans, Vulkans):**
    - *Consumption loop:* On a breathable planet or moon, residents breathe ambient oxygen without buying `oxygen_gas` from the market. On an airless or hostile body and aboard a conscious-transit spacecraft, enclosed life support needs a supplied oxygen stock. Organic food remains a material requirement in either setting.
    - *The diversity multiplier:* If their `nutrientSpreadRequirement` is set to "Diverse" (e.g., Humans), they demand a mix of plant, animal or synthetic proteins. Fulfilling this diversity requirement multiplies local private economy happiness and accelerates growth. Forcing them to subsist on basic mono-crop sludge hits happiness variables, slowing down training speeds and generating crime.
- **Silicon-based lithovores (Silicon Core):**
    - *Consumption loop:* Completely ignore standard agricultural food and oxygen reserves. They require no oxygen gas and are fully vacuum-compatible.
    - *The mineral drain:* To survive and grow, they consume raw planetary mineral stocks like `silicates` and `limestone` directly from the entity's storage vaults. Mismanaging inventory means an overpopulated Silicon Core colony will literally eat the structural materials required to build your starship hulls.
- **Gaseous and alternative biochemicals:**
    - *Consumption loop:* Absorb atmospheric vapors. They continuously drain stored volumes of volatile elements such as `methane_ice` or `ammonia_ice`, requiring *Gas Condensation and Enrichment Modules* to maintain their unique breathing pressure loops.

#### Demographic growth and the hive mind exception

Population reproduction is evaluated based on the active `societyStructure` configuration of the empire:

- **Individualist and collectivist societies:**
    - *Growth profile:* Follows an exponential expansion curve bounded by resource abundance, local habitation space and the specific `fertileAgeSpan` variables of the race.
    - *The retirement burden:* When a citizen cohort crosses their profession's `retirementLifespanPercentage`, they stop generating work hours. They remain inside the habitation module as pure consumers, requiring food and public welfare credits until they hit the end of their natural lifespan.
    - *Medical intervention:* Researching advanced *Gene Technology* (like *Gene Editing* or *Gene Therapy*) increases a race's baseline lifespan. This delays retirement burnout and allows high-experience `scientists` and `engineers` to remain active on production lines for decades longer.
- **Hive mind societies:**
    - *Growth profile:* **Follows a strict linear reproduction model.** Drones do not reproduce independently based on age brackets. Population growth is determined entirely by the number of active, fertile **Queens** present on the space entity.
    - *Zero-overhead mechanics:* Hive mind drones have no retirement age, no private wallets and no happiness parameters. They do not consume diverse diets or luxury consumer goods. They require a flat, uniform allocation of base organic or mineral nutrients from the state grid to maintain physical function, operating with absolute command efficiency until exterminated.

#### Spacecraft stasis controls

When transporting population cohorts across the galactic map inside *Troop Transports* or *Passenger Transports*, players must manage the state of the passengers:

- **Conscious transit (passenger cabins):** Workers or migrants remain fully conscious. They consume food, deplete oxygen gases and generate happiness checks every travel turn. This forces long-range transport ships to dedicate valuable slot capacity to large storage modules and recycling arrays.
- **Cryogenic stasis transit (troop transport bays):** Biological functions are completely suspended using cryogenic stasis applications. While inside stasis chambers, troops or colonists consume **0 food, 0 water and 0 breathing gases**. This eliminates life support weight scaling constraints, allowing compact military vessels to haul thousands of soldiers across deep space without crashing the ship's logistics grid.
