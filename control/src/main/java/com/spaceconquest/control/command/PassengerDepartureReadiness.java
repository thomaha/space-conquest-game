package com.spaceconquest.control.command;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import com.spaceconquest.engine.ship.Fleet;

import java.io.IOException;

/** Uses the same cached species requirements for each command's actual scheduled passenger days. */
final class PassengerDepartureReadiness {
    private PassengerDepartureReadiness() {}

    static boolean ready(GameState state, Fleet fleet, double days) {
        if (fleet == null || !Double.isFinite(days) || days < 0) return false;
        boolean booked = state.passengerManifests().stream().anyMatch(manifest ->
                fleet.ships().stream().anyMatch(ship -> ship.id().equals(manifest.shipId())));
        if (!booked) return true;
        try {
            return PassengerTransitProcessor.canSustainJourney(state, fleet, DataModelLoader.loadRaces(), days);
        } catch (IOException exception) {
            return false;
        }
    }
}
