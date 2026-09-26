package com.spaceconquest.frontend.empire;

public enum Tab {
    ECONOMY("Economy"),
    CABINET("Empire cabinet"),
    PLANETS("Planets"),
    STATIONS("Orbital stations and shipyards"),
    CORPORATIONS("Corporation registry"),
    MEGASTRUCTURES("Megastructures");

    private final String displayName;

    Tab(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
