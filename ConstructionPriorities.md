# AI construction priorities

## Queue decisions

EmpireAIController stages PrioritizeAIShipConstructionCommand after its ship and yard investment commands. The command recalculates each affiliated provider queue against the current tick snapshot. Public yards and corporate yards in compatible societies participate, including accepted domestic customer contracts. Other empires' provider queues retain their positions and orders. Independent yards keep their relative slots in the overall order list.

The priority model favors wartime threat reduction, essential facility cargo supply and replacements for badly damaged or lost ships of the same role. Combat value is an estimate from captured application-defined salvo damage, accuracy, electronic damage fractions and multipliers and point-defense capacity. It does not simulate a future battle or guarantee disabled enemy ships. Peacetime combat estimates have a separate weight so military stockpiling does not automatically displace essential supply.

Completion estimates use the actual work supported by a discarded next-installment material and fee quote. Missing access, manufacturing capability, paid workforce, materials or cash prevents readiness. Ready orders precede blocked ones. Near completion, waiting time and a capped contribution from contracted fees affect priority. Corporate rates, ownership and previous paid installments stay unchanged. All priority weights and limits live in construction_priority_policy.json; no weapon capabilities or balance values are added to the planner.

## Waiting protection and persistence

ShipConstructionOrder optionally retains immutable ShipConstructionPriority metadata: the managing empire, first ranked turn, latest ranked turn, ready waiting credit, score and explanation. It is part of the existing version 1 save format. The saved physical queue order is authoritative and can continue after reload without rebuilding decisions.

Ready orders that repeatedly miss work gain one waiting credit per observed turn. Duplicate commands in the same turn do not add credit. At eight credits by default, an order precedes normal urgent builds and receives available work before them. Actual positive work resets its credit and protection. Blocked orders do not accumulate ready waiting credit. Stable ties retain the saved queue order. This prevents continuous newly urgent arrivals from indefinitely denying work to a ready commercial order; completion still depends on continued funds, materials and yard service.

Ranking is bounded to 128 orders per yard by default. Larger queues retain their saved order rather than partially ranking a subset. Foreign queues and incompatible corporate providers are not managed by another empire's command. Queue diagnostics display the latest saved explanation alongside the actual construction status.

## Limited verification

AIConstructionPriorityTest verifies urgent paid work ahead of a commercial contract, a controlled equal-damage probe for electronic disabling capability, essential supply in peacetime, near-completion preference, damaged fleet replacement and ordinary campaign tick execution. An unfunded urgent order yields to paid corporate work. Continuous urgent arrivals still allow a saved commercial order to receive work and actual contract fees remain tied to delivered construction. Planning leaves hardware, markets, credits and fleets unchanged.

This is a limited priority test rather than weapon or economic balancing. Tactical threat estimates, civilian trade delivery and repair decisions retain their existing separate planners. Auxiliary hardware construction still follows its existing allocation after ship orders.
