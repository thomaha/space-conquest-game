#### Design and implementation status
Generated campaigns begin with mixed state-owned and corporate industry on each empire homeworld, opening credits for empires, corporations and population households and stocked hubs on populated colonies. These opening balances are seeded without processing a day. On later daily turns material facilities pay funded workers, pay for powered shifts, buy available inputs and sell produced goods to the local hub using its available cash. The owner receives the sale payment and unsold output stays in facility stock. Power plants buy distinct fuels where required and sell measured electricity to households and industries on their local grid. Cargo terminals are intended for surface-to-orbit handling but do not yet provide a metered service; interplanetary fleet and route trading is separate and its final scope is undecided.

Within one planetary body, local trade has no transport charge or gross industry-sale tariff. VAT remains unimplemented; its rate, input-credit treatment and taxable goods are not set. The live corporate profit-tax pass sums realized earnings and costs across each corporation's facilities, offsets prior losses, assesses the empire's corporate tax rate and carries unpaid liability forward. Fleet and other corporate income is not included yet. Actual payments reduce corporate reserves and enter municipalities hosting profitable facilities with populations, or another populated body in the empire if needed. Without a receiving municipality, assessed tax remains unpaid.

This file describes intended economic rules and UI behavior, including unmarked sections. The live turn runs market processing, system budgets, household wages and purchases, power generation, material industry transactions, corporate profit tax, then municipal accounting. The newer cohort model is derived from age-group populations for household accounting but is not yet persistent or used for industry staffing. `PlanetaryMunicipalProcessor` computes local balance sheets, carries unpaid municipal obligations forward and settles signed system transfers. The system view aggregates those sheets and the imperial view reports actual central treasury receipts and expenditures from the last processed day. Of the commands listed below, only `SetSystemEconomyBudgetCommand` exists under that name; the other five are proposed. `ColonyLedgerView` and `CorporateGovernanceView` are also proposed. `tech_subspace_banking` is referenced by code but absent from the current technology catalog. Saves retain household, market, industry and corporate tax accounts, planetary and imperial balance sheets, courier ships and the contribution setting. The lifecycle order at the end of this file remains a design target beyond the new passes.

Households on known breathable planets and moons use ambient oxygen without a market purchase. Households on airless or hostile bodies still buy `oxygen_gas`. This is a market-demand rule rather than a modeled atmospheric oxygen cycle.

Generated homeworlds now carry enough opening household credits and market stock to meet all tracked basic, electricity, secondary and luxury needs through a 30-day balance check. Farms and consumer-goods factories on those homeworlds are sized against population demand, while other seeded facilities use their required operating crew rather than hundreds of thousands of idle paid workers. Public job quotas are funding targets: the household payroll pass scales actual hires to fit the system's daily public budget. The municipal ledger still records public salaries and the sector budget as separate expenditures. Current agricultural inputs, specialized non-human nutrients and luxury goods rely partly on finite opening stock; replenishment and broad civilian employment are not complete, so the first-month result is not a perpetual equilibrium.

#### Universal credits
- All financial transactions use universal credits, representing the liquid fiat wealth or asset-backed reserves of an empire.
- Credits can be spent globally to subsidize planetary deficits, fund technology research or pay for ship module manufacturing.

#### Public economy
The public economy represents the liquid credit reserves controlled directly by the empire's central government.
- **State revenue:** Planetary income taxes, corporate profit-tax payments and docking fees first enter municipal accounts. They become imperial treasury receipts only when a contribution reaches the treasury. State-owned facility sales change the central treasury directly, as do delivered couriers. Same-body industry sales no longer pay a gross corporate sale tariff.
- **State expenditures:** The treasury is used to pay for macro-infrastructure projects. This includes funding technological research applications, paying the base upkeep costs of space stations and colonies, compensating state employees (`soldiers`, `scientists`, `bureaucrats`), maintaining public facilities and covering surface-to-orbit orbital lift costs for state-owned spacecraft.
- **Planetary subsidies:** If a frontier colony's localized public maintenance exceeds its tax collection, the state treasury can pump credits directly into the world to prevent structural decay, assuming a secure logistics network is active.

#### System economy and public sector allocation
Education, law and order, health and welfare, infrastructure and planetary militias each receive an adjustable part of the system economy budget. The game calculates requirements for each of these areas based on the system's population, resources, technology level, happiness and existing investment. The frontend displays information about the current investment level so that the user can make informed decisions.
- **Tax level:** Set per planetary system. Can be set by the governor of the system or directly by the player. Determines the income tax rate levied on private citizen wages and corporate activities in the system.
- **Education:** Improves the intelligence level of people from the system, notably younger generations. High levels grant a positive happiness effect while low levels produce discontent. Increases employment of teachers and scientists. Functions as the primary driver for upward social mobility.
- **Law and order:** Suppresses crime, safeguards commerce and audits corporate ledgers. High levels grant a positive happiness effect while low levels yield lawlessness. Increases employment of police and judicial bureaucrats.
- **Health and welfare:** Improves population health and happiness, reducing disease outbreak probability and elevating morale. High levels accelerate population growth and bio-resilience while low levels create market opportunities for private healthcare corporations to charge out-of-pocket fees.
- **Infrastructure:** Enhances the operational throughput and structural durability of all surface and orbital entities in the system. Subsidizes spaceport logistics, maintains ecological baselines on hostile worlds and reduces turnaround maintenance wear.
- **Military:** Provides a defensive militia for the system to deter invasions and suppress civil unrest. Functions as the primary recruitment ground for the imperial navy, scaling the pool of hireable soldiers and ship crew.
- **Empire contribution:** The only signed slider, from -100% to +100% of the daily public budget. Positive values request a transfer from local credits to the central treasury. Negative values request an empire subsidy for the system. All sector allocation and tax sliders remain nonnegative. The UI shows the requested transfer in credits per day and the last local transfer. A positive local transfer may still be aboard a courier before the imperial treasury receives it.

The five sector sliders are nonnegative shares of the public budget. Their values are normalized to 100% when the command is applied and the workbench shows each effective share as credits per day. The municipal pass charges the public budget locally across inhabited bodies in proportion to population. It does not deduct the same budget directly from the imperial treasury. The signed transfer target is `empireContributionRate * totalBudgetCredits` each day. A positive transfer is limited by available local credits after outstanding local debt is repaid. A negative transfer disburses the requested subsidy even when the imperial treasury is insufficient; the unfunded part becomes imperial debt. Zero requests no transfer. A system may run a daily deficit even after receiving a subsidy.

#### Recorded balances and debt ownership
`PlanetaryBalanceSheet` stores each body's daily revenue, daily expenditure, net balance, liquid reserve and outstanding municipal debt. A negative daily balance consumes that body's reserve first and any remaining shortfall increases its debt. Later surpluses or subsidies repay existing debt before a reserve can grow or a positive empire contribution can leave. System revenue, expenditure and debt are sums of these local sheets, not a second set of obligations. A depopulated body's debt and reserve remain on its last sheet until the body is processed again.

`ImperialBalanceSheet` stores the central treasury's last-day receipts, expenditures, outstanding debt and debt principal repaid. Receipts are counted when state-owned industry sales, electronic contributions or couriers actually reach the treasury. Corporate profit tax remains municipal revenue until transferred. Expenditures include state wages, reactor fuel, subsidies and commands that spend central credits. Subsidies may create imperial debt when the treasury cannot fund them. Later treasury cash repays this debt; principal repayment is shown separately from that day's operating expenditure. No interest, creditor market or debt-service penalty is implemented. Local debt is not copied to the imperial ledger.

#### System administrative pipeline and bureaucrat integration
Allocating liquid credits to system sectors is necessary but insufficient on its own. Every sector requires institutional oversight provided by the `bureaucrat` citizen strata. Without sufficient bureaucrats, funding suffers from administrative bottlenecks, corruption and clerical stagnation.

Draft:
- **Baseline sector spending:**
  $$S_i = \frac{B_i}{P_{\text{total}}}$$
  where $B_i$ is the credit allocation to sector $i$ and $P_{\text{total}}$ is the total system population headcount.

- **Baseline sector efficiency ($E_{\text{base}, i}$):**
  $$E_{\text{base}, i} = \min\left(1.50, \frac{S_i}{S_{\text{target}, i}}\right)$$
  where $S_{\text{target}, i}$ is the target per-capita spending baseline (standard default: 10.0 credits per citizen).

- **Sector administrative demand ($D_{\text{admin}, i}$):**
  $$D_{\text{admin}, i} = \text{ceil}(P_{\text{total}} \times \alpha_i \times E_{\text{base}, i})$$
  where $\alpha_i$ represents the bureaucrat demand coefficient:
  - Education: $\alpha_{\text{edu}} = 0.005$ (5 bureaucrats per 1,000 citizens at baseline)
  - Law and order: $\alpha_{\text{law}} = 0.008$ (8 bureaucrats per 1,000 citizens at baseline)
  - Health and welfare: $\alpha_{\text{health}} = 0.006$ (6 bureaucrats per 1,000 citizens at baseline)
  - Infrastructure: $\alpha_{\text{infra}} = 0.004$ (4 bureaucrats per 1,000 citizens at baseline)
  - Military: $\alpha_{\text{mil}} = 0.005$ (5 bureaucrats per 1,000 citizens at baseline)

- **Total administrative demand and bureaucrat allocation:**
  $$D_{\text{admin}, \text{total}} = \sum_{i} D_{\text{admin}, i}$$
  If the available system bureaucrat headcount $B_{\text{available}}$ is less than $D_{\text{admin}, \text{total}}$, available personnel are distributed across sectors according to governor-assigned priority weights $w_i$ (defaulting to equal weighting):
  $$B_{\text{assigned}, i} = B_{\text{available}} \times \frac{w_i \cdot D_{\text{admin}, i}}{\sum_{j} w_j \cdot D_{\text{admin}, j}}$$

- **Administrative efficiency ratio ($A_i$):**
  $$A_i = \min\left(1.0, \frac{B_{\text{assigned}, i}}{D_{\text{admin}, i} + \epsilon}\right)$$

- **Effective sector efficiency ($E_{\text{eff}, i}$):**
  $$E_{\text{eff}, i} = E_{\text{base}, i} \times (0.35 + 0.65 \times A_i)$$
  A total deficit of bureaucrats ($A_i = 0$) caps effective efficiency at 35% of baseline funding due to unmanaged red tape, lost manifests and organizational paralysis.

#### Sector equations and downstream mechanics

##### 1. Education: upward social mobility and technological scaling
Education consolidates academic institutions, research laboratories and vocational retraining centers.

Draft:
- **Upward social mobility pass:**
  Every turn, the engine evaluates working-age cohorts in low-complexity strata (`laborer`, `miner` and `farmer`). A fraction of these cohorts is retrained into higher-complexity professions:
  $$\text{Strata Upgrade Rate} = \text{Base Mobility Rate} \times E_{\text{eff}, \text{edu}}$$
  where $\text{Base Mobility Rate} = 0.02$ (2% baseline per turn). People only retrain if they will earn better in other jobs. Lacking enough higher level jobs and given demand for simpler jobs, people can also change from a higher to a lower level.
  Retrained cohorts are distributed into higher strata according to systemic demand:
  - Up to 40% upgrade to `technician`
  - Up to 25% upgrade to `engineer`
  - Up to 20% upgrade to `teacher`
  - Up to 10% upgrade to `medic`
  - Up to 5% upgrade to `scientist`

  Cohorts that do not have their needs met are more likely to retrain, either up or down according to system demand.

- **Local research generation:**
  Hired `scientist` cohorts generate raw research points for the global technology tree:
  $$\text{Tech Points per Turn} = N_{\text{scientist}} \times \beta_{\text{tech}} \times E_{\text{eff}, \text{edu}} \times (1.0 + M_{\text{tech}})$$
  where $\beta_{\text{tech}} = 1.0$ point per scientist and $M_{\text{tech}}$ represents imperial research multipliers.

- **Manufacturing complexity tolerance:**
  High education enables industrial facilities to manufacture complex component blueprints without defect backlogs:
  $$\text{Complexity Bonus} = \text{round}(2.0 \times E_{\text{eff}, \text{edu}})$$

##### 2. Law and order: customs auditing and crime suppression
Law and order consolidates planetary constabularies, orbital customs checkpoints, financial auditing bureaus and judicial tribunals.

Draft:
- **Smuggling detection rate ($D_{\text{smuggle}}$):**
  $$D_{\text{smuggle}} = \text{clamp}\left(0.05, 0.95, 0.15 + 0.70 \times E_{\text{eff}, \text{law}} - 0.20 \times \frac{\text{Hub Trade Volume}}{\text{Hub Capacity}}\right)$$
  When an autonomous corporate freighter attempts to evade tariffs through unpoliced channels, $D_{\text{smuggle}}$ determines interception probability. Caught freighters are assessed a fine equal to $2.5 \times \text{Cargo Value}$ deposited into the public treasury. Vessels with repeated infractions are impounded.

- **Systemic crime index:**
  $$\text{Crime Index} = \text{clamp}\left(0.0, 1.0, \frac{\text{Hub Trade Volume}}{10{,}000} \times (1.0 - E_{\text{eff}, \text{law}}) + (1.0 - \text{Happiness}) \times 0.50\right)$$

- **Downstream crime penalties:**
  - Worker absenteeism: $P_{\text{absent}} = 0.15 \times \text{Crime Index}$ (percentage reduction in industrial output).
  - Storage inventory shrinkage: $S_{\text{shrink}} = 0.05 \times \text{Crime Index}$ (percentage of commercial hub warehouse materials lost per turn).
  - Happiness penalty: $\Delta H_{\text{crime}} = -0.25 \times \text{Crime Index}$.

##### 3. Health and welfare: demographic resilience and social safety nets
Health and welfare manages public hospital networks, biosafety protocols, social security pensions and demographic preservation.

Draft:
- **Net population growth modifier ($\Delta g$):**
  $$\Delta g = (E_{\text{eff}, \text{health}} - 1.0) \times 0.015 - \text{Environmental Penalty} \times 0.01$$

- **Bio-resilience factor ($B_r$):**
  $$B_r = \text{clamp}(0.0, 1.0, 0.20 + 0.80 \times E_{\text{eff}, \text{health}})$$
  - Disease outbreak probability: $P_{\text{outbreak}} = P_{\text{base}} \times (1.0 - B_r)$
  - Epidemic mortality: $M_{\text{epidemic}} = M_{\text{base}} \times (1.0 - 0.75 \times B_r)$

- **Public retirement welfare subsidy:**
  When a citizen exceeds their profession's `retirementLifespanPercentage`, they stop receiving employment wages:
  $$C_{\text{pension}} = N_{\text{retired}} \times \text{Nutrient Spot Price} \times \min(1.0, E_{\text{eff}, \text{health}})$$
  If $E_{\text{eff}, \text{health}} < 1.0$, unfunded retirees suffer nutrient starvation, triggering a happiness penalty of up to $-0.40$ and elevated mortality.

##### 4. Infrastructure: surface-to-orbit logistics and ecological baselines
Infrastructure funds spaceport maintenance, transport corridors, utility power grids, mass drivers and environmental life support scrubbers.

Draft:
- **Orbital lift cost subsidies:**
  High infrastructure funding reduces operational lift overhead:
  - Spaceport handling fee discount: $\text{Discount}_{\text{port}} = \min(0.80, 0.20 \times E_{\text{eff}, \text{infra}})$
  - Vehicle turnaround wear discount: $\text{Discount}_{\text{wear}} = \min(0.70, 0.30 \times E_{\text{eff}, \text{infra}})$

- **Ecological baseline and environmental penalty:**
  Hostile celestial environments have a native environmental penalty score ($P_{\text{env}}$):
  $$P_{\text{env}} = \max\left(0.0, \text{Radiation Level} \times 0.40 + \text{Atmospheric Toxicity} \times 0.40 + \frac{|\text{Surface Temp} - 288|}{100} \times 0.20\right)$$
  If $E_{\text{eff}, \text{infra}} < P_{\text{env}}$, the colony experiences an environmental deficit ($\Delta_{\text{env}} = P_{\text{env}} - E_{\text{eff}, \text{infra}}$):
  - Structural decay rate: $D_{\text{decay}} = \Delta_{\text{env}} \times 0.05$ per turn to all building durability.
  - Population mortality increase: $M_{\text{env}} = \Delta_{\text{env}} \times 0.02$ per turn.

- **Operational throughput bonus:**
  $$\text{Throughput Bonus} = 0.10 \times (E_{\text{eff}, \text{infra}} - 1.0)$$
  Applied as an efficiency multiplier to all active industrial processors and refineries.

##### 5. Military: recruitment pools and martial stabilization
Military coordinates planetary defense forces, naval conscription offices, fortification maintenance and orbital security garrisons.

Draft:
- **Recruitment yield coefficient ($Y_{\text{recruit}}$):**
  $$Y_{\text{recruit}} = \max(0.0, 0.01 \times P_{\text{eligible}} \times E_{\text{eff}, \text{mil}})$$
  where $P_{\text{eligible}}$ is the non-essential working-age population. Fresh recruits are transferred each turn to the empire's central recruitment reserves as `soldiers` and `ship_crew`.

- **Unrest index and garrison strike suppression:**
  $$\text{Unrest Index} = \text{clamp}(0.0, 1.0, (1.0 - \text{Happiness}) \times 0.70 + \text{Tax Rate} \times 0.30)$$
  If $\text{Unrest Index} > 0.60$, a worker strike triggers, halting local production. A stationed garrison suppresses the strike if:
  $$\frac{N_{\text{soldier}}}{P_{\text{total}}} \ge 0.02 \times \text{Unrest Index}$$
  Successful suppression breaks the strike and restores production, but inflicts a $-0.15$ happiness penalty and $-0.05$ trust penalty for 5 turns.

#### Planetary balance sheets and municipal finance
The design target gives every colony, outpost and space station an autonomous localized balance sheet. The current municipal pass calculates sheets for populated planets and moons. Space stations do not yet have their own public finance ledger.

##### Planetary balance sheet data structure
In the codebase, planetary balance sheets are represented by immutable records:

```java
public record PlanetaryBalanceSheet(
        String planetId,
        String systemId,
        String empireId,
        double grossPlanetaryProduct,
        double incomeTaxRevenue,
        double corporateProfitTaxRevenue,
        double dockingFeeRevenue,
        double totalRevenueCredits,
        double workforceSalaries,
        double facilityMaintenanceCosts,
        double publicWelfareExpenditures,
        double infrastructureUpkeepCosts,
        double totalExpenditureCredits,
        double netBalanceCredits,
        double uncollectedLocalCredits,
        double centralSubsidyReceivedCredits,
        double publicSectorFundingCredits,
        double empireTransferCredits,
        double outstandingDebtCredits
) {}
```

##### Revenue formulas and local tax collection
Income and corporate profit-tax collection run today; detailed docking charges remain draft.
- **Planetary income tax:**
  $$R_{\text{income}} = \sum_{\text{cohorts}} (N_{\text{cohort}} \times W_{\text{strata}} \times \tau_{\text{system}})$$
  where $W_{\text{strata}}$ is the wage rate of the profession and $\tau_{\text{system}}$ is the system income tax rate.
- **Corporate profit tax:**
  $$R_{\text{corporate}} = \min(\text{available corporate cash},\;\text{prior unpaid tax} + \tau_{\text{corporate}}\max(0,\text{realized profit} - \text{carried losses}))$$
  Realized profit sums the corporation's facility sales minus input costs and wages. Losses reduce later taxable profit, and assessed tax that cannot be paid remains a corporate liability. Only tax actually paid enters municipal revenue.
- **Commercial docking and handling fees:**
  $$R_{\text{docking}} = \sum_{\text{docked vessels}} \text{Spaceport Fee Credits}$$
- **Total localized revenue:**
  $$R_{\text{total}} = R_{\text{income}} + R_{\text{corporate}} + R_{\text{docking}}$$

##### Municipal expenditures and facility upkeep
Draft:
- **Workforce salaries (state personnel):**
  $$E_{\text{salaries}} = \sum_{\text{state employees}} (N_{\text{worker}} \times W_{\text{strata}})$$
  covers active `soldiers`, `scientists`, `bureaucrats`, `police`, `teachers` and `medics`.
- **Facility maintenance:**
  $$E_{\text{facility}} = \sum_{\text{facilities}} \text{Base Upkeep Credits}$$
- **Public welfare expenditures:**
  $$E_{\text{welfare}} = C_{\text{pension}} + \text{Unemployment Relief Credits}$$
- **Infrastructure upkeep:**
  $$E_{\text{infra}} = \text{Grid Power Maintenance} + \text{Life Support Maintenance}$$
- **Total localized expenditures:**
  $$E_{\text{total}} = E_{\text{salaries}} + E_{\text{facility}} + E_{\text{welfare}} + E_{\text{infra}} + E_{\text{public budget}}$$
  The current implementation distributes the daily public budget among inhabited bodies by population share. Workforce salaries and other operating costs are additional expenses.

##### Deficit handling and emergency state bailouts
- **Net balance:** $\Delta B = R_{\text{total}} - E_{\text{total}}$.
- **Surplus resolution:** When $\Delta B > 0$, credits accumulate in `uncollectedLocalCredits`. A positive empire contribution setting remits up to its requested amount from available local credits; the rest remains local.
- **Deficit resolution:** When $\Delta B < 0$, the deficit is paid from local credit reserves. If local reserves are exhausted, the unpaid amount accumulates in `outstandingDebtCredits` on that body. An empire subsidy may reduce the body's debt while increasing imperial debt if central cash is insufficient. Service-collapse effects below are not yet modeled.
  - A separate emergency auto-subsidy setting is a design target, not current behavior.
  - If subsidies cannot reach the world (due to lack of couriers or trade route blockades), local municipal services collapse: facility efficiency drops by 25% per turn, crime spikes by +0.30 and citizen happiness drops by -0.35.

#### Private economy and civilian markets
The private economy governs capital generation, disposable income and daily consumer spending by citizens.

New campaigns begin with zero municipal balances; the first daily tick posts the first actual revenues and expenses.

Market purchases currently deplete available order supply, while desired household demand is not yet added to the spot-price calculation.

The household pass groups derived cohorts by body, race and profession. Working-age citizens (18 through 64) earn wages only when a public system job or a funded industrial facility job is available. Retired citizens receive the current small public pension. Public system wages and pensions are charged to the body's municipal balance sheet. State-owned facility wages reduce the empire treasury and corporate facility wages reduce the owner's reserves. The system income tax rate applies to wages actually paid, not pensions; each body's tax receipts enter its municipal balance sheet and therefore its system and empire reports. A household carries its savings across days and buys species-specific basic nutrients from a hub on its body. If its empire has `electricity` and `industrial_production`, electricity is also a tier 1 need; the household reserves its expected power bill before optional `consumer_goods` and `luxury_goods`. Electricity is billed on the grid at 0.02 credits per kWh and recorded as spending and unmet basic kWh. Material purchases reduce market stock and add credits to the hub's trading cash, which can buy facility output. The account separately reports unmet basic kilograms, unmet electricity kWh and secondary and luxury fulfillment. Housing, healthcare, wage settlement for other employers, retail seller attribution, Hive Mind allocation and effects of unmet needs on happiness or mortality remain unimplemented.

##### Single-education cohort model and colony fragmentation
Every citizen cohort represents exclusively one education and profession profile (`miner`, `technician`, `engineer`, `medic`, `bureaucrat`, `police`, `soldier`, `farmer`, `scientist`, `teacher`). Because skills cannot be arbitrarily mixed within a single cohort, colonies naturally operate as an ensemble of specialized cohort fragments:
- **Standard cohort unit:** $1.0\text{ standard cohort} = 100{,}000\text{ citizens}$.
- **Continuous fractional efficiency:** Partially filled fragments operate at linear fractional efficiency:
  $$\text{Cohort Fraction} = \frac{\text{Headcount}}{100{,}000}$$
  A fragment of 16,000 miners represents 0.16 cohorts and operates at continuous fractional capacity.
- **Facility staffing and multi-profession bottlenecks:** Facilities require specific profession quotas. When a colony lacks sufficient personnel in any required profession, operational output scales with the most severe bottleneck:
  $$\text{Facility Operational Efficiency} = \min_{p} \left( \frac{\text{Assigned Headcount}_p}{\text{Required Headcount}_p}, 1.0 \right)$$
- **Colony specialization profiles:** Frontier mining outposts, agricultural domes and industrial worlds maintain distinct cohort fragment distributions to satisfy technical and administrative quotas.

##### Citizen wage scales by profession complexity
Professions receive base credit compensation per turn according to technical complexity:

| Complexity tier | Professions | Base wage (credits/turn) |
| :--- | :--- | :--- |
| Low complexity | `laborer`, `miner`, `farmer` | 10.0 |
| Medium complexity | `technician`, `police`, `teacher` | 25.0 |
| High complexity | `engineer`, `medic`, `bureaucrat` | 50.0 |
| Elite complexity | `scientist`, `ship_captain`, `executive` | 100.0 |

Draft:
- **Disposable income:**
  $$I_{\text{disp}} = W_{\text{strata}} \times (1.0 - \tau_{\text{system}})$$

##### Civilian purchasing and consumption loop
Every turn, employed citizens spend disposable income on biological and social necessities:
- **Nutrient purchasing:** Citizens buy compatible food from local commercial hubs:
  $$C_{\text{nutrient}} = \text{Daily Food Requirement} \times \text{Local Nutrient Spot Price}$$
- **Residential rent:** Housing upkeep paid to habitation module operators:
  $$C_{\text{rent}} = \text{Habitation Base Upkeep} \times \text{Zoning Multiplier}$$
- **Out-of-pocket healthcare:** If public health and welfare efficiency is below 1.0, citizens pay private medical corporations:
  $$C_{\text{health}} = \max(0.0, 1.0 - E_{\text{eff}, \text{health}}) \times 8.0\text{ credits}$$

###### Every cohort has three levels of needs. 
  - Basic needs are what is needed for survival, typically food and housing. Not having basic needs met, means the citizen is in a state of starvation and will eventually die.
  - Secondary needs are what is needed for a satisfactory life style. Not having secondary needs met, means the citizen will have reduced happiness. 
  - Luxury needs are what is needed for comfort and luxury. Not having luxury needs met have no negative impact, but having them met increases happiness.

#####  Strategic interaction with society types
  - Individualist: The cohort uses its private wallet salary to purchase these tiers from the CommerceHub. If a corporation is hoarding goods or charging exorbitant prices, secondary and luxury tiers fail first.
  - Collectivist: The state can set price caps on basic goods to guarantee everyone survives, but this might lower corporate profits, meaning the state must step in with subsidies to keep secondary/luxury production alive.
  - Hive Mind: Money is bypassed entirely. The central engine allocates raw biomass and energy directly to the cohorts from central storage nodes. Tiers 2 and 3 are either completely deactivated or translated into raw "Drone Maintenance Processing Units" to optimize collective network throughput.

##### Private savings and demographic feedback
Draft:
- **Net turn savings:**
  $$\Delta \text{Savings} = I_{\text{disp}} - (C_{\text{nutrient}} + C_{\text{rent}} + C_{\text{health}})$$
- **Prosperity and happiness modifier:**
  If $\Delta \text{Savings} > 0$, accumulated savings increase citizen living standards, adding up to $+0.20$ to happiness.
  If $\Delta \text{Savings} < 0$, citizens deplete savings. Once depleted, citizens enter poverty, triggering food rationing, a $-0.35$ happiness penalty and an unrest surge.

#### Autonomous private corporations

##### Capital accumulation and shortcoming resolution
Profits generated from civilian transactions (food sales, residential rent, private clinics) aggregate into corporate investment pools.
- **Market shortcoming score ($S_m$):**
  Corporations continuously evaluate systems and commodities for shortages:
  $$S_m = \frac{\text{Local Demand} - \text{Local Supply}}{\text{Local Demand} + \epsilon} \times \text{Spot Price}$$
- **Autonomous investment action:** When $S_m$ exceeds threshold $\theta_{\text{invest}}$, corporations allocate capital to build extraction sites, construct refineries or commission commercial freighters to exploit arbitrage opportunities.

##### State governance and corporate taxation
The sovereign empire regulates corporate behavior without micro-managing individual vessels:
- **Corporate profit tax ($\tau_{\text{corporate}}$):** Applies to positive realized earnings across a corporation's facilities after input costs, wages and carried losses. Fleet and other corporate income is not included yet. Actual payments enter populated local municipal balances while unpaid assessed tax stays on the corporation's persistent tax account. A possible VAT on eligible purchases would be separate and its rules remain draft.
- **Sector subsidies:** State credit grants directed to specific corporate orientations (`EXTRACTION`, `MANUFACTURING`, `AGRICULTURE`, `TRANSPORT`) to guide investment toward strategic shortages.
- **Public zoning restrictions:** State zoning laws can reserve planetary slots exclusively for military shipyards or government research complexes, prohibiting private commercial construction.

##### Nationalization of corporate assets
In emergencies or wartime, the state can forcefully nationalize corporate assets (such as cargo freighters or mining platforms):
- Assets transfer immediately to the public sector.
- Inflicts a severe corporate trust penalty (-50 trust), raises private market prices by 30% and causes corporate capital flight to foreign systems.

#### The hive mind command economy
Hive mind societies operate completely outside the public-private market dichotomy.
- **Zero-market mechanics:** Hive minds collect no taxes, pay no wages and track no citizen happiness or commercial profits.
- **Unified command grid:** 100% of extracted materials, energy and manufactured goods flow directly into state stockpiles. Populations consume food and life support as raw industrial maintenance.
- **Demographic trade-off:** Hive minds are immune to tax evasion, strike actions, corporate monopolies and crime leakage. However, they lack autonomous corporate initiative, requiring the player to manually orchestrate every supply chain and facility expansion.

#### Currency logistics, courier ships and electronic banking

##### Physical currency latency and courier dispatch rules
Universal credits generated on distant colonies do not teleport instantly to the imperial treasury during early eras. Physical currency caches must be hauled across interstellar distances.
The current implementation dispatches a courier for each positive scheduled contribution when electronic banking is unavailable, even below the draft thresholds below. Negative subsidies credit local reserves immediately; outbound subsidy couriers and route blockade checks are not implemented yet.

Draft:
- **Courier dispatch trigger:**
  A colony dispatches an autonomous `CourierShip` when:
  1. `uncollectedLocalCredits` $\ge T_{\text{threshold}}$ (standard threshold: 10,000 credits); or
  2. Scheduled courier cycle elapsed (every 10 turns) with uncollected balance $\ge 1{,}000$ credits.
- **Transit route and cargo payload:**
  The courier ship loads the local credits into physical vaults and charts a course to the imperial capital or regional sector treasury.

##### Courier ship transit and piracy risk
- **Vulnerability:** Unescorted courier ships are prime targets for pirate raiders and shadow syndicates.
- **Interception probability ($P_{\text{intercept}}$):**
  $$P_{\text{intercept}} = \text{clamp}\left(0.0, 0.45, 0.50 \times \overline{\text{Crime Index}}_{\text{route}}\right)$$
  where $\overline{\text{Crime Index}}_{\text{route}}$ is the average crime metric along the systems traversed.
- **Looting consequence:** Intercepted couriers lose their stored credits, which are transferred directly into pirate shadow capital pools to fund rogue combat fleets.

##### Quantum sub-space electronic banking transition
- **Technology unlock:** Researching `tech_subspace_banking` (Quantum Sub-space Electronic Banking, tier 3 communications) unlocks instant digital capital synchronization.
- **Mechanical effect:** Eliminates physical currency latency. Planetary surpluses and central subsidies transfer instantaneously at the end of each turn with zero courier requirements and zero piracy vulnerability.

#### Control layer commands and player governance
The control module provides validated commands for managing the economy:
- `SetSystemEconomyBudgetCommand`: Sets five nonnegative sector shares, the daily public budget, the system income tax rate and the signed empire contribution percentage.
- `SetPlanetaryTaxRateCommand`: Sets localized income tax rate $\tau_{\text{system}}$ (valid bounds: 0.0 to 0.50).
- `SetEmpireCorporateTaxCommand`: Adjusts empire-wide corporate production tariff $\tau_{\text{corporate}}$ (valid bounds: 0.0 to 0.40).
- `SetPlanetarySubsidyCommand`: Authorizes central treasury credit disbursements to deficit worlds.
- `SubsidizeCorporateSectorCommand`: Issues targeted credit grants or tax abatements to specific corporate orientations.
- `NationalizeCorporateAssetCommand`: Authorizes emergency expropriation of corporate vessels or facilities.

#### Presentation layer and user interface requirements
- **Immutable snapshot consumption:** UI views read strictly from immutable `GameState` records (such as `PlanetaryBalanceSheet` and `SystemEconomy`). The frontend never computes synthetic economic values independently.
- **`SystemEconomyWorkbenchCard`:**
  - Five nonnegative sector allocation sliders, a nonnegative tax-rate slider and one signed empire contribution slider. Allocation shares, estimated tax receipts and the contribution target are shown in credits per day. The last local transfer is shown separately.
  - Live preview of administrative bureaucrat headcount versus demand.
  - Warning indicators for institutional bottlenecks, unrest risk and environmental deficits.
- **`ColonyLedgerView` in `EconomyTab`:**
  - Tabular breakdown of local revenues (income tax, tariffs and docking fees) and expenditures (salaries, facility upkeep, welfare and infrastructure).
  - Colony fiscal status indicator (surplus, self-sufficient or deficit).
  - Physical courier ship status (credits in transit, estimated arrival time and route risk).
- **`CorporateGovernanceView`:**
  - Corporate taxation controls, sector subsidy toggles and market shortcoming heatmaps.

#### Turn execution order and simulation lifecycle
During each simulation tick, economic systems execute in strict sequence:
1. **Private wage and consumption phase:** Citizens receive profession wages from employers, pay system income taxes and purchase food, rent and healthcare.
2. **Commercial market and freight phase:** `MarketProcessor` updates spot prices, `CorporateFleetProcessor` executes trade arbitrage and deducts physical orbital lift costs and tariffs are calculated.
3. **Planetary balance sheet phase:** Municipal revenues and state expenditures are calculated, producing authoritative `PlanetaryBalanceSheet` snapshots.
4. **Administrative pipeline phase:** `SystemEconomyProcessor` computes sector administrative demand, allocates bureaucrats and determines effective efficiencies ($E_{\text{eff}, i}$).
5. **Downstream sector resolution phase:**
   - Education: runs social mobility retraining pass and adds scientist research points.
   - Law and order: computes crime index, runs smuggling audits and assesses fines.
   - Health and welfare: computes net population growth modifiers and disburses retirement pensions.
   - Infrastructure: applies module throughput bonuses and evaluates environmental decay.
   - Military: replenishes recruitable personnel pool and evaluates strike suppression.
6. **Currency logistics and treasury phase:** Dispatches courier ships for surplus colonies (or executes instant quantum banking transfers if researched), routing net funds to the central treasury.
