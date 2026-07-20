# Space Conquest Game
This will be a space conquest game with similarities to Stellaris and Master of Orion. 
The game will have resources, technology, races, spaceships and combat both in space and on the ground.

## Project Structure
- `engine`: Core game logic and state.
- `control`: Input handling and player/AI logic.
- `frontend`: Main entry point and user interface.

## Technical stack
The game will be built using java. Starting with java26 but will upgrade when new versions are stable.
Framework will be spring boot. A UI-framework is not yet decided but will not be browser-based.

## Game components
### Time
- The game time will run for many years. The lowest game speed is 1 minute per real second, with steps at 1 hour, 6 hours, 12 hours and 1 day per second. 
- It is possible to pause the game.

### Galaxy
The galaxy will consist of multiple systems. Systems with stars are solar systems. Most systems are solar systems. 
Planets, asteroids, comets and other stellar bodies might occur outside solar systems, but most planets are within solar systems. 
Each planet with its own resources, technology level and population. 

### Planets and moons
All planets have a mass. The mass of a planet will influence its gravity and the amount of resources it can mine. 
The gravity of a planet, or g-force, will influence how much it costs to move materials from the planet into orbit using propulsion. 
A planet can have a population of multiple races. Some planets may be uninhabitable, while others may be habitable but not suitable for certain races.
Each planet will have mineable resources and industries which can be used to produce goods and technology. 
Planets may have one or more moons. A moon is a separate entity from the planet and will have its own resources and industries. 
Moons are usually smaller than planets and have less resources than their parent planet. Having a smaller mass means lower g-force, 
so moving resources into space from a moon will require less force than from a planet. 

### Asteroids
Asteroids are small celestial bodies that orbit the sun. They are made up of a variety of materials and can be mined for resources. 
Asteroids can be mined from space and can be used to build spaceships and other structures. Asteroids typically are tiny, making it easy to transport mined resources into space

### Technology
The technology tree will feature different areas where researching an area will give the possibility of practical application in that area. 
For instance, the area of atomic fission would grant access to practical application in propulsion, energy production and weapons.
Most technical applications will have several levels of miniaturization and optimization reflecting how useful that application is. For instance, 
it might be better to build a spaceship with a highly optimized fission drive instead of using a newly researched fusion drive. 
Each technical application has:
- Name
- Description
- List of factors it affects (power production, production increase, combat strength, etc.)
- Cost to build per unit in work hours
- List of required materials to build
- Complexity: How challenging it is to produce.

### Materials
What things are built from affect their properties. Building a spaceship from carbon nanotubes will make a strong and light ship, but require a huge industry producing nanotubes. 
- Name
- Description
- Chemical composition: List of elements and % of composition
- Weight per unit in kg
- Material strength
- Complexity: How challenging it is to produce. 

### Races
The races will have the properties of the population in an empire. 
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
- Name
- Description
- Type: What the profession is for: Soldier, farmer, ship crew, miner, industrial worker, scientist, engineer, etc.
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
