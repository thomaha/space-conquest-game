package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Discarded full construction purchase and commissioned-ship service quote. No forecast asset is committed. */
public record FacilityTransportBuildQuote(GameState registered, QueueShipBuildCommand build, double buildCostCredits) {
    public static boolean pending(GameState state, Empire empire) {
        if (state.shipyardLedger().deployments().values().stream().anyMatch(intent -> EconomicOwnership.transportOperator(state, empire, intent.ownerId())
                && (intent.kind() == ShipDeploymentIntent.Kind.SUPPLY || intent.kind() == ShipDeploymentIntent.Kind.TRADE))) return true;
        return state.shipConstructionOrders().stream().anyMatch(order -> EconomicOwnership.transportOperator(state, empire, order.ownerEntityId())
                && state.shipDesigns().stream().anyMatch(design -> order.designId().equals(design.id()) && ShipRole.CARGO_TRANSPORT.equals(design.role())));
    }
    public static long carriers(GameState state, Empire empire) {
        return state.fleets().stream().filter(fleet -> EconomicOwnership.transportOperator(state, empire, fleet.ownerEntityId())).flatMap(fleet -> fleet.ships().stream())
                .filter(ship -> ship.currentHullHealth() > 0 && state.shipDesigns().stream().anyMatch(design -> ship.designId().equals(design.id())
                        && ship.ownerEntityId().equals(design.ownerEntityId()) && ShipRole.CARGO_TRANSPORT.equals(design.role()))).count();
    }
    public static FacilityTransportBuildQuote quote(GameState state, String ownerId, String designId, String systemId, String hubId, String materialId) {
        if (state == null || ownerId == null || designId == null || systemId == null || hubId == null || materialId == null) return null;
        var empire = EconomicOwnership.empireFor(state, ownerId); var policy = TransportConstructionPolicy.current();
        if (!EconomicOwnership.transportOperator(state, empire, ownerId) || pending(state, empire) || carriers(state, empire) >= policy.maximumCarriersPerEmpire()) return null;
        var source = state.shipDesigns().stream().filter(design -> designId.equals(design.id()) && ownerId.equals(design.ownerEntityId())
                && ShipRole.CARGO_TRANSPORT.equals(design.role()) && design.maxCargoMassKg() > 0).findFirst().orElse(null);
        var need = FacilitySupplyNeeds.shortages(state, empire, LogisticsPolicy.current()).stream()
                .filter(item -> hubId.equals(item.hubId()) && materialId.equals(item.materialId())).findFirst().orElse(null);
        if (need == null) return null;
        if (source == null && designId.isEmpty()) source = CorporateShipDesigner.choose(state, ownerId, ShipRole.CARGO_TRANSPORT, Math.max(0, need.targetKg() - need.positionKg()));
        if (source == null) return null;
        if (canServe(state, empire, need)) return null;
        var design = AIShipResearchRevision.current(state, source); var registered = AIShipResearchRevision.register(state, design);
        var build = new QueueShipBuildCommand(ownerId, design.id(), systemId);
        if (!build.validate(registered)) return null;
        String yard = build.resolveYardEntity(registered);
        if (ShipyardWorkCapacity.forYard(registered, build.resolveYard(registered).providerOwnerId(), systemId, yard).workPerDay() <= 0) return null;
        var feesPaid = ShipyardPayments.pay(registered, ownerId, build.resolveYard(registered).contract(), ShipConstructionRequirements.estimate(design).workUnits());
        if (feesPaid == null) return null;
        var bill = ShipConstructionRequirements.estimate(design).materialsKg();
        boolean orbital = registered.orbitalStations().stream().anyMatch(station -> yard.equals(station.id()));
        var purchase = orbital ? ConstructionMaterials.buyOrbitalUpTo(feesPaid, systemId, yard, ownerId, bill, Double.MAX_VALUE)
                : ConstructionMaterials.buyUpTo(feesPaid, yard, ownerId, bill);
        double cost = EconomicOwnership.cash(state, ownerId) - EconomicOwnership.cash(purchase.state(), ownerId);
        double floor = ownerId.equals(empire.id()) ? policy.empireReserveCredits() : policy.corporateReserveCredits();
        if (!purchase.covers(bill) || cost > policy.maximumBuildCostCredits() || EconomicOwnership.cash(purchase.state(), ownerId) < floor) return null;
        var ship = new ShipInstance("ai_transport_preview_ship", design.id(), ownerId, ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH, 0, 0, Map.of());
        if (design.powerProfile() != null) ship = ship.withPowerState(ShipPowerState.empty());
        var fleet = new Fleet("ai_transport_preview_fleet", "Preview", ownerId, systemId, "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(orbital ? FleetLocation.Site.docked(yard) : FleetLocation.Site.surface(yard)));
        var fleets = new ArrayList<>(purchase.state().fleets()); fleets.add(fleet); var projected = purchase.state().withFleets(fleets);
        var futureEmpire = EconomicOwnership.empireFor(projected, ownerId);
        var service = FacilitySupplyShipment.quote(projected, futureEmpire, need, ship.id(), "ai_transport_preview_route", LogisticsPolicy.current());
        if (service == null || EconomicOwnership.cash(service.state(), ownerId) < floor) return null;
        if (!ownerId.equals(empire.id())) {
            double profit = service.estimatedOperatingProfitCredits();
            if (profit < policy.minimumOperatingProfitCredits() || profit * policy.maximumPaybackShipments() < cost) return null;
        }
        return new FacilityTransportBuildQuote(registered, build, cost);
    }
    private static boolean canServe(GameState state, Empire empire, FacilitySupplyNeeds.Need need) {
        int previews = 0; var policy = LogisticsPolicy.current();
        for (var fleet : state.fleets()) {
            if (!FacilitySupplyShipment.available(state, empire, fleet)) continue;
            if (previews++ >= policy.maximumShipmentPreviews()) break;
            String ship = fleet.ships().getFirst().id();
            if (FacilitySupplyShipment.quote(state, empire, need, ship, "ai_capacity_preview", policy) != null) return true;
            for (var hub : state.commercialHubs()) {
                if (previews++ >= policy.maximumShipmentPreviews()) return false;
                if (FacilitySupplyReposition.quote(state, empire, need, ship, hub.id(), "ai_capacity_preview", policy) != null) return true;
            }
        }
        return false;
    }
}
