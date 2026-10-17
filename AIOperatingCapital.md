# AI operating-capital recovery

Empire AI can transfer existing treasury credits to a viable public factory or power plant whose operating account cannot cover its application-defined reserve threshold. This restores cash for later payroll and purchases. The transfer itself creates no workers, fuel, inputs, electricity or output.

## Eligibility and blockers

FacilityOperatingReadiness is an immutable snapshot query. It distinguishes cash shortages from missing reserve policy, public ownership or territorial control, construction, research, manufacturing capacity, trained workers, local hubs, inputs or fuel, suitable environment, deposits, physical electricity and useful demand. Factories require a funded buyer and stock or storage room for their outputs. Plants require demand beyond other generation or an active brownout.

Potential staffing combines the facility's last paid headcount with unemployed workers of its profession on that body, capped by its allocation and physical adult population. This allows an empty cash account to be distinguished from a lack of potential workers. Actual next-day household payroll still determines hires. New profession training and future competing vacancies are not forecast.

The quote requires enough local inputs or fuel for the projected operating day. Factory power is checked against measured generation, battery storage and other recorded load. Prior paid electricity is used when identifying the facility's own recorded load; unpaid job slots do not reserve generation. These are current-condition checks rather than guarantees about subsequent days.

## Application reserve values

Forty-two application definitions currently have an `operatingReserve` policy in `technologies.json`. Unsupported recipes and applications without a policy receive no automatic recovery. The provisional policy targets three operating days, refills below one day, caps the resulting facility cash balance at 50,000 credits and leaves at least 10,000 credits in the empire treasury. Public maintenance remains 80 credits per tier per day, now read from the policy where present. These values are tunable per application; no full economy balance pass was performed.

Daily estimates include potential wages, recipe inputs or reactor fuel, factory electricity and public maintenance. Research modifiers and facility tier affect the operational quantities. A cap that cannot finance even the projected day blocks recovery. The treasury must afford the full transfer while retaining its floor. Negative facility cash is repaid as part of restoring the target balance.

## Queued transfers and persistence

EmpireOperatingCapitalPlanner proposes one recovery per observed turn and gives generation priority. FundAIFacilityOperationsCommand rechecks current readiness and transfers the current quoted amount from the empire treasury to the facility account. Repeated commands see the restored reserve and reject without another payment. Competing requests recheck remaining treasury funds. No caller-supplied transfer amount bypasses the application policy.

The account records the actual transfer in its existing daily subsidy receipt. Its opt-in local subsidy setting is preserved; this command does not enable municipal subsidies. Ordinary daily processing resets the daily receipt as before. Account cash and receipts use the existing version 1 save fields, so reload does not repeat a completed transfer. Controller scheduling memory is recreated on load, while current account balances remain authoritative.

Recovery takes priority over new power investment. A positive funding receipt for a plant with no paid workers or generation also defers additional local power construction until daily processing gives it a chance to work. Subsidies to already operating plants do not prevent needed capacity investment. Existing budget subsidies and player spending remain separate and unchanged.

## Bounded verification and remaining limits

AIOperatingCapitalTest follows an empty plant account through an exact treasury transfer, duplicate rejection, disk reload, paid workers, actual fuel purchases and generation. The factory path pays wages, grid electricity and inputs before producing and selling ammunition. Other checks cover operation blockers, policy edits, negative cash, competing treasury requests, load-shed demand and controller integration.

This is operational recovery rather than a profitability forecast. A useful public service may still run at a loss and later need another permitted transfer. Current stock, employment, prices and power can change after the command executes. Corporate facilities, Hive allocation, orbital industry, unsupported generic facilities and broader budget planning remain outside this recovery path.
