# Measured lunar supply report

Automated physical freight now secures a funded exit before dispatch. Fuel already aboard may cover the unloaded return; otherwise the trader prepurchases private destination fuel. Other customers cannot consume that stock. The hub buys the committed freight into inventory and the ship claims its fuel after unloading. Save version 46 preserves these reservations.

All five measured scenarios record **zero stationary customer return waits**, compared with 122 days in the earlier full-load acceptance audit. Price-aware refueling fills working tanks at cheap stocked ports and buys smaller safe allowances at expensive or scarce ports. Steady goods consumption increases from 7,450 kg to 11,650 kg compared with net ranking alone; growth consumption increases from 9,050 kg to 25,850 kg.

## Experiment

`MeasuredLunarSupplyAuditTest` runs five 240-day scenarios with identical finite inventory and six ships. An independent hydrolox customer delivers ordinary goods and returns alongside three supplier routes and a finite aluminum backhaul. Every scenario starts with 18,000 kg of each depot good and an additional finite 3,000,000 kg of low-Earth hydrolox. Initial battery charging is paid. No production or cash injection occurs.

Days 1–60 establish history with manual targets and requests for 25 kg/day of each good. Measured suggestions are applied after day 60. Growth requests 75 kg/day of each good from day 91 and reviews suggestions after day 120. Supplier dispatch pauses start on day 91 and last 30 or 70 days. Committed flights continue. Missing supplier timing uses reviewed four-day depot and fourteen-day lunar inputs.

Private fuel counts in physical conservation and hub storage, but stays outside public market orders. Its prepurchase records a public withdrawal; claiming it records no second purchase or withdrawal. Freight remains separate and mixed or partial loads remain allowed.

## Results

Goods quantities cover the whole run and combine steel and food matrix. Customer events and waits cover the 180 days after review.

| Scenario | Safety days | Supplier pause days | Goods consumed kg | Unserved goods kg | Customer sale events | Customer return wait days | First supplier delivery after resumption |
|---|---:|---:|---:|---:|---:|---:|---|
| Steady | 2 | 0 | 11,650 | 350 | 4 | 0 | Not applicable |
| Growth | 2 | 0 | 25,850 | 1,150 | 5 | 0 | Not applicable |
| Pause | 2 | 30 | 11,650 | 350 | 5 | 0 | Day 127, six days after resumption |
| Larger allowance | 30 | 30 | 11,650 | 350 | 4 | 0 | Day 127, six days after resumption |
| Long pause | 2 | 70 | 10,450 | 1,550 | 5 | 0 | Day 167, six days after resumption |

The steady run serves 11,650 of 12,000 kg requested goods. Growth serves 25,850 of 27,000 kg. The long pause still leaves 1,550 kg unserved. Customer sale events are manifest settlements and need not correspond one-to-one with trips.

There are 25–39 mixed fuel/goods departures and 41–48 partial loads. Peak depot physical stock plus paid inbound is 10,457,981 kg out of 12,000,000 kg, about 87.15%. The lunar peak is 470,600 kg out of 8,000,000 kg, about 5.88%. Neither hub reaches physical capacity.

Supplier selection scores net public replenishment after protected destination fuel purchases. It counts withdrawals of other scarce fuel ingredients too and compares smaller protected previews when a profitable full load withdraws scarce stock. The new stock and price policy increases traffic: steady consumed fuel is 4,342,392 kg, growth 4,448,239 kg, pause 4,309,791 kg, larger allowance 4,264,168 kg and long pause 3,987,981 kg. Higher total consumption accompanies substantially more served goods; this is not a reduction in total fuel use.

## Remaining limits

In the steady scenario, lunar usable hydrolox remains above 21,572 kg and fission hydrogen above 56,394 kg. Upstream depot hydrolox is effectively empty for 77 days after review. Safe smaller refills improve lunar supply, but finite upstream stocks still limit dispatch and the long pause still causes unmet demand. Initial resumed deliveries do not prove indefinite throughput.

Measured withdrawals include private fuel prepurchases. Blocked dispatches still suppress observed demand, and the rolling history cannot infer all unserved transport requirements. Tiny ingredient remnants can also suppress stockout flags despite having no useful mixture coverage. Thresholds remain reviewed planning estimates.

Cheap full refills require enough public working fuel to preserve reorder buffers and a compatible refill cost within 10% of the cheapest stocked operational port. Other refills reduce added main propellant, coupled drive feed and electrical fuel through bounded complete-leg checks with two percent allowance headroom. Real inventory and cash still fund every purchase. The next supply work concerns upstream production and replenishment capacity; raising thresholds alone cannot make finite fuel last indefinitely.

## Verification and reproduction

The audit checks gross withdrawal and delivery history against an independent daily ledger, rolling windows, finite fuel and goods conservation, shared storage with paid inbound, cash conservation and exact save/reload. It explicitly requires zero stationary customer return waits in every scenario.

`TradeFuelEconomyTest` verifies cheap full tanks, expensive safe smaller allowances and buffer preservation. `TradeReturnFuelSizingTest` checks reduced propellant, limited real stock and pure forecasts. `TradeNetFuelTest` compares protected supplier loads and scarce-ingredient penalties. `TradeReturnFuelTest` completes an actual return after another customer buys every public fuel ingredient and verifies ownership, companion ships, payment, rollback and persistence. `HubBulkPurchaseTest` retains full mixed-load sales above current targets, including tiny lots.

Run `mvn test` with JDK 27. The detailed generated report is `engine/target/measured-lunar-supply-report.md`. Initial inventory, upstream production, initial lift, capital and overhead remain outside this operational experiment. The policy covers the forecast exit with working equipment; arbitrary dwell, combat damage or changed orders require new readiness checks.
