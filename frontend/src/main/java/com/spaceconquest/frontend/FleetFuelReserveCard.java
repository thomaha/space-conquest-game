package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.SetFleetFuelPolicyCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetFuelPolicy;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.LocalTravel;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;

/** Snapshot-only contingency display and queued edits to normal fleet targets. */
final class FleetFuelReserveCard {
    private FleetFuelReserveCard() {}

    static HBox create(GameState state, Fleet fleet, HumanController controller, Label feedback) {
        var policy = fleet.fuelPolicy();
        Spinner<Double> routine = percent(policy.routineFraction()), elevated = percent(policy.elevatedFraction()), war = percent(policy.wartimeFraction());
        Button save = new Button("Save reserve policy");
        save.setDisable(controller == null);
        save.setOnAction(event -> {
            try {
                var chosen = new FleetFuelPolicy(routine.getValue() / 100, elevated.getValue() / 100, war.getValue() / 100);
                controller.stageCommand(new SetFleetFuelPolicyCommand(fleet.ownerEntityId(), fleet.id(), chosen));
                feedback.setText("Queued contingency reserve policy for future orders.");
            } catch (IllegalArgumentException invalid) {
                feedback.setText("Reserve targets must increase from routine to elevated risk to war and stay below 100%.");
            }
        });
        String current = state == null ? "" : String.format("Current target: %.0f%% of tank capacity (%s)",
                policy.fraction(state, fleet) * 100, policy.risk(state, fleet).toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
        return new HBox(6, new Label("Contingency %: routine"), routine, new Label("Elevated"), elevated,
                new Label("War"), war, save, new Label(current));
    }

    private static Spinner<Double> percent(double fraction) {
        var spinner = new Spinner<Double>(0, 99, fraction * 100, 1);
        spinner.setPrefWidth(75);
        return spinner;
    }

    static String localPreview(GameState state, Fleet fleet, FleetLocation.Site site, boolean emergency) {
        if (state == null || site == null) return "";
        var planning = emergency ? fleet.withFuelPolicy(new FleetFuelPolicy(0, 0, 0)) : fleet;
        var plan = LocalTravel.plan(state, planning, site);
        if (plan == null) return "No funded local plan; check contingency fuel and propulsion.";
        var rows = com.spaceconquest.control.command.FuelReservePreview.inspect(state, fleet, plan.propellantKg(), java.util.Map.of(), null);
        return String.join("; ", rows.stream().map(fuel -> String.format(
                "%s: aboard %.1f kg | Burn %.1f kg | Arrival %.1f kg | Contingency %.1f kg | Shortfall %.1f kg%s",
                fuel.shipId(), fuel.departureKg(), fuel.plannedBurnKg(), fuel.arrivalKg(), fuel.protectedKg(), fuel.shortfallKg(),
                emergency ? " | Emergency override" : "")).toList());
    }
}
