# Farming and food processing

Farming is biological production on renewable land. It can supply edible food and industrial raw materials directly to the marketplace. Food processing is a factory activity that purchases harvested crops and converts them into food and useful byproducts. The engine distinguishes these activities through application data rather than named farm branches.

## Application definitions

`industrial_soil_cultivation` and `industrial_biomass_cultivation` now own `FarmingDefinition` and production work data. They have no `factory` definition and are excluded from the standalone factory-refinement catalog. `food_processing` is a new researchable factory application. `biomass_processing` also now owns its factory definition and mixed material recipe.

The existing `IndustrialFacility` state container, construction queue, operating account, staffing and market settlement are shared. A farm's `infrastructure` uses the existing `FactoryDefinition` schema for common construction and equipment constraints; this shared schema does not make cultivation a factory process. No class hierarchy or save migration was introduced.

| Application | Growing area per installed line | Cycle | Products per complete baseline cycle or factory batch |
| --- | ---: | ---: | --- |
| Soil cultivation | 6 ha | 120 days | 14,400 kg food, 14,400 kg harvested crops and 14,400 kg plant residues |
| Fiber cultivation | 20 ha | 120 days | 120,000 kg agricultural biomass |
| Food processing | Factory | Immediate work settlement | 1,000 kg harvested crops becomes 800 kg food and 200 kg agricultural biomass |
| Biomass processing | Factory | Immediate work settlement | 1,000 kg biomass and 100 kg water become 300 kg biopolymers and 800 kg retained processing residue |

Food stocks use a coarse nutritional commodity rather than a wet-produce assay. Harvested crops are a separate commodity for factory intake. Food and biomass factory recipes conserve every catalog element; the former biomass recipe silently lost its process water and its polymer composition could not be supplied by its inputs. The corrected polymer fraction leaves the remaining elements in an explicit mixed residue commodity. Further residue recovery and disposal are still future work. The subsequent [agricultural economics review](AgricultureEconomics.md) checks these yields against dry-biomass examples and measured delivered-food alternatives.

## Land, work and harvests

Applications specify area per line, growing days, usable land fraction, allowed atmospheres and whether liquid water is required. The initial outdoor applications require a nitrogen-oxygen atmosphere and accessible liquid water. Available area is the body's spherical surface area multiplied by dry-land fraction and the application-defined usable fraction. This is a coarse suitability estimate rather than surveyed soil quality.

Operating farms, pending construction and pending expansions reserve land. Construction, tile-placement commands, expansion, recipe changes, corporate planning and production quotes check those requirements. Area scales with captured parallel equipment and tier capacity; an expansion's production penalty does not release its land. Unknown bodies and orbital stations have no outdoor growing land.

Work units retain their existing meaning: equipment, operator skill and automation determine the rate. The initial cultivation equipment supplies 1,000 task units across a fully staffed 120-day cycle; lower skill reduces the cultivated fraction. Growing days independently cap the cultivated fraction per line, so more work cannot manufacture an instantaneous harvest. Each daily installment consumes actual nutrients and electrical energy. Irrigation comes from the declared local surface-water source rather than purchased drinking-water stock.

`FarmingState` saves the application, cycle start, latest worked turn and paid cultivated quantity in `IndustryAccount`. Products appear only after the growing interval, on a funded operating day. Missing nutrients, operators, money or energy prevent further cultivation. Partial cultivation yields only its paid fraction; an interruption does not create the missing output. Repeating settlement in the same turn cannot add cultivation twice. Active crops prevent recipe changes and expansion; new cultivation waits for construction or expansion to finish so newly added land cannot inherit an almost-complete growing cycle.

Harvests enter the ordinary facility stock and use the existing funded wholesale settlement. Marketplace demand, buyer cash and storage still limit sales; unsold food and crops remain with the producer. Factory inputs are purchased from actual hub stock, so food processing cannot run on a forecast harvest.

## Resource and price scale

The outdoor recipes declare 500 mm of local irrigation per cycle: 30 million kg over 6 ha and 100 million kg over 20 ha. FAO gives 450–650 mm as a representative total growing-period water range for barley, oats and wheat in its [crop water needs guidance](https://www.fao.org/4/s2022e/s2022e02.htm). This is a scale comparison, not a claim that all crop water must be pumped: [FAO's irrigation definition](https://www.fao.org/aquastat/en/data-analysis/irrig-water-use/irrig-water-requirement/) separates effective rainfall and soil-moisture contributions from irrigation demand. The current game source does not model those contributions separately.

The 120-day cycle is within the examples in [FAO's crop water requirements tables](https://www.fao.org/4/s8376e/s8376e.pdf). The 80% food-processing fraction is a coarse milling-style example; an [FAO supply-account methodology example](https://www.fao.org/fileadmin/templates/ess/documents/methodology/prepsua3.pdf) reports an 80% wheat-flour extraction rate. Neither example validates this mixed crop basket, nutrient bill, worker count or construction bill as a real farm estimate.

Electrical allowances are 3,000 kWh per soil cycle, 10,000 kWh per fiber cycle, 150 kWh per food-processing batch and 2,000 kWh per biomass-processing batch. These remain provisional. Solar energy, atmospheric carbon uptake, transpiration, drainage and soil nutrient exchanges are environmental contributions and losses outside the commodity ledger. Cultivation is not presented as an element-balanced closed factory reaction.

Baseline game prices are 2 credits/kg harvested crops, 1 credit/kg agricultural biomass, 4 credits/kg food, 6 credits/kg biopolymers and 0.1 credits/kg mixed processing residue. Raw biomass was reduced from 3 and polymers increased from 5 to account for the corrected conversion yield. The processing routes cover baseline purchased materials and electricity from primary-product wholesale revenue without requiring residue sales. Staffing, maintenance, capital, transport, storage and seasonal financing can still make a particular facility unprofitable. There is no real-currency conversion.

## Starting economies and scope

Opening market reserves for farm products now cover the application's growing period plus seven days. Existing farms start new paid cultivation rather than receiving a free mature harvest. Opening biomass equipment is sized against its actual declared polymer yield, rated work capacity and process-water use, and captured application equipment supplies its operator profession. The provisional biomass line handles about 3.33 input batches per day with ten reference operators, yielding 1,000 kg polymers per day before tier scaling. Opening assets remain scenario-selected and broader workforce planning is still provisional.

The polymer intermediate now receives the same seven-day opening reserve as other manufactured chain inputs, replacing its previous one-day allowance. These physical startup stocks buffer seasonal and electrical supply constraints; passing a first-month needs check is not proof of a replenishable or profitable campaign.

Opening generation is sized against application-defined throughput improvements already known by the empire before adding the existing reserve margin. The previous flat allowance missed the larger combined extraction improvement in the warp scenario, leaving its industrial grid undersized even before further construction.

Corporate production investment now checks spare capacity on a known electrical grid, including researched operating capacity, temporarily idle existing processes and the additional load of pending projects. This prevents profitable-looking expansions from consuming the power required by existing supply chains. Sites without a grid snapshot retain their prior investment policy. Actual operation still settles finite power independently; the investment estimate does not guarantee future generation or fuel supply. Brownout priority follows the application's protected-load policy rather than a named farm ID.

The current outdoor definitions do not support every alien homeworld. Incompatible sites remain blocked and opening reserves do not establish a sustainable farming chain there. Subsequent [controlled cultivation](ControlledCultivation.md) implements hydroponic and automated-biosphere applications with purchased utilities, water recovery, enclosure space and growing cycles. Alien biochemistry, livestock, seasonal calendars, weather, temperature, soil surveys, seed stock, crop disease and irrigation-source depletion still need explicit models or applications.

## Verification

Focused checks cover paid research, material-funded construction, saved growing cycles, missing-resource and energy gates, repeated-turn protection, limited land, direct farm sales and crop-to-factory sales. Material accounting checks cover both new factory recipes. The repeatable catalog audit now runs cultivation through its actual growing interval before checking funded marketplace transfer. UI checks read saved growth and site status from immutable snapshots.

The initial outdoor-farming scope passed 232 tests across 42 engine, control and frontend suites. Subsequent controlled-cultivation verification passed 260 tests across 49 suites with no failures, errors or skipped tests, including generated-economy first-month needs checks. The full Maven suite was not run. The refreshed [production catalog audit](ProductionCatalogAudit.md) records 142 applications, 122 materials and 66 production recipes; four recipes are cultivation, 45 have complete factory definitions, four have partial ammunition definitions and 13 retain legacy definitions.
