#### Maneuvering Hardware and Propulsion Modules
To change vector states, execute evasive maneuvers during tactical engagements, or maintain steady entry corridors, ships must equip dedicated maneuvering thrusters.

#### The Vacuum of Space vs. The Atmospheric Friction Factor
When a ship executes sub-light transit commands or engages hostile forces, the game engine evaluates the host celestial entity's **atmosphere** and **gravity** attributes from the planetary database to alter flight behaviors:

*   **Deep Space Vacuum Profile (`atmosphere: none`):**
    In an airless environment (like around the Moon or deep space star systems), aerodynamic flight surfaces provide zero utility. A ship's maneuvering agility—its acceleration rates and tracking evasion scores—is calculated using a pure **Thrust-to-Mass Ratio**:

    $$\text{Maneuvering Agility Score} = \frac{\sum \text{RCS Thruster Impulse Yields}}{\text{Total Structural Dry Mass} + \text{Stored Cargo Weight}}$$

    This calculation dictates why a *Cargo Transport* packed with thousands of kilograms of unrefined `iron_ore` turns incredibly sluggishly compared to an empty ship, rendering it an easy target for fast *Short-Range Fighters* or *Escort* wings.

*   **Atmospheric Entry Profile (`atmosphere: nitrogen_oxygen` / `carbon_dioxide`):**
    When a vessel descends into a planet with an active atmosphere (like Earth or Mars), the structural cross-section of the hull generates **Atmospheric Drag** and friction heating.
    *   **The Aerodynamic Drag Penalty:** Thick gas envelopes place an aerodynamic drag penalty on sub-light speeds, heavily draining the ship's active power grid as engines fight fluid resistance.
    *   **Friction and Thermal Management:** Blasting down an entry corridor at high speeds generates intense thermal stress. If a ship is built entirely out of cheap, lightweight `refined_aluminum` (Melting point: ~933 K) or biological `bio_polymers`, entering a dense atmosphere without proper deceleration will cause catastrophic hull degradation or completely vaporize the frame.
    *   **Thermal Protection Synergy:** Hulls reinforced with high-melting-point materials like `refined_tungsten` (Melting point: ~3695 K), `silicon_carbide` ceramic tiles, or specialized *Ablative Deflection Plating* effortlessly absorb the friction heat block, ensuring a safe entry vector.

#### The Planetary Blast-Off Gravity Tax
Your design notes state that spaceships built on a planet must escape its g-forces to become useful. The cost to lift a customized frame from a planet's surface slot into a stable orbit represents a massive economic hurdle:

$$\text{Launch Cost (Credits)} = \left( \text{Total Ship Structural Dry Mass} + \text{Stored Cargo Mass} \right) \times \text{Planetary Gravity} \times \left( 1 + \text{Atmospheric Pressure} \right)$$

*   **The Fission vs. Fusion Lift Dynamics:** If a player launches a ship fitted with a baseline *Fission Propulsion Drive*, its heavy lead containment shields add immense dry mass, compounding the gravity tax. Upgrading to a lightweight *Advanced Fusion Propulsion Drive* slashes the dry mass loop, permanently dropping the launch fee.
*   **The Commercial Arbitrage Implication:** This equation creates an immediate operational challenge for private corporate fleets. If a private corporation operates a *Cargo Transport* on the surface of Earth (`gravity: 9.81`, `atmosphere: 1.0`), the export gravity tax on thousands of kilograms of dense refined elements will wipe out their trade margins. The corporate AI will naturally optimize its supply lines by using surface mass drivers to launch raw ores up to a zero-gravity orbital space station, executing all high-volume B2B marketplace trading inside a *Commerce Module* anchored completely outside the atmospheric tax zone.
