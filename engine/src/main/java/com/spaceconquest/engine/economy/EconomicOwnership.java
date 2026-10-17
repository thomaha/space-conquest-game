package com.spaceconquest.engine.economy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.engine.*;
import java.io.*;
import java.util.*;

/** Society-defined private enterprise eligibility and explicit payer affiliation. */
public final class EconomicOwnership {
    public record SocietyRule(String societyStructure, boolean corporationsAllowed) {}
    private EconomicOwnership() {}
    private static final class Rules {
        static final List<SocietyRule> VALUE = read();
        private static List<SocietyRule> read() {
            try (var stream = EconomicOwnership.class.getResourceAsStream("/society_economy_rules.json")) {
                if (stream == null) throw new IOException("Missing society economy rules");
                return List.copyOf(new ObjectMapper().readValue(stream, new TypeReference<List<SocietyRule>>() {}));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }
    public static boolean corporationsAllowed(Empire empire) {
        return empire != null && Rules.VALUE.stream().anyMatch(rule -> rule.corporationsAllowed()
                && rule.societyStructure().equalsIgnoreCase(empire.societyStructure()));
    }
    public static Empire empireFor(GameState state, String owner) {
        var empire = state.empires().stream().filter(item -> item.id().equals(owner)).findFirst().orElse(null);
        if (empire != null) return empire;
        return state.corporations().stream().filter(item -> item.id().equals(owner)).flatMap(corp -> state.empires().stream()
                .filter(item -> item.id().equals(corp.empireId()))).findFirst().orElse(null);
    }
    public static boolean transportOperator(GameState state, Empire empire, String owner) {
        return empire != null && (empire.id().equals(owner) || corporationsAllowed(empire) && state.corporations().stream()
                .anyMatch(corp -> corp.id().equals(owner) && empire.id().equals(corp.empireId()) && "TRANSPORT".equalsIgnoreCase(corp.marketOrientation())));
    }
    public static double cash(GameState state, String owner) {
        var empire = state.empires().stream().filter(item -> item.id().equals(owner)).findFirst().orElse(null);
        return empire == null ? state.corporations().stream().filter(item -> item.id().equals(owner)).mapToDouble(Corporation::liquidCapitalReserves).findFirst().orElse(0)
                : empire.treasuryCredits();
    }
}
