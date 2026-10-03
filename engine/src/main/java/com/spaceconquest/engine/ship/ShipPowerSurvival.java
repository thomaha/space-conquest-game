package com.spaceconquest.engine.ship;

/** Provisional outage tuning. Losses start after a grace period and apply only during an ongoing outage. */
public final class ShipPowerSurvival {
    public static final double PASSENGER_GRACE_HOURS = 24;
    public static final double CONSCIOUS_DAILY_LOSS_FRACTION = .01;
    public static final double STASIS_DAILY_LOSS_FRACTION = .02;
    private ShipPowerSurvival() {}

    public static int casualties(ShipInstance ship, ShipDesign design) {
        var power = ship.powerState();
        if (power == null || design == null || design.powerProfile() == null || ship.passengerCount() <= 0
                || power.lastUnmetEssentialKwh() <= 0) return 0;
        double unmetToday = power.lastUnmetEssentialKwh() / design.powerProfile().essentialKw(ship, design);
        double exposed = Math.min(unmetToday, Math.max(0, power.unmetEssentialHours() - PASSENGER_GRACE_HOURS));
        double rate = ShipInstance.MODE_CRYOGENIC_STASIS.equals(ship.transitMode())
                ? STASIS_DAILY_LOSS_FRACTION : CONSCIOUS_DAILY_LOSS_FRACTION;
        return (int) Math.min(ship.passengerCount(), Math.ceil(ship.passengerCount() * rate * exposed / 24));
    }
}
