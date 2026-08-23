# Requirements

### Overview and goals

This plan outlines the implementation of phases 1 through 5 for the space conquest game, extending the core engine, command pipelines, AI controllers and frontend user interfaces:

1. **Biochemical habitation, demographic lifespans and colony logistics:** Race-specific biochemical metabolism loops (carbon oxygen breathers, silicon lithovores, ammonia volatiles, synthetic machines, plasma anomalies), demographic aging curves, retirement burden, gene technology longevity extensions, hive mind linear reproduction and conscious vs cryogenic stasis passenger transport.
2. **Atmospheric entry physics, thermal stress and surface-to-orbit mass drivers:** Planetary atmospheric drag, entry thermal stress and material melting limits, maneuvering agility metrics, surface mass driver catapults and zero-gravity orbital commerce hubs.
3. **Procedural galaxy generation, warp networks and deep-space anomalies:** Multi-system galaxy generation with varied star spectral classes, graph-based warp lane networks and deep-space anomalies (derelict ships, ruins, wormholes) scanned by science exploration vessels.
4. **Diplomatic negotiation engine, bilateral pacts and casus belli:** Bilateral treaty negotiation state machines (trade, research, defense, vassalage), casus belli justification and grievance tracking and ideological war constraints.
5. **Interactive star map, colony management UI and campaign manager:** 2D interactive galaxy and star system views with warp networks, colony management interfaces with power and mass driver controls and customizable campaign setup with save/load management.

---

### Scope

#### In scope
- Enhanced data models and simulation processors in `engine` for biochemical consumption, retirement aging, atmospheric entry drag, surface mass drivers, procedural warp graphs, anomalies, diplomatic pacts and casus belli.
- Command pipeline extensions in `control` for passenger stasis toggles, surface mass driver operations, anomaly scanning and diplomatic pact negotiations.
- AI controller extensions in `EmpireAIController` and `CorporationAIController` for demographic welfare, orbital trade routing, anomaly scanning and treaty evaluation.
- Interactive user interfaces in `frontend` for colony management, orbital mass drivers, galaxy map navigation, diplomatic negotiation dialogues and campaign initialization.

#### Out of scope
- Real-time 3D tactical combat rendering (tactical space and orbital battles resolve through deterministic multi-round turn calculations).
- Multiplayer network synchronization (single-player architecture with multi-agent simulation).

---

### Non-functional requirements
- Deterministic simulation: All stochastic rolls (anomalies, discovery, rolls) use seeded random generators to guarantee save game reproducibility.
- Turn execution performance: Simulating 10 turns across 50 solar systems with 100 fleets and 200 facilities must complete within 250 milliseconds.
- Clean separation of concerns: Engine maintains pure simulation state, Control processes command validation and AI agents and Frontend manages FXGL/JavaFX rendering.

# Delivery Steps

### ✓ Step 1: Implement biochemical habitation, demographic lifespans and colony logistics
Race-specific biochemical metabolism, demographic aging curves, retirement burden, gene therapy longevity extensions, hive mind reproduction and passenger stasis logistics are fully operational.

- Implement race-specific nutrient consumption in `PopulationProcessor` (carbon oxygen breathers, silicon lithovores, ammonia volatiles, synthetic machines, plasma anomalies).
- Implement demographic aging and retirement lifespan calculations with gene technology extensions.
- Implement hive mind linear reproduction model bounded by active Queen count with zero retirement or happiness overhead.
- Implement conscious vs cryogenic stasis passenger transit modes in `ShipInstance` and `FleetProcessor`.
- Add stasis and migration commands to `control` and write comprehensive unit tests in `engine` and `control`.

### ✓ Step 2: Implement atmospheric entry physics, thermal stress and surface-to-orbit mass drivers
Atmospheric fluid drag, entry thermal stress, hull material melting thresholds, surface mass drivers and orbital commerce hubs are fully operational.

- Implement atmospheric drag and Maneuvering Agility calculations in `ShipDesignValidator` and `FleetProcessor`.
- Implement entry corridor thermal stress against hull structural material melting points.
- Implement `SurfaceMassDriver` data models and simulation logic for launching raw minerals into zero-gravity orbit.
- Implement orbital commerce modules and update corporate AI to route high-volume bulk trading to orbital hubs when surface launch taxes are prohibitive.
- Add surface mass driver commands to `control` and unit tests across engine and control.

### ✓ Step 3: Implement procedural galaxy generation, warp networks and deep-space anomalies
Multi-system galaxy generation, warp lane graph topology and deep-space anomaly exploration are fully operational.

- Enhance `GalaxyGenerator` to generate multi-system star clusters with diverse spectral classes, binary stars and asteroid belts.
- Implement graph-based warp lane / hyperlane network generation and pathfinding across solar systems.
- Implement `Anomaly`, `AnomalyProcessor` and exploration scanning for derelict ships, ancient ruins and cosmic phenomena.
- Add `ScanAnomalyCommand` to `control` and extend AI controllers with automated science vessel exploration.
- Add unit tests for galaxy topology and anomaly resolution.

### ✓ Step 4: Implement diplomatic negotiation engine, bilateral pacts and casus belli
Bilateral treaty negotiations, grievance tracking, casus belli justifications and ideological war declarations are fully operational.

- Implement `DiplomaticPact`, `DiplomaticProposal` and `DiplomacyProcessor` bilateral negotiation state machines.
- Implement `CasusBelli` and `Grievance` tracking with democratic public happiness penalties for unprovoked wars.
- Extend `EmpireAIController` with autonomous treaty evaluation based on military balance, trade value and historical trust.
- Add diplomatic treaty commands to `control` and unit tests covering pact negotiations and casus belli validations.

### ✓ Step 5: Implement interactive star map, colony management UI and campaign manager
Interactive 2D galaxy map, orbital system navigation, colony management panels and campaign setup interfaces are fully operational in frontend.

- Implement interactive galaxy map and star system views with warp lane visual networks in `frontend`.
- Implement colony management view with biochemical consumption meters, power grid balances and surface mass driver controls.
- Implement diplomatic negotiation dialogue and treaty management panels in `frontend`.
- Implement campaign setup and save/load manager with customizable galaxy seeds and AI personalities.
- Verify full test suite across engine, control and frontend and update documentation.
