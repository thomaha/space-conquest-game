package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ResearchExchangeTest {
    @TempDir Path directory;
    private Empire knowledge(Empire empire, List<String> ids) {
        return new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), empire.treasuryCredits(), empire.corporateTaxRate(),
                empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(), ids, empire.activeShipDesignIds());
    }
    private GameState world() {
        var state = new ResearchLifecycleTest().world(); var receiver = knowledge(state.empires().getFirst(), List.of("electricity"));
        var sender = new Empire("source", "Source", "human", "Individualist", 0, 0,
                List.of(), List.of(), Map.of(), List.of("electricity", "solar_power"), List.of());
        return state.withEmpires(List.of(receiver, sender)).withDiplomaticRelations(List.of(new DiplomaticRelation("source", "research", "COMMERCIAL_ALLIANCE", 0)));
    }
    private GameState start(GameState state) { return new StartResearchCommand("research", "solar_power", true, 3).apply(state); }
    private ResearchProject tick(GameState state) {
        return new ResearchTickProcessor().process(state, List.of(), new ResearchProcessor(new Random(42))).projects().getFirst();
    }
    @Test void sharingRequiresKnownTargetsCurrentAllianceAndPrerequisites() {
        var state = world(); var command = new ShareTechnologyCommand("source", "research", "solar_power");
        assertTrue(command.validate(state));
        assertFalse(command.validate(state.withDiplomaticRelations(List.of())));
        assertFalse(new ShareTechnologyCommand("source", "research", "unknown").validate(state));
        assertFalse(new ShareTechnologyCommand("research", "source", "solar_power").validate(state));
        assertFalse(command.validate(state.withEmpires(List.of(knowledge(state.empires().getFirst(), List.of()), state.empires().getLast()))));
        assertFalse(command.validate(state.withEmpires(List.of(state.empires().getFirst(), knowledge(state.empires().getLast(), List.of())))));
    }
    @Test void initialProgressIsAppliedOnceAndSpeedRequiresFundedLocalWork() {
        var state = world(); var share = new ShareTechnologyCommand("source", "research", "solar_power");
        var shared = share.apply(state); assertFalse(share.validate(shared));
        assertFalse(shared.empires().getFirst().unlockedTechIds().contains("solar_power"));
        var started = start(shared); double grant = ResearchPolicy.current().exchangeProgressFraction() * 500;
        assertEquals(grant, started.researchProjects().getFirst().accumulatedPoints());
        var reassigned = new StartResearchCommand("research", "solar_power", true, 2).apply(started);
        assertEquals(grant, reassigned.researchProjects().getFirst().accumulatedPoints());
        assertEquals(grant + 2 * (1 + ResearchPolicy.current().exchangeSpeedBonus()), tick(reassigned).accumulatedPoints());
        assertEquals(grant, tick(new ResearchLifecycleTest().scientists(reassigned, 0)).accumulatedPoints());
    }
    @Test void warKnowledgeLossAndRevocationRemoveBonusesWithoutRefundingProgress() {
        var state = start(new ShareTechnologyCommand("source", "research", "solar_power").apply(world()));
        double grant = state.researchProjects().getFirst().accumulatedPoints();
        assertEquals(grant + 3, tick(state.withDiplomaticRelations(List.of(new DiplomaticRelation("source", "research", "TOTAL_WAR", 0)))).accumulatedPoints());
        assertEquals(grant + 3, tick(state.withEmpires(List.of(state.empires().getFirst(), knowledge(state.empires().getLast(), List.of())))).accumulatedPoints());
        var id = state.technologyExchangeRoutes().getFirst().routeId();
        assertFalse(new RevokeTechnologyExchangeCommand("foreign", id).validate(state));
        var ended = new RevokeTechnologyExchangeCommand("research", id).apply(state);
        assertTrue(ended.technologyExchangeRoutes().isEmpty()); assertEquals(grant + 3, tick(ended).accumulatedPoints());
    }
    @Test void refinementDoesNotRecycleForeignStartingProgressAndDiskReloadPreservesTheOriginalGrant() throws Exception {
        var state = start(new ShareTechnologyCommand("source", "research", "solar_power").apply(world()));
        var manager = new SaveGameManager(directory); var file = directory.resolve("exchange.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T00:00:00");
        var loaded = manager.load(file).toGameState(state.turn(), "RUNNING");
        assertEquals(state.technologyExchangeRoutes(), loaded.technologyExchangeRoutes()); assertEquals(state.researchProjects(), loaded.researchProjects());
        assertEquals(tick(state), tick(loaded));
        var owned = state.withEmpires(List.of(knowledge(state.empires().getFirst(), List.of("electricity", "solar_power")), state.empires().getLast()))
                .withResearchProjects(List.of());
        assertEquals(0, start(owned).researchProjects().getFirst().accumulatedPoints());
    }
}
