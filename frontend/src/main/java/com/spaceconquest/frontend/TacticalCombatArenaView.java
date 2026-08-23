package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.SetFleetStanceCommand;
import com.spaceconquest.control.command.TargetSubsystemCommand;
import com.spaceconquest.engine.audio.AudioSynthesizer;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
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
 * Interactive 2D tactical combat arena with graphical projectile rendering,
 * real-time subsystem targeting, fleet stance controls and battle playback.
 */
public class TacticalCombatArenaView {

    private VBox root;
    private Canvas combatCanvas;
    private GraphicsContext gc;
    private VBox logContent;
    private Label roundLabel;
    private Label statusLabel;
    private final Menubar menubar;
    private final AudioSynthesizer audioSynthesizer;
    private HumanController humanController;

    private TacticalCombatProcessor.CombatEngagementResult currentResult;
    private int currentRoundIndex = 0;
    private String selectedTargetSubsystem = TacticalCombatProcessor.TARGET_SUBSYSTEM_ALL;
    private String selectedFleetStance = TacticalCombatProcessor.STANCE_AGGRESSIVE_BRAWL;
    private String activeFleetId = "fleet_attacker_alpha";

    public TacticalCombatArenaView(Menubar menubar, AudioSynthesizer audioSynthesizer) {
        this.menubar = menubar;
        this.audioSynthesizer = audioSynthesizer != null ? audioSynthesizer : new AudioSynthesizer();
        build();
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    public void setActiveFleetId(String fleetId) {
        if (fleetId != null && !fleetId.isEmpty()) {
            this.activeFleetId = fleetId;
        }
    }

    private void build() {
        root = new VBox(12);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: rgba(10, 15, 30, 0.98); " +
                "-fx-border-color: #e74c3c; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(980, 720);

        Text title = new Text("Interactive 2D tactical combat arena and subsystem targeter");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> hide());

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        // 1. Interactive Tactical Controls Bar
        HBox tacticalToolbar = createTacticalToolbar();

        // 2. 2D Combat Canvas
        combatCanvas = new Canvas(940, 280);
        gc = combatCanvas.getGraphicsContext2D();
        drawEmptyArena();

        // 3. Playback Controls & Round Summary
        HBox playbackControls = createPlaybackControls();

        // 4. Detailed Combat Action Log
        logContent = new VBox(4);
        ScrollPane logScroll = new ScrollPane(logContent);
        logScroll.setPrefHeight(180);
        logScroll.setFitToWidth(true);
        logScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        root.getChildren().addAll(header, tacticalToolbar, combatCanvas, playbackControls, logScroll);
        root.setVisible(false);
    }

    public VBox getArenaContentBox() {
        VBox arenaContent = new VBox(10);
        HBox tacticalToolbar = createTacticalToolbar();
        combatCanvas = new Canvas(940, 280);
        gc = combatCanvas.getGraphicsContext2D();
        drawEmptyArena();
        HBox playbackControls = createPlaybackControls();
        logContent = new VBox(4);
        ScrollPane logScroll = new ScrollPane(logContent);
        logScroll.setPrefHeight(180);
        logScroll.setFitToWidth(true);
        logScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        arenaContent.getChildren().addAll(tacticalToolbar, combatCanvas, playbackControls, logScroll);
        return arenaContent;
    }

    private HBox createTacticalToolbar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8));
        bar.setStyle("-fx-background-color: rgba(30, 40, 70, 0.7); -fx-background-radius: 6;");

        Label targetLabel = new Label("Target subsystem:");
        targetLabel.setTextFill(Color.LIGHTCYAN);
        targetLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        Button targetAll = createTargetButton("All", TacticalCombatProcessor.TARGET_SUBSYSTEM_ALL);
        Button targetWarp = createTargetButton("Warp core", TacticalCombatProcessor.TARGET_SUBSYSTEM_WARP);
        Button targetWeapons = createTargetButton("Weapons", TacticalCombatProcessor.TARGET_SUBSYSTEM_WEAPONS);
        Button targetShields = createTargetButton("Shields", TacticalCombatProcessor.TARGET_SUBSYSTEM_SHIELDS);
        Button targetEngines = createTargetButton("Engines", TacticalCombatProcessor.TARGET_SUBSYSTEM_ENGINES);

        Label stanceLabel = new Label("Stance:");
        stanceLabel.setTextFill(Color.GOLD);
        stanceLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        Button stanceBrawl = createStanceButton("Brawl", TacticalCombatProcessor.STANCE_AGGRESSIVE_BRAWL);
        Button stanceKite = createStanceButton("Kite", TacticalCombatProcessor.STANCE_STANDOFF_KITE);
        Button stanceScreen = createStanceButton("Screen", TacticalCombatProcessor.STANCE_POINT_DEFENSE_SCREEN);
        Button stanceRetreat = createStanceButton("Retreat", TacticalCombatProcessor.STANCE_EVASIVE_RETREAT);

        bar.getChildren().addAll(targetLabel, targetAll, targetWarp, targetWeapons, targetShields, targetEngines,
                new javafx.scene.layout.Region(), stanceLabel, stanceBrawl, stanceKite, stanceScreen, stanceRetreat);
        return bar;
    }

    private Button createTargetButton(String label, String subsystem) {
        Button btn = new Button(label);
        btn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-size: 10px;");
        btn.setOnAction(e -> {
            selectedTargetSubsystem = subsystem;
            audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_UI_CLICK);
            if (humanController != null) {
                humanController.stageCommand(new TargetSubsystemCommand(activeFleetId, subsystem));
            }
            if (statusLabel != null) {
                statusLabel.setText("Active subsystem target: " + subsystem);
            }
        });
        return btn;
    }

    private Button createStanceButton(String label, String stance) {
        Button btn = new Button(label);
        btn.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #f1c40f; -fx-font-size: 10px;");
        btn.setOnAction(e -> {
            selectedFleetStance = stance;
            audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_UI_CLICK);
            if (humanController != null) {
                humanController.stageCommand(new SetFleetStanceCommand(activeFleetId, stance));
            }
            if (statusLabel != null) {
                statusLabel.setText("Fleet stance updated: " + stance);
            }
        });
        return btn;
    }

    private HBox createPlaybackControls() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6));

        roundLabel = new Label("Round: 0 / 0");
        roundLabel.setTextFill(Color.WHITE);
        roundLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Button prevBtn = new Button("◀ Prev round");
        prevBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white;");
        prevBtn.setOnAction(e -> {
            if (currentResult != null && currentRoundIndex > 0) {
                currentRoundIndex--;
                renderCurrentRound();
            }
        });

        Button nextBtn = new Button("Next round ▶");
        nextBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        nextBtn.setOnAction(e -> {
            if (currentResult != null && currentResult.visualRounds() != null && currentRoundIndex < currentResult.visualRounds().size() - 1) {
                currentRoundIndex++;
                audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_LASER_FIRE);
                renderCurrentRound();
            }
        });

        Button fireSimulationBtn = new Button("Simulate tactical skirmish");
        fireSimulationBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");
        fireSimulationBtn.setOnAction(e -> simulateDemoSkirmish());

        statusLabel = new Label("Ready | Select fleet stance and targeted enemy subsystem.");
        statusLabel.setTextFill(Color.LIGHTGREEN);
        statusLabel.setFont(Font.font("Verdana", 11));

        bar.getChildren().addAll(roundLabel, prevBtn, nextBtn, fireSimulationBtn, statusLabel);
        return bar;
    }

    public void setEngagementResult(TacticalCombatProcessor.CombatEngagementResult result) {
        this.currentResult = result;
        this.currentRoundIndex = 0;
        if (root.isVisible()) {
            renderCurrentRound();
        }
    }

    private void renderCurrentRound() {
        if (currentResult == null || currentResult.visualRounds() == null || currentResult.visualRounds().isEmpty()) {
            drawEmptyArena();
            return;
        }

        TacticalCombatProcessor.CombatVisualRound vr = currentResult.visualRounds().get(currentRoundIndex);
        roundLabel.setText(String.format("Round: %d / %d", vr.roundNumber(), currentResult.visualRounds().size()));

        // Draw Canvas Frame
        gc.setFill(Color.rgb(12, 18, 35));
        gc.fillRect(0, 0, combatCanvas.getWidth(), combatCanvas.getHeight());

        // Draw Stars / Space Grid
        gc.setStroke(Color.rgb(30, 45, 80));
        gc.setLineWidth(1);
        for (int x = 0; x < combatCanvas.getWidth(); x += 60) {
            gc.strokeLine(x, 0, x, combatCanvas.getHeight());
        }
        for (int y = 0; y < combatCanvas.getHeight(); y += 60) {
            gc.strokeLine(0, y, combatCanvas.getWidth(), y);
        }

        // Draw Attacker Ships
        if (vr.attackerShipStates() != null) {
            for (TacticalCombatProcessor.ShipVisualState s : vr.attackerShipStates()) {
                drawShip(s, true);
            }
        }

        // Draw Defender Ships
        if (vr.defenderShipStates() != null) {
            for (TacticalCombatProcessor.ShipVisualState s : vr.defenderShipStates()) {
                drawShip(s, false);
            }
        }

        // Draw Projectiles
        if (vr.activeProjectiles() != null) {
            for (TacticalCombatProcessor.VisualProjectile p : vr.activeProjectiles()) {
                drawProjectile(p);
            }
        }

        // Update Log
        logContent.getChildren().clear();
        if (vr.eventLogs() != null) {
            for (String log : vr.eventLogs()) {
                Text lText = new Text("• " + log);
                lText.setFill(Color.LIGHTCYAN);
                lText.setFont(Font.font("Verdana", 11));
                logContent.getChildren().add(lText);
            }
        }
    }

    private void drawShip(TacticalCombatProcessor.ShipVisualState ship, boolean isAttacker) {
        double x = ship.posX();
        double y = ship.posY();

        if (ship.isDestroyed()) {
            gc.setFill(Color.DARKRED);
            gc.fillOval(x - 8, y - 8, 16, 16);
            gc.setStroke(Color.ORANGE);
            gc.strokeOval(x - 12, y - 12, 24, 24);
            return;
        }

        // Ship Icon Shape
        gc.setFill(isAttacker ? Color.CYAN : Color.CRIMSON);
        double[] xPoints = isAttacker ?
                new double[]{x + 15, x - 10, x - 5, x - 10} :
                new double[]{x - 15, x + 10, x + 5, x + 10};
        double[] yPoints = new double[]{y, y - 10, y, y + 10};
        gc.fillPolygon(xPoints, yPoints, 4);

        // Shield Halo
        if (ship.shieldPct() > 0.05) {
            gc.setStroke(Color.rgb(52, 152, 219, ship.shieldPct()));
            gc.setLineWidth(2);
            gc.strokeOval(x - 18, y - 16, 36, 32);
        }

        // Hull & Shield Mini Bars
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(x - 20, y - 22, 40, 4);
        gc.setFill(Color.LIGHTGREEN);
        gc.fillRect(x - 20, y - 22, 40 * ship.hullPct(), 4);

        gc.setFill(Color.rgb(41, 128, 185));
        gc.fillRect(x - 20, y - 26, 40 * ship.shieldPct(), 3);
    }

    private void drawProjectile(TacticalCombatProcessor.VisualProjectile p) {
        gc.setStroke(Color.web(p.colorHex()));
        gc.setLineWidth(p.weaponType().equals("TORPEDO") ? 3 : 2);

        if ("LASER".equals(p.weaponType())) {
            gc.strokeLine(p.startX(), p.startY(), p.targetX(), p.targetY());
        } else {
            // Trajectory dash line
            gc.setLineDashes(6, 4);
            gc.strokeLine(p.startX(), p.startY(), p.targetX(), p.targetY());
            gc.setLineDashes(null);
            gc.setFill(Color.web(p.colorHex()));
            gc.fillOval(p.targetX() - 4, p.targetY() - 4, 8, 8);
        }
    }

    private void drawEmptyArena() {
        gc.setFill(Color.rgb(10, 15, 30));
        gc.fillRect(0, 0, combatCanvas.getWidth(), combatCanvas.getHeight());
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font("Verdana", 14));
        gc.fillText("Tactical combat arena ready. Launch skirmish or select active battle to inspect.", 180, 140);
    }

    private void simulateDemoSkirmish() {
        TacticalCombatProcessor processor = new TacticalCombatProcessor();
        com.spaceconquest.engine.ship.ShipInstance attShip1 = new com.spaceconquest.engine.ship.ShipInstance("ship_att_1", "design_cruiser", "terran_confederation", 600, 200, 100, java.util.Map.of());
        com.spaceconquest.engine.ship.ShipInstance attShip2 = new com.spaceconquest.engine.ship.ShipInstance("ship_att_2", "design_cruiser", "terran_confederation", 600, 200, 100, java.util.Map.of());
        com.spaceconquest.engine.ship.ShipInstance defShip1 = new com.spaceconquest.engine.ship.ShipInstance("ship_def_1", "design_raider", "shadow_syndicate", 450, 150, 80, java.util.Map.of());
        com.spaceconquest.engine.ship.ShipInstance defShip2 = new com.spaceconquest.engine.ship.ShipInstance("ship_def_2", "design_raider", "shadow_syndicate", 450, 150, 80, java.util.Map.of());

        com.spaceconquest.engine.ship.Fleet attFleet = new com.spaceconquest.engine.ship.Fleet("att_fleet", "1st imperial armada", "terran_confederation", "sol", "", 0, 0, 0, false, selectedFleetStance, List.of(attShip1, attShip2));
        com.spaceconquest.engine.ship.Fleet defFleet = new com.spaceconquest.engine.ship.Fleet("def_fleet", "Syndicate raider flotilla", "shadow_syndicate", "sol", "", 0, 0, 0, false, TacticalCombatProcessor.STANCE_STANDOFF_KITE, List.of(defShip1, defShip2));

        TacticalCombatProcessor.CombatEngagementResult res = processor.resolveFleetEngagementWithCarrierWings(
                attFleet, defFleet, List.of(), List.of(), List.of(), null, selectedTargetSubsystem
        );

        audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_COMBAT_FIRE);
        setEngagementResult(res);
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        root.setVisible(true);
        root.toFront();
        if (currentResult != null) {
            renderCurrentRound();
        } else {
            simulateDemoSkirmish();
        }
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }
}
