package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.ShipDesign;
import java.util.*;

/** Bounded first affordable mission build, using existing public templates and domestic service yards. */
public final class FleetNeedsConstructionPlanner {
    private FleetNeedsConstructionPlanner() {}
    public static List<GameCommand> plan(GameState state, Empire empire) {
        int previews = 0;
        for (var need : EmpireFleetNeeds.assess(state, empire))
            for (var design : state.shipDesigns().stream().filter(item -> empire.id().equals(item.ownerEntityId()) && EmpireFleetPlanner.combat(item.role()))
                    .sorted(Comparator.comparing(ShipDesign::id)).toList())
                for (String system : empire.controlledSystemIds().stream().sorted(Comparator.comparingInt((String id) -> id.equals(need.systemId()) ? 0 : 1).thenComparing(id -> id)).toList()) {
                    if (++previews > FleetNeedsPolicy.current().maximumBuildPreviews()) return List.of();
                    var command = new RequestAIFleetNeedBuildCommand(empire.id(), design.id(), system, need.systemId(), need.kind(), need.targetId());
                    if (command.validate(state)) return List.of(command);
                }
        return List.of();
    }
}
