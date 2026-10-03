package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.industry.PowerBillingProcessor;
import com.spaceconquest.engine.industry.PowerGridState;
import java.util.ArrayList;

/** Paid transfer from actual local grid storage. Repeated commands cannot reuse energy or the daily charge limit. */
public final class ShipBatteryCharging {
    private ShipBatteryCharging() {}

    public static GameState charge(GameState state, String shipId, String sourceId, double inputKwh) {
        if (state == null || shipId == null || sourceId == null || !Double.isFinite(inputKwh) || inputKwh <= 0) return state;
        Fleet fleet = state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> ship.id().equals(shipId)))
                .findFirst().orElse(null);
        if (fleet == null || fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || !(fleet.location().isAt(FleetLocation.Site.surface(sourceId))
                || fleet.location().isAt(FleetLocation.Site.docked(sourceId)))) return state;
        var ship = fleet.ships().stream().filter(item -> item.id().equals(shipId)).findFirst().orElseThrow();
        var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
        if (design == null || design.powerProfile() == null || !ship.ownerEntityId().equals(fleet.ownerEntityId())) return state;
        var profile = design.powerProfile();
        var power = ShipPowerProcessor.reserves(ship);
        double stored = inputKwh * ShipPowerProcessor.CHARGING_EFFICIENCY;
        if (power.batteryChargeKwh() + stored > profile.batteryKwh() + .000001
                || power.chargedInputKwhToday() + inputKwh > profile.chargeKw() * 24 + .000001) return state;
        var grid = state.powerGrids().stream().filter(item -> item.entityId().equals(sourceId)).findFirst().orElse(null);
        if (grid == null || !Double.isFinite(grid.currentStoredKwh()) || grid.currentStoredKwh() < inputKwh
                || grid.isDeficitBrownoutActive()) return state;
        String sourceSystem = ConstructionMaterials.systemForBody(state, sourceId);
        String provider = state.orbitalStations().stream().filter(station -> station.id().equals(sourceId))
                .filter(station -> station.systemId().equals(fleet.currentSystemId()))
                .map(station -> station.ownerEntityId()).findFirst().orElse(null);
        if (provider == null) {
            String systemId = sourceSystem;
            if (!fleet.currentSystemId().equals(systemId)) return state;
            provider = state.empires().stream().filter(empire -> empire.controlledSystemIds().contains(systemId))
                    .map(Empire::id).findFirst().orElse(null);
        }
        if (provider == null) return state;
        double price = inputKwh * PowerBillingProcessor.PRICE_PER_KWH;
        if (balance(state, ship.ownerEntityId()) < price || balance(state, provider) < 0) return state;
        GameState paid = changeCash(changeCash(state, ship.ownerEntityId(), -price), provider, price);
        var grids = new ArrayList<>(paid.powerGrids());
        grids.set(grids.indexOf(grid), new PowerGridState(grid.entityId(), grid.totalGenerationKw(),
                grid.totalDemandKw(), grid.netBalanceKw(), grid.batteryCapacityKwh(),
                grid.currentStoredKwh() - inputKwh, grid.isDeficitBrownoutActive()));
        var next = new ShipPowerState(power.generatorMaterialsKg(), power.chemicalMixture(), power.reactorFuel(),
                power.batteryChargeKwh() + stored, power.arraysDeployed(), power.arrayCondition(), power.orientationFraction(),
                power.unmetEssentialHours(), power.unmetDriveKwh(), power.unmetCargoKwh(), power.lastUnmetEssentialKwh(),
                power.chargedInputKwhToday() + inputKwh, power.cargoPreservation());
        return ShipPowerResupply.replace(paid.toBuilder().powerGrids(grids).build(), ship.withPowerState(next));
    }

    private static double balance(GameState state, String owner) {
        double empire = state.empires().stream().filter(item -> item.id().equals(owner))
                .mapToDouble(Empire::treasuryCredits).findFirst().orElse(-1);
        return empire >= 0 ? empire : state.corporations().stream().filter(item -> item.id().equals(owner))
                .mapToDouble(Corporation::liquidCapitalReserves).findFirst().orElse(-1);
    }

    private static GameState changeCash(GameState state, String owner, double delta) {
        return state.toBuilder().empires(state.empires().stream().map(item -> !item.id().equals(owner) ? item
                : new Empire(item.id(), item.name(), item.raceId(), item.societyStructure(), item.treasuryCredits() + delta,
                item.corporateTaxRate(), item.controlledSystemIds(), item.ministries(), item.systemGovernorAssignments(),
                item.unlockedTechIds(), item.activeShipDesignIds())).toList())
                .corporations(state.corporations().stream().map(item -> !item.id().equals(owner) ? item
                : new Corporation(item.id(), item.name(), item.empireId(), item.headquartersEntityId(), item.marketOrientation(),
                item.liquidCapitalReserves() + delta, item.ownedFacilityIds(), item.ownedShipIds(), item.claimedVeinIds())).toList())
                .build();
    }
}
