package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Creates an ordinary researched revision for a new order without modifying ships or pending orders. */
final class AIShipResearchRevision {
    private AIShipResearchRevision() {}
    static ShipDesign current(GameState state, ShipDesign source) {
        if (!ShipApplicationProduction.developmentChanged(state, source)) return source;
        var evaluation = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification(source.id() + "_research_revision",
                source.name() + " (researched)", source.ownerEntityId(), source.role(), source.hullMaterialId(),
                source.equippedModuleIds(), source.armorMaterialId(), source.armorThicknessCm(), source.hullFrame() == null ? null : source.hullFrame().totalSlots(),
                source.assembly() == null ? List.of() : source.assembly().identities()));
        if (!evaluation.valid()) return source;
        var revised = evaluation.design();
        return state.shipDesigns().stream().filter(design -> source.ownerEntityId().equals(design.ownerEntityId())
                && source.role().equals(design.role()) && source.equippedModuleIds().equals(design.equippedModuleIds())
                && source.hullMaterialId().equals(design.hullMaterialId()) && source.armorMaterialId().equals(design.armorMaterialId())
                && source.armorThicknessCm() == design.armorThicknessCm() && !ShipApplicationProduction.developmentChanged(state, design)
                && revised.totalThrustN() == design.totalThrustN() && revised.maxCargoMassKg() == design.maxCargoMassKg()
                && revised.totalDryMassKg() == design.totalDryMassKg() && revised.fuelCapacityKg() == design.fuelCapacityKg()
                && revised.powerBalanceKw() == design.powerBalanceKw() && revised.manufacturingProfile().equals(design.manufacturingProfile())
                && revised.powerProfile().equals(design.powerProfile()) && revised.sensorProfile().equals(design.sensorProfile())
                && revised.combatModules().equals(design.combatModules()) && java.util.Objects.equals(revised.hullFrame(), design.hullFrame())).findFirst().orElseGet(() -> copyWithId(revised));
    }
    private static ShipDesign copyWithId(ShipDesign design) {
        return new ShipDesign("design_ai_" + UUID.randomUUID(), design.name(), design.ownerEntityId(), design.role(), design.hullMaterialId(),
                design.equippedModuleIds(), design.armorMaterialId(), design.armorThicknessCm(), design.totalDryMassKg(), design.maxCargoMassKg(),
                design.fuelCapacityKg(), design.powerBalanceKw(), design.calculatedStructuralIntegrity(), design.minLaunchThrustRequiredN(), design.totalThrustN(),
                design.isValidForLaunch(), design.isProprietaryCorporateDesign(), design.manufacturingProfile(), design.powerProfile(), design.sensorProfile(), design.combatModules(), design.hullFrame(), design.assembly());
    }
    static GameState register(GameState state, ShipDesign design) {
        if (state.shipDesigns().stream().anyMatch(value -> value.id().equals(design.id()))) return state;
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(design); return state.withShipDesigns(designs);
    }
}
