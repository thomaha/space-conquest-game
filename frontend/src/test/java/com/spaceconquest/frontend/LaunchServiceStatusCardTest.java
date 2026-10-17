package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.LaunchServiceWork;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LaunchServiceStatusCardTest {
    @BeforeAll static void startup() { try { Platform.startup(() -> {}); } catch (IllegalStateException started) { } }
    @Test void snapshotWorkRendersAndMoreAllocatedWorkersCannotInventPaidCapacity() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 100000, 0, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        var facility = new IndustrialFacility("driver", "earth", "mass_driver", "owner", IndustrialFacility.PUBLIC_STATE, 1, 25, "technician", false, 0);
        var earth = new Planet("earth", "Earth", "", 1, 9.81, 1, 0, 12742, "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of());
        var state = GameState.builder().empires(List.of(owner)).industrialFacilities(List.of(facility))
                .industryAccounts(List.of(IndustryAccount.empty("driver").withPaidWorkers(25)))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1, "Yellow", List.of(earth), List.of()))).build();
        var queue = new CommandQueue(); var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                var card = new LaunchServiceStatusCard(state, facility, "owner", new HumanController(queue));
                new javafx.scene.Scene(card); card.applyCss(); card.layout();
                assertTrue(((Label) card.lookup("#launch-work-capacity")).getText().contains("500"));
                @SuppressWarnings("unchecked") var spinner = (Spinner<Integer>) card.lookup("#launch-service-workers");
                spinner.getValueFactory().setValue(50);
                ((Button) card.lookup("#launch-service-staffing")).fire();
                assertEquals(25, state.industrialFacilities().getFirst().allocatedWorkers());
                var changed = queue.drainAndExecute(state);
                assertEquals(50, changed.industrialFacilities().getFirst().allocatedWorkers());
                assertEquals(500, LaunchServiceWork.capacity(changed, facility.id()), 1e-6);
                done.complete(null);
            } catch (Throwable failure) { done.completeExceptionally(failure); }
        });
        done.get(10, TimeUnit.SECONDS);
    }
}
