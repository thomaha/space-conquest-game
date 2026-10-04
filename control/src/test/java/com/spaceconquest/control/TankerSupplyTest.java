package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TankerSupplyTest {
    @TempDir Path directory;
    private GameState state() {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("a"), List.of(), Map.of(),
                List.of("rocketry", "electricity", "nuclear_fission", "industrial_production"), List.of());
        var supplyModules = List.of("mod_chemical_rocket", ShipSupplyCatalog.LIQUID_TANK, ShipSupplyCatalog.CRYOGENIC_TANK,
                ShipSupplyCatalog.TRANSFER_PUMP, ShipComponentCatalog.FISSION_REACTOR_ID, ShipComponentCatalog.REACTOR_TANK_ID);
        var p = ShipPowerProfile.capture(supplyModules.stream().map(ShipComponentCatalog::module).toList());
        var donorDesign = new ShipDesign("tanker", "Tanker", "owner", ShipRole.CARGO_TRANSPORT, "steel", supplyModules,
                "steel", 0, 50000, 0, 15000, 400, 1, 0, 600000, false, false, ShipManufacturingProfile.baseline(), p);
        var receiverModules = List.of("mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID, ShipComponentCatalog.GENERATOR_TANK_ID);
        var rp = ShipPowerProfile.capture(receiverModules.stream().map(ShipComponentCatalog::module).toList());
        var rd = new ShipDesign("receiverDesign", "Receiver", "owner", ShipRole.EXPLORER, "steel", receiverModules,
                "steel", 0, 20000, 0, 15000, 400, 1, 0, 600000, false, false, ShipManufacturingProfile.baseline(), rp);
        var donor = new ShipInstance("supplier", "tanker", "owner", 100, 0, 15000, Map.of())
                .withPowerState(power(Map.of("refined_uranium", .1)))
                .withSupplyState(new ShipSupplyState(Map.of("rp1_kerosene", 560.0, "liquid_oxygen", 1440.0), null, ""));
        var receiver = new ShipInstance("receiver", "receiverDesign", "owner", 100, 0, 15000, Map.of())
                .withPowerState(power(Map.of("rp1_kerosene", 140.0, "liquid_oxygen", 360.0)));
        return GameState.builder().empires(List.of(owner)).shipDesigns(List.of(donorDesign, rd)).solarSystems(List.of(system("a", 0),
                system("b", 1e9 / InterstellarTravel.METERS_PER_LIGHT_YEAR))).fleets(List.of(new Fleet("fleet", "Supply fleet", "owner", "a", "",
                0, 0, 0, false, "PASSIVE", List.of(donor, receiver)))).build();
    }
    private ShipPowerState power(Map<String, Double> materials) {
        return new ShipPowerState(materials, "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0);
    }
    private SolarSystem system(String id, double x) { return new SolarSystem(id, id, "", x, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of()); }
    private ShipInstance supplier(GameState state) { return state.fleets().getFirst().ships().getFirst(); }
    private ShipInstance receiver(GameState state) { return state.fleets().getFirst().ships().getLast(); }
    private GameState schedule(GameState state, double kg, double delay) {
        var command = new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", kg, delay);
        assertTrue(command.validate(state));
        return command.apply(state);
    }
    private GameState propellant(GameState state, double kg, double delay) {
        var design = state.shipDesigns().getLast();
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        var command = new ScheduleFleetSupplyCommand("supplier", "receiver", drive.moduleId(), kg, delay,
                ShipSupplyTransfer.Destination.PROPELLANT);
        assertTrue(command.validate(state));
        return command.apply(state);
    }
    private GameState tick(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
    }

    private GameState nuclear(String driveId, String feed, double feedKg) {
        var initial = state();
        var d = initial.shipDesigns().getLast();
        var modules = List.of(driveId, ShipComponentCatalog.CHEMICAL_GENERATOR_ID, ShipComponentCatalog.GENERATOR_TANK_ID);
        var design = new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(), modules,
                d.armorMaterialId(), d.armorThicknessCm(), d.totalDryMassKg(), 1000, d.fuelCapacityKg(), d.powerBalanceKw(),
                d.calculatedStructuralIntegrity(), 0, d.totalThrustN(), false, false, d.manufacturingProfile(),
                ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList()));
        initial = initial.withShipDesigns(List.of(initial.shipDesigns().getFirst(), design));
        var ship = receiver(initial);
        return ShipPowerResupply.replace(initial, new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0,
                ship.currentFuelKg(), feed == null ? Map.of() : Map.of(feed, feedKg))
                .withPowerState(power(Map.of("rp1_kerosene", 560.0, "liquid_oxygen", 1440.0))));
    }

    @Test void nuclearDriveDeliveriesUseTheSelectedExhaustAfterFeedCommitment() {
        for (var feed : List.of("refined_uranium", "refined_thorium", "hydrogen_gas", "deuterium_gas", "fusion_fuel_pellets")) {
            boolean fission = feed.startsWith("refined_");
            String drive = fission ? "mod_fission_thruster" : "mod_fusion_drive";
            double kg = switch (feed) {
                case "refined_uranium" -> 15;
                case "refined_thorium" -> 18;
                case "deuterium_gas" -> 300;
                case "fusion_fuel_pellets" -> 150;
                default -> 0;
            };
            var scheduled = schedule(nuclear(drive, kg == 0 ? null : feed, kg), 1, 25);
            var move = new MoveFleetCommand("fleet", "b");
            var preview = move.preview(scheduled);
            assertTrue(preview.ready(), feed + ": " + preview.electrical());
            var propulsion = preview.crossing().propulsion().get("receiver");
            assertEquals(feed, propulsion.reactorFeedId());
            assertEquals(PropulsionCatalog.drive(drive).exhaustVelocityMps()
                    * PropulsionCatalog.reactorFuel(drive, feed).exhaustMultiplier(), propulsion.exhaustVelocityMps());
            var current = move.apply(scheduled);
            assertEquals(propulsion, current.fleets().getFirst().journeyPropulsion().get("receiver"));
            assertEquals(kg - propulsion.committedReactorKg(), receiver(current).storedCargoKg().getOrDefault(feed, 0.0), 1e-6);
            for (int day = 0; day < 60 && current.fleets().getFirst().hasInterstellarOrder(); day++) current = tick(current);
            assertEquals("b", current.fleets().getFirst().currentSystemId(), feed);
            assertTrue(supplier(current).supplyState().outcome().startsWith("Delivered"), feed);
            assertTrue(current.fleets().getFirst().journeyPropulsion().isEmpty());
        }
    }

    @Test void frozenPropulsionSurvivesSaveCopiesSplitAndMerge() throws Exception {
        var current = new MoveFleetCommand("fleet", "b").apply(schedule(nuclear("mod_fusion_drive", "deuterium_gas", 300), 1, 25));
        var original = current.fleets().getFirst().journeyPropulsion();
        assertEquals(600000, original.get("receiver").exhaustVelocityMps());
        current = new SetFleetStanceCommand("fleet", "AGGRESSIVE").apply(tick(current));
        assertEquals(original, current.fleets().getFirst().journeyPropulsion());
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("nuclear.scsave").toFile();
        manager.save(file, current, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(current.fleets(), loaded.fleets());
        assertEquals(tick(current).fleets(), tick(loaded).fleets());
        assertTrue(supplier(tick(loaded)).supplyState().outcome().startsWith("Delivered"));
        var split = new SplitFleetCommand("owner", "fleet", List.of("receiver"), "split", "Receiver").apply(current);
        assertEquals(Map.of("supplier", original.get("supplier")), split.fleets().getFirst().journeyPropulsion());
        assertEquals(Map.of("receiver", original.get("receiver")), split.fleets().getLast().journeyPropulsion());
        var merge = new MergeFleetsCommand("owner", "split", "fleet");
        assertTrue(merge.validate(split));
        assertEquals(original, merge.apply(split).fleets().getFirst().journeyPropulsion());
        assertEquals(original, current.fleets().getFirst().withInterruptedTravel().journeyPropulsion());
    }

    @Test void missingLegacyNuclearPropulsionFailsDeliveryWithoutGuessingExhaust() {
        var current = new MoveFleetCommand("fleet", "b").apply(schedule(nuclear("mod_fusion_drive", "deuterium_gas", 300), 1, 0));
        current = current.withFleets(List.of(current.fleets().getFirst().withJourneyPropulsion(Map.of())));
        var next = tick(current);
        assertEquals(2000, supplier(next).supplyFuelMassKg());
        assertTrue(supplier(next).supplyState().outcome().contains("failed"));
        assertNull(supplier(next).supplyState().order());
    }

    @Test void olderSavesLoadWithoutInventingMissingPropulsion() throws Exception {
        var current = new MoveFleetCommand("fleet", "b").apply(schedule(nuclear("mod_fusion_drive", "deuterium_gas", 300), 1, 0));
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("older.scsave").toFile();
        manager.save(file, current, 1, "2027-01-01T08:00:00");
        var mapper = new ObjectMapper();
        var json = (ObjectNode) mapper.readTree(file);
        json.put("version", 33);
        ((ObjectNode) json.get("fleets").get(0)).remove("journeyPropulsion");
        ((ObjectNode) json.get("fleets").get(0).get("ships").get(0).get("supplyState").get("order")).remove("destination");
        mapper.writeValue(file, json);
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertTrue(loaded.fleets().getFirst().journeyPropulsion().isEmpty());
        assertEquals(receiver(current).currentFuelKg(), receiver(loaded).currentFuelKg());
        assertEquals(supplier(current).supplyState(), supplier(loaded).supplyState());
        assertTrue(supplier(tick(loaded)).supplyState().outcome().contains("failed"));
    }

    @Test void replacementRecoveryCapturesItsNewFeedAndInterruptionKeepsIt() {
        var initial = nuclear("mod_fusion_drive", "deuterium_gas", 300);
        var current = new MoveFleetCommand("fleet", "b").apply(initial);
        assertEquals(600000, current.fleets().getFirst().journeyPropulsion().get("receiver").exhaustVelocityMps());
        var powerless = ShipPowerResupply.replace(current, receiver(current).withPowerState(power(Map.of())));
        var stopped = tick(powerless);
        var plan = FlightRecovery.plan(stopped, stopped.fleets().getFirst());
        assertNotNull(plan);
        assertEquals("hydrogen_gas", plan.propulsion().get("receiver").reactorFeedId());
        assertEquals(150000, plan.propulsion().get("receiver").exhaustVelocityMps());
        var recovering = FlightRecovery.depart(stopped.fleets().getFirst(), plan);
        assertEquals(plan.propulsion(), recovering.journeyPropulsion());
        assertEquals(plan.propulsion(), FlightRecovery.advance(recovering, 0).journeyPropulsion());
    }

    @Test void nuclearElectricalDeliveryReplansItsPreviouslyInsufficientFixedBrakingBudget() {
        var scheduled = schedule(nuclear("mod_fusion_drive", "deuterium_gas", 300), 1000, 0);
        assertTrue(new MoveFleetCommand("fleet", "b").validate(scheduled));
        var delivered = tick(new MoveFleetCommand("fleet", "b").apply(scheduled));
        assertTrue(supplier(delivered).supplyState().outcome().contains("Replanned"));
        assertEquals(2000, supplier(scheduled).supplyFuelMassKg());
    }

    @Test void propulsionRefillReplansAtContactAndConservesAllMainFuel() {
        var initial = nuclear("mod_chemical_rocket", null, 0);
        var scheduled = propellant(initial, 1000, 0);
        var move = new MoveFleetCommand("fleet", "b");
        var preview = move.preview(scheduled);
        assertTrue(preview.ready(), preview.electrical().toString());
        var departed = move.apply(scheduled);
        var oldBudget = departed.fleets().getFirst().interstellarFuelBudgetKg();
        var delivered = tick(departed);
        var replacement = delivered.fleets().getFirst();
        assertEquals(Fleet.MODE_RECOVERY, replacement.interstellarMode());
        assertNull(supplier(delivered).supplyState().order());
        assertTrue(supplier(delivered).supplyState().outcome().contains("Replanned"));
        assertEquals(1000, supplier(delivered).supplyFuelMassKg(), 1e-6);
        assertEquals(15000 - oldBudget.get("receiver") / 2 + 1000, receiver(delivered).currentFuelKg(), 1e-6);
        assertEquals(15000 - oldBudget.get("supplier") / 2, supplier(delivered).currentFuelKg(), 1e-6);
        assertEquals(0, replacement.flightMotion().trajectory().accelerationSeconds());
        assertEquals(departed.fleets().getFirst().interstellarPeakSpeedMps(), replacement.flightMotion().velocityMps(), 1e-6);
        var newBudget = replacement.interstellarFuelBudgetKg();
        var current = delivered;
        int days = 1;
        while (current.fleets().getFirst().hasInterstellarOrder() && days < 60) { current = tick(current); days++; }
        assertEquals("b", current.fleets().getFirst().currentSystemId());
        assertEquals(preview.totalDays(), days);
        assertEquals(15000 - oldBudget.get("receiver") / 2 + 1000 - newBudget.get("receiver"), receiver(current).currentFuelKg(), 1e-5);
        assertEquals(15000 - oldBudget.get("supplier") / 2 - newBudget.get("supplier"), supplier(current).currentFuelKg(), 1e-5);
        assertEquals(0, receiver(current).powerState().unmetEssentialHours());
        assertEquals(preview.electrical().getLast().generatorFuelUsedKg(),
                receiver(initial).generatorFuelMassKg() - receiver(current).generatorFuelMassKg(), 1e-5);
    }

    @Test void pendingAndDeliveredPropulsionOrdersContinueExactlyAfterSaveLoad() throws Exception {
        var current = new MoveFleetCommand("fleet", "b").apply(propellant(nuclear("mod_chemical_rocket", null, 0), 1000, 25));
        current = tick(current);
        assertNotNull(supplier(current).supplyState().order());
        assertEquals(Fleet.MODE_RECOVERY, current.fleets().getFirst().interstellarMode());
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("propulsion.scsave").toFile();
        manager.save(file, current, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(current.fleets(), loaded.fleets());
        assertEquals(tick(current).fleets(), tick(loaded).fleets());
        var delivered = tick(loaded);
        assertNull(supplier(delivered).supplyState().order());
        manager.save(file, delivered, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(tick(delivered).fleets(), tick(restored).fleets());
        assertEquals(1000, supplier(tick(restored)).supplyFuelMassKg(), 1e-5);
    }

    @Test void failedRefillLeavesStocksAndWorkingFuelUnchangedAtContact() {
        var initial = nuclear("mod_chemical_rocket", null, 0);
        var departed = new MoveFleetCommand("fleet", "b").apply(propellant(initial, 1000, 0));
        var budget = departed.fleets().getFirst().interstellarFuelBudgetKg();
        var missing = supplier(departed).withSupplyState(supplier(departed).supplyState()
                .withMaterials(Map.of("rp1_kerosene", 560.0)));
        var next = tick(ShipPowerResupply.replace(departed, missing));
        assertEquals(560, supplier(next).supplyFuelMassKg());
        assertEquals(15000 - budget.get("receiver") / 2, receiver(next).currentFuelKg(), 1e-6);
        assertTrue(supplier(next).supplyState().outcome().contains("failed"));
        assertNull(supplier(next).supplyState().order());
        var stopped = tick(ShipPowerResupply.replace(departed, receiver(departed).withPowerState(power(Map.of()))));
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, stopped.fleets().getFirst().interstellarMode());
        assertEquals(2000, supplier(stopped).supplyFuelMassKg());
        assertEquals(15000, receiver(stopped).currentFuelKg());
        assertNull(supplier(stopped).supplyState().order());
    }

    @Test void propulsionScheduleRejectsConflictingOrdersCapacityTimingAndMissingMixtureIngredients() {
        var initial = nuclear("mod_chemical_rocket", null, 0);
        var scheduled = propellant(initial, 1000, 0);
        assertSame(scheduled, new ScheduleFleetSupplyCommand("receiver", "supplier", "rp1", 1, 0).apply(scheduled));
        var electrical = schedule(initial, 1, 0);
        assertSame(electrical, new ScheduleFleetSupplyCommand("supplier", "receiver", "mod_chemical_rocket", 1, 0,
                ShipSupplyTransfer.Destination.PROPELLANT).apply(electrical));
        assertFalse(new MoveFleetCommand("fleet", "b").validate(propellant(initial, 1000, 1000)));
        var overfill = ShipPowerResupply.replace(initial, supplier(initial).withSupplyState(new ShipSupplyState(
                Map.of("rp1_kerosene", 2800.0, "liquid_oxygen", 7200.0), null, "")));
        assertFalse(new MoveFleetCommand("fleet", "b").validate(propellant(overfill, 10000, 0)));
        assertSame(initial, new ScheduleFleetSupplyCommand("supplier", "receiver", "mod_chemical_rocket", 1, 0,
                ShipSupplyTransfer.Destination.DRIVE_REACTOR).apply(initial));
    }

    @Test void cancellingPendingPropulsionDoesNotChangePaidMotionOrConsumeSupplies() {
        var departed = new MoveFleetCommand("fleet", "b").apply(propellant(nuclear("mod_chemical_rocket", null, 0), 1000, 25));
        var current = tick(departed);
        var cancelled = new CancelFleetSupplyCommand("supplier").apply(current);
        assertEquals(current.fleets().getFirst().flightMotion(), cancelled.fleets().getFirst().flightMotion());
        assertEquals(current.fleets().getFirst().interstellarFuelBudgetKg(), cancelled.fleets().getFirst().interstellarFuelBudgetKg());
        assertNull(supplier(cancelled).supplyState().order());
        assertEquals(2000, supplier(tick(cancelled)).supplyFuelMassKg());
    }

    @Test void nuclearPropellantRefillRetainsFeedAndCommitsOnlyAdditionalCarriedFeed() {
        for (var drive : List.of("mod_fission_thruster", "mod_fusion_drive")) {
            String feed = drive.equals("mod_fission_thruster") ? "refined_uranium" : "deuterium_gas";
            var initial = nuclear(drive, feed, feed.equals("refined_uranium") ? 15 : 300);
            initial = ShipPowerResupply.replace(initial, supplier(initial).withSupplyState(new ShipSupplyState(
                    Map.of("rp1_kerosene", 560.0, "liquid_oxygen", 1440.0, "hydrogen_gas", 1000.0), null, "")));
            double kg = drive.equals("mod_fission_thruster") ? 1000 : 20;
            var scheduled = propellant(initial, kg, 0);
            var move = new MoveFleetCommand("fleet", "b");
            var preview = move.preview(scheduled);
            assertTrue(preview.ready(), drive + preview.electrical());
            var departed = move.apply(scheduled);
            var original = departed.fleets().getFirst().journeyPropulsion().get("receiver");
            double cargo = receiver(departed).storedCargoKg().get(feed);
            var delivered = tick(departed);
            var saved = delivered.fleets().getFirst().journeyPropulsion().get("receiver");
            assertEquals(original.exhaustVelocityMps(), saved.exhaustVelocityMps());
            assertEquals(feed, saved.reactorFeedId());
            double extra = Math.max(0, saved.committedReactorKg() - original.committedReactorKg() / 2);
            assertEquals(cargo - extra, receiver(delivered).storedCargoKg().get(feed), 1e-6);
            assertEquals(1000 - kg, supplier(delivered).supplyState().materialsKg().getOrDefault("hydrogen_gas", 0.0), 1e-6);
            assertTrue(supplier(delivered).supplyState().outcome().contains("Replanned"));
        }
    }

    @Test void insufficientNuclearFeedRejectsRefillAtomically() {
        var initial = nuclear("mod_fusion_drive", "deuterium_gas", 300);
        initial = ShipPowerResupply.replace(initial, supplier(initial).withSupplyState(new ShipSupplyState(
                Map.of("rp1_kerosene", 560.0, "liquid_oxygen", 1440.0, "hydrogen_gas", 1000.0), null, "")));
        var departed = new MoveFleetCommand("fleet", "b").apply(propellant(initial, 20, 0));
        var r = receiver(departed);
        var noFeed = new ShipInstance(r.id(), r.designId(), r.ownerEntityId(), 100, 0, r.currentFuelKg(),
                Map.of("steel", r.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum()))
                .withPowerState(r.powerState());
        var next = tick(ShipPowerResupply.replace(departed, noFeed));
        assertEquals(1000, supplier(next).supplyState().materialsKg().get("hydrogen_gas"));
        assertTrue(supplier(next).supplyState().outcome().contains("failed"));
        assertEquals(departed.fleets().getFirst().journeyPropulsion().get("receiver"),
                next.fleets().getFirst().journeyPropulsion().get("receiver"));
    }

    @Test void eachChemicalMixtureUsesItsRealIngredientsForPropulsionDelivery() {
        for (var driveId : List.of("mod_chemical_rocket", "mod_methalox_rocket", "mod_hydrolox_rocket")) {
            var initial = nuclear(driveId, null, 0);
            var materials = new java.util.HashMap<>(supplier(initial).supplyState().materialsKg());
            PropulsionCatalog.drive(driveId).propellantMaterials(1000).forEach((id, kg) -> materials.merge(id, kg, Double::sum));
            initial = ShipPowerResupply.replace(initial, supplier(initial).withSupplyState(new ShipSupplyState(materials, null, "")));
            var move = new MoveFleetCommand("fleet", "b");
            var scheduled = propellant(initial, 1000, 0);
            assertTrue(move.validate(scheduled), driveId);
            var next = tick(move.apply(scheduled));
            assertTrue(supplier(next).supplyState().outcome().contains("Replanned"), driveId);
            var expected = new java.util.HashMap<>(supplier(initial).supplyState().materialsKg());
            PropulsionCatalog.drive(driveId).propellantMaterials(1000).forEach((id, kg) -> expected.merge(id, -kg, Double::sum));
            expected.forEach((id, kg) ->
                    assertEquals(kg, supplier(next).supplyState().materialsKg().getOrDefault(id, 0.0), 1e-6));
        }
    }

    @Test void mpdRefillCompletesWithNuclearElectricityAndPreviewMatchesArrival() {
        var initial = nuclear("mod_ion_drive", null, 0);
        var d = initial.shipDesigns().getLast();
        var modules = List.of("mod_ion_drive", ShipComponentCatalog.FISSION_REACTOR_ID, ShipComponentCatalog.REACTOR_TANK_ID);
        var design = new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(), modules, d.armorMaterialId(),
                0, d.totalDryMassKg(), d.maxCargoMassKg(), d.fuelCapacityKg(), d.powerBalanceKw(), 1, 0, d.totalThrustN(),
                false, false, d.manufacturingProfile(), ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList()));
        initial = initial.withShipDesigns(List.of(initial.shipDesigns().getFirst(), design))
                .withSolarSystems(List.of(system("a", 0), system("b", 2e9 / InterstellarTravel.METERS_PER_LIGHT_YEAR)));
        initial = ShipPowerResupply.replace(initial, receiver(initial).withPowerState(power(Map.of("refined_uranium", .1))));
        initial = ShipPowerResupply.replace(initial, supplier(initial).withSupplyState(new ShipSupplyState(
                Map.of("methane_ice", 1000.0), null, "")));
        var scheduled = propellant(initial, 100, 0);
        var move = new MoveFleetCommand("fleet", "b");
        var preview = move.preview(scheduled);
        assertTrue(preview.ready(), preview.electrical().toString());
        var current = move.apply(scheduled);
        int days = 0;
        while (current.fleets().getFirst().hasInterstellarOrder() && days < 160) { current = tick(current); days++; }
        assertEquals("b", current.fleets().getFirst().currentSystemId());
        assertEquals(preview.totalDays(), days);
        assertEquals(900, supplier(current).supplyFuelMassKg(), 1e-6);
        assertTrue(supplier(current).supplyState().outcome().contains("Replanned"));
        assertEquals(0, receiver(current).powerState().unmetDriveKwh());
    }

    @Test void addedMassTooLateToBrakeRejectsTheReplacementAtomically() {
        var initial = nuclear("mod_chemical_rocket", null, 0);
        var d = initial.shipDesigns().getLast();
        var slower = new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(), d.equippedModuleIds(),
                d.armorMaterialId(), 0, d.totalDryMassKg(), d.maxCargoMassKg(), d.fuelCapacityKg(), d.powerBalanceKw(),
                1, 0, 100000, false, false, d.manufacturingProfile(), d.powerProfile());
        initial = initial.withShipDesigns(List.of(initial.shipDesigns().getFirst(), slower));
        var r = receiver(initial);
        initial = ShipPowerResupply.replace(initial, new ShipInstance(r.id(), r.designId(), r.ownerEntityId(), 100, 0, 5000,
                r.storedCargoKg()).withPowerState(r.powerState()));
        initial = ShipPowerResupply.replace(initial, supplier(initial).withSupplyState(new ShipSupplyState(
                Map.of("rp1_kerosene", 2800.0, "liquid_oxygen", 7200.0), null, "")));
        var plan = InterstellarTravel.plan(initial, initial.fleets().getFirst(), "b");
        double burn = plan.peakSpeedMps() / plan.accelerationMps2() / 3600;
        double total = InterstellarTravel.travelSeconds(plan.distanceMeters(), plan.accelerationMps2(), plan.peakSpeedMps()) / 3600;
        var scheduled = propellant(initial, 5000, total - 2 * burn - 5 - .0001);
        assertFalse(new MoveFleetCommand("fleet", "b").validate(scheduled));
        var planned = FleetSupplySimulation.planned(scheduled.fleets().getFirst(), plan);
        var result = FleetPropulsionSupply.run(scheduled, planned, total - burn - .0001);
        assertEquals(10000, result.fleet().ships().getFirst().supplyFuelMassKg());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("braking geometry")));
    }

    @Test void sameDayPropulsionDeliveryAndArrivalDoNotDoubleBurnPropellant() {
        var initial = nuclear("mod_chemical_rocket", null, 0)
                .withSolarSystems(List.of(system("a", 0), system("b", 1e7 / InterstellarTravel.METERS_PER_LIGHT_YEAR)));
        var scheduled = propellant(initial, 1000, 0);
        var move = new MoveFleetCommand("fleet", "b");
        var preview = move.preview(scheduled);
        assertTrue(preview.ready(), preview.electrical().toString());
        assertEquals(1, preview.totalDays());
        var planned = FleetSupplySimulation.planned(scheduled.fleets().getFirst(), preview.crossing());
        var contact = FleetPropulsionSupply.run(scheduled, planned,
                preview.crossing().peakSpeedMps() / preview.crossing().accelerationMps2() / 3600 + 1).fleet();
        var next = tick(move.apply(scheduled));
        assertEquals("b", next.fleets().getFirst().currentSystemId());
        assertEquals(contact.ships().getLast().currentFuelKg() - contact.interstellarFuelBudgetKg().get("receiver"),
                receiver(next).currentFuelKg(), 1e-5);
        assertEquals(1000, supplier(next).supplyFuelMassKg());
        assertEquals(preview.electrical().getLast().generatorFuelUsedKg(),
                receiver(initial).generatorFuelMassKg() - receiver(next).generatorFuelMassKg(), 1e-6);
    }

    @Test void centuryScalePropulsionForecastRemainsBoundedAndRejectsInsufficientElectricity() {
        var initial = nuclear("mod_chemical_rocket", null, 0)
                .withSolarSystems(List.of(system("a", 0), system("b", 1)));
        var scheduled = propellant(initial, 1000, 0);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () ->
                assertFalse(new MoveFleetCommand("fleet", "b").validate(scheduled)));
    }

    @Test void mixedScheduleUsesLaterElectricalDeliveryAndExecutesEveryOrderOnce() {
        var scheduled = propellant(state(), 1000, 0);
        var electrical = new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 1000, 2);
        assertTrue(electrical.validate(scheduled));
        scheduled = electrical.apply(scheduled);
        assertEquals(2, supplier(scheduled).supplyState().orders().size());
        var move = new MoveFleetCommand("fleet", "b");
        var preview = move.preview(scheduled);
        assertTrue(preview.ready(), preview.electrical().toString());
        var current = move.apply(scheduled);
        int days = 0;
        while (current.fleets().getFirst().hasInterstellarOrder() && days < 60) { current = tick(current); days++; }
        assertEquals("b", current.fleets().getFirst().currentSystemId());
        assertEquals(preview.totalDays(), days);
        assertEquals(0, supplier(current).supplyFuelMassKg(), 1e-6);
        assertTrue(supplier(current).supplyState().orders().isEmpty());
        assertEquals(preview.electrical().getLast().generatorFuelUsedKg(), 1500 - receiver(current).generatorFuelMassKg(), 1e-5);
        assertEquals(0, supplier(tick(current)).supplyFuelMassKg(), 1e-6);
    }

    @Test void pumpIntervalsAndTotalReservationsPreventDoubleBookingAndOversubscription() {
        var scheduled = propellant(nuclear("mod_chemical_rocket", null, 0), 1000, 0);
        assertFalse(new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 500, .5).validate(scheduled));
        assertFalse(new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 1001, 2).validate(scheduled));
        var second = new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 1000, 2);
        assertTrue(second.validate(scheduled));
        var both = second.apply(scheduled);
        var selected = supplier(both).supplyState().orders().getFirst();
        var cancel = new CancelFleetSupplyCommand("supplier", selected);
        assertTrue(cancel.validate(both));
        var cancelled = cancel.apply(both);
        assertEquals(1, supplier(cancelled).supplyState().orders().size());
        assertEquals(ShipSupplyTransfer.Destination.ELECTRICAL_FUEL, supplier(cancelled).supplyState().order().destination());
        assertEquals(2000, supplier(cancelled).supplyFuelMassKg());
        assertFalse(cancel.validate(cancelled));
    }

    @Test void absoluteScheduleClockSurvivesReplanningAndSaveLoadBetweenDeliveries() throws Exception {
        var scheduled = propellant(state(), 1000, 0);
        scheduled = new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 1000, 25).apply(scheduled);
        var current = tick(new MoveFleetCommand("fleet", "b").apply(scheduled));
        assertEquals(1, supplier(current).supplyState().orders().size());
        assertEquals(24, supplier(current).supplyState().timeline().elapsedHours(), 1e-8);
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("mixed.scsave").toFile();
        manager.save(file, current, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(current.fleets(), loaded.fleets());
        assertEquals(tick(current).fleets(), tick(loaded).fleets());
        var delivered = tick(loaded);
        assertTrue(supplier(delivered).supplyState().orders().isEmpty());
        assertEquals(0, supplier(delivered).supplyFuelMassKg(), 1e-6);
        assertEquals(0, supplier(tick(delivered)).supplyFuelMassKg(), 1e-6);
    }

    @Test void multiplePropulsionDeliveriesRetainFixedTimesAndConserveBulkFuel() {
        var scheduled = propellant(nuclear("mod_chemical_rocket", null, 0), 500, 0);
        scheduled = propellant(scheduled, 500, 25);
        var move = new MoveFleetCommand("fleet", "b");
        var preview = move.preview(scheduled);
        assertTrue(preview.ready(), preview.electrical().toString());
        var current = tick(move.apply(scheduled));
        assertEquals(1500, supplier(current).supplyFuelMassKg(), 1e-6);
        assertEquals(1, supplier(current).supplyState().orders().size());
        var second = tick(current);
        assertEquals(1000, supplier(second).supplyFuelMassKg(), 1e-6);
        assertTrue(supplier(second).supplyState().orders().isEmpty());
        assertEquals(0, second.fleets().getFirst().flightMotion().trajectory().accelerationSeconds());
    }

    @Test void reactorFeedDeliveryFundsLaterPropulsionRefillWithoutChangingExhaust() {
        var initial = nuclear("mod_fusion_drive", "deuterium_gas", 300);
        var d = initial.shipDesigns().getFirst();
        var modules = new java.util.ArrayList<>(d.equippedModuleIds()); modules.add(ShipSupplyCatalog.REACTOR_MAGAZINE);
        var tanker = new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(), modules, d.armorMaterialId(), 0,
                d.totalDryMassKg(), d.maxCargoMassKg(), d.fuelCapacityKg(), d.powerBalanceKw(), 1, 0, d.totalThrustN(), false, false,
                d.manufacturingProfile(), ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList()));
        initial = initial.withShipDesigns(List.of(tanker, initial.shipDesigns().getLast()));
        initial = ShipPowerResupply.replace(initial, supplier(initial).withSupplyState(new ShipSupplyState(
                Map.of("hydrogen_gas", 20.0, "deuterium_gas", 1.0), null, "")));
        var feed = new ScheduleFleetSupplyCommand("supplier", "receiver", "deuterium_gas", 1, 0, ShipSupplyTransfer.Destination.DRIVE_REACTOR);
        assertTrue(feed.validate(initial));
        var scheduled = propellant(feed.apply(initial), 20, .01);
        var move = new MoveFleetCommand("fleet", "b");
        assertTrue(move.validate(scheduled));
        var current = tick(move.apply(scheduled));
        assertEquals(0, supplier(current).supplyFuelMassKg(), 1e-6);
        assertTrue(supplier(current).supplyState().orders().isEmpty());
        assertEquals(600000, current.fleets().getFirst().journeyPropulsion().get("receiver").exhaustVelocityMps());
        assertTrue(receiver(current).storedCargoKg().get("deuterium_gas") > 299);
    }

    @Test void destinationReservesMatchActualArrivalAndReturnPreviewConsumesNothing() {
        var initial = nuclear("mod_chemical_rocket", null, 0)
                .withSolarSystems(List.of(system("a", 0), system("b", 1e7 / InterstellarTravel.METERS_PER_LIGHT_YEAR)));
        var scheduled = propellant(initial, 1000, 0);
        var move = new MoveFleetCommand("fleet", "b");
        var preview = move.preview(scheduled);
        var reserve = preview.returnReserve();
        assertTrue(reserve.ready(), reserve.explanation());
        var arrived = tick(move.apply(scheduled));
        for (var ship : arrived.fleets().getFirst().ships()) {
            var predicted = reserve.destination().stream().filter(item -> item.shipId().equals(ship.id())).findFirst().orElseThrow();
            assertEquals(ship.currentFuelKg(), predicted.propellantKg(), 1e-5);
            assertEquals(ship.generatorFuelMassKg(), predicted.electricalFuelKg(), 1e-5);
            assertEquals(ship.supplyFuelMassKg(), predicted.bulkSupplyKg(), 1e-5);
        }
        var returnMove = new MoveFleetCommand("fleet", "a");
        assertTrue(returnMove.validate(arrived));
        assertEquals(reserve.returnDays(), returnMove.preview(arrived).totalDays());
        assertEquals(2000, supplier(scheduled).supplyFuelMassKg());
        assertNotNull(supplier(scheduled).supplyState().order());
    }

    @Test void validOutboundDoesNotPromiseAnUnfundedReturn() {
        var scheduled = propellant(nuclear("mod_chemical_rocket", null, 0), 1000, 0);
        var preview = new MoveFleetCommand("fleet", "b").preview(scheduled);
        assertTrue(preview.ready());
        assertFalse(preview.returnReserve().ready());
        assertEquals(2, preview.returnReserve().destination().size());
        assertTrue(preview.returnReserve().explanation().contains("electrical"));
    }

    @Test void changedFutureDeliveryRejectsTheEarlierRefillWithoutWithdrawingStock() {
        var scheduled = propellant(state(), 500, 0);
        scheduled = new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 1500, 2).apply(scheduled);
        var move = new MoveFleetCommand("fleet", "b");
        assertTrue(move.validate(scheduled));
        var departed = move.apply(scheduled);
        var changed = supplier(departed).withSupplyState(supplier(departed).supplyState()
                .withMaterials(Map.of("rp1_kerosene", 280.0, "liquid_oxygen", 720.0)));
        var next = tick(ShipPowerResupply.replace(departed, changed));
        assertEquals(1000, supplier(next).supplyFuelMassKg(), 1e-5);
        assertTrue(supplier(next).supplyState().orders().isEmpty());
        assertTrue(supplier(next).supplyState().outcome().contains("failed"));
        assertEquals(15000 - departed.fleets().getFirst().interstellarFuelBudgetKg().get("receiver") / 2,
                receiver(next).currentFuelKg(), 1e-5);
    }

    @Test void parallelSuppliersCompleteAtTheSameBoundaryWithoutLosingAnOrder() {
        var initial = state(); var d = supplier(initial);
        var second = new ShipInstance("supplier2", d.designId(), d.ownerEntityId(), 100, 0, d.currentFuelKg(), Map.of())
                .withPowerState(d.powerState()).withSupplyState(new ShipSupplyState(Map.of("rp1_kerosene", 140.0, "liquid_oxygen", 360.0), null, ""));
        initial = initial.withFleets(List.of(initial.fleets().getFirst().withShips(List.of(d, second, receiver(initial)))));
        var planned = InterstellarTravel.plan(initial, initial.fleets().getFirst(), "b");
        double burn = planned.peakSpeedMps() / planned.accelerationMps2() / 3600;
        var scheduled = schedule(initial, 500, 24 - burn - .5);
        scheduled = new ScheduleFleetSupplyCommand("supplier2", "receiver", "rp1", 500, 24 - burn - .5).apply(scheduled);
        var move = new MoveFleetCommand("fleet", "b");
        assertTrue(move.validate(scheduled));
        var next = tick(move.apply(scheduled));
        assertTrue(FleetSupplySimulation.orders(next.fleets().getFirst()).isEmpty());
        assertEquals(1500, supplier(next).supplyFuelMassKg(), 1e-6);
        assertEquals(0, next.fleets().getFirst().ships().get(1).supplyFuelMassKg(), 1e-6);
        assertTrue(next.fleets().getFirst().ships().get(1).supplyState().outcome().startsWith("Delivered"));
    }

    @Test void scheduleLimitKeepsForecastWorkBoundedAndDoesNotOverreserveStock() {
        var current = nuclear("mod_chemical_rocket", null, 0);
        for (int index = 0; index < 64; index++) {
            var add = new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", .001, index);
            assertTrue(add.validate(current));
            current = add.apply(current);
        }
        assertEquals(64, supplier(current).supplyState().orders().size());
        assertFalse(new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", .001, 65).validate(current));
        var scheduled = current;
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> assertTrue(new MoveFleetCommand("fleet", "b").validate(scheduled)));
        assertEquals(2000, supplier(current).supplyFuelMassKg());
    }
    @Test void scheduledSupplyEnablesOtherwiseRejectedDepartureAndActualTicksReachArrivalWithoutOutages() {
        var initial = state();
        var move = new MoveFleetCommand("fleet", "b");
        assertFalse(move.validate(initial));
        var scheduled = schedule(initial, 1000, 0);
        var preview = move.preview(scheduled);
        assertTrue(preview.ready(), preview.electrical().toString());
        assertTrue(preview.electrical().getLast().explanation().contains("Scheduled"));
        var current = move.apply(scheduled);
        for (int day = 0; day < 60 && current.fleets().getFirst().hasInterstellarOrder(); day++) current = tick(current);
        assertEquals("b", current.fleets().getFirst().currentSystemId());
        assertFalse(current.fleets().getFirst().hasInterstellarOrder());
        assertNull(supplier(current).supplyState().order());
        assertTrue(supplier(current).supplyState().outcome().startsWith("Delivered"));
        assertEquals(280, supplier(current).supplyState().materialsKg().get("rp1_kerosene"), 1e-8);
        assertEquals(720, supplier(current).supplyState().materialsKg().get("liquid_oxygen"), 1e-8);
        assertEquals(0, receiver(current).powerState().unmetEssentialHours());
        assertTrue(receiver(current).generatorFuelMassKg() > 96 / 1.008);
        assertEquals(2000, supplier(initial).supplyFuelMassKg());
    }
    @Test void shortCoastingWindowIsProcessedInsideDayAndPreviewMatchesActualFuel() {
        var initial = state();
        initial = initial.withSolarSystems(List.of(system("a", 0), system("b", 1e7 / InterstellarTravel.METERS_PER_LIGHT_YEAR)));
        var scheduled = schedule(initial, 1, 0);
        var move = new MoveFleetCommand("fleet", "b");
        var preview = move.preview(scheduled);
        assertTrue(preview.ready(), preview.electrical().toString());
        assertEquals(1, Math.ceil(preview.crossing().days()));
        var next = tick(move.apply(scheduled));
        assertEquals("b", next.fleets().getFirst().currentSystemId());
        assertNull(supplier(next).supplyState().order());
        assertTrue(supplier(next).supplyState().outcome().startsWith("Delivered"));
        assertEquals(1999, supplier(next).supplyFuelMassKg(), 1e-8);
        double actualUsed = 500 + 1 - receiver(next).generatorFuelMassKg();
        assertEquals(preview.electrical().getLast().generatorFuelUsedKg(), actualUsed, 1e-6);
    }
    @Test void capacityTimingPreContactEnduranceAndUnsafeBrakingPreventDeparture() {
        var move = new MoveFleetCommand("fleet", "b");
        var late = schedule(state(), 1000, 1000);
        assertFalse(move.validate(late));
        var noPower = ShipPowerResupply.replace(state(), receiver(state()).withPowerState(power(Map.of())));
        assertFalse(move.validate(schedule(noPower, 1000, 0)));
        // Electrical deliveries now replace the braking plan using real remaining propellant.
        var stock = ShipPowerResupply.replace(state(), supplier(state()).withSupplyState(new ShipSupplyState(
                Map.of("rp1_kerosene", 2800.0, "liquid_oxygen", 7200.0), null, "")));
        assertTrue(move.validate(schedule(stock, 10000, 0)));
        assertFalse(new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 15001, 0).validate(stock));
    }
    @Test void saveLoadPreservesReservedDeliveryAndCannotExecuteItTwice() throws Exception {
        var scheduled = schedule(state(), 1000, 25);
        var current = new MoveFleetCommand("fleet", "b").apply(scheduled);
        current = tick(current);
        assertNotNull(supplier(current).supplyState().order());
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("tanker.scsave").toFile();
        manager.save(file, current, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(current.fleets(), loaded.fleets());
        assertEquals(tick(current).fleets(), tick(loaded).fleets());
        var delivered = tick(loaded);
        assertNull(supplier(delivered).supplyState().order());
        double stock = supplier(delivered).supplyFuelMassKg();
        assertEquals(stock, supplier(tick(delivered)).supplyFuelMassKg());
    }
    @Test void changedPayloadFailsAtomicallyAndSplitCancelsPolicyWithoutLosingSupplyStores() {
        var scheduled = schedule(state(), 1000, 0);
        var current = new MoveFleetCommand("fleet", "b").apply(scheduled);
        var removed = supplier(current).withSupplyState(supplier(current).supplyState().withMaterials(Map.of("rp1_kerosene", 10.0)));
        current = ShipPowerResupply.replace(current, removed);
        var next = tick(current);
        assertNull(supplier(next).supplyState().order());
        assertTrue(supplier(next).supplyState().outcome().contains("failed"));
        assertEquals(10, supplier(next).supplyFuelMassKg());
        var split = new SplitFleetCommand("owner", "fleet", List.of("supplier"), "new", "Tanker").apply(scheduled);
        assertEquals(2000, split.fleets().getLast().ships().getFirst().supplyFuelMassKg());
        var reconciled = tick(split);
        assertNull(reconciled.fleets().getLast().ships().getFirst().supplyState().order());
        assertTrue(reconciled.fleets().getLast().ships().getFirst().supplyState().outcome().contains("cancelled"));
        assertEquals(2000, reconciled.fleets().getLast().ships().getFirst().supplyFuelMassKg());
    }
    @Test void realHubPurchasesFillSeparateCompartmentsAndRejectUnsupportedGoodsAndInsufficientPayment() {
        var initial = state();
        var yard = new OrbitalStation("yard", "Yard", "a", "earth", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                10, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
        var hub = new CommercialHub("hub", "yard", 0, 100000, 0, 1,
                Map.of("rp1_kerosene", new MarketOrder("rp1_kerosene", 10000, 0, 2, 0),
                        "liquid_oxygen", new MarketOrder("liquid_oxygen", 10000, 0, 3, 0)));
        initial = initial.withOrbitalStations(List.of(yard)).withCommercialHubs(List.of(hub)).withFleets(List.of(initial.fleets().getFirst()
                .withLocation(FleetLocation.at(FleetLocation.Site.docked("yard")))));
        var buy = new BuyShipSupplyFuelCommand("supplier", "yard", "rp1_kerosene", 1000);
        assertTrue(buy.validate(initial));
        var next = buy.apply(initial);
        assertEquals(1560, supplier(next).supplyState().materialsKg().get("rp1_kerosene"));
        assertEquals(9000, next.commercialHubs().getFirst().activeOrders().get("rp1_kerosene").supplyKg());
        assertTrue(next.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits());
        assertEquals(supplier(initial).storedCargoKg(), supplier(next).storedCargoKg());
        assertEquals(supplier(initial).powerState(), supplier(next).powerState());
        assertEquals(supplier(initial).currentFuelKg(), supplier(next).currentFuelKg());
        assertSame(next, new BuyShipSupplyFuelCommand("supplier", "yard", "food_matrix", 1).apply(next));
        assertSame(next, new BuyShipSupplyFuelCommand("supplier", "yard", "rp1_kerosene", 100000).apply(next));
        var ship = supplier(next);
        var partlyFueled = ShipPowerResupply.replace(next, new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                ship.currentHullHealth(), ship.currentShieldHealth(), 10000, ship.storedCargoKg(), ship.passengerCount(),
                ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState()));
        var refueled = ShipFueling.refuel(partlyFueled, ship.id(), "yard", 1000);
        assertEquals(11000, supplier(refueled).currentFuelKg());
        assertEquals(ship.supplyState(), supplier(refueled).supplyState());
    }
    @Test void dedicatedSupplySharingConservesMixturesAndOrdersProtectTheirStores() {
        var initial = state();
        var transfer = new TransferShipSuppliesCommand("supplier", "receiver", ShipSupplyTransfer.Source.SUPPLY_TANK,
                ShipSupplyTransfer.Destination.ELECTRICAL_FUEL, "rp1", 500);
        var next = transfer.apply(initial);
        assertEquals(1500, supplier(next).supplyFuelMassKg());
        assertEquals(1000, receiver(next).generatorFuelMassKg());
        assertEquals(supplier(initial).powerState(), supplier(next).powerState());
        var scheduled = schedule(initial, 1000, 0);
        assertSame(scheduled, transfer.apply(scheduled));
        assertSame(scheduled, new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 1, 0).apply(scheduled));
        var cancelled = new CancelFleetSupplyCommand("supplier").apply(scheduled);
        assertNull(supplier(cancelled).supplyState().order());
        assertEquals(2000, supplier(cancelled).supplyFuelMassKg());
    }
    @Test void newTankerBlueprintIsValidatedAndSupplyMassChangesItsTravelCapability() {
        var initial = state();
        var spec = new ShipDesignSpecification("newTanker", "Tanker", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                ShipSupplyCatalog.tankerModules("mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID), "steel", .5);
        var evaluation = ShipBlueprintFactory.evaluate(initial, spec);
        assertTrue(evaluation.valid(), evaluation.errors().toString());
        assertEquals(0, evaluation.design().maxCargoMassKg());
        assertEquals(80000, ShipSupplyCatalog.totalCapacity(evaluation.design()));
        assertEquals(15000, evaluation.design().fuelCapacityKg());
        var full = InterstellarTravel.plan(initial, initial.fleets().getFirst(), "b");
        var empty = ShipPowerResupply.replace(initial, supplier(initial).withSupplyState(ShipSupplyState.empty()));
        var unloaded = InterstellarTravel.plan(empty, empty.fleets().getFirst(), "b");
        assertTrue(unloaded.accelerationMps2() > full.accelerationMps2());
        assertTrue(unloaded.peakSpeedMps() > full.peakSpeedMps());
    }

    @Test void powerFailureBeforeContactCancelsDeliveryWithoutRemoteFuelAndBulkBoilOffIsPhysical() {
        var scheduled = schedule(state(), 1000, 10);
        var departed = new MoveFleetCommand("fleet", "b").apply(scheduled);
        var powerless = ShipPowerResupply.replace(departed, receiver(departed).withPowerState(power(Map.of())));
        var stopped = tick(powerless);
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, stopped.fleets().getFirst().interstellarMode());
        assertEquals(2000, supplier(stopped).supplyFuelMassKg());
        assertNull(supplier(stopped).supplyState().order());
        assertEquals(0, receiver(stopped).generatorFuelMassKg());
        var idle = ShipPowerResupply.replace(state(), supplier(state()).withPowerState(power(Map.of())));
        var spoiled = tick(idle);
        assertTrue(supplier(spoiled).supplyFuelMassKg() < 2000);
        assertEquals(560, supplier(spoiled).supplyState().materialsKg().get("rp1_kerosene"));
        assertTrue(supplier(spoiled).supplyState().preservation().lostKgToday().get("liquid_oxygen") > 0);
        assertEquals(0, supplier(spoiled).storedCargoKg().size());
    }

    @Test void cargoOnlyPowerDeficitStillRejectsScheduledDeparture() {
        var initial = state();
        var d = initial.shipDesigns().getLast();
        var p = d.powerProfile();
        var weak = new ShipPowerProfile(0, 5, 0, 0, 0, 0, 15000, 0, 2, 0, 30, .65, p.fuels());
        var design = new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(), d.equippedModuleIds(),
                d.armorMaterialId(), d.armorThicknessCm(), d.totalDryMassKg(), 1000, d.fuelCapacityKg(), d.powerBalanceKw(),
                d.calculatedStructuralIntegrity(), 0, d.totalThrustN(), false, false, d.manufacturingProfile(), weak);
        initial = initial.withShipDesigns(List.of(initial.shipDesigns().getFirst(), design));
        var old = receiver(initial);
        initial = ShipPowerResupply.replace(initial, new ShipInstance(old.id(), old.designId(), old.ownerEntityId(), 100, 0,
                old.currentFuelKg(), Map.of("food_matrix", 1000.0)).withPowerState(old.powerState()));
        var scheduled = schedule(initial, 1000, 0);
        var preview = new MoveFleetCommand("fleet", "b").preview(scheduled);
        assertFalse(preview.ready());
        assertTrue(preview.electrical().getLast().explanation().contains("cargo"));
    }
}
