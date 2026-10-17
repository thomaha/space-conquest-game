# Ship cancellation, retirement and salvage

## Cancelling existing-ship work

CancelShipYardWorkCommand cancels an owned refit or retirement order at the command tick. The ship keeps its current model, damage and remaining stores. Current refit and dismantling work installs or removes the configuration only at final completion, so cancellation releases the reservation without restoring an earlier ship snapshot.

Consumed inputs and paid work remain spent. Earned service fees remain with the provider. There is no cash or material refund. The saved yard history records cancellation, consumed quantities, paid work and earned fees. A completed or cancelled order cannot be cancelled again. A missing ship can have its owned blocked order cleared without creating a replacement ship.

## Paid retirement

ShipRetirementQuote previews dismantling at the ship's current accessible yard. QueueShipRetirementCommand reserves the actual ship using optional ShipRetirementPlan metadata in the ordinary construction queue. Dismantling shares finite paid yard capacity with construction and refits. Corporate customers pay their own service fees through the existing contract installments. Expected salvage proceeds cannot advance-fund those fees.

The ship remains vulnerable and unavailable for travel, repair, supply and weapon firing during work. Partial progress and frozen recovery rules survive version 1 saves. Lost access, unavailable workforce, unfunded fees or an unavailable market pause work. The ship is removed only after paid completion and an atomic funded recovery sale. Corporate ownership lists and completed replacement links are cleaned up; other ships and unrelated fleets are retained.

Passengers must disembark. Active missions, committed trade, scheduled supply transfers, commissioning deployment and carried strike craft must finish or be released first. Prepaid return fuel must be claimed at its reserved port before retirement; automatic release or transfer of remote private reservations remains future work. Wreck salvage is separate from retirement of a surviving ship.

## Recovery and accounting

Hull applications define `retirementTechnology`: dismantling work fraction plus frame, armor and component recovery fractions. Applications without separate rules inherit the baseline hull application's values. Required work uses the captured assembly receipt. Queued orders freeze these recovery settings.

Frame and armor recovery follow current hull condition. Each installation uses its captured production bill and current component condition. Materials already aboard as cargo, working main propellant, generator fuel or dedicated supplies are recovered at their remaining quantities. Battery energy and shield charge dissipate during dismantling; they create no material or credit output. Damage received after the quote reduces actual recovery.

Recovered materials enter the local market's actual inventory on completion. The market must have storage room, valid prices and enough real trading cash to buy the entire recovery at the ordinary wholesale share. Local tariffs go to the controlling empire and the remaining proceeds go to the ship owner. Service fees remain separate. Prices, funds, capacity and live stores are rechecked during work; no future inventory or speculative credits enter the live state. Saved receipts identify actual recovered quantities, buyer payment, owner proceeds and tariff.

This first workflow sells recovery through a funded local market. Direct transfer into private storage, reusable removed components, corporate asset book values and dedicated salvage profit assessment remain separate future work. Retirement does not provide free parts for another ship.

## AI decisions and fleet coverage

ShipLifecyclePlanner considers retirement only for a saved completed replacement link. RequestAIShipRetirementCommand requires the replacement to have the same owner and role, remain operational in the same system, finish commissioning and meet the condition threshold in `ship_retirement_policy.json`. Propulsion, electrical endurance and installed weapon readiness are checked with existing captured capabilities.

For military ships, removing the original may not increase existing defense, reinforcement or escort shortages. The ordinary paid retirement command is used only after these checks pass at the command tick. The replacement is excluded from competing AI lifecycle work while dismantling is pending. Its normal missions remain subject to coverage review.

AI retirement orders save the replacement identity and condition threshold. Every installment pauses if that replacement becomes physically unavailable. Subsequent AI reviews cancel retirement when the replacement is lost, becomes unready, leaves the system or removing the original would increase a coverage shortage. Cancellation preserves spent work and restores the original ship's availability rather than refunding prior payments.

Completed replacement links suppress duplicate lifecycle purchases without suppressing ordinary service for an original ship that fleet coverage still needs. Active paid work and pending replacement builds retain their existing service reservations.

## User interface and verification

The fleet view's Ship upgrades and retirement panel shows saved work, spent materials, earned fees and cancellation terms. Its retirement preview shows recovered quantities, market proceeds, tariffs, service fees and estimated queue time. Quotes run in the background with selection and snapshot guards. Start and cancellation actions use tracked commands processed at the simulation tick.

ShipRetirementTest checks no-refund cancellation through reload, replay rejection, condition-sensitive recovery, live damage after quotes, remaining stores, market cash and capacity, passengers, prepaid fuel, corporate fees and tariffs, saved partial retirement, replacement readiness, coverage preservation and cancellation after replacement loss or changed wartime needs. ShipRetirementCardTest checks rendering, stale results and tracked cancellation. Shared yard, construction priority, deployment, custom hull, assembly, refit and lifecycle regressions are included in scoped verification. This is lifecycle accounting verification rather than a balance pass.

The final scoped regression passed 112 tests across 19 engine, control and frontend suites with no failures or errors. The full suite and a full balance pass were not run.
