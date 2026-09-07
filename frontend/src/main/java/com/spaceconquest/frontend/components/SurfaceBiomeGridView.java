package com.spaceconquest.frontend.components;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.PlaceFacilityOnTileCommand;
import com.spaceconquest.engine.biome.PlanetBiomeGrid;
import com.spaceconquest.engine.biome.SurfaceTile;
import com.spaceconquest.frontend.PlanetaryBodyEntry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.function.BiConsumer;

/**
 * Reusable UI component for rendering the planetary surface biome grid.
 * Supports rendering based on PlanetBiomeGrid and provides facility placement interaction.
 */
public class SurfaceBiomeGridView extends VBox {

    public SurfaceBiomeGridView(PlanetaryBodyEntry body, HumanController humanController, String playerEmpireId, BiConsumer<String, Integer> onBuildRequest) {
        super(8);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Planetary surface biome grid and facility adjacency matrix");
        header.setFill(Color.AQUA);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        getChildren().add(header);

        PlanetBiomeGrid grid = body.biomeGrid();

        if (grid == null || grid.totalTiles() == 0) {
            VBox gasCard = new VBox(6);
            gasCard.setPadding(new Insets(12));
            gasCard.setStyle("-fx-background-color: rgba(30, 45, 75, 0.6); -fx-background-radius: 6;");

            String title = body.isMoon() ? "Moon surface topography" : "Gas giant celestial body";
            Label gasTitle = new Label(title);
            gasTitle.setTextFill(Color.GOLD);
            gasTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

            String desc = body.isMoon() ? 
                "Planetary moon — Small celestial body with no complex biome distribution detected. Surface development limited to standard orbital structures." :
                "Gas giant celestial body — Gaseous atmosphere with no solid surface crust for ground facilities. Surface tile development unavailable.";
            Label gasDesc = new Label(desc);
            gasDesc.setTextFill(Color.LIGHTGRAY);
            gasDesc.setFont(Font.font("Verdana", 10));
            gasDesc.setWrapText(true);

            gasCard.getChildren().addAll(gasTitle, gasDesc);
            getChildren().add(gasCard);
            return;
        }

        VBox surfaceGridContainer = new VBox(6);
        surfaceGridContainer.setAlignment(Pos.CENTER);
        surfaceGridContainer.setPadding(new Insets(4));

        for (int r = 0; r < grid.rows(); r++) {
            HBox rowBox = new HBox(6);
            rowBox.setAlignment(Pos.CENTER);
            int colsInRow = grid.columnsInRow(r);

            for (int c = 0; c < colsInRow; c++) {
                SurfaceTile tile = grid.getTile(r, c);
                if (tile == null) continue;

                VBox tileCard = new VBox(4);
                tileCard.setPadding(new Insets(6));
                tileCard.setPrefSize(120, 58);

                String colorStyle = switch (tile.biomeType()) {
                    case SurfaceTile.BIOME_EQUATORIAL_DESERT -> "-fx-background-color: rgba(180, 130, 40, 0.6); -fx-border-color: #f1c40f;";
                    case SurfaceTile.BIOME_VOLCANIC_RIDGE -> "-fx-background-color: rgba(180, 50, 30, 0.6); -fx-border-color: #e74c3c;";
                    case SurfaceTile.BIOME_POLAR_ICE -> "-fx-background-color: rgba(60, 140, 200, 0.6); -fx-border-color: #3498db;";
                    case SurfaceTile.BIOME_OCEANIC_SHELF -> "-fx-background-color: rgba(30, 80, 160, 0.6); -fx-border-color: #2980b9;";
                    case SurfaceTile.BIOME_MOUNTAIN_RANGE -> "-fx-background-color: rgba(100, 100, 110, 0.6); -fx-border-color: #95a5a6;";
                    case SurfaceTile.BIOME_RADIOACTIVE_CRATER -> "-fx-background-color: rgba(120, 80, 140, 0.6); -fx-border-color: #9b59b6;";
                    case SurfaceTile.BIOME_BARREN_ROCK -> "-fx-background-color: rgba(90, 90, 90, 0.6); -fx-border-color: #7f8c8d;";
                    default -> "-fx-background-color: rgba(40, 140, 60, 0.6); -fx-border-color: #2ecc71;";
                };
                tileCard.setStyle(colorStyle + " -fx-background-radius: 6; -fx-border-width: 1; -fx-border-radius: 6;");

                Text tileName = new Text(String.format("Tile #%d [%s]", tile.tileIndex(), tile.biomeType().replace('_', ' ')));
                tileName.setFill(Color.WHITE);
                tileName.setFont(Font.font("Verdana", FontWeight.BOLD, 10));

                Text depositTxt = new Text(tile.hasDeposit() ? "Mineral vein colocated" : "No deposit");
                depositTxt.setFill(tile.hasDeposit() ? Color.GOLD : Color.LIGHTGRAY);
                depositTxt.setFont(Font.font("Verdana", 9));

                Button placeBtn = new Button("Build on tile");
                placeBtn.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-size: 9px;");
                final int tIdx = tile.tileIndex();
                placeBtn.setOnAction(e -> {
                    if (onBuildRequest != null) {
                        onBuildRequest.accept(body.id(), tIdx);
                    } else if (humanController != null) {
                        humanController.stageCommand(new PlaceFacilityOnTileCommand(
                                body.id(), tIdx, "solar_power_array", playerEmpireId, "PUBLIC_STATE", 50, "technician"
                        ));
                    }
                });

                tileCard.getChildren().addAll(tileName, depositTxt, placeBtn);
                rowBox.getChildren().add(tileCard);
            }
            surfaceGridContainer.getChildren().add(rowBox);
        }

        ScrollPane tileScroll = new ScrollPane(surfaceGridContainer);
        tileScroll.setFitToWidth(true);
        VBox.setVgrow(tileScroll, Priority.ALWAYS);
        tileScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        getChildren().add(tileScroll);
    }
}
