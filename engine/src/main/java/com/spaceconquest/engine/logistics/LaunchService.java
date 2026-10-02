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
                       double powerKw, double powerCostCredits) {
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
            if ("cargo_terminal".equals(facility.applicationId())) {
                Plan rocket = rocket(state, facility, hub, payloadKg, shipLaunch, shipDryMassKg);
                if (rocket != null && available(state, facility.id(),
                        100_000.0 * facility.tier(), payloadKg)) plans.add(rocket);
            } else if (!shipLaunch && !passengers
                    && "mass_driver".equals(facility.applicationId())) {
                double capacity = 1_000.0 * facility.tier();
                double power = 250.0 * facility.tier();
                double electricity = electricityCost(state, bodyId, power);
                if (available(state, facility.id(), capacity, payloadKg)
                        && hasPower(state, bodyId, power))
                    plans.add(new Plan(Mode.MASS_DRIVER, facility.id(), facility.ownerEntityId(),
                            bodyId, hub.id(), payloadKg,
                            Math.max(0.01, payloadKg * 0.0005 + electricity * 1.5),
                            0.0, 0.0, power, electricity));
            }
        }
        for (SpaceElevator elevator : state.spaceElevators()) {
            double power = payloadKg * 0.02;
            if (!bodyId.equals(elevator.planetId()) || !elevator.isOperational()
                    || elevator.structuralIntegrityPercent() <= 0.0
                    || !knownOwner(state, elevator.ownerEntityId())
                    || !available(state, elevator.id(),
                    elevator.transitThroughputCapacityKgPerTurn(), payloadKg)
                    || !hasPower(state, bodyId, power)) continue;
            plans.add(new Plan(Mode.SPACE_ELEVATOR, elevator.id(), elevator.ownerEntityId(),
                    bodyId, hub.id(), payloadKg,
                    Math.max(0.01, payloadKg * 0.05
                            * (1.0 - Math.clamp(elevator.surfaceToOrbitCostDiscount(), 0.0, 1.0))),
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
                               double shipDryMassKg) {
        PropulsionCatalog.Drive drive = PropulsionCatalog.drive("mod_chemical_rocket");
        MarketOrder fuel = hub.activeOrders().get(drive.propellantId());
        MarketOrder oxidizer = hub.activeOrders().get(drive.oxidizerId());
        if (fuel == null || oxidizer == null || !Double.isFinite(fuel.pricePerKg())
                || !Double.isFinite(oxidizer.pricePerKg()) || fuel.pricePerKg() <= 0.0
                || oxidizer.pricePerKg() <= 0.0)
            return null;
        double dryMass = shipLaunch ? shipDryMassKg : 1_000.0;
        double blendedPrice = fuel.pricePerKg() * drive.fuelFraction()
                + oxidizer.pricePerKg() * (1.0 - drive.fuelFraction());
        OrbitalLiftProfile profile = OrbitalFreightCost.profile(state, hub.entityId(),
                dryMass, payloadKg, blendedPrice);
        if (profile == null || !Double.isFinite(profile.propellantConsumedKg())
                || profile.propellantConsumedKg() * drive.fuelFraction() > fuel.supplyKg()
                || profile.propellantConsumedKg() * (1.0 - drive.fuelFraction())
                > oxidizer.supplyKg()) return null;
        double fee = profile.spaceportHandlingFeeCredits()
                + profile.turnaroundWearCostCredits();
        return new Plan(Mode.ROCKET, facility.id(), facility.ownerEntityId(),
                hub.entityId(), hub.id(), payloadKg, fee, profile.propellantConsumedKg(),
                profile.propellantConsumedKg() * blendedPrice, 0.0, 0.0);
    }

    public static GameState settle(GameState state, String payerId, Plan plan) {
        if (plan == null || balance(state, payerId) + 0.000001 < payerCost(plan, payerId)
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
        PropulsionCatalog.Drive drive = PropulsionCatalog.drive("mod_chemical_rocket");
        Map<String, Double> consumed = drive.propellantMaterials(plan.fuelKg());
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
            hubs.set(index, new CommercialHub(hub.id(), hub.entityId(),
                    hub.transactionTariffRate(), hub.storageCapacityKg(),
                    Math.max(0.0, hub.currentStoredWeightKg() - plan.fuelKg()),
                    hub.logisticsRangeUnits(), Map.copyOf(orders)));
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
        return hasSeller ? powerKw * PowerBillingProcessor.PRICE_PER_KWH : 0.0;
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
