# Active backhaul recovery report

The lunar trader now uses protected fuel to deliver the final Earth leg itself while the separate handler stages. Both routes stay active through stock, price and buyer-cash shocks. A fall in the final price during the committed flights does not leave the paid aluminum waiting aboard the trader.

| Campaign day | Event |
|---|---|
| 66 | Both routes activated once after paid supply flights |
| 69 | Routes wait with no lunar market stock |
| 72 | Released lunar stock remains unsold at zero return margin |
| 82 | Lunar depot sale completes while the final buyer has no cash |
| 85 | Restored cash with an unfavorable final price still blocks new loads |
| 87 | Trader directly completes the 2,500 kg final delivery while the handler reaches the depot; the buyer purchases the committed load after its price falls |
| 207 | Both routes remain active and stationary; live prices reject further loads |

`ActiveBackhaulRecoveryAuditTest` keeps both routes active without resets. A separate RP-1 restocker supplies finite fuel and goods. Private lunar stock releases conserve a tracked reserve hub and buyer-cash shocks transfer money to and from the port operator. Protected destination fuel is also paid and tracked. No stock or cash is created.

Waiting preserves public stock, accounts, owner cash, cargo and working fuel. Reload checks cover waits, paid supply flights and the handler's empty climb. Total aluminum, each fuel material and total money reconcile. The previous two-handoff fixture relied on the lunar trader being unable to fund the final leg; its old timing is superseded by this valid direct delivery.

Run `mvn test` with JDK 27. Generated output: `engine/target/active-backhaul-recovery-report.md`. Production, surface lift, capital, overhead and autonomous upstream procurement remain excluded.
