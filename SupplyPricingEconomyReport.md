# Supply pricing and agricultural operations

Three 360-day probes with active supply-offer pricing and cost-covering metered electricity use 1,000 ordinary human residents, real household purchases and profession payroll, finite opening stocks, application work and timed harvests, real fueled generation and power billing. Each has one tier-1 farm and one food-processing factory. All research is known. No goods or money are replenished externally. Public operating cash is seeded and subsidies are disabled. Input reserves cover 20 cycles. Food opens at 14,000 kg and oxygen at 2,000 kg. Construction is a quoted sunk material bill, not a simulated build in these operating probes.

| Application | Nitrate shortage | Harvests | Idle days | Food produced kg | Household food bought kg | Unmet food kg | Closing market food kg | Oxygen sold kg | Retained cultivation residue kg |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| industrial_soil_cultivation | false | 3 | 0 | 66240.00 | 36000.00 | 0.00 | 6500.00 | 0.00 | 0.00 |
| hydroponic_growth_arrays | false | 12 | 0 | 16800.00 | 30800.00 | 5200.00 | 0.00 | 17500.00 | 2479.20 |
| automated_biosphere_macro_farms | false | 8 | 0 | 44800.00 | 36000.00 | 0.00 | 6500.00 | 17400.00 | 6611.20 |

## Actual operating transactions

Farm and food factory combined; internal crop transfers retain the market spread. Generator fuel, wages and capital belong to the grid provider and are not added again to the farms' paid electricity.

| Application | Shortage | Materials | Wages | Electricity | Maintenance | Sales | Cash operating result | Construction materials at baseline | Minimum facility cash |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| industrial_soil_cultivation | false | 75690.34 | 93600.00 | 1192483.84 | 57600.00 | 850383.93 | -568990.25 | 9200.00 | 9532688.94 |
| hydroponic_growth_arrays | false | 382333.63 | 104400.00 | 1193009.00 | 72000.00 | 1552831.52 | -198911.11 | 44900.00 | 9776023.30 |
| automated_biosphere_macro_farms | false | 522604.64 | 104400.00 | 1193013.06 | 201600.00 | 929929.72 | -1091687.97 | 165800.00 | 9420643.28 |

## Local food cost and capital sensitivity

All operating costs minus actual non-food receipts divided by all food physically produced, including retained unsold food. Processing crop purchases and farm crop receipts retain the real market spread. Capital sensitivity spreads only the quoted farm and processor construction material bill over 5, 10 or 20 years of 360 operating days, without interest or salvage. Work, land and construction service are additional unpriced costs. These are scenario unit costs, not universal application break-even prices.

| Application | Shortage | Operating credits/kg food | With 5-year capital | With 10-year capital | With 20-year capital | Retail break-even with 10-year capital |
|---|---|---:|---:|---:|---:|---:|
| industrial_soil_cultivation | false | 14.54 | 14.57 | 14.55 | 14.54 | 18.19 |
| hydroponic_growth_arrays | false | 87.40 | 87.94 | 87.67 | 87.54 | 109.59 |
| automated_biosphere_macro_farms | false | 37.55 | 38.29 | 37.92 | 37.74 | 47.40 |

## Grid provider funding

The metered tariff targets provider costs and reserves at the forecast load. These are actual generator operating-cash changes after paid fuel, wages, maintenance and electricity receipts; no capital amortization is included. They are reported separately to avoid counting both fuel and purchased farm electricity as the same farm expense.

| Application | Shortage | Generator fuel consumed kg | Generator operating-cash change |
|---|---|---:|---:|
| industrial_soil_cultivation | false | 3600.00 | 120215.81 |
| hydroponic_growth_arrays | false | 3600.00 | 120215.81 |
| automated_biosphere_macro_farms | false | 3600.00 | 120215.81 |

No cultivation residue is bought without a downstream consumer. Oxygen sales follow actual breathing and stockpile demand, not the farm's own output. Physical shortages can coexist with funded workers. Scarcity prices read finite installed supply offers and dynamic generator tariffs. No premium contracts or guaranteed farm sales are assumed. Fixed population, opening savings, seeded reserves and a well-funded existing generator make this a bounded operating audit rather than a self-starting campaign. No transport occurs within these local probes; delivered imports are measured separately. Unsold stock is retained without modeled spoilage or producer storage costs. Public maintenance is charged; private maintenance remains an integration gap. Reproduction: `mvn -pl engine -Dtest=AgricultureEconomicsAuditTest test` with JDK 27.
