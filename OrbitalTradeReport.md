# Paid orbital trade audit

Fourteen deterministic Earth-to-Mars probes use live chemical freighter blueprints and actual paid main fuel, grid charging and mixed cargo. Ships begin empty. Source ports hold finite stocks and destination buyers have finite cash. Each accepted basket carries 1,250 kg steel and 1,250 kg food bought at 1 credit/kg and sold against a 3 credit/kg posted price at the current 80% wholesale share. Normal reserves are tested explicitly at 5%, 10% and 20%. High ports are fixture-built at 100,000 km Earth and 8,000 km Mars; low ports are 500 km. No return leg is required. Campaign epoch changes the actual saved launch window. Solar arrays supply auxiliary electricity.

Fuel prices of 1 and 0.01 credits/kg are sensitivity inputs, not proposed catalog prices. Charging pays a distinct port owner and withdraws real grid storage. Consumption result equals sales minus cargo cost, consumed fuel replacement cost and initial charging. Cash change also includes the purchase of unused fuel that remains aboard. Route result is existing realized cargo accounting; it excludes supplies bought before assignment. Ship and station construction, wages, depreciation, factory replenishment, interest, tariffs and exact shadows are outside this audit.

| Drive | Reserve | Epoch | Parking | Fuel price | Accepted | Wait days | Total days | Main used kg | Main remaining kg | Reserve margin kg | Cash change | Consumption result | Route result |
|---|---:|---:|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| mod_chemical_rocket | 5% | 0 | High | 1.00 | true | 610.68 | 870 | 111168.30 | 8831.70 | 2831.70 | -116511.11 | -107679.41 | 3500.00 |
| mod_chemical_rocket | 10% | 0 | High | 1.00 | false | Unavailable | Unavailable | Unavailable | 120000.00 | Unavailable | -120011.11 | Unavailable | 0.00 |
| mod_chemical_rocket | 20% | 0 | High | 1.00 | false | Unavailable | Unavailable | Unavailable | 120000.00 | Unavailable | -120011.11 | Unavailable | 0.00 |
| mod_chemical_rocket | 5% | 0 | Low | 1.00 | false | Unavailable | Unavailable | Unavailable | 120000.00 | Unavailable | -120011.11 | Unavailable | 0.00 |
| mod_methalox_rocket | 5% | 0 | High | 1.00 | true | 610.68 | 870 | 106312.70 | 13687.30 | 7687.30 | -116511.11 | -102823.81 | 3500.00 |
| mod_methalox_rocket | 10% | 0 | High | 1.00 | true | 610.68 | 870 | 106312.70 | 13687.30 | 1687.30 | -116511.11 | -102823.81 | 3500.00 |
| mod_methalox_rocket | 20% | 0 | High | 1.00 | false | Unavailable | Unavailable | Unavailable | 120000.00 | Unavailable | -120011.11 | Unavailable | 0.00 |
| mod_methalox_rocket | 5% | 0 | Low | 1.00 | false | Unavailable | Unavailable | Unavailable | 120000.00 | Unavailable | -120011.11 | Unavailable | 0.00 |
| mod_hydrolox_rocket | 5% | 0 | High | 1.00 | true | 610.68 | 870 | 95209.26 | 24790.74 | 18790.74 | -116511.11 | -91720.37 | 3500.00 |
| mod_hydrolox_rocket | 10% | 0 | High | 1.00 | true | 610.68 | 870 | 95209.26 | 24790.74 | 12790.74 | -116511.11 | -91720.37 | 3500.00 |
| mod_hydrolox_rocket | 20% | 0 | High | 1.00 | true | 610.68 | 870 | 95209.26 | 24790.74 | 790.74 | -116511.11 | -91720.37 | 3500.00 |
| mod_hydrolox_rocket | 5% | 0 | Low | 1.00 | false | Unavailable | Unavailable | Unavailable | 120000.00 | Unavailable | -120011.11 | Unavailable | 0.00 |
| mod_hydrolox_rocket | 20% | 365 | High | 1.00 | true | 245.68 | 505 | 95209.26 | 24790.74 | 790.74 | -116511.11 | -91720.37 | 3500.00 |
| mod_hydrolox_rocket | 20% | 0 | High | 0.01 | true | 610.68 | 870 | 95209.26 | 24790.74 | 790.74 | 2288.89 | 2536.80 | 3500.00 |

## Measured tuning findings

- Eight of fourteen scenarios complete a funded mixed shipment. RP-1 supports the tested 5% reserve, methalox supports 5% and 10% and hydrolox supports all three high-orbit targets. All tested low-orbit cases fail.
- The seeded day-zero launch waits about 611 days, giving an 870-day trip. The day-365 launch waits about 246 days and takes 505 days. The coast alone is about 259 days, so it is insufficient as a delivery estimate.
- Hydrolox retains about 791 kg above its 20% reserve after the funded docking approach. This narrow margin remains a tuning concern for unexpected maneuvers.
- At 1 credit/kg fuel all accepted voyages lose money on the stated consumption basis. At 0.01 credit/kg the hydrolox case earns about 2,537 credits before omitted costs. Equipment feasibility does not imply profitability.
- A separate automated-planner check rejects the loss-making case even with prepaid full tanks and selects the profitable mixed basket without mutating its input snapshot.
- The route ledger reports 3,500 credits of cargo margin because main fuel and commissioning charge were bought before assignment. That ledger is not a complete voyage consumption result. Rejected baskets spend no cargo cash; fuel and charge bought before planning remain real paid inventory.

## Rejected shipments

- mod_chemical_rocket at 10.0% reserve, high parking: Main propellant cannot fund departure and capture with protected reserve.
- mod_chemical_rocket at 20.0% reserve, high parking: Main propellant cannot fund departure and capture with protected reserve.
- mod_chemical_rocket at 5.0% reserve, low parking: Main propellant cannot fund departure and capture with protected reserve; Burn exceeds the provisional 10% parking-period impulse limit.
- mod_methalox_rocket at 20.0% reserve, high parking: Main propellant cannot fund departure and capture with protected reserve.
- mod_methalox_rocket at 5.0% reserve, low parking: Main propellant cannot fund departure and capture with protected reserve; Burn exceeds the provisional 10% parking-period impulse limit.
- mod_hydrolox_rocket at 5.0% reserve, low parking: Main propellant cannot fund departure and capture with protected reserve; Burn exceeds the provisional 10% parking-period impulse limit.
