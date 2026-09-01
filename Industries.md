#### 1. Core Operational Variables

Every technical application inside the industrial tree processes materials according to four baseline operational layers:

- **Production Facility Capability:** An industry cannot be activated on a planet or space station unless a local production facility possesses an equal or higher complexity threshold than the target application's active tier.
- **Workforce Efficiency Multiplier:** Processing speed and resource yields are heavily scaled by the headcount and average training efficiency of the local allocated profession (`industrial_workers`, `engineers` or `farmers`). If minimum intelligence or strength thresholds inside `professions.json` are unmet, the facility experiences severe production bottlenecks.
- **Dynamic Power Grid Demands:** Active operations pull a static electrical energy draw ($E_{\text{draw}}$) from the local grid every turn. Running out of power trips a grid deficit, forcing the facility completely cold and freezing all item or element assembly pipelines instantly.
- **The Byproduct Recovery Ledger:** Highly complex chemical refining loops generate secondary atomic byproducts. If a colony has researched gas capture or recycling infrastructure, these byproducts are safely routed into storage modules as valuable secondary resources rather than being lost as environmental pollution.

#### 2. Environmental and Gravity Constraints

Industrial applications are strictly bounded by localized planetary physics from the celestial database. Processing blueprints are categorized into three physical execution environments:

[ENVIRONMENTAL LOGISTICS OVERLAYS]
├── Universal (Anywhere) ──> Standard Mechanics • Independent of Gravitational State
├── Gravity-Locked ─────────> Requires Massive Fluid Settling • Local G-Force > 0.1g
└── Microgravity-Native ───> Requires Near-Zero Weight or Hard Vacuum • Local G-Force < 0.05g

- **Universal Applications:** Can be executed seamlessly on any terrestrial world, airless moon, asteroid node or orbital base frame (e.g., *Automated Assembly Lines*, *Biomass Processing*).
- **Gravity-Locked Applications:** Rely on physical weight, fluid buoyancy or atmospheric settling to process composites. If built inside an orbital space station or an asteroid outpost without heavy artificial gravity plating modules, the facility's production efficiency drops to zero (e.g., *Industrial Soil Cultivation*, *Pyrometallurgical Smelting*).
- **Microgravity-Native Applications:** Demand perfect weightlessness, molecular isolation or hard vacuums to grow pristine atomic alignments, foam heavy metals or capture free-floating elements without wall contamination. If built on a planet with a high G-force, the material structure breaks down entirely, ruining the output (e.g., *Zero-G Magnetic Refining*, *Microgravity Foundries*).

#### 3. Granular Industrial Recipe Layouts

Draft: Base effect of industries. This needs to be generalized, especially for refineries based on the materials being refined.
All industries require some `technicians` to be maintained, in addition to the noted primary workforce type. 

#### Open-World Agriculture: Industrial Soil Cultivation
- **Operational Environment:** Gravity-Locked (Requires stable soil, local G-Force > 0.2g)
- **System Requisites:** Requires a planet with an active `atmosphere` and an established headcount of the `farmer` profession.
- **Turn-Based Resource Inputs:**
    - 100 kg `nitrates` (Nitrogen base)
    - 50 kg `phosphates` (Phosphorus core)
    - 50 kg `potash` (Potassium catalyst)
    - 500 kg `water_ice` (Melted irrigation liquid)
    - 2,500 kW Electrical Power Grid Draw
- **Turn-Based Outputs:**
    - 1,200 kg Organic Food Stocks (Compatible Carbon-based nutrients)
    - *Systemic Side-Effect:* Induces a minor, permanent reduction to the planet's local `waterLevel` score due to water table depletion over time.

#### Closed-Loop Life Support: Hydroponic Growth Arrays
- **Operational Environment:** Universal (Fully enclosed, insulated from vacuum)
- **System Requisites:** Installed inside space stations or space bases, run by the `farmer` profession.
- **Turn-Based Resource Inputs:**
    - 50 kg `refined_phosphorus`
    - 20 kg `nitrogen_gas`
    - 300 kg `water_ice`
    - 6,000 kW Electrical Power Grid Draw
- **Turn-Based Outputs:**
    - 800 kg High-Density Organic Food Stocks
    - 10 kg `oxygen_gas` (Atmospheric recycling byproduct captured by storage modules)

#### Ore Refining: Pyrometallurgical Smelting
- **Operational Environment:** Gravity-Locked (Requires thermodynamic gravity separation, local G-Force > 0.1g)
- **System Requisites:** Run on planetary surfaces by the `industrial_worker` profession.
- **Turn-Based Resource Inputs:**
    - 1,000 kg `iron_ore`
    - 200 kg `carbon_monoxide_ice` or raw petrochemical deposits (as thermal reducing agents)
    - 4,000 kW Electrical Power Grid Draw
- **Turn-Based Outputs:**
    - 700 kg `refined_iron`
    - 300 kg `oxygen_gas` (Gaseous byproduct available for life support capture)
    - *Systemic Side-Effect:* Generates systemic pollution metrics, lowering local colony habitability indexes unless gas scrubbing arrays are active.

#### Ore Refining: Zero-G Magnetic Refining
- **Operational Environment:** Microgravity-Native (Requires near-zero weight and hard vacuum, local G-Force < 0.05g)
- **System Requisites:** Built strictly on orbital stations or asteroid outposts, run by the `industrial_worker` profession.
- **Turn-Based Resource Inputs:**
    - 1,000 kg `platinum_ore`
    - 12,000 kW Electrical Power Grid Draw (Massive electromagnetic ionization draw)
- **Turn-Based Outputs:**
    - 500 kg `refined_platinum` (100% pure element yield)
    - 300 kg `refined_iron`
    - 200 kg `refined_sulfur`

#### Metallurgy: Heavy Steel & Titanium Alloying
- **Operational Environment:** Universal (Can execute via induction thermal coils in any environment)
- **System Requisites:** Operated by the `industrial_worker` profession inside a foundry module.
- **Turn-Based Resource Inputs:**
    - 980 kg `refined_iron`
    - 20 kg `refined_carbon (graphite)`
    - 8,000 kW Electrical Power Grid Draw
- **Turn-Based Outputs:**
    - 1,000 kg `steel` (Advanced structural alloy structural asset)

#### Manufacturing: Replicators / Matter Synthesizers
- **Operational Environment:** Universal (Functions via atomic nanotechnology sorting)
- **System Requisites:** High-complexity late-game installation (Level 9+), managed exclusively by the `engineer` profession.
- **Turn-Based Resource Inputs:**
    - 100 kg Base Scrap Elements or raw unrefined slag residues
    - 150,000 kW Electrical Power Grid Draw (Extreme energy-to-matter stabilization threshold)
- **Turn-Based Outputs:**
    - 100 kg High-Complexity Electronics Assemblies, Superconducting Coils, or target advanced spaceship modules.
    - *Systemic Impact:* Radically slashes the raw material weight requirement of high-complexity manufacturing to near zero, entirely shifting the empire's economic bottleneck from resource mining logistics onto bulk electrical power generation.

### Industrial Scale, Expansion Loops, and Financial Ownership

Industrial facilities across all sectors—whether planetary surface plants or orbital space station modules—do not operate at a static, unchangeable capacity. Every industry is simulated with a specific size class that can be systematically upgraded and expanded to amplify production throughput. 

[ THE FACILITY EXPANSION CYCLE ]
Expansion initiated ──> Material & credit cost deducted ──> Output throttled (-50%) ──> Capacity & max slot volume increased

#### 1. Sizing and Scalability Mechanics

Every active industrial node (e.g., an electronics matrix, a metallurgy foundry, or an organic hydroponic growth array) operates under an integrated scaling framework:
- **Capacity Tier Arrays:** Facilities scale from Tier I (Local Prototype Plants) through Tier II and III (Regional Mass-Foundries) up to Tier IV+ (Continental Industrial Complexes).
- **Resource and Energy Multipliers:** Upgrading an industry to a larger tier multiplies its total material throughput limits, maximum worker capacity slots, and turn-based power grid draws by a flat scale factor. A Tier III facility consumes and outputs three times the base volume of a Tier I node per game turn.

#### 2. The Infrastructure Expansion Pipeline

To scale up a facility, the owning entity must execute a structural upgrade project. This loop is heavily bounded by resource sinks and operational downtime:
- **The Construction Cost Receipt:** Expanding an industry requires an upfront capital investment paid in universal credits and a flat structural material receipt from the **60-80 materials list** (e.g., expanding an electronics matrix demands a bulk delivery of `steel`, `refined_copper`, and `refined_silicon` to physically build the new cleanrooms and conveyor tracks).
- **Workforce Assembly Demand:** The expansion project requires an active allocation of the `engineer` profession to calibrate tolerances and guide assembly lines.
- **The Production Throttle Penalty:** Constructing new wings or adding structural module framing creates deep physical disruptions. **While an expansion project is active, the facility's current processing output is reduced by 50%**. This temporary supply chain bottleneck forces empires and corporations to strategically time their expansions, preventing sudden resource or food deficits during active military campaigns.

#### 3. Ownership Structures and Post-Tax Net Profit Routing

The financial flows, capital investments, and output distribution of an expanding facility are strictly governed by its underlying ownership classification:

[ INDUSTRIAL ASSET OWNERSHIP SPECTRUM ]
├── Public State Property ───> State Funded • 100% Net Profit Routes to National Treasury
├── Private Corporate Asset ─> Corporately Funded • Profit Routed to Corporate Reserves after State Tariffs
└── Hive Consciousness Grid ─> Zero-Currency System • 100% Material Routing to State Grid (No Penalty/Wages)

##### Public State Industries
- **Management Framework:** Funded and initiated entirely by the player using central state treasury credits.
- **Profit Routing:** 100% of the net credits generated by selling output elements or modules at local *Commercial Hubs* bypasses civilian channels and flows directly back into the public state budget to fund technological research or subsidize colony deficits.

##### Private Corporate Industries
- **Management Framework:** Managed autonomously by private corporate syndicates using their aggregated liquid capital reserves. The corporate AI independently evaluates local *Market Shortcoming Scores* to initiate and fund expansions where profitable production deficits exist.
- **The Tariff Skim Layer:** Every turn that a private facility liquidates its output or executes business-to-business (B2B) trade, the engine subjects the gross transaction value to the local hub's active **transaction tariff rate**.
- **Net Profit Routing:** Once the public state tariff is cleanly skimmed and local worker salaries are fully settled, **all remaining net profit goes exclusively to the private corporate owner**. These credits are deposited straight into the corporation's treasury pool, providing them with the required capital to buy more *Cargo Transports*, fund independent *Prospector* teams, or trigger further factory expansions.

##### The Hive Mind Exception
- **Management Framework:** The property of the hive. Because hive mind societies completely eliminate the division between public and private sectors, currency, and corporate entities, expansions do not cost credits or require wage balances.
- **Zero-Downtime Command Economy:** The hive consciousness shifts drone cohorts and allocates base raw elements (`steel`, `refined_iron`) straight from the state storage modules to enlarge its network links. 100% of the industrial yield routes directly back into the unified state grid as pure material capital to sustain the collection, ignoring the credit profit loop entirely.