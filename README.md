# Space Conquest Game
This will be a space conquest game with similarities to Stellaris and Master of Orion. 
The game will have resources, technology, races, spaceships and combat both in space and on the ground.

## Project Structure
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
All applications are on a scale from 0 to 10, where 0 means unknown and 10 means most effective and most advanced.
To produce an item of a given tech-level, you will need a production facility of the correct type of the same tech-level.
- Electricity
  - Electronics
  - Solar power
  - Hydropower
  - Wind power
  - Nuclear power
  - Thermoelectric power
  - Batteries

- Nuclear fission
  - Fission reactors
  - Fission weapons

- Nuclear fusion
  - Fusion reactors
  - Fusion weapons

- Industrial production
  - Farming
  - Ore refining
  - Manufacturing
  - Metallurgy

- Rocketry
  - Rocket engines
  - Launch facilities
  - Fission engines, req: Fission reactors
  - Fusion engines, req: Fusion reactors
  - Orbital rockets
  - Interplanetary rockets
  - Spacecraft
  - Space stations

- Energy fields, req: Electricity
  - Shields

- Particle weapons

- Energy weapons, req: Energy fields

- Nanotechnology. Required for electronics > 4

- Robotics

- Artificial intelligence

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
- Nutrient spread requirement: Some races happily subsists on one food type, others require a diverse diet. Influence happiness and reproduction speed.
- Retirement age for each profession.

### Professions
- Id
- Name
- Description
- Type: What the profession is for: Soldier, farmer, ship crew, miner, industrial worker, scientist, engineer, technician, police, buorocrate, etc.
- Minimum intelligence requirement: Required for the profession
- Minimum strength requirement: Required for the profession
- Complexity: How complex the profession is. Influence how fast population can be trained for the population and how much experience is required to level up.

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

## How to Read
Each module contains a `MODULE.md` file with specific details about its purpose and components to help AI agents navigate the codebase.

## How to Build and Run
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
