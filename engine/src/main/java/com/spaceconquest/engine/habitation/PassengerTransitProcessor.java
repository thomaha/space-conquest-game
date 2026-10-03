package com.spaceconquest.engine.habitation;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.PopulationProcessor;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.habitation.PassengerLogisticsResult;
import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEmployment;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Moves only explicitly booked people from a source population through a ship to another body. */
public final class PassengerTransitProcessor {
    private PassengerTransitProcessor() {}

    public static boolean canBoard(GameState state, String shipId, String raceId,
                                   int count, String destinationBodyId) {
        if (state == null || shipId == null || raceId == null || count <= 0
                || destinationBodyId == null || !bodyExists(state, destinationBodyId)
                || !hasDestinationCapacity(state, destinationBodyId, count)
                || state.passengerManifests().stream().anyMatch(item -> shipId.equals(item.shipId())))
            return false;
        for (Fleet fleet : state.fleets()) {
            ShipInstance ship = fleet.ships().stream().filter(item -> shipId.equals(item.id()))
                    .findFirst().orElse(null);
            if (ship == null || fleet.location().inTransit() || fleet.hasInterstellarOrder()
                    || !isHabitablePopulationSite(state, fleet.location().current())
                    || ship.passengerCount() != 0) continue;
            String source = fleet.location().current().entityId();
            Population population = population(state, source, raceId);
            return !source.equals(destinationBodyId) && population != null
                    && population.totalCount() >= count;
        }
        return false;
    }

    public static GameState board(GameState state, String shipId, String raceId,
                                  int count, String destinationBodyId, String transitMode) {
        if (!canBoard(state, shipId, raceId, count, destinationBodyId)) return state;
        Fleet fleet = state.fleets().stream().filter(item -> item.ships().stream()
                .anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElseThrow();
        String source = fleet.location().current().entityId();
        Map<Integer, Long> booked = selectAges(population(state, source, raceId), count);
        List<PassengerManifest> manifests = new ArrayList<>(state.passengerManifests());
        manifests.add(new PassengerManifest(shipId, source, destinationBodyId, raceId, booked));
        List<Fleet> fleets = state.fleets().stream().map(item -> !item.id().equals(fleet.id())
                ? item : item.withShips(item.ships().stream().map(ship ->
                !shipId.equals(ship.id()) ? ship : new ShipInstance(ship.id(), ship.designId(),
                        ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                        ship.currentFuelKg(), ship.storedCargoKg(), count, raceId, transitMode,
                        ship.powerState() == null ? null : ship.powerState().resetPassengerOutage()))
                .toList())).toList();
        GameState reduced = changePopulation(state, source, raceId, booked, -1);
        return reduced.toBuilder().fleets(fleets).passengerManifests(manifests).build();
    }

    public static boolean canBoardTroops(GameState state, String fleetId, String shipId,
                                         String raceId, int count, String destinationBodyId) {
        if (state == null || fleetId == null || shipId == null || raceId == null || count <= 0
                || destinationBodyId == null || !bodyExists(state, destinationBodyId)) return false;
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())).findFirst().orElse(null);
        if (fleet == null || fleet.location().inTransit() || fleet.hasInterstellarOrder()
                || fleet.location().current().kind() != FleetLocation.Kind.SURFACE) return false;
        ShipInstance ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElse(null);
        ShipDesign design = ship == null ? null : state.shipDesigns().stream()
                .filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
        if (ship == null || design == null || !ShipRole.TROOP_TRANSPORT.equalsIgnoreCase(design.role())
                || ship.passengerCount() != 0 || state.passengerManifests().stream()
                .anyMatch(manifest -> shipId.equals(manifest.shipId()))) return false;
        if (ShipInstance.MODE_CRYOGENIC_STASIS.equalsIgnoreCase(ship.transitMode())
                && !PassengerStasis.availableFor(state, ship, count)) return false;
        String originBodyId = fleet.location().current().entityId();
        if (originBodyId.equals(destinationBodyId)) return false;
        HouseholdAccount soldiers = availableSoldierAccount(state, originBodyId, fleet.ownerEntityId(), raceId);
        if (soldiers == null || soldiers.employment().publicWorkers() < count
                || soldiers.headcount() < count || soldiers.employment().workingAge() < count
                || adultResidents(population(state, originBodyId, raceId)) < count) return false;
        double cargoKg = ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        if (cargoKg + count * 80.0 > design.maxCargoMassKg()) return false;
        String sourceSystemId = systemOfBody(state, originBodyId);
        String destinationSystemId = systemOfBody(state, destinationBodyId);
        String destinationOwner = ownerOfSystem(state, destinationSystemId);
        return sourceSystemId != null && sourceSystemId.equals(fleet.currentSystemId())
                && destinationSystemId != null && destinationOwner != null
                && !destinationOwner.equals(fleet.ownerEntityId())
                && atWar(fleet.ownerEntityId(), destinationOwner, state.diplomaticRelations());
    }

    public static GameState boardTroops(GameState state, String fleetId, String shipId,
                                        String raceId, int count, String destinationBodyId) {
        if (!canBoardTroops(state, fleetId, shipId, raceId, count, destinationBodyId)) return state;
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())).findFirst().orElseThrow();
        String sourceBodyId = fleet.location().current().entityId();
        HouseholdAccount account = availableSoldierAccount(state, sourceBodyId, fleet.ownerEntityId(), raceId);
        Map<Integer, Long> ages = selectAges(population(state, sourceBodyId, raceId), count);
        List<PassengerManifest> manifests = new ArrayList<>(state.passengerManifests());
        manifests.add(new PassengerManifest(shipId, sourceBodyId, destinationBodyId, raceId, ages, true));
        List<Fleet> fleets = state.fleets().stream().map(item -> !fleetId.equals(item.id()) ? item
                : item.withShips(item.ships().stream().map(ship -> !shipId.equals(ship.id()) ? ship
                : new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                ship.currentShieldHealth(), ship.currentFuelKg(), ship.storedCargoKg(), count, raceId,
                ship.transitMode(), ship.powerState())).toList())).toList();
        List<HouseholdAccount> accounts = state.householdAccounts().stream().map(current -> {
            if (!current.key().equals(account.key())) return current;
            HouseholdEmployment employment = current.employment();
            return new HouseholdAccount(current.bodyId(), current.systemId(), current.empireId(),
                    current.raceId(), current.professionId(), current.headcount() - count,
                    current.savingsCredits(), current.wageIncomeCredits(), current.welfareIncomeCredits(),
                    current.incomeTaxPaidCredits(), current.marketSpendingCredits(), current.unmetBasicKg(),
                    current.secondaryNeedsMetFraction(), current.luxuryNeedsMetFraction(),
                    current.electricitySpendingCredits(), current.unmetBasicElectricityKwh(),
                    current.wellbeing(), new HouseholdEmployment(employment.workingAge() - count,
                    employment.publicWorkers() - count, employment.industryWorkers(),
                    employment.unemploymentPressure()));
        }).toList();
        GameState reduced = changePopulation(state, sourceBodyId, raceId, ages, -1);
        return reduced.toBuilder().fleets(fleets).passengerManifests(manifests)
                .householdAccounts(accounts).build();
    }

    public static GameState disembarkArrivals(GameState state) {
        GameState current = state;
        List<PassengerManifest> remaining = new ArrayList<>();
        for (PassengerManifest manifest : state.passengerManifests()) {
            Fleet arrived = current.fleets().stream().filter(fleet -> fleet.ships().stream()
                    .anyMatch(ship -> manifest.shipId().equals(ship.id())))
                    .findFirst().orElse(null);
            if (arrived == null) continue;
            if (manifest.combatDeployment()) {
                remaining.add(manifest);
                continue;
            }
            if (!arrivedAtDestination(current, arrived, manifest.destinationBodyId())) {
                remaining.add(manifest);
                continue;
            }
            current = changePopulation(current, manifest.destinationBodyId(),
                    manifest.raceId(), manifest.ageGroups(), 1);
            List<Fleet> fleets = current.fleets().stream().map(fleet ->
                    !fleet.id().equals(arrived.id()) ? fleet : fleet.withShips(
                            fleet.ships().stream().map(ship ->
                                    !ship.id().equals(manifest.shipId()) ? ship
                                            : new ShipInstance(ship.id(), ship.designId(),
                                            ship.ownerEntityId(), ship.currentHullHealth(),
                                            ship.currentShieldHealth(), ship.currentFuelKg(),
                                            ship.storedCargoKg(), 0, "", ship.transitMode(), ship.powerState()))
                                    .toList())).toList();
            current = current.withFleets(fleets);
        }
        return current.toBuilder().passengerManifests(remaining).build();
    }

    /** Consumes one day of carried supplies before passengers can disembark. */
    public static GameState advanceDay(GameState state, List<Race> races) {
        PopulationProcessor needs = new PopulationProcessor();
        Map<String, PassengerManifest> updatedManifests = new HashMap<>();
        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet fleet : state.fleets()) {
            List<ShipInstance> updatedShips = new ArrayList<>();
            for (ShipInstance ship : fleet.ships()) {
                PassengerManifest manifest = state.passengerManifests().stream()
                        .filter(item -> ship.id().equals(item.shipId())).findFirst().orElse(null);
                if (manifest == null || manifest.headcount() == 0) {
                    updatedShips.add(ship);
                    continue;
                }
                Race race = races.stream().filter(item -> item.id().equals(manifest.raceId()))
                        .findFirst().orElse(null);
                if (race == null) {
                    updatedShips.add(ship);
                    updatedManifests.put(manifest.shipId(), manifest);
                    continue;
                }
                PassengerLogisticsResult result = needs.processPassengerLifeSupport(ship, race,
                        ship.storedCargoKg());
                Map<String, Double> cargo = new HashMap<>(ship.storedCargoKg());
                result.consumedSupplies().forEach((id, amount) ->
                        cargo.computeIfPresent(id, (key, available) ->
                                Math.max(0.0, available - amount)));
                Map<Integer, Long> survivors = new HashMap<>(manifest.ageGroups());
                ShipDesign electricalDesign = state.shipDesigns().stream().filter(item -> ship.designId().equals(item.id()))
                        .findFirst().orElse(null);
                int casualties = Math.min(ship.passengerCount(), result.casualtyCount()
                        + com.spaceconquest.engine.ship.ShipPowerSurvival.casualties(ship, electricalDesign));
                for (int age : manifest.ageGroups().keySet().stream().sorted().toList()) {
                    long lost = Math.min(casualties, survivors.getOrDefault(age, 0L));
                    survivors.computeIfPresent(age, (key, count) -> count - lost);
                    casualties -= (int) lost;
                    if (casualties == 0) break;
                }
                survivors.entrySet().removeIf(entry -> entry.getValue() <= 0);
                PassengerManifest remaining = new PassengerManifest(manifest.shipId(),
                        manifest.sourceBodyId(), manifest.destinationBodyId(), manifest.raceId(), survivors,
                        manifest.combatDeployment());
                if (remaining.headcount() > 0) updatedManifests.put(ship.id(), remaining);
                updatedShips.add(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                        ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(),
                        Map.copyOf(cargo), (int) remaining.headcount(),
                        remaining.headcount() > 0 ? ship.passengerRaceId() : "", ship.transitMode(), ship.powerState()));
            }
            updatedFleets.add(fleet.withShips(updatedShips));
        }
        GameState supplied = state.toBuilder().fleets(updatedFleets)
                .passengerManifests(List.copyOf(updatedManifests.values())).build();
        return disembarkArrivals(supplied);
    }

    /** Checks an interstellar booking against carried supplies through departure and arrival. */
    public static boolean canSustainJourney(GameState state, Fleet fleet, List<Race> races,
                                            double crossingDays) {
        double days = Math.ceil(crossingDays) + 4.0;
        PopulationProcessor needs = new PopulationProcessor();
        for (PassengerManifest manifest : state.passengerManifests()) {
            ShipInstance ship = fleet.ships().stream()
                    .filter(item -> item.id().equals(manifest.shipId()))
                    .findFirst().orElse(null);
            if (ship == null) continue;
            if (ShipInstance.MODE_CRYOGENIC_STASIS.equals(ship.transitMode())) {
                if (!PassengerStasis.availableFor(state, ship, (int) manifest.headcount()))
                    return false;
                continue;
            }
            Race race = races.stream().filter(item -> item.id().equals(manifest.raceId()))
                    .findFirst().orElse(null);
            if (race == null) return false;
            for (var need : needs.calculateDailyNutrientRequirements(manifest.headcount(), race)
                    .entrySet()) {
                if (ship.storedCargoKg().getOrDefault(need.getKey(), 0.0) + 0.000001
                        < need.getValue() * days) return false;
            }
        }
        return true;
    }

    private static Map<Integer, Long> selectAges(Population population, int count) {
        Map<Integer, Long> booked = new LinkedHashMap<>();
        long remaining = count;
        List<Integer> ages = population.ageGroups().keySet().stream()
                .sorted((left, right) -> Integer.compare(
                        left >= 18 && left < 65 ? 0 : 1,
                        right >= 18 && right < 65 ? 0 : 1)).toList();
        for (int age : ages) {
            long selected = Math.min(remaining, population.ageGroups().get(age));
            if (selected > 0) booked.put(age, selected);
            remaining -= selected;
            if (remaining == 0) break;
        }
        return Map.copyOf(booked);
    }

    private static boolean bodyExists(GameState state, String bodyId) {
        return state.orbitalStations().stream().anyMatch(station -> station.id().equals(bodyId))
                || state.solarSystems().stream().flatMap(system -> system.planets().stream())
                .anyMatch(planet -> bodyId.equals(planet.id()) || planet.moons().stream()
                        .anyMatch(moon -> bodyId.equals(moon.id())));
    }

    private static boolean hasDestinationCapacity(GameState state, String destination, int count) {
        OrbitalStation station = state.orbitalStations().stream()
                .filter(item -> destination.equals(item.id())).findFirst().orElse(null);
        if (station == null) return true;
        if (!station.hasModuleType(StationModule.TYPE_HABITATION)) return false;
        long capacity = station.habitationCapacity();
        long residents = station.populations().stream().mapToLong(Population::totalCount).sum();
        long booked = state.passengerManifests().stream()
                .filter(manifest -> destination.equals(manifest.destinationBodyId()))
                .mapToLong(PassengerManifest::headcount).sum();
        return capacity >= residents + booked + count;
    }

    private static boolean isHabitablePopulationSite(GameState state, FleetLocation.Site site) {
        if (site.kind() == FleetLocation.Kind.SURFACE) return bodyExists(state, site.entityId());
        if (site.kind() != FleetLocation.Kind.DOCKED) return false;
        return state.orbitalStations().stream().anyMatch(station ->
                station.id().equals(site.entityId()) && station.modules().stream()
                        .anyMatch(module -> module.isOnline()
                                && StationModule.TYPE_HABITATION.equalsIgnoreCase(module.type())));
    }

    private static boolean arrivedAtDestination(GameState state, Fleet fleet, String destination) {
        if (state.orbitalStations().stream().anyMatch(station -> station.id().equals(destination)))
            return fleet.location().isAt(FleetLocation.Site.docked(destination));
        return fleet.location().isAt(FleetLocation.Site.surface(destination));
    }

    private static Population population(GameState state, String bodyId, String raceId) {
        Population orbitalPopulation = state.orbitalStations().stream()
                .filter(station -> bodyId.equals(station.id()))
                .flatMap(station -> station.populations().stream())
                .filter(group -> raceId.equals(group.raceId())).findFirst().orElse(null);
        if (orbitalPopulation != null) return orbitalPopulation;
        return state.solarSystems().stream().flatMap(system -> system.planets().stream())
                .flatMap(planet -> {
                    List<Population> groups = new ArrayList<>();
                    if (bodyId.equals(planet.id())) groups.addAll(planet.populations());
                    planet.moons().stream().filter(moon -> bodyId.equals(moon.id()))
                            .forEach(moon -> groups.addAll(moon.populations()));
                    return groups.stream();
                }).filter(group -> raceId.equals(group.raceId())).findFirst().orElse(null);
    }

    private static GameState changePopulation(GameState state, String bodyId, String raceId,
                                              Map<Integer, Long> ages, int sign) {
        List<SolarSystem> systems = state.solarSystems().stream().map(system ->
                new SolarSystem(system.id(), system.name(), system.description(), system.x(),
                        system.y(), system.z(), system.sunMass(), system.sunDiameter(),
                        system.sunColor(), system.planets().stream().map(planet ->
                        changePlanet(planet, bodyId, raceId, ages, sign)).toList(),
                        system.asteroidBelts())).toList();
        List<OrbitalStation> stations = state.orbitalStations().stream().map(station ->
                bodyId.equals(station.id()) ? station.withPopulations(
                        changeGroups(station.populations(), raceId, ages, sign)) : station).toList();
        return state.withSolarSystems(systems).withOrbitalStations(stations);
    }

    private static Planet changePlanet(Planet planet, String bodyId, String raceId,
                                       Map<Integer, Long> ages, int sign) {
        List<Population> people = bodyId.equals(planet.id())
                ? changeGroups(planet.populations(), raceId, ages, sign) : planet.populations();
        List<Moon> moons = planet.moons().stream().map(moon -> !bodyId.equals(moon.id())
                ? moon : new Moon(moon.id(), moon.name(), moon.description(), moon.mass(),
                moon.gravity(), moon.distance(), moon.diameter(), moon.atmosphere(),
                moon.hasLiquidWater(), moon.waterLevel(), moon.resources(),
                changeGroups(moon.populations(), raceId, ages, sign))).toList();
        return new Planet(planet.id(), planet.name(), planet.description(), planet.mass(),
                planet.gravity(), planet.distance(), planet.inclination(), planet.diameter(),
                planet.type(), planet.atmosphere(), planet.hasLiquidWater(),
                planet.waterLevel(), planet.resources(), moons, people);
    }

    private static List<Population> changeGroups(List<Population> previous, String raceId,
                                                 Map<Integer, Long> ages, int sign) {
        List<Population> groups = new ArrayList<>();
        boolean found = false;
        for (Population group : previous) {
            if (!raceId.equals(group.raceId())) { groups.add(group); continue; }
            Map<Integer, Long> updated = new HashMap<>(group.ageGroups());
            ages.forEach((age, count) -> updated.merge(age, sign * count, Long::sum));
            updated.entrySet().removeIf(entry -> entry.getValue() <= 0);
            groups.add(new Population(raceId, Map.copyOf(updated)));
            found = true;
        }
        if (!found && sign > 0) groups.add(new Population(raceId, ages));
        return List.copyOf(groups);
    }

    private static HouseholdAccount availableSoldierAccount(GameState state, String bodyId,
                                                             String empireId, String raceId) {
        return state.householdAccounts().stream().filter(account -> bodyId.equals(account.bodyId())
                && empireId.equals(account.empireId()) && raceId.equals(account.raceId())
                && "soldier".equalsIgnoreCase(account.professionId())
                && account.employment().publicWorkers() > 0).findFirst().orElse(null);
    }

    private static long adultResidents(Population population) {
        if (population == null) return 0;
        return population.ageGroups().entrySet().stream().filter(entry -> entry.getKey() >= 18
                && entry.getKey() < 65).mapToLong(Map.Entry::getValue).sum();
    }

    private static String systemOfBody(GameState state, String bodyId) {
        String stationSystem = state.orbitalStations().stream()
                .filter(station -> bodyId.equals(station.id()))
                .map(OrbitalStation::systemId).findFirst().orElse(null);
        if (stationSystem != null) return stationSystem;
        return state.solarSystems().stream().filter(system -> system.planets().stream()
                .anyMatch(planet -> bodyId.equals(planet.id()) || planet.moons().stream()
                        .anyMatch(moon -> bodyId.equals(moon.id()))))
                .map(SolarSystem::id).findFirst().orElse(null);
    }

    private static String ownerOfSystem(GameState state, String systemId) {
        if (systemId == null) return null;
        return state.empires().stream().filter(empire -> empire.controlledSystemIds().contains(systemId))
                .map(com.spaceconquest.engine.Empire::id).findFirst().orElse(null);
    }

    private static boolean atWar(String first, String second, List<DiplomaticRelation> relations) {
        return first != null && second != null && DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(
                new DiplomacyProcessor().getDiplomaticTier(first, second, relations));
    }
}
