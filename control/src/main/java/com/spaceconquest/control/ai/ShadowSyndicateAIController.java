package com.spaceconquest.control.ai;

import com.spaceconquest.control.Controller;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ShadowSyndicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Autonomous AI decision controller for criminal shadow syndicates and pirate cells.
 */
public class ShadowSyndicateAIController implements Controller {
    private static final Logger logger = LogManager.getLogger(ShadowSyndicateAIController.class);

    private final String syndicateId;
    private final CommandQueue commandQueue;

    public ShadowSyndicateAIController(String syndicateId, CommandQueue commandQueue) {
        this.syndicateId = syndicateId;
        this.commandQueue = commandQueue;
    }

    public String getSyndicateId() {
        return syndicateId;
    }

    @Override
    public void onGameStateUpdate(GameState state) {
        if (state == null || commandQueue == null) return;

        ShadowSyndicate syndicate = state.shadowSyndicates().stream()
                .filter(s -> s.id().equals(syndicateId))
                .findFirst()
                .orElse(null);

        if (syndicate == null) return;

        logger.debug("Shadow syndicate {} active with {} credits and {} rogue ships.",
                syndicate.name(), syndicate.shadowCapitalPool(), syndicate.rogueShipIds().size());
    }
}
