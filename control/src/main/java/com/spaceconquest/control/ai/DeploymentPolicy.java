package com.spaceconquest.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

public record DeploymentPolicy(int maximumRequestsPerTurn, double propellantReserveFraction,
        int maximumTargetPreviews, double defenseWeight, double detectedThreatWeight, double escortWeight,
        double reinforcementWeight, double friendlyForceWeight, double incomingWeight, double distanceWeight,
        double remotePriorityMargin, double maximumTravelDays, double arrivalPropellantFraction, double minimumTreasuryCredits) {
    public DeploymentPolicy {
        if (maximumRequestsPerTurn < 1 || !Double.isFinite(propellantReserveFraction) || propellantReserveFraction < 0 || propellantReserveFraction > 1)
            throw new IllegalArgumentException("Invalid deployment policy");
        for (double value : new double[]{defenseWeight, detectedThreatWeight, escortWeight, reinforcementWeight, friendlyForceWeight,
                incomingWeight, distanceWeight, remotePriorityMargin, maximumTravelDays, arrivalPropellantFraction, minimumTreasuryCredits})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid strategic deployment weight");
        if (maximumTargetPreviews < 1 || maximumTravelDays <= 0 || arrivalPropellantFraction >= 1)
            throw new IllegalArgumentException("Invalid strategic deployment bounds");
    }
    private static final class Loaded {
        static final DeploymentPolicy VALUE = read();
        static DeploymentPolicy read() {
            try (var stream = DeploymentPolicy.class.getResourceAsStream("/deployment_policy.json")) {
                if (stream == null) throw new IOException("Missing deployment policy");
                return new ObjectMapper().readValue(stream, DeploymentPolicy.class);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    public static DeploymentPolicy current() { return Loaded.VALUE; }
}
