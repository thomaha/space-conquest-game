package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Resolves multi-round tactical space combat engagements between hostile fleets.
 * Accounts for weapon range brackets, shield absorption, ablative armor deflection,
 * carrier strike wing swarms, planetary defense batteries and tactical fleet stances.
 */
public class TacticalCombatProcessor {

    public static final String STANCE_AGGRESSIVE_BRAWL = "AGGRESSIVE_BRAWL";
    public static final String STANCE_STANDOFF_KITE = "STANDOFF_KITE";
    public static final String STANCE_POINT_DEFENSE_SCREEN = "POINT_DEFENSE_SCREEN";
    public static final String STANCE_EVASIVE_RETREAT = "EVASIVE_RETREAT";

    public static final String TARGET_SUBSYSTEM_ALL = "ALL";
    public static final String TARGET_SUBSYSTEM_WARP = "WARP_DRIVE";
    public static final String TARGET_SUBSYSTEM_WEAPONS = "WEAPONS";
    public static final String TARGET_SUBSYSTEM_SHIELDS = "SHIELDS";
    public static final String TARGET_SUBSYSTEM_ENGINES = "ENGINES";

    private final Random random;

    public TacticalCombatProcessor() {
        this(new Random());
    }

    public TacticalCombatProcessor(Random random) {
        this.random = random;
    }

    public record VisualProjectile(
            String weaponType, // LASER, MASS_DRIVER, TORPEDO, FLAK
            double startX,
            double startY,
            double targetX,
            double targetY,
            String colorHex,
            boolean isHit
    ) {}

    public record ShipVisualState(
            String shipId,
            String designId,
            String ownerEntityId,
            double posX,
            double posY,
            double hullPct,
            double shieldPct,
            boolean isDestroyed,
            String targetedSubsystem
    ) {}

    public record CombatVisualRound(
            int roundNumber,
            List<ShipVisualState> attackerShipStates,
            List<ShipVisualState> defenderShipStates,
            List<VisualProjectile> activeProjectiles,
            List<String> eventLogs
    ) {}

    public record CombatActionReport(
            String phase,
            String description,
            double damageAmount,
            String targetEntityId
    ) {}

    public record CombatRoundReport(
            int roundNumber,
            double attackerDamageDealt,
            double defenderDamageDealt,
            int attackerShipsDestroyed,
            int defenderShipsDestroyed,
            int attackerFightersLost,
            int defenderFightersLost,
            List<CombatActionReport> detailedActions
    ) {
        public CombatRoundReport(
                int roundNumber,
                double attackerDamageDealt,
                double defenderDamageDealt,
                int attackerShipsDestroyed,
                int defenderShipsDestroyed
        ) {
            this(roundNumber, attackerDamageDealt, defenderDamageDealt, attackerShipsDestroyed, defenderShipsDestroyed, 0, 0, List.of());
        }
    }

    public record CombatEngagementResult(
            String winnerOwnerEntityId,
            Fleet survivingAttackerFleet,
            Fleet survivingDefenderFleet,
            List<CombatRoundReport> roundReports,
            List<CarrierWing> survivingAttackerWings,
            List<CarrierWing> survivingDefenderWings,
            List<CombatVisualRound> visualRounds
    ) {
        public CombatEngagementResult(
                String winnerOwnerEntityId,
                Fleet survivingAttackerFleet,
                Fleet survivingDefenderFleet,
                List<CombatRoundReport> roundReports
        ) {
            this(winnerOwnerEntityId, survivingAttackerFleet, survivingDefenderFleet, roundReports, List.of(), List.of(), List.of());
        }

        public CombatEngagementResult(
                String winnerOwnerEntityId,
                Fleet survivingAttackerFleet,
                Fleet survivingDefenderFleet,
                List<CombatRoundReport> roundReports,
                List<CarrierWing> survivingAttackerWings,
                List<CarrierWing> survivingDefenderWings
        ) {
            this(winnerOwnerEntityId, survivingAttackerFleet, survivingDefenderFleet, roundReports, survivingAttackerWings, survivingDefenderWings, List.of());
        }
    }

    /**
     * Resolves a tactical fleet engagement over multiple combat rounds.
     */
    public CombatEngagementResult resolveFleetEngagement(
            Fleet attackerFleet,
            Fleet defenderFleet,
            List<ShipDesign> designs
    ) {
        return resolveFleetEngagementWithCarrierWings(
                attackerFleet, defenderFleet, designs, List.of(), List.of(), null, TARGET_SUBSYSTEM_ALL
        );
    }

    /**
     * Resolves a tactical fleet engagement with carrier strike wings and planetary defense batteries.
     */
    public CombatEngagementResult resolveFleetEngagementWithCarrierWings(
            Fleet attackerFleet,
            Fleet defenderFleet,
            List<ShipDesign> designs,
            List<CarrierWing> attackerWings,
            List<CarrierWing> defenderWings,
            PlanetaryDefenseBattery planetaryBattery,
            String targetedSubsystem
    ) {
        if (attackerFleet == null || defenderFleet == null) {
            return new CombatEngagementResult("DRAW", attackerFleet, defenderFleet, List.of(), attackerWings, defenderWings);
        }

        List<ShipInstance> attackers = new ArrayList<>(attackerFleet.ships());
        List<ShipInstance> defenders = new ArrayList<>(defenderFleet.ships());
        List<CarrierWing> attWings = new ArrayList<>(attackerWings != null ? attackerWings : List.of());
        List<CarrierWing> defWings = new ArrayList<>(defenderWings != null ? defenderWings : List.of());
        List<CombatRoundReport> reports = new ArrayList<>();
        List<CombatVisualRound> visualRounds = new ArrayList<>();

        int round = 1;
        while (!attackers.isEmpty() && !defenders.isEmpty() && round <= 10) {
            RoundExecutionResult res = executeCombatRound(
                    round, attackers, defenders, attackerFleet, defenderFleet, designs,
                    attWings, defWings, planetaryBattery, targetedSubsystem
            );
            attackers = res.updatedAttackers();
            defenders = res.updatedDefenders();
            reports.add(res.report());
            visualRounds.add(res.visualRound());
            round++;
        }

        String winner = determineWinner(attackers, defenders, attackerFleet, defenderFleet);

        Fleet updatedAttacker = createUpdatedFleet(attackerFleet, attackers);
        Fleet updatedDefender = createUpdatedFleet(defenderFleet, defenders);

        return new CombatEngagementResult(winner, updatedAttacker, updatedDefender, reports, attWings, defWings, visualRounds);
    }

    private record RoundExecutionResult(
            List<ShipInstance> updatedAttackers,
            List<ShipInstance> updatedDefenders,
            CombatRoundReport report,
            CombatVisualRound visualRound
    ) {}

    private RoundExecutionResult executeCombatRound(
            int round,
            List<ShipInstance> attackers,
            List<ShipInstance> defenders,
            Fleet attackerFleet,
            Fleet defenderFleet,
            List<ShipDesign> designs,
            List<CarrierWing> attWings,
            List<CarrierWing> defWings,
            PlanetaryDefenseBattery planetaryBattery,
            String targetedSubsystem
    ) {
        List<CombatActionReport> roundActions = new ArrayList<>();
        List<String> eventLogs = new ArrayList<>();
        List<VisualProjectile> activeProjectiles = new ArrayList<>();

        double attStanceMult = getStanceFirepowerModifier(attackerFleet.fleetStance());
        double defStanceMult = getStanceFirepowerModifier(defenderFleet.fleetStance());

        double attFirepower = calculateFleetFirepower(attackers, designs, round) * attStanceMult;
        double defFirepower = calculateFleetFirepower(defenders, designs, round) * defStanceMult;

        attFirepower = processAttackerWings(attWings, defenderFleet, roundActions, eventLogs, activeProjectiles, attFirepower);
        defFirepower = processDefenderWings(defWings, attackerFleet, roundActions, eventLogs, activeProjectiles, defFirepower);
        defFirepower = processPlanetaryBattery(planetaryBattery, attackerFleet, roundActions, eventLogs, activeProjectiles, defFirepower);

        addShipProjectiles(round, attFirepower, defFirepower, targetedSubsystem, activeProjectiles, eventLogs);

        int prevAttCount = attackers.size();
        int prevDefCount = defenders.size();

        List<ShipVisualState> attVisualStates = buildVisualStates(attackers, designs, true, TARGET_SUBSYSTEM_ALL);
        List<ShipVisualState> defVisualStates = buildVisualStates(defenders, designs, false, targetedSubsystem);

        List<ShipInstance> updatedDefenders = applyDamageToFleet(defenders, attFirepower, designs, targetedSubsystem);
        List<ShipInstance> updatedAttackers = applyDamageToFleet(attackers, defFirepower, designs, TARGET_SUBSYSTEM_ALL);

        int attFightersLost = (!attWings.isEmpty() && defFirepower > 50.0) ? Math.min(2, attWings.get(0).activeCraftCount()) : 0;
        int defFightersLost = (!defWings.isEmpty() && attFirepower > 50.0) ? Math.min(2, defWings.get(0).activeCraftCount()) : 0;

        int attackersLost = prevAttCount - updatedAttackers.size();
        int defendersLost = prevDefCount - updatedDefenders.size();

        if (attackersLost > 0) {
            eventLogs.add(String.format("%d attacker vessel(s) suffered critical hull destruction", attackersLost));
        }
        if (defendersLost > 0) {
            eventLogs.add(String.format("%d defender vessel(s) suffered critical hull destruction", defendersLost));
        }

        CombatRoundReport report = new CombatRoundReport(
                round, attFirepower, defFirepower,
                attackersLost, defendersLost, attFightersLost, defFightersLost, roundActions
        );
        CombatVisualRound visualRound = new CombatVisualRound(round, attVisualStates, defVisualStates, activeProjectiles, eventLogs);

        return new RoundExecutionResult(updatedAttackers, updatedDefenders, report, visualRound);
    }

    private double processAttackerWings(List<CarrierWing> attWings, Fleet defenderFleet,
                                       List<CombatActionReport> roundActions, List<String> eventLogs,
                                       List<VisualProjectile> activeProjectiles, double currentPower) {
        double power = attWings.stream().mapToDouble(CarrierWing::getTotalFirepower).sum();
        if (power > 0.0) {
            roundActions.add(new CombatActionReport("STRIKE_WING", "Attacker strike wings deployed torpedo runs", power, defenderFleet.id()));
            eventLogs.add(String.format("Attacker strike wings launched torpedo salvos dealing %.1f damage", power));
            activeProjectiles.add(new VisualProjectile("TORPEDO", 200, 250, 600, 250, "#00ffff", true));
            return currentPower + power;
        }
        return currentPower;
    }

    private double processDefenderWings(List<CarrierWing> defWings, Fleet attackerFleet,
                                       List<CombatActionReport> roundActions, List<String> eventLogs,
                                       List<VisualProjectile> activeProjectiles, double currentPower) {
        double power = defWings.stream().mapToDouble(CarrierWing::getTotalFirepower).sum();
        if (power > 0.0) {
            roundActions.add(new CombatActionReport("STRIKE_WING", "Defender strike wings deployed interceptor screens", power, attackerFleet.id()));
            eventLogs.add(String.format("Defender strike wings deployed interceptor screens dealing %.1f damage", power));
            activeProjectiles.add(new VisualProjectile("FLAK", 600, 250, 200, 250, "#2ecc71", true));
            return currentPower + power;
        }
        return currentPower;
    }

    private double processPlanetaryBattery(PlanetaryDefenseBattery planetaryBattery, Fleet attackerFleet,
                                          List<CombatActionReport> roundActions, List<String> eventLogs,
                                          List<VisualProjectile> activeProjectiles, double currentPower) {
        if (planetaryBattery != null && planetaryBattery.isOperational()) {
            double batteryDamage = planetaryBattery.damagePerRound();
            roundActions.add(new CombatActionReport("SURFACE_BATTERY", "Surface battery fired heavy kinetic salvos into orbit", batteryDamage, attackerFleet.id()));
            eventLogs.add(String.format("Ground defense battery fired planetary kinetic salvos (%.1f damage)", batteryDamage));
            activeProjectiles.add(new VisualProjectile("MASS_DRIVER", 400, 500, 200, 200, "#e67e22", true));
            return currentPower + batteryDamage;
        }
        return currentPower;
    }

    private void addShipProjectiles(int round, double attFirepower, double defFirepower, String targetedSubsystem,
                                    List<VisualProjectile> activeProjectiles, List<String> eventLogs) {
        if (attFirepower > 0.0) {
            String wType = round == 1 ? "LASER" : (round <= 3 ? "MASS_DRIVER" : "TORPEDO");
            String color = round == 1 ? "#e74c3c" : (round <= 3 ? "#f1c40f" : "#9b59b6");
            activeProjectiles.add(new VisualProjectile(wType, 150, 200 + (round * 20), 650, 200 + (round * 20), color, true));
            eventLogs.add(String.format("Attacker fleet fired %s salvos (%.1f firepower, targeting %s)", wType, attFirepower, targetedSubsystem));
        }
        if (defFirepower > 0.0) {
            String wType = round == 1 ? "LASER" : (round <= 3 ? "MASS_DRIVER" : "TORPEDO");
            String color = round == 1 ? "#3498db" : (round <= 3 ? "#f39c12" : "#1abc9c");
            activeProjectiles.add(new VisualProjectile(wType, 650, 220 + (round * 20), 150, 220 + (round * 20), color, true));
            eventLogs.add(String.format("Defender fleet returned %s fire (%.1f firepower)", wType, defFirepower));
        }
    }

    private String determineWinner(List<ShipInstance> attackers, List<ShipInstance> defenders, Fleet attackerFleet, Fleet defenderFleet) {
        if (defenders.isEmpty() && !attackers.isEmpty()) {
            return attackerFleet.ownerEntityId();
        } else if (attackers.isEmpty() && !defenders.isEmpty()) {
            return defenderFleet.ownerEntityId();
        }
        return "DRAW";
    }

    private Fleet createUpdatedFleet(Fleet original, List<ShipInstance> survivingShips) {
        return new Fleet(
                original.id(), original.name(), original.ownerEntityId(),
                original.currentSystemId(), original.targetSystemId(),
                original.coordinateX(), original.coordinateY(),
                original.transitProgress(), original.isInWarp(),
                original.fleetStance(), survivingShips
        );
    }

    private List<ShipVisualState> buildVisualStates(List<ShipInstance> ships, List<ShipDesign> designs, boolean isAttacker, String targetedSubsystem) {
        List<ShipVisualState> states = new ArrayList<>();
        double baseX = isAttacker ? 180.0 : 640.0;
        for (int i = 0; i < ships.size(); i++) {
            ShipInstance s = ships.get(i);
            ShipDesign d = findDesign(s.designId(), designs);
            double maxHull = d != null ? d.calculatedStructuralIntegrity() * 100.0 : 500.0;
            double maxShield = 200.0;

            double hullPct = Math.max(0.0, Math.min(1.0, s.currentHullHealth() / Math.max(1.0, maxHull)));
            double shieldPct = Math.max(0.0, Math.min(1.0, s.currentShieldHealth() / Math.max(1.0, maxShield)));
            double y = 140.0 + (i * 70.0);

            states.add(new ShipVisualState(
                    s.id(),
                    s.designId(),
                    s.ownerEntityId(),
                    baseX,
                    y,
                    hullPct,
                    shieldPct,
                    s.currentHullHealth() <= 0.0,
                    targetedSubsystem
            ));
        }
        return states;
    }

    private double getStanceFirepowerModifier(String stance) {
        if (stance == null) return 1.0;
        return switch (stance.toUpperCase()) {
            case STANCE_AGGRESSIVE_BRAWL -> 1.25;
            case STANCE_STANDOFF_KITE -> 1.10;
            case STANCE_POINT_DEFENSE_SCREEN -> 0.90;
            case STANCE_EVASIVE_RETREAT -> 0.0;
            default -> 1.0;
        };
    }

    private double calculateFleetFirepower(List<ShipInstance> ships, List<ShipDesign> designs, int round) {
        double totalFirepower = 0.0;
        for (ShipInstance ship : ships) {
            ShipDesign design = findDesign(ship.designId(), designs);
            double baseDamage = 50.0;
            if (design != null) {
                baseDamage = Math.max(30.0, design.totalDryMassKg() * 0.005);
            }

            double rangeModifier = switch (round) {
                case 1 -> 0.80; // Long-range opening bracket (torpedoes/lasers)
                case 2, 3 -> 1.20; // Medium-range broadside bracket (mass drivers)
                default -> 1.00; // Close-range dogfight bracket (point defense / guns)
            };

            double variance = 0.90 + random.nextDouble() * 0.20;
            totalFirepower += baseDamage * rangeModifier * variance;
        }
        return totalFirepower;
    }

    private List<ShipInstance> applyDamageToFleet(
            List<ShipInstance> ships,
            double incomingDamage,
            List<ShipDesign> designs,
            String targetedSubsystem
    ) {
        if (ships.isEmpty() || incomingDamage <= 0.0) {
            return ships;
        }

        double damagePerShip = incomingDamage / ships.size();
        List<ShipInstance> surviving = new ArrayList<>();

        for (ShipInstance ship : ships) {
            ShipDesign design = findDesign(ship.designId(), designs);
            double armorDeflection = 0.0;
            if (design != null) {
                armorDeflection = Math.min(0.60, design.armorThicknessCm() * 0.05);
            }

            // Subsystem targeting specific damage bonuses
            double subsystemMod = 1.0;
            double shieldPenetrationMod = 1.0;

            if (TARGET_SUBSYSTEM_SHIELDS.equalsIgnoreCase(targetedSubsystem)) {
                shieldPenetrationMod = 1.40; // 40% increased shield disruption
            } else if (TARGET_SUBSYSTEM_WARP.equalsIgnoreCase(targetedSubsystem)) {
                subsystemMod = 1.25; // 25% hull critical damage
            } else if (TARGET_SUBSYSTEM_ENGINES.equalsIgnoreCase(targetedSubsystem)) {
                subsystemMod = 1.20; // 20% tracking bonus
            } else if (TARGET_SUBSYSTEM_WEAPONS.equalsIgnoreCase(targetedSubsystem)) {
                subsystemMod = 1.15; // 15% component damage
            }

            double effectiveDamage = damagePerShip * (1.0 - armorDeflection) * subsystemMod;
            double shields = ship.currentShieldHealth();
            double hull = ship.currentHullHealth();

            double shieldDamage = effectiveDamage * shieldPenetrationMod;

            if (shields >= shieldDamage) {
                shields -= shieldDamage;
            } else {
                double remaining = effectiveDamage - (shields / Math.max(0.1, shieldPenetrationMod));
                shields = 0.0;
                hull -= remaining;
            }

            if (hull > 0.0) {
                surviving.add(new ShipInstance(
                        ship.id(), ship.designId(), ship.ownerEntityId(),
                        hull, shields, ship.currentFuelKg(), ship.storedCargoKg(),
                        ship.passengerCount(), ship.passengerRaceId(), ship.transitMode()
                ));
            }
        }
        return surviving;
    }

    private ShipDesign findDesign(String designId, List<ShipDesign> designs) {
        if (designs == null || designId == null) return null;
        for (ShipDesign d : designs) {
            if (d.id().equals(designId)) return d;
        }
        return null;
    }
}
