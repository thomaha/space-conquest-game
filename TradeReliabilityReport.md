# Trade reliability audit

## Scope

Deterministic 360-day scenarios use modeled chemical freighters, actual power and movement ticks and paid logistics. The fixture injects production, destination consumption and buyer liquidity every 12 days. Fuel replacement prices stay at 0.001 credits/kg. Shocks explicitly alter goods prices, fuel stocks or buyer cash. Crossings cover one million meters rather than full light-year distances. Default 5% main-tank contingency targets are protected; automated traders never use emergency overrides. Port upkeep targets 60 days of stationary essential and cargo electricity, with paid partial purchases when stock or cash is limited. Port departures carry a 60-day arrival allowance when destination refill stock is insufficient or total scheduled travel exceeds two days. Short stocked trips retain the 48-hour arrival requirement. No tanker rescue is provided.

## Findings

- Default 5% capacity-based main-tank contingency targets remain protected. Automated traders never use emergency overrides. No interrupted voyages or stationary electrical outages occur in the nine scenarios.
- Routine fixed and mixed-load routes complete 120 deliveries carrying 12,000 kg. Routine roaming completes 180 deliveries carrying 18,000 kg. All three competing traders complete 20 shipments each.
- Routine roaming consumes 1,641.9 kg of main fuel and fixed local routes consume 2,603.6 kg over 360 days. Changed carried electrical mass and departure insurance can alter maneuver costs, departure timing and top-up decisions; the propulsion coefficients and opening stocks are unchanged.
- The short crossing baseline completes 89 deliveries with one trip unfinished at day 360. Contingency protection reduces available burn fuel and can extend coasting while keeping braking funded.
- Fixed fuel shortages complete 11 deliveries moving 1,016 kg, with one shipment unfinished. Their longest readiness wait is eight days compared with 47 before departure dwell insurance and the crossing projection fix. Roaming shortages complete 21 deliveries moving 1,925 kg, with no unfinished shipment. Their longest wait falls from 28 days to seven and port outage ship-days fall from 26 to zero.
- Queued crossings previously planned with electrical stores from before the departure maneuver while previews consumed that maneuver's electricity first. Near the main propulsion reserve limit, that mass difference could substantially prolong a crossing and drain its actual arrival electricity. Queued plans now use the same post-maneuver projection as readiness and cost previews. A regression test checks their duration, acceleration, peak speed and fuel allocation.
- Port upkeep targets a provisional 60-day stationary electrical reserve with a 5% purchase energy margin. Port departures require carried electricity for a 60-day wait after the complete journey when destination refill stock is insufficient or total scheduled travel exceeds two days. Short stocked trips retain 48 hours. Current destination stock is an advisory snapshot, not a reservation or future production forecast. Committed journeys retain their funded approach checks.
- Expected and realized completed profit agree within 0.1 credits in stable unshocked markets. The price-collapse scenario realizes 1,520 credits less than its departure estimate. Paid leftovers remain aboard and route cash accounting charges actual top-ups independently of consumed-fuel profit estimates.
- Main top-ups purchase the largest affordable amount up to tank capacity. Carried surplus remains useful insurance. Electrical upkeep and dwell checks never borrow protected main fuel or require a return itinerary.
- Nearby station geometry remains representative. These results do not establish planet-to-planet reliability or prove immunity to future shortages on short stocked trips or waits exceeding 60 days. [The catalog and production audit](TradePropulsionReport.md) adds paid refinery transactions and realistic star separation probes. [FleetSupply.md](FleetSupply.md#trader-departure-dwell-insurance) records the live dwell rules and [ContingencyFuel.md](ContingencyFuel.md) records the main reserve policy.

## Measured results

| Scenario | Ships | Departures | Sales completed | Delivered kg | Wait ship-days | Longest wait days | Interrupted fleets | Port outage ship-days | Main fuel kg | Electrical fuel kg | Expected completed profit | Realized completed profit | Sale-day cash result | Total route cash result | Unfinished trips |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Fixed local baseline | 1 | 120 | 120 | 12000 | 120 | 1 | 0 | 0 | 2603.6 | 8573.9 | 179995.8 | 179995.8 | 180000.0 | 179992.1 | 0 |
| Roaming local baseline | 1 | 180 | 180 | 18000 | 0 | 0 | 0 | 0 | 1641.9 | 8573.0 | 269994.1 | 269994.1 | 269996.9 | 269993.0 | 0 |
| Roaming crossing baseline | 1 | 90 | 89 | 8900 | 0 | 0 | 0 | 0 | 25207.4 | 8595.6 | 133468.6 | 133468.6 | 133500.0 | 133466.0 | 1 |
| Fixed fuel shortages | 1 | 12 | 11 | 1016 | 35 | 8 | 0 | 0 | 5203.9 | 8576.4 | 15226.9 | 15226.9 | 15232.6 | 15218.9 | 1 |
| Roaming fuel shortages | 1 | 21 | 21 | 1925 | 21 | 7 | 0 | 0 | 5165.0 | 8576.4 | 28857.9 | 28857.9 | 28870.6 | 28857.0 | 0 |
| Roaming price collapse | 1 | 101 | 100 | 10000 | 159 | 40 | 0 | 0 | 903.4 | 8572.3 | 149996.7 | 148476.7 | 148478.4 | 148473.9 | 1 |
| Roaming buyer cash drought | 1 | 80 | 80 | 8000 | 200 | 50 | 0 | 0 | 741.4 | 8572.1 | 119997.4 | 119997.4 | 119998.6 | 119993.9 | 0 |
| Fixed mixed-load trader | 1 | 120 | 120 | 12000 | 120 | 1 | 0 | 0 | 2603.6 | 8573.9 | 179995.8 | 179995.8 | 180000.0 | 179992.1 | 0 |
| Three competing roaming traders | 3 | 60 | 60 | 4800 | 809 | 28 | 0 | 0 | 13279.3 | 25727.0 | 71981.7 | 71981.7 | 72000.0 | 71964.5 | 0 |

## Waiting reasons

- Fixed local baseline: {No viable profitable trade or source stock=120}
- Roaming local baseline: {}
- Roaming crossing baseline: {}
- Fixed fuel shortages: {No viable profitable trade or source stock=11, Supplies or next-leg readiness=24}
- Roaming fuel shortages: {No viable profitable trade or source stock=21}
- Roaming price collapse: {No viable profitable trade or source stock=159}
- Roaming buyer cash drought: {No viable profitable trade or source stock=200}
- Fixed mixed-load trader: {No viable profitable trade or source stock=120}
- Three competing roaming traders: {No viable profitable trade or source stock=809}

## Per-trader completed shipments

- Fixed local baseline: {route_0=120}
- Roaming local baseline: {route_0=180}
- Roaming crossing baseline: {route_0=89}
- Fixed fuel shortages: {route_0=11}
- Roaming fuel shortages: {route_0=21}
- Roaming price collapse: {route_0=100}
- Roaming buyer cash drought: {route_0=80}
- Fixed mixed-load trader: {route_0=120}
- Three competing roaming traders: {route_0=20, route_1=20, route_2=20}

## Interpretation and limits

Expected profit prices consumed working fuel at departure. Realized completed profit uses actual sale revenue, cargo cost and consumed working fuel through sale; idle-before-loading and empty-return fuel are excluded from that comparison. Sale-day cash results charge cargo and any same-day resupply or empty return purchases. Total route cash also includes tank top-ups with usable leftovers. These columns are not directly interchangeable. Waiting can drain stationary electrical stores even at a safe port. Interruptions count physical failed itineraries; port outages are reported separately. Main fuel totals include actual physical local burns, reconstructed from physical market withdrawals and working-store changes. This audit excludes full economy production, diplomacy, passengers, combat, solar propulsion and market reservations.

## Further local-distance coverage

[LocalTradeDistanceReport.md](LocalTradeDistanceReport.md) adds sixteen catalog probes using Sol-scale planet and moon geometry, mixed perishable cargo, empty fuel markets and loaded destination waits. It compares complete physical forecasts with actual daily ticks and records equipment and travel-time limits. Representative orbital phases remain frozen and the full economy is not simulated.
