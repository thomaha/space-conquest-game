# Contingency fuel reserves (Draft)

## Purpose

Carry usable surplus propellant for unexpected maneuvers, diversions and combat. Minimize planned fuel consumption while preserving useful insurance; buying the absolute minimum is not the goal. No return itinerary is required.

## Live provisional policy

- Persist an immutable fuel reserve policy on each fleet. Offer an automatic risk policy and a configurable reserve percentage. Splitting inherits the source policy; merging uses the more protective policy unless explicitly changed later.
- Provisional automatic targets are 5% of each ship's main-tank capacity for routine travel, 10% for an owning empire participating in cold war and 20% when its owning empire is at total war. Resolve corporate owners to their empire. These percentages are starting points for tuning. Cold war is a conservative empire-wide elevated-risk proxy; route-specific piracy, hostile territory and maneuver hazards remain future refinements.
- Use tank capacity as the reference so a nearly empty tank does not silently reduce the contingency target to nearly zero. Extra bulk supplies do not count as immediately usable main propellant unless an already funded transfer makes them available before the relevant burn.
- Keep compatible uncommitted nuclear drive feed for the protected main propellant as well, so contingency stock remains usable. Count all carried fuel in loaded mass. A protective reserve may reduce peak speed or extend coasting; electricity, passenger stores and controlled braking must still cover the resulting duration.
- Ordinary planned burns may use only main fuel above the protected target. Keep this contingency floor separate from propellant allocated to queued crossings and destination approaches. Approach fuel is an expected expense and must not consume contingency fuel in its preview.
- Fuel purchases use actual current-port stocks, cash and capacity. Safe surplus purchases remain permitted. Automatic traders retain the reserve and wait or resupply when they cannot fund their complete next leg. They never invoke an emergency override automatically.

## Emergency override

- An explicit per-order override permits planned use of contingency fuel. It does not relax braking, electricity, passenger, ownership or location checks.
- The departure preview must identify the override and show the projected reserve shortfall. Keep the override separate from the fleet's saved normal policy so one emergency does not permanently lower later targets.
- Recovery uses the same policy by default and offers an explicit override when consuming contingency stock is necessary. Existing saved journeys retain their already committed trajectory; a new policy must not retroactively invalidate their paid burns.

## Previews and saved state

- Show each ship's departure main fuel, planned burn, expected arrival fuel, protected target and surplus above the target. Display the selected risk basis and any emergency override.
- Preview local departure, crossing and destination approach together where the itinerary contains those phases. Subtract planned consumption once and preserve the same contingency floor across the phases.
- Preserve normal policy through movement, resupply, combat, recovery, fleet membership changes and save/load. Older saves receive the automatic default for subsequent orders. Bump the save version when adding persistent policy.

## Acceptance checks

- Check routine and wartime targets, corporate ownership, configured percentages and invalid inputs.
- Check that mixed fleets protect each member's own reserve and that planned braking remains funded.
- Check reserve conservation through local departure, crossing and approach plus forecast/tick agreement.
- Check explicit emergency departure and recovery without weakening other readiness gates.
- Check split/merge, ship replacement and older save compatibility.
- Rerun trade and endurance audits and report changes in delivery capacity, fuel consumption and safety. Do not change opening stocks merely to restore previous counts.

## Implementation status

The initial integration is implemented in save version 40. Normal policies persist through fleet copies, split/merge and save/load. Local, sublight, rescue and physical recovery planning protect main-tank targets, including future scheduled-refill braking. Movement and recovery commands expose an explicit one-order override and fleet controls queue target edits. Warp uses no main propellant for the crossing, so a ship already below its target can warp when all other gates pass; its shortfall is disclosed and later powered maneuvers still need reserves or an override. Existing saved journeys retain their frozen commitments, with missing protected floors loading as zero.

The full Maven suite covers 709 tests, including reserve ownership and diplomacy, mixed fleet targets, complete next-port approach, active save/load, organization and emergency departure/recovery. Routine nearby-port audits retain 120 fixed and 180 roaming deliveries without interrupted voyages. Fuel-shortage scenarios now produce fewer deliveries and some stationary electrical outages; preserving contingency stock can prolong port waiting. These results and provisional limits are recorded in [TradeReliabilityReport.md](TradeReliabilityReport.md), [TradePropulsionReport.md](TradePropulsionReport.md), [ShipEnduranceReport.md](ShipEnduranceReport.md) and [RescueBalanceReport.md](RescueBalanceReport.md).
