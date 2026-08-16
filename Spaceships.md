#### Ship roles
##### Short-Range Fighter or Bomber
*   **Operational Scope:** Designed exclusively for localized system defense, interceptor screens, and strike runs during tactical engagements.
*   **Module Requirements:** Requires dedicated offensive weapon mounts or strike torpedo arrays. To optimize agility, it cannot equip heavy utility or hyper-dense infrastructure modules.
*   **Systemic Constraints:** Lacks long-range life support or interstellar propulsion systems. It possesses 0 sub-system transit range on the galactic map and requires a nearby *Military Hangar Module* on a space station or a *Carrier Ship* for transportation, repair, and strategic deployment.

##### Explorer
*   **Operational Scope:** Engineered for long-range deep-space reconnaissance, hyper-spectral anomaly scanning, and system profiling.
*   **Module Requirements:** Requires advanced scanning arrays and specialized sensor modules.
*   **Systemic Constraints:** Receives a distinct scanner broadcast range bonus on the galactic map. Its high-precision electronics must be shielded from environmental radiation variables using high-purity refined metals like platinum or gold.

##### Troop Transport
*   **Operational Scope:** Functions as the primary tactical vehicle for moving ground forces, executing planetary invasions, and reinforcing contested colonies.
*   **Module Requirements:** Must equip at least one *Cryogenic Troop Transport Bay*.
*   **Systemic Constraints:** The total deployment capacity is strictly limited by the sum of its equipped transport bays. Its heavy dry mass profile makes it highly vulnerable to defensive surface batteries during orbital descent vectors.

##### Cargo Transport
*   **Operational Scope:** The logistical lifeblood of the empire, moving massive volume caches of materials across solar networks.
*   **Module Requirements:** Must equip at least one *Bulk Cargo Vault*.
*   **Systemic Constraints:** Private corporations actively buy custom blueprints matching this role to solve system shortcomings. Fully loaded hulls experience severe sub-light acceleration penalties and exponentially higher planetary launch taxes due to the weight-scaling gravity calculations.

##### Colony Ship
*   **Operational Scope:** A massive, single-use ark hull built to expand the empire’s borders by seeding new populations on virgin worlds.
*   **Module Requirements:** Requires a *Planetary Colonization Module*.
*   **Systemic Constraints:** Due to the immense size slot profile of the colonization structure, these ships are slow-moving, high-mass targets. Upon arriving at a valid celestial body, the module is permanently consumed to unlock a new planetary population registry.

##### Mining Ship
*   **Operational Scope:** Extracts raw ores and volatile compounds directly from deep-space asteroid fields, comets, and mineral-rich planetary rings.
*   **Module Requirements:** Requires heavy electromagnetic extraction rays or mining laser arrays.
*   **Systemic Constraints:** Can harvest resources continuously without relying on planetary veins. Because it stores raw, unrefined ores (`iron_ore`, `copper_ore`), it relies on nearby *Cargo Transports* or its own limited internal lockers to haul the weight to a refinery.

##### Escort
*   **Operational Scope:** A light-to-medium combat platform optimized for rapid positioning, point-defense screening, and convoy protection.
*   **Module Requirements:** Must equip rapid-tracking *Point-Defense Laser Grids* and agile sub-light propulsion systems.
*   **Systemic Constraints:** Highly effective at neutralizing incoming enemy bomber formations, physical torpedoes, and particle streams. They lack the heavy structural frame slots required to carry anti-ship orbital batteries.

##### Passenger Transport
*   **Operational Scope:** Ferries active workforce populations, specialists, and migrant cohorts between established planetary colonies and space stations.
*   **Module Requirements:** Must equip at least one *Biometric Passenger Cabin Array*.
*   **Systemic Constraints:** Because passengers remain conscious during transit, this role requires matching food storage modules to feed the population every game turn, adding a continuous weight-depletion dynamic to the travel path.

##### Combat Ship
*   **Operational Scope:** The heavy frontline tactical hammer of the fleet, engineered to engage enemy capital warships, destroy space bases, and enforce blockades.
*   **Module Requirements:** Requires heavy anti-ship weapon mounts (such as spinal mass drivers or continuous lasers) alongside layers of *Ablative Deflection Plating* or *Shield Grids*.
*   **Systemic Constraints:** Built for maximum damage output and structural survivability. Their intense dry mass and weapon power draw completely isolate them from carrying commercial cargo vaults or civilian modules.

##### Carrier Ship
*   **Operational Scope:** Operates as a mobile orbital airbase, deploying and supporting swarms of short-range fighters or bombers directly into active combat theaters.
*   **Module Requirements:** Must equip an internal military hangar array or ship deployment launch bays.
*   **Systemic Constraints:** Functions as the vital logistics anchor for short-range combat crafts. When a fighter is damaged or destroyed during a fleet engagement, the carrier can rebuild or service the craft over time by pulling refined materials directly from its internal cargo bays.

##### Construction Ship
*   **Operational Scope:** Engineered to deploy, assemble, and repair macro-structures, orbital installations, deep space bases, and orbital space stations.
*   **Module Requirements:** Requires heavy structural assembly bays, heavy robotic crane arrays, or nanite fabrication projectors.
*   **Systemic Constraints:** Functions as the vital physical catalyst for space-based empire expansion. To build a space station or an out-post structure, the construction ship must travel to the target coordinates hauling the required foundation modules or raw refined materials inside its internal holds. While deploying a structure, the vessel is locked into a fixed position, rendering it highly vulnerable to hostile fleet interceptors or long-range scanner discovery.

#### Ship modules
##### Baseline fission propulsion drive
*   **Description:** Utilizes controlled nuclear fission to superheat a gas propellant, providing steady, long-range propulsion for early interstellar vessels.
*   **Primary Material Inputs:** `steel`, `refined_iron`, and `refined_lead` (for reactor core containment shielding).
*   **Fuel Consumption:** Consumes `refined_uranium` or `refined_thorium` fuel allocations per transit leg.
*   **Systemic Factors:** A low-complexity (Level 4), high-mass engine variant. It is incredibly cheap to manufacture, but its heavy dry mass drastically increases the ship's planetary blast-off gravity tax.
*   **Workforce Requirement:** Operated and maintained by the `technician` profession.

##### Advanced fusion propulsion drive
*   **Description:** Leverages high-energy magnetic fields to fuse hydrogen isotopes, unlocking exceptional thrust velocities and fuel efficiency.
*   **Primary Material Inputs:** `inconel_alloy`, `superconducting_cuprates`, and `refined_neodymium` (for magnetic plasma constriction nozzles).
*   **Fuel Consumption:** Requires a continuous feed of high-density `fusion_fuel_pellets` to execute system transits.
*   **Systemic Factors:** A high-complexity (Level 8) propulsion unit. It reduces overall transit travel times across solar systems and possesses a significantly lower dry mass footprint than fission drives, optimizing the ship for planetary launches.
*   **Workforce Requirement:** Supervised and calibrated exclusively by the `engineer` profession.

##### Spacetime deformation warp drive
*   **Description:** Compresses the fabric of space in front of the vehicle and expands it behind, allowing the ship to travel between adjacent star systems inside a localized warp bubble.
*   **Primary Material Inputs:** `graphene` (for gravitational stress dissipation), `titanium_aluminide`, and hyper-advanced electronic control processors.
*   **Systemic Factors:** The ultimate late-game FTL mobility module (Complexity Level 9+). Activating the drive drains up to 80% of the ship's power grid capacity upon exiting warp space, leaving the vessel temporarily vulnerable to scanners and ambush unless optimized through efficiency research paths.
*   **Workforce Requirement:** Requires a specialized dual-allocation of the `engineer` and `scientist` professions to map subspace paths.

##### Continuous Beam Laser Mount
*   **Description:** Focuses highly concentrated light photons to melt enemy armor plating and slice through external structural components over sustained contact windows.
*   **Primary Material Inputs:** `refined_platinum` (for hyper-precise focusing lenses), `graphene` (for massive thermal dissipation arrays), and `refined_silver` (for high-speed optical mirrors).
*   **Systemic Factors:** Fires instantly across medium ranges. It cannot be dodged or intercepted by point-defense systems. However, its effectiveness drops off dramatically inside planetary atmospheres due to photon scattering, and it is easily dissipated by active energy shield grids.
*   **Workforce Requirement:** Controlled during tactical combat alerts by the `soldier` or `ship_crew` professions.

##### Kinetic Mass Driver Mount
*   **Description:** Employs parallel electromagnetic rail tracks to accelerate heavy, unguided solid slugs to hyper-velocities, shattering enemy shields through sheer kinetic momentum.
*   **Primary Material Inputs:** `refined_tungsten` (for high-density armor-piercing kinetic slugs), `superconducting_cuprates` (for electromagnetic rail charging), and heavy `steel` rail beds.
*   **Systemic Factors:** Operates at extreme long ranges. It deals devastating burst damage directly to energy shield grids but has a slow projectile travel time, allowing small, high-evasion tactical fighters or nimble scouts to dodge the slug at maximum distances.
*   **Workforce Requirement:** Calibrated by the `engineer` profession and operated by the `soldier` profession.

##### Point-Defense Laser Grid
*   **Description:** Rapid-tracking, low-yield laser arrays designed to intercept and vaporize incoming physical torpedoes, missiles, and small enemy bomber wings before impact.
*   **Primary Material Inputs:** `refined_silicon` (for target-tracking computers), `refined_copper`, and `refined_aluminum` (for rapid turret rotation joints).
*   **Systemic Factors:** Functions as an automated tactical layer. It deals no damage to capital warships but selectively nullifies incoming projectile payloads and enemy strike crafts. It demands a constant, minor power draw from the ship's energy grid while active.
*   **Workforce Requirement:** Automated by local electronics, requiring overall system maintenance from the `technician` profession.

##### Ablative Deflection Plating
*   **Description:** Layers of ultra-dense composite ceramics and structured carbon networks designed to absorb the thermal shock of lasers and deflect small mass driver fragments.
*   **Primary Material Inputs:** `silicon_carbide` (outer protective tile array), `carbon_nanotubes` (internal tensile structure), and `refined_lead` (for ambient cosmic radiation blocking).
*   **Systemic Factors:** Provides permanent passive structural protection to the ship's hull variables. It requires 0 energy from the ship's power grid to function, but its immense weight adds significantly to the ship's dry mass, heavily penalizing the vessel's sub-light acceleration curves and increasing its planetary launch cost.
*   **Workforce Requirement:** Patched and repaired in harbor zones by the `industrial_worker` or `engineer` professions.

##### Bulk Cargo Vault
*   **Size Profile:** 12 slots (Heavy Infrastructure)
*   **Dry Mass:** 25,000 kg
*   **Primary Material Inputs:** `steel`, `refined_aluminum`, and `aerogel` (for temperature-regulated storage seals).
*   **Capacity:** 150,000 kg of bulk inventory capacity.
*   **Systemic Factors:** Sets the absolute cargo limit for carrying raw ores, refined metals, processed gasses, or consumer goods. Its high mass when fully loaded directly adds to the launch gravity calculation, meaning a fully laden cargo hauler will pay an exponentially higher credit-per-kilogram tax to escape a planetary surface than an empty one.
*   **Workforce Requirement:** Maintained by the `technician` profession.

##### Cryogenic Troop Transport Bay
*   **Size Profile:** 8 slots (Medium Utility)
*   **Dry Mass:** 12,000 kg
*   **Primary Material Inputs:** `bio_polymers`, `refined_lead` (for radiation safety during deep-space transit), and `water_ice`.
*   **Capacity:** 5,000 active ground units (`soldier` or `police` professions) held in biometric stasis.
*   **Systemic Factors:** Allows the ship to transport planetary invasion or colony defense forces. Because troops are held in stasis chambers, their biological needs are frozen: they consume 0 food, 0 water, and require 0 luxury consumer goods during transit, removing any weight-scaling life support bottlenecks.
*   **Workforce Requirement:** Monitored and calibrated by the `medic` profession.

##### Planetary Colonization Module
*   **Size Profile:** 30 slots (Super-Heavy Macro-Structure)
*   **Dry Mass:** 95,000 kg
*   **Primary Material Inputs:** `bio_polymers`, `nitrates`, `phosphates`, `potash` (baseline agricultural seed stocks), `refined_silicon`, and basic `steel` framing.
*   **Capacity:** 50,000 civilian population cohorts, complete with primitive surface-shelter infrastructure kits and baseline soil cultivation materials.
*   **Systemic Factors:** A hyper-dense, single-use module designed to establish a new colony on a terrestrial or barren frontier world. Upon arrival at a destination planet, the module is permanently detached from the ship structure and landed on the surface, consuming 1 empty planetary surface area slot and converting the payload directly into a new planetary population database entry.
*   **Workforce Requirement:** Governed during transit by the `bureaucrats` profession to manage demographic records.

##### Biometric Passenger Cabin Array
*   **Size Profile:** 6 slots (Light Utility)
*   **Dry Mass:** 8,000 kg
*   **Primary Material Inputs:** `bio_polymers`, `refined_aluminum`, and `oxygen_gas`.
*   **Capacity:** 1,200 conscious population cohorts (active workforce, migrants, or specialists).
*   **Systemic Factors:** Used to ferry active workers between established colonies, space bases, or space stations. Because passengers are fully conscious, they remain active economic consumers: they continuously deplete the ship's stored food and oxygen supplies every transit turn, requiring matching storage arrays for long trips.
*   **Workforce Requirement:** Managed by the `quartermaster` or `medic` professions.

##### Electromagnetic RCS Thruster Array
*   **Size Profile:** 2 slots (Light Utility)
*   **Dry Mass:** 1,500 kg
*   **Primary Material Inputs:** `refined_copper`, `refined_aluminum` (for rapid directional articulation joints), and basic `steel` nozzle housings.
*   **Consumables:** Consumes compressed `hydrogen_gas` or compressed off-gasses harvested from `chemical and gas processing` operations.
*   **Systemic Factors:** Provides baseline sub-light rotational torque and lateral thrust vectors. It is highly effective in the vacuum of space for light and medium starframes. Its low thrust yield means its efficiency drops sharply inside dense planetary atmospheres, making it difficult for heavy, fully loaded ships to arrest their descent.
*   **Workforce Requirement:** Monitored and calibrated by the `technician` profession.

##### High-Impulse Magnetoplasmadynamic (MPD) Thruster
*   **Size Profile:** 4 slots (Medium Utility)
*   **Dry Mass:** 4,500 kg
*   **Primary Material Inputs:** `inconel_alloy`, `superconducting_cuprates` (for electromagnetic ionization fields), and `refined_neodymium` (for magnetic plasma acceleration tracks).
*   **Consumables:** Utilizes ionized gas propellant loops derived directly from processed `ammonia_ice` or `methane_ice`.
*   **Systemic Factors:** A high-complexity (Level 7) maneuvering module. It generates extreme structural impulse vectors, allowing even heavy capital warships or dense cargo transports to execute tight tactical evasions and change headings rapidly in deep space. It produces enough raw force to actively counter heavy gravity wells during planetary landing runs.
*   **Workforce Requirement:** Supervised and calibrated exclusively by the `engineer` profession.

### 🛸 Modular Hull Architecture and User-Defined Roles

Instead of selecting from rigid, predefined hull sizes (such as "Corvette" or "Cruiser"), empires construct spaceships by building custom configurations from scratch. The user dictates the ship's scale, classification, and tactical role entirely through their choice of module combinations.

### Space Station Modules
Every module constructed on a space station requires a specific combination of refined materials, demands constant electrical power, and relies on distinct professions to maintain peak operational efficiency.

#### Control Module
*   **Size Profile:** 6 slots (Medium Infrastructure)
*   **Dry Mass:** 12,000 kg
*   **Primary Material Inputs:** `refined_silicon`, `refined_silver`, `refined_gold`, and `titanium_aluminide`.
*   **Systemic Factors:** Functions as an absolute baseline prerequisite; a space station structure cannot initialize or update its processing loops without a control module online. It determines the maximum number of individual modules the station can safely connect to its shared network before system automation degradation sets in.
*   **Integrated Systems:** Natively houses the space station's core **Sensor and Scanning Arrays**. The local logistics range, threat detection sweep speed, and hidden celestial node mapping efficiency scale directly based on the electronic complexity tier of this module.
*   **Workforce Requirement:** Command, operations, and sensor monitoring are managed by a combination of the `bureaucrat` and `technician` professions.

#### Multi-Race Habitation Module
*   **Size Profile:** 8 slots (Medium Infrastructure)
*   **Dry Mass:** 15,000 kg
*   **Primary Material Inputs:** `steel`, `bio_polymers`, `refined_lead`, and `oxygen_gas`.
*   **Systemic Factors:** Dictates the maximum population capacity of the station. Multiple distinct races can live together within the same quarters provided they share compatible breathing atmosphere requirements. Incompatible species utilize atmospheric suits to execute transactions or attend diplomatic meetings.
*   **Workforce Requirement:** Constantly monitored and optimized by the `medic` profession to manage health variables and the `bureaucrat` profession to administer life-support resource allocations.

#### Power Module
*   **Size Profile:** 10 slots (Heavy Infrastructure)
*   **Dry Mass:** 22,000 kg
*   **Primary Material Inputs:** Early-game configurations require `refined_iron` and `industrial_ceramics`. Late-game arrays demand `inconel_alloy` and `superconducting_cuprates` to contain singularity or fusion systems.
*   **Fuel Consumption:** Consumes `fusion_fuel_pellets` or `refined_uranium` depending on the active technology level.
*   **Workforce Requirement:** Operated and maintained exclusively by the `technician` profession.

#### Storage Modules (Categorized by Type)
*   **Size Profile:** 12 slots (Heavy Infrastructure)
*   **Dry Mass:** 25,000 kg
*   **Primary Material Inputs:** Built primarily from heavy `steel` and insulated with `aerogel` to maintain strict temperature control for cryogenic gasses or biological foods.
*   **Systemic Factors:** Sets the absolute volume and weight limit (measured in kilograms) for item inventories held on the station. Sub-types include power storage (battery banks), food storage, ore storage, resource storage, and industry storage.
*   **Workforce Requirement:** Administered and inventory-tracked by the `bureaucrat` profession.

#### Commerce Module
*   **Size Profile:** 8 slots (Medium Infrastructure)
*   **Dry Mass:** 14,000 kg
*   **Primary Material Inputs:** `refined_silver`, `refined_silicon`, and `titanium_aluminide`.
*   **Systemic Factors:** Requires a *Civilian Hangar Module* to be present on the station to function. Captures transaction fees from civilian purchases and corporate B2B trading, funneling credits directly into the state treasury based on the active transaction tariff rate.
*   **Workforce Requirement:** Requires an active headcount of `bureaucrat` professions to calculate tax tariffs and `technician` professions to maintain localized digital data grids.

#### Logistics Module
*   **Size Profile:** 6 slots (Medium Utility)
*   **Dry Mass:** 10,000 kg
*   **Primary Material Inputs:** `refined_silicon`, `refined_copper`, and `graphene`.
*   **Systemic Factors:** Extends the station's logistics range across the galactic map, allowing private corporations to scan its inventories and identify local material shortcomings much faster.
*   **Workforce Requirement:** Optimized and managed by the `bureaucrat` profession.

#### Civilian Hangar Module
*   **Size Profile:** 12 slots (Heavy Infrastructure)
*   **Dry Mass:** 28,000 kg
*   **Primary Material Inputs:** `steel`, `refined_aluminum`, and `refined_copper`.
*   **Systemic Factors:** Functions as an absolute technical prerequisite for the *Commerce Module*. It expands local storage capacity linkages and reduces warehouse delay penalties for private corporate entities trading within the logistics range.
*   **Workforce Requirement:** Run and managed by the `technician` profession to oversee landing guidance arrays.

#### Military Hangar Module
*   **Size Profile:** 16 slots (Heavy Capital Infrastructure)
*   **Dry Mass:** 45,000 kg
*   **Primary Material Inputs:** `titanium_aluminide`, `silicon_carbide`, and `refined_neodymium`.
*   **Systemic Factors:** Allows the station to act as a military fleet base. Warships docked inside this module repair hull damage and replenish weapon munitions over time, utilizing resources directly from the station's industrial storage reserves. It does not provide any docking or trading infrastructure for private corporate shipping lanes.
*   **Workforce Requirement:** Maintained by the `technician` profession and guarded by an active headcount of the `soldier` profession.

#### Ordnance and Fuel Resupply Depot
*   **Size Profile:** 8 slots (Medium Utility)
*   **Dry Mass:** 18,000 kg
*   **Primary Material Inputs:** Heavy `steel` plating, `refined_lead`, and `inconel_alloy`.
*   **Consumables:** Consumes stored `fusion_fuel_pellets`, `deuterium_gas`, or raw ammunition packages produced by local manufacturing lines.
*   **Systemic Factors:** Drastically reduces the time required to re-arm and re-fuel combat ships, mine ships, or long-range explorers after an active deployment. It increases the local explosion hazard profile of the station and raises local crime metrics if left unpoliced.
*   **Workforce Requirement:** Managed by the `bureaucrat` and `technician` professions.

#### Tactical Strike Wing Bay
*   **Size Profile:** 14 slots (Heavy Capital Infrastructure)
*   **Dry Mass:** 35,000 kg
*   **Primary Material Inputs:** `titanium_aluminide`, `refined_aluminum`, and `refined_silver`.
*   **Systemic Factors:** Deploys autonomous screens of small, high-evasion combat crafts during an engagement. Fighters intercept incoming enemy bomber wings, while bombers launch heavy torpedo payloads to systematically dismantle enemy warship shield grids. Striking craft are rebuilt and replaced over time using the station's industrial storage assets.
*   **Workforce Requirement:** Maintained by the `technician` profession and piloted by an active headcount of the `soldier` or `ship_crew` professions.

#### Food Production Module (Hydroponic / Organic Feed)
*   **Size Profile:** 8 slots (Medium Production)
*   **Dry Mass:** 11,000 kg
*   **Primary Material Inputs:** Built from `bio_polymers` and `refined_aluminum`.
*   **Consumables:** Consumes raw `nitrates`, `phosphates`, `potash`, and `water_ice` to maintain production outputs.
*   **Workforce Requirement:** Run by the `farmer` profession to maximize crop yields and nutrient spread diversity.

#### Mineral Synthesis Module (Lithotrophic / Silicon Feed)
*   **Size Profile:** 8 slots (Medium Production)
*   **Dry Mass:** 13,000 kg
*   **Primary Material Inputs:** `silicates`, `limestone`, `refined_manganese`, and `refined_iron`.
*   **Consumables:** Consumes raw `potash` and `phosphates` as chemical catalysts to break down heavy minerals into a digestible form.
*   **Workforce Requirement:** Operated and optimized by the `industrial_worker` profession.

#### Gas Condensation and Enrichment Module (Gaseous Feed)
*   **Size Profile:** 8 slots (Medium Production)
*   **Dry Mass:** 12,500 kg
*   **Primary Material Inputs:** `refined_aluminum`, `refined_copper`, and `refined_silicon`.
*   **Consumables:** Consumes bulk feeds of raw `methane_ice`, `ammonia_ice`, `hydrogen_gas`, and `nitrogen_gas` to blend the required atmospheric diets.
*   **Workforce Requirement:** Run by the `technician` profession to monitor pressure seals and blend ratios.

#### Bioreactor Tissue Printing Module (Cellular Agriculture)
*   **Size Profile:** 12 slots (Heavy Production Matrix)
*   **Dry Mass:** 24,000 kg
*   **Primary Material Inputs:** `bio_polymers`, `refined_silver`, and high-complexity electronic nanofabrication matrices.
*   **Systemic Factors:** A high-complexity module (Level 7+) that can be researched and re-tuned via *Xenobiologist* rules to print complex diets for *any* compatible race, maximizing nutrient spread requirements.
*   **Workforce Requirement:** Requires a highly trained headcount of the `medic` or `scientist` professions to prevent cellular breakdown.

#### Metallurgy Foundry Module
*   **Size Profile:** 16 slots (Heavy Macro-Industrial)
*   **Dry Mass:** 48,000 kg
*   **Primary Material Inputs:** `refined_iron`, `refined_nickel`, `refined_chromium`, and `refined_tungsten`.
*   **Systemic Factors:** Directly processes metalloid and elemental combinations into composite blocks (like `inconel_alloy` or `steel`).
*   **Workforce Requirement:** Operated by the `industrial_worker` profession.

#### Chemical and Gas Processing Module
*   **Size Profile:** 12 slots (Heavy Production Matrix)
*   **Dry Mass:** 32,000 kg
*   **Primary Material Inputs:** `refined_aluminum`, `refined_lead`, and `refined_sulfur`.
*   **Consumables:** Consumes raw `water_ice`, `methane_ice`, `ammonia_ice`, and `uranium_ore`.
*   **Workforce Requirement:** Maintained and run by the `technician` profession to monitor high-pressure chemical reactions.

#### Civilian Consumer Goods Factory
*   **Size Profile:** 12 slots (Heavy Macro-Industrial)
*   **Dry Mass:** 38,000 kg
*   **Primary Material Inputs:** `bio_polymers`, `refined_aluminum`, `refined_copper`, and `refined_silicon`.
*   **Systemic Factors:** Directly fuels the *Private Economy* loops of individualist and collectivist societies. Citizens purchase these goods using their private salaries at local *Commercial Hubs*, directly multiplying local colony happiness metrics and mitigating crime growth. This module produces 0 value in a *Hive Mind* society.
*   **Workforce Requirement:** Run and optimized by the `industrial_worker` profession.

#### Advanced Component Assembly Line
*   **Size Profile:** 14 slots (Heavy Macro-Industrial)
*   **Dry Mass:** 42,000 kg
*   **Primary Material Inputs:** Heavy `steel` framing, `graphene`, and `refined_neodymium`.
*   **Systemic Factors:** Functions as the primary assembly hub for spaceship and space station module items. Efficiency is capped by the lower of local engineering and worker training levels.
*   **Workforce Requirement:** Supervised and calibrated exclusively by the `engineer` profession.

#### Weapon Systems Foundry
*   **Size Profile:** 14 slots (Heavy Macro-Industrial)
*   **Dry Mass:** 46,000 kg
*   **Primary Material Inputs:** `steel`, `refined_lead`, `refined_tungsten`, and `refined_sulfur`.
*   **Consumables:** Requires a continuous supply of `chemical and gas processing` byproducts to stabilize solid explosives and propellant mixtures.
*   **Systemic Factors:** Directly outputs tactical equipment for planetary ground defense forces, raising the operational combat strength of the `soldier` profession. This module functions as a prerequisite for assembling orbital drop pods or ground fortification nodes.
*   **Workforce Requirement:** Supervised and calibrated exclusively by the `engineer` profession, utilizing `industrial_workers` for general assembly line operations.

#### Advanced Energy Weapon Lab
*   **Size Profile:** 12 slots (Heavy Production Matrix)
*   **Dry Mass:** 30,000 kg
*   **Primary Material Inputs:** `graphene`, `titanium_aluminide`, `refined_neodymium`, and `superconducting_cuprates`.
*   **Systemic Factors:** Functions as a highly specialized, high-complexity assembly matrix. It takes technologies discovered in the *Particle Weapons* and *Energy Weapons* branches and physically builds ship modules (like continuous beam lasers or mass driver mounts). Production output limits are directly bounded by the empire's current nanotechnology tech tier.
*   **Workforce Requirement:** Run by a dual-allocation of the `engineer` and `scientist` professions to calibrate electromagnetic containment fields.

#### Orbital Shipyard Assembly Grid
*   **Size Profile:** 24 slots (Mega-Engineering Framework)
*   **Dry Mass:** 85,000 kg
*   **Primary Material Inputs:** `refined_iron`, `titanium_aluminide`, and `refined_neodymium`.
*   **Systemic Factors:** Restricts ship construction by scale and complexity. It can only assemble user-designed blueprints that do not exceed **50 total module slots** and a maximum dry mass threshold. The construction speed is modified by the empire's active *Advanced Component Assembly Line* tech level. Private corporations use this module to construct their autonomously owned cargo transport and mine ship fleets.
*   **Workforce Requirement:** Calibrated and supervised by the `engineer` profession, utilizing `industrial_workers` for heavy structural welding loops.

#### Capital Mega-Engineering Slipway
*   **Size Profile:** 60 slots (Super-Heavy Structural Matrix)
*   **Dry Mass:** 280,000 kg
*   **Primary Material Inputs:** `silicon_carbide`, `graphene`, and advanced micro-thruster positioning grids.
*   **Systemic Factors:** Unlocks the physical capability to construct heavy class vessels (exceeding **50 to 150+ total module slots**). Because these massive hulls possess extreme dry mass profiles that carry crippling launch tax penalties on planetary surfaces, this orbital module functions as the primary, cost-effective manufacturing hub for an interstellar empire's capital fleet.
*   **Workforce Requirement:** Requires a high density of the `engineer` profession to manage the complex structural tolerances of heavy starframes.

#### Theoretical Physics Lab
*   **Size Profile:** 10 slots (Heavy Research Array)
*   **Dry Mass:** 21,000 kg
*   **Primary Material Inputs:** `superconducting_cuprates`, `refined_platinum`, and `refined_gold`.
*   **Systemic Factors:** Generates specialized research progress points dedicated explicitly to the *Warp*, *Gravitational Engineering*, and *Particle Weapons* technology branches.
*   **Workforce Requirement:** Requires a dedicated staff of high-intelligence individuals from the `scientist` profession.

#### Material Science Lab
*   **Size Profile:** 10 slots (Heavy Research Array)
*   **Dry Mass:** 19,500 kg
*   **Primary Material Inputs:** `silicon_carbide`, `aerogel`, and specialized laser optics components.
*   **Systemic Factors:** Accelerates breakthrough and optimization discoveries across all *Metallurgy*, *Manufacturing*, and *Nanotechnology* applications.
*   **Workforce Requirement:** Staffed by the `scientist` profession, with minor training speed bonuses if an `engineer` is co-allocated.

#### Xenobiology Lab
*   **Size Profile:** 12 slots (Heavy Research Array)
*   **Dry Mass:** 24,500 kg
*   **Primary Material Inputs:** `bio_polymers`, `refined_silver`, and advanced environmental simulation capsules.
*   **Systemic Factors:** Unlocks multi-race nutrient spread formulas and alternative agricultural modules. Directs food trade translation through international trade treaties. Directly raises the overall effectiveness of the `medic` profession.
*   **Workforce Requirement:** Requires the highest intelligence tier of the `scientist` profession to prevent dangerous biological containment failures.

#### Shield Generator Grid Module
*   **Size Profile:** 14 slots (Heavy Capital Infrastructure)
*   **Dry Mass:** 40,000 kg
*   **Primary Material Inputs:** `superconducting_cuprates`, `graphene`, and `refined_gold`.
*   **Systemic Factors:** Provides the station with an energy shield capacity layer. When hit, the module dissipates damage instantly at the cost of high electrical energy consumption, completely nullifying low-yield mass driver slugs and laser fire until depleted.
*   **Workforce Requirement:** Managed and calibrated by the `engineer` profession to prevent frequency modulation collapses.

#### Defensive Weapon Platform Module
*   **Size Profile:** 8 slots (Medium Tactical Array)
*   **Dry Mass:** 22,000 kg
*   **Primary Material Inputs:** `refined_tungsten`, `refined_silver`, and `inconel_alloy`.
*   **Systemic Factors:** Operates as the station's primary point-defense layer. It actively shoots down incoming physical projectiles and deals defensive damage to hostile capital ships within its tactical tracking range.
*   **Workforce Requirement:** Calibrated by the `engineer` profession and operated during combat alerts by the `soldier` profession.

#### Heavy Orbital Battery Module
*   **Size Profile:** 20 slots (Mega-Engineering Framework)
*   **Dry Mass:** 95,000 kg
*   **Primary Material Inputs:** `refined_tungsten`, `graphene`, `superconducting_cuprates`, and `inconel_alloy`.
*   **Systemic Factors:** Serves as the station's heavy anti-ship combat layer. It bypasses small interceptor screens to deal massive structural and shield damage directly to hostile capital warships within the system. Its firing rate is capped by the station's available energy grid output per combat round.
*   **Workforce Requirement:** Calibrated by the `engineer` profession and commanded during combat alerts by the `soldier` profession.

### Space Station Hull Armor Options
Unlike customizable spaceship modules or internal station compartments, Station Armor is not a module slot. Instead, it is treated as an external plating overlay applied directly to the station’s structural starframe. Adding armor layers expands the station's passive hit points and particle resistance, but permanently increases its overall Structural Dry Mass.

[STATION DAMAGE MITIGATION SPECTRUM]
Incoming Fire ──> Shield Generator (Energy Drain) ──> Hull Armor (Passive Reduction) ──> Structural Collapse
Global Plating Overlay: Armor layers scale their material costs directly with the total number of connected module slots. As a station expands by adding production foundries or laboratories, the surface area increases, requiring a proportional expenditure of refined materials to maintain full armor coverage.Passive Damage Mitigation: Armor acts as the final passive defense layer before a station suffers permanent structural collapse. While shields consume active electrical power from Power Modules to absorb hits, armor requires zero energy grid allocation. It relies purely on material properties to naturally absorb or deflect kinetic energy from mass driver slugs and dissipate thermal energy from continuous beam lasers.Mass and Engineering Logistics: Applying a thicker or higher-tier armor plating increases the space station’s total Structural Dry Mass according to the weight characteristics of the researched material. This added weight penalizes the station's sub-light handling properties, heavily inflating the universal credit cost or thruster fuel requirements if a Construction Ship attempts to tow, position, or relocate the station structure across coordinates.Technology Tree Integration: The specific material compositions, manufacturing costs, and protective coefficients of armor are governed entirely by applications within the Metallurgy and Advanced Materials technology branches. The station layout simply provides the interface to apply the empire's highest researched plating tier to the starframe shell.

#### Heavy Composite Steel Armor Plating
*   **Application Method:** Global starframe overlay (scales with the total number of connected module slots).
*   **Primary Material Inputs:** 80% `steel` + 20% `refined_manganese`.
*   **Systemic Factors:** A low-complexity (Level 3) basic passive defense layer. It significantly increases the space station's structural damage threshold against mass driver slugs and flak shrapnel. However, its heavy mass composition drastically inflates the station's total dry mass, making it significantly more expensive for *Construction Ships* to position or tow the layout.

#### Ablative Ceramic Thermal Plating
*   **Application Method:** Global starframe overlay (scales with the total number of connected module slots).
*   **Primary Material Inputs:** 70% `silicon_carbide` + 30% `refined_lead` (for radiation dampening).
*   **Systemic Factors:** A medium-complexity (Level 6) defense solution engineered explicitly to counter energy weapons. The composite tiles absorb and dissipate the extreme thermal shock of continuous-beam lasers and plasma pulses, completely melting away outward damage loops. It features a moderately lighter dry mass profile than steel armor.

#### Crystalline Nanotube Armor Matrix
*   **Application Method:** Global starframe overlay (scales with the total number of connected module slots).
*   **Primary Material Inputs:** 100% `carbon_nanotubes` or `graphene` structural weaves.
*   **Systemic Factors:** The ultimate high-complexity (Level 8+) macro-engineering hull reinforcement. It multiplies the station's absolute structural integrity against both kinetic momentum and thermal energy weapons simultaneously. Due to the incredible strength-to-weight ratio of carbon lattices, it adds almost zero mass to the station frame, keeping the structure's positioning costs fully optimized.
