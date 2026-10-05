# Planetary transfer comparison

## Scope

72 read-only probes compare circular coplanar Hohmann benchmarks with the current straight-line planner. Each catalog blueprint carries a full main tank, its normal electrical reserves and either 10,000 kg of mixed steel and food or no trade cargo. Fission thermal ships retain their 15 kg drive reactor feed. Parking altitude is 500 km at both planets. Three relative phases are the ideal departure phase, ideal plus 90 degrees and ideal plus 180 degrees. The provisional impulse check limits each wet-mass burn estimate to 10% of the local parking period. Wait and coast are informational; no transfer is executed and no power, passenger or economy readiness is asserted.

## Findings

- All 72 benchmark probes retain the main contingency target and leave the game snapshot unchanged. Six pass the main propellant check: the loaded and empty MPD Mars cases at three phases. None pass every maneuver check. This is a capability comparison rather than a new supported travel order.
- The circular Earth-to-Mars coast takes 258.79 days. From the 500 km parking envelopes, departure requires 3,549.9 m/s and powered capture requires 2,068.6 m/s. Ideal alignment needs no wait; the selected 90-degree and 180-degree offsets add 195.02 and 390.05 days. Launch timing changes the wait rather than making the Hohmann coast faster.
- The loaded RP-1 solar ship has only 1,064.7 m/s of main-tank velocity change after protecting 750 kg. The benchmark calls for 42,846.69 kg of propellant at its current initial wet mass, against 14,250 kg usable aboard. Methalox and hydrolox also fail both loaded and empty planetary budgets with the current catalog tank and structure. This required quantity is a mathematical feasibility check, not a purchase quote: adding fuel would increase wet mass and equipment requirements again.
- Loaded fission thermal has 2,636.6 m/s available, below the 5,618.5 m/s Mars maneuver total. It needs 26,057.01 kg at current wet mass and also lacks sufficient propulsion reactor feed. Emptying the trade hold does not make either budget pass.
- Loaded MPD fission needs only 7,154.96 kg of main propellant for the Mars impulse benchmark, but its departure burn estimate exceeds 8,767 parking periods. It cannot perform the assumed short maneuver. The fuel column must never be interpreted as a funded 259-day MPD journey.
- Jupiter's coast takes 997.62 days and its low parking orbit is expensive: 6,289.1 m/s at Earth departure and 17,834.4 m/s at capture. Every tested blueprint fails main propellant there. A high destination orbit could materially change capture cost and needs a separate comparison before selecting a base-to-base model.
- The straight-line results alongside the benchmarks still describe the current planner. No actual journey changes, supplies are not consumed and no power or passenger endurance has been established for the proposed orbit. The coplanar approximation omits inclination and exact finite-burn navigation.

## Equations and sources

The benchmark sets stellar `mu = G * stellarMass`, transfer axis `a = (r1 + r2) / 2`, coast seconds `pi * sqrt(a^3 / mu)` and circular speed `sqrt(mu / r)`. Transfer endpoint speed is `sqrt(mu * (2 / r - 1 / a))`. Its difference from circular speed supplies the planet-relative hyperbolic excess speed. NASA's archived [Mars transfer calculation](https://pwg.gsfc.nasa.gov/stargaze/Smars2.htm) explains the departure and arrival velocity differences under circular-orbit assumptions.

Parking maneuvers use local planet mass: `deltaV = sqrt(excessSpeed^2 + 2 * planetMu / parkingRadius) - sqrt(planetMu / parkingRadius)`. This combines positive escape energy with a circular parking speed and applies the same energy change in reverse for powered capture. The 500 km parking altitude and the maximum burn duration of 10% of a local parking period are provisional comparison choices. They are not operational navigation criteria adopted from NASA.

Launch phase is `pi - destinationAngularRate * coastSeconds`, modulo one revolution. Relative angular speed determines the nonnegative wait until that phase. Waiting consumes no stocks in this read-only calculation; an eventual operational order must budget those real elapsed days. NASA JPL's [launch-window lesson](https://www.jpl.nasa.gov/edu/resources/lesson-plan/lets-go-to-mars-calculating-launch-windows/) describes the circular-orbit alignment approximation.

## Recommended next comparison

Compare explicit low and high departure and arrival parking envelopes, then calculate the fuel fractions and equipment needed for selected early cargo ships. Continue reporting both departure and capture with protected reserves. High bases may reduce a transfer's gravity-well costs but their local approach and exports require their own real budgets. Do not erase those costs by moving a nominal endpoint.

A single-stage feasibility estimate must solve for extra fuel's added mass rather than buying the apparent deficit at the existing wet mass. Tank mass, slots, propulsion feed and reserve capacity must grow consistently. Staging, separate departure tugs or new larger hulls require explicit equipment rules if the comparison shows they are needed. Low-thrust propulsion requires a separate finite-thrust orbital model.

## Results

| Equipment | Cargo kg | Target | Phase offset degrees | Straight-line scheduled days | Wait days | Coast days | Departure m/s | Capture m/s | Available main delta-v m/s | Required main kg | Protected main kg | Main fits | Reactor feed fits | Departure burn/period | Capture burn/period | Impulse fits |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---:|---:|---|
| RP-1 solar | 10000 | mars | 0 | 2609 | 0.00 | 258.79 | 3549.9 | 2068.6 | 1064.7 | 42846.69 | 750 | false | true | 0.0553 | 0.0248 | true |
| RP-1 solar | 10000 | mars | 90 | 2609 | 195.02 | 258.79 | 3549.9 | 2068.6 | 1064.7 | 42846.69 | 750 | false | true | 0.0553 | 0.0248 | true |
| RP-1 solar | 10000 | mars | 180 | 2609 | 390.05 | 258.79 | 3549.9 | 2068.6 | 1064.7 | 42846.69 | 750 | false | true | 0.0553 | 0.0248 | true |
| RP-1 solar | 0 | mars | 0 | 2030 | 0.00 | 258.79 | 3549.9 | 2068.6 | 1368.7 | 34762.41 | 750 | false | true | 0.0449 | 0.0201 | true |
| RP-1 solar | 0 | mars | 90 | 2030 | 195.02 | 258.79 | 3549.9 | 2068.6 | 1368.7 | 34762.41 | 750 | false | true | 0.0449 | 0.0201 | true |
| RP-1 solar | 0 | mars | 180 | 2030 | 390.05 | 258.79 | 3549.9 | 2068.6 | 1368.7 | 34762.41 | 750 | false | true | 0.0449 | 0.0201 | true |
| RP-1 solar | 10000 | jupiter | 0 | 15119 | 0.00 | 997.62 | 6289.1 | 17834.4 | 1064.7 | 52956.06 | 750 | false | true | 0.0980 | 0.1510 | false |
| RP-1 solar | 10000 | jupiter | 90 | 15119 | 99.70 | 997.62 | 6289.1 | 17834.4 | 1064.7 | 52956.06 | 750 | false | true | 0.0980 | 0.1510 | false |
| RP-1 solar | 10000 | jupiter | 180 | 15119 | 199.40 | 997.62 | 6289.1 | 17834.4 | 1064.7 | 52956.06 | 750 | false | true | 0.0980 | 0.1510 | false |
| RP-1 solar | 0 | jupiter | 0 | 11762 | 0.00 | 997.62 | 6289.1 | 17834.4 | 1368.7 | 42964.35 | 750 | false | true | 0.0795 | 0.1225 | false |
| RP-1 solar | 0 | jupiter | 90 | 11762 | 99.70 | 997.62 | 6289.1 | 17834.4 | 1368.7 | 42964.35 | 750 | false | true | 0.0795 | 0.1225 | false |
| RP-1 solar | 0 | jupiter | 180 | 11762 | 199.40 | 997.62 | 6289.1 | 17834.4 | 1368.7 | 42964.35 | 750 | false | true | 0.0795 | 0.1225 | false |
| Methalox solar | 10000 | mars | 0 | 2408 | 0.00 | 258.79 | 3549.9 | 2068.6 | 1153.6 | 41547.13 | 750 | false | true | 0.0537 | 0.0241 | true |
| Methalox solar | 10000 | mars | 90 | 2408 | 195.02 | 258.79 | 3549.9 | 2068.6 | 1153.6 | 41547.13 | 750 | false | true | 0.0537 | 0.0241 | true |
| Methalox solar | 10000 | mars | 180 | 2408 | 390.05 | 258.79 | 3549.9 | 2068.6 | 1153.6 | 41547.13 | 750 | false | true | 0.0537 | 0.0241 | true |
| Methalox solar | 0 | mars | 0 | 1876 | 0.00 | 258.79 | 3549.9 | 2068.6 | 1481.0 | 33737.52 | 750 | false | true | 0.0436 | 0.0195 | true |
| Methalox solar | 0 | mars | 90 | 1876 | 195.02 | 258.79 | 3549.9 | 2068.6 | 1481.0 | 33737.52 | 750 | false | true | 0.0436 | 0.0195 | true |
| Methalox solar | 0 | mars | 180 | 1876 | 390.05 | 258.79 | 3549.9 | 2068.6 | 1481.0 | 33737.52 | 750 | false | true | 0.0436 | 0.0195 | true |
| Methalox solar | 10000 | jupiter | 0 | 13955 | 0.00 | 997.62 | 6289.1 | 17834.4 | 1153.6 | 53121.59 | 750 | false | true | 0.0952 | 0.1467 | false |
| Methalox solar | 10000 | jupiter | 90 | 13955 | 99.70 | 997.62 | 6289.1 | 17834.4 | 1153.6 | 53121.59 | 750 | false | true | 0.0952 | 0.1467 | false |
| Methalox solar | 10000 | jupiter | 180 | 13955 | 199.40 | 997.62 | 6289.1 | 17834.4 | 1153.6 | 53121.59 | 750 | false | true | 0.0952 | 0.1467 | false |
| Methalox solar | 0 | jupiter | 0 | 10870 | 0.00 | 997.62 | 6289.1 | 17834.4 | 1481.0 | 43136.33 | 750 | false | true | 0.0773 | 0.1191 | false |
| Methalox solar | 0 | jupiter | 90 | 10870 | 99.70 | 997.62 | 6289.1 | 17834.4 | 1481.0 | 43136.33 | 750 | false | true | 0.0773 | 0.1191 | false |
| Methalox solar | 0 | jupiter | 180 | 10870 | 199.40 | 997.62 | 6289.1 | 17834.4 | 1481.0 | 43136.33 | 750 | false | true | 0.0773 | 0.1191 | false |
| Hydrolox solar | 10000 | mars | 0 | 2015 | 0.00 | 258.79 | 3549.9 | 2068.6 | 1378.7 | 38506.28 | 750 | false | true | 0.0520 | 0.0233 | true |
| Hydrolox solar | 10000 | mars | 90 | 2015 | 195.02 | 258.79 | 3549.9 | 2068.6 | 1378.7 | 38506.28 | 750 | false | true | 0.0520 | 0.0233 | true |
| Hydrolox solar | 10000 | mars | 180 | 2015 | 390.05 | 258.79 | 3549.9 | 2068.6 | 1378.7 | 38506.28 | 750 | false | true | 0.0520 | 0.0233 | true |
| Hydrolox solar | 0 | mars | 0 | 1577 | 0.00 | 258.79 | 3549.9 | 2068.6 | 1761.1 | 31375.49 | 750 | false | true | 0.0424 | 0.0190 | true |
| Hydrolox solar | 0 | mars | 90 | 1577 | 195.02 | 258.79 | 3549.9 | 2068.6 | 1761.1 | 31375.49 | 750 | false | true | 0.0424 | 0.0190 | true |
| Hydrolox solar | 0 | mars | 180 | 1577 | 390.05 | 258.79 | 3549.9 | 2068.6 | 1761.1 | 31375.49 | 750 | false | true | 0.0424 | 0.0190 | true |
| Hydrolox solar | 10000 | jupiter | 0 | 11676 | 0.00 | 997.62 | 6289.1 | 17834.4 | 1378.7 | 53746.35 | 750 | false | true | 0.0922 | 0.1421 | false |
| Hydrolox solar | 10000 | jupiter | 90 | 11676 | 99.70 | 997.62 | 6289.1 | 17834.4 | 1378.7 | 53746.35 | 750 | false | true | 0.0922 | 0.1421 | false |
| Hydrolox solar | 10000 | jupiter | 180 | 11676 | 199.40 | 997.62 | 6289.1 | 17834.4 | 1378.7 | 53746.35 | 750 | false | true | 0.0922 | 0.1421 | false |
| Hydrolox solar | 0 | jupiter | 0 | 9141 | 0.00 | 997.62 | 6289.1 | 17834.4 | 1761.1 | 43793.32 | 750 | false | true | 0.0751 | 0.1157 | false |
| Hydrolox solar | 0 | jupiter | 90 | 9141 | 99.70 | 997.62 | 6289.1 | 17834.4 | 1761.1 | 43793.32 | 750 | false | true | 0.0751 | 0.1157 | false |
| Hydrolox solar | 0 | jupiter | 180 | 9141 | 199.40 | 997.62 | 6289.1 | 17834.4 | 1761.1 | 43793.32 | 750 | false | true | 0.0751 | 0.1157 | false |
| RP-1 solar backup | 10000 | mars | 0 | 3848 | 0.00 | 258.79 | 3549.9 | 2068.6 | 721.8 | 60227.89 | 750 | false | true | 0.0778 | 0.0348 | true |
| RP-1 solar backup | 10000 | mars | 90 | 3848 | 195.02 | 258.79 | 3549.9 | 2068.6 | 721.8 | 60227.89 | 750 | false | true | 0.0778 | 0.0348 | true |
| RP-1 solar backup | 10000 | mars | 180 | 3848 | 390.05 | 258.79 | 3549.9 | 2068.6 | 721.8 | 60227.89 | 750 | false | true | 0.0778 | 0.0348 | true |
| RP-1 solar backup | 0 | mars | 0 | 3272 | 0.00 | 258.79 | 3549.9 | 2068.6 | 848.8 | 52143.61 | 750 | false | true | 0.0673 | 0.0301 | true |
| RP-1 solar backup | 0 | mars | 90 | 3272 | 195.02 | 258.79 | 3549.9 | 2068.6 | 848.8 | 52143.61 | 750 | false | true | 0.0673 | 0.0301 | true |
| RP-1 solar backup | 0 | mars | 180 | 3272 | 390.05 | 258.79 | 3549.9 | 2068.6 | 848.8 | 52143.61 | 750 | false | true | 0.0673 | 0.0301 | true |
| RP-1 solar backup | 10000 | jupiter | 0 | 22302 | 0.00 | 997.62 | 6289.1 | 17834.4 | 721.8 | 74438.23 | 750 | false | true | 0.1378 | 0.2123 | false |
| RP-1 solar backup | 10000 | jupiter | 90 | 22302 | 99.70 | 997.62 | 6289.1 | 17834.4 | 721.8 | 74438.23 | 750 | false | true | 0.1378 | 0.2123 | false |
| RP-1 solar backup | 10000 | jupiter | 180 | 22302 | 199.40 | 997.62 | 6289.1 | 17834.4 | 721.8 | 74438.23 | 750 | false | true | 0.1378 | 0.2123 | false |
| RP-1 solar backup | 0 | jupiter | 0 | 18965 | 0.00 | 997.62 | 6289.1 | 17834.4 | 848.8 | 64446.52 | 750 | false | true | 0.1193 | 0.1838 | false |
| RP-1 solar backup | 0 | jupiter | 90 | 18965 | 99.70 | 997.62 | 6289.1 | 17834.4 | 848.8 | 64446.52 | 750 | false | true | 0.1193 | 0.1838 | false |
| RP-1 solar backup | 0 | jupiter | 180 | 18965 | 199.40 | 997.62 | 6289.1 | 17834.4 | 848.8 | 64446.52 | 750 | false | true | 0.1193 | 0.1838 | false |
| Fission thermal | 10000 | mars | 0 | 1054 | 0.00 | 258.79 | 3549.9 | 2068.6 | 2636.6 | 26057.01 | 750 | false | false | 0.0413 | 0.0185 | true |
| Fission thermal | 10000 | mars | 90 | 1054 | 195.02 | 258.79 | 3549.9 | 2068.6 | 2636.6 | 26057.01 | 750 | false | false | 0.0413 | 0.0185 | true |
| Fission thermal | 10000 | mars | 180 | 1054 | 390.05 | 258.79 | 3549.9 | 2068.6 | 2636.6 | 26057.01 | 750 | false | false | 0.0413 | 0.0185 | true |
| Fission thermal | 0 | mars | 0 | 835 | 0.00 | 258.79 | 3549.9 | 2068.6 | 3326.7 | 21413.51 | 750 | false | false | 0.0340 | 0.0152 | true |
| Fission thermal | 0 | mars | 90 | 835 | 195.02 | 258.79 | 3549.9 | 2068.6 | 3326.7 | 21413.51 | 750 | false | false | 0.0340 | 0.0152 | true |
| Fission thermal | 0 | mars | 180 | 835 | 390.05 | 258.79 | 3549.9 | 2068.6 | 3326.7 | 21413.51 | 750 | false | false | 0.0340 | 0.0152 | true |
| Fission thermal | 10000 | jupiter | 0 | 6106 | 0.00 | 997.62 | 6289.1 | 17834.4 | 2636.6 | 52269.08 | 750 | false | false | 0.0732 | 0.1129 | false |
| Fission thermal | 10000 | jupiter | 90 | 6106 | 99.70 | 997.62 | 6289.1 | 17834.4 | 2636.6 | 52269.08 | 750 | false | false | 0.0732 | 0.1129 | false |
| Fission thermal | 10000 | jupiter | 180 | 6106 | 199.40 | 997.62 | 6289.1 | 17834.4 | 2636.6 | 52269.08 | 750 | false | false | 0.0732 | 0.1129 | false |
| Fission thermal | 0 | jupiter | 0 | 4839 | 0.00 | 997.62 | 6289.1 | 17834.4 | 3326.7 | 42954.45 | 750 | false | false | 0.0602 | 0.0928 | true |
| Fission thermal | 0 | jupiter | 90 | 4839 | 99.70 | 997.62 | 6289.1 | 17834.4 | 3326.7 | 42954.45 | 750 | false | false | 0.0602 | 0.0928 | true |
| Fission thermal | 0 | jupiter | 180 | 4839 | 199.40 | 997.62 | 6289.1 | 17834.4 | 3326.7 | 42954.45 | 750 | false | false | 0.0602 | 0.0928 | true |
| MPD fission | 10000 | mars | 0 | 949 | 0.00 | 258.79 | 3549.9 | 2068.6 | 12097.7 | 7154.96 | 750 | true | true | 8767.9111 | 3924.0373 | false |
| MPD fission | 10000 | mars | 90 | 949 | 195.02 | 258.79 | 3549.9 | 2068.6 | 12097.7 | 7154.96 | 750 | true | true | 8767.9111 | 3924.0373 | false |
| MPD fission | 10000 | mars | 180 | 949 | 390.05 | 258.79 | 3549.9 | 2068.6 | 12097.7 | 7154.96 | 750 | true | true | 8767.9111 | 3924.0373 | false |
| MPD fission | 0 | mars | 0 | 858 | 0.00 | 258.79 | 3549.9 | 2068.6 | 15397.5 | 5844.52 | 750 | true | true | 7162.0666 | 3205.3491 | false |
| MPD fission | 0 | mars | 90 | 858 | 195.02 | 258.79 | 3549.9 | 2068.6 | 15397.5 | 5844.52 | 750 | true | true | 7162.0666 | 3205.3491 | false |
| MPD fission | 0 | mars | 180 | 858 | 390.05 | 258.79 | 3549.9 | 2068.6 | 15397.5 | 5844.52 | 750 | true | true | 7162.0666 | 3205.3491 | false |
| MPD fission | 10000 | jupiter | 0 | 2311 | 0.00 | 997.62 | 6289.1 | 17834.4 | 12097.7 | 24727.27 | 750 | false | true | 15533.5891 | 23938.9886 | false |
| MPD fission | 10000 | jupiter | 90 | 2311 | 99.70 | 997.62 | 6289.1 | 17834.4 | 12097.7 | 24727.27 | 750 | false | true | 15533.5891 | 23938.9886 | false |
| MPD fission | 10000 | jupiter | 180 | 2311 | 199.40 | 997.62 | 6289.1 | 17834.4 | 12097.7 | 24727.27 | 750 | false | true | 15533.5891 | 23938.9886 | false |
| MPD fission | 0 | jupiter | 0 | 2065 | 0.00 | 997.62 | 6289.1 | 17834.4 | 15397.5 | 20198.47 | 750 | false | true | 12688.6094 | 19554.5584 | false |
| MPD fission | 0 | jupiter | 90 | 2065 | 99.70 | 997.62 | 6289.1 | 17834.4 | 15397.5 | 20198.47 | 750 | false | true | 12688.6094 | 19554.5584 | false |
| MPD fission | 0 | jupiter | 180 | 2065 | 199.40 | 997.62 | 6289.1 | 17834.4 | 15397.5 | 20198.47 | 750 | false | true | 12688.6094 | 19554.5584 | false |

## Limits and reproduction

Run `mvn test` with JDK 27. Generated output: `engine/target/planetary-transfer-report.md`. Coplanar benchmarks omit catalog inclination, station phases, sphere-of-influence transit times, exact finite burns, evolving mass, aerobraking, gravity assists, boil-off and electrical endurance. Parking escape and powered capture use hyperbolic excess speed and local planet mass. Feed checks retain reactor feed for protected propellant. Available main delta-v only measures main propellant; the separate feed column remains required. No row establishes full journey readiness or authorizes travel.

## Parking and tank extension

The next comparison is now recorded in [ParkingOrbitTankReport.md](ParkingOrbitTankReport.md). It checks real catalog tank additions and explicit parking altitudes, including a Mars case where a higher capture orbit is more expensive.
