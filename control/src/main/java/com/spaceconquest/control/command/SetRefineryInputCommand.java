package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.*;
import java.util.ArrayList;

/** Selects fresh feedstock or an actual owned residue lot; processing and payments happen on the tick. */
public record SetRefineryInputCommand(String empireId, String facilityId, String feedstockMaterialId,
                                     String tailingsLotId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || empireId == null || facilityId == null || feedstockMaterialId != null && tailingsLotId != null) return false;
        var facility = state.industrialFacilities().stream().filter(f -> f.id().equals(facilityId)).findFirst().orElse(null);
        if (facility == null || facility.tier() <= 0 || facility.isUndergoingExpansion()) return false;
        String owner = state.corporations().stream().filter(c -> c.id().equals(facility.ownerEntityId()))
                .map(com.spaceconquest.engine.Corporation::empireId).findFirst().orElse(facility.ownerEntityId());
        if (!empireId.equals(owner) || !ProductionWork.supports(facility, facility.applicationId())) return false;
        var recipe = IndustryRecipeCatalog.find(facility.applicationId());
        var empire = state.empires().stream().filter(e -> e.id().equals(owner)).findFirst().orElse(null);
        if (recipe == null || recipe.refineryProcess() == null || !IndustryRecipeCatalog.isUnlocked(recipe, empire)) return false;
        if (tailingsLotId != null) return RefineryProcess.inventory(state, facilityId).tailings().stream()
                .anyMatch(lot -> lot.id().equals(tailingsLotId) && lot.massKg() > 0);
        return feedstockMaterialId == null || recipe.refineryProcess().feedstocks().contains(feedstockMaterialId);
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var accounts = new ArrayList<>(state.industryAccounts());
        var account = accounts.stream().filter(a -> a.facilityId().equals(facilityId)).findFirst().orElse(IndustryAccount.empty(facilityId));
        accounts.removeIf(a -> a.facilityId().equals(facilityId));
        accounts.add(account.withRefineryState(account.refineryState().select(feedstockMaterialId, tailingsLotId)));
        return state.toBuilder().industryAccounts(accounts).build();
    }
}
