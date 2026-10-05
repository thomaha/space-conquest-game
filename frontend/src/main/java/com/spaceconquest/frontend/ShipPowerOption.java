package com.spaceconquest.frontend;

import com.spaceconquest.engine.ship.ShipComponentCatalog;

import java.util.ArrayList;
import java.util.List;

/** Electrical equipment choices for blueprint previews and staged commands. */
record ShipPowerOption(String label, String primaryId, boolean solarSupport) {
    static List<ShipPowerOption> available(List<String> technologies) {
        var options = new ArrayList<ShipPowerOption>();
        boolean solar = ShipComponentCatalog.powerResearched(List.of(ShipComponentCatalog.SOLAR_ARRAY_ID), technologies);
        for (String id : ShipComponentCatalog.POWER_MODULE_IDS) {
            if (ShipComponentCatalog.powerResearched(List.of(id), technologies)) {
                options.add(new ShipPowerOption(ShipComponentCatalog.module(id).name(), id, false));
                if (solar && !ShipComponentCatalog.SOLAR_ARRAY_ID.equals(id))
                    options.add(new ShipPowerOption("Solar arrays + " + ShipComponentCatalog.module(id).name(), id, true));
            }
        }
        return List.copyOf(options);
    }

    boolean matches(List<String> modules) {
        return ShipComponentCatalog.POWER_MODULE_IDS.stream().allMatch(id -> modules.contains(id)
                == (id.equals(primaryId) || solarSupport && id.equals(ShipComponentCatalog.SOLAR_ARRAY_ID)));
    }

    @Override public String toString() { return label; }
}
