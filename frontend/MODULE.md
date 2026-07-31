# Frontend Module

This module serves as the entry point and user interface for the Space Conquest Game, powered by the FXGL game engine.

## Key Components
- `Main`: The main class that extends FXGL's `GameApplication` to initialize the game and UI.
- `GameHud`: Builds and positions all UI overlays (menubar, zoom controls, goto, page overlays).
- `Menubar`: Top bar with the sector buttons, the time view and the game pause/resume handling for pages.
- `CameraController`: Zoom, focus and goto navigation on the galaxy map.
- `SolarSystemRenderer` / `GalaxyRegistry`: Renders solar systems and keeps track of rendered entities.
- `TechnologyView`, `GalaxyListView`, `GameMenuView`: The page overlays opened from the menubar.

> Note: all JavaFX nodes must be created inside `build()`/`initUI()` and never in static or field
> initializers, otherwise FXGL fails at startup with `Toolkit not initialized`.

## Display
### Galaxy view
Displays the galaxy map, allowing players to explore and manage their space empire.
- Has a menubar with options to change game speed and technology view.
- Has a goto in the top right corner where the user can type name and navigate to the corresponding entity, setting it as focus with zoom level 1.
  - On select should focus on the selected entity.
  - Goto should be slightly below the menubar.
  - Stars, planets, planetoids, and moons are all searchable, and each search result shows an indicator of the body type.
  - The select option should show what kind of entity it is.
- Zoom in and out buttons. Using these should alter the zoom level and retain the current focus. 
  - Mousewheel zooming should be supported.
  - Should be slightly below the menubar.
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
- The color of a star is decided by the mass of the star. We have a property file based on the Hertzsprung-Russel diagram that specifies the relationship. 

### Menubar
Displays buttons with information about the different sectors of the game. Opening the corresponding page for a button should pause the game. Closing that page should resume the game, at the game speed it was before the page opened.
- Empire button. On the button is displayed total colonies and wealth. On click opens the empire page which will provide detailed information on all planets in the empire and the economy of the empire.
- Diplomacy button. On button it displays diplomatic status. At war or at peace. On click of button opens the diplomacy page which will provide detailed information on all diplomatic relations between the empire and other empires. Will also show detailed information about the other empires.
- Technology button. On button displays information about the progress of the currently researched technology. On click of the button opens the technology page which will show the entire technology tree, as far as the empire is aware of it. It will also provide detailed information on all technologies researched by the empire.
- Spaceships and star bases button. On the button displays information about the current spaceship and starbase count. On click opens the spaceship and starbase page which will provide detailed information on all spaceships and starbases in the empire.
- Galaxy view. Opens a display listing all (later will only list the entities known to the player) space entities in the galaxy grouped by star, then planet.
  - The listing should have a visual representation of each space entity, along with name, type and known resources.  
  - The visual representation for star should be based on the star size. For planet and moon it should be based on type and atmosphere.
- Current time view. Displays the current time in the game and game speed. Include controls to adjust the game speed.
- Game menu button. At extreme right position. Opening it gives options for Save, Load, Game settings and Exit.

### Randomized galaxy
create a function to generate a new random galaxy with a given number of solar systems. Solar systems have random planets, can have random moons, and will have randomized resources. Some planets might be inhabitated. Add a temporary button in the right part of the menu bar that triggers the generation of new galaxy and asks for the number of solar systems to generate. The galaxy data should be in memory only until game is saved, but follow the same structure as the json files we have made before.  
make a property file based on the Hertzsprung–Russell diagram to specify the relation between star mass and star color. Use these properties to decide what colors stars are presented with on the galaxy map and in other places. Also use the diagram to specify a distribution of star masses to use when generating random galaxies.