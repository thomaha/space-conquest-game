#### Planetary Surface Fortification Node
*   **Surface Footprint:** 1 planetary surface area slot.
*   **Primary Material Inputs:** `steel`, `silicon_carbide` (for heavy ceramic impact tiles), and `refined_lead` (for radiation and particle beam blast dampening).
*   **Systemic Factors:** Provides a localized defensive shielding layer that actively absorbs damage from orbital planetary bombardment bombs. It acts as a combat multiplier for defending ground forces, increasing the defensive survivability and firing efficacy of any garrisoned population cohorts.
*   **Workforce Requirement:** Requires an active garrison allocation of the `soldier` or `police` professions to maintain operational alertness.

#### Deep Crust Underground Bunker
*   **Surface Footprint:** 1 planetary surface area slot (requires a terrestrial or barren frontier planet crust).
*   **Primary Material Inputs:** `titanium_aluminide`, heavy `steel` structural reinforcement beams, and `limestone` (for processed planetary concrete sealing).
*   **Systemic Factors:** A hyper-dense, underground shelter module designed to protect a fixed volume of civilian population cohorts and stored resource inventories. Population units housed inside deep crust bunkers are completely immune to orbital bombardment damage, ensuring an empire's tax base and workforce survive even if the surface infrastructure is completely leveled.
*   **Workforce Requirement:** Maintained and operated by the `technician` profession.

#### Planetary Surface Defense Battery
*   **Surface Footprint:** 2 planetary surface area slots.
*   **Primary Material Inputs:** Heavy `refined_tungsten` (for anti-ship kinetic barrels), `superconducting_cuprates`, and `inconel_alloy` (for high-temperature cooling loops).
*   **Systemic Factors:** Fires heavy capital-scale anti-ship weapon arrays directly from the planetary surface into low orbit. It actively engages hostile blockading fleets, combat ships, or incoming troop transports attempting atmospheric entry. Surface batteries cannot be dodged by heavy capital ships, making them high-priority targets for orbital bombardment before an invasion begins.
*   **Workforce Requirement:** Calibrated by the `engineer` profession and commanded during combat alerts by the `soldier` profession.

#### Ground Combat Mechanics and Resolution

When an enemy empire deploys a **Troop Transport** to a planet and triggers a ground invasion, the game engine initiates a multi-turn ground combat processing loop on that specific celestial entity.

*   **Workforce mobilization:** Garrisoned units from the `soldier` and `police` professions form the core defensive army. If fortifications are breached or casualties mount, the planetary system governor can pass emergency conscription mandates, forcefully converting civilian `industrial_workers`, `miners`, or `farmers` into auxiliary milita cohorts. This provides emergency headcount but tanks the planet's resource production yields and civilian happiness variables.
*   **The attribute scaling factor:** Inherent biological traits from the `races.json` database directly multiply ground combat performance. A species like the **Chitinous Hive** or **Silicon Core** possessing a `physicalStrength` modifier of `1.5` or `1.8` will deal massive close-quarters combat damage, easily crushing higher-intelligence but physically weaker species like **Humans** or **Vulkans** if fighting outside of fortification nodes.
*   **The leadership variable:** If the active *System Governor* has a professional background matching the `soldier` trait (such as a veteran commander), their tactical expertise grants a system-wide morale and defense modifier, reducing incoming casualties across all space entities in that solar system.

#### Orbital Bombardment and Planet-Cracking Bombs

To crack a heavily fortified world before sending down fragile troop transports, combat ships can equip specialized **Bombs** to execute orbital planetary bombardment missions.

#### Conventional Kinetic Bombardment Pack
*   **Module Type:** Capital ship weapon mount (requires a *Combat Ship* primary role).
*   **Primary Material Inputs:** `refined_tungsten` kinetic dart pods and basic `steel` launch rigs.
*   **Systemic Factors:** Drops heavy, unguided tungsten rods from high orbit, utilizing gravitational acceleration to smash surface structures. It deals high damage to *Surface Fortification Nodes* and *Planetary Surface Defense Batteries*. It creates minimal collateral fallout, leaving underlying mineral veins and civilian populations relatively untouched for post-war occupation.
*   **Workforce Requirement:** Armed and tracked by the `soldier` profession.

#### Isotopic Fission Bombardment Warhead
*   **Module Type:** Capital ship weapon mount (requires *Nuclear Fission* technology).
*   **Primary Material Inputs:** `refined_uranium` or `refined_thorium` core containers, `refined_lead` shielding, and specialized electronic fuses.
*   **Systemic Factors:** Unleashes massive radioactive explosions across a planet's surface. It instantly vaporizes surface area slots, obliterating factories, farms, and open-world agriculture zones while causing catastrophic civilian population casualties. It spikes the planet's environmental toxicity, introducing long-term habitability penalties and rendering the world hazardous for carbon-based lifeforms for decades post-siege.
*   **Workforce Requirement:** Calibrated and deployed exclusively by the `engineer` profession under direct imperial state command.

#### Antimatter Planet-Cracker Ordinance
*   **Module Type:** Super-heavy capital weapon mount (requires *Antimatter Mechanics* technology).
*   **Primary Material Inputs:** `antimatter harvesting / storage` magnetic containment pods, `superconducting_cuprates`, and `graphene`.
*   **Systemic Factors:** The ultimate late-game siege payload. Upon detonation, it rips apart the physical matter of the target planet from the inside out, triggering a chain reaction that completely collapses its structural integrity. 
*   **The Destructive Choice:** It permanently deletes the celestial entity from the galaxy map, replacing the planet or moon with a shattered **Asteroid Field Node** containing hyper-dense chunks of pure debris like `refined_iron`, `refined_silicon`, and platinum group metals, turning a living population colony into a cold, uninhabited mining zone.
*   **Workforce Requirement:** Requires a specialized deployment team composed of the `scientist` and `engineer` professions.

#### The Society Structure Factor

The active political orientation of an empire dictates its structural rules for winning or losing ground sieges:
*   **Individualist Societies (Democracies):** Highly vulnerable to prolonged planetary sieges. As casualties pile up and food or consumer goods shortages hit the commercial hub, citizen happiness drops exponentially, triggering worker strikes, productivity drops, or anti-war riots that can force the democratic government to auto-surrender the system.
*   **Collectivist Societies (Autocracies):** Can sustain brutal wars of attrition by enforcing iron-clad martial law. They can forcefully conscript aging populations or drop retirement percentage thresholds to keep production lines feeding the weapon factories, using local `police` presence to forcefully silence civilian dissent.
*   **Hive Mind Societies:** The ultimate defensive nightmare. Because they feature no private individualism, civilian happiness metrics, or worker wages, a hive mind world can never undergo civil unrest, strike actions, or political demoralization. They will fight with 100% uniform efficiency block-by-block until every single drone organism on the astronomical body is physically exterminated or the fertile queen is destroyed.
