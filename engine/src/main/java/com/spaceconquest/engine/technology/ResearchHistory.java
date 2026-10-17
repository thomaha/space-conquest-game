package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.GameState;
import java.util.*;

public final class ResearchHistory {
    public static final int LIMIT_PER_EMPIRE = 32;
    private ResearchHistory() {}
    public static List<ResearchEvent> append(List<ResearchEvent> events, ResearchEvent event) {
        var result = new ArrayList<>(events); result.add(event);
        int excess = (int) result.stream().filter(value -> event.empireId().equals(value.empireId())).count() - LIMIT_PER_EMPIRE;
        for (var iterator = result.iterator(); iterator.hasNext() && excess > 0;) if (event.empireId().equals(iterator.next().empireId())) { iterator.remove(); excess--; }
        return List.copyOf(result);
    }
    public static GameState record(GameState state, ResearchEvent event) {
        return state.toBuilder().researchHistory(append(state.researchHistory(), event)).build();
    }
}
