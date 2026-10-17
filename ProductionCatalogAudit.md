# Production catalog audit

Catalog: 31 foundations, 188 applications, 123 materials and 100 production recipes, including cultivation.

Consumer classifications reported by ApplicationIntegration: {AVAILABLE=14, PARTIAL=133, PLANNED=41}. These are metadata classifications, not proof of complete production chains.

Reachable through industrial recipes with all research, facilities, power, labor and funding assumed: 108 materials. Deposit extraction, surface liquid water and declared atmospheric intake are assumed where required.

Reachable when every natural material is additionally supplied by a hypothetical accessible small-body deposit and mining ship: 117 materials. This is a conditional upper bound, not a generated-campaign guarantee.

Still unreachable under that upper bound: [fluoride_tailings, hyperdense_neutronium, polymorphic_nanofluid, programmable_matter, radioisotopic_thermoelectric_matrix, tachyon_condensate].

No industrial output recipe (includes natural goods potentially accessible to ships): [ammonia_ice, carbon_dioxide_ice, carbon_monoxide_ice, fluoride_tailings, heavy_sands, helium_gas, hyperdense_neutronium, iridium, limestone, palladium, platinum_group_metals, polymorphic_nanofluid, programmable_matter, radioisotopic_thermoelectric_matrix, tachyon_condensate].

Research nodes unreachable from an empty knowledge set under catalog prerequisites: [].

## Recipe checks and baseline economics

One complete recipe batch at tier 1-equivalent staffing; cultivation batches span their declared growing interval. Power includes the recipe's minimum operating tier. Prices are MarketProcessor base credits/kg, not current spot prices. Sales assume all outputs sell at the 80% wholesale share. Contribution excludes wages, maintenance, capital, transport and tax. Byproduct sales are optimistic. Staffing is reference operator headcount for work-unit recipes and legacy headcount per daily batch otherwise. No research improvements are applied.

| Recipe | Catalog application | Missing inputs: industrial / conditional | Reference staffing | kWh/batch | Input credits | Wholesale credits | Before labor and power | Input kg / output kg |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| aerogel_fabrication | true | [] / [] | 100 | 72000.00 | 5680.00 | 8000.00 | 2320.00 | 1000.00 / 1000.00 |
| alloy_inconel | true | [] / [] | 100 | 1000.00 | 9250.00 | 8000.00 | -1250.00 | 1000.00 / 1000.00 |
| alloy_steel | true | [] / [] | 10 | 1000.00 | 4940.00 | 8000.00 | 3060.00 | 1000.00 / 1000.00 |
| alloy_titanium_aluminide | true | [] / [] | 100 | 1500.00 | 10000.00 | 8000.00 | -2000.00 | 1000.00 / 1000.00 |
| aluminum_ore_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| aluminum_refining | true | [] / [] | 10 | 7500.00 | 2000.00 | 8000.00 | 6000.00 | 1000.00 / 1000.00 |
| automated_biosphere_macro_farms | true | [] / [] | 4 | 218400.00 | 38080.00 | 34546.05 | -3533.95 | 20540.00 / 18976.36 |
| beryllium_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| beryllium_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 1752.00 | -248.00 | 1000.00 / 219.00 |
| biomass_processing | true | [] / [] | 10 | 2000.00 | 1150.00 | 1504.00 | 354.00 | 1100.00 / 1100.00 |
| boron_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| cannon_ammunition_mfg | true | [] / [] | 1 | 1800.00 | 42.00 | 40.00 | -2.00 | 5.00 / 5.00 |
| carbon_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| ceramic_synthesis | true | [] / [] | 100 | 120000.00 | 7200.00 | 8000.00 | 800.00 | 1000.00 / 1000.00 |
| chromium_ore_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| chromium_refining | true | [] / [] | 10 | 5000.00 | 2000.00 | 4680.00 | 2680.00 | 1000.00 / 710.00 |
| cobalt_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| cobalt_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 5160.00 | 3160.00 | 1000.00 / 645.00 |
| consumer_goods_mfg | true | [] / [] | 10 | 144000.00 | 8244.00 | 9600.00 | 1356.00 | 1000.00 / 1000.00 |
| copper_ore_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| copper_refining | true | [] / [] | 10 | 3000.00 | 2000.00 | 8000.00 | 6000.00 | 1000.00 / 1000.00 |
| cryogenic_insulator_fabrication | true | [] / [] | 100 | 120000.00 | 6800.00 | 8000.00 | 1200.00 | 1000.00 / 1000.00 |
| food_processing | true | [] / [] | 10 | 150.00 | 2000.00 | 2720.00 | 720.00 | 1000.00 / 1000.00 |
| fuel_fusion_pellets | true | [] / [] | 100 | 360000.00 | 501000.00 | 1600000.00 | 1099000.00 | 1000.00 / 1000.00 |
| gallium_bearing_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| gallium_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 1632.00 | -368.00 | 1000.00 / 204.00 |
| gold_mining | true | [] / [] | 100 | 200.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 10.00 |
| graphene_fabrication | true | [] / [] | 100 | 192000.00 | 2000.00 | 7920.00 | 5920.00 | 1000.00 / 990.00 |
| graphite_purification | true | [] / [] | 100 | 120000.00 | 2000.00 | 7920.00 | 5920.00 | 1000.00 / 990.00 |
| guided_missile_mfg | true | [] / [] | 10 | 12000.00 | 760.00 | 800.00 | 40.00 | 100.00 / 100.00 |
| helium_3_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| hydrocarbons_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| hydrogen_liquefaction | true | [] / [] | 10 | 144000.00 | 2000.00 | 6400.00 | 4400.00 | 1000.00 / 1000.00 |
| hydroponic_growth_arrays | true | [] / [] | 4 | 58800.00 | 10270.00 | 8636.51 | -1633.49 | 5635.00 / 4744.09 |
| ice_water_treatment | true | [] / [] | 10 | 150.00 | 1000.00 | 1180.00 | 180.00 | 1000.00 / 1000.00 |
| industrial_biomass_cultivation | true | [] / [] | 10 | 10000.00 | 12000.00 | 96000.00 | 84000.00 | 6000.00 / 120000.00 |
| industrial_soil_cultivation | true | [] / [] | 1 | 3000.00 | 4800.00 | 80640.00 | 75840.00 | 2400.00 / 43200.00 |
| kinetic_pd_ammunition_mfg | true | [] / [] | 1 | 2400.00 | 50.00 | 40.00 | -10.00 | 5.00 / 5.00 |
| lead_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| lead_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 6460.00 | 4460.00 | 1000.00 / 807.50 |
| lithium_ore_mining | true | [] / [] | 100 | 200.00 | 0.00 | 160.00 | 160.00 | 0.00 / 100.00 |
| lithium_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 2480.00 | 480.00 | 1000.00 / 310.00 |
| luxury_goods_mfg | true | [] / [] | 10 | 120000.00 | 8200.00 | 15360.00 | 7160.00 | 800.00 / 800.00 |
| magnesium_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| magnesium_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 2128.00 | 128.00 | 1000.00 / 266.00 |
| mass_driver_ammunition_mfg | true | [] / [] | 2 | 3600.00 | 200.00 | 160.00 | -40.00 | 20.00 / 20.00 |
| methane_ice_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| methane_liquefaction | true | [] / [] | 10 | 84000.00 | 2000.00 | 4000.00 | 2000.00 | 1000.00 / 1000.00 |
| mining_outpost | true | [] / [] | 100 | 12000.00 | 0.00 | 160.00 | 160.00 | 0.00 / 100.00 |
| molybdenum_ore_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| molybdenum_refining | true | [] / [] | 10 | 5000.00 | 2000.00 | 8000.00 | 6000.00 | 1000.00 / 1000.00 |
| nanotube_fabrication | true | [] / [] | 100 | 192000.00 | 2000.00 | 7920.00 | 5920.00 | 1000.00 / 990.00 |
| neodymium_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| neodymium_separation | true | [] / [] | 100 | 96000.00 | 2000.00 | 5040.00 | 3040.00 | 1000.00 / 630.00 |
| nickel_ore_mining | true | [] / [] | 100 | 200.00 | 0.00 | 160.00 | 160.00 | 0.00 / 100.00 |
| nickel_refining | true | [] / [] | 10 | 4000.00 | 2000.00 | 6400.00 | 4400.00 | 1000.00 / 1000.00 |
| nitrates_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| nitrogen_gas_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| oxygen_liquefaction | true | [] / [] | 10 | 72000.00 | 2000.00 | 3200.00 | 1200.00 | 1000.00 / 1000.00 |
| phosphates_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| phosphorus_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 1296.00 | -704.00 | 1000.00 / 162.00 |
| plasmonic_alloy_synthesis | true | [] / [] | 100 | 192000.00 | 132000.00 | 8000.00 | -124000.00 | 1000.00 / 1000.00 |
| platinum_ore_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| potash_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| pyro_iron_smelting | true | [] / [] | 100 | 4000.00 | 2225.00 | 3460.00 | 1235.00 | 1112.50 / 1112.50 |
| radiation_hardened_semiconductor_fabrication | true | [] / [] | 100 | 192000.00 | 10000.00 | 8000.00 | -2000.00 | 1000.00 / 1000.00 |
| rare_earth_fluorides_mining | true | [] / [] | 100 | 200.00 | 0.00 | 1000.00 | 1000.00 | 0.00 / 25.00 |
| rare_earth_refining | true | [] / [] | 10 | 6000.00 | 50160.00 | 160041.60 | 109881.60 | 1105.00 / 800.51 |
| rp1_refining | true | [] / [] | 10 | 36000.00 | 2000.00 | 2720.00 | 720.00 | 1000.00 / 850.00 |
| silicates_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| silicon_carbide_synthesis | true | [] / [] | 100 | 120000.00 | 7600.00 | 8000.00 | 400.00 | 1000.00 / 1000.00 |
| silicon_refining | true | [] / [] | 10 | 6500.00 | 2375.00 | 5100.00 | 2725.00 | 1187.50 / 1187.50 |
| silver_ore_mining | true | [] / [] | 100 | 200.00 | 0.00 | 1200.00 | 1200.00 | 0.00 / 50.00 |
| sodium_separation | true | [] / [] | 100 | 96000.00 | 2000.00 | 2016.00 | 16.00 | 1000.00 / 252.00 |
| superconducting_cuprates | true | [] / [] | 100 | 4000.00 | 8000.00 | 8000.00 | 0.00 | 1000.00 / 1000.00 |
| surface_water_treatment | true | [] / [] | 10 | 0.40 | 0.00 | 1200.00 | 1200.00 | 0.00 / 1000.00 |
| synthesis_antimatter_cells | true | [] / [] | 100 | 600000.00 | 10000.00 | 8000.00 | -2000.00 | 1000.00 / 1000.00 |
| synthesis_boron_nitride | true | [] / [] | 100 | 336000.00 | 6000.00 | 8000.00 | 2000.00 | 3000.00 / 1000.00 |
| synthesis_metallic_hydrogen | true | [] / [] | 100 | 720000.00 | 2000.00 | 8000.00 | 6000.00 | 1000.00 / 1000.00 |
| synthesis_metamaterials | true | [] / [] | 100 | 432000.00 | 67000.00 | 8000.00 | -59000.00 | 1000.00 / 1000.00 |
| synthesis_monomolecular_wire | true | [] / [] | 100 | 480000.00 | 10000.00 | 8000.00 | -2000.00 | 1000.00 / 1000.00 |
| synthesis_quantum_spin_glass | true | [] / [] | 100 | 384000.00 | 8500.00 | 8000.00 | -500.00 | 1000.00 / 1000.00 |
| thorium_ore_mining | true | [] / [] | 100 | 200.00 | 0.00 | 64.00 | 64.00 | 0.00 / 40.00 |
| thorium_refining | true | [] / [] | 10 | 5600.00 | 2000.00 | 6400.00 | 4400.00 | 1000.00 / 80.00 |
| tin_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| tin_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 5928.00 | 3928.00 | 1000.00 / 741.00 |
| titanium_ore_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| titanium_refining | true | [] / [] | 10 | 6000.00 | 2000.00 | 3800.00 | 1800.00 | 1000.00 / 650.00 |
| tungsten_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| tungsten_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 5640.00 | 3640.00 | 1000.00 / 750.00 |
| uranium_ore_mining | true | [] / [] | 100 | 200.00 | 0.00 | 80.00 | 80.00 | 0.00 / 50.00 |
| uranium_refining | true | [] / [] | 10 | 6000.00 | 2000.00 | 8000.00 | 6000.00 | 1000.00 / 100.00 |
| water_electrolysis | true | [] / [] | 10 | 6100.00 | 1500.00 | 1600.00 | 100.00 | 1000.00 / 1000.00 |
| water_ice_mining | true | [] / [] | 100 | 50.00 | 0.00 | 800.00 | 800.00 | 0.00 / 1000.00 |
| water_isotope_separation | true | [] / [] | 10 | 192000.00 | 1500.00 | 1719.76 | 219.76 | 1000.00 / 1000.00 |
| yttrium_ore_mining | true | [] / [] | 100 | 50.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| yttrium_refining | true | [] / [] | 10 | 5000.00 | 2000.00 | 6000.00 | 4000.00 | 1000.00 / 750.00 |
| zero_g_platinum | true | [] / [] | 100 | 12000.00 | 2000.00 | 6800.00 | 4800.00 | 1000.00 / 1000.00 |
| zinc_ore_extraction | true | [] / [] | 100 | 12000.00 | 0.00 | 1600.00 | 1600.00 | 0.00 / 1000.00 |
| zinc_refining | true | [] / [] | 100 | 96000.00 | 2000.00 | 5092.00 | 3092.00 | 1000.00 / 636.50 |

## Application material coverage

Declared requiredMaterials are names without quantities. Hardware receipts can differ and need separate review.

| Foundation | Application | Reported consumer | Declared materials lacking even a conditional supply chain |
| --- | --- | --- | --- |
| space_mining | ship_mining_arrays | ShipMiningCatalog / ShipMiningProcessor | [] |
| methalox_propulsion | methalox_rocket_engines | ShipApplicationProduction | [] |
| methalox_propulsion | methane_liquefaction | IndustryMarketProcessor | [] |
| hydrolox_propulsion | hydrolox_rocket_engines | ShipApplicationProduction | [] |
| hydrolox_propulsion | hydrogen_liquefaction | IndustryMarketProcessor | [] |
| cryogenic_stasis | cryogenic_stasis_pod | ShipApplicationProduction | [] |
| electricity | electronics | None | [] |
| electricity | solar_power | PowerGenerationProcessor | [] |
| electricity | hydropower | PowerGenerationProcessor | [] |
| electricity | wind_power | PowerGenerationProcessor | [] |
| electricity | thermoelectric_power | PowerGenerationProcessor | [] |
| electricity | combustion_power | PowerGenerationProcessor | [] |
| electricity | nuclear_power_app | PowerGenerationProcessor | [] |
| electricity | fusion_power_app | PowerGenerationProcessor | [] |
| electricity | antimatter_power_app | PowerGenerationProcessor | [] |
| electricity | batteries | None | [] |
| nuclear_fission | fission_reactors | ShipApplicationProduction | [] |
| nuclear_fission | fission_weapons | None | [] |
| nuclear_fission | uranium_refining | IndustryMarketProcessor | [] |
| nuclear_fission | thorium_refining | IndustryMarketProcessor | [] |
| nuclear_fission | graphite_purification | IndustryMarketProcessor | [] |
| nuclear_fusion | fusion_reactors | None | [] |
| nuclear_fusion | fusion_weapons | None | [] |
| nuclear_fusion | water_isotope_separation | IndustryMarketProcessor | [] |
| nuclear_fusion | fuel_fusion_pellets | IndustryMarketProcessor | [] |
| industrial_production | cannon_ammunition_mfg | IndustryMarketProcessor | [] |
| industrial_production | ship_cannons | CombatModuleCatalog / ShipApplicationProduction | [] |
| industrial_production | chromium_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | titanium_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | yttrium_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | molybdenum_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | platinum_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | zero_g_platinum | IndustryMarketProcessor | [] |
| industrial_production | alloy_inconel | IndustryMarketProcessor | [] |
| industrial_production | alloy_titanium_aluminide | IndustryMarketProcessor | [] |
| industrial_production | superconducting_cuprates | IndustryMarketProcessor | [] |
| industrial_production | nickel_refining | IndustryMarketProcessor | [] |
| industrial_production | chromium_refining | IndustryMarketProcessor | [] |
| industrial_production | titanium_refining | IndustryMarketProcessor | [] |
| industrial_production | yttrium_refining | IndustryMarketProcessor | [] |
| industrial_production | molybdenum_refining | IndustryMarketProcessor | [] |
| industrial_production | hydrocarbons_mining | IndustryMarketProcessor | [] |
| industrial_production | aluminum_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | copper_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | silicates_mining | IndustryMarketProcessor | [] |
| industrial_production | nitrates_mining | IndustryMarketProcessor | [] |
| industrial_production | phosphates_mining | IndustryMarketProcessor | [] |
| industrial_production | potash_mining | IndustryMarketProcessor | [] |
| industrial_production | carbon_mining | IndustryMarketProcessor | [] |
| industrial_production | water_ice_mining | IndustryMarketProcessor | [] |
| industrial_production | gold_mining | IndustryMarketProcessor | [] |
| industrial_production | silver_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | rare_earth_fluorides_mining | IndustryMarketProcessor | [] |
| industrial_production | uranium_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | thorium_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | lithium_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | nickel_ore_mining | IndustryMarketProcessor | [] |
| industrial_production | industrial_soil_cultivation | FarmingDefinition / IndustryMarketProcessor | [] |
| industrial_production | industrial_biomass_cultivation | FarmingDefinition / IndustryMarketProcessor | [] |
| industrial_production | automated_biosphere_macro_farms | FarmingDefinition / IndustryMarketProcessor | [] |
| industrial_production | hydroponic_growth_arrays | FarmingDefinition / IndustryMarketProcessor | [] |
| industrial_production | aeroponic_nutrient_misting | None | [] |
| industrial_production | food_processing | IndustryMarketProcessor | [] |
| industrial_production | biomass_processing | IndustryMarketProcessor | [] |
| industrial_production | consumer_goods_mfg | IndustryMarketProcessor | [] |
| industrial_production | luxury_goods_mfg | IndustryMarketProcessor | [] |
| industrial_production | water_electrolysis | IndustryMarketProcessor | [] |
| industrial_production | surface_water_treatment | IndustryMarketProcessor | [] |
| industrial_production | ice_water_treatment | IndustryMarketProcessor | [] |
| industrial_production | algae_carbon_scrubbing | None | [] |
| industrial_production | bioreactor_tissue_printing | None | [] |
| industrial_production | mining_outpost | IndustryMarketProcessor | [] |
| industrial_production | alloy_steel | IndustryMarketProcessor | [] |
| industrial_production | pyro_iron_smelting | IndustryMarketProcessor | [] |
| industrial_production | aluminum_refining | IndustryMarketProcessor | [] |
| industrial_production | copper_refining | IndustryMarketProcessor | [] |
| industrial_production | silicon_refining | IndustryMarketProcessor | [] |
| industrial_production | rare_earth_refining | IndustryMarketProcessor | [] |
| industrial_production | rare_earth_byproduct_recovery | RefineryProcess | [] |
| industrial_production | chemical_leaching_hydrometallurgy | None | [] |
| industrial_production | centrifugal_isotope_separation | None | [] |
| industrial_production | zero_g_magnetic_refining | Application research prerequisites | [] |
| industrial_production | automated_assembly_lines | ShipyardTechnology / ShipyardWorkCapacity | [] |
| industrial_production | nanofabrication_matrices | None | [] |
| industrial_production | replicators_matter_synthesizers | None | [] |
| industrial_production | heavy_steel_titanium_alloying | Application research prerequisites | [] |
| industrial_production | crystal_lattice_tuning | None | [] |
| industrial_production | radiation_ablative_shielding | None | [] |
| industrial_production | orbital_drydocks | None | [] |
| industrial_production | microgravity_foundry | None | [] |
| industrial_production | asteroid_capture_processing | None | [] |
| industrial_production | synthesis_metamaterials | IndustryMarketProcessor | [] |
| industrial_production | synthesis_metallic_hydrogen | IndustryMarketProcessor | [] |
| industrial_production | synthesis_boron_nitride | IndustryMarketProcessor | [] |
| industrial_production | lead_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | neodymium_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | magnesium_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | tungsten_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | cobalt_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | tin_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | zinc_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | beryllium_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | boron_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | gallium_bearing_ore_extraction | IndustryMarketProcessor | [] |
| industrial_production | methane_ice_extraction | IndustryMarketProcessor | [] |
| industrial_production | nitrogen_gas_extraction | IndustryMarketProcessor | [] |
| industrial_production | helium_3_extraction | IndustryMarketProcessor | [] |
| industrial_production | lithium_refining | IndustryMarketProcessor | [] |
| industrial_production | lead_refining | IndustryMarketProcessor | [] |
| industrial_production | neodymium_separation | IndustryMarketProcessor | [] |
| industrial_production | magnesium_refining | IndustryMarketProcessor | [] |
| industrial_production | tungsten_refining | IndustryMarketProcessor | [] |
| industrial_production | cobalt_refining | IndustryMarketProcessor | [] |
| industrial_production | tin_refining | IndustryMarketProcessor | [] |
| industrial_production | zinc_refining | IndustryMarketProcessor | [] |
| industrial_production | beryllium_refining | IndustryMarketProcessor | [] |
| industrial_production | phosphorus_refining | IndustryMarketProcessor | [] |
| industrial_production | gallium_refining | IndustryMarketProcessor | [] |
| industrial_production | sodium_separation | IndustryMarketProcessor | [] |
| industrial_production | graphene_fabrication | IndustryMarketProcessor | [] |
| industrial_production | nanotube_fabrication | IndustryMarketProcessor | [] |
| industrial_production | silicon_carbide_synthesis | IndustryMarketProcessor | [] |
| industrial_production | aerogel_fabrication | IndustryMarketProcessor | [] |
| industrial_production | ceramic_synthesis | IndustryMarketProcessor | [] |
| industrial_production | cryogenic_insulator_fabrication | IndustryMarketProcessor | [] |
| industrial_production | radiation_hardened_semiconductor_fabrication | IndustryMarketProcessor | [] |
| rocketry | cargo_terminal | InfrastructureService / LaunchService | [] |
| rocketry | guided_missile_weapons | CombatModuleCatalog / ShipApplicationProduction | [] |
| rocketry | rocket_engines | ShipApplicationProduction | [] |
| rocketry | guided_missile_mfg | IndustryMarketProcessor | [] |
| rocketry | launch_facilities | None | [] |
| rocketry | fission_engines | ShipApplicationProduction | [] |
| rocketry | fusion_engines | ShipApplicationProduction | [] |
| rocketry | orbital_rockets | None | [] |
| rocketry | interplanetary_rockets | None | [] |
| rocketry | spacecraft | HullTechnology / ShipBlueprintFactory | [] |
| rocketry | large_hull_engineering | HullTechnology / ShipBlueprintFactory | [] |
| rocketry | space_stations | ShipyardTechnology / ShipyardWorkCapacity | [] |
| rocketry | rp1_refining | IndustryMarketProcessor | [] |
| rocketry | oxygen_liquefaction | IndustryMarketProcessor | [] |
| surface_to_orbit_infrastructure | mass_driver_launch_tracks | InfrastructureService / LaunchService | [] |
| surface_to_orbit_infrastructure | space_elevators | InfrastructureService / LaunchService | [] |
| waste_management | gas_capture_scrubbing | None | [] |
| waste_management | slag_processing_molecular_reclaiming | None | [] |
| interstellar_shipping_optimization | automated_cargo_route_freighters | None | [] |
| interstellar_shipping_optimization | pressurized_cargo_holds | ShipApplicationProduction | [] |
| supply_chain_automation | just_in_time_logistics_networks | None | [] |
| supply_chain_automation | cybernetic_workforce_integration | ShipyardTechnology / ShipyardWorkCapacity | [] |
| geological_prospecting | orbital_hyperspectral_mapping | None | [] |
| geological_prospecting | seismic_crustal_sounding | None | [] |
| superconductors | mpd_ion_drives | ShipApplicationProduction | [] |
| computers | electronic_computers | None | [] |
| computers | quantum_computers | None | [] |
| computers | synthesis_quantum_spin_glass | IndustryMarketProcessor | [] |
| energy_fields | shields | CombatModuleCatalog / ShipApplicationProduction | [] |
| energy_fields | scanners | ShipApplicationProduction | [] |
| electromagnetic_weapons | mass_drivers | CombatModuleCatalog / ShipApplicationProduction | [] |
| electromagnetic_weapons | mass_driver_ammunition_mfg | IndustryMarketProcessor | [] |
| electromagnetic_weapons | pd_mass_drivers | CombatModuleCatalog / ShipApplicationProduction | [] |
| electromagnetic_weapons | kinetic_pd_ammunition_mfg | IndustryMarketProcessor | [] |
| electromagnetic_weapons | particle_beams | CombatModuleCatalog / ShipApplicationProduction | [] |
| electromagnetic_weapons | ion_weapons | CombatModuleCatalog / ShipApplicationProduction | [] |
| electromagnetic_weapons | pd_ion_beams | CombatModuleCatalog / ShipApplicationProduction | [] |
| energy_weapons | laser_weapons | CombatModuleCatalog / ShipApplicationProduction | [] |
| energy_weapons | pd_lasers | CombatModuleCatalog / ShipApplicationProduction | [] |
| energy_weapons | plasma_weapons | None | [] |
| energy_weapons | emp_weapons | None | [] |
| defensive_weapons | flak_cannons | None | [] |
| nanotechnology | synthesis_monomolecular_wire | IndustryMarketProcessor | [] |
| nanotechnology | plasmonic_alloy_synthesis | IndustryMarketProcessor | [] |
| antimatter | antimatter_harvesting_storage | None | [] |
| antimatter | antimatter_bombs | None | [] |
| antimatter | antimatter_engines | ShipApplicationProduction | [] |
| antimatter | synthesis_antimatter_cells | IndustryMarketProcessor | [] |
| gene_technology | gene_sequencing | PopulationProcessor | [] |
| gene_technology | gene_editing | PopulationProcessor | [] |
| gene_technology | gene_therapy | PopulationProcessor | [] |
| transhumanism | cybernetic_augmentation | None | [] |
| transhumanism | mind_uploading_digital_immortality | None | [] |
| gravitational_engineering | inertial_dampeners | None | [] |
| gravitational_engineering | artificial_gravity | None | [] |
| gravitational_engineering | singularity_reactors | None | [] |
| warp | warp_drive | None | [] |
| stellar_megastructures | dyson_swarm_assembly | None | [] |
| stellar_megastructures | hyperlane_gateway_construction | None | [] |
| auxiliary_warfare | carrier_hangars | CombatModuleCatalog / ShipApplicationProduction | [] |
| auxiliary_warfare | strike_craft_chassis | AuxiliaryApplicationCatalog | [] |
| auxiliary_warfare | surface_weapon_emplacements | AuxiliaryApplicationCatalog | [] |
