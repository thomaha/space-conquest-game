package com.spaceconquest.frontend;

import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.List;

/**
 * A UI component that visualizes all space entities in the galaxy.
 */
public class GalaxyListView {
    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private final Menubar menubar;
    private final Main mainApp;

    public GalaxyListView(Menubar menubar, Main mainApp) {
        this.menubar = menubar;
        this.mainApp = mainApp;
        build();
    }

    private void build() {
        root = new VBox(20);
        content = new VBox(15);
        scrollPane = new ScrollPane(content);
        
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.95); " +
                      "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                      "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(1000, 800);

        Text title = new Text("Galaxy View");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 24));

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

    public void show(List<SolarSystem> solarSystems) {
        loadData(solarSystems);
        root.setVisible(true);
        root.toFront();
        menubar.openPage();
    }

    public void hide() {
        root.setVisible(false);
        menubar.closePage();
    }

    private void loadData(List<SolarSystem> solarSystems) {
        content.getChildren().clear();
        if (solarSystems == null) {
            return;
        }
        for (SolarSystem ss : solarSystems) {
            content.getChildren().add(createSolarSystemBox(ss));
        }
    }

    private VBox createSolarSystemBox(SolarSystem ss) {
        VBox ssBox = new VBox(10);
        ssBox.setPadding(new Insets(15));
        ssBox.setStyle("-fx-background-color: rgba(60, 80, 120, 0.3); -fx-background-radius: 8; -fx-border-color: rgba(120, 170, 255, 0.3); -fx-border-radius: 8;");

        HBox starHeader = new HBox(15);
        starHeader.setAlignment(Pos.CENTER_LEFT);

        // Star visual
        Circle starCircle = new Circle(Math.max(10, ss.sunDiameter() / 50000.0));
        try {
            starCircle.setFill(Color.web(ss.sunColor()));
        } catch (Exception e) {
            starCircle.setFill(Color.YELLOW);
        }
        
        VBox starInfo = new VBox(2);
        Text starName = new Text(ss.name() + " (Star)");
        starName.setFill(Color.WHITE);
        starName.setFont(Font.font("Verdana", FontWeight.BOLD, 18));
        
        Text starDetails = new Text(String.format("Type: Star | Size: %,.0f km", ss.sunDiameter()));
        starDetails.setFill(Color.LIGHTGRAY);
        starDetails.setFont(Font.font("Verdana", 12));

        starInfo.getChildren().addAll(starName, starDetails);
        starHeader.getChildren().addAll(starCircle, starInfo);
        
        ssBox.getChildren().add(starHeader);

        if (ss.planets() != null) {
            for (Planet p : ss.planets()) {
                ssBox.getChildren().add(createPlanetBox(p));
            }
        }

        return ssBox;
    }

    private VBox createPlanetBox(Planet p) {
        VBox planetBox = new VBox(5);
        planetBox.setPadding(new Insets(10, 10, 10, 40));
        
        HBox planetHeader = new HBox(15);
        planetHeader.setAlignment(Pos.CENTER_LEFT);

        // Planet visual
        Circle planetCircle = new Circle(8);
        planetCircle.setFill(getPlanetColor(p));
        if (hasAtmosphere(p)) {
             planetCircle.setStroke(Color.LIGHTBLUE);
             planetCircle.setStrokeWidth(2);
        }

        VBox planetInfo = new VBox(2);
        Text planetName = new Text(p.name());
        planetName.setFill(Color.LIGHTBLUE);
        planetName.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        String resourcesStr = resourceText(p.resources());
        Text planetDetails = new Text(String.format("Type: %s | %s | Resources: %s",
                p.type() != null ? p.type() : "unknown", populationText(p), resourcesStr));
        planetDetails.setFill(Color.GAINSBORO);
        planetDetails.setFont(Font.font("Verdana", 12));

        planetInfo.getChildren().addAll(planetName, planetDetails);
        planetHeader.getChildren().addAll(planetCircle, planetInfo);
        
        planetBox.getChildren().add(planetHeader);

        if (p.moons() != null) {
            for (Moon m : p.moons()) {
                planetBox.getChildren().add(createMoonBox(m));
            }
        }

        return planetBox;
    }

    private VBox createMoonBox(Moon m) {
        VBox moonBox = new VBox(2);
        moonBox.setPadding(new Insets(5, 10, 5, 80));

        HBox moonHeader = new HBox(10);
        moonHeader.setAlignment(Pos.CENTER_LEFT);

        Circle moonCircle = new Circle(4);
        moonCircle.setFill(Color.LIGHTGRAY);

        VBox moonInfo = new VBox(2);
        Text moonName = new Text(m.name() + " (Moon)");
        moonName.setFill(Color.WHITESMOKE);
        moonName.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        String resourcesStr = resourceText(m.resources());
        Text moonDetails = new Text(String.format("Type: Moon | Resources: %s", resourcesStr));
        moonDetails.setFill(Color.SILVER);
        moonDetails.setFont(Font.font("Verdana", 11));

        moonInfo.getChildren().addAll(moonName, moonDetails);
        moonHeader.getChildren().addAll(moonCircle, moonInfo);

        moonBox.getChildren().add(moonHeader);
        return moonBox;
    }

    private static String resourceText(List<String> resources) {
        return resources == null || resources.isEmpty() ? "None" : String.join(", ", resources);
    }

    private static boolean hasAtmosphere(Planet p) {
        return p.description() != null && p.description().toLowerCase().contains("atmosphere");
    }

    private static String populationText(Planet p) {
        if (p.populations() == null || p.populations().isEmpty()) {
            return "Uninhabited";
        }
        long total = p.populations().stream()
                .flatMap(pop -> pop.ageGroups().values().stream())
                .mapToLong(Long::longValue)
                .sum();
        return String.format("Inhabited: %,d", total);
    }

    private Color getPlanetColor(Planet p) {
        String type = p.type() != null ? p.type().toLowerCase() : "unknown";
        if (type.contains("gas")) return Color.ORANGERED;
        if (type.contains("ice")) return Color.LIGHTCYAN;
        if (type.contains("terrestrial")) return Color.FORESTGREEN;
        if (type.contains("rocky")) return Color.GRAY;
        return Color.BEIGE;
    }
}
