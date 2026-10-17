# Agricultural production and demand audit

Four 360-day probes use 1,000 ordinary human residents, real household purchases and profession payroll, finite opening stocks, application work and timed harvests, real fueled generation and power billing. Each has one tier-1 farm and one food-processing factory. All research is known. No goods or money are replenished externally. Public operating cash is seeded and subsidies are disabled. Input reserves cover 20 cycles except the shortage case's 2.25 nitrate cycles. Food opens at 14,000 kg and oxygen at 2,000 kg. Construction is a quoted sunk material bill, not a simulated build in these operating probes.

| Application | Nitrate shortage | Harvests | Idle days | Food produced kg | Household food bought kg | Unmet food kg | Closing market food kg | Oxygen sold kg | Retained cultivation residue kg |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| industrial_soil_cultivation | false | 3 | 0 | 66240.00 | 36000.00 | 0.00 | 6500.00 | 0.00 | 0.00 |
| hydroponic_growth_arrays | false | 12 | 0 | 16800.00 | 30800.00 | 5200.00 | 0.00 | 17500.00 | 2479.20 |
| automated_biosphere_macro_farms | false | 8 | 0 | 44800.00 | 36000.00 | 0.00 | 6500.00 | 17400.00 | 6611.20 |
| hydroponic_growth_arrays | true | 2 | 297 | 2800.00 | 16800.00 | 19200.00 | 0.00 | 5474.98 | 413.20 |

## Actual operating transactions

Farm and food factory combined; internal crop transfers retain the market spread. Generator fuel, wages and capital belong to the grid provider and are not added again to the farms' paid electricity.

| Application | Shortage | Materials | Wages | Electricity | Maintenance | Sales | Cash operating result | Construction materials at baseline | Minimum facility cash |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| industrial_soil_cultivation | false | 33168.00 | 93600.00 | 266.40 | 57600.00 | 174720.00 | -9914.40 | 9200.00 | 9856265.60 |
| hydroponic_growth_arrays | false | 68837.14 | 104400.00 | 15138.00 | 72000.00 | 72059.20 | -188315.94 | 44900.00 | 9872189.20 |
| automated_biosphere_macro_farms | false | 157200.00 | 104400.00 | 37488.00 | 201600.00 | 106448.00 | -394240.00 | 165800.00 | 9751248.00 |
| hydroponic_growth_arrays | true | 11956.75 | 104400.00 | 2649.00 | 72000.00 | 10287.99 | -180717.76 | 44900.00 | 9879551.00 |

## Local food cost and capital sensitivity

All operating costs minus actual non-food receipts divided by all food physically produced, including retained unsold food. Processing crop purchases and farm crop receipts retain the real market spread. Capital sensitivity spreads only the quoted farm and processor construction material bill over 5, 10 or 20 years of 360 operating days, without interest or salvage. Work, land and construction service are additional unpriced costs. These are scenario unit costs, not universal application break-even prices.

| Application | Shortage | Operating credits/kg food | With 5-year capital | With 10-year capital | With 20-year capital | Retail break-even with 10-year capital |
|---|---|---:|---:|---:|---:|---:|
| industrial_soil_cultivation | false | 0.70 | 0.73 | 0.71 | 0.71 | 0.89 |
| hydroponic_growth_arrays | false | 13.69 | 14.22 | 13.96 | 13.82 | 17.45 |
| automated_biosphere_macro_farms | false | 9.61 | 10.35 | 9.98 | 9.80 | 12.48 |
| hydroponic_growth_arrays | true | 65.82 | 69.03 | 67.43 | 66.62 | 84.28 |

## Grid provider funding

The fixed electricity tariff is not proof of a cost-covering grid. These are actual generator operating-cash changes after paid fuel, wages, maintenance and electricity receipts; no capital amortization is included. They are reported separately to avoid counting both fuel and purchased farm electricity as the same farm expense.

| Application | Shortage | Generator fuel consumed kg | Generator operating-cash change |
|---|---|---:|---:|
| industrial_soil_cultivation | false | 3600.00 | -1072533.51 |
| hydroponic_growth_arrays | false | 3600.00 | -1057661.91 |
| automated_biosphere_macro_farms | false | 3600.00 | -1035311.91 |
| hydroponic_growth_arrays | true | 3600.00 | -1070150.91 |

No cultivation residue is bought without a downstream consumer. Oxygen sales follow actual breathing and stockpile demand, not the farm's own output. Physical shortages can coexist with funded workers. Prices use the existing spot-price rule, not assumed premium contracts. Fixed population, opening savings, seeded reserves and a well-funded existing generator make this a bounded operating audit rather than a self-starting campaign. No transport occurs within these local probes; delivered imports are measured separately. Unsold stock is retained without modeled spoilage or producer storage costs. Public maintenance is charged; private maintenance remains an integration gap. Reproduction: `mvn -pl engine -Dtest=AgricultureEconomicsAuditTest test` with JDK 27.
