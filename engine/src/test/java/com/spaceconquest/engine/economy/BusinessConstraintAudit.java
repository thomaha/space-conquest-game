package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Audit-only daily receipts; harvest windows do not pretend to be inventory-lot cost accounting. */
final class BusinessConstraintAudit {
    private final Map<String, Integer> constraints = new TreeMap<>();
    private final StringBuilder trace = new StringBuilder("turn\tfacility\tkind\tresource\trequired\tavailable\n");
    private final StringBuilder seasons = new StringBuilder("| Days | Harvest kg | Sales | Inputs | Wages | Electricity | Maintenance | Tax paid | Capital allowance | Reserve transfer | After-tax result |\n")
            .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    private int first = 1;
    private double sales, inputs, wages, power, maintenance, taxes, allowance, reserves, totalResult, totalTax;
    void record(GameState state) {
        state.businessLedger().operatingDecisions().forEach((id, decision) -> {
            for (var constraint : decision.constraints()) count(state, id, constraint);
            var receipt = state.industryAccounts().stream().filter(a -> a.facilityId().equals(id)).findFirst().orElse(null);
            if (receipt != null && receipt.paidWorkers() < decision.requestedWorkers())
                count(state, id, new ProductionConstraints.Constraint("Settled workforce", "workers", decision.requestedWorkers(), receipt.paidWorkers()));
            if (!decision.operate()) constraints.merge(id + " / decision: " + decision.reason(), 1, Integer::sum);
        });
        var receipt = state.industryAccounts().stream().filter(a -> a.facilityId().equals("producer")).findFirst().orElseThrow();
        var book = state.businessLedger().account("producer-owner");
        sales += receipt.salesCredits(); inputs += receipt.inputCostsCredits(); wages += receipt.wageCostsCredits();
        power += receipt.powerCostsCredits(); maintenance += receipt.maintenanceCostsCredits();
        double tax = state.corporateTaxAccounts().stream().filter(a -> a.corporationId().equals("producer-owner"))
                .mapToDouble(CorporateTaxAccount::paidTaxCredits).sum();
        taxes += tax; totalTax += tax; reserves += book.reserveContributionToday();
        allowance += BusinessAssets.ownerValue(state, "producer-owner") / SupplyPricingPolicy.current().capitalRecoveryDays();
        totalResult += receipt.realizedResultCredits();
        assertEquals(totalResult, book.cumulativeOperatingResultCredits(), Math.max(1e-6, Math.abs(totalResult) * 1e-9));
        assertEquals(totalTax, book.cumulativeTaxPaidCredits(), Math.max(1e-6, totalTax * 1e-9));
        double harvested = receipt.producedKg().values().stream().mapToDouble(Double::doubleValue).sum();
        if (harvested > 0) close((int) state.turn(), harvested);
    }
    private void count(GameState state, String id, ProductionConstraints.Constraint c) {
        constraints.merge(id + " / " + c.kind() + " / " + c.resource(), 1, Integer::sum);
        trace.append(state.turn()).append('\t').append(id).append('\t').append(c.kind()).append('\t').append(c.resource())
                .append('\t').append(c.required()).append('\t').append(c.available()).append('\n');
    }
    private void close(int day, double harvest) {
        double afterTax = sales - inputs - wages - power - maintenance - taxes;
        seasons.append(String.format(Locale.ROOT, "| %d–%d | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f |\n",
                first, day, harvest, sales, inputs, wages, power, maintenance, taxes, allowance, reserves, afterTax));
        first = day + 1; sales = inputs = wages = power = maintenance = taxes = allowance = reserves = 0;
    }
    String report(int lastDay) {
        if (first <= lastDay) close(lastDay, 0);
        var result = new StringBuilder("### Capacity constraints and operating decisions\n\n")
                .append("Counts can overlap. Material, power and cash constraints compare pre-hiring availability with allocated capacity; they are not exclusive causes of a stopped day. Workforce compares the plan with actual paid workers. Generator fuel is a rated-capacity check and may be sufficient for curtailed dispatch.\n\n")
                .append("| Facility / observation | Days |\n|---|---:|\n");
        constraints.forEach((key, days) -> result.append("| ").append(key.replace("|", "/")).append(" | ").append(days).append(" |\n"));
        return result.append("\n### Farm cash accounting between harvests\n\n")
                .append("Every day is included, including pauses. Zero harvest denotes an unfinished closing window. Sales can include older inventory, so these are calendar-window cash receipts rather than product-lot costs. After-tax result excludes capital allowances and reserve transfers; reserves remain assets. The allowance shows the policy's full daily replacement requirement before available-profit limits. No coefficient is adjusted to force a profit.\n\n")
                .append(seasons).append('\n').toString();
    }
    String trace() { return trace.toString(); }
}
