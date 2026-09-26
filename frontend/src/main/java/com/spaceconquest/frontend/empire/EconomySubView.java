package com.spaceconquest.frontend.empire;

public enum EconomySubView {
    IMPERIAL("Empire treasury and budget"),
    SYSTEM("System budget and taxes");

    private final String displayName;

    EconomySubView(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
