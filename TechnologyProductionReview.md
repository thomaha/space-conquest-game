# Technology, materials and production review

The subsequent [agricultural economics review](AgricultureEconomics.md) compares real-world scales, repeated household demand and existing offworld freight costs. It corrects self-generated market demand and low-gravity launch units and records destination-price and energy-provider funding gaps. The historical catalog review below does not establish current campaign profitability.

Subsequent cultivation implementation: [FarmingProduction.md](FarmingProduction.md) separates farming from food processing and adds land and growing-cycle constraints. [ControlledCultivation.md](ControlledCultivation.md) adds hydroponic and biosphere applications with finite enclosure space, paid utilities, water recovery and element-balanced mixed harvests. Current coverage reaches 62 materials through 66 production recipes under the audit's full-research and resource assumptions. Enclosed farming remains unprofitable at baseline prices. The earlier review below remains a historical baseline.

Reviewed on 15 October 2026 against the current working tree. This is an implementation and catalog review with focused verification. Proposed changes below are Draft. No production rules or balance values were changed by this review.

Subsequent implementation: [ProductionWork.md](ProductionWork.md) records typed work units, captured equipment, skill, automation and actual process-energy settlement for the initial iron chain and rare-earth separation. [RefineryRecovery.md](RefineryRecovery.md) records composition-aware gold and silver recovery, research and retained-tailings reprocessing. The findings and catalog counts below describe the reviewed baseline; most recipes and factory construction are not yet migrated.

## Main conclusion

The subsequent extraction and metal migration is recorded in [MetalProduction.md](MetalProduction.md). Current repeatable catalog coverage reaches 59 materials through industrial recipes under full-research, source, facility and funding assumptions. The counts below remain the original review baseline.

The subsequent iron-chain application migration and remaining factory exceptions are recorded in [FactoryApplications.md](FactoryApplications.md). Baseline coverage counts below remain the original review rather than a claim of current full-chain completeness.

The researched-application-to-market loop has working pieces but is not complete enough to guarantee that newly researched equipment can be manufactured or that its supply chain is economically sustainable. The main need is to connect existing definitions and correct production accounting before adding many more technology names.

The live catalog contains 31 foundations, 123 applications, 118 materials and 53 industrial recipes. All recipe goods and declared application materials reference existing material IDs. All research prerequisite IDs resolve and the catalog prerequisite graph is checked for reachability by the repeatable audit. Names and references alone do not establish production availability.

Pure elemental materials use their element names, such as gold and silver. Refining is a production process rather than part of the resulting element's name. Distinguish ore and concentrate explicitly where those are separate material inputs.

With every industrial recipe available and suitable deposits, liquid water, facilities, staff, energy and money assumed, the recipe graph can replenish 45 materials. Even adding hypothetical mining-ship access to every natural material raises that upper bound to only 76 of 118 materials. The other 42 lack a complete replenishable chain. These are optimistic global coverage counts: they do not demonstrate local accessibility, affordable transport, successful AI investment or a self-starting economy.

The full inventories, missing inputs and per-recipe cost calculations are in [ProductionCatalogAudit.md](ProductionCatalogAudit.md). [ProductionChainAuditTest](engine/src/test/java/com/spaceconquest/engine/industry/ProductionChainAuditTest.java) regenerates the underlying report at `engine/target/production-chain-audit.md`.

The prerequisite graph has no unreachable research nodes. Runtime consumer labels currently report 13 available, 62 partial and 48 planned applications. Treat those labels as metadata: `ship_mining_arrays` is reported as having no consumer even though the ship mining catalog and processor use it. The older integration audit's 121-application summary is also stale relative to the current 123. Update consumer classification and inventories together when wiring production support.

## Findings by priority

### High: Essential and advanced materials have incomplete supply chains

There is no industrial recipe for silver, lithium, titanium, tungsten, yttrium, chromium or many other refined metals. Carbon nanotubes, graphene, industrial ceramics and radiation-hardened semiconductors are defined but lack production recipes. Consequently existing recipes for titanium aluminide, Inconel, superconducting cuprates, metamaterials, quantum spin glass and antimatter containment cells cannot replenish all their inputs.

This affects current consumption as well as future designs. [PopulationProcessor](engine/src/main/java/com/spaceconquest/engine/PopulationProcessor.java:121) requires silver for synthetic populations. Silver ore has a mine but no silver refinery. Limestone for lithovores and nitrogen gas and methane ice for ammonia-based populations have no industrial extraction recipe. Helium-3 is also required by fusion pellets and some population needs without an industrial extraction path. Small-body ship mining is a conditional alternative for natural materials, with actual deposit, hardware, access, power, cargo and delivery requirements; it does not refine synthetic materials or guarantee these supplies in generated campaigns.

Natural resource names on planets are not automatically mineable inventory. Inspect actual `GeologicalDeposit` records as well as the material catalog. The current prospecting fallback can create a `refined_iron` deposit rather than a geology-selected raw ore, so it is not a general solution for missing feedstocks. See [ProspectingProcessor](engine/src/main/java/com/spaceconquest/engine/industry/ProspectingProcessor.java).

### High: Production and electricity do not scale by the same work quantity

[IndustryMarketProcessor](engine/src/main/java/com/spaceconquest/engine/industry/IndustryMarketProcessor.java:76) produces batches in proportion to powered workers, tier throughput, researched effects and foundation improvements. [PowerProcessor](engine/src/main/java/com/spaceconquest/engine/industry/PowerProcessor.java:119) caps the staffing factor at one batch's worker count and scales power linearly with tier.

For example, a tier 1 electrolyzer with 10 workers produces one baseline batch using 5,000 kW. With 100 workers it can produce ten baseline batches while still requesting 5,000 kW. Research and tier throughput introduce further differences. There is no corresponding installed production-line capacity limit. Thus the energy per kilogram changes with staffing for reasons unrelated to a defined efficiency improvement. Price and workforce calibration cannot be reliable until this is reconciled.

Power and wages settle before input purchases. An input-starved, research-blocked or unsuitable facility can therefore consume paid staffing and electricity without making output. Some idle overhead is reasonable, but the current model does not distinguish idle overhead from productive process energy.

Draft: Define explicit recipe batches, person-hours, process kWh and installed line capacity. Calculate planned feasible work from research, capacity, material availability, energy and funding, then settle actual execution on the tick. Keep idle energy and maintenance separate. Do not make power free when additional workers or research increase output.

### High: Manufacturing capability and factory identity are not a complete industrial model

[FacilityManufacturingCapacity](engine/src/main/java/com/spaceconquest/engine/industry/FacilityManufacturingCapacity.java:42) gives a specialized tier 1 facility its application's own baseline complexity. This catches complexity increases from research but does not require a newly discovered high-complexity process to obtain correspondingly advanced manufacturing equipment. The only current capacity rule is the default offset zero plus one complexity per extra tier.

[SetFacilityRecipeCommand](control/src/main/java/com/spaceconquest/control/command/SetFacilityRecipeCommand.java) checks research and calculated capacity but does not preserve a physical factory family or charge for conversion. A sufficiently eligible existing facility can change between very different processes, including power plants, without purchasing their machinery or construction materials. Recipes carry tiers but no installed process compatibility.

[BuildFacilityCommand](control/src/main/java/com/spaceconquest/control/command/BuildFacilityCommand.java:60) selects the manufacturing-capacity minimum tier without taking `recipe.minTier()` into account. A recipe with minimum tier 2, 3 or 4 can therefore get a tier 1 construction project and require further expansion before producing. The compatibility constructor also ignores the requested tier argument. Corporate investment explicitly takes the recipe minimum into account, so the two construction paths differ.

Draft: Separate the physical factory type and installed process equipment from its selected recipe. Gate compatible processes on installed capacity and charge for conversions. Share the same minimum-tier and construction quote between player and AI commands.

### High: Most industrial construction does not use application-specific bills or paid construction labor

For ordinary industrial facilities [ConstructionMaterialCatalog](engine/src/main/java/com/spaceconquest/engine/industry/ConstructionMaterialCatalog.java) uses the same bill per tier: 500 kg refined iron, 150 kg aluminum, 50 kg copper and 10 kg silicon. Plants and recognized shipyards have specific definitions, but mines, chemical factories and advanced refiners mostly share this fallback. Their declared application `requiredMaterials` lists do not define quantities or control this generic bill.

Ordinary construction work is generally 500 hours per tier rather than the application's declared `costToBuildPerUnit`. [IndustryProcessor](engine/src/main/java/com/spaceconquest/engine/industry/IndustryProcessor.java:76) advances each facility project by up to 100 hours daily when materials can be purchased. Its paid-worker argument supports ship construction but does not constrain facility construction work. Many simultaneous factory projects therefore each receive that work allowance without a shared paid construction workforce.

This can bypass the intended advanced material requirement or block a real requirement without making the reason visible. It also prevents meaningful comparison with real factory size and construction effort. Ship blueprints already have a better foundation: captured frame, armor, component and installation receipts. Preserve that path rather than treating ships and industrial factories as equally incomplete.

Draft: Define application-backed construction materials, installation work, construction labor and required plant equipment. Scale by actual rated capacity. Capture project quotes when ordered and use shared paid construction capacity for progress.

### High: Baseline prices are disconnected from process economics

[MarketProcessor](engine/src/main/java/com/spaceconquest/engine/market/MarketProcessor.java) assigns fixed reference prices by switch with a default of 10 credits/kg. Spot prices range from roughly 0.4 to 2 times that reference. Factories buy inputs at retail spot prices and sell outputs at 80% of spot. Neither the reference price nor the wholesale share is derived from production cost.

Several ordinary processes lose money at baseline prices and baseline recipe staffing. Illustrative results below include 24-hour recipe electricity at 0.02 credits/kWh and the current profession wage. They exclude maintenance, capital, transport and taxes, assume tier 1 and no research improvements and assume all output sells. These are diagnostic examples, not assertions about every current campaign price or staffing configuration.

| Process | Output per baseline batch | Wholesale proceeds | Inputs | Wages | Electricity | Contribution |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Steel alloying | 1,000 kg steel | 8,000 | 4,940 | 250 | 3,840 | -1,030 |
| Consumer goods | 1,000 kg goods | 9,600 | 7,745 | 250 | 2,880 | -1,275 |
| Water electrolysis | 888.9 kg oxygen plus 111.1 kg hydrogen | 1,600 | 1,500 | 250 | 2,400 | -2,550 |
| Cannon ammunition | 5 kg rounds | 40 | 42 | 25 | 36 | -63 |

Cannon and kinetic defense ammunition already cost more in input materials than the baseline wholesale sale brings in. Other advanced products use the default price despite consuming costly refined materials. Byproducts cannot be assumed to sell indefinitely merely because they have a catalog price.

Draft: Calibrate physical receipts first, then derive a reference cost from materials, skilled labor, process energy, consumables, maintenance and amortized plant cost at stated utilization. Add a sustainable normal margin and distribution cost when defining a reference market price. Keep actual supply, demand, tariffs, local scarcity, transport and intentional subsidies separate. Market prices may still fall below cost; a cost reference should not create a guaranteed buyer or guaranteed profit.

### Medium: Several production processes bypass their corresponding application research

[IndustryRecipeCatalog](engine/src/main/java/com/spaceconquest/engine/industry/IndustryRecipeCatalog.java:125) imports refinement recipes with `industrial_production` as the required foundation. IDs such as `alloy_steel`, `zero_g_platinum`, `superconducting_cuprates` and `fuel_fusion_pellets` are not application IDs. [ApplicationProduction.isAvailable](engine/src/main/java/com/spaceconquest/engine/technology/ApplicationProduction.java:21) intentionally treats noncatalog IDs as available. All 19 imported refinement recipes therefore share a foundation-level legacy gate unless an exact application ID provides a stronger gate.

For example, the catalog application `zero_g_magnetic_refining` does not gate the recipe `zero_g_platinum`; `heavy_steel_titanium_alloying` does not gate `alloy_steel` or `alloy_titanium_aluminide`. Fuel refining and liquefaction also retain some legacy IDs. Discovery of an application consequently does not consistently correspond to gaining its production process. Conversely researching one of these named applications may have no effect on its similarly named existing recipe.

Also, converting `RefinementRecipe` to `IndustryRecipeCatalog.Recipe` retains inputs, outputs, tier and power but drops gravity/environment and profession requirements. The standalone refinement processor checks gravity, but the live market production path does not call that validator. A paid ground factory can therefore execute the nominally zero-gravity recipe when its inputs and other live gates are supplied.

Draft: Give every live recipe an explicit unlock application and required factory/environment/profession constraints. Make any deliberate baseline exceptions explicit data rather than consequences of unmatched strings.

### High: Player controls do not expose the live production catalog consistently

[IndustryView](frontend/src/main/java/com/spaceconquest/frontend/IndustryView.java:179) offers a fixed list including `foundry`, `hydroponics_dome`, `consumer_factory`, `fission_reactor`, `fusion_reactor` and `mass_driver`. Those names are not the corresponding live recipe or power plant IDs accepted by `FacilityResearchEligibility`. `smelter` is accepted infrastructure but has no output recipe until repurposed. The recipe selector lists only `RefinementProcessor.STANDARD_RECIPES`, omitting mining, water, fuel and application-defined ammunition recipes.

The planet power button submits `fusion_reactor`, while tile build callbacks submit `solar_power_array`; the live applications are `fusion_power_app` and `solar_power`. These controls also display optimistic success wording immediately after staging commands. A rejected request can therefore look like successful construction. Several body selectors use fixed Sol IDs rather than the selected snapshot's controlled bodies.

Draft: Build selectors from live supported factory definitions filtered by owner research and actual site eligibility. Present output, material bill, staffing, energy and blockers before ordering. Display tracked command acceptance and saved construction progress. A researched recipe must be reachable through a normal player interaction as well as directly executable in a test.

### Medium: Products reach the marketplace through a funded purchase, not merely through production

The physical path works: production adds to the facility's `IndustryAccount.unsoldStockKg`, then [IndustryMarketProcessor.sellStock](engine/src/main/java/com/spaceconquest/engine/industry/IndustryMarketProcessor.java:154) buys some or all into the body's commercial hub. For monetary economies this requires an order with positive demand, room in hub storage and actual market cash. The market debits its cash and credits the owner. Output that cannot sell remains at the factory and can sell on a later day. Hive transfer is nonmonetary.

[MarketDemandProcessor](engine/src/main/java/com/spaceconquest/engine/market/MarketDemandProcessor.java) creates demand for inputs and outputs of eligible staffed factories as well as population, construction, fuel and stockpile needs. Adding output demand makes goods listable but also makes factory capacity itself a source of desired market purchases, even without an independent final customer. The target is normally 30 days of demand. This buffers new products but is not evidence of eventual consumption or economic viability.

The new audit exercises all 53 recipes with sufficient supplied inputs, eligible knowledge, settled powered-worker counts and a funded spacious hub. Every recipe creates its declared outputs and transfers them into market inventory with consistent money accounting. Separate existing tests cover no buyer cash, retained unsold stock, delayed sale, missing research, unpaid/unpowered workers and insufficient inputs. This does not establish that missing upstream inputs can be replenished or that the full daily economy can sustain every recipe.

Draft: Distinguish physical goods listed for sale from goods the hub has already bought. Show factory stock, market stock, market cash and the exact unsold reason. If retaining the funded market-maker model, explicitly define its inventory target and working capital instead of treating a listing as guaranteed sale. Factory storage also needs an explicit capacity policy; the unsold map currently has no physical limit.

## Coverage of required technology and factory families

The following is a Draft coverage plan for the existing game needs. Presence means some relevant catalog or runtime support, not a fully verified chain.

| Family | Current coverage | Required next definition or connection |
| --- | --- | --- |
| Basic mining and water | Common ore, fertilizer and water recipes exist | Add essential missing extraction, actual geological availability and safe environmental requirements |
| Steel, aluminum, copper and silicon | Live mining/refining and manufacturing recipes | Calibrate yields, energy, paid labor and equipment; connect alloy research |
| Specialist metals | Most material names exist | Silver, lithium, nickel, titanium, tungsten, chromium, yttrium and other refining recipes; route ore versus native metal consistently |
| Atmospheric and volatile separation | Electrolysis and chemical-fuel recipes exist | Nitrogen, methane, helium and helium-3 extraction/separation plus actual environmental feedstock access |
| Life support and food | Soil and biomass production, water, oxygen and food exist | Close every playable species' needs; treat hydroponics, aeroponics and tissue production as separate supported or explicitly planned processes |
| Chemical and polymer production | Biomass polymer and propellant paths exist | Define industrial chemicals, process reagents and catalysts at a manageable aggregate level; separate consumed feedstocks from plant equipment |
| Electronics and precision equipment | Silicon and aggregate consumer goods exist | Define supported electronics/semiconductor and radiation-hardened hardware production; link electronics and computer research to real consumers |
| Structural and advanced materials | Several alloys and synthesis recipes exist | Complete graphite, ceramics, graphene, nanotubes, superconductor and composite precursor chains |
| Energy and nuclear fuel | Eight data-defined grid plants, fission fuel and fusion-pellet recipes | Complete advanced plant bills, isotope/fuel chain eligibility, construction workforce and spent-material policy |
| Batteries and electrical equipment | Ships and grids have electrical behavior | Connect researched battery/electronics applications to captured equipment bills and manufacturing support; do not imply existing behavior proves those application effects |
| Ammunition and ship equipment | Paid ammunition and captured blueprint assembly work | Close tungsten and specialist inputs, define ammunition prices and make every new supported model physically producible at an actual yard |
| Recycling and waste recovery | Byproducts and paid ship retirement exist | General industrial recovery, waste/storage capacity and explicit recycling recipes rather than assuming all byproducts sell |
| Exotic and speculative materials | Names and some recipes exist | Label as planned until an intentional fictional production chain, energy budget and supported consumer are defined |

We do not need a separate technology for every element or a factory for every product. We do need explicit process unlocks and a small set of factory families with meaningful compatibility, capacity and equipment. Adding a general metallurgy/separation framework with data recipes is more useful than adding many unconnected named technologies.

## Real-world anchors for materials, energy and work

Use physical quantities and stated process boundaries as calibration references. These examples are contemporary measurements or historical examples, not exact forecasts for 2050 or 2200 and not direct conversions between dollars and credits.

| Process | External reference | Current game comparison | Implication |
| --- | --- | --- | --- |
| Water electrolysis | DOE reports 55 kWh/kg hydrogen as 2022 PEM system status, with 51 kWh/kg as a 2026 target | One baseline recipe uses 120,000 kWh for 111.1 kg hydrogen: about 1,080 kWh/kg, also producing oxygen | Roughly 20 times the cited historical system intensity at baseline staffing; additional workers change that ratio without added power |
| Hydrogen liquefaction | A Praxair presentation hosted by DOE gives 13 kWh/kg | 144,000 kWh per 1,000 kg: 144 kWh/kg | About 11 times that example at baseline staffing; calibrate separately from hydrogen production |
| Steelmaking | worldsteel reports 20.95 GJ/t crude steel in 2024, equivalent to about 5.82 kWh/kg of total energy | Alloying alone uses 192 kWh/kg at baseline staffing, before iron smelting | Large scale discrepancy; electrical and thermal boundaries differ, so do not substitute the industry average directly into the alloying step |
| Primary aluminum | IAI gives about 4-6 t bauxite to 2 t alumina to 1 t aluminum | Recipe uses 1 t generic aluminum ore for 0.5 t aluminum | About 2 t ore/t metal; acceptable only if the game's ore means a concentrated feedstock rather than untreated bauxite |
| Steel labor | AISI defines labor efficiency using total employee hours divided by shipped tons; a 2005 USITC hearing records a producer's claim of about 0.4 worker-hours per ton for its particular hot-band operations | Steel alloying uses 10 worker-days/t without a defined shift duration; smelting labor is additional | Define person-hours and process scope before assigning work prices; historical plant-specific claims are not a universal target |

Sources: [DOE PEM electrolysis status and targets](https://www.energy.gov/cmei/fuels/technical-targets-proton-exchange-membrane-electrolysis), [Praxair hydrogen liquefaction presentation hosted by DOE](https://www.energy.gov/sites/prod/files/2014/07/f17/fcto_2014_h2_trans_dist_wkshp_burgunder.pdf), [worldsteel energy indicators](https://worldsteel.org/wider-sustainability/sustainability-indicators/), [IAI primary aluminum material chain](https://international-aluminium.org/landing/aluminium-carbon-footprint-faqs/), [AISI labor measurement definition](https://www.steel.org/steel-technology/steel-production/glossary/) and [2005 USITC hearing, transcript page 154](https://www.usitc.gov/sites/default/files/trade_remedy/731_ad_701_cvd/investigations/2004/Hot-Rolled%20Flat-Rolled%20Carbon-Quality%20Steel%20Products/First%20Review%20%28Full%29/hearing_review_03-02-05.pdf).

Draft: Store each calibrated recipe's input/output mass, process loss, byproducts, energy, skilled person-hours, installed throughput and reference provenance. Use ranges when evidence differs. Define whether a feedstock is ore, concentrate or refined product. Where chemistry requires ambient inputs such as air or water, either model them or explicitly document the abstraction. Keep advanced fictional improvements tied to application data and preserve mass accounting rather than multiplying final material output without supplying its feedstock.

## Recommended implementation order

1. Repair normal player access and unify recipe eligibility, minimum tier and paid conversion rules. Add checks that supported research unlocks have a reachable player construction path.
2. Introduce one production-work calculation shared by previews, AI, power billing and execution, with explicit process energy, person-hours and installed capacity. Make factory construction use actual paid workforce.
3. Close essential population chains first: silver, limestone and atmospheric/volatile supplies. Then close metals and precursor chains used by currently supported hardware and live advanced recipes.
4. Replace generic factory bills with capacity-based application definitions. Connect legacy recipe IDs to their intended research and enforce environments and factory compatibility.
5. Calibrate recipes against the physical examples above, derive transparent reference production costs and review resulting prices and margins. Preserve scarce inputs, real payment and transport costs.
6. Run focused research-to-build-to-production-to-purchase scenarios with depletion, shortages and save/reload. Follow with the longer campaign audit once the basic production contract is consistent.

## Verification and limits

The review adds a repeatable audit and a funded physical-output probe for every live industrial recipe. The probe supplies all upstream inputs intentionally; it verifies production and market accounting without pretending to establish supply-chain closure. Research and generation lifecycle tests separately exercise unlock rejection, actual scientist progress, material-funded construction and disk reload.

Focused verification passed 70 tests across 14 suites with zero failures, errors or skipped tests: ProductionChainAuditTest, IndustryRecipeCatalogTest, IndustryMarketProcessorTest, MarketDemandProcessorTest, MarketProcessorTest, ResearchPolicyTest, PowerProcessorTest, PowerBillingProcessorTest, PowerGenerationProcessorTest, PowerPlantApplicationDataTest, ApplicationUnlockLifecycleTest, ResearchLifecycleTest, ResearchInvestmentLifecycleTest and IndustryCommandTest. The audit's two JUnit tests include a processor probe loop over all 53 live industrial recipes.

The generated report distinguishes industrial-only reachability from a generous conditional ship-mining upper bound. It does not count initial market reserves as renewable production. Review findings involving UI, free conversion, environment checks and construction workforce are source findings and are not claimed fixed by passing processor tests. No weapon values, recipes, research costs or market prices were changed. A full Maven suite, broad campaign run and economy balance pass remain outside this review.
