#### Range of influence and territorial sovereignty

An empire’s borders are fluid, scaling directly based on the physical presence and infrastructure development of its controlled assets.

- **The influence formula:** Every celestial body (planet, moon, asteroid colony) and active deep-space station generates a localized **Influence Value ($I_v$)** that radiates across the galactic coordinate grid:

Draft: Systems without population have no influence. $$I_v = \left( \log_{10}(\text{Total System Population}) * \text{Module Development Factor} \right) + \text{Military Hangar Scaling Modifier}$$

- **Territorial jurisdiction:** Any star system or orbital node falling within the combined, overlapping influence ranges of an empire’s assets is legally considered sovereign part of that empire.
- **The structural enforcement layer:** Inside its active range of influence, the state treasury automatically harvests corporate transaction tariffs, collects public taxes and deploys its `police` profession to suppress crime metrics.

#### Diplomatic relations tiers

An empire’s diplomatic posture toward a foreign state is categorized into five distinct tiers, directly dictating how their populations, economies and fleets interact when crossing paths:
- **Total war:** Military fleets automatically engage on sight. Combat ships can initiate orbital bombardment missions using planet-cracking bombs and troop transports can execute planetary ground invasions. Public and private economies are completely decoupled.
- **Cold war / blockade:** Borders are closed to public assets. State military forces can intercept and scan foreign vessels and commerce hubs deny docking access to foreign-flagged transport hulls. Private corporations risk asset seizure if caught operating inside the blockading empire's range of influence.
- **Neutral state:** The baseline standard for un-aligned empires. Foreign cargo transports and mine ships are permitted to transit through local space and dock at *Commercial Hubs*, provided they pay standard docking fees and the state's active *transaction tariff rate* on all B2B transactions.
- **Commercial alliance:** Unlocks advanced bilateral trade treaties. The host states mutually reduce their *transaction tariff rates* for each other's private corporations. Autonomous corporate entities can evaluate price differentials across both empires, actively moving cargo transport fleets across borders to resolve distant resource shortcomings for private profit.
- **Integrated federation:** Absolute logistical and military unity. Empires share a unified *Logistics Range*, allowing private corporate networks to scan both state repositories simultaneously. Friendly military vessels can dock inside a foreign ally's *Military Hangar Modules* to execute free repairs and ordnance resupply loops by drawing directly from the local station's industrial storage pools.

#### Interstellar trade treaties and nutrient conversion barriers

When executing trade agreements involving food stocks, raw elements or manufactured components, the game engine enforces strict biological limits on consumption while maintaining open compatibility for raw industrial freight:

- **The nutrient incompatibility wall:** Biological food assets cannot be traded or consumed interchangeably across differing race profiles. If a carbon-based empire attempts to export an agricultural surplus of organic proteins to a silicon-based **Silicon Core** state, the silicon population cannot consume it as nutrients.
- **Xenobiologist remediation:** To bypass this biological restriction, the food trade route must pass through an intermediate space station fitted with a specialized **Xenobiology Lab**. A staff of highly intelligent `scientists` must actively run conversion applications to synthesize the incoming organic carbon matter or gaseous elements into a globally viable crystalline mineral or material format before it enters the purchasing empire's local commercial hub.
- **The universal material exception:** Unlike food and organic nutrients, all non-biological materials, raw ores, gases and refined structural elements (e.g., `iron_ore`, `refined_silicon`, `graphene` or `superconducting_cuprates`) are universally compatible. Any race or corporation can purchase, stockpile and utilize any inorganic resource for infrastructure construction, shipyard manufacturing or industrial processing without requiring biochemical translation or xenobiological filtering.
- **Financial currency latency:** Capital transfers are bounded by the technology levels of the trading partners. In early-game eras lacking warp or quantum infrastructure, credits exchanged in international trade deals are physically loaded onto courier spacecraft. These ships must travel between coordinates to deliver the liquid wealth, rendering the cash transfer vulnerable to piracy or military blockades during transit.

#### The society structure impact on diplomacy

The active ideological alignment of an empire's population cohort directly constraints its diplomatic flexibility:

- **Individualist societies (democracies):** Highly restricted by public opinion. If a democratic government declares Total War without a severe threat provocation, citizen happiness crashes across all colonies. This triggers widespread worker strikes in metallurgy foundries and electronics matrices, forcing the government to seek peace. They receive significant organic bonuses when maintaining *Commercial Alliances*.
- **Collectivist societies (autocracies):** Complete sovereign freedom. The state player can shift diplomatic tiers instantly via a single command click without public pushback. However, breaking an alliance or executing a sudden blockade will cause massive drops in corporate trust, forcing private companies to freeze autonomous investments or withdraw their cargo transport fleets from local sectors.
- **Hive mind societies:** Natural diplomatic outsiders. Because a hive mind features no private market, citizen consumer goods or civilian happiness metrics, they are completely incapable of signing trade treaties, reducing transaction tariffs or executing B2B corporate arbitrage. They interact with individualist or collectivist empires on a purely binary spectrum: either cold, un-aligned neutrality or total, multi-system resource extermination.

#### Diplomacy view and commands

- The diplomacy view renders bilateral tiers and tariff discounts from the current `GameState` snapshot. It displays the stored mutual discount for an existing relation and falls back to the tier's default discount when no relation is stored.
- The player empire's trade and war buttons stage `ProposeDiplomaticPactCommand` and `DeclareWarCommand` through the human command queue. The UI changes only after a later snapshot refreshes from simulation state.
- A trade proposal is stored with `PENDING` status and expires 30 turns after submission. Incoming proposals to the player appear with accept and reject actions; the sender can withdraw an unresolved offer. Every non-player empire has an AI controller that evaluates proposals addressed to it and submits a resolution on a later tick. Resolve and withdraw commands validate the acting empire and ensure the proposal is still pending and unexpired.
- Accepting a mutual trade agreement sets the relation to `COMMERCIAL_ALLIANCE`; accepting a defensive pact or federation proposal sets it to `INTEGRATED_FEDERATION`. Rejecting a proposal records `REJECTED` and leaves the relation unchanged. Other terminal statuses are `WITHDRAWN` and `EXPIRED`. Proposal status and expiry turn are persisted in `GameState` and save files beginning with save version 20.
- Transitioning a pair to `TOTAL_WAR` rejects any pending proposal between them. The diplomacy view does not offer accept or reject actions for a pending proposal whose empires are already at war.
- Declaring war changes the bilateral tier to `TOTAL_WAR` and appends a `WarDeclarationRecord` with the turn, empires, casus belli ID, justification result, penalties and summary. These records are part of `GameState` and are persisted in save files beginning with save version 18.
- A supplied casus belli ID is currently interpreted as a `BORDER_FRICTION` justification with a grievance score of 50 and 10 turns remaining. Without a supplied ID, the declaration is evaluated as unprovoked aggression.
- When calculating the consequences of war, a commercial alliance is treated as an active mutual trade pact and an integrated federation as an active defensive pact. Breaking either applies a corporate trust penalty of -50. An unprovoked declaration by an `Individualist` empire applies a civilian happiness penalty of -0.40.
- While the relation remains at total war, the civilian penalty is converted to demographic stress during annual population updates. A corporate trust penalty of at least 20 points blocks autonomous corporate investment during the war. Ending the war by changing the relation tier stops these active effects; the declaration record remains in campaign history.
- The diplomacy view shows an **End war** action for the player empire when a bilateral relation is at `TOTAL_WAR`. It stages `EndWarCommand`, which validates that the war is active and returns the relation to `NEUTRAL`. The next snapshot stops active war penalties while preserving the declaration record.
- Fleets owned by empires at `TOTAL_WAR` automatically engage when idle at the same site in the same system during a daily turn. Fleet encounters do not occur during transit or at other diplomatic tiers. The current tactical processor is provisional and runs behind a replaceable resolver while its full overhaul remains planned.
- Loading troops for a planetary invasion and resolving the invasion both require `TOTAL_WAR` with the current controller of the target system. A valid invasion needs an arrived troop transport and its destination-bound deployment manifest.
    
Limiting the restriction strictly to biological food resources keeps the tactical complexity of demographic tracking sharp, while allowing raw structural commodities to flow freely to avoid grinding the galaxy's industrial supply lines to a halt.
