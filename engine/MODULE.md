# Engine module

This module contains the core game logic, the static data model and state management for the Space Conquest Game.

## Key components
- `GameEngine`: Interface defining the core loop and engine operations.
- `GameState`: Represents the current state of the game world.
- `SpaceConquestEngine`: A mock implementation for testing and development.
- `DataModelLoader`: Loads and caches all static data from the JSON property files in `src/main/resources`.
- `GalaxyGenerator`: Procedurally generates galaxies (solar systems, planets, moons, resources, populations).
- `PopulationProcessor`: Handles population growth per race and age group.

## Data model records
| Record | Resource file | Description |
| --- | --- | --- |
| `SolarSystem`, `Planet`, `Moon`, `AsteroidBelt` | `solar_systems.json` | Celestial bodies and their hierarchy |
| `Race` | `races.json` | Playable and NPC races |
| `Material` | `materials.json` | Raw materials and refined resources |
| `Technology`, `TechnicalApplication` | `technologies.json` | Technology tree with applications |
| `StarProperty` | `star_properties.json` | Hertzsprung-Russell mapping of star mass to spectral type and color |
| `Profession` | `professions.json` | Professions the population can be trained for |
| `Population` | part of `solar_systems.json` | Population per race, segregated by age group |

## Conventions
- All game component properties are read from property files in the resources folder; no hard-coded game values.
- Data model classes are immutable Java records mapped by Jackson.
- `DataModelLoader` caches loaded lists; call `DataModelLoader.clearCache()` to force a reload.
- Unknown JSON properties are ignored, so data files can be extended before the records are updated.
