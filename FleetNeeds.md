# Fleet needs, research and production

## Mission shortages

EmpireFleetNeeds assesses defense and reinforcement coverage in controlled systems with operational public stations or available friendly combat fleets. Configurable defense targets, wartime reserves and detected hostile ship counts determine the desired coverage. Unescorted available public cargo convoys create separate escort needs. Existing armed living ships, queued mission builds, commissioning handoffs and ordinary incoming crossings reduce demand. A pending ship is counted through its mission rather than again at its departure site. Destroyed ships release coverage.

These are ship-count planning estimates, not combat strength or weapon balance predictions. Damaged but living armed ships remain assets for repair. Unarmed military templates do not provide combat coverage. Escort capacity is read from the real combined convoy's saved escort membership. Existing corporate logistics, service yards and society permissions continue through their current paths; public military missions do not borrow corporate designs or funds.

## Research and new designs

EmpireResearchNeeds uses current mission shortages to prioritize offensive weapon applications and applications linked to owned military templates. ResearchOpportunities retains the relevant application goal while selecting missing foundational prerequisites. Existing power and material shortages retain their higher priority. Saved research decisions explain the concrete fleet need. Research still requires actual paid scientists and existing prerequisite validation.

Performance refinement checks relevant ship component complexity against available domestic manufacturing capability as well as existing facility capacity. Miniaturization remains available when a performance increase would exceed the yard limit. Research does not retrofit existing ships or alter pending orders.

FleetNeedsConstructionPlanner considers owned military templates and controlled construction systems, preferring a yard in the mission's system. RequestAIFleetNeedBuildCommand rechecks the exact target shortage on the command tick. It captures current application development in a new design when needed. An unarmed template can receive one researched offensive module; an escort mission creates an escort-role variant. The existing blueprint factory validates research, physical configuration, power and manufacturing capability. Hardware capabilities and all weapon balance values come from technological applications.

Current design selection is deliberately limited: it reuses owned templates and considers offensive modules in stable catalog ID order. It does not synthesize a new fleet architecture or optimize weapon combinations. A design that does not pass blueprint validation waits for a compatible template or development choice.

## Paid construction and deployment

The command quotes the complete material bill, shared-yard service fees and treasury reserve before accepting an order. It registers a new design only with an accepted ordinary QueueShipBuildCommand. The order captures its specific defense, reinforcement or escort mission. Duplicate queued requests revalidate and cannot create another order. One pending public order per empire limits simultaneous commitments; actual construction installments remain paid and can pause if resources later change.

Saved build purpose follows existing commissioning and deployment handling. Paid weapon service and propellant preparation precede guard or escort assignment, local movement or interstellar deployment. Ships already under construction or travelling toward a need suppress redundant production. Ownership and target changes are revalidated by both build and deployment commands. No new save format or engine loop fields are introduced.

fleet_needs_policy.json controls coverage targets, the defense/reinforcement cap, build preview count and treasury reserve. deployment_policy.json continues to control deployment scoring and travel limits. These planning policies do not replace application-defined weapon capabilities.

## Limited verification

FleetNeedsLifecycleTest seeds prior research progress and runs the final installment with actual paid scientists. It follows an identified shortage through application discovery, a validated armed design, paid assembly, disk reload and paid guard deployment while preserving an existing ship. It separately verifies escort-role construction and real convoy membership, duplicate rejection, unavailable stock, the treasury floor, vanished demand, incoming crossing accounting and destruction releasing coverage. Existing research revision, construction, logistics and interstellar deployment checks cover their shared paths. These checks establish the lifecycle without attempting full fleet balance.
