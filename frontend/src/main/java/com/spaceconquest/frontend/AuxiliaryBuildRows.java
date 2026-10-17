package com.spaceconquest.frontend;

import com.spaceconquest.control.command.BuildAuxiliaryWeaponCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Background-only construction quotes for the surface yard at the fleet's actual site. */
final class AuxiliaryBuildRows {
    private AuxiliaryBuildRows() {}
    static List<AuxiliarySupplyCard.Row> inspect(GameState state, Fleet fleet, String owner) {
        if (fleet.location().inTransit() || fleet.hasInterstellarOrder()
                || fleet.location().current().kind() != FleetLocation.Kind.SURFACE) return List.of();
        return inspect(state, fleet.currentSystemId(), fleet.location().current().entityId(), owner,
                fleet.ships().stream().map(ShipInstance::id).toList(), true);
    }
    static List<AuxiliarySupplyCard.Row> inspectSurface(GameState state, String body, String owner) {
        String system = com.spaceconquest.engine.industry.ConstructionMaterials.systemForBody(state, body);
        return system == null ? List.of() : inspect(state, system, body, owner, List.of(), false);
    }
    private static List<AuxiliarySupplyCard.Row> inspect(GameState state, String system, String body, String owner,
            List<String> carriers, boolean includeWings) {
        var rows = new ArrayList<AuxiliarySupplyCard.Row>();
        for (var order : state.auxiliaryForces().orders()) if (owner.equals(order.ownerId()) && body.equals(order.bodyId())
                && (includeWings || order.hardware().kind().equals("BATTERY")))
            rows.add(new AuxiliarySupplyCard.Row(order.id(), String.format(Locale.ROOT, "Building %s: %.1f / %.1f work hours; materials paid as work advances",
                    order.hardware().kind().equals("WING") ? "wing" : "battery", order.completedHours(), order.requiredHours()), null, false));
        for (var chassis : AuxiliaryApplicationCatalog.applications()) {
            if (!AuxiliaryApplicationCatalog.researched(state, owner, chassis.id())) continue;
            for (var weaponId : CombatModuleCatalog.IDS) {
                var weaponApp = CombatModuleCatalog.application(weaponId);
                if (!weaponApp.combatModule().operationalStats().containsKey("weaponDamage")) continue;
                var hosts = chassis.auxiliaryHardware().kind().equals("WING") ? carriers
                        : Collections.<String>singletonList(null);
                for (var host : hosts) {
                    var command = new BuildAuxiliaryWeaponCommand(owner, system, body, host, chassis.id(), weaponApp.id(), 1);
                    if (!command.validate(state)) continue;
                    var quote = AuxiliaryConstruction.order(state, owner, system, body, host, chassis.id(), weaponApp.id(), 1);
                    String key = "build-" + chassis.id() + "-" + weaponId + "-" + (host == null ? body : host);
                    String text = String.format(Locale.ROOT, "Queue %s with %s%s: %.1f work hours; %s",
                            chassis.name(), weaponApp.name(), host == null ? "" : " for " + host, quote.requiredHours(), quote.requiredKg());
                    rows.add(new AuxiliarySupplyCard.Row(key, text, command, true));
                }
            }
        }
        return List.copyOf(rows);
    }
}
