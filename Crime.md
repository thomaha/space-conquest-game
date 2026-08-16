#### Crime Generation and Systemic Triggers

Crime metrics are calculated dynamically during each star system's turn-update loop. Crime does not appear arbitrarily; it scales directly as a byproduct of local commercial development, population layout friction, and infrastructure configurations:

*   **Commercial Density Escalation:** Every active *Commerce Module* or planetary *Commercial Hub* increases the systemic crime baseline. The clustering of private corporate transactions and liquid credit velocity naturally attracts smuggling networks.
*   **The Logistical Footprint Factor:** Unpoliced cargo berths, *Civilian Hangar Modules*, and *Ordnance and Fuel Resupply Depots* act as massive crime amplifiers due to the presence of high-value industrial materials and military contraband.
*   **The Nutrient and Consumer Goods Shortage:** If a carbon-based population cohort with a "Diverse" nutrient spread requirement is restricted to a low-variety diet, or if local *Civilian Consumer Goods Factories* fail to meet local demand, citizen happiness metrics plummet. Low happiness directly triggers an exponential surge in local crime variables as citizens turn to illicit supply chains to secure goods.

#### The Black Market Leakage Formula

When systemic crime metrics surpass a planet or space station's security threshold, **Black Market Leakage** triggers. This mechanic forcefully intercepts civilian and corporate business-to-business (B2B) transactions, siphoning wealth away from the state.

The **State Revenue Leakage ($L_{\text{credits}}$)** per transaction turn is computed using the following systemic loop:

$$L_{\text{credits}} = \text{Total Gross Hub Transaction Value} \times \text{Transaction Tariff Rate} \times \left( \frac{\text{Local Crime Metric}}{\text{Local Police Efficiency} + \epsilon} \right)$$

*   **Tariff Evasion:** Smuggling rings use unpoliced flight corridors to move refined elements (like `refined_silicon`, `refined_silver`, or `helium_3`) directly through local hubs without logging the entries on the digital state ledgers.
*   **The Public Treasury Drain:** The credits calculated under $L_{\text{credits}}$ are completely deleted from the state's public tax harvest. The state loses the passive revenue required to fund public sector research or subsidize frontier planetary deficits, causing strategic gridlock.

#### Shadow Corporate Capital Pools and Pirate Spawning

Credits successfully siphoned through black market leakage do not disappear from the simulation; they are aggregated into autonomous **Shadow Capital Pools** tied to criminal corporate syndicates.

To maintain total hard sci-fi fidelity, pirate fleets never spawn from nothing or appear outside the physical mechanics of the galaxy. Pirate fleets obey strict material generation laws:

*   **The Internal Spawning Rule:** If an empire has not made contact with external alien civilizations, **pirate cells and rogue factions can only originate directly from your own population**. Unpoliced local criminals, disgruntled citizen cohorts, and corrupt shadow corporations are the literal source of the threat.
*   **Autonomous Criminal Investment:** Criminal syndicates evaluate system shortcomings just like legitimate private corporations. They use their accumulated black market wealth to lease local shipyard capacity or illegally acquire materials (like `steel`, `refined_iron`, and basic propulsion components) to independently construct un-registered, stealth cargo freighters or rogue mine ships.
*   **Piracy and Asset Poaching:** These shadow fleets operate on the fringes of an empire's range of influence. Rogue mine ships will illegally poach high-value materials from public or corporate-owned asteroid fields, while shadow raiders will actively ambush un-escorted *Cargo Transports* to seize their entire physical material vaults. The crew for these pirate vessels consists of citizens who have dropped out of the empire's registered workforce due to low happiness or poverty.

#### Security Countermeasures and Law Enforcement

Empires can actively suppress crime metrics and eliminate black market leakage loops by deploying targeted infrastructure and allocating trained workforces:

*   **The Police Profession Allocation:** The primary counter-measure to crime is the allocation of the `police` profession from the local population database. Stationing officers inside planetary zones or space station modules applies a direct, flat reduction to the local crime metric.
*   **The Attribute Scaling Factor:** The security efficiency of your police forces scales with the race properties mapped in the `races.json` file. A species with a high `physicalStrength` modifier (such as a *Silicon Core* or *Chitinous Hive* auxiliary force) receives a major bonus when suppressing crime inside high-density industrial and mining zones.
*   **The System Governor Stabilizer:** Appointing a *System Governor* to a planetary system provides an administrative security anchor. A governor enforces strict banking tracking and anti-smuggling maritime laws across the entire coordinate cluster, multiplying the baseline efficiency of all local police cohorts simultaneously.
*   **The Hive Mind Immunity:** Hive mind societies are **100% immune to the crime simulation loop, black market leakage, and internal pirate spawning**. Because a hive mind features no private citizens, individual income, private wallets, or autonomous corporations, there is no economic mechanism for illegal capital accumulation or tariff evasion. 100% of material extraction flows securely into the unified state grid without security overhead. They are only subject to physical piracy if an independent individualist/collectivist empire's criminal networks spill across their borders.
