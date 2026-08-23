package com.spaceconquest.control.ai;

import com.spaceconquest.control.Controller;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.CorporateInvestCommand;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipRole;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Autonomous AI decision controller for private corporations.
 */
public class CorporationAIController implements Controller {
    private static final Logger logger = LogManager.getLogger(CorporationAIController.class);

    private final String corporationId;
    private final CommandQueue commandQueue;
    private final CorporateInvestmentProcessor investmentProcessor = new CorporateInvestmentProcessor();

    public CorporationAIController(String corporationId, CommandQueue commandQueue) {
        this.corporationId = corporationId;
        this.commandQueue = commandQueue;
    }

    public String getCorporationId() {
        return corporationId;
    }

    @Override
    public void onGameStateUpdate(GameState state) {
        if (state == null || commandQueue == null) return;

        Corporation corp = state.corporations().stream().filter(c -> c.id().equals(corporationId)).findFirst().orElse(null);
        if (corp == null) return;

        double maxShortcoming = investmentProcessor.findMaxShortcoming(corp, state.commercialHubs());

        // 1. Autonomous proprietary ship design when public blueprints fail to resolve logistics shortcomings
        if (maxShortcoming >= 0.30) {
            boolean hasCargoDesign = state.shipDesigns().stream()
                    .anyMatch(d -> ShipRole.CARGO_TRANSPORT.equalsIgnoreCase(d.role()));
            if (!hasCargoDesign) {
                String proprietaryId = "design_corp_cargo_" + corporationId;
                ShipDesign proprietaryCargo = new ShipDesign(
                        proprietaryId,
                        "Corporate Atlas Hauler",
                        corporationId,
                        ShipRole.CARGO_TRANSPORT,
                        "refined_aluminum",
                        List.of("mod_cargo_hold_large", "mod_fission_thruster"),
                        "steel",
                        2.0,
                        25000.0,
                        50000.0,
                        150.0,
                        1.20,
                        300000.0,
                        850000.0,
                        true,
                        true
                );
                logger.info("Corporation {} creating proprietary cargo blueprint {}", corporationId, proprietaryId);
                commandQueue.submit(new DesignShipCommand(proprietaryCargo));

                if (corp.liquidCapitalReserves() >= 20000.0 && !state.commercialHubs().isEmpty()) {
                    String systemId = state.solarSystems().isEmpty() ? "sol" : state.solarSystems().getFirst().id();
                    commandQueue.submit(new QueueShipBuildCommand(corporationId, proprietaryId, systemId));
                }
            }
        }

        // 2. Autonomous investment decisions
        if (maxShortcoming >= 0.50 && corp.liquidCapitalReserves() >= 10000.0) {
            String targetEntity = state.commercialHubs().isEmpty() ? "hq" : state.commercialHubs().getFirst().entityId();
            logger.info("Corporation {} investing in infrastructure due to high shortcoming {}", corporationId, maxShortcoming);
            commandQueue.submit(new CorporateInvestCommand(corporationId, targetEntity, "INFRASTRUCTURE", 8000.0));
        } else if (maxShortcoming >= 0.40 && corp.liquidCapitalReserves() >= 15000.0) {
            String targetEntity = state.commercialHubs().isEmpty() ? "hq" : state.commercialHubs().getFirst().entityId();
            logger.info("Corporation {} investing in fleet procurement due to shortcoming {}", corporationId, maxShortcoming);
            commandQueue.submit(new CorporateInvestCommand(corporationId, targetEntity, "FLEET", 12000.0));
        }
    }
}
