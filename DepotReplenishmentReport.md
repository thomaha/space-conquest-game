# Depot replenishment report

## Scope

DepotReplenishmentAuditTest exercises three paid replenishment cycles over 57 simulation days. The 2,000 km Earth depot starts with no fuel or cargo. A single supplier buys and transports its supplies from a finite-stock 500 km Earth port. Three catalog hydrolox freighters start empty at the depot and each make one explicit one-way shipment to 500 km Moon parking after replenishment.

The supplier uses a validated catalog RP-1 blueprint with a 30,000 kg pressurized cargo vault, two standard main tanks totaling 30,000 kg, two solar arrays and a 500 kWh battery. It fits the 30-slot medium hull. Every delivery and its empty return use funded parking maneuvers. The depot holds no RP-1, so the supplier carries enough main fuel for its actual return and refills only at the source.

Opening source stock contains 360,000 kg of hydrolox mixture for delivery, 600,000 kg of RP-1 mixture for supplier propulsion and 7,500 kg of mixed goods. Source fuel costs 0.005 credits/kg and goods cost 0.8 credits/kg. Depot prices are 0.01 credits/kg for hydrolox ingredients and 1 credit/kg for goods. Moon goods prices are 3 credits/kg with the existing 80% wholesale share. These are explicit price sensitivity fixtures rather than changes to catalog balance.

Ports, ships, source stock, grid storage and opening cash are fixtures. No cargo or cash is added after opening. The upstream supply contracts are dispatched explicitly through paid shipment previews; lunar loading uses an explicit one-way preview with ordinary price-aware current-port refueling. This establishes repeated depot supply using existing mechanics rather than autonomous supply contracting or source production.

## Physical supply chain

Each new carrier begins with a three-day wait for goods at the depot. The new lunar carrier cannot load or depart and spends no purchase cash during that wait. The supplier then makes four 30,000 kg hydrolox deliveries and one 2,500 kg mixed-goods delivery. Fuel arrivals alone cannot create a goods shipment. The paid goods arrival allows the waiting lunar trader to buy its shipment and fuel, then depart on its explicit one-way order.

Across all cycles, the supplier makes fifteen paid deliveries and fifteen funded empty returns. It delivers 367,500 kg to the depot: 360,000 kg of compatible hydrolox ingredients and 7,500 kg of goods. All three lunar shipments sell their complete 2,500 kg loads. Each lunar carrier retains its protected main propellant reserve. Expensive-port refueling leaves public depot fuel for later waves; the third wave can attempt a full refill when cheaper upstream hydrolox no longer has sufficient stock.

| Completed wave | Campaign day | Lunar goods delivered kg | Supplier propulsion consumed kg | Lunar propulsion consumed kg |
|---|---:|---:|---:|---:|
| 1 | 19 | 2,500 | 131,312.63 | 80,317.99 |
| 2 | 38 | 5,000 | 262,625.26 | 160,635.98 |
| 3 | 57 | 7,500 | 393,937.89 | 248,930.92 |

The supplier consumes more main propellant across its delivery and return flights than the three lunar carriers consume on their outbound journeys. Depot access is therefore a substantial part of the supply budget. The three lunar ships remain at the Moon after selling; this audit does not establish their refueling or return.

The follow-up [lunar round-trip report](LunarRoundTripReport.md) measures paid lunar replenishment and three goods cycles by the same freighter, including supplier and tanker returns. Its feasible supply design loses money once these additional physical costs are included.

## Economics and conservation

The consolidated chain consists of the supplier treasury, trader treasury and depot market account. Supplier sales to the depot and trader purchases from it are internal transfers. The Moon buyer pays 18,000 credits in total. External payments cover source goods, source propellant and commissioning electricity from the separate port operator.

| Completed wave | Chain cash change | Retained fuel value | Consumption result |
|---|---:|---:|---:|
| 1 | 2,663.25 | 234.15 | 2,897.40 |
| 2 | 5,406.69 | 432.56 | 5,839.25 |
| 3 | 8,150.12 | 591.09 | 8,741.21 |

Retained fuel is valued at its source acquisition price of 0.005 credits/kg. Adding that inventory to cash change gives the consumption result. Unused stock at the original source is outside the chain and is not credited as profit. The 44.44-credit commissioning charge covers all four ships and is paid before the first wave.

The final consumption result independently equals 18,000 credits of sales minus 6,000 credits of source goods, 3,214.34 credits of consumed supplier and lunar propellant and 44.44 credits of electricity. At fixed measured quantities and goods prices, the result breaks even at approximately 0.01860 credits/kg of source propellant before other costs. This is arithmetic sensitivity rather than a proposed fuel price.

Cash conservation includes every empire treasury and market account. Physical checks reconcile all source withdrawals with delivered cargo, consumed propellant, remaining working tanks and retained public depot fuel. No essential electrical deficit, cargo power deficit or cargo loss occurs in the measured run. Buyer accounts remain nonnegative and no supply delivery is recorded before depot docking.

## Verification and limits

Every outbound supply order and each completed wave survives save/load with fleets, routes, markets, cash and grid stores preserved. A separate rejection test starts from a funded valid supplier and proves that an incomplete source mixture, exhausted depot buyer cash or an insolvent supplier rejects the complete shipment without partial purchase or departure.

Run `mvn test` with JDK 27. The detailed generated artifact is `engine/target/depot-replenishment-report.md`.

The engine regression reports include the refreshed depot audit and price-aware refueling checks.

Source production, surface lift, ship and station construction, wages, depreciation, taxes, autonomous supply contracting and lunar refueling or return are outside this audit. Circular parking transfers and lunar encounter geometry retain their documented approximations. This is a finite repeatability measurement using three outbound carriers, not an indefinitely sustainable closed loop.
