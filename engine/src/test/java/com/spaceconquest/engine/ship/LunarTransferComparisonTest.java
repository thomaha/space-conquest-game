package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LunarTransferComparisonTest {
    @Test void bothDirectionsMeetMovingEndpointsAndUseTheCorrectGravityWells() throws Exception {
        var system = DataModelLoader.loadSolarSystems().getFirst();
        var earth = system.planets().stream().filter(body -> body.id().equals("earth")).findFirst().orElseThrow();
        var moon = earth.moons().stream().filter(body -> body.id().equals("moon")).findFirst().orElseThrow();
        for (boolean outward : List.of(true, false)) for (double epoch : List.of(0.0, 365.0)) {
            var result = LunarTransferComparison.compare(system, earth, moon, epoch, 2000, 500, outward);
            var coast = result.coast(); var orbit = result.orbit();
            assertEquals(earth.mass() * PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT, coast.gravitationalParameter());
            assertTrue(orbit.coastDays() > 4 && orbit.coastDays() < 6);
            double arrivalEpoch = epoch + orbit.waitDays() + orbit.coastDays();
            double targetPhase = outward ? LunarTransferComparison.moonPhase(system, earth, moon, arrivalEpoch)
                    : LunarTransferComparison.parkingPhase(earth, 2000, arrivalEpoch);
            var arrival = coast.at(coast.coastSeconds());
            double radius = outward ? moon.distance() * 1000 : (earth.diameter() / 2 + 2000) * 1000;
            assertEquals(radius * Math.cos(targetPhase), arrival.xMeters(), .01);
            assertEquals(radius * Math.sin(targetPhase), arrival.yMeters(), .01);
            double moonRadius = (moon.diameter() / 2 + 500) * 1000;
            double lunarPeriod = 2 * Math.PI * Math.sqrt(moonRadius * moonRadius * moonRadius
                    / (moon.mass() * PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT));
            assertEquals(lunarPeriod, outward ? orbit.arrivalParkingPeriodSeconds() : orbit.departureParkingPeriodSeconds(), 1e-8);
            assertEquals(result, LunarTransferComparison.compare(system, earth, moon, epoch, 2000, 500, outward));
            assertTrue(orbit.totalDeltaVMps() > 3000 && orbit.totalDeltaVMps() < 4500);
        }
    }

    @Test void unrelatedParentAndUnsupportedSpheresAreRejected() throws Exception {
        var system = DataModelLoader.loadSolarSystems().getFirst();
        var earth = system.planets().stream().filter(body -> body.id().equals("earth")).findFirst().orElseThrow();
        var mars = system.planets().stream().filter(body -> body.id().equals("mars")).findFirst().orElseThrow();
        var moon = earth.moons().getFirst();
        assertThrows(IllegalArgumentException.class, () -> LunarTransferComparison.compare(system, mars, moon, 0, 2000, 500, true));
        assertThrows(IllegalArgumentException.class, () -> LunarTransferComparison.compare(system, earth, moon, 0, 400000, 500, true));
        assertThrows(IllegalArgumentException.class, () -> LunarTransferComparison.compare(system, earth, moon, 0, 2000, 100000, true));
        assertThrows(IllegalArgumentException.class, () -> LunarTransferComparison.compare(system, earth, moon, Double.NaN, 2000, 500, true));
    }
}
