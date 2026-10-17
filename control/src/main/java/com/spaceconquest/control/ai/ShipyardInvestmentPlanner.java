package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.ShipyardWorkCapacity;
import com.spaceconquest.engine.ship.ShipyardAccess;
import java.util.*;

/** Companies consider their own commercial yards before public strategic investment. */
public final class ShipyardInvestmentPlanner {
    private record Site(String owner, String system, String yard) {}
    public List<GameCommand> plan(GameState state, Empire empire) {
        var sites = new LinkedHashSet<Site>();
        state.orbitalStations().stream().filter(station -> station.modules().stream().anyMatch(module -> {
                    var capability = com.spaceconquest.engine.ship.ShipyardTechnology.yard(module.type());
                    return capability != null && capability.buildsShips() && capability.baseComplexity() > 0;
                }))
                .forEach(station -> sites.add(new Site(station.ownerEntityId(), station.systemId(), station.id())));
        state.industrialFacilities().stream().filter(facility -> com.spaceconquest.engine.ship.ShipyardTechnology.isSurface(facility.applicationId()))
                .forEach(facility -> sites.add(new Site(facility.ownerEntityId(), ConstructionMaterials.systemForBody(state, facility.planetId()), facility.planetId())));
        int previews = 0;
        for (var site : sites.stream().filter(item -> { var home = EconomicOwnership.empireFor(state, item.owner()); return home != null && empire.id().equals(home.id()); })
                .sorted(Comparator.comparingInt((Site item) -> empire.id().equals(item.owner()) ? 1 : 0).thenComparing(Site::yard).thenComparing(Site::owner)).toList()) {
            if (previews++ >= ShipyardInvestmentPolicy.current().maximumYardPreviews()) break;
            int designPreviews = 0;
            var designs = state.shipDesigns().stream().filter(design -> ShipyardAccess.permitted(state, design.ownerEntityId(), site.owner(), site.system()))
                    .sorted(Comparator.comparingInt((com.spaceconquest.engine.ship.ShipDesign design) -> state.shipConstructionOrders().stream()
                            .anyMatch(order -> design.id().equals(order.designId()) && site.yard().equals(order.yardBodyId())) ? 0 : 1)
                            .thenComparing(com.spaceconquest.engine.ship.ShipDesign::id)).toList();
            for (var design : designs) {
                if (designPreviews++ >= ShipyardInvestmentPolicy.current().maximumYardPreviews()) break;
                var upgrade = new RequestAIShipyardCapabilityCommand(site.owner(), design.id(), site.system(), site.yard());
                if (upgrade.validate(state)) return List.of(upgrade);
            }
            var command = new RequestAIShipyardExpansionCommand(site.owner(), site.system(), site.yard());
            if (command.validate(state)) return List.of(command);
        }
        return List.of();
    }
}
