package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.Map;

/** Pure paid supply operations for explicit tactical inputs; the tick owner applies both returned states. */
public final class AuxiliaryWeaponResupply {
    private AuxiliaryWeaponResupply() {}
    public record WingLoad(CarrierWing wing, ShipInstance carrier) {}
    public static WingLoad loadWing(CarrierWing wing, ShipInstance carrier, int rounds, double chargeKwh) {
        if (!wing.carrierShipId().equals(carrier.id())) throw new IllegalArgumentException("Wrong carrier");
        var transfer = wing.stores().loadFrom(carrier, wing.weapon(), rounds, chargeKwh);
        wing.hardware().checkStores(transfer.stores(), wing.maxCraftCount());
        return new WingLoad(wing.withStores(transfer.stores()), transfer.source());
    }
    public record BatteryPurchase(GameState state, PlanetaryDefenseBattery battery, boolean purchased) {}
    /** Whole requested batches only; unsuccessful projected purchases do not commit stock or credits. */
    public static BatteryPurchase buyBatteryAmmunition(GameState state, String ownerId, PlanetaryDefenseBattery battery, int rounds) {
        if (rounds <= 0 || battery.weapon().ammunitionId().isBlank()) throw new IllegalArgumentException("Invalid ammunition request");
        var weapon = battery.weapon();
        double mass = rounds * weapon.operationalStats().get("ammunitionMassKg");
        var requested = new java.util.HashMap<>(battery.stores().ammunitionKg());
        requested.merge(weapon.ammunitionId(), mass, Double::sum);
        battery.hardware().checkStores(new AuxiliaryWeaponStores(requested, battery.stores().energyKwh()), 1);
        var bought = ConstructionMaterials.buyUpTo(state, battery.planetId(), ownerId, Map.of(weapon.ammunitionId(), mass));
        if (bought.acquiredKg().getOrDefault(weapon.ammunitionId(), 0.0) + 1e-9 < mass)
            return new BatteryPurchase(state, battery, false);
        var cargo = new java.util.HashMap<>(battery.stores().ammunitionKg());
        cargo.merge(weapon.ammunitionId(), mass, Double::sum);
        return new BatteryPurchase(bought.state(), battery.withStores(new AuxiliaryWeaponStores(cargo, battery.stores().energyKwh())), true);
    }
    public record BatteryCharge(PlanetaryDefenseBattery battery, ShipInstance source) {}
    /** Explicit owner and surface location validation until batteries become campaign-owned entities. */
    public static BatteryCharge chargeBattery(GameState state, String ownerId, PlanetaryDefenseBattery battery,
                                               String sourceShipId, double chargeKwh) {
        var fleet = state.fleets().stream().filter(f -> ownerId.equals(f.ownerEntityId()) && !f.location().inTransit()
                && f.location().current().kind() == FleetLocation.Kind.SURFACE
                && battery.planetId().equals(f.location().current().entityId()))
                .filter(f -> f.ships().stream().anyMatch(s -> sourceShipId.equals(s.id()))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No owned surface power source at battery"));
        var source = fleet.ships().stream().filter(s -> s.id().equals(sourceShipId) && ownerId.equals(s.ownerEntityId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Foreign power source"));
        var transfer = battery.stores().loadFrom(source, battery.weapon(), 0, chargeKwh);
        battery.hardware().checkStores(transfer.stores(), 1);
        return new BatteryCharge(battery.withStores(transfer.stores()), transfer.source());
    }
}
