package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Releases local job allocations from exhausted extraction; machinery, accounts and people stay in place. */
public record ReleaseExhaustedWorkersCommand(String empireId) implements GameCommand {
    public static boolean exhausted(GameState state, IndustrialFacility facility, String owner) {
        var recipe = IndustryRecipeCatalog.forFacility(state, facility);
        return owner != null && owner.equals(facility.ownerEntityId()) && IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())
                && state.empires().stream().anyMatch(e -> e.id().equals(owner) && com.spaceconquest.control.ai.PowerInvestmentQuote.controls(state, e, facility.planetId()))
                && facility.allocatedWorkers() > 0 && facility.tier() > 0 && !facility.isUndergoingExpansion() && recipe != null
                && recipe.inputSources().stream().anyMatch(i -> i.source() == ProcessInput.Source.DEPOSIT
                && state.geologicalDeposits().stream().anyMatch(d -> d.planetId().equals(facility.planetId()) && d.materialId().equals(i.materialId()) && d.isDiscovered() && d.initialVolumeKg() > 0))
                && ProcessInput.maximumBatches(state, facility.planetId(), recipe.inputSources(), state.geologicalDeposits()) <= 0
                && state.industryAccounts().stream().noneMatch(a -> a.facilityId().equals(facility.id()) && a.farmingState().cultivatedBatches() > 0);
    }
    @Override public boolean validate(GameState state) {
        return state != null && state.industrialFacilities().stream().anyMatch(f -> exhausted(state, f, empireId));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var released = new HashMap<String, Integer>();
        state.industrialFacilities().stream().filter(f -> exhausted(state, f, empireId))
                .forEach(f -> released.merge(f.planetId(), f.allocatedWorkers(), Integer::sum));
        var facilities = new ArrayList<>(state.industrialFacilities().stream().map(f -> exhausted(state, f, empireId)
                ? new IndustrialFacility(f.id(), f.planetId(), f.applicationId(), f.ownerEntityId(), f.ownershipType(), f.tier(), 0,
                f.workerProfessionId(), f.isUndergoingExpansion(), f.expansionProgress(), f.productionEquipment()) : f).toList());
        for (int index = 0; index < facilities.size(); index++) {
            var f = facilities.get(index); var recipe = IndustryRecipeCatalog.forFacility(state, f);
            int available = released.getOrDefault(f.planetId(), 0);
            if (available <= 0 || !empireId.equals(f.ownerEntityId()) || !IndustrialFacility.PUBLIC_STATE.equals(f.ownershipType())
                    || f.productionEquipment() == null || f.productionEquipment().autonomousFraction() >= 1 || recipe == null || !recipe.infrastructure().permitsSite(state, f.planetId())
                    || ProcessInput.maximumBatches(state, f.planetId(), recipe.inputSources(), state.geologicalDeposits()) <= 0
                    || state.commercialHubs().stream().noneMatch(h -> h.entityId().equals(f.planetId()) && recipe.outputsKg().keySet().stream()
                    .anyMatch(id -> h.activeOrders().containsKey(id) && h.activeOrders().get(id).demandKg() > h.activeOrders().get(id).supplyKg()))) continue;
            int added = (int) Math.min(available, Math.max(0, f.productionEquipment().referenceCrew() - f.allocatedWorkers()));
            if (added <= 0) continue;
            facilities.set(index, new IndustrialFacility(f.id(), f.planetId(), f.applicationId(), f.ownerEntityId(), f.ownershipType(), f.tier(),
                    f.allocatedWorkers() + added, f.workerProfessionId(), f.isUndergoingExpansion(), f.expansionProgress(), f.productionEquipment()));
            released.put(f.planetId(), available - added);
        }
        return state.withIndustrialFacilities(facilities);
    }
}
