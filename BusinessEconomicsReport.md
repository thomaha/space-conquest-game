# Three-year business operation audit

Each scenario runs 1,080 simulation days with independent extractors, a farm, a food processor and an electricity provider. All research is available. Population starts at 1,000 and follows ordinary campaign updates. Each firm starts with 10 million credits; the market and government each start with 100 million. Opening farm inputs cover 1.5 cycles, reactor fuel 30 days and discovered deposits are finite. The reactor-ore quarry uses tier 2 to support the reactor's fuel requirement. One initial harvest is prefunded. Later explicit harvest and CO₂ orders share remaining market money with ordinary automatic long-lead procurement. There is no external replenishment. Government basic-needs support and its debt remain ordinary simulation behavior, not proof of fiscal sustainability.

## Industrial soil cultivation

Harvests: 9. Household food bought: 112,727.00 kg. Unmet food: 0.00 kg. Closing food stock: 6,850.93 kg.

| Business | Produced kg | Marketed kg | Paused days | Operating result | Tax paid | After-tax result | Held replacement cash | Spendable cash | Unsold kg |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| producer | 388800.00 | 229148.66 | 0 | -469768.96 | 42529.45 | -512298.41 | 5142.69 | 9482558.90 | 159651.34 |
| processor | 40800.00 | 6029.27 | 1021 | -5002.64 | 36500.57 | -41503.21 | 1138.06 | 9957358.73 | 34770.73 |
| generator | 0.00 | 0.00 | 0 | 438021.52 | 109505.38 | 328516.14 | 2742.69 | 10325773.45 | 0.00 |
| potash | 4725.00 | 4650.00 | 1076 | 1109179.92 | 277294.98 | 831884.94 | 5142.69 | 10826742.25 | 75.00 |
| reactor-fuel | 1708.63 | 26.92 | 931 | -826337.09 | 7902.48 | -834239.57 | 4.76 | 9165755.67 | 1681.71 |
| phosphates | 708075.00 | 4650.00 | 555 | -67959.33 | 98974.11 | -166933.44 | 5142.69 | 9827923.87 | 703425.00 |
| reactor-ore | 49207.50 | 49186.25 | 594 | -785078.66 | 4682.84 | -789761.51 | 1428.53 | 9208809.97 | 21.25 |
| nitrates | 1144800.00 | 9300.00 | 232 | -586558.21 | 13600.44 | -600158.65 | 4576.04 | 9395265.30 | 1135500.00 |

Service clearing cash: 777600.00. Unpaid invoices: 0.00. Held purchase escrow: 1931.00. Municipal debt: 0.00.

### Capacity constraints and operating decisions

Counts can overlap. Material, power and cash constraints compare pre-hiring availability with allocated capacity; they are not exclusive causes of a stopped day. Workforce compares the plan with actual paid workers. Generator fuel is a rated-capacity check and may be sufficient for curtailed dispatch.

| Facility / observation | Days |
|---|---:|
| nitrates / Funded demand / credits/day | 232 |
| nitrates / decision: Expected receipts do not cover costs, tax and replacement | 232 |
| phosphates / Funded demand / credits/day | 555 |
| phosphates / decision: Expected receipts do not cover costs, tax and replacement | 555 |
| potash / Funded demand / credits/day | 1076 |
| potash / decision: Expected receipts do not cover costs, tax and replacement | 1076 |
| processor / Funded demand / credits/day | 901 |
| processor / Material / harvested_crops | 120 |
| processor / decision: Expected receipts do not cover costs, tax and replacement | 901 |
| processor / decision: No funded profitable demand or available inputs | 120 |
| reactor-fuel / Funded demand / credits/day | 931 |
| reactor-fuel / Material / uranium_ore | 159 |
| reactor-fuel / decision: Expected receipts do not cover costs, tax and replacement | 931 |
| reactor-ore / Funded demand / credits/day | 594 |
| reactor-ore / decision: Expected receipts do not cover costs, tax and replacement | 594 |

### Farm cash accounting between harvests

Every day is included, including pauses. Zero harvest denotes an unfinished closing window. Sales can include older inventory, so these are calendar-window cash receipts rather than product-lot costs. After-tax result excludes capital allowances and reserve transfers; reserves remain assets. The allowance shows the policy's full daily replacement requirement before available-profit limits. No coefficient is adjusted to force a profit.

| Days | Harvest kg | Sales | Inputs | Wages | Electricity | Maintenance | Tax paid | Capital allowance | Reserve transfer | After-tax result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1–120 | 43200.00 | 29777.37 | 1920.00 | 1200.00 | 8603.21 | 9600.00 | 2113.54 | 571.41 | 571.41 | 6340.62 |
| 121–240 | 43200.00 | 185352.74 | 440172.77 | 1200.00 | 16817.76 | 9600.00 | 260.74 | 571.41 | 571.41 | -282698.53 |
| 241–360 | 43200.00 | 540975.42 | 584608.88 | 1200.00 | 19406.55 | 9600.00 | 0.00 | 571.41 | 571.41 | -73840.01 |
| 361–480 | 43200.00 | 686085.40 | 584608.88 | 1200.00 | 18568.54 | 9600.00 | 22128.17 | 571.41 | 571.41 | 49979.81 |
| 481–600 | 43200.00 | 686085.40 | 579568.02 | 1200.00 | 54011.22 | 9600.00 | 18027.00 | 571.41 | 571.41 | 23679.16 |
| 601–720 | 43200.00 | 499928.49 | 579205.65 | 1200.00 | 61729.90 | 9600.00 | 0.00 | 571.41 | 571.41 | -151807.05 |
| 721–840 | 43200.00 | 686945.41 | 579205.64 | 1200.00 | 58346.08 | 9600.00 | 0.00 | 571.41 | 571.41 | 38593.69 |
| 841–960 | 43200.00 | 686945.41 | 579205.64 | 1200.00 | 58484.17 | 9600.00 | 0.00 | 571.41 | 571.41 | 38455.59 |
| 961–1080 | 43200.00 | 485745.98 | 579205.64 | 1200.00 | 56742.03 | 9600.00 | 0.00 | 571.41 | 571.41 | -161001.69 |

## Hydroponic growth arrays

Harvests: 34. Household food bought: 39,987.49 kg. Unmet food: 67,960.11 kg. Closing food stock: 443.76 kg.

| Business | Produced kg | Marketed kg | Paused days | Operating result | Tax paid | After-tax result | Held replacement cash | Spendable cash | Unsold kg |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| producer | 89565.89 | 28319.20 | 450 | -2301459.94 | 0.00 | -2301459.94 | 26656.90 | 7671883.16 | 61246.70 |
| processor | 9439.73 | 7551.79 | 1044 | 839656.59 | 209954.15 | 629702.45 | 25056.94 | 10604645.50 | 1887.95 |
| generator | 0.00 | 0.00 | 0 | 661951.79 | 165487.95 | 496463.85 | 18980.63 | 10477483.22 | 0.00 |
| potash | 675.00 | 370.93 | 1079 | 621069.57 | 155267.39 | 465802.17 | 25707.54 | 10440094.64 | 304.07 |
| reactor-fuel | 814.00 | 714.09 | 1051 | 768842.70 | 192210.68 | 576632.03 | 25689.76 | 10550942.26 | 99.91 |
| phosphates | 675.00 | 370.93 | 1079 | 621069.57 | 155267.39 | 465802.17 | 25707.54 | 10440094.64 | 304.07 |
| reactor-ore | 41613.75 | 40240.00 | 669 | -4819.74 | 32688.20 | -37507.94 | 43819.52 | 9918672.54 | 1373.75 |
| nitrates | 120825.00 | 11591.52 | 986 | 530495.15 | 132623.79 | 397871.36 | 25707.54 | 10372163.83 | 109233.48 |
| water | 407440.00 | 41112.77 | 591 | -84496.25 | 1422.80 | -85919.05 | 20110.25 | 9893970.70 | 366327.23 |
| iron-ore | 126360.00 | 126360.00 | 144 | -158487.94 | 11736.71 | -170224.65 | 21909.76 | 9807865.59 | 0.00 |
| carbon | 755325.00 | 17991.00 | 501 | -470788.31 | 8631.71 | -479420.02 | 9085.41 | 9511494.57 | 737334.00 |
| carbon-dioxide | 140425.31 | 54610.81 | 504 | 2258343.12 | 567953.85 | 1690389.27 | 25707.54 | 11664681.74 | 85814.50 |

Service clearing cash: 1166400.00. Unpaid invoices: 0.00. Held purchase escrow: 231383.49. Municipal debt: 1944949.92.

### Capacity constraints and operating decisions

Counts can overlap. Material, power and cash constraints compare pre-hiring availability with allocated capacity; they are not exclusive causes of a stopped day. Workforce compares the plan with actual paid workers. Generator fuel is a rated-capacity check and may be sufficient for curtailed dispatch.

| Facility / observation | Days |
|---|---:|
| carbon / Funded demand / credits/day | 501 |
| carbon / decision: Expected receipts do not cover costs, tax and replacement | 501 |
| carbon-dioxide / Electricity / kWh | 34 |
| carbon-dioxide / Funded demand / credits/day | 408 |
| carbon-dioxide / Material / carbon | 1 |
| carbon-dioxide / Material / iron_ore | 727 |
| carbon-dioxide / decision: Expected receipts do not cover costs, tax and replacement | 408 |
| carbon-dioxide / decision: No funded profitable demand or available inputs | 96 |
| iron-ore / Funded demand / credits/day | 144 |
| iron-ore / decision: Expected receipts do not cover costs, tax and replacement | 144 |
| nitrates / Funded demand / credits/day | 986 |
| nitrates / decision: Expected receipts do not cover costs, tax and replacement | 986 |
| phosphates / Funded demand / credits/day | 1079 |
| phosphates / decision: Expected receipts do not cover costs, tax and replacement | 1079 |
| potash / Funded demand / credits/day | 1079 |
| potash / decision: Expected receipts do not cover costs, tax and replacement | 1079 |
| processor / Funded demand / credits/day | 59 |
| processor / Material / harvested_crops | 1080 |
| processor / decision: Expected receipts do not cover costs, tax and replacement | 59 |
| processor / decision: No funded profitable demand or available inputs | 985 |
| producer / Funded demand / credits/day | 189 |
| producer / Material / carbon_dioxide_gas | 489 |
| producer / decision: Expected receipts do not cover costs, tax and replacement | 189 |
| producer / decision: No funded profitable demand or available inputs | 261 |
| reactor-fuel / Funded demand / credits/day | 1051 |
| reactor-fuel / decision: Expected receipts do not cover costs, tax and replacement | 1051 |
| reactor-ore / Funded demand / credits/day | 669 |
| reactor-ore / decision: Expected receipts do not cover costs, tax and replacement | 669 |
| water / Funded demand / credits/day | 591 |
| water / decision: Expected receipts do not cover costs, tax and replacement | 591 |

### Farm cash accounting between harvests

Every day is included, including pauses. Zero harvest denotes an unfinished closing window. Sales can include older inventory, so these are calendar-window cash receipts rather than product-lot costs. After-tax result excludes capital allowances and reserve transfers; reserves remain assets. The allowance shows the policy's full daily replacement requirement before available-profit limits. No coefficient is adjusted to force a profit.

| Days | Harvest kg | Sales | Inputs | Wages | Electricity | Maintenance | Tax paid | Capital allowance | Reserve transfer | After-tax result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1–28 | 4744.09 | 28513.58 | 84506.57 | 1120.00 | 48696.65 | 3360.00 | 0.00 | 702.25 | 702.25 | -109169.63 |
| 29–114 | 4574.66 | 237911.34 | 248038.67 | 1080.00 | 46082.92 | 10320.00 | 0.00 | 2105.84 | 2105.84 | -67610.25 |
| 115–142 | 4405.23 | 359228.18 | 1040068.56 | 1040.00 | 19679.19 | 3360.00 | 0.00 | 699.52 | 699.52 | -704919.56 |
| 143–170 | 4066.36 | 243894.40 | 901655.07 | 960.00 | 20055.41 | 3360.00 | 0.00 | 696.35 | 696.35 | -682136.07 |
| 171–198 | 2668.55 | 119685.63 | 148877.62 | 840.00 | 19061.73 | 3360.00 | 0.00 | 691.20 | 691.20 | -52453.72 |
| 199–241 | 881.05 | 293566.23 | 79400.43 | 360.00 | 7136.49 | 5160.00 | 0.00 | 1045.04 | 1045.04 | 201509.31 |
| 242–269 | 4744.09 | 409967.77 | 1067057.14 | 1120.00 | 20536.18 | 3360.00 | 0.00 | 721.74 | 721.74 | -682105.55 |
| 270–297 | 1935.76 | 86800.51 | 105066.07 | 760.00 | 15011.84 | 3360.00 | 0.00 | 691.07 | 691.07 | -37397.40 |
| 298–331 | 347.34 | 115732.83 | 56396.42 | 120.00 | 3247.53 | 4080.00 | 0.00 | 824.17 | 824.17 | 51888.88 |
| 332–359 | 4574.66 | 395316.47 | 1022860.60 | 1080.00 | 19876.52 | 3360.00 | 0.00 | 720.07 | 720.07 | -651860.65 |
| 360–388 | 2283.09 | 101909.40 | 123567.09 | 880.00 | 15169.71 | 3480.00 | 0.00 | 718.36 | 718.36 | -41187.41 |
| 389–420 | 1427.93 | 48023.27 | 78103.79 | 480.00 | 3269.80 | 3840.00 | 0.00 | 784.18 | 784.18 | -37670.32 |
| 421–449 | 1280.15 | 66044.49 | 36525.29 | 390.00 | 5758.05 | 3480.00 | 0.00 | 717.22 | 717.22 | 19891.15 |
| 450–480 | 3219.20 | 220319.55 | 204451.35 | 760.00 | 9275.52 | 3720.00 | 0.00 | 765.64 | 765.64 | 2112.67 |
| 481–508 | 3135.43 | 441529.64 | 138006.92 | 800.00 | 12691.99 | 3360.00 | 0.00 | 691.48 | 691.48 | 286670.74 |
| 509–537 | 2734.44 | 183562.78 | 165118.46 | 680.00 | 8405.93 | 3480.00 | 0.00 | 719.23 | 719.23 | 5878.39 |
| 538–567 | 2604.54 | 189542.46 | 104125.42 | 680.00 | 10183.40 | 3600.00 | 0.00 | 743.90 | 743.90 | 70953.65 |
| 568–597 | 2401.70 | 160028.13 | 118487.89 | 720.00 | 9909.30 | 3600.00 | 0.00 | 742.77 | 742.77 | 27310.95 |
| 598–627 | 1956.94 | 142419.58 | 78863.62 | 680.00 | 14262.10 | 3600.00 | 0.00 | 739.65 | 739.65 | 45013.85 |
| 628–655 | 2321.22 | 160217.31 | 108054.04 | 720.00 | 9566.65 | 3360.00 | 0.00 | 693.29 | 693.29 | 38516.62 |
| 656–684 | 1948.47 | 146677.13 | 97892.12 | 600.00 | 6067.03 | 3480.00 | 0.00 | 715.87 | 715.87 | 38637.99 |
| 685–713 | 2643.14 | 179946.85 | 173140.33 | 720.00 | 10029.68 | 3480.00 | 0.00 | 718.67 | 718.67 | -7423.15 |
| 714–742 | 1982.35 | 149279.18 | 80991.69 | 660.00 | 14772.96 | 3480.00 | 0.00 | 716.40 | 716.40 | 49374.54 |
| 743–772 | 2562.66 | 172058.40 | 178423.96 | 640.00 | 5545.38 | 3600.00 | 0.00 | 742.77 | 742.77 | -16150.93 |
| 773–801 | 2143.31 | 91464.36 | 79481.53 | 640.00 | 12496.72 | 3480.00 | 0.00 | 713.08 | 713.08 | -4633.89 |
| 802–829 | 2329.69 | 104150.11 | 125389.39 | 800.00 | 14500.59 | 3360.00 | 0.00 | 698.16 | 698.16 | -39899.87 |
| 830–858 | 1869.40 | 136962.54 | 111017.55 | 620.00 | 5907.90 | 3480.00 | 0.00 | 715.12 | 715.12 | 15937.09 |
| 859–900 | 1811.98 | 66732.85 | 171188.05 | 600.00 | 17730.97 | 5040.00 | 0.00 | 1035.76 | 1035.76 | -127826.16 |
| 901–928 | 1256.62 | 53117.98 | 35847.34 | 400.00 | 2922.61 | 3360.00 | 0.00 | 693.26 | 693.26 | 10588.03 |
| 929–957 | 4598.19 | 733056.86 | 690404.46 | 1120.00 | 9843.15 | 3480.00 | 0.00 | 724.34 | 724.34 | 28209.25 |
| 958–985 | 3272.86 | 150459.32 | 263563.16 | 960.00 | 16764.73 | 3360.00 | 0.00 | 699.21 | 699.21 | -134188.57 |
| 986–1016 | 1599.25 | 269180.21 | 50965.18 | 580.00 | 8021.12 | 3720.00 | 0.00 | 761.10 | 761.10 | 205893.90 |
| 1017–1045 | 2734.44 | 183562.21 | 146623.22 | 650.00 | 9915.61 | 3480.00 | 0.00 | 719.43 | 719.43 | 22893.39 |
| 1046–1077 | 2507.12 | 103921.34 | 99076.15 | 680.00 | 5517.97 | 3840.00 | 0.00 | 790.75 | 790.75 | -5192.78 |
| 1078–1080 | 0.00 | 0.00 | 19438.85 | 120.00 | 995.57 | 360.00 | 0.00 | 76.04 | 0.00 | -20914.41 |

## Automated biosphere macro farms

Harvests: 23. Household food bought: 42,323.00 kg. Unmet food: 66,488.60 kg. Closing food stock: 289.50 kg.

| Business | Produced kg | Marketed kg | Paused days | Operating result | Tax paid | After-tax result | Held replacement cash | Spendable cash | Unsold kg |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| producer | 96957.36 | 30656.25 | 594 | 1046440.19 | 265057.70 | 781382.49 | 105165.18 | 10676217.31 | 66301.11 |
| processor | 10218.75 | 8175.00 | 1055 | 453856.00 | 113544.00 | 340312.00 | 18691.41 | 10321620.59 | 2043.75 |
| generator | 0.00 | 0.00 | 0 | 561635.97 | 140408.99 | 421226.98 | 11248.42 | 10409978.56 | 0.00 |
| potash | 1350.00 | 347.02 | 1079 | 148225.30 | 37056.33 | 111168.98 | 19355.09 | 10091813.89 | 1002.98 |
| reactor-fuel | 2343.00 | 631.92 | 1042 | 555429.19 | 140153.48 | 415275.71 | 19355.09 | 10395920.63 | 1711.08 |
| phosphates | 1350.00 | 347.02 | 1079 | 148225.30 | 37056.33 | 111168.98 | 19355.09 | 10091813.89 | 1002.98 |
| reactor-ore | 56193.75 | 55081.25 | 525 | -84964.28 | 19787.94 | -104752.22 | 38660.58 | 9856587.19 | 1112.50 |
| nitrates | 174487.50 | 10880.39 | 884 | -130897.41 | 17344.72 | -148242.12 | 19355.09 | 9832402.79 | 163607.11 |
| water | 343200.00 | 28973.35 | 726 | -116290.61 | 0.00 | -116290.61 | 7902.32 | 9875807.07 | 314226.65 |
| iron-ore | 104490.00 | 104490.00 | 306 | -47940.68 | 11520.60 | -59461.28 | 19330.29 | 9921208.43 | 0.00 |
| carbon | 666265.50 | 15576.19 | 550 | -442342.34 | 8681.16 | -451023.50 | 13157.30 | 9535819.20 | 650689.31 |
| carbon-dioxide | 116094.94 | 45667.24 | 554 | -1547087.58 | 112769.09 | -1659856.67 | 19355.09 | 8320788.24 | 70427.70 |

Service clearing cash: 1555200.00. Unpaid invoices: 0.00. Held purchase escrow: 185508.16. Municipal debt: 0.00.

### Capacity constraints and operating decisions

Counts can overlap. Material, power and cash constraints compare pre-hiring availability with allocated capacity; they are not exclusive causes of a stopped day. Workforce compares the plan with actual paid workers. Generator fuel is a rated-capacity check and may be sufficient for curtailed dispatch.

| Facility / observation | Days |
|---|---:|
| carbon / Funded demand / credits/day | 550 |
| carbon / Settled workforce / workers | 1 |
| carbon / decision: Expected receipts do not cover costs, tax and replacement | 550 |
| carbon-dioxide / Electricity / kWh | 39 |
| carbon-dioxide / Funded demand / credits/day | 342 |
| carbon-dioxide / Material / carbon | 1 |
| carbon-dioxide / Material / iron_ore | 961 |
| carbon-dioxide / decision: Expected receipts do not cover costs, tax and replacement | 342 |
| carbon-dioxide / decision: No funded profitable demand or available inputs | 212 |
| iron-ore / Funded demand / credits/day | 306 |
| iron-ore / decision: Expected receipts do not cover costs, tax and replacement | 306 |
| nitrates / Funded demand / credits/day | 884 |
| nitrates / decision: Expected receipts do not cover costs, tax and replacement | 884 |
| phosphates / Funded demand / credits/day | 1079 |
| phosphates / decision: Expected receipts do not cover costs, tax and replacement | 1079 |
| potash / Funded demand / credits/day | 1079 |
| potash / decision: Expected receipts do not cover costs, tax and replacement | 1079 |
| processor / Funded demand / credits/day | 65 |
| processor / Material / harvested_crops | 1028 |
| processor / decision: Expected receipts do not cover costs, tax and replacement | 65 |
| processor / decision: No funded profitable demand or available inputs | 990 |
| producer / Funded demand / credits/day | 103 |
| producer / Material / carbon_dioxide_gas | 947 |
| producer / decision: Expected receipts do not cover costs, tax and replacement | 103 |
| producer / decision: No funded profitable demand or available inputs | 491 |
| reactor-fuel / Funded demand / credits/day | 1042 |
| reactor-fuel / decision: Expected receipts do not cover costs, tax and replacement | 1042 |
| reactor-ore / Funded demand / credits/day | 525 |
| reactor-ore / decision: Expected receipts do not cover costs, tax and replacement | 525 |
| water / Funded demand / credits/day | 726 |
| water / decision: Expected receipts do not cover costs, tax and replacement | 726 |

### Farm cash accounting between harvests

Every day is included, including pauses. Zero harvest denotes an unfinished closing window. Sales can include older inventory, so these are calendar-window cash receipts rather than product-lot costs. After-tax result excludes capital allowances and reserve transfers; reserves remain assets. The allowance shows the policy's full daily replacement requirement before available-profit limits. No coefficient is adjusted to force a profit.

| Days | Harvest kg | Sales | Inputs | Wages | Electricity | Maintenance | Tax paid | Capital allowance | Reserve transfer | After-tax result |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1–42 | 18976.36 | 110627.59 | 64563.09 | 1680.00 | 103885.57 | 20160.00 | 0.00 | 4160.53 | 4160.53 | -79661.07 |
| 43–87 | 13224.15 | 37478.19 | 279371.88 | 1400.00 | 70603.88 | 21600.00 | 0.00 | 4471.02 | 4471.02 | -335497.56 |
| 88–150 | 540.77 | 936300.68 | 4785.34 | 80.00 | 3925.05 | 30240.00 | 120527.91 | 6099.32 | 6099.32 | 776742.37 |
| 151–194 | 7109.08 | 419333.96 | 310818.70 | 1000.00 | 45111.48 | 21120.00 | 10320.94 | 4345.43 | 4345.43 | 30962.83 |
| 195–241 | 1251.91 | 96232.21 | 81416.33 | 440.00 | 6527.26 | 22560.00 | 0.00 | 4582.65 | 4582.65 | -14711.38 |
| 242–301 | 4625.49 | 921432.30 | 480062.15 | 880.00 | 18350.39 | 28800.00 | 94657.09 | 5872.75 | 5872.75 | 298682.67 |
| 302–343 | 4803.86 | 164141.30 | 471080.32 | 870.00 | 17381.72 | 20160.00 | 0.00 | 4135.34 | 4135.34 | -345350.74 |
| 344–386 | 3017.30 | 130501.83 | 75619.63 | 640.00 | 16467.02 | 20640.00 | 0.00 | 4194.24 | 4194.24 | 17135.18 |
| 387–428 | 2401.70 | 45998.11 | 69624.88 | 1040.00 | 24355.47 | 20160.00 | 0.00 | 4114.48 | 4114.48 | -69182.24 |
| 429–472 | 2846.45 | 123112.64 | 69395.27 | 840.00 | 17107.68 | 21120.00 | 0.00 | 4300.03 | 4300.03 | 14649.69 |
| 473–515 | 2312.74 | 83120.12 | 67607.03 | 680.00 | 23555.19 | 20640.00 | 0.00 | 4194.93 | 4194.93 | -29362.10 |
| 516–561 | 2675.61 | 127315.33 | 96168.39 | 520.00 | 17265.41 | 22080.00 | 0.00 | 4474.53 | 4474.53 | -8718.47 |
| 562–603 | 2750.44 | 67431.59 | 61936.39 | 640.00 | 20213.00 | 20160.00 | 0.00 | 4103.83 | 4103.83 | -35517.81 |
| 604–647 | 2230.85 | 42216.59 | 76254.85 | 440.00 | 13846.58 | 21120.00 | 0.00 | 4290.87 | 2530.25 | -69444.84 |
| 648–689 | 3639.96 | 194348.06 | 119472.19 | 1000.00 | 19091.34 | 20160.00 | 0.00 | 4139.48 | 5900.10 | 34624.52 |
| 690–731 | 2846.45 | 167759.01 | 94243.17 | 720.00 | 23150.93 | 20160.00 | 0.00 | 4097.36 | 4097.36 | 29484.91 |
| 732–801 | 4632.55 | 445575.48 | 114335.14 | 1400.00 | 15844.96 | 33600.00 | 0.00 | 6847.38 | 6847.38 | 280395.38 |
| 802–843 | 3284.15 | 156169.25 | 82155.82 | 1000.00 | 28070.24 | 20160.00 | 0.00 | 4116.79 | 4116.79 | 24783.18 |
| 844–901 | 2853.04 | 447109.98 | 134719.17 | 940.00 | 22267.18 | 27840.00 | 26210.08 | 5654.52 | 5654.52 | 235133.56 |
| 902–945 | 2935.88 | 122028.99 | 108085.22 | 660.00 | 23331.62 | 21120.00 | 0.00 | 4287.54 | 4287.54 | -31167.85 |
| 946–988 | 2928.35 | 175704.60 | 112353.56 | 600.00 | 20421.73 | 20640.00 | 0.00 | 4200.48 | 4200.48 | 21689.31 |
| 989–1033 | 2401.70 | 103892.24 | 56934.74 | 620.00 | 20938.98 | 21600.00 | 0.00 | 4387.57 | 4387.57 | 3798.52 |
| 1034–1075 | 2668.55 | 184602.21 | 80370.06 | 660.00 | 24365.43 | 20160.00 | 13341.67 | 4094.11 | 4094.11 | 45705.05 |
| 1076–1080 | 0.00 | 0.00 | 5379.31 | 120.00 | 5891.30 | 2400.00 | 0.00 | 490.04 | 0.00 | -13790.61 |


## Interpretation

Positive asks are not guaranteed sales. Idle days, shortages, unsold coproducts and unfunded invoices remain visible. Reserve transfers remain assets and are shown separately from after-tax operating profit. Maintenance and transport services are paid into a service clearing account; a detailed maintenance-parts or service-worker sector is not simulated. The enclosed chains obtain CO₂ as a paid iron-smelting coproduct; retained iron and other residue are not assumed sold. These small, initially wealthy economies are operating probes, not balanced campaign presets. Capital construction was seeded, not performed in this audit. All deliveries below are local; offworld transport is covered by the separate funded logistics and import tests.
