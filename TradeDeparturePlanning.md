# Trade departure and return fuel

## Funded physical plans

Automated shipments forecast the loaded departure and empty return before buying cargo. Same-system orbital trade prefers a funded phasing approach before the faster station rendezvous search. Bounded reserve candidates in `trade_departure_fuel_policy.json` let planning protect onward propellant when the fastest approach would consume it. The chosen saved trajectory carries its protected fuel budget. The live fleet keeps its normal fuel policy.

Return checks use the future arrival turn and actual fuel, drive feed and electrical consumption. Accessible current destination fuel is bought into the existing private reservation when needed. No remote refueling or virtual forecast stock is committed.

A fuel shipment can also establish its own return supply. This fallback requires a delivered working-fuel ingredient and a discarded execution of the ordinary sale and paid refueling workflow. The buyer must fund the complete delivery and actual working stores must support the return. Departure commits only the real cargo purchase and flight; destination inventory changes on actual delivery. Prices, buyer funds and storage can change during travel, so actual delivery and refueling revalidate their usual gates. This conditional delivery forecast does not provide the guarantee of an already paid private fuel reservation.

Profit estimates use the same discarded delivery and paid return preparation. Consumed fuel is priced as a replacement cost even when it was carried before dispatch. A feasible physical route may still be unprofitable.

## Limited audits

LocalTradeDistanceAuditTest uses explicit fitted hull capacity and catalog long-range hydrolox equipment for its feasible small mixed shipment. Markets quote liquid hydrogen separately from gaseous hydrogen. Movement advances the actual campaign turn and uses the same economical plan as trade execution.

LunarTradeAuditTest derives elapsed days from the actual paid trajectory. DepotReplenishmentAuditTest uses a captured fitted supplier with a catalog long-range tank and finite real source fuel. Longer lunar and backhaul audits exercise repeated phase-dependent approaches. Explicit audit dispatch waits real engine days when no funded round trip is currently available instead of assuming identical docking geometry at every epoch. The deliberately stranded one-way case remains an explicit one-way preview.

The active backhaul fixture uses four catalog main tanks and explicit fitted hull capacity for its larger tanker payload. Its paid recovery scenario passes without changing drive or cargo application values. Comparative price grids and profitability thresholds remain separate balance probes.

The three-cycle lunar fixture seeds 10,000,000 kg hydrogen and 20,000 kg uranium in a 12,000,000 kg depot. Its former 5,000,000 kg hydrogen stock was exhausted before the required 90 tanker trips under captured hull mass and paid docking. Starting stocks remain finite and every acquisition, retained store and consumed kilogram enters the conservation checks.

The focused fuel-delivery check follows a fission tanker through paid cargo delivery, refueling, physical return and save/reload. It checks unchanged normal fuel policy, no invented destination stock, buyer funding and conserved money and fuel. These checks establish feasibility and transaction behavior rather than weapon or ship balance.

The scoped regression and subsequent depot checks pass. The three-cycle lunar scenario completes 117 paid round trips with finite stocks, reload and conservation. Its report retains the measured operating loss; no application values were adjusted to force profitability. The full suite and comparative price-grid audits remain outside this verification.
