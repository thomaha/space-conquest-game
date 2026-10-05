# Early chemical freighter (Draft)

## Status and agreed direction

Draft: Use a small cargo hold with large dedicated main propellant storage. The first implementation now registers the compact hold and conditioned main tank in the live catalog and offers a researched chemical freighter preset. Existing drive coefficients and the medium frame remain unchanged. Values and thermal behavior remain provisional.

The recommended first family carries 2,500 kg of mixed cargo with a 120,000 kg main tank. A 5,000 kg hold remains a comparison option; the smaller hold leaves space for the hydrolox engine and reduces its electrical load. The proposed ships operate between appropriately positioned orbital bases. They are not surface-launched rockets or a guarantee of travel between any two bases.

## Implemented provisional equipment

| Component | Slots | Dry mass kg | Capacity or output | Continuous electrical demand | Complexity |
|---|---:|---:|---|---:|---:|
| Existing medium frame | 30 available | 15,000 | Existing structural model | Included in hotel load | Existing |
| Dedicated long-range main tank | 16 | 12,000 | 120,000 kg compatible main propellant | 10 kW conditioning allowance | 3 |
| Compact pressurized cargo hold | 3 | 750 | 2,500 kg ordinary mixed cargo | 8 kW full vulnerable-load ceiling | 2 |
| Existing solar array | 4 | 2,500 | 120 kW at Sol and 1 AU | 0 kW | 2 |
| Existing battery | 2 | 2,500 | 500 kWh; 100 kW charge; 200 kW discharge | Existing losses | 2 |
| RP-1/LOX engine | 4 | 4,000 | Existing 600 kN and 3,400 m/s exhaust | 20 kW during powered phases | 2 |
| Methalox engine alternative | 4 | 4,200 | Existing 620 kN and 3,700 m/s exhaust | 22 kW during powered phases | 3 |
| Hydrolox engine alternative | 5 | 5,000 | Existing 650 kN and 4,500 m/s exhaust | 25 kW during powered phases | 4 |

The tank is exclusively working main propellant storage. It cannot double as freight, electrical-generator feed or tanker supply inventory. Purchases must use the selected drive's compatible fuel and oxidizer mixture through normal paid fueling. Cargo can contain several goods within the shared capacity.

Tank tare mass is provisionally 10% of rated propellant capacity and slot use is four slots per 30,000 kg. This is twice the current modular tank's capacity per slot and a lower tare fraction than its 2,000 kg structure for 15,000 kg capacity. These are deliberate proposed equipment values requiring balance and volume review, not measured tank engineering performance. They are not a blanket increase to existing tanks.

The 10 kW conditioning allowance is captured as an essential auxiliary load in every variant. The first implementation assumes powered conditioning prevents net loss. This is a game abstraction rather than a certified cryogenic cooling design.

## Volume and thermal rules

The tank declares 400 m³ of usable volume with a separate 120,000 kg mass limit. Provisional density coefficients are RP-1 800 kg/m³, methane 420 kg/m³, hydrogen 70 kg/m³ and liquid oxygen 1,140 kg/m³. Mixture volume sums each constituent's mass divided by its density. All three rated mixtures fit; unsupported drives cannot use this tank. These coefficients and the slot-to-volume relationship require later tuning.

An essential-power outage causes venting of remaining main propellant: 0.5% per unconditioned day for RP-1/LOX, 1% for methalox and 2% for hydrolox. Equivalent unconditioned hours equal unmet essential kWh divided by essential kW, bounded to 24 hours per tick. Fractional exposure uses exponential retention: `remainingKg = initialKg * (1 - dailyLoss)^(hours / 24)`. There is no thermal grace period in this first model.

Venting removes the compatible mixture proportionally. It does not create surplus oxidizer, consume generator feed or alter tanker supplies. Temperature, differential constituent losses and insulation history are deferred. Loss runs once after daily electricity and interrupted maneuvers resolve and before day-end rescue deliveries. Fully powered accepted departure forecasts retain their existing fuel accounting; an essential-power failure interrupts thrust and recovery uses the actual remaining stores.

Existing saved main fuel and power state preserve the result without a save-version change. Thermal coefficients remain catalog rules, so later tuning affects existing equipped ships too.

## Measured family and reserves

The benchmark departs a hypothetical 100,000 km Earth parking orbit and captures into an 8,000 km Mars orbit. Circular coplanar coast time is 258.79 days. Departure and capture total 3,957.4 m/s. Surface export, reaching the departure base and final local docking are separate funded operations.

All three recommended variants carry 1,250 kg steel and 1,250 kg food with 0.5 cm steel armor. Main tanks start full only as study seeds. Their comparison results are:

| Variant | Slots | Dry kg | Initial wet kg | Main used kg | Main remaining kg | Highest tested protected reserve that fits |
|---|---:|---:|---:|---:|---:|---|
| RP-1 | 29 | 38,925 | 161,425 | 111,020.27 | 8,979.73 | 5% = 6,000 kg |
| Methalox | 29 | 39,125 | 161,625 | 106,163.01 | 13,836.99 | 10% = 12,000 kg |
| Hydrolox | 30 | 40,000 | 162,500 | 95,059.56 | 24,940.44 | 20% = 24,000 kg |

These percentages are the existing routine, elevated and wartime targets. The study applies each target directly to compare its budget; it does not change diplomatic state or the saved automatic policy. Full tanks and excess fuel remain allowed insurance. Burn consumption follows the required maneuver rather than spending that insurance deliberately.

The hydrolox wartime margin is about 940 kg above the protected target. Increasing armor from 0.5 cm to 1 cm makes that same 20% budget fail. Extra cargo, electrical backup equipment, tank losses and unexpected maneuvers can likewise use the margin. A passing benchmark is not evidence that the narrowest margin is a desirable operational load.

At 500 km parking altitude at both planets, none of the tested chemical prototypes passes the main budget. The draft must not imply ordinary low-orbit ports have become reachable by moving their comparison coordinates. Return planning is optional and any later leg is funded at its own departure port.

## Auxiliary power and cargo

Solar arrays power ship services, cargo preservation and the proposed tank conditioning load. Chemical combustion supplies main thrust. The draft contains no solar electric main drive and no nuclear requirement.

For the selected hydrolox ship, essential demand is 12 kW including the tank allowance. The half-food mixed hold draws 4.4 kW under current cargo weighting. With drive auxiliaries active, total demand is 41.4 kW. The existing 500 kWh battery bridges the conservative two-hour eclipse. Mars-distance array output and real charging losses restore charge between eclipses.

The selected ship passes a conservative 710-day electrical check using full drive auxiliary demand throughout hypothetical phase waiting, coast and 60 days after arrival. Repeated interval accounting agrees with the bounded forecast's final battery charge. This overstates normal coast demand but does not validate shadows on a propagated orbit or model the thermal response of a tank failure.

The 5,000 kg hydrolox hold fails this conservative continuous-drive electrical screen even where its main budget fits. Its actual phased electrical budget could differ. It must not be presented as electrically ready based only on main fuel feasibility.

## Construction and access

The 2,500 kg hydrolox design requires complexity 4 manufacturing. RP-1 and methalox require complexity 3 under these draft tank rules. Blueprint validation uses the real medium frame, material strength, armor, power and slot calculations. All tested 150,000 kg tank layouts exceed the frame limit and are rejected.

The selected hydrolox construction estimate is 400 standardized labor-hours and 40,000 kg of materials: 34,000 kg steel, 4,000 kg refined copper and 2,000 kg silicon. The initial implementation uses this existing generic material-bill convention. A yard must still have the required resources. Tank-specific manufacturing recipes and insulation costs remain future tuning work.

These fully loaded ships fail Earth surface lift capability. Construction orders containing the conditioned tank now require an owned operational orbital yard with an online slipway, adequate manufacturing complexity and a commercial hub. They cannot fall back to a ground yard. The generic blueprint launch flag depends on the owner's selected reference planet and is not a guarantee of Earth launch capability. A finished ship must buy fuel and charge its batteries; commissioning begins with empty stores and may buy only a small available initial main load.

The tank and compact hold require electricity and industrial production. Drive research and solar power are checked separately. The designer's chemical freighter card previews asynchronously and stages registration through the command queue. Editing retains the compact hold and tank. Construction rechecks current research and yard access.

## Implementation sequence

1. Implemented: Dedicated catalog entries, research requirements, declared mixture volume and provisional outage venting.
2. Implemented: Asynchronous preset preview, queued registration, preserved editing choices and orbital construction access.
3. Verified: Paid compatible-mixture purchases, current research and yard checks, powered retention, outage venting, interrupted thrust, save/load and preserved mixed freight. Existing affordable partial-purchase rules still apply. Study seeds do not become free opening supplies.
4. Implemented with user permission: The high-orbit impulse prototype includes saved trajectory and parking state, shared power and fuel accounting, fleet copies, funded docking and explicit unsupported recovery. See [OrbitalTransfers.md](OrbitalTransfers.md).
5. Tune payload, tank mass, slots, thermal loads, fuel prices and reserve margins from paid voyage results. The narrow hydrolox wartime case especially needs later adjustment.

## Evidence and reproduction

The detailed study is [ChemicalFreighterDraftReport.md](ChemicalFreighterDraftReport.md). Run `mvn test` with JDK 27 to regenerate `engine/target/chemical-freighter-draft-report.md`. Original study IDs remain test-local; gameplay uses `mod_long_range_main_tank` and `mod_compact_freighter_hold`. Lifecycle tests exercise those live entries. Orbital integration tests verify paid fuel and charging, mixed cargo delivery, funded docking and forecast/tick agreement for the operational prototype. These cases do not establish general economic profitability.
