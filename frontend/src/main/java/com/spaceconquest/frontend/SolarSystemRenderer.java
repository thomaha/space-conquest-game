package com.spaceconquest.frontend;

import com.almasb.fxgl.entity.Entity;
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
        registry.clear();
        displaySun();
        displayPlanets();
    }

    private void displaySun() {
        // Use a scale where 1,000,000 km = 15 pixels for the Sun's radius
        double sunRadius = (solarSystem.sunDiameter() / 2.0) / 1000000.0 * 15.0;

        Entity sun = entityBuilder()
                .at(getAppWidth() / 2.0 - sunRadius, getAppHeight() / 2.0 - sunRadius)
                .viewWithBBox(new Circle(sunRadius, sunRadius, sunRadius, Color.YELLOW))
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

        // Sort planets by distance for consistent rendering.
        planets.sort(Comparator.comparingDouble(Planet::distance));

        for (Planet planet : planets) {
            double angle = Math.random() * 2 * Math.PI;

            // Logarithmic scaling with a baseline to ensure proper relative spacing.
            double logDist = Math.log(planet.distance());
            double logMin = Math.log(5.0e7); // slightly less than Mercury
            double logMax = Math.log(maxDistance);
            double radius = minDistance + ((logDist - logMin) / (logMax - logMin)) * (availableWidth - minDistance);

            double x = (getAppWidth() / 2.0) + radius * Math.cos(angle);
            // Height above/below plane based on inclination.
            double verticalDeviation = radius * Math.sin(Math.toRadians(planet.inclination()));
            double y = (getAppHeight() / 2.0) + (radius * Math.sin(angle) * squashingFactor) - verticalDeviation;

            // Planet diameter scaling: 10,000 km = 1 pixel radius, minimum radius 2.
            double planetRadius = Math.max(2.0, (planet.diameter() / 2.0) / 10000.0);

            Entity planetEntity = entityBuilder()
                    .at(x - planetRadius, y - planetRadius)
                    .viewWithBBox(new Circle(planetRadius, planetRadius, planetRadius, Color.LIGHTBLUE))
                    .buildAndAttach();

            String planetType = planet.type() != null ? planet.type() : "planet";
            String info = String.format(
                    "%s: %s%nmass: %.3e kg%ngravity: %.2f m/s^2%ndistance from sun: %.3e km%ndiameter: %.0f km%ninclination: %.2f deg%nresources: %s%nmoons: %d",
                    planetType, planet.name(), planet.mass(), planet.gravity(), planet.distance(),
                    planet.diameter(), planet.inclination(),
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

            String info = String.format(
                    "moon: %s (orbiting %s)%nmass: %.3e kg%ngravity: %.2f m/s^2%ndistance from planet: %.3e km%ndiameter: %.0f km%nresources: %s",
                    moon.name(), planet.name(), moon.mass(), moon.gravity(),
                    moon.distance(), moon.diameter(),
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
}
