package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.*;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Read-only cultivation details from the injected tick snapshot and application catalog. */
public final class FarmingProductionCard extends VBox {
    public FarmingProductionCard(GameState snapshot, IndustrialFacility facility) {
        var recipe = IndustryRecipeCatalog.find(facility.applicationId());
        if (recipe == null || recipe.farming() == null) { setManaged(false); return; }
        var definition = recipe.farming();
        var growth = snapshot.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id()))
                .map(IndustryAccount::farmingState).findFirst().orElse(FarmingState.empty());
        long days = growth.cultivatedBatches() == 0 ? 0 : Math.min(definition.growingDays(), snapshot.turn() - growth.startedTurn() + 1);
        getChildren().addAll(new Label(String.format("Farming: %.1f ha; %d-day growing cycle", definition.requiredHectares(facility), definition.growingDays())),
                new Label(String.format("Cultivation: %d/%d days; %.3f paid harvest batches", days, definition.growingDays(), growth.cultivatedBatches())),
                new Label(definition.permits(snapshot, facility) ? "Harvest products can be sold directly to the marketplace"
                        : "Cultivation paused: unsuitable site or insufficient space"));
        getChildren().forEach(node -> node.setStyle("-fx-text-fill: #dfe6e9;"));
        if (definition.controlled() != null) {
            var enclosure = definition.controlled();
            double scale = CultivationSpace.scale(snapshot, facility);
            getChildren().addAll(new Label(String.format("Enclosure: %.0f m³; %.0f m² footprint; %.0f m² growing area",
                            enclosure.enclosedVolumeM3PerLine() * scale, enclosure.footprintHectaresPerLine() * 10000 * scale,
                            definition.requiredHectares(facility) * 10000)),
                    new Label(String.format("Water recovery: %.1f%%; %.1f kg makeup per harvest batch",
                            enclosure.waterRecoveryFraction() * 100, enclosure.makeupWaterKgPerBatch())),
                    new Label("Energy per harvest batch: " + enclosure.energyKwhPerBatch().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                            .map(e -> String.format("%s %.0f kWh", e.getKey().replace('_', ' '), e.getValue())).collect(java.util.stream.Collectors.joining("; "))),
                    new Label("Unrecovered losses per harvest batch: " + enclosure.environmentalLossesKg().entrySet().stream()
                            .map(e -> String.format("%.1f kg %s", e.getValue(), e.getKey().replace('_', ' '))).collect(java.util.stream.Collectors.joining("; "))));
            if (snapshot.orbitalStations().stream().anyMatch(s -> s.id().equals(facility.planetId())))
                getChildren().add(new Label("Reserved station slots: " + CultivationSpace.requiredSlots(snapshot, facility)));
            getChildren().forEach(node -> { node.setStyle("-fx-text-fill: #dfe6e9;"); ((Label) node).setWrapText(true); });
        }
    }
}
