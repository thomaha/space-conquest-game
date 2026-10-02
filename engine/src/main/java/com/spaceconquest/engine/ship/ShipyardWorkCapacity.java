package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calculates shipyard work capacity from the yard, installed modules, technology and local labor. */
public final class ShipyardWorkCapacity {
    public static final String SURFACE_SHIPYARD_APPLICATION_ID = "surface_shipyard";
    public static final String TECH_AUTOMATED_ASSEMBLY = "automated_assembly_lines";
    public static final String TECH_CYBERNETIC_WORKFORCE = "cybernetic_workforce_integration";

    public record Staffing(String professionId, int requiredWorkers, long filledWorkers,
                           double filledFraction) {}

    public record Profile(String yardType, double baseWorkPerDay,
                          double technologyMultiplier, double workerRequirementMultiplier,
                          double staffingFraction,
                          double workPerDay, boolean operational,
                          Map<String, Staffing> staffingByProfession,
                          Map<String, Integer> installedYardModules) {
        public Profile {
            staffingByProfession = staffingByProfession == null
                    ? Map.of() : Map.copyOf(staffingByProfession);
            installedYardModules = installedYardModules == null
                    ? Map.of() : Map.copyOf(installedYardModules);
        }

        public boolean isFullyStaffed() {
            return staffingFraction >= 0.999;
        }
    }

    private record YardBase(String type, double workPerDay, boolean operational,
                            Map<String, Integer> requiredStaff,
                            Map<String, Long> staffedWorkers,
                            Map<String, Integer> installedModules,
                            boolean staffingAlreadyTechnologyAdjusted) {}

    private ShipyardWorkCapacity() {}

    /** Returns workers of the facility's profession not already committed to other local jobs. */
    public static long assignableWorkers(GameState state, IndustrialFacility target) {
        if (state == null || target == null) return 0L;
        long localWorkers = state.householdAccounts().stream()
                .filter(account -> target.planetId().equals(account.bodyId())
                        && profession(target).equalsIgnoreCase(account.professionId())
                        && empireForOwner(state, target.ownerEntityId()).equals(account.empireId()))
                .mapToLong(account -> Math.max(0L, account.employment().workingAge()
                        - account.employment().publicWorkers())).sum();
        long otherJobs = state.industrialFacilities().stream()
                .filter(facility -> !target.id().equals(facility.id())
                        && target.planetId().equals(facility.planetId())
                        && profession(target).equalsIgnoreCase(profession(facility))
                        && (facility.tier() > 0 || SURFACE_SHIPYARD_APPLICATION_ID
                        .equals(facility.applicationId()))
                        && empireForOwner(state, facility.ownerEntityId())
                        .equals(empireForOwner(state, target.ownerEntityId())))
                .mapToLong(facility -> Math.max(0, facility.allocatedWorkers())).sum();
        return Math.max(0L, localWorkers - otherJobs);
    }

    private static String profession(IndustrialFacility facility) {
        return facility.workerProfessionId() == null || facility.workerProfessionId().isBlank()
                ? "industrial_worker" : facility.workerProfessionId();
    }

    public static Profile forYard(GameState state, String ownerEntityId, String systemId,
                                  String yardEntityId) {
        return forYard(state, ownerEntityId, systemId, yardEntityId, null);
    }

    public static Profile forYard(GameState state, String ownerEntityId, String systemId,
                                  String yardEntityId, Map<String, Integer> paidWorkersByFacility) {
        if (state == null || ownerEntityId == null || systemId == null || yardEntityId == null)
            return emptyProfile();
        YardBase yard = state.orbitalStations().stream()
                .filter(station -> yardEntityId.equals(station.id())
                        && systemId.equals(station.systemId())
                        && ownerEntityId.equals(station.ownerEntityId()))
                .findFirst().map(station -> orbitalYard(state, station, paidWorkersByFacility))
                .orElseGet(() -> surfaceYard(state, yardEntityId, systemId, ownerEntityId,
                        paidWorkersByFacility));
        if (yard == null) return emptyProfile();

        Empire empire = empireRecordForOwner(state, ownerEntityId);
        var technologies = empire == null ? java.util.List.<String>of() : empire.unlockedTechIds();
        boolean automatedAssembly = technologies.contains(TECH_AUTOMATED_ASSEMBLY);
        double technologyMultiplier = automatedAssembly ? 1.25 : 1.0;
        double workerRequirementMultiplier = workerRequirementMultiplier(technologies);

        Map<String, Staffing> staffing = new LinkedHashMap<>();
        double staffingFraction = 1.0;
        for (Map.Entry<String, Integer> requirement : yard.requiredStaff().entrySet()) {
            int adjustedRequired = yard.staffingAlreadyTechnologyAdjusted()
                    ? requirement.getValue()
                    : (int) Math.ceil(requirement.getValue() * workerRequirementMultiplier);
            long available = yard.staffedWorkers().getOrDefault(requirement.getKey(), 0L);
            double filled = adjustedRequired <= 0 ? 1.0
                    : Math.clamp((double) available / adjustedRequired, 0.0, 1.0);
            staffing.put(requirement.getKey(), new Staffing(requirement.getKey(),
                    adjustedRequired, available, filled));
            staffingFraction = Math.min(staffingFraction, filled);
        }
        if (yard.requiredStaff().isEmpty()) staffingFraction = 1.0;
        boolean operational = yard.operational() && yard.workPerDay() > 0.0;
        double dailyWork = operational
                ? yard.workPerDay() * technologyMultiplier * staffingFraction : 0.0;
        return new Profile(yard.type(), yard.workPerDay(), technologyMultiplier,
                workerRequirementMultiplier, staffingFraction, dailyWork, operational,
                staffing, yard.installedModules());
    }

    private static YardBase orbitalYard(GameState state, OrbitalStation station,
                                        Map<String, Integer> paidWorkersByFacility) {
        Map<String, Integer> staffing = new LinkedHashMap<>();
        Map<String, Long> paidStaff = new LinkedHashMap<>();
        Map<String, Integer> modules = new LinkedHashMap<>();
        double capacity = 0.0;
        int gridCount = 0;
        int slipwayCount = 0;
        for (StationModule module : station.modules()) {
            if (!module.isOnline()) continue;
            double moduleCapacity;
            if (StationModule.TYPE_SHIPYARD_GRID.equalsIgnoreCase(module.type())) {
                moduleCapacity = 100.0;
                gridCount++;
            } else if (StationModule.TYPE_CAPITAL_SLIPWAY.equalsIgnoreCase(module.type())) {
                moduleCapacity = 300.0;
                slipwayCount++;
            } else if (StationModule.TYPE_COMPONENT_ASSEMBLY.equalsIgnoreCase(module.type())) {
                moduleCapacity = 50.0;
            } else {
                continue;
            }
            capacity += moduleCapacity;
            modules.merge(module.type(), 1, Integer::sum);
            String profession = moduleProfession(module);
            int workers = requiredWorkers(state, station.ownerEntityId(), module);
            staffing.merge(profession, workers, Integer::sum);
            long paid = paidWorkersByFacility == null
                    ? module.paidWorkers()
                    : Math.max(0, paidWorkersByFacility.getOrDefault(module.id(), 0));
            paidStaff.merge(profession, paid, Long::sum);
        }
        String type = slipwayCount > 0 ? "Capital slipway"
                : gridCount > 0 ? "Orbital shipyard grid" : "No active shipyard module";
        return new YardBase(type, capacity, station.isOperational(), staffing, paidStaff, modules, true);
    }

    private static YardBase surfaceYard(GameState state, String bodyId, String systemId,
                                        String ownerEntityId,
                                        Map<String, Integer> paidWorkersByFacility) {
        IndustrialFacility facility = state.industrialFacilities().stream()
                .filter(item -> SURFACE_SHIPYARD_APPLICATION_ID.equals(item.applicationId())
                        && bodyId.equals(item.planetId())
                        && ownerEntityId.equals(item.ownerEntityId()))
                .findFirst().orElse(null);
        String empireId = state.empires().stream().filter(empire -> ownerEntityId.equals(empire.id()))
                .map(Empire::id).findFirst().orElseGet(() -> state.corporations().stream()
                        .filter(corporation -> ownerEntityId.equals(corporation.id()))
                        .map(Corporation::empireId).findFirst().orElse(null));
        boolean controlsSystem = state.empires().stream().anyMatch(empire ->
                empire.id().equals(empireId) && empire.controlledSystemIds().contains(systemId));
        if (facility == null || !controlsSystem
                || !systemId.equals(ConstructionMaterials.systemForBody(state, bodyId))) return null;
        String profession = facility.workerProfessionId() == null
                || facility.workerProfessionId().isBlank()
                ? "industrial_worker" : facility.workerProfessionId();
        long paid = paidWorkers(state, bodyId, ownerEntityId, paidWorkersByFacility);
        return new YardBase("Surface shipyard",
                facility.tier() > 0 ? 100.0 * facility.getEffectiveThroughputMultiplier() : 0.0,
                facility.tier() > 0, Map.of(profession, 100), Map.of(profession, paid),
                Map.of(SURFACE_SHIPYARD_APPLICATION_ID, 1), false);
    }

    public static boolean isYardWorkforceModule(StationModule module) {
        return module != null && (StationModule.TYPE_SHIPYARD_GRID.equalsIgnoreCase(module.type())
                || StationModule.TYPE_CAPITAL_SLIPWAY.equalsIgnoreCase(module.type())
                || StationModule.TYPE_COMPONENT_ASSEMBLY.equalsIgnoreCase(module.type()));
    }

    public static int requiredWorkers(StationModule module) {
        if (module == null) return 0;
        if (module.requiredWorkers() > 0) return module.requiredWorkers();
        if (StationModule.TYPE_SHIPYARD_GRID.equalsIgnoreCase(module.type())) return 100;
        if (StationModule.TYPE_CAPITAL_SLIPWAY.equalsIgnoreCase(module.type())) return 300;
        if (StationModule.TYPE_COMPONENT_ASSEMBLY.equalsIgnoreCase(module.type())) return 50;
        return 0;
    }

    /** Applies the owner's researched workforce reductions to a module's payroll job count. */
    public static int requiredWorkers(GameState state, String ownerEntityId, StationModule module) {
        return (int) Math.ceil(requiredWorkers(module)
                * workerRequirementMultiplier(state, ownerEntityId));
    }

    public static double workerRequirementMultiplier(GameState state, String ownerEntityId) {
        Empire empire = state == null ? null : empireRecordForOwner(state, ownerEntityId);
        List<String> technologies = empire == null ? java.util.List.of() : empire.unlockedTechIds();
        return workerRequirementMultiplier(technologies);
    }

    private static double workerRequirementMultiplier(List<String> technologies) {
        return (technologies.contains(TECH_AUTOMATED_ASSEMBLY) ? 0.75 : 1.0)
                * (technologies.contains(TECH_CYBERNETIC_WORKFORCE) ? 0.75 : 1.0);
    }

    public static String moduleProfession(StationModule module) {
        return module == null || module.workforceProfessionId() == null
                || module.workforceProfessionId().isBlank()
                ? "industrial_worker" : module.workforceProfessionId();
    }

    private static long paidWorkers(GameState state, String bodyId, String ownerEntityId,
                                    Map<String, Integer> paidWorkersByFacility) {
        IndustrialFacility facility = state.industrialFacilities().stream()
                .filter(item -> SURFACE_SHIPYARD_APPLICATION_ID.equals(item.applicationId())
                        && bodyId.equals(item.planetId())
                        && ownerEntityId.equals(item.ownerEntityId()))
                .findFirst().orElse(null);
        if (facility == null) return 0L;
        if (paidWorkersByFacility != null) {
            return Math.max(0, paidWorkersByFacility.getOrDefault(facility.id(), 0));
        }
        return state.industryAccounts().stream()
                .filter(account -> facility.id().equals(account.facilityId()))
                .mapToLong(account -> Math.max(0, account.paidWorkers())).findFirst().orElse(0L);
    }

    private static String empireForOwner(GameState state, String ownerEntityId) {
        return state.empires().stream().filter(empire -> ownerEntityId.equals(empire.id()))
                .map(Empire::id).findFirst().orElseGet(() -> state.corporations().stream()
                        .filter(corporation -> ownerEntityId.equals(corporation.id()))
                        .map(Corporation::empireId).findFirst().orElse(""));
    }

    private static Empire empireRecordForOwner(GameState state, String ownerEntityId) {
        if (state == null || ownerEntityId == null) return null;
        return state.empires().stream().filter(empire -> ownerEntityId.equals(empire.id()))
                .findFirst().orElseGet(() -> state.corporations().stream()
                        .filter(corporation -> ownerEntityId.equals(corporation.id()))
                        .map(Corporation::empireId)
                        .map(id -> state.empires().stream()
                                .filter(empire -> id.equals(empire.id())).findFirst().orElse(null))
                        .findFirst().orElse(null));
    }

    private static Profile emptyProfile() {
        return new Profile("Unavailable", 0.0, 1.0, 1.0, 0.0, 0.0,
                false, Map.of(), Map.of());
    }
}
