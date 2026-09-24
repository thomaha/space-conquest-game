package com.spaceconquest.control.command;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;

import java.util.ArrayList;
import java.util.List;

/**
 * Command for a private corporation to allocate liquid capital toward investments.
 */
public record CorporateInvestCommand(
        String corporationId,
        String entityId,
        String investmentType,
        double credits
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || corporationId == null || credits <= 0.0) return false;

        Corporation corp = state.corporations().stream().filter(c -> c.id().equals(corporationId)).findFirst().orElse(null);
        return corp != null && corp.liquidCapitalReserves() >= credits;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<Corporation> updatedCorps = new ArrayList<>();
        for (Corporation c : state.corporations()) {
            if (c.id().equals(corporationId)) {
                List<String> facilities = new ArrayList<>(c.ownedFacilityIds());
                List<String> ships = new ArrayList<>(c.ownedShipIds());

                if ("INFRASTRUCTURE".equalsIgnoreCase(investmentType)) {
                    facilities.add("facility_" + c.marketOrientation().toLowerCase() + "_" + (facilities.size() + 1));
                } else if ("FLEET".equalsIgnoreCase(investmentType) || "SHIP".equalsIgnoreCase(investmentType)) {
                    ships.add("ship_" + c.marketOrientation().toLowerCase() + "_" + (ships.size() + 1));
                }

                updatedCorps.add(new Corporation(
                        c.id(), c.name(), c.empireId(), c.headquartersEntityId(),
                        c.marketOrientation(), c.liquidCapitalReserves() - credits,
                        facilities, ships, c.claimedVeinIds()
                ));
            } else {
                updatedCorps.add(c);
            }
        }

        return state.toBuilder()
                .corporations(updatedCorps)
                .build();
    }
}
