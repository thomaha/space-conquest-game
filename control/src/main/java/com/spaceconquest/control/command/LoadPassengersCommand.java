package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;

/** Books actual residents onto a ship for one explicit offworld destination. */
public record LoadPassengersCommand(
        String fleetId,
        String shipId,
        String passengerRaceId,
        int passengerCount,
        String transitMode,
        String destinationBodyId
) implements GameCommand {
    /** An unaddressed passenger request cannot remove people from a population. */
    @Deprecated(forRemoval = true)
    public LoadPassengersCommand(String fleetId, String shipId, String passengerRaceId,
                                 int passengerCount, String transitMode) {
        this(fleetId, shipId, passengerRaceId, passengerCount, transitMode, null);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || fleetId == null || shipId == null || passengerRaceId == null
                || destinationBodyId == null || passengerCount <= 0) return false;
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id()))
                .findFirst().orElse(null);
        if (fleet == null) return false;
        ShipInstance ship = fleet.ships().stream().filter(item -> shipId.equals(item.id()))
                .findFirst().orElse(null);
        if (ship == null || !fleet.ownerEntityId().equals(ship.ownerEntityId())) return false;
        String mode = transitMode == null || transitMode.isBlank()
                ? ShipInstance.MODE_CONSCIOUS : transitMode;
        if (!ShipInstance.MODE_CONSCIOUS.equalsIgnoreCase(mode)
                && !ShipInstance.MODE_CRYOGENIC_STASIS.equalsIgnoreCase(mode)) return false;
        if (ShipInstance.MODE_CRYOGENIC_STASIS.equalsIgnoreCase(mode)
                && !PassengerStasis.availableFor(state, ship, passengerCount)) return false;
        ShipDesign design = state.shipDesigns().stream()
                .filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
        double cargoKg = ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        return design != null && cargoKg + passengerCount * 80.0 <= design.maxCargoMassKg()
                && PassengerTransitProcessor.canBoard(state, shipId, passengerRaceId,
                passengerCount, destinationBodyId);
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        String mode = transitMode == null || transitMode.isBlank()
                ? ShipInstance.MODE_CONSCIOUS : transitMode.toUpperCase();
        return PassengerTransitProcessor.board(state, shipId, passengerRaceId,
                passengerCount, destinationBodyId, mode);
    }
}
