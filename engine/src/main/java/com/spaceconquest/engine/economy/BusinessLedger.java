package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.market.SupplyContract;
import java.util.*;

/** Immutable cash accounts, unpaid service bills and prefunded procurement retained in snapshots and saves. */
public record BusinessLedger(Map<String, BusinessAccount> accounts, Map<String, Double> unpaidExpenses,
                             double serviceClearingCredits, List<SupplyContract> contracts, long lastClosedTurn,
                             Map<String, BusinessOperatingPlan.Decision> operatingDecisions) {
    public BusinessLedger {
        accounts = accounts == null ? Map.of() : Map.copyOf(accounts);
        unpaidExpenses = unpaidExpenses == null ? Map.of() : Map.copyOf(unpaidExpenses);
        contracts = contracts == null ? List.of() : List.copyOf(contracts);
        operatingDecisions = operatingDecisions == null ? Map.of() : Map.copyOf(operatingDecisions);
        if (!Double.isFinite(serviceClearingCredits) || serviceClearingCredits < 0 || lastClosedTurn < -1)
            throw new IllegalArgumentException("Invalid service clearing balance");
        unpaidExpenses.values().forEach(value -> {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid unpaid service bill");
        });
        if (contracts.stream().map(SupplyContract::id).distinct().count() != contracts.size())
            throw new IllegalArgumentException("Duplicate supply contract");
    }
    public BusinessLedger(Map<String, BusinessAccount> accounts, Map<String, Double> unpaidExpenses,
                          double serviceClearingCredits, List<SupplyContract> contracts, long lastClosedTurn) {
        this(accounts, unpaidExpenses, serviceClearingCredits, contracts, lastClosedTurn, Map.of());
    }
    public static BusinessLedger empty() { return new BusinessLedger(Map.of(), Map.of(), 0, List.of(), -1); }
    public BusinessAccount account(String owner) { return accounts.getOrDefault(owner, BusinessAccount.empty()); }
    public BusinessLedger withContracts(List<SupplyContract> value) {
        return new BusinessLedger(accounts, unpaidExpenses, serviceClearingCredits, value, lastClosedTurn, operatingDecisions);
    }
    public BusinessLedger withAccount(String owner, BusinessAccount account) {
        var updated = new HashMap<>(accounts); updated.put(owner, account);
        return new BusinessLedger(updated, unpaidExpenses, serviceClearingCredits, contracts, lastClosedTurn, operatingDecisions);
    }
    public BusinessLedger withDecisions(Map<String, BusinessOperatingPlan.Decision> value) {
        return new BusinessLedger(accounts, unpaidExpenses, serviceClearingCredits, contracts, lastClosedTurn, value);
    }
    public double escrowCredits() { return contracts.stream().mapToDouble(SupplyContract::escrowCredits).sum(); }
}
