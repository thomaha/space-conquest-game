package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.SetRefineryInputCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.function.Supplier;

/** Snapshot-only refinery receipts and tick-validated input selection. */
public final class RefineryRecoveryCard extends VBox {
    private record Source(String label, String material, String lot) { @Override public String toString() { return label; } }
    public RefineryRecoveryCard(GameState snapshot, IndustrialFacility facility, String empireId,
                                HumanController controller, Supplier<GameState> current) {
        super(5);
        var recipe = IndustryRecipeCatalog.find(facility.applicationId());
        if (recipe == null || recipe.refineryProcess() == null) return;
        String owner = snapshot.corporations().stream().filter(c -> c.id().equals(facility.ownerEntityId()))
                .map(com.spaceconquest.engine.Corporation::empireId).findFirst().orElse(facility.ownerEntityId());
        if (!owner.equals(empireId)) return;
        var inventory = RefineryProcess.inventory(snapshot, facility.id());
        var plan = RefineryProcess.plan(snapshot, facility, inventory);
        var status = new Label(plan.blocker().isEmpty() ? "Recovery method: " + plan.profile().requiredApplicationId().replace('_', ' ') : plan.blocker());
        status.setId("refinery-status"); status.setWrapText(true);
        var storage = new Label(String.format("Retained tailings: %,.3f / %,.0f kg", inventory.storedKg(), recipe.refineryProcess().storageCapacityKg(facility)));
        storage.setId("refinery-storage");
        getChildren().addAll(status, storage);
        var fractions = new Label("Recovery fractions: " + plan.profile().recoveryFractions()); fractions.setWrapText(true);
        var costs = new Label(String.format("Per process batch: %,.0f work units, %,.0f kWh; purchased inputs (kg): %s",
                plan.profile().work().workPerBatch(), plan.profile().work().energyKwhPerBatch(), plan.recipe().inputsKg())); costs.setWrapText(true);
        getChildren().addAll(fractions, costs);
        if (inventory.latestReceipt() != null) {
            var receipt = inventory.latestReceipt();
            var recovered = new Label("Last recovered products (kg): " + receipt.productsKg()); recovered.setId("refinery-products"); recovered.setWrapText(true);
            var residue = new Label("Last residue elements (kg): " + receipt.residue().elementsKg()); residue.setId("refinery-elements"); residue.setWrapText(true);
            getChildren().addAll(recovered, residue);
        }
        var sources = new ComboBox<Source>(); sources.setId("refinery-source"); sources.setMaxWidth(Double.MAX_VALUE);
        recipe.refineryProcess().feedstocks().forEach(id -> sources.getItems().add(new Source("Fresh " + id.replace('_', ' '), id, null)));
        inventory.tailings().stream().limit(40).forEach(lot -> sources.getItems().add(new Source(
                String.format("Tailings (%,.3f kg): %s", lot.massKg(), new java.util.TreeMap<>(lot.elementsKg())), null, lot.id())));
        for (var source : sources.getItems()) if (source.lot() != null && source.lot().equals(inventory.selectedTailingsId())
                || source.lot() == null && inventory.selectedTailingsId() == null
                && source.material().equals(inventory.feedstockMaterialId() == null ? recipe.refineryProcess().defaultFeedstock() : inventory.feedstockMaterialId()))
            sources.setValue(source);
        var select = new Button("Queue refinery input"); select.setId("refinery-select");
        select.setDisable(controller == null || facility.tier() <= 0 || facility.isUndergoingExpansion());
        select.setOnAction(event -> {
            var source = sources.getValue();
            if (source == null || current.get() != snapshot) return;
            select.setDisable(true); status.setText("Input selection queued for the next simulation tick");
            controller.stageTrackedCommand(new SetRefineryInputCommand(empireId, facility.id(), source.material(), source.lot()))
                    .whenComplete((outcome, failure) -> Platform.runLater(() -> {
                        if (current.get() != snapshot) return;
                        status.setText(failure != null ? "Input selection failed" : outcome == com.spaceconquest.control.command.CommandOutcome.EXECUTED
                                ? "Input selected; production still requires resources and power" : "Input selection rejected by the simulation");
                        select.setDisable(false);
                    }));
        });
        getChildren().addAll(sources, select);
    }
}
