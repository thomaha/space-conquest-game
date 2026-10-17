package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.SetLaunchServiceStaffingCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.logistics.LaunchServiceWork;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Cosmetic service work preview and queued staffing changes from an immutable snapshot. */
public final class LaunchServiceStatusCard extends VBox {
    public LaunchServiceStatusCard(GameState state, IndustrialFacility facility, String empireId, HumanController controller) {
        super(4);
        var quote = LaunchServiceWork.quote(state, facility);
        double remaining = Math.max(0, quote.capacityKg() - state.launchUsageKg().getOrDefault(facility.id(), 0.0));
        var capacity = new Label(String.format("Launch work: %,.1f units per period; %,.1f kg remaining", quote.workUnitsPerDay(), remaining));
        capacity.setId("launch-work-capacity"); capacity.setWrapText(true);
        var cost = new Label(String.format("Service quote includes payroll %,.1f, maintenance %,.1f and replacement reserve %,.1f per period before tax and margin",
                quote.payrollCredits(), quote.maintenanceCredits(), quote.capitalReserveCredits()));
        cost.setId("launch-work-cost"); cost.setWrapText(true);
        getChildren().addAll(capacity, cost);
        int paid = state.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id())).mapToInt(a -> a.paidWorkers()).findFirst().orElse(0);
        getChildren().add(new Label(String.format("Paid crew: %d; allocated: %d; skill: %.2f; automation: %.0f%%", paid, facility.allocatedWorkers(),
                facility.productionEquipment().operatorSkill(), facility.productionEquipment().autonomousFraction() * 100)));
        if (controller == null || !empireId.equals(facility.ownerEntityId()) || !IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) return;
        int maximum = (int) Math.min(Integer.MAX_VALUE, facility.productionEquipment().referenceCrew());
        var workers = new Spinner<Integer>(0, maximum, Math.clamp(facility.allocatedWorkers(), 0, maximum));
        workers.setId("launch-service-workers");
        var apply = new Button("Set service staffing"); apply.setId("launch-service-staffing");
        apply.setOnAction(event -> controller.stageCommand(new SetLaunchServiceStaffingCommand(empireId, facility.id(), workers.getValue())));
        getChildren().addAll(workers, apply);
    }
}
