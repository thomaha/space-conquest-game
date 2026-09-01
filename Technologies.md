#### Technologies with applications
All applications can be improved upon by doing more research on it. Applications have a base type with a given starting cost in work and materials and effect of what it can achieve.
When improving an application by researching it, you can either increase its effect at the cost of more complexity and materials or better materials or the research can result in reduced cost in work and materials.
There should be some randomness to the actual effect of such research.
To produce an item of a given complexity, you will need a production facility of the correct type and ability to produce items of that complexity, as well as the required materials and work force.
- Electricity
  - Electronics
  - Solar power
  - Hydropower
  - Wind power
  - Thermoelectric power
  - Nuclear power
  - Fusion power
  - Antimatter power
  - Batteries

- Nuclear fission
  - Fission reactors
  - Fission weapons

- Nuclear fusion
  - Fusion reactors
  - Fusion weapons

- Industrial production
    - Farming applications take planetary raw materials (like liquid water, raw nitrogen, phosphates and soil minerals) or space station power grids and convert them into complex biological matter.
        - Open-world agriculture (Planetary surfaces). This area focuses on utilizing natural planetary biomes, atmospheres and soil compositions to grow biological matter at a massive scale.
            - Industrial soil cultivation: Large-scale mechanized harvesting of native or terraformed soils using basic irrigation and chemical fertilizers.
- Factors affected: food production (+), planetary water table depletion (+), work hours per unit (low).
            - Automated biosphere macro-farms: Giant, drone-managed agricultural zones equipped with climate-control arrays to protect crops from extreme planetary weather shifts.
- Factors affected: crop yield stability (+++), electricity demand (+), material production complexity (medium).
        - Closed-loop life support (Space infrastructure). When a colony is on a barren moon, an asteroid or a deep-space station, farming must be completely insulated from the vacuum of space.
            - Hydroponic growth arrays: Growing plants in nutrient-rich water solutions instead of soil, stacked vertically to maximize space inside orbital habitats.
- Factors affected: space station capacity usage (--), food production (+), liquid water demand (++).
            - Aeroponic nutrient misting (Req: Nanotechnology): Suspending plant roots in the air and using automated micro-nozzles to spray them with mist. This radically optimizes resource consumption.
- Factors affected: water consumption (---), crop growth speed (++), electronics complexity (+).
        - Industrial bio-synthesis (Advanced manufacturing inputs) This area transitions farming from food production into a source of raw structural materials and chemical components.
            - Biomass processing: Refining agricultural waste and fast-growing plant fibers into organic plastics, insulation materials and clothing.
- Factors affected: manufacturing material cost (-), reliance on petrochemical deposits (--).
            - Algae carbon scrubbing (Req: Energy fields): Cultivating specialized genetic strains of algae in massive fluid vats exposed to artificial light fields to recycle breathable air.
- Factors affected: spacecraft life support duration (+++), electricity demand (++).
        - Cellular agriculture (Late-game scaling) By bypassing the need to grow whole plants or animals, the colony can synthesize nutrients directly at a molecular level.
            - Bioreactor tissue printing (Req: Biological cloning + Nanotechnology): Cultivating animal proteins and complex plant tissues directly inside industrial synthetic vats.
- Factors affected: food production per square meter (++++), work hours required (---), production complexity (high).
    - Ore refining (raw materials ➔ pure elements) This area focuses on isolating the elements from the unique geological compositions of your planets and asteroids.
        - Pyrometallurgical smelting (base level): Uses high thermal energy (coal, oil, basic electricity) to melt and separate common ores into basic metals.
- Factors affected: material yield (+), electricity demand (+), pollution/habitability (-).
        - Chemical leaching & hydrometallurgy: Uses chemical acids and gasses to dissolve and extract noble or rare-earth metals from complex crust veins.
- Factors affected: rare element extraction rate (++), work hours per unit (+).
        - Centrifugal & gaseous isotope separation (req: Nuclear fission): Separates specific heavy isotopes from mined radioactive materials.
- Factors affected: fission fuel purity (+++), complexity rating (high).
        - Zero-G magnetic refining req: Orbital production: Uses the vacuum and weightlessness of space alongside magnetic fields to perfectly separate pristine metals from asteroid rock without atmospheric contamination.
- Factors affected: material purity (+++), space station power demand (+).
    - Manufacturing (materials ➔ finished modules) This is where materials are physically cut, pressed and assembled into components.
        - Automated assembly lines (base level): Robotic and mechanical arms mass-producing parts.
- Factors affected: cost to build per unit in work hours (--), workforce demand (-).
        - Nanofabrication matrices (req: Nanotechnology): Tiny machines constructing electronic boards and micro-components block-by-block. Required for producing high-complexity electronics.
- Factors affected: max production complexity cap (up to level 5).
        - Replicators / matter synthesizers (req: Molecular assembly & nanotechnology): Converts raw electrical energy or base scrap elements directly into complex components.
- Factors affected: required mined materials (--), electricity demand (++++), work hours (+).
    - Metallurgy (pure elements ➔ structural alloys/metamaterials) While refining gives you raw elements, metallurgy combines them based on their chemical composition to achieve optimal weight and material strength.
        - Heavy steel & titanium alloying (base level): Forging heavy structural metals for ground structures and early spacecraft. High weight, medium strength.
- Factors affected: material strength (+), weight per unit (high).
        - Crystal lattice tuning (req: Superconductors): Using electromagnetic fields during cooling to arrange atoms into perfect, flawless crystal structures.
- Factors affected: material strength (+++), complexity (+).
        - Radiation ablative shielding: Creating composite alloys designed specifically to absorb cosmic rays and dissipate heat from laser weapons.
- Factors affected: ship energy weapon resistance (++), weight per unit (+).
    - Orbital & space production req: Rocketry. Transporting materials into space costs credits per kg based on planetary g-force. Therefore, off-world manufacturing is a massive strategic advantage.
        - Orbital drydocks (req: Rocketry / spacecraft): Space station modules capable of assembling large hull frames.
- Factors affected: maximum ship size cap (allows capital ships that would otherwise collapse under a planet's gravity during construction).
        - Microgravity foundry: Allows the manufacturing of materials that require perfect weightlessness to form correctly, such as foaming metals or flawless fiber optics.
- Factors affected: unlocks exotic space-only material types.
        - Asteroid capture & processing: Anchoring a production facility directly onto a captured asteroid to mine, refine and manufacture ship hulls entirely in deep space.
- Factors affected: transportation credit cost to orbit (reduced to 0 for these specific materials).

- Rocketry
  - Rocket engines
  - Launch facilities
  - Fission engines, req: Fission reactors
  - Fusion engines, req: Fusion reactors
  - Orbital rockets
  - Interplanetary rockets
  - Spacecraft
  - Space stations

    - Early-era rocketry and surface-to-orbit logistics
      Before an empire masters advanced macro-engineering like *Space Elevators* or electromagnetic *Mass Driver Launch Tracks*, chemical and early nuclear rocketry represents the absolute, single path for launching structural mass past a planet's gravity well. Rockets serve a dual operational role: they function as the primary logistical surface-to-orbit freighters and act as a civilization's first space explorers, charting nearby moons and adjacent planets.

      - The surface-to-orbit gravity bottleneck
        Launching mass into space from a terrestrial planet is a hard physics-bounded restriction. Every kilogram of structural hull plating, electronics substrates and stored payload weight requires an exponential counter-allocation of propellant, calculated directly using the target planet's physical attributes:

        Launch Propellant Mass (kg) = Total Payload Mass * \left( e^{\frac{\Delta V}{\text{Engine Efficiency (Isp)}}} - 1 \right) \times \text{Planetary Gravity Well Factor}$$

*   **The logistical tax:** Early-era empires must allocate thousands of kilograms of unrefined fuel elements—such as `hydrogen_gas` or compressed off-gasses derived from `chemical and gas processing` operations—just to lift basic payloads into low orbit.
*   **The moon staging strategy:** Because moons like Earth's satellite possess drastically lower surface gravity vectors (`1.62` vs. Earth's `9.81`) and `atmosphere: none`, the rocket launch fuel tax on a moon is slashed by over 80%. This creates a massive economic incentive for early players to haul light manufacturing components to low-G moons first, using them as efficient, fuel-saving staging hubs to assemble the empire's first true interstellar fleets.

  - The rocket modular component system
    To provide deep technical progression without clogging up the capital spaceship database, early rockets operate on an independent, specialized **Rocket Component System**. Unlike true spaceships, which use open-frame modular slots designed for flexible customization, early rocketry frames utilize **Rigid Stage Slots** categorized into explicit, sequential engineering zones.

    Rockets cannot mount complex capital modules like *Shield Grids*, *Continuous Beam Lasers* or *Commerce Modules*. Instead, users design rockets by populating three dedicated stage types:

    ##### Propulsion tier components
    *   **Liquid-chemical rocket engine:** A low-complexity (Level 1) primitive booster. It generates immense raw thrust required to escape heavy gravity fields but has a terrible fuel efficiency rating, consuming massive volumes of `hydrogen_gas` or liquid hydrocarbon composites per launch.
    *   **Solid-fuel booster ring:** High-thrust, single-use launch straps. They cannot be deactivated once ignited. They drastically increase initial launch lift capabilities at the cost of high structural dead-weight penalties once depleted.
    *   **Nuclear fission thermal rocket (NTR):** Unlocked by bridging *Nuclear Fission* with *Rocketry*. It utilizes a mini fission reactor core to heat hydrogen gas, delivering near-double the fuel efficiency of chemical thrusters.
    *   *Systemic factors:* A medium-complexity (Level 4) unit. Its high efficiency makes it the ultimate engine choice for long-range *Explorers* mapping the solar rim, but its heavy lead shielding increases the dry mass footprint, limiting its raw liftoff thrust capability on heavy terrestrial planets.

    ##### Core booster components
    *   **Cryogenic bulk fuel tank:** Lightweight aluminum-lithium tanks designed to hold liquid propellants. Larger tanks expand the delta-V range of the rocket, allowing it to reach further planetary nodes at the cost of increasing total liftoff mass.
    *   **Avionics and guidance matrix:** The computer brain of the rocket, assembled using `refined_silicon` and copper circuitry. Upgrading this matrix lowers the random chance of catastrophic launch failure or navigation trajectory deviations.

    ##### Payload stage components
    *   **Pioneer exploration module:** Fits the rocket with hyper-spectral mapping arrays and basic seismic sounding tools. Turns the rocket into an early *Explorer* capable of executing the empire's first automated scouting runs to discover basic surface veins on nearby planets.
    *   **Automated satellite deployment pack:** Deploys communication or prospecting satellites into a target planet's orbit, scanning the celestial entity from above to flip hidden material tags to visible states before colonies land.
    *   **Bulk cargo capsule:** A heavy, structural shell designed to ferry raw ores, refined metals or conscious workforce cohorts from the planetary surface up to early orbital space base anchors.

    #### Rocket technology progression tree
    Players can scale up their early rocketry operations by investing research hours directly into the sub-points of the *Rocketry* branch. Evolving rocketry technology shifts game values across three absolute vectors:

    *   **Bigger (starframe scale upgrades):** Unlocks larger hull templates, moving rocket tiers from light orbital lifters up to massive multi-stage heavy-lift vehicles. This expands total *Payload Stage* capacity, letting players launch complex components like *Control Modules* or heavy *Power Modules* into orbit to seed early space stations.
    *   **Faster (impulse trajectory calculations):** Upgrading propellant chemical configurations or magnetic gas channeling reduces the total game turns required for a rocket to transit from the home world to adjacent solar coordinates, accelerating early-game resource pooling.
    *   **More efficient (the cost mitigation path):** Directly raises the baseline engine efficiency variables across all liquid and nuclear propulsion components.
      *   *The economic impact:* Advanced efficiency research drops the raw material fuel requirement per launch. This lets players transition their home world's industry away from constant, brute-force chemical gas harvesting and refocus their workforce training pipelines on high-complexity electronics or metallurgy production.
    
- Surface-to-orbit infrastructure (Req: Rocketry + Advanced materials)
    - Mass driver launch tracks: Ground-based electromagnetic rails that rail-launch raw unrefined resources (like iron_ore or silicates) directly into low orbit using pure electrical energy.
- Factors affected: launch transport cost per kg (--- for raw materials), electricity demand (+++), cargo damage risk (high—cannot launch delicate electronics or biological items).
    - Space elevators / Orbital tethers (Req: Superconductors, high material tech.): Massive planetary tethers anchoring a ground station to a geostationary orbital platform. Replaces traditional orbital rockets, permanently dropping the material and workforce cost of launching spacecraft from a planet's surface.
- Factors affected: launch transport cost per kg (--- for all items), structural material cost (extreme), vulnerable to orbital bombardment.

- Waste management and closed loops (Req: Industrial production)
    - Gas capture and scrubbing arrays: Heavy industrial filters inside refining foundries that capture chemical off-gasses before they escape into the planet's atmosphere or space.
- Factors affected: extraction yield of secondary gasses like oxygen_gas or sulfur (++), factory pollution generation (--).
    - Slag processing and molecular reclaiming (Req: Nanotechnology): Dissolving left-over industrial rock slag using chemical leaching pools to extract trace amounts of noble metals.
- Factors affected: mineral vein extraction lifetime (+), electricity demand (++), work hours per unit (+).

- Interstellar shipping optimization (Req: Rocketry)
    - Automated cargo route freighters: Standardizing cargo containers to automate bulk hauling networks between mining moons and manufacturing hubs.
- Factors affected: work hours required to manage shipping lanes (-), fleet fuel efficiency (+).

- Supply chain automation and industrial computing. To balance production complexity ratings (1 to 10), players need technologies that optimize how their workforce handles complex factory tasks.
    - Automated factory matrices (Req: Robotics + Electricity)
        - Just-in-time logistics networks: Computerized inventory routing that syncs local mining output with manufacturing factory demands across an entire star system.
- Factors affected: material storage capacity requirements (-), production delay penalties (--).
        - Cybernetic workforce integration (Req: Artificial intelligence): Deploying specialized, non-sentient AI clusters to oversee heavy manufacturing plants, removing the need for human safety parameters.
- Factors affected: production complexity capacity threshold (+), workforce demand (---), vulnerability to EMP weapons (+).

- Geological prospecting and orbital scanning. Planetary bodies feature finite numbers of veins, finding resource deposits needs to be an active, scalable technological pursuit.
    - Remote sensing (Req: Energy fields / Scanners)
        - Orbital hyper-spectral mapping: Scanning a planetary body from orbit using infrared and deep-radar sensors to discover obvious surface deposits and shallow veins.
- Factors affected: baseline vein discovery speed (+), initial planetary map visibility (++).
    - Seismic crustal sounding: Detonating controlled explosive charges on a planet's surface to bounce acoustic waves through the crust, revealing hidden deep-layer veins.
- Factors affected: deep-crust rare vein discovery chance (+++), work hours per prospecting mission (+).

- Superconductors
- Computers
- Electronic computers
- Quantum computers
- Energy fields, req: Superconductors
- Shields
- Scanners
- Electromagnetic weapons, req: Energy fields
- Mass drivers: slow bolts. Heavy shield damage. Long range.
- Particle beams
- Energy weapons, req: Energy fields
- Laser weapons: weak in atmosphere. Instant beam. Weak against shields. Medium range.
- Plasma weapons: slow glowing bolts of superheated plasma. Strong against armor. Short range.
- EMP-weapons: non-lethal. Fries electronics. Short to medium range.
- Defensive weapons (point defense systems – PDS). Useless against energy beams.
- Flak cannons (Industrial/metallurgy): fires bursts of shrapnel. Cheap to manufacture, highly effective at shredding swarms of fragile bombers or missiles.
- PD lasers (energy fields/lasers): low-yield, rapid-tracking lasers. Instantly zaps incoming physical torpedoes or particle streams before they hit the main shields. High-power draw.
- Nanotechnology. Required for electronics > 4
- Antimatter
- Antimatter harvesting / storage: magnetic containment facilities capable of holding positrons and antiprotons. req: Superconductors
- Antimatter bombs / warheads: the ultimate destructive bomb category for erasing planetary fortifications or vaporizing enemy capital ships.
- Antimatter engines: extremely high-impulse propulsion that allows rapid interstellar transit inside a star system.
- Robotics
- Artificial intelligence
- Gene technology
- Gene sequencing
- Gene editing
- Gene therapy
- Biological cloning
- Transhumanism & digital consciousness req: AI, gene technology
- Cybernetic augmentation: blends robotics with gene therapy to permanently enhance workforce productivity or ground-combat troop effectiveness.
- Mind uploading / digital immortality: converts citizens into digital data. Gameplay effect: drastically reduces the reliance on farming and biological space requirements, transitioning your civilization's workforce into pure server architecture.
- Gravitational engineering, req: Energy fields, Warp
- Inertial dampeners: neutralizes g-forces. Allows massive capital ships to turn tightly without crushing the crew inside or lets fighters accelerate instantly.
- Artificial gravity / grav-plating: essential for maintaining crew health on permanent space stations and long-term spacecraft.
- Singularity reactors: harnessing micro-black holes for near-infinite power generation, surpassing fusion reactors.
- Warp req: Singularity reactors
- Warp drive
- Base application (level 1) base cost: extremely high work and rarest materials (e.g., exotic matter / antimatter).
- Base effect: allows travel between adjacent star systems at high power cost
- Random upgrade research outcomes: when a player spends resources to research
- Spatial stabilization (breakthrough - 10%): warp speed increases by 30% and the post-warp power drain is reduced to 20%. Complexity remains unchanged.
- Engine optimization (success - 50%): the player chooses between: Option A (effect focus): +15% travel range across the galactic map. Option B (cost focus): -20% material cost to manufacture future warp engines.
- Volatile folding (flawed design - 30%): warp speed increases by 20%, but the engine's complexity spikes. It has a tiny random chance to damage the ship's hull on activation.
- Subspace tear (catastrophic setback - 10%): the research fails to increase speed. Instead, the engine radiates massive amounts of energy. The ship becomes highly visible on enemy scanners across the galaxy whenever it drops out of warp.
- Stellar megastructures req: Gravitational engineering, surface-to-orbit infrastructure
- Dyson swarm assembly: constructs orbital solar collector swarms around parent suns to harvest massive energy outputs.
- Hyperlane gateway construction: constructs deep-space artificial conduits enabling instantaneous interstellar transit between star systems.
