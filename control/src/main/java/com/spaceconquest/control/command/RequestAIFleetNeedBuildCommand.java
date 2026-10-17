package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Revalidates one concrete shortage and stages a fully affordable researched build for that mission. */
public record RequestAIFleetNeedBuildCommand(String ownerId, String sourceDesignId, String yardSystemId,
        String targetSystemId, ShipDeploymentIntent.Kind kind, String targetId) implements GameCommand {
    private record Quote(GameState registered, ShipDesign design, EmpireFleetNeeds.Need need) {}
    @Override public boolean validate(GameState state) { return quote(state) != null; }
    @Override public GameState apply(GameState state) {
        var quote = quote(state); if (quote == null) return state;
        var built = new QueueShipBuildCommand(ownerId, quote.design().id(), yardSystemId).apply(quote.registered());
        var order = built.shipConstructionOrders().getLast();
        return built.withShipConstructionOrders(built.shipConstructionOrders().stream().map(item -> item.id().equals(order.id())
                ? item.withDeployment(quote.need().intent(ownerId, state.turn())) : item).toList());
    }
    private Quote quote(GameState state) {
        if (state == null || ownerId == null || sourceDesignId == null || yardSystemId == null || targetSystemId == null || kind == null || targetId == null) return null;
        var empire = state.empires().stream().filter(item -> ownerId.equals(item.id())).findFirst().orElse(null);
        if (empire == null || state.shipConstructionOrders().stream().anyMatch(order -> ownerId.equals(order.ownerEntityId()))) return null;
        var need = EmpireFleetNeeds.assess(state, empire).stream().filter(item -> targetSystemId.equals(item.systemId()) && kind == item.kind() && targetId.equals(item.targetId())).findFirst().orElse(null);
        var source = state.shipDesigns().stream().filter(design -> sourceDesignId.equals(design.id()) && ownerId.equals(design.ownerEntityId()) && EmpireFleetPlanner.combat(design.role())).findFirst().orElse(null);
        if (need == null || source == null) return null;
        var design = researched(state, empire, source, kind); if (design == null) return null;
        var registered = AIShipResearchRevision.register(state, design); var command = new QueueShipBuildCommand(ownerId, design.id(), yardSystemId);
        if (!CombatModuleCatalog.researched(design.equippedModuleIds(), empire.unlockedTechIds()) || !command.validate(registered)) return null;
        var offer = command.resolveYard(registered); var estimate = ShipConstructionRequirements.estimate(design);
        var purchase = state.orbitalStations().stream().anyMatch(station -> offer.yardId().equals(station.id()))
                ? ConstructionMaterials.buyOrbitalUpTo(registered, yardSystemId, offer.yardId(), ownerId, estimate.materialsKg(), Double.MAX_VALUE)
                : ConstructionMaterials.buyUpTo(registered, offer.yardId(), ownerId, estimate.materialsKg());
        double fees = offer.contract() == null ? 0 : estimate.workUnits() * offer.contract().feeCreditsPerWorkUnit();
        return purchase.covers(estimate.materialsKg()) && com.spaceconquest.engine.economy.EconomicOwnership.cash(purchase.state(), ownerId)
                >= fees + FleetNeedsPolicy.current().treasuryReserveCredits() ? new Quote(registered, design, need) : null;
    }
    private static ShipDesign researched(GameState state, Empire empire, ShipDesign source, ShipDeploymentIntent.Kind kind) {
        var modules = new ArrayList<>(source.equippedModuleIds());
        if (CombatModuleCatalog.weaponDamage(source) <= 0) {
            String weapon = CombatModuleCatalog.IDS.stream().sorted().filter(id -> CombatModuleCatalog.module(id).operationalStats().getOrDefault("weaponDamage", 0.0) > 0
                    && CombatModuleCatalog.researched(List.of(id), empire.unlockedTechIds())).findFirst().orElse(null);
            if (weapon == null) return null;
            modules.add(weapon);
        }
        String role = kind == ShipDeploymentIntent.Kind.ESCORT ? ShipRole.ESCORT : source.role();
        if (modules.equals(source.equippedModuleIds()) && role.equals(source.role())) return AIShipResearchRevision.current(state, source);
        String id = "design_ai_" + UUID.randomUUID();
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification(id, source.name() + " (mission)",
                empire.id(), role, source.hullMaterialId(), modules, source.armorMaterialId(), source.armorThicknessCm(), source.hullFrame() == null ? null : source.hullFrame().totalSlots(),
                ShipBlueprintAssembly.identitiesFor(source, modules, id)));
        if (!evaluated.valid()) return null;
        return state.shipDesigns().stream().filter(design -> empire.id().equals(design.ownerEntityId()) && role.equals(design.role())
                && modules.equals(design.equippedModuleIds()) && design.manufacturingProfile().equals(evaluated.design().manufacturingProfile())
                && design.combatModules().equals(evaluated.design().combatModules()) && design.powerProfile().equals(evaluated.design().powerProfile())
                && source.hullMaterialId().equals(design.hullMaterialId()) && source.armorMaterialId().equals(design.armorMaterialId())
                && source.armorThicknessCm() == design.armorThicknessCm() && Objects.equals(evaluated.design().hullFrame(), design.hullFrame())).findFirst().orElse(evaluated.design());
    }
}
