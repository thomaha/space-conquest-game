# Ship design review and refit foundation

Review date: 14 October 2026. Findings describe the current implementation. Proposed model and refit rules below are Draft.

Follow-up implementation: Player blueprints capture freely chosen hull capacity and frame mass governed by HullTechnology application data. The workbench exposes hull capacity plus cargo-hold, tank, battery and combat equipment counts. Application research raises maximum structural capacity without unlocking named ship classes. Factory blueprints now capture individual installation identities and separate frame, armor, equipment and integration receipts. Damage remapping supports retained equipment and reordered models, and armor estimates follow chosen hull capacity rather than occupied slots. See [ShipAssembly.md](ShipAssembly.md). The findings below describe the reviewed baseline; physical geometry, material-aware structural physics and reusable removed components remain outstanding. Corporate procurement now shares the freely sized factory and captured assembly lifecycle. See [CorporateShipDesigns.md](CorporateShipDesigns.md). Paid refit orders preserve progress through reload and update the same reserved ship after physical payment. See [ShipRefits.md](ShipRefits.md). Bounded AI repair/refit/replacement selection is implemented for idle ships at controlled ports. See [ShipLifecycle.md](ShipLifecycle.md). Existing-ship cancellation, paid retirement and funded salvage now complete this first lifecycle. See [ShipRetirement.md](ShipRetirement.md).

Design constraint: The designer controls ship size and module mix. Frigate, battleship and similar names must not impose size or predefined hardpoints. Future component installation identities distinguish physical equipment and preserve its condition; they do not define class-specific weapon restrictions. Research advances maximum feasible size and engineering methods rather than unlocking specific ship models.

## Main finding

Ship design currently combines a fixed player hull, aggregate calculated properties and captured equipment capabilities. The travel, research, payment and damage systems provide useful foundations but the blueprint lacks the structural and component identity needed for variable ship size and reliable upgrades. Replacement and retirement decisions should follow this foundation work.

The requested refit behavior is a model requirement: replacing a weapon should primarily buy the replacement weapon and installation work. Enlarging the vessel should require substantial structural reconstruction. Total dry-mass difference alone cannot distinguish those changes.

## Findings by priority

### High: Hull size and structure are missing from saved designs

[ShipComponentCatalog](engine/src/main/java/com/spaceconquest/engine/ship/ShipComponentCatalog.java) supplies a fixed medium frame with 30 slots and 15,000 kg frame mass. [ShipBlueprintFactory](engine/src/main/java/com/spaceconquest/engine/ship/ShipBlueprintFactory.java) always uses that frame for player designs. ShipHullFrame exists as a validation input but [ShipDesign](engine/src/main/java/com/spaceconquest/engine/ship/ShipDesign.java) does not retain it. ShipDesignSpecification has no frame or dimensional choice.

ShipDesign.md describes dynamic hull sizing and light through heavy classifications. These are design intentions rather than implemented workbench choices. Slots currently bound one medium template; changing a module changes mass and occupied slots but does not describe a hull enlargement.

Consequence: An upgrade quote cannot determine whether added equipment fits spare space, requires new bays or replaces the structural hull. Ship roles also do not provide a physical hull size.

### High: Material choice and armor construction are only partly represented

[ShipDesignValidator](engine/src/main/java/com/spaceconquest/engine/ship/ShipDesignValidator.java) adds a fixed frame mass and computes armor mass as occupied slots multiplied by thickness and 150 kg. Hull material strength participates in the integrity formula but the material's weightPerUnit and complexity are not used in this validation path. Armor material does not change this mass calculation.

Example: Two otherwise identical steel and aluminum designs retain the same calculated frame mass. Changing armor material at the same thickness retains the same armor mass. Removing a weapon reduces armor mass through occupied slot count even when the external vessel envelope should remain unchanged.

[ShipCombatStats](engine/src/main/java/com/spaceconquest/engine/combat/ShipCombatStats.java) computes armor deflection from thickness with fixed coefficients. WeaponImpactResolver also applies captured weapon-specific armor interactions. The selected armor material is not consulted by those direct impact calculations.

Consequence: Material, hull geometry, armor cost and combat protection do not form one consistent physical model. The existing material weightPerUnit must not be treated as density without defining its units. Explicit density and structural or armor properties are needed.

### High: Construction costs cannot yet support component-difference refits

[ConstructionMaterialCatalog](engine/src/main/java/com/spaceconquest/engine/industry/ConstructionMaterialCatalog.java) derives a generic ship bill from total dry mass: 65% hull material, 20% armor material, 10% copper and 5% silicon, plus recognized module receipts. [ShipConstructionRequirements](engine/src/main/java/com/spaceconquest/engine/ship/ShipConstructionRequirements.java) derives work from total mass with a 200-unit minimum and applies an aggregate manufacturing multiplier.

This is a provisional bill, not separate receipts for structure, armor, equipment and installation. A heavier weapon can increase the generic hull and armor bill even with an unchanged frame. Armor material remains part of the generic bill regardless of the actual armor thickness.

There is also a concrete captured-data gap: ConstructionMaterialCatalog.ship(ShipDesign) has logic to use captured combat module material receipts, but ShipConstructionRequirements.estimate(ShipDesign) calls the aggregate overload instead. That route reads current catalog module receipts and applies the aggregate manufacturing multiplier. Ordinary queued orders freeze their resulting bill, but a later estimate of an existing captured design can still use current catalog receipts.

Consequence: Subtracting two whole-ship bills would produce misleading weapon replacement and structural rebuild costs. Capture the individual receipts first and share them between construction, repair and refit quotes.

### High: Component damage identities cannot survive arbitrary model changes

[ShipModuleDamage](engine/src/main/java/com/spaceconquest/engine/ship/ShipModuleDamage.java) saves damage by index in equippedModuleIds. Duplicate modules are permitted but have no stable installation IDs or physical bay identity. Combat module definitions are captured by module type, while noncombat damage lookup falls back to catalog hardware.

Example: Moving a weapon earlier in the module list can make its old index refer to another component. Switching a ship's design ID without an explicit mapping could transfer damage to the wrong equipment or erase damage on retained hardware.

Consequence: Refits need stable installation identities and a mapping of retained, removed, replaced and new equipment. Retained components keep their actual condition. A changed research revision with the same catalog module ID still represents changed captured hardware and can require replacement work.

### Medium: Hull durability has inconsistent anchors

[ShipConstructionProcessor](engine/src/main/java/com/spaceconquest/engine/ship/ShipConstructionProcessor.java) commissions every hull with 1,000 health. ShipCombatStats scales weapon condition against calculatedStructuralIntegrity multiplied by 100, subject to a floor. ShipDesign has no captured maximum hull health or structural sections.

Consequence: The meaning of a fully healthy ship is not consistently derived from its structure. Enlarging a hull cannot currently establish a meaningful new durability limit, and a refit must not silently repair the original hull.

### Medium: Player, corporate and AI design paths are uneven

Player designs use ShipBlueprintFactory and physics validation. [CorporateInvestmentProcessor](engine/src/main/java/com/spaceconquest/engine/market/CorporateInvestmentProcessor.java) creates proprietary designs from an independent aggregate template with its own mass, launch thrust and integrity values. AI military development reuses owned templates and adds a researched weapon when needed; it does not design a physical hull layout.

The workbench exposes propulsion, power, armor, sensors and equipment counts, while its helper automatically includes cargo, tank and battery choices. It cannot express arbitrary bay placement, selectable hull dimensions, multiple sized power installations or structural changes. Several declared operational roles have limited catalog or UI support.

Consequence: One blueprint evaluation contract should serve public and corporate designers while keeping their ownership and permissions intact. Broadening AI design before establishing that contract would multiply inconsistent rules.

### Medium: Preview assumptions need clearer separation

The factory uses the first suitable owned planet for launch validation; the player cannot select the actual launch environment. Static power balance, journey electrical endurance, loaded launch capability and yard feasibility are distinct questions. The UI estimates minimum construction time using work divided by a nominal 100-unit rate, rather than a selected yard's current paid capacity.

Workbench preview and submit handlers call blueprint evaluation synchronously. DataModelLoader caches lists, so resource reads are usually cached, but cold catalog loading remains possible through this path. Expanded design solving and refit quoting should run off the JavaFX thread and publish immutable results with stale-snapshot checks.

## Useful foundations to retain

Blueprint commands recalculate submitted choices rather than trusting client-supplied totals. Referenced blueprints cannot be edited in place. New research revisions preserve existing ships and pending construction. Weapons capture application-defined capabilities. Ship instances retain actual cargo, fuel, electrical state and component damage. Existing shipyard access, paid staffing, construction installments, service fees and saved deployment missions are reusable.

These boundaries should continue: engine-owned immutable evaluation, control-layer tick validation and frontend snapshot rendering. Blueprint editing and an actual ship refit must remain separate operations.

## Draft: A richer blueprint model

Use immutable records for the following information, keeping choices separate from calculated output:

- **Hull definition:** A stable frame identity, structural technology application, material, dimensions or section count, usable volume, external area and load limits. Size remains a design choice or derived result; tactical labels such as light or heavy are derived summaries.
- **Component installations:** Stable installation IDs, bay or mount identity, footprint, captured component revision, material receipt, manufacturing work and supported interfaces. Identical weapon mounts remain distinguishable.
- **Armor definition:** Covered area, material, thickness and captured armor technology properties. Derive mass from geometry and explicitly unit-defined density.
- **Engineering results:** Dry mass breakdown, cargo and fuel capacity, generation and peak demand, electrical endurance, crew support, loaded acceleration, structure, armor and maximum hull durability.
- **Production receipts:** Separate immutable bills and labor for frame, armor, each installation and integration. Technological applications supply capability, manufacturing and refit coefficients. Market prices remain live at the quote or payment tick.
- **Model lineage:** A design family and revision identifier for comparison, without assuming that a later revision is compatible with every existing ship in the family.

A bay and section model is sufficient for the first implementation. A full 3D editor and structural simulation are not prerequisites. Material and technology values should have explicit units and validation before introducing additional physics.

## Draft: Upgrade effort by actual change

| Change | Paid materials | Yard effort and constraints |
| --- | --- | --- |
| Replace a weapon in a compatible mount | Full replacement weapon receipt and any changed adapters or wiring | Removal, installation and commissioning work; the existing frame and armor remain |
| Add equipment into unused compatible space | New component receipt and required connections | Available bay volume, mount load, power, cooling and structural capacity must pass |
| Upgrade sensors or electronics | New captured hardware and integration materials | Keep unchanged installations and their damage; revalidate power and interfaces |
| Change generator or main drive | New equipment plus changed tanks, routing or supports | Greater integration work, compatible fuel storage and structure; unload incompatible stores explicitly |
| Change armor | Replacement plating and support materials | Removal and installation based on actual area and thickness; existing structural damage remains |
| Enlarge or reshape the hull | New structural sections and replaced plating, routing and supports | Major reconstruction, higher yard capability and significant downtime; cost can approach a new ship |
| Replace the structural material throughout | Broad structural replacement | Treat as a major rebuild and compare with new construction |

The quote should show retained equipment, replaced equipment, structural work, integration, total material cost, labor, elapsed time and unavailable capabilities. Difficulty follows changed structure and interfaces, not only percentage change in total mass. Coefficients and upgrade permissions belong to relevant technological applications.

Removed equipment should become explicit yard inventory only when supported by an implemented reuse or salvage path. Do not assume a full-cost refund. Repair and refit costs remain separately visible. Salvage cannot fund a refit unless the actual recovery and sale have occurred.

## Draft: Paid refit lifecycle

The player chooses an existing ship, an accessible target model and a suitable yard. A discarded quote identifies compatibility, required unloading, costs and expected downtime. A queued command revalidates ownership, model access, location, target revision, materials, funding and yard capacity on the simulation tick.

A saved refit order reserves the real ship and captures the target hardware and work plan. The ship is unavailable for military deployment or trade during work. Daily installments share finite paid yard capacity with other work. Stock or funding shortages pause progress. Reload preserves paid materials, removed or installed equipment and the remaining work.

Completion applies the target configuration to the same ship identity, using explicit installation mappings. Retained damage, hull damage and compatible supplies persist. New equipment starts in its commissioned condition and carries no invented ammunition, propellant or battery charge. Capacity reductions require actual unloading; incompatible stores are transferred or rejected rather than discarded or converted automatically.

Cancellation must account for actual work already performed. It cannot simply restore the old design after paid equipment has been removed. The first implementation should avoid promising cancellation rules before installed and stored equipment are represented.

AI decisions can later compare repair, refit and replacement against funded costs, downtime, mission usefulness and remaining fleet coverage. A fleet should keep necessary defense and escort coverage while ships are withdrawn for work.

## Recommended implementation order

1. Capture frame identity, stable installation identities and separate construction receipts. Correct the captured receipt estimation path.
2. Add variable hull capacity and a material-aware frame and armor calculation. Establish one maximum-hull-health definition and shared public/corporate blueprint evaluation.
3. Add a pure model comparison and refit quote. Verify that an unchanged hull weapon swap pays for the weapon and integration while hull enlargement quotes major structural work.
4. Add saved paid refit orders, ship reservations, finite yard work and immutable UI quotes. Begin with same-frame weapon, sensor and power upgrades.
5. Extend structural rebuilding and AI repair/refit/replacement selection once the simpler lifecycle is sound.

This order is staged to keep changes reviewable. The review does not implement a refit command or change current simulation behavior.

## Verification

The existing ShipDesignValidatorTest, BlueprintAuthorityTest, ShipDesignerOutcomeTest, AIShipResearchRevisionTest, WeaponApplicationDataTest, ShipyardApplicationDataTest and FleetNeedsLifecycleTest pass. These checks confirm the existing validation, ownership, captured research and paid build foundations; they do not establish variable hulls, component-level receipts or refit behavior.

Future limited checks should cover repeated identical mounts, reordered installations, retained damage, changed hardware under the same application ID, zero-change quotes, paid weapon swaps, armor-area stability, structural enlargement, shared yard capacity, stock shortages, incompatible fuel, overflow unloading and reload during partial work. No full weapon balance pass is needed for those checks.
