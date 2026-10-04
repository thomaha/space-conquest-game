package com.spaceconquest.engine.logistics;

/** Remaining paid freight and its purchase/launch cost basis, excluding working fuel. */
public record TradeCargo(double massKg, double costCredits) {
    public TradeCargo {
        if (!Double.isFinite(massKg) || massKg < 0 || !Double.isFinite(costCredits) || costCredits < 0)
            throw new IllegalArgumentException("Invalid paid freight");
    }
    public TradeCargo remaining(double kg) {
        double mass = Math.clamp(kg, 0, massKg);
        return new TradeCargo(mass, massKg == 0 ? 0 : costCredits * mass / massKg);
    }
}
