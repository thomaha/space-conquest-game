package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import com.spaceconquest.engine.galaxy.SensorSharing;
import com.spaceconquest.engine.governance.DiplomacyProcessor;

/** Travel saves optional shield energy unless the shared view has live or recent hostile observations. */
public final class FleetShieldPolicy {
    public enum Mode {
        AUTOMATIC("Automatic"), ALWAYS_CHARGE("Always charge"), CONSERVE_ENERGY("Conserve energy");
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public record Decision(boolean recharge, String reason) {}
    private FleetShieldPolicy() {}

    public static boolean travelling(Fleet fleet) {
        return fleet.location().inTransit() || fleet.hasInterstellarOrder() || fleet.flightMotion() != null;
    }

    public static boolean recharge(GameState state, Fleet fleet, boolean travelling) {
        return decision(state, fleet, travelling).recharge();
    }

    public static Decision decision(GameState state, Fleet fleet, boolean travelling) {
        if (fleet.shieldPolicy() == Mode.CONSERVE_ENERGY) return new Decision(false, "Deferred by conserve energy policy.");
        if (fleet.shieldPolicy() == Mode.ALWAYS_CHARGE) return new Decision(true, "Eligible by always charge policy.");
        if (!travelling) return new Decision(true, "Eligible while stationary under automatic policy.");
        var diplomacy = new DiplomacyProcessor();
        String home = SensorSharing.homeEmpire(state, fleet.ownerEntityId());
        String owner = home.isBlank() ? fleet.ownerEntityId() : home;
        boolean live = state.fleets().stream().anyMatch(enemy -> !enemy.ships().isEmpty()
                && fleet.currentSystemId().equals(enemy.currentSystemId())
                && DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(diplomacy.getDiplomaticTier(
                        owner, empire(state, enemy.ownerEntityId()), state.diplomaticRelations()))
                && SensorCoverage.detects(state, fleet.ownerEntityId(), enemy));
        if (live) return new Decision(true, "Eligible for a live hostile contact under automatic policy.");
        if (!com.spaceconquest.engine.galaxy.SensorContactMemory.threats(state, fleet, Double.POSITIVE_INFINITY).isEmpty())
            return new Decision(true, "Eligible for a recent last-known threat under automatic policy.");
        return new Decision(false, "Deferred during travel: no live or recent hostile contact.");
    }

    private static String empire(GameState state, String owner) {
        String home = SensorSharing.homeEmpire(state, owner);
        return home.isBlank() ? owner : home;
    }
}
