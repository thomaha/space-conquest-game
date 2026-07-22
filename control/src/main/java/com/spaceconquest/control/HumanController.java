package com.spaceconquest.control;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.spaceconquest.engine.GameState;
public class HumanController implements Controller {
    private static final Logger logger = LogManager.getLogger(HumanController.class);
    @Override
    public void onGameStateUpdate(GameState state) {
        logger.info("Human Controller received update: Turn {} [{}]", state.turn(), state.status());
    }
}
