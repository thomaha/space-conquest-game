package com.spaceconquest.engine;

public record GameState(long turn, String status) {
    public GameState() {
        this(0, "INITIALIZING");
    }
}
