package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Paid surface-yard assembly using the same remaining daily work ledger as full ship orders. */
public final class AuxiliaryConstruction {
    private AuxiliaryConstruction() {}
    public static boolean available(GameState state, String owner, String system, String body, String carrier,
                                    AuxiliaryHardware hardware, int units) {
        if (units <= 0 || !system.equals(ConstructionMaterials.systemForBody(state, body))) return false;
        if (hardware.kind().equals("BATTERY")) {
            if (carrier != null || units != 1) return false;
            double occupied = state.auxiliaryForces().batteries().stream().filter(b -> b.battery().planetId().equals(body))
                    .mapToDouble(b -> b.battery().surfaceSlotsOccupied()).sum();
            occupied += state.auxiliaryForces().orders().stream().filter(o -> o.bodyId().equals(body) && o.hardware().kind().equals("BATTERY"))
                    .mapToDouble(o -> o.units() * o.hardware().value("surfaceSlots")).sum();
            return occupied + hardware.value("surfaceSlots") <= hardware.value("bodyDefenseSlots");
        }
        var fleet = state.fleets().stream().filter(f -> f.ownerEntityId().equals(owner) && f.currentSystemId().equals(system)
                && !f.hasInterstellarOrder() && !f.location().inTransit() && f.location().isAt(FleetLocation.Site.surface(body)))
                .filter(f -> f.ships().stream().anyMatch(s -> s.id().equals(carrier) && s.ownerEntityId().equals(owner)
                        && s.currentHullHealth() > 0)).findFirst().orElse(null);
        if (fleet == null) return false;
        var ship = fleet.ships().stream().filter(s -> s.id().equals(carrier)).findFirst().orElseThrow();
        if (ShipRefitPlan.reserved(state, carrier)) return false;
        long reserved = state.auxiliaryForces().wings().stream().filter(w -> w.carrierShipId().equals(carrier)).mapToLong(CarrierWing::maxCraftCount).sum()
                + state.auxiliaryForces().orders().stream().filter(o -> Objects.equals(carrier, o.carrierShipId())).mapToLong(AuxiliaryConstructionOrder::units).sum();
        return reserved + units <= AuxiliaryApplicationCatalog.hangarCapacity(state, fleet, ship);
    }
    public static AuxiliaryConstructionOrder order(GameState state, String owner, String system, String body,
            String carrier, String chassisApp, String weaponApp, int units) {
        var app = AuxiliaryApplicationCatalog.application(chassisApp);
        var hardware = AuxiliaryApplicationCatalog.capture(state, owner, chassisApp);
        var weapon = ShipApplicationProduction.optimize(state, owner, CombatModuleCatalog.moduleForApplication(weaponApp));
        var weaponDefinition = CombatModuleCatalog.application(weapon.id());
        var bill = new HashMap<String, Double>();
        hardware.materialCostsKg().forEach((id, kg) -> bill.merge(id, units * kg, Double::sum));
        weapon.materialCostsKg().forEach((id, kg) -> bill.merge(id, units * kg, Double::sum));
        double hours = units * (ApplicationProduction.constructionWorkHours(state, owner, chassisApp, app.costToBuildPerUnit())
                + ApplicationProduction.constructionWorkHours(state, owner, weaponApp, weaponDefinition.costToBuildPerUnit()));
        int complexity = Math.max(app.calculateOptimizedComplexity(ApplicationProduction.modifiersForOwner(state, owner, chassisApp)), weapon.complexityLevel());
        return new AuxiliaryConstructionOrder("aux_" + UUID.randomUUID(), owner, system, body, carrier, chassisApp, weaponApp,
                units, hardware, weapon, complexity, hours, 0, bill, Map.of());
    }
    public static GameState advance(GameState state, Map<String, Double> remainingWork, Map<String, Integer> paidWorkers) {
        var current = state; var pending = new ArrayList<AuxiliaryConstructionOrder>();
        var reservedOrders = new ArrayList<>(state.auxiliaryForces().orders());
        var wings = new ArrayList<>(state.auxiliaryForces().wings()); var batteries = new ArrayList<>(state.auxiliaryForces().batteries());
        for (var order : state.auxiliaryForces().orders()) {
            var withoutSelf = current.toBuilder().auxiliaryForces(new AuxiliaryForces(wings, batteries,
                    reservedOrders.stream().filter(o -> !o.id().equals(order.id())).toList())).build();
            if (!available(withoutSelf, order.ownerId(), order.systemId(), order.bodyId(), order.carrierShipId(), order.hardware(), order.units())
                    || ShipManufacturingCapacity.forYard(current, order.ownerId(), order.systemId(), order.bodyId()) < order.complexity()) {
                pending.add(order); continue;
            }
            String key = order.systemId() + "/" + order.bodyId();
            double work = remainingWork.computeIfAbsent(key, ignored -> ShipyardWorkCapacity.forYard(state, order.ownerId(),
                    order.systemId(), order.bodyId(), paidWorkers).workPerDay());
            var step = ConstructionProgress.advance(current, order.bodyId(), order.ownerId(), order.requiredKg(),
                    order.consumedKg(), order.completedHours(), order.requiredHours(), work);
            remainingWork.put(key, Math.max(0, work - (step.workHours() - order.completedHours()))); current = step.state();
            if (!step.complete()) { pending.add(order.progress(step.workHours(), step.consumedKg())); continue; }
            reservedOrders.removeIf(o -> o.id().equals(order.id()));
            if (order.hardware().kind().equals("WING")) wings.add(new CarrierWing(order.id(), order.carrierShipId(),
                    CarrierWing.TYPE_INTERCEPTOR_FIGHTER, order.units(), order.units(), order.hardware().value("hullHealth"),
                    order.weaponApplicationId(), CarrierWing.TARGET_CAPITAL_SHIPS, order.weapon(), AuxiliaryWeaponStores.empty(), order.hardware()));
            else batteries.add(new AuxiliaryForces.OwnedBattery(order.ownerId(), new PlanetaryDefenseBattery(order.id(), order.bodyId(),
                    order.weaponApplicationId(), (int) order.hardware().value("surfaceSlots"), true, order.weapon(), AuxiliaryWeaponStores.empty(),
                    order.hardware(), order.hardware().value("hullHealth"), ShipModuleDamage.empty())));
        }
        return BatteryMaintenance.advance(current.toBuilder().auxiliaryForces(new AuxiliaryForces(wings, batteries, pending)).build(),
                remainingWork, paidWorkers);
    }
}
