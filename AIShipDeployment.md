# AI build missions and deployment

## Saved build purpose

AI ship orders now retain immutable ShipDeploymentIntent metadata for defense, reinforcement, escort, facility supply or trade. Ordinary AI builds capture a concrete target when the order is accepted. Facility transport construction preserves its original destination, material and first observed shortage turn. AI-managed queue ranking fills missing intent on affiliated orders without replacing existing purpose. New researched revisions and capability upgrades preserve the same handoff path.

ShipConstructionProcessor keeps the intent through partial work and transfers it to ShipyardLedger.deployments under the stable commissioned ship ID. This commissioning handoff map is separate from accounting totals inside the ledger. It preserves pending preparation through version 1 saves without changing the engine loop or fleet record. Successful mission assignment clears the pending entry; the actual guard, escort, fleet membership or trade route then persists normally.

## Paid preparation and assignment

AIShipDeploymentPlanner submits bounded DeployAIShipCommand requests before general fleet and logistics planning. Pending ships are reserved from generic trade, supply and combat service assignment and count as committed transport capacity, preventing redundant cargo construction during preparation. Selection rotates through pending entries over successive turns so stalled first entries cannot permanently hide later ships.

Military preparation uses ordinary ServiceAICombatFleetCommand for finite ammunition, generator stores, grid charging and repair. Propellant purchases use RefuelShipCommand with a configurable tank reserve fraction. Weapon requirements remain captured application capabilities. No readiness, fuel or ammunition is granted by commissioning intent. A supply shortage leaves the ship reserved with an explanatory status.

LoadDockedCargoCommand now pays for stock already at the ship's operational orbital hub and transfers only the purchased mass within cargo capacity. AmmunitionResupplyQuote prefers that local stock when available, otherwise retaining its existing surface purchase and paid orbital lift path. This closes the orbital-yard ammunition handoff gap without manufacturing free rounds.

Ready defense ships receive an ordinary owned-site guard order. Reinforcements merge into their validated owned target fleet and escorts use EscortFleetCommand. Same-system repositioning uses ordinary paid local travel, checks weapon readiness after the quoted journey and retains pending intent until arrival.

## Deployment across systems

StrategicDeploymentPlanner ranks owned operational stations and available friendly fleets across controlled systems. Only detected wartime enemies contribute to threat demand. Friendly combat ships and already committed build or commissioning missions reduce demand; distance and a minimum priority advantage discourage unnecessary remote assignments. These ship-count scores are planning estimates rather than tactical combat forecasts. Scores are cached per candidate system and target validation has a configurable preview bound. When a target disappears after arrival, the planner can select an owned defense site or an available friendly fleet again.

CrossSystemDeployment uses the ordinary MoveFleetCommand departure quote and a discarded FleetDeploymentArrival projection. It checks local departure, crossing duration, paid launch fees, carried drive fuel, electrical stores, destination approach, remaining propellant and weapon readiness. The existing essential-load arrival reserve also applies. Stronger existing fleet fuel policies remain protected. When additional stores are needed, ordinary refuel, power resupply and grid charging commands run on a discarded snapshot; their purchases are committed only if the complete travel and arrival quote then succeeds and the treasury floor remains funded.

ShipDeploymentIntent saves the AI's crossing destination and arrival checkpoint in the existing ledger. Actual travel consumes the ordinary physical and electrical stores. Interrupted travel retains the mission and requires the existing travel recovery process. Reload preserves the checkpoint. The arrival command revalidates current ownership and availability before local approach, merge or escort assignment. An explicit replacement crossing releases the commissioning reservation. Cargo ships continue to use their existing paid logistics routes and protected return planning.

Facility supply executes the existing paid shipment or paid repositioning path, including buyer funds, cargo, operating stores and protected return. Corporate carriers retain their owner and payer and the existing profit gate. Trading deployment selects an ordinary roaming trade departure on an isolated snapshot and commits its actual paid cargo and travel state only when funded and useful. It does not merely create an empty speculative route.

## Changed needs and control

Every deployment request rechecks ownership, society eligibility, movement, target availability and current demand. A vanished friendly fleet target falls back to an owned defense site. Resolved facility demand selects a new current shortage or a funded trade purpose. Retargeted supply intent starts a fresh observation period. Explicit guard, escort, intercept, interstellar or committed trade orders take precedence and release the commissioning reservation. Destroyed or missing commissioned ships release their pending entry.

deployment_policy.json controls request and target preview bounds, deployment scoring weights, the remote priority margin, maximum journey duration, propellant reserve fractions and the treasury floor. Weapon capability and balance values remain in technological applications captured by ship designs. Existing logistics and application data continue to control fees, fuel, power and magazine quantities. The shipyard economy card renders pending purpose and preparation status from the immutable snapshot; queue rows show planned deployment purpose.

## Limited verification

AIShipDeploymentTest checks paid military preparation, ammunition and propellant before guard assignment, save/reload during preparation, waiting without available stock, changed fleet targets, explicit order precedence and ready reinforcement and escort merges without duplicate ships. Public and corporate facility ships survive ordinary construction ticks and disk reload, depart once with paid cargo and restore actual production before completing their protected return. A resolved supply need retargets to an actual paid trading departure.

Transport and shared-yard lifecycle tests now exercise this commissioning handoff directly. ShipyardEconomyCardTest verifies pending deployment visibility and foreign filtering without mutation. These are limited lifecycle checks rather than fleet or weapon balancing.

AIInterstellarDeploymentTest checks detected frontier demand, committed reinforcement accounting, paid finite generator purchases, arrival reserve rejection without free stores, a normal warp crossing through disk reload and actual reinforcement, a vanished arrival target, explicit replacement travel and interrupted sublight travel with a physical propellant budget. Existing interstellar, fuel policy and paused travel tests cover the underlying travel rules.
