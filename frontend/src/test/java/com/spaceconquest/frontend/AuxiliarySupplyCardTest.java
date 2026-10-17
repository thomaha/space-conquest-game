package com.spaceconquest.frontend;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AuxiliarySupplyCardTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }
    private void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable failure) { done.completeExceptionally(failure); } });
        done.get(10, TimeUnit.SECONDS);
    }
    @Test void supplyInspectionUsesCapturedShotCostsAndRestrictsSurfaceAndForeignControls() {
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        var wing = new CarrierWing("wing", "carrier", "INTERCEPTOR_FIGHTER", 2, 2, 100,
                CombatModuleCatalog.application(weapon.id()).id(), "CAPITAL_SHIPS");
        var battery = new PlanetaryDefenseBattery("battery", "earth", wing.weaponApplicationId(), 1, true);
        var source = new ShipInstance("carrier", "design", "own", 1000, 0, 0,
                Map.of(weapon.ammunitionId(), weapon.operationalStats().get("ammunitionMassKg")))
                .withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 100, false, 1, 1, 0, 0, 0, 0, 0));
        var fleet = new Fleet("fleet", "Fleet", "own", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(source),
                FleetLocation.at(FleetLocation.Site.surface("earth")));
        var state = GameState.builder().fleets(List.of(fleet)).auxiliaryForces(new AuxiliaryForces(List.of(wing),
                List.of(new AuxiliaryForces.OwnedBattery("own", battery)))).build();
        var rows = AuxiliarySupplyCard.inspect(state, fleet, "own");
        var load = rows.stream().filter(r -> r.id().equals("wing-load")).findFirst().orElseThrow();
        assertTrue(load.available());
        var command = (ResupplyAuxiliaryWeaponCommand) load.command();
        var salvo = WeaponSalvo.of(weapon);
        assertEquals(2 * weapon.powerDrawKw() / 60 + salvo.energyKwh() / salvo.shots(), command.chargeKwh());
        assertEquals(1, command.rounds());
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-charge-carrier") && r.available()));
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-buy") && !r.available()));
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-readiness") && r.text().contains("Empty magazine")));
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-targeting") && r.text().contains("live sensor contact")));
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-repair") && r.text().contains("No repairs needed")));
        var wreck = battery.withDamage(0, ShipModuleDamage.empty());
        var wreckState = state.toBuilder().auxiliaryForces(state.auxiliaryForces().withBattery(wreck)).build();
        var wreckRows = AuxiliarySupplyCard.inspect(wreckState, fleet, "own");
        assertTrue(wreckRows.stream().anyMatch(r -> r.id().equals("battery-readiness") && r.text().contains("Destroyed battery")));
        assertTrue(wreckRows.stream().anyMatch(r -> r.id().equals("battery-repair") && r.text().contains("dismantle")));
        assertTrue(wreckRows.stream().noneMatch(r -> r.id().equals("battery-buy") || r.id().startsWith("battery-charge")));
        var orbit = fleet.withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth")));
        assertTrue(AuxiliarySupplyCard.inspect(state, orbit, "own").stream().noneMatch(r -> r.id().startsWith("battery")));
        assertTrue(AuxiliarySupplyCard.inspect(state, fleet, "foreign").isEmpty());
        assertTrue(state.auxiliaryForces().wings().getFirst().stores().ammunitionKg().isEmpty());
    }
    @Test void supplyButtonsQueueTrackedCommandsOnceAndRejectChangedSnapshots() throws Exception {
        var state = GameState.builder().build(); var current = new AtomicReference<>(state);
        var command = new ResupplyAuxiliaryWeaponCommand("own", ResupplyAuxiliaryWeaponCommand.Kind.LOAD_WING, "wing", "carrier", 1, 10);
        var rows = List.of(new AuxiliarySupplyCard.Row("load", "Transfer one round", command, true));
        var queued = new ArrayList<GameCommand>(); var completion = new CompletableFuture<CommandOutcome>();
        var feedback = new AtomicReference<Label>();
        fx(() -> {
            var box = new VBox(); feedback.set(new Label());
            AuxiliarySupplyCard.render(box, rows, state, current::get, c -> { queued.add(c); return completion; }, feedback.get());
            var button = (Button) box.lookup("#aux-supply-load"); button.fire(); button.fire();
            assertEquals(List.of(command), queued); assertTrue(button.isDisabled());
        });
        completion.complete(CommandOutcome.EXECUTED);
        fx(() -> assertTrue(feedback.get().getText().contains("completed")));
        fx(() -> {
            var box = new VBox(); var label = new Label();
            AuxiliarySupplyCard.render(box, rows, state, current::get, c -> { queued.add(c); return completion; }, label);
            current.set(state.toBuilder().turn(1).build()); ((Button) box.lookup("#aux-supply-load")).fire();
            assertEquals(1, queued.size()); assertTrue(label.getText().contains("Snapshot changed"));
            AuxiliarySupplyCard.render(box, List.of(new AuxiliarySupplyCard.Row("dry", "No cargo", command, false)),
                    state, current::get, c -> completion, label);
            assertTrue(((Button) box.lookup("#aux-supply-dry")).isDisabled());
        });
        assertTrue(state.auxiliaryForces().wings().isEmpty());
    }
    @Test void constructionClicksQueueOrdersAndDescribeAcceptanceWithoutClaimingCompletion() throws Exception {
        var state = GameState.builder().build(); var command = new BuildAuxiliaryWeaponCommand("own", "sol", "earth", "carrier",
                "strike_craft_chassis", "mass_drivers", 1);
        var queued = new ArrayList<GameCommand>(); var completion = new CompletableFuture<CommandOutcome>(); var feedback = new AtomicReference<Label>();
        fx(() -> {
            var box = new VBox(); feedback.set(new Label());
            AuxiliarySupplyCard.render(box, List.of(new AuxiliarySupplyCard.Row("build", "Queue craft", command, true)), state, () -> state,
                    c -> { queued.add(c); return completion; }, feedback.get());
            ((Button) box.lookup("#aux-supply-build")).fire();
            assertEquals(List.of(command), queued); assertTrue(feedback.get().getText().contains("construction queued"));
        });
        completion.complete(CommandOutcome.EXECUTED);
        fx(() -> assertEquals("Auxiliary construction order accepted.", feedback.get().getText()));
        assertTrue(state.auxiliaryForces().orders().isEmpty());
    }
    @Test void dismantlingQueuesOnceAndReportsFreedSlotsOnlyAfterExecution() throws Exception {
        var state = GameState.builder().build(); var command = new DismantleBatteryCommand("own", "battery");
        var queued = new ArrayList<GameCommand>(); var completion = new CompletableFuture<CommandOutcome>(); var feedback = new AtomicReference<Label>();
        fx(() -> {
            var box = new VBox(); feedback.set(new Label());
            AuxiliarySupplyCard.render(box, List.of(new AuxiliarySupplyCard.Row("dismantle", "Dismantle permanently", command, true)), state, () -> state,
                    c -> { queued.add(c); return completion; }, feedback.get());
            var button = (Button) box.lookup("#aux-supply-dismantle"); button.fire(); button.fire();
            assertEquals(List.of(command), queued); assertEquals("Battery dismantling queued.", feedback.get().getText());
        });
        completion.complete(CommandOutcome.EXECUTED);
        fx(() -> assertEquals("Battery dismantled; surface slots freed.", feedback.get().getText()));
    }
}
