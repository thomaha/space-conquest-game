# Frontend Module

This module serves as the entry point and user interface for the Space Conquest Game, powered by the FXGL game engine.

## Key Components
- `Main`: The main class that extends FXGL's `GameApplication` to initialize the game and UI.

## Display
### Galaxy view
Displays the galaxy map, allowing players to explore and manage their space empire.
- Has a goto in the top right corner where the user can type name and navigate to the corresponding entity, setting it as focus with zoom level 1.
  - On select should focus on the selected entity.
  - Stars, planets, planetoids and moons are all searchable, and each search result shows an indicator of the body type.
  - The select option should show what kind of entity it is.
- Zoom in and out buttons. Using these should alter the zoom level and retain the current focus.
- Shown entities should use their respective sizes and distances.
- Entity names are shown as a tooltip when the mouse pointer hovers over it.
- For solar systems, a small label with the system name is displayed. These labels remain at a constant screen size as seen by the user regardless of the zoom level. This means the actual size must be recalculated when altering zoom level.
- Clicking on the map should focus on that point.
- Double-clicking on an entity should focus on that entity with zoom level 1.
- When focusing on an entity:
  - A panel should show information about the entity in the right part of the screen, under the goto.
    - The information panel should have a close button that removes the panel when clicked.
    - A slightly colored background with contrast to the text. 
    - The panel should also have some level of transparency, so parts of the galaxy can be seen in the background.
  - The center of the entity should be used as the focus point for the display.