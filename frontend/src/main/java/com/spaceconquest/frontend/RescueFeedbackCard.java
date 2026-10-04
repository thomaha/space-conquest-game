package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.RescueRendezvous;
import com.spaceconquest.engine.ship.RescueStatus;
import com.spaceconquest.engine.ship.ShipInstance;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.util.Locale;

/** Renders persisted rescue feedback independently of the request controls and selected donor. */
final class RescueFeedbackCard {
    private RescueFeedbackCard() {}
    static VBox create(GameState state, ShipInstance ship) {
        VBox box = new VBox(4);
        var status = ship.powerState() == null ? null : ship.powerState().rescueStatus();
        if (status == null) return box;
        var order = status.order();
        String detail = status.explanation();
        if (status.phase() == RescueStatus.Phase.APPROACHING) {
            var rescuer = RescueRendezvous.find(state, status.rescuerFleetId());
            var motion = rescuer == null ? null : rescuer.flightMotion();
            if (motion != null && motion.trajectory() != null && order.equals(motion.trajectory().rescueOrder()))
                detail += String.format(Locale.ROOT, " %.2f days to planned contact.",
                        Math.max(0, motion.trajectory().totalSeconds() - motion.elapsedSeconds()) / 86400);
            else detail += " The rescue approach is no longer available in this snapshot.";
        }
        Label heading = new Label(String.format(Locale.ROOT, "Rescue for %s from %s: %.3f kg %s | Updated turn %d",
                order.receiverShipId(), order.donorShipId(), order.quantityKg(), order.fuelId(), status.turn()));
        Label result = new Label(detail);
        heading.setWrapText(true);
        result.setWrapText(true);
        heading.setTextFill(Color.LIGHTCYAN);
        result.setTextFill(status.phase() == RescueStatus.Phase.DELIVERED ? Color.LIGHTGREEN : Color.LIGHTCYAN);
        box.getChildren().addAll(heading, result);
        return box;
    }
}
