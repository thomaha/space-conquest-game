# Frontend module

This module serves as the entry point and user interface for the Space Conquest Game, powered by the FXGL game engine.

## Key components
- `Main`: The main class that extends FXGL's `GameApplication` to initialize the game and UI.
- `GameHud`: Builds and positions all UI overlays (menubar, zoom controls, goto, page overlays).
- `Menubar`: Top bar with the sector buttons, the time view and the game pause/resume handling for pages.
- `CameraController`: Zoom, focus and goto navigation on the galaxy map.
- `SolarSystemRenderer` / `GalaxyRegistry`: Renders solar systems and keeps track of rendered entities.
- `TechnologyView`, `GalaxyListView`, `GameMenuView`: The base page overlays opened from the menubar.
- `EmpireView`: Modular tabbed management hub featuring Imperial economy, Imperial cabinet governance, unified planetary body explorer, sovereign orbital stations and shipyards, private corporation registry and imperial megastructures.
- `PlanetaryBodyEntry`: Adapter model encapsulating planetary and moon characteristics, colonization status, habitability and sorting attributes.
- `CorporateView`: UI panel displaying registered private corporations, liquid capital, shortcomings and fleets.
- `DiplomacyView`: UI panel displaying galactic diplomatic relations, treaties, influence values and bilateral stances.
- `CommercialHubView`: UI panel displaying active commercial hubs, commodity spot prices, supply-demand bars, shortcoming scores and automated trade route logistics.
- `ShipDesignerView`: UI panel providing interactive starframe layout, module slots and real-time physics validation warnings.
- `FleetManagementView`: UI panel providing fleet command overviews, ship manifests, warp progress and operational stances.
- `PlanetDetailView`: UI panel displaying celestial prospecting data, mineral veins, power grid balances and local megastructures.
- `IndustryView`: UI panel displaying industrial processing facilities, tier scaling pipelines, active manufacturing recipes and the metallurgical alloy refinement catalog.
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
- `GalaxyCanvasView`: Interactive 2D visual canvas rendering stars, orbital planet paths, hyperlane links, transit vectors and megastructures.
- `ScenarioEditorView`: UI panel customizing campaign scenarios, starting eras, galaxy scale, AI distributions and victory conditions during new game setup.
- `VictoryDefeatView`: UI panel displaying victory and defeat outcome modals with summary statistics.
- `TutorialOnboardingView`: UI panel providing guided tutorial campaign onboarding player rulers across science, industry, naval engineering and diplomacy with a tactical combat arena sub-tab.
- `TacticalCombatArenaView`: Interactive 2D graphical combat arena with real-time subsystem targeting, animated weapon projectiles and fleet stance controls.
- `EmpireCreationWizardView`: Multi-step interactive wizard for designing custom sovereign empires, species traits, physiology and ideological ethics during new game creation.
- `AudioSettingsView`: UI panel accessed from the game menu for configuring audio volume levels, mute preferences and testing synthesized acoustic cues.
- `AudioPlaybackManager`: Coordinates UI acoustic feedback, combat sound effects and procedural synthesized audio playback.
- `ScreenSettingsView`: UI panel accessed from the game menu for configuring screen resolution and display options.
- `ScreenSettingsManager`: Manages persistence and loading of screen resolution and display preferences.
- `ScreenSettings`: Models display dimensions, fullscreen preferences and proportional UI scaling calculations.

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
- Empire button. Opens the consolidated Empire view containing the Imperial economy tab, Imperial cabinet tab, Planets tab, Orbital stations and shipyards tab, Corporation registry tab and Megastructures tab.
- Diplomacy button. On button it displays diplomatic status. At war or at peace. On click of button opens the diplomacy page which will provide detailed information on all diplomatic relations between the empire and other empires. Will also show detailed information about the other empires.
- Technology button. On button displays information about the progress of the currently researched technology. On click of the button opens the technology page which will show the entire technology tree, as far as the empire is aware of it. It will also provide detailed information on all technologies researched by the empire.
- Spaceships and star bases button. On the button displays information about the current spaceship and starbase count. On click opens the spaceship and starbase page which will provide detailed information on all spaceships and starbases in the empire.
- Fleets button. Opens the fleet management interface for organizing combat task forces and orbital stances.
- Commercial hub button. Opens the galactic market and trade route logistics exchange.
- Espionage button. Opens the covert intelligence and sleeper operative command center.
- Galactic Senate button. Opens the Galactic Senate legislative floor for voting on pan-galactic resolutions.
- Tactical canvas button. Opens the interactive 2D galaxy canvas rendering stars, orbital links, fleets and megastructures.
- Combat playback button. Opens the tactical after-action battle replay theater.
- Tutorial button. Opens the strategic command onboarding guide with a dedicated tactical combat arena sub-tab.
- Galaxy view. Opens a display listing all (later will only list the entities known to the player) space entities in the galaxy grouped by star, then planet.
  - The listing should have a visual representation of each space entity, along with name, type and known resources.  
  - The visual representation for star should be based on the star size. For planet and moon it should be based on type and atmosphere.
- Current time view. Displays the current time in the game and game speed. Include controls to adjust the game speed.
- Game menu button. At extreme right position. Opening it gives options for New game, Save, Campaign saves, Audio settings, Screen settings and Exit.

### Empire view and planetary management
- Central imperial administration hub organized into an extensible tabbed structure.
- Imperial economy tab serves as the primary default overview displaying liquid treasury credits, projected revenues, operational costs, net budget balance, revenue stream breakdowns (colonial taxes, state manufacturing profits, corporate tariffs, space elevator transit fees and mining royalties), expenditure breakdowns (governance overhead, ministry budgets, infrastructure maintenance, station upkeep, science grants and terraforming subsidies), per-colony economic ledgers (clarifying that tax rates are governed per colony in colonial administration) and private corporate market summaries.
- Hovering over buttons, tab switches and interactive controls displays a responsive hand pointer cursor. Tab button styling maintains consistent font size and layout geometry on hover without shrinking.
- Imperial cabinet tab displays sovereign empire governance, leader details, ministerial portfolios (Interior, Defense, Science, Treasury, Diplomacy), governor planetary assignments and public treasury metrics.
- Planets tab unifies all planetary bodies (planets and moons) across all star systems in the galaxy.
- Reactive filtering controls support Colonized (default), Uncolonized, Colonizable, All planetary bodies, Only planets and Only moons.
- Multi-attribute sorting controls support alphabetical name, star system name, population count, diameter/size, surface gravity and resource vein count.
- Contextual technology-gated operations matrix updates on selecting any celestial body to display and stage available actions unlocked by current imperial research:
  - Planetary survey and prospecting missions
  - Surface biome grid facility placement and power grid connectivity
  - Colony transport missions and civil governance decrees
  - Atmospheric terraforming and geoengineering projects
  - Local megastructures under construction or operational in the system
- Orbital stations and shipyards tab displays orbital space stations, attached starframe modules, shipyard slipways, station power balances and planetary space elevators filtered strictly for the player's empire.
- Corporation registry tab displays private commercial corporations, market orientations, liquid capital reserves, owned industrial facilities, commercial freighters and claimed mineral veins filtered strictly for the player's empire.
- Megastructures tab displays all imperial megastructures (Dyson swarms, Dyson spheres, star lifters, ringworlds, orbital habitats and hyperlane gateways) under construction or complete, energy outputs and housing capacity with a commissioning workbench gated by the researched Stellar megastructures technology.
- Consolidates separate planetary operations, orbital station, corporate registry, refinement and megastructure views to keep the top navigation menubar lean and focused.

### Randomized galaxy
Generate a new random galaxy with a given number of solar systems and starting scenario from the game menu. Solar systems have random planets, can have random moons and will have randomized resources. Some planets might be inhabited. The galaxy data is in memory until game is saved, following the same structure as the JSON files.  
Make a property file based on the Hertzsprung–Russell diagram to specify the relation between star mass and star color. Use these properties to decide what colors stars are presented with on the galaxy map and in other places. Also use the diagram to specify a distribution of star masses to use when generating random galaxies.

### Screen settings and UI scaling
- Supports persistent screen resolutions and fullscreen display options.
- Automatically computes proportional UI scaling factors for high-resolution displays such as 1440p and 4K UHD.
- Dynamically scales menubar buttons, time controls, dialogue overlays and HUD panels so on-screen text remains readable across all screen settings.

### Dialogue and UI event isolation
- Configures mouse event interception and pick-on-bounds property across all dialogue overlays, HUD panels and menubar containers.
- Consumes mouse press, release, click, drag and scroll events on UI components to prevent clicks inside open dialogues from propagating through to the galaxy map.

### Surface biome grid and gas giant handling
- Dynamically scales surface sector grid dimensions based on celestial diameter with variable row lengths (from 2 rows for asteroids up to 6 rows for massive worlds and super-earths).
- Simulates spherical planetary geometry by allocating fewer sector places in top and bottom polar rows compared to equatorial bands.
- Accurately models terrestrial Earth-like biome coverage with approximately 5% permanent polar ice, 5% equatorial desert, 70% oceanic shelf water and 20% temperate and tectonic landmass.
- Detects gas giants and ice giants, preventing generation of false solid rock tiles and presenting an informative gaseous status card with disabled ground facility placement.

### Full viewport dialogue overlays
- Dialogue overlays opened from the top menubar dynamically expand to cover the entire game viewport beneath the navigation bar.
- Uses proportional UI scaling factors to ensure dialogue windows and internal containers utilize available screen real estate across standard, 1440p and 4K UHD resolutions.

### Physical unit display and SI standardization
- Displays all physical quantities using standard SI notation across panels and overview cards:
  - Surface and preferred gravity in meters per second squared (m/s²)
  - Temperatures in Kelvin (K)
  - Vessel, cargo, storage and material weights in kilograms (kg)
  - Thruster outputs in Newtons (N)
  - Power generation and consumption in kilowatts (kW)
  - Celestial body diameters and distances in kilometers (km)

### Galaxy generation and view state synchronization
- When generating a new random galaxy or loading a campaign save, the new game state is applied to the central simulation engine.
- Staged player commands from empire creation are processed and updated galaxy solar systems and celestial bodies are rendered on the map.
- Menubar propagates live game state updates across all dialogue views, resetting stale celestial selections and refreshing empire overview, planets, orbital stations and corporate registries.
