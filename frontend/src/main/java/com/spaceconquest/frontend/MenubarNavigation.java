package com.spaceconquest.frontend;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory and state manager for Menubar navigation buttons and toolbar styling.
 */
public class MenubarNavigation {

    public static final String STYLE_NORMAL = "-fx-background-color: #263d69; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;";
    public static final String STYLE_SELECTED = "-fx-background-color: #4a90e2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #ffffff; -fx-border-width: 2; -fx-border-radius: 4;";
    public static final String STYLE_TUTORIAL_NORMAL = "-fx-background-color: #00cec9; -fx-text-fill: black; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;";
    public static final String STYLE_TUTORIAL_SELECTED = "-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #ffffff; -fx-border-width: 2; -fx-border-radius: 4;";
    public static final String STYLE_ARENA_NORMAL = "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;";
    public static final String STYLE_ARENA_SELECTED = "-fx-background-color: #a93226; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #ffffff; -fx-border-width: 2; -fx-border-radius: 4;";

    private final List<Button> navButtons = new ArrayList<>();
    private Button activeButton;

    public List<Button> getNavButtons() {
        return navButtons;
    }

    public Button createNavButton(String text, Runnable onAction) {
        Button button = new Button(text);
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(STYLE_NORMAL);
        button.setCursor(Cursor.HAND);
        button.setOnAction(e -> {
            setActiveButton(button);
            if (onAction != null) {
                onAction.run();
            }
        });
        navButtons.add(button);
        return button;
    }

    public Button createSpecialButton(String text, String initialStyle, Runnable onAction) {
        Button button = new Button(text);
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(initialStyle);
        button.setCursor(Cursor.HAND);
        button.setOnAction(e -> {
            setActiveButton(button);
            if (onAction != null) {
                onAction.run();
            }
        });
        navButtons.add(button);
        return button;
    }

    public static Button createSmallButton(String text) {
        Button button = new Button(text);
        button.setMinWidth(30);
        button.setStyle(STYLE_NORMAL);
        button.setCursor(Cursor.HAND);
        return button;
    }

    public void setActiveButton(Button button) {
        activeButton = button;
        for (Button btn : navButtons) {
            boolean isActive = (btn == activeButton);
            String text = btn.getText();
            if (text.equals("Tutorial")) {
                btn.setStyle(isActive ? STYLE_TUTORIAL_SELECTED : STYLE_TUTORIAL_NORMAL);
            } else if (text.contains("arena") || text.contains("Arena")) {
                btn.setStyle(isActive ? STYLE_ARENA_SELECTED : STYLE_ARENA_NORMAL);
            } else {
                btn.setStyle(isActive ? STYLE_SELECTED : STYLE_NORMAL);
            }
        }
    }
}
