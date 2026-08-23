package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.BuildOrbitalStationCommand;
import com.spaceconquest.control.command.ColonizePlanetCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.ScanSystemCommand;
import com.spaceconquest.control.command.StartProspectingMissionCommand;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.galaxy.FogOfWarState;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.ship.Fleet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive 2D graphical galaxy and system canvas rendering celestial bodies, orbits, hyperlanes,
 * fleet transit vectors with direct click-to-select, right-click move and contextual action menus.
 */
public class GalaxyCanvasView {

    private VBox root;
    private Canvas canvas;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<SolarSystem> solarSystems = new ArrayList<>();
    private final List<Fleet> fleets = new ArrayList<>();
    private final List<Megastructure> megastructures = new ArrayList<>();
    private final List<FogOfWarState> fogOfWarStates = new ArrayList<>();
    private double zoomFactor = 1.0;
    private double panOffsetX = 0.0;
    private double panOffsetY = 0.0;
    private double dragStartX = 0.0;
    private double dragStartY = 0.0;

    private Fleet selectedFleet = null;
    private SolarSystem selectedSystem = null;
    private Planet selectedPlanet = null;

    private Label statusLabel;
    private HBox contextActionRow;

    public GalaxyCanvasView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId != null && !empireId.isEmpty()) {
            this.playerEmpireId = empireId;
        }
    }

    private void build() {
        root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: rgba(10, 15, 30, 0.98); " +
                "-fx-border-color: #1abc9c; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(960, 720);

        Text title = new Text("Interactive tactical galaxy and solar canvas");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

        Button zoomInBtn = new Button("Zoom +");
        zoomInBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; -fx-font-weight: bold;");
        zoomInBtn.setOnAction(e -> {
            zoomFactor = Math.min(2.5, zoomFactor + 0.25);
            drawCanvas();
        });

        Button zoomOutBtn = new Button("Zoom -");
        zoomOutBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; -fx-font-weight: bold;");
        zoomOutBtn.setOnAction(e -> {
            zoomFactor = Math.max(0.5, zoomFactor - 0.25);
            drawCanvas();
        });

        Button resetViewBtn = new Button("Reset view");
        resetViewBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold;");
        resetViewBtn.setOnAction(e -> {
            zoomFactor = 1.0;
            panOffsetX = 0.0;
            panOffsetY = 0.0;
            selectedFleet = null;
            selectedSystem = null;
            selectedPlanet = null;
            updateContextRow();
            drawCanvas();
        });

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> hide());

        HBox controls = new HBox(10, title);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        controls.getChildren().addAll(spacer, zoomInBtn, zoomOutBtn, resetViewBtn, closeBtn);

        canvas = new Canvas(920, 520);
        canvas.setStyle("-fx-background-color: #050811;");

        setupCanvasInteractions();

        statusLabel = new Label("Ready | Left-click: Select fleet/celestial body | Right-click: Issue move command | Drag: Pan sector");
        statusLabel.setTextFill(Color.LIGHTCYAN);
        statusLabel.setFont(Font.font("Verdana", 11));

        contextActionRow = new HBox(10);
        contextActionRow.setAlignment(Pos.CENTER_LEFT);
        contextActionRow.setPadding(new Insets(5));
        contextActionRow.setStyle("-fx-background-color: rgba(20, 30, 55, 0.7); -fx-background-radius: 6;");
        updateContextRow();

        root.getChildren().addAll(controls, canvas, statusLabel, contextActionRow);
        root.setVisible(false);
    }

    private void setupCanvasInteractions() {
        canvas.setOnMousePressed(e -> {
            dragStartX = e.getX() - panOffsetX;
            dragStartY = e.getY() - panOffsetY;
        });

        canvas.setOnMouseDragged(e -> {
            panOffsetX = e.getX() - dragStartX;
            panOffsetY = e.getY() - dragStartY;
            drawCanvas();
        });

        canvas.setOnMouseClicked(e -> {
            double cx = canvas.getWidth() / 2.0 + panOffsetX;
            double cy = canvas.getHeight() / 2.0 + panOffsetY;
            double mouseX = e.getX();
            double mouseY = e.getY();

            if (e.getButton() == MouseButton.PRIMARY) {
                // 1. Check if clicking on a Fleet
                Fleet hitFleet = null;
                for (Fleet f : fleets) {
                    double fx = cx + f.coordinateX() * 1.5 * zoomFactor;
                    double fy = cy + f.coordinateY() * 1.5 * zoomFactor;
                    if (Math.hypot(mouseX - fx, mouseY - fy) < 15.0 * zoomFactor) {
                        hitFleet = f;
                        break;
                    }
                }

                if (hitFleet != null) {
                    selectedFleet = hitFleet;
                    selectedSystem = null;
                    selectedPlanet = null;
                    statusLabel.setText("Selected fleet: " + hitFleet.name() + " (" + hitFleet.ships().size() + " ships) | Right-click star to issue warp transit.");
                    statusLabel.setTextFill(Color.LIGHTGREEN);
                    updateContextRow();
                    drawCanvas();
                    return;
                }

                // 2. Check if clicking on a Solar System / Star or Planet
                SolarSystem hitSystem = null;
                Planet hitPlanet = null;

                for (SolarSystem sys : solarSystems) {
                    double sx = cx + sys.x() * 1.5 * zoomFactor;
                    double sy = cy + sys.y() * 1.5 * zoomFactor;

                    // Check Planets
                    int pIndex = 1;
                    for (Planet p : sys.planets()) {
                        double orbitRadius = (25.0 + pIndex * 15.0) * zoomFactor;
                        double angle = (pIndex * 1.2);
                        double px = sx + Math.cos(angle) * orbitRadius;
                        double py = sy + Math.sin(angle) * orbitRadius;

                        if (Math.hypot(mouseX - px, mouseY - py) < 10.0 * zoomFactor) {
                            hitPlanet = p;
                            hitSystem = sys;
                            break;
                        }
                        pIndex++;
                    }

                    if (hitPlanet != null) break;

                    if (Math.hypot(mouseX - sx, mouseY - sy) < 20.0 * zoomFactor) {
                        hitSystem = sys;
                        break;
                    }
                }

                if (hitPlanet != null) {
                    selectedPlanet = hitPlanet;
                    selectedSystem = hitSystem;
                    selectedFleet = null;
                    statusLabel.setText("Target planet: " + hitPlanet.name() + " (" + hitPlanet.type() + ", Atmosphere: " + hitPlanet.atmosphere() + ") in " + hitSystem.name() + " System.");
                    statusLabel.setTextFill(Color.GOLD);
                } else if (hitSystem != null) {
                    selectedSystem = hitSystem;
                    selectedPlanet = null;
                    selectedFleet = null;
                    statusLabel.setText("Target solar system: " + hitSystem.name() + " (" + hitSystem.planets().size() + " orbiting planetary bodies).");
                    statusLabel.setTextFill(Color.GOLD);
                } else {
                    selectedFleet = null;
                    selectedSystem = null;
                    selectedPlanet = null;
                    statusLabel.setText("Ready | Click celestial objects or fleets to inspect.");
                    statusLabel.setTextFill(Color.LIGHTCYAN);
                }

                updateContextRow();
                drawCanvas();

            } else if (e.getButton() == MouseButton.SECONDARY && selectedFleet != null) {
                // Right-click order: find closest target system to mouse coordinate
                SolarSystem targetSys = null;
                double closestDist = Double.MAX_VALUE;

                for (SolarSystem sys : solarSystems) {
                    double sx = cx + sys.x() * 1.5 * zoomFactor;
                    double sy = cy + sys.y() * 1.5 * zoomFactor;
                    double d = Math.hypot(mouseX - sx, mouseY - sy);
                    if (d < 40.0 * zoomFactor && d < closestDist) {
                        closestDist = d;
                        targetSys = sys;
                    }
                }

                if (targetSys != null && humanController != null) {
                    humanController.stageCommand(new MoveFleetCommand(selectedFleet.id(), targetSys.id()));
                    statusLabel.setText("Dispatched fleet " + selectedFleet.name() + " on warp vector to " + targetSys.name() + " System!");
                    statusLabel.setTextFill(Color.LIGHTGREEN);
                    drawCanvas();
                }
            }
        });
    }

    private void updateContextRow() {
        contextActionRow.getChildren().clear();

        if (selectedPlanet != null) {
            Label targetLbl = new Label("Planet actions (" + selectedPlanet.name().toUpperCase() + "):");
            targetLbl.setTextFill(Color.GOLD);
            targetLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

            Button prospectBtn = new Button("Survey and prospect");
            prospectBtn.setStyle("-fx-background-color: #6c5ce7; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
            prospectBtn.setOnAction(e -> {
                if (humanController != null) {
                    humanController.stageCommand(new StartProspectingMissionCommand(
                            playerEmpireId, selectedPlanet.id(), 5
                    ));
                    statusLabel.setText("Dispatched prospecting team to survey " + selectedPlanet.name());
                    statusLabel.setTextFill(Color.LIGHTGREEN);
                }
            });

            Button buildFacBtn = new Button("Build surface facility");
            buildFacBtn.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
            buildFacBtn.setOnAction(e -> {
                if (humanController != null) {
                    humanController.stageCommand(new BuildFacilityCommand(
                            playerEmpireId, selectedPlanet.id(), "smelter", "smelter_operator", 1
                    ));
                    statusLabel.setText("Ordered smelter facility construction on " + selectedPlanet.name());
                    statusLabel.setTextFill(Color.LIGHTGREEN);
                }
            });

            Button colonizeBtn = new Button("Deploy colony ship");
            colonizeBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
            colonizeBtn.setOnAction(e -> {
                if (humanController != null) {
                    humanController.stageCommand(new ColonizePlanetCommand(
                            playerEmpireId, selectedPlanet.id(), "colony_ship_alpha", 1000, "human"
                    ));
                    statusLabel.setText("Dispatched colony expedition to " + selectedPlanet.name());
                    statusLabel.setTextFill(Color.LIGHTGREEN);
                }
            });

            contextActionRow.getChildren().addAll(targetLbl, prospectBtn, buildFacBtn, colonizeBtn);

        } else if (selectedSystem != null) {
            boolean explored = isSystemExplored(selectedSystem.id());
            Label targetLbl = new Label("System actions (" + selectedSystem.name().toUpperCase() + (explored ? "" : " - UNEXPLORED") + "):");
            targetLbl.setTextFill(explored ? Color.GOLD : Color.ORANGERED);
            targetLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

            if (!explored) {
                Button scanBtn = new Button("Launch deep sensor scan");
                scanBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
                scanBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new ScanSystemCommand(
                                playerEmpireId, selectedSystem.id()
                        ));
                        statusLabel.setText("Dispatched deep sensor scan vectors across " + selectedSystem.name() + " System.");
                        statusLabel.setTextFill(Color.LIGHTGREEN);
                    }
                });
                contextActionRow.getChildren().addAll(targetLbl, scanBtn);
            } else {
                Button stationBtn = new Button("Construct orbital station");
                stationBtn.setStyle("-fx-background-color: #d63031; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
                stationBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new BuildOrbitalStationCommand(
                                playerEmpireId, selectedSystem.id(), "station_" + selectedSystem.id()
                        ));
                        statusLabel.setText("Constructed orbital defense and commerce station in " + selectedSystem.name());
                        statusLabel.setTextFill(Color.LIGHTGREEN);
                    }
                });
                contextActionRow.getChildren().addAll(targetLbl, stationBtn);
            }

        } else if (selectedFleet != null) {
            Label targetLbl = new Label("Fleet selected (" + selectedFleet.name() + "): right-click target system on canvas to move.");
            targetLbl.setTextFill(Color.LIGHTGREEN);
            targetLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
            contextActionRow.getChildren().add(targetLbl);
        } else {
            Label readyLbl = new Label("Select any planet, star system or fleet to access direct tactical commands.");
            readyLbl.setTextFill(Color.LIGHTGRAY);
            readyLbl.setFont(Font.font("Verdana", 11));
            contextActionRow.getChildren().add(readyLbl);
        }
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        drawCanvas();
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public void updateData(List<SolarSystem> newSystems, List<Fleet> newFleets, List<Megastructure> newMegastructures) {
        updateData(newSystems, newFleets, newMegastructures, List.of());
    }

    public void updateData(List<SolarSystem> newSystems, List<Fleet> newFleets, List<Megastructure> newMegastructures, List<FogOfWarState> newFOW) {
        solarSystems.clear();
        if (newSystems != null) solarSystems.addAll(newSystems);

        fleets.clear();
        if (newFleets != null) fleets.addAll(newFleets);

        megastructures.clear();
        if (newMegastructures != null) megastructures.addAll(newMegastructures);

        fogOfWarStates.clear();
        if (newFOW != null) fogOfWarStates.addAll(newFOW);

        if (root.isVisible()) {
            drawCanvas();
        }
    }

    public boolean isSystemExplored(String systemId) {
        if (fogOfWarStates.isEmpty()) return true; // Default visible if not initialized
        for (FogOfWarState s : fogOfWarStates) {
            if (s.empireId().equals(playerEmpireId)) {
                return s.isSystemExplored(systemId);
            }
        }
        return true;
    }

    private void drawCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 1. Clear background
        gc.setFill(Color.web("#060a17"));
        gc.fillRect(0, 0, w, h);

        // 2. Draw background starfield
        gc.setFill(Color.web("#2c3e50"));
        for (int i = 0; i < 60; i++) {
            double rx = ((i * 12345) % (int) w);
            double ry = ((i * 67891) % (int) h);
            gc.fillOval(rx, ry, (i % 2 == 0) ? 1.5 : 2.5, (i % 2 == 0) ? 1.5 : 2.5);
        }

        double centerX = w / 2.0 + panOffsetX;
        double centerY = h / 2.0 + panOffsetY;

        // 3. Draw Hyperlane Links
        gc.setStroke(Color.web("#1abc9c", 0.4));
        gc.setLineWidth(1.5 * zoomFactor);
        for (int i = 0; i < solarSystems.size(); i++) {
            SolarSystem s1 = solarSystems.get(i);
            double x1 = centerX + s1.x() * 1.5 * zoomFactor;
            double y1 = centerY + s1.y() * 1.5 * zoomFactor;

            for (int j = i + 1; j < solarSystems.size(); j++) {
                SolarSystem s2 = solarSystems.get(j);
                double x2 = centerX + s2.x() * 1.5 * zoomFactor;
                double y2 = centerY + s2.y() * 1.5 * zoomFactor;

                double dist = Math.hypot(x2 - x1, y2 - y1);
                if (dist < 300.0 * zoomFactor) {
                    gc.strokeLine(x1, y1, x2, y2);
                }
            }
        }

        // 4. Draw Solar Systems & Stars
        for (SolarSystem sys : solarSystems) {
            double sx = centerX + sys.x() * 1.5 * zoomFactor;
            double sy = centerY + sys.y() * 1.5 * zoomFactor;

            boolean isSystemSelected = selectedSystem != null && selectedSystem.id().equals(sys.id());
            boolean explored = isSystemExplored(sys.id());

            // Star Selection Highlight
            if (isSystemSelected) {
                gc.setStroke(explored ? Color.GOLD : Color.ORANGERED);
                gc.setLineWidth(2.0);
                gc.strokeOval(sx - 20 * zoomFactor, sy - 20 * zoomFactor, 40 * zoomFactor, 40 * zoomFactor);
            }

            if (!explored) {
                // Unexplored shrouded star
                gc.setFill(Color.web("#576574", 0.4));
                gc.fillOval(sx - 16 * zoomFactor, sy - 16 * zoomFactor, 32 * zoomFactor, 32 * zoomFactor);

                gc.setFill(Color.web("#718093"));
                gc.fillOval(sx - 7 * zoomFactor, sy - 7 * zoomFactor, 14 * zoomFactor, 14 * zoomFactor);

                gc.setFill(Color.LIGHTGRAY);
                gc.setFont(Font.font("Verdana", FontWeight.NORMAL, 9 * zoomFactor));
                gc.fillText(sys.name() + " [Fog of war]", sx - 25 * zoomFactor, sy + 20 * zoomFactor);
                continue;
            }

            // Star Corona glow
            gc.setFill(Color.web("#f39c12", 0.3));
            gc.fillOval(sx - 14 * zoomFactor, sy - 14 * zoomFactor, 28 * zoomFactor, 28 * zoomFactor);

            // Star Core
            gc.setFill(Color.web("#f1c40f"));
            gc.fillOval(sx - 8 * zoomFactor, sy - 8 * zoomFactor, 16 * zoomFactor, 16 * zoomFactor);

            // Star Label
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Verdana", FontWeight.BOLD, 10 * zoomFactor));
            gc.fillText(sys.name(), sx - 15 * zoomFactor, sy + 20 * zoomFactor);

            // Planetary Orbit Rings
            int pIndex = 1;
            for (Planet p : sys.planets()) {
                double orbitRadius = (25.0 + pIndex * 15.0) * zoomFactor;
                gc.setStroke(Color.web("#34495e", 0.5));
                gc.setLineWidth(1.0);
                gc.strokeOval(sx - orbitRadius, sy - orbitRadius, orbitRadius * 2, orbitRadius * 2);

                // Planet node on orbit
                double angle = (pIndex * 1.2);
                double px = sx + Math.cos(angle) * orbitRadius;
                double py = sy + Math.sin(angle) * orbitRadius;

                boolean isPlanetSelected = selectedPlanet != null && selectedPlanet.id().equals(p.id());
                if (isPlanetSelected) {
                    gc.setStroke(Color.AQUA);
                    gc.setLineWidth(2.0);
                    gc.strokeOval(px - 8 * zoomFactor, py - 8 * zoomFactor, 16 * zoomFactor, 16 * zoomFactor);
                }

                gc.setFill(p.populations().isEmpty() ? Color.web("#7f8c8d") : Color.web("#2ecc71"));
                gc.fillOval(px - 4 * zoomFactor, py - 4 * zoomFactor, 8 * zoomFactor, 8 * zoomFactor);

                pIndex++;
            }
        }

        // 5. Draw Fleets
        for (Fleet fleet : fleets) {
            double fx = centerX + fleet.coordinateX() * 1.5 * zoomFactor;
            double fy = centerY + fleet.coordinateY() * 1.5 * zoomFactor;

            boolean isFleetSelected = selectedFleet != null && selectedFleet.id().equals(fleet.id());

            // Selection ring
            if (isFleetSelected) {
                gc.setStroke(Color.CYAN);
                gc.setLineWidth(2.0);
                gc.strokeOval(fx - 12 * zoomFactor, fy - 12 * zoomFactor, 24 * zoomFactor, 24 * zoomFactor);
            }

            gc.setFill(fleet.isInWarp() ? Color.web("#9b59b6") : Color.web("#e74c3c"));
            gc.fillPolygon(
                    new double[]{fx, fx - 5 * zoomFactor, fx + 5 * zoomFactor},
                    new double[]{fy - 7 * zoomFactor, fy + 5 * zoomFactor, fy + 5 * zoomFactor},
                    3
            );

            // Draw warp trajectory vector
            if (fleet.isInWarp() && fleet.targetSystemId() != null) {
                for (SolarSystem sys : solarSystems) {
                    if (sys.id().equalsIgnoreCase(fleet.targetSystemId())) {
                        double tx = centerX + sys.x() * 1.5 * zoomFactor;
                        double ty = centerY + sys.y() * 1.5 * zoomFactor;
                        gc.setStroke(Color.web("#9b59b6", 0.7));
                        gc.setLineWidth(1.5);
                        gc.strokeLine(fx, fy, tx, ty);
                        break;
                    }
                }
            }
        }

        // 6. Draw Megastructures
        for (Megastructure mega : megastructures) {
            SolarSystem sys = solarSystems.stream()
                    .filter(s -> s.id().equalsIgnoreCase(mega.systemId()) || s.name().equalsIgnoreCase(mega.systemId()))
                    .findFirst()
                    .orElse(null);

            double mx = sys != null ? centerX + sys.x() * 1.5 * zoomFactor + 30.0 * zoomFactor : centerX + 50.0 * zoomFactor;
            double my = sys != null ? centerY + sys.y() * 1.5 * zoomFactor - 30.0 * zoomFactor : centerY + 50.0 * zoomFactor;

            if (mega.isOperational()) {
                gc.setStroke(Color.web("#f1c40f"));
                gc.setLineWidth(2.0 * zoomFactor);
                gc.strokeOval(mx - 10 * zoomFactor, my - 10 * zoomFactor, 20 * zoomFactor, 20 * zoomFactor);

                gc.setFill(Color.web("#00cec9"));
                gc.fillOval(mx - 4 * zoomFactor, my - 4 * zoomFactor, 8 * zoomFactor, 8 * zoomFactor);

                gc.setFill(Color.GOLD);
                gc.setFont(Font.font("Verdana", FontWeight.BOLD, 9 * zoomFactor));
                gc.fillText(mega.name(), mx - 20 * zoomFactor, my - 14 * zoomFactor);
            } else {
                gc.setStroke(Color.web("#e67e22"));
                gc.setLineWidth(1.5 * zoomFactor);
                gc.strokeOval(mx - 8 * zoomFactor, my - 8 * zoomFactor, 16 * zoomFactor, 16 * zoomFactor);

                gc.setFill(Color.LIGHTGRAY);
                gc.setFont(Font.font("Verdana", FontWeight.NORMAL, 8 * zoomFactor));
                gc.fillText(mega.name() + " [Const]", mx - 20 * zoomFactor, my - 12 * zoomFactor);
            }
        }
    }
}
