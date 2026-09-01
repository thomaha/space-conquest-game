package com.spaceconquest.frontend;

import com.spaceconquest.frontend.empire.Tab;
import com.spaceconquest.control.HumanController;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive UI panel providing a guided tutorial campaign onboarding player rulers
 * through science, planetary industry, spaceship engineering and interstellar diplomacy,
 * along with a sub-tab for the interactive 2D tactical combat arena.
 */
public class TutorialOnboardingView {

    public enum Tab {
        TUTORIAL("Campaign tutorial"),
        ARENA("Tactical arena");

        private final String displayName;

        Tab(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public record TutorialMission(
            int stepNumber,
            String title,
            String description,
            String hint,
            boolean isCompleted
    ) {}

    private VBox root;
    private VBox tabContentContainer;
    private final Map<Tab, Button> tabButtons = new HashMap<>();
    private final Menubar menubar;
    private Tab currentTab = Tab.TUTORIAL;
    private TacticalCombatArenaView combatArenaView;
    private HumanController humanController;
    private final List<TutorialMission> missions = new ArrayList<>();

    public TutorialOnboardingView(Menubar menubar) {
        this(menubar, null);
    }

    public TutorialOnboardingView(Menubar menubar, TacticalCombatArenaView combatArenaView) {
        this.menubar = menubar;
        this.combatArenaView = combatArenaView;
        initDefaultMissions();
        build();
    }

    public void setCombatArenaView(TacticalCombatArenaView arenaView) {
        this.combatArenaView = arenaView;
        if (humanController != null && combatArenaView != null) {
            combatArenaView.setHumanController(humanController);
        }
    }

    public TacticalCombatArenaView getCombatArenaView() {
        return combatArenaView;
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
        if (combatArenaView != null) {
            combatArenaView.setHumanController(controller);
        }
    }

    public Tab getCurrentTab() {
        return currentTab;
    }

    public void selectTab(Tab tab) {
        if (tab != null) {
            this.currentTab = tab;
            updateTabButtonStyles();
            renderCurrentTab();
        }
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
        root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: rgba(14, 25, 45, 0.96); " +
                "-fx-border-color: #00cec9; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(980, 720);

        Text title = new Text("Tutorial and tactical combat arena");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setCursor(javafx.scene.Cursor.HAND);
        closeButton.setOnAction(e -> hide());

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getChildren().add(title);

        HBox tabHeaderBar = new HBox(8);
        tabHeaderBar.setAlignment(Pos.CENTER_LEFT);

        for (Tab tab : Tab.values()) {
            Button tabBtn = new Button(tab.getDisplayName());
            tabBtn.setPrefHeight(34);
            tabBtn.setCursor(javafx.scene.Cursor.HAND);
            tabBtn.setOnAction(e -> selectTab(tab));
            tabButtons.put(tab, tabBtn);
            tabHeaderBar.getChildren().add(tabBtn);
        }

        updateTabButtonStyles();

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.getChildren().addAll(tabHeaderBar, spacer, closeButton);

        tabContentContainer = new VBox(8);
        VBox.setVgrow(tabContentContainer, Priority.ALWAYS);

        root.getChildren().addAll(topBar, tabContentContainer);
        root.setVisible(false);
    }

    private void updateTabButtonStyles() {
        for (Map.Entry<Tab, Button> entry : tabButtons.entrySet()) {
            Button btn = entry.getValue();
            boolean active = entry.getKey() == currentTab;
            if (active) {
                btn.setStyle("-fx-background-color: #00cec9; -fx-text-fill: black; -fx-font-weight: bold; " +
                        "-fx-font-family: 'Verdana'; -fx-font-size: 12px; -fx-background-radius: 6; -fx-padding: 6 14 6 14;");
            } else {
                btn.setStyle("-fx-background-color: rgba(30, 45, 75, 0.8); -fx-text-fill: #dfe6e9; -fx-font-weight: bold; " +
                        "-fx-font-family: 'Verdana'; -fx-font-size: 12px; -fx-background-radius: 6; -fx-padding: 6 14 6 14; " +
                        "-fx-border-color: #00cec9; -fx-border-width: 1; -fx-border-radius: 6;");
            }
        }
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        show(currentTab);
    }

    public void show(Tab tab) {
        if (tab != null) {
            this.currentTab = tab;
            updateTabButtonStyles();
        }
        renderCurrentTab();
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
        if (root.isVisible() && currentTab == Tab.TUTORIAL) {
            renderCurrentTab();
        }
    }

    private void renderCurrentTab() {
        if (tabContentContainer == null) return;
        tabContentContainer.getChildren().clear();

        if (currentTab == Tab.TUTORIAL) {
            tabContentContainer.getChildren().add(buildTutorialMissionsContent());
        } else if (currentTab == Tab.ARENA) {
            tabContentContainer.getChildren().add(buildArenaContent());
        }
    }

    private VBox buildTutorialMissionsContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(4));
        VBox.setVgrow(content, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

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
            checkBox.setCursor(javafx.scene.Cursor.HAND);
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
            desc.setWrappingWidth(740);

            Text hint = new Text(mission.hint());
            hint.setFill(Color.GOLD);
            hint.setFont(Font.font("Verdana", 11));

            card.getChildren().addAll(cardHeader, desc, hint);
            content.getChildren().add(card);
        }

        VBox container = new VBox(scrollPane);
        VBox.setVgrow(container, Priority.ALWAYS);
        return container;
    }

    private VBox buildArenaContent() {
        VBox container = new VBox(6);
        VBox.setVgrow(container, Priority.ALWAYS);

        if (combatArenaView != null) {
            VBox arenaNode = combatArenaView.getArenaContentBox();
            VBox.setVgrow(arenaNode, Priority.ALWAYS);
            container.getChildren().add(arenaNode);
        } else {
            Text placeholder = new Text("Tactical combat arena initialized.");
            placeholder.setFill(Color.LIGHTCYAN);
            container.getChildren().add(placeholder);
        }

        return container;
    }
}
