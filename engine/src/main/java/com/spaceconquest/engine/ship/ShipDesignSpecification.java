package com.spaceconquest.engine.ship;

import java.util.List;

/** Player choices for the current medium-frame designer. Contains no calculated blueprint values. */
public record ShipDesignSpecification(String id, String name, String ownerEntityId, String role,
                                      String hullMaterialId, List<String> moduleIds,
                                      String armorMaterialId, double armorThicknessCm) {
    public ShipDesignSpecification {
        moduleIds = moduleIds == null ? List.of() : List.copyOf(moduleIds);
    }

    /** Compatibility input: computed stats and manufacturing metadata are deliberately ignored. */
    public static ShipDesignSpecification from(ShipDesign design) {
        if (design == null || design.isProprietaryCorporateDesign()) return null;
        return new ShipDesignSpecification(design.id(), design.name(), design.ownerEntityId(), design.role(),
                design.hullMaterialId(), design.equippedModuleIds(), design.armorMaterialId(), design.armorThicknessCm());
    }
}
