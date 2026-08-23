package com.spaceconquest.engine.combat;

/**
 * Surface-to-orbit planetary defense battery engaging hostile blockading fleets and descent transports.
 *
 * @param id                    unique battery identifier
 * @param planetId              host planet identifier
 * @param damagePerRound        anti-ship firepower per tactical combat round
 * @param surfaceSlotsOccupied  surface area slots occupied
 * @param isOperational         true if battery is active and staffed
 */
public record PlanetaryDefenseBattery(
        String id,
        String planetId,
        double damagePerRound,
        int surfaceSlotsOccupied,
        boolean isOperational
) {}
