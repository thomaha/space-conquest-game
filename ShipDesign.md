### Ship customization and the modular hull design interface

The ship design interface is a hard physics-bounded engineering playground. Instead of selecting from fixed, pre-defined hull size classes (such as "Destroyer" or "Cruiser"), users construct starframes completely from scratch. The interface dynamically calculates the ship's dry mass, slot limits, operational classification and launch restrictions based entirely on the user's custom choice of modules and structural materials.

#### Structural blueprint properties

Every user-defined ship configuration requires the allocation of a primary operational anchor role and a core structural material, which dictate the baseline scaling limits of the starframe:

- **Primary operational role:** The user tags the design with an explicit intent from the [User-Defined Ship Roles Matrix](Spaceships.md). This tag functions as a validation check (e.g., a *Troop Transport* blueprint must contain a *Cryogenic Troop Transport Bay*). It also dictates how automated private corporate networks or military AI fleets utilize the hull after manufacturing.
- **Starframe structural material:** The user selects a material from the [Materials Database](engine/src/main/resources/materials.json) to forge the ship's chassis skeleton and base outer plating. The choice of material alters three absolute properties per slot used:
    - `baseHullWeightMultiplier`: Affects the total dry mass.
    - `structuralStrengthCoefficient`: Dictates hull damage thresholds against kinetic and thermal weapons.
    - `manufacturingComplexityRating`: Restricts which orbital shipyards can assemble the blueprint.

#### The dynamic sizing and slot allocation engine

Hulls expand or contract dynamically based on the total sum of their equipped **Module Size Slots**. There are no artificial size limits; the scale of the vessel is an emergent output of its functional components:

- **Light starframes (1 to 15 total slots allocated):** Classified automatically as light craft (e.g., short-range fighters, bombers, light scouts or fast couriers). They possess high tactical evasion bonuses and minor dry mass footprints, making them incredibly cheap to launch past planetary gravity wells.
- **Medium starframes (16 to 50 total slots allocated):** Classified as medium hulls (e.g., escorts, system-defense gunships, explorers or specialized corporate cargo haulers). They strike a balance between structural durability and propulsion efficiency.
- **Heavy starframes (51 to 150+ total slots allocated):** Classified as heavy capital starframes (e.g., combat warships, carriers, deep-space construction trains, long-range logistics freighters or colony arks). They hold an immense physical footprint, making them highly optimal to manufacture exclusively within zero-G *Capital Mega-Engineering Slipways* to completely avoid planetary launch gravity penalties.

#### Slot classes and component formatting

Instead of using uniform, arbitrary item grids, a custom starframe layout handles modules using explicit **Slot Size Classes**. Modules from the [Spaceship Component Manifest](Spaceships.md) or utility manifests carry static slot size footprints that map directly onto the hull frame:

- **Small (S) slots [1 size slot]:** Optimized for compact, highly reactive utility and light tactical sub-systems. Examples include *Point-Defense Laser Grids*, maneuvering *RCS Thruster Arrays*, light *Biometric Passenger Cabin Arrays* and low-yield localized scanning arrays.
- **Medium (M) slots [2–6 size slots]:** Designed for standard ship operations and baseline frontline combat modules. Examples include *Continuous Beam Laser Mounts* (2 slots), *High-Impulse MPD Thrusters* (4 slots), *Baseline Fission Propulsion Drives* (4 slots).
- **Large (L) slots [8–30+ size slots]:** Reserved for hyper-dense, super-heavy macro-structures and capital-grade fleet components. Examples include *Advanced Fusion Propulsion Drives* (8 slots), *Bulk Cargo Vaults* (12 slots), *Spacetime Deformation Warp Drives* (16 slots) and *Planetary Colonization Modules* (30 slots).

#### The structural integrity ceiling ($SI_c$)

Hulls expand dynamically as slots are added, but a ship frame cannot be expanded infinitely without collapsing under its own structural stress or propulsion vectors. Every design must satisfy the **Structural Integrity Ceiling**:

Draft: $$SI_c = \frac{\text{Hull Frame Material Strength Coefficient}}{\text{Total Allocated Module Slots}} \times \left(1.0 - \text{Total Dry Mass Modifier}\right)$$

- **The material choice barrier:** If a user designs a massive starframe utilizing cheap `steel` or lightweight `refined_aluminum` to save on upfront costs, the low structural strength coefficient will flag a structural integrity failure if they attempt to squeeze more than 40 slots into the frame.
- **The nanotech pivot:** To successfully build a heavy capital starframe holding 100+ module slots (like a *Carrier Ship* or a multi-vault *Cargo Transport*), the design must utilize advanced composite materials like `carbon_nanotubes` or `silicon_carbide`. This ensures the frame can withstand the immense torsional forces generated by high-impulse propulsion drives without tearing itself apart.

#### Autonomous corporate ship design

Private corporations may contract shipyards to manufacture eligible public blueprints or design proprietary blueprints for their own use. A proprietary blueprint is owned by the designing corporation:
- **The shortcoming trigger:** If an empire's public design database lacks a ship specialized to solve a critical regional supply bottleneck, the corporate AI will independently initialize its own ship design cycle. For example, if a colony has a massive surplus of volatile ices but a public cargo design has a dry mass too heavy to escape the planet's gravity well profitably, a trade corporation will autonomously design a custom, hyper-lightweight *Cargo Transport* utilizing aluminum framing and minimal modules to clear the deficit.
- **Proprietary blueprint access:** Only the corporation that owns a proprietary blueprint may commission ships from it. The player must not be offered that blueprint in a normal ship construction flow. Purchasing a finished ship from a corporate shipyard transfers the ship to the player but does not transfer its blueprint or manufacturing rights.
- **Yard fee revenues:** When a private corporation designs a proprietary hull and pays universal credits to manufacture it at a state-owned *Orbital Shipyard Assembly Grid*, the state public treasury directly skims revenue from the contract, turning autonomous corporate design initiatives into a massive financial booster for the empire.

#### The external armor interface

To maintain total structural clarity within the interface, **Hull Armor Plating is completely separated from the slot formatting engine**.
- **No slot allocation:** A user or corporation never spends an internal Small, Medium or Large slot to add armor to a ship.
- **Global shell scaling:** Armor is applied as an external shell overlay that scales its total material requirements and weight modifiers directly with the **Total Sum of Allocated Module Slots**. A 120-slot *Dreadnought* layout automatically requires ten times more `silicon_carbide` or composite steel to line its frame than a 12-slot *Escort*, ensuring that larger custom designs carry realistic, weight-scaling physical and financial launch tax penalties.

#### Physics and capability validation rules

Before a user can finalize and save a ship design blueprint, the interface runs a multi-variable validation pass. If any of the following constraints are broken, the blueprint is flagged with an engineering error and cannot be queued for manufacturing:

- **The power grid constraint:** The total structural energy draw demanded by active sub-systems (such as sensors, shield grid arrays and automated point-defense networks) cannot exceed the continuous energy output generated by the equipped propulsion drives or dedicated power modules:
- **The nanotech complexity cap:** The maximum complexity rating among any individual module selected for the design cannot exceed the empire's current *Nanotechnology* tier. For example, a player cannot attach a level 8 *Advanced Fusion Drive* to a custom frame if their empire's nanotechnology tree is currently locked at tier 4.
- **The thrust-to-mass launch barrier:** The interface computes the absolute **Maximum Launch Mass** (the total structural dry mass of all modules and hull plating combined with the maximum weight capacity of all internal cargo holds). It checks this value against the raw thrust yield generated by the ship's propulsion drives:
- **Launch capability error:** If the total thrust output of the selected drives falls below the `Required Minimum Thrust`, the interface flags a critical warning: **the ship is too heavy to blast off from the home world's surface**. The user must either substitute heavy modules for lighter alternatives, upgrade propulsion performance or commit to manufacturing the ship exclusively in orbital space stations.

#### Cost and work hour estimation profiles

Once a design passes all physics and structural validations, the interface generates a dynamic manufacturing bill of materials (BOM) and production timeline:

- **Unit cost in work hours:** Calculated by multiplying the combined complexity ratings of the modules by a structural scale factor, determining how long an assembly grid's workforce must manipulate tools to output the hull.
- **Required materials inventory:** The interface extracts the exact elemental and composite weights required to build each component. It compiles a precise material receipt from the **60-80 materials list** (e.g., a combat warship blueprint might demand 40,000 kg of `tungsten`, 12,000 kg of `superconducting_cuprates` and 5,000 kg of `silver`).
- **Corporate fleet access:** Corporations may evaluate eligible public blueprints for **Cargo Transport** or **Mine Ship** roles. A proprietary blueprint remains available only to its owning corporation, even when its design record appears in a shared registry.

#### Construction scheduling and progress (Draft)

Saving a valid blueprint does not create a ship. Commissioning a hull creates a construction order at a compatible shipyard and ties that order to the blueprint version, owner, yard and bill of materials. A yard must meet the design's size and complexity limits. Only one hull may occupy a slipway at a time; additional orders wait in that yard's queue.

- **Shared time base:** One simulation turn is one game day. A shipyard adds work only on a daily turn, regardless of the selected display speed. Even a small hull finishes no earlier than its first eligible daily turn. A ship that requires several days retains its accumulated work between turns, saves and loads.
- **Effective daily work:** Available `engineer` and `industrial_worker` hours, powered slipway capacity and researched assembly efficiency determine how many work hours the yard can contribute that day. Work cannot exceed the yard's daily capacity or the order's remaining work. The exact shift length and efficiency coefficients need balancing.
- **Progress calculation:** The order records required work hours and accumulated work hours. At each daily turn, `accumulatedWorkHours = min(requiredWorkHours, accumulatedWorkHours + effectiveWorkHoursForDay)`. The UI shows completed work, total work, percentage and an estimated completion date based on the current effective rate. If the rate is zero, the estimate is unavailable rather than implying instant completion.
- **Blocking and resources:** The order does not advance when required materials, qualified workers, power or access to its slipway are missing. Materials and credits must be accounted for without allowing another order to spend the same stock. Reservation, staged consumption and cancellation refund rules remain Draft.
- **Completion:** The ship is instantiated and assigned to its owner only after required work and material obligations are satisfied. A player purchase from a corporate shipyard transfers a finished ship; it does not grant access to the corporation's proprietary blueprint or let the player place a construction order from it.

The current `ShipDesign` record stores an owner ID and a proprietary flag, but does not store manufacturing work hours or a bill of materials. `QueueShipBuildCommand` checks only that a design ID exists and immediately creates a ship. Construction orders, daily work progress, ownership restrictions and finished-ship purchases are not yet implemented.
