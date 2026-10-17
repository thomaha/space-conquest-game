package com.spaceconquest.engine.ship;

import java.util.*;

/** Pure hardware comparison and refit bill. Removed equipment grants no speculative refund. */
public record ShipModelComparison(List<ComponentChange> components, boolean structuralChange, boolean armorChange,
        double structuralChangeFraction, ShipProductionReceipt equipment, ShipProductionReceipt structure,
        ShipProductionReceipt armor, ShipProductionReceipt installation) {
    public enum Kind { RETAINED, REPLACED, ADDED, REMOVED }
    public record ComponentChange(Kind kind, ShipComponentInstallation source, ShipComponentInstallation target) {}
    public ShipModelComparison { components = List.copyOf(components); }
    public ShipProductionReceipt total() { return equipment.plus(structure).plus(armor).plus(installation); }
    public boolean noChange() { return !structuralChange && !armorChange && components.stream().allMatch(change -> change.kind() == Kind.RETAINED); }
    public Map<String, String> retainedIdentities() {
        var result = new HashMap<String, String>();
        components.stream().filter(change -> change.kind() == Kind.RETAINED).forEach(change -> result.put(change.target().id(), change.source().id()));
        return Map.copyOf(result);
    }
    public static ShipModelComparison compare(ShipDesign source, ShipDesign target) {
        if (target == null || target.hullFrame() == null) throw new IllegalArgumentException("Captured target hull is required");
        return compare(source, target, HullTechnology.refitTechnology(target.hullFrame()));
    }
    public static ShipModelComparison compare(ShipDesign source, ShipDesign target, ShipRefitTechnology policy) {
        if (source == null || target == null || source.assembly() == null || target.assembly() == null || source.hullFrame() == null || target.hullFrame() == null)
            throw new IllegalArgumentException("Captured source and target assemblies and hulls are required");
        var changes = components(source.assembly(), target.assembly()); var empty = new ShipProductionReceipt(Map.of(), 0);
        var equipment = empty; double installWork = 0;
        for (var change : changes) {
            if (change.kind() == Kind.ADDED || change.kind() == Kind.REPLACED) {
                equipment = equipment.plus(change.target().production());
                installWork += change.target().installationWorkHours() * policy.installationWorkMultiplier();
            }
            if (change.kind() == Kind.REMOVED || change.kind() == Kind.REPLACED)
                installWork += change.source().installationWorkHours() * policy.removalWorkFraction();
        }
        var old = source.hullFrame(); var next = target.hullFrame();
        double fraction = Math.max(Math.abs((double) next.totalSlots() / old.totalSlots() - 1),
                Math.max(Math.abs(next.frameMassKg() / old.frameMassKg() - 1), Math.abs(next.strengthMultiplier() / old.strengthMultiplier() - 1)));
        boolean structural = fraction > 0 || !source.hullMaterialId().equals(target.hullMaterialId());
        boolean armorChange = next.totalSlots() != old.totalSlots() || !source.armorMaterialId().equals(target.armorMaterialId())
                || source.armorThicknessCm() != target.armorThicknessCm() || !source.assembly().armor().materialsKg().equals(target.assembly().armor().materialsKg());
        var structure = structural ? new ShipProductionReceipt(target.assembly().frame().materialsKg(), target.assembly().frame().workHours()
                * (policy.structuralWorkMultiplier() + Math.pow(fraction, policy.structuralGrowthExponent()))) : empty;
        var armor = armorChange ? new ShipProductionReceipt(target.assembly().armor().materialsKg(), target.assembly().armor().workHours()
                + source.assembly().armor().workHours() * policy.armorRemovalWorkFraction()) : empty;
        if (structural) installWork += changes.stream().filter(change -> change.kind() == Kind.RETAINED)
                .mapToDouble(change -> change.target().installationWorkHours()).sum() * policy.systemsReconnectionMultiplier();
        return new ShipModelComparison(changes, structural, armorChange, fraction, equipment, structure, armor, new ShipProductionReceipt(Map.of(), installWork));
    }
    private static List<ComponentChange> components(ShipBlueprintAssembly source, ShipBlueprintAssembly target) {
        var available = new ArrayList<>(source.installations()); var result = new ArrayList<ComponentChange>();
        var explicit = new HashSet<>(target.identities());
        for (var next : target.installations()) {
            var previous = available.stream().filter(item -> item.id().equals(next.id())).findFirst().orElse(null);
            if (previous == null) previous = available.stream().filter(item -> !explicit.contains(item.id()) && item.hardware().equals(next.hardware())).findFirst().orElse(null);
            if (previous == null) previous = available.stream().filter(item -> !explicit.contains(item.id()) && item.hardware().id().equals(next.hardware().id())).findFirst().orElse(null);
            if (previous == null) result.add(new ComponentChange(Kind.ADDED, null, next));
            else {
                result.add(new ComponentChange(previous.hardware().equals(next.hardware()) ? Kind.RETAINED : Kind.REPLACED, previous, next)); available.remove(previous);
            }
        }
        available.forEach(previous -> result.add(new ComponentChange(Kind.REMOVED, previous, null)));
        return List.copyOf(result);
    }
}
