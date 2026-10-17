package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.SupplyContract;
import com.spaceconquest.engine.ship.FleetPositioning;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.UUID;
import java.util.function.Supplier;

/** Reads only published receipts. Pricing, funding and acceptance are revalidated on the tick. */
public final class BusinessFinanceCard extends VBox {
    public BusinessFinanceCard(GameState snapshot, IndustrialFacility facility, String empireId,
                               HumanController controller, Supplier<GameState> current) {
        super(5);
        var book = snapshot.businessLedger().account(facility.ownerEntityId());
        var reserve = new Label(String.format("Owner replacement reserve: %,.2f credits", book.replacementReserveCredits()));
        reserve.setId("business-reserve"); getChildren().add(reserve);
        getChildren().add(new Label(String.format("Unfunded replacement target: %,.2f credits",
                Math.max(0, book.replacementFundingTargetCredits() - book.replacementReserveCredits()))));
        var decision = snapshot.businessLedger().operatingDecisions().get(facility.id());
        if (decision != null) {
            var label = new Label("Operating decision: " + decision.reason() + "; requested workers: " + decision.requestedWorkers());
            label.setId("business-decision"); label.setWrapText(true); getChildren().add(label);
            for (var constraint : decision.constraints()) {
                var detail = new Label(String.format("%s: %s; available %,.2f / required %,.2f at allocated capacity",
                        constraint.kind(), constraint.resource().replace('_', ' '), constraint.available(), constraint.required()));
                detail.setWrapText(true); getChildren().add(detail);
            }
        }
        var hub = snapshot.commercialHubs().stream().filter(h -> h.entityId().equals(facility.planetId())).findFirst().orElse(null);
        boolean controls = hub != null && snapshot.empires().stream().anyMatch(e -> e.id().equals(empireId)
                && e.controlledSystemIds().contains(FleetPositioning.systemForHub(snapshot, hub)));
        var status = new Label(); status.setId("supply-contract-status"); status.setWrapText(true);
        snapshot.businessLedger().contracts().stream().filter(c -> c.facilityId().equals(facility.id()) && SupplyContract.OPEN.equals(c.status()))
                .forEach(c -> {
                    var label = new Label(String.format("Contract: %,.2f / %,.2f kg %s delivered; %,.2f credits held; due turn %d",
                            c.deliveredKg(), c.quantityKg(), c.materialId().replace('_', ' '), c.escrowCredits(), c.deadlineTurn()));
                    label.setWrapText(true); getChildren().add(label);
                    if (controls && controller != null) {
                        var cancel = new Button("Cancel undelivered order");
                        cancel.setOnAction(e -> stage(new CancelSupplyContractCommand(empireId, c.id()), cancel, status, snapshot, current, controller));
                        getChildren().add(cancel);
                    }
                });
        var ownerEmpire = com.spaceconquest.engine.economy.EconomicOwnership.empireFor(snapshot, facility.ownerEntityId());
        if (ownerEmpire != null && ownerEmpire.id().equals(empireId) && book.replacementReserveCredits() > 0 && controller != null) {
            var release = new Button("Release replacement reserve to cash"); release.setId("business-release-reserve");
            release.setOnAction(e -> stage(new ReleaseReplacementReserveCommand(empireId, facility.ownerEntityId(), book.replacementReserveCredits()),
                    release, status, snapshot, current, controller)); getChildren().add(release);
        }
        var recipe = IndustryRecipeCatalog.find(facility.applicationId());
        if (controls && controller != null && recipe != null && !IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())) {
            var material = new ComboBox<String>(); material.getItems().addAll(recipe.outputsKg().keySet().stream().sorted().toList());
            material.getSelectionModel().selectFirst(); material.setId("supply-contract-material");
            var quantity = new TextField(); quantity.setPromptText("Quantity (kg)"); quantity.setId("supply-contract-quantity");
            var price = new TextField(); price.setPromptText("Wholesale credits/kg"); price.setId("supply-contract-price");
            var days = new TextField(); days.setPromptText("Delivery within days (1–360)"); days.setId("supply-contract-days");
            var accept = new Button("Queue funded supply contract"); accept.setId("supply-contract-create");
            accept.setOnAction(e -> {
                try {
                    long due = Math.addExact(snapshot.turn(), Long.parseLong(days.getText()));
                    stage(new CreateSupplyContractCommand(empireId, UUID.randomUUID().toString(), facility.id(), hub.id(), material.getValue(),
                            Double.parseDouble(quantity.getText()), Double.parseDouble(price.getText()), due), accept, status, snapshot, current, controller);
                } catch (RuntimeException invalid) { status.setText("Enter a quantity, wholesale price and whole number of delivery days"); }
            });
            getChildren().addAll(new Label("New local supply contract; funds are held until actual delivery"), material, quantity, price, days, accept);
        }
        getChildren().add(status);
    }
    private static void stage(GameCommand command, Button button, Label status, GameState snapshot,
                              Supplier<GameState> current, HumanController controller) {
        if (current.get() != snapshot) return;
        button.setDisable(true); status.setText("Queued for the next simulation tick");
        controller.stageTrackedCommand(command).whenComplete((outcome, failure) -> Platform.runLater(() -> {
            if (current.get() != snapshot) return;
            status.setText(failure != null ? "Request failed" : outcome == CommandOutcome.EXECUTED ? "Request executed" : "Request rejected by the simulation");
            button.setDisable(false);
        }));
    }
}
