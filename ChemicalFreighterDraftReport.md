# Chemical freighter draft study

## Scope

Proposed tank and compact hold values are test-local prototypes, not live catalog components. They use the existing medium frame, steel material, chemical drives, solar array and battery. The tank has 10% tare mass, four slots per 30,000 kg capacity and a continuous 10 kW conditioning allowance. All rows carry their full mixed steel and food hold with 0.5 cm armor. Manufacturing complexity is limited to 4. High route means 100,000 km Earth departure and 8,000 km Mars arrival; low route is 500 km at each end. Electrical checks conservatively apply full drive auxiliary demand during 390.05 days of hypothetical phase waiting, 259 rounded coast days and 60 arrival days, under Mars's 10-hour light/2-hour eclipse envelope. This does not simulate operational orbits, tank boil-off, real purchases or paid construction.

## Recommended candidate

Use the 120,000 kg tank and 2,500 kg compact hold family under [ChemicalFreighterDraft.md](ChemicalFreighterDraft.md). Its measured RP-1, methalox and hydrolox variants cover the routine, elevated and wartime reserve targets respectively for the high-orbit Mars benchmark. The hydrolox wartime margin is only about 940 kg; 1 cm armor fails that target. The same low-orbit route fails main fuel in every valid tested configuration.

Proposed equipment remains confined to tests and cannot be registered in the live game. The selected hydrolox draft passes a 710-day conservative solar/battery screen and repeated eclipse accounting. Tank thermal-loss behavior, real orbital access, paid construction and operational navigation remain required before deployment.

## Results

| Drive | Main capacity kg | Cargo kg | Slots | Valid | Dry kg | Route | Maneuver delta-v m/s | Main required kg | 5% fits | 10% fits | 20% fits | Impulse fits | Auxiliary power fits | Build work | Build complexity |
|---|---:|---:|---:|---|---:|---|---:|---:|---|---|---|---|---|---:|---:|
| mod_chemical_rocket | 90000 | 2500 | 25 | true | 35625 | Low | 5618.5 | 103579.84 | false | false | false | false | true | 356.25 | 3 |
| mod_chemical_rocket | 90000 | 2500 | 25 | true | 35625 | High | 3957.4 | 88118.15 | false | false | false | true | true | 356.25 | 3 |
| mod_chemical_rocket | 90000 | 5000 | 26 | true | 35950 | Low | 5618.5 | 105863.65 | false | false | false | false | true | 359.50 | 3 |
| mod_chemical_rocket | 90000 | 5000 | 26 | true | 35950 | High | 3957.4 | 90061.05 | false | false | false | true | true | 359.50 | 3 |
| mod_chemical_rocket | 120000 | 2500 | 29 | true | 38925 | Low | 5618.5 | 130500.50 | false | false | false | false | true | 389.25 | 3 |
| mod_chemical_rocket | 120000 | 2500 | 29 | true | 38925 | High | 3957.4 | 111020.27 | true | false | false | true | true | 389.25 | 3 |
| mod_chemical_rocket | 120000 | 5000 | 30 | true | 39250 | Low | 5618.5 | 132784.30 | false | false | false | false | true | 392.50 | 3 |
| mod_chemical_rocket | 120000 | 5000 | 30 | true | 39250 | High | 3957.4 | 112963.17 | true | false | false | true | true | 392.50 | 3 |
| mod_methalox_rocket | 90000 | 2500 | 25 | true | 35825 | Low | 5618.5 | 100216.83 | false | false | false | false | true | 358.25 | 3 |
| mod_methalox_rocket | 90000 | 2500 | 25 | true | 35825 | High | 3957.4 | 84289.98 | true | false | false | true | true | 358.25 | 3 |
| mod_methalox_rocket | 90000 | 5000 | 26 | true | 36150 | Low | 5618.5 | 102423.05 | false | false | false | false | true | 361.50 | 3 |
| mod_methalox_rocket | 90000 | 5000 | 26 | true | 36150 | High | 3957.4 | 86145.57 | false | false | false | true | true | 361.50 | 3 |
| mod_methalox_rocket | 120000 | 2500 | 29 | true | 39125 | Low | 5618.5 | 126222.84 | false | false | false | false | true | 391.25 | 3 |
| mod_methalox_rocket | 120000 | 2500 | 29 | true | 39125 | High | 3957.4 | 106163.01 | true | true | false | true | true | 391.25 | 3 |
| mod_methalox_rocket | 120000 | 5000 | 30 | true | 39450 | Low | 5618.5 | 128429.05 | false | false | false | false | true | 394.50 | 3 |
| mod_methalox_rocket | 120000 | 5000 | 30 | true | 39450 | High | 3957.4 | 108018.60 | true | false | false | true | true | 394.50 | 3 |
| mod_hydrolox_rocket | 90000 | 2500 | 26 | true | 36700 | Low | 5618.5 | 92129.84 | false | false | false | false | true | 367.00 | 4 |
| mod_hydrolox_rocket | 90000 | 2500 | 26 | true | 36700 | High | 3957.4 | 75579.66 | true | true | false | true | true | 367.00 | 4 |
| mod_hydrolox_rocket | 90000 | 5000 | 27 | true | 37025 | Low | 5618.5 | 94144.29 | false | false | false | false | false | 370.25 | 4 |
| mod_hydrolox_rocket | 90000 | 5000 | 27 | true | 37025 | High | 3957.4 | 77232.24 | true | true | false | true | false | 370.25 | 4 |
| mod_hydrolox_rocket | 120000 | 2500 | 30 | true | 40000 | Low | 5618.5 | 115875.38 | false | false | false | false | true | 400.00 | 4 |
| mod_hydrolox_rocket | 120000 | 2500 | 30 | true | 40000 | High | 3957.4 | 95059.56 | true | true | true | true | true | 400.00 | 4 |

## Rejected layouts

- mod_chemical_rocket / 150000.0 kg tank / 2500.0 kg hold: Total allocated module slots (33) exceeds hull frame limit (30)
- mod_chemical_rocket / 150000.0 kg tank / 5000.0 kg hold: Total allocated module slots (34) exceeds hull frame limit (30)
- mod_methalox_rocket / 150000.0 kg tank / 2500.0 kg hold: Total allocated module slots (33) exceeds hull frame limit (30)
- mod_methalox_rocket / 150000.0 kg tank / 5000.0 kg hold: Total allocated module slots (34) exceeds hull frame limit (30)
- mod_hydrolox_rocket / 120000.0 kg tank / 5000.0 kg hold: Total allocated module slots (31) exceeds hull frame limit (30)
- mod_hydrolox_rocket / 150000.0 kg tank / 2500.0 kg hold: Total allocated module slots (34) exceeds hull frame limit (30)
- mod_hydrolox_rocket / 150000.0 kg tank / 5000.0 kg hold: Total allocated module slots (35) exceeds hull frame limit (30)

## Reproduction

Run `mvn test` with JDK 27. Generated output: `engine/target/chemical-freighter-draft-report.md`. The orbital comparison, main reserve, short-burn and auxiliary electricity checks are separate. A passing row is a prototype bound, not full funded voyage readiness.
