package com.spaceconquest.engine.governance;

/**
 * Represents a proposed diplomatic treaty awaiting mutual ratification.
 *
 * @param id               unique proposal identifier
 * @param senderEmpireId   empire dispatching the diplomatic envoy
 * @param receiverEmpireId target empire receiving the proposal
 * @param proposalType     treaty type proposed
 * @param status           negotiation status (PENDING, ACCEPTED, REJECTED)
 */
public record DiplomaticProposal(
        String id,
        String senderEmpireId,
        String receiverEmpireId,
        String proposalType,
        String status
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
}
