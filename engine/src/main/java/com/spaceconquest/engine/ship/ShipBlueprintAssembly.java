package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Captured separate structural and equipment bills with deterministic installation identities. */
public record ShipBlueprintAssembly(ShipProductionReceipt frame, ShipProductionReceipt armor,
        ShipProductionReceipt integration, List<ShipComponentInstallation> installations) {
    public ShipBlueprintAssembly {
        installations = List.copyOf(installations);
        if (frame == null || armor == null || integration == null || installations.stream().map(ShipComponentInstallation::id).distinct().count() != installations.size())
            throw new IllegalArgumentException("Invalid blueprint assembly");
    }
    public ShipProductionReceipt total() {
        var total = frame.plus(armor).plus(integration);
        for (var installation : installations) total = total.plus(installation.production());
        return total;
    }
    public List<String> identities() { return installations.stream().map(ShipComponentInstallation::id).toList(); }
    public static ShipManufacturingProfile manufacturingProfile(GameState state, String owner, List<ShipModule> modules, double mass, ShipHullFrame frame) {
        var profile = ShipApplicationProduction.profile(state, owner, modules, mass);
        var captured = new HashMap<>(profile.applicationModifiers());
        captured.put(frame.applicationId(), ApplicationProduction.modifiersForOwner(state, owner, frame.applicationId()));
        return new ShipManufacturingProfile(profile.costMultiplier(), profile.requiredComplexity(), profile.stasisCapacity(), captured);
    }
    public static ShipBlueprintAssembly capture(GameState state, ShipDesignSpecification spec, ShipHullFrame hull,
                                               double dryMass, List<ShipModule> modules) {
        var policy = HullTechnology.assemblyTechnology(hull);
        var structure = policy.recipe(policy.frameMaterialsPerKg(), hull.frameMassKg(), spec.hullMaterialId(),
                Math.max(policy.minimumFrameWork(), hull.frameMassKg() * policy.frameWorkPerKg()))
                .scale(ApplicationProduction.modifiersForOwner(state, spec.ownerEntityId(), hull.applicationId()).costMultiplier());
        double armorMass = Math.max(0, dryMass - hull.frameMassKg() - modules.stream().mapToDouble(ShipModule::dryMassKg).sum());
        var armor = new ShipProductionReceipt(armorMass > 0 ? Map.of(spec.armorMaterialId(), armorMass) : Map.of(), armorMass * policy.armorWorkPerKg());
        var installations = new ArrayList<ShipComponentInstallation>();
        var occurrence = new HashMap<String, Integer>();
        for (int index = 0; index < modules.size(); index++) {
            var module = modules.get(index); String app = ShipApplicationProduction.applicationId(module.id());
            String identity = spec.installationIds().isEmpty() ? spec.id() + "/" + module.id() + "/" + occurrence.merge(module.id(), 1, Integer::sum) : spec.installationIds().get(index);
            double work = module.dryMassKg() * policy.componentWorkPerKg();
            var combat = CombatModuleCatalog.application(module.id());
            if (combat == null) combat = ShipMiningModuleCatalog.application(module.id());
            if (combat != null) work = combat.costToBuildPerUnit();
            var bill = module.materialCostsKg().isEmpty() ? policy.recipe(policy.componentMaterialsPerKg(), module.dryMassKg(), spec.hullMaterialId(), work)
                    : new ShipProductionReceipt(module.materialCostsKg(), work);
            if (app != null) bill = bill.scale(ApplicationProduction.modifiersForOwner(state, spec.ownerEntityId(), app).costMultiplier());
            installations.add(new ShipComponentInstallation(identity, module, app, bill, module.slotCost() * policy.installationWorkPerSlot()));
        }
        double integrationWork = modules.stream().mapToInt(ShipModule::slotCost).sum() * policy.installationWorkPerSlot();
        return new ShipBlueprintAssembly(structure, armor, new ShipProductionReceipt(Map.of(), integrationWork), installations);
    }
    /** Preserve matching individual installations while allocating distinct IDs for added equipment. */
    public static List<String> identitiesFor(ShipDesign source, List<String> modules, String newDesignId) {
        if (source == null || source.assembly() == null) return List.of();
        var remaining = new ArrayList<>(source.assembly().installations()); var result = new ArrayList<String>();
        for (int index = 0; index < modules.size(); index++) {
            String type = modules.get(index);
            var matching = remaining.stream().filter(item -> type.equals(item.hardware().id())).findFirst().orElse(null);
            if (matching != null) { result.add(matching.id()); remaining.remove(matching); }
            else {
                String id = newDesignId + "/added/" + index;
                while (result.contains(id) || source.assembly().identities().contains(id)) id += "/new";
                result.add(id);
            }
        }
        return List.copyOf(result);
    }
}
