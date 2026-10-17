package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.industry.IndustryRecipeCatalog;
import com.spaceconquest.engine.industry.PowerPlantCatalog;
import java.util.Set;

/** Reports operational consumers, rather than treating descriptive affected factors as implemented effects. */
public final class ApplicationIntegration {
    public enum Status { AVAILABLE, PARTIAL, PLANNED }
    public record Support(Status status, String consumer, String description) {}
    private static final Set<String> COMPONENTS = Set.of("rocket_engines", "methalox_rocket_engines", "hydrolox_rocket_engines",
            "fission_engines", "fusion_engines", "mpd_ion_drives", "antimatter_engines", "fission_reactors",
            "cryogenic_stasis_pod", "pressurized_cargo_holds", "scanners");
    private ApplicationIntegration() {}
    public static Support describe(TechnicalApplication app) {
        if (app.surveyCapability() != null) return new Support(Status.PARTIAL, "SurveyOperations / HouseholdEconomyProcessor",
                "Persistent field teams use paid local work and supplies. Automated orbital teams use paid equipment, healthy sensors, real travel and electrical reserves. Repeat coverage and current-format persistence work; detailed geology remains future work.");
        if (app.infrastructureService() != null) return new Support(Status.PARTIAL, "InfrastructureService / LaunchService",
                "Research, construction, paid crew work, skill, automation, propellant and electricity consume application capabilities. Service quotes include payroll, maintenance, tax and replacement reserves. Network investment and detailed equipment wear remain future work.");
        if (app.hullTechnology() != null) return new Support(Status.PARTIAL, "HullTechnology / ShipBlueprintFactory",
                "Application research raises maximum custom hull capacity and structural strength for new designs. Ship classes do not impose sizes or mounts.");
        if (app.combatModule() != null) return new Support(Status.AVAILABLE, "CombatModuleCatalog / ShipApplicationProduction",
                "Installed hardware uses captured application capabilities. Listed effect stats improve new blueprints; existing ships retain their captured values.");
        if (app.auxiliaryHardware() != null) return new Support(Status.AVAILABLE, "AuxiliaryApplicationCatalog",
                "Paid auxiliary construction and supplies use application-defined hardware. Existing units retain captured capabilities.");
        if (app.miningModule() != null) return new Support(Status.AVAILABLE, "ShipMiningCatalog / ShipMiningProcessor",
                "New ships capture application-defined extraction hardware. Paid mining consumes finite compatible deposits and delivers physical cargo.");
        if (app.shipyardTechnology() != null) return new Support(Status.PARTIAL, "ShipyardTechnology / ShipyardWorkCapacity",
                "Declared yard construction, manufacturing, work and staffing capabilities are consumed. Other descriptive infrastructure effects remain future work.");
        if (PowerPlantCatalog.find(app.id()) != null) return new Support(Status.PARTIAL, "PowerGenerationProcessor",
                "Staffed generation, fuel, application output and construction modifiers work. Environmental or pollution claims are limited to modeled grid rules.");
        if (app.productionRecipe() != null && app.productionRecipe().farming() != null)
            return new Support(Status.PARTIAL, "FarmingDefinition / IndustryMarketProcessor",
                    app.productionRecipe().farming().controlled() == null
                            ? "Application-defined land, site requirements, paid cultivation, saved growing cycles and market harvests work. Weather, soil quality and other descriptive effects remain future work."
                            : "Application-defined enclosure space, purchased utilities, water recovery, automation, growing cycles and market harvests work. Habitat damage, detailed climate simulation and crop survival during outages remain future work.");
        if (IndustryRecipeCatalog.find(app.id()) != null) return new Support(Status.PARTIAL, "IndustryMarketProcessor",
                "Paid recipe inputs, outputs, workforce, power and application production modifiers work. Other descriptive factors are not automatic global bonuses.");
        if (IndustryRecipeCatalog.all().stream().filter(r -> r.refineryProcess() != null)
                .flatMap(r -> r.refineryProcess().profiles().stream()).anyMatch(p -> p.requiredApplicationId().equals(app.id())))
            return new Support(Status.PARTIAL, "RefineryProcess",
                    "Discovery enables application-defined recovery fractions and process costs on compatible refinery equipment. Refinement multipliers do not increase recovery fractions.");
        if (COMPONENTS.contains(app.id())) return new Support(Status.PARTIAL, "ShipApplicationProduction",
                "New components capture output, capacity, complexity and manufacturing modifiers. Legacy baseline propulsion and reactor equipment remain foundation-gated.");
        if (unlocksProduction(app.id())) return new Support(Status.PARTIAL, "Application research prerequisites",
                "Discovery unlocks research of application-defined industrial processes. Descriptive factors do not grant automatic production bonuses.");
        if (Set.of("automated_assembly_lines", "cybernetic_workforce_integration").contains(app.id()))
            return new Support(Status.PARTIAL, "ShipyardWorkCapacity", "Shipyard staffing productivity works; other factory, complexity and vulnerability effects remain future work.");
        if (Set.of("gene_sequencing", "gene_editing", "gene_therapy").contains(app.id()))
            return new Support(Status.PARTIAL, "PopulationProcessor", "A provisional lifespan bonus works. Trait editing, disease resistance and research-speed effects remain future work.");
        if ("space_stations".equals(app.id())) return new Support(Status.PARTIAL, "BuildOrbitalStationCommand / AddStationModuleCommand",
                "Station construction and module eligibility work; application-driven habitation and refinement effects remain future work.");
        return new Support(Status.PLANNED, "None", "Future work: this application has no operational consumer. Research does not currently provide its described gameplay effects.");
    }
    private static boolean unlocksProduction(String id) {
        try {
            return com.spaceconquest.engine.DataModelLoader.loadTechnologies().stream().flatMap(t -> t.applications().stream())
                    .anyMatch(a -> a.productionRecipe() != null && a.requiredTechnologies().contains(id));
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
