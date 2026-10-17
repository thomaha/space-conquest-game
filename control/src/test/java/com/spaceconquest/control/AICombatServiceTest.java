package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireFleetPlanner;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AICombatServiceTest {
    private final ServiceAICombatFleetCommand command = new ServiceAICombatFleetCommand("ai", "fleet");
    private OrbitalStation station(String id, boolean repair) {
        return new OrbitalStation(id, id, "sol", "", "ai", "PUBLIC_STATE", 30,
                repair ? List.of(BaseRepairCatalog.module().withPayroll(2, 0)) : List.of(),
                Map.of(), 100, 50, 0, 0, 100, 100, "steel", 0, true);
    }
    GameState world(String weapon, double output, FleetLocation.Site site) {
        var module = CombatModuleCatalog.module(weapon);
        var profile = new ShipPowerProfile(0, 0, output, 100, 100, 1000, 0, 10,
                2 + module.powerDrawKw(), 0, 0, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6000)));
        var design = new ShipDesign("design", "Design", "ai", ShipRole.COMBAT_SHIP, "steel",
                List.of(weapon, "mod_chemical_rocket"), "steel", 0, 1000, 1000, 1000, 100,
                10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), profile);
        var ship = new ShipInstance("ship", "design", "ai", 1000, 0, 100, Map.of())
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 10.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var fleet = new Fleet("fleet", "Fleet", "ai", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(site));
        var empire = new Empire("ai", "AI", "human", "Individualist", 10000, 0,
                List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        var earth = new Planet("earth", "Earth", "", 1, 9.81, 1, 0, 12742,
                "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of());
        var orders = new HashMap<String, MarketOrder>();
        if (!module.ammunitionId().isBlank()) orders.put(module.ammunitionId(), new MarketOrder(module.ammunitionId(),
                module.operationalStats().get("ammunitionMassKg"), 0, 2, 0));
        return GameState.builder().empires(List.of(empire)).shipDesigns(List.of(design)).fleets(List.of(fleet))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1, "Yellow", List.of(earth), List.of())))
                .orbitalStations(List.of(station("home", false), station("port", true)))
                .commercialHubs(List.of(new CommercialHub("surface", "earth", 0, 10000, 100, 10, orders),
                        new CommercialHub("repair", "port", 0, 10000, 1000, 10,
                                Map.of("steel", new MarketOrder("steel", 1000, 0, 1, 0),
                                        "refined_uranium", new MarketOrder("refined_uranium", 10, 0, 2, 0)))))
                .powerGrids(List.of(new PowerGridState("port", 100, 0, 100, 1000, 200, false))).build();
    }
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "fleet"); }
    private ShipInstance ship(GameState state) { return fleet(state).ships().getFirst(); }
    private ShipWeaponReadiness.Weapon weapon(GameState state) {
        return ShipWeaponReadiness.inspect(state.shipDesigns().getFirst(), ship(state), true).weapons().getFirst();
    }
    private GameState noCash(GameState state) {
        var old = state.empires().getFirst();
        return state.withEmpires(List.of(new Empire(old.id(), old.name(), old.raceId(), old.societyStructure(), 0,
                old.corporateTaxRate(), old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(),
                old.unlockedTechIds(), old.activeShipDesignIds())));
    }

    @Test void plannerQueuesFiniteWholeRoundAndCompetingRequestCannotReuseStock() {
        var initial = world(CombatModuleCatalog.MASS_DRIVER, 10000, FleetLocation.Site.surface("earth"));
        assertEquals(ShipWeaponReadiness.Blocker.AMMUNITION, weapon(initial).blocker());
        var plan = EmpireFleetPlanner.plan(initial, initial.empires().getFirst());
        assertEquals(List.of(command), plan);
        assertTrue(ship(initial).storedCargoKg().isEmpty());
        var queue = new CommandQueue();
        var first = queue.submitTracked(command); var second = queue.submitTracked(command);
        var paid = queue.drainAndExecute(initial);
        var module = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        double mass = module.operationalStats().get("ammunitionMassKg");
        assertEquals(CommandOutcome.EXECUTED, first.join()); assertEquals(CommandOutcome.REJECTED, second.join());
        assertEquals(mass, ship(paid).storedCargoKg().get(module.ammunitionId()));
        assertEquals(10000 - mass * 2, paid.empires().getFirst().treasuryCredits());
        assertEquals(0, paid.commercialHubs().getFirst().activeOrders().get(module.ammunitionId()).supplyKg());
        assertEquals(ShipWeaponReadiness.Blocker.NONE, weapon(paid).blocker());
        assertEquals(ship(initial).powerState(), ship(paid).powerState());
    }

    @Test void paidGridChargeRestoresReadinessWithoutSpendingTheQuoteOrReusingEnergy() {
        var initial = world(CombatModuleCatalog.LASER, 36, FleetLocation.Site.docked("port"));
        assertEquals(ShipWeaponReadiness.Blocker.POWER, weapon(initial).blocker());
        assertTrue(command.validate(initial)); assertEquals(0, ship(initial).powerState().batteryChargeKwh());
        var paid = command.apply(initial);
        double input = 100 / ShipPowerProcessor.CHARGING_EFFICIENCY;
        assertEquals(100, ship(paid).powerState().batteryChargeKwh(), 1e-9);
        assertEquals(200 - input, paid.powerGrids().getFirst().currentStoredKwh(), 1e-9);
        assertEquals(input, ship(paid).powerState().chargedInputKwhToday(), 1e-9);
        assertEquals(ShipWeaponReadiness.Blocker.NONE, weapon(paid).blocker());
        assertSame(paid, command.apply(paid));
        assertEquals(10, ship(paid).generatorFuelMassKg());
        var empty = initial.toBuilder().powerGrids(List.of()).build();
        assertSame(empty, command.apply(empty));
        var poor = noCash(initial); assertSame(poor, command.apply(poor));
    }

    @Test void depletedGeneratorBuysOnlyRealCompatibleFuelWhenItImprovesFiring() {
        var initial = world(CombatModuleCatalog.LASER, 10000, FleetLocation.Site.docked("port"));
        initial = ShipPowerResupply.replace(initial.toBuilder().powerGrids(List.of()).build(), ship(initial).withPowerState(ShipPowerState.empty()));
        assertEquals(ShipWeaponReadiness.Blocker.POWER, weapon(initial).blocker());
        var paid = command.apply(initial);
        assertEquals(10, ship(paid).generatorFuelMassKg());
        assertEquals(9980, paid.empires().getFirst().treasuryCredits());
        assertEquals(0, paid.commercialHubs().getLast().activeOrders().get("refined_uranium").supplyKg());
        assertEquals(ShipWeaponReadiness.Blocker.NONE, weapon(paid).blocker());
        assertSame(paid, command.apply(paid));
        var missing = initial.toBuilder().commercialHubs(List.of()).build(); assertSame(missing, command.apply(missing));
    }

    @Test void idleFleetSeeksReachableChargingStationAndDryPropulsionCannotTeleport() {
        double upkeep = 2 + CombatModuleCatalog.module(CombatModuleCatalog.LASER).powerDrawKw();
        var initial = world(CombatModuleCatalog.LASER, upkeep, FleetLocation.Site.docked("home"));
        assertEquals(ShipWeaponReadiness.Blocker.POWER, weapon(initial).blocker());
        assertTrue(new MoveFleetLocalCommand("fleet", FleetLocation.Kind.DOCKED, "port").validate(initial),
                () -> "Travel: " + LocalTravel.plan(initial, fleet(initial), FleetLocation.Site.docked("port")));
        var arrivalPlan = FleetReturnTravel.localPlan(initial, fleet(initial), FleetLocation.Site.docked("port"));
        assertNotNull(arrivalPlan);
        var arrival = LocalTravel.arrivalPreview(initial, fleet(initial), FleetLocation.Site.docked("port"), arrivalPlan);
        var projected = FleetReturnTravel.replace(initial, arrival);
        assertEquals(ShipWeaponReadiness.Blocker.POWER, weapon(projected).blocker(), () -> weapon(projected).toString());
        assertTrue(new ChargeShipBatteryCommand("ship", "port", 10).validate(projected), () -> ship(projected).powerState().toString());
        assertTrue(command.validate(projected));
        var departing = command.apply(initial);
        assertTrue(fleet(departing).location().inTransit());
        assertEquals(FleetLocation.Site.docked("port"), fleet(departing).location().destination());
        var burned = LocalFlightProcessor.advance(fleet(departing), arrivalPlan.days() * 24, false);
        assertTrue(burned.ships().getFirst().currentFuelKg() < ship(initial).currentFuelKg());
        assertEquals(initial.powerGrids(), departing.powerGrids());
        assertEquals(initial.empires(), departing.empires());
        assertSame(departing, command.apply(departing));
        var dryShip = new ShipInstance("ship", "design", "ai", 1000, 0, 0, Map.of()).withPowerState(ship(initial).powerState());
        var dry = initial.withFleets(List.of(fleet(initial).withShips(List.of(dryShip))));
        assertFalse(command.validate(dry)); assertSame(dry, command.apply(dry));
        var ready = world(CombatModuleCatalog.LASER, 10000, FleetLocation.Site.docked("home"));
        assertSame(ready, command.apply(ready));
    }

    @Test void disabledHardwareUsesPaidRepairAndRestoresGuardIntent() {
        var initial = world(CombatModuleCatalog.GUN, 10000, FleetLocation.Site.docked("port"));
        var original = ship(initial); var gun = CombatModuleCatalog.module(CombatModuleCatalog.GUN);
        initial = ShipPowerResupply.replace(initial, new ShipInstance(original.id(), original.designId(), original.ownerEntityId(),
                original.currentHullHealth(), original.currentShieldHealth(), original.currentFuelKg(),
                Map.of(gun.ammunitionId(), gun.operationalStats().get("ammunitionMassKg"))).withPowerState(original.powerState()));
        initial = ShipPowerResupply.replace(initial, ship(initial).withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))));
        initial = new GuardObjectCommand("ai", "fleet", FleetGuardOrder.TargetType.STATION, "port").apply(initial);
        var guard = fleet(initial).guardOrder(); assertNotNull(guard);
        var waiting = command.apply(initial);
        assertEquals(FleetProtectionAssignment.Stage.REPAIRING, fleet(waiting).interceptOrder().protection().stage());
        assertEquals(guard, fleet(waiting).interceptOrder().protection().repairIntent().guard());
        assertEquals(initial.empires(), waiting.empires()); assertEquals(ship(initial), ship(waiting));
        var worked = BaseRepairProcessor.advanceDay(waiting);
        assertEquals(50, ship(worked).moduleDamage().damageByMount().get(0));
        assertTrue(worked.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits());
        var finished = FleetProtectionReturn.advance(BaseRepairProcessor.advanceDay(worked));
        assertEquals(ShipModuleDamage.empty(), ship(finished).moduleDamage());
        assertEquals(guard, fleet(finished).guardOrder());
        var poor = noCash(initial); assertEquals(ship(poor), ship(command.apply(poor)));
        assertTrue(FleetWeaponRecovery.active(fleet(command.apply(poor))));
        var missing = initial.toBuilder().commercialHubs(List.of()).build(); assertEquals(ship(missing), ship(command.apply(missing)));
    }

    @Test void queuedServiceRechecksManualOrdersOwnershipAndDepletedSources() {
        var initial = world(CombatModuleCatalog.LASER, 36, FleetLocation.Site.docked("port"));
        for (var changed : List.of(noCash(initial), initial.toBuilder().powerGrids(List.of()).build(),
                initial.withFleets(List.of(fleet(initial).withStance("AGGRESSIVE"))))) {
            var queue = new CommandQueue(); var receipt = queue.submitTracked(command);
            assertSame(changed, queue.drainAndExecute(changed)); assertEquals(CommandOutcome.REJECTED, receipt.join());
        }
        assertFalse(new ServiceAICombatFleetCommand("foreign", "fleet").validate(initial));
        var unavailable = initial.withShipDesigns(List.of()); assertSame(unavailable, command.apply(unavailable));
    }

    @Test void ammunitionPurchasesFollowRetunedCapturedApplicationValues() {
        var initial = world(CombatModuleCatalog.MASS_DRIVER, 10000, FleetLocation.Site.surface("earth"));
        var old = initial.shipDesigns().getFirst(); var module = old.combatModules().get(CombatModuleCatalog.MASS_DRIVER);
        var stats = new HashMap<>(module.operationalStats());
        double mass = stats.get("ammunitionMassKg") * 3; stats.put("ammunitionMassKg", mass);
        var tuned = new ShipModule(module.id(), module.name(), module.slotSize(), module.slotCost(), module.dryMassKg(),
                module.powerDrawKw(), module.powerOutputKw(), module.thrustOutputN(), module.complexityLevel(),
                module.materialCostsKg(), stats, module.ammunitionId());
        var design = new ShipDesign(old.id(), old.name(), old.ownerEntityId(), old.role(), old.hullMaterialId(),
                old.equippedModuleIds(), old.armorMaterialId(), old.armorThicknessCm(), old.totalDryMassKg(), old.maxCargoMassKg(),
                old.fuelCapacityKg(), old.powerBalanceKw(), old.calculatedStructuralIntegrity(), old.minLaunchThrustRequiredN(),
                old.totalThrustN(), old.isValidForLaunch(), old.isProprietaryCorporateDesign(), old.manufacturingProfile(),
                old.powerProfile(), old.sensorProfile(), Map.of(tuned.id(), tuned));
        var hub = new CommercialHub("surface", "earth", 0, 10000, mass, 10,
                Map.of(tuned.ammunitionId(), new MarketOrder(tuned.ammunitionId(), mass, 0, 2, 0)));
        initial = initial.withShipDesigns(List.of(design)).withCommercialHubs(List.of(hub));
        var paid = command.apply(initial);
        assertEquals(mass, ship(paid).storedCargoKg().get(tuned.ammunitionId()));
        assertEquals(10000 - mass * 2, paid.empires().getFirst().treasuryCredits());
        assertEquals(1, weapon(paid).wholeRounds());
    }

    @Test void partialFuelStockIsUsableAndAnUnderpoweredPlantDoesNotWasteFuelPurchases() {
        var initial = world(CombatModuleCatalog.LASER, 10000, FleetLocation.Site.docked("port"));
        initial = ShipPowerResupply.replace(initial.toBuilder().powerGrids(List.of()).build(), ship(initial).withPowerState(ShipPowerState.empty()));
        var limited = initial.withCommercialHubs(List.of(new CommercialHub("repair", "port", 0, 10000, .5, 10,
                Map.of("refined_uranium", new MarketOrder("refined_uranium", .5, 0, 2, 0)))));
        var paid = command.apply(limited);
        assertEquals(.5, ship(paid).generatorFuelMassKg());
        assertEquals(9999, paid.empires().getFirst().treasuryCredits());
        assertEquals(ShipWeaponReadiness.Blocker.NONE, weapon(paid).blocker());
        var weak = world(CombatModuleCatalog.LASER, 1, FleetLocation.Site.docked("port"));
        weak = ShipPowerResupply.replace(weak.toBuilder().powerGrids(List.of()).build(), ship(weak).withPowerState(ShipPowerState.empty()));
        assertSame(weak, command.apply(weak));
    }
}
