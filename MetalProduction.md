# Extraction and metal production

All existing deposit-extraction recipes and ordinary metal-refining and alloy recipes now have researchable application definitions. Their Java recipe tables and corporate product mappings have been removed. The shared engine reads their input sources, equipment, work, electrical demand, construction bills, operating policies, scaling and site requirements from [technologies.json](engine/src/main/resources/technologies.json).

The live catalog now contains 141 applications, 119 materials and 63 industrial recipes. Of those recipes, 43 have complete application-owned factory definitions and four ammunition recipes have application-owned input/output definitions with further factory migration still needed. The other 16 recipes retain legacy definitions. No older development-save compatibility was added.

## Migrated processes

The migration covers the 16 formerly hardcoded extraction recipes, aluminum, copper and silicon refining, uranium and thorium separation, microgravity platinum separation, nickel-chromium superalloys, titanium aluminide and superconducting cuprates. Hydrocarbon extraction now has an actual researchable application instead of relying on a legacy recipe ID absent from the research catalog.

Existing alloy definitions could not obtain all of their declared elements. The catalog now also defines chromium, titanium, yttrium, platinum and molybdenum extraction plus nickel, chromium, titanium, yttrium and molybdenum separation. A molybdenum ore commodity supplies the molybdenum required by the existing Inconel composition. These are explicit finite-deposit and processing paths rather than fabricated material grants.

Applications may require other researchable applications. The existing magnetic-refining and heavy-alloy applications now have an operational role as prerequisites of the corresponding concrete processes. Integration metadata recognizes such prerequisites generically. It does not apply free production bonuses based on their descriptive text.

## Physical material accounting

| Process | Inputs per batch | Useful products | Other accounted material |
| --- | --- | --- | --- |
| Aluminum separation | 1,000 kg catalog aluminum ore | 500 kg aluminum | 500 kg declared aluminum tailings |
| Copper separation | 1,000 kg catalog copper ore | 600 kg copper | 400 kg sulfur |
| Silicon reduction | 1,000 kg silicates and 187.5 kg carbon | 500 kg silicon | 687.5 kg carbon dioxide |
| Uranium separation | 1,000 kg catalog uranium ore | 100 kg uranium | 900 kg retained elemental residue |
| Thorium separation | 1,000 kg catalog thorium ore | 80 kg thorium | 920 kg retained elemental residue |
| Nickel separation | 1,000 kg catalog nickel ore | 400 kg nickel and 400 kg iron | 200 kg sulfur |
| Chromium separation | 1,000 kg catalog chromium ore | 460 kg chromium and 250 kg iron | 290 kg retained oxygen-bearing residue |
| Titanium separation | 1,000 kg catalog titanium ore | 300 kg titanium and 350 kg iron | 350 kg retained oxygen-bearing residue |
| Yttrium separation | 1,000 kg catalog yttrium ore | 250 kg yttrium and 500 kg silicon | 250 kg retained oxygen-bearing residue |
| Molybdenum separation | 1,000 kg catalog molybdenum ore | 600 kg molybdenum | 400 kg sulfur |
| Microgravity platinum separation | 1,000 kg catalog platinum ore | 500 kg platinum and 300 kg iron | 200 kg sulfur |

These catalog assays are simplified, often very rich feeds. They are not measured mine grades. The fixed aluminum and copper recipes preserve their existing mass-balanced product streams. The additional separations use the existing generic composition-recovery executor, preserving every unrecovered element in actual retained residue. No per-metal engine branch was introduced.

Silicon explicitly consumes carbon as a reducing input. Most other separation profiles currently declare no additional chemical reagents. That is a limitation of the coarse process definitions: acids, caustic soda, carbon anodes, process salts, cooling circuits and their recovery are not yet represented as complete industrial chemistry. An empty reagent bill is visible data rather than an undocumented engine exemption, but it is not proof that the real process needs none.

Retained residue belongs to the facility, survives current-format save/load and uses the existing paid reprocessing rules. Storage is finite and scales with captured installed lines. Saleable sulfur and fixed-recipe tailings enter ordinary product inventory and require actual market purchases to generate revenue. Molecular species, effluent handling and dedicated residue disposal remain further work.

## Alloy corrections

The 1,000 kg alloy bills now agree with the existing material compositions:

- Inconel: 600 kg nickel, 200 kg chromium, 150 kg iron and 50 kg molybdenum
- Titanium aluminide: 650 kg titanium and 350 kg aluminum
- Superconducting cuprates: 500 kg copper, 250 kg yttrium and 250 kg oxygen

The old recipes omitted Inconel's molybdenum and used different proportions for the other two compounds. Carbon dioxide gas and ice now use the same precise 12:32 carbon-to-oxygen mass ratio instead of rounded 27:73 percentages. This removes artificial carbon and oxygen gains or losses from the existing reduction recipes.

## Energy and cost calibration

| Process | Baseline electricity per batch | Product basis |
| --- | ---: | --- |
| New bulk extraction profiles | 50 kWh | 1,000 kg extracted catalog commodity |
| Existing low-volume extraction profiles migrated here | 200 kWh | The application's declared 10–100 kg commodity batch |
| Aluminum separation | 7,500 kWh | 500 kg aluminum |
| Copper separation | 3,000 kWh | 600 kg copper |
| Silicon reduction | 6,500 kWh | 500 kg silicon |
| Uranium / thorium separation | 6,000 / 5,600 kWh | 100 / 80 kg primary metal |
| Nickel / chromium / titanium / yttrium / molybdenum separation | 4,000 / 5,000 / 6,000 / 5,000 / 5,000 kWh | Per 1,000 kg feedstock |
| Platinum separation | 12,000 kWh | 1,000 kg feedstock |
| Inconel / titanium aluminide / cuprates | 1,000 / 1,500 / 4,000 kWh | 1,000 kg product |

Aluminum's 15 MWh/t game value is near the 14.5 MWh/t smelting figure discussed in the [European Commission JRC aluminum assessment, PDF page 282](https://circulareconomy.europa.eu/platform/sites/default/files/2024-12/JRC138903_01.pdf#page=282). That assessment also gives 20.3 MWh/t for European primary aluminum production with a broader process boundary. The game allowance is a simplified electrical scale and omits a complete alumina-refining and thermal-energy chain. The [International Aluminium Institute's boundary definitions](https://international-aluminium.org/statistics/primary-aluminium-smelting-energy-intensity/) likewise distinguish smelting electricity from other production stages.

Copper's game value is 5 MWh/t copper from the unusually rich catalog feed. The [DOE copper industry profile](https://www1.eere.energy.gov/manufacturing/resources/mining/pdfs/copper.pdf) documents substantial differences among mining, milling and smelting routes, and includes model examples based on much lower ore grades. It supports treating route, grade, transport and recovery separately; it does not validate the game's feed assay or this single electrical allowance as a modern whole-mine estimate.

Other new energy values are provisional engineering and balance allowances. Existing iron-chain energy definitions were not changed in this migration. Work units, operator counts, construction bills, investment charges and market base prices also remain provisional. No conversion between game credits and real currency or complete campaign profitability has been established. Every completed batch still pays its actual declared inputs and electrical cost under current market and grid settlement.

## Research, sites and construction

Microgravity platinum separation requires acceleration at or below 0.5 m/s². Alloy applications retain their declared minimum operating tiers. Shared construction quotes now include the recipe's minimum tier as well as researched manufacturing complexity, so a newly built advanced line can reach its own operating requirement. Site selection follows application data; the physical recipe audit chooses a compatible planetary or lunar fixture rather than assuming every recipe works on the same planet.

Corporate planning selects these processes from declared outputs and business orientations. It no longer maps ore or metal names to factory IDs. Opening assets still use scenario-selected lineups and provisional upstream workforce planning; those remain part of the separate startup-planning backlog.

## Verification and remaining work

Copper and aluminum lifecycle tests fund actual scientist work, research both extraction and separation, construct the required equipment with purchased materials, remove opening product stock and process a finite deposit into funded marketplace inventory across current-format reloads. Cash remains conserved during the material transactions.

Additional tests verify that all extraction definitions belong to applications, metallurgical element accounting includes retained residue, nuclear separation retains its formerly missing mass, minimum-tier construction respects application requirements and microgravity processes reject unsuitable sites. The repeatable production audit exercises physical output and funded market transfer for every recipe with compatible sources and sites assumed.

Scoped verification passed 224 tests across 39 engine, control and frontend suites with no failures, errors or skipped tests. The final explicit operating-policy coverage check and physical recipe audit also passed. The full Maven suite was not run.

The industrial recipe graph can now replenish 59 materials under those assumptions, compared with 45 before this migration. Even hypothetical external access to every natural commodity leaves 35 materials without a replenishable chain. This is an optimistic catalog-coverage result rather than a guarantee of local deposits, transport, affordable investment or a sustainable generated campaign.

Remaining work includes agriculture, biomass conversion, propellant processing, advanced synthesis, completion of ammunition factory metadata and infrastructure services. Further metal chemistry, geological grade variation, waste handling, construction equipment and price calibration are still needed. The full campaign balance audit remains deferred.
