package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.ai.EmpireFleetPlanner;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded campaign turns with captured application hardware and finite paid stores. */
class CampaignCombatLifecycleTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final List<String> WEAPONS = List.of(CombatModuleCatalog.LASER, CombatModuleCatalog.PARTICLE_BEAM,
            CombatModuleCatalog.ION, CombatModuleCatalog.MASS_DRIVER);
    private Fleet own(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private Fleet enemy(GameState state) { return FleetOrganization.find(state, "target"); }
    private SpaceConquestEngine engine(GameState state) {
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state); return engine;
    }
    private ShipDesign weaponDesign(ShipDesign original, String id, String weapon) throws Exception {
        var modules = new ArrayList<>(original.equippedModuleIds());
        modules.replaceAll(moduleId -> CombatModuleCatalog.module(moduleId) != null
                && CombatModuleCatalog.module(moduleId).operationalStats().containsKey("weaponDamage") ? weapon : moduleId);
        modules.addAll(Collections.nCopies(3, ShipComponentCatalog.FISSION_REACTOR_ID));
        var hardware = modules.stream().map(ShipComponentCatalog::module).toList();
        ObjectNode json = mapper.valueToTree(original); json.put("id", id); json.put("armorThicknessCm", 0);
        json.set("equippedModuleIds", mapper.valueToTree(modules));
        json.set("combatModules", mapper.valueToTree(CombatModuleCatalog.capture(hardware)));
        json.set("powerProfile", mapper.valueToTree(ShipPowerProfile.capture(hardware)));
        // This combat-only equipment variant has no production receipt from its source template.
        json.putNull("assembly");
        json.put("totalDryMassKg", original.totalDryMassKg() + 3 * ShipComponentCatalog.module(ShipComponentCatalog.FISSION_REACTOR_ID).dryMassKg());
        return mapper.treeToValue(json, ShipDesign.class);
    }
    private GameState opening(int enemies) throws Exception {
        var state = new FleetProtectionTest().world(enemies); var source = own(state).ships().getFirst();
        var template = FleetSupplySimulation.design(state, source);
        var designs = new ArrayList<>(state.shipDesigns()); var ships = new ArrayList<ShipInstance>();
        for (String weapon : WEAPONS) {
            var design = weaponDesign(template, weapon, weapon); designs.add(design);
            var cargo = new HashMap<>(source.storedCargoKg()); cargo.remove("cannon_round");
            var module = CombatModuleCatalog.module(weapon);
            if (!module.ammunitionId().isBlank()) cargo.put(module.ammunitionId(), module.operationalStats().get("ammunitionMassKg"));
            ships.add(new ShipInstance(weapon, design.id(), "owner", ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH, 0, source.currentFuelKg(), cargo)
                    .withPowerState(source.powerState()));
        }
        var stations = new ArrayList<OrbitalStation>();
        for (var station : state.orbitalStations().stream().filter(station -> station.ownerEntityId().equals("owner")).toList()) {
            var modules = new ArrayList<StationModule>(); modules.add(BaseRepairCatalog.module().withPayroll(2, 0));
            modules.add(new StationModule("control", "Fixture control", StationModule.TYPE_CONTROL, 1, 1000, 10, 0, Map.of(), "technician", 0, true));
            modules.add(new StationModule("power", "Fixture power", StationModule.TYPE_POWER, 1, 1000, 0, 10000, Map.of(), "engineer", 0, true));
            ObjectNode json = mapper.valueToTree(station); json.set("modules", mapper.valueToTree(modules));
            json.put("currentPowerGenerationKw", 10000); json.put("currentPowerDemandKw", 50);
            json.set("populations", mapper.valueToTree(List.of(new Population("human", Map.of(25, 2000L)))));
            stations.add(mapper.treeToValue(json, OrbitalStation.class).withParkingAltitudeKm(8000.0));
        }
        var stock = new TreeMap<String, MarketOrder>();
        for (var design : designs) for (String id : design.equippedModuleIds()) {
            var module = ShipModuleDamage.damageableModule(design, id);
            if (module != null) module.materialCostsKg().keySet().forEach(material -> stock.put(material, new MarketOrder(material, 10000, 0, 1, 0)));
        }
        stock.put("fusion_fuel_pellets", new MarketOrder("fusion_fuel_pellets", 1000, 0, 1, 0));
        stock.put("refined_uranium", new MarketOrder("refined_uranium", 100, 0, 1, 0));
        var workers = new HouseholdAccount("owner-yard", "sol", "owner", "human", "engineer", 200, 10000, 0, 0, 0, 0,
                Map.of(), 1, 1, 0, 0, HouseholdWellbeing.healthy(), new HouseholdEmployment(200, 0, 0, 0));
        var foes = enemy(state).ships().stream().map(ship -> new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH, 0, ship.currentFuelKg(), ship.storedCargoKg()).withPowerState(ship.powerState())).toList();
        return state.withShipDesigns(designs).withFleets(List.of(own(state).withShips(ships), enemy(state).withShips(foes).withLocation(own(state).location())))
                .toBuilder().orbitalStations(stations).householdAccounts(List.of(workers))
                .commercialHubs(List.of(new CommercialHub("repair", "owner-yard", 0, 1000000,
                                stock.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, stock),
                        new CommercialHub("ammunition", "earth", 0, 1000000, 20100, 10, Map.of(
                                "mass_driver_slug", new MarketOrder("mass_driver_slug", 100, 0, 2, 0),
                                "rp1_kerosene", new MarketOrder("rp1_kerosene", 10000, 0, 2, 0),
                                "liquid_oxygen", new MarketOrder("liquid_oxygen", 10000, 0, 2, 0)))))
                .industrialFacilities(List.of(new IndustrialFacility("terminal", "earth", "cargo_terminal", "owner",
                        IndustrialFacility.PUBLIC_STATE, 1, 0, "technician", false, 0)))
                .powerGrids(List.of(new PowerGridState("owner-yard", 100, 0, 100, 10000, 10000, false))).build();
    }
    private GameState checkpoint(GameState state, String name) throws Exception {
        var manager = new SaveGameManager(directory); var file = directory.resolve(name + ".scsave").toFile();
        manager.save(file, state, 1, GameClock.START_TIME.plusDays(state.turn()).toString());
        var loaded = manager.load(file).toGameState(state.turn(), "RUNNING");
        assertSameCampaign(state, loaded); return loaded;
    }
    private void assertSameCampaign(GameState expected, GameState actual) {
        assertEquals(expected.turn(), actual.turn()); assertEquals(expected.fleets(), actual.fleets());
        assertEquals(expected.shipDesigns(), actual.shipDesigns()); assertEquals(expected.commercialHubs(), actual.commercialHubs());
        assertEquals(expected.empires(), actual.empires()); assertEquals(expected.powerGrids(), actual.powerGrids());
        assertEquals(expected.orbitalStations(), actual.orbitalStations()); assertEquals(expected.marketAccounts(), actual.marketAccounts());
        assertEquals(expected.householdAccounts(), actual.householdAccounts()); assertEquals(expected.industryAccounts(), actual.industryAccounts());
        assertEquals(expected.industrialFacilities(), actual.industrialFacilities());
        assertEquals(expected.launchUsageKg(), actual.launchUsageKg()); assertEquals(expected.launchActivities(), actual.launchActivities());
        assertEquals(expected.fleetEngagements(), actual.fleetEngagements()); assertEquals(expected.fleetServiceEvents(), actual.fleetServiceEvents());
    }
    private double damage(GameState state) {
        return own(state).ships().stream().flatMap(ship -> ship.moduleDamage().damageByMount().values().stream()).mapToDouble(Double::doubleValue).sum();
    }
    private double fuel(GameState state) { return own(state).ships().stream().mapToDouble(ShipInstance::currentFuelKg).sum(); }
    private ShipInstance kinetic(GameState state) {
        return own(state).ships().stream().filter(ship -> ship.id().equals(CombatModuleCatalog.MASS_DRIVER)).findFirst().orElseThrow();
    }
    private GameState serviceTick(SpaceConquestEngine engine) {
        var before = engine.getGameState(); var queue = new CommandQueue();
        var empire = before.empires().stream().filter(item -> item.id().equals("owner")).findFirst().orElseThrow();
        EmpireFleetPlanner.plan(before, empire).stream().filter(command -> command instanceof ServiceAICombatFleetCommand).forEach(queue::submit);
        queue.processCommands(engine); engine.stepTurn(); return engine.getGameState();
    }
    @Test void normalCampaignCombatPaysForWithdrawalRepairAndResupplyBeforeRestoringTheSavedGuardAcrossDiskReloads() throws Exception {
        var initial = opening(10); var guard = own(initial).guardOrder(); var engine = engine(initial);
        assertEquals(FleetThreatAssessment.Action.HOLD, FleetThreatAssessment.assess(initial, own(initial)).action());
        engine.stepTurn(); var fought = engine.getGameState();
        assertEquals(1, fought.fleetEngagements().size()); assertEquals(1, fought.fleetEngagements().getFirst().rounds());
        assertEquals(4, own(fought).ships().size()); assertTrue(damage(fought) > 0);
        assertTrue(own(fought).interceptOrder().protection().escaped()); assertTrue(own(fought).location().inTransit());
        assertEquals(guard, own(fought).interceptOrder().protection().guard());
        assertEquals(0, kinetic(fought).storedCargoKg().getOrDefault("mass_driver_slug", 0.0));
        var repeat = engine(initial); repeat.stepTurn(); assertSameCampaign(fought, repeat.getGameState());
        engine = engine(checkpoint(fought, "withdrawal"));
        var queue = new CommandQueue(); queue.submit(new EndWarCommand("owner", "enemy")); queue.processCommands(engine);
        var state = engine.getGameState(); SpaceConquestEngine uninterrupted = null; boolean partial = false;
        for (int day = 0; day < 35 && !(own(state).guardOrder() != null && !FleetWeaponRecovery.needs(state, own(state))); day++) {
            state = serviceTick(engine);
            assertEquals(fought.fleetEngagements(), state.fleetEngagements(), "Recovery cannot replay combat");
            if (uninterrupted != null) assertSameCampaign(state, serviceTick(uninterrupted));
            if (!partial && damage(state) > 0 && damage(state) < damage(fought)) {
                assertNull(own(state).guardOrder()); assertTrue(fuel(state) < fuel(initial));
                uninterrupted = engine(state); state = checkpoint(state, "partial-repair"); engine = engine(state); partial = true;
            }
            if (FleetWeaponRecovery.needs(state, own(state))) assertNull(own(state).guardOrder(), "Unready weapons retain suspended mission");
        }
        assertTrue(partial, "A real partial paid repair must be saved");
        var completed = state;
        assertEquals(guard, own(state).guardOrder(), "Recovery order: " + own(state).interceptOrder()
                + "; readiness: " + own(state).ships().stream().map(ship -> ShipWeaponReadiness.inspect(FleetSupplySimulation.design(completed, ship), ship, true)).toList());
        assertNull(own(state).interceptOrder()); assertEquals(0, damage(state));
        assertFalse(FleetWeaponRecovery.needs(state, own(state))); assertTrue(fuel(state) < fuel(initial));
        assertEquals(4, own(state).ships().size()); assertRepairDebits(fought, state);
        var receipt = state.fleetServiceEvents().stream().filter(event -> event.kind() == FleetServiceEvent.Kind.SUPPLIES_PURCHASED).findFirst().orElseThrow();
        double round = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).operationalStats().get("ammunitionMassKg");
        assertEquals(round, receipt.resourcesKg().get("mass_driver_slug")); assertTrue(receipt.netCreditsSpent() > round * 2, "Actual rocket lift is paid too");
        var surface = state.commercialHubs().stream().filter(hub -> hub.entityId().equals("earth")).findFirst().orElseThrow();
        assertEquals(100 - round, surface.activeOrders().get("mass_driver_slug").supplyKg());
        assertTrue(surface.activeOrders().get("rp1_kerosene").supplyKg() < 10000);
        assertTrue(surface.activeOrders().get("liquid_oxygen").supplyKg() < 10000);
        assertEquals(FleetServiceEvent.Kind.MISSION_RESUMED, state.fleetServiceEvents().getLast().kind());
        checkpoint(state, "restored-guard");
        Files.writeString(Path.of("target", "campaign-combat-lifecycle.md"), "# Campaign combat lifecycle\n\n"
                + "Seed 42. Four captured weapon families survive one combat round and funded withdrawal.\n\n"
                + "Guard restored on turn " + state.turn() + "; partial repair and withdrawal survived disk reloads.\n\n"
                + "Actual purchased slug mass: " + round + " kg. Paid propulsion: " + (fuel(initial) - fuel(state)) + " kg.\n");
    }
    private void assertRepairDebits(GameState fought, GameState restored) {
        var spent = new TreeMap<String, Double>();
        for (var before : own(fought).ships()) {
            var after = own(restored).ships().stream().filter(ship -> ship.id().equals(before.id())).findFirst().orElseThrow();
            var design = FleetSupplySimulation.design(fought, before);
            spent.merge(design.hullMaterialId(), after.currentHullHealth() - before.currentHullHealth(), Double::sum);
            before.moduleDamage().damageByMount().forEach((mount, points) -> ShipModuleDamage.damageableModule(design,
                    design.equippedModuleIds().get(mount)).materialCostsKg().forEach((id, kg) -> spent.merge(id, kg * points / ShipModuleDamage.DURABILITY, Double::sum)));
        }
        spent.forEach((id, kg) -> assertEquals(fought.commercialHubs().getFirst().activeOrders().get(id).supplyKg() - kg,
                restored.commercialHubs().getFirst().activeOrders().get(id).supplyKg(), 1e-6, id));
        assertTrue(restored.orbitalStations().getFirst().modules().stream().filter(module -> module.type().equals(StationModule.TYPE_REPAIR_WORKSHOP))
                .allMatch(module -> module.paidWorkers() == 2 && module.dailyWageCostsCredits() > 0));
    }
    @Test void normalCampaignKeepsTheSavedGuardSuspendedWhenAmmunitionStockIsMissing() throws Exception {
        var initial = opening(10);
        var hubs = initial.commercialHubs().stream().map(hub -> {
            if (!hub.entityId().equals("earth")) return hub;
            var orders = new HashMap<>(hub.activeOrders());
            orders.remove("mass_driver_slug");
            return hub.withInventory(initial.turn(), hub.currentStoredWeightKg() - 100, orders);
        }).toList();
        var engine = engine(initial.toBuilder().commercialHubs(hubs).build());
        engine.stepTurn(); var fought = engine.getGameState();
        var queue = new CommandQueue(); queue.submit(new EndWarCommand("owner", "enemy")); queue.processCommands(engine);
        var state = engine.getGameState();
        for (int day = 0; day < 35; day++) {
            state = serviceTick(engine);
            assertNull(own(state).guardOrder(), "Missing rounds cannot restore the saved mission");
            assertEquals(fought.fleetEngagements(), state.fleetEngagements());
            assertEquals(0, kinetic(state).storedCargoKg().getOrDefault("mass_driver_slug", 0.0));
        }
        assertEquals(0, damage(state), "Paid electronics repair can finish independently of ammunition");
        assertTrue(FleetWeaponRecovery.needs(state, own(state)));
        assertEquals(own(initial).guardOrder(), own(state).interceptOrder().protection().guard());
        assertTrue(state.fleetServiceEvents().stream().noneMatch(event -> event.kind() == FleetServiceEvent.Kind.MISSION_RESUMED));
        checkpoint(state, "blocked-ammunition");
    }
    private GameState targetingOpening() throws Exception {
        var state = opening(2); var ownTemplate = FleetSupplySimulation.design(state, own(state).ships().getFirst());
        var foeTemplate = FleetSupplySimulation.design(state, enemy(state).ships().getFirst());
        var ion = weaponDesign(ownTemplate, "ion-team", CombatModuleCatalog.ION);
        var beam = weaponDesign(foeTemplate, "dangerous-beam", CombatModuleCatalog.PARTICLE_BEAM);
        var empty = weaponDesign(foeTemplate, "empty-kinetic", CombatModuleCatalog.MASS_DRIVER);
        var ships = own(state).ships().stream().map(ship -> new ShipInstance(ship.id(), ion.id(), "owner", 1000, 0,
                ship.currentFuelKg(), Map.of("fusion_fuel_pellets", 200.0)).withPowerState(ship.powerState())).toList();
        var power = enemy(state).ships().getFirst().powerState();
        var foes = List.of(new ShipInstance("empty-kinetic", empty.id(), "enemy", 1000, 0, 0, Map.of()).withPowerState(power),
                new ShipInstance("dangerous-beam", beam.id(), "enemy", 1000, 0, 0, Map.of()).withPowerState(power));
        return state.withShipDesigns(List.of(ion, beam, empty)).withFleets(List.of(own(state).withShips(ships), enemy(state).withShips(foes)))
                .toBuilder().orbitalStations(List.of()).commercialHubs(List.of()).powerGrids(List.of()).householdAccounts(List.of())
                .industrialFacilities(List.of()).build();
    }
    private double offense(GameState state, ShipInstance ship) {
        return ShipCombatPower.fundedFirepower(ship, FleetSupplySimulation.design(state, ship),
                TacticalCombatProcessor.SECONDS_PER_ROUND / 3600, true);
    }
    @Test void normalCampaignIonTargetingDisablesFundedEnemyOffenseAndReducesIncomingDamage() throws Exception {
        var initial = targetingOpening(); var dangerous = enemy(initial).ships().getLast();
        double openingFire = offense(initial, dangerous);
        assertTrue(openingFire > 0); assertEquals(0, offense(initial, enemy(initial).ships().getFirst()));
        var engine = engine(initial); engine.stepTurn(); var fought = engine.getGameState();
        var record = fought.fleetEngagements().getFirst(); assertTrue(record.rounds() > 1);
        assertFalse(record.targetingDecisions().isEmpty());
        assertTrue(record.targetingDecisions().stream().anyMatch(decision -> decision.explanation().contains("funded firepower")));
        assertEquals(2, enemy(fought).ships().size());
        assertTrue(enemy(fought).ships().stream().allMatch(ship -> ship.currentHullHealth() == 1000), "Ion cannot damage hull");
        assertTrue(enemy(fought).ships().stream().allMatch(ship -> offense(fought, ship) == 0));
        double actualLoss = 4000 - own(fought).ships().stream().mapToDouble(ShipInstance::currentHullHealth).sum();
        assertTrue(actualLoss > 0, "The funded enemy opening salvo remains simultaneous");
        assertTrue(actualLoss < openingFire * (record.rounds() - 1), "Disabling dangerous electronics removes later incoming salvos");
        var repeat = engine(initial); repeat.stepTurn(); assertSameCampaign(fought, repeat.getGameState());
        checkpoint(fought, "disabled-enemy");
        ObjectNode legacy = (ObjectNode) mapper.readTree(directory.resolve("disabled-enemy.scsave").toFile());
        legacy.put("version", com.spaceconquest.engine.SaveGame.CURRENT_VERSION);
        legacy.get("fleetEngagements").forEach(event -> ((ObjectNode) event).remove("targetingDecisions"));
        var legacyFile = directory.resolve("legacy-targeting.scsave").toFile(); mapper.writeValue(legacyFile, legacy);
        var old = new SaveGameManager(directory).load(legacyFile).toGameState(fought.turn(), "RUNNING");
        assertTrue(old.fleetEngagements().getFirst().targetingDecisions().isEmpty());
        assertEquals(record.rounds(), old.fleetEngagements().getFirst().rounds());
        Files.writeString(Path.of("target", "campaign-targeting.md"), "# Campaign targeting\n\nSeed 42. " + record.rounds()
                + " rounds. Initial funded enemy salvo: " + openingFire + "; actual incoming hull damage: " + actualLoss
                + ". Both enemy hulls remain intact with no funded firing capability.\n");
    }
}
