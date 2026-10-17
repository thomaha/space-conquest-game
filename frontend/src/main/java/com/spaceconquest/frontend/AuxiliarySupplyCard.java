package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Saved auxiliary supplies from snapshots; paid purchase previews run away from the FX thread. */
final class AuxiliarySupplyCard {
    record Row(String id, String text, GameCommand command, boolean available) {}
    private AuxiliarySupplyCard() {}
    static TitledPane create(GameState state, Fleet fleet, String owner, HumanController controller, Label feedback,
                            Supplier<GameState> current, Set<String> expanded) {
        return create(state, "Wings and surface batteries", "aux-" + fleet.id(), () -> inspect(state, fleet, owner),
                controller, feedback, current, expanded);
    }
    static TitledPane createSurface(GameState state, String body, String owner, HumanController controller, Label feedback,
            Supplier<GameState> current, Set<String> expanded) {
        var pane = create(state, "Surface batteries", "battery-" + owner + "-" + body, () -> inspectSurface(state, body, owner),
                controller, feedback, current, expanded);
        pane.setId("planet-batteries"); return pane;
    }
    private static TitledPane create(GameState state, String title, String key, Supplier<List<Row>> inspect,
            HumanController controller, Label feedback, Supplier<GameState> current, Set<String> expanded) {
        var box = new VBox(5); var pane = new TitledPane(title, box); pane.setExpanded(false);
        pane.expandedProperty().addListener((obs, old, open) -> {
            if (!open) { expanded.remove(key); box.setUserData(new Object()); return; }
            expanded.add(key); var request = new Object(); box.setUserData(request);
            box.getChildren().setAll(label("Checking saved supplies and paid resupply..."));
            CompletableFuture.supplyAsync(inspect).whenComplete((rows, failure) -> Platform.runLater(() -> {
                if (box.getUserData() != request || current.get() != state) return;
                if (failure != null) box.getChildren().setAll(label("Supplies unavailable; reopen after the next update."));
                else render(box, rows, state, current, controller == null ? null : controller::stageTrackedCommand, feedback);
            }));
        });
        pane.setExpanded(expanded.contains(key)); return pane;
    }
    static List<Row> inspect(GameState state, Fleet fleet, String owner) {
        if (state == null || !fleet.ownerEntityId().equals(owner)) return List.of();
        var rows = new ArrayList<Row>();
        for (var wing : state.auxiliaryForces().wingsFor(fleet)) {
            rows.add(status(wing.id(), "Wing " + wing.id(), wing.weapon(), wing.stores(), wing.hardware(), wing.maxCraftCount()));
            var salvo = WeaponSalvo.of(wing.weapon());
            if (wing.activeCraftCount() == 0 || salvo.shots() == 0) continue;
            double charge = wing.activeCraftCount() * wing.weapon().powerDrawKw() * TacticalCombatProcessor.SECONDS_PER_ROUND / 3600
                    + salvo.energyKwh() / salvo.shots();
            int rounds = wing.weapon().ammunitionId().isBlank() ? 0 : 1;
            add(rows, state, wing.id() + "-load", "Transfer " + (rounds == 0 ? "energy" : "1 round and energy")
                    + String.format(Locale.ROOT, " (%.2f kWh) from carrier", charge),
                    new ResupplyAuxiliaryWeaponCommand(owner, ResupplyAuxiliaryWeaponCommand.Kind.LOAD_WING,
                            wing.id(), wing.carrierShipId(), rounds, charge));
        }
        if (!fleet.location().inTransit() && fleet.location().current().kind() == FleetLocation.Kind.SURFACE) {
            rows.addAll(inspectBatteries(state, fleet.location().current().entityId(), owner, fleet.ships()));
        }
        rows.addAll(AuxiliaryBuildRows.inspect(state, fleet, owner));
        return List.copyOf(rows);
    }
    static List<Row> inspectSurface(GameState state, String body, String owner) {
        if (state == null || body == null || owner == null) return List.of();
        var sources = state.fleets().stream().filter(f -> owner.equals(f.ownerEntityId()) && !f.isInWarp() && !f.hasInterstellarOrder()
                && !f.location().inTransit() && f.location().isAt(FleetLocation.Site.surface(body)))
                .flatMap(f -> f.ships().stream()).filter(s -> owner.equals(s.ownerEntityId()) && s.currentHullHealth() > 0).toList();
        var rows = new ArrayList<>(inspectBatteries(state, body, owner, sources));
        if (rows.isEmpty()) rows.add(new Row("empty-batteries", "No owned batteries on this body.", null, false));
        if (sources.isEmpty()) rows.add(new Row("battery-power-source", "Charging requires an owned ship parked on this surface with stored electricity.", null, false));
        rows.addAll(AuxiliaryBuildRows.inspectSurface(state, body, owner));
        rows.addAll(SurfaceCombatHistoryRows.inspect(state, body, owner));
        return List.copyOf(rows);
    }
    private static List<Row> inspectBatteries(GameState state, String body, String owner, List<ShipInstance> sources) {
            var rows = new ArrayList<Row>(); var diagnostics = BatteryStatus.inspect(state, owner, body);
            for (var owned : state.auxiliaryForces().batteries()) {
                var battery = owned.battery();
                if (!owner.equals(owned.ownerEntityId()) || !battery.planetId().equals(body)) continue;
                rows.add(status(battery.id(), "Battery " + battery.id() + String.format(Locale.ROOT, " (%.0f hull; %.0f%% weapon condition)",
                        battery.hullHealth(), 100 * battery.moduleDamage().condition(0)), battery.weapon(), battery.stores(), battery.hardware(), 1));
                diagnostics.stream().filter(d -> d.id().equals(battery.id())).findFirst().ifPresent(d -> rows.addAll(BatteryStatusRows.rows(d)));
                add(rows, state, battery.id() + "-dismantle", "Dismantle permanently (discard stores; no refund)",
                        new DismantleBatteryCommand(owner, battery.id()));
                if (battery.hullHealth() <= 0) continue;
                if (!battery.weapon().ammunitionId().isBlank()) {
                    var command = new ResupplyAuxiliaryWeaponCommand(owner, ResupplyAuxiliaryWeaponCommand.Kind.BUY_BATTERY_AMMUNITION,
                            battery.id(), null, 1, 0);
                    var quote = command.apply(state);
                    double before = treasury(state, owner), after = treasury(quote, owner);
                    add(rows, state, battery.id() + "-buy", String.format(Locale.ROOT, "Buy 1 round from local stock (%.2f credits)", before - after), command);
                }
                var salvo = WeaponSalvo.of(battery.weapon());
                if (salvo.shots() == 0) continue;
                double charge = battery.weapon().powerDrawKw() * TacticalCombatProcessor.SECONDS_PER_ROUND / 3600 + salvo.energyKwh() / salvo.shots();
                for (var source : sources) add(rows, state, battery.id() + "-charge-" + source.id(),
                        String.format(Locale.ROOT, "Transfer %.2f kWh from %s", charge, source.id()),
                        new ResupplyAuxiliaryWeaponCommand(owner, ResupplyAuxiliaryWeaponCommand.Kind.CHARGE_BATTERY,
                                battery.id(), source.id(), 0, charge));
            }
        return List.copyOf(rows);
    }
    private static double treasury(GameState state, String owner) {
        return state.empires().stream().filter(e -> owner.equals(e.id())).mapToDouble(e -> e.treasuryCredits()).sum();
    }
    private static Row status(String id, String name, ShipModule weapon, AuxiliaryWeaponStores stores, AuxiliaryHardware hardware, int units) {
        String ammo = weapon.ammunitionId().isBlank() ? "Energy only" : ShipWeaponAmmunition.rounds(stores.ammunitionKg(), weapon)
                + " whole " + weapon.ammunitionId().replace('_', ' ') + " rounds";
        return new Row(id, String.format(Locale.ROOT, "%s: %s; %.2f / %.2f kWh stored; %.2f / %.2f kg magazine",
                name, ammo, stores.energyKwh(), units * hardware.value("energyCapacityKwh"),
                stores.ammunitionKg().values().stream().mapToDouble(Double::doubleValue).sum(), units * hardware.value("magazineKg")), null, false);
    }
    private static void add(List<Row> rows, GameState state, String id, String text, GameCommand command) {
        boolean available = command.validate(state);
        rows.add(new Row(id, text + (available ? "" : " — unavailable with current supplies or access"), command, available));
    }
    static void render(VBox box, List<Row> rows, GameState state, Supplier<GameState> current,
                       Function<GameCommand, CompletableFuture<CommandOutcome>> submit, Label feedback) {
        box.getChildren().clear();
        if (rows.isEmpty()) box.getChildren().add(label("No saved wings or owned batteries at this fleet's surface site."));
        for (var row : rows) {
            if (row.command() == null) { box.getChildren().add(label(row.text())); continue; }
            var button = new Button(row.text()); button.setId("aux-supply-" + row.id()); button.setWrapText(true);
            button.setDisable(!row.available() || submit == null);
            button.setOnAction(event -> {
                button.setDisable(true);
                if (current.get() != state) { feedback.setText("Snapshot changed; reopen auxiliary supplies."); return; }
                boolean building = row.command() instanceof BuildAuxiliaryWeaponCommand;
                boolean dismantling = row.command() instanceof DismantleBatteryCommand;
                feedback.setText(building ? "Auxiliary construction queued." : dismantling ? "Battery dismantling queued." : "Auxiliary resupply queued.");
                submit.apply(row.command()).whenComplete((outcome, failure) -> Platform.runLater(() ->
                        feedback.setText(failure == null && outcome == CommandOutcome.EXECUTED
                                ? building ? "Auxiliary construction order accepted." : dismantling ? "Battery dismantled; surface slots freed." : "Auxiliary resupply completed."
                                : "Auxiliary request was not applied; refresh capacity, supplies and access.")));
            });
            box.getChildren().add(button);
        }
        box.getChildren().add(label("Transfers use existing ship charge and cargo. Purchases spend local stock and credits. Requests are checked again next tick."));
    }
    private static Label label(String text) { var label = new Label(text); label.setWrapText(true); return label; }
}
