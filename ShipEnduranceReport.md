# Ship endurance balance report

## Reproduce the report

From the repository root with JDK 27 selected:

```powershell
mvn --% -q -pl control -am -Dtest=ShipEnduranceAuditTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The default output is `control/target/ship-endurance-report.md`. Add `-Dendurance.report=../ShipEnduranceReport.md` to refresh this checked-in report. Rows and arithmetic are deterministic; there is no wall-clock timestamp.

## Scope and assumptions

All blueprints pass the normal catalog builder with unoptimized steel hulls, a 500 kWh fully charged battery, a 15,000 kg full propulsion tank and the workbench cargo vault. Fueled sources use a full dedicated compartment except the explicitly under-supplied row. Food loads start at 1,000 kg except the 30,000 kg full-hold row; empty holds use standby draw. Stasis rows use one occupied 100-seat pod and a tier 3 manufacturing fixture. Solar stasis requires two panels to pass the builder's 1 AU static power check. Passenger counts are fixed electrical load probes; nutritional consumption, casualties and ticket economics are excluded. No simulation runs on the UI thread.

Local trips are two-day planet-to-moon orbital transfers except the one-day atmospheric landing. Stellar strength uses the existing mass-to-luminosity relation. Crossings marked synthetic span 5 billion metres, far shorter than actual star separation. The 1-light-year cases run the bounded analytic forecast only when the journey exceeds 180 days; no arrival or daily endurance is asserted for them.

Rejected short scenarios are deliberately forced through engine tick processing to diagnose depletion. Normal commands still reject them. Readiness includes a separate 48-hour essential and cargo reserve. Known local arrivals use destination sunlight with an initial eclipse; interstellar and unknown arrivals assume darkness. Forecast fuel use describes the journey alone; checking the reserve consumes no actual stock.

## Journey outcomes

| Scenario | Planned days | Sunlit solar kW | Essential / cargo / drive kW | Command ready | Actual outcome |
| --- | ---: | ---: | --- | --- | --- |
| RP-1/LOX | 2.00 | 0.00 | 2.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Methane/LOX | 2.00 | 0.00 | 2.0 / 3.9 / 22.0 | true | Arrived + reserve |
| Hydrogen/LOX | 2.00 | 0.00 | 2.0 / 3.9 / 25.0 | true | Arrived + reserve |
| Chemical under-supplied | 2.00 | 0.00 | 2.0 / 3.9 / 20.0 | false | Power interruption |
| Solar at 0.5 AU | 2.00 | 480.00 | 2.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Solar at 1.0 AU | 2.00 | 120.00 | 2.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Solar at 2.0 AU | 2.00 | 30.00 | 2.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Solar at 5.0 AU | 2.00 | 4.80 | 2.0 / 3.9 / 20.0 | false | Power interruption |
| Solar at 2 AU, empty hold | 2.00 | 30.00 | 2.0 / 3.0 / 20.0 | true | Arrived + reserve |
| Solar at 2 AU, full food hold | 2.00 | 30.00 | 2.0 / 30.0 / 20.0 | false | Power interruption |
| Solar around 0.5 solar masses | 2.00 | 10.61 | 2.0 / 3.9 / 20.0 | false | Power interruption |
| Solar around 2 solar masses at 5 AU | 2.00 | 54.31 | 2.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Solar during atmospheric landing | 1.00 | 0.00 | 2.0 / 3.9 / 20.0 | false | Power interruption |
| Solar with 100 conscious passengers | 2.00 | 120.00 | 102.0 / 3.9 / 20.0 | false | Power interruption |
| Two solar arrays with 100 stasis passengers at 0.5 AU | 2.00 | 960.00 | 82.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Two solar arrays with 100 stasis passengers at 1 AU | 2.00 | 240.00 | 82.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Chemical with 100 conscious passengers | 2.00 | 0.00 | 102.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Chemical with 100 stasis passengers | 2.00 | 0.00 | 82.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Chemical with 10 conscious passengers | 2.00 | 0.00 | 12.0 / 3.9 / 20.0 | true | Arrived + reserve |
| Chemical with 10 stasis passengers | 2.00 | 0.00 | 11.8 / 3.9 / 20.0 | true | Arrived + reserve |
| Fission synthetic crossing | 34.00 | 0.00 | 2.0 / 3.9 / 100.0 | true | Arrived + reserve |
| Chemical synthetic crossing | 126.00 | 0.00 | 2.0 / 3.9 / 20.0 | false | Power interruption |
| Fission at 1 light-year | 63488581.00 | 0.00 | 2.0 / 3.9 / 100.0 | false | Plan only |
| Chemical at 1 light-year | 236863941.00 | 0.00 | 2.0 / 3.9 / 20.0 | false | Plan only |

## Actual resource changes

Fuel columns separate main propellant from electrical generator mixtures or refined electrical reactor feed. Battery delta is final minus initial. Plan-only rows have no tick measurements.

| Scenario | Main fuel used kg | Electrical fuel used kg | Arrival/end battery kWh | Battery delta kWh | Food lost kg |
| --- | ---: | ---: | ---: | ---: | ---: |
| RP-1/LOX | 553.437 | 1233.333333 | 500.000 | +0.000 | 0.000 |
| Methane/LOX | 510.361 | 1449.350649 | 500.000 | +0.000 | 0.000 |
| Hydrogen/LOX | 425.248 | 872.470588 | 500.000 | +0.000 | 0.000 |
| Chemical under-supplied | 421.842 | 20.000000 | 0.000 | -500.000 | 8.336 |
| Solar at 0.5 AU | 386.528 | 0.000000 | 448.200 | -51.800 | 0.000 |
| Solar at 1.0 AU | 386.528 | 0.000000 | 448.200 | -51.800 | 0.000 |
| Solar at 2.0 AU | 386.528 | 0.000000 | 403.500 | -96.500 | 0.000 |
| Solar at 5.0 AU | 386.528 | 0.000000 | 0.000 | -500.000 | 0.000 |
| Solar at 2 AU, empty hold | 377.743 | 0.000000 | 435.000 | -65.000 | 0.000 |
| Solar at 2 AU, full food hold | 641.284 | 0.000000 | 0.000 | -500.000 | 0.000 |
| Solar around 0.5 solar masses | 386.528 | 0.000000 | 36.043 | -463.957 | 0.000 |
| Solar around 2 solar masses at 5 AU | 386.528 | 0.000000 | 448.200 | -51.800 | 0.000 |
| Solar during atmospheric landing | 0.000 | 0.000000 | 0.000 | -500.000 | 0.000 |
| Solar with 100 conscious passengers | 456.805 | 0.000000 | 0.000 | -500.000 | 0.000 |
| Two solar arrays with 100 stasis passengers at 0.5 AU | 489.309 | 0.000000 | 288.200 | -211.800 | 0.000 |
| Two solar arrays with 100 stasis passengers at 1 AU | 489.309 | 0.000000 | 288.200 | -211.800 | 0.000 |
| Chemical with 100 conscious passengers | 623.715 | 5995.238095 | 500.000 | +0.000 | 0.000 |
| Chemical with 100 stasis passengers | 634.257 | 5042.857143 | 500.000 | +0.000 | 0.000 |
| Chemical with 10 conscious passengers | 560.465 | 1709.523810 | 500.000 | +0.000 | 0.000 |
| Chemical with 10 stasis passengers | 571.007 | 1700.000000 | 500.000 | +0.000 | 0.000 |
| Fission synthetic crossing | 15000.000 | 0.000803 | 500.000 | +0.000 | 0.000 |
| Chemical synthetic crossing | 7500.000 | 15000.000000 | 0.000 | -500.000 | 529.311 |
| Fission at 1 light-year | — | — | — | — | — |
| Chemical at 1 light-year | — | — | — | — | — |

## Preview agreement

Only completed, uninterrupted journeys compare final battery and journey fuel use. A negative preview is allowed to finish the journey if its arrival reserve is insufficient. Missing values mean that comparison does not apply. Each simulated row asserts readiness matches the actual journey and reserve outcome.

| Scenario | Forecast journey kWh | Forecast reserve kWh | Battery error kWh | Electrical fuel error kg |
| --- | ---: | ---: | ---: | ---: |
| RP-1/LOX | 1243.20 | 283.20 | 0.000000 | 0.000000 |
| Methane/LOX | 1339.20 | 283.20 | 0.000000 | 0.000000 |
| Hydrogen/LOX | 1483.20 | 283.20 | 0.000000 | 0.000000 |
| Chemical under-supplied | 1243.20 | 283.20 | — | — |
| Solar at 0.5 AU | 1243.20 | 283.20 | 0.000000 | 0.000000 |
| Solar at 1.0 AU | 1243.20 | 283.20 | 0.000000 | 0.000000 |
| Solar at 2.0 AU | 1243.20 | 283.20 | 0.000000 | 0.000000 |
| Solar at 5.0 AU | 1243.20 | 283.20 | — | — |
| Solar at 2 AU, empty hold | 1200.00 | 240.00 | 0.000000 | 0.000000 |
| Solar at 2 AU, full food hold | 2496.00 | 1536.00 | — | — |
| Solar around 0.5 solar masses | 1243.20 | 283.20 | — | — |
| Solar around 2 solar masses at 5 AU | 1243.20 | 283.20 | 0.000000 | 0.000000 |
| Solar during atmospheric landing | 621.60 | 283.20 | — | — |
| Solar with 100 conscious passengers | 6043.20 | 5083.20 | — | — |
| Two solar arrays with 100 stasis passengers at 0.5 AU | 5083.20 | 4123.20 | 0.000000 | 0.000000 |
| Two solar arrays with 100 stasis passengers at 1 AU | 5083.20 | 4123.20 | 0.000000 | 0.000000 |
| Chemical with 100 conscious passengers | 6043.20 | 5083.20 | 0.000000 | 0.000000 |
| Chemical with 100 stasis passengers | 5083.20 | 4123.20 | 0.000000 | 0.000000 |
| Chemical with 10 conscious passengers | 1723.20 | 763.20 | 0.000000 | 0.000000 |
| Chemical with 10 stasis passengers | 1713.60 | 753.60 | 0.000000 | 0.000000 |
| Fission synthetic crossing | 4819.71 | 283.20 | 0.000000 | 0.000000 |
| Chemical synthetic crossing | 17842.14 | 283.20 | — | — |
| Fission at 1 light-year | 8989983074.91 | 283.20 | — | — |
| Chemical at 1 light-year | 33539934046.14 | 283.20 | — | — |

## Emergency resupply and recovery

Three unpowered days spoil 97.5 kg from 1,000 kg of food. A stationary donor transfers 4,000 kg of RP-1/LOX from cargo, then recovery completes the original two-day local leg. Arrival retains 902.5 kg of food, 2770.85 kg of generator supplies and 14578.33 kg of main propellant. Recovery spends no second maneuver commitment.

## Tuning candidates

- Local arrival reserves now use eclipse-first destination illumination and include cargo preservation. Interstellar arrivals retain darkness. Continue testing weak stars and long nights when tuning storage.
- Cargo demand now uses 10% standby plus 90% times vulnerable-mass utilization. Food weight is 1, biomass 0.5 and cryogenic liquids 2; stable freight stays at standby. Keep the full rated load as the ceiling.
- Stasis now uses 2 kW per occupied pod plus 0.78 kW per passenger: 9.8 kW for 10 seats and 80 kW for a full 100-seat pod. Empty pods remain off. Sparse one-seat pods still have overhead.
- Chemical sources should remain useful locally. Their electrical stores and modest exhaust velocities do not establish viable 1-light-year endurance. Fission electricity does not improve the thermal drive's exhaust velocity.
- Revisit food grace and loss rates alongside recovery delays. This example loses 9.75% over three fully unpowered days; restoring power prevents additional loss.

These are provisional gameplay coefficients. The audit reports the implemented reserve and occupancy adjustments; executing it changes no game state outside its fixtures.
