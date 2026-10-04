package com.spaceconquest.engine.governance;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEmployment;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.governance.GroundCombatProcessor.GroundCombatResult;
import com.spaceconquest.engine.habitation.PassengerManifest;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Validates transport-delivered troop deployments and applies their campaign-state effects. */
public final class PlanetaryInvasionProcessor {
    private final GroundCombatProcessor combatProcessor = new GroundCombatProcessor();
    private final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();

    public boolean canInvade(GameState state, String attackerId, String systemId,
                             String planetId, String fleetId, String shipId) {
        if (state == null || attackerId == null || systemId == null || planetId == null
                || fleetId == null || shipId == null) return false;
        Empire attacker = empire(state, attackerId);
        Empire defender = ownerOfSystem(state, systemId);
        Planet planet = planet(state, systemId, planetId);
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())).findFirst().orElse(null);
        if (attacker == null || defender == null || defender.id().equals(attackerId) || planet == null
                || fleet == null || !attackerId.equals(fleet.ownerEntityId())
                || !systemId.equals(fleet.currentSystemId()) || fleet.hasInterstellarOrder()
                || fleet.location().inTransit()
                || !fleet.location().isAt(FleetLocation.Site.surface(planetId))) return false;
        ShipInstance ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElse(null);
        if (ship == null || !attackerId.equals(ship.ownerEntityId())) return false;
        ShipDesign design = state.shipDesigns().stream()
                .filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
        if (design == null || !ShipRole.TROOP_TRANSPORT.equalsIgnoreCase(design.role())) return false;
        PassengerManifest deployment = state.passengerManifests().stream()
                .filter(item -> shipId.equals(item.shipId()) && item.combatDeployment())
                .findFirst().orElse(null);
        return deployment != null && planetId.equals(deployment.destinationBodyId())
                && deployment.headcount() > 0 && ship.passengerCount() == deployment.headcount()
                && atWar(attackerId, defender.id(), state.diplomaticRelations());
    }

    public GameState resolve(GameState state, String attackerId, String systemId,
                             String planetId, String fleetId, String shipId) {
        if (!canInvade(state, attackerId, systemId, planetId, fleetId, shipId)) return state;
        Empire attacker = empire(state, attackerId);
        Empire defender = ownerOfSystem(state, systemId);
        Planet target = planet(state, systemId, planetId);
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())).findFirst().orElseThrow();
        ShipInstance transport = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
        PassengerManifest deployment = state.passengerManifests().stream()
                .filter(item -> shipId.equals(item.shipId()) && item.combatDeployment()).findFirst().orElseThrow();
        Race attackerRace = race(deployment.raceId());
        Race defenderRace = race(defender.raceId());
        if (attackerRace == null || defenderRace == null) return state;

        long defenders = state.householdAccounts().stream()
                .filter(account -> planetId.equals(account.bodyId()) && defender.id().equals(account.empireId())
                        && defender.raceId().equalsIgnoreCase(account.raceId())
                        && "soldier".equalsIgnoreCase(account.professionId()))
                .mapToLong(account -> account.employment().publicWorkers()).sum();
        long militia = state.systemEconomies().stream()
                .filter(economy -> systemId.equals(economy.systemId()) && defender.id().equals(economy.empireId()))
                .mapToLong(SystemEconomy::recruitableSoldiers).findFirst().orElse(0L);
        GroundDefenses defenses = defenses(state, planetId);
        GroundCombatResult battle = combatProcessor.resolveCombat(deployment.headcount(), attackerRace, 1.0,
                defenders, militia, defenderRace, defenses.fortifications(), defenses.bunkers(),
                defenses.batteries(), false, defender.societyStructure());

        GameState updated = updateDefenderCasualties(state, planetId, systemId, defender,
                defenders, battle.survivingDefenders());
        if (battle.attackerWon()) {
            updated = addPopulation(updated, systemId, planetId, deployment.raceId(),
                    survivingAges(deployment.ageGroups(), battle.survivingAttackers()));
            updated = transferSystem(updated, systemId, attackerId);
        }
        return removeDeployment(updated, fleet, transport, deployment);
    }

    private GameState updateDefenderCasualties(GameState state, String planetId, String systemId,
                                                Empire defender,
                                                long committed, long survivors) {
        long casualties = Math.max(0, committed - survivors);
        if (casualties == 0) return state;
        Map<Integer, Long> lostAges = removePopulationAges(planet(state, systemId, planetId),
                defender.raceId(), casualties);
        GameState updated = adjustPopulation(state, systemId, planetId, defender.raceId(), lostAges, -1);
        List<HouseholdAccount> accounts = updated.householdAccounts().stream().map(account -> {
            if (!planetId.equals(account.bodyId()) || !defender.id().equals(account.empireId())
                    || !defender.raceId().equalsIgnoreCase(account.raceId())
                    || !"soldier".equalsIgnoreCase(account.professionId())) return account;
            long lost = Math.min(casualties, account.employment().publicWorkers());
            return adjustSoldierAccount(account, lost);
        }).toList();
        return updated.toBuilder().householdAccounts(accounts).build();
    }

    private HouseholdAccount adjustSoldierAccount(HouseholdAccount account, long removed) {
        HouseholdEmployment employment = account.employment();
        long count = Math.min(removed, Math.min(account.headcount(), employment.workingAge()));
        return new HouseholdAccount(account.bodyId(), account.systemId(), account.empireId(), account.raceId(),
                account.professionId(), account.headcount() - count, account.savingsCredits(),
                account.wageIncomeCredits(), account.welfareIncomeCredits(), account.incomeTaxPaidCredits(),
                account.marketSpendingCredits(), account.unmetBasicKg(), account.secondaryNeedsMetFraction(),
                account.luxuryNeedsMetFraction(), account.electricitySpendingCredits(),
                account.unmetBasicElectricityKwh(), account.wellbeing(), new HouseholdEmployment(
                employment.workingAge() - count, Math.max(0, employment.publicWorkers() - count),
                employment.industryWorkers(), employment.unemploymentPressure()));
    }

    private GameState removeDeployment(GameState state, Fleet fleet, ShipInstance transport,
                                       PassengerManifest deployment) {
        List<Fleet> fleets = state.fleets().stream().map(current -> !fleet.id().equals(current.id()) ? current
                : current.withShips(current.ships().stream().map(ship -> !transport.id().equals(ship.id()) ? ship
                : new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                ship.currentShieldHealth(), ship.currentFuelKg(), ship.storedCargoKg(), 0, "",
                ship.transitMode(), ship.powerState(), ship.supplyState())).toList())).toList();
        List<PassengerManifest> manifests = state.passengerManifests().stream()
                .filter(item -> !deployment.shipId().equals(item.shipId())).toList();
        return state.toBuilder().fleets(fleets).passengerManifests(manifests).build();
    }

    private GameState transferSystem(GameState state, String systemId, String newOwnerId) {
        List<Empire> empires = state.empires().stream().map(empire -> {
            List<String> systems = new ArrayList<>(empire.controlledSystemIds());
            systems.removeIf(systemId::equals);
            if (newOwnerId.equals(empire.id())) systems.add(systemId);
            return withControlledSystems(empire, systems);
        }).toList();
        List<SystemEconomy> economies = state.systemEconomies().stream().map(economy ->
                !systemId.equals(economy.systemId()) ? economy : withEconomyOwner(economy, newOwnerId)).toList();
        return state.toBuilder().empires(empires).systemEconomies(economies).build();
    }

    private Empire withControlledSystems(Empire empire, List<String> systems) {
        return new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(),
                empire.treasuryCredits(), empire.corporateTaxRate(), systems, empire.ministries(),
                empire.systemGovernorAssignments(), empire.unlockedTechIds(), empire.activeShipDesignIds());
    }

    private SystemEconomy withEconomyOwner(SystemEconomy e, String ownerId) {
        return new SystemEconomy(e.systemId(), ownerId, e.educationAllocation(), e.lawAndOrderAllocation(),
                e.healthAndWelfareAllocation(), e.infrastructureAllocation(), e.planetaryMilitiasAllocation(),
                e.totalBudgetCredits(), e.accumulatedMilitiaInvestment(), e.educationLevel(), e.lawAndOrderLevel(),
                e.healthAndWelfareLevel(), e.infrastructureLevel(), e.planetaryMilitiaLevel(), e.employedTeachers(),
                e.employedScientists(), e.employedPolice(), e.employedMedics(), e.employedEngineers(),
                e.employedTechnicians(), e.employedSoldiers(), e.recruitableSoldiers(), e.taxRate(),
                e.empireContributionRate());
    }

    private GameState addPopulation(GameState state, String systemId, String planetId,
                                    String raceId, Map<Integer, Long> additions) {
        return adjustPopulation(state, systemId, planetId, raceId, additions, 1);
    }

    private GameState adjustPopulation(GameState state, String systemId, String planetId, String raceId,
                                       Map<Integer, Long> ageDelta, int sign) {
        List<SolarSystem> systems = state.solarSystems().stream().map(system -> {
            if (!systemId.equals(system.id())) return system;
            List<Planet> planets = system.planets().stream().map(current -> !planetId.equals(current.id())
                    ? current : updatePlanetPopulation(current, raceId, ageDelta, sign)).toList();
            return new SolarSystem(system.id(), system.name(), system.description(), system.x(),
                    system.y(), system.z(), system.sunMass(), system.sunDiameter(), system.sunColor(),
                    planets, system.asteroidBelts());
        }).toList();
        return state.toBuilder().solarSystems(systems).build();
    }

    private Planet updatePlanetPopulation(Planet planet, String raceId, Map<Integer, Long> delta, int sign) {
        Map<Integer, Long> groups = new HashMap<>();
        List<Population> populations = new ArrayList<>();
        boolean found = false;
        for (Population population : planet.populations()) {
            if (!raceId.equalsIgnoreCase(population.raceId())) {
                populations.add(population);
                continue;
            }
            found = true;
            groups.putAll(population.ageGroups());
            applyAgeDelta(groups, delta, sign);
            if (groups.values().stream().mapToLong(Long::longValue).sum() > 0) {
                populations.add(new Population(population.raceId(), groups));
            }
        }
        if (!found && sign > 0) {
            groups.putAll(delta);
            populations.add(new Population(raceId, groups));
        }
        return new Planet(planet.id(), planet.name(), planet.description(), planet.mass(), planet.gravity(),
                planet.distance(), planet.inclination(), planet.diameter(), planet.type(), planet.atmosphere(),
                planet.hasLiquidWater(), planet.waterLevel(), planet.resources(), planet.moons(), populations);
    }

    private void applyAgeDelta(Map<Integer, Long> groups, Map<Integer, Long> delta, int sign) {
        delta.forEach((age, count) -> {
            long next = groups.getOrDefault(age, 0L) + sign * count;
            if (next <= 0) groups.remove(age);
            else groups.put(age, next);
        });
    }

    private Map<Integer, Long> removePopulationAges(Planet planet, String raceId, long count) {
        Population population = planet.populations().stream()
                .filter(item -> raceId.equalsIgnoreCase(item.raceId())).findFirst().orElse(null);
        Map<Integer, Long> removed = new HashMap<>();
        if (population == null) return removed;
        long remaining = count;
        for (int age : population.ageGroups().keySet().stream().sorted(Comparator.comparingInt(a ->
                a >= 18 && a < 65 ? 0 : 1)).toList()) {
            long lost = Math.min(remaining, population.ageGroups().get(age));
            if (lost > 0) removed.put(age, lost);
            remaining -= lost;
            if (remaining == 0) break;
        }
        return removed;
    }

    private Map<Integer, Long> survivingAges(Map<Integer, Long> ageGroups, long count) {
        Map<Integer, Long> survivors = new HashMap<>();
        long remaining = count;
        for (int age : ageGroups.keySet().stream().sorted().toList()) {
            long kept = Math.min(remaining, ageGroups.get(age));
            if (kept > 0) survivors.put(age, kept);
            remaining -= kept;
            if (remaining == 0) break;
        }
        return survivors;
    }

    private GroundDefenses defenses(GameState state, String planetId) {
        int fortifications = 0;
        int bunkers = 0;
        int batteries = 0;
        for (IndustrialFacility facility : state.industrialFacilities()) {
            if (!planetId.equals(facility.planetId()) || facility.isUndergoingExpansion()
                    || facility.allocatedWorkers() <= 0) continue;
            String id = facility.applicationId().toLowerCase();
            int number = Math.max(1, facility.tier());
            if (id.contains("fortification")) fortifications += number;
            if (id.contains("bunker")) bunkers += number;
            if (id.contains("defense_battery") || id.contains("defence_battery")) batteries += number;
        }
        return new GroundDefenses(fortifications, bunkers, batteries);
    }

    private record GroundDefenses(int fortifications, int bunkers, int batteries) {}

    private Planet planet(GameState state, String systemId, String planetId) {
        return state.solarSystems().stream().filter(system -> systemId.equals(system.id())).findFirst()
                .flatMap(system -> system.planets().stream().filter(item -> planetId.equals(item.id())).findFirst())
                .orElse(null);
    }

    private Empire empire(GameState state, String empireId) {
        return state.empires().stream().filter(item -> empireId.equals(item.id())).findFirst().orElse(null);
    }

    private Empire ownerOfSystem(GameState state, String systemId) {
        return state.empires().stream().filter(item -> item.controlledSystemIds().contains(systemId))
                .findFirst().orElse(null);
    }

    private boolean atWar(String attackerId, String defenderId, List<DiplomaticRelation> relations) {
        return DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(
                diplomacyProcessor.getDiplomaticTier(attackerId, defenderId, relations));
    }

    private Race race(String raceId) {
        try {
            return DataModelLoader.loadRaces().stream()
                    .filter(item -> raceId.equalsIgnoreCase(item.id())).findFirst().orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }
}
