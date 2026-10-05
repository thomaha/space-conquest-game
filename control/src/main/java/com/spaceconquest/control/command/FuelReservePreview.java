package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import java.util.List;
import java.util.Map;

/** Immutable per-ship consumption and contingency disclosure for a funded departure preview. */
public record FuelReservePreview(String shipId, double departureKg, double plannedBurnKg,
                                 double arrivalKg, double protectedKg) {
    public double shortfallKg() { return Math.max(0, protectedKg - arrivalKg); }
    public static List<FuelReservePreview> inspect(GameState state, Fleet fleet, Map<String, Double> local,
                                                  Map<String, Double> crossing, Fleet projectedArrival) {
        return fleet.ships().stream().map(ship -> {
            double burned = local.getOrDefault(ship.id(), 0.0) + crossing.getOrDefault(ship.id(), 0.0);
            double arrival = projectedArrival == null ? Math.max(0, ship.currentFuelKg() - burned)
                    : projectedArrival.ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow().currentFuelKg();
            return new FuelReservePreview(ship.id(), ship.currentFuelKg(), burned, arrival,
                    fleet.fuelPolicy().reserveKg(state, fleet, ship));
        }).toList();
    }
}
