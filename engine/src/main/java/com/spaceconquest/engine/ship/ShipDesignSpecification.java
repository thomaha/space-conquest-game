package com.spaceconquest.engine.ship;

import java.util.List;

/** Player choices including freely selected hull capacity. Contains no calculated blueprint values. */
public record ShipDesignSpecification(String id, String name, String ownerEntityId, String role,
                                      String hullMaterialId, List<String> moduleIds,
                                      String armorMaterialId, double armorThicknessCm, Integer hullCapacity, List<String> installationIds) {
    public ShipDesignSpecification(String id, String name, String ownerEntityId, String role, String hullMaterialId,
                                   List<String> moduleIds, String armorMaterialId, double armorThicknessCm, Integer hullCapacity) {
        this(id, name, ownerEntityId, role, hullMaterialId, moduleIds, armorMaterialId, armorThicknessCm, hullCapacity, List.of());
    }
    public ShipDesignSpecification(String id, String name, String ownerEntityId, String role, String hullMaterialId,
                                   List<String> moduleIds, String armorMaterialId, double armorThicknessCm) {
        this(id, name, ownerEntityId, role, hullMaterialId, moduleIds, armorMaterialId, armorThicknessCm, null);
    }
    public ShipDesignSpecification {
        moduleIds = moduleIds == null ? List.of() : List.copyOf(moduleIds);
        installationIds = installationIds == null ? List.of() : List.copyOf(installationIds);
    }

    /** Compatibility input: computed stats and manufacturing metadata are deliberately ignored. */
    public static ShipDesignSpecification from(ShipDesign design) {
        if (design == null || design.isProprietaryCorporateDesign()) return null;
        return new ShipDesignSpecification(design.id(), design.name(), design.ownerEntityId(), design.role(),
                design.hullMaterialId(), design.equippedModuleIds(), design.armorMaterialId(), design.armorThicknessCm(), design.hullFrame() == null ? null : design.hullFrame().totalSlots(),
                design.assembly() == null ? List.of() : design.assembly().identities());
    }
}
