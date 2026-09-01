#### Planetary power generation (facility infrastructure)

On planetary surfaces, power generation scales dynamically with the planet's unique physical attributes (atmosphere, climate and liquid water status) as mapped in the celestial entity databases. To construct and operate a power facility, the colony must possess the correct type of production facility capable of matching the design's underlying **complexity rating**:

- **Solar power facilities:** Utilizes photovoltaic arrays to harvest starlight. They require 0 fuel consumables to run. However, their electrical output scales down inversely based on the planet's orbital `distance` from the host star and is heavily suppressed by thick atmospheric densities or cloud cover variables.
- **Hydropower facilities:** Harnesses liquid water currents to spin massive kinetic turbines.
- **Wind power facilities:** Converts atmospheric currents into electrical energy.
- **Thermoelectric power facilities:** Extracts geothermal energy directly from tectonic friction inside a planet's crust. They provide steady, baseline grid power regardless of atmospheric or orbital conditions, matching well with tectonic variables on active rocky or molten worlds.
- **Nuclear fission power facilities:** Utilizes centralized ground reactors to split heavy radioactive atoms, outputting heavy baseline electrical energy independent of planetary climate or atmosphere variables.
- **Nuclear fusion power facilities:** Represents a monumental energy leap, running ground-based magnetic confinement fields to fuse light hydrogen and helium isotopes.

#### Deep-space station and starship power alternatives

Unlike planetary surface nodes, space stations and spaceships cannot exploit wind or water currents. They must balance mobile logistics between high-yield fuel consumption and passive renewable collection:

- **Solar power arrays (the fuel alternative):** Spaceships and space stations can deploy external photovoltaic wings to capture ambient starlight without consuming fuel cells. While solar power provides an exceptional baseline survival net for scouts, stations and cargo transports, it cannot generate the massive energy spikes required to fire anti-ship weaponry or recharge heavy combat shield grids. Its yield drops to zero in the interstellar void between star systems.
- **Nuclear fission power (early interstellar tier):** Utilizes controlled nuclear fission to output heavy baseline electrical energy.
- **Fusion power (advanced mid-game tier):** Houses controlled magnetic confinement fields to fuse heavy hydrogen isotopes.
- **Antimatter power (end-game hyper-tech tier):** Harnesses total mass-energy conversion by colliding matter and antimatter streams inside magnetic vacuum rings.

#### The turn-based energy balancing pass

During every game turn-update loop, the engine runs an automated energy balancing pass across the space entity or starship frame. This ledger calculates the net energy balance ($E_{\text{net}}$):

$$E_{\text{net}} = \sum \text{Facility / Module Electrical Outputs} - \sum \text{Attached Module Energy Demands}$$

- **The connected grid:** Every active installation assigned to a planet or module slotted into a hull frame possesses a static, turn-by-turn energy demand rating. Intellectual arrays like *Science Laboratories* or digital systems like *Commerce Modules* run a continuous, passive energy draw. Heavy industrial modules—such as *Metallurgy Foundries* or *Electronics Matrices*—spike their energy draw exponentially when actively processing materials or fabricating high-complexity items.
- **Batteries (energy storage linkage):** If a spaceship, base or space station is equipped with a *Batteries Module/Facility*, any positive surplus ($E_{\text{net}} > 0$) is converted into stored electricity. These battery banks act as localized buffer pools, allowing a ship to sustain operations if its primary fuel reserves run out or if an active system temporarily spikes past its base generator outputs.
- **Component disablement matrix:** When a grid deficit triggers, the engine automatically disables non-essential attached structures to protect core functionality. Industrial processing foundries go cold, freezing manufacturing and refining progress loops. Digital commerce exchanges shut down completely, pausing private corporate B2B trading and stopping all public tax harvesting.
- **The habitation emergency:** If the energy deficit is severe enough to starve local *Atmospheric Recycling Arrays*, life support metrics drop instantly. The enclosed living environment loses temperature control and gas scrubbing capability, triggering rapid population decay and severe happiness penalties across all biological worker cohorts.
- **The technician safeguard:** The rate of power module decay and the speed at which a grid deficit can be manually rerouted or repaired scales directly based on the allocation and training efficiency of the local `technician` profession. A high headcount of master technicians can prevent total system brownouts by dynamically overclocking reactors or shedding localized grid loads safely.
