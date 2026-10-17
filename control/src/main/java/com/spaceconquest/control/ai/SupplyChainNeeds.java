package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.InfrastructureService;
import java.util.*;

/** Bounded, snapshot-only dependency discovery from funded local buyers and stranded carriers. */
public final class SupplyChainNeeds {
    public record Goal(String bodyId, String applicationId, String materialId) {}
    private SupplyChainNeeds() {}
    public static List<Goal> goals(GameState state, Empire empire) {
        var result = new LinkedHashMap<String, Goal>();
        var hubs = state.commercialHubs().stream().filter(h -> PowerInvestmentQuote.controls(state, empire, h.entityId()))
                .sorted(Comparator.comparing(CommercialHub::id)).toList();
        for (int index = 0; index < Math.min(8, hubs.size()) && result.size() < 32; index++) {
            var hub = hubs.get((int) Math.floorMod(state.turn() + index, hubs.size()));
            double buyerCash = state.marketAccounts().stream().filter(a -> a.hubId().equals(hub.id()))
                    .mapToDouble(a -> a.unsettledSalesCredits()).findFirst().orElse(0);
            if (buyerCash <= 0) continue;
            for (var order : hub.activeOrders().values().stream().filter(o -> o.demandKg() > o.supplyKg()
                    && o.pricePerKg() > 0 && Double.isFinite(o.pricePerKg())).sorted(Comparator.comparingDouble(MarketOrder::shortcomingScore).reversed()
                            .thenComparing(MarketOrder::resourceId)).limit(12).toList())
                visit(state, empire, hub, order.resourceId(), result, new HashSet<>(), 0);
            boolean carrier = state.fleets().stream().filter(f -> empire.id().equals(f.ownerEntityId()) && !f.location().inTransit() && !f.hasInterstellarOrder())
                    .anyMatch(f -> hub.entityId().equals(f.location().current().entityId()) && f.ships().stream().anyMatch(s -> s.currentHullHealth() > 0 &&
                            state.shipDesigns().stream().anyMatch(d -> d.id().equals(s.designId())
                                    && com.spaceconquest.engine.ship.ShipRole.CARGO_TRANSPORT.equals(d.role()) && d.maxCargoMassKg() > 0
                                    && com.spaceconquest.engine.ship.PropulsionCatalog.mainDrive(d.equippedModuleIds()) != null)));
            boolean provider = state.industrialFacilities().stream().anyMatch(f -> f.planetId().equals(hub.entityId())
                    && InfrastructureService.find(f.applicationId()) != null && InfrastructureService.find(f.applicationId()).capability().shipLaunch()
                    && com.spaceconquest.engine.logistics.LaunchServiceWork.capacity(state, f.id()) > 0);
            if (carrier && !provider && state.commercialHubs().stream().anyMatch(other -> !other.id().equals(hub.id())
                    && state.marketAccounts().stream().anyMatch(a -> a.hubId().equals(other.id()) && a.unsettledSalesCredits() > 0)
                    && other.activeOrders().values().stream().anyMatch(o -> o.demandKg() > o.supplyKg()
                    && stock(hub, o.resourceId()) > 0))) {
                var service = InfrastructureService.all().entrySet().stream().filter(e -> e.getValue().capability().shipLaunch()
                                && e.getValue().capability().mode() != com.spaceconquest.engine.logistics.LaunchService.Mode.SPACE_ELEVATOR)
                        .sorted(Comparator.comparing(Map.Entry::getKey)).findFirst().orElse(null);
                if (service != null && !installed(state, empire, hub.entityId(), service.getKey())) {
                    var drive = com.spaceconquest.engine.ship.PropulsionCatalog.drive(service.getValue().capability().driveId());
                    if (drive != null) drive.propellantMaterials(service.getValue().capability().fuelStockKgPerTier()).forEach((material, kg) -> {
                        if (stock(hub, material) < kg) visit(state, empire, hub, material, result, new HashSet<>(), 0);
                    });
                    result.putIfAbsent(hub.entityId() + ":" + service.getKey(), new Goal(hub.entityId(), service.getKey(), ""));
                }
            }
        }
        for (var plan : state.supplyDevelopmentPlans()) if (plan.empireId().equals(empire.id())
                && com.spaceconquest.engine.logistics.SupplyDevelopmentDemand.valid(state, plan)) {
            var source = state.commercialHubs().stream().filter(h -> h.id().equals(plan.sourceHubId())).findFirst().orElse(null);
            if (source != null && PowerInvestmentQuote.controls(state, empire, source.entityId())
                    && state.tradeRoutes().stream().noneMatch(r -> r.id().equals(plan.routeId()) && (r.onboardKg() > 0 || r.totalVolumeMovedKg() >= plan.quantityKg()))) {
                visit(state, empire, source, plan.materialId(), result, new HashSet<>(), 0);
                launchGoal(state, empire, source, result);
                state.commercialHubs().stream().filter(h -> h.id().equals(plan.destinationHubId()))
                        .findFirst().ifPresent(h -> launchGoal(state, empire, h, result));
            }
        }
        return List.copyOf(result.values());
    }
    private static void launchGoal(GameState state, Empire empire, CommercialHub hub, Map<String, Goal> goals) {
        var service = InfrastructureService.all().entrySet().stream().filter(e -> e.getValue().capability().shipLaunch()
                && e.getValue().capability().mode() != com.spaceconquest.engine.logistics.LaunchService.Mode.SPACE_ELEVATOR)
                .sorted(Comparator.comparing(Map.Entry::getKey)).findFirst().orElse(null);
        if (service == null || state.industrialFacilities().stream().anyMatch(f -> f.planetId().equals(hub.entityId())
                && InfrastructureService.find(f.applicationId()) != null && InfrastructureService.find(f.applicationId()).capability().shipLaunch())) return;
        var drive = com.spaceconquest.engine.ship.PropulsionCatalog.drive(service.getValue().capability().driveId());
        if (drive != null) drive.propellantMaterials(service.getValue().capability().fuelStockKgPerTier()).forEach((material, kg) -> {
            if (stock(hub, material) < kg) visit(state, empire, hub, material, goals, new HashSet<>(), 0);
        });
        goals.putIfAbsent(hub.entityId() + ":" + service.getKey(), new Goal(hub.entityId(), service.getKey(), ""));
    }
    private static void visit(GameState state, Empire empire, CommercialHub hub, String material,
                              Map<String, Goal> goals, Set<String> path, int depth) {
        if (depth >= 8 || goals.size() >= 32 || !path.add(material)) return;
        var recipe = recipe(state, empire, hub, material);
        if (recipe != null) {
            for (var input : new TreeMap<>(recipe.inputsKg()).entrySet()) if (stock(hub, input.getKey()) < input.getValue())
                visit(state, empire, hub, input.getKey(), goals, path, depth + 1);
            if (!installed(state, empire, hub.entityId(), recipe.applicationId())) {
                for (var materialBill : new TreeMap<>(recipe.infrastructure().constructionMaterialsKg()).entrySet())
                    if (stock(hub, materialBill.getKey()) < materialBill.getValue()) visit(state, empire, hub, materialBill.getKey(), goals, path, depth + 1);
            }
            goals.putIfAbsent(hub.entityId() + ":" + recipe.applicationId(), new Goal(hub.entityId(), recipe.applicationId(), material));
        }
        path.remove(material);
    }
    public static IndustryRecipeCatalog.Recipe recipe(GameState state, Empire empire, CommercialHub hub, String material) {
        return IndustryRecipeCatalog.all().stream().filter(r -> r.outputsKg().containsKey(material) && r.infrastructure() != null
                        && r.infrastructure().permitsSite(state, hub.entityId())
                        && ProcessInput.environmentAvailable(state, hub.entityId(), r.inputSources())
                        && ProcessInput.maximumBatches(state, hub.entityId(), r.inputSources(), state.geologicalDeposits()) > 0
                        && r.inputsKg().entrySet().stream().allMatch(e -> stock(hub, e.getKey()) >= e.getValue()
                        || sourcePath(state, hub, e.getKey(), new HashSet<>(Set.of(material)), 1)))
                .sorted(Comparator.comparing((IndustryRecipeCatalog.Recipe r) -> !installed(state, empire, hub.entityId(), r.applicationId()))
                        .thenComparing(r -> !IndustryRecipeCatalog.isUnlocked(r, empire))
                        .thenComparingInt(r -> r.inputsKg().size()).thenComparing(IndustryRecipeCatalog.Recipe::applicationId)).findFirst().orElse(null);
    }
    public static boolean installed(GameState state, Empire empire, String body, String application) {
        return state.industrialFacilities().stream().anyMatch(f -> body.equals(f.planetId()) && application.equals(f.applicationId())
                && (empire.id().equals(f.ownerEntityId()) || state.corporations().stream().anyMatch(c -> c.id().equals(f.ownerEntityId())
                && empire.id().equals(c.empireId()))));
    }
    /** Reject stranded input chains so another application can replace an exhausted source. */
    private static boolean sourcePath(GameState state, CommercialHub hub, String material, Set<String> path, int depth) {
        if (depth >= 8 || !path.add(material)) return false;
        return IndustryRecipeCatalog.all().stream().anyMatch(r -> r.outputsKg().containsKey(material) && r.infrastructure() != null
                && r.infrastructure().permitsSite(state, hub.entityId())
                && ProcessInput.maximumBatches(state, hub.entityId(), r.inputSources(), state.geologicalDeposits()) > 0
                && r.inputsKg().entrySet().stream().allMatch(e -> stock(hub, e.getKey()) >= e.getValue()
                || sourcePath(state, hub, e.getKey(), new HashSet<>(path), depth + 1)));
    }
    public static double stock(CommercialHub hub, String material) {
        var order = hub.activeOrders().get(material);
        return order == null ? 0 : Math.max(0, order.supplyKg());
    }
}
