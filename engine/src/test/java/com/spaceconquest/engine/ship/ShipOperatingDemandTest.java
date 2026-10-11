package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShipOperatingDemandTest {
    private ShipDesign design(String... ids) {
        var modules = List.of(ids);
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", modules,
                "steel", 0, 1000, 10000, 1000, 100, 10, 0, 2000, false, false,
                ShipManufacturingProfile.baseline(), ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList()));
    }
    private ShipInstance ship(Map<Integer, Double> damage, Map<String, Double> cargo) {
        return new ShipInstance("ship", "design", "owner", 1000, 0, 1000, cargo)
                .withModuleDamage(new ShipModuleDamage(damage))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0,
                        false, 1, 1, 0, 0, 0, 0, 0));
    }
    private GameState world(ShipDesign design, ShipInstance ship) {
        var fleet = new Fleet("fleet", "Fleet", "owner", "system", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
    }

    @Test void disabledAndPartialModulesReduceIdleDemandWithoutChangingBlueprintOrOtherLoads() {
        var design = design(CombatModuleCatalog.LASER, CombatModuleCatalog.SHIELD, SensorModuleCatalog.BASIC,
                ShipRepairModuleCatalog.WORKSHOP, "mod_ion_drive", "mod_cargo_vault", "cryogenic_stasis_pod");
        var damaged = ship(Map.of(0, 100.0, 1, 50.0, 2, 100.0, 3, 25.0, 4, 100.0, 5, 100.0, 6, 100.0), Map.of());
        var rated = design.powerProfile(); var live = design.powerProfile(damaged);
        assertEquals(102, rated.hotelKw()); assertEquals(32, live.hotelKw());
        assertEquals(rated.driveKw(), live.driveKw()); assertEquals(rated.cargoKw(), live.cargoKw());
        var passengers = new ShipInstance(damaged.id(), damaged.designId(), damaged.ownerEntityId(), 1000, 0, 1000,
                Map.of(), 100, "human", "NORMAL", damaged.powerState(), damaged.supplyState(), damaged.moduleDamage());
        assertEquals(132, live.essentialKw(passengers, design));
        var stasis = new ShipInstance(passengers.id(), passengers.designId(), passengers.ownerEntityId(), 1000, 0, 1000,
                Map.of(), 100, "human", ShipInstance.MODE_CRYOGENIC_STASIS, passengers.powerState(),
                passengers.supplyState(), passengers.moduleDamage());
        assertEquals(112, live.essentialKw(stasis, design));
        assertEquals(rated, design.powerProfile(damaged.withModuleDamage(ShipModuleDamage.empty())));
    }

    @Test void survivingWeaponsAndEmittersStillFundTheirSeparateActiveCosts() {
        var design = design(ShipComponentCatalog.FISSION_REACTOR_ID, CombatModuleCatalog.LASER,
                CombatModuleCatalog.LASER, CombatModuleCatalog.SHIELD, CombatModuleCatalog.SHIELD, ShipComponentCatalog.FISSION_REACTOR_ID);
        var damaged = ship(Map.of(1, 100.0, 3, 100.0), Map.of());
        var result = ShipCombatPower.round(damaged, design, 1.0 / 60, true);
        assertEquals(72, design.powerProfile(damaged).hotelKw());
        assertEquals(8, result.requestedWeaponKwh()); assertEquals(8, result.paidWeaponKwh(), 1e-7);
        assertTrue(result.firepower() > 0); assertEquals(1.0 / 6, result.ship().currentShieldHealth(), 1e-7);
        double energy = 72.0 / 60 + 8 + 1.0 / 3;
        assertEquals(energy / 6_000_000, 1 - result.ship().generatorFuelMassKg(), 1e-12);
        assertEquals(1, damaged.generatorFuelMassKg());
    }

    @Test void paidRepairRestoresDemandOnTheNextPowerTickAndCanTriggerLoadShedding() {
        var design = design(ShipComponentCatalog.FISSION_REACTOR_ID, CombatModuleCatalog.LASER, ShipRepairModuleCatalog.WORKSHOP);
        var damaged = ship(Map.of(0, 95.0, 1, 100.0), Map.of("industrial_ceramics", 20.0, "silicon", 10.0));
        var state = world(design, damaged);
        var dayOne = ShipPowerProcessor.advanceDay(state).getFirst(); var repaired = dayOne.ships().getFirst();
        assertEquals(0, repaired.powerState().lastUnmetEssentialKwh(), 1e-7);
        assertEquals(80, repaired.moduleDamage().damageByMount().get(1));
        assertTrue(repaired.storedCargoKg().isEmpty()); assertEquals(30, design.powerProfile(repaired).hotelKw(), 1e-7);
        assertEquals(528.0 / 6_000_000, 1 - repaired.generatorFuelMassKg(), 1e-12);
        var dayTwo = ShipPowerProcessor.advanceDay(state.withFleets(List.of(dayOne))).getFirst().ships().getFirst();
        assertEquals(0, dayTwo.powerState().lastUnmetEssentialKwh(), 1e-7);
        assertTrue(dayTwo.powerState().shedServices().contains(ShipServicePower.WEAPONS));
        assertEquals(repaired.moduleDamage(), dayTwo.moduleDamage());
        assertEquals(100, damaged.moduleDamage().damageByMount().get(1)); assertEquals(62, design.powerProfile().hotelKw());
    }

    @Test void disabledSensorsReleasePowerForRequiredUpkeepButDoNotGrantCoverage() {
        var design = design(ShipComponentCatalog.FISSION_REACTOR_ID, SensorModuleCatalog.ADVANCED);
        var healthy = ship(Map.of(0, 99.0), Map.of());
        var healthyDay = ShipPowerProcessor.advanceDay(world(design, healthy)).getFirst().ships().getFirst();
        assertEquals(0, healthyDay.powerState().lastUnmetEssentialKwh(), 1e-7);
        assertTrue(healthyDay.powerState().shedServices().contains(ShipServicePower.SENSORS));
        var disabled = healthy.withModuleDamage(new ShipModuleDamage(Map.of(0, 99.0, 1, 100.0)));
        var disabledDay = ShipPowerProcessor.advanceDay(world(design, disabled)).getFirst().ships().getFirst();
        assertEquals(0, disabledDay.powerState().lastUnmetEssentialKwh(), 1e-7);
        assertEquals(0, SensorModuleCatalog.profile(design, disabledDay).rangeKm());
        assertEquals(48.0 / 6_000_000, 1 - disabledDay.generatorFuelMassKg(), 1e-12);
    }

    @Test void aFullOutageRetainsItsActualExposureWithDisabledEquipment() {
        var design = design(SensorModuleCatalog.ADVANCED, "mod_chemical_rocket", ChemicalFreighterCatalog.MAIN_TANK);
        var damaged = ship(Map.of(0, 100.0), Map.of())
                .withPowerState(ShipPowerState.empty())
                .withSupplyState(new ShipSupplyState(Map.of("liquid_hydrogen", 100.0), null, ""));
        var after = ShipPowerProcessor.advanceDay(world(design, damaged)).getFirst().ships().getFirst();
        assertEquals(24, after.powerState().unmetEssentialHours(), 1e-7);
        assertEquals(24, after.supplyState().preservation().exposureHours().get("liquid_hydrogen"), 1e-7);
        assertEquals(1000 * (1 - ChemicalFreighterCatalog.unconditionedDailyLoss("mod_chemical_rocket")), after.currentFuelKg(), 1e-7);
        assertEquals(1000, damaged.currentFuelKg());
    }
}
