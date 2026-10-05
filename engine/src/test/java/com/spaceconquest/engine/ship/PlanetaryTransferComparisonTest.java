package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.SolarSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetaryTransferComparisonTest {
    private SolarSystem sol() throws Exception { return DataModelLoader.loadSolarSystems().getFirst(); }

    @Test void earthMarsBenchmarkIncludesBothParkingOrbitManeuvers() throws Exception {
        var sol = sol();
        var earth = sol.planets().stream().filter(body -> body.id().equals("earth")).findFirst().orElseThrow();
        var mars = sol.planets().stream().filter(body -> body.id().equals("mars")).findFirst().orElseThrow();
        var orbit = PlanetaryTransferComparison.orbit(sol, earth, mars, 0);
        assertEquals(259, orbit.coastDays(), 2);
        assertEquals(2950, orbit.departureExcessMps(), 40);
        assertEquals(2650, orbit.arrivalExcessMps(), 40);
        assertTrue(orbit.departureDeltaVMps() > orbit.departureExcessMps(), "Earth escape must not be omitted.");
        assertTrue(orbit.captureDeltaVMps() > 1000, "Mars parking capture must require a real maneuver.");
        var inward = PlanetaryTransferComparison.orbit(sol, mars, earth, 0);
        assertEquals(orbit.coastDays(), inward.coastDays(), 1e-8);
        assertEquals(orbit.totalDeltaVMps(), inward.totalDeltaVMps(), 1e-8);
    }

    @Test void launchWaitActuallyAlignsTheMovingArrivalBody() throws Exception {
        var sol = sol();
        var source = sol.planets().get(2);
        var target = sol.planets().get(3);
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * sol.sunMass();
        double rate = Math.sqrt(mu / Math.pow(target.distance() * 1000, 3))
                - Math.sqrt(mu / Math.pow(source.distance() * 1000, 3));
        var ideal = PlanetaryTransferComparison.orbit(sol, source, target, 0);
        for (double offset : new double[]{0, Math.PI / 2, Math.PI}) {
            double phase = ideal.requiredPhaseRadians() + offset;
            var orbit = PlanetaryTransferComparison.orbit(sol, source, target, phase);
            double error = Math.IEEEremainder(phase + rate * orbit.waitDays() * 86400 - orbit.requiredPhaseRadians(), 2 * Math.PI);
            assertEquals(0, error, 1e-9);
            if (offset == 0) assertEquals(0, orbit.waitDays());
            else assertTrue(orbit.waitDays() > 0);
            assertEquals(orbit.waitDays(), PlanetaryTransferComparison.orbit(sol, source, target, phase + 2 * Math.PI).waitDays(), 1e-8);
        }
    }

    @Test void unsupportedAndMalformedGeometryCannotProduceAComparison() throws Exception {
        var sol = sol();
        var earth = sol.planets().get(2);
        assertThrows(IllegalArgumentException.class, () -> PlanetaryTransferComparison.orbit(sol, earth, earth, 0));
        assertThrows(IllegalArgumentException.class, () -> PlanetaryTransferComparison.orbit(sol, earth, sol.planets().get(3), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PlanetaryTransferComparison.orbit(null, earth, sol.planets().get(3), 0));
        assertThrows(IllegalArgumentException.class, () -> PlanetaryTransferComparison.orbit(sol, earth, sol.planets().get(3), 0, -1, 500));
        assertThrows(IllegalArgumentException.class, () -> PlanetaryTransferComparison.orbit(sol, earth, sol.planets().get(3), 0, 1e9, 500));
    }

    @Test void parkingCostDependsOnAltitudeWithoutChangingInterplanetaryCoast() throws Exception {
        var sol = sol();
        var earth = sol.planets().get(2);
        var mars = sol.planets().get(3);
        var low = PlanetaryTransferComparison.orbit(sol, earth, mars, 0);
        var high = PlanetaryTransferComparison.orbit(sol, earth, mars, 0, 100000, 100000);
        assertTrue(high.departureDeltaVMps() < low.departureDeltaVMps());
        var intermediate = PlanetaryTransferComparison.orbit(sol, earth, mars, 0, 100000, 8000);
        assertTrue(intermediate.captureDeltaVMps() < low.captureDeltaVMps());
        assertTrue(high.captureDeltaVMps() > low.captureDeltaVMps(), "Higher altitude does not always reduce capture cost.");
        assertEquals(low.coastDays(), high.coastDays());
        assertEquals(low.waitDays(), high.waitDays());
    }
}
