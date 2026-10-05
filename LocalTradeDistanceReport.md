# Local trade distance audit

## Scope

Sixteen one-way loaded station probes use the repository's Sol bodies, catalog blueprints and paid mixed steel and food shipments. The baseline is 5,000 kg of each good; a half-load probe carries 2,500 kg of each. Source stations orbit Earth; targets orbit the Moon, Mars or Jupiter. Body positions retain deterministic representative orbital phases, not ephemerides or gravity-assisted trajectories. Ships start with full legal working tanks, batteries and required fission drive feed. No port sells fuel and no free supplies are injected. Destination buyer cash is seeded. Funded voyages advance actual daily power and movement ticks for up to 12,000 days. Completed voyages retain their mixed cargo for 60 days without electrical purchases, then sell it. Solar MPD blueprints use two catalog arrays and chemical solar uses one. All arrays occupy slots and add captured dry mass. Main contingency targets remain protected.

## Findings and tuning candidates

- Ten of sixteen probes fund and complete their voyages; six are rejected before purchases commit. Every completed voyage matches scheduled arrival day, main fuel and electrical fuel within 0.00001 kg and battery charge within 0.00001 kWh. Carried food survives both travel and a further 60 days aboard at the destination, with no power outage, tanker assistance or port fuel purchase. The complete paid mixed shipment then sells.
- Representative station separations are 386,841 km to the Moon, 119,975,168 km to Mars and 695,409,720 km to Jupiter. These are distances between the current frozen fixture positions, not closest approaches or real launch trajectories. Destination solar factors are 1.0000, 0.4309 and 0.0369 respectively. The transfer envelope uses the weaker endpoint and repeated orbital eclipses.
- Full-load Moon trips take nine days with chemical solar, four with fission thermal and 54-55 with MPD. Chemical auxiliary electricity is insufficient for the full loaded journey plus the required 60-day wait. Halving the mixed cargo to 5,000 kg makes that ship's 12-day trip and loaded wait viable. A separate test confirms the existing basket planner finds a smaller paid profitable mixed load without changing opening fuel or introducing free refills.
- Funded Mars trips take 2,609 days with chemical solar, 1,054 with fission thermal and 949 with MPD fission. Funded Jupiter trips take 6,106 days with fission thermal and 2,311 with MPD fission. These measured durations are balance evidence, not accepted gameplay targets. Defining useful travel-time targets and evaluating orbital mechanics should precede coefficient tuning.
- Two-array solar MPD is rejected at Mars by electrical endurance and has no supported loaded trajectory at Jupiter. Four catalog arrays would raise the current medium blueprint to 36 allocated slots against its 30-slot limit; a regression test verifies the blueprint rejection. More array output must respect hull capacity, mass, eclipse storage and sustained drive demand. Larger hull support or revised equipment allocation needs design work.
- Existing power accounting retains captured drive draw during each burn even where solar-limited thrust is lower. The conservative forecast and actual ticks agree under that rule. Variable electrical throttling is a possible future refinement and requires the same captured burn demand in previews and ticks.
- The 60-day dwell allowance materially affects chemical auxiliary eligibility. Perishable cargo mix and shipment size matter as well as total mass. Future comparisons should examine stock-aware reserve horizons, explicit backup generators and larger electrical tanks while keeping main contingency fuel and actual braking protected. No production coefficient or opening stock was changed to force a viable voyage in this audit.
- Initial component inventories, yard availability, owner funds and buyer cash are fixture seeds. The separate paid production audit covers refinery transactions. These tests do not claim full economy reliability, continuously moving orbits, gravity assists or full stellar-distance transport.

## Results

| Equipment | Cargo kg | Target body | Distance km | Destination solar factor | Funded | Planned days | Actual tick days | Completed | Sold kg | Planned main kg | Actual main kg | Planned electrical kg | Actual electrical kg | Battery error kWh | Arrival dwell ready |
|---|---:|---|---:|---:|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---|
| Chemical auxiliary | 10000 | moon | 386841 | 1.0000 | false | 12 | 0 | false | 0 | 0.0000 | 0.0000 | 0.000000 | 0.000000 | 0.000000 | false |
| Chemical solar | 10000 | moon | 386841 | 1.0000 | true | 9 | 9 | true | 10000 | 13447.0785 | 13447.0785 | 0.000000 | 0.000000 | -0.000000 | true |
| Fission thermal | 10000 | moon | 386841 | 1.0000 | true | 4 | 4 | true | 10000 | 12359.8419 | 12359.8419 | 0.000153 | 0.000153 | 0.000000 | true |
| MPD solar | 10000 | moon | 386841 | 1.0000 | true | 55 | 55 | true | 10000 | 403.1239 | 403.1239 | 0.000000 | 0.000000 | -0.000000 | true |
| MPD fission | 10000 | moon | 386841 | 1.0000 | true | 54 | 54 | true | 10000 | 421.7076 | 421.7076 | 0.026174 | 0.026174 | 0.000000 | true |
| Chemical auxiliary | 10000 | mars | 119975168 | 0.4309 | false | 3704 | 0 | false | 0 | 0.0000 | 0.0000 | 0.000000 | 0.000000 | 0.000000 | false |
| Chemical solar | 10000 | mars | 119975168 | 0.4309 | true | 2609 | 2609 | true | 10000 | 14246.8379 | 14246.8379 | 0.000000 | 0.000000 | 0.000000 | true |
| Fission thermal | 10000 | mars | 119975168 | 0.4309 | true | 1054 | 1054 | true | 10000 | 14242.2911 | 14242.2911 | 0.040053 | 0.040053 | 0.000000 | true |
| MPD solar | 10000 | mars | 119975168 | 0.4309 | false | 1087 | 0 | false | 0 | 0.0000 | 0.0000 | 0.000000 | 0.000000 | 0.000000 | false |
| MPD fission | 10000 | mars | 119975168 | 0.4309 | true | 949 | 949 | true | 10000 | 7260.8661 | 7260.8661 | 0.480007 | 0.480007 | 0.000000 | true |
| Chemical auxiliary | 10000 | jupiter | 695409720 | 0.0369 | false | 21468 | 0 | false | 0 | 0.0000 | 0.0000 | 0.000000 | 0.000000 | 0.000000 | false |
| Chemical solar | 10000 | jupiter | 695409720 | 0.0369 | false | 15119 | 0 | false | 0 | 0.0000 | 0.0000 | 0.000000 | 0.000000 | 0.000000 | false |
| Fission thermal | 10000 | jupiter | 695409720 | 0.0369 | true | 6106 | 6106 | true | 10000 | 14248.8654 | 14248.8654 | 0.232029 | 0.232029 | 0.000000 | true |
| MPD solar | 10000 | jupiter | 695409720 | 0.0369 | false | Unavailable | 0 | false | 0 | 0.0000 | 0.0000 | 0.000000 | 0.000000 | 0.000000 | false |
| MPD fission | 10000 | jupiter | 695409720 | 0.0369 | true | 2311 | 2311 | true | 10000 | 14241.6022 | 14241.6022 | 1.028103 | 1.028103 | 0.000000 | true |
| Chemical auxiliary half load | 5000 | moon | 386841 | 1.0000 | true | 12 | 12 | true | 5000 | 13203.5791 | 13203.5791 | 2071.887849 | 2071.887849 | 0.000000 | true |

## Departure explanations

- Chemical auxiliary to moon: Waiting: destination shortages or a journey over two days require carried electricity for a 60-day port wait.
- Chemical solar to moon: Next port is reachable with carried electricity for a 60-day wait.
- Fission thermal to moon: Next port is reachable with carried electricity for a 60-day wait.
- MPD solar to moon: Next port is reachable with carried electricity for a 60-day wait.
- MPD fission to moon: Next port is reachable with carried electricity for a 60-day wait.
- Chemical auxiliary to mars: Local leg requires electricity and an arrival reserve.
- Chemical solar to mars: Next port is reachable with carried electricity for a 60-day wait.
- Fission thermal to mars: Next port is reachable with carried electricity for a 60-day wait.
- MPD solar to mars: Local leg requires electricity and an arrival reserve.
- MPD fission to mars: Next port is reachable with carried electricity for a 60-day wait.
- Chemical auxiliary to jupiter: Local leg requires electricity and an arrival reserve.
- Chemical solar to jupiter: Local leg requires electricity and an arrival reserve.
- Fission thermal to jupiter: Next port is reachable with carried electricity for a 60-day wait.
- MPD solar to jupiter: Waiting: local maneuver propellant or drive feed is insufficient.
- MPD fission to jupiter: Next port is reachable with carried electricity for a 60-day wait.
- Chemical auxiliary half load to moon: Next port is reachable with carried electricity for a 60-day wait.

## Limits and reproduction

Run `mvn test` with JDK 27. The generated report is `engine/target/local-trade-distance-report.md`. Unfunded rows show loaded analytic durations only; actual days and consumption are zero because no journey is executed. Unfinished funded voyages report partial tick consumption. Forecast agreement is asserted only for completed voyages. Solar uses the conservative endpoint transfer envelope and destination eclipses; exact illumination along the path, evolving mass, radiation, orbital mechanics, combat, passengers and the full economy are excluded. Opening supplies are fixture seeds rather than factory output. Fuel stock shortages remain real throughout each probe. Catalog values and reserve coefficients are provisional.
