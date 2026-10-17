package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Ship-borne automated mapping uses installed sensors, real travel and stored electrical energy. */
public final class OrbitalSurveyOperations {
    private OrbitalSurveyOperations() {}
    public static SurveyCapability.Definition definition(GameState state, String owner) {
        var empire = state.empires().stream().filter(e -> e.id().equals(owner)).findFirst().orElse(null);
        return empire == null ? null : SurveyCapability.all().stream().filter(d -> d.capability().mode() == SurveyCapability.Mode.ORBITAL
                && empire.unlockedTechIds().contains(d.applicationId()) && empire.unlockedTechIds().contains(d.technologyId())).findFirst().orElse(null);
    }
    public static boolean eligible(GameState state, Fleet fleet, String owner) {
        if (fleet == null || !owner.equals(fleet.ownerEntityId()) || fleet.ships().size() != 1
                || fleet.guardOrder() != null || fleet.escortOrder() != null || fleet.interceptOrder() != null
                || !"PASSIVE".equalsIgnoreCase(fleet.fleetStance()) || ShipRefitPlan.reserved(state, fleet)) return false;
        var ship = fleet.ships().getFirst();
        var design = state.shipDesigns().stream().filter(d -> d.id().equals(ship.designId())).findFirst().orElse(null);
        return design != null && ShipRole.EXPLORER.equals(design.role()) && owner.equals(ship.ownerEntityId())
                && ship.currentHullHealth() > 0 && design.powerProfile() != null
                && ship.passengerCount() == 0 && ship.supplyState().orders().isEmpty()
                && !state.shipyardLedger().deployments().containsKey(ship.id())
                && state.tradeRoutes().stream().noneMatch(r -> r.carrierCommitted() && r.assignedFreighterIds().contains(ship.id()))
                && !ShipMiningProcessor.committed(state, ship.id()) && SensorCoverage.rangeKm(state, fleet) > 0;
    }
    public static List<String> targets(GameState state, String owner) {
        var empire = state.empires().stream().filter(e -> e.id().equals(owner)).findFirst().orElse(null);
        if (empire == null) return List.of();
        var fog = state.fogOfWarStates().stream().filter(f -> f.empireId().equals(owner)).findFirst().orElse(null);
        var result = new ArrayList<String>();
        for (var system : state.solarSystems()) {
            boolean controlled = empire.controlledSystemIds().contains(system.id());
            if (!controlled && (fog == null || !fog.exploredSystemIds().contains(system.id()))) continue;
            if (state.empires().stream().anyMatch(e -> !owner.equals(e.id()) && e.controlledSystemIds().contains(system.id()))) continue;
            if (fog != null && state.pirateBases().stream().anyMatch(p -> p.systemId().equals(system.id())
                    && fog.discoveredPirateBaseIds().contains(p.id()))) continue;
            if (fog != null && state.fleets().stream().anyMatch(f -> f.currentSystemId().equals(system.id())
                    && !f.ownerEntityId().equals(owner) && fog.detectedFleetIds().contains(f.id()))) continue;
            for (var planet : system.planets()) if (controlled || fog.scannedPlanetIds().contains(planet.id())) {
                result.add(planet.id()); planet.moons().forEach(m -> result.add(m.id()));
            }
        }
        return result.stream().sorted().toList();
    }
    public static GameState establish(GameState state, String owner, String fleetId) {
        if (state == null || fleetId == null || owner == null) return state;
        var fleet = state.fleets().stream().filter(f -> f.id().equals(fleetId)).findFirst().orElse(null);
        var definition = definition(state, owner);
        if (definition == null || !eligible(state, fleet, owner) || fleet.location().inTransit() || fleet.hasInterstellarOrder()
                || state.surveyTeams().stream().anyMatch(t -> fleetId.equals(t.fleetId()))) return state;
        var kind = fleet.location().current().kind();
        if (kind != FleetLocation.Kind.DOCKED && kind != FleetLocation.Kind.SURFACE) return state;
        var target = targets(state, owner).stream().sorted(Comparator.comparing((String body) ->
                !fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state, body))).thenComparing(body -> body)).findFirst().orElse(null);
        if (target == null || com.spaceconquest.engine.economy.EconomicOwnership.cash(state, owner) < SurveyOperations.TREASURY_RESERVE) return state;
        var cap = definition.capability();
        var purchase = ConstructionMaterials.buyUpTo(state, fleet.location().current().entityId(), owner, cap.equipmentMaterialsKg());
        if (!purchase.covers(cap.equipmentMaterialsKg())
                || com.spaceconquest.engine.economy.EconomicOwnership.cash(purchase.state(), owner) < SurveyOperations.TREASURY_RESERVE) return state;
        var teams = new ArrayList<>(state.surveyTeams());
        teams.add(new SurveyTeam("orbital-survey:" + fleetId, owner, target, definition.applicationId(), cap,
                cap.installationWork(), 0, 0, state.turn(), 0, "Travel to survey orbit", fleetId, Map.of()));
        return purchase.state().toBuilder().surveyTeams(teams).build();
    }
    public static GameState retarget(GameState state, String owner, String fleetId, String target) {
        var fleet = state.fleets().stream().filter(f -> f.id().equals(fleetId)).findFirst().orElse(null);
        if (!eligible(state, fleet, owner) || fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || !targets(state, owner).contains(target)) return state;
        var team = state.surveyTeams().stream().filter(t -> t.empireId().equals(owner) && t.fleetId().equals(fleetId)).findFirst().orElse(null);
        if (team == null || team.bodyId().equals(target) || team.progressWork() > 0) return state;
        var updated = new SurveyTeam(team.id(), owner, target, team.applicationId(), team.capability(),
                team.installationRemaining(), 0, team.completedPasses(), team.lastWorkedTurn(), team.totalWork(), "Travel to survey orbit", fleetId, team.coveragePasses());
        return state.toBuilder().surveyTeams(state.surveyTeams().stream().map(t -> t.id().equals(team.id()) ? updated : t).toList()).build();
    }
    public static GameState advance(GameState state) {
        var current = state; var teams = new ArrayList<SurveyTeam>();
        for (var team : state.surveyTeams()) {
            if (team.fleetId().isBlank() || team.lastWorkedTurn() >= state.turn()) { teams.add(team); continue; }
            var fleet = current.fleets().stream().filter(f -> f.id().equals(team.fleetId())).findFirst().orElse(null);
            String blocker = eligible(current, fleet, team.empireId()) ? "" : "Survey ship or healthy sensors unavailable";
            if (blocker.isBlank() && !targets(current, team.empireId()).contains(team.bodyId())) blocker = "Survey site unknown or unsafe";
            if (blocker.isBlank() && (!atOrbit(fleet, team.bodyId())
                    || !fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(current, team.bodyId())))) blocker = "Travel to survey orbit";
            double work = 0;
            if (blocker.isBlank()) {
                var ship = fleet.ships().getFirst(); var power = ShipPowerProcessor.reserves(ship);
                var design = FleetSupplySimulation.design(current, ship); var profile = design.powerProfile(ship);
                double available = Math.max(0, power.batteryChargeKwh() - profile.hotelKw() * ShipPowerProcessor.ARRIVAL_RESERVE_HOURS);
                double ratedStrength = SensorModuleCatalog.profile(design).scanStrength();
                double condition = ratedStrength <= 0 ? 0 : SensorModuleCatalog.profile(design, ship).scanStrength() / ratedStrength;
                double rate = team.capability().equipment().ratedWorkPerDay() * Math.clamp(condition, 0, 1);
                work = Math.min(rate, Math.min(available, profile.dischargeKw() * 24) / team.capability().energyKwhPerWorkUnit());
                work = Math.min(work, team.installationRemaining() + team.capability().workPerPass() - team.progressWork());
                if (work <= 0) blocker = "Survey electrical reserve unavailable";
                else {
                    var spent = new ShipPowerState(power.generatorMaterialsKg(), power.chemicalMixture(), power.reactorFuel(),
                            Math.max(0, power.batteryChargeKwh() - work * team.capability().energyKwhPerWorkUnit()), power.arraysDeployed(),
                            power.arrayCondition(), power.orientationFraction(), power.unmetEssentialHours(), power.unmetDriveKwh(),
                            power.unmetCargoKwh(), power.lastUnmetEssentialKwh(), power.chargedInputKwhToday(),
                            power.cargoPreservation(), power.rescueStatus(), power.shedServices());
                    var updated = fleet.withShips(List.of(ship.withPowerState(spent)));
                    current = current.withFleets(current.fleets().stream().map(f -> f.id().equals(updated.id()) ? updated : f).toList());
                }
            }
            double setup = Math.max(0, team.installationRemaining() - work);
            double progress = team.progressWork() + Math.max(0, work - team.installationRemaining());
            long passes = team.completedPasses(); var coverage = new TreeMap<>(team.coveragePasses());
            if (progress + 1e-8 >= team.capability().workPerPass()) {
                progress = 0; passes++; long atBody = coverage.merge(team.bodyId(), 1L, Long::sum);
                var random = new Random(31L * (team.id() + team.bodyId()).hashCode() + atBody * 104729L);
                var deposits = new ProspectingProcessor(random).executeProspectingSurvey(team.bodyId(), team.empireId(),
                        team.capability().discoveryProbability(), 1, current.geologicalDeposits());
                current = current.toBuilder().geologicalDeposits(deposits).build();
            }
            teams.add(new SurveyTeam(team.id(), team.empireId(), team.bodyId(), team.applicationId(), team.capability(),
                    setup, progress, passes, state.turn(), team.totalWork() + work, blocker, team.fleetId(), coverage));
        }
        return current.toBuilder().surveyTeams(teams).build();
    }
    public static boolean atOrbit(Fleet fleet, String body) {
        return fleet != null && !fleet.hasInterstellarOrder() && !fleet.location().inTransit()
                && fleet.location().current().kind() == FleetLocation.Kind.ORBIT
                && fleet.location().current().entityId().equals(body);
    }
}
