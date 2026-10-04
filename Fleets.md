# Fleets

A fleet is a named collection of ships that travel and operate together. It can contain combat ships, cargo ships, passenger transports, miners, construction ships or any mixture of roles. Every ship belongs to one fleet. A single ship is a fleet of one; removing a ship gives it its own fleet rather than leaving it without a location or movement orders.

## Travel and operation

- Movement orders apply to the whole fleet. Ships share the fleet's site, itinerary, progress and stance.
- Physical sublight departures use the lowest loaded acceleration and the lowest fuel-supported peak speed among all members. Loaded mass includes propellant, generator stores, cargo and passengers. Faster ships throttle their travel to remain with the fleet.
- Each ship supplies its own propulsion fuel, reactor feed and electricity. Adding an escort does not fill a freighter's tanks or improve its endurance. Every member must pass departure checks; a power failure stops shared thrust at the first failing ship.
- Warp currently uses the owner's researched four-day corridor model. Local journeys retain one- or two-day site minimums and extend to the slowest member's loaded maneuver time, rounded up to whole days. Launch, propulsion and electrical checks apply to every member. Per-ship warp speeds and detailed local trajectories remain future rules.
- Trade and passenger assignments refer to ship IDs and remain attached to those ships. A route carrier's escorts travel with it. A fleet can have at most one active trade route so separate automated routes cannot issue conflicting fleet orders.

## Organizing ships

The fleet page provides an Organize fleets panel:

1. Choose a source fleet and select one or more ships.
2. Choose a destination and move the selection into it or merge the whole source into it.
3. To remove ships, enter a new fleet name and detach the selection. Select several ships to split a fleet into two groups.
4. Rename a fleet without changing its ID or orders.

Selection shows blueprint name, ship role and ship ID when the blueprint is available. Ctrl or Shift selects several ships and Select all ships selects the entire fleet. Controls stage commands for the next tick and report completed, rejected or cancelled execution from command receipts. They do not alter the snapshot directly.

## Joining fleets

Ships can join another fleet when both fleets have the same owner and physical location:

- Idle fleets must share the same system and exact surface, orbit, dock or named space site.
- Traveling fleets must have identical itinerary, progress and physical motion. This allows split groups to rejoin while they still travel together and prevents teleporting ships between unrelated journeys.
- Disabled coasting fleets may join after their corridor, position and velocity meet the existing rescue contact tolerances.

The destination keeps its ID, name, stance and travel progress. Empty source fleets are removed. Multiple active trade-route carriers cannot be combined; cancel a conflicting route first or move only escorts. Active rescue approaches lock the membership of both rescue and target fleets until the approach finishes or is interrupted. Names may still change during a rescue.

## Splitting fleets

A split requires at least one selected ship and at least one ship left in the original fleet. The new fleet starts at the same site or flight position, velocity and progress. It inherits the original stance and current itinerary.

Splitting during local travel, warp, sublight travel, recovery or drift preserves existing motion and paid travel commitments. Main propellant budgets are partitioned by ship ID. Actual cargo, hull and shield health, passengers, generator materials, battery charge, outage history and rescue results remain unchanged. Splitting does not repeat a launch payment, propulsion commitment or fuel burn.

An inherited journey retains its existing speed and trajectory. A later departure or validated recovery computes the new fleet's capabilities from its current members. This avoids introducing free acceleration by dropping a slow ship partway through an already funded maneuver.

## Persistence and provisional limits

Fleet membership, names, ship state and inherited travel profiles use the existing save format. No save version change is needed for organization commands. Save/load tests verify that split groups consume the same next-day propellant as their original shared itinerary.

Role combinations and all travel coefficients remain subject to tuning. Fleet organization currently requires a shared owner; allied fleets and mixed empire/corporation command arrangements need separate rules. Formation geometry, fleet officers, ship-to-ship towing and in-flight trajectory changes after a split remain future work.

## Fleet supplies

Plan the next leg through the destination port by default. Return planning is optional and ships may choose their next trade after selling cargo and checking local resupply. Compatible destination stock is advisory until a paid local purchase succeeds. Automated trade retains approach fuel and an electrical arrival reserve. See [FleetSupply.md](FleetSupply.md) for the implemented checks.

When a member exhausts propulsion fuel, the fleet cannot continue shared acceleration or powered braking. Its actual velocity persists as ballistic drift; there is no fuel-free reduced-speed travel mode. A new feasible recovery plan or assistance is required to regain controlled motion.
