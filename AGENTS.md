# Agent guidelines
## Package Boundaries
- **`src/main/java/engine/`**: Contains core simulation logic and data models. It has NO access to JavaFX or FXGL.
- **`src/main/java/control/`**: Acts as the gatekeeper. Validates inputs against `engine` data before updating state.
- **`src/main/java/frontend/`**: Pure UI/View layer. Cannot mutate data directly.

## Constraints
- Refer to individual package rules located in each module's `module.md` file before making structural refactors.
- NEVER perform a mass refactor or split large Java classes without asking the user for permission in chat first.

## Capitalization and formatting rules
- Always use sentence case for all headings, section titles, subheadings and list item labels across Markdown documentation files (e.g., `README.md`, `MODULE.md`).
- Only capitalize the first letter of a heading, title, sentence, sentence in a bullet list, plus proper nouns, acronyms or specific technical terms (e.g., `JSON`, `UI`, `FXGL`, `ID`, `Java`, `Spring Boot`).
- Avoid Title Case for section headings, descriptions and list entries.
- Do not use comma separator before 'and' or 'or'
- Entries marked with Draft are subject to change and await a final definition.

## Code rules
- Java files are not allowed to be longer than 1000 lines.
- Code methods should not exceed 100 lines of code.
- Never execute long-running simulation math, pathfinding, or heavy IO operations on the JavaFX application thread.
- Always utilize Java Records or immutable DTOs when passing game state snapshots from the `engine` to the `frontend` for rendering.

## Game State and Synchronization Rules
- The game loop must strictly follow the hybrid Push-Pull model with Explicit Tick Coordination.
- The `frontend` package must never modify the game state directly; it must stage all mutations as `Command` objects in the `CommandQueue` to be processed at the start of the next simulation tick.
- The `engine` package must only expose state to the `frontend` using the immutable `GameState` record snapshot via `ViewRegistry.refreshOnTick(state)`.
- Never mutate the `engine` game state directly from a background thread; all state mutations must be synchronized with the main simulation tick.
- Individual views (e.g., `EmpireView`, `PlanetDetailView`) must implement `updateData(GameState)` and pull data strictly from the injected snapshot.
- Local UI cards (e.g., `SystemEconomyWorkbenchCard`) may compute local mathematical previews for instant user feedback, but these calculations must remain purely cosmetic and never alter the underlying `engine` state.
