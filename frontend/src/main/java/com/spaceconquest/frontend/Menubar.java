package com.spaceconquest.frontend;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.almasb.fxgl.dsl.FXGL.*;

/** The top-level navigation and game-clock controls for the galaxy view. */
public class Menubar {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] SPEED_NAMES = {"1 min/s", "1 hour/s", "6 hours/s", "12 hours/s", "1 day/s"};
    private static final int[] MINUTES_PER_TICK = {1, 60, 360, 720, 1440};

    private VBox root;
    private Label clockLabel;
    private Label speedLabel;
    private VBox detailPanel;
    private Label detailTitle;
    private Label detailText;
    private final Timeline clock = new Timeline();
    private TechnologyView techView;
    private GameMenuView gameMenuView;
    private GalaxyListView galaxyListView;

    private LocalDateTime gameTime = LocalDateTime.of(2200, 1, 1, 8, 0);
    private int speedIndex = 1;
    private int savedSpeedIndex = 1;
    private boolean paused;
    private boolean manuallyPaused;
    private Main mainApp;

    public TechnologyView getTechView() {
        return techView;
    }

    public GameMenuView getGameMenuView() {
        return gameMenuView;
    }

    public GalaxyListView getGalaxyListView() {
        return galaxyListView;
    }

    public void build(Main mainApp) {
        this.mainApp = mainApp;
        root = new VBox(8);
        techView = new TechnologyView(this);
        gameMenuView = new GameMenuView(this);
        galaxyListView = new GalaxyListView(this, mainApp);
        clockLabel = new Label();
        speedLabel = new Label();
        detailPanel = new VBox(8);
        detailTitle = new Label();
        detailText = new Label();

        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.92); -fx-background-radius: 6;"
                + " -fx-border-color: rgba(120, 170, 255, 0.55); -fx-border-radius: 6;");
        root.setPrefWidth(getAppWidth() - 20);

        HBox navigation = new HBox(8);
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.getChildren().addAll(
                navigationButton("Empire\n0 colonies | 0 credits", "Empire",
                        "Planets in the empire\nNo colonies have been established yet.\n\nEconomy\nWealth: 0 credits\nIncome: 0 credits per turn"),
                navigationButton("Diplomacy\nAt peace", "Diplomacy",
                        "Current status: At peace\n\nDiplomatic relations\nNo other empires have been encountered."),
                techButton(),
                navigationButton("Ships & Bases\n0 ships | 0 bases", "Spaceships and star bases",
                        "Fleet overview\nSpaceships: 0\nStar bases: 0\n\nNo ships or star bases are currently registered in the empire."),
                galaxyButton());

        VBox timeView = new VBox(2, clockLabel, speedLabel);
        timeView.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(timeView, Priority.ALWAYS);

        Button slower = smallButton("−");
        slower.setTooltip(new javafx.scene.control.Tooltip("Decrease game speed"));
        slower.setOnAction(e -> changeSpeed(-1));
        Button pause = smallButton("Ⅱ");
        pause.setTooltip(new javafx.scene.control.Tooltip("Pause or resume game time"));
        pause.setOnAction(e -> togglePaused(pause));
        Button faster = smallButton("+");
        faster.setTooltip(new javafx.scene.control.Tooltip("Increase game speed"));
        faster.setOnAction(e -> changeSpeed(1));

        Button gameMenu = new Button("Game Menu");
        gameMenu.setStyle(buttonStyle());
        gameMenu.setPrefHeight(52);
        gameMenu.setOnAction(e -> gameMenuView.show());

        Button genGalaxy = new Button("New Galaxy");
        genGalaxy.setStyle(buttonStyle());
        genGalaxy.setPrefHeight(52);
        genGalaxy.setOnAction(e -> {
            openPage();
            TextInputDialog dialog = new TextInputDialog("10");
            dialog.setTitle("Generate New Galaxy");
            dialog.setHeaderText("Enter number of solar systems to generate:");
            dialog.setContentText("Solar Systems:");
            dialog.showAndWait().ifPresent(input -> {
                try {
                    int num = Integer.parseInt(input);
                    mainApp.createNewGalaxy(num);
                } catch (NumberFormatException ex) {
                    // Ignore or show error
                }
            });
            closePage();
        });

        HBox timeControls = new HBox(4, timeView, slower, pause, faster, genGalaxy, gameMenu);
        timeControls.setAlignment(Pos.CENTER_RIGHT);
        navigation.getChildren().add(timeControls);
        root.getChildren().add(navigation);

        detailTitle.setTextFill(Color.WHITE);
        detailTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        detailText.setTextFill(Color.LIGHTGRAY);
        detailText.setWrapText(true);
        Button close = smallButton("X");
        close.setOnAction(e -> {
            detailPanel.setVisible(false);
            closePage();
        });
        HBox detailHeader = new HBox(detailTitle, close);
        detailHeader.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(detailTitle, Priority.ALWAYS);
        detailPanel.getChildren().addAll(detailHeader, detailText);
        detailPanel.setPadding(new Insets(14));
        detailPanel.setPrefWidth(300);
        detailPanel.setTranslateX(20);
        detailPanel.setTranslateY(95);
        detailPanel.setVisible(false);
        detailPanel.setStyle("-fx-background-color: rgba(25, 38, 75, 0.94); -fx-background-radius: 6;"
                + " -fx-border-color: rgba(120, 170, 255, 0.7); -fx-border-radius: 6;");

        root.setTranslateX(10);
        root.setTranslateY(10);
        addUINode(root);
        addUINode(detailPanel);

        updateClockLabels();
        restartClock();
    }

    private Button techButton() {
        Button button = new Button("Technology\nVisualization available");
        button.setPrefWidth(205);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            detailPanel.setVisible(false);
            if (galaxyListView != null) galaxyListView.hide();
            if (gameMenuView != null) gameMenuView.hide();
            openPage();
            techView.show();
        });
        return button;
    }

    private Button galaxyButton() {
        Button button = new Button("Galaxy\nView");
        button.setPrefWidth(205);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            detailPanel.setVisible(false);
            if (techView != null) techView.getRoot().setVisible(false);
            if (gameMenuView != null) gameMenuView.hide();
            galaxyListView.show(mainApp.getSolarSystems());
        });
        return button;
    }

    private Button navigationButton(String text, String title, String description) {
        Button button = new Button(text);
        button.setPrefWidth(205);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            openPage();
            showDetails(title, description);
        });
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(text);
        button.setMinWidth(30);
        button.setStyle(buttonStyle());
        return button;
    }

    private String buttonStyle() {
        return "-fx-background-color: #263d69; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-background-radius: 4;";
    }

    private void showDetails(String title, String description) {
        detailTitle.setText(title);
        detailText.setText(description);
        detailPanel.setVisible(true);
    }

    private void changeSpeed(int change) {
        speedIndex = Math.max(0, Math.min(SPEED_NAMES.length - 1, speedIndex + change));
        updateClockLabels();
        restartClock();
    }

    private void togglePaused(Button pause) {
        paused = !paused;
        manuallyPaused = paused;
        pause.setText(paused ? "▶" : "Ⅱ");
        restartClock();
    }

    public void openPage() {
        if (!paused) {
            savedSpeedIndex = speedIndex;
            paused = true;
            restartClock();
            updateClockLabels();
        }
    }

    public void closePage() {
        if (!manuallyPaused) {
            paused = false;
            speedIndex = savedSpeedIndex;
            restartClock();
            updateClockLabels();
        }
    }

    private void restartClock() {
        clock.stop();
        if (!paused) {
            clock.getKeyFrames().setAll(new KeyFrame(Duration.seconds(1), e -> {
                gameTime = gameTime.plusMinutes(MINUTES_PER_TICK[speedIndex]);
                updateClockLabels();
            }));
            clock.setCycleCount(Timeline.INDEFINITE);
            clock.play();
        }
    }

    private void updateClockLabels() {
        clockLabel.setText("TIME  " + gameTime.format(TIME_FORMAT));
        clockLabel.setTextFill(Color.WHITE);
        speedLabel.setText("SPEED  " + (paused ? "PAUSED" : SPEED_NAMES[speedIndex]));
        speedLabel.setTextFill(Color.LIGHTGRAY);
    }
}
