package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import java.util.ArrayList;

/** Queues captured chassis and weapon construction; no hardware or stores are granted by submission. */
public record BuildAuxiliaryWeaponCommand(String ownerId, String systemId, String bodyId, String carrierShipId,
        String chassisApplicationId, String weaponApplicationId, int units) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerId == null || systemId == null || bodyId == null || chassisApplicationId == null
                || weaponApplicationId == null || units <= 0) return false;
        try {
            var owner = state.empires().stream().filter(e -> e.id().equals(ownerId)
                    && e.controlledSystemIds().contains(systemId)).findFirst().orElse(null);
            var app = AuxiliaryApplicationCatalog.application(chassisApplicationId);
            var weapon = CombatModuleCatalog.moduleForApplication(weaponApplicationId);
            if (owner == null || app == null || !AuxiliaryApplicationCatalog.researched(state, ownerId, chassisApplicationId)
                    || !CombatModuleCatalog.researched(java.util.List.of(weapon.id()), owner.unlockedTechIds())
                    || !weapon.operationalStats().containsKey("weaponDamage")) return false;
            var order = AuxiliaryConstruction.order(state, ownerId, systemId, bodyId, carrierShipId, chassisApplicationId, weaponApplicationId, units);
            return AuxiliaryConstruction.available(state, ownerId, systemId, bodyId, carrierShipId, order.hardware(), units)
                    && ShipManufacturingCapacity.forYard(state, ownerId, systemId, bodyId) >= order.complexity();
        } catch (IllegalArgumentException | ArithmeticException ex) { return false; }
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var forces = state.auxiliaryForces(); var orders = new ArrayList<>(forces.orders());
        orders.add(AuxiliaryConstruction.order(state, ownerId, systemId, bodyId, carrierShipId, chassisApplicationId, weaponApplicationId, units));
        return state.toBuilder().auxiliaryForces(new AuxiliaryForces(forces.wings(), forces.batteries(), orders)).build();
    }
}
