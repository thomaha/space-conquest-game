#### Planetary Power Generation (Facility Infrastructure)

On planetary surfaces, power generation scales dynamically with the planet's unique physical attributes (atmosphere, climate, and liquid water status) as mapped in the celestial entity databases. To construct and operate a power facility, the colony must possess the correct type of production facility capable of matching the design's underlying **complexity rating**:

*   **Solar Power Facilities:** Utilizes photovoltaic arrays to harvest starlight. They require 0 fuel consumables to run. However, their electrical output scales down inversely based on the planet's orbital `distance` from the host star and is heavily suppressed by thick atmospheric densities or cloud cover variables.
*   **Hydropower Facilities:** Harnesses liquid water currents to spin massive kinetic turbines.
    *   *Prerequisite:* Can only be constructed on planets where `hasLiquidWater` is flagged as `true` and requires a high local `waterLevel` score (e.g., Earth). They generate immense, reliable, low-complexity electricity with 0 fuel cost.
*   **Wind Power Facilities:** Converts atmospheric currents into electrical energy.
    *   *Prerequisite:* Requires a dense, active `atmosphere` profile (such as `nitrogen_oxygen` or `carbon_dioxide`). They produce cheap, low-complexity power but their turn-by-turn vary slightly based on planetary climate fluctuations.
*   **Thermoelectric Power Facilities:** Extracts geothermal energy directly from tectonic friction inside a planet's crust. They provide steady, baseline grid power regardless of atmospheric or orbital conditions, matching well with tectonic variables on active rocky or molten worlds.
*   **Nuclear Fission Power Facilities:** Utilizes centralized ground reactors to split heavy radioactive atoms, outputting heavy baseline electrical energy independent of planetary climate or atmosphere variables.
    *   *Fuel Requirement:* Requires a continuous, turn-by-turn consumption of `refined_uranium` or `refined_thorium` pulled directly from the planet's storage vaults.
    *   *Systemic Factors:* A medium-complexity (Level 4+) installation. While highly reliable, it demands careful workforce oversight to manage fuel throughput and security overhead.
*   **Nuclear Fusion Power Facilities:** Represents a monumental energy leap, running ground-based magnetic confinement fields to fuse light hydrogen and helium isotopes.
    *   *Fuel Requirement:* Consumes high-purity `fusion_fuel_pellets` (synthesized from `helium_3` and `deuterium_gas`).
    *   *Systemic Factors:* A high-complexity (Level 8+) macro-facility. If the empire's technology tier allows its construction, it provides immense, clean electrical grid outputs that can effortlessly power a planet's entire heavy industrial metallurgy, refining, and manufacturing sectors simultaneously.

#### Deep-Space Station and Starship Power Alternatives

Unlike planetary surface nodes, space stations and spaceships cannot exploit wind or water currents. They must balance mobile logistics between high-yield fuel consumption and passive renewable collection:

*   **Solar Power Arrays (The Fuel Alternative):** Spaceships and space stations can deploy external photovoltaic wings to capture ambient starlight without consuming fuel cells. While solar power provides an exceptional baseline survival net for scouts, stations, and cargo transports, it cannot generate the massive energy spikes required to fire anti-ship weaponry or recharge heavy combat shield grids. Its yield drops to zero in the interstellar void between star systems.
*   **Nuclear Fission Power (Early Interstellar Tier):** Utilizes controlled nuclear fission to output heavy baseline electrical energy.
    *   *Fuel Requirement:* Requires a continuous, turn-by-turn consumption of `refined_uranium` or `refined_thorium` pulled directly from the entity's storage vaults.
    *   *Systemic Factors:* A low-complexity (Level 4), high-mass power source. Its heavy lead containment shields add immense dry mass, penalizing sub-light handling characteristics and increasing a starship's planetary launch gravity tax.
*   **Fusion Power (Advanced Mid-Game Tier):** Houses controlled magnetic confinement fields to fuse heavy hydrogen isotopes.
    *   *Fuel Requirement:* Consumes hyper-dense, high-purity `fusion_fuel_pellets` (manufactured by combining `helium_3` and `deuterium_gas` inside high-complexity electronics fabrication matrices).
    *   *Systemic Factors:* A high-complexity (Level 8) power system. It generates an exceptional volume of clean electricity with a highly optimized, lightweight structural footprint, minimizing spaceship launching costs.
*   **Antimatter Power (End-Game Hyper-Tech Tier):** Harnesses total mass-energy conversion by colliding matter and antimatter streams inside magnetic vacuum rings.
    *   *Fuel Requirement:* Consumes ultra-volatile antimatter storage cores.
    *   *Systemic Factors:* The ultimate late-game energy generation module (Complexity Level 10). It outputs near-infinite electrical grid energy, easily supplying the immense power spikes demanded by continuous-beam particle weapons, planet-cracking bombs, and interstellar warp bubble generation.

#### The Turn-Based Energy Balancing Pass

During every game turn-update loop, the engine runs an automated energy balancing pass across the space entity or starship frame. This ledger calculates the net energy balance ($E_{\text{net}}$):

$$E_{\text{net}} = \sum \text{Facility / Module Electrical Outputs} - \sum \text{Attached Module Energy Demands}$$

*   **The Connected Grid:** Every active installation assigned to a planet or module slotted into a hull frame possesses a static, turn-by-turn energy demand rating. Intellectual arrays like *Science Laboratories* or digital systems like *Commerce Modules* run a continuous, passive energy draw. Heavy industrial modules—such as *Metallurgy Foundries* or *Electronics Matrices*—spike their energy draw exponentially when actively processing materials or fabricating high-complexity items.
*   **Batteries (Energy Storage Linkage):** If a spaceship, base, or space station is equipped with a *Batteries Module/Facility*, any positive surplus ($E_{\text{net}} > 0$) is converted into stored electricity. These battery banks act as localized buffer pools, allowing a ship to sustain operations if its primary fuel reserves run out or if an active system temporarily spikes past its base generator outputs.

#### Grid Deficits and Structural Failure States

If $E_{\text{net}}$ drops below zero and local battery bank reserves are fully depleted ($E_{\text{net}} < 0$), the entity enters a **Grid Deficit State**. This triggers cascading operational failures:

*   **Component Disablement Matrix:** When a grid deficit triggers, the engine automatically disables non-essential attached structures to protect core functionality. Industrial processing foundries go cold, freezing manufacturing and refining progress loops. Digital commerce exchanges shut down completely, pausing private corporate B2B trading and stopping all public tax harvesting.
*   **The Habitation Emergency:** If the energy deficit is severe enough to starve local *Atmospheric Recycling Arrays*, life support metrics drop instantly. The enclosed living environment loses temperature control and gas scrubbing capability, triggering rapid population decay and severe happiness penalties across all biological worker cohorts.
*   **The Technician Safeguard:** The rate of power module decay and the speed at which a grid deficit can be manually rerouted or repaired scales directly based on the allocation and training efficiency of the local `technician` profession. A high headcount of master technicians can prevent total system brownouts by dynamically overclocking reactors or shedding localized grid loads safely.
