package com.spaceconquest.engine.ship;

import java.util.Map;

/**
 * Physical instantiated starship operating in a fleet.
 *
 * @param id                  unique ship instance identifier
 * @param designId            referenced ShipDesign blueprint ID
 * @param ownerEntityId       empire or corporate owner identifier
 * @param currentHullHealth   current structural hull points
 * @param currentShieldHealth current shield energy points
 * @param currentFuelKg       current onboard propulsion fuel in kilograms
 * @param storedCargoKg       current cargo manifest by material ID
 * @param passengerCount      number of passengers or troops currently embarked
 * @param passengerRaceId     species identifier of embarked passengers
 * @param transitMode         passenger life support mode (CONSCIOUS or CRYOGENIC_STASIS)
 */
public record ShipInstance(
        String id,
        String designId,
        String ownerEntityId,
        double currentHullHealth,
        double currentShieldHealth,
        double currentFuelKg,
        Map<String, Double> storedCargoKg,
        int passengerCount,
        String passengerRaceId,
        String transitMode,
        ShipPowerState powerState
) {
    public static final String MODE_CONSCIOUS = "CONSCIOUS";
    public static final String MODE_CRYOGENIC_STASIS = "CRYOGENIC_STASIS";

    public ShipInstance {
        storedCargoKg = storedCargoKg == null ? Map.of() : Map.copyOf(storedCargoKg);
        if (transitMode == null) transitMode = MODE_CONSCIOUS;
        if (passengerRaceId == null) passengerRaceId = "";
    }

    public ShipInstance(String id, String designId, String ownerEntityId, double currentHullHealth,
                        double currentShieldHealth, double currentFuelKg, Map<String, Double> storedCargoKg,
                        int passengerCount, String passengerRaceId, String transitMode) {
        this(id, designId, ownerEntityId, currentHullHealth, currentShieldHealth, currentFuelKg,
                storedCargoKg, passengerCount, passengerRaceId, transitMode, null);
    }

    public ShipInstance withPowerState(ShipPowerState power) {
        return new ShipInstance(id, designId, ownerEntityId, currentHullHealth, currentShieldHealth,
                currentFuelKg, storedCargoKg, passengerCount, passengerRaceId, transitMode, power);
    }

    public double generatorFuelMassKg() { return powerState == null ? 0 : powerState.fuelMassKg(); }

    public ShipInstance(
            String id,
            String designId,
            String ownerEntityId,
            double currentHullHealth,
            double currentShieldHealth,
            double currentFuelKg,
            Map<String, Double> storedCargoKg
    ) {
        this(id, designId, ownerEntityId, currentHullHealth, currentShieldHealth, currentFuelKg, storedCargoKg, 0, "", MODE_CONSCIOUS);
    }
}
