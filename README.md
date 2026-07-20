# Space Conquest Game

This will be a space conquest game with similarities to Stellaris and Master of Orion. 
The game will have resources, technology, races, spaceships and combat both in space and on the ground.

## Project Structure
- `engine`: Core game logic and state.
- `control`: Input handling and player/AI logic.
- `frontend`: Main entry point and user interface.

## Galaxy
The galaxy will consist of multiple systems. Systems with stars are solar systems. Most systems are solar systems. 
Planets, asteroids, comets and other stellar bodies might occur outside solar systems, but most planets are within solar systems. 
Each planet with its own resources, technology level and population. 

## Planets
All planets have a mass. The mass of a planet will influence its gravity and the amount of resources it can mine. 
The gravity of a planet, or g-force, will influence how much it costs to move materials from the planet into orbit using propulsion. 
A planet can have a population of multiple races. Some planets may be uninhabitable, while others may be habitable but not suitable for certain races.
Each planet will have mineable resources and industries which can be used to produce goods and technology. 
Planets may have one or more moons. A moon is a separate entity from the planet and will have its own resources and industries. 
Moons are usually smaller than planets and have less resources than their parent planet. Having a smaller mass means lower g-force, 
so moving resources into space from a moon will require less force than from a planet.

## Technology
The technology tree will feature different areas where researching an area will give the possibility of practical application in that area. 
For instance, the area of atomic fission would grant access to practical application in propulsion, energy production and weapons.

## Races
The races will have the properties of the population in an empire. 
- Intelligence: Influence research speed
- Physical strength: Influence combat strength some production
- Society structure: Individualist, collectivist, hive mind. A hive society will reproduce linearly with respect to how many queens they have – which means linearly. Other societies wil reproduce with respect to how many they are in fertile age and their resource state.
- Prefered g-force. 
- Prefered temperature. 
- Chemical composition: Carbon based, silicon based, etc.
- Breathing atmosphere: Oxygen based, nitrogen based, etc.

## Empires
Each empire will have its own technology tree. It can contain population of multiple races. 
It will also have its own society structure, which will influence the way it reproduces and the way it interacts with other empires.
The empire may consist of many planets. It will control space ships, colonies and ground troops.

## Economy
For simplicity, we will have universal credits reflecting money. Money, resources and space ships might be traded between empires that have sufficient diplomatic relations.
An empire can use its credits to directly pay for costs in its planets and space stations that they own that do not generate enough income to provide for themselves.

## Space stations
Around planets, moons and asteroids it will be possible to build space stations. It will also be possible to build deep space stations around stars and even outside solar systems.
Space stations will have their own resource stores, population, industries and defenses. A space station will contain multiple modules.
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

## How to Read
Each module contains a `MODULE.md` file with specific details about its purpose and components to help AI agents navigate the codebase.
