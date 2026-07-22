package com.spaceconquest.frontend;

import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;

import java.util.function.Consumer;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * Manages the viewport zoom levels and keeps the camera centered on the
 * currently focused celestial body or world point.
 */
public class CameraController {
    public static final int MIN_ZOOM = 1;  // closest
    public static final int MAX_ZOOM = 10; // furthest

    private final GalaxyRegistry registry;
    private Consumer<String> infoUpdater = info -> {};

    private int zoomLevel = 5; // Start at middle zoom
    private Entity focusedEntity;
    private Point2D focusPoint;

    public CameraController(GalaxyRegistry registry) {
        this.registry = registry;
    }

    public void setInfoUpdater(Consumer<String> infoUpdater) {
        this.infoUpdater = infoUpdater;
    }

    public int getZoomLevel() {
        return zoomLevel;
    }

    public void zoomIn() {
        if (zoomLevel > MIN_ZOOM) {
            zoomLevel--;
            updateZoom();
        }
    }

    public void zoomOut() {
        if (zoomLevel < MAX_ZOOM) {
            zoomLevel++;
            updateZoom();
        }
    }

    public void gotoEntity(String name) {
        gotoEntity(name, 1);
    }

    public void gotoEntity(String name, int targetZoomLevel) {
        Entity entity = registry.get(name);
        if (entity != null) {
            focusedEntity = entity;
            focusPoint = entity.getCenter();
            zoomLevel = targetZoomLevel;
            infoUpdater.accept(registry.getInfo(name));
            updateZoom();
        }
    }

    public void focusOnEntity(Entity entity, int targetZoomLevel) {
        focusedEntity = entity;
        focusPoint = entity.getCenter();
        zoomLevel = targetZoomLevel;
        infoUpdater.accept(registry.infoFor(entity));
        updateZoom();
    }

    public void focusOnPoint(Point2D worldPoint) {
        focusedEntity = null;
        focusPoint = worldPoint;
        infoUpdater.accept(null);
        updateZoom();
    }

    /**
     * Finds the searchable celestial body whose bounding circle contains (or is
     * closest to) the given world point, so that clicks near small bodies still hit.
     */
    public Entity findEntityAt(Point2D world) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : registry.entities()) {
            Point2D center = entity.getCenter();
            double radius = Math.max(entity.getWidth(), entity.getHeight()) / 2.0;
            double tolerance = Math.max(radius, 8.0);
            double dist = world.distance(center);
            if (dist <= tolerance && dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    public void updateZoom() {
        double scale = scaleFor(zoomLevel);

        getGameScene().getViewport().setZoom(scale);
        set("zoomLevel", zoomLevel);

        if (focusedEntity != null) {
            focusPoint = focusedEntity.getCenter();
        }
        if (focusPoint != null) {
            double zoom = getGameScene().getViewport().getZoom();
            getGameScene().getViewport().setX(focusPoint.getX() - getAppWidth() / (2.0 * zoom));
            getGameScene().getViewport().setY(focusPoint.getY() - getAppHeight() / (2.0 * zoom));
        }
    }

    /**
     * Maps a zoom level (1 closest .. 10 furthest) to a viewport scale.
     */
    private static double scaleFor(int zoomLevel) {
        return switch (zoomLevel) {
            case 1 -> 40.0;
            case 2 -> 20.0;
            case 3 -> 10.0;
            case 4 -> 5.0;
            case 5 -> 2.5;
            case 6 -> 1.5;
            case 7 -> 1.0;
            case 8 -> 0.7;
            case 9 -> 0.5;
            case 10 -> 0.3;
            default -> 1.0;
        };
    }
}
