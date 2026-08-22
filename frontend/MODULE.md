# Frontend module

This module serves as the entry point and user interface for the Space Conquest Game, powered by the FXGL game engine.

## Key components
- `Main`: The main class that extends FXGL's `GameApplication` to initialize the game and UI.
- `GameHud`: Builds and positions all UI overlays (menubar, zoom controls, goto, page overlays).
- `Menubar`: Top bar with the sector buttons, the time view and the game pause/resume handling for pages.
- `CameraController`: Zoom, focus and goto navigation on the galaxy map.
- `SolarSystemRenderer` / `GalaxyRegistry`: Renders solar systems and keeps track of rendered entities.
- `TechnologyView`, `GalaxyListView`, `GameMenuView`: The base page overlays opened from the menubar.
- `EmpireView`: UI panel displaying imperial ministries, governor assignments, treasury and macro-policies.
- `CorporateView`: UI panel displaying registered private corporations, liquid capital, shortcomings and fleets.
- `DiplomacyView`: UI panel displaying galactic diplomatic relations, treaties, influence values and bilateral stances.
- `CommercialHubView`: UI panel displaying active commercial hubs, commodity spot prices, supply-demand bars, shortcoming scores and automated trade route logistics.
- `ShipDesignerView`: UI panel providing interactive starframe layout, module slots and real-time physics validation warnings.
- `FleetManagementView`: UI panel providing fleet command overviews, ship manifests, warp progress and operational stances.
- `PlanetDetailView`: UI panel displaying celestial prospecting data, mineral veins and power grid balances.
- `IndustryView`: UI panel displaying industrial processing facilities, tier scaling pipelines and ownership routing.
- `ColonyManagementView`: UI panel displaying planetary demographics, species biochemical metabolism meters and surface mass drivers.
- `CampaignManagerView`: UI panel providing campaign initialization, galaxy size customization, live file saving, quick-saving and save/load management.
- `CombatResolutionView`: UI panel displaying tactical space battle after-action reports, orbital bombardment logs and ground siege outcomes.
- `OrbitalStationView`: UI panel displaying orbital space stations, modular station facilities, power grids and space elevator tethers.
- `EspionageView`: UI panel displaying sleeper operatives, covert intelligence missions and syndicate pirate bases.
- `RefinementView`: UI panel displaying chemical and metallurgical refinement recipes and living standard satisfaction.
- `TacticalBattlePlaybackView`: UI panel rendering round-by-round space battle playback with weapon logs and strike wing deployments.
- `TerraformingView`: UI panel displaying planetary atmospheric composition meters, geoengineering project queues and biomes.
- `MegastructureView`: UI panel displaying stellar megastructures, construction stages, Dyson power output and gateways.
- `GalacticSenateView`: UI panel displaying Galactic Senate legislative assemblies, voting results, charters and sanctions.
- `GalaxyCanvasView`: Interactive 2D visual canvas rendering stars, orbital planet paths, hyperlane links and transit vectors.
- `ScenarioEditorView`: UI panel customizing campaign scenarios, starting eras, galaxy scale, AI distributions and victory conditions during new game setup.
- `VictoryDefeatView`: UI panel displaying victory and defeat outcome modals with summary statistics.
- `TutorialOnboardingView`: UI panel providing guided tutorial campaign onboarding player rulers across science, industry, naval engineering and diplomacy.
- `TacticalCombatArenaView`: Interactive 2D graphical combat arena with real-time subsystem targeting, animated weapon projectiles and fleet stance controls.
- `EmpireCreationWizardView`: Multi-step interactive wizard for designing custom sovereign empires, species traits, physiology and ideological ethics during new game creation.
- `AudioSettingsView`: UI panel accessed from the game menu for configuring audio volume levels, mute preferences and testing synthesized acoustic cues.
- `AudioPlaybackManager`: Coordinates UI acoustic feedback, combat sound effects and procedural synthesized audio playback.

> Note: all JavaFX nodes must be created inside `build()`/`initUI()` and never in static or field
> initializers, otherwise FXGL fails at startup with `Toolkit not initialized`.

## Display
### Galaxy view
Displays the galaxy map, allowing players to explore and manage their space empire.
- Has a menubar with options to change game speed and technology view.
- Has a goto in the top right corner where the user can type name and navigate to the corresponding entity, setting it as focus with zoom level 1.
  - On select should focus on the selected entity.
  - Goto should be slightly below the menubar.
  - Stars, planets, planetoids and moons are all searchable and each search result shows an indicator of the body type.
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
- The visual look of a planet or moon is decided by a combination of the planet type, any atmosphere present and how much surface water is present. What resources are present on the planet may also affect the visual look. For instance, a dry planet with much iron should be redish.

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
- Game menu button. At extreme right position. Opening it gives options for New game, Save, Campaign saves, Audio settings and Exit.

### Randomized galaxy
Generate a new random galaxy with a given number of solar systems and starting scenario from the game menu. Solar systems have random planets, can have random moons and will have randomized resources. Some planets might be inhabited. The galaxy data is in memory until game is saved, following the same structure as the JSON files.  
Make a property file based on the Hertzsprung–Russell diagram to specify the relation between star mass and star color. Use these properties to decide what colors stars are presented with on the galaxy map and in other places. Also use the diagram to specify a distribution of star masses to use when generating random galaxies.
