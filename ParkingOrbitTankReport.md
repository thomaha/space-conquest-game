# Parking orbit and tank comparison

## Scope

Read-only catalog probes vary one to three main tanks, empty or 10,000 kg mixed cargo and Mars or Jupiter targets. Parking altitude pairs in km are 500/500, 100,000/500, 100,000/8,000 and 100,000/100,000. Every candidate is rebuilt by the blueprint factory. Tanks add real mass, slots and capacity and start full. Fission drive cargo is seeded at 0.001 kg per kg of main capacity; protected main reserves scale with tank capacity. These are separate imagined base locations, not free moves from existing stations. High-orbit access costs, electricity and actual navigation remain unevaluated.

## Findings

- Of 192 planned combinations, 160 have valid blueprints. The 32 remaining combinations share four rejected setup/target blueprints: a three-tank hydrolox ship needs 31 slots and a three-tank fission thermal ship needs 32, against the medium frame's 30. No tank mass or capacity is added to an invalid design.
- Six valid probes pass the main propellant, propulsion feed and provisional impulse checks. All use a two-tank fission thermal ship from a 100,000 km Earth parking altitude to Mars. Both empty holds and a 10,000 kg mixed shipment pass for each of the three destination altitudes tested. These are maneuver-feasible benchmarks, not authorized operational journeys.
- The two-tank fission design occupies 28 slots and carries 30,000 kg main propellant. Its protected reserve is 1,500 kg, leaving 28,500 kg usable. The loaded candidate's wet mass is 73,130 kg including real dry structure, electrical stock, cargo and 30 kg of separately seeded propulsion reactor feed.
- From 100,000 km at Earth to 8,000 km at Mars, the loaded fission benchmark requires 3,957.4 m/s and 26,018.27 kg main propellant at its current wet mass. It preserves 3,981.73 kg in the main tank, exceeding the protected target. The corresponding empty ship requires 22,460.46 kg. Coast time remains the original 258.79 days; departure phase waiting remains separately determined.
- Raising every orbit is not always cheaper. At Mars, 8,000 km capture costs less than the 500 km orbit, while 100,000 km capture costs more than the 500 km orbit for this incoming excess speed. The capture expression has an altitude-dependent minimum because the parking orbit's own circular speed matters. Higher Earth departure altitude still reduces the tested departure cost.
- None of the RP-1, methalox or hydrolox configurations pass all maneuver checks, even with empty trade holds and their largest valid tested tank arrangement. Every Jupiter configuration also fails. Better orbital modeling cannot by itself supply the current ships with sufficient fuel fraction.
- Extra tanks increase initial wet mass, tank dry mass, capacity and protected reserve together. Every candidate is rebuilt by the real blueprint factory and each budget is recomputed from its loaded ship. The reports never treat the previous mass's apparent fuel deficit as a valid purchase quote.

## Model and sources

The same coplanar circular transfer and parking escape/capture equations as [PlanetaryTransferReport.md](PlanetaryTransferReport.md) apply. High-orbit endpoints represent separate hypothetical bases rather than free movement from the existing low stations. Cargo export and local ascent to those bases still need funded travel and service budgets.

Parking radius must be inside the comparison's approximate sphere of influence: `sphereRadius = planetaryOrbitalRadius * (planetMass / stellarMass)^(2/5)`. This is a domain guard for the patched-conic benchmark rather than an exact gravitational boundary. [MIT's astrodynamics lecture 29](https://ocw.mit.edu/courses/16-346-astrodynamics-fall-2008/d753d9c944d9bffb13c169f0f1c73d69_lec_29.pdf) gives this approximation. Inclination, moon perturbations, sphere-crossing time and actual finite-burn trajectories remain excluded.

## Next equipment decision

Keep chemical propulsion available for supported local routes such as the existing Moon station trip. Before promising early chemical Mars transport, compare a smaller freighter or a hull with a substantially larger achievable propellant fraction. Any new hull, tank or staging equipment needs explicit material, mass, slot and construction rules.

An initial single-stage sizing bound can use `q = exp(-requiredDeltaV / exhaustVelocity)` and `reserveFraction = r`. With fixed nonpropellant mass `B`, full tank fuel must satisfy `(q - r) * fuel >= B * (1 - q)`. When new tanks add dry mass proportionally, that added structure must enter `B` and the candidate must be rebuilt. If `q <= r`, extra fuel alone cannot preserve the chosen reserve fraction. Reactor feed and auxiliary supplies must enter the same loaded mass budget.

The measured fission configuration is a useful candidate for the next orbital prototype. It still needs power, waiting, departure geometry, finite burns and approach/capture validation before it can carry real cargo in the simulation. Operational saved trajectory and fleet-copy changes remain a later coordinated implementation step.

## Results

| Equipment | Tanks | Slots | Cargo kg | Target | Source altitude km | Target altitude km | Required delta-v m/s | Available main delta-v m/s | Initial wet kg | Required main kg | Usable main kg | Protected main kg | Main fits | Feed fits | Impulse fits |
|---|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|
| RP-1 solar | 1 | 22 | 0 | mars | 500 | 500 | 5618.5 | 1368.7 | 43000.00 | 34762.41 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 1 | 22 | 0 | mars | 100000 | 500 | 4152.9 | 1368.7 | 43000.00 | 30323.31 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 1 | 22 | 0 | mars | 100000 | 8000 | 3957.4 | 1368.7 | 43000.00 | 29573.31 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 1 | 22 | 0 | mars | 100000 | 100000 | 4241.8 | 1368.7 | 43000.00 | 30650.46 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 1 | 22 | 10000 | mars | 500 | 500 | 5618.5 | 1064.7 | 53000.00 | 42846.69 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 1 | 22 | 10000 | mars | 100000 | 500 | 4152.9 | 1064.7 | 53000.00 | 37375.24 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 1 | 22 | 10000 | mars | 100000 | 8000 | 3957.4 | 1064.7 | 53000.00 | 36450.83 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 1 | 22 | 10000 | mars | 100000 | 100000 | 4241.8 | 1064.7 | 53000.00 | 37778.47 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 2 | 26 | 0 | mars | 500 | 500 | 5618.5 | 2190.8 | 60000.00 | 48505.68 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 2 | 26 | 0 | mars | 100000 | 500 | 4152.9 | 2190.8 | 60000.00 | 42311.59 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 2 | 26 | 0 | mars | 100000 | 8000 | 3957.4 | 2190.8 | 60000.00 | 41265.09 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 2 | 26 | 0 | mars | 100000 | 100000 | 4241.8 | 2190.8 | 60000.00 | 42768.08 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 2 | 26 | 10000 | mars | 500 | 500 | 5618.5 | 1777.5 | 70000.00 | 56589.96 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 2 | 26 | 10000 | mars | 100000 | 500 | 4152.9 | 1777.5 | 70000.00 | 49363.53 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 2 | 26 | 10000 | mars | 100000 | 8000 | 3957.4 | 1777.5 | 70000.00 | 48142.60 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 2 | 26 | 10000 | mars | 100000 | 100000 | 4241.8 | 1777.5 | 70000.00 | 49896.09 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 3 | 30 | 0 | mars | 500 | 500 | 5618.5 | 2754.4 | 77000.00 | 62248.96 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 3 | 30 | 0 | mars | 100000 | 500 | 4152.9 | 2754.4 | 77000.00 | 54299.88 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 3 | 30 | 0 | mars | 100000 | 8000 | 3957.4 | 2754.4 | 77000.00 | 52956.86 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 3 | 30 | 0 | mars | 100000 | 100000 | 4241.8 | 2754.4 | 77000.00 | 54885.70 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 3 | 30 | 10000 | mars | 500 | 500 | 5618.5 | 2298.6 | 87000.00 | 70333.24 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 3 | 30 | 10000 | mars | 100000 | 500 | 4152.9 | 2298.6 | 87000.00 | 61351.81 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 3 | 30 | 10000 | mars | 100000 | 8000 | 3957.4 | 2298.6 | 87000.00 | 59834.37 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 3 | 30 | 10000 | mars | 100000 | 100000 | 4241.8 | 2298.6 | 87000.00 | 62013.71 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 1 | 22 | 0 | jupiter | 500 | 500 | 24123.5 | 1368.7 | 43000.00 | 42964.35 | 14250.00 | 750 | false | true | false |
| RP-1 solar | 1 | 22 | 0 | jupiter | 100000 | 500 | 25109.4 | 1368.7 | 43000.00 | 42973.32 | 14250.00 | 750 | false | true | false |
| RP-1 solar | 1 | 22 | 0 | jupiter | 100000 | 8000 | 24256.1 | 1368.7 | 43000.00 | 42965.71 | 14250.00 | 750 | false | true | false |
| RP-1 solar | 1 | 22 | 0 | jupiter | 100000 | 100000 | 18995.4 | 1368.7 | 43000.00 | 42838.89 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 1 | 22 | 10000 | jupiter | 500 | 500 | 24123.5 | 1064.7 | 53000.00 | 52956.06 | 14250.00 | 750 | false | true | false |
| RP-1 solar | 1 | 22 | 10000 | jupiter | 100000 | 500 | 25109.4 | 1064.7 | 53000.00 | 52967.12 | 14250.00 | 750 | false | true | false |
| RP-1 solar | 1 | 22 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 1064.7 | 53000.00 | 52957.74 | 14250.00 | 750 | false | true | false |
| RP-1 solar | 1 | 22 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 1064.7 | 53000.00 | 52801.43 | 14250.00 | 750 | false | true | true |
| RP-1 solar | 2 | 26 | 0 | jupiter | 500 | 500 | 24123.5 | 2190.8 | 60000.00 | 59950.25 | 28500.00 | 1500 | false | true | false |
| RP-1 solar | 2 | 26 | 0 | jupiter | 100000 | 500 | 25109.4 | 2190.8 | 60000.00 | 59962.78 | 28500.00 | 1500 | false | true | false |
| RP-1 solar | 2 | 26 | 0 | jupiter | 100000 | 8000 | 24256.1 | 2190.8 | 60000.00 | 59952.16 | 28500.00 | 1500 | false | true | false |
| RP-1 solar | 2 | 26 | 0 | jupiter | 100000 | 100000 | 18995.4 | 2190.8 | 60000.00 | 59775.20 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 2 | 26 | 10000 | jupiter | 500 | 500 | 24123.5 | 1777.5 | 70000.00 | 69941.96 | 28500.00 | 1500 | false | true | false |
| RP-1 solar | 2 | 26 | 10000 | jupiter | 100000 | 500 | 25109.4 | 1777.5 | 70000.00 | 69956.57 | 28500.00 | 1500 | false | true | false |
| RP-1 solar | 2 | 26 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 1777.5 | 70000.00 | 69944.18 | 28500.00 | 1500 | false | true | false |
| RP-1 solar | 2 | 26 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 1777.5 | 70000.00 | 69737.73 | 28500.00 | 1500 | false | true | true |
| RP-1 solar | 3 | 30 | 0 | jupiter | 500 | 500 | 24123.5 | 2754.4 | 77000.00 | 76936.16 | 42750.00 | 2250 | false | true | false |
| RP-1 solar | 3 | 30 | 0 | jupiter | 100000 | 500 | 25109.4 | 2754.4 | 77000.00 | 76952.23 | 42750.00 | 2250 | false | true | false |
| RP-1 solar | 3 | 30 | 0 | jupiter | 100000 | 8000 | 24256.1 | 2754.4 | 77000.00 | 76938.60 | 42750.00 | 2250 | false | true | false |
| RP-1 solar | 3 | 30 | 0 | jupiter | 100000 | 100000 | 18995.4 | 2754.4 | 77000.00 | 76711.51 | 42750.00 | 2250 | false | true | true |
| RP-1 solar | 3 | 30 | 10000 | jupiter | 500 | 500 | 24123.5 | 2298.6 | 87000.00 | 86927.87 | 42750.00 | 2250 | false | true | false |
| RP-1 solar | 3 | 30 | 10000 | jupiter | 100000 | 500 | 25109.4 | 2298.6 | 87000.00 | 86946.02 | 42750.00 | 2250 | false | true | false |
| RP-1 solar | 3 | 30 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 2298.6 | 87000.00 | 86930.63 | 42750.00 | 2250 | false | true | false |
| RP-1 solar | 3 | 30 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 2298.6 | 87000.00 | 86674.04 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 1 | 22 | 0 | mars | 500 | 500 | 5618.5 | 1481.0 | 43200.00 | 33737.52 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 0 | mars | 100000 | 500 | 4152.9 | 1481.0 | 43200.00 | 29138.50 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 0 | mars | 100000 | 8000 | 3957.4 | 1481.0 | 43200.00 | 28375.82 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 0 | mars | 100000 | 100000 | 4241.8 | 1481.0 | 43200.00 | 29472.31 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 10000 | mars | 500 | 500 | 5618.5 | 1153.6 | 53200.00 | 41547.13 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 10000 | mars | 100000 | 500 | 4152.9 | 1153.6 | 53200.00 | 35883.52 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 10000 | mars | 100000 | 8000 | 3957.4 | 1153.6 | 53200.00 | 34944.30 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 10000 | mars | 100000 | 100000 | 4241.8 | 1153.6 | 53200.00 | 36294.61 | 14250.00 | 750 | false | true | true |
| Methalox solar | 2 | 26 | 0 | mars | 500 | 500 | 5618.5 | 2373.0 | 60200.00 | 47013.86 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 2 | 26 | 0 | mars | 100000 | 500 | 4152.9 | 2373.0 | 60200.00 | 40605.04 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 2 | 26 | 0 | mars | 100000 | 8000 | 3957.4 | 2373.0 | 60200.00 | 39542.23 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 2 | 26 | 0 | mars | 100000 | 100000 | 4241.8 | 2373.0 | 60200.00 | 41070.21 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 2 | 26 | 10000 | mars | 500 | 500 | 5618.5 | 1927.1 | 70200.00 | 54823.47 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 2 | 26 | 10000 | mars | 100000 | 500 | 4152.9 | 1927.1 | 70200.00 | 47350.06 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 2 | 26 | 10000 | mars | 100000 | 8000 | 3957.4 | 1927.1 | 70200.00 | 46110.71 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 2 | 26 | 10000 | mars | 100000 | 100000 | 4241.8 | 1927.1 | 70200.00 | 47892.51 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 3 | 30 | 0 | mars | 500 | 500 | 5618.5 | 2985.5 | 77200.00 | 60290.20 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 3 | 30 | 0 | mars | 100000 | 500 | 4152.9 | 2985.5 | 77200.00 | 52071.58 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 3 | 30 | 0 | mars | 100000 | 8000 | 3957.4 | 2985.5 | 77200.00 | 50708.64 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 3 | 30 | 0 | mars | 100000 | 100000 | 4241.8 | 2985.5 | 77200.00 | 52668.12 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 3 | 30 | 10000 | mars | 500 | 500 | 5618.5 | 2493.2 | 87200.00 | 68099.81 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 3 | 30 | 10000 | mars | 100000 | 500 | 4152.9 | 2493.2 | 87200.00 | 58816.60 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 3 | 30 | 10000 | mars | 100000 | 8000 | 3957.4 | 2493.2 | 87200.00 | 57277.12 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 3 | 30 | 10000 | mars | 100000 | 100000 | 4241.8 | 2493.2 | 87200.00 | 59490.41 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 1 | 22 | 0 | jupiter | 500 | 500 | 24123.5 | 1481.0 | 43200.00 | 43136.33 | 14250.00 | 750 | false | true | false |
| Methalox solar | 1 | 22 | 0 | jupiter | 100000 | 500 | 25109.4 | 1481.0 | 43200.00 | 43151.22 | 14250.00 | 750 | false | true | false |
| Methalox solar | 1 | 22 | 0 | jupiter | 100000 | 8000 | 24256.1 | 1481.0 | 43200.00 | 43138.57 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 0 | jupiter | 100000 | 100000 | 18995.4 | 1481.0 | 43200.00 | 42945.40 | 14250.00 | 750 | false | true | true |
| Methalox solar | 1 | 22 | 10000 | jupiter | 500 | 500 | 24123.5 | 1153.6 | 53200.00 | 53121.59 | 14250.00 | 750 | false | true | false |
| Methalox solar | 1 | 22 | 10000 | jupiter | 100000 | 500 | 25109.4 | 1153.6 | 53200.00 | 53139.93 | 14250.00 | 750 | false | true | false |
| Methalox solar | 1 | 22 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 1153.6 | 53200.00 | 53124.35 | 14250.00 | 750 | false | true | false |
| Methalox solar | 1 | 22 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 1153.6 | 53200.00 | 52886.46 | 14250.00 | 750 | false | true | true |
| Methalox solar | 2 | 26 | 0 | jupiter | 500 | 500 | 24123.5 | 2373.0 | 60200.00 | 60111.27 | 28500.00 | 1500 | false | true | false |
| Methalox solar | 2 | 26 | 0 | jupiter | 100000 | 500 | 25109.4 | 2373.0 | 60200.00 | 60132.03 | 28500.00 | 1500 | false | true | false |
| Methalox solar | 2 | 26 | 0 | jupiter | 100000 | 8000 | 24256.1 | 2373.0 | 60200.00 | 60114.40 | 28500.00 | 1500 | false | true | false |
| Methalox solar | 2 | 26 | 0 | jupiter | 100000 | 100000 | 18995.4 | 2373.0 | 60200.00 | 59845.21 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 2 | 26 | 10000 | jupiter | 500 | 500 | 24123.5 | 1927.1 | 70200.00 | 70096.54 | 28500.00 | 1500 | false | true | false |
| Methalox solar | 2 | 26 | 10000 | jupiter | 100000 | 500 | 25109.4 | 1927.1 | 70200.00 | 70120.74 | 28500.00 | 1500 | false | true | false |
| Methalox solar | 2 | 26 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 1927.1 | 70200.00 | 70100.18 | 28500.00 | 1500 | false | true | false |
| Methalox solar | 2 | 26 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 1927.1 | 70200.00 | 69786.27 | 28500.00 | 1500 | false | true | true |
| Methalox solar | 3 | 30 | 0 | jupiter | 500 | 500 | 24123.5 | 2985.5 | 77200.00 | 77086.22 | 42750.00 | 2250 | false | true | false |
| Methalox solar | 3 | 30 | 0 | jupiter | 100000 | 500 | 25109.4 | 2985.5 | 77200.00 | 77112.83 | 42750.00 | 2250 | false | true | false |
| Methalox solar | 3 | 30 | 0 | jupiter | 100000 | 8000 | 24256.1 | 2985.5 | 77200.00 | 77090.22 | 42750.00 | 2250 | false | true | false |
| Methalox solar | 3 | 30 | 0 | jupiter | 100000 | 100000 | 18995.4 | 2985.5 | 77200.00 | 76745.02 | 42750.00 | 2250 | false | true | true |
| Methalox solar | 3 | 30 | 10000 | jupiter | 500 | 500 | 24123.5 | 2493.2 | 87200.00 | 87071.48 | 42750.00 | 2250 | false | true | false |
| Methalox solar | 3 | 30 | 10000 | jupiter | 100000 | 500 | 25109.4 | 2493.2 | 87200.00 | 87101.54 | 42750.00 | 2250 | false | true | false |
| Methalox solar | 3 | 30 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 2493.2 | 87200.00 | 87076.00 | 42750.00 | 2250 | false | true | false |
| Methalox solar | 3 | 30 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 2493.2 | 87200.00 | 86686.08 | 42750.00 | 2250 | false | true | true |
| Hydrolox solar | 1 | 23 | 0 | mars | 500 | 500 | 5618.5 | 1761.1 | 44000.00 | 31375.49 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 0 | mars | 100000 | 500 | 4152.9 | 1761.1 | 44000.00 | 26515.28 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 0 | mars | 100000 | 8000 | 3957.4 | 1761.1 | 44000.00 | 25739.20 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 0 | mars | 100000 | 100000 | 4241.8 | 1761.1 | 44000.00 | 26857.29 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 10000 | mars | 500 | 500 | 5618.5 | 1378.7 | 54000.00 | 38506.28 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 10000 | mars | 100000 | 500 | 4152.9 | 1378.7 | 54000.00 | 32541.48 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 10000 | mars | 100000 | 8000 | 3957.4 | 1378.7 | 54000.00 | 31589.02 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 10000 | mars | 100000 | 100000 | 4241.8 | 1378.7 | 54000.00 | 32961.22 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 2 | 27 | 0 | mars | 500 | 500 | 5618.5 | 2833.4 | 61000.00 | 43497.84 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 2 | 27 | 0 | mars | 100000 | 500 | 4152.9 | 2833.4 | 61000.00 | 36759.82 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 2 | 27 | 0 | mars | 100000 | 8000 | 3957.4 | 2833.4 | 61000.00 | 35683.90 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 2 | 27 | 0 | mars | 100000 | 100000 | 4241.8 | 2833.4 | 61000.00 | 37233.97 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 2 | 27 | 10000 | mars | 500 | 500 | 5618.5 | 2309.3 | 71000.00 | 50628.63 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 2 | 27 | 10000 | mars | 100000 | 500 | 4152.9 | 2309.3 | 71000.00 | 42786.01 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 2 | 27 | 10000 | mars | 100000 | 8000 | 3957.4 | 2309.3 | 71000.00 | 41533.72 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 2 | 27 | 10000 | mars | 100000 | 100000 | 4241.8 | 2309.3 | 71000.00 | 43337.90 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 1 | 23 | 0 | jupiter | 500 | 500 | 24123.5 | 1761.1 | 44000.00 | 43793.32 | 14250.00 | 750 | false | true | false |
| Hydrolox solar | 1 | 23 | 0 | jupiter | 100000 | 500 | 25109.4 | 1761.1 | 44000.00 | 43833.99 | 14250.00 | 750 | false | true | false |
| Hydrolox solar | 1 | 23 | 0 | jupiter | 100000 | 8000 | 24256.1 | 1761.1 | 44000.00 | 43799.32 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 0 | jupiter | 100000 | 100000 | 18995.4 | 1761.1 | 44000.00 | 43354.04 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 1 | 23 | 10000 | jupiter | 500 | 500 | 24123.5 | 1378.7 | 54000.00 | 53746.35 | 14250.00 | 750 | false | true | false |
| Hydrolox solar | 1 | 23 | 10000 | jupiter | 100000 | 500 | 25109.4 | 1378.7 | 54000.00 | 53796.26 | 14250.00 | 750 | false | true | false |
| Hydrolox solar | 1 | 23 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 1378.7 | 54000.00 | 53753.71 | 14250.00 | 750 | false | true | false |
| Hydrolox solar | 1 | 23 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 1378.7 | 54000.00 | 53207.23 | 14250.00 | 750 | false | true | true |
| Hydrolox solar | 2 | 27 | 0 | jupiter | 500 | 500 | 24123.5 | 2833.4 | 61000.00 | 60713.47 | 28500.00 | 1500 | false | true | false |
| Hydrolox solar | 2 | 27 | 0 | jupiter | 100000 | 500 | 25109.4 | 2833.4 | 61000.00 | 60769.84 | 28500.00 | 1500 | false | true | false |
| Hydrolox solar | 2 | 27 | 0 | jupiter | 100000 | 8000 | 24256.1 | 2833.4 | 61000.00 | 60721.79 | 28500.00 | 1500 | false | true | false |
| Hydrolox solar | 2 | 27 | 0 | jupiter | 100000 | 100000 | 18995.4 | 2833.4 | 61000.00 | 60104.46 | 28500.00 | 1500 | false | true | true |
| Hydrolox solar | 2 | 27 | 10000 | jupiter | 500 | 500 | 24123.5 | 2309.3 | 71000.00 | 70666.50 | 28500.00 | 1500 | false | true | false |
| Hydrolox solar | 2 | 27 | 10000 | jupiter | 100000 | 500 | 25109.4 | 2309.3 | 71000.00 | 70732.11 | 28500.00 | 1500 | false | true | false |
| Hydrolox solar | 2 | 27 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 2309.3 | 71000.00 | 70676.18 | 28500.00 | 1500 | false | true | false |
| Hydrolox solar | 2 | 27 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 2309.3 | 71000.00 | 69957.65 | 28500.00 | 1500 | false | true | true |
| Fission thermal | 1 | 24 | 0 | mars | 500 | 500 | 5618.5 | 3326.7 | 46115.00 | 21413.51 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 0 | mars | 100000 | 500 | 4152.9 | 3326.7 | 46115.00 | 17044.98 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 0 | mars | 100000 | 8000 | 3957.4 | 3326.7 | 46115.00 | 16406.84 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 0 | mars | 100000 | 100000 | 4241.8 | 3326.7 | 46115.00 | 17330.71 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 10000 | mars | 500 | 500 | 5618.5 | 2636.6 | 56115.00 | 26057.01 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 10000 | mars | 100000 | 500 | 4152.9 | 2636.6 | 56115.00 | 20741.18 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 10000 | mars | 100000 | 8000 | 3957.4 | 2636.6 | 56115.00 | 19964.65 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 10000 | mars | 100000 | 100000 | 4241.8 | 2636.6 | 56115.00 | 21088.86 | 14250.00 | 750 | false | false | true |
| Fission thermal | 2 | 28 | 0 | mars | 500 | 500 | 5618.5 | 5404.3 | 63130.00 | 29314.43 | 28500.00 | 1500 | false | false | true |
| Fission thermal | 2 | 28 | 0 | mars | 100000 | 500 | 4152.9 | 5404.3 | 63130.00 | 23334.05 | 28500.00 | 1500 | true | true | true |
| Fission thermal | 2 | 28 | 0 | mars | 100000 | 8000 | 3957.4 | 5404.3 | 63130.00 | 22460.46 | 28500.00 | 1500 | true | true | true |
| Fission thermal | 2 | 28 | 0 | mars | 100000 | 100000 | 4241.8 | 5404.3 | 63130.00 | 23725.20 | 28500.00 | 1500 | true | true | true |
| Fission thermal | 2 | 28 | 10000 | mars | 500 | 500 | 5618.5 | 4444.5 | 73130.00 | 33957.93 | 28500.00 | 1500 | false | false | true |
| Fission thermal | 2 | 28 | 10000 | mars | 100000 | 500 | 4152.9 | 4444.5 | 73130.00 | 27030.25 | 28500.00 | 1500 | true | true | true |
| Fission thermal | 2 | 28 | 10000 | mars | 100000 | 8000 | 3957.4 | 4444.5 | 73130.00 | 26018.27 | 28500.00 | 1500 | true | true | true |
| Fission thermal | 2 | 28 | 10000 | mars | 100000 | 100000 | 4241.8 | 4444.5 | 73130.00 | 27483.35 | 28500.00 | 1500 | true | true | true |
| Fission thermal | 1 | 24 | 0 | jupiter | 500 | 500 | 24123.5 | 3326.7 | 46115.00 | 42954.45 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 0 | jupiter | 100000 | 500 | 25109.4 | 3326.7 | 46115.00 | 43282.38 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 0 | jupiter | 100000 | 8000 | 24256.1 | 3326.7 | 46115.00 | 43000.65 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 0 | jupiter | 100000 | 100000 | 18995.4 | 3326.7 | 46115.00 | 40527.48 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 10000 | jupiter | 500 | 500 | 24123.5 | 2636.6 | 56115.00 | 52269.08 | 14250.00 | 750 | false | false | false |
| Fission thermal | 1 | 24 | 10000 | jupiter | 100000 | 500 | 25109.4 | 2636.6 | 56115.00 | 52668.13 | 14250.00 | 750 | false | false | false |
| Fission thermal | 1 | 24 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 2636.6 | 56115.00 | 52325.31 | 14250.00 | 750 | false | false | true |
| Fission thermal | 1 | 24 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 2636.6 | 56115.00 | 49315.83 | 14250.00 | 750 | false | false | true |
| Fission thermal | 2 | 28 | 0 | jupiter | 500 | 500 | 24123.5 | 5404.3 | 63130.00 | 58803.30 | 28500.00 | 1500 | false | false | false |
| Fission thermal | 2 | 28 | 0 | jupiter | 100000 | 500 | 25109.4 | 5404.3 | 63130.00 | 59252.23 | 28500.00 | 1500 | false | false | false |
| Fission thermal | 2 | 28 | 0 | jupiter | 100000 | 8000 | 24256.1 | 5404.3 | 63130.00 | 58866.55 | 28500.00 | 1500 | false | false | false |
| Fission thermal | 2 | 28 | 0 | jupiter | 100000 | 100000 | 18995.4 | 5404.3 | 63130.00 | 55480.86 | 28500.00 | 1500 | false | false | true |
| Fission thermal | 2 | 28 | 10000 | jupiter | 500 | 500 | 24123.5 | 4444.5 | 73130.00 | 68117.94 | 28500.00 | 1500 | false | false | false |
| Fission thermal | 2 | 28 | 10000 | jupiter | 100000 | 500 | 25109.4 | 4444.5 | 73130.00 | 68637.98 | 28500.00 | 1500 | false | false | false |
| Fission thermal | 2 | 28 | 10000 | jupiter | 100000 | 8000 | 24256.1 | 4444.5 | 73130.00 | 68191.21 | 28500.00 | 1500 | false | false | false |
| Fission thermal | 2 | 28 | 10000 | jupiter | 100000 | 100000 | 18995.4 | 4444.5 | 73130.00 | 64269.21 | 28500.00 | 1500 | false | false | true |

## Counts and rejected blueprints

160 valid-blueprint probes; 6 pass all benchmark maneuver checks. These do not establish full journey readiness.

- Hydrolox solar with 3 tanks for mars: Total allocated module slots (31) exceeds hull frame limit (30)
- Hydrolox solar with 3 tanks for jupiter: Total allocated module slots (31) exceeds hull frame limit (30)
- Fission thermal with 3 tanks for mars: Total allocated module slots (32) exceeds hull frame limit (30)
- Fission thermal with 3 tanks for jupiter: Total allocated module slots (32) exceeds hull frame limit (30)

## Reproduction and limits

Run `mvn test` with JDK 27. Generated output: `engine/target/parking-tank-report.md`. All circular coplanar and impulsive limitations of the planetary report apply. Parking radii must remain within the approximate planetary sphere of influence. High-altitude station construction, cargo export, local approach, daily power, phase waiting and paid refill readiness require separate budgets. Opening stores are fixture seeds.
