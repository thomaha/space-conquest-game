package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Native observations and direct hardware grants only; remembered contacts are never relayed. */
public final class SensorContactMemory {
    private SensorContactMemory() {}

    static List<SensorContact> refresh(GameState state, List<SensorContact> previous, Set<String> detected) {
        var contacts = new TreeMap<String, SensorContact>();
        previous.stream().filter(contact -> contact.recent(state.turn())).forEach(contact -> contacts.put(contact.fleetId(), contact));
        for (var fleet : state.fleets()) {
            if (!detected.contains(fleet.id())) continue;
            var position = SensorPosition.at(state, fleet.currentSystemId(), fleet.location());
            if (position == null) continue;
            contacts.put(fleet.id(), new SensorContact(fleet.id(), fleet.ownerEntityId(), fleet.currentSystemId(), position,
                    state.turn(), FleetThreatAssessment.strength(state, List.of(fleet))));
        }
        return List.copyOf(contacts.values());
    }

    /** Lost detections near an actual own position, without looking up hidden ships or motion. */
    public static List<SensorContact> threats(GameState state, Fleet fleet, double radiusMeters) {
        var view = SensorSharing.view(state, fleet.ownerEntityId());
        var position = SensorPosition.at(state, fleet.currentSystemId(), fleet.location());
        if (view == null || position == null) return List.of();
        return view.contacts().stream().filter(contact -> contact.recent(state.turn())
                && fleet.currentSystemId().equals(contact.systemId())
                && SensorSharing.atWar(state, fleet.ownerEntityId(), contact.ownerId())
                && !live(state, fleet.ownerEntityId(), contact.fleetId())
                && VectorFlight.norm(VectorFlight.subtract(position, contact.position()))
                    <= radiusMeters + contact.uncertaintyMeters(state.turn())).toList();
    }

    private static boolean live(GameState state, String viewer, String fleetId) {
        return state.fleets().stream().anyMatch(fleet -> fleet.id().equals(fleetId) && SensorCoverage.detects(state, viewer, fleet));
    }
}
