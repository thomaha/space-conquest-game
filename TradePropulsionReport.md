# Catalog trade and production audit

Six 360-day probes use blueprints built by the normal catalog. Local station travel uses provisional site minimums extended by available thrust and loaded mass. Crossing probes place stars one light-year apart. Fuel factories use the real paid industry processor with finite input stocks; finished chemical and electrical reactor fuels start at zero in port markets. Hydrogen and MPD propellant have explicit opening stocks. Powered staff are fixture inputs and production pauses on days 20-79; wages and grid generation are not simulated. Opening ship tanks, factory operating cash and buyer cash are explicit seeds. Ports start with opposing steel and copper stocks. No goods, fuel or cash is replenished externally during the run.

| Drive | Destination | Initial next-leg readiness | Unloaded local/crossing days | Departures | Sold kg | Port outage days | Factory output kg | Paid industrial input credits | Voyage unfinished | Final owner cash |
|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|
| mod_chemical_rocket | Local station | true | 1 | 8 | 800 | 0 | 4680000 | 7200.0 | false | 1011968.6 |
| mod_chemical_rocket | 1 light-year | false | 265556831 | 0 | 0 | 0 | 4680000 | 7200.0 | false | 999972.4 |
| mod_fission_thruster | Local station | true | 1 | 8 | 800 | 0 | 4680000 | 7200.0 | false | 1012000.0 |
| mod_fission_thruster | 1 light-year | false | 64142965 | 0 | 0 | 0 | 4680000 | 7200.0 | false | 1000000.0 |
| mod_ion_drive | Local station | true | 2 | 8 | 800 | 0 | 4680000 | 7200.0 | false | 1012000.0 |
| mod_ion_drive | 1 light-year | false | 13461746 | 0 | 0 | 0 | 4680000 | 7200.0 | false | 1000000.0 |

## Findings and tuning candidates

- Chemical, fission thermal and MPD traders each completed eight local shipments and delivered all 800 kg of the opening two-way goods stock. All six scenarios had no movement interruptions or stationary electrical outages. The unloaded catalog MPD station maneuver takes two days instead of being rejected by the previous one-day limit.
- Fuel factories produced physical output from paid finite inputs. The production pause did not create replacement fuel or drain stores below safe departure requirements in these fixtures.
- None of the three drives was authorized across one light-year. Unloaded plans span approximately 265.6 million days for chemical propulsion, 64.1 million for fission thermal propulsion and 13.5 million for MPD propulsion; current electrical reserves cannot fund them. The MPD departure maneuver is now possible but the complete crossing remains unfunded.
- Local maneuvers use provisional delta-v and site minimums extended by the slowest loaded member's available thrust. They do not yet solve actual orbital distances, departure windows or coast trajectories. No coefficient was changed to force a successful stellar crossing.
- Separate tests compare 120-day local electrical forecasts with actual ticks, check late eclipse failure and arrival reserves and keep a billion-day solar preview bounded. Longer interrupted local travel survives save/load and resumes without another maneuver fuel payment.
- These results identify tuning constraints. They do not establish general long-distance reliability or justify fuel-free movement.

## Initial readiness explanations

- mod_chemical_rocket locally: Local leg requires electricity and an arrival reserve.
- mod_chemical_rocket at 1 light-year: Waiting: crossing electricity and arrival reserve are insufficient.
- mod_fission_thruster locally: Local leg requires electricity and an arrival reserve.
- mod_fission_thruster at 1 light-year: Waiting: crossing electricity and arrival reserve are insufficient.
- mod_ion_drive locally: Local leg requires electricity and an arrival reserve.
- mod_ion_drive at 1 light-year: Waiting: crossing electricity and arrival reserve are insufficient.

## Limits and reproduction

Run `mvn test` with JDK 27; the measured report is regenerated at `engine/target/trade-propulsion-report.md`. Unloaded plan duration is a scale probe, not a funded shipment estimate. Crossing rows report only the crossing duration; local departure and approach are excluded. Journeys longer than the run are not claimed as completed. Assertions protect movement against power interruptions and finite nonnegative fuel and owner cash. Factory output includes all three fuel recipes at both ports; payments and input depletion use actual industry accounting. This fixture does not simulate the full population, power grid, changing prices or industrial workforce economy. No coefficient is tuned to make interstellar chemical or thermal travel artificially viable.
