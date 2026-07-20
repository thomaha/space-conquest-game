package com.spaceconquest.frontend;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import static com.almasb.fxgl.dsl.FXGL.*;

public class Main extends GameApplication {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("Space Conquest Game");
        settings.setVersion("0.1");
    }

    @Override
    protected void initUI() {
        Text text = new Text("Hello Space Conquest!");
        text.setFill(Color.WHITE);
        text.setTranslateX(300);
        text.setTranslateY(300);

        addUINode(text);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
