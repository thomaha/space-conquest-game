#### The imperial ministry
The central government cabinet provides empire-wide ideological modifiers, economic efficiencies or tactical bonuses.
*   **The workforce pool:** Any individual citizen belonging to an eligible workforce cohort can be selected to fill a ministerial post. Candidates are drawn directly from the existing training groups mapped in the `professions.json` database.
*   **Professional background synergy:** While any profession can legally fill a post, an individual whose background matches the specific topic of the ministry grants an **Expertise Synergy Bonus**, significantly increasing the post's baseline effectiveness.
*   **The bureaucrat exception:** Due to their administrative optimization, citizens trained in the `bureaucrat` profession possess universal efficiency traits. They can fill *any* cabinet position and grant a stable baseline administrative bonus, even if the post does not match their core topic.

#### Ministerial portfolios and optimal backgrounds
The home planet's cabinet consists of specialized posts that influence distinct gameplay mechanics across the entire empire:

*   **Ministry of industry and refining**
*   *Systemic effect:* Enhances raw vein extraction rates and alloy processing speeds.
*   *Optimal matching background:* `industrial_worker`, `miner` or `bureaucrat`.
*   **Ministry of agricultural and biosphere stability**
*   *Systemic effect:* Increases food production efficiency and nutrient spread happiness modifiers.
*   *Optimal matching background:* `farmer` or `bureaucrat`.
*   **Ministry of technology and technological application**
*   *Systemic effect:* Accelerates research speed variables and lowers upgrade complexity ratings.
*   *Optimal matching background:* `scientist` or `bureaucrat`.
*   **Ministry of defense and logistics procurement**
*   *Systemic effect:* Boosts the ground combat strength of soldiers and lowers spaceframe manufacturing times.
*   *Optimal matching background:* `soldier`, `engineer` or `bureaucrat`.
*   **Ministry of finance and central commerce**
*   *Systemic effect:* Raises the collection efficiency of transaction tariff rates across all commercial hubs.
*   *Optimal matching background:* `bureaucrat`.

#### System governors and granular economy
To balance administrative overhead with deep economic simulations, governance is executed at the solar system level rather than requiring a separate leader for every individual cosmic body.
*   **The system-wide restriction:** Empires are restricted to **one appointed governor per planetary system**. A single governor oversees the political stability and infrastructure modifiers of the host star system as a single administrative territory.
*   **Granular entity tracking:** While leadership is unified at the system scale, **materials, population cohorts, veins and industries remain completely granular and separate per space entity**. The engine continues to independently simulate individual data models for each planet, moon, asteroid mining outpost and orbital space base spinning within that system.
*   **Localized application:** A governor's background bonuses apply across all space entities under their system jurisdiction. A governor with a mining background will accelerate raw element extraction for both a rocky planet's deep crustal veins and a nearby moon's regolith solar wind traps simultaneously. However, if that system consists mostly of agricultural terraformed worlds, a governor with a mining profile will yield zero baseline efficiency gains, forcing players to match governor backgrounds to a system's dominant industry type.
*   **Systemic stability and crime mitigation:** The active presence of an appointed system governor acts as an infrastructure anchor across the entire local coordinate cluster. It applies a system-wide modifier that suppresses crime metrics and boosts the baseline efficiency of `police` professions stationed on any local planet or space base.
*   **System economy:** Education, law and order, health and welfare, infrastructure and planetary militias each receive an adjustable part of the system economy.
    *  **Education** Improves the intelligence level of people from the system, notably the younger generations. High levels have positive happiness effect, low negative. Increase the number of teachers and scientists employed.
    *  **Law and order** Reduces crime and. High levels have positive happiness effect, low negative. Increases the number of police employed.
    *  **Health and welfare** Improves the health and happiness of the population, reducing disease and increasing morale. High levels have positive happiness effect, low negative. Increases the number of medics employed
    *  **Infrastructure** Improves the efficiency of all space entities in the system, including resource production, industry and science. High levels have positive happiness effect, low negative. Increases the number of engineers and technicians employed.
    *  **Planetary militias** Provides a military force for the system, which can be used to defend against invasions and maintain stability. Also functions as a training ground for the empire. Increases the number of soldiers employed and increase the number of soldiers that can be hired by the empire.

#### Ideological accession mechanics (democracy vs. autocracy)
The method by which ministers and governors ascend to power is strictly dictated by the active `societyStructure` and political alignment configuration of the empire:
*   **Democratic systems (individualist alignment)**
*   *Mechanic:* Positions are **elected** by citizen cohorts through an automated political cycle.
*   *Gameplay impact:*
    *The player has limited direct control over cabinet placement or regional system assignments. Every election cycle, local citizen age groups vote based on system-wide happiness and current material shortages. A severe systemic food shortage will cause local populations to democratically elect a `farmer` to manage that solar system. If the player forces an unwanted replacement, civilian happiness variables crash, triggering immediate workforce efficiency penalties.
    *Democracies have more efficient private sector than other systems, but are more vulnerable to systemic crises.
*   **Autocratic systems (collectivist alignment)**
*   *Mechanic:* Positions are **appointed** directly by the sovereign player from a pool of eligible candidates.
*   *Gameplay impact:* Total state command. The player can instantly rotate, dismiss or insert any trained professional into any ministerial or system governor slot without political friction or regional resistance. However, autocracies lack the organic public happiness bonuses generated by popular democratic elections, increasing the long-term dependency on local `police` presence across all space entities to maintain structural stability.
*   **The hive mind exception**
*   *Mechanic:* **Completely bypasses government.** Because a hive mind society features no individual individualism, private wallets or political factions, it does not utilize ministries, system governors, elections or appointments. The central consciousness directs all modules, space bases and worker populations across all astronomical bodies with uniform baseline efficiency, sacrificing political specialization bonuses for absolute command stability.
