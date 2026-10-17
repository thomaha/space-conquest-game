package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.industry.PowerBillingProcessor;
import com.spaceconquest.engine.industry.PowerPlantCatalog;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.market.OrbitalLiftProfile;
import com.spaceconquest.engine.ship.PropulsionCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Selects and settles a real surface-to-orbit provider for one shipment. */
public final class LaunchService {
    public enum Mode { ROCKET, MASS_DRIVER, SPACE_ELEVATOR }
    public record Plan(Mode mode, String providerId, String providerOwnerId,
                       String bodyId, String hubId, double payloadKg,
                       double serviceFeeCredits, double fuelKg, double fuelCostCredits,
                       double powerKw, double powerCostCredits, Map<String, Double> fuelMaterialsKg) {
        public Plan { fuelMaterialsKg = Map.copyOf(fuelMaterialsKg); }
        public Plan(Mode mode, String providerId, String providerOwnerId, String bodyId, String hubId,
                    double payloadKg, double serviceFeeCredits, double fuelKg, double fuelCostCredits,
                    double powerKw, double powerCostCredits) {
            this(mode, providerId, providerOwnerId, bodyId, hubId, payloadKg, serviceFeeCredits,
                    fuelKg, fuelCostCredits, powerKw, powerCostCredits, Map.of());
        }
        public double totalCredits() { return serviceFeeCredits + fuelCostCredits + powerCostCredits; }
    }

    public static double payerCost(Plan plan, String payerId) {
        return plan == null ? 0.0 : plan.fuelCostCredits()
                + (payerId.equals(plan.providerOwnerId()) ? 0.0 : plan.serviceFeeCredits());
    }

    public static double payerOperatingCost(GameState state, Plan plan, String payerId) {
        if (plan == null) return 0.0;
        boolean publicFacility = state.industrialFacilities().stream()
                .anyMatch(item -> plan.providerId().equals(item.id())
                        && IndustrialFacility.PUBLIC_STATE.equals(item.ownershipType()));
        return payerCost(plan, payerId) + (!publicFacility
                && payerId.equals(plan.providerOwnerId()) ? plan.powerCostCredits() : 0.0);
    }

    private LaunchService() {}

    public static Plan choose(GameState state, String bodyId, String payerId,
                              double payloadKg, boolean passengers, boolean shipLaunch,
                              double shipDryMassKg) {
        return choose(state, bodyId, payerId, payloadKg, passengers, shipLaunch,
                shipDryMassKg, null, null);
    }

    public static Plan choose(GameState state, String bodyId, String payerId,
                              double payloadKg, boolean passengers, boolean shipLaunch,
                              double shipDryMassKg, Mode requiredMode) {
        return choose(state, bodyId, payerId, payloadKg, passengers, shipLaunch,
                shipDryMassKg, requiredMode, null);
    }

    public static Plan choose(GameState state, String bodyId, String payerId,
                              double payloadKg, boolean passengers, boolean shipLaunch,
                              double shipDryMassKg, Mode requiredMode,
                              String requiredProviderId) {
        if (state == null || bodyId == null || payerId == null || !Double.isFinite(payloadKg)
                || payloadKg <= 0.0 || !knownOwner(state, payerId)) return null;
        CommercialHub hub = state.commercialHubs().stream()
                .filter(item -> bodyId.equals(item.entityId())).findFirst().orElse(null);
        if (hub == null) return null;
        List<Plan> plans = new ArrayList<>();
        for (IndustrialFacility facility : state.industrialFacilities()) {
            if (!bodyId.equals(facility.planetId()) || facility.tier() <= 0
                    || !knownOwner(state, facility.ownerEntityId())) continue;
            var definition = InfrastructureService.find(facility.applicationId());
            if (definition == null) continue;
            var service = definition.capability();
            if (service.mode() == Mode.SPACE_ELEVATOR) continue;
            if ((shipLaunch && !service.shipLaunch()) || (passengers && !service.passengers())) continue;
            if (!available(state, facility.id(), LaunchServiceWork.capacity(state, facility.id()), payloadKg)) continue;
            if (service.mode() == Mode.ROCKET) {
                Plan rocket = rocket(state, facility, hub, payloadKg, shipLaunch, shipDryMassKg, service);
                if (rocket != null) plans.add(rocket);
            } else {
                double power = service.powerKwPerTier() * facility.tier() + service.powerKwPerKg() * payloadKg;
                double electricity = electricityCost(state, bodyId, power);
                if (hasPower(state, bodyId, power))
                    plans.add(new Plan(service.mode(), facility.id(), facility.ownerEntityId(),
                            bodyId, hub.id(), payloadKg,
                            LaunchServiceWork.fee(state, facility.id(), payloadKg, electricity,
                                    Math.max(service.minimumFee(), payloadKg * service.feePerKg() + electricity * service.powerPriceMultiplier())),
                            0.0, 0.0, power, electricity));
            }
        }
        for (SpaceElevator elevator : state.spaceElevators()) {
            var service = InfrastructureService.find("SPACE_ELEVATOR").capability();
            double power = payloadKg * service.powerKwPerKg();
            if (!bodyId.equals(elevator.planetId()) || !elevator.isOperational()
                    || elevator.structuralIntegrityPercent() <= 0.0
                    || !knownOwner(state, elevator.ownerEntityId())
                    || !available(state, elevator.id(),
                    Math.min(elevator.transitThroughputCapacityKgPerTurn(), LaunchServiceWork.capacity(state, elevator.id())), payloadKg)
                    || !hasPower(state, bodyId, power)) continue;
            plans.add(new Plan(Mode.SPACE_ELEVATOR, elevator.id(), elevator.ownerEntityId(),
                    bodyId, hub.id(), payloadKg,
                    LaunchServiceWork.fee(state, elevator.id(), payloadKg, electricityCost(state, bodyId, power),
                            Math.max(service.minimumFee(), payloadKg * service.feePerKg()
                                    * (1.0 - Math.clamp(elevator.surfaceToOrbitCostDiscount(), 0.0, 1.0)))),
                    0.0, 0.0, power, electricityCost(state, bodyId, power)));
        }
        double cash = balance(state, payerId);
        return plans.stream().filter(plan -> requiredMode == null || plan.mode() == requiredMode)
                .filter(plan -> requiredProviderId == null
                        || plan.providerId().equals(requiredProviderId))
                .filter(plan -> payerOperatingCost(state, plan, payerId) <= cash + 0.000001)
                .filter(plan -> providerCanPayPower(state, plan, payerId))
                .min(Comparator.comparingDouble(plan -> payerOperatingCost(state, plan, payerId)))
                .orElse(null);
    }

    private static Plan rocket(GameState state, IndustrialFacility facility,
                               CommercialHub hub, double payloadKg, boolean shipLaunch,
                               double shipDryMassKg, InfrastructureService service) {
        PropulsionCatalog.Drive drive = PropulsionCatalog.drive(service.driveId());
        if (drive == null) return null;
        var mixture = drive.propellantMaterials(1);
        double blendedPrice = 0;
        for (var material : mixture.entrySet()) {
            var order = hub.activeOrders().get(material.getKey());
            if (order == null || !Double.isFinite(order.pricePerKg()) || order.pricePerKg() <= 0) return null;
            blendedPrice += material.getValue() * order.pricePerKg();
        }
        double dryMass = shipLaunch ? shipDryMassKg : service.vehicleDryMassKg();
        OrbitalLiftProfile profile = OrbitalFreightCost.profile(state, hub.entityId(),
                dryMass, payloadKg, blendedPrice, drive.exhaustVelocityMps() / 9.80665);
        if (profile == null || !Double.isFinite(profile.propellantConsumedKg())) return null;
        var fuelMaterials = drive.propellantMaterials(profile.propellantConsumedKg());
        if (fuelMaterials.entrySet().stream().anyMatch(e -> e.getValue() > hub.activeOrders().get(e.getKey()).supplyKg())) return null;
        double fee = Math.max(service.minimumFee(), (dryMass + payloadKg) * service.feePerKg()
                + profile.turnaroundWearCostCredits());
        return new Plan(Mode.ROCKET, facility.id(), facility.ownerEntityId(),
                hub.entityId(), hub.id(), payloadKg, LaunchServiceWork.fee(state, facility.id(), payloadKg, 0, fee), profile.propellantConsumedKg(),
                profile.propellantConsumedKg() * blendedPrice, 0.0, 0.0,
                fuelMaterials);
    }

    public static GameState settle(GameState state, String payerId, Plan plan) {
        if (plan == null || !resourcesAvailable(state, plan)
                || balance(state, payerId) + 0.000001 < payerCost(plan, payerId)
                || !providerCanPayPower(state, plan, payerId))
            return state;
        GameState current = adjustOwner(state, payerId, -payerCost(plan, payerId));
        if (!payerId.equals(plan.providerOwnerId()))
            current = payProvider(current, plan);
        if (plan.powerCostCredits() > 0.0)
            current = adjustProvider(current, plan, -plan.powerCostCredits());
        if (plan.fuelKg() > 0.0) current = consumeFuel(current, plan);
        if (plan.powerKw() > 0.0) current = consumePower(current, plan);
        Map<String, Double> usage = new HashMap<>(current.launchUsageKg());
        usage.merge(plan.providerId(), plan.payloadKg(), Double::sum);
        List<LaunchServiceActivity> activities = new ArrayList<>(current.launchActivities());
        activities.add(new LaunchServiceActivity(plan.providerId(), plan.providerOwnerId(),
                plan.bodyId(), payerId.equals(plan.providerOwnerId()) ? 0.0
                : plan.serviceFeeCredits(), plan.powerKw(), plan.powerCostCredits()));
        return current.toBuilder().launchUsageKg(Map.copyOf(usage))
                .launchActivities(List.copyOf(activities)).build();
    }

    private static GameState consumeFuel(GameState state, Plan plan) {
        Map<String, Double> consumed = plan.fuelMaterialsKg();
        List<CommercialHub> hubs = new ArrayList<>(state.commercialHubs());
        for (int index = 0; index < hubs.size(); index++) {
            CommercialHub hub = hubs.get(index);
            if (!plan.hubId().equals(hub.id())) continue;
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            consumed.forEach((material, kg) -> {
                MarketOrder old = orders.get(material);
                orders.put(material, new MarketOrder(material,
                        Math.max(0.0, old.supplyKg() - kg), old.demandKg(),
                        old.pricePerKg(), old.shortcomingScore()));
            });
            hubs.set(index, hub.withInventory(state.turn(), Math.max(0.0, hub.currentStoredWeightKg() - plan.fuelKg()), orders));
            break;
        }
        List<MarketAccount> accounts = new ArrayList<>(state.marketAccounts());
        accounts.removeIf(item -> plan.hubId().equals(item.hubId()));
        double prior = state.marketAccounts().stream().filter(item ->
                plan.hubId().equals(item.hubId())).mapToDouble(MarketAccount::unsettledSalesCredits)
                .findFirst().orElse(0.0);
        accounts.add(new MarketAccount(plan.hubId(), prior + plan.fuelCostCredits()));
        return state.toBuilder().commercialHubs(hubs).marketAccounts(accounts).build();
    }

    private static GameState payProvider(GameState state, Plan plan) {
        return adjustProvider(state, plan, plan.serviceFeeCredits());
    }

    private static GameState adjustProvider(GameState state, Plan plan, double delta) {
        if (delta == 0.0) return state;
        IndustrialFacility facility = state.industrialFacilities().stream()
                .filter(item -> plan.providerId().equals(item.id()))
                .findFirst().orElse(null);
        if (facility == null || !IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType()))
            return adjustOwner(state, plan.providerOwnerId(), delta);
        List<IndustryAccount> accounts = new ArrayList<>(state.industryAccounts());
        IndustryAccount previous = accounts.stream()
                .filter(item -> plan.providerId().equals(item.facilityId()))
                .findFirst().orElse(IndustryAccount.empty(plan.providerId()));
        accounts.removeIf(item -> plan.providerId().equals(item.facilityId()));
        accounts.add(previous.withOperatingCash(previous.operatingCashCredits()
                + delta));
        return state.toBuilder().industryAccounts(accounts).build();
    }

    private static double electricityCost(GameState state, String bodyId, double powerKw) {
        boolean hasSeller = state.industrialFacilities().stream().anyMatch(facility ->
                bodyId.equals(facility.planetId()) && facility.tier() > 0
                        && !IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())
                        && PowerPlantCatalog.find(facility.applicationId()) != null);
        return hasSeller ? powerKw * com.spaceconquest.engine.industry.GridSupplyPricing.price(state, bodyId) : 0.0;
    }

    private static boolean providerCanPayPower(GameState state, Plan plan, String payerId) {
        if (plan.powerCostCredits() <= 0.0) return true;
        IndustrialFacility facility = state.industrialFacilities().stream()
                .filter(item -> plan.providerId().equals(item.id()))
                .findFirst().orElse(null);
        double available = facility != null
                && IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())
                ? state.industryAccounts().stream().filter(item ->
                plan.providerId().equals(item.facilityId()))
                .mapToDouble(IndustryAccount::operatingCashCredits).findFirst().orElse(0.0)
                : balance(state, plan.providerOwnerId());
        if (payerId.equals(plan.providerOwnerId()) && (facility == null
                || !IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())))
            available -= payerCost(plan, payerId);
        if (!payerId.equals(plan.providerOwnerId())) available += plan.serviceFeeCredits();
        return available + 0.000001 >= plan.powerCostCredits();
    }

    private static GameState consumePower(GameState state, Plan plan) {
        List<PowerGridState> grids = state.powerGrids().stream().map(grid ->
                plan.bodyId().equals(grid.entityId()) ? new PowerGridState(grid.entityId(),
                        grid.totalGenerationKw(), grid.totalDemandKw() + plan.powerKw(),
                        grid.netBalanceKw() - plan.powerKw(), grid.batteryCapacityKwh(),
                        grid.currentStoredKwh(), grid.isDeficitBrownoutActive()) : grid).toList();
        return state.withPowerGrids(grids);
    }

    private static boolean hasPower(GameState state, String bodyId, double powerKw) {
        return state.powerGrids().stream().anyMatch(grid -> bodyId.equals(grid.entityId())
                && grid.netBalanceKw() + 0.000001 >= powerKw);
    }

    private static boolean available(GameState state, String providerId,
                                     double capacityKg, double payloadKg) {
        return payloadKg + state.launchUsageKg().getOrDefault(providerId, 0.0)
                <= capacityKg + 0.000001;
    }

    private static boolean resourcesAvailable(GameState state, Plan plan) {
        if (plan.powerKw() > 0 && !hasPower(state, plan.bodyId(), plan.powerKw())) return false;
        double capacity;
        if (plan.mode() == Mode.SPACE_ELEVATOR) {
            var elevator = state.spaceElevators().stream().filter(e -> plan.providerId().equals(e.id())
                    && plan.providerOwnerId().equals(e.ownerEntityId()) && e.isOperational()
                    && e.structuralIntegrityPercent() > 0).findFirst().orElse(null);
            if (elevator == null) return false;
            capacity = elevator.transitThroughputCapacityKgPerTurn();
        } else {
            var facility = state.industrialFacilities().stream().filter(f -> plan.providerId().equals(f.id())
                    && plan.providerOwnerId().equals(f.ownerEntityId()) && f.tier() > 0).findFirst().orElse(null);
            if (facility == null) return false;
            var service = InfrastructureService.find(facility.applicationId());
            if (service == null || service.capability().mode() != plan.mode()) return false;
            capacity = service.capability().capacityKgPerTier() * facility.tier();
        }
        if (!available(state, plan.providerId(), Math.min(capacity, LaunchServiceWork.capacity(state, plan.providerId())), plan.payloadKg())) return false;
        if (plan.fuelKg() <= 0) return true;
        var hub = state.commercialHubs().stream().filter(h -> plan.hubId().equals(h.id())).findFirst().orElse(null);
        return hub != null && !plan.fuelMaterialsKg().isEmpty() && plan.fuelMaterialsKg().entrySet().stream().allMatch(e ->
                hub.activeOrders().containsKey(e.getKey()) && hub.activeOrders().get(e.getKey()).supplyKg() + 1e-6 >= e.getValue());
    }

    private static boolean knownOwner(GameState state, String ownerId) {
        return state.corporations().stream().anyMatch(item -> ownerId.equals(item.id()))
                || state.empires().stream().anyMatch(item -> ownerId.equals(item.id()));
    }

    private static double balance(GameState state, String ownerId) {
        return state.corporations().stream().filter(item -> ownerId.equals(item.id()))
                .mapToDouble(Corporation::liquidCapitalReserves).findFirst().orElseGet(() ->
                state.empires().stream().filter(item -> ownerId.equals(item.id()))
                        .mapToDouble(Empire::treasuryCredits).findFirst().orElse(0.0));
    }

    private static GameState adjustOwner(GameState state, String ownerId, double delta) {
        if (delta == 0.0) return state;
        List<Corporation> corporations = state.corporations().stream().map(item ->
                ownerId.equals(item.id()) ? new Corporation(item.id(), item.name(), item.empireId(),
                        item.headquartersEntityId(), item.marketOrientation(),
                        item.liquidCapitalReserves() + delta, item.ownedFacilityIds(),
                        item.ownedShipIds(), item.claimedVeinIds()) : item).toList();
        List<Empire> empires = state.empires().stream().map(item -> ownerId.equals(item.id())
                ? new Empire(item.id(), item.name(), item.raceId(), item.societyStructure(),
                item.treasuryCredits() + delta, item.corporateTaxRate(),
                item.controlledSystemIds(), item.ministries(), item.systemGovernorAssignments(),
                item.unlockedTechIds(), item.activeShipDesignIds()) : item).toList();
        return state.toBuilder().corporations(corporations).empires(empires).build();
    }
}
