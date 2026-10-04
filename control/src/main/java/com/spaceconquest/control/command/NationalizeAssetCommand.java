package com.spaceconquest.control.command;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Transfers a real corporate factory or ship into the sovereign public sector. */
public record NationalizeAssetCommand(String empireId, String corporationId, String assetId)
        implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || corporationId == null || assetId == null) return false;
        Corporation corp = state.corporations().stream()
                .filter(item -> corporationId.equals(item.id()) && empireId.equals(item.empireId()))
                .findFirst().orElse(null);
        if (corp == null || state.empires().stream().noneMatch(item -> empireId.equals(item.id()))) return false;
        boolean facility = corp.ownedFacilityIds().contains(assetId)
                && state.industrialFacilities().stream().anyMatch(item -> assetId.equals(item.id())
                && corporationId.equals(item.ownerEntityId())
                && IndustrialFacility.PRIVATE_CORPORATE.equals(item.ownershipType()));
        boolean ship = corp.ownedShipIds().contains(assetId)
                && state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                .anyMatch(item -> assetId.equals(item.id()) && corporationId.equals(item.ownerEntityId()));
        return facility || ship;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        List<Corporation> corporations = new ArrayList<>();
        for (Corporation corp : state.corporations()) {
            if (!corporationId.equals(corp.id())) {
                corporations.add(corp);
                continue;
            }
            List<String> facilities = new ArrayList<>(corp.ownedFacilityIds());
            List<String> ships = new ArrayList<>(corp.ownedShipIds());
            facilities.remove(assetId);
            ships.remove(assetId);
            corporations.add(new Corporation(corp.id(), corp.name(), corp.empireId(),
                    corp.headquartersEntityId(), corp.marketOrientation(), corp.liquidCapitalReserves(),
                    facilities, ships, corp.claimedVeinIds()));
        }
        List<IndustrialFacility> facilities = new ArrayList<>();
        for (IndustrialFacility facility : state.industrialFacilities()) {
            facilities.add(assetId.equals(facility.id())
                    ? new IndustrialFacility(facility.id(), facility.planetId(), facility.applicationId(),
                    empireId, IndustrialFacility.PUBLIC_STATE, facility.tier(), facility.allocatedWorkers(),
                    facility.workerProfessionId(), facility.isUndergoingExpansion(), facility.expansionProgress())
                    : facility);
        }
        List<Fleet> fleets = new ArrayList<>();
        for (Fleet fleet : state.fleets()) {
            List<ShipInstance> remaining = new ArrayList<>();
            for (ShipInstance ship : fleet.ships()) {
                if (!assetId.equals(ship.id())) {
                    remaining.add(ship);
                    continue;
                }
                ShipInstance publicShip = new ShipInstance(ship.id(), ship.designId(), empireId,
                        ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(),
                        ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
                fleets.add(new Fleet("fleet_nationalized_" + UUID.randomUUID(), "Nationalized fleet",
                        empireId, fleet.currentSystemId(), fleet.targetSystemId(), fleet.coordinateX(),
                        fleet.coordinateY(), fleet.transitProgress(), fleet.isInWarp(), fleet.fleetStance(),
                        List.of(publicShip), fleet.location(), fleet.interstellarMode(),
                        fleet.interstellarTravelDays(), fleet.interstellarDistanceMeters(),
                        fleet.interstellarAccelerationMps2(), fleet.interstellarElapsedDays(),
                        fleet.interstellarPeakSpeedMps(),
                        java.util.Map.of(ship.id(), fleet.interstellarFuelBudgetKg()
                                .getOrDefault(ship.id(), 0.0)), fleet.flightMotion()));
            }
            if (!remaining.isEmpty()) {
                fleets.add(fleet.withShips(remaining));
            }
        }
        return state.toBuilder().corporations(corporations).industrialFacilities(facilities).fleets(fleets).build();
    }
}
