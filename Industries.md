## Current implementation

Generated homeworlds now include a steelworks using the `alloy_steel` recipe. It consumes refined iron and carbon and supplies steel for ship and orbital construction. Outstanding construction bills add demand to local commercial hubs so refineries can sell inputs as projects proceed.

Ground facility, elevator and geoengineering projects buy only from a hub on their own planet or moon. Orbital stations, station modules, orbital shipyards and megastructures use a commercial hub on a station at the construction site. Until a commerce module establishes that hub, an owner can build from material already loaded aboard their cargo or construction ship at the target orbit, dock or deep-space site. Loading a transport buys stock from the source body's hub, checks cargo capacity, requires the ship in that body's orbit or at a station orbiting it and books an available launch provider. A project can be queued before supply arrives and will wait without progress. Fleets distinguish surface, orbit, dock and deep-space sites; local journeys and warp have daily progress. Automated routes assign a real cargo ship, buy source stock from its hub into the ship's hold, travel to the destination and sell only when the destination hub has cash and storage. Exports from a surface hub use an installed launch provider. Local maneuvers now commit design-dependent propellant and reactor fuel at departure, while automated routes buy a shortfall from an accessible hub and record that purchase as an operating cost. Surface ascent uses its selected launch provider instead of ship propellant. Route travel times and maneuver budgets remain provisional fixed values rather than orbital mechanics.

Water electrolysis is the live planetary industry that produces `oxygen_gas`: one 1,000 kg `purified_water` batch yields 888.9 kg oxygen and 111.1 kg hydrogen using electricity and paid workers. It is buildable where its technology and input conditions are met, but generated enclosed settlements do not yet start with one. Surface-water treatment produces `purified_water` only on a body with liquid water. Ice mining depletes a local `water_ice` deposit and a separate ice-treatment facility converts that ice to purified water. Surface water is currently a renewable environmental source limited by treatment capacity rather than a measured reservoir. Online orbital hydroponics modules report 10 kg oxygen per tick, but that output is not connected to a local commercial hub or household life support. Breathable worlds supply ambient oxygen to households without an industrial transaction; enclosed settlements still need market stock or an electrolysis plant. Iron smelting produces carbon dioxide rather than breathable oxygen.

The live reactor-fuel chain mines uranium and thorium ore, refines each into a separate fission fuel and separates deuterium from purified water. Fission thermal ships carry refined uranium or refined thorium in their cargo and heat hydrogen propellant. Fusion ships also use hydrogen propellant; carried deuterium or fusion pellets increase effective exhaust speed and consume a separate reactor-fuel allowance on departure. Generated homeworlds have uranium and thorium deposits, starter extraction and refining facilities and initial hub stock for these fuels. Staffed power plants now add their daily fuel requirements to local hub demand so fuel refiners can continue selling after opening stock is depleted. Fusion pellets have a cataloged deuterium and helium-3 recipe, but helium-3 has no live extraction chain. Plutonium production and fuel handling are not modeled yet.

Starting homeworld farm, biomass, consumer-goods and luxury-goods staffing scales with population and technology tier. A farm batch consumes purified water and fertilizer minerals, then produces 120 kg food and 4 kg recoverable agricultural biomass per paid farmer before facility and technology modifiers. On homeworlds whose people do not eat farm food, industrial biomass cultivation also supplies fiber using water, carbon and fertilizer minerals. A biomass-processing plant buys biomass and purified water to make biopolymers. Consumer manufacturing combines 499 kg biopolymers, 250 kg refined aluminum, 150 kg refined copper, 100 kg silicon and 1 kg refined rare earths into a 1,000 kg basket of everyday goods including electronics per ten workers. Rare earth ore yields only 25 kg per mining batch of 100 workers, compared with 1,000 kg for common ores, and its refined product has a higher price baseline. Luxury workshops buy consumer goods, biopolymers and copper. These goods are measured in kilograms, not discrete units. The opening hub holds seven days of most live-chain inputs and a one-day biopolymer buffer; the tested first month does not depend solely on opening consumer or luxury stock.

The live material-industry pass supports common and scarce ore mining, surface-water treatment, ice mining and treatment, industrial soil and biomass cultivation, biomass processing, water electrolysis and the recipes in `RefinementProcessor.STANDARD_RECIPES`, including recovered byproducts. A facility requires its owning empire to have `industrial_production`, enough facility tier, paid workers and available grid power; electrolysis also requires `electricity`. Researched `supply_chain_automation` increases throughput by 10%, while `geological_prospecting` adds 25% to mine output. Its owner pays for powered shifts at 0.02 credits per kWh before production, then buys recipe inputs from the hub on the same body using corporate reserves or the facility's state-owned operating balance. Unaffordable shifts cannot produce. Mines deplete a discovered local deposit. Production enters a persistent facility stock account. A non-Hive local hub buys it only if modeled household or recipe-input demand exists and stocks are below 30 days of that demand, subject to hub cash and storage. Unsold output stays with the facility. The hub pays producers 80% of the posted retail spot price, retaining 20% as a trading margin; this is neither freight nor tax. Hive facilities continue to use physical stock without a corporate buyer. Generated hubs begin with trading cash so production can reach shelves before all opening inventory is sold. `IndustryAccount` records daily inputs, electricity, wages, maintenance, production, sales, unsold stock, pretax realized result and the public facility's signed operating balance; saves preserve this account.

The spot price uses a resource-specific baseline in credits per kilogram, adjusted by local supply and demand with a 40% baseline floor. Raw ores and bulk nutrients start below refined materials; biopolymers, consumer goods and luxury goods have progressively higher baselines. A facility has no fixed one-credit-per-unit recipe fee: the overview's input-cost line reports credits actually spent on the listed recipe inputs during the last processed day. Market prices, wages, power, unsold stock and wholesale settlement determine its realized profit or loss.

### Remaining production gaps

- Generated homeworlds now have discovered deposits for common fertilizer and manufacturing inputs as well as smaller, varied deposits for gold, silver, rare earths and other scarce minerals. Their staffed starter mines, surface-water treatment and metal and silicon refineries replenish the everyday consumer chain. The four-month audit confirms cumulative nitrate mining and water treatment exceed the opening reserves, but it also finds household purchasing shortfalls after the first month. Uranium and thorium now have starter extraction and refining, while deuterium separation needs nuclear fusion technology. Generated homeworlds now mine hydrocarbon deposits and refine RP-1 while electrolysis and liquefaction produce liquid oxygen. Later eras can process methane or liquefy hydrogen for researched chemical drives. Silver and several non-human nutrients also rely partly on opening stock or lack a matching refinery. Neither discovered deposits nor starter cash guarantee indefinite economic balance.
- The live material pass does not enforce the gravity constraints declared by `RefinementRecipe`. Power draw is a facility-level load rather than energy proportional to every batch. Carbon dioxide and excess biomass can accumulate as stock; pollution, waste treatment and biological carbon uptake are not settled.
- Many advanced recipes have cataloged materials but no upstream producer or matching application entry in `technologies.json`. All live recipe input and output IDs now resolve to material entries, and the live smelting, biomass, consumer, luxury and electrolysis applications are named in the technology catalog. A complete technology-to-recipe mapping remains future work.

Corporate shortage investments now create owned tier-zero factory sites in the live facility list and debit the corporation only after validation. Supported matches include common ore and rare-earth mining, surface-water treatment where liquid water exists, ice mining and treatment, cultivation, refining and consumer-goods manufacturing. Each site has a 500-work-hour project advanced on daily turns and starts production at its target tier when completed. Active factories use the same payroll, inputs, production, sales and profit-tax passes as starting factories. Generic structural material bills are bought and consumed from the local hub during daily construction work. A site can progress with a partial bill and pauses when no further materials can be bought. Component-specific receipts, construction wages and cancellation refunds remain future work.

Power plants buy distinct fuel where required and deliver measured daily electricity to the local grid. Their generation, fuel costs, wages and electricity sales are recorded. Households, material facilities, mass drivers and elevators pay for metered generator electricity, and plant owners receive those credits. Each launch reserves one hour of power. Stored battery power is currently free because the stored energy has no owner ledger. Cargo terminals offer rocket launch service. Detailed grid connections, gravity restrictions, per-facility seller attribution for household retail purchases and component-specific construction receipts and full construction-cost rules below remain design work. Existing market stock and seeded industrial inputs are not attributed to a seller. Recipe IDs for refining are still distinct from technology application IDs.

### Surface-to-orbit freight (draft)

A `cargo_terminal` represents a rocket launch provider on its host body, separate from the `CommercialHub` that holds local market stock and settles purchases. It consumes locally bought RP-1 and liquid oxygen in the same provisional 28%/72% mass split as a ship's baseline chemical drive and its owner earns a handling and wear fee. A built `mass_driver` facility launches goods only, draws available grid power and earns a mass-based fee. An operational `SpaceElevator` draws grid power and can lift goods or a ship with passengers. All three have per-day payload limits shared by manual orders and assigned trade routes. The least costly available provider is chosen unless a command names one. Material purchase, propellant purchase and service fee debit the transport owner; physical goods enter a ship in orbit, and a surface ship begins a one-day ascent. Passenger seats are filled only by explicit offworld bookings, which subtract real residents and retain a manifest until arrival at the named destination surface. Launch fees credit a corporate provider's reserves or a public facility's operating balance; public elevator fees enter the owner's treasury. Drivers and elevators reserve one hour of local power per launch. Their providers prepay that electricity, the daily grid pass pays generators for delivered power and refunds power that came from unowned stored batteries or was unavailable. Corporate launch fees and settled power costs enter realized profit and tax. The old corporate fleet arbitrage path was removed because it did not settle cash.

Material bought and sold within the same planetary body has no transport charge and does not use a cargo terminal. VAT is not implemented; its rate, eligible purchases and treatment of tax paid on business inputs remain draft. Corporate profit tax is collected separately on positive realized earnings across each corporation's facilities and assigned trade routes after modeled costs and carried losses. Mining and other fleet income are not included in the tax base yet. Unpaid tax remains a corporate liability, and actual payments enter a populated local municipal balance sheet.

#### 1. Core operational variables

Every technical application inside the industrial tree processes materials according to four baseline operational layers:

- **Production facility capability:** An industry cannot be activated on a planet or space station unless a local production facility possesses an equal or higher complexity threshold than the target application's active tier.
- **Workforce efficiency multiplier:** Processing speed and resource yields are heavily scaled by the headcount and average training efficiency of the local allocated profession (`industrial_workers`, `engineers`, `farmers`, etc.). If minimum intelligence or strength thresholds inside `professions.json` are unmet, the facility experiences severe production bottlenecks.
  - Each facility will have a number of worker slots that will be filled by worker cohorts that are allocated to the target profession. 
  - Worker cohorts will split and merge with other worker cohorts of same type to optimize utilization of available production facilities. 
  - Workers will prioritize the jobs that give them the best salary. 
- **Dynamic power grid demands:** Active operations pull a static electrical energy draw ($E_{\text{draw}}$) from the local grid every turn. Running out of power trips a grid deficit, forcing the facility completely cold and freezing all item or element assembly pipelines instantly.
- **The byproduct recovery ledger:** Highly complex chemical refining loops generate secondary atomic byproducts. If a colony has researched gas capture or recycling infrastructure, these byproducts are safely routed into storage modules as valuable secondary resources rather than being lost as environmental pollution.

#### 2. Environmental and Gravity Constraints

Industrial applications are strictly bounded by localized planetary physics from the celestial database. Processing blueprints are categorized into three physical execution environments:

[ENVIRONMENTAL LOGISTICS OVERLAYS]
├── Universal (Anywhere) ──> Standard Mechanics • Independent of Gravitational State
├── Gravity-Locked ─────────> Requires Massive Fluid Settling • Local G-Force > 0.1g
└── Microgravity-Native ───> Requires Near-Zero Weight or Hard Vacuum • Local G-Force < 0.05g

- **Universal Applications:** Can be executed seamlessly on any terrestrial world, airless moon, asteroid node or orbital base frame (e.g., *Automated Assembly Lines*, *Biomass Processing*).
- **Gravity-Locked Applications:** Rely on physical weight, fluid buoyancy or atmospheric settling to process composites. If built inside an orbital space station or an asteroid outpost without heavy artificial gravity plating modules, the facility's production efficiency drops to zero (e.g., *Industrial Soil Cultivation*, *Pyrometallurgical Smelting*).
- **Microgravity-Native Applications:** Demand perfect weightlessness, molecular isolation or hard vacuums to grow pristine atomic alignments, foam heavy metals or capture free-floating elements without wall contamination. If built on a planet with a high G-force, the material structure breaks down entirely, ruining the output (e.g., *Zero-G Magnetic Refining*, *Microgravity Foundries*).

#### 3. Granular Industrial Recipe Layouts DRAFT

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
    - 112.5 kg `carbon` as reducing agent
    - 4,000 kW Electrical Power Grid Draw
- **Turn-Based Outputs:**
    - 700 kg `refined_iron`
    - 412.5 kg `carbon_dioxide_gas` (Gaseous exhaust; capture and pollution effects are not yet simulated)
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
- **Capacity tier arrays:** Facilities scale from Tier I (Local Prototype Plants) through Tier II and III (Regional Mass-Foundries) up to Tier IV+ (Continental Industrial Complexes).
- **Resource and energy multipliers:** Upgrading an industry to a larger tier multiplies its total material throughput limits, maximum worker capacity slots, and turn-based power grid draws by a flat scale factor. A Tier III facility consumes and outputs three times the base volume of a Tier I node per game turn.

#### 2. The Infrastructure Expansion Pipeline

To scale up a facility, the owning entity must execute a structural upgrade project. This loop is heavily bounded by resource sinks and operational downtime:
- **The construction cost receipt:** The current upgrade uses a generic metal, copper and silicon bill, bought from the local hub as daily work progresses. The intended application-specific receipt from the wider materials list remains Draft. Upfront credits are tracked separately from these material purchases.
- **Workforce assembly demand:** The expansion project requires an active allocation of the `engineer` profession to calibrate tolerances and guide assembly lines.
- **The production throttle penalty:** Constructing new wings or adding structural module framing creates deep physical disruptions. **While an expansion project is active, the facility's current processing output is reduced by 50%**. This temporary supply chain bottleneck forces empires and corporations to strategically time their expansions, preventing sudden resource or food deficits during active military campaigns.

#### 3. Ownership structures and operating cash

The financial flows, capital investments, and output distribution of an expanding facility are strictly governed by its underlying ownership classification:

[ INDUSTRIAL ASSET OWNERSHIP SPECTRUM ]
├── Public state property ───> Facility-funded operations • Proceeds remain with the facility
├── Private Corporate Asset ─> Corporately Funded • Profit Routed to Corporate Reserves after State Tariffs
└── Hive Consciousness Grid ─> Zero-Currency System • 100% Material Routing to State Grid (No Penalty/Wages)

##### Public state industries
- **Management framework:** New public facilities receive up to 50,000 credits of opening operating capital transferred from their owner's treasury. Their own balances pay workers, inputs, electricity, fuel and maintenance. Unpaid maintenance accumulates as a negative operating balance. Direct treasury payment of ordinary operating costs is no longer the default.
- **Profit routing:** Sale proceeds and electricity revenue stay in the facility's operating balance. Automatic distribution of surplus to the treasury is not implemented.
- **Strategic support:** The player can mark an owned public facility for support in the Planets view. At the end of each day the system may cover its loss or next-day payroll shortfall from the infrastructure and industry allocation left after public engineer and technician wages. Requests share the capped allocation proportionally. Unmarked facilities get no industrial subsidy.

##### Private corporate industries
- **Management framework:** Managed autonomously by private corporate syndicates using their aggregated liquid capital reserves. The corporate AI independently evaluates local *Market Shortcoming Scores* to initiate and fund expansions where profitable production deficits exist.
- **Local sale settlement:** A private facility receives 80% of the hub's posted retail spot price for goods it sells to the same-body hub. The hub retains the other 20% as a merchant margin. Local sales do not incur freight charges or a gross transaction tariff. VAT remains draft.
- **Profit tax and reinvestment:** The corporation pays profit tax on positive realized earnings after input costs, wages and carried losses. The remaining credits stay in its reserves for later investments. Unpaid assessed tax carries forward as a corporate liability.

##### The hive mind exception
- **Current material production:** `HIVE_GRID` facilities are owned by the hive empire rather than a corporation. Assigned workers use available physical power, consume actual local inputs and deposit output in local hub inventory up to its storage capacity. Overflow stays in facility stock. The `soldKg` counter records delivery to local inventory while `salesCredits` remains zero. No wages or material payments are created.
- **Draft unified command grid:** Hive drone cohorts would be assigned against the actual available population, while state-wide material stockpiles and direct consumption would replace the temporary local-hub inventory path. Facility expansion and logistics are not yet part of that grid.
