# Trade reliability audit

## Scope

Deterministic 360-day scenarios use modeled chemical freighters, actual power and movement ticks and paid logistics. The fixture injects production, destination consumption and buyer liquidity every 12 days. Fuel replacement prices stay at 0.001 credits/kg. Shocks explicitly alter goods prices, fuel stocks or buyer cash. Crossings cover one million meters rather than full light-year distances. No tanker rescue is provided.

## Findings

- No interrupted journeys or stationary electrical outages occurred in these scenarios.
- All three competing traders completed 20 shipments each. The longest wait was 28 days; dispatch priority now rotates behind waiting routes instead of repeatedly favoring the first trader.
- Expected and realized completed profit agree within 0.1 credits in stable markets. The price-collapse scenario realized 1,520 credits less than its departure estimate because sale prices changed while cargo was aboard.
- Fuel shortages produced waits of up to 57 days. Traders resumed deliveries when usable supplies returned; no tanker rescue or fuel-free movement was used.
- Waiting traders now buy real electrical supplies independently of new cargo purchases. These purchases remain conditional on stock, funds and tank capacity.
- The mixed-load trader completed 120 shipments carrying steel and copper together under one 100 kg limit. It delivered 12,000 kg with no interrupted journeys or port outages. Purchase and fuel costs enter the ledger only for the selected candidate.
- [The catalog and production audit](TradePropulsionReport.md) complements these nine scenarios with actual refinery transactions and realistic star separation probes.

## Reproduction

Run the Maven test suite from the repository root with JDK 27:

```powershell
$env:JAVA_HOME='C:\java\jdk-27'
mvn test
```

`TradeReliabilityAuditTest` regenerates the measured report at `engine/target/trade-reliability-report.md`. This file records that output with findings and reproduction instructions. The audit assertions check repeated deliveries, finite nonnegative working stores, successful shipments for every competing trader and physical journey interruptions. Separate tests check real paid idle upkeep and dispatch ordering.

## Results

| Scenario | Ships | Departures | Sales completed | Delivered kg | Wait ship-days | Longest wait days | Interrupted fleets | Port outage ship-days | Main fuel kg | Electrical fuel kg | Expected completed profit | Realized completed profit | Sale-day cash result | Total route cash result | Unfinished trips |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Fixed local baseline | 1 | 120 | 120 | 12000 | 120 | 1 | 0 | 0 | 2955.0 | 20000.0 | 179989.9 | 179989.9 | 179999.9 | 179977.2 | 0 |
| Roaming local baseline | 1 | 180 | 180 | 18000 | 0 | 0 | 0 | 0 | 2309.9 | 17142.9 | 269984.8 | 269984.8 | 270000.0 | 269983.4 | 0 |
| Roaming crossing baseline | 1 | 90 | 90 | 9000 | 0 | 0 | 0 | 0 | 88377.8 | 17213.5 | 134896.5 | 134896.6 | 135000.0 | 134894.6 | 0 |
| Fixed fuel shortages | 1 | 21 | 20 | 2000 | 237 | 57 | 0 | 0 | 39383.4 | 12412.4 | 29977.0 | 29977.0 | 29978.3 | 29947.3 | 1 |
| Roaming fuel shortages | 1 | 36 | 36 | 3600 | 216 | 56 | 0 | 0 | 35351.3 | 12028.3 | 53958.6 | 53958.6 | 54000.0 | 53952.8 | 0 |
| Roaming price collapse | 1 | 101 | 100 | 10000 | 159 | 40 | 0 | 0 | 1315.9 | 13333.3 | 149991.5 | 148471.5 | 148480.0 | 148469.4 | 1 |
| Roaming buyer cash drought | 1 | 80 | 80 | 8000 | 200 | 50 | 0 | 0 | 1033.0 | 12381.0 | 119993.3 | 119993.3 | 120000.0 | 119989.4 | 0 |
| Fixed mixed-load trader | 1 | 120 | 120 | 12000 | 120 | 1 | 0 | 0 | 2955.0 | 20000.0 | 179989.9 | 179989.9 | 179999.9 | 179977.2 | 0 |
| Three competing roaming traders | 3 | 60 | 60 | 4800 | 840 | 28 | 0 | 0 | 58919.8 | 31475.8 | 71931.0 | 71931.0 | 72000.0 | 71911.2 | 0 |

## Waiting reasons

- Fixed local baseline: {No viable profitable trade or source stock=120}
- Roaming local baseline: {}
- Roaming crossing baseline: {}
- Fixed fuel shortages: {No viable profitable trade or source stock=20, Supplies or next-leg readiness=217}
- Roaming fuel shortages: {No viable profitable trade or source stock=216}
- Roaming price collapse: {No viable profitable trade or source stock=159}
- Roaming buyer cash drought: {No viable profitable trade or source stock=200}
- Fixed mixed-load trader: {No viable profitable trade or source stock=120}
- Three competing roaming traders: {No viable profitable trade or source stock=840}

## Per-trader completed shipments

- Fixed local baseline: {route_0=120}
- Roaming local baseline: {route_0=180}
- Roaming crossing baseline: {route_0=90}
- Fixed fuel shortages: {route_0=20}
- Roaming fuel shortages: {route_0=36}
- Roaming price collapse: {route_0=100}
- Roaming buyer cash drought: {route_0=80}
- Fixed mixed-load trader: {route_0=120}
- Three competing roaming traders: {route_0=20, route_1=20, route_2=20}

## Interpretation and limits

Expected profit prices consumed working fuel at departure. Realized completed profit uses actual sale revenue, cargo cost and consumed working fuel through sale; idle-before-loading and empty-return fuel are excluded from that comparison. Sale-day cash results charge cargo and any same-day resupply or empty return purchases. Total route cash also includes tank top-ups with usable leftovers. These columns are not directly interchangeable. Waiting can drain stationary electrical stores even at a safe port. Interruptions count physical failed itineraries; port outages are reported separately. Main fuel totals include prepaid local burns, reconstructed from physical market withdrawals and working-store changes. This audit excludes full economy production, diplomacy, passengers, combat, solar propulsion and market reservations.
