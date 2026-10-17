package com.spaceconquest.engine.ship;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;
/** Corporate preferences only; the shared factory supplies all physical capabilities and engineering limits. */
public record CorporateShipDesignPolicy(List<String> drives, List<String> cargoModules, List<String> hullMaterials,
        String armorMaterial, double armorThicknessCm, int maximumCargoModules, int maximumFuelTanks,
        int maximumCandidates, double spareCapacityFraction) {
    public CorporateShipDesignPolicy {
        drives = List.copyOf(drives); cargoModules = List.copyOf(cargoModules); hullMaterials = List.copyOf(hullMaterials);
        if (drives.isEmpty() || cargoModules.isEmpty() || hullMaterials.isEmpty() || armorMaterial == null || armorMaterial.isBlank()
                || maximumCargoModules < 1 || maximumCargoModules > 256 || maximumFuelTanks < 1 || maximumFuelTanks > 256 || maximumCandidates < 1 || maximumCandidates > 256
                || drives.stream().anyMatch(id -> !PropulsionCatalog.MAIN_DRIVE_IDS.contains(id))
                || cargoModules.stream().anyMatch(id -> ShipComponentCatalog.module(id) == null || ShipComponentCatalog.module(id).operationalStats().getOrDefault("cargoCapacityKg", 0.0) <= 0)
                || !Double.isFinite(armorThicknessCm) || armorThicknessCm < 0 || !Double.isFinite(spareCapacityFraction) || spareCapacityFraction < 0 || spareCapacityFraction > 1)
            throw new IllegalArgumentException("Invalid corporate design policy");
    }
    private static final class Loaded {
        static final CorporateShipDesignPolicy VALUE = read();
        private static CorporateShipDesignPolicy read() {
            try (var stream = CorporateShipDesignPolicy.class.getResourceAsStream("/corporate_ship_design_policy.json")) {
                if (stream == null) throw new IOException("Missing corporate design policy");
                return new ObjectMapper().readValue(stream, CorporateShipDesignPolicy.class);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    public static CorporateShipDesignPolicy current() { return Loaded.VALUE; }
}
