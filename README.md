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

[Technologies with applications](Technologies.md)

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

# Species and Race Archetypes

This document codifies the biological, chemical, and sociological foundations of all sapient populations within the galactic ecosystem. Species parameters act as the structural filters for empire demographics, restricting workforce profession eligibility, defining resource consumption loops, and dictating colonization logistics across various stellar environments.

[Species and Race Archetypes](Species.md)

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

#### The Imperial Ministry
The central government cabinet provides empire-wide ideological modifiers, economic efficiencies, or tactical bonuses.
*   **The workforce pool:** Any individual citizen belonging to an eligible workforce cohort can be selected to fill a ministerial post. Candidates are drawn directly from the existing training groups mapped in the `professions.json` database.
*   **Professional background synergy:** While any profession can legally fill a post, an individual whose background matches the specific topic of the ministry grants an **Expertise Synergy Bonus**, significantly increasing the post's baseline effectiveness.
*   **The bureaucrat exception:** Due to their administrative optimization, citizens trained in the `bureaucrat` profession possess universal efficiency traits. They can fill *any* cabinet position and grant a stable baseline administrative bonus, even if the post does not match their core topic.

#### Ministerial Portfolios and Optimal Backgrounds
The home planet's cabinet consists of specialized posts that influence distinct gameplay mechanics across the entire empire:

*   **Ministry of Industry and Refining**
  *   *Systemic effect:* Enhances raw vein extraction rates and alloy processing speeds.
  *   *Optimal matching background:* `industrial_worker` or `miner`.
*   **Ministry of Agricultural and Biosphere Stability**
  *   *Systemic effect:* Increases food production efficiency and nutrient spread happiness modifiers.
  *   *Optimal matching background:* `farmer`.
*   **Ministry of Technology and Technological Application**
  *   *Systemic effect:* Accelerates research speed variables and lowers upgrade complexity ratings.
  *   *Optimal matching background:* `scientist`.
*   **Ministry of Defense and Logistics Procurement**
  *   *Systemic effect:* Boosts the ground combat strength of soldiers and lowers spaceframe manufacturing times.
  *   *Optimal matching background:* `soldier` or `engineer`.
*   **Ministry of Finance and Central Commerce**
  *   *Systemic effect:* Raises the collection efficiency of transaction tariff rates across all commercial hubs.
  *   *Optimal matching background:* `bureaucrat`.

#### System Governors and Granular Economy
To balance administrative overhead with deep economic simulations, governance is executed at the solar system level rather than requiring a separate leader for every individual cosmic body.
*   **The system-wide restriction:** Empires are restricted to **one appointed governor per planetary system**. A single governor oversees the political stability and infrastructure modifiers of the host star system as a single administrative territory.
*   **Granular entity tracking:** While leadership is unified at the system scale, **materials, population cohorts, veins, and industries remain completely granular and separate per space entity**. The engine continues to independently simulate individual data models for each planet, moon, asteroid mining outpost, and orbital space base spinning within that system.
*   **Localized application:** A governor's background bonuses apply across all space entities under their system jurisdiction. A governor with a mining background will accelerate raw element extraction for both a rocky planet's deep crustal veins and a nearby moon's regolith solar wind traps simultaneously. However, if that system consists mostly of agricultural terraformed worlds, a governor with a mining profile will yield zero baseline efficiency gains, forcing players to match governor backgrounds to a system's dominant industry type.
*   **Systemic stability and crime mitigation:** The active presence of an appointed system governor acts as an infrastructure anchor across the entire local coordinate cluster. It applies a system-wide modifier that suppresses crime metrics and boosts the baseline efficiency of `police` professions stationed on any local planet or space base.

#### Ideological Accession Mechanics (Democracy vs. Autocracy)
The method by which ministers and governors ascend to power is strictly dictated by the active `societyStructure` and political alignment configuration of the empire:

*   **Democratic Systems (Individualist alignment)**
  *   *Mechanic:* Positions are **elected** by citizen cohorts through an automated political cycle.
  *   *Gameplay impact:* The player has limited direct control over cabinet placement or regional system assignments. Every election cycle, local citizen age groups vote based on system-wide happiness and current material shortages. A severe systemic food shortage will cause local populations to democratically elect a `farmer` to manage that solar system. If the player forces an unwanted replacement, civilian happiness variables crash, triggering immediate workforce efficiency penalties.
*   **Autocratic Systems (Collectivist alignment)**
  *   *Mechanic:* Positions are **appointed** directly by the sovereign player from a pool of eligible candidates.
  *   *Gameplay impact:* Total state command. The player can instantly rotate, dismiss, or insert any trained professional into any ministerial or system governor slot without political friction or regional resistance. However, autocracies lack the organic public happiness bonuses generated by popular democratic elections, increasing the long-term dependency on local `police` presence across all space entities to maintain structural stability.
*   **The Hive Mind Exception**
  *   *Mechanic:* **Completely bypasses government.** Because a hive mind society features no individual individualism, private wallets, or political factions, it does not utilize ministries, system governors, elections, or appointments. The central consciousness directs all modules, space bases, and worker populations across all astronomical bodies with uniform baseline efficiency, sacrificing political specialization bonuses for absolute command stability.

### Economy
For simplicity, we will have universal credits reflecting money. Money, resources and spaceships might be traded between empires that have sufficient diplomatic relations.
An empire can use its credits to directly pay for costs in its planets and space stations that they own that do not generate enough income to provide for themselves.
Money can be transferred according to the technology level of the empire.
The economic engine balances planetary logistics, workforce compensation, and interstellar trade networks through a unified credit system.
The economic engine of the galaxy is split into two primary layers of capital flow: the public state budget and the private citizen market. The degree of separation between these sectors dictates how an empire grows, builds, and taxes its workforce.

#### Universal credits
- All financial transactions use universal credits, representing the liquid fiat wealth or asset-backed reserves of an empire.
- Credits can be spent globally to subsidize planetary deficits, fund technology research or pay for ship module manufacturing.

#### Public economy
The public economy represents the liquid credit reserves controlled directly by the empire's central government.
*   **State revenue:** Liquid credits are generated primarily by taxing the private sector. This includes planetary income taxes levied on working citizen cohorts, corporate production tariffs on refined materials, and commercial docking fees collected inside orbital commerce modules.
*   **State expenditures:** The treasury is used to pay for macro-infrastructure projects. This includes funding technological research applications, paying the base upkeep costs of space stations and colonies, compensating state employees (`soldiers`, `scientists`, `bureaucrats`), and covering the credit-per-kilogram gravity launch tax for state-owned spacecraft.
*   **Planetary subsidies:** If a frontier colony's localized public maintenance exceeds its tax collection, the state treasury can pump credits directly into the world to prevent structural decay, assuming a secure logistics network is active.

#### Private economy
The private economy represents the organic wealth generated, stored, and spent by individual citizens and commercial entities within non-hive societies.
*   **Citizen income:** Population groups in eligible age brackets receive credit salaries from their active professions. High-complexity jobs (`engineers`, `medics`, `scientists`) yield significantly higher private wages than low-complexity labor (`miners`, `farmers`).
*   **Private purchasing:** Citizens spend their private wealth on daily survival and luxury needs. They purchase compatible food varieties from local markets, buy residential housing space in habitation modules, and pay for medical treatments.
*   **Demographic impact:** A thriving private economy directly drives population growth rates and maximizes colony happiness. If an empire sets public tax rates too high, it strips liquidity from the private market. This forces citizens to eat basic mono-crop hydroponic sludge instead of a diverse nutrient diet, causing happiness to plummet and triggering civil unrest or workforce efficiency penalties.
*   **Pensions and retirement:** When a citizen crosses their profession's `retirementLifespanPercentage`, they stop receiving a salary. Instead, they consume private savings or rely on a state-mandated public welfare subsidy to purchase food, remaining on the colony's balance sheet as pure consumers.

#### Private companies
In individualist societies, private wealth does not remain stagnant in citizen wallets; it dynamically aggregates into autonomous corporate entities.
*   **Wealth aggregation:** Private companies accumulate capital by operating local supply chains, managing consumer real estate in habitation modules, running private medical clinics or selling compatible food stocks. The profits generated from these civilian transactions are consolidated into corporate investment pools.
*   **Market-driven investment:** Private companies continuously scan the colony, star system, and greater empire for high-value logistics bottlenecks and resource shortages. They prioritize investing their aggregated wealth directly where there is a market shortcoming.
*   **Shortcoming resolution:** Example: If a colony lacks enough `refined_silicon` to support local high-complexity electronics manufacturing, private companies will automatically pool capital to construct new ore refining zones or fund `miner` workforce training. If a colony is starving due to a lack of nutrient diversity, private enterprises will independently build food production modules or establish private cargo trade lanes to import missing organic components.
*   **State interaction:** While these corporations operate autonomously to generate more private wealth, their assets exist within the physical jurisdiction of the empire. The state can indirectly manipulate corporate investment behavior by adjusting corporate tax rates, issuing public subsidies for specific sectors or blockading certain raw material markets entirely.

#### The hive mind exception
Hive mind societies completely bypass the division between public and private sectors. Because every individual organism is a non-sentient extension of the central consciousness or the fertile queens, private ownership, currency, private companies, and commerce do not exist.
*   **Zero-market mechanics:** Hive minds do not collect taxes, do not pay workforce salaries and do not track citizen happiness. There are no private commerce modules, retail markets, or corporate investments.
*   **The total command economy:** 100% of generated resource extraction and industrial manufacturing flows directly into a unified state grid. The collective population consumes food and energy directly from state storage modules as a baseline maintenance cost, similar to computing processors or robotic drones.
*   **The demographic trade-off:** While hive minds completely escape the economic friction of tax optimization, pensions, private monopolies and worker riots, they suffer from a rigid workforce structure. They cannot utilize private market incentives or autonomous corporate investments to naturally solve supply chain shortages or accelerate training speeds for high-complexity professions, relying entirely on slow, linear queen reproduction and central research structures to evolve.

#### Planetary balance sheets and restrictions
Each colony or space station operates an autonomous localized budget containing strict operational parameters:
*   **Revenue calculation:** Localized revenue is derived from population income taxes, corporate production tariffs, and commercial docking fees inside commerce modules. Hive mind worlds generate 0 baseline revenue and operate strictly on material upkeep values.
*   **Expenditures calculation:** Localized expenditures are calculated as the sum of professional workforce salaries, infrastructure module upkeep and retired citizen welfare costs.
*   **Subsidies and network links:** If a planet’s expenditures exceed its localized revenue, the central treasury can allocate credits to bridge the deficit. However, this transfer is strictly bounded by the empire's technology level.
*   **Currency latency and transfer limits:** Financial capital is subject to localized physics limits. Credits generated on remote planets must be physically or digitally routed back to the central imperial treasury. Early-game transfer speed is locked to physical transport speeds, requiring courier spacecraft to haul physical currency caches between star systems. Advanced research in quantum computing and communications unlocks instant, secure sub-space electronic banking grids, reducing financial latency and transfer friction to zero.
*   **Interstellar commerce and the gravity factor:** Trade agreements between sovereign empires allow the automated exchange of credits, raw materials or fully assembled spaceships. Any material trade routed from a planet's surface must automatically deduct the required gravity transportation fee (credits per kilogram) from the seller's profit margins, unless bypassed by structural applications like a space elevator.
*   **Nutrient conversion restrictions:** Races with incompatible nutrient requirements cannot trade food assets effectively unless an intermediate xenobiologist processing facility converts the organic matter or crystalline minerals into a globally viable synthetic format.

### Private Corporations

In individualist and collectivist societies, the private economy extends into space logistics. Autonomous private corporations can independently purchase, own, and operate their own fleets of cargo transports and mine ships to maximize profit margins and exploit remote astronomical resources.

#### Corporate profiling and structural data
Every private corporation operating within an empire's territory is tracked by the game engine as an independent economic agent with the following structural properties:
*   **Id and Name:** Unique identifiers (e.g., `corp_hephaestus_foundries`, `corp_bio_grow_alliancel`).
*   **Headquarters location:** The specific planet or space station where the corporation's primary financial assets are legally anchored.
*   **Liquid capital reserves:** The current pool of universal credits available to the corporation for investment and expansion.
*   **Asset holdings:** A structural list of real estate, private cargo freighters, production facilities, or mineral veins owned or leased by the company.
*   **Market orientation:** The sector preference of the corporation (e.g., extraction, metallurgy, agriculture, transportation, or consumer services), which modifies how aggressively it evaluates specific types of market shortcomings.

#### Capital accumulation mechanisms
Corporations expand their liquid capital reserves by providing essential and luxury goods and services directly to the civilian workforce. They capture wealth from private citizen wallets through four main avenues:
*   **Residential real estate:** Leasing out or managing housing spaces within state-built or corporate-owned habitation modules.
*   **Nutrient distribution:** Retailing compatible food types and diverse diets to populations through local planetary marketplaces and commerce modules.
*   **Consumer healthcare:** Operating private medical clinics and optimization facilities that charge citizens out-of-pocket fees to maintain their happiness and longevity parameters.
*   **Secondary B2B logistics:** Contracting with other private entities or the state to refine raw ores into pure elements (such as transforming `iron_ore` into `refined_iron`) for a localized processing fee.

#### Shortcoming detection and dynamic investment
Instead of accumulating dead credit balances, private corporations run an automated investment evaluation pass during the empire's turn-update loop. The corporation calculates a **Shortcoming Score ($S_m$)** for every material, resource, and profession type within its logistics range:

$$S_m = \left( \frac{\text{Local Demand} - \text{Local Supply}}{\text{Local Supply} + \epsilon} \right) \times \text{Market Price Modifier}$$

When a critical shortcoming threshold is breached, the corporation reacts dynamically based on its sector orientation:
*   **Infrastructure funding:** If a colony suffers from a severe shortage of high-complexity electronics due to a deficit in `refined_silicon`, a metallurgy-oriented corporation will automatically allocate credits to construct a new surface refining zone or upgrade an existing foundry's complexity rating.
*   **Workforce training subsidies:** If an asset cannot operate at peak efficiency due to a lack of skilled labor, private corporations will independently fund training programs for local citizen cohorts. They will pay the work hour costs to train low-skill citizens into specialized professions like `miners`, `technicians`, or `logistics_officers`.
*   **Logistical bridge building:** If a resource is abundant in the outer rim but critically missing at a core manufacturing station, transport corporations will independently purchase cargo hull modules, hire `ship_crew`, and establish private supply lanes to arbitrage the price difference.

#### Sovereign state interactions and intervention
The empire’s central government does not control private corporations directly, but can manipulate their behaviors using macro-economic policy tools:
*   **Corporate taxation:** Setting a high corporate tax rate drains liquid capital reserves from corporate investment pools into the public state treasury. This slows down autonomous corporate expansion but provides the state with maximum funding for military ship production.
*   **Public zoning laws:** The state can lock specific planetary surface slots, preventing private companies from building factories on worlds designated purely for state military shipyards or massive public farming projects.
*   **Sector subsidies:** The state can issue targeted tax credits or credit grants to specific corporate identifiers. For example, offering a subsidy to agricultural corporations lowers their investment costs, driving them to prioritize building *hydroponic growth arrays* over mining operations.
*   **Nationalization:** In times of total war or economic collapse, individualist states can forcefully nationalize corporate assets (such as cargo fleets or weapon foundries), transferring them directly to the public economy. This action instantly destroys corporate trust, driving private capital away from the sub-sector and causing severe happiness drops among the citizen cohorts who held corporate investments.

#### Corporate fleet procurement
Private corporations do not design their own ship hulls; instead, they utilize the player’s or the empire's custom ship designer blueprints.
*   **Blueprint selection:** The corporate AI continuously evaluates the empire's unlocked public ship designs. When a corporation detects a logistics shortage or a high-value asteroid node, it scans for blueprints tagged with the matching **Cargo Transport** or **Mine Ship** primary operational roles.
*   **Shipyard contracts:** The corporation spends its accumulated private liquid capital reserves to place a manufacturing order at a valid planetary or orbital shipyard module. The corporation pays the exact universal credit cost to the shipyard owner (which can be the state or another private mega-corporation).
*   **Material consumption:** Constructing the corporate ship consumes the required refined metals, electronics, and composites from the local industrial storage inventory. The corporation must wait out the required work hour assembly window just like a state-owned military warship order.

#### Mine ship operations
When a private corporation takes delivery of a vessel tagged with the **Mine Ship** role, it operates the asset to extract raw planetary ring or asteroid rock reserves independently of the central government.
*   **Asset deployment:** Corporate mine ships autonomously route to un-depleted asteroid fields, comets, or low-G moons within their active logistics range. They prioritize nodes rich in high-value or high-demand resources matching current *Market Shortcoming Scores*.
*   **Yield destinations:** The raw extracted ores do not enter the state's public storage modules. Instead, the corporation routes the raw yield back to its own corporate warehouses, or sells the unrefined mass directly to local *Commercial Hubs* to be bought and processed by commercial metallurgy or gas processing modules.

#### Cargo transport operations
Corporate-owned **Cargo Transports** function as the primary physical vehicles for market arbitrage across the galaxy.
*   **Shortcoming resolution:** These ships are completely controlled by the corporation’s local trade algorithms. If a colony has a massive deficit in `refined_silicon`, corporate cargo haulers will independently purchase the material from a surplus hub, load the vaults, and fly to the shorted colony to liquidate the stock for a massive private profit.
*   **The systemic launch tax burden:** Corporate captains are fully bound by the game's physics loops. When a corporate cargo transport blasts off from a heavy terrestrial world, the corporation must automatically pay the calculated credit-per-kilogram launch gravity tax to the planetary hub owner. This fee is automatically deducted from the corporation's gross trading margins, forcing the corporate AI to naturally favor low-G moons or zero-G orbital space stations for high-volume freight transfers.

#### State jurisdiction and maritime laws
Even though these fleets are privately owned and operated, they must respect the physical and diplomatic laws of the empire within whose range of influence they travel.
*   **State taxation and tariffs:** Every time a corporate cargo transport buys or sells resources at a planetary or space base commercial hub, it triggers the state's active *transaction tariff rate*. The state automatically skims credits from these private corporate transactions, turning a booming corporate trade network into a massive passive revenue stream for the public state treasury.
*   **Smuggling and police interception:** If a corporation attempts to bypass state tariffs by using unpoliced, corrupt commercial loops, the state can deploy military combat hulls or assign the local `police` profession to scan, fine, or forcefully impound corporate cargo transports.
*   **Eminent domain and wartime requisition:** During times of catastrophic collapse or foreign invasion, states can pass emergency laws to nationalize corporate fleets. This forcefully converts privately owned cargo and mine ships into state-controlled utility platforms. While this action gives the military instant logistical transport capability, it causes corporate trust to crash, freezing future private capital investments across that entire star sector.


### Commercial hubs
Every planet functions as a commercial hub. So does a space base with a commercial module. It acts as the primary physical bridge between the public state treasury, private citizens, and autonomous private corporations. It serves as a trade hub, capturing transaction fees from civilian purchases and interstellar freight while providing market liquidity to the local colony economy.

#### Properties and data structure
Every commerce hub is managed using the following structural parameters:
*   **Id and Name:** Unique identifiers mapping the instance to its host entity
*   **Module tier / complexity:** Dictates the maximum volume of resource traffic and financial liquidity the hub can process per turn without bottlenecks.
*   **Transaction tariff rate:** A state-adjusted percentage fee levied on all private and corporate transactions occurring within the hub.
*   **Storage capacity linkage:** The maximum weight limit (in kilograms) of consumer goods and raw materials the hub can temporarily hold for marketplace trading.
*   **Logistics range:** The operational distance (measured across the galactic coordinate map) over which the hub can broadcast its local buy/sell orders to private corporate networks.

#### Civilian retail and tax generation
The commerce hub provides the physical marketplace where private citizen cohorts spend their disposable income.
*   **Nutrient spread retail:** Citizens access the hub to purchase compatible food varieties. The hub tracks available food inventory. If a diverse selection of organic or synthetic nutrients is present, citizens spend more credits, directly boosting colony happiness and population growth variables.
*   **Consumer service leasing:** Private companies use the hub's commercial slots to operate healthcare clinics, recreational lounges, and retail spaces, paying a fixed credit lease to the hub owner.
*   **Automated tax harvesting:** Every time a citizen purchases food or services, the game engine processes the transaction through the hub's *transaction tariff rate*. The calculated credits are automatically deducted from the private sector and deposited directly into the public state treasury as tax revenue.

#### Corporate B2B trading and arbitrage
Beyond civilian retail, the commerce hubs handles high-volume business-to-business (B2B) trade between competing private corporations and the state.
*   **Shortcoming liquidation:** Corporations use the hub's commodity exchange to sell materials they have in surplus and buy items matching their local *Shortcoming Score*. For example, an electronics corporation will place bulk buy orders for `refined_silicon` and `refined_silver` within the hub's digital ledger.
*   **Docking and freight fees:** Independent cargo freighters operated by transport corporations must pay a baseline docking fee in credits to utilize the hub's cargo transfer systems. This fee scales with the total weight (kilograms) of the materials being offloaded.
*   **The gravity tax deduction:** If a commerce hub is located on a planetary surface, any corporate transaction involving the export of heavy resources (like `iron_ore` or `refined_lead`) automatically calculates the planetary G-force penalty. The credit cost to blast that mass into space is displayed on the hub's exchange board and is deducted from the transaction's net profitability.

#### Systemic dependencies and failure states
To remain operational, the commerce hub relies on the active presence of other professions and modules within the local colony network:
*   **Workforce requirement:** The hub requires an active allocation of trained `bureaucrats` and `technicians`. If the local population suffers a headcount deficit in these professions, the hub's processing efficiency drops, causing transaction backlogs and reducing collected tax revenues.
*   **Power grid dependency:** Operating the digital trade ledgers, atmospheric storage seals, and automated cargo crane matrices requires a constant feed of electricity from local power modules. If the power grid fails or runs a deficit, the commerce hub goes offline, freezing all private market transactions and causing an immediate drop in local citizen happiness.
*   **Security blanket:** Because commerce hubs store immense liquid wealth and material inventory, they actively increase the crime metrics of a planet or space station. If an empire does not station an adequate headcount of the `police` profession nearby, corporate smuggling rings will bypass the transaction tariffs, diverting wealth into black market pools and starving the public state treasury.


### Space stations
Around planets, moons and asteroids it will be possible to build space stations. It will also be possible to build deep space stations around stars and even outside solar systems.
Space stations will have their own resource stores, population, industries and defenses. A space station will contain multiple modules.

### Spaceships
Spaceships are the primary means of transportation and combat in the game. They are made up of a combination of modules and can be customized to suit the needs of the player.
Spaceships can be built on planets or in space and can be upgraded with additional modules as the player progresses. 
Building spaceships on planets will require them to blast off and escape the g-forces of the planet into space before they can be useful.
When designing spaceships the user selects the specific role the ship is intended to fulfill, then selects the required modules to construct the ship. Total mass and required materials should be shown during this process. Also the current abilities so user can see if more engines or power is required.

[Spaceship roles and modules](Spaceships.md)

### Food
Each organic race requires food. Some races might require more food than others. Non-organic races do not require food. 
Food will be produced on planets or by food production modules in space and stored in food storage modules. 
Food will be consumed by the population on planets, space stations and spaceships. Troops are also population and require food. People in stasis do not require food. 
Food can be traded between empires that have sufficient diplomatic relations, but the races must have compatible food for them to be able to consume it.

### Ground Combat and Fortifications
Planetary sieges and ground combat represent the final stage of stellar conquest. While combat ships command the orbital spaceways, conquering a celestial body requires physically overcoming its localized defenses through ground invasion loops, material-backed fortifications, and structural siege dynamics.

[Ground combat and fortifications](GroundCombat.md)

### Maneuvering and Atmospheric Aerodynamics
Moving a spaceship across the galaxy requires navigating two distinct physics environments: the frictionless vacuum of deep space and the dense, drag-heavy atmospheres of terrestrial planets. Managing a starframe's maneuvering profile relies on specialized Reaction Control Systems (RCS), which must balance the ship's total structural mass against local planetary gravity and atmospheric resistance variables.

[Maneuvering and Atmospheric Aerodynamics](AtmosphericAerodynamics.md)

### Geological Prospecting and Resource Extraction
The resource system simulates a dynamic, high-fidelity geological model where planets are treated as structurally active chemical bodies. Rather than pre-programming every resource vein at the start of a game, a planetary system initializes with a baseline known composition. The majority of its hidden mineral wealth remains unmapped, requiring active public or private prospecting cycles to locate.

[Geological Prospecting and Resource Extraction](GeologicalProspecting.md)

### Diplomatic Relations and Range of Influence
Interstellar diplomacy governs how sovereign empires coexist, demarcate territorial sovereignty, and execute commerce across the galactic map. Rather than relying on hard physical borders, territory is defined dynamically through an empire's **Range of Influence**, establishing spaces where local jurisdictions, public taxes, and maritime laws are forcefully enforced.

[Diplomatic Relations and Range of Influence](DiplomaticRelations.md)

### Habitation, Life Support, and Population Dynamics
Maintaining a biological workforce in the vacuum of space or on hostile planetary surfaces requires specialized habitation infrastructure and continuous atmospheric recycling. This system governs how population cohorts grow, consume resources, and survive across different celestial entities based on their biochemical profiles.

[Habitation, Life Support, and Population Dynamics](Habitation.md)

### Crime, Smuggling, and Black Market Leakage

The crime simulation loop represents the organic friction within an empire's private economy. In individualist and collectivist societies, the movement of high-volume civilian commodities and commercial freight naturally creates opportunities for illegal capital accumulation. If left unpoliced, crime creates a black market siphon that redirects wealth away from the state treasury and into shadow corporate networks.

[Crime, Smuggling, and Black Market Leakage](Crime.md)

### Ship Customization and the Modular Hull Design Interface

The ship design interface is a hard physics-bounded engineering playground. Instead of selecting from fixed, pre-defined hull size classes (such as "Destroyer" or "Cruiser"), users construct starframes completely from scratch. The interface dynamically calculates the ship's dry mass, slot limits, operational classification, and launch restrictions based entirely on the user's custom choice of modules and structural materials.

[Ship Design Interface](ShipDesign.md)

### Power, Propulsion Energy, and Fuel Logistics

The power grid represents the operational lifeblood of every spaceship, space station, orbital space base, and planetary colony. Every active installation or module requires a continuous supply of electricity to remain functional. Power is treated as a strict physics-bounded flow system, balancing variable energy generation methods—both fuel-dependent and renewable—against structural demands and storage limits.

[Power, Propulsion Energy, and Fuel Logistics](Power.md)

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
