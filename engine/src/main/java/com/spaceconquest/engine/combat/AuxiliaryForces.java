package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.Fleet;
import java.util.*;

/** Saved auxiliary hardware and stores. Wing ownership follows the host ship; batteries have explicit owners. */
public record AuxiliaryForces(List<CarrierWing> wings, List<OwnedBattery> batteries, List<AuxiliaryConstructionOrder> orders) {
    public AuxiliaryForces(List<CarrierWing> wings, List<OwnedBattery> batteries) { this(wings, batteries, List.of()); }
    public record OwnedBattery(String ownerEntityId, PlanetaryDefenseBattery battery) {
        public OwnedBattery {
            if (ownerEntityId == null || ownerEntityId.isBlank() || battery == null)
                throw new IllegalArgumentException("Battery owner and hardware are required");
        }
    }
    public AuxiliaryForces {
        wings = wings == null ? List.of() : List.copyOf(wings);
        batteries = batteries == null ? List.of() : List.copyOf(batteries);
        orders = orders == null ? List.of() : List.copyOf(orders);
        var ids = new HashSet<String>();
        for (var wing : wings) if (wing.id() == null || wing.id().isBlank() || !ids.add(wing.id()))
            throw new IllegalArgumentException("Wing IDs must be unique");
        ids.clear();
        for (var entry : batteries) if (entry.battery().id() == null || entry.battery().id().isBlank() || !ids.add(entry.battery().id()))
            throw new IllegalArgumentException("Battery IDs must be unique");
    }
    public static AuxiliaryForces empty() { return new AuxiliaryForces(List.of(), List.of()); }
    public List<CarrierWing> wingsFor(Fleet fleet) {
        return wings.stream().filter(w -> fleet.ships().stream().anyMatch(s -> s.id().equals(w.carrierShipId())
                && s.ownerEntityId().equals(fleet.ownerEntityId()) && s.currentHullHealth() > 0)).toList();
    }
    public AuxiliaryForces withWing(CarrierWing updated) {
        return new AuxiliaryForces(wings.stream().map(w -> w.id().equals(updated.id()) ? updated : w).toList(), batteries, orders);
    }
    public AuxiliaryForces withBattery(PlanetaryDefenseBattery updated) {
        return new AuxiliaryForces(wings, batteries.stream().map(b -> b.battery().id().equals(updated.id())
                ? new OwnedBattery(b.ownerEntityId(), updated) : b).toList(), orders);
    }
    /** Retains paid depletion and removes wings whose host was destroyed or removed. */
    public AuxiliaryForces afterCombat(TacticalCombatProcessor.CombatEngagementResult result, List<Fleet> fleets) {
        var updates = new HashMap<String, CarrierWing>();
        result.survivingAttackerWings().forEach(w -> updates.put(w.id(), w));
        result.survivingDefenderWings().forEach(w -> updates.put(w.id(), w));
        var surviving = wings.stream().map(w -> updates.getOrDefault(w.id(), w))
                .filter(w -> fleets.stream().anyMatch(f -> f.ships().stream().anyMatch(s -> s.id().equals(w.carrierShipId())
                        && s.currentHullHealth() > 0))).toList();
        return new AuxiliaryForces(surviving, batteries, orders);
    }
    public boolean reaches(Fleet fleet, double meters) {
        return Double.isFinite(meters) && meters >= 0 && wingsFor(fleet).stream()
                .anyMatch(w -> !w.stores().fire(w.weapon(), w.activeCraftCount(), meters).hits().isEmpty());
    }
}
