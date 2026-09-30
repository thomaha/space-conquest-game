#### Design and implementation status
Generated campaigns begin with mixed state-owned and corporate industry on each empire homeworld, opening credits for empires, corporations, population households and commercial hubs and stocked hubs on populated colonies. These opening balances are seeded without processing a day. On later daily turns material facilities pay funded workers, pay for powered shifts, buy available inputs and sell produced goods to the local hub using its available trading cash. Producers receive 80% of the posted retail spot price and the hub retains 20% to finance inventory turnover. Unsold output stays in facility stock. This merchant margin is separate from tax and transport costs. Power plants buy distinct fuels where required and sell measured electricity to households and industries on their local grid. Built cargo terminals now offer rocket launches, built mass drivers launch goods and operating space elevators can carry goods or ships with passengers; interplanetary trade routes remain separate and their final scope is undecided. Assigned trade routes buy cargo from the origin hub and sell it to the destination hub after timed travel. Autonomous route selection remains disabled.

An assigned route spends its corporation's reserves or its empire's treasury at the origin hub's posted price when cargo is loaded. The origin hub receives those credits. Surface exports require an available launch provider. Rocket launches purchase and consume hydrocarbons from the local hub, while mass drivers and elevators require spare grid power. Each provider has a daily payload limit and receives a launch fee. A driver or elevator launch reserves one hour of electricity and prepays its provider's power bill; the daily grid pass transfers the delivered, generator-backed portion to local power plants and refunds any undelivered or battery-supplied portion. Manual orbital loading settles the same way. Purchase and launch costs stay with route cargo in transit, including partial loads. On arrival, the destination hub buys as much as its cash and storage allow at 80% of its then-current posted price. The configured destination transaction tariff goes to the controlling empire. The route records daily and cumulative realized results after prorated cargo cost and tariff; unsold cargo and its cost remain aboard until the hub can buy it. Corporate launch fees and realized power costs enter facility or elevator profit tax. The carrier must have enough cash to load, and a hub with no trading cash cannot buy a delivery. The model does not yet charge crew wages or local-travel fuel.

Within one planetary body, local trade has no transport charge or gross industry-sale tariff. VAT remains unimplemented; its rate, input-credit treatment and taxable goods are not set. The live corporate profit-tax pass sums realized earnings and costs across each corporation's facilities, space elevators and assigned trade routes, offsets prior losses, assesses the empire's corporate tax rate and carries unpaid liability forward. Mining and other fleet income are not included yet. Actual payments reduce corporate reserves and enter municipalities hosting profitable facilities with populations, or another populated body in the empire if needed. Without a receiving municipality, assessed tax remains unpaid.

The live corporate investment pass estimates operating profit from posted output prices, input prices, wages and electricity before automatically funding a shortage-driven factory. Supported factories use an unlocked recipe and extraction requires a local discovered deposit. A funded factory begins at tier zero with a 500-work-hour project. Daily work buys and consumes available structural materials from its local hub; partial supply permits partial progress and an unavailable remainder pauses work. Duplicate projects for the same corporation, body and application are blocked while one is pending. Corporations can explicitly place a funded ship order using their own proprietary blueprint; autonomous transport-fleet investment remains paused pending route-selection and investment rules. Ships commission only after their work and material bill are complete. These bills are generic mass-based estimates rather than component-level receipts. Shipyard capacity, variable labor costs and cancellation refunds remain unmodeled. The corporation registry shows estimated book net worth from cash, tracked facilities and ships, priced unsold facility stock, paid cargo aboard assigned routes and unpaid corporate tax. Loss carryforwards reduce future tax rather than counting as current cash or assets. The previous synthetic fleet income path was removed; assigned routes now record realized trading results, while mining income and unmodeled crew or fuel costs remain future work.

Draft: A later commercial-hub stock exchange may allow formation of new corporations and share issues by existing ones. Corporations, empires and population cohorts could hold equity and receive dividends from after-tax distributable earnings. Rules for issuance, retained earnings, dividend policy, share pricing and society-specific access remain undefined. Open commercial societies may support equity markets while closed communist or hive mind societies may fund production through public or collective budgets. No equity ledger or dividend transfer is implemented.

This file describes intended economic rules and UI behavior, including unmarked sections. The live turn refreshes local demand from current population, operating recipes and outstanding construction bills before market pricing, then runs system budgets, household wages and purchases, power generation, material industry transactions, corporate profit tax and municipal accounting. The newer cohort model is derived from age-group populations for household accounting but is not yet used directly for industry staffing. Household accounts retain profession shares across daily turns, filled jobs, unemployment pressure, need coverage and rolling wellbeing. When funded vacancies exist, a small share of unemployed workers retrain each day. Detailed qualification rules and wage competition remain open. Unmet basic goods and electricity accumulate annual shortage stress that lowers survival and births at the year boundary. `PlanetaryMunicipalProcessor` computes local balance sheets, carries unpaid municipal obligations forward, settles signed system transfers and allocates opted-in industry support. The system view aggregates those sheets and the imperial view reports actual central treasury receipts and expenditures from the last processed day. Of the commands listed below, only `SetSystemEconomyBudgetCommand` exists under that name; the other five are proposed. `SetPublicIndustrySubsidyCommand` separately controls facility support. `ColonyLedgerView` and `CorporateGovernanceView` are also proposed. `tech_subspace_banking` is referenced by code but absent from the current technology catalog. Saves retain household, market, industry and corporate tax accounts, planetary and imperial balance sheets, courier ships and the contribution setting. The lifecycle order at the end of this file remains a design target beyond the new passes.

Households on known breathable planets and moons use ambient oxygen without a market purchase. Households on airless or hostile bodies still buy `oxygen_gas`. Water electrolysis can produce market oxygen where built and supplied, but generated enclosed settlements still rely on opening stock. This is a market-demand rule rather than a modeled atmospheric oxygen cycle.

Generated homeworlds now carry enough opening household credits, market stock and hub trading cash to meet all tracked basic, electricity, secondary and luxury needs through a 30-day balance check. Farms, water treatment, mines, biomass processors, common-metal and silicon refineries, consumer factories and luxury workshops are sized against population demand, while other seeded facilities use their required operating crew rather than hundreds of thousands of idle paid workers. Common fertilizer and manufacturing inputs are replenished through local deposits or surface-water treatment; rare earths for everyday goods come from lower-yield deposits and refining. The four-month audit confirms cumulative nitrate and purified-water production exceed their opening reserves. Means-tested health and welfare payments now keep purchasable basic goods affordable through that audit, although optional goods can still be unaffordable for households without wages. Public job quotas are funding targets: teachers and scientists draw wages from education, police from law and order, medics from health and welfare, engineers and technicians in public service from infrastructure and soldiers from the militia allocation. The household payroll pass caps actual hires within each sector. State-owned industry employees are separate from public service employees and are paid from the facility's operating balance. Municipal salaries and remaining non-payroll public funding together equal one daily public budget; welfare first displaces unused health and welfare funding, then creates additional expense and local debt. Reactor fuels and specialized non-human nutrients still rely partly on finite opening stock. Broad civilian employment and a perpetual economic balance remain unfinished.

#### Universal credits
- All financial transactions use universal credits, representing the liquid fiat wealth or asset-backed reserves of an empire.
- Credits can be spent globally to subsidize planetary deficits, fund technology research or pay for ship module manufacturing.

#### Public economy
The public economy represents the liquid credit reserves controlled directly by the empire's central government.
- **State revenue:** Planetary income taxes, corporate profit-tax payments and docking fees first enter municipal accounts. They become imperial treasury receipts only when a contribution reaches the treasury. State-owned facility sales replenish that facility's operating balance, while delivered couriers enter the central treasury. Same-body industry sales do not pay a gross corporate sale tariff.
- **State expenditures:** The treasury pays for central projects and transfers opening working capital to new public facilities. Local public sector allocations fund service wages and other municipal spending. Each state-owned industry pays its own wages, inputs, power, fuel and maintenance from its operating balance; an unfunded operating balance can become negative through maintenance, recording facility-level debt.
- **Planetary subsidies:** If a frontier colony's localized public maintenance exceeds its tax collection, the state treasury can pump credits directly into the world to prevent structural decay, assuming a secure logistics network is active.

#### System economy and public sector allocation
Education, law and order, health and welfare, infrastructure and planetary militias each receive an adjustable part of the system economy budget. The game calculates requirements for each of these areas based on the system's population, resources, technology level, happiness and existing investment. The frontend displays information about the current investment level so that the user can make informed decisions.
- **Tax level:** Set per planetary system. Can be set by the governor of the system or directly by the player. Determines the income tax rate levied on private citizen wages and corporate activities in the system.
- **Education:** Improves the intelligence level of people from the system, notably younger generations. High levels grant a positive happiness effect while low levels produce discontent. Increases employment of teachers and scientists. Functions as the primary driver for upward social mobility.
- **Law and order:** Suppresses crime, safeguards commerce and audits corporate ledgers. High levels grant a positive happiness effect while low levels yield lawlessness. Increases employment of police and judicial bureaucrats.
- **Health and welfare:** Improves population health and happiness, reducing disease outbreak probability and elevating morale. High levels accelerate population growth and bio-resilience while low levels create market opportunities for private healthcare corporations to charge out-of-pocket fees.
- **Infrastructure and industry:** Funds public engineers and technicians and may support individually selected loss-making state-owned facilities from the remaining daily allocation. Transit and spaceport logistics are part of infrastructure. Other proposed throughput, ecological and wear effects remain design goals.
- **Military:** Provides a defensive militia for the system to deter invasions and suppress civil unrest. Functions as the primary recruitment ground for the imperial navy, scaling the pool of hireable soldiers and ship crew.
- **Empire contribution:** The only signed slider, from -100% to +100% of the daily public budget. Positive values request a transfer from local credits to the central treasury. Negative values request an empire subsidy for the system. All sector allocation and tax sliders remain nonnegative. The UI shows the requested transfer in credits per day and the last local transfer. A positive local transfer may still be aboard a courier before the imperial treasury receives it.

The five sector sliders are nonnegative shares of the public budget. Their values are normalized to 100% when the command is applied and the workbench shows each effective share as credits per day. The municipal pass charges the public budget locally across inhabited bodies in proportion to population. Public salaries are part of that budget. The infrastructure and industry share remaining after public engineer and technician wages is the maximum daily pool for selected state-owned facility subsidies. Losses and a shortfall in next-day payroll determine each selected facility's request; if requests exceed the pool, support is prorated. Subsidies replenish facility balances and are included in the already charged non-payroll public funding, not charged a second time. The signed transfer target is `empireContributionRate * totalBudgetCredits` each day. A positive transfer is limited by available local credits after outstanding local debt is repaid. A negative transfer disburses the requested subsidy even when the imperial treasury is insufficient; the unfunded part becomes imperial debt. Zero requests no transfer. A system may run a daily deficit even after receiving a subsidy.

#### Recorded balances and debt ownership
`PlanetaryBalanceSheet` stores each body's daily revenue, daily expenditure, net balance, liquid reserve and outstanding municipal debt. A negative daily balance consumes that body's reserve first and any remaining shortfall increases its debt. Later surpluses or subsidies repay existing debt before a reserve can grow or a positive empire contribution can leave. System revenue, expenditure and debt are sums of these local sheets, not a second set of obligations. A depopulated body's debt and reserve remain on its last sheet until the body is processed again.

`ImperialBalanceSheet` stores the central treasury's last-day receipts, expenditures, outstanding debt and debt principal repaid. Receipts are counted when electronic contributions or couriers actually reach the treasury. State-owned industry sales remain in the facility account. Corporate profit tax remains municipal revenue until transferred. Expenditures include central subsidies, initial public facility capital and commands that spend central credits. Local service wages and optional industry support are municipal expenses. Subsidies may create imperial debt when the treasury cannot fund them. Later treasury cash repays this debt; principal repayment is shown separately from that day's operating expenditure. No interest, creditor market or debt-service penalty is implemented. Local debt is not copied to the imperial ledger.

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
Health and welfare manages public hospital networks, biosafety protocols, social security pensions, basic-needs support and demographic preservation. The system economy slider is labeled `Health and welfare` in the live UI. Its allocation funds medics and absorbs means-tested basic-needs payments before any excess becomes an additional local expense. More detailed welfare eligibility and benefit policies remain future design work.

Draft:
- **Net population growth modifier ($\Delta g$):**
  $$\Delta g = (E_{\text{eff}, \text{health}} - 1.0) \times 0.015 - \text{Environmental Penalty} \times 0.01$$

- **Bio-resilience factor ($B_r$):**
  $$B_r = \text{clamp}(0.0, 1.0, 0.20 + 0.80 \times E_{\text{eff}, \text{health}})$$
  - Disease outbreak probability: $P_{\text{outbreak}} = P_{\text{base}} \times (1.0 - B_r)$
  - Epidemic mortality: $M_{\text{epidemic}} = M_{\text{base}} \times (1.0 - 0.75 \times B_r)$

- **Future welfare policy:** Eligibility, payment levels, healthcare coverage and how benefits respond to sector efficiency still need player-facing policy definitions. The live rule below guarantees purchasing power for available basic goods and expected electricity regardless of sector efficiency; any unfunded expense is recorded as municipal debt.

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
  State-owned industry pays its own daily maintenance from its facility balance. Unpaid maintenance becomes facility-level debt rather than an automatic municipal expense.
- **Public welfare expenditures:**
  $$E_{\text{welfare}} = C_{\text{pension}} + \text{Means-tested Basic-needs Credits}$$
- **Infrastructure upkeep:**
  $$E_{\text{infra}} = \text{Grid Power Maintenance} + \text{Life Support Maintenance}$$
- **Total localized expenditures:**
  $$E_{\text{total}} = E_{\text{public budget}} + \max(0, E_{\text{welfare}} - \text{Unspent Health Allocation}) + E_{\text{infra}}$$
  The current implementation distributes the daily public budget among inhabited bodies by population share. Public service salaries and selected industry support are included within that budget. Welfare uses the unspent health and welfare allocation first; payments beyond that amount are additional local expenses. Infrastructure upkeep is an additional expense.

##### Deficit handling and emergency state bailouts
- **Net balance:** $\Delta B = R_{\text{total}} - E_{\text{total}}$.
- **Surplus resolution:** When $\Delta B > 0$, credits accumulate in `uncollectedLocalCredits`. A positive empire contribution setting remits up to its requested amount from available local credits; the rest remains local.
- **Deficit resolution:** When $\Delta B < 0$, the deficit is paid from local credit reserves. If local reserves are exhausted, the unpaid amount accumulates in `outstandingDebtCredits` on that body. An empire subsidy may reduce the body's debt while increasing imperial debt if central cash is insufficient. Service-collapse effects below are not yet modeled.
  - A separate emergency auto-subsidy setting is a design target, not current behavior.
  - If subsidies cannot reach the world (due to lack of couriers or trade route blockades), local municipal services collapse: facility efficiency drops by 25% per turn, crime spikes by +0.30 and citizen happiness drops by -0.35.

#### Private economy and civilian markets
The private economy governs capital generation, disposable income and daily consumer spending by citizens.

New campaigns begin with zero municipal balances; the first daily tick posts the first actual revenues and expenses.

Market purchases deplete available order supply. Before spot-price updates, desired household demand is rebuilt from the current population's compatible basic nutrients, required life support, industrial-level electricity and optional goods. Recipe input demand is also refreshed from operating facilities. A non-Hive hub purchases facility output only when one of these modeled demands exists and its stock is below 30 days of that demand; unsold output stays with the facility. These are desired quantities, not guaranteed purchases; household cash and available stock still limit actual consumption. A zero-demand commodity such as mined gold has no automatic hub buyer until a consumer or industrial use is modeled.

The household pass groups derived cohorts by body, race and profession. Working-age citizens (18 through 64) earn wages only when a public system job or a funded industrial facility job is available. Retired citizens receive a small fixed pension. Before shopping, each household applies its after-tax wages, pension and savings to the cost of its available species-specific basic goods and expected electricity. A means-tested health and welfare payment covers any remaining gap. It is not taxed and does not intentionally fund optional goods. When a basic good is out of stock, its unavailable quantity receives no cash top-up and remains an unmet need. Public service wages are limited by their corresponding sector allocation. State-owned facility wages reduce that facility's operating balance and corporate facility wages reduce the owner's reserves. The system income tax rate applies to wages actually paid, not pensions or welfare; each body's tax receipts enter its municipal balance sheet and therefore its system and empire reports. If its empire has `electricity` and `industrial_production`, electricity is also a tier 1 need; the household reserves its expected power bill before optional `consumer_goods` and `luxury_goods`. Electricity is billed on the grid at 0.02 credits per kWh and recorded as spending and unmet basic kWh. Material purchases reduce market stock and add credits to the hub's trading cash, which can buy facility output. The account separately reports unmet basic kilograms, unmet electricity kWh and secondary and luxury fulfillment. Housing, healthcare, wage settlement for other employers, retail seller attribution, Hive Mind allocation and effects of unmet needs on happiness or mortality remain unimplemented.

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
- **Corporate profit tax ($\tau_{\text{corporate}}$):** Applies to positive realized earnings across a corporation's facilities and assigned trade routes after modeled costs and carried losses. Mining and other fleet income are not included yet. Actual payments enter populated local municipal balances while unpaid assessed tax stays on the corporation's persistent tax account. A possible VAT on eligible purchases would be separate and its rules remain draft.
- **Sector subsidies:** State credit grants directed to specific corporate orientations (`EXTRACTION`, `MANUFACTURING`, `AGRICULTURE`, `TRANSPORT`) to guide investment toward strategic shortages.
- **Public zoning restrictions:** State zoning laws can reserve planetary slots exclusively for military shipyards or government research complexes, prohibiting private commercial construction.

##### Nationalization of corporate assets
In emergencies or wartime, the state can forcefully nationalize corporate assets (such as cargo freighters or mining platforms):
- Assets transfer immediately to the public sector.
- Inflicts a severe corporate trust penalty (-50 trust), raises private market prices by 30% and causes corporate capital flight to foreign systems.

#### The hive mind command economy
Current generated hive societies have no corporations or household accounts. Their industry uses allocated workers and actual local power and materials. Produced goods enter the local hub inventory without credit settlement, with excess held as facility stock. Hive population consumption and a unified state stockpile are not implemented.

Draft:
- **Zero-market mechanics:** Hive minds would collect no taxes, pay no wages and track no citizen happiness or commercial profits.
- **Unified command grid:** Extracted materials, energy and manufactured goods would flow into state stockpiles. Populations would consume food and life support as raw industrial maintenance.
- **Demographic trade-off:** Hive minds would avoid tax evasion, strikes, corporate monopolies and crime leakage. They would lack autonomous corporate initiative and require player-managed supply chains and facility expansion.

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
2. **Commercial market and freight phase:** `MarketProcessor` updates spot prices and the route processor moves assigned ships, pays for cargo at the origin and settles delivery against destination hub cash. Autonomous route selection and fleet arbitrage remain future work.
3. **Planetary balance sheet phase:** Municipal revenues and state expenditures are calculated, producing authoritative `PlanetaryBalanceSheet` snapshots.
4. **Administrative pipeline phase:** `SystemEconomyProcessor` computes sector administrative demand, allocates bureaucrats and determines effective efficiencies ($E_{\text{eff}, i}$).
5. **Downstream sector resolution phase:**
   - Education: runs social mobility retraining pass and adds scientist research points.
   - Law and order: computes crime index, runs smuggling audits and assesses fines.
   - Health and welfare: computes net population growth modifiers, disburses retirement pensions and funds means-tested basic needs.
   - Infrastructure: applies module throughput bonuses and evaluates environmental decay.
   - Military: replenishes recruitable personnel pool and evaluates strike suppression.
6. **Currency logistics and treasury phase:** Dispatches courier ships for surplus colonies (or executes instant quantum banking transfers if researched), routing net funds to the central treasury.
