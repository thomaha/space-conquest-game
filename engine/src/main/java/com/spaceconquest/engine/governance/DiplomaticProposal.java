package com.spaceconquest.engine.governance;

/**
 * Represents a proposed diplomatic treaty awaiting mutual ratification.
 *
 * @param id               unique proposal identifier
 * @param senderEmpireId   empire dispatching the diplomatic envoy
 * @param receiverEmpireId target empire receiving the proposal
 * @param proposalType     treaty type proposed
 * @param expiresOnTurn    turn when the proposal expires
 * @param status           negotiation status (PENDING, ACCEPTED, REJECTED, WITHDRAWN, EXPIRED)
 */
public record DiplomaticProposal(
        String id,
        String senderEmpireId,
        String receiverEmpireId,
        String proposalType,
        long expiresOnTurn,
        String status
) {
    public static final long DEFAULT_DURATION_TURNS = 30;
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
    public static final String STATUS_EXPIRED = "EXPIRED";

    public DiplomaticProposal(String id, String senderEmpireId, String receiverEmpireId,
                              String proposalType, String status) {
        this(id, senderEmpireId, receiverEmpireId, proposalType, Long.MAX_VALUE, status);
    }

    public DiplomaticProposal {
        if (expiresOnTurn <= 0) {
            expiresOnTurn = Long.MAX_VALUE;
        }
    }

    public boolean isPendingAtTurn(long turn) {
        return STATUS_PENDING.equals(status) && turn < expiresOnTurn;
    }

    public DiplomaticProposal withStatus(String updatedStatus) {
        return new DiplomaticProposal(id, senderEmpireId, receiverEmpireId,
                proposalType, expiresOnTurn, updatedStatus);
    }
}
