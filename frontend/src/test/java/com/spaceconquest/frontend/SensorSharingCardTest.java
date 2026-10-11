package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.governance.*;
import com.spaceconquest.engine.galaxy.SensorSharing;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SensorSharingCardTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }
    private void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable failure) { done.completeExceptionally(failure); } });
        done.get(10, TimeUnit.SECONDS);
    }
    private GameState state() {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
        var partner = new Empire("partner", "Partner", "human", "Individualist", 1000, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
        var corporation = new Corporation("foreign", "Foreign company", "partner", "", "TRANSPORT", 1000, List.of(), List.of(), List.of());
        return GameState.builder().empires(List.of(owner, partner)).corporations(List.of(corporation)).build();
    }
    @Test void proposalAcceptanceAndRevocationStageCommandsWithoutMutatingSnapshots() throws Exception {
        var original = state(); var controller = new HumanController();
        fx(() -> {
            var card = SensorSharingCard.create(original, "owner", controller);
            assertFalse(((Button) card.lookup("#propose-sensors-foreign")).isDisabled());
            ((Button) card.lookup("#propose-sensors-partner")).fire();
            assertTrue(original.diplomaticProposals().isEmpty());
        });
        var pending = controller.getCommandQueue().drainAndExecute(original); var proposal = pending.diplomaticProposals().getLast();
        assertFalse(SensorSharing.agreement(pending, "owner", "partner"));
        fx(() -> ((Button) SensorSharingCard.create(pending, "partner", controller).lookup("#accept-sensors-" + proposal.id())).fire());
        var accepted = controller.getCommandQueue().drainAndExecute(pending);
        assertTrue(SensorSharing.agreement(accepted, "owner", "partner")); assertTrue(accepted.diplomaticRelations().isEmpty());
        fx(() -> ((Button) SensorSharingCard.create(accepted, "owner", controller).lookup("#revoke-sensors-" + proposal.id())).fire());
        assertTrue(SensorSharing.agreement(accepted, "owner", "partner"));
        var revoked = controller.getCommandQueue().drainAndExecute(accepted);
        assertFalse(SensorSharing.agreement(revoked, "owner", "partner"));
    }
}
