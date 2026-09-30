package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.CasusBelli;
import com.spaceconquest.engine.governance.DiplomaticPact;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.governance.WarDeclarationRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to formally declare Total War against a foreign empire.
 */
public record DeclareWarCommand(
        String initiatorEmpireId,
        String targetEmpireId,
        String casusBelliId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || initiatorEmpireId == null || targetEmpireId == null) {
            return false;
        }
        if (initiatorEmpireId.equals(targetEmpireId)) return false;

        boolean initiatorExists = state.empires().stream().anyMatch(e -> e.id().equals(initiatorEmpireId));
        boolean targetExists = state.empires().stream().anyMatch(e -> e.id().equals(targetEmpireId));
        return initiatorExists && targetExists;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        Empire initiator = state.empires().stream().filter(e -> e.id().equals(initiatorEmpireId)).findFirst().orElse(null);
        Empire target = state.empires().stream().filter(e -> e.id().equals(targetEmpireId)).findFirst().orElse(null);
        if (initiator == null || target == null) return state;

        List<CasusBelli> casusBelliList = List.of();
        if (casusBelliId != null && !casusBelliId.isEmpty()) {
            casusBelliList = List.of(new CasusBelli(casusBelliId, initiatorEmpireId, targetEmpireId, CasusBelli.BORDER_FRICTION, 50.0, 10.0));
        }

        DiplomacyProcessor processor = new DiplomacyProcessor();
        String currentTier = processor.getDiplomaticTier(
                initiatorEmpireId, targetEmpireId, state.diplomaticRelations());
        List<DiplomaticPact> activePacts = switch (currentTier.toUpperCase()) {
            case DiplomacyProcessor.COMMERCIAL_ALLIANCE -> List.of(new DiplomaticPact(
                    "pact_" + initiatorEmpireId + "_" + targetEmpireId, initiatorEmpireId,
                    targetEmpireId, DiplomaticPact.MUTUAL_TRADE_AGREEMENT, 1.0, true));
            case DiplomacyProcessor.INTEGRATED_FEDERATION -> List.of(new DiplomaticPact(
                    "pact_" + initiatorEmpireId + "_" + targetEmpireId, initiatorEmpireId,
                    targetEmpireId, DiplomaticPact.DEFENSIVE_PACT, 1.0, true));
            default -> List.of();
        };
        DiplomacyProcessor.WarDeclarationResult warRes = processor.evaluateWarDeclarationImpact(
                initiator, target, casusBelliList, activePacts
        );

        GameState atWar = new SetDiplomaticTierCommand(
                initiatorEmpireId, targetEmpireId, DiplomacyProcessor.TOTAL_WAR).apply(state);
        List<WarDeclarationRecord> declarations = new ArrayList<>(atWar.warDeclarations());
        declarations.add(new WarDeclarationRecord(
                atWar.turn(), initiatorEmpireId, targetEmpireId, casusBelliId,
                warRes.isJustified(), warRes.civilianHappinessPenalty(),
                warRes.corporateTrustPenalty(), warRes.justificationSummary()
        ));
        return atWar.toBuilder().warDeclarations(declarations).build();
    }
}
