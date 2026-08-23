package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates species habitability and deploys colony ships to initialize populations on virgin worlds.
 */
public class ColonizationProcessor {

    public record ColonizationResult(
            boolean isSuccessful,
            Planet colonizedPlanet,
            Fleet updatedFleet,
            String failureReason
    ) {}

    /**
     * Attempts to establish a new colonial settlement on a target celestial body.
     */
    public ColonizationResult colonizeWorld(
            Planet targetPlanet,
            Fleet colonyFleet,
            Race race,
            Empire empire,
            List<ShipDesign> designs
    ) {
        if (targetPlanet == null || colonyFleet == null || race == null || empire == null) {
            return new ColonizationResult(false, targetPlanet, colonyFleet, "Invalid parameters");
        }

        // 1. Locate and verify eligible Colony Ship in fleet
        ShipInstance colonyShip = null;
        for (ShipInstance ship : colonyFleet.ships()) {
            if (designs != null) {
                for (ShipDesign d : designs) {
                    if (d.id().equals(ship.designId()) && ShipRole.COLONY_SHIP.equalsIgnoreCase(d.role())) {
                        colonyShip = ship;
                        break;
                    }
                }
            }
            if (colonyShip != null) break;
        }

        if (colonyShip == null) {
            return new ColonizationResult(false, targetPlanet, colonyFleet, "No eligible Colony Ship in fleet");
        }

        // 2. Validate Environmental Habitability (Gravity tolerance in SI units m/s²)
        double gravDiff = Math.abs(race.preferredGravity() - targetPlanet.gravity());
        if (gravDiff > 20.0) {
            return new ColonizationResult(false, targetPlanet, colonyFleet,
                    String.format("Surface gravity (%.2f m/s²) exceeds species survival tolerance (%.2f m/s²)",
                            targetPlanet.gravity(), race.preferredGravity()));
        }

        // 3. Initialize Virgin Colony Population Cohort (1000 colonists aged 20)
        List<Population> updatedPops = new ArrayList<>(targetPlanet.populations());
        updatedPops.add(new Population(race.id(), Map.of(20, 1000L)));

        Planet colonizedPlanet = new Planet(
                targetPlanet.id(), targetPlanet.name(), targetPlanet.description(),
                targetPlanet.mass(), targetPlanet.gravity(),
                targetPlanet.distance(), targetPlanet.inclination(),
                targetPlanet.diameter(), targetPlanet.type(),
                targetPlanet.atmosphere(), targetPlanet.hasLiquidWater(),
                targetPlanet.waterLevel(), targetPlanet.resources(),
                targetPlanet.moons(), updatedPops
        );

        // 4. Consume the colony ship from the fleet
        List<ShipInstance> remainingShips = new ArrayList<>(colonyFleet.ships());
        remainingShips.remove(colonyShip);

        Fleet updatedFleet = new Fleet(
                colonyFleet.id(), colonyFleet.name(), colonyFleet.ownerEntityId(),
                colonyFleet.currentSystemId(), colonyFleet.targetSystemId(),
                colonyFleet.coordinateX(), colonyFleet.coordinateY(),
                colonyFleet.transitProgress(), colonyFleet.isInWarp(),
                colonyFleet.fleetStance(), remainingShips
        );

        return new ColonizationResult(true, colonizedPlanet, updatedFleet, "Colony successfully established");
    }
}
