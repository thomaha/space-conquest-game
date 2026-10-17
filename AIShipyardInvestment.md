# AI shipyard investment

## Capacity and funded demand

AI yard investment requires four consecutive days of actual paid ship work at at least 95% of current fully staffed capacity. The existing saved ShipyardLedger supplies these observations. Missing history or a gap delays investment. A bounded discarded construction forecast checks customer ownership, access, manufacturing complexity, complete remaining material bills and cash for captured service fees. Early progress with only part of a material bill does not qualify as sustained demand.

The yard must have at least six days of funded backlog and enough remaining work to benefit after construction completes. Idle yards, unpaid staff, missing materials and unfunded customers do not trigger expansion. Local professional unemployment and spare station power must cover an additional orbital grid. Surface yards must retain their settled operating reserve. Pending construction or expansion projects prevent duplicate investment, including after save/reload.

## Ownership and construction

Compatible societies consider corporate yard owners first. Each company uses its own capital and retains its yard. Its bounded forecast requires contracted fees, after the empire's corporate tax rate, to cover the material investment and forecast yard payroll. This is an estimate based on existing funded orders rather than a promise of future customers. Government investment requires funded government ship orders and its own treasury reserve; a commercial backlog alone does not justify public expansion.

The AI submits RequestAIShipyardExpansionCommand through CommandQueue. The command revalidates the quote at the simulation tick and stages an ordinary AddStationModuleCommand or ExpandFacilityCommand. Staging does not spend credits. Existing daily construction buys and consumes the physical bill before commissioning. Payroll then determines actual increased ship throughput. Changes in funds or supply can still pause a project after it is queued.

## Application data and tuning

The space_stations application in technologies.json declares yard work, staff, profession, slots, mass, power, construction duration, materials, surface tier growth and expansion throttling. automated_assembly_lines and cybernetic_workforce_integration declare their yard work and staffing multipliers. ShipyardWorkCapacity reads these values and new orbital modules capture their application-defined staff and power requirements. Existing captured modules retain their recorded staff requirements. Resource edits take effect when the catalog loads in a new process.

shipyard_investment_policy.json controls observation periods, utilization, queue thresholds, forecast horizon, bounded yard previews, spending limits, cash reserves and minimum corporate profit. Initial values are limited test baselines. This work does not balance the weapon or shipyard economy.

## Limited verification

AIShipyardInvestmentTest runs paid public and corporate expansion through ordinary campaign ticks and actual disk save/reload. New material consumption is compared with an unexpanded campaign and paid daily ship work increases from 100 to 200 units. A separate surface construction check increases its tier capacity from 100 to 150. Negative cases cover absent observation history, materials, a missing final component, customer and provider cash, professional staff, power, unprofitable contracts, unauthorized ownership and a purely commercial public-yard backlog. ShipyardApplicationDataTest checks application loading, modifier composition and malformed data rejection.

Throughput expansion adds another orbital shipyard grid or one surface tier. Missing observations after history trimming conservatively postpone it. Manufacturing upgrades use the separate demand checks below and do not require a saturated yard: a complexity-blocked order produces no paid work to observe. New yard sites remain a separate investment decision.

## Manufacturing capability upgrades

ShipManufacturingCapacity now reads baseComplexity and complexityPerTier from each yard's technological application. The previous standalone manufacturing-limit resource has been removed. Its baseline limits are preserved: surface yards start at 5 and gain one per tier, grids support 7 and slipways support 10. These values can be changed in technologies.json.

ShipyardInvestmentPlanner first considers capability-blocked committed orders and researched designs needed by the existing military, trade or facility-supply planners. A registered blueprint alone does not justify investment. New demand uses a discarded completed-yard snapshot to check ordinary build validation and relevant component research. Available staffed domestic yards are preferred over a new upgrade. Recent research that increases component complexity can produce a new frozen blueprint revision after the hypothetical upgrade passes physics and research validation; the source blueprint and existing ships remain unchanged.

An orbital upgrade selects the least material-intensive application-defined module that meets the required complexity. It checks slots, spare power, professional unemployment and funds for the full bill and payroll reserve. A surface upgrade calculates the required tier from its application-defined complexity increment and checks its operating reserve. maximumSurfaceTierIncrease bounds one project to three added tiers by default. Costs and construction work scale with the actual tier increase.

RequestAIShipyardCapabilityCommand rechecks demand, research, access, ownership and joint funding at the simulation tick. It stages ordinary paid upgrade construction and, for new demand, a waiting ShipConstructionOrder atomically. The discarded completed yard is never committed. Existing blocked orders keep their captured blueprint, customer, consumed materials and service tariff. Manufacturing gates prevent ship work before the installed upgrade can support its blueprint. The pending project and waiting order persist in version 1 saves and prevent duplicate investment.

Public upgrades require government ship demand. Corporate service-yard upgrades require contracted earnings after the corporate tax estimate to cover upgrade materials and forecast yard payroll. A company's own ship order without service income does not pass this service-yard profit check. Funding quotes include the upgrade bill, the customer's complete remaining ship bill and service fees together; neither customer nor provider receives planning credits. Later resource changes can pause ordinary paid construction.

AIShipyardCapabilityTest verifies public commissioning after paid upgrade and disk reload, corporate construction with an unchanged captured tariff, surface tier upgrades and research-created complexity increases. Negative checks include missing research, materials, staff, power or demand, unprofitable service, unavailable ownership, excessive complexity and an existing staffed alternative yard. Queue diagnostics distinguish missing component research from insufficient manufacturing capability and display required and available complexity.
