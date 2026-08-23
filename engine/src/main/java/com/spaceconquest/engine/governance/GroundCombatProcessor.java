package com.spaceconquest.engine.governance;

import com.spaceconquest.engine.Race;

/**
 * Resolves planetary ground sieges, garrison mobilization, militia conscription,
 * deep crust bunkers, defense batteries, and society structure morale thresholds.
 */
public class GroundCombatProcessor {

    /**
     * Calculates total attacking ground combat power.
     *
     * @param attackingSoldiers       headcount of invading soldier units
     * @param attackerRace            species biological traits
     * @param ministryDefenseModifier  ministry of defense synergy multiplier
     * @return total calculated attacking strength score
     */
    public double calculateAttackerStrength(
            long attackingSoldiers,
            Race attackerRace,
            double ministryDefenseModifier
    ) {
        if (attackingSoldiers <= 0 || attackerRace == null) {
            return 0.0;
        }
        double strengthModifier = attackerRace.physicalStrength();
        double ministryBonus = ministryDefenseModifier > 0 ? ministryDefenseModifier : 1.0;
        return attackingSoldiers * strengthModifier * ministryBonus;
    }

    /**
     * Calculates total defending ground combat power with deep crust bunkers and surface defense batteries.
     *
     * @param defendingSoldiers      headcount of garrisoned soldier units
     * @param conscriptedMilitia     headcount of emergency mobilized civilian workers
     * @param defenderRace           species biological traits
     * @param fortificationCount     number of active surface fortification nodes
     * @param deepCrustBunkers       number of deep crust underground bunkers
     * @param surfaceDefenseBatteries number of planetary defense turret batteries
     * @param governorIsSoldier      whether the system governor has a military background
     * @param defenderSocietyStructure society structure (Individualist, Collectivist, Hive Mind)
     * @return total calculated defending strength score
     */
    public double calculateDefenderStrength(
            long defendingSoldiers,
            long conscriptedMilitia,
            Race defenderRace,
            int fortificationCount,
            int deepCrustBunkers,
            int surfaceDefenseBatteries,
            boolean governorIsSoldier,
            String defenderSocietyStructure
    ) {
        if (defenderRace == null) {
            return 0.0;
        }
        double strengthModifier = defenderRace.physicalStrength();
        double soldierPower = defendingSoldiers * strengthModifier;
        double militiaPower = conscriptedMilitia * strengthModifier * 0.50; // militia fights at half efficiency

        double batteryPower = Math.max(0, surfaceDefenseBatteries) * 150.0;
        double bunkerProtection = 1.0 + (Math.max(0, deepCrustBunkers) * 0.30);
        double fortificationMultiplier = 1.0 + (Math.max(0, fortificationCount) * 0.50);
        double governorMultiplier = governorIsSoldier ? 1.25 : 1.00;

        // Hive mind defenders fight to the last organism with zero morale decay
        double moraleMultiplier = 1.0;
        if ("Hive Mind".equalsIgnoreCase(defenderSocietyStructure) || "Hive mind".equalsIgnoreCase(defenderSocietyStructure)) {
            moraleMultiplier = 1.30;
        }

        return ((soldierPower + militiaPower) * bunkerProtection + batteryPower)
                * fortificationMultiplier * governorMultiplier * moraleMultiplier;
    }

    public double calculateDefenderStrength(
            long defendingSoldiers,
            long conscriptedMilitia,
            Race defenderRace,
            int fortificationCount,
            boolean governorIsSoldier
    ) {
        return calculateDefenderStrength(
                defendingSoldiers, conscriptedMilitia, defenderRace,
                fortificationCount, 0, 0, governorIsSoldier, "Individualist"
        );
    }

    /**
     * Resolves a turn of planetary ground combat engagement.
     */
    public GroundCombatResult resolveCombat(
            long attackingSoldiers,
            Race attackerRace,
            double ministryDefenseModifier,
            long defendingSoldiers,
            long conscriptedMilitia,
            Race defenderRace,
            int fortificationCount,
            int deepCrustBunkers,
            int surfaceDefenseBatteries,
            boolean governorIsSoldier,
            String defenderSocietyStructure
    ) {
        double attackPower = calculateAttackerStrength(attackingSoldiers, attackerRace, ministryDefenseModifier);
        double defensePower = calculateDefenderStrength(
                defendingSoldiers, conscriptedMilitia, defenderRace,
                fortificationCount, deepCrustBunkers, surfaceDefenseBatteries,
                governorIsSoldier, defenderSocietyStructure
        );

        if (attackPower > defensePower) {
            long survivingAttackers = (long) Math.max(1, attackingSoldiers * (1.0 - (defensePower / (attackPower + 1.0)) * 0.5));
            return new GroundCombatResult(
                    true,
                    survivingAttackers,
                    0,
                    "Attacker broke planetary defenses and occupied the colony."
            );
        } else {
            long survivingDefenders = (long) Math.max(1, defendingSoldiers * (1.0 - (attackPower / (defensePower + 1.0)) * 0.5));
            return new GroundCombatResult(
                    false,
                    0,
                    survivingDefenders,
                    "Defender successfully repelled the invading ground forces."
            );
        }
    }

    public GroundCombatResult resolveCombat(
            long attackingSoldiers,
            Race attackerRace,
            double ministryDefenseModifier,
            long defendingSoldiers,
            long conscriptedMilitia,
            Race defenderRace,
            int fortificationCount,
            boolean governorIsSoldier
    ) {
        return resolveCombat(
                attackingSoldiers, attackerRace, ministryDefenseModifier,
                defendingSoldiers, conscriptedMilitia, defenderRace,
                fortificationCount, 0, 0, governorIsSoldier, "Individualist"
        );
    }

    public record GroundCombatResult(
            boolean attackerWon,
            long survivingAttackers,
            long survivingDefenders,
            String combatLog
    ) {}
}
