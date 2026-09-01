package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.CourierShip;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.SystemEconomy;

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
            Map<String, Double> materialYieldsKg,
            List<CourierShip> spawnedCouriers
    ) {}

    /**
     * Executes the turn update pass across all industrial facilities and expansion projects.
     */
    public IndustryTurnResult processIndustrialProduction(
            List<IndustrialFacility> facilities,
            List<FacilityExpansionProject> expansionProjects,
            List<Empire> empires,
            List<Corporation> corporations,
            List<SystemEconomy> economies,
            List<SolarSystem> solarSystems,
            double stateTariffRate
    ) {
        if (facilities == null) facilities = List.of();
        if (expansionProjects == null) expansionProjects = List.of();
        if (empires == null) empires = List.of();
        if (corporations == null) corporations = List.of();

        Map<String, Double> empireTreasuryDeltas = new HashMap<>();
        Map<String, Double> corpReserveDeltas = new HashMap<>();
        Map<String, Double> materialYields = new HashMap<>();
        List<CourierShip> spawnedCouriers = new ArrayList<>();

        Map<String, SystemEconomy> economyMap = buildEconomyMap(economies);
        Map<String, String> planetToSystemMap = buildPlanetToSystemMap(solarSystems);

        List<IndustrialFacility> updatedFacilities = processFacilityCycles(
                facilities, economyMap, planetToSystemMap, empires, corporations,
                stateTariffRate, materialYields, empireTreasuryDeltas, corpReserveDeltas, spawnedCouriers
        );

        Map<String, Integer> completedUpgrades = new HashMap<>();
        List<FacilityExpansionProject> remainingProjects = processExpansionProjects(expansionProjects, completedUpgrades);
        updatedFacilities = applyCompletedUpgrades(updatedFacilities, completedUpgrades);

        List<Empire> updatedEmpires = updateEmpireTreasuries(empires, empireTreasuryDeltas);
        List<Corporation> updatedCorps = updateCorporationReserves(corporations, corpReserveDeltas);

        return new IndustryTurnResult(
                updatedFacilities,
                remainingProjects,
                updatedEmpires,
                updatedCorps,
                materialYields,
                spawnedCouriers
        );
    }

    private Map<String, SystemEconomy> buildEconomyMap(List<SystemEconomy> economies) {
        Map<String, SystemEconomy> economyMap = new HashMap<>();
        if (economies != null) {
            for (SystemEconomy eco : economies) {
                if (eco != null && eco.systemId() != null) {
                    economyMap.put(eco.systemId(), eco);
                }
            }
        }
        return economyMap;
    }

    private Map<String, String> buildPlanetToSystemMap(List<SolarSystem> solarSystems) {
        Map<String, String> map = new HashMap<>();
        if (solarSystems != null) {
            for (SolarSystem sys : solarSystems) {
                if (sys != null && sys.planets() != null) {
                    for (var p : sys.planets()) {
                        if (p != null) {
                            map.put(p.id(), sys.id());
                        }
                    }
                }
            }
        }
        return map;
    }

    private List<IndustrialFacility> processFacilityCycles(
            List<IndustrialFacility> facilities,
            Map<String, SystemEconomy> economyMap,
            Map<String, String> planetToSystemMap,
            List<Empire> empires,
            List<Corporation> corporations,
            double stateTariffRate,
            Map<String, Double> materialYields,
            Map<String, Double> empireTreasuryDeltas,
            Map<String, Double> corpReserveDeltas,
            List<CourierShip> spawnedCouriers
    ) {
        List<IndustrialFacility> result = new ArrayList<>();
        for (IndustrialFacility facility : facilities) {
            String systemId = planetToSystemMap.get(facility.planetId());
            SystemEconomy economy = systemId != null ? economyMap.get(systemId) : null;

            double strikePenalty = (economy != null && economy.healthAndWelfareLevel() < 0.5) ? 0.0 : 1.0;
            double effectiveWorkers = facility.allocatedWorkers();
            double throughputMultiplier = facility.getEffectiveThroughputMultiplier() * strikePenalty;
            double yieldKg = effectiveWorkers * 10.0 * throughputMultiplier;
            double grossRevenue = yieldKg * 2.0;
            double operatingCost = effectiveWorkers * 1.0;
            double netProfit = Math.max(0.0, grossRevenue - operatingCost);

            materialYields.merge(facility.applicationId(), yieldKg, Double::sum);

            if (netProfit > 0) {
                routeProfit(facility, netProfit, systemId, empires, corporations, stateTariffRate,
                        empireTreasuryDeltas, corpReserveDeltas, spawnedCouriers);
            }
            result.add(facility);
        }
        return result;
    }

    private void routeProfit(
            IndustrialFacility facility,
            double netProfit,
            String systemId,
            List<Empire> empires,
            List<Corporation> corporations,
            double stateTariffRate,
            Map<String, Double> empireTreasuryDeltas,
            Map<String, Double> corpReserveDeltas,
            List<CourierShip> spawnedCouriers
    ) {
        if (IndustrialFacility.PUBLIC_STATE.equalsIgnoreCase(facility.ownershipType())) {
            routePublicProfit(facility, netProfit, systemId, empires, empireTreasuryDeltas, spawnedCouriers);
        } else if (IndustrialFacility.PRIVATE_CORPORATE.equalsIgnoreCase(facility.ownershipType())) {
            routeCorporateProfit(facility, netProfit, systemId, corporations, stateTariffRate,
                    empireTreasuryDeltas, corpReserveDeltas, spawnedCouriers);
        }
    }

    private void routePublicProfit(
            IndustrialFacility facility,
            double netProfit,
            String systemId,
            List<Empire> empires,
            Map<String, Double> empireTreasuryDeltas,
            List<CourierShip> spawnedCouriers
    ) {
        boolean isLocal = false;
        for (Empire emp : empires) {
            if (emp.id().equals(facility.ownerEntityId())) {
                if (systemId == null || (emp.controlledSystemIds() != null && emp.controlledSystemIds().contains(systemId))) {
                    isLocal = true;
                }
                break;
            }
        }

        if (isLocal) {
            empireTreasuryDeltas.merge(facility.ownerEntityId(), netProfit, Double::sum);
        } else {
            spawnedCouriers.add(new CourierShip(
                    null, facility.ownerEntityId(), netProfit,
                    systemId != null ? systemId : "UNKNOWN",
                    "CAPITAL", 3, false
            ));
        }
    }

    private void routeCorporateProfit(
            IndustrialFacility facility,
            double netProfit,
            String systemId,
            List<Corporation> corporations,
            double stateTariffRate,
            Map<String, Double> empireTreasuryDeltas,
            Map<String, Double> corpReserveDeltas,
            List<CourierShip> spawnedCouriers
    ) {
        double tariff = netProfit * Math.max(0.0, stateTariffRate);
        double corpProfit = netProfit - tariff;

        for (Corporation corp : corporations) {
            if (corp.id().equals(facility.ownerEntityId())) {
                if (corp.empireId() != null) {
                    empireTreasuryDeltas.merge(corp.empireId(), tariff, Double::sum);
                }
                boolean isAtHQ = (systemId == null)
                        || facility.planetId().equals(corp.headquartersEntityId())
                        || (systemId != null && systemId.equals(corp.headquartersEntityId()));
                if (isAtHQ) {
                    corpReserveDeltas.merge(facility.ownerEntityId(), corpProfit, Double::sum);
                } else {
                    spawnedCouriers.add(new CourierShip(
                            null, facility.ownerEntityId(), corpProfit,
                            systemId != null ? systemId : "UNKNOWN",
                            "HQ", 3, false
                    ));
                }
                break;
            }
        }
    }

    private List<FacilityExpansionProject> processExpansionProjects(
            List<FacilityExpansionProject> expansionProjects,
            Map<String, Integer> completedUpgrades
    ) {
        List<FacilityExpansionProject> remaining = new ArrayList<>();
        for (FacilityExpansionProject proj : expansionProjects) {
            double newHours = proj.accumulatedWorkHours() + 100.0;
            if (newHours >= proj.requiredWorkHours()) {
                completedUpgrades.put(proj.facilityId(), proj.targetTier());
            } else {
                remaining.add(new FacilityExpansionProject(
                        proj.projectId(), proj.facilityId(), proj.targetTier(),
                        newHours, proj.requiredWorkHours(), proj.costCredits()
                ));
            }
        }
        return remaining;
    }

    private List<IndustrialFacility> applyCompletedUpgrades(
            List<IndustrialFacility> facilities,
            Map<String, Integer> completedUpgrades
    ) {
        if (completedUpgrades.isEmpty()) {
            return facilities;
        }
        return facilities.stream().map(fac -> {
            if (completedUpgrades.containsKey(fac.id())) {
                int newTier = completedUpgrades.get(fac.id());
                return new IndustrialFacility(
                        fac.id(), fac.planetId(), fac.applicationId(), fac.ownerEntityId(),
                        fac.ownershipType(), newTier, fac.allocatedWorkers(),
                        fac.workerProfessionId(), false, 0.0
                );
            }
            return fac;
        }).toList();
    }

    private List<Empire> updateEmpireTreasuries(List<Empire> empires, Map<String, Double> empireTreasuryDeltas) {
        return empires.stream().map(emp -> {
            double delta = empireTreasuryDeltas.getOrDefault(emp.id(), 0.0);
            return new Empire(
                    emp.id(), emp.name(), emp.raceId(), emp.societyStructure(),
                    emp.treasuryCredits() + delta, emp.corporateTaxRate(), emp.controlledSystemIds(),
                    emp.ministries(), emp.systemGovernorAssignments(), emp.unlockedTechIds(), emp.activeShipDesignIds()
            );
        }).toList();
    }

    private List<Corporation> updateCorporationReserves(List<Corporation> corporations, Map<String, Double> corpReserveDeltas) {
        return corporations.stream().map(corp -> {
            double delta = corpReserveDeltas.getOrDefault(corp.id(), 0.0);
            return new Corporation(
                    corp.id(), corp.name(), corp.empireId(),
                    corp.headquartersEntityId(), corp.marketOrientation(),
                    corp.liquidCapitalReserves() + delta, corp.ownedFacilityIds(),
                    corp.ownedShipIds(), corp.claimedVeinIds()
            );
        }).toList();
    }

    public record MassDriverLaunchResult(
            boolean isSuccessful,
            double launchedPayloadTons,
            double kineticEnergyMegaJoules,
            double transferDurationDays,
            double escapeVelocityKmPerSec,
            double atmosphereLossPercentage,
            double operationalCostCredits,
            String statusMessage
    ) {}

    /**
     * Resolves the mechanical trajectory of surface-to-orbit mass driver payload launches.
     */
    public MassDriverLaunchResult launchMassDriverPayload(
            double payloadMassTons,
            double targetOrbitalAltitudeKm,
            double planetGravityG,
            double atmosphereDensityBar
    ) {
        if (payloadMassTons <= 0.0 || targetOrbitalAltitudeKm <= 0.0 || planetGravityG <= 0.0) {
            return new MassDriverLaunchResult(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "Invalid launch parameters");
        }

        double escapeVelocity = Math.sqrt(2.0 * 9.81 * planetGravityG * (targetOrbitalAltitudeKm * 1000.0)) / 1000.0;
        double kineticEnergy = 0.5 * (payloadMassTons * 1000.0) * Math.pow(escapeVelocity * 1000.0, 2) / 1_000_000.0;

        double atmoLoss = Math.min(0.50, atmosphereDensityBar * 0.15);
        double netDeliveredMass = payloadMassTons * (1.0 - atmoLoss);
        double transferDays = Math.max(0.05, (targetOrbitalAltitudeKm / (escapeVelocity * 3600.0 * 24.0)));

        return new MassDriverLaunchResult(
                true,
                netDeliveredMass,
                kineticEnergy,
                transferDays,
                escapeVelocity,
                atmoLoss * 100.0,
                0.0,
                "Launch successful"
        );
    }

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
            return new MassDriverLaunchResult(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "Mass driver is inactive or null");
        }
        if (availablePowerKw < driver.powerDrawKw()) {
            return new MassDriverLaunchResult(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "Insufficient electrical power for mass driver coils");
        }
        double actualPayload = Math.min(payloadTons, driver.maxPayloadTonsPerTurn());
        double cost = actualPayload * driver.launchCostPerTonCredits();
        if (ownerAvailableCredits < cost) {
            return new MassDriverLaunchResult(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "Insufficient credits for mass driver launch");
        }
        // Simplified mechanical parameters for this high-level method
        return new MassDriverLaunchResult(true, actualPayload, driver.powerDrawKw(), 0.1, 11.2, 0.0, cost, "Launch sequence successful");
    }
}
