package com.spaceconquest.frontend.empire;

public enum SortOption {
    NAME_AZ("Name (A-Z)"),
    SYSTEM_NAME("Star system name"),
    POPULATION_DESC("Population (High to Low)"),
    SIZE_DESC("Diameter / Size"),
    GRAVITY_DESC("Surface gravity"),
    RESOURCES_DESC("Resource vein count");

    private final String label;

    SortOption(String label) {
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
