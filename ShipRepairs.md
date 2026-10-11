# Shipborne repairs (Draft)

## Research and construction

The ship repair workshop (`mod_ship_repair_workshop`) requires the owner's electricity, industrial production and robotics research. Foreign research does not unlock it. Research enables new designs rather than adding workshops to existing ships or sharing blueprints.

The provisional workshop occupies three slots, weighs 2,000 kg and draws 20 kW. Its construction receipt adds 250 kg steel, 50 kg refined copper and 50 kg silicon to the ordinary hull bill. These values establish the mechanism and remain subject to tuning. The designer offers researched workshop counts through ordinary design commands. Mass, power and complexity enter authoritative validation. Installed counts remain visible when editing an unused design.

## Daily work and fleet scope

A healthy powered workshop provides 20 repair points per simulation day. Multiple workshops add capacity and mount condition scales their output. Disabled workshops provide none. Capacity is frozen before work so restoring a disabled workshop cannot grant an extra turn of work. Another functioning workshop in the same fleet can repair it.

Repairs run automatically on the simulation tick after electrical accounting. Providers need a captured power profile covering workshop demand and no recorded essential or drive-power shortfall. Legacy ships without an electrical profile receive no free work. Interrupted local flight, power-interrupted travel and recovery fleets wait; idle fleets and funded travel can receive repairs.

Each host repairs its own damaged mounts first, then its hull. Mounts use the mission priorities below. Remaining capacity goes to other surviving ships in the same fleet in saved ship order, again repairing mounts before hull. Recipients must belong to the fleet owner. Separate fleets do not share work even at the same site. Splitting a fleet separates workshop access until ships rejoin. Destroyed ships cannot be repaired or revived.

## Fleet repair status

The fleet view has an expandable read-only repair status panel. It lists each ship's repairable mount damage in the current priority order and hull work up to commissioning health. Workshop rows separate condition-scaled output from ready capacity before materials and identify disabled workshops, missing electrical profiles, recorded power shortages, interrupted motion and cargo protected for trade delivery. First-priority recipe shortages use each provider's own cargo. An immutable projection through the actual onboard processor shows work funded by current stores across that fleet without spending resources or advancing the electrical tick.

The panel shows the saved guard, escort, intercept or stance intent for recovery. Resumption still requires safe funded return and a valid assignment. A destroyed ship cannot receive work and an unavailable blueprint has unknown repair needs rather than an invented estimate.

Calculations run asynchronously from the injected GameState snapshot only when the panel is expanded. Expansion survives tick refreshes while request and snapshot guards reject stale results. The UI has no repair or purchase mutation controls. Current power, materials and orders may change before the next authoritative tick; the panel does not promise a completion date or unrestricted capacity. Base quote details are described in [BaseRepairs.md](BaseRepairs.md).

## Mission priorities

ShipRepairPriority chooses a deterministic mount order for each recipient. A fully disabled workshop is first so another functioning workshop can restore it. Useful electrical restoration follows a disabled workshop, then drive work that unlocks funded mobility. Sensors follow when no currently authorized observer covers the fleet's actual position. Coverage includes working ships, online stations, affiliated corporations and directly agreed sensor-sharing partners through the ordinary live coverage rules. Hardware elsewhere in the system does not count merely because it exists.

With a live or recent last-known wartime threat, a fleet assessed as overwhelmed or already withdrawing repairs shield emitters before weapons. A fleet holding its ground repairs weapons before emitters. Remaining damaged mounts use blueprint order and hull repair follows mounts. Without a live or recent threat the ordinary blueprint order remains after disabled workshops, useful electrical restoration, funded mobility restoration and uncovered sensors. Missing materials for a preferred mount leave capacity available for other fundable work. Restoring shield hardware grants no shield charge.

Fleet context is quoted once for each work pass from current coverage, local illumination, funded next-round strength and saved escape or retreat stance. Each recipient uses its current condition and carried electrical reserves. Onboard decisions use the fleets after electrical accounting so a newly recorded outage cannot provide coverage. Quotes consume no stores and do not change orders, agreements or exploration. Existing self-first recipient order, saved ship order and frozen provider capacity remain intact. Newly repaired workshops cannot add work during the same tick. Bases reuse the same mount ordering within their existing service order. Player-configurable queues and priorities across different recipients remain future work.

## Materials and resource accounting

Only the workshop host's carried cargo supplies repair materials. Mount repairs consume the catalog material receipt in proportion to repaired damage out of the mount's 100-point durability. The scarcest ingredient bounds partial work. Hull repairs consume one kilogram of the recipient's hull material per health point and stop at the existing 1,000-point commissioning hull health. Repairs do not reduce higher saved health.

Hosts assigned to active trade routes or still carrying a route's undelivered load do not supply repair work from cargo. A separate workshop ship in the same fleet can still repair those freighters. Recipient cargo, propulsion reserves, electrical fuel and bulk supply stores are never borrowed for repair materials. No market stock or money is withdrawn remotely. Carried materials are consumed directly without an additional purchase fee.

Workshop power demand is included in the electrical profile and paid by the ordinary power tick. Material shortages limit work without creating substitutes or negative stock. Unused capacity is not banked. Restoring an emitter increases potential capacity without recharging shields. Restoring a weapon improves its condition-based output; hull condition and electrical readiness still apply.

## Damage and persistence

General hull-penetrating combat damage includes workshops among damageable hardware. Weapon and shield targeting selects only the requested hardware type. A disabled workshop cannot repair itself. A military hangar alone grants no repairs. Equipped bases now provide paid mount and hull repair; see [BaseRepairs.md](BaseRepairs.md). See [CombatModules.md](CombatModules.md).

Hull health, sparse mount damage, host materials and electrical stores already persist in save version 57. Partial repairs continue after load using the same remaining resources. No additional save schema or repair queue is introduced. The frontend reads immutable snapshots and performs no repair mutation.

## Focused verification and limits

ShipRepairProcessorTest verifies self-first hull and mount repair, shared capacity, exact material and power consumption, limiting ingredients, no free shield charge, damaged and disabled workshops, stacked capacity, no same-tick activation, shortages, freight protection, fleet and ownership boundaries, destroyed ships and deterministic continuation after save/load. ShipRepairModuleTest verifies owned research, foreign-research rejection, construction receipts, power demand and unchanged older designs.

Player-configurable priorities, reserved repair stores, crew skills and replacement remain future mechanics. Sensor damage and paid sensor repair are implemented; see [SensorModules.md](SensorModules.md).

ShipRepairPriorityTest verifies disabled workshop restoration without extra capacity, missing coverage, working backup observers, threat-dependent weapon and shield ordering, unchanged snapshots, useful generator restoration, working electrical backups, available fuel, empty batteries, sunlight and material-blocked fallthrough. BaseRepairProcessorTest checks the same workshop and useful generator priority with exact material, labor and receipt accounting.

FleetRepairStatusTest verifies funded work, exact first-recipe shortages, immutable diagnostics, power blockers and saved guard intent without spending stores. FleetRepairStatusCardTest verifies displayed damage, capacity, base prices, missing ingredients, saved missions, deferred calculations, retained expansion, stale-response rejection and recoverable calculation failures.

Stationary shield recharge now runs through paid electrical accounting before onboard repair. Restoring an emitter creates capacity only; new charge requires another funded power tick. See [CombatModules.md](CombatModules.md#powered-shield-recharge).

Automatic protection recovery previews one real power and onboard repair tick to choose useful shipborne work before a paid base detour. Forecast materials and electrical stores remain uncommitted. The ordinary tick performs actual work. Recovery preserves guard or escort identity in save version 58; see [FleetProtection.md](FleetProtection.md).

Ordinary idle player, AI and corporate space fleets also select useful onboard repair before base service. Save version 59 retains previous assignments for valid resumption. Active transport, supply and rescue commitments are excluded from automatic suspension. The ordinary power tick still performs all real onboard work.

## Propulsion damage and recovery

Recognized main drives use the same persistent 100-point mount condition as weapons, shields, sensors and workshops. Engine-targeted hull hits concentrate mount damage on the drive and general hull hits can damage it as collateral. Remaining condition scales the blueprint's captured thrust without changing its researched design. Ion drives retain their actual sustained electrical limit before condition scaling. A fully disabled drive cannot fund a new physical burn or recovery journey. Warp hardware and older journeys without recognized main-drive hardware retain their existing compatibility rules.

New travel quotes use remaining thrust and loaded mass. Saved local and sublight trajectories check whether every ship can still provide the scheduled acceleration. Coasting continues until the next unsupported burn. Interruption retains actual position, velocity, per-ship commitments and already spent supplies, pays onboard electrical upkeep and cannot claim arrival without braking. Earlier fuel or electrical exhaustion takes precedence over a later drive failure. Native orbital impulses check remaining thrust against their saved duration before paying the burn; failure preserves ballistic motion and does not repeat earlier paid impulses.

A powered onboard workshop can repair damaged drives while mechanically interrupted, using its own real cargo and ordinary daily capacity. Controlled recovery travel still blocks workshop work. Repair restores capability but does not restart the failed itinerary: recovery needs a new funded plan from actual motion. Refuelling alone cannot restore a disabled drive. Existing rescue, fuel sharing and recovery controls remain available when their physical contact and departure checks succeed.

Main-drive material receipts provisionally contain steel equal to 10 percent of module dry mass and refined copper equal to 2 percent. New construction includes these receipts and mount repair purchases or consumes the fraction corresponding to restored durability. These coefficients are mechanism placeholders rather than a completed balance pass. Existing saved mount damage and flight state suffice; no save schema change is required.
## Useful power restoration

ShipPowerRepairPriority quotes the next 24 hours of critical and cargo loads using actual carried generator fuel, surviving backup equipment, retained battery charge and the fleet's current illumination envelope. It compares current hardware with each damaged electrical mount hypothetically restored. A mount gains priority if restoration reduces critical shortage, reduces cargo shortage without worsening critical service or restores a higher-priority shed service without worsening either required load. This is a readiness quote, not a purchase or a promise that one day's repair capacity can fully restore the mount.

Disabled workshops remain first. Useful generation, solar or battery repairs follow before funded mobility restoration, missing sensor coverage and threat-dependent combat hardware. A working backup can remove the shortage and leave the damaged source in ordinary blueprint order. Fuel-starved generators and empty batteries receive no automatic preference. Solar restoration requires useful local illumination and deployed arrays. Battery comparisons first discard energy above damaged capacity; repaired capacity cannot recreate that energy. Sunlight may fund real charging through the ordinary efficiency and rate limits.

Quotes are bounded by 64 illumination phases and skip electrical promotion if that bound cannot cover a full power day. They consume no stocks, money, electricity or repair capacity. Material-short priority mounts fall through to other fundable work. Onboard self-first recipient order, base service order and frozen daily capacity remain unchanged. The repair status panel and paid base first-job quote use the same ordering. No save fields or new repair commands are introduced.

## Funded mobility restoration

FleetMobilityRepairPriority quotes the complete fleet at its current location and motion. A damaged main drive gains priority when the current recovery or departure is blocked and hypothetically restoring that mount makes it fundable with actual stores. Disabled workshops and useful electrical restoration remain ahead of this work, followed by missing sensor coverage, threat-dependent combat hardware and ordinary blueprint order. If motion is already fundable, drive damage retains ordinary priority.

Interrupted local travel reuses the saved position and velocity through PausedTravelRecovery. Interrupted sublight crossings reuse FlightRecoveryReadiness, including braking distance, physical propellant, reactor feed and electrical arrival reserve. Ballistic gravity recovery uses the shared OrbitalRecovery planner at the actual current radius and phase. Stationary saved guard or escort return reuses its existing destination and rendezvous checks. A detected overwhelming threat must produce the existing coordinated funded escape quote before a drive is promoted. Unknown passenger nutrition definitions conservatively block promotion when an existing booked manifest needs them; real departure commands retain their full passenger checks.

Quotes neither transfer fuel nor buy supplies, change orders or restart an itinerary. Empty tanks, overshoot, insufficient electricity or another disabled fleet member can keep a proposed repair from unlocking complete-fleet motion. Material-short drive recipes fall through to fundable work and daily provider capacity stays frozen. The fleet context carries immutable per-ship mount preferences through diagnostics without a save schema change. Fleets with no available onboard workshop skip these mobility quotes in the repair processor.

Paid repair can restore capability over multiple days. The existing recovery and return processors still require actual funded motion and safe valid assignments before departure or reattachment. Focused checks cover unchanged drift and stores, useful power before drive work, material fallthrough, blocked group recovery, coordinated escape and paid guard or escort restoration after save/load and physical return.

## Electrical damage and recovery

Weapons, shield emitters, sensors and onboard workshops draw idle power in proportion to their surviving mount condition. Disabled mounts draw none of their installed operating load. The ship's baseline two kW, other captured loads, passenger life support, cryogenic cooling, cargo preservation and drive demand remain required under their existing rules. Live profiles subtract lost catalogued idle draw from the frozen blueprint; older reduced profiles limit this subtraction to their captured equipment share. Uncatalogued demand is retained and the designer continues to show healthy ratings.

Salvos and shield recharge still pay their separate condition-scaled active energy costs after required upkeep. Blueprint capture checks continue to use healthy ratings so surviving duplicate mounts remain usable. Paid repair restores idle demand along with capability on subsequent power accounting; the repair tick uses the condition present when power was paid and cannot grant extra workshop capacity. Restored demand can exceed damaged generation and trigger load shedding on the next tick. ShipOperatingDemandTest covers real fuel consumption, retained life support, disabled sensor coverage, surviving combat mounts and material-paid repair followed by equipment shedding.

Chemical generators, fission reactors, solar arrays and ship batteries now receive ordinary hull-hit collateral through the saved mount-damage map. Remaining condition scales each equipment type's captured electrical rating. Surviving duplicate mounts contribute their remaining share of output or storage; research and blueprint ratings are unchanged. Older synthetic profiles without corresponding installed hardware retain their electrical compatibility rules.

Generator damage reduces maximum supplied power without destroying its carried fuel. Solar mount condition combines with deployment, orientation and the existing array-condition factor. Battery damage reduces usable capacity, charging power and discharge power. Actual combat hits and the power tick discard energy above the damaged capacity. Repair restores capacity but never returns discarded charge. Immutable forecasts use these limits without changing the supplied snapshot.

Daily and orbital accounting, departure and recovery forecasts, scheduled supplies, combat firing, shield recharge and ion thrust use current electrical capability. Essential and cargo loads retain their existing priority. Sensor coverage and workshop readiness respect the resulting recorded power outages. A disabled generator with no working backup or usable battery cannot fund weapons or shield recharge. Paid grid charging validates damaged capacity and charging limits before withdrawing stock or money. Fleet power cards display current capability while the designer continues to describe healthy blueprint equipment.

Powered workshops in the same fleet may repair electrical hardware during interrupted drift. An unpowered workshop cannot repair itself without another powered provider. Controlled recovery travel still blocks onboard work and repairing the fault does not automatically resume an interrupted journey. Existing paid base service also repairs these mounts. Electrical recipes provisionally require steel equal to 10 percent of module dry mass and refined copper equal to 4 percent; construction and repaired durability use the same receipts. Fuel compartment damage and reactor accidents are outside this mechanism. Existing save version 59 needs no new fields.

## Power load shedding

Electrical intervals separate catalogued sensor, workshop, weapon and shield idle demand from critical upkeep. Critical ship service and passenger life support remain first, cargo conditioning second and scheduled drive demand third. Equipment priority then follows the fleet's current activity as described below. A group is enabled only if its complete surviving idle demand fits available solar, fueled generation and battery discharge. An unaffordable group is switched off and cheaper later groups may still operate. Uncatalogued loads remain critical. Salvo energy and optional recharge require their own funded idle service.

Fuel or battery exhaustion triggers another bounded allocation at its actual time. Shed equipment consumes no idle power and does not record an essential or drive outage. Shortages in critical upkeep, cargo or scheduled drive demand still use ordinary outage, deterioration and motion interruption rules. Ion thrust quotes can shed equipment before applying their sustained electrical limit. Drive-only orbital auxiliary accounting does not charge equipment upkeep twice. Departure and reserve checks permit equipment shedding while requiring critical, cargo and scheduled drive service.

The immutable power snapshot records any service group that was shed during the accounting day. Sensors contribute no coverage, workshops perform no work, weapons provide no firepower and shield charge provides no active protection while their respective group is recorded off. Existing stored shield charge is retained within surviving emitter capacity; power restoration does not invent charge. New daily and tactical accounting clears the recorded service list and evaluates current supplies again. Repair and resupply alone retain it until power is actually allocated. A partially funded accounting day conservatively disables that group's subsequent snapshot capability rather than granting a full day of work.

Save version 60 persists service loads and recorded shedding. Older records default to empty service data and live blueprint quotes reconstruct recognized loads without changing healthy designer ratings. Ordinary power-state copies preserve the status. Fleet power cards distinguish critical and equipment loads and show switched-off services; repair diagnostics explain workshop shedding. ShipLoadSheddingTest checks exact fuel consumption, required load priority, capability gates, finite battery exhaustion, material-paid helper repair, next-tick restoration, immutable state and new and older save data. User-configured service priorities and separate mount switching remain future mechanics.

## Activity-aware power allocation

Required upkeep, cargo conditioning and scheduled burns always precede equipment. The following equipment orders are provisional:

| Activity | Equipment order |
| --- | --- |
| Quiet travel or ordinary operation | Sensors, workshops, weapons and shields |
| Combat | Sensors, weapons, shields and workshops |
| Escape | Shields, sensors, weapons and workshops |
| Waiting for repairs | Workshops, sensors, shields and weapons |

Tactical firing uses combat priority and evasive rounds use escape priority. Funded combat salvos also precede workshop idle demand. Escape shield charging precedes weapon and workshop idle demand after sensors are considered. These reservations use actual remaining output and energy; they cannot displace required loads or run unfunded emitters. Existing charge, condition-scaled rates, arrival reserve and partial salvo rules still apply.

Daily fleet activity uses live or recent last-known wartime danger from the shared sensor view. An overwhelming threat, explicit evasive stance or moving saved escape uses escape priority. Other observed danger uses combat priority. In quiet space, a stationary fleet with damaged surviving ships and installed workshops or a saved REPAIRING stage favors repair. Travel takes precedence over ordinary repair waiting. A historic escape flag alone does not keep a stationary quiet repair fleet in escape mode. Losing actual coverage removes live targeting while three-day observation memory can retain cautious power priorities. This grants no blueprint access. See [SensorModules.md](SensorModules.md#last-known-contacts).

One immutable activity value follows daily accounting, shield phases, analytic local forecasts, native orbital services and auxiliary checks, scheduled propulsion supply and departure reserve previews. Useful electrical-repair quotes rank restored services using the same activity order. Context selection and funding quotes spend no resources and change no missions. Healthy designer ratings and save version 60 remain unchanged because activity is derived from existing live and saved state. ShipActivityPowerTest checks exact idle funding, required-load precedence, paid tactical shots, escape recharge, actual material-paid repair, preview agreement, detected and lost contacts and saved escape versus repair waiting.

