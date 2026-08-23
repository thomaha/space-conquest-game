package com.spaceconquest.frontend;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
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
 * Interactive UI panel providing a guided tutorial campaign onboarding player rulers
 * through science, planetary industry, spaceship engineering and interstellar diplomacy.
 */
public class TutorialOnboardingView {

    public record TutorialMission(
            int stepNumber,
            String title,
            String description,
            String hint,
            boolean isCompleted
    ) {}

    private VBox root;
    private VBox content;
    private final Menubar menubar;
    private final List<TutorialMission> missions = new ArrayList<>();

    public TutorialOnboardingView(Menubar menubar) {
        this.menubar = menubar;
        initDefaultMissions();
        build();
    }

    private void initDefaultMissions() {
        missions.clear();
        missions.add(new TutorialMission(
                1, "Scientific breakthroughs and technology",
                "Open the Imperial Technology Laboratory and assign research scientists to a foundational technology or technical application.",
                "Hint: Click 'Technology' in the top menu and click 'Start research' on any technology card.",
                false
        ));
        missions.add(new TutorialMission(
                2, "Planetary prospecting and industrial expansion",
                "Conduct a geological prospecting survey on an orbital planet and commission the construction of a surface manufacturing facility.",
                "Hint: Click 'Industries' in the top menu, pick a planet, and click 'Order facility construction'.",
                false
        ));
        missions.add(new TutorialMission(
                3, "Modular starframe engineering and shipyards",
                "Engineer a physics-compliant spaceship blueprint in the Shipyard Designer and queue ship construction in orbital drydocks.",
                "Hint: Click 'Shipyard' in the top menu, configure chassis modules, and click 'Register blueprint design'.",
                false
        ));
        missions.add(new TutorialMission(
                4, "Interstellar expansion and galactic legislation",
                "Deploy a colonization fleet to seed an uninhabited world or introduce a resolution to the Galactic Senate floor.",
                "Hint: Click 'Colonies' or 'Galactic Senate' in the top menu to exercise sovereign leadership.",
                false
        ));
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(14, 25, 45, 0.96); " +
                "-fx-border-color: #00cec9; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(850, 620);

        Text title = new Text("Imperial command tutorial and strategic onboarding");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 22));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> hide());

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(10));

        root.getChildren().addAll(header, scrollPane);
        root.setVisible(false);
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        renderContent();
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public void markMissionCompleted(int stepNumber) {
        for (int i = 0; i < missions.size(); i++) {
            TutorialMission m = missions.get(i);
            if (m.stepNumber() == stepNumber) {
                missions.set(i, new TutorialMission(m.stepNumber(), m.title(), m.description(), m.hint(), true));
            }
        }
        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        Text welcome = new Text("Welcome, Sovereign Commander. Complete the foundational directives below to master empire management:");
        welcome.setFill(Color.LIGHTCYAN);
        welcome.setFont(Font.font("Verdana", 13));
        content.getChildren().add(welcome);

        for (TutorialMission mission : missions) {
            VBox card = new VBox(6);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: " +
                    (mission.isCompleted() ? "#2ecc71" : "#00cec9") + "; -fx-border-width: 1; -fx-border-radius: 8;");

            HBox cardHeader = new HBox(10);
            cardHeader.setAlignment(Pos.CENTER_LEFT);

            CheckBox checkBox = new CheckBox();
            checkBox.setSelected(mission.isCompleted());
            checkBox.setOnAction(e -> {
                int idx = mission.stepNumber() - 1;
                if (idx >= 0 && idx < missions.size()) {
                    missions.set(idx, new TutorialMission(mission.stepNumber(), mission.title(), mission.description(), mission.hint(), checkBox.isSelected()));
                }
            });

            Text missionTitle = new Text(String.format("Mission %d: %s %s",
                    mission.stepNumber(), mission.title(), mission.isCompleted() ? "✔ [COMPLETED]" : "[IN PROGRESS]"));
            missionTitle.setFill(mission.isCompleted() ? Color.LIGHTGREEN : Color.AQUA);
            missionTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

            cardHeader.getChildren().addAll(checkBox, missionTitle);

            Text desc = new Text(mission.description());
            desc.setFill(Color.WHITE);
            desc.setFont(Font.font("Verdana", 12));
            desc.setWrappingWidth(720);

            Text hint = new Text(mission.hint());
            hint.setFill(Color.GOLD);
            hint.setFont(Font.font("Verdana", 11));

            card.getChildren().addAll(cardHeader, desc, hint);
            content.getChildren().add(card);
        }
    }
}
