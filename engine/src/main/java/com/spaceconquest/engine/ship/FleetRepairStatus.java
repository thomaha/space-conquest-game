package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Immutable read-only explanation of the existing repair rules at one snapshot. */
public record FleetRepairStatus(List<Ship> ships, List<Provider> providers, double fundedOnboardPoints,
                                ShipRepairPriority.Context priority, Base base, String savedMission) {
    public FleetRepairStatus { ships = List.copyOf(ships); providers = List.copyOf(providers); }
    public record Mount(int index, String name, double damagePoints) {}
    public record Ship(String id, double hullPoints, List<Mount> mounts, boolean knownDesign, boolean alive) {
        public Ship { mounts = List.copyOf(mounts); }
    }
    public record Provider(String shipId, double installedPoints, double availablePoints, List<String> blockers,
                           String firstRecipient, Map<String, Double> firstRecipeShortages) {
        public Provider { blockers = List.copyOf(blockers); firstRecipeShortages = Map.copyOf(firstRecipeShortages); }
    }
    public record Base(String name, double sharedPoints, String firstRecipient, String firstJob,
                       double quotedPoints, double quotedCredits, Map<String, Double> shortages, List<String> blockers) {
        public Base { shortages = Map.copyOf(shortages); blockers = List.copyOf(blockers); }
    }
    private record Job(String shipId, String name, double points, Map<String, Double> perPoint) {}

    public static FleetRepairStatus inspect(GameState state, Fleet fleet) {
        var priority = ShipRepairPriority.context(state, fleet);
        var ships = new ArrayList<Ship>(); var providers = new ArrayList<Provider>();
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            var mounts = new ArrayList<Mount>();
            if (design != null) for (int mount : ShipRepairPriority.mounts(priority, design, ship)) {
                var module = ShipModuleDamage.damageableModule(design.equippedModuleIds().get(mount));
                mounts.add(new Mount(mount, module.name(), ship.moduleDamage().damageByMount().get(mount)));
            }
            ships.add(new Ship(ship.id(), hullNeed(ship), mounts, design != null, ship.currentHullHealth() > 0));
            if (design == null) continue;
            double installed = ship.moduleDamage().capability(design, "repairPointsPerDay");
            boolean hasWorkshop = design.equippedModuleIds().contains(ShipRepairModuleCatalog.WORKSHOP);
            if (!hasWorkshop) continue;
            var blockers = providerBlockers(state, fleet, ship, design, installed);
            double available = blockers.isEmpty() ? ShipRepairProcessor.capacity(state, fleet, ship) : 0;
            var targets = new ArrayList<ShipInstance>(); targets.add(ship);
            fleet.ships().stream().filter(other -> !other.id().equals(ship.id())).forEach(targets::add);
            var job = firstJob(state, fleet, targets, priority);
            var shortages = job == null ? Map.<String, Double>of() : shortages(ship.storedCargoKg(), job.perPoint(),
                    Math.min(available, job.points()));
            providers.add(new Provider(ship.id(), installed, available, blockers,
                    job == null ? "" : job.shipId() + " / " + job.name(), shortages));
        }
        var projected = ShipRepairProcessor.repair(state, fleet);
        double funded = Math.max(0, need(state, fleet) - need(state, projected));
        return new FleetRepairStatus(ships, providers, funded, priority, base(state, fleet, priority), mission(fleet));
    }

    private static List<String> providerBlockers(GameState state, Fleet fleet, ShipInstance ship, ShipDesign design, double installed) {
        var result = new ArrayList<String>();
        if (ship.currentHullHealth() <= 0 || !fleet.ownerEntityId().equals(ship.ownerEntityId())) result.add("Ship unavailable");
        if (installed <= 0) result.add("All workshops disabled");
        if (design.powerProfile() == null || ship.powerState() == null) result.add("No supported electrical profile");
        else {
            long count = design.equippedModuleIds().stream().filter(ShipRepairModuleCatalog.WORKSHOP::equals).count();
            if (design.powerProfile().hotelKw() + 1e-9 < count * ShipRepairModuleCatalog.module(ShipRepairModuleCatalog.WORKSHOP).powerDrawKw())
                result.add("Workshop power demand missing from design");
            if (ship.powerState().lastUnmetEssentialKwh() > 1e-6 || ship.powerState().unmetDriveKwh() > 1e-6)
                result.add("Recorded power shortfall");
            if (!ShipServicePower.funded(ship, ShipServicePower.WORKSHOPS)) result.add("Workshop switched off for power shortage");
        }
        if (Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()) || (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                || fleet.location().localFlight() != null && fleet.location().localFlight().interrupted())
                && !PropulsionDamage.damaged(state, fleet) && !ShipElectricalDamage.damaged(state, fleet)) result.add("Interrupted flight or recovery");
        if (state.tradeRoutes().stream().anyMatch(route -> route.assignedFreighterIds().contains(ship.id())
                && (route.isActive() || route.onboardKg() > 1e-9))) result.add("Repair cargo protected for trade delivery");
        return result;
    }

    private static Job firstJob(GameState state, Fleet fleet, List<ShipInstance> targets, ShipRepairPriority.Context priority) {
        for (var ship : targets) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || ship.currentHullHealth() <= 0 || !fleet.ownerEntityId().equals(ship.ownerEntityId())) continue;
            var mounts = ShipRepairPriority.mounts(priority, design, ship);
            if (!mounts.isEmpty()) {
                int mount = mounts.getFirst(); var module = ShipModuleDamage.damageableModule(design.equippedModuleIds().get(mount));
                var recipe = new TreeMap<String, Double>();
                module.materialCostsKg().forEach((id, kg) -> recipe.put(id, kg / ShipModuleDamage.DURABILITY));
                return new Job(ship.id(), module.name(), ship.moduleDamage().damageByMount().get(mount), Map.copyOf(recipe));
            }
            if (hullNeed(ship) > 1e-9) return new Job(ship.id(), "Hull", hullNeed(ship), Map.of(design.hullMaterialId(), 1.0));
        }
        return null;
    }

    private static Base base(GameState state, Fleet fleet, ShipRepairPriority.Context priority) {
        if (fleet.location().inTransit() || fleet.location().current().kind() != FleetLocation.Kind.DOCKED) return null;
        var station = state.orbitalStations().stream().filter(item -> fleet.location().current().entityId().equals(item.id())
                && fleet.currentSystemId().equals(item.systemId())).findFirst().orElse(null);
        if (station == null) return null;
        var blockers = new ArrayList<String>(); double capacity = BaseRepairCatalog.capacity(state, station);
        if (!BaseRepairCatalog.accessible(state, station, fleet.ownerEntityId())) blockers.add("No base repair access");
        if (fleet.isInWarp() || fleet.hasInterstellarOrder()) blockers.add("Departure order prevents base service");
        if (capacity <= 1e-9) blockers.add("Base workshop absent, offline, unstaffed or unpowered");
        var job = firstJob(state, fleet, fleet.ships(), priority);
        if (job == null) return new Base(station.name(), capacity, "", "", 0, 0, Map.of(), blockers);
        var quote = BaseRepairPayment.quote(state, station.id(), fleet.ownerEntityId(), job.perPoint(), Math.min(capacity, job.points()));
        if (!quote.blocker().isEmpty() && capacity > 0) blockers.add(quote.blocker());
        boolean eligible = BaseRepairCatalog.accessible(state, station, fleet.ownerEntityId()) && !fleet.isInWarp() && !fleet.hasInterstellarOrder();
        return new Base(station.name(), capacity, job.shipId(), job.name(), eligible ? quote.points() : 0,
                eligible ? quote.credits() : 0, quote.shortages(), blockers);
    }

    private static Map<String, Double> shortages(Map<String, Double> stock, Map<String, Double> recipe, double points) {
        var missing = new TreeMap<String, Double>();
        recipe.forEach((id, kg) -> { double need = Math.max(0, kg * points - stock.getOrDefault(id, 0.0)); if (need > 1e-9) missing.put(id, need); });
        return Map.copyOf(missing);
    }
    private static double hullNeed(ShipInstance ship) {
        return ship.currentHullHealth() <= 0 ? 0 : Math.max(0, ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH - ship.currentHullHealth());
    }
    private static double need(GameState state, Fleet fleet) {
        double points = 0;
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || ship.currentHullHealth() <= 0 || !fleet.ownerEntityId().equals(ship.ownerEntityId())) continue;
            points += hullNeed(ship);
            for (int mount : ShipRepairPriority.mounts(new ShipRepairPriority.Context(false, FleetThreatAssessment.Action.NO_CONTACT, false), design, ship))
                points += ship.moduleDamage().damageByMount().get(mount);
        }
        return points;
    }
    private static String mission(Fleet fleet) {
        var order = fleet.interceptOrder();
        if (order == null || order.protection() == null) return "";
        var saved = order.protection(); var intent = saved.repairIntent();
        if (saved.guard() != null) return "Guard " + saved.guard().targetId();
        if (!saved.convoyFleetId().isBlank()) return "Rejoin escort of " + saved.convoyFleetId();
        if (intent != null && intent.escort() != null) return "Resume escort " + intent.escort().escortFleetId();
        if (intent != null && intent.intercept() != null) return "Intercept " + intent.intercept().targetFleetId();
        return intent == null ? "Return to original site" : "Restore stance " + intent.stance();
    }
}
