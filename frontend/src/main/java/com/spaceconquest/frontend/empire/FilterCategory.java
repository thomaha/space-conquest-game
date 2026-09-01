package com.spaceconquest.frontend.empire;

public enum FilterCategory {
    COLONIZED("Colonized"),
    UNCOLONIZED("Uncolonized"),
    COLONIZABLE("Colonizable"),
    ALL_BODIES("All planetary bodies"),
    ONLY_PLANETS("Only planets"),
    ONLY_MOONS("Only moons");

    private final String label;

    FilterCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
