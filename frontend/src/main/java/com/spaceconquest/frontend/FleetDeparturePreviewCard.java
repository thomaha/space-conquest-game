package com.spaceconquest.frontend;

import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.InterstellarTravel;
import com.spaceconquest.engine.ship.FleetSupplySimulation;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Cosmetic departure details calculated from an injected immutable snapshot. */
final class FleetDeparturePreviewCard {
    private static final Object PREVIEW_VERSION = new Object();
    private FleetDeparturePreviewCard() {}

    static void refresh(VBox box, Button move, GameState state, Fleet fleet, String target) {
        box.getChildren().clear();
        Object version = new Object();
        box.getProperties().put(PREVIEW_VERSION, version);
        if (FleetSupplySimulation.hasOrders(fleet)) {
            move.setDisable(true);
            box.getChildren().add(line("Calculating scheduled supply and departure readiness…"));
            CompletableFuture.supplyAsync(() -> new MoveFleetCommand(fleet.id(), target).preview(state))
                    .whenComplete((preview, failure) -> Platform.runLater(() -> {
                        if (box.getProperties().get(PREVIEW_VERSION) != version) return;
                        box.getChildren().clear();
                        if (failure != null) {
                            box.getChildren().add(line("Departure preview failed. Refresh the fleet selection."));
                            return;
                        }
                        render(box, move, preview);
                    }));
            return;
        }
        var preview = new MoveFleetCommand(fleet.id(), target).preview(state, false);
        render(box, move, preview);
        if (preview != null && preview.ready()) {
            Label pending = line("Calculating destination and return reserves…");
            box.getChildren().add(pending);
            CompletableFuture.supplyAsync(() -> new MoveFleetCommand(fleet.id(), target).preview(state))
                    .whenComplete((complete, failure) -> Platform.runLater(() -> {
                        if (box.getProperties().get(PREVIEW_VERSION) != version) return;
                        box.getChildren().remove(pending);
                        if (failure == null && complete != null) returnReserve(box, complete);
                        else box.getChildren().add(line("Return reserve calculation failed. Refresh the departure preview."));
                    }));
        }
    }

    private static void render(VBox box, Button move, MoveFleetCommand.DeparturePreview preview) {
        move.setDisable(preview == null || !preview.ready());
        if (preview == null) {
            box.getChildren().add(line("Departure unavailable. Check orders, propulsion electricity, propellant, launch service and passenger supplies."));
            return;
        }
        var crossing = preview.crossing();
        String mode = Fleet.MODE_WARP.equals(crossing.mode()) ? "Warp" : "Sublight";
        double localDays = preview.local() == null ? 0 : Math.ceil(preview.local().days());
        String years = preview.totalDays() >= 365.25 ? String.format(" (~%,.1f years)", preview.totalDays() / 365.25) : "";
        box.getChildren().add(line(String.format("Departure preview: %s | %,.0f days total%s | %.0f local + %,.0f crossing",
                mode, preview.totalDays(), years, localDays, preview.totalDays() - localDays)));
        if (Fleet.MODE_SUBLIGHT.equals(crossing.mode()))
            box.getChildren().add(line(String.format("Distance: %.3f light-years | Peak speed: %.3f km/s",
                    crossing.distanceMeters() / InterstellarTravel.METERS_PER_LIGHT_YEAR, crossing.peakSpeedMps() / 1000)));
        double localFuel = preview.local() == null ? 0 : total(preview.local().propellantKg());
        box.getChildren().add(line(String.format("Propellant: %,.1f kg local + %,.1f kg crossing (includes braking)",
                localFuel, total(crossing.fuelBudgetKg()))));
        if (preview.scheduledArrivalDays() > 0) box.getChildren().add(line(
                "Propellant figures show the initial budget. Scheduled refill replaces the remaining coast and braking budget at delivery."));
        if (preview.local() != null) reactorFuel(box, "Local drive fuel", preview.local().reactorFuelKg());
        reactorFuel(box, "Crossing drive fuel", crossing.reactorFuelBudgetKg());
        if (preview.launchCostCredits() > 0)
            box.getChildren().add(line(String.format("Surface launch charge: %,.2f credits", preview.launchCostCredits())));
        for (var power : preview.electrical()) {
            box.getChildren().add(line(power.modeled()
                    ? String.format("Electrical supply for %s: %s | Journey %,.0f kWh | Arrival reserve %,.0f kWh | Fuel %,.3f kg",
                    power.shipId(), power.ready() ? "Ready" : "Insufficient", power.journeyKwh(),
                    power.arrivalReserveKwh(), power.generatorFuelUsedKg())
                    : power.explanation()));
            if (!power.ready()) box.getChildren().add(line(power.explanation()));
        }
        returnReserve(box, preview);
    }

    private static void returnReserve(VBox box, MoveFleetCommand.DeparturePreview preview) {
        var reserve = preview.returnReserve();
        if (reserve == null) return;
        box.getChildren().add(line("Return reserve: " + reserve.explanation()));
        for (var ship : reserve.destination()) box.getChildren().add(line(String.format(
                "At destination %s: propellant %,.1f kg | Electrical fuel %,.3f kg | Bulk supplies %,.1f kg",
                ship.shipId(), ship.propellantKg(), ship.electricalFuelKg(), ship.bulkSupplyKg())));
        if (reserve.ready()) box.getChildren().add(line(String.format("Immediate return estimate: %,.0f days", reserve.returnDays())));
        box.getChildren().add(line("Return check assumes no purchases, transfers or dwell time. Passenger food and destination operations require separate supplies."));
    }

    private static double total(Map<String, Double> quantities) {
        return quantities.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static Label line(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.LIGHTCYAN);
        label.setWrapText(true);
        return label;
    }

    private static void reactorFuel(VBox box, String label, Map<String, InterstellarTravel.ReactorFuelUse> fuels) {
        fuels.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> box.getChildren()
                .add(line(String.format("%s for %s: %,.3f kg %s", label, entry.getKey(),
                        entry.getValue().quantityKg(), entry.getValue().materialId().replace('_', ' ')))));
    }
}
