package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.logistics.LaunchService;
import com.spaceconquest.engine.ship.*;
import java.util.Map;

/** One packaged round purchased from the physically local surface hub, without committing projected state. */
public record AmmunitionResupplyQuote(String shipId, String moduleId, String bodyId, String ammunitionId,
                                     double quantityKg, double localStockKg, Double purchaseCredits, Double liftCredits,
                                     String reason, GameCommand command) {
    public boolean available() { return command != null; }
    public static AmmunitionResupplyQuote inspect(GameState state, String ownerId, String shipId, String moduleId) {
        var fleet = state.fleets().stream().filter(f -> f.ships().stream().anyMatch(s -> s.id().equals(shipId))).findFirst().orElse(null);
        if (fleet == null || !fleet.ownerEntityId().equals(ownerId)) return blocked(shipId, moduleId, "Ship is not owned by the requesting player");
        var ship = fleet.ships().stream().filter(s -> s.id().equals(shipId)).findFirst().orElseThrow();
        if (!ship.ownerEntityId().equals(ownerId)) return blocked(shipId, moduleId, "Ship ownership differs from fleet ownership");
        var design = state.shipDesigns().stream().filter(d -> d.id().equals(ship.designId())).findFirst().orElse(null);
        var module = CombatModuleCatalog.module(design, moduleId);
        if (module == null || !design.equippedModuleIds().contains(moduleId) || module.ammunitionId().isBlank())
            return blocked(shipId, moduleId, "No installed ammunition requirement");
        var site = fleet.location().current();
        if (site.kind() == FleetLocation.Kind.DOCKED) {
            double quantity = module.operationalStats().getOrDefault("ammunitionMassKg", 0.0);
            var local = new LoadDockedCargoCommand(shipId, site.entityId(), module.ammunitionId(), quantity);
            if (local.validate(state)) {
                var hub = state.commercialHubs().stream().filter(item -> site.entityId().equals(item.entityId())).findFirst().orElseThrow();
                var order = hub.activeOrders().get(module.ammunitionId());
                return new AmmunitionResupplyQuote(shipId, moduleId, site.entityId(), module.ammunitionId(), quantity, order.supplyKg(),
                        balance(state, ownerId) - balance(local.apply(state), ownerId), 0.0, "Paid docked stock transfer", local);
            }
        }
        String body = switch (site.kind()) {
            case SURFACE, ORBIT -> site.entityId();
            case DOCKED -> state.orbitalStations().stream().filter(s -> s.id().equals(site.entityId())).map(s -> s.planetOrbitId()).findFirst().orElse("");
            default -> "";
        };
        double mass = module.operationalStats().getOrDefault("ammunitionMassKg", 0.0);
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(body)).findFirst().orElse(null);
        var order = hub == null ? null : hub.activeOrders().get(module.ammunitionId());
        double stock = order == null ? 0 : Math.max(0, order.supplyKg());
        Double price = order == null || !Double.isFinite(order.pricePerKg()) || order.pricePerKg() < 0 ? null : order.pricePerKg() * mass;
        var result = new AmmunitionResupplyQuote(shipId, moduleId, body, module.ammunitionId(), mass, stock, price, null, "", null);
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit()) return result.block("Loading unavailable during travel or a queued departure");
        if (body.isBlank()) return result.block("No local surface hub: land, orbit or dock above a body");
        if (!fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state, body))) return result.block("Source body is outside the ship's system");
        if (hub == null) return result.block("No surface commercial hub at this body");
        if (!Double.isFinite(mass) || mass <= 0) return result.block("Invalid packaged round mass");
        if (stock < mass) return result.block("Local stock cannot supply one whole round");
        if (price == null) return result.block("Local ammunition price unavailable");
        double carried = ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum() + ship.passengerCount() * 80.0;
        if (carried + mass > design.maxCargoMassKg()) return result.block("Insufficient cargo capacity for one round");
        var purchase = ConstructionMaterials.buyUpTo(state, body, ownerId, Map.of(module.ammunitionId(), mass));
        if (!purchase.covers(Map.of(module.ammunitionId(), mass))) return result.block("Insufficient purchase funds or unavailable local stock");
        double paidPurchase = balance(state, ownerId) - balance(purchase.state(), ownerId);
        GameCommand command;
        double lift = 0;
        if (site.kind() == FleetLocation.Kind.SURFACE) command = new LoadSurfaceCargoCommand(shipId, body, module.ammunitionId(), mass);
        else {
            var plan = LaunchService.choose(purchase.state(), body, ownerId, mass, false, false, 0);
            if (plan == null) return result.block("No affordable lift service with required fuel, power and capacity");
            lift = LaunchService.payerOperatingCost(purchase.state(), plan, ownerId);
            command = new LoadOrbitalCargoCommand(shipId, body, module.ammunitionId(), mass);
        }
        if (!command.validate(state)) return result.block("Loading is unavailable at this site");
        return new AmmunitionResupplyQuote(shipId, moduleId, body, module.ammunitionId(), mass, stock, paidPurchase, lift,
                site.kind() == FleetLocation.Kind.SURFACE ? "Local surface loading" : "Purchase plus orbital lift", command);
    }
    private AmmunitionResupplyQuote block(String reason) {
        return new AmmunitionResupplyQuote(shipId, moduleId, bodyId, ammunitionId, quantityKg, localStockKg, purchaseCredits, liftCredits, reason, null);
    }
    private static AmmunitionResupplyQuote blocked(String ship, String module, String reason) {
        return new AmmunitionResupplyQuote(ship, module, "", "", 0, 0, null, null, reason, null);
    }
    private static double balance(GameState state, String owner) {
        var corporation = state.corporations().stream().filter(c -> c.id().equals(owner)).findFirst().orElse(null);
        return corporation != null ? corporation.liquidCapitalReserves()
                : state.empires().stream().filter(e -> e.id().equals(owner)).mapToDouble(e -> e.treasuryCredits()).findFirst().orElse(0);
    }
}
