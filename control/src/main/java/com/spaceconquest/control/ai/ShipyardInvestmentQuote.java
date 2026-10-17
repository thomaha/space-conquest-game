package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** One bounded, discarded capital and funded-demand forecast. Never mutates its source snapshot. */
public record ShipyardInvestmentQuote(GameCommand construction, double investmentCredits, double forecastProfitCredits) {
    private record Expansion(GameCommand command, Map<String, Double> materials, double dailyWork,
                             double dailyWages, double constructionDays, boolean orbital) {}
    public static ShipyardInvestmentQuote evaluate(GameState state, String owner, String system, String yard) {
        var empire = EconomicOwnership.empireFor(state, owner); var policy = ShipyardInvestmentPolicy.current();
        if (empire == null || !empire.controlledSystemIds().contains(system)
                || !owner.equals(empire.id()) && !EconomicOwnership.corporationsAllowed(empire)) return null;
        var profile = ShipyardWorkCapacity.forYard(state, owner, system, yard);
        if (!profile.operational() || !profile.isFullyStaffed() || profile.workPerDay() <= 0
                || !ShipyardBottleneck.saturated(state, owner, system, yard, profile.workPerDay(),
                policy.observationTurns(), policy.minimumUtilization())) return null;
        var expansion = expansion(state, owner, system, yard, profile);
        if (expansion == null || expansion.dailyWork() <= profile.workPerDay() || !expansion.command().validate(state)) return null;
        double totalKg = expansion.materials().values().stream().mapToDouble(Double::doubleValue).sum();
        var purchase = expansion.orbital() ? ConstructionMaterials.buyOrbitalUpTo(state, system, yard, owner, expansion.materials(), totalKg)
                : ConstructionMaterials.buyUpTo(state, yard, owner, expansion.materials(), totalKg);
        if (!purchase.covers(expansion.materials())) return null;
        double cost = EconomicOwnership.cash(state, owner) - EconomicOwnership.cash(purchase.state(), owner);
        boolean publicYard = owner.equals(empire.id());
        double floor = publicYard ? policy.empireReserveCredits() : policy.corporateReserveCredits();
        if (cost > policy.maximumInvestmentCredits() || EconomicOwnership.cash(purchase.state(), owner)
                < floor + expansion.dailyWages() * policy.payrollReserveDays()) return null;
        var demand = ShipyardBottleneck.fundedDemand(purchase.state(), owner, system, yard,
                profile.workPerDay() * policy.maximumForecastDays(), publicYard);
        if (demand.work() < profile.workPerDay() * policy.minimumQueuedDays()
                || demand.work() <= profile.workPerDay() * expansion.constructionDays()) return null;
        double days = expansion.constructionDays() + demand.work() / expansion.dailyWork();
        double profit = demand.fees() * (1 - Math.clamp(empire.corporateTaxRate(), 0, 1)) - cost - expansion.dailyWages() * days;
        if (!publicYard && profit < policy.minimumProfitCredits()) return null;
        return new ShipyardInvestmentQuote(expansion.command(), cost, profit);
    }
    private static Expansion expansion(GameState state, String owner, String system, String yard, ShipyardWorkCapacity.Profile profile) {
        var station = state.orbitalStations().stream().filter(item -> yard.equals(item.id()) && owner.equals(item.ownerEntityId())).findFirst().orElse(null);
        if (station != null) {
            if (state.constructionProjects().stream().anyMatch(project -> yard.equals(project.targetStationId()))) return null;
            var candidate = ShipyardTechnology.allYards().entrySet().stream()
                    .filter(e -> !e.getValue().surface() && e.getValue().buildsShips() && e.getValue().baseComplexity() > 0)
                    .filter(e -> com.spaceconquest.engine.technology.ApplicationProduction.isAvailable(EconomicOwnership.empireFor(state, owner), ShipyardTechnology.application(e.getKey())))
                    .filter(e -> station.hasAvailableSlots(e.getValue().slots())
                            && station.currentPowerGenerationKw() >= station.currentPowerDemandKw() + e.getValue().powerDrawKw())
                    .min(Comparator.comparingDouble((Map.Entry<String, ShipyardTechnology.Yard> e) -> e.getValue().workPerDay())
                            .thenComparing(Map.Entry::getKey)).orElse(null);
            var capability = candidate == null ? null : candidate.getValue();
            if (capability == null || station.currentPowerGenerationKw() < station.currentPowerDemandKw() + capability.powerDrawKw()) return null;
            var home = EconomicOwnership.empireFor(state, owner);
            int workers = (int) Math.ceil(capability.workers() * profile.workerRequirementMultiplier());
            long free = state.householdAccounts().stream().filter(account -> yard.equals(account.bodyId()) && home.id().equals(account.empireId())
                    && capability.professionId().equals(account.professionId())).mapToLong(account -> account.employment().unemployedWorkers()).sum();
            if (free < workers) return null;
            double wages = station.modules().stream().filter(ShipyardWorkCapacity::isYardWorkforceModule).mapToDouble(StationModule::dailyWageCostsCredits).sum()
                    + workers * Profession.getBaseWageForProfession(capability.professionId());
            var command = new AddStationModuleCommand(yard, "Shipyard expansion", candidate.getKey(),
                    capability.slots(), capability.dryMassKg(), capability.powerDrawKw(), 0, capability.professionId(), capability.workers());
            return new Expansion(command, capability.materialsKg(), profile.workPerDay() + capability.workPerDay() * profile.technologyMultiplier(),
                    wages, capability.constructionWork(), true);
        }
        var facility = state.industrialFacilities().stream().filter(item -> yard.equals(item.planetId()) && owner.equals(item.ownerEntityId())
                && com.spaceconquest.engine.ship.ShipyardTechnology.isSurface(item.applicationId())).findFirst().orElse(null);
        if (facility == null || facility.isUndergoingExpansion() || state.expansionProjects().stream().anyMatch(project -> facility.id().equals(project.facilityId()))) return null;
        var capability = ShipyardTechnology.yard(facility.applicationId());
        var account = state.industryAccounts().stream().filter(item -> facility.id().equals(item.facilityId())).findFirst().orElse(null);
        if (capability == null || account == null || account.operatingCashCredits() < (account.wageCostsCredits()
                + account.maintenanceCostsCredits() + account.powerCostsCredits()) * ShipyardInvestmentPolicy.current().payrollReserveDays()) return null;
        var command = new ExpandFacilityCommand(facility.id(), facility.tier() + 1, capability.constructionWork(), 0);
        var materials = ConstructionMaterialCatalog.facility(state, owner, facility.applicationId(), 1);
        double work = com.spaceconquest.engine.technology.ApplicationProduction.constructionWorkHours(state, owner, facility.applicationId(), capability.constructionWork());
        return new Expansion(command, materials, profile.workPerDay() * capability.tierMultiplier(),
                account.wageCostsCredits() + account.maintenanceCostsCredits() + account.powerCostsCredits(), Math.ceil(work / 100), false);
    }
}
