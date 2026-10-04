package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class RoamingTradeWorkbenchTest {
    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) { /* Already initialized. */ }
    }
    private Stream<Node> nodes(Node node) {
        Stream<Node> descendants = node instanceof ScrollPane pane ? nodes(pane.getContent())
                : node instanceof Parent parent ? parent.getChildrenUnmodifiable().stream().flatMap(this::nodes) : Stream.empty();
        return Stream.concat(Stream.of(node), descendants);
    }

    @Test void onePortCanCommissionRoamingTradeAndOnlyStagesTheMutation() throws Exception {
        var design = new ShipDesign("design", "Freighter", "owner", ShipRole.CARGO_TRANSPORT, "steel", List.of(),
                "steel", 0, 1000, 100, 0, 1, 0, 0, true, false);
        var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(new Fleet("fleet", "Freighter", "owner",
                "sol", "", 0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of())))))
                .commercialHubs(List.of(new CommercialHub("port", "station", 0, 10000, 0, 10, Map.of())))
                .empires(List.of(new Empire("owner", "Owner", "human", "Individualist", 1000, 0,
                        List.of("sol"), List.of(), Map.of(), List.of(), List.of()))).build();
        var controller = new HumanController();
        var task = new FutureTask<Void>(() -> {
            var view = new CommercialHubView(null);
            view.setPlayerEmpireId("owner"); view.setHumanController(controller);
            view.initializeAfterConstruction(); view.updateData(state); view.show(state.commercialHubs());
            var mode = nodes(view.getRoot()).filter(CheckBox.class::isInstance).map(CheckBox.class::cast).findFirst().orElseThrow();
            var commission = nodes(view.getRoot()).filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().equals("Commission trade route")).findFirst().orElseThrow();
            assertTrue(commission.isDisabled());
            mode.setSelected(true);
            assertFalse(commission.isDisabled());
            commission.fire();
            assertTrue(state.tradeRoutes().isEmpty());
            assertEquals(1, controller.getCommandQueue().size());
            return null;
        });
        Platform.runLater(task); task.get(10, TimeUnit.SECONDS);
        var next = controller.getCommandQueue().drainAndExecute(state);
        assertTrue(next.tradeRoutes().getFirst().roaming());
        assertEquals("port", next.tradeRoutes().getFirst().originEntityId());
        assertEquals("port", next.tradeRoutes().getFirst().destinationEntityId());
    }
}
