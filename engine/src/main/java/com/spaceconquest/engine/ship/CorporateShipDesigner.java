package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import java.util.*;

/** Bounded freely sized corporate candidates captured by the same factory as player models. */
public final class CorporateShipDesigner {
    private CorporateShipDesigner() {}
    public static ShipDesign choose(GameState state, String corporationId, String role, double desiredCargoKg) {
        return choose(state, corporationId, role, desiredCargoKg, CorporateShipDesignPolicy.current());
    }
    public static ShipDesign choose(GameState state, String corporationId, String role, double desiredCargoKg, CorporateShipDesignPolicy policy) {
        if (state == null || corporationId == null || policy == null) return null;
        var corporation = state.corporations().stream().filter(corp -> corporationId.equals(corp.id())).findFirst().orElse(null);
        var home = EconomicOwnership.empireFor(state, corporationId);
        if (corporation == null || home == null || !EconomicOwnership.corporationsAllowed(home) || !Double.isFinite(desiredCargoKg) || desiredCargoKg < 0) return null;
        var candidates = new ArrayList<ShipDesign>(); int attempts = 0;
        for (String drive : policy.drives()) {
            if (!PropulsionCatalog.researched(List.of(drive), home.unlockedTechIds())) continue;
            boolean reactor = !PropulsionCatalog.reactorFuels(drive).isEmpty();
            for (int count = 1; count <= policy.maximumCargoModules(); count++) for (String cargo : policy.cargoModules())
                for (int tanks = 1; tanks <= policy.maximumFuelTanks(); tanks++) for (String material : policy.hullMaterials()) {
                    if (++attempts > policy.maximumCandidates()) return select(state, candidates, desiredCargoKg);
                    var modules = new ArrayList<>(List.of(drive, reactor ? ShipComponentCatalog.FISSION_REACTOR_ID : ShipComponentCatalog.CHEMICAL_GENERATOR_ID,
                            ShipComponentCatalog.BATTERY_ID, reactor ? ShipComponentCatalog.REACTOR_TANK_ID : ShipComponentCatalog.GENERATOR_TANK_ID));
                    for (int index = 0; index < count; index++) modules.add(cargo);
                    if (ShipRole.MINING_SHIP.equals(role)) {
                        var mining = ShipMiningModuleCatalog.ids().stream().filter(id -> ShipMiningModuleCatalog.researched(List.of(id), home.unlockedTechIds())).findFirst().orElse(null);
                        if (mining == null) return null;
                        modules.add(mining);
                    }
                    for (int index = 0; index < tanks; index++) modules.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
                    int slots = modules.stream().map(ShipComponentCatalog::module).mapToInt(ShipModule::slotCost).sum();
                    int capacity = (int)Math.ceil(slots * (1 + policy.spareCapacityFraction()));
                    if (capacity > HullTechnology.maximumCapacity(state, corporationId)) continue;
                    String id = "design_corp_" + UUID.randomUUID();
                    var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification(id, corporation.name() + " " + role,
                            corporationId, role, material, modules, policy.armorMaterial(), policy.armorThicknessCm(), capacity));
                    if (evaluated.valid()) candidates.add(evaluated.design());
                }
            if (!candidates.isEmpty()) return select(state, candidates, desiredCargoKg);
        }
        return select(state, candidates, desiredCargoKg);
    }
    private static ShipDesign select(GameState state, List<ShipDesign> candidates, double desiredCargoKg) {
        var selected = candidates.stream().min(Comparator.comparingDouble((ShipDesign design) -> Math.max(0, desiredCargoKg - design.maxCargoMassKg()))
                .thenComparingDouble(design -> design.assembly().total().workHours()).thenComparingDouble(ShipDesign::totalDryMassKg)).orElse(null);
        if (selected == null) return null;
        return state.shipDesigns().stream().filter(design -> equivalent(design, selected)).sorted(Comparator.comparing(ShipDesign::id)).findFirst().orElse(selected);
    }
    public static boolean equivalent(ShipDesign left, ShipDesign right) {
        if (left.assembly() == null || right.assembly() == null || left.assembly().installations().size() != right.assembly().installations().size()) return false;
        return left.ownerEntityId().equals(right.ownerEntityId()) && Objects.equals(left.role(), right.role())
                && Objects.equals(left.hullFrame(), right.hullFrame()) && left.armorMaterialId().equals(right.armorMaterialId()) && left.armorThicknessCm() == right.armorThicknessCm()
                && left.equippedModuleIds().equals(right.equippedModuleIds()) && left.manufacturingProfile().equals(right.manufacturingProfile())
                && left.powerProfile().equals(right.powerProfile()) && left.sensorProfile().equals(right.sensorProfile()) && left.combatModules().equals(right.combatModules())
                && left.assembly().frame().equals(right.assembly().frame()) && left.assembly().armor().equals(right.assembly().armor()) && left.assembly().integration().equals(right.assembly().integration())
                && java.util.stream.IntStream.range(0, left.assembly().installations().size()).allMatch(index -> {
                    var a = left.assembly().installations().get(index); var b = right.assembly().installations().get(index);
                    return a.hardware().equals(b.hardware()) && a.applicationId().equals(b.applicationId()) && a.production().equals(b.production()) && a.installationWorkHours() == b.installationWorkHours();
                });
    }
}
