package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Material;
import com.spaceconquest.engine.habitation.PassengerStasis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rebuilds a medium-frame blueprint from catalog choices and the owner's live research and yard capacity. */
public final class ShipBlueprintFactory {
    public record Evaluation(ShipDesign design, ShipDesignValidator.ValidationResult physics, List<String> errors) {
        public Evaluation { errors = List.copyOf(errors); }
        public boolean valid() { return design != null && errors.isEmpty(); }
    }

    private ShipBlueprintFactory() {}

    public static Evaluation evaluate(GameState state, ShipDesignSpecification specification) {
        List<String> errors = new ArrayList<>();
        if (state == null || specification == null) return invalid("A game state and design specification are required");
        if (blank(specification.id()) || blank(specification.name()) || blank(specification.ownerEntityId()))
            return invalid("A blueprint ID, name and owner are required");
        var owner = state.empires().stream().filter(empire -> specification.ownerEntityId().equals(empire.id()))
                .findFirst().orElse(null);
        if (owner == null || state.corporations().stream().anyMatch(corp -> specification.ownerEntityId().equals(corp.id())))
            return invalid("Player blueprints require an empire owner");
        if (specification.role() == null || !ShipComponentCatalog.ROLES.contains(specification.role()))
            return invalid("Unknown ship role");
        if (!Double.isFinite(specification.armorThicknessCm()) || specification.armorThicknessCm() < 0
                || specification.armorThicknessCm() > 10) return invalid("Armor thickness must be between 0 and 10 cm");
        List<ShipModule> modules = new ArrayList<>();
        for (String id : specification.moduleIds()) {
            ShipModule module = ShipComponentCatalog.module(id);
            if (module == null) errors.add("Unknown component: " + id);
            else modules.add(ShipApplicationProduction.optimize(state, owner.id(), module));
        }
        if (!errors.isEmpty()) return new Evaluation(null, null, errors);
        if (specification.moduleIds().stream().filter(PropulsionCatalog.MAIN_DRIVE_IDS::contains).count() != 1)
            errors.add("A new blueprint requires exactly one recognized main drive");
        if (!PropulsionCatalog.researched(specification.moduleIds(), owner.unlockedTechIds()))
            errors.add("The selected propulsion drive has not been researched");
        if (!ShipComponentCatalog.powerResearched(specification.moduleIds(), owner.unlockedTechIds()))
            errors.add("The selected electrical source has not been researched");
        if (!ChemicalFreighterCatalog.researched(specification.moduleIds(), owner.unlockedTechIds()))
            errors.add("Compact freighter equipment requires electricity and industrial production research");
        if (!ChemicalFreighterCatalog.compatible(specification.moduleIds()))
            errors.add("The conditioned long-range tank requires a supported chemical mixture within its volume limit");
        if (specification.moduleIds().stream().anyMatch(id -> ShipSupplyCatalog.STORAGE_IDS.contains(id)
                || ShipSupplyCatalog.TRANSFER_PUMP.equals(id)) && !owner.unlockedTechIds().contains("electricity"))
            errors.add("Dedicated supply storage and transfer equipment require electricity research");
        if (specification.moduleIds().contains(PassengerStasis.MODULE_ID)
                && !owner.unlockedTechIds().contains(PassengerStasis.TECHNOLOGY_ID))
            errors.add("Cryogenic stasis has not been researched");
        Material hull;
        Material armor;
        try {
            var materials = DataModelLoader.loadMaterials();
            hull = material(materials, specification.hullMaterialId());
            armor = material(materials, specification.armorMaterialId());
        } catch (IOException e) {
            return invalid("Cannot load structural materials");
        }
        if (hull == null || armor == null) return invalid("Hull and armor must use known structural materials");
        var environment = launchEnvironment(state, owner.controlledSystemIds());
        var physics = new ShipDesignValidator().validate(specification.role(), ShipComponentCatalog.mediumFrame(hull.id()),
                modules, hull, armor, specification.armorThicknessCm(), environment.gravity(), environment.pressure(),
                ShipManufacturingCapacity.forOwner(state, owner.id()));
        if (!environment.surface()) physics = new ShipDesignValidator.ValidationResult(physics.isValid(), false,
                physics.structuralIntegrity(), physics.powerBalanceKw(), physics.totalDryMassKg(),
                physics.maxCargoMassKg(), physics.fuelCapacityKg(), physics.totalThrustN(), 0,
                physics.validationErrors());
        errors.addAll(physics.validationErrors());
        if (!errors.isEmpty()) return new Evaluation(null, physics, errors);
        ShipDesign design = new ShipDesign(specification.id(), specification.name(), owner.id(), specification.role(),
                hull.id(), specification.moduleIds(), armor.id(), specification.armorThicknessCm(),
                physics.totalDryMassKg(), physics.maxCargoMassKg(), physics.fuelCapacityKg(), physics.powerBalanceKw(),
                physics.structuralIntegrity(), environment.surface() ? physics.minLaunchThrustRequiredN() : 0,
                physics.totalThrustN(), environment.surface() && physics.isLaunchCapable(), false,
                ShipApplicationProduction.profile(state, owner.id(), modules, physics.totalDryMassKg()),
                ShipPowerProfile.capture(modules));
        return new Evaluation(design, physics, errors);
    }

    private record LaunchEnvironment(double gravity, double pressure, boolean surface) {}

    private static LaunchEnvironment launchEnvironment(GameState state, List<String> controlledSystems) {
        for (var system : state.solarSystems()) {
            if (!controlledSystems.contains(system.id())) continue;
            for (var planet : system.planets()) {
                if ("gas_giant".equals(planet.type()) || !Double.isFinite(planet.gravity()) || planet.gravity() <= 0) continue;
                return new LaunchEnvironment(planet.gravity(), "none".equalsIgnoreCase(planet.atmosphere()) ? 0 : 1, true);
            }
        }
        return new LaunchEnvironment(0.1, 0, false);
    }

    private static Material material(List<Material> materials, String id) {
        return materials.stream().filter(material -> material.id().equals(id) && material.strength() > 0)
                .findFirst().orElse(null);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Evaluation invalid(String error) { return new Evaluation(null, null, List.of(error)); }
}
