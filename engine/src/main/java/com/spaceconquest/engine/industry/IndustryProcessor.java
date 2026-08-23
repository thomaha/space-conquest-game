package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages industrial facility recipe throughput, tier expansion pipelines and ownership profit routing.
 */
public class IndustryProcessor {

    public record IndustryTurnResult(
            List<IndustrialFacility> updatedFacilities,
            List<FacilityExpansionProject> remainingProjects,
            List<Empire> updatedEmpires,
            List<Corporation> updatedCorporations,
            Map<String, Double> materialYieldsKg
    ) {}

    /**
     * Executes the turn update pass across all industrial facilities and expansion projects.
     */
    public IndustryTurnResult processIndustrialProduction(
            List<IndustrialFacility> facilities,
            List<FacilityExpansionProject> expansionProjects,
            List<Empire> empires,
            List<Corporation> corporations,
            double stateTariffRate
    ) {
        if (facilities == null) facilities = List.of();
        if (expansionProjects == null) expansionProjects = List.of();

        Map<String, Double> empireTreasuryDeltas = new HashMap<>();
        Map<String, Double> corpReserveDeltas = new HashMap<>();
        Map<String, Double> materialYields = new HashMap<>();

        List<IndustrialFacility> updatedFacilities = new ArrayList<>();

        // 1. Process Facility Manufacturing Cycles
        for (IndustrialFacility facility : facilities) {
            double effectiveWorkers = facility.allocatedWorkers();
            double throughputMultiplier = facility.getEffectiveThroughputMultiplier();
            double yieldKg = effectiveWorkers * 10.0 * throughputMultiplier;
            double grossRevenue = yieldKg * 2.0;
            double operatingCost = effectiveWorkers * 1.0;
            double netProfit = Math.max(0.0, grossRevenue - operatingCost);

            materialYields.merge(facility.applicationId(), yieldKg, Double::sum);

            // Ownership Routing
            if (IndustrialFacility.PUBLIC_STATE.equalsIgnoreCase(facility.ownershipType())) {
                empireTreasuryDeltas.merge(facility.ownerEntityId(), netProfit, Double::sum);
            } else if (IndustrialFacility.PRIVATE_CORPORATE.equalsIgnoreCase(facility.ownershipType())) {
                double tariff = netProfit * Math.max(0.0, stateTariffRate);
                double corpProfit = netProfit - tariff;

                // Route tariff to empire of host planet or corporation owner
                for (Corporation corp : corporations) {
                    if (corp.id().equals(facility.ownerEntityId())) {
                        empireTreasuryDeltas.merge(corp.empireId(), tariff, Double::sum);
                        break;
                    }
                }
                corpReserveDeltas.merge(facility.ownerEntityId(), corpProfit, Double::sum);
            } else if (IndustrialFacility.HIVE_GRID.equalsIgnoreCase(facility.ownershipType())) {
                // Hive grid: 100% material yields routed directly into the collective (0 currency)
            }

            updatedFacilities.add(facility);
        }

        // 2. Process Facility Expansion Projects (-50% output penalty handled via getEffectiveThroughputMultiplier)
        List<FacilityExpansionProject> remainingProjects = new ArrayList<>();
        Map<String, Integer> completedUpgrades = new HashMap<>();

        for (FacilityExpansionProject proj : expansionProjects) {
            double newHours = proj.accumulatedWorkHours() + 100.0;
            if (newHours >= proj.requiredWorkHours()) {
                completedUpgrades.put(proj.facilityId(), proj.targetTier());
            } else {
                remainingProjects.add(new FacilityExpansionProject(
                        proj.projectId(),
                        proj.facilityId(),
                        proj.targetTier(),
                        newHours,
                        proj.requiredWorkHours(),
                        proj.costCredits()
                ));
            }
        }

        // Apply completed tier upgrades to facilities
        if (!completedUpgrades.isEmpty()) {
            updatedFacilities = updatedFacilities.stream().map(fac -> {
                if (completedUpgrades.containsKey(fac.id())) {
                    int newTier = completedUpgrades.get(fac.id());
                    return new IndustrialFacility(
                            fac.id(),
                            fac.planetId(),
                            fac.applicationId(),
                            fac.ownerEntityId(),
                            fac.ownershipType(),
                            newTier,
                            fac.allocatedWorkers(),
                            fac.workerProfessionId(),
                            false,
                            0.0
                    );
                }
                return fac;
            }).toList();
        }

        // 3. Update Empires with State Treasury Revenues
        List<Empire> updatedEmpires = empires.stream().map(emp -> {
            double delta = empireTreasuryDeltas.getOrDefault(emp.id(), 0.0);
            return new Empire(
                    emp.id(), emp.name(), emp.raceId(), emp.societyStructure(),
                    emp.treasuryCredits() + delta, emp.corporateTaxRate(), emp.controlledSystemIds(),
                    emp.ministries(), emp.systemGovernorAssignments(), emp.unlockedTechIds(), emp.activeShipDesignIds()
            );
        }).toList();

        // 4. Update Corporations with Liquid Capital Reserves
        List<Corporation> updatedCorps = corporations.stream().map(corp -> {
            double delta = corpReserveDeltas.getOrDefault(corp.id(), 0.0);
            return new Corporation(
                    corp.id(), corp.name(), corp.empireId(),
                    corp.headquartersEntityId(), corp.marketOrientation(),
                    corp.liquidCapitalReserves() + delta, corp.ownedFacilityIds(),
                    corp.ownedShipIds(), corp.claimedVeinIds()
            );
        }).toList();

        return new IndustryTurnResult(
                updatedFacilities,
                remainingProjects,
                updatedEmpires,
                updatedCorps,
                materialYields
        );
    }

    public record MassDriverLaunchResult(
            boolean isSuccessful,
            double launchedPayloadTons,
            double powerConsumedKw,
            double operationalCostCredits,
            String errorMessage
    ) {}

    /**
     * Executes a surface-to-orbit mass driver freight launch, bypassing planetary gravity launch taxes.
     */
    public MassDriverLaunchResult processMassDriverLaunch(
            SurfaceMassDriver driver,
            double payloadTons,
            double availablePowerKw,
            double ownerAvailableCredits
    ) {
        if (driver == null || !driver.isActive()) {
            return new MassDriverLaunchResult(false, 0.0, 0.0, 0.0, "Mass driver is inactive or null");
        }
        if (availablePowerKw < driver.powerDrawKw()) {
            return new MassDriverLaunchResult(false, 0.0, 0.0, 0.0, "Insufficient electrical power for mass driver coils");
        }
        double actualPayload = Math.min(payloadTons, driver.maxPayloadTonsPerTurn());
        double cost = actualPayload * driver.launchCostPerTonCredits();
        if (ownerAvailableCredits < cost) {
            return new MassDriverLaunchResult(false, 0.0, 0.0, 0.0, "Insufficient credits for mass driver launch");
        }
        return new MassDriverLaunchResult(true, actualPayload, driver.powerDrawKw(), cost, "");
    }
}
