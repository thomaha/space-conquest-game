# Rescue rendezvous rules (draft)

Draft: These rules have an initial implementation. Geometry, tolerances, reserve duration and transfer timing remain provisional and need later tuning.

## Scope

- A disabled fleet drifting on an interrupted physical sublight crossing can request assistance from another fleet with the same owner.
- A rescuer starts at generic system space in the crossing's departure system or from a drift position behind the target on the same directed corridor. It must initially travel no faster than the target.
- Each corridor is identified by its departure system, destination system and crossing distance. Reversed crossings and different corridors do not share a coordinate frame.
- Atmospheric departures, orbit-to-corridor launch maneuvers, lateral interception, warp rescues, towing and reverse flight remain future work. A ship at a planet or station must first reach system space using the existing local movement command.
- Every rescue ship needs a recognized physical drive and captured electrical equipment. Legacy electrical compatibility does not establish rescue endurance.

## Rescue order

Select the disabled receiver ship, donor fleet and ship, cargo or tank source, compatible supply and total kilograms. The fleet card stages a command for the next simulation tick. Validation runs again against the live state before departure.

The request contains one supply transfer. Electrical fuel, main-drive propellant and drive reactor material use the existing mixture, ownership, stock and receiver-capacity rules. Mixture mass includes its oxidizer. No credits, launch service, material or battery charge are invented.

## Interception and braking

The target continues to coast at its saved velocity. The rescuer uses its actual loaded mass, slowest ship's powered thrust and least available delta-v. The trajectory accelerates, optionally coasts and brakes to the target's velocity. It does not stop the disabled fleet or declare arrival at the destination system.

For acceleration `a`, target velocity `v`, initial rescuer velocity `u` and starting separation `d`, a candidate peak velocity `p` closes this distance during its powered phases:

`powered relative distance = ((p - v)^2 - (v - u)^2 / 2) / a`

The peak is capped by available delta-v and the existing cruise-speed limit. Any remaining separation requires a coast at relative speed `p - v`. A nonpositive closing speed or insufficient acceleration and braking propellant rejects the order. Planning is analytic and does not loop through every travel day.

Main propellant burns only during actual powered phases. Required drive reactor material is committed at departure. Planned payload is excluded from usable tank propellant, electrical fuel and reactor feed. Its physical mass and cargo preservation load remain aboard throughout the approach. A one-milligram main propellant remainder protects tank donations against floating-point subtraction at phase boundaries.

The rescuer must supply essential services, cargo preservation and each powered drive phase, the remainder of the contact day and a further 48 hours of essential and cargo demand. Crossing electricity assumes darkness. This reserve covers the initial rescue contact; it does not certify a return or onward trip.

## Contact and transfer

- Contact requires the same directed corridor and owner, positions within 1 meter and velocities within 0.001 m/s. Both fleets must be coasting on interrupted itineraries.
- At a powered intercept's completion, the rescuer coasts at the matched velocity for the remainder of the day. Contact is checked after all fleets' motion updates, so list order does not change docking behavior.
- The requested supply transfer runs once and is atomic. Missing ingredients, changed ownership, moved ships, a maneuvering target or insufficient receiver capacity prevent it.
- Cargo can deteriorate during the approach. The request earmarks a quantity for planning but does not move stock into an invulnerable compartment. Changed stock is checked on contact.
- The completed request is cleared even if its transfer fails. Existing supply controls can stage additional transfers while position and velocity remain matched. Read the receiver's actual stores after the tick to confirm delivery.
- Resupply leaves the original target itinerary interrupted. A separate recovery command must check the receiver's current motion, propulsion fuel and electrical budget before resuming travel. Maneuvering away ends contact.

## Failure and persistence

An electrical failure stops powered motion at the actual failure time and preserves the resulting drift. It cancels the pending automatic supply transfer. A new rescue order can be planned from the resulting position if it satisfies the same forward interception rules. Missed intercepts do not teleport supplies or stop either fleet.

Save version 31 preserves matching velocity and the pending rescue request within the existing flight trajectory. Older ordinary recovery trajectories default to zero final velocity with no rescue request. Reloading continues actual fuel consumption, drift and the single contact transfer.

## Outcome feedback and recovery readiness

Save version 32 also preserves the latest rescue status on each involved ship's power state. Starting a new request records approaching status for the donor and receiver. Completion records delivered supplies, missed contact or rejected transfer. An intercept power failure records a failed rescue on both ships. Status updates contain the request, rescuer fleet ID and simulation turn; they do not alter physical reserves. Older saves default to no status.

Fleet ship cards show this status after the trajectory is cleared. An active approach includes its planned remaining time. Delivered status confirms the atomic transfer and does not certify onward endurance. The latest status persists through power accounting, array changes, purchases, charging, cargo deterioration and subsequent travel. A later rescue involving the ship replaces its previous status; this is not a full rescue history.

The interrupted fleet's recovery card reports readiness from the same gates used by its command. Blockers identify missing main propellant, insufficient braking reserves, missing compatible reactor feed, unavailable drive electricity, insufficient remaining braking distance, destination overshoot or inadequate electrical endurance for travel and the 48-hour reserve. Checks use every fleet ship, so rescuing one ship does not automatically make the entire fleet ready.

The repeatable [rescue balance report](RescueBalanceReport.md) compares catalog interception time, actual main and electrical fuel use, committed drive reactor feed, passenger survival at outcome and receiver readiness. It includes nutrition and cargo deterioration in passenger ticks. Approaches over 180 days remain plan-only and the report does not simulate return travel or survival after contact.

## Tuning and verification

Tests cover velocity matching, physical fuel consumption, separate recovery after delivery, tank payload reservation, inaccessible targets, missing supplies, changed target motion, power failure, fleet-order independence and pending-order save/load.

Further work should consider realistic corridor coordinates, rendezvous maneuver tolerances, docking hardware and duration, shared electrical connections, passenger evacuation, return planning and explicit receipts for rejected launch commands. Current trajectories keep their initial acceleration despite fuel use and cargo loss. Rescue does not guarantee passenger survival or cargo preservation during a long approach.
