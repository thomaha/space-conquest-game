package com.spaceconquest.engine.habitation;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;

/** Checks the actual ship design and its owner's research before suspending passengers. */
public final class PassengerStasis {
    public static final String TECHNOLOGY_ID = "cryogenic_stasis";
    public static final String MODULE_ID = "cryogenic_stasis_pod";
    public static final int PASSENGERS_PER_POD = 100;

    private PassengerStasis() {}

    public static boolean available(GameState state, ShipInstance ship) {
        return availableFor(state, ship, 1);
    }

    public static boolean availableFor(GameState state, ShipInstance ship, int passengers) {
        if (state == null || ship == null) return false;
        ShipDesign design = state.shipDesigns().stream()
                .filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
        if (design == null || passengers <= 0 || capacity(design) < passengers) return false;
        String empireId = state.corporations().stream()
                .filter(corp -> corp.id().equals(ship.ownerEntityId()))
                .map(Corporation::empireId).findFirst().orElse(ship.ownerEntityId());
        return state.empires().stream().anyMatch(empire -> empire.id().equals(empireId)
                && empire.unlockedTechIds().contains(TECHNOLOGY_ID));
    }

    public static int capacity(ShipDesign design) {
        if (design == null || !design.equippedModuleIds().contains(MODULE_ID)) return 0;
        Integer captured = design.manufacturingProfile().stasisCapacity();
        return captured != null ? captured : (int) design.equippedModuleIds().stream()
                .filter(MODULE_ID::equals).count() * PASSENGERS_PER_POD;
    }
}
