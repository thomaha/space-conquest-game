# Agricultural economics and delivered food costs

Subsequent implementation: [SupplyPricing.md](SupplyPricing.md) connects local and delivered offers, target profit after corporate tax and actual generator tariffs. The cost tables below retain the earlier fixed-tariff review baseline; the new operating report is linked from that implementation note.

Reviewed on 16 October 2026. Enclosed farming does not need to beat outdoor farming at ordinary food prices. Its useful comparison is with the cost and availability of food at the destination, including offworld transport. This review checks real-world scales, actual game operating costs, finite buyers and repeated growing cycles.

## Findings and corrections

Two accounting assumptions were corrected. Market demand no longer includes a producer's expected outputs merely because it produces them. Households, downstream inputs, construction, ship supplies and explicit inventory targets still create demand. Oxygen can be sold to breathing residents or reserve replenishment; cultivation residue has no buyer in the tested economy and remains with its producer. Ammunition and refinery settlement tests now configure explicit buyer stock requests rather than relying on production to create demand.

Surface export now passes body gravity through an explicitly named m/s² calculation. The previous ambiguous unit heuristic treated Moon and Mars values as multiples of Earth gravity. Corrected launch-service probes consume actual fuel and pay actual providers. The older convenience calculation retains its existing unit convention; current body-backed freight uses the explicit SI entry point.

Application yields, material prices and energy coefficients have not been changed to make farming profitable. The comparisons below expose remaining assumptions and price-formation gaps rather than hiding them in larger yields or cheaper machinery.

## Real-world scale review

| Item | Current game assumption | Assessment |
| --- | --- | --- |
| Outdoor crop yield | 14.4 t food and 14.4 t harvested crops from 6 ha per 120-day cycle | 4.8 t/ha combined primary products per cycle. This mixed basket is not a measured wheat harvest and repeating three cycles ignores real seasonal calendars. |
| Hydroponic crop yield | 1.5 t combined food and crops over 1,000 m² and 28 days | 53.6 g/m²/day before processing. A high dry-biomass rate rather than a wet-lettuce comparison. |
| Biosphere crop yield | 6 t combined food and crops over 4,000 m² and 42 days | 35.7 g/m²/day before processing. More total output and a slower area-normalized rate than the smaller application. |
| Artificial light | 1.6 kWh/m²/day for hydroponics and 0.8 for biospheres | Optimistic with these dry-food yields. Neither application defines light spectrum, photon efficacy or photoperiod, so exact yield validation is not possible. |
| Purchased nutrients | Hydroponics buys 625 kg nitrates, 20 kg phosphates and 20 kg potash per cycle; biospheres buy four times these amounts | Element accounting is checked against game assays. It does not establish a biologically complete nutrient solution. |
| Internal water circulation | 3.57 kg/m²/day in hydroponics and 2.38 in biospheres | Plausible order of magnitude for a circulation/transpiration comparison, not a measured crop requirement. |
| Water recovery | 98% and 98.5%, with separate initial fill and recurring makeup | Internal reuse creates no saleable water. Makeup also supplies atoms incorporated into products; it is not all waste. |
| Work and automation | Four reference farmers for each enclosed line, with 25% and 75% autonomous work | Defined work rates rather than person-hour prices. Staffing counts and technical-skill requirements remain provisional. |
| Construction | Declared enclosure materials plus installed water and oxygen charges | A paid game material bill, not a complete engineered habitat or a validated real-currency estimate. |

[NASA's controlled wheat experiments](https://ntrs.nasa.gov/citations/19880029853) report edible biomass rates of 23–57 g/m²/day with artificial-light electricity around 600 W/m². This supports the scale of the game yields under intensive conditions, while exposing the much more optimistic lighting allowance. The [underlying study](https://ntrs.nasa.gov/api/citations/19880002888/downloads/19880002888.pdf) discusses solution circulation and mineral replenishment. It uses older lighting equipment and does not validate the game's mixed crop basket or future equipment.

A separate [NASA wheat gas-exchange study](https://ntrs.nasa.gov/citations/19900016137) measured average transpiration around 4.5 L/m²/day and net carbon dioxide uptake around 43 g/m²/day over its particular growing period. The game hydroponic recipe fixes about 106 g CO₂/m²/day and biospheres about 71. This comparison flags optimistic carbon fixation, differing growing periods and crop definitions; it is not a direct calibration between identical crops.

[USDA's controlled-environment agriculture review](https://ers.usda.gov/publications/108220) describes substantial energy and capital costs, specialized crops and uncertain competitiveness for staples. Its cited vertical-farm startup range of $150–400/ft² is roughly $1,615–4,306/m², compared with about $538–1,615/m² for greenhouses. These are context-dependent money estimates, not construction material quantities or a currency conversion for game credits. Automation can reduce labor demand while raising capital requirements. This supports retaining expensive enclosed farming where its alternatives are cheaper.

[USDA nutrient guidance](https://www.ars.usda.gov/ARSUserFiles/50820500/Publications/Frantz188004_2005_GrnHseNutr.pdf) discusses continuous-feed nitrogen concentrations around 100–200 mg/L for its example crops. A solution concentration cannot be substituted for the total nitrogen incorporated over a game cycle: fresh nutrient additions, retained salts, working inventory and discharge have different roles. Current food assays omit detailed crop minerals and the process does not model nutrient concentration, pH, micronutrients or contamination. Those need better crop and solution definitions before changing the nutrient bill scientifically.

The existing 28-day lettuce reference supports one crop's growing interval, not the present dry-food yield. Food and harvested crops should be treated consistently as coarse nutritional commodities in this comparison. Fresh-produce premiums, perishability and different crop values remain possible later extensions rather than assumptions used to justify profitability here.

## Local production with actual buyers

The repeatable [production and household audit](AgricultureEconomicsReport.md) runs four 360-day cases with 1,000 human residents, one farm, one dedicated food processor and an existing fueled generator. Households actually buy food and oxygen, workers receive funded wages and farms pay for their finite inputs and electricity. Initial food covers the first outdoor growing interval. No external deliveries or cash injections occur. Public maintenance is charged and public operating subsidies are disabled. Fixed population and known research keep the comparison controlled.

| Local method | Completed harvests | Food produced | Unmet household food | Cost/kg with 10-year material-capital allowance |
| --- | ---: | ---: | ---: | ---: |
| Outdoor soil cultivation | 3 | 66,240 kg | 0 kg | 0.71 credits |
| Hydroponic growth arrays | 12 | 16,800 kg | 5,200 kg | 13.96 credits |
| Automated biosphere macro farms | 8 | 44,800 kg | 0 kg | 9.98 credits |
| Hydroponics with nitrate exhaustion | 2 | 2,800 kg | 19,200 kg | 67.43 credits |

Unit costs include actual material purchases, wages, electricity and maintenance, less actual non-food receipts. Construction materials are spread over ten years of 360 operating days as an explicit comparison assumption; the report also gives five- and twenty-year cases. Land, construction work, financing, spoilage and producer storage costs are additional. The dedicated processor remains paid when it has no crops, so a shared processor or different staffing decision can change the result. Research modifiers, reserve stocks and local prices also matter.

Produced food includes unsold producer stock. A low unit cost does not guarantee a positive current cash result when production exceeds demand. The final outdoor harvest also leaves crops awaiting processing after the horizon. Nitrate exhaustion reduces hydroponic harvests to two and leaves 297 idle days; funded staffing cannot replace missing nutrients. Oxygen sells only as residents or reserves require it. No cultivation residue sells in any case.

The fixed electricity tariff is a separate limitation. The report records the generator's actual fuel use and operating-cash change. A funded generator can operate at a loss from its opening reserve. Farm electricity prices therefore do not demonstrate that the combined energy and food economy covers its full costs. Fuel costs must not be added twice to a farm that already paid the electricity provider.

## Imported food with existing transport costs

The [food-import audit](FoodImportCostReport.md) buys 2,500 kg food at 4 credits/kg and fuels at current baseline prices. Working tanks begin empty. Four funded voyages execute real daily power and movement ticks, deliver their cargo and sell to funded buyers. A fifth configuration cannot fund departure. Empty-return costs are quoted after actual arrival and paid delivery; the return voyages themselves are not executed.

| Carrier and destination | Outbound duration | One-way landed cost/kg | Retail price needed to cover food, consumed fuel and quoted empty return |
| --- | ---: | ---: | ---: |
| Hydrolox, another Earth station | 2 days | 117.86 credits | 310.44 credits |
| Hydrolox, Moon station | 7 days | 159.79 credits | 413.74 credits |
| Hydrolox, Mars station | 871 days | 210.11 credits | 525.92 credits |
| Fission thermal, Moon station | 7 days | 15.57 credits | 34.05 credits |
| Fission thermal, Mars station | Unavailable | Unavailable | Unavailable |

One-way landed cost includes source food and consumed outbound working fuel. The retail comparison also allows for the ordinary 80% wholesale settlement share. Crew, maintenance, capital recovery and financing are additional. Full working-tank purchases are not treated as consumed voyage expense: unused reserves remain physical assets. Ship construction material quotes are recorded separately. Backhaul cargo could change the empty-return comparison.

These voyages start at an Earth station and end at another station. Separate [paid surface-export probes](FoodSurfaceExportReport.md) add about 8.58 credits/kg for the existing Earth rocket service, 1.19 for the Moon and 2.83 for Mars. They settle fuel and provider payment but do not execute the subsequent orbital voyage or final landing. Earth-to-Moon imports originating on the ground therefore have an indicative cost of at least 24.15 credits/kg before the empty return, destination landing and other overhead, using these two separate service measurements. These are current game estimates rather than validated launch prices: the service retains provisional carrier, atmosphere and specific-impulse assumptions.

The local ground biosphere example at 9.98 credits/kg can consequently beat these imported-food alternatives despite losing money at baseline food prices. Hydroponics can also be useful where outdoor farming is impossible and imports are expensive. These are conditional comparisons, not a claim that every location or crop favors enclosed production. Orbital enclosure construction, grid capital and complete surface logistics need their own site budgets.

Local input availability is decisive. Each hydroponic cycle purchases 5,635 kg of water, gases and nutrients for 1,400 kg of food after crop processing; biospheres purchase 20,540 kg for 5,600 kg food. Shipping every recurring input from the food-exporting world can require more cargo mass than importing food. Water extraction, locally available carbon dioxide and eventual habitat recycling matter. The operating cases use finite delivered opening stocks and do not claim a replenishable local source for every input. Gas containment and transport can also differ from packaged food, so the food freight rate must not simply be assigned to every input material.

## Remaining economic decisions

The current spot rule approaches twice the base price under extreme scarcity. Food therefore approaches 8 credits/kg, below the measured enclosed-farm retail break-even values of 17.45 and 12.48 and far below the lunar import return-cost example. This is a price-formation gap: transport expense exists, but it does not establish the destination's sustainable market price. Removing artificial output demand fixes one distortion without solving this cap.

The next implementation should connect generic destination procurement and market prices to real local production offers and funded delivered offers, including the same resource and ownership constraints used for settlement. It should preserve scarcity when no affordable offer exists. Ordinary food need not receive a global price increase and enclosed farms need no guaranteed profit. Input freight, viable staffing and energy-provider costs belong in those offers.

Other open assumptions are optimistic lighting and carbon fixation, incomplete crop mineral assays, private-facility maintenance, whole-habitat construction costs, seasonal outdoor yields and spoilage. At four times the present lighting energy, the added lighting bill alone would be about 1.92 credits/kg for the hydroponic operating case and 1.44 for biospheres at the fixed electricity tariff. This is a sensitivity calculation, not a new physical calibration or an endorsed tariff. A complete light-to-food and grid-cost model is preferable to assigning a replacement coefficient from an unrelated crop experiment.

## Verification

The new audits check actual food conservation, finite market stock, funded payroll, paid generators, growing intervals, shortages, unsold byproducts, consumed transport fuel, paid delivery and surface-export settlement. Existing research-to-construction and save/load checks remain applicable. Generated reports contain commands and scenario limits.

Scoped verification passed 310 tests across 58 engine, control and frontend suites with no failures, errors or skipped tests in their final results. This includes the four 360-day agricultural scenarios, five food-import probes, three paid surface-export probes, explicit SI gravity, household and market settlement, AI supply and construction, research, current-format saves and frontend rendering. The full Maven suite was not run. Source whitespace checks also passed.
