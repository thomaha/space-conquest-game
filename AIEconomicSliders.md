# AI economic sliders

Empire AI now reviews existing system economies as well as creating missing policies. It adjusts education and science, law and order, health and welfare, infrastructure and planetary militia allocations, the total daily budget, income tax and the signed system-to-empire transfer rate. Controller proposals enter the command queue; the UI and planner do not mutate snapshots.

## Decisions and bounds

Sector shares use current sector indices as need signals. Active funded research raises education priority, material hardship and unemployment raise health and welfare, physical grid deficits raise infrastructure and active war raises militia priority. Law and order responds to its current sector index; a separate measured crime signal is not available in the snapshot. Shares remain normalized and each sector has a configurable maximum change per review.

Settled local deficits reduce the budget, while surplus supports a budget increase. Tax correction uses observed personal tax receipts to estimate taxable income and includes the proposed budget change before choosing a bounded rate adjustment. A resource-defined fiscal deadband holds budget and tax when the imbalance is small. Material hardship favors tax relief. The population baseline, minimum and maximum budget, tax bounds and change sizes are policy values. Decisions use current receipts rather than assuming slider changes have already produced new revenue or trained workers. See [MultiSystemEconomy.md](MultiSystemEconomy.md) for the 60-day integration probe and the overshoot it exposed.

Positive transfer rates send locally held funds to the empire. They require cash above the configured local reserve. Negative rates request an empire subsidy to address deficits or debt. Available treasury cash above the retained floor is apportioned across controlled systems by population over a configurable funding horizon. Increased transfers move in bounded steps; requests are cancelled or reduced immediately when their direction, local reserve or current affordability no longer supports them.

These decisions use the existing municipal accounting path. Contributions arrive electronically with subspace banking or through couriers otherwise. Negative transfers are subsidies received locally. Municipal settlement retains its explicit imperial debt support for unfunded policy requests; the AI's quote caps its own requests against current available cash. Later unrelated spending or changing receipts can still alter the eventual fiscal position.

## Configuration and queued execution

`control/src/main/resources/economic_policy.json` contains review cadence, sector weights, adjustment limits, budget and tax bounds, local reserves, the retained treasury floor and subsidy funding horizon. These are provisional AI strategy values rather than a full economy balance pass. Weapon and application capability values are unchanged.

The current cadence is five daily turns. EmpireEconomicPlanner remembers reviews in its controller instance. Repeated snapshots do not schedule another review; a controller recreated after loading starts a fresh review window using saved settings and receipts. Hive economies retain their separate allocation model.

AdjustAISystemEconomyCommand captures the current slider settings and recomputes the proposal from current finances when executed. Changed settings, lost control and duplicate requests reject. Treasury changes update the subsidy cap before application. SetSystemEconomyBudgetCommand performs the ordinary validation and normalization.

Configuring a budget now preserves accumulated militia investment. Only daily SystemEconomyProcessor progression adds that day's militia allocation. Repeated player or AI slider commands therefore do not grant extra militia investment.

## Bounded verification

AIEconomicSlidersTest verifies increases in each sector, research, power, war and hardship signals, bounded normalized shares, tax and budget changes, positive contributions and negative subsidies through actual municipal settlement, shared subsidy caps, current-finance revalidation, duplicate and stale rejection, controller integration, policy edits and version 1 disk reload.

This remains a bounded decision policy. It does not forecast a full market equilibrium, migration, trained staffing or war outcomes. Investment percentages and public job targets still operate within the current payroll and municipal accounting implementation. Review scheduling memory is not a new saved receipt.
