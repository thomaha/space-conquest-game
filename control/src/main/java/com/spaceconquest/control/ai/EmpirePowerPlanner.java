package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.PowerPlantCatalog;
import java.util.*;

/** Observes distinct daily deficits; controller recreation begins a fresh observation window. */
public final class EmpirePowerPlanner {
    private record Observation(long lastTurn, int count, long firstTurn) {}
    private final Map<String, Observation> deficits = new HashMap<>();
    private long lastTurn = -1;
    public List<GameCommand> plan(GameState state, Empire empire) {
        if (state.turn() < lastTurn) deficits.clear();
        lastTurn = state.turn();
        var bodies = state.powerGrids().stream().map(grid -> grid.entityId()).distinct()
                .filter(body -> PowerInvestmentQuote.controls(state, empire, body)
                        && PowerInvestmentQuote.shortage(state, body) > 0).sorted().toList();
        deficits.keySet().retainAll(bodies);
        var eligible = new ArrayList<PowerInvestmentQuote>();
        for (String body : bodies) {
            var old = deficits.get(body);
            var observed = old == null || state.turn() > old.lastTurn() + 1
                    ? new Observation(state.turn(), 1, state.turn())
                    : state.turn() == old.lastTurn() ? old : new Observation(state.turn(), old.count() + 1, old.firstTurn());
            deficits.put(body, observed);
            PowerInvestmentQuote.candidates(state, empire, body).stream()
                    .filter(quote -> observed.count() >= PowerPlantCatalog.find(quote.applicationId()).shortageObservationTurns())
                    .forEach(eligible::add);
        }
        return eligible.stream().sorted(Comparator.comparingDouble(PowerInvestmentQuote::score).reversed()
                .thenComparing(PowerInvestmentQuote::bodyId).thenComparing(PowerInvestmentQuote::applicationId)
                .thenComparing(quote -> Objects.toString(quote.facilityId(), ""))).findFirst()
                .<List<GameCommand>>map(quote -> List.of(new RequestAIPowerInvestmentCommand(quote.empireId(), quote.bodyId(),
                        quote.applicationId(), quote.facilityId(), quote.targetTier(), deficits.get(quote.bodyId()).firstTurn())))
                .orElse(List.of());
    }
}
