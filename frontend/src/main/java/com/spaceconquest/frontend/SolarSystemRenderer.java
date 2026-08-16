package com.spaceconquest.frontend;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.TypeComponent;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.Comparator;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * Renders a {@link SolarSystem} (its sun, planets and moons) as FXGL entities
 * and registers every searchable body in the {@link GalaxyRegistry}.
 */
public class SolarSystemRenderer {
    private final SolarSystem solarSystem;
    private final GalaxyRegistry registry;

    public SolarSystemRenderer(SolarSystem solarSystem, GalaxyRegistry registry) {
        this.solarSystem = solarSystem;
        this.registry = registry;
    }

    public void render() {
        displaySun();
        displayPlanets();
    }

    private void displaySun() {
        // Use a scale where 1,000,000 km = 15 pixels for the Sun's radius
        double sunRadius = (solarSystem.sunDiameter() / 2.0) / 1000000.0 * 15.0;

        // Position based on system coordinates in light-years.
        // Scale: 1 light-year = 2000 pixels for separation between systems.
        double lyScale = 2000.0;
        double offsetX = solarSystem.x() * lyScale;
        double offsetY = solarSystem.y() * lyScale;
        double offsetZ = solarSystem.z() * lyScale;

        // Apply a simple orthographic-ish projection for the 3D coordinates.
        // Y and Z contribute to the vertical position on screen.
        double x = (getAppWidth() / 2.0) + offsetX;
        double y = (getAppHeight() / 2.0) + (offsetY * 0.1) - (offsetZ * 0.2);

        Color starColor = Color.YELLOW;
        if (solarSystem.sunColor() != null) {
            try {
                starColor = Color.web(solarSystem.sunColor());
            } catch (IllegalArgumentException e) {
                // Keep default
            }
        }

        // Special rendering for black holes
        Entity sun;
        if (solarSystem.description().contains("Black Hole")) {
            // Draw an event horizon (black) with a small accretion disk hint (purple/dark blue border)
            Circle eventHorizon = new Circle(sunRadius, sunRadius, sunRadius, Color.BLACK);
            eventHorizon.setStroke(Color.DARKVIOLET);
            eventHorizon.setStrokeWidth(2.0);

            sun = entityBuilder()
                    .at(x - sunRadius, y - sunRadius)
                    .viewWithBBox(eventHorizon)
                    .with(new TypeComponent("STAR"))
                    .buildAndAttach();
        } else {
            sun = entityBuilder()
                    .at(x - sunRadius, y - sunRadius)
                    .viewWithBBox(new Circle(sunRadius, sunRadius, sunRadius, starColor))
                    .with(new TypeComponent("STAR"))
                    .buildAndAttach();
        }

        // Label for zoom levels 7+
        entityBuilder()
                .at(x, y + sunRadius + 5)
                .view(getUIFactoryService().newText(solarSystem.name(), Color.WHITE, 12.0))
                .with(new TypeComponent("SYSTEM_LABEL"))
                .scaleOrigin(new javafx.geometry.Point2D(0, 0))
                .buildAndAttach();

        String info = String.format(
                "star: %s%ndescription: %s%nmass: %.3e kg%ndiameter: %.0f km%nplanets: %d",
                solarSystem.name(), solarSystem.description(),
                solarSystem.sunMass(), solarSystem.sunDiameter(),
                solarSystem.planets().size());
        registry.register(solarSystem.name(), sun, "star", info);
        registerHover(sun, solarSystem.name());
    }

    private void displayPlanets() {
        List<Planet> planets = solarSystem.planets();
        double maxDistance = planets.stream().mapToDouble(Planet::distance).max().orElse(1.0);
        double sunRadius = (solarSystem.sunDiameter() / 2.0) / 1000000.0 * 15.0;
        double availableWidth = (getAppWidth() / 2.0) - 100;
        double minDistance = sunRadius + 20.0; // Minimum pixels from Sun to ensure visibility
        double squashingFactor = 0.05; // 5% height for "thin pancake" look

        // Center position for this system
        double lyScale = 2000.0;
        double centerX = (getAppWidth() / 2.0) + (solarSystem.x() * lyScale);
        double centerY = (getAppHeight() / 2.0) + (solarSystem.y() * lyScale * 0.1) - (solarSystem.z() * lyScale * 0.2);

        // Sort planets by distance for consistent rendering.
        planets.sort(Comparator.comparingDouble(Planet::distance));

        for (Planet planet : planets) {
            double angle = Math.random() * 2 * Math.PI;

            // Logarithmic scaling with a baseline to ensure proper relative spacing.
            double logDist = Math.log(planet.distance());
            double logMin = Math.log(5.0e7); // slightly less than Mercury
            double logMax = Math.log(maxDistance);
            double radius = minDistance + ((logDist - logMin) / (logMax - logMin)) * (availableWidth - minDistance);

            double x = centerX + radius * Math.cos(angle);
            // Height above/below plane based on inclination.
            double verticalDeviation = radius * Math.sin(Math.toRadians(planet.inclination()));
            double y = centerY + (radius * Math.sin(angle) * squashingFactor) - verticalDeviation;

            // Planet diameter scaling: 10,000 km = 1 pixel radius, minimum radius 2.
            double planetRadius = Math.max(2.0, (planet.diameter() / 2.0) / 10000.0);

            Circle planetCircle = new Circle(planetRadius, planetRadius, planetRadius, getPlanetColor(planet));
            if (planet.atmosphere() != null && !planet.atmosphere().equalsIgnoreCase("none")) {
                planetCircle.setStroke(getAtmosphereColor(planet.atmosphere()));
                planetCircle.setStrokeWidth(Math.max(1.0, planetRadius * 0.2));
            }

            Entity planetEntity = entityBuilder()
                    .at(x - planetRadius, y - planetRadius)
                    .viewWithBBox(planetCircle)
                    .buildAndAttach();

            String planetType = planet.type() != null ? planet.type() : "planet";
            String waterInfo = planet.hasLiquidWater() ? String.format("%nliquid water: Yes") : "";
            String info = String.format(
                    "%s: %s%nmass: %.3e kg%ngravity: %.2f m/s^2%ndistance from sun: %.3e km%ndiameter: %.0f km%ninclination: %.2f deg%natmosphere: %s%s%nresources: %s%nmoons: %d",
                    planetType, planet.name(), planet.mass(), planet.gravity(), planet.distance(),
                    planet.diameter(), planet.inclination(),
                    planet.atmosphere() != null ? planet.atmosphere().replace("_", " ") : "none",
                    waterInfo,
                    String.join(", ", planet.resources()), planet.moons().size());
            registry.register(planet.name(), planetEntity, planetType, info);
            registerHover(planetEntity, planet.name());

            displayMoons(planet, x, y);
        }
    }

    private void displayMoons(Planet planet, double planetX, double planetY) {
        List<Moon> moons = planet.moons();
        if (moons == null || moons.isEmpty()) return;

        double maxMoonDistance = moons.stream().mapToDouble(Moon::distance).max().orElse(1.0);
        double moonOrbitScale = 15.0; // Fixed scale for moon orbits to keep them near planet

        for (Moon moon : moons) {
            double angle = Math.random() * Math.PI * 2;
            double distance = (moon.distance() / maxMoonDistance) * moonOrbitScale + 10;
            double mx = planetX + Math.cos(angle) * distance;
            // Also squash moon orbits for consistency with the sideways view.
            double my = planetY + Math.sin(angle) * distance * 0.2;

            // Moon diameter scaling: 2,000 km = 1 pixel radius, minimum 1.
            double moonRadius = Math.max(1.0, (moon.diameter() / 2.0) / 2000.0);

            Entity moonEntity = entityBuilder()
                    .at(mx - moonRadius, my - moonRadius)
                    .viewWithBBox(new Circle(moonRadius, moonRadius, moonRadius, Color.GRAY))
                    .buildAndAttach();

            String waterInfo = moon.hasLiquidWater() ? String.format("%nliquid water: Yes") : "";
            String info = String.format(
                    "moon: %s (orbiting %s)%nmass: %.3e kg%ngravity: %.2f m/s^2%ndistance from planet: %.3e km%ndiameter: %.0f km%natmosphere: %s%s%nresources: %s",
                    moon.name(), planet.name(), moon.mass(), moon.gravity(),
                    moon.distance(), moon.diameter(),
                    moon.atmosphere() != null ? moon.atmosphere().replace("_", " ") : "none",
                    waterInfo,
                    String.join(", ", moon.resources()));
            registry.register(moon.name(), moonEntity, "moon", info);
            registerHover(moonEntity, moon.name());
        }
    }

    /**
     * Installs a tooltip on the entity's view nodes so the body's name is shown
     * only when the mouse pointer hovers over it.
     */
    private void registerHover(Entity entity, String name) {
        Tooltip tooltip = new Tooltip(name);
        tooltip.setShowDelay(Duration.millis(100));
        entity.getViewComponent().getChildren().forEach(node ->
                Tooltip.install(node, tooltip));
    }

    private Color getAtmosphereColor(String atmosphere) {
        if (atmosphere == null) return Color.TRANSPARENT;
        String lower = atmosphere.toLowerCase();
        if (lower.contains("dense_co2")) return Color.LEMONCHIFFON;
        if (lower.contains("oxygen")) return Color.LIGHTBLUE;
        if (lower.contains("nitrogen")) return Color.POWDERBLUE;
        if (lower.contains("methane")) return Color.AQUAMARINE;
        if (lower.contains("hydrogen")) return Color.LIGHTSTEELBLUE;
        if (lower.contains("sulfur")) return Color.PALEGOLDENROD;
        return Color.LIGHTGRAY;
    }

    private Color getPlanetColor(Planet p) {
        Color baseColor = getBaseColor(p);

        // Adjust for resources (e.g., iron makes it reddish)
        if (p.resources() != null) {
            if (p.resources().contains("iron_ore")) {
                baseColor = baseColor.interpolate(Color.INDIANRED, 0.4);
            }
            if (p.resources().contains("sulfur") || p.resources().contains("refined_sulfur")) {
                baseColor = baseColor.interpolate(Color.GOLDENROD, 0.3);
            }
        }

        if (p.waterLevel() > 0) {
            // Blend with water color (Royal Blue) based on water level
            // We use a more subtle blend for Earth-like visuals
            return baseColor.interpolate(Color.ROYALBLUE, p.waterLevel() * 0.8);
        }
        return baseColor;
    }

    private Color getBaseColor(Planet p) {
        String type = p.type() != null ? p.type().toLowerCase() : "unknown";
        if (type.contains("gas")) return Color.ORANGERED;
        if (type.contains("ice")) return Color.LIGHTCYAN;
        if (type.contains("terrestrial")) return Color.FORESTGREEN;
        if (type.contains("desert")) return Color.SANDYBROWN;
        if (type.contains("ocean")) return Color.ROYALBLUE;
        if (type.contains("lava")) return Color.DARKRED;
        if (type.contains("barren")) return Color.SLATEGRAY;
        if (type.contains("toxic")) return Color.PALEGOLDENROD;
        if (type.contains("dwarf") || type.contains("protoplanet")) return Color.DARKGRAY;
        return Color.BEIGE;
    }
}
