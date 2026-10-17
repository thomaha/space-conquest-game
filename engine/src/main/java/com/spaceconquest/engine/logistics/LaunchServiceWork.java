package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import java.util.*;

/** Paid payroll supports the following service period; usage consumes finite task work. */
public final class LaunchServiceWork {
    private LaunchServiceWork() {}
    public record Quote(double workUnitsPerDay, double workUnitsPerKg, double capacityKg,
                        double payrollCredits, double maintenanceCredits, double capitalReserveCredits) {}
    public static Quote quote(GameState state, IndustrialFacility facility) {
        var entry = InfrastructureService.find(facility.applicationId());
        var equipment = facility.productionEquipment();
        if (entry == null || equipment == null || facility.tier() <= 0
                || !entry.capability().professionId().equals(facility.workerProfessionId())
                || !equipment.operatorProfession().equals(facility.workerProfessionId())
                || !entry.capability().work().equipment().processType().equals(equipment.processType())
                || !FacilityManufacturingCapacity.canOperate(state, facility))
            return new Quote(0, 1, 0, 0, 0, 0);
        var service = entry.capability();
        var account = state.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id()))
                .findFirst().orElse(IndustryAccount.empty(facility.id()));
        int operators = Math.min(Math.max(0, facility.allocatedWorkers()), account.paidWorkers());
        double scale = facility.tier() * (facility.isUndergoingExpansion() ? service.infrastructure().expansionCapacityFraction() : 1);
        double work = equipment.ratedWorkPerDay() * scale * equipment.staffedFraction(operators)
                * com.spaceconquest.engine.technology.ApplicationProduction.modifiersForOwner(state, facility.ownerEntityId(), facility.applicationId()).effectMultiplier();
        double capacity = Math.min(service.capacityKgPerTier() * facility.tier() * equipment.parallelLines(), work / service.work().unitsPerKg());
        if (service.mode() == LaunchService.Mode.SPACE_ELEVATOR) {
            var elevator = state.spaceElevators().stream().filter(e -> e.id().equals(facility.id()) && e.isOperational()
                    && e.structuralIntegrityPercent() > 0).findFirst().orElse(null);
            capacity = elevator == null ? 0 : Math.min(capacity, elevator.transitThroughputCapacityKgPerTurn());
        }
        double payroll = Math.max(account.wageCostsCredits(), operators * Profession.getBaseWageForProfession(equipment.operatorProfession()));
        double capital = FacilitySupplyPricing.capital(state, state.commercialHubs().stream()
                .filter(h -> h.entityId().equals(facility.planetId())).findFirst().orElse(null), facility);
        return new Quote(capacity * service.work().unitsPerKg(), service.work().unitsPerKg(), capacity, payroll, FacilitySupplyPricing.declaredMaintenance(facility),
                capital / SupplyPricingPolicy.current().capitalRecoveryDays());
    }
    public static double capacity(GameState state, String id) {
        var facility = state.industrialFacilities().stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
        return facility == null ? 0 : quote(state, facility).capacityKg();
    }
    public static double fee(GameState state, String id, double payloadKg, double powerCost, double floor) {
        var facility = state.industrialFacilities().stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
        if (facility == null) return Double.POSITIVE_INFINITY;
        var quote = quote(state, facility);
        if (quote.capacityKg() <= 0) return Double.POSITIVE_INFINITY;
        double share = payloadKg / quote.capacityKg();
        return Math.max(floor, SupplyPricingPolicy.current().requiredReceipts(
                (quote.payrollCredits() + quote.maintenanceCredits()) * share + powerCost,
                quote.capitalReserveCredits() * share, SupplyPricingPolicy.taxRate(state, facility.ownerEntityId())));
    }
    /** Elevators share ordinary facility payroll, business expenses and replacement accounting. */
    public static GameState commission(GameState state, com.spaceconquest.engine.macrostructure.SpaceElevator elevator) {
        if (state.industrialFacilities().stream().anyMatch(f -> f.id().equals(elevator.id()))) return state;
        var service = InfrastructureService.find("SPACE_ELEVATOR").capability();
        boolean company = state.corporations().stream().anyMatch(c -> c.id().equals(elevator.ownerEntityId()));
        var facility = new IndustrialFacility(elevator.id(), elevator.planetId(), service.facilityType(), elevator.ownerEntityId(),
                company ? IndustrialFacility.PRIVATE_CORPORATE : IndustrialFacility.PUBLIC_STATE, 1,
                service.workers(), service.professionId(), false, 0);
        var facilities = new ArrayList<>(state.industrialFacilities()); facilities.add(facility);
        state = state.withIndustrialFacilities(facilities);
        if (company) return state;
        var policy = OperatingReservePolicy.forApplication(service.facilityType());
        double capital = Math.min(Math.max(0, com.spaceconquest.engine.economy.EconomicOwnership.cash(state, elevator.ownerEntityId())
                        - policy.treasuryReserveCredits()), Math.min(policy.maximumCashCredits(), policy.targetDays()
                        * (service.workers() * Profession.getBaseWageForProfession(service.professionId()) + policy.maintenanceCreditsPerTier())));
        state = com.spaceconquest.engine.economy.BusinessCash.change(state, elevator.ownerEntityId(), -capital);
        var accounts = new ArrayList<>(state.industryAccounts()); accounts.add(IndustryAccount.empty(elevator.id()).withOperatingCash(capital));
        return state.toBuilder().industryAccounts(accounts).build();
    }
}
