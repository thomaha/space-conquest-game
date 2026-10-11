# Paid base repair (Draft)

## Workshop construction and readiness

The base repair workshop (`REPAIR_WORKSHOP`) requires the station owner's electricity, industrial production and space stations research. Corporate stations use their empire's existing research authority. Construction uses the ordinary station-module project and material bill. The authoritative command fixes the workshop at four slots, 5,000 kg, 50 kW and two engineers; custom input cannot request a free or unstaffed equivalent.

A fully staffed online workshop supplies 50 repair points per day. Additional workshops add capacity and filled worker fraction scales output through the existing labor requirement rules. The station must be operational, alive and powered without a recorded generation deficit. Offline or unstaffed workshops do no work. These provisional values define the mechanism rather than balance targets.

The station view offers a researched build button from its injected snapshot. It stages the existing AddStationModuleCommand; the UI does not install workshops or repair ships directly.

## Docking, access and work order

Only surviving ships physically docked at the station in the same system qualify. Orbiting nearby, approaching, traveling, in-warp and interstellar-ordered fleets receive no base service. Every ship must match its fleet owner and have a known blueprint. Ship cargo, fuel, passengers, flight commitments and blueprint ownership remain unchanged by repairs.

The service accepts the station owner, corporations affiliated with the same empire and owners whose empires have an alliance or federation relationship. Other foreign ships are rejected. Access does not grant blueprints, technology or sensor sharing.

One daily capacity budget is shared across all qualifying fleets and ships in saved order. Each ship's damaged mounts use ShipRepairPriority before its hull: fully disabled workshops first, useful electrical restoration when current fuel, backups and sunlight show a required-load shortage, drive work that unlocks funded recovery or departure, sensors when current authorized coverage is missing and combat hardware according to detected threats. Overwhelmed or withdrawing fleets favor emitters while fleets holding their ground favor weapons. Remaining mounts retain blueprint order. The same policy is used onboard; see [ShipRepairs.md](ShipRepairs.md#mission-priorities). Missing ingredients can block one mount while leaving capacity for other funded work. Unused capacity is not banked. No separate queue or full-repair prepayment is introduced.

## Materials, labor and conservation

Materials come from the commercial hub at the station itself. Planetary stock, another station's hub, ship freight and dedicated fuel or bulk-supply compartments are not substituted. A station without a local stocked hub cannot provide work.

Mount repairs use each catalog material receipt in proportion to the mount's 100-point durability. Hull repair consumes one kilogram of hull material per point and stops at the existing 1,000-point commissioning health limit without reducing higher saved health. The scarcest ingredient bounds partial work. Restoring an emitter does not recharge shield energy and destroyed ships are never revived.

Stationary ships can separately recharge shields through their own real electrical equipment and reserves on the power tick. Base work supplies no free shield charge and newly restored capacity waits for funded recharge. See [CombatModules.md](CombatModules.md#powered-shield-recharge).

Labor costs two credits per completed repair point in addition to the current material prices. A complete recipe and labor must be jointly affordable before anything is committed. Each successful increment consumes exactly its material recipe, debits the ship owner's treasury or corporate capital and credits the local hub's existing market account with material and service receipts. Missing stock or cash creates no charge, negative inventory or unpaid repair. Existing hive material allocation remains a finite-stock internal service without monetary receipts.

Prices and cash are checked anew on every tick. Departing or losing access stops further work and charges while preserving completed repairs and spent resources. Shipborne repair still runs on the ordinary power tick and may precede independently paid base work. See [ShipRepairs.md](ShipRepairs.md).

## Fleet repair quotes

A physically docked fleet's repair panel shows the base's shared daily capacity, service access and workshop readiness. Its first-priority job quote reuses BaseRepairPayment's complete material, labor, stock and cash calculation. Missing local hubs, recipes, valid prices or funds appear as blockers; partial stock or cash shows the reduced funded work. Material and labor receipts remain tick-owned and inspection spends nothing. Finite internal hive allocation can have zero monetary cost.

The quote covers the first preferred job using current prices before other docked ships receive service. Earlier recipients and subsequent jobs still follow the real saved service order and may use the shared capacity, money or ingredients first. It is not a whole-fleet invoice or a guarantee of next-tick progress. See [ShipRepairs.md](ShipRepairs.md#fleet-repair-status).

## Tick integration and persistence

BaseRepairProcessor runs in the engine fleet phase after ordinary power and movement and before tactical encounters. The engine retains repaired ships, stock changes, market receipts, imperial treasuries and corporate capital from the resulting immutable state. The former military-hangar shortcut granting free hull repair and shield charge has been removed. A military hangar alone provides no repair service.

Remaining hull and mount damage, local stock, funds, workshop payroll and market receipts already persist in save version 57. Partial work continues after load without a new schema. Undocking leaves existing damage and completed work intact.

## Focused verification and next mechanics

BaseRepairProcessorTest verifies station-wide capacity across fleets, incremental payment and receipts, material and labor affordability, limiting ingredients, restored hull and disabled workshop condition, staffing and power failures, missing hubs or recipes, physical docking, ownership, living ships, corporate funds, save/load continuation, departure and actual engine fleet-phase retention. BaseRepairModuleTest verifies owned research and canonical construction costs, slots, power and staffing. Existing combat and fleet checks verify removal of free hangar repairs.

Automatic guard and detached escort recovery now chooses useful onboard work before a suitable base. See [FleetProtection.md](FleetProtection.md). Configurable repair priority, surface workshops, module replacement and crew skills remain future work.

FleetRepairRecoveryTest checks repair selection, forecast conservation, funded detours, actual paid work, saved recovery, shortage release, guard and escort restoration, fuel rejection, explicit-order precedence and the provisional hull return threshold. Save version 58 adds the REPAIRING protection stage while retaining the existing assignment fields.

Ordinary idle space fleets now use the same service selection through the common order tick. Previous stance, guard, escort membership or interception persists in save version 59 while repair and funded return execute. Current contact, ownership and service availability gate restoration. Transport commitments retain precedence. See [FleetProtection.md](FleetProtection.md).
