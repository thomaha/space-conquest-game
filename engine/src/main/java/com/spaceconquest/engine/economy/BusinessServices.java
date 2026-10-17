package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import java.util.*;

/** Pays generic service invoices into a separate clearing balance and carries genuinely unfunded bills. */
public final class BusinessServices {
    public record Result(GameState state, Map<String, Double> paidByOwner) {}
    private BusinessServices() {}
    public static String ownerPrefix(String owner) { return owner.length() + ":" + owner + ":"; }
    public static String facilityBill(String owner, String asset) { return ownerPrefix(owner) + "facility:" + asset; }
    public static String shipBill(String owner, String asset) { return ownerPrefix(owner) + "ship:" + asset; }
    public static Result settle(GameState state) {
        var due = new HashMap<>(state.businessLedger().unpaidExpenses());
        var paidByOwner = new HashMap<String, Double>(); var transport = new HashMap<String, Double>();
        var activeBills = new HashSet<String>();
        var accounts = new LinkedHashMap<String, IndustryAccount>();
        state.industryAccounts().forEach(a -> accounts.put(a.facilityId(), a));
        double clearing = state.businessLedger().serviceClearingCredits();
        for (var facility : state.industrialFacilities()) {
            if (IndustrialFacility.HIVE_GRID.equals(facility.ownershipType()) || facility.tier() <= 0) continue;
            var account = accounts.getOrDefault(facility.id(), IndustryAccount.empty(facility.id()));
            double alreadyPaid = account.maintenanceCostsCredits();
            String bill = facilityBill(facility.ownerEntityId(), facility.id()); activeBills.add(bill);
            double invoice = due.getOrDefault(bill, 0.0)
                    + Math.max(0, FacilitySupplyPricing.declaredMaintenance(facility) - alreadyPaid);
            boolean privateOwner = IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType());
            double available = privateOwner ? EconomicOwnership.cash(state, facility.ownerEntityId()) : Math.max(0, account.operatingCashCredits());
            double payment = Math.min(available, invoice);
            if (privateOwner) state = BusinessCash.change(state, facility.ownerEntityId(), -payment);
            else account = account.withOperatingCash(account.operatingCashCredits() - payment);
            accounts.put(facility.id(), account.withMaintenanceCost(payment));
            due.put(bill, Math.max(0, invoice - payment));
            clearing += alreadyPaid + payment;
            paidByOwner.merge(facility.ownerEntityId(), alreadyPaid + payment, Double::sum);
        }
        var policy = SupplyPricingPolicy.current();
        for (var fleet : state.fleets()) {
            boolean working = fleet.location().inTransit() || fleet.hasInterstellarOrder() || state.tradeRoutes().stream()
                    .anyMatch(r -> r.carrierCommitted() && fleet.ships().stream().anyMatch(s -> r.assignedFreighterIds().contains(s.id())));
            for (var ship : fleet.ships()) {
                if (!BusinessAssets.carrier(state, ship)) continue;
                String bill = shipBill(fleet.ownerEntityId(), ship.id()); activeBills.add(bill);
                double invoice = due.getOrDefault(bill, 0.0) + BusinessAssets.shipValue(state, fleet, ship)
                        * policy.transportMaintenanceDailyFraction() + (working ? policy.transportOperatingCreditsPerDay() : 0);
                double payment = Math.min(Math.max(0, EconomicOwnership.cash(state, fleet.ownerEntityId())), invoice);
                state = BusinessCash.change(state, fleet.ownerEntityId(), -payment);
                due.put(bill, Math.max(0, invoice - payment));
                clearing += payment;
                paidByOwner.merge(fleet.ownerEntityId(), payment, Double::sum);
                transport.merge(fleet.ownerEntityId(), payment, Double::sum);
            }
        }
        due.entrySet().removeIf(e -> e.getValue() <= 1e-9);
        var books = new HashMap<>(state.businessLedger().accounts());
        var owners = new HashSet<>(books.keySet()); state.empires().forEach(e -> owners.add(e.id())); state.corporations().forEach(c -> owners.add(c.id()));
        // A sale or removal does not transfer the former owner's old invoices to the new owner.
        for (String owner : new TreeSet<>(owners)) for (String bill : new TreeSet<>(due.keySet())) {
            if (activeBills.contains(bill) || !bill.startsWith(ownerPrefix(owner))) continue;
            double payment = Math.min(Math.max(0, EconomicOwnership.cash(state, owner)), due.get(bill));
            state = BusinessCash.change(state, owner, -payment); clearing += payment;
            due.put(bill, due.get(bill) - payment);
            paidByOwner.merge(owner, payment, Double::sum); transport.merge(owner, payment, Double::sum);
        }
        due.entrySet().removeIf(e -> e.getValue() <= 1e-9);
        for (String owner : owners) books.put(owner, books.getOrDefault(owner, BusinessAccount.empty())
                .withTransportPayment(transport.getOrDefault(owner, 0.0)));
        return new Result(state.toBuilder().industryAccounts(List.copyOf(accounts.values())).businessLedger(new BusinessLedger(books, due,
                clearing, state.businessLedger().contracts(), state.businessLedger().lastClosedTurn(), state.businessLedger().operatingDecisions())).build(), Map.copyOf(paidByOwner));
    }
}
