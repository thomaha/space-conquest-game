package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.ChargeShipBatteryCommand;
import com.spaceconquest.control.command.ResupplyShipPowerCommand;
import com.spaceconquest.control.command.RecoverFleetTravelCommand;
import com.spaceconquest.control.command.SetShipSolarArraysCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FlightRecoveryReadiness;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipSolarEnvironment;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Electrical equipment and resupply controls using only the injected snapshot. */
final class ShipPowerCard {
    private ShipPowerCard() {}

    static VBox create(GameState state, Fleet fleet, ShipInstance ship, ShipDesign design,
                       HumanController controller, Label feedback) {
        VBox box = new VBox(5);
        box.getChildren().add(ShipSupplyStorageCard.create(state, fleet, ship, design, controller, feedback));
        box.getChildren().add(RescueFeedbackCard.create(state, ship));
        box.getChildren().add(ShipSupplyTransferCard.create(state, fleet, ship, design, controller, feedback));
        box.getChildren().add(RescueRendezvousCard.create(state, fleet, ship, design, controller, feedback));
        addRecovery(box, state, fleet, controller, feedback);
        if (design == null || design.powerProfile() == null) {
            box.getChildren().add(label("Legacy electrical compatibility mode: reserves and endurance are unknown."));
            return box;
        }
        var power = ShipPowerProcessor.reserves(ship);
        var profile = design.powerProfile();
        if (!power.cargoPreservation().exposureHours().isEmpty())
            box.getChildren().add(label("Cargo exposure (equivalent unpowered hours): " + power.cargoPreservation().exposureHours()));
        if (!power.cargoPreservation().lostKgToday().isEmpty())
            box.getChildren().add(label("Cargo lost last tick (kg): " + power.cargoPreservation().lostKgToday()));
        var environment = fleet.location().inTransit()
                ? ShipSolarEnvironment.journey(state, fleet, fleet.location().destination())
                : ShipSolarEnvironment.at(state, fleet, fleet.location().current());
        box.getChildren().addAll(label(String.format("Electricity: %.1f/%.1f kWh battery | Generator supplies: %s",
                power.batteryChargeKwh(), profile.batteryKwh(), power.generatorMaterialsKg())),
                label(String.format("Loads: %.1f kW essential | %.1f kW cargo | %.1f kW during propulsion",
                        profile.essentialKw(ship, design), ShipPowerProcessor.cargoKw(profile, ship, design), profile.driveKw())));
        if (profile.solarKw() > 0) {
            box.getChildren().add(label(String.format("Solar: %.1f kW in sunlight | %.3f times Sol at 1 AU | %s",
                    ShipPowerProcessor.solarKw(profile, power, environment), environment.fluxRelativeToEarth(), environment.description())));
            Button arrays = new Button(power.arraysDeployed() ? "Stow solar arrays" : "Deploy solar arrays");
            arrays.setDisable(controller == null || !new SetShipSolarArraysCommand(ship.id(), !power.arraysDeployed()).validate(state));
            arrays.setOnAction(event -> {
                controller.stageCommand(new SetShipSolarArraysCommand(ship.id(), !power.arraysDeployed()));
                feedback.setText("Queued solar array deployment change.");
            });
            box.getChildren().add(arrays);
        }
        if (power.unmetEssentialHours() > 0 || power.unmetDriveKwh() > 0 || power.unmetCargoKwh() > 0)
            box.getChildren().add(label(String.format("Power shortage: %.2f accumulated essential-load hours | %.1f kWh drive | %.1f kWh cargo",
                    power.unmetEssentialHours(), power.unmetDriveKwh(), power.unmetCargoKwh())));
        if (controller == null || fleet.hasInterstellarOrder() || fleet.location().inTransit()) return box;
        ComboBox<String> source = new ComboBox<>();
        state.commercialHubs().stream().map(hub -> hub.entityId()).forEach(source.getItems()::add);
        String current = fleet.location().current().entityId();
        if (source.getItems().contains(current)) source.setValue(current);
        else state.orbitalStations().stream().filter(station -> station.id().equals(current))
                .map(station -> station.planetOrbitId()).filter(source.getItems()::contains).findFirst().ifPresent(source::setValue);
        if (profile.chemicalKw() > 0 || profile.fissionKw() > 0) {
            ComboBox<String> fuel = new ComboBox<>();
            profile.fuels().entrySet().stream().filter(entry -> entry.getValue().oxidizerId() == null
                    ? profile.fissionKw() > 0 : profile.chemicalKw() > 0).map(java.util.Map.Entry::getKey)
                    .sorted().forEach(fuel.getItems()::add);
            fuel.setValue(profile.chemicalKw() > 0 ? power.chemicalMixture() : power.reactorFuel());
            Spinner<Double> kg = new Spinner<>(.01, 15_000, profile.chemicalKw() > 0 ? 1000 : 10, 1);
            kg.setEditable(true);
            Button buy = new Button("Buy electrical fuel");
            buy.setOnAction(event -> {
                var command = new ResupplyShipPowerCommand(ship.id(), source.getValue(), fuel.getValue(), kg.getValue());
                if (!command.validate(state)) {
                    feedback.setText("Electrical fuel unavailable, unaffordable or beyond compartment capacity.");
                    return;
                }
                controller.stageCommand(command);
                feedback.setText("Queued electrical fuel purchase for " + ship.id() + ".");
            });
            box.getChildren().add(new HBox(6, source, fuel, kg, buy));
        }
        if (profile.batteryKwh() <= 0) return box;
        Spinner<Double> kwh = new Spinner<>(.01, profile.batteryKwh() / .9,
                Math.min(100, profile.batteryKwh() / .9), 10);
        kwh.setEditable(true);
        Button charge = new Button("Buy stored grid electricity (kWh)");
        charge.setOnAction(event -> {
            var command = new ChargeShipBatteryCommand(ship.id(), current, kwh.getValue());
            if (!command.validate(state)) {
                feedback.setText("Charging requires local grid storage, funds and available battery capacity and charge rate.");
                return;
            }
            controller.stageCommand(command);
            feedback.setText("Queued paid battery charging for " + ship.id() + ".");
        });
        box.getChildren().add(new HBox(6, kwh, charge));
        return box;
    }

    private static void addRecovery(VBox box, GameState state, Fleet fleet,
                                    HumanController controller, Label feedback) {
        if (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())) {
            box.getChildren().add(label("Travel interrupted by power loss. Recovery requires sufficient electricity for the remaining journey."));
            if (!fleet.location().inTransit() && fleet.flightMotion() != null && fleet.interstellarDistanceMeters() > 0)
                box.getChildren().add(label(String.format("Coasting: %.3f km along crossing | %.1f m/s",
                        fleet.flightMotion().positionMeters() / 1000, fleet.flightMotion().velocityMps())));
            var command = new RecoverFleetTravelCommand(fleet.id());
            var paused = command.pausedPreview(state);
            var readiness = paused == null ? FlightRecoveryReadiness.check(state, fleet) : null;
            var recovery = readiness != null && readiness.ready() ? readiness.plan() : null;
            Button resume = new Button("Plan recovery to destination");
            resume.setDisable(controller == null || (paused == null ? recovery == null : !paused.ready()));
            resume.setOnAction(event -> {
                controller.stageCommand(command);
                feedback.setText(paused == null ? "Queued recovery trajectory with powered braking."
                        : "Queued resumption of the remaining itinerary.");
            });
            box.getChildren().add(resume);
            if (paused != null) {
                box.getChildren().add(label(String.format("Paused %s: %.1f%% complete | %.0f days remaining%s",
                        paused.local() ? "local transfer" : "warp journey",
                        (paused.local() ? fleet.location().progress() : fleet.transitProgress()) * 100,
                        paused.remainingDays(), paused.local() ? " | Maneuver fuel already committed" : "")));
                if (!paused.ready()) paused.electrical().stream().filter(check -> !check.ready())
                        .forEach(check -> box.getChildren().add(label(check.explanation())));
            } else if (!readiness.ready()) readiness.blockers().forEach(reason -> box.getChildren().add(label(reason)));
            else box.getChildren().add(label(String.format("Ready to resume: %.2f days | %.1f kg propellant",
                    recovery.trajectory().totalSeconds() / 86400, recovery.fuelKg().values().stream().mapToDouble(Double::doubleValue).sum())));
        }
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.LIGHTCYAN);
        label.setWrapText(true);
        return label;
    }
}
