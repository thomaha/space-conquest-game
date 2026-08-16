# Space conquest game
This will be a space conquest game with similarities to Stellaris and Master of Orion. 
The game will have resources, technology, races, spaceships and combat both in space and on the ground.

## Project structure
- `engine`: Core game logic and state.
- `control`: Input handling and player/AI logic.
- `frontend`: Main entry point and user interface.

## Technical stack
The game will be built using java. Starting with java26 but will upgrade when new versions are stable.
Framework will be spring boot. A UI-framework is not yet decided but will not be browser-based. Starting with FXGL and see how that works out.

## Game components
Every component in the game like race, technologies, technological applications, materials, professions, raw materials should fetch all properties from property files stored in the resources folder.  

### Time
- The game time will run for many years. The lowest game speed is 1 minute per real second, with steps at 1 hour, 6 hours, 12 hours, 1 day, 5 days, 10 days per second. 
- It is possible to pause the game.

### Galaxy
The galaxy will consist of multiple systems. Systems with stars are solar systems. Most systems are solar systems. 
Planets, asteroids, comets and other stellar bodies might occur outside solar systems, but most planets are within solar systems. 
Each planet with its own resources, technology level and population. 

### Space entities
Planets, moons and asteroids are alle space entities. They can all have a population of multiple races, as well as various buildings and technical installations. 
Most entities will initially be uninhabitable, while others may be habitable but not suitable for certain races. Buildings and installations might later make the entity partly habitable.
Each entity will have mineable resources and industries which can be used to produce goods and technology.
All entities have a mass. The mass of an entity will influence its gravity and the amount of resources for each resource type it can mine.
The gravity, or g-force, will influence how much it costs to move materials from the entity into orbit using propulsion.

#### Planets and moons
Planets may have one or more moons. A moon is a separate entity from the planet and will have its own population, resources and industries. 
Moons are usually smaller than planets and have less resources than planets. Having a smaller mass means lower g-force, 
so moving resources into space from a moon will require less force than from a planet.
Both planets and moons may have an atmosphere, which can affect the air pressure and temperature important for if a race can live on the planet or not.
Planets and moons with enough mass and moderate temperature can have liquid water on their surface, which is important for life to exist.

#### Asteroids
Asteroids are small celestial bodies that orbit the sun. They are made up of a variety of materials and can be mined for resources. 
Asteroids can be mined from space and can be used to build spaceships and other structures. Asteroids typically are tiny, making it easy to transport mined resources into space

### Technology
The technology tree will feature different areas where researching an area will give the possibility of practical application in that area. 
For instance, the area of atomic fission would grant access to practical application in propulsion, energy production and weapons.
Most technical applications will have several levels of miniaturization and optimization reflecting how useful that application is. For instance, 
it might be better to build a spaceship with a highly optimized fission drive instead of using a newly researched fusion drive. 
To model this, we can use the following rules:
Every time a player optimizes a technical application, its attributes can be modified using an inverse scaling formula:
Optimized unit cost = Base cost * 0.9^Optimization level
Optimized complexity = Base complexity - (optimization level * R). 
Optimized effect. R is a minor random factor.
  - New effect: Current effect * 1.15 * R
  - New cost: Current cost * 1.20 * R
  - New complexity: Current complexity + round(1 * R)
Example:
- The newly researched fusion drive: Low optimization. Massive material cost, requires rare gasses, and has a complexity rating of 8 (meaning only your top-tier, expensive orbital ring facilities can even attempt to build it).
- The gen-5 optimized fission drive: High optimization. Cheap, uses common refined metals, and has its complexity reduced to 2. A tiny, low-tech colony shipyard can pump them out in massive quantities.

Each technology has:
- Id
- Name
- Description
- Complexity: How challenging it is to discover.
- List of required technologies to research

Each technical application has:
- Id
- Name
- Description
- List of required technologies
- List of factors it affects (power production, production increase, combat strength, etc.)
- Cost to build per unit in work hours
- List of required materials to build
- Complexity: How challenging it is to produce.

#### Technologies with applications as sub points
All applications can be improved upon by doing more research on it. Applications have a base type with a given starting cost in work and materials and effect of what it can achieve. 
When improving an application by researching it, you can either increase its effect at the cost of more complexity and materials or better materials, or the research can result in reduced cost in work and materials.
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
  - Farming applications take planetary raw materials (like liquid water, raw nitrogen, phosphates, and soil minerals) or space station power grids and convert them into complex biological matter.
    - Open-world agriculture (Planetary surfaces). This area focuses on utilizing natural planetary biomes, atmospheres, and soil compositions to grow biological matter at a massive scale.
      - Application: Industrial soil cultivation
        - Description: Large-scale mechanized harvesting of native or terraformed soils using basic irrigation and chemical fertilizers.
        - Factors affected: Food production (+), planetary water table depletion (+), work hours per unit (low).
      - Application: Automated biosphere macro-farms
        - Description: Giant, drone-managed agricultural zones equipped with climate-control arrays to protect crops from extreme planetary weather shifts.
        - Factors affected: Crop yield stability (+++), electricity demand (+), material production complexity (medium).
    - Closed-loop life support (Space infrastructure). When a colony is on a barren moon, an asteroid, or a deep-space station, farming must be completely insulated from the vacuum of space.
      - Application: Hydroponic growth arrays
        - Description: Growing plants in nutrient-rich water solutions instead of soil, stacked vertically to maximize space inside orbital habitats.
        - Factors affected: Space station capacity usage (--), food production (+), liquid water demand (++).
      - Application: Aeroponic nutrient misting (Req: Nanotechnology)
        - Description: Suspending plant roots in the air and using automated micro-nozzles to spray them with mist. This radically optimizes resource consumption.
        - Factors affected: Water consumption (---), crop growth speed (++), electronics complexity (+).
    - Industrial bio-synthesis (Advanced manufacturing inputs) This area transitions farming from food production into a source of raw structural materials and chemical components.
      - Application: Biomass processing
        - Description: Refining agricultural waste and fast-growing plant fibers into organic plastics, insulation materials, and clothing.
        - Factors affected: Manufacturing material cost (-), reliance on petrochemical deposits (--).
      - Application: Algae carbon scrubbing (Req: Energy fields)
        - Description: Cultivating specialized genetic strains of algae in massive fluid vats exposed to artificial light fields to recycle breathable air.
        - Factors affected: Spacecraft life support duration (+++), electricity demand (++).
    - Cellular agriculture (Late-game scaling) By bypassing the need to grow whole plants or animals, the colony can synthesize nutrients directly at a molecular level.
      - Application: Bioreactor tissue printing (Req: Biological cloning + Nanotechnology)
        - Description: Cultivating animal proteins and complex plant tissues directly inside industrial synthetic vats.
        - Factors affected: Food production per square meter (++++), work hours required (---), production complexity (high).
  - Ore refining (raw materials ➔ pure elements) This area focuses on isolating the elements from the unique geological compositions of your planets and asteroids.
    - Application: Pyrometallurgical smelting (base level) 
      - Description: Uses high thermal energy (coal, oil, basic electricity) to melt and separate common ores into basic metals. 
      - Factors affected: Material yield (+), electricity demand (+), pollution/habitability (-).
    - Application: Chemical leaching & hydrometallurgy
      - Description: Uses chemical acids and gasses to dissolve and extract noble or rare-earth metals from complex crust veins.
      - Factors affected: Rare element extraction rate (++), work hours per unit (+).
    - Application: Centrifugal & gaseous isotope separation (req: Nuclear fission) 
      - Description: Separates specific heavy isotopes from mined radioactive materials.
      - Factors affected: Fission fuel purity (+++), complexity rating (high).
    - Application: Zero-G magnetic refining req: Orbital production
      - Description: Uses the vacuum and weightlessness of space alongside magnetic fields to perfectly separate pristine metals from asteroid rock without atmospheric contamination.
      - Factors affected: Material purity (+++), space station power demand (+).
  - Manufacturing (materials ➔ finished modules) This is where materials are physically cut, pressed, and assembled into components.
    - Application: Automated assembly lines (base level)
      - Description: Robotic and mechanical arms mass-producing parts.
      - Factors affected: Cost to build per unit in work hours (--), workforce demand (-).
    - Application: Nanofabrication matrices (req: Nanotechnology)
      - Description: Tiny machines constructing electronic boards and micro-components block-by-block. Required for producing high-complexity electronics.
      - Factors affected: Max production complexity cap (up to level 5).
    - Application: Replicators / matter synthesizers (req: Molecular assembly & nanotechnology)
    - Description: Converts raw electrical energy or base scrap elements directly into complex components. Factors affected: Required mined materials (--), electricity demand (++++), work hours (+).
  - Metallurgy (pure elements ➔ structural alloys/metamaterials) While refining gives you raw elements, metallurgy combines them based on their chemical composition to achieve optimal weight and material strength.
    - Application: Heavy steel & titanium alloying (base level)
      - Description: Forging heavy structural metals for ground structures and early spacecraft. High weight, medium strength.
      - Factors affected: Material strength (+), weight per unit (high).
    - Application: Crystal lattice tuning (req: Superconductors) 
      - Description: Using electromagnetic fields during cooling to arrange atoms into perfect, flawless crystal structures.
      - Factors affected: Material strength (+++), complexity (+).
    - Application: Radiation ablative shielding 
    - Description: Creating composite alloys designed specifically to absorb cosmic rays and dissipate heat from laser weapons. Factors affected: Ship energy weapon resistance (++), weight per unit (+).
  - Orbital & space production req: Rocketry. Transporting materials into space costs credits per kg based on planetary g-force. Therefore, off-world manufacturing is a massive strategic advantage.
    - Application: Orbital drydocks (req: Rocketry / spacecraft) 
      - Description: Space station modules capable of assembling large hull frames.
      - Factors affected: Maximum ship size cap (allows capital ships that would otherwise collapse under a planet's gravity during construction).
    - Application: Microgravity foundry
      - Description: Allows the manufacturing of materials that require perfect weightlessness to form correctly, such as foaming metals or flawless fiber optics.
      - Factors affected: Unlocks exotic space-only material types.
    - Application: Asteroid capture & processing HUI
      - Description: Anchoring a production facility directly onto a captured asteroid to mine, refine and manufacture ship hulls entirely in deep space.
      - Factors affected: Transportation credit cost to orbit (reduced to 0 for these specific materials).

- Rocketry
  - Rocket engines
  - Launch facilities
  - Fission engines, req: Fission reactors
  - Fusion engines, req: Fusion reactors
  - Orbital rockets
  - Interplanetary rockets
  - Spacecraft
  - Space stations

- Surface-to-orbit infrastructure (Req: Rocketry + Advanced materials)
  - Application: Mass driver launch tracks
  - Description: Ground-based electromagnetic rails that rail-launch raw unrefined resources (like iron_ore or silicates) directly into low orbit using pure electrical energy.
  - Factors affected: Launch transport cost per kg (--- for raw materials), electricity demand (+++), cargo damage risk (high—cannot launch delicate electronics or biological items).
  - Application: Space elevators / Orbital tethers (Req: Superconductors, high material tech.)
    - Description: Massive planetary tethers anchoring a ground station to a geostationary orbital platform. Replaces traditional orbital rockets, permanently dropping the material and workforce cost of launching spacecraft from a planet's surface.
    - Factors affected: Launch transport cost per kg (--- for all items), structural material cost (extreme), vulnerable to orbital bombardment.

- Waste management and closed loops (Req: Industrial production)
  - Application: Gas capture and scrubbing arrays
    - Description: Heavy industrial filters inside refining foundries that capture chemical off-gasses before they escape into the planet's atmosphere or space.
    - Factors affected: Extraction yield of secondary gasses like oxygen_gas or sulfur (++), factory pollution generation (--).
  - Application: Slag processing and molecular reclaiming (Req: Nanotechnology)
    - Description: Dissolving left-over industrial rock slag using chemical leaching pools to extract trace amounts of noble metals.
    - Factors affected: Mineral vein extraction lifetime (+), electricity demand (++), work hours per unit (+).

- Interstellar shipping optimization (Req: Rocketry)
  - Application: Automated cargo route freighters
  - Description: Standardizing cargo containers to automate bulk hauling networks between mining moons and manufacturing hubs.
  - Factors affected: Work hours required to manage shipping lanes (-), fleet fuel efficiency (+).

- Supply chain automation and industrial computing. To balance production complexity ratings (1 to 10), players need technologies that optimize how their workforce handles complex factory tasks.
  - Automated factory matrices (Req: Robotics + Electricity) 
    - Application: Just-in-time logistics networks
      - Description: Computerized inventory routing that syncs local mining output with manufacturing factory demands across an entire star system.
      - Factors affected: Material storage capacity requirements (-), production delay penalties (--).
    - Application: Cybernetic workforce integration (Req: Artificial intelligence).
      - Description: Deploying specialized, non-sentient AI clusters to oversee heavy manufacturing plants, removing the need for human safety parameters.
      - Factors affected: Production complexity capacity threshold (+), workforce demand (---), vulnerability to EMP weapons (+).

- Geological prospecting and orbital scanning. Planetary bodies feature finite numbers of veins, finding resource deposits needs to be an active, scalable technological pursuit.
  - Remote sensing (Req: Energy fields / Scanners)
    - Application: Orbital hyper-spectral mapping
    - Description: Scanning a planetary body from orbit using infrared and deep-radar sensors to discover obvious surface deposits and shallow veins.
    - Factors affected: Baseline vein discovery speed (+), initial planetary map visibility (++).
  - Application: Seismic crustal sounding
    - Description: Detonating controlled explosive charges on a planet's surface to bounce acoustic waves through the crust, revealing hidden deep-layer veins.
    - Factors affected: Deep-crust rare vein discovery chance (+++), work hours per prospecting mission (+).

- Superconductors

- Computers
  - Electronic computers
  - Quantum computers

- Energy fields, req: Superconductors
  - Shields
  - Scanners

- Electromagnetic weapons, req: Energy fields
  - Mass drivers: Slow bolts. Heavy shield damage. Long range.
  - Particle beams

- Energy weapons, req: Energy fields
  - Laser weapons: Weak in atmosphere. Instant beam. Weak against shields. Medium range.
  - Plasma weapons: Slow glowing bolts of superheated plasma. Strong against armor. Short range.
  - EMP-weapons: Non-lethal. Fries electronics. Short to medium range.

- Defensive weapons (point defense systems – PDS). Useless against energy beams.
  - Flak cannons (Industrial/metallurgy): Fires bursts of shrapnel. Cheap to manufacture, highly effective at shredding swarms of fragile bombers or missiles.
  - PD lasers (energy fields/lasers): Low-yield, rapid-tracking lasers. Instantly zaps incoming physical torpedoes or particle streams before they hit the main shields. High-power draw.

- Nanotechnology. Required for electronics > 4

- Antimatter
  - Antimatter harvesting / storage: Magnetic containment facilities capable of holding positrons and antiprotons. req: Superconductors
  - Antimatter bombs / warheads: The ultimate destructive bomb category for erasing planetary fortifications or vaporizing enemy capital ships.
  - Antimatter engines: Extremely high-impulse propulsion that allows rapid interstellar transit inside a star system.

- Robotics

- Artificial intelligence

- Gene technology
  - Gene sequencing
  - Gene editing
  - Gene therapy

- Biological cloning

- Transhumanism & digital consciousness req: AI, gene technology
  - Cybernetic augmentation: Blends robotics with gene therapy to permanently enhance workforce productivity or ground-combat troop effectiveness.
  - Mind uploading / digital immortality: Converts citizens into digital data. Gameplay effect: Drastically reduces the reliance on farming and biological space requirements, transitioning your civilization's workforce into pure server architecture.

- Gravitational engineering, req: Energy fields, Warp 
  - Inertial dampeners: Neutralizes g-forces. Allows massive capital ships to turn tightly without crushing the crew inside, or lets fighters accelerate instantly.
  - Artificial gravity / grav-plating: Essential for maintaining crew health on permanent space stations and long-term spacecraft.
  - Singularity reactors: Harnessing micro-black holes for near-infinite power generation, surpassing fusion reactors.

- Warp req: Singularity reactors
  - Warp drive
    - Base application (level 1) base cost: Extremely high work and rarest materials (e.g., exotic matter / antimatter). 
    - Base effect: Allows travel between adjacent star systems at high power cost
    - Random upgrade research outcomes: When a player spends resources to research
      - Spatial stabilization (breakthrough - 10%): Warp speed increases by 30%, and the post-warp power drain is reduced to 20%. Complexity remains unchanged.
      - Engine optimization (success - 50%): The player chooses between: Option A (effect focus): +15% travel range across the galactic map. Option B (cost focus): -20% material cost to manufacture future warp engines.
      - Volatile folding (flawed design - 30%): Warp speed increases by 20%, but the engine's complexity spikes. It has a tiny random chance to damage the ship's hull on activation.
      - Subspace tear (catastrophic setback - 10%): The research fails to increase speed. Instead, the engine radiates massive amounts of energy. The ship becomes highly visible on enemy scanners across the galaxy whenever it drops out of warp.

### Weapons
We must have weapons that fulfill different roles so that there is a point to research different technologies and combine them on ships and stations. 
Also, designing ships to fulfill different roles should be encouraged. Weapons are unlocked and improved by researching various technological applications. 
Some weapons are better at long range and some are better at close range.

#### Weapon types
- Offensive ship weapons. For attacking other ships and space stations.
- Defensive ship weapons. Point defense. For shooting incoming missiles, torpedoes, small fighter or bomber crafts and particles from offensive particle weapons.
- Bombs. Makes big explosions. Best for bombarding planets.

### Raw materials
Raw materials are mined or extracted in other ways from planetary bodies, asteroids, comets, moons and planets.
Raw materials (ore, mixed gasses, deposits like oil, coal, natural gas, etc.) can be refined into resources (metals, pure gasses, etc.). Some metals might occur in pure form, 
typically the nobler metals, but most will be mixed with other elements.
Each planetary body has a unique composition of raw materials. Some planets, like earth, have most of their available raw materials in veins 
in the crust, which have much higher concentration of certain minerals than the rest of the crust. The composition of veins will be based on the planet's composition.
Finding such veins requires prospecting and will be the main way of extracting certain minerals. 
In the game, a populated planet will start with a number of known veins with known content, while more can be found later. 
A planet will have a finite number of veins. 
- Plan for 60 to 80 different raw material types.
- Resources can also be extracted from the planetary body outside of veins and deposits, and will be expected to have the default composition of the planetary body.
- Asteroids are a special case; they might consist of rock, mixed metals or even pure metals.

### Materials
What things are built from affect their properties. Building a spaceship from carbon nanotubes will make a strong and lite ship, but require a huge industry producing nanotubes.
For a material to be transported to space, the transportation cost must be paid. The cost will vary with infrastructure (the relevant technical applications used at the location) and
the g-force of the body the material is transported from, and is a number of credits per kg.
- Id
- Name
- Description
- Chemical composition: List of elements and % of composition
- Weight per unit in kg
- Material strength
- Complexity: How challenging it is to produce. 

### Races
The races will have the properties of the population in an empire. 
- Id
- Name
- Description
- Intelligence: Influence research speed
- Physical strength: Influence combat strength some production
- Society structure: Individualist, collectivist, hive mind. A hive society will reproduce linearly with respect to how many queens they have – which means linearly. Other societies wil reproduce with respect to how many they are in fertile age and their resource state.
- Preferred g-force. 
- Preferred temperature. 
- Chemical composition: Carbon based, silicon based, etc.
- Breathing atmosphere: Oxygen based, nitrogen based, etc.
- Fertile age span (from, to): Influence reproduction speed
- Nutrient type: Organic (plants, animals, fungi..), rock (various types of rock), metal (various types of metals) or gas (various types of gas).
  - Carbon based
    - Nitrates, phosphates, potash, liquid_water
    - High nutrient spread requirement means players must grow multiple crop varieties or face happiness penalties.
  - Silicon based
    - Silicates, limestone, iron_ore, and trace refined metals like refined_magnesium.
    - They do not consume agricultural food. They eat your primary ship-building resources. Overpopulation of these races will literally consume the raw structural materials needed to build your fleet.
    - Low nutrient spread requirement means players can grow a single crop variety and still be happy.
  - Gas-breathing/absorbing races
    - Thick atmospheric feeds of methane_ice, ammonia_ice, hydrogen_gas, or nitrogen_gas
    - Incredibly cheap to maintain in the outer systems of M-class or brown dwarf stars, but completely incompatible with oxygen-rich terrestrial worlds.
- Nutrient spread requirement: Some races happily subsists on one food type, others require a diverse diet. Influence happiness and reproduction speed.
- Retirement age: Baseline retirement age cut-off for the race. Some technologies adjust the retirement age for races, so this is a baseline value rather than a constant value.

### Professions
- Id
- Name
- Description
- Type: What the profession is for: Soldier, farmer, ship crew, miner, industrial worker, scientist, engineer, technician, police, buorocrate, etc.
- Minimum intelligence requirement: Required for the profession
- Minimum strength requirement: Required for the profession
- Complexity: How complex the profession is. Influence how fast population can be trained for the population and how much experience is required to level up.
- Retirement age: Percentage value used with the race's baseline retirement age cut-off.

### Population
Planets and space stations will have a population of one or more races. Population is segregated by rase and age. For races eligible to reproduce, 
only the part of the population in the fertile age span will do so. Hive societies will only reproduce if they contain a queen of fertile age. 
The population will be influenced by the nutrient type and spread requirement of the available food stored in the planet or space station. 
Ships containing population modules that do not have stasis chambers will behave the same way.
A group of people can be trained for a profession. They will have a training level for it that will decide how efficient they are at the task.

### Empires
Each empire will have its own economy and technology tree. It can contain a population of multiple races. 
It will also have its own society structure, which will influence the way it reproduces and the way it interacts with other empires.
The empire may consist of many planets. It will control spaceships, colonies and ground troops. 
An empire has a range of influence in the galaxy based on its controlled planets, space stations and asteroids. 
Any celestial body that is within the influence range of an empire is considered part of that empire.

### Economy
For simplicity, we will have universal credits reflecting money. Money, resources and spaceships might be traded between empires that have sufficient diplomatic relations.
An empire can use its credits to directly pay for costs in its planets and space stations that they own that do not generate enough income to provide for themselves.
Money can be transferred according to the technology level of the empire.
The economic engine balances planetary logistics, workforce compensation, and interstellar trade networks through a unified credit system.

#### Universal credits
- All financial transactions use universal credits, representing the liquid fiat wealth or asset-backed reserves of an empire.
- Credits can be spent globally to subsidize planetary deficits, fund technology research, or pay for ship module manufacturing.

#### 2. Currency latency and transfer limits
- Financial capital is subject to localized physics limits. Credits generated on remote planets must be physically or digitally routed back to the central imperial treasury.
- Early-game transfer speed is locked to physical transport speeds, requiring courier spacecraft to haul physical currency caches between systems.
- Advanced research in quantum computing and communications unlocks instant, secure sub-space electronic banking grids, reducing financial latency to zero.

#### 3. Planetary balance sheets
Each colony or space station operates an autonomous localized budget containing:
- **Revenue:** Derived from population income taxes, corporate production tariffs, and commercial docking fees inside commerce modules.
- **Expenditures:** Calculated as the sum of professional workforce salaries, infrastructure module upkeep, and retired citizen welfare costs.
- **Subsidies:** If a planet’s expenditures exceed its localized revenue, the empire can allocate central treasury credits to bridge the deficit, assuming a secure logistical link exists.

#### 4. Interstellar commerce and the gravity factor
- Trade agreements between sovereign empires allow the automated exchange of credits, raw materials, or fully assembled spaceships.
- Any material trade routed from a planet's surface must automatically deduct the required gravity transportation fee (credits per kilogram) from the seller's profit margins. Using a space elevator also has a cost, although much lower.
- Races with incompatible nutrient requirements cannot trade food assets effectively unless an intermediate xenobiologist processing facility converts the organic matter or crystalline minerals into a globally viable synthetic format.

### Space stations
Around planets, moons and asteroids it will be possible to build space stations. It will also be possible to build deep space stations around stars and even outside solar systems.
Space stations will have their own resource stores, population, industries and defenses. A space station will contain multiple modules.

### Spaceships
Spaceships are the primary means of transportation and combat in the game. They are made up of a combination of modules and can be customized to suit the needs of the player.
Spaceships can be built on planets or in space and can be upgraded with additional modules as the player progresses. 
Building spaceships on planets will require them to blast off and escape the g-forces of the planet into space before they can be useful.

### Modules
A module will be made of a selection of raw materials, reflecting the resources required to build it. A module will require power to be functional.
Module types:
- Habitation module
- Power module
- Defense module
- Production module
  - Power production
  - Food production
  - Ore refining
  - Industry production
- Storage module
  - Power storage
  - Food storage
  - Ore storage
  - Resource storage
  - Industry storage
- Hangar module
- Commerce module
- Science module

### Food
Each organic race requires food. Some races might require more food than others. Non-organic races do not require food. 
Food will be produced on planets or by food production modules in space and stored in food storage modules. 
Food will be consumed by the population on planets, space stations and spaceships. Troops are also population and require food. People in stasis do not require food. 
Food can be traded between empires that have sufficient diplomatic relations, but the races must have compatible food for them to be able to consume it.

## How to read
Each module contains a `MODULE.md` file with specific details about its purpose and components to help AI agents navigate the codebase.

## How to build and run
- Build everything and run the tests: `mvn clean install`
- Run only the engine tests: `mvn test -pl engine`
- Start the game: `mvn javafx:run -pl frontend` (or run `com.spaceconquest.frontend.Main`)

## Implemented so far
- Static data model loaded from JSON property files: solar systems, races, materials, technologies with
  applications, star properties (Hertzsprung-Russell) and professions.
- Procedural galaxy generation with realistic star mass distribution and colors.
- Galaxy map with zoom (buttons and mouse wheel), goto search, entity focus panels and tooltips.
- Menubar with empire, diplomacy, technology, fleet, galaxy view and game menu pages; opening a page pauses
  the game and closing it resumes at the previous speed.
