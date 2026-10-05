# Auxiliary power audit

## Scope

3 one-way loaded station probes use the repository's Sol bodies, catalog blueprints and paid mixed steel and food shipments. The baseline is 5,000 kg of each good; these probes all carry the full mixed load. Source stations orbit Earth; targets orbit the Moon, Mars or Jupiter. Body positions retain deterministic representative orbital phases, not ephemerides or gravity-assisted trajectories. Ships start with full legal working tanks, batteries and required fission drive feed. No port sells fuel and no free supplies are injected. Destination buyer cash is seeded. Funded voyages advance actual daily power and movement ticks for up to 12,000 days. Completed voyages retain their mixed cargo for 60 days without electrical purchases, then sell it. Each chemical ship uses one solar array, a battery, a chemical auxiliary generator and a full dedicated generator tank. All arrays occupy slots and add captured dry mass. Main contingency targets remain protected.

## Findings and tuning candidates

- The catalog combination occupies 28 of the medium hull's 30 slots. Every array, battery, generator and tank contributes real mass. Opening tanks are full fixture seeds rather than purchased production.
- The loaded Moon voyage completes in 13 days and Mars in 3,848 days. Both match forecast propellant use within 0.00001 kg and battery charge within 0.00001 kWh. Neither consumes auxiliary generator feed during travel. Both preserve the 10,000 kg mixed shipment through a further 60 days without refills and sell it in full.
- Comparable solar-only ships take nine days to the Moon and 2,609 days to Mars. The additional generator, tank and 15,000 kg backup stock explain the slower hybrid configuration. Carried insurance is useful and permitted; these measurements do not establish a required minimum or an optimal reserve policy.
- The Jupiter probe is rejected before purchase commitment because its carried electrical reserves cannot support the long itinerary and arrival reserve. No free thrust or supplies are introduced to make it viable.
- Electrical dispatch uses sunlight first. Solar-equipped ships prefer stored energy when one fueled backup source can cover the remaining load, switch to generators at depletion and supplement battery discharge limits. Undersized generators run first to preserve battery support for their output shortfall. Generator-only ships retain their previous priority.
- Unit tests cover eclipse recharge losses, battery depletion, exact backup consumption, weak sunlight, discharge peaks and insufficient generator output. The long forecast test still agrees with daily processing across feed exhaustion and charging limits.
- Chemical thrust energy remains supplied by main propellant. Component ratings, travel times, backup storage sizing and reserve horizons need later tuning. The designer now offers combined solar and chemical or fission generation using these catalog construction rules. Hybrid selections are preserved when editing and registration remains tick-validated.

## Results

| Equipment | Cargo kg | Target body | Distance km | Destination solar factor | Funded | Planned days | Actual tick days | Completed | Sold kg | Planned main kg | Actual main kg | Planned electrical kg | Actual electrical kg | Battery error kWh | Arrival dwell ready |
|---|---:|---|---:|---:|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---|
| Chemical solar with backup | 10000 | moon | 386841 | 1.0000 | true | 13 | 13 | true | 10000 | 13663.0469 | 13663.0469 | 0.000000 | 0.000000 | -0.000000 | true |
| Chemical solar with backup | 10000 | mars | 119975168 | 0.4309 | true | 3848 | 3848 | true | 10000 | 14248.5497 | 14248.5497 | 0.000000 | 0.000000 | -0.000000 | true |
| Chemical solar with backup | 10000 | jupiter | 695409720 | 0.0369 | false | 22302 | 0 | false | 0 | 0.0000 | 0.0000 | 0.000000 | 0.000000 | 0.000000 | false |

## Departure explanations

- Chemical solar with backup to moon: Next port is reachable with carried electricity for a 60-day wait.
- Chemical solar with backup to mars: Next port is reachable with carried electricity for a 60-day wait.
- Chemical solar with backup to jupiter: Local leg requires electricity and an arrival reserve.

## Limits and reproduction

Run `mvn test` with JDK 27. The generated report is `engine/target/auxiliary-power-report.md`. Unfunded rows show loaded analytic durations only; actual days and consumption are zero because no journey is executed. Unfinished funded voyages report partial tick consumption. Forecast agreement is asserted only for completed voyages. Solar uses the conservative endpoint transfer envelope and destination eclipses; exact illumination along the path, evolving mass, radiation, orbital mechanics, combat, passengers and the full economy are excluded. Opening supplies are fixture seeds rather than factory output. Fuel stock shortages remain real throughout each probe. Catalog values and reserve coefficients are provisional.
