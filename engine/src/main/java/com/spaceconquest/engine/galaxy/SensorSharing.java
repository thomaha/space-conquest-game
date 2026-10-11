package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.*;
import java.util.*;

/** Direct hardware-sharing grants. Foreign agreements never relay another empire's contacts. */
public final class SensorSharing {
    private SensorSharing() {}

    public static boolean partyExists(GameState state, String id) {
        return id != null && (state.empires().stream().anyMatch(empire -> id.equals(empire.id()))
                || state.corporations().stream().anyMatch(corporation -> id.equals(corporation.id())));
    }

    public static String homeEmpire(GameState state, String id) {
        if (state.empires().stream().anyMatch(empire -> empire.id().equals(id))) return id;
        return state.corporations().stream().filter(corporation -> corporation.id().equals(id))
                .map(corporation -> corporation.empireId() == null ? "" : corporation.empireId()).findFirst().orElse("");
    }

    public static boolean atWar(GameState state, String first, String last) {
        String a = homeEmpire(state, first), b = homeEmpire(state, last);
        return !a.isBlank() && !b.isBlank() && !a.equals(b) && DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(
                new DiplomacyProcessor().getDiplomaticTier(a, b, state.diplomaticRelations()));
    }

    public static boolean agreement(GameState state, String first, String last) {
        return !atWar(state, first, last) && state.diplomaticProposals().stream().anyMatch(proposal ->
                DiplomaticPact.SENSOR_DATA_SHARING.equalsIgnoreCase(proposal.proposalType())
                && DiplomaticProposal.STATUS_ACCEPTED.equals(proposal.status()) && between(proposal, first, last));
    }

    public static boolean between(DiplomaticProposal proposal, String first, String last) {
        return first.equals(proposal.senderEmpireId()) && last.equals(proposal.receiverEmpireId())
                || last.equals(proposal.senderEmpireId()) && first.equals(proposal.receiverEmpireId());
    }

    private static Set<String> nativeOwners(GameState state, String empireId) {
        var owners = new HashSet<String>(); owners.add(empireId);
        state.corporations().stream().filter(corporation -> empireId.equals(corporation.empireId()))
                .forEach(corporation -> owners.add(corporation.id()));
        return owners;
    }

    private static Set<String> empireOwners(GameState state, String empireId) {
        var owners = nativeOwners(state, empireId);
        for (var empire : state.empires()) if (agreement(state, empireId, empire.id()))
            owners.addAll(nativeOwners(state, empire.id()));
        for (var corporation : state.corporations()) if (agreement(state, empireId, corporation.id())) owners.add(corporation.id());
        return owners;
    }

    public static Set<String> observerOwners(GameState state, String viewerId) {
        if (!partyExists(state, viewerId)) return Set.of();
        if (state.empires().stream().anyMatch(empire -> viewerId.equals(empire.id())))
            return Set.copyOf(empireOwners(state, viewerId));
        var owners = new HashSet<String>(); owners.add(viewerId);
        String home = homeEmpire(state, viewerId);
        if (state.empires().stream().anyMatch(empire -> home.equals(empire.id()))) owners.addAll(empireOwners(state, home));
        for (var empire : state.empires()) if (agreement(state, viewerId, empire.id())) owners.addAll(nativeOwners(state, empire.id()));
        return Set.copyOf(owners);
    }

    /** Corporate members consume the same immutable current view as their home empire. */
    public static FogOfWarState view(GameState state, String viewerId) {
        String home = homeEmpire(state, viewerId);
        String viewId = !home.isBlank() && observerOwners(state, viewerId).equals(observerOwners(state, home)) ? home : viewerId;
        return state.fogOfWarStates().stream().filter(view -> viewId.equals(view.empireId())).findFirst().orElse(null);
    }
}
