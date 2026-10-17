package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SharedShipyardTest {
    @TempDir Path directory;
    GameState world(String provider) throws Exception {
        var state = new AITransportConstructionTest().world(true); var yard = state.orbitalStations().getFirst();
        var station = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), yard.planetOrbitId(), provider,
                provider.equals("owner") ? OrbitalStation.OWNERSHIP_PUBLIC_STATE : OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE,
                yard.totalSlots(), yard.modules(), yard.storedCargoKg(), yard.currentPowerGenerationKw(), yard.currentPowerDemandKw(), yard.currentShieldHealth(), yard.maxShieldHealth(),
                yard.currentHullHealth(), yard.maxHullHealth(), yard.armorMaterialId(), yard.armorThicknessCm(), yard.isOperational(), yard.populations(), yard.parkingAltitudeKm(), yard.orbitalPhaseRadians());
        var corporations = new ArrayList<>(state.corporations());
        if (!provider.equals("owner")) corporations.add(new Corporation(provider, "Yard company", "owner", "port-a", "METALLURGY", 1000000, List.of(), List.of(), List.of()));
        return state.toBuilder().orbitalStations(List.of(station, state.orbitalStations().getLast())).corporations(corporations).build();
    }
    private double cash(GameState state) {
        return state.empires().stream().mapToDouble(Empire::treasuryCredits).sum() + state.corporations().stream().mapToDouble(Corporation::liquidCapitalReserves).sum()
                + state.marketAccounts().stream().mapToDouble(account -> account.unsettledSalesCredits()).sum();
    }
    private ShipConstructionOrder extended(ShipConstructionOrder order, double work) {
        return new ShipConstructionOrder(order.id(), order.ownerEntityId(), order.designId(), order.systemId(), order.yardBodyId(), 0, work, order.requiredMaterialsKg(), Map.of(), order.yardContract());
    }
    @Test void publicAndCorporateProvidersReceiveOnlyTheActualWorkFees() throws Exception {
        for (String provider : List.of("owner", "yard-corp")) {
            var state = world(provider); var command = new QueueShipBuildCommand("corp", "corporate-design", "a"); assertTrue(command.validate(state));
            var ordered = command.apply(state); var order = ordered.shipConstructionOrders().getFirst(); assertEquals(provider, order.yardContract().providerOwnerId());
            double fee = order.requiredWorkHours() * order.yardContract().feeCreditsPerWorkUnit();
            var built = ordered;
            for (int day = 0; day < 20 && !built.shipConstructionOrders().isEmpty(); day++) built = new ShipConstructionProcessor().process(built);
            assertTrue(built.shipConstructionOrders().isEmpty());
            assertEquals(EconomicOwnership.cash(state, provider) + fee, EconomicOwnership.cash(built, provider), .001);
            assertEquals(cash(state), cash(built), .001); assertEquals("corp", built.fleets().getFirst().ownerEntityId());
            assertEquals(state.shipDesigns(), built.shipDesigns());
        }
    }
    @Test void twoCustomersShareTheSamePaidDailyCapacityAndKeepTheirSavedRates() throws Exception {
        var state = world("yard-corp"); var base = state.shipDesigns().getLast();
        var design = new ShipDesign("other-design", base.name(), "other", base.role(), base.hullMaterialId(), base.equippedModuleIds(), base.armorMaterialId(), base.armorThicknessCm(),
                base.totalDryMassKg(), base.maxCargoMassKg(), base.fuelCapacityKg(), base.powerBalanceKw(), base.calculatedStructuralIntegrity(), base.minLaunchThrustRequiredN(), base.totalThrustN(),
                base.isValidForLaunch(), true, base.manufacturingProfile(), base.powerProfile());
        var corporations = new ArrayList<>(state.corporations()); corporations.add(new Corporation("other", "Other client", "owner", "port-a", "TRANSPORT", 1000000, List.of(), List.of(), List.of()));
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(design); state = state.toBuilder().corporations(corporations).shipDesigns(designs).build();
        var first = new QueueShipBuildCommand("corp", "corporate-design", "a").apply(state);
        var second = new QueueShipBuildCommand("other", "other-design", "a").apply(first); assertEquals(2, second.shipConstructionOrders().size());
        double capacity = ShipyardWorkCapacity.forYard(state, "yard-corp", "a", "port-a").workPerDay();
        second = second.withShipConstructionOrders(second.shipConstructionOrders().stream().map(order -> extended(order, capacity * 2)).toList());
        var oneDay = new ShipConstructionProcessor().process(second); var orders = oneDay.shipConstructionOrders();
        assertEquals(capacity, orders.stream().mapToDouble(ShipConstructionOrder::accumulatedWorkHours).sum(), .001);
        assertEquals(0, orders.getLast().accumulatedWorkHours()); assertEquals(capacity * orders.getFirst().yardContract().feeCreditsPerWorkUnit(), orders.getFirst().yardContract().paidFeesCredits(), .001);
        var file = directory.resolve("shared.scsave").toFile(); var saves = new SaveGameManager(directory); saves.save(file, oneDay, 1, GameClock.START_TIME.toString());
        var reloaded = saves.load(file).toGameState(oneDay.turn(), oneDay.status()); assertEquals(orders, reloaded.shipConstructionOrders());
        for (int day = 0; day < 3; day++) reloaded = new ShipConstructionProcessor().process(reloaded);
        assertTrue(reloaded.shipConstructionOrders().isEmpty()); assertEquals(2, reloaded.fleets().size());
        double totalFees = capacity * 4 * ShipyardAccessPolicy.current().feeCreditsPerWorkUnit();
        assertEquals(EconomicOwnership.cash(state, "yard-corp") + totalFees, EconomicOwnership.cash(reloaded, "yard-corp"), .001);
        assertEquals(cash(state), cash(reloaded), .001);
    }
    @Test void queueAdmissionAccountsForExistingPublicWork() throws Exception {
        var state = world("owner"); var publicOrder = new QueueShipBuildCommand("owner", "design", "a").apply(state).shipConstructionOrders().getFirst();
        double capacity = ShipyardWorkCapacity.forYard(state, "owner", "a", "port-a").workPerDay();
        var full = state.withShipConstructionOrders(List.of(extended(publicOrder, capacity * ShipyardAccessPolicy.current().maximumQueuedWorkDays())));
        var command = new QueueShipBuildCommand("corp", "corporate-design", "a"); assertFalse(command.validate(full)); assertSame(full, command.apply(full));
    }
    @Test void shortagesOfCashOrMaterialsChargeNoFeeForZeroWork() throws Exception {
        var state = new QueueShipBuildCommand("corp", "corporate-design", "a").apply(world("owner"));
        var corp = state.corporations().getFirst();
        var noCash = state.toBuilder().corporations(List.of(new Corporation(corp.id(), corp.name(), corp.empireId(), corp.headquartersEntityId(), corp.marketOrientation(), 0, corp.ownedFacilityIds(), corp.ownedShipIds(), corp.claimedVeinIds()))).build();
        var noMaterials = state.withCommercialHubs(List.of());
        for (var blocked : List.of(noCash, noMaterials)) {
            var result = new ShipConstructionProcessor().process(blocked);
            assertEquals(0, result.shipConstructionOrders().getFirst().accumulatedWorkHours()); assertEquals(0, result.shipConstructionOrders().getFirst().yardContract().paidFeesCredits());
            assertEquals(blocked.empires(), result.empires()); assertEquals(blocked.corporations(), result.corporations()); assertTrue(result.fleets().isEmpty());
        }
    }
    @Test void publicYardAIConstructionSurvivesReloadAndItsCompanyDelivers() throws Exception {
        var state = world("owner").withTurn(3); var request = new RequestAIFacilityTransportBuildCommand("corp", "corporate-design", "a", "destination", "steel", 0);
        assertTrue(request.validate(state)); var ordered = request.apply(state); assertNotNull(ordered.shipConstructionOrders().getFirst().yardContract());
        var file = directory.resolve("lifecycle.scsave").toFile(); var saves = new SaveGameManager(directory); saves.save(file, ordered, 1, GameClock.START_TIME.plusDays(3).toString());
        var restored = saves.load(file).toGameState(3, state.status()); assertSame(restored, request.apply(restored));
        var engine = new SpaceConquestEngine(42); engine.applyGameState(restored);
        for (int day = 0; day < 20 && !engine.getGameState().shipConstructionOrders().isEmpty(); day++) engine.stepTurn();
        var built = engine.getGameState(); assertTrue(built.shipConstructionOrders().isEmpty()); assertEquals("corp", built.fleets().getFirst().ownerEntityId());
        var next = built.withTurn(built.turn() + 1);
        var dispatch = new DeployAIShipCommand("corp", next.fleets().getFirst().ships().getFirst().id()).apply(next); engine.applyGameState(dispatch); double produced = 0;
        for (int day = 0; day < 25; day++) { engine.stepTurn(); produced += engine.getGameState().industryAccounts().stream().filter(account -> account.facilityId().equals("factory"))
                .mapToDouble(account -> account.producedKg().getOrDefault("cannon_round", 0.0)).sum(); }
        assertTrue(produced > 0); assertFalse(engine.getGameState().tradeRoutes().getFirst().carrierCommitted());
    }
    @Test void lostAccessAndStaffingPauseSavedOrdersWithoutChargingFees() throws Exception {
        var state = new QueueShipBuildCommand("corp", "corporate-design", "a").apply(world("owner")); var owner = state.empires().getFirst();
        var lost = new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), owner.corporateTaxRate(), List.of("b"),
                owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds());
        var yard = state.orbitalStations().getFirst();
        var unstaffed = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), yard.planetOrbitId(), yard.ownerEntityId(), yard.ownershipType(), yard.totalSlots(),
                yard.modules().stream().map(module -> module.withPayroll(0, 0)).toList(), yard.storedCargoKg(), 0, 0, 0, 0, 100, 100, "steel", 0, true, yard.populations(), yard.parkingAltitudeKm(), yard.orbitalPhaseRadians());
        for (var blocked : List.of(state.withEmpires(List.of(lost)), state.toBuilder().orbitalStations(List.of(unstaffed, state.orbitalStations().getLast())).build())) {
            var result = new ShipConstructionProcessor().process(blocked); assertEquals(blocked.shipConstructionOrders(), result.shipConstructionOrders());
            assertEquals(blocked.empires(), result.empires()); assertEquals(blocked.corporations(), result.corporations()); assertTrue(result.fleets().isEmpty());
        }
        assertFalse(ShipyardAccess.permitted(state, "corp", "owner", "a", new ShipyardAccessPolicy(false, true, 2, 30)));
        assertFalse(ShipyardAccess.permitted(world("yard-corp"), "corp", "yard-corp", "a", new ShipyardAccessPolicy(true, false, 2, 30)));
    }
    @Test void corporationsCanPayForSurfaceYardWorkWithoutTakingItsOwnership() throws Exception {
        var state = world("owner"); var stock = state.commercialHubs().getFirst();
        var hub = new CommercialHub("surface", "planet-a", 0, stock.storageCapacityKg(), stock.currentStoredWeightKg(), 10, stock.activeOrders());
        var facilities = new ArrayList<>(state.industrialFacilities()); facilities.add(new IndustrialFacility("surface-yard", "planet-a", "surface_shipyard", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "industrial_worker", false, 0));
        var accounts = new ArrayList<>(state.industryAccounts()); accounts.add(IndustryAccount.empty("surface-yard").withPaidWorkers(100));
        state = state.toBuilder().orbitalStations(List.of(state.orbitalStations().getLast())).industrialFacilities(facilities).industryAccounts(accounts)
                .commercialHubs(List.of(hub, state.commercialHubs().getLast())).marketAccounts(List.of(new MarketAccount("surface", 20000), state.marketAccounts().getLast())).build();
        var command = new QueueShipBuildCommand("corp", "corporate-design", "a"); assertTrue(command.validate(state)); assertEquals("planet-a", command.resolveYardEntity(state));
        var ordered = command.apply(state); var order = ordered.shipConstructionOrders().getFirst(); var built = ordered;
        for (int day = 0; day < 20 && !built.shipConstructionOrders().isEmpty(); day++) built = new ShipConstructionProcessor().process(built);
        assertTrue(built.shipConstructionOrders().isEmpty()); assertEquals("corp", built.fleets().getFirst().ownerEntityId());
        assertEquals(EconomicOwnership.cash(state, "owner") + order.requiredWorkHours() * order.yardContract().feeCreditsPerWorkUnit(), EconomicOwnership.cash(built, "owner"), .001);
        assertEquals("owner", built.industrialFacilities().getLast().ownerEntityId()); assertEquals(cash(state), cash(built), .001);
    }
    @Test void capturedProviderCannotChargeAfterTheYardChangesOwner() throws Exception {
        var state = new QueueShipBuildCommand("corp", "corporate-design", "a").apply(world("owner")); var yard = state.orbitalStations().getFirst();
        var changed = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), yard.planetOrbitId(), "corp", OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE,
                yard.totalSlots(), yard.modules(), yard.storedCargoKg(), 0, 0, 0, 0, 100, 100, "steel", 0, true, yard.populations(), yard.parkingAltitudeKm(), yard.orbitalPhaseRadians());
        var transferred = state.toBuilder().orbitalStations(List.of(changed, state.orbitalStations().getLast())).build();
        var paused = new ShipConstructionProcessor().process(transferred); assertEquals(transferred.shipConstructionOrders(), paused.shipConstructionOrders());
        assertEquals(transferred.empires(), paused.empires()); assertEquals(transferred.corporations(), paused.corporations()); assertTrue(paused.fleets().isEmpty());
    }
}
