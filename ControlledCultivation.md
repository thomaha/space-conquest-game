# Controlled cultivation

Implemented on 16 October 2026. Hydroponic growth arrays and automated biosphere macro farms now have researchable, application-owned farming definitions. They share cultivation, construction, work, power and marketplace settlement with outdoor farming. Food processing remains a separate factory operation. No named farm behavior is selected in the production executor.

## Applications and space

| Requirement per installed line at tier 1 | Hydroponic growth arrays | Automated biosphere macro farms |
| --- | ---: | ---: |
| Growing cycle | 28 days | 42 days |
| Growing area | 1,000 m² | 4,000 m² |
| Ground footprint | 250 m² | 2,000 m² |
| Enclosed volume | 2,000 m³ | 12,000 m³ |
| Orbital station slots | 4 | Ground only |
| Reference operators | 4 farmers | 4 farmers |
| Autonomous share of rated work | 25% | 75% |
| Work units per full batch | 1,000 | 1,000 |
| Working water charged during construction | 2,000 kg | 8,000 kg |

Hydroponics requires electricity research. Biosphere farming also requires robotics and hydroponics. Both accept application-declared solid surface types without an outdoor atmosphere or local liquid water. Unknown bodies and gas giants are rejected. Construction consumes the declared enclosure materials and initial water and oxygen charges. Dry sites therefore need deliveries of real goods before commissioning.

Parallel lines and tier capacity scale the reserved footprint and station slots. Pending farms and expansions reserve space immediately. Ordinary pending station modules share the same slot budget, so command order cannot double-book the station. Station access follows ownership or corporate affiliation. Slots are the existing coarse station-capacity measure; the enclosure volume is application data rather than a universal volume assigned to every station slot. These reservations do not yet provide a general volume model for every kind of station equipment.

Equipment and operator skill determine the cultivated fraction, with the declared growing interval as an independent rate limit. Autonomous work still needs the same proportional utilities and growing time. No production requirement is measured in person hours.

## Inputs, harvests and recovery

| Commodity per complete growing cycle | Hydroponic growth arrays | Automated biosphere macro farms |
| --- | ---: | ---: |
| Purchased purified water | 2,000 kg | 6,000 kg |
| Purchased carbon dioxide | 2,970 kg | 11,880 kg |
| Purchased nitrates | 625 kg | 2,500 kg |
| Purchased phosphates | 20 kg | 80 kg |
| Purchased potash | 20 kg | 80 kg |
| Food matrix output | 1,000 kg | 4,000 kg |
| Harvested crops output | 500 kg | 2,000 kg |
| Agricultural biomass output | 300 kg | 1,200 kg |
| Oxygen output | 2,737.491 kg | 10,949.964 kg |
| Cultivation residue output | 206.6 kg | 826.4 kg |
| Unrecovered environmental water loss | 890.909 kg | 1,563.636 kg |

These recipes balance every element against the catalog material compositions, including the declared environmental loss. The residue retains salts and nutrient constituents that do not enter the harvested products. It is a low-value material rather than an assumed valuable chemical separation. Environmental losses never enter producer or market stock.

Hydroponics internally circulates 100,000 kg of water per cycle with 98% recovery. Biosphere farming circulates 400,000 kg with 98.5% recovery. Only unrecovered makeup water is purchased each cycle. Recovered circulation is internal reuse and creates neither market water nor sales revenue. Initial working inventory is purchased once through construction and scales with installed capacity. Makeup water also supplies hydrogen and oxygen incorporated into the harvest; it is not all discarded water. The remaining loss closes the elemental balance.

Advanced recovery reduces makeup demand from 2,000 to 1,500 kg per hydroponic-equivalent output basket. The biosphere application defines that improvement explicitly. Water loss and energy requirements are fixed application values; variable contamination, equipment degradation and recovery research on existing hardware remain future work.

## Energy and marketplace delivery

| Electricity per complete growing cycle | Hydroponic growth arrays | Automated biosphere macro farms |
| --- | ---: | ---: |
| Lighting | 44,800 kWh | 134,400 kWh |
| Climate control | 8,400 kWh | 50,400 kWh |
| Pumping | 2,800 kWh | 16,800 kWh |
| Water recovery | 2,800 kWh | 16,800 kWh |
| Total | 58,800 kWh | 218,400 kWh |

The utility breakdown must sum to the recipe's operating energy. Finite generation and the normal billing path constrain actual work. Missing water, carbon dioxide, nutrients or energy prevent further paid cultivation. Saved partial cultivation cannot produce more than its funded fraction. Crop survival during prolonged outages is not yet simulated.

Harvests are released after the growing interval and offered through ordinary funded wholesale settlement. Cash, demand and storage constrain sales. A food processor purchases actual delivered crops. When a market opens with no crops, the processor receives power against the newly available stock on the following tick; no same-tick forecast stock is fabricated. Legacy station hydroponic modules no longer advertise a free fixed food and oxygen yield in the macrostructure result. Operational orbital food production uses these paid cultivation facilities.

## Cost scale and real-world comparisons

At baseline game prices, hydroponics purchases 10,270 credits of materials per cycle and 1,176 credits of electricity. Selling every output at the standard 80% wholesale share returns about 8,636.51 credits: a loss of about 2,809.49 credits before wages, maintenance, construction, transport and tax. Biosphere farming purchases 38,080 credits of materials and 4,368 credits of electricity against about 34,546.05 credits of wholesale revenue: a loss of about 7,901.95 credits before those additional costs. These optimistic sales totals include all oxygen and residue. A local market may buy much less.

The recipes establish finite physical supply, not baseline profitability. The subsequent [agricultural economics review](AgricultureEconomics.md) compares finite household demand, local food costs and actual offworld freight. It corrects self-generated output demand and low-gravity launch units and identifies destination pricing and energy-provider funding gaps. Global food and gas prices were not increased to force a profit. The repeatable [production catalog audit](ProductionCatalogAudit.md) reports the underlying material and revenue assumptions. Credits have no defined conversion to real currency.

[NASA's controlled-environment agriculture overview](https://ntrs.nasa.gov/citations/20205008832) supports treating electrical power, growing volume and resource recycling as explicit constraints. [NASA's indoor-farming account](https://www.nasa.gov/technology/tech-transfer-spinoffs/nasa-research-launches-a-new-generation-of-indoor-farming/) describes lighting and water/nutrient delivery as core engineered systems. A [NASA hydroponic lettuce study](https://osdr.nasa.gov/bio/repo/data/studies/OSD-723) uses a 28-day growth period, providing a reference for the shorter cycle. These sources inform the shape of the model; they do not validate the game's yields, nutrient composition, labor rates or capital costs.

The game harvest is a coarse dry-food basket under existing material assays, not wet lettuce. The recipes do not claim to model a balanced diet, complete habitat atmosphere, life-support pressure, heat rejection, radiation protection, disease, seed propagation or alien biochemistry. Biosphere farming is ground-only until orbital structure and capacity requirements are defined. Existing starting-world and AI behavior is not proof of a self-sustaining orbital agricultural economy.

## Verification

Tests cover all-element accounting, dry-site and orbital placement, automation with finite energy, shared station reservations and queued commands. Funded research-to-harvest scenarios purchase construction goods, generator fuel and daily farm inputs, pay actual generators, preserve total cash and save/load both growing and harvested current-format state. Missing-input and energy checks preserve paid growth. Delivered crops feed the real food-processing factory on the following tick. Frontend checks render enclosure, recovery, utilities and station reservations from an immutable snapshot.

Scoped verification passed 260 tests across 49 engine, control and frontend suites with no failures, errors or skipped tests. This includes generated-economy first-month checks, research, manufacturing, market and power settlement, stations and current-format saves. The full Maven suite was not run.
