# Space conquest game
This will be a space conquest game with similarities to Stellaris, Master of Orion and Distant worlds 2. 
The game will have resources, technology, races, spaceships and combat both in space and on the ground.

## Documentation status
This repository documents an evolving game design. A feature described without a `Draft` label is not necessarily implemented. All design details may change and `Draft` sections are especially unsettled. The module `MODULE.md` files describe intended boundaries and the current implementation notes below identify known gaps. Check the source and tests when determining current behavior.

## Project structure
- `engine`: core game logic and state.
- `control`: input handling and player/AI logic.
- `frontend`: main entry point and user interface. All assignments made must be backed by actual data in the game world. Example: you cannot assign more scientists to perform research than available in the empire.

## Technical stack
The current Maven build targets Java 27. Use a Java 27 JDK to build and run the project. The application uses FXGL and JavaFX for its desktop UI, Jackson for JSON data and saves and JUnit for tests. Spring Boot is not a current dependency.

## Game components
The design aims to load configurable properties for races, technologies, technological applications, materials, professions and raw materials from files in the resources folder. The current code also contains hard-coded simulation constants and catalogs.

### Time (Draft)
- The engine `GameClock` owns the campaign calendar. The frontend schedules real-time pulses and displays the time returned by the engine. Available speeds are 1 minute, 1 hour, 6 hours, 12 hours, 1 day, 5 days and 10 days of game time per real second. The game can be paused.
- One simulation turn represents one game day. Commands enter at the start of the next daily turn. Systems that track progress in turns or work hours advance on daily boundaries, so a project can span several days. Population age brackets advance on calendar-year boundaries and democratic elections occur every five calendar years.
- A shared calendar does not mean every action has the same cadence. Some actions still complete immediately because they do not yet have a duration or project model; their intended work rates remain to be defined.

### Game start
The New game starting date / technology setting chooses one era preset. The default is **2027 — Contemporary industry**: an industrialized homeworld at a broadly present-day technology level, with electricity, rocketry, nuclear fission and industrial production. **2050 — Advanced rocketry** starts with colonies and offworld assets in the home system. **2200 — Basic warp technology** adds expansion into nearby systems. The selected preset sets both the calendar and the initial technology; the date is not a separate input.

Generated player and AI homeworlds start with public power facilities and private transport, mining, agriculture, refining and consumer manufacturing facilities. Their owner corporations, empire treasuries and population household accounts receive opening credits. The starting homeworld is intended to have a healthy economy that meets every tracked population need. Opening food and consumer-goods production are sized for the home population, with input reserves and household cash to support the first month. Populated colonies start with local market stock. Basic warp expansion claims do not overlap another empire's starting system, and selected expansion systems receive populated outposts. These are opening assets rather than a simulated first day: local balance sheets begin at zero revenue, expenditure and debt. The first-month balance test must be extended as new needs are implemented. Full input replenishment and income for every profession remain unfinished.

### Galaxy
The galaxy will consist of multiple systems. Systems with stars are solar systems. Most systems are solar systems. 
Planets, asteroids, comets and other stellar bodies might occur outside solar systems, but most planets are within solar systems. 
Each planet with its own resources, technology level and population. 

### Space entities
Planets, moons and asteroids are alle space entities. They can all have a population of multiple races, as well as various buildings and technical installations. 
Most entities will initially be uninhabitable, while others may be habitable but not suitable for certain races. Buildings and installations might later make the entity partly habitable.
Each entity will have mineable resources and industries which can be used to produce goods and technology.
All entities have a mass. The mass of an entity will influence its gravity and the amount of resources for each resource type it can mine.
The gravity or g-force will influence how much it costs to move materials from the entity into orbit using propulsion.

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
Every time a player optimizes a technical application, its attributes can be modified using an inverse scaling formula.
Example:
- The newly researched fusion drive: Low optimization. Massive material cost, requires rare gasses and has a complexity rating of 8 (meaning only your top-tier, expensive orbital ring facilities can even attempt to build it).
- The gen-5 optimized fission drive: High optimization. Cheap, uses common refined metals and has its complexity reduced to 2. A tiny, low-tech colony shipyard can pump them out in massive quantities.

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
[Technology mechanics](TechnologyMechanics.md)

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
- Resources can also be extracted from the planetary body outside of veins and deposits and will be expected to have the default composition of the planetary body.
- Asteroids are a special case; they might consist of rock, mixed metals or even pure metals.

### Materials
What things are built from affect their properties. Building a spaceship from carbon nanotubes will make a strong and lite ship, but require a huge industry producing nanotubes.
For a material to be transported from a planetary surface to orbit, the transportation cost must be paid. The cost will vary with the available lift method: rockets, mass drivers or space elevators. The `cargo_terminal` is intended for this ground-to-orbit handling, while the `CommercialHub` handles local market stock and transactions. The terminal's operational rules are draft.
Buying and selling goods within one planetary body has no transport charge or gross sale tariff. VAT may apply to eligible purchases if added later. Corporate profit tax is collected separately on positive realized profit after costs and carried losses; unpaid tax remains due from the corporation.
- Id
- Name
- Description
- Chemical composition: List of elements and % of composition
- Weight per unit in kg
- Material strength
- Complexity: How challenging it is to produce. 

### Industrial production mechanics

The industrial engine processes raw planetary or asteroid assets into refined elements, advanced composites or completed spaceframes. Rather than existing as abstract text toggles, industries operate as physical data entities executing closed-loop material transformations bounded by environmental constraints, energy draw and workforce skills.

[Industry production mechanics](Industries.md)

### Species and race archetypes

This document codifies the biological, chemical and sociological foundations of all sapient populations within the galactic ecosystem. Species parameters act as the structural filters for empire demographics, restricting workforce profession eligibility, defining resource consumption loops and dictating colonization logistics across various stellar environments.

[Species and race archetypes](Species.md)

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
- Natural lifespan: Baseline natural life expectancy in years for the race before technological augmentation. Used to scale chronological age against profession retirement lifespan curves.
- Nutrient type: Organic (plants, animals, fungi..), rock (various types of rock), metal (various types of metals) or gas (various types of gas).
  - Carbon based
    - Nitrates, phosphates, potash, liquid_water
    - High nutrient spread requirement means players must grow multiple crop varieties or face happiness penalties.
  - Silicon based
    - Silicates, limestone, iron_ore and trace refined metals like refined_magnesium.
    - They do not consume agricultural food. They eat your primary ship-building resources. Overpopulation of these races will literally consume the raw structural materials needed to build your fleet.
    - Low nutrient spread requirement means players can grow a single crop variety and still be happy.
  - Gas-breathing/absorbing races
    - Thick atmospheric feeds of methane_ice, ammonia_ice, hydrogen_gas or nitrogen_gas
    - Incredibly cheap to maintain in the outer systems of M-class or brown dwarf stars, but completely incompatible with oxygen-rich terrestrial worlds.
- Nutrient spread requirement: Some races happily subsists on one food type, others require a diverse diet. Influence happiness and reproduction speed.

### Professions
- Id
- Name
- Description
- Type: What the profession is for: Soldier, farmer, ship crew, miner, industrial worker, scientist, engineer, technician, police, bureaucrat, etc.
- Minimum intelligence requirement: Required for the profession
- Minimum strength requirement: Required for the profession
- Complexity: How complex the profession is. Influence how fast population can be trained for the population and how much experience is required to level up.
- Retirement lifespan percentage: Percentage value applied against the race's natural lifespan to calculate chronological retirement age.

### Population
Planets and space stations will have a population of one or more races. Population is segregated by race and age. For races eligible to reproduce, only the part of the population in the fertile age span will do so. Hive societies will only reproduce if they contain a queen of fertile age. The population will be influenced by the nutrient type and spread requirement of the available food stored in the planet or space station. Ships containing population modules that do not have stasis chambers will behave the same way.
A group of people can be trained for a profession. They will have a training level for it that will decide how efficient they are at the task. All people of eligible age to be part of the workforce either have a profession, are unemployed or disabled.

### Empires
Each empire will have its own economy and technology tree. It can contain a population of multiple races. 
It will also have its own society structure, which will influence the way it reproduces and the way it interacts with other empires.
The empire may consist of many planets. It will control spaceships, colonies and ground troops. 
An empire has a range of influence in the galaxy based on its controlled planets, space stations and asteroids. 
Any celestial body that is within the influence range of an empire is considered part of that empire.

[Empire details](Empire.md)

### Economy
For simplicity, we will have universal credits reflecting money. Money, resources and spaceships might be traded between empires that have sufficient diplomatic relations.
An empire can use its credits to directly pay for costs in its planets and space stations that they own that do not generate enough income to provide for themselves.
Money can be transferred according to the technology level of the empire.
The economic engine balances planetary logistics, workforce compensation and interstellar trade networks through a unified credit system.
The economic engine of the galaxy is split into two primary layers of capital flow: the public state budget and the private citizen market. The degree of separation between these sectors dictates how an empire grows, builds and taxes its workforce.

[Economy](Economy.md)

### Private corporations

In individualist and collectivist societies, the private economy extends into space logistics. Autonomous private corporations can independently purchase, own and operate their own fleets of cargo transports and mine ships to maximize profit margins and exploit remote astronomical resources.

#### Corporate profiling and structural data
Every private corporation operating within an empire's territory is tracked by the game engine as an independent economic agent with the following structural properties:
*   **ID and name:** Unique identifiers (e.g., `corp_hephaestus_foundries`, `corp_bio_grow_alliancel`).
*   **Headquarters location:** The specific planet or space station where the corporation's primary financial assets are legally anchored.
*   **Liquid capital reserves:** The current pool of universal credits available to the corporation for investment and expansion.
*   **Asset holdings:** A structural list of real estate, private cargo freighters, production facilities or mineral veins owned or leased by the company.
*   **Market orientation:** The sector preference of the corporation (e.g., extraction, metallurgy, agriculture, transportation or consumer services), which modifies how aggressively it evaluates specific types of market shortcomings.

#### Capital accumulation mechanisms
Corporations expand their liquid capital reserves by providing essential and luxury goods and services directly to the civilian workforce. They capture wealth from private citizen wallets through four main avenues:
*   **Residential real estate:** Leasing out or managing housing spaces within state-built or corporate-owned habitation modules.
*   **Nutrient distribution:** Retailing compatible food types and diverse diets to populations through local planetary marketplaces and commerce modules.
*   **Consumer healthcare:** Operating private medical clinics and optimization facilities that charge citizens out-of-pocket fees to maintain their happiness and longevity parameters.
*   **Secondary B2B logistics:** Contracting with other private entities or the state to refine raw ores into pure elements (such as transforming `iron_ore` into `refined_iron`) for a localized processing fee.

#### Shortcoming detection and dynamic investment
Instead of accumulating dead credit balances, private corporations run an automated investment evaluation pass during the empire's turn-update loop. The corporation calculates a **Shortcoming Score ($S_m$)** for every material, resource and profession type within its logistics range:

Draft: $$S_m = \left( \frac{\text{Local Demand} - \text{Local Supply}}{\text{Local Supply} + \epsilon} \right) \times \text{Market Price Modifier}$$

When a critical shortcoming threshold is breached, the corporation reacts dynamically based on its sector orientation:
*   **Infrastructure funding:** If a colony suffers from a severe shortage of high-complexity electronics due to a deficit in `refined_silicon`, a metallurgy-oriented corporation will automatically allocate credits to construct a new surface refining zone or upgrade an existing foundry's complexity rating.
*   **Workforce training subsidies:** If an asset cannot operate at peak efficiency due to a lack of skilled labor, private corporations will independently fund training programs for local citizen cohorts. They will pay the work hour costs to train low-skill citizens into specialized professions like `miners`, `technicians` or `logistics_officers`.
*   **Logistical bridge building:** If a resource is abundant in the outer rim but critically missing at a core manufacturing station, transport corporations will independently purchase cargo hull modules, hire `ship_crew` and establish private supply lanes to arbitrage the price difference.

#### Sovereign state interactions and intervention
The empire’s central government does not control private corporations directly, but can manipulate their behaviors using macro-economic policy tools:
*   **Corporate taxation:** Setting a high corporate tax rate drains liquid capital reserves from corporate investment pools into the public state treasury. This slows down autonomous corporate expansion but provides the state with maximum funding for military ship production.
*   **Public zoning laws:** The state can lock specific planetary surface slots, preventing private companies from building factories on worlds designated purely for state military shipyards or massive public farming projects.
*   **Sector subsidies:** The state can issue targeted tax credits or credit grants to specific corporate identifiers. For example, offering a subsidy to agricultural corporations lowers their investment costs, driving them to prioritize building *hydroponic growth arrays* over mining operations.
*   **Nationalization:** In times of total war or economic collapse, individualist states can forcefully nationalize corporate assets (such as cargo fleets or weapon foundries), transferring them directly to the public economy. This action instantly destroys corporate trust, driving private capital away from the sub-sector and causing severe happiness drops among the citizen cohorts who held corporate investments.

#### Corporate fleet procurement
Private corporations may use eligible public designs or create proprietary blueprints for their own fleets. A proprietary blueprint belongs to its designing corporation and only that corporation may use it to build ships. The player cannot commission a ship directly from a corporate blueprint. The player may purchase a finished ship from a corporate shipyard without receiving the blueprint or the right to manufacture further copies.
*   **Blueprint selection:** Corporate AI may evaluate unlocked public designs and its own proprietary designs for the required **Cargo Transport** or **Mine Ship** role. Public designs remain distinct from corporation-owned designs.
*   **Shipyard contracts:** The corporation uses its private capital to place a construction order at a compatible shipyard. The yard processes the order over daily turns according to the blueprint's required work hours and its available assembly capacity. The corporation pays the shipyard owner, which may be the state or another corporation.
*   **Material consumption:** Constructing the corporate ship requires refined metals, electronics and composites from local inventory. The order pauses when required inputs or yard capacity are unavailable. Corporate and state orders follow the same construction-time rules described in [ShipDesign.md](ShipDesign.md).

#### Mine ship operations
When a private corporation takes delivery of a vessel tagged with the **Mine Ship** role, it operates the asset to extract raw planetary ring or asteroid rock reserves independently of the central government.
*   **Asset deployment:** Corporate mine ships autonomously route to un-depleted asteroid fields, comets or low-G moons within their active logistics range. They prioritize nodes rich in high-value or high-demand resources matching current *Market Shortcoming Scores*.
*   **Yield destinations:** The raw extracted ores do not enter the state's public storage modules. Instead, the corporation routes the raw yield back to its own corporate warehouses or sells the unrefined mass directly to local *Commercial Hubs* to be bought and processed by commercial metallurgy or gas processing modules.

#### Cargo transport operations (draft)
The existing corporate fleet and trade-route processors model movement between hubs without requiring a cargo terminal. Whether to retain interplanetary freight as a separate simulated system is undecided; it does not define the terminal's ground-to-orbit role. The following wider transport design remains draft.
Corporate-owned **Cargo Transports** function as the primary physical vehicles for market arbitrage across the galaxy.
*   **Shortcoming resolution:** These ships are completely controlled by the corporation’s local trade algorithms. If a colony has a massive deficit in `refined_silicon`, corporate cargo haulers will independently purchase the material from a surplus hub, load the vaults and fly to the shorted colony to liquidate the stock for a massive private profit.
*   **Orbital lift logistics costs:** Corporate captains are fully bound by the game's physics loops. When a corporate cargo transport blasts off from a heavy terrestrial world, the transport must spend propellant to overcome gravity losses and atmospheric drag into orbit. This orbital lift cost is derived from the Tsiolkovsky rocket equation, local fuel prices, municipal spaceport handling fees and vehicle maintenance wear. This cost is automatically deducted from the corporation's gross trading margins, forcing the corporate AI to naturally favor low-G moons, surface mass drivers, space elevators or zero-G orbital space stations for high-volume freight transfers.

#### State jurisdiction and maritime laws
Even though these fleets are privately owned and operated, they must respect the physical and diplomatic laws of the empire within whose range of influence they travel.
*   **State taxation and tariffs:** Every time a corporate cargo transport buys or sells resources at a planetary or space base commercial hub, it triggers the state's active *transaction tariff rate*. The state automatically skims credits from these private corporate transactions, turning a booming corporate trade network into a massive passive revenue stream for the public state treasury.
*   **Smuggling and police interception:** If a corporation attempts to bypass state tariffs by using unpoliced, corrupt commercial loops, the state can deploy military combat hulls or assign the local `police` profession to scan, fine or forcefully impound corporate cargo transports.
*   **Eminent domain and wartime requisition:** During times of catastrophic collapse or foreign invasion, states can pass emergency laws to nationalize corporate fleets. This forcefully converts privately owned cargo and mine ships into state-controlled utility platforms. While this action gives the military instant logistical transport capability, it causes corporate trust to crash, freezing future private capital investments across that entire star sector.

### Commercial hubs
Every planet functions as a commercial hub. So does a space base with a commercial module. It acts as the primary physical bridge between the public state treasury, private citizens and autonomous private corporations. It serves as a trade hub, capturing transaction fees from civilian purchases and interstellar freight while providing market liquidity to the local colony economy.

#### Properties and data structure
Every commerce hub is managed using the following structural parameters:
*   **ID and name:** Unique identifiers mapping the instance to its host entity
*   **Module tier / complexity:** Dictates the maximum volume of resource traffic and financial liquidity the hub can process per turn without bottlenecks.
*   **Transaction tariff rate:** A state-adjusted rate retained for wider trade routes and fleet transactions. Same-body industry sales do not pay it; VAT on eligible local purchases remains draft.
*   **Storage capacity linkage:** The maximum weight limit (in kilograms) of consumer goods and raw materials the hub can temporarily hold for marketplace trading.
*   **Logistics range:** The operational distance (measured across the galactic coordinate map) over which the hub can broadcast its local buy/sell orders to private corporate networks.

#### Civilian retail and tax generation
The commerce hub provides the physical marketplace where private citizen cohorts spend their disposable income.
*   **Nutrient spread retail:** Citizens access the hub to purchase compatible food varieties. The hub tracks available food inventory. If a diverse selection of organic or synthetic nutrients is present, citizens spend more credits, directly boosting colony happiness and population growth variables.
*   **Consumer service leasing:** Private companies use the hub's commercial slots to operate healthcare clinics, recreational lounges and retail spaces, paying a fixed credit lease to the hub owner.
*   **Automated tax harvesting (draft):** A future VAT may tax eligible household purchases through the hub. Household purchases currently pay for goods but do not collect VAT or route a transaction tax to the treasury.

#### Corporate B2B trading and arbitrage
Beyond civilian retail, the commerce hubs handles high-volume business-to-business (B2B) trade between competing private corporations and the state.
*   **Shortcoming liquidation:** Corporations use the hub's commodity exchange to sell materials they have in surplus and buy items matching their local *Shortcoming Score*. For example, an electronics corporation will place bulk buy orders for `refined_silicon` and `silver` within the hub's digital ledger.
*   **Docking and freight fees:** Independent cargo freighters operated by transport corporations must pay a baseline docking fee in credits to utilize the hub's cargo transfer systems. This fee scales with the total weight (kilograms) of the materials being offloaded.
*   **Orbital lift cost deduction:** If a commerce hub is located on a planetary surface, any corporate transaction involving the export of heavy resources (like `iron_ore` or `lead`) automatically calculates the required orbital lift cost. The credit cost to lift that mass into orbit is displayed on the hub's exchange board and is deducted from the transaction's net profitability, unless mitigated by surface mass drivers or space elevators.

#### Systemic dependencies and failure states
To remain operational, the commerce hub relies on the active presence of other professions and modules within the local colony network:
*   **Workforce requirement:** The hub requires an active allocation of trained `bureaucrats` and `technicians`. If the local population suffers a headcount deficit in these professions, the hub's processing efficiency drops, causing transaction backlogs and reducing collected tax revenues.
*   **Power grid dependency:** Operating the digital trade ledgers, atmospheric storage seals and automated cargo crane matrices requires a constant feed of electricity from local power modules. If the power grid fails or runs a deficit, the commerce hub goes offline, freezing all private market transactions and causing an immediate drop in local citizen happiness.
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

### Ground combat and fortifications
Planetary sieges and ground combat represent the final stage of stellar conquest. While combat ships command the orbital spaceways, conquering a celestial body requires physically overcoming its localized defenses through ground invasion loops, material-backed fortifications and structural siege dynamics.

[Ground combat and fortifications](GroundCombat.md)

### Maneuvering and atmospheric aerodynamics
Moving a spaceship across the galaxy requires navigating two distinct physics environments: the frictionless vacuum of deep space and the dense, drag-heavy atmospheres of terrestrial planets. Managing a starframe's maneuvering profile relies on specialized Reaction Control Systems (RCS), which must balance the ship's total structural mass against local planetary gravity and atmospheric resistance variables.

[Maneuvering and atmospheric aerodynamics](AtmosphericAerodynamics.md)

### Geological prospecting and resource extraction
The resource system simulates a dynamic, high-fidelity geological model where planets are treated as structurally active chemical bodies. Rather than pre-programming every resource vein at the start of a game, a planetary system initializes with a baseline known composition. The majority of its hidden mineral wealth remains unmapped, requiring active public or private prospecting cycles to locate.

[Geological prospecting and resource extraction](GeologicalProspecting.md)

### Diplomatic relations and range of influence
Interstellar diplomacy governs how sovereign empires coexist, demarcate territorial sovereignty and execute commerce across the galactic map. Rather than relying on hard physical borders, territory is defined dynamically through an empire's **Range of Influence**, establishing spaces where local jurisdictions, public taxes and maritime laws are forcefully enforced.

[Diplomatic relations and range of influence](DiplomaticRelations.md)

### Habitation, life support and population dynamics
Maintaining a biological workforce in the vacuum of space or on hostile planetary surfaces requires specialized habitation infrastructure and continuous atmospheric recycling. This system governs how population cohorts grow, consume resources and survive across different celestial entities based on their biochemical profiles.

[Habitation, life support and population dynamics](Habitation.md)

### Crime, smuggling and black market leakage

The crime simulation loop represents the organic friction within an empire's private economy. In individualist and collectivist societies, the movement of high-volume civilian commodities and commercial freight naturally creates opportunities for illegal capital accumulation. If left unpoliced, crime creates a black market siphon that redirects wealth away from the state treasury and into shadow corporate networks.

[Crime, smuggling and black market leakage](Crime.md)

### Ship customization and the modular hull design interface

The ship design interface is a hard physics-bounded engineering playground. Instead of selecting from fixed, pre-defined hull size classes (such as "Destroyer" or "Cruiser"), users construct starframes completely from scratch. The interface dynamically calculates the ship's dry mass, slot limits, operational classification and launch restrictions based entirely on the user's custom choice of modules and structural materials.

[Ship design interface](ShipDesign.md)

### Power, propulsion energy and fuel logistics

The power grid represents the operational lifeblood of every spaceship, space station, orbital space base and planetary colony. Every active installation or module requires a continuous supply of electricity to remain functional. Power is treated as a strict physics-bounded flow system, balancing variable energy generation methods—both fuel-dependent and renewable—against structural demands and storage limits.

[Power, propulsion energy and fuel logistics](Power.md)

### Megastructures and stellar engineering
Advanced technological civilizations can construct macro-scale engineering projects that encompass entire stars or planetary systems. These structures are built in multiple stages and provide massive energy or material yields.
*   **Dyson swarms and spheres:** Built around stars to capture astronomical levels of energy. Operational output is measured in kilowatts (kW).
*   **Star lifters:** Massive magnetic arrays that extract raw elements directly from a star's photosphere.
*   **Ringworlds and orbital habitats:** Artificial habitable zones providing massive population capacity.
*   **Hyperlane gateways:** Megastructures that enable instantaneous warp transit between connected solar systems, bypassing standard FTL travel times.

### Procedural audio synthesis
The game engine features a dedicated audio synthesis worker that generates real-time audio feedback for galactic events.
*   **Event-driven cues:** Specific sounds are triggered for turn advancement, terraforming completion and galactic senate sessions.
*   **Atmospheric audio feedback:** Sound profiles shift based on the environment, providing auditory cues for warp transit and tactical combat engagements.

### Physics standards and simulation fidelity
To ensure consistency across all sub-systems, the engine standardizes all calculations on the International System of Units (SI).
*   **Standardized units:** Mass is tracked in kilograms (kg), thrust in Newtons (N), temperature in Kelvin (K) and energy in kilowatts (kW).
*   **Environmental barriers:** Atmospheric density affects laser weapon effectiveness due to photon scattering. Planetary blast-off costs are calculated as a strict force-to-mass ratio against local gravity.
*   **Nanotechnology complexity:** The production of advanced energy weapons and high-tier electronics is capped by the empire's current nanotechnology research level.

## How to read
Each module contains a `MODULE.md` file with specific details about its purpose and components to help AI agents navigate the codebase.

## How to build and run
- Build everything and run the tests: `mvn clean install`
- Run only the engine tests: `mvn test -pl engine`
- Start the game by running `com.spaceconquest.frontend.Main` with the frontend module dependencies available. The current POMs do not configure the `javafx:run` Maven goal.

## Current implementation
- Static data model loaded from JSON property files: solar systems, races, materials, technologies with
  applications, star properties (Hertzsprung-Russell) and professions.
- Procedural galaxy generation with realistic star mass distribution and colors.
- New generated campaigns use the selected era's calendar date, starting technologies and opening industrial economy. The bundled Sol data shown before starting a new campaign remains a separate static sample world.
- Turn processors for markets, public budgets, municipal finances, industry, governance, research, fleet movement, sensors, espionage, construction, terraforming, megastructures and senate sessions.
- Procedural audio synthesis worker for event-driven feedback.
- FXGL entity map with zoom, goto search, entity focus panels and tooltips plus a separate canvas galaxy view.
- Menubar with empire, diplomacy, technology, fleet, galaxy view and game menu pages; opening a page pauses
  the game and closing it resumes at the previous speed.

### Known integration gaps
- Material industries now require researched technology, paid workers, input stock, owner cash and local grid power. They pay for powered shifts and sell produced goods to local hubs only within hub cash and storage limits; per-facility accounts preserve unsold stock and show actual input costs, electricity costs, wages, sales and pretax realized results. Power plants generate measured electricity; combustion, fission, fusion and antimatter plants buy distinct fuels while solar, wind, hydro and thermoelectric plants do not. Households at industrial technology level treat electricity as a tier 1 need, and metered local electricity sales pay plant owners. Stored battery electricity remains unpriced until grid ownership is modeled. Cargo-terminal services remain unimplemented. Corporate profit tax is collected from realized earnings across a corporation's facilities after carried losses; fleet and other corporate income is not in the tax base yet. Refinement recipe IDs remain separate from technology application IDs, and existing retail stock has no attributed seller.
- The UI still supplies one real-time pulse per second, while the engine accumulates partial days and executes the number of daily turns due. Simulation turns run on a dedicated worker and publish snapshots back to JavaFX. At high speeds, processing can lag behind real time if the simulation cannot complete the due turns quickly.
- `GameState` is a record but does not defensively copy all nested collections. Commands now use a copy builder or `with...` methods to preserve unrelated snapshot fields. The tick refresh does not update every view and some views read initial JSON data instead of live state.
- Save and load restore the calendar and selected speed; the daily turn is derived from the saved calendar. Courier ships, local and imperial balance sheets and system contribution settings round-trip. Local deficits accumulate as debt on each body. System debt aggregates those local balances and the imperial ledger records actual treasury flows and its own debt. Other newer live fields may still be absent from the save format.
- The daily turn now derives household groups from age-group populations, pays funded jobs and retiree pensions, collects wage income tax into local balance sheets and buys available nutrients and optional goods from local hubs. Industrial households also require metered electricity before optional goods. Savings, unmet food and electricity needs, hub trading cash and facility stock persist. Housing, healthcare, retail seller attribution and demographic effects of unmet needs remain future work. Cohort staffing, warp-network routing and tactical fleet combat are not fully integrated into the normal turn loop.
- Corporation-owned blueprints exist in the model and AI, but build authorization and finished-ship purchases do not yet enforce the ownership rule above.
- Ship builds still complete immediately. The planned daily construction-order progress and yard capacity rules are documented in [ShipDesign.md](ShipDesign.md) but have no live project model yet.

### Planned hive mind economy
For hive mind societies, disabling the private sector is intended to shift the economy toward direct material balances and player-managed logistics. Citizen wallets, taxes and corporate accounting would be bypassed. Without corporate AI responding to shortages, the player would manage trade routes and cargo capacity. This is a design direction, not a description of the current simulation.
