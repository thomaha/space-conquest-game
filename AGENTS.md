# Agent guidelines
## Package boundaries
These are intended boundaries. Current integration gaps are recorded in the module `MODULE.md` files.
- **`engine/src/main/java/com/spaceconquest/engine/`**: Contains core simulation logic and data models. It has no access to JavaFX or FXGL.
- **`control/src/main/java/com/spaceconquest/control/`**: Acts as the gatekeeper. Validates inputs against `engine` data before updating state.
- **`frontend/src/main/java/com/spaceconquest/frontend/`**: Pure UI/view layer. Cannot mutate data directly.

## Constraints
- Refer to individual package rules located in each module's `MODULE.md` file before making structural refactors.
- NEVER perform a mass refactor or split large Java classes without asking the user for permission in chat first.

## Capitalization and formatting rules
- Always use sentence case for all headings, section titles, subheadings and list item labels across Markdown documentation files (e.g., `README.md`, `MODULE.md`).
- Only capitalize the first letter of a heading, title, sentence, sentence in a bullet list, plus proper nouns, acronyms or specific technical terms (e.g., `JSON`, `UI`, `FXGL`, `ID`, `Java`, `Spring Boot`).
- Avoid Title Case for section headings, descriptions and list entries.
- Do not use comma separator before 'and' or 'or'
- All design entries are subject to change. Entries marked with Draft are especially unsettled and await a final definition. The absence of Draft does not imply implementation.

## Code rules
- Java files are not allowed to be longer than 1000 lines.
- Code methods should not exceed 100 lines of code.
- Never execute long-running simulation math, pathfinding, or heavy IO operations on the JavaFX application thread.
- Always utilize Java Records or immutable DTOs when passing game state snapshots from the `engine` to the `frontend` for rendering.

## Game state and synchronization rules
These are target architecture rules. Existing code does not yet satisfy every rule; consult the implementation notes in `README.md` before assuming a boundary is enforced.
- The game loop must strictly follow the hybrid Push-Pull model with Explicit Tick Coordination.
- The `frontend` package must never modify the game state directly; it must stage all mutations as `Command` objects in the `CommandQueue` to be processed at the start of the next simulation tick.
- The `engine` package must only expose state to the `frontend` using the immutable `GameState` record snapshot via `ViewRegistry.refreshOnTick(state)`.
- Never mutate the `engine` game state directly from a background thread; all state mutations must be synchronized with the main simulation tick.
- Individual views (e.g., `EmpireView`, `PlanetDetailView`) must implement `updateData(GameState)` and pull data strictly from the injected snapshot.
- Local UI cards (e.g., `SystemEconomyWorkbenchCard`) may compute local mathematical previews for instant user feedback, but these calculations must remain purely cosmetic and never alter the underlying `engine` state.
