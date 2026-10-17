# Food import cost audit

Five catalog-carrier probes buy 2,500 kg food at 4 credits/kg and working fuels at baseline prices from finite stocks. Main and generator tanks start empty; batteries start charged. Earth departure stations are at 4,000 km altitude and destination stations at 500 km. The existing travel and daily electrical models execute funded arrivals. Arrival markets have cash and a food price of 8, the ordinary spot rule's limiting scarcity price. Manual shipments may lose money.

| Drive | Destination | Delivered | Days | Initial fuel purchases | Outbound fuel consumed credits | Empty-return fuel quote credits | One-way landed credits/kg | Retail break-even with empty return | Ship construction materials credits |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| mod_hydrolox_rocket | earth | true | 2 | 561600.00 | 284646.88 | 326231.96 | 117.86 | 310.44 | 275500.00 |
| mod_hydrolox_rocket | moon | true | 7 | 561600.00 | 389477.07 | 428006.52 | 159.79 | 413.74 | 275500.00 |
| mod_hydrolox_rocket | mars | true | 871 | 561600.00 | 515271.69 | 526568.86 | 210.11 | 525.92 | 275500.00 |
| mod_fission_thruster | moon | true | 7 | 41500.00 | 28934.70 | 29155.72 | 15.57 | 34.05 | 167900.00 |
| mod_fission_thruster | mars | false | 0 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 167900.00 |

## Route outcomes

- mod_hydrolox_rocket to earth: Delivered; empty return quoted
- mod_hydrolox_rocket to moon: Delivered; empty return quoted
- mod_hydrolox_rocket to mars: Delivered; empty return quoted
- mod_fission_thruster to moon: Delivered; empty return quoted
- mod_fission_thruster to mars: Waiting: Main propellant cannot fund departure and capture with protected reserve; Drive reactor feed cannot fund the required propellant

## Cost boundaries

One-way landed cost includes source food and consumed outbound working fuel. Unused purchased reserves remain assets rather than voyage expenses. Retail break-even additionally includes the quoted empty return and the 80% wholesale settlement share; it excludes crew, maintenance, financing, tax and capital recovery. The return is a read-only operating quote after actual paid delivery, not an executed return voyage. Rejected departures are unavailable alternatives rather than zero-cost imports. Ship capital is a baseline material bill; its work and yard service are additional. No fresh fuel is injected during a voyage. These are station-to-station costs: surface export and final landing must be added for surface food. Source food is explicitly stocked at its market price, not claimed as free farm output. The fixed representative orbital phases and current propulsion catalog limit these comparisons. Reproduction: `mvn -pl engine -Dtest=FoodImportCostAuditTest test` with JDK 27.
