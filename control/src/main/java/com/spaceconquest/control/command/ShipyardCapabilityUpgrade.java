package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.ShipyardInvestmentPolicy;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** A discarded completed-yard snapshot used to validate a paid capability upgrade. */
record ShipyardCapabilityUpgrade(GameCommand command, GameState completed, Map<String, Double> materials,
        double dailyWages, double constructionDays, boolean orbital) {
    static ShipyardCapabilityUpgrade plan(GameState state, String owner, String system, String yard, int required) {
        var home = EconomicOwnership.empireFor(state, owner);
        if (home == null || !home.controlledSystemIds().contains(system)
                || !owner.equals(home.id()) && !EconomicOwnership.corporationsAllowed(home)
                || required <= ShipManufacturingCapacity.forYard(state, owner, system, yard)) return null;
        var profile = ShipyardWorkCapacity.forYard(state, owner, system, yard);
        if (!profile.operational() || !profile.isFullyStaffed() || profile.workPerDay() <= 0) return null;
        var station = state.orbitalStations().stream().filter(item -> yard.equals(item.id()) && owner.equals(item.ownerEntityId())).findFirst().orElse(null);
        return station == null ? surface(state, owner, yard, required, profile) : orbital(state, station, required, profile);
    }
    private static double wages(ShipyardWorkCapacity.Profile profile) {
        return profile.staffingByProfession().values().stream()
                .mapToDouble(staff -> staff.requiredWorkers() * Profession.getBaseWageForProfession(staff.professionId())).sum();
    }
    private static ShipyardCapabilityUpgrade orbital(GameState state, OrbitalStation station, int required, ShipyardWorkCapacity.Profile profile) {
        if (state.constructionProjects().stream().anyMatch(project -> station.id().equals(project.targetStationId()))) return null;
        var home = EconomicOwnership.empireFor(state, station.ownerEntityId());
        for (var entry : ShipyardTechnology.allYards().entrySet().stream().filter(item -> !com.spaceconquest.engine.ship.ShipyardTechnology.isSurface(item.getKey())
                && item.getValue().baseComplexity() >= required).sorted(Comparator.comparingDouble((Map.Entry<String, ShipyardTechnology.Yard> item) ->
                item.getValue().materialsKg().values().stream().mapToDouble(Double::doubleValue).sum()).thenComparing(Map.Entry::getKey)).toList()) {
            var capability = entry.getValue();
            if (station.currentPowerGenerationKw() < station.currentPowerDemandKw() + capability.powerDrawKw()) continue;
            int workers = (int) Math.ceil(capability.workers() * profile.workerRequirementMultiplier());
            long free = state.householdAccounts().stream().filter(account -> station.id().equals(account.bodyId()) && home.id().equals(account.empireId())
                    && capability.professionId().equals(account.professionId())).mapToLong(account -> account.employment().unemployedWorkers()).sum();
            if (free < workers) continue;
            var command = new AddStationModuleCommand(station.id(), "Manufacturing capability upgrade", entry.getKey(), capability.slots(),
                    capability.dryMassKg(), capability.powerDrawKw(), 0, capability.professionId(), capability.workers());
            if (!command.validate(state)) continue;
            var modules = new ArrayList<>(station.modules()); modules.add(capability.module(entry.getKey()).withPayroll(workers, 0));
            var completed = new OrbitalStation(station.id(), station.name(), station.systemId(), station.planetOrbitId(), station.ownerEntityId(), station.ownershipType(),
                    station.totalSlots(), modules, station.storedCargoKg(), station.currentPowerGenerationKw(), station.currentPowerDemandKw() + capability.powerDrawKw(),
                    station.currentShieldHealth(), station.maxShieldHealth(), station.currentHullHealth(), station.maxHullHealth(), station.armorMaterialId(), station.armorThicknessCm(),
                    station.isOperational(), station.populations(), station.parkingAltitudeKm(), station.orbitalPhaseRadians());
            return new ShipyardCapabilityUpgrade(command, state.toBuilder().orbitalStations(state.orbitalStations().stream()
                    .map(item -> item.id().equals(station.id()) ? completed : item).toList()).build(), capability.materialsKg(),
                    wages(profile) + workers * Profession.getBaseWageForProfession(capability.professionId()), capability.constructionWork(), true);
        }
        return null;
    }
    private static ShipyardCapabilityUpgrade surface(GameState state, String owner, String yard, int required, ShipyardWorkCapacity.Profile profile) {
        var facility = state.industrialFacilities().stream().filter(item -> owner.equals(item.ownerEntityId()) && yard.equals(item.planetId())
                && com.spaceconquest.engine.ship.ShipyardTechnology.isSurface(item.applicationId())).findFirst().orElse(null);
        if (facility == null || facility.isUndergoingExpansion() || state.expansionProjects().stream().anyMatch(project -> facility.id().equals(project.facilityId()))) return null;
        var capability = ShipyardTechnology.yard(facility.applicationId());
        if (capability == null || capability.complexityPerTier() <= 0 || !EconomicOwnership.empireFor(state, owner).unlockedTechIds()
                .contains(ShipyardTechnology.application(facility.applicationId()))) return null;
        int target = 1 + Math.ceilDiv(required - capability.baseComplexity(), capability.complexityPerTier());
        int increase = target - facility.tier(); var policy = ShipyardInvestmentPolicy.current();
        if (increase < 1 || increase > policy.maximumSurfaceTierIncrease()) return null;
        var account = state.industryAccounts().stream().filter(item -> facility.id().equals(item.facilityId())).findFirst().orElse(null);
        if (account == null) return null;
        double dailyCost = wages(profile) + account.maintenanceCostsCredits() + account.powerCostsCredits();
        if (account.operatingCashCredits() < dailyCost * policy.payrollReserveDays()) return null;
        var command = new ExpandFacilityCommand(facility.id(), target, capability.constructionWork() * increase, 0);
        var completed = new IndustrialFacility(facility.id(), facility.planetId(), facility.applicationId(), facility.ownerEntityId(), facility.ownershipType(),
                target, facility.allocatedWorkers(), facility.workerProfessionId(), false, 0, facility.productionEquipment());
        double work = com.spaceconquest.engine.technology.ApplicationProduction.constructionWorkHours(state, owner, facility.applicationId(), capability.constructionWork() * increase);
        return new ShipyardCapabilityUpgrade(command, state.toBuilder().industrialFacilities(state.industrialFacilities().stream()
                .map(item -> item.id().equals(facility.id()) ? completed : item).toList()).build(), ConstructionMaterialCatalog.facility(state, owner, facility.applicationId(), increase),
                dailyCost, Math.ceil(work / 100), false);
    }
}
