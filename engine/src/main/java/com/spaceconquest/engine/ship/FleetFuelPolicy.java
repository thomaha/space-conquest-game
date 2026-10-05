package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Saved main-tank contingency targets; emergency authority belongs to an individual order. */
public record FleetFuelPolicy(double routineFraction, double elevatedFraction, double wartimeFraction) {
    public FleetFuelPolicy {
        if (!Double.isFinite(routineFraction) || !Double.isFinite(elevatedFraction) || !Double.isFinite(wartimeFraction)
                || routineFraction < 0 || elevatedFraction < routineFraction || wartimeFraction < elevatedFraction
                || wartimeFraction >= 1) throw new IllegalArgumentException("Invalid contingency reserve percentages");
    }

    public static FleetFuelPolicy automatic() { return new FleetFuelPolicy(.05, .10, .20); }

    public double fraction(GameState state, Fleet fleet) {
        return switch (risk(state, fleet)) {
            case "TOTAL_WAR" -> wartimeFraction;
            case "COLD_WAR" -> elevatedFraction;
            default -> routineFraction;
        };
    }

    public String risk(GameState state, Fleet fleet) {
        String empire = state.corporations().stream().filter(corp -> corp.id().equals(fleet.ownerEntityId()))
                .map(corp -> corp.empireId()).findFirst().orElse(fleet.ownerEntityId());
        String risk = "ROUTINE";
        for (var relation : state.diplomaticRelations()) {
            if (!empire.equals(relation.empireAId()) && !empire.equals(relation.empireBId())) continue;
            if ("TOTAL_WAR".equals(relation.tier())) return "TOTAL_WAR";
            if ("COLD_WAR".equals(relation.tier())) risk = "COLD_WAR";
        }
        return risk;
    }

    public double reserveKg(GameState state, Fleet fleet, ShipInstance ship) {
        var design = FleetSupplySimulation.design(state, ship);
        return design == null ? Double.POSITIVE_INFINITY : design.fuelCapacityKg() * fraction(state, fleet);
    }

    public FleetFuelPolicy protectBoth(FleetFuelPolicy other) {
        return new FleetFuelPolicy(Math.max(routineFraction, other.routineFraction),
                Math.max(elevatedFraction, other.elevatedFraction), Math.max(wartimeFraction, other.wartimeFraction));
    }
}
