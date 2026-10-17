package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipCombatPower;
import com.spaceconquest.engine.ship.WeaponHit;
import com.spaceconquest.engine.ship.ShipModule;
import com.spaceconquest.engine.ship.ShipMissileWeapons;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * Resolves multi-round tactical space combat engagements between hostile fleets.
 * Accounts for application-defined hit delivery, shield absorption, armor deflection,
 * carrier strike wing swarms, planetary defense batteries and tactical fleet stances.
 */
public class TacticalCombatProcessor {

    public static final String STANCE_AGGRESSIVE_BRAWL = "AGGRESSIVE_BRAWL";
    public static final double SECONDS_PER_ROUND = 60;
    public static final String STANCE_STANDOFF_KITE = "STANDOFF_KITE";
    public static final String STANCE_POINT_DEFENSE_SCREEN = "POINT_DEFENSE_SCREEN";
    public static final String STANCE_EVASIVE_RETREAT = "EVASIVE_RETREAT";

    public static final String TARGET_SUBSYSTEM_ALL = "ALL";
    public static final String TARGET_SUBSYSTEM_WARP = "WARP_DRIVE";
    public static final String TARGET_SUBSYSTEM_WEAPONS = "WEAPONS";
    public static final String TARGET_SUBSYSTEM_SHIELDS = "SHIELDS";
    public static final String TARGET_SUBSYSTEM_ENGINES = "ENGINES";

    private final Random random;
    private final java.util.Set<String> refittingShips;

    public TacticalCombatProcessor() {
        this(new Random());
    }

    public TacticalCombatProcessor(Random random) {
        this(random, java.util.Set.of());
    }
    private TacticalCombatProcessor(Random random, java.util.Set<String> refittingShips) {
        this.random = random;
        this.refittingShips = java.util.Set.copyOf(refittingShips);
    }
    public TacticalCombatProcessor forState(com.spaceconquest.engine.GameState state) {
        var reserved = new java.util.HashSet<String>();
        state.shipConstructionOrders().stream().filter(order -> order.refit() != null).forEach(order -> reserved.add(order.refit().shipId()));
        return new TacticalCombatProcessor(random, reserved);
    }

    public record VisualProjectile(
            String weaponType, // LASER, MASS_DRIVER, TORPEDO, FLAK
            double startX,
            double startY,
            double targetX,
            double targetY,
            String colorHex,
            boolean isHit,
            boolean instantDelivery
    ) {
        public VisualProjectile(String weaponType, double startX, double startY, double targetX, double targetY,
                                String colorHex, boolean isHit) {
            this(weaponType, startX, startY, targetX, targetY, colorHex, isHit, false);
        }
    }

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
            String targetEntityId,
            String sourceEntityId,
            int paidShots,
            double paidEnergyKwh
    ) {
        public CombatActionReport(String phase, String description, double damage, String target) {
            this(phase, description, damage, target, "", 0, 0);
        }
    }

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
            List<CombatVisualRound> visualRounds,
            PlanetaryDefenseBattery survivingPlanetaryBattery,
            List<PlanetaryDefenseBattery> survivingPlanetaryBatteries
    ) {
        public CombatEngagementResult {
            survivingPlanetaryBatteries = survivingPlanetaryBatteries == null
                    ? survivingPlanetaryBattery == null ? List.of() : List.of(survivingPlanetaryBattery) : List.copyOf(survivingPlanetaryBatteries);
            survivingPlanetaryBattery = survivingPlanetaryBatteries.isEmpty() ? null : survivingPlanetaryBatteries.getFirst();
            roundReports = List.copyOf(roundReports);
            survivingAttackerWings = survivingAttackerWings == null ? List.of() : List.copyOf(survivingAttackerWings);
            survivingDefenderWings = survivingDefenderWings == null ? List.of() : List.copyOf(survivingDefenderWings);
            visualRounds = List.copyOf(visualRounds);
        }
        public CombatEngagementResult(String winner, Fleet attacker, Fleet defender, List<CombatRoundReport> reports,
                                      List<CarrierWing> attWings, List<CarrierWing> defWings, List<CombatVisualRound> visuals,
                                      PlanetaryDefenseBattery battery) {
            this(winner, attacker, defender, reports, attWings, defWings, visuals, battery, null);
        }
        public CombatEngagementResult(String winner, Fleet attacker, Fleet defender, List<CombatRoundReport> reports,
                                      List<CarrierWing> attWings, List<CarrierWing> defWings, List<CombatVisualRound> visuals) {
            this(winner, attacker, defender, reports, attWings, defWings, visuals, null);
        }
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

    /** Tick-owned adapter may attempt a physical withdrawal after each simultaneous round. */
    public record Withdrawal(Fleet attacker, Fleet defender, boolean departed) {}

    public CombatEngagementResult resolveFleetEngagement(Fleet attacker, Fleet defender, List<ShipDesign> designs,
            BiFunction<Fleet, Fleet, Withdrawal> withdrawal) {
        return resolveFleetEngagement(attacker, defender, designs, withdrawal, null);
    }

    /** Campaign targeting is a live shared-coverage query against each round's surviving fleets. */
    public CombatEngagementResult resolveFleetEngagement(Fleet attacker, Fleet defender, List<ShipDesign> designs,
            BiFunction<Fleet, Fleet, Withdrawal> withdrawal, java.util.function.BiPredicate<Fleet, Fleet> targeting) {
        return resolveFleetEngagement(attacker, defender, designs, withdrawal, targeting, null);
    }
    public CombatEngagementResult resolveFleetEngagement(Fleet attacker, Fleet defender, List<ShipDesign> designs,
            BiFunction<Fleet, Fleet, Withdrawal> withdrawal, java.util.function.BiPredicate<Fleet, Fleet> targeting,
            java.util.function.ToDoubleBiFunction<Fleet, Fleet> separation) {
        return resolveFleetEngagementWithCarrierWings(attacker, defender, designs, List.of(), List.of(), null,
                TARGET_SUBSYSTEM_ALL, withdrawal, targeting, separation);
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
        return resolveFleetEngagementWithCarrierWings(attackerFleet, defenderFleet, designs, attackerWings,
                defenderWings, planetaryBattery, targetedSubsystem, null, null, null);
    }

    public CombatEngagementResult resolveFleetEngagementWithCarrierWings(
            Fleet attackerFleet, Fleet defenderFleet, List<ShipDesign> designs,
            List<CarrierWing> attackerWings, List<CarrierWing> defenderWings,
            PlanetaryDefenseBattery planetaryBattery, String targetedSubsystem,
            BiFunction<Fleet, Fleet, Withdrawal> withdrawal, java.util.function.BiPredicate<Fleet, Fleet> targeting,
            java.util.function.ToDoubleBiFunction<Fleet, Fleet> separation) {
        return resolveSurfaceEngagement(attackerFleet, defenderFleet, designs, attackerWings, defenderWings,
                planetaryBattery == null ? List.of() : List.of(planetaryBattery), targetedSubsystem,
                withdrawal == null ? null : (a, b, batteries) -> withdrawal.apply(a, b), targeting, separation);
    }

    @FunctionalInterface public interface BatteryWithdrawal {
        Withdrawal apply(Fleet attacker, Fleet defender, List<PlanetaryDefenseBattery> batteries);
    }
    /** Surface groups share simultaneous impacts and the same projectile interception budget. */
    public CombatEngagementResult resolveSurfaceEngagement(
            Fleet attackerFleet, Fleet defenderFleet, List<ShipDesign> designs,
            List<CarrierWing> attackerWings, List<CarrierWing> defenderWings,
            List<PlanetaryDefenseBattery> surfaceBatteries, String targetedSubsystem,
            BatteryWithdrawal withdrawal, java.util.function.BiPredicate<Fleet, Fleet> targeting,
            java.util.function.ToDoubleBiFunction<Fleet, Fleet> separation) {
        var batteries = new ArrayList<>(surfaceBatteries);
        if (attackerFleet == null || defenderFleet == null) {
            return new CombatEngagementResult("DRAW", attackerFleet, defenderFleet, List.of(), attackerWings, defenderWings, List.of(),
                    batteries.isEmpty() ? null : batteries.getFirst(), batteries);
        }

        List<ShipInstance> attackers = new ArrayList<>(attackerFleet.ships());
        List<ShipInstance> defenders = new ArrayList<>(defenderFleet.ships());
        List<CarrierWing> attWings = new ArrayList<>(attackerWings != null ? attackerWings : List.of());
        List<CarrierWing> defWings = new ArrayList<>(defenderWings != null ? defenderWings : List.of());
        List<CombatRoundReport> reports = new ArrayList<>();
        List<CombatVisualRound> visualRounds = new ArrayList<>();
        var attackerMissiles = new GuidedMissileFlight();
        var defenderMissiles = new GuidedMissileFlight();

        int round = 1;
        while (!attackers.isEmpty() && !defenders.isEmpty() && round <= 10) {
            var currentAttacker = attackerFleet.withShips(attackers);
            var currentDefender = defenderFleet.withShips(defenders);
            boolean attackContact = targeting == null || targeting.test(currentAttacker, currentDefender);
            boolean defendContact = targeting == null || targeting.test(currentDefender, currentAttacker);
            if (!attackContact && !defendContact) {
                if (!visualRounds.isEmpty()) {
                    var logs = visualRounds.getLast().eventLogs();
                    logs.add("Both fleets lost live sensor contact; firing ends.");
                    attackerMissiles.end(logs, "live guidance contact lost");
                    defenderMissiles.end(logs, "live guidance contact lost");
                }
                break;
            }
            RoundExecutionResult res = executeCombatRound(
                    round, attackers, defenders, attackerFleet, defenderFleet, designs,
                    attWings, defWings, batteries, targetedSubsystem, attackContact, defendContact,
                    separation == null ? 0 : separation.applyAsDouble(currentAttacker, currentDefender),
                    attackerMissiles, defenderMissiles, targeting
            );
            attackers = res.updatedAttackers();
            defenders = res.updatedDefenders();
            reports.add(res.report());
            visualRounds.add(res.visualRound());
            if (withdrawal != null && !attackers.isEmpty() && !defenders.isEmpty()) {
                var decision = withdrawal.apply(attackerFleet.withShips(attackers), defenderFleet.withShips(defenders), List.copyOf(batteries));
                attackerFleet = decision.attacker();
                defenderFleet = decision.defender();
                attackers = new ArrayList<>(attackerFleet.ships());
                defenders = new ArrayList<>(defenderFleet.ships());
                if (decision.departed()) {
                    res.visualRound().eventLogs().add("A funded coordinated withdrawal ends this engagement; survivors retain return intent.");
                    break;
                }
            }
            round++;
        }

        if (!visualRounds.isEmpty()) {
            attackerMissiles.end(visualRounds.getLast().eventLogs(), "engagement ended");
            defenderMissiles.end(visualRounds.getLast().eventLogs(), "engagement ended");
        }
        String winner = determineWinner(attackers, defenders, attackerFleet, defenderFleet);

        Fleet updatedAttacker = createUpdatedFleet(attackerFleet, attackers);
        Fleet updatedDefender = createUpdatedFleet(defenderFleet, defenders);

        return new CombatEngagementResult(winner, updatedAttacker, updatedDefender, reports, List.copyOf(attWings), List.copyOf(defWings), visualRounds,
                batteries.isEmpty() ? null : batteries.getFirst(), batteries);
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
            List<PlanetaryDefenseBattery> batteries,
            String targetedSubsystem, boolean attackContact, boolean defendContact, double separationMeters,
            GuidedMissileFlight attackerMissiles, GuidedMissileFlight defenderMissiles,
            java.util.function.BiPredicate<Fleet, Fleet> targeting
    ) {
        List<CombatActionReport> roundActions = new ArrayList<>();
        List<String> eventLogs = new ArrayList<>();
        List<VisualProjectile> activeProjectiles = new ArrayList<>();

        double attStanceMult = getStanceFirepowerModifier(attackerFleet.fleetStance());
        double defStanceMult = getStanceFirepowerModifier(defenderFleet.fleetStance());

        if (!attackContact) eventLogs.add(attackerFleet.id() + ": firing held; no live shared sensor contact.");
        if (!defendContact) eventLogs.add(defenderFleet.id() + ": firing held; no live shared sensor contact.");
        if (attStanceMult <= 0) eventLogs.add(attackerFleet.id() + ": firing held by fleet stance.");
        if (defStanceMult <= 0) eventLogs.add(defenderFleet.id() + ": firing held by fleet stance.");
        var attackPower = combatPower(attackers, designs, attackContact && attStanceMult > 0, eventLogs, attackerFleet.shieldPolicy(), separationMeters,
                defenderMissiles, defendContact, attackContact, attackerFleet.id(), roundActions);
        boolean surfaceTarget = batteries.stream().anyMatch(b -> defenderFleet.ships().stream().anyMatch(s -> s.id().equals(b.combatEntityId())));
        var defendPower = combatPower(defenders, designs, defendContact && defStanceMult > 0 && !surfaceTarget, eventLogs, defenderFleet.shieldPolicy(), separationMeters,
                attackerMissiles, attackContact, defendContact, defenderFleet.id(), roundActions);
        attackers = attackPower.stream().map(ShipCombatPower.Result::ship).toList();
        defenders = defendPower.stream().map(ShipCombatPower.Result::ship).toList();
        var attHits = directHits(attackPower, attStanceMult);
        var defHits = directHits(defendPower, defStanceMult);
        attHits.addAll(attackerMissiles.advanceHits(attackContact, defenderFleet.id(), eventLogs, roundActions));
        defHits.addAll(defenderMissiles.advanceHits(defendContact, attackerFleet.id(), eventLogs, roundActions));
        if (attackContact && attStanceMult > 0)
            auxiliaryWeapons(attWings, null, attackers, attHits, attackerMissiles, separationMeters, attStanceMult, defenderFleet.id(), eventLogs, roundActions);
        if (defendContact && defStanceMult > 0) {
            auxiliaryWeapons(defWings, null, defenders, defHits, defenderMissiles, separationMeters, defStanceMult, attackerFleet.id(), eventLogs, roundActions);
            for (int i = 0; i < batteries.size(); i++) batteries.set(i, auxiliaryWeapons(List.of(), batteries.get(i), defenders, defHits,
                    defenderMissiles, separationMeters, defStanceMult, attackerFleet.id(), eventLogs, roundActions));
        }
        attackerMissiles.launch(attackPower, separationMeters, attStanceMult, defenderFleet.id(), eventLogs, roundActions);
        defenderMissiles.launch(defendPower, separationMeters, defStanceMult, attackerFleet.id(), eventLogs, roundActions);

        int prevAttCount = attackers.size();
        int prevDefCount = defenders.size();

        List<ShipVisualState> attVisualStates = buildVisualStates(attackers, designs, true, TARGET_SUBSYSTEM_ALL);
        List<ShipVisualState> defVisualStates = buildVisualStates(defenders, designs, false, targetedSubsystem);

        var currentAttacker = attackerFleet.withShips(attackers); var currentDefender = defenderFleet.withShips(defenders);
        var attacking = new ProjectileTargetingContext(attackerMissiles.pending(attackContact), defenderMissiles.pending(defendContact),
                ships -> targeting != null && targeting.test(currentDefender.withShips(ships), currentAttacker));
        var defending = new ProjectileTargetingContext(defenderMissiles.pending(defendContact), attackerMissiles.pending(attackContact),
                ships -> targeting != null && targeting.test(currentAttacker.withShips(ships), currentDefender));
        var defenderHits = WeaponImpactResolver.apply(defenders, attHits, designs, targetedSubsystem, random, roundActions,
                CombatTargetingDoctrine.defaults(), attacking);
        var attackerHits = WeaponImpactResolver.apply(attackers, defHits, designs, TARGET_SUBSYSTEM_ALL, random, roundActions,
                CombatTargetingDoctrine.defaults(), defending);
        roundActions.stream().filter(action -> action.phase().equals("WEAPON_HIT") || action.phase().equals("WEAPON_MISS")
                || action.phase().equals("TARGET_SELECTION"))
                .map(action -> action.targetEntityId() + ": " + action.description()).forEach(eventLogs::add);
        List<ShipInstance> updatedDefenders = defenderHits.ships();
        List<ShipInstance> updatedAttackers = attackerHits.ships();
        if (surfaceTarget) {
            for (int i = 0; i < batteries.size(); i++) {
                var battery = batteries.get(i);
                var survivor = updatedDefenders.stream().filter(s -> s.id().equals(battery.combatEntityId())).findFirst().orElse(null);
                batteries.set(i, battery.withDamage(survivor == null ? 0 : survivor.currentHullHealth(),
                        survivor == null ? battery.moduleDamage() : survivor.moduleDamage()));
            }
        }
        double attFirepower = defenderHits.landedDamage(), defFirepower = attackerHits.landedDamage();
        addShipProjectiles(round, attHits, defenders, true, activeProjectiles, eventLogs, roundActions);
        addShipProjectiles(round, defHits, attackers, false, activeProjectiles, eventLogs, roundActions);

        // Standalone wings have no per-craft damage state; report only losses actually resolved.
        int attFightersLost = 0, defFightersLost = 0;

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

    private static ArrayList<WeaponHit> directHits(List<ShipCombatPower.Result> ships, double stance) {
        var hits = new ArrayList<WeaponHit>();
        for (var ship : ships) for (var hit : ship.directHits()) hits.add(hit.scaled(stance));
        return hits;
    }

    private PlanetaryDefenseBattery auxiliaryWeapons(List<CarrierWing> wings, PlanetaryDefenseBattery battery, List<ShipInstance> carriers, List<WeaponHit> hits,
            GuidedMissileFlight flight, double meters, double stance, String target, List<String> logs, List<CombatActionReport> actions) {
        for (int index = 0; index < wings.size(); index++) {
            var wing = wings.get(index);
            boolean active = !refittingShips.contains(wing.carrierShipId()) && carriers.stream().anyMatch(ship -> ship.id().equals(wing.carrierShipId()) && ship.currentHullHealth() > 0);
            if (active) wings.set(index, wing.withStores(auxiliaryWeapon(wing.weapon(), wing.activeCraftCount(), wing.stores(), hits,
                    flight, meters, stance, target, "STRIKE_WING", wing.id(), logs, actions)));
        }
        double condition = battery == null ? 0 : battery.moduleDamage().condition(0);
        return battery != null && battery.isOperational() && condition > 0 ? battery.withStores(auxiliaryWeapon(battery.weapon(), 1, battery.stores(),
                hits, flight, meters, stance * condition, target, "SURFACE_BATTERY", battery.id(), logs, actions)) : battery;
    }


    private static AuxiliaryWeaponStores auxiliaryWeapon(ShipModule weapon, int count, AuxiliaryWeaponStores stores, List<WeaponHit> hits, GuidedMissileFlight flight,
            double meters, double stance, String target, String phase, String source, List<String> logs, List<CombatActionReport> actions) {
        var paid = stores.fire(weapon, count, meters);
        var funded = paid.hits().stream().map(hit -> hit.scaled(stance)).toList();
        if (!paid.reason().isBlank()) {
            String reason = weapon.name() + ": " + paid.reason();
            logs.add(reason); actions.add(new CombatActionReport("WEAPON_HOLD", reason, 0, target));
        }
        if (!funded.isEmpty()) {
            if (ShipMissileWeapons.projectile(weapon)) flight.launchHits(funded, target, logs, actions); else hits.addAll(funded);
            logs.add(weapon.name() + ": auxiliary fire paid " + paid.spentKwh() + " kWh including upkeep.");
            actions.add(new CombatActionReport(phase, weapon.name() + " fired", funded.stream().mapToDouble(WeaponHit::damage).sum(), target,
                    source, funded.size(), paid.spentKwh()));
        }
        return paid.stores();
    }

    private static void addShipProjectiles(int round, List<WeaponHit> hits, List<ShipInstance> targets, boolean attacking,
            List<VisualProjectile> projectiles, List<String> logs, List<CombatActionReport> actions) {
        var targetIds = targets.stream().map(ShipInstance::id).toList();
        for (var weapon : hits.stream().map(WeaponHit::weapon).distinct().toList()) {
            boolean landed = actions.stream().anyMatch(action -> action.phase().equals("WEAPON_HIT")
                    && action.description().startsWith(weapon.name() + " hit:") && targetIds.contains(action.targetEntityId()));
            projectiles.add(new VisualProjectile(weapon.name(), attacking ? 150 : 650, 200 + round * 20,
                    attacking ? 650 : 150, 200 + round * 20, attacking ? "#e74c3c" : "#3498db", landed,
                    !ShipMissileWeapons.projectile(weapon)));
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
        return original.withShips(survivingShips);
    }

    private List<ShipVisualState> buildVisualStates(List<ShipInstance> ships, List<ShipDesign> designs, boolean isAttacker, String targetedSubsystem) {
        List<ShipVisualState> states = new ArrayList<>();
        double baseX = isAttacker ? 180.0 : 640.0;
        for (int i = 0; i < ships.size(); i++) {
            ShipInstance s = ships.get(i);
            ShipDesign d = findDesign(s.designId(), designs);
            double maxHull = d != null ? d.calculatedStructuralIntegrity() * 100.0 : 500.0;
            double maxShield = com.spaceconquest.engine.ship.CombatModuleCatalog.shieldCapacity(d);

            double hullPct = Math.clamp(s.currentHullHealth() / Math.max(1.0, maxHull), 0.0, 1.0);
            double shieldPct = Math.clamp(ShipCombatStats.of(d, s).shields() / Math.max(1.0, maxShield), 0.0, 1.0);
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
        return ShipCombatStats.stanceModifier(stance);
    }

    private List<ShipCombatPower.Result> combatPower(List<ShipInstance> ships, List<ShipDesign> designs,
                                                    boolean firing, List<String> eventLogs, com.spaceconquest.engine.ship.FleetShieldPolicy.Mode policy, double separationMeters,
                                                    GuidedMissileFlight incoming, boolean guidanceContact, boolean sharedContact, String defendedFleet,
                                                    List<CombatActionReport> actions) {
        double sensorRange = ships.stream().mapToDouble(ship -> com.spaceconquest.engine.ship.SensorModuleCatalog
                .profile(findDesign(ship.designId(), designs), ship).rangeKm() * 1000).max().orElse(0);
        double range = sharedContact ? Double.POSITIVE_INFINITY : sensorRange;
        var results = new ArrayList<ShipCombatPower.Result>();
        for (var ship : ships) {
            if (refittingShips.contains(ship.id())) {
                results.add(new ShipCombatPower.Result(ship, 0, 0, 0));
                eventLogs.add(ship.id() + ": weapons and point defense offline during refit.");
                continue;
            }
            var design = findDesign(ship.designId(), designs);
            var threats = incoming.incomingThreats(range, guidanceContact);
            var result = ShipCombatPower.roundThreats(ship, design, SECONDS_PER_ROUND / 3600, firing,
                    policy != com.spaceconquest.engine.ship.FleetShieldPolicy.Mode.CONSERVE_ENERGY, separationMeters, threats);
            WeaponFiringReport.append(ship, design, result, firing, separationMeters, threats, eventLogs, actions);
            incoming.intercept(result.pointDefenseShots(), defendedFleet, eventLogs, actions);
            if (result.requestedWeaponKwh() > 0)
                eventLogs.add(String.format("%s: weapon salvos funded %.3f / %.3f kWh", ship.id(),
                        result.paidWeaponKwh(), result.requestedWeaponKwh()));
            if (result.paidPointDefenseKwh() > 0) eventLogs.add(ship.id() + ": point defense paid " + result.paidPointDefenseKwh() + " kWh.");
            results.add(result);
        }
        return List.copyOf(results);
    }

    private ShipDesign findDesign(String designId, List<ShipDesign> designs) {
        if (designs == null || designId == null) return null;
        for (ShipDesign d : designs) {
            if (d.id().equals(designId)) return d;
        }
        return null;
    }
}
