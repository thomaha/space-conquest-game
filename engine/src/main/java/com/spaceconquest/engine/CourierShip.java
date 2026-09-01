package com.spaceconquest.engine;

/**
 * Represents a physical courier spacecraft carrying liquid wealth (credits) between star systems.
 * Credits are only added to the destination treasury once the courier ship arrives.
 *
 * @param id                   unique identifier
 * @param ownerEmpireId        empire that owns the credits
 * @param credits              amount of credits being transported
 * @param originSystemId       starting solar system
 * @param destinationSystemId  target solar system for delivery
 * @param travelTurnsRemaining turns until the courier arrives at its destination
 * @param isIntercepted        true if the ship was intercepted by pirates or enemies
 */
public record CourierShip(
        String id,
        String ownerEmpireId,
        double credits,
        String originSystemId,
        String destinationSystemId,
        int travelTurnsRemaining,
        boolean isIntercepted
) {
    public CourierShip {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

    public CourierShip withReducedTravel() {
        return new CourierShip(id, ownerEmpireId, credits, originSystemId, destinationSystemId, 
                Math.max(0, travelTurnsRemaining - 1), isIntercepted);
    }

    public boolean hasArrived() {
        return travelTurnsRemaining <= 0 && !isIntercepted;
    }
}
