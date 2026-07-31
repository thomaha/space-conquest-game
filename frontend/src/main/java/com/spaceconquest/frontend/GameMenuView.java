package com.spaceconquest.frontend;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * A UI component that visualizes the game menu (Save, Load, Settings, Exit).
 */
public class GameMenuView {

    private VBox root;
    private final Menubar menubar;

    public GameMenuView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    private void build() {
        root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.95); " +
                      "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                      "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(400, 500);
        root.setAlignment(Pos.TOP_CENTER);

        Text title = new Text("Game Menu");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 24));

        Button closeButton = new Button("X");
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
        closeButton.setOnAction(e -> hide());

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER);
        HBox.setHgrow(title, Priority.ALWAYS);
        
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        HBox headerContainer = new HBox(spacer, title, new HBox(spacer, closeButton));
        headerContainer.setAlignment(Pos.CENTER);
        headerContainer.setPrefWidth(360);

        VBox buttonsBox = new VBox(15);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.setPadding(new Insets(20));

        Button btnSave = createMenuButton("Save Game");
        btnSave.setOnAction(e -> {
            System.out.println("Save Game clicked - Not implemented yet");
            // TODO: Implement save logic
        });

        Button btnLoad = createMenuButton("Load Game");
        btnLoad.setOnAction(e -> {
            System.out.println("Load Game clicked - Not implemented yet");
            // TODO: Implement load logic
        });

        Button btnSettings = createMenuButton("Settings");
        btnSettings.setOnAction(e -> {
            System.out.println("Settings clicked - Not implemented yet");
            // TODO: Implement settings logic
        });

        Button btnExit = createMenuButton("Exit to Desktop");
        btnExit.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");
        btnExit.setOnAction(e -> getGameController().exit());

        buttonsBox.getChildren().addAll(btnSave, btnLoad, btnSettings, btnExit);

        root.getChildren().addAll(headerContainer, buttonsBox);
        root.setVisible(false);
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.setPrefWidth(300);
        btn.setPrefHeight(50);
        btn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");
        
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #4e6a85; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;"));
        
        return btn;
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        root.setVisible(true);
        root.toFront();
        menubar.openPage();
    }

    public void hide() {
        root.setVisible(false);
        menubar.closePage();
    }
}
