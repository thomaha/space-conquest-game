package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Finite standalone tactical stores; callers retain the returned state after firing or loading. */
public record AuxiliaryWeaponStores(Map<String, Double> ammunitionKg, double energyKwh) {
    public AuxiliaryWeaponStores {
        ammunitionKg = ammunitionKg == null ? Map.of() : Map.copyOf(ammunitionKg);
        if (!Double.isFinite(energyKwh) || energyKwh < 0 || ammunitionKg.values().stream().anyMatch(v -> !Double.isFinite(v) || v < 0))
            throw new IllegalArgumentException("Invalid auxiliary stores");
    }
    public static AuxiliaryWeaponStores empty() { return new AuxiliaryWeaponStores(Map.of(), 0); }
    public record Fire(AuxiliaryWeaponStores stores, List<WeaponHit> hits, double spentKwh, String reason) {
        public Fire { hits = List.copyOf(hits); }
    }
    public Fire fire(ShipModule weapon, int units, double meters) {
        if (units < 0 || !Double.isFinite(meters) || meters < 0) throw new IllegalArgumentException("Invalid auxiliary firing input");
        if (units == 0) return new Fire(this, List.of(), 0, "No active weapon units");
        if (meters > weapon.operationalStats().getOrDefault("weaponRangeMeters", 0.0))
            return new Fire(this, List.of(), 0, "Out of range");
        var salvo = WeaponSalvo.of(weapon);
        if (salvo.shots() <= 0 || salvo.damage() <= 0) return new Fire(this, List.of(), 0, "No offensive capability");
        double upkeep = units * weapon.powerDrawKw() * TacticalCombatProcessor.SECONDS_PER_ROUND / 3600;
        if (energyKwh + 1e-9 < upkeep) return new Fire(this, List.of(), 0, "Insufficient energy for weapon upkeep");
        double perShot = salvo.energyKwh() / salvo.shots();
        double remaining = Math.max(0, energyKwh - upkeep);
        int desired = Math.multiplyExact(units, salvo.shots());
        int count = Math.min(desired, ShipWeaponAmmunition.rounds(ammunitionKg, weapon));
        if (perShot > 0) count = Math.min(count, (int) Math.min(Integer.MAX_VALUE, Math.floor((remaining + 1e-9) / perShot)));
        var cargo = new HashMap<>(ammunitionKg);
        ShipWeaponAmmunition.reserve(cargo, weapon, count);
        cargo.entrySet().removeIf(entry -> entry.getValue() <= 1e-9);
        double spent = upkeep + count * perShot;
        var hits = new ArrayList<WeaponHit>();
        for (int shot = 0; shot < count; shot++) hits.add(new WeaponHit(weapon, salvo.damage() / salvo.shots(), meters));
        String reason = count >= desired ? "" : ShipWeaponAmmunition.rounds(cargo, weapon) == 0
                ? "No whole round remains" : "Insufficient firing energy";
        return new Fire(new AuxiliaryWeaponStores(cargo, Math.max(0, energyKwh - spent)), hits, spent, reason);
    }

    public record Transfer(AuxiliaryWeaponStores stores, ShipInstance source) {}
    /** Moves already-carried rounds and stored electricity atomically; no generator energy is invented. */
    public Transfer loadFrom(ShipInstance source, ShipModule weapon, int rounds, double chargeKwh) {
        if (rounds < 0 || !Double.isFinite(chargeKwh) || chargeKwh < 0 || source.currentHullHealth() <= 0
                || rounds > 0 && weapon.ammunitionId().isBlank()) throw new IllegalArgumentException("Invalid auxiliary resupply");
        var power = source.powerState();
        if (power == null && chargeKwh > 0 || power != null && power.batteryChargeKwh() + 1e-9 < chargeKwh)
            throw new IllegalArgumentException("Insufficient stored electricity");
        double mass = rounds * weapon.operationalStats().getOrDefault("ammunitionMassKg", 0.0);
        var used = rounds == 0 ? Map.<String, Double>of() : Map.of(weapon.ammunitionId(), mass);
        var loadedSource = ShipWeaponAmmunition.consume(source, used);
        if (power != null) loadedSource = loadedSource.withPowerState(new ShipPowerState(power.generatorMaterialsKg(),
                power.chemicalMixture(), power.reactorFuel(), Math.max(0, power.batteryChargeKwh() - chargeKwh),
                power.arraysDeployed(), power.arrayCondition(), power.orientationFraction(), power.unmetEssentialHours(),
                power.unmetDriveKwh(), power.unmetCargoKwh(), power.lastUnmetEssentialKwh(), power.chargedInputKwhToday(),
                power.cargoPreservation(), power.rescueStatus(), power.shedServices()));
        var cargo = new HashMap<>(ammunitionKg);
        used.forEach((id, kg) -> cargo.merge(id, kg, Double::sum));
        return new Transfer(new AuxiliaryWeaponStores(cargo, energyKwh + chargeKwh), loadedSource);
    }
}
