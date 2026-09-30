package com.spaceconquest.engine.ship;

/** A fleet's site inside its current solar system and any local journey in progress. */
public record FleetLocation(Site current, Site destination, double progress, double travelDays) {
    public enum Kind { SURFACE, ORBIT, DOCKED, DEEP_SPACE }

    public record Site(Kind kind, String entityId) {
        public Site {
            if (kind == null || entityId == null || entityId.isBlank())
                throw new IllegalArgumentException("A site needs a kind and entity ID");
        }

        public static Site surface(String bodyId) { return new Site(Kind.SURFACE, bodyId); }
        public static Site orbit(String bodyId) { return new Site(Kind.ORBIT, bodyId); }
        public static Site docked(String stationId) { return new Site(Kind.DOCKED, stationId); }
        public static Site deepSpace() { return new Site(Kind.DEEP_SPACE, "system_space"); }
        public static Site deepSpace(String siteId) { return new Site(Kind.DEEP_SPACE, siteId); }
    }

    public FleetLocation {
        if (current == null) throw new IllegalArgumentException("Fleet location needs a current site");
        if (!Double.isFinite(progress) || progress < 0.0 || progress > 1.0
                || !Double.isFinite(travelDays) || travelDays < 0.0)
            throw new IllegalArgumentException("Invalid local travel progress");
        if (destination == null && (progress != 0.0 || travelDays != 0.0))
            throw new IllegalArgumentException("Idle fleet cannot have travel progress");
        if (destination != null && (destination.equals(current) || travelDays <= 0.0))
            throw new IllegalArgumentException("A journey needs a distinct destination and duration");
    }

    public static FleetLocation at(Site site) { return new FleetLocation(site, null, 0.0, 0.0); }

    public boolean inTransit() { return destination != null; }

    public boolean isAt(Site site) { return !inTransit() && current.equals(site); }

    public FleetLocation depart(Site target, double days) {
        if (inTransit() || target == null || target.equals(current)
                || !Double.isFinite(days) || days <= 0.0)
            throw new IllegalArgumentException("Invalid local journey");
        return new FleetLocation(current, target, 0.0, days);
    }

    public static double travelDays(Site from, Site to) {
        if (from.entityId().equals(to.entityId())
                && ((from.kind() == Kind.SURFACE && to.kind() == Kind.ORBIT)
                || (from.kind() == Kind.ORBIT && to.kind() == Kind.SURFACE))) return 1.0;
        if (from.kind() == Kind.DOCKED || to.kind() == Kind.DOCKED) return 1.0;
        if (from.kind() == Kind.DEEP_SPACE || to.kind() == Kind.DEEP_SPACE) return 2.0;
        return 2.0;
    }

    public FleetLocation advanceDay() {
        if (!inTransit()) return this;
        double next = progress + 1.0 / travelDays;
        return next + 0.000001 >= 1.0 ? at(destination)
                : new FleetLocation(current, destination, next, travelDays);
    }
}
