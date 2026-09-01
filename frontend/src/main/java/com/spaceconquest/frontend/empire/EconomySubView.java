package com.spaceconquest.frontend.empire;

public enum EconomySubView {
    IMPERIAL("Imperial economy"),
    SYSTEM("System economy");

    private final String displayName;

    EconomySubView(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
