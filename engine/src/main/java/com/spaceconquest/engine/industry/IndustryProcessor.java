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
 * Advances facility expansion projects. Material production and trade run in IndustryMarketProcessor.
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
     * Advances expansion work without estimating output or crediting unsold production.
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

        List<IndustrialFacility> updatedFacilities = new ArrayList<>(facilities);

        Map<String, Integer> completedUpgrades = new HashMap<>();
        List<FacilityExpansionProject> remainingProjects = processExpansionProjects(expansionProjects, completedUpgrades);
        updatedFacilities = applyCompletedUpgrades(updatedFacilities, completedUpgrades);

        return new IndustryTurnResult(
                updatedFacilities,
                remainingProjects,
                empires,
                corporations,
                Map.of(),
                List.of()
        );
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
