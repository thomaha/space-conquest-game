package com.spaceconquest.engine.industry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tick-local operating cash ledger for state-owned facilities. */
public final class IndustryOperatingLedger {
    private final Map<String, IndustryAccount> accounts = new LinkedHashMap<>();

    public IndustryOperatingLedger(List<IndustryAccount> previous) {
        for (IndustryAccount account : previous) accounts.put(account.facilityId(), account);
    }

    public double balance(IndustrialFacility facility) {
        return Math.max(0.0, accounts.getOrDefault(facility.id(), IndustryAccount.empty(facility.id()))
                .operatingCashCredits());
    }

    public void change(IndustrialFacility facility, double delta) {
        IndustryAccount account = accounts.getOrDefault(facility.id(), IndustryAccount.empty(facility.id()));
        accounts.put(facility.id(), account.withOperatingCash(account.operatingCashCredits() + delta));
    }

    public IndustryAccount withDay(IndustryAccount day) {
        IndustryAccount current = accounts.getOrDefault(day.facilityId(), IndustryAccount.empty(day.facilityId()));
        IndustryAccount updated = current.withDailyTransactions(day);
        accounts.put(day.facilityId(), updated);
        return updated;
    }

    public List<IndustryAccount> snapshot() {
        return List.copyOf(accounts.values());
    }
}
