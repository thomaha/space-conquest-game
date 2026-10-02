package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.CourierShip;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.ship.ShipConstructionProcessor;

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

        GameState completed = processIndustrialProduction(GameState.builder()
                .industrialFacilities(facilities).expansionProjects(expansionProjects)
                .empires(empires).corporations(corporations).solarSystems(solarSystems)
                .systemEconomies(economies == null ? List.of() : economies).build());
        return new IndustryTurnResult(
                completed.industrialFacilities(),
                completed.expansionProjects(),
                completed.empires(),
                completed.corporations(),
                Map.of(),
                List.of()
        );
    }

    /** Advances only the work supported by materials actually bought this day. */
    public GameState processIndustrialProduction(GameState state) {
        return processIndustrialProduction(state, null);
    }

    /** Advances construction using workers actually hired and paid during the current payroll. */
    public GameState processIndustrialProduction(GameState state,
                                                 Map<String, Integer> paidWorkersByFacility) {
        GameState current = state;
        List<FacilityExpansionProject> remaining = new ArrayList<>();
        Map<String, Integer> completedUpgrades = new HashMap<>();
        for (FacilityExpansionProject proj : state.expansionProjects()) {
            IndustrialFacility facility = state.industrialFacilities().stream()
                    .filter(item -> proj.facilityId().equals(item.id())).findFirst().orElse(null);
            if (facility == null) continue;
            ConstructionProgress.Step step = ConstructionProgress.advance(current,
                    facility.planetId(), facility.ownerEntityId(),
                    proj.requiredMaterialsKg(), proj.consumedMaterialsKg(),
                    proj.accumulatedWorkHours(), proj.requiredWorkHours(), 100.0);
            current = step.state();
            if (step.complete()) {
                completedUpgrades.put(proj.facilityId(), proj.targetTier());
            } else {
                remaining.add(new FacilityExpansionProject(
                        proj.projectId(), proj.facilityId(), proj.targetTier(),
                        step.workHours(), proj.requiredWorkHours(), proj.costCredits(),
                        proj.requiredMaterialsKg(), step.consumedKg()
                ));
            }
        }
        GameState completed = current.toBuilder().expansionProjects(remaining)
                .industrialFacilities(applyCompletedUpgrades(current.industrialFacilities(),
                        completedUpgrades, remaining)).build();
        return new ShipConstructionProcessor().process(completed, paidWorkersByFacility);
    }

    private List<IndustrialFacility> applyCompletedUpgrades(
            List<IndustrialFacility> facilities,
            Map<String, Integer> completedUpgrades,
            List<FacilityExpansionProject> remainingProjects
    ) {
        if (completedUpgrades.isEmpty() && remainingProjects.isEmpty()) {
            return facilities;
        }
        Map<String, FacilityExpansionProject> pending = new HashMap<>();
        for (FacilityExpansionProject project : remainingProjects) {
            pending.put(project.facilityId(), project);
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
            FacilityExpansionProject project = pending.get(fac.id());
            if (project != null) {
                double progress = project.requiredWorkHours() <= 0.0 ? 1.0
                        : Math.clamp(project.accumulatedWorkHours() / project.requiredWorkHours(), 0.0, 1.0);
                return new IndustrialFacility(fac.id(), fac.planetId(), fac.applicationId(),
                        fac.ownerEntityId(), fac.ownershipType(), fac.tier(), fac.allocatedWorkers(),
                        fac.workerProfessionId(), true, progress);
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
