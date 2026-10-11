package com.spaceconquest.control;

import com.spaceconquest.control.command.AddStationModuleCommand;
import com.spaceconquest.engine.ship.BaseRepairCatalog;
import com.spaceconquest.engine.macrostructure.StationModule;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BaseRepairModuleTest {
    @Test void workshopConstructionRequiresOwnedResearchAndCanonicalSlotsPowerAndStaff() {
        var fixture = new SensorModuleTest(); var researched = fixture.world(List.of("space_stations", "electricity", "industrial_production"));
        var station = researched.orbitalStations().getFirst();
        var request = new AddStationModuleCommand(station.id(), "Free repairs", StationModule.TYPE_REPAIR_WORKSHOP,
                0, 0, 0, 9999, "", 0);
        assertFalse(request.validate(fixture.world(List.of("space_stations", "electricity"))));
        assertTrue(request.validate(researched));
        var after = request.apply(researched); var project = after.constructionProjects().getFirst(); var module = project.plannedModule();
        assertEquals(BaseRepairCatalog.module().slotSize(), module.slotSize()); assertEquals(5000, module.dryMassKg());
        assertEquals(50, module.powerDrawKw()); assertEquals(0, module.powerOutputKw()); assertEquals(2, module.requiredWorkers());
        assertEquals("engineer", module.workforceProfessionId()); assertFalse(project.requiredMaterialsKg().isEmpty());
        assertEquals(researched.orbitalStations(), after.orbitalStations());
    }
}
