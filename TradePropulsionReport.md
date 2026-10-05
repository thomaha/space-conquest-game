# Catalog trade and production audit

Six 360-day probes use blueprints built by the normal catalog. Local station travel uses deterministic representative geometry and loaded acceleration, coasting and braking. Stationary departures conserve fuel within the earliest funded arrival day; displayed local durations approach that daily boundary. Default 5% main-tank contingency targets remain protected. Crossing probes place stars one light-year apart. Fuel factories use the real paid industry processor with finite input stocks; finished chemical and electrical reactor fuels start at zero in port markets. Hydrogen and MPD propellant have explicit opening stocks. Powered staff are fixture inputs and production pauses on days 20-79; wages and grid generation are not simulated. Opening ship tanks, factory operating cash and buyer cash are explicit seeds. Ports start with opposing steel and copper stocks. No goods, fuel or cash is replenished externally during the run.

| Drive | Destination | Initial next-leg readiness | Unloaded local/crossing days | Departures | Sold kg | Port outage days | Factory output kg | Paid industrial input credits | Voyage unfinished | Final owner cash |
|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|
| mod_chemical_rocket | Local station | true | 1.0000 | 8 | 800 | 0 | 4680000 | 7200.0 | false | 1011964.8 |
| mod_chemical_rocket | 1 light-year | false | 566151166.0000 | 0 | 0 | 0 | 4680000 | 7200.0 | false | 999964.8 |
| mod_fission_thruster | Local station | true | 1.0000 | 8 | 800 | 0 | 4680000 | 7200.0 | false | 1012000.0 |
| mod_fission_thruster | 1 light-year | false | 154272451.0000 | 0 | 0 | 0 | 4680000 | 7200.0 | false | 1000000.0 |
| mod_ion_drive | Local station | true | 2.0000 | 8 | 800 | 0 | 4680000 | 7200.0 | false | 1012000.0 |
| mod_ion_drive | 1 light-year | false | 33379704.0000 | 0 | 0 | 0 | 4680000 | 7200.0 | false | 1000000.0 |

## Findings and tuning candidates

- Chemical, fission thermal and MPD traders each complete eight local shipments and sell all 800 kg of opening goods stock. All six probes avoid movement interruptions and stationary electrical outages while protecting default contingency targets.
- Economical local plans approach one day for chemical and fission thermal propulsion and two days for MPD propulsion. The earliest funded arrival day now accounts for a protected 5% capacity-based main-tank target; planned burn impulse remains minimized within that day.
- One-light-year crossing probes remain unfunded: unloaded crossing plans take about 566.2 million days, 154.3 million days and 33.4 million days respectively. Protected contingency stock is independent of the expected destination approach allocation. Local departure and approach durations are excluded from the displayed crossing estimate.
- Local geometry is frozen and representative. Planetary orbital motion, gravity and launch windows remain future work. Nearby free station fixtures do not establish long-distance planetary travel reliability.
- Port upkeep now targets 60 days of stationary electricity with paid partial purchases and compatible reactor fallback. Port departures also retain 60-day arrival electricity when refill stock is insufficient or travel exceeds two scheduled days, while short stocked trips retain 48 hours. Queued crossing plans now share the post-maneuver electrical projection used by previews. Consecutive-leg allocations, risk-based reserve coefficients and future destination stock forecasting remain tuning candidates. No production coefficient or physical opening stock was changed for this feature. Surplus purchases remain permitted as contingency insurance.
## Initial readiness explanations

- mod_chemical_rocket locally: Next port is reachable with carried electricity for a 60-day wait.
- mod_chemical_rocket at 1 light-year: Waiting: crossing electricity and arrival reserve are insufficient.
- mod_fission_thruster locally: Next port is reachable with carried electricity for a 60-day wait.
- mod_fission_thruster at 1 light-year: Waiting: crossing electricity and arrival reserve are insufficient.
- mod_ion_drive locally: Next port is reachable with carried electricity for a 60-day wait.
- mod_ion_drive at 1 light-year: Waiting: crossing electricity and arrival reserve are insufficient.

## Limits and reproduction

Run `mvn test` with JDK 27; the measured report is regenerated at `engine/target/trade-propulsion-report.md`. Unloaded plan duration is a scale probe, not a funded shipment estimate. Crossing rows report only the crossing duration; local departure and approach are excluded. Journeys longer than the run are not claimed as completed. Assertions protect movement against power interruptions and finite nonnegative fuel and owner cash. Factory output includes all three fuel recipes at both ports; payments and input depletion use actual industry accounting. This fixture does not simulate the full population, power grid, changing prices or industrial workforce economy. No coefficient is tuned to make interstellar chemical or thermal travel artificially viable.

## Further local-distance coverage

[LocalTradeDistanceReport.md](LocalTradeDistanceReport.md) adds sixteen catalog probes using Sol-scale planet and moon geometry, mixed perishable cargo, empty fuel markets and loaded destination waits. It compares complete physical forecasts with actual daily ticks and records equipment and travel-time limits. Representative orbital phases remain frozen and the full economy is not simulated.
