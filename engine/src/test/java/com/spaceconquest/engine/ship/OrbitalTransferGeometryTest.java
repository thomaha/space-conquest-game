package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalTransferGeometryTest {
    private SolarSystem sol() throws Exception { return DataModelLoader.loadSolarSystems().getFirst(); }
    private Planet body(SolarSystem system, String id) {
        return system.planets().stream().filter(planet -> planet.id().equals(id)).findFirst().orElseThrow();
    }

    @Test void circularMotionHasPhysicalSpeedAndRepeatsWithoutDependingOnListOrder() throws Exception {
        var system = sol();
        var earth = body(system, "earth");
        var now = CircularOrbitalEphemeris.at(system, earth, 37.25);
        double period = CircularOrbitalEphemeris.periodDays(system, earth);
        assertEquals(365.25, period, 2);
        assertEquals(earth.distance() * 1000, now.radiusMeters(), .001);
        assertEquals(29780, now.speedMps(), 100);
        assertState(now, CircularOrbitalEphemeris.at(system, earth, 37.25 + period), .001);
        var reordered = new SolarSystem(system.id(), system.name(), system.description(), system.x(), system.y(), system.z(),
                system.sunMass(), system.sunDiameter(), system.sunColor(), system.planets().reversed(), system.asteroidBelts());
        assertEquals(now, CircularOrbitalEphemeris.at(reordered, earth, 37.25));
        var future = CircularOrbitalEphemeris.at(system, earth, 38.25);
        assertTrue(Math.hypot(future.xMeters() - now.xMeters(), future.yMeters() - now.yMeters()) > 2e9);
        assertEquals(0, now.xMeters() * now.vxMps() + now.yMeters() * now.vyMps(), 2);
    }

    @Test void deterministicLaunchWindowsMeetMovingPlanetsInBothDirections() throws Exception {
        var system = sol();
        for (boolean outward : new boolean[]{true, false}) {
            var source = body(system, outward ? "earth" : "mars");
            var target = body(system, outward ? "mars" : "earth");
            for (double epoch : new double[]{0, 100, 10000.125}) {
                var comparison = CircularOrbitalEphemeris.comparison(system, source, target, epoch, 500, 500);
                double launch = epoch + comparison.waitDays();
                var coast = new HohmannCoast(CircularOrbitalEphemeris.gravitationalParameter(system),
                        source.distance() * 1000, target.distance() * 1000,
                        CircularOrbitalEphemeris.phase(system, source, launch));
                var start = coast.at(0);
                var sourcePosition = CircularOrbitalEphemeris.at(system, source, launch);
                assertEquals(sourcePosition.xMeters(), start.xMeters(), .01);
                assertEquals(sourcePosition.yMeters(), start.yMeters(), .01);
                assertEquals(comparison.coastDays() * 86400, coast.coastSeconds(), .00001);
                var finish = coast.at(coast.coastSeconds());
                var targetPosition = CircularOrbitalEphemeris.at(system, target, launch + comparison.coastDays());
                assertEquals(targetPosition.xMeters(), finish.xMeters(), .1);
                assertEquals(targetPosition.yMeters(), finish.yMeters(), .1);
                assertEquals(comparison.arrivalExcessMps(), Math.hypot(finish.vxMps() - targetPosition.vxMps(),
                        finish.vyMps() - targetPosition.vyMps()), .00001,
                        "Position contact still needs funded velocity matching and planetary capture.");
                assertEquals(0, CircularOrbitalEphemeris.comparison(system, source, target, launch, 500, 500).waitDays(), .00001);
            }
        }
    }

    @Test void coastConservesEnergyAndAngularMomentumThroughAndBeyondMissedCapture() {
        for (boolean outward : new boolean[]{true, false}) {
            var coast = new HohmannCoast(1.3271244e20, outward ? 1.5e11 : 2.3e11,
                    outward ? 2.3e11 : 1.5e11, 1.3);
            var initial = coast.at(0);
            double momentum = initial.xMeters() * initial.vyMps() - initial.yMeters() * initial.vxMps();
            double energy = -coast.gravitationalParameter() / (2 * coast.axisMeters());
            for (double fraction : new double[]{0, .001, .25, .5, .9, 1, 1.1, 1.75, 2}) {
                var position = coast.at(coast.coastSeconds() * fraction);
                assertEquals(energy, position.speedMps() * position.speedMps() / 2
                        - coast.gravitationalParameter() / position.radiusMeters(), Math.abs(energy) * 1e-12);
                assertEquals(momentum, position.xMeters() * position.vyMps() - position.yMeters() * position.vxMps(),
                        Math.abs(momentum) * 1e-12);
            }
            assertState(initial, coast.at(2 * coast.coastSeconds()), .001);
            assertNotEquals(coast.at(coast.coastSeconds()), coast.at(1.1 * coast.coastSeconds()));
        }
    }

    @Test void analyticMotionDoesNotDependOnTickSubdivision() {
        var coast = new HohmannCoast(1.3271244e20, 1.5e11, 2.3e11, 0);
        double half = coast.coastSeconds() / 2, step = half / 1024, elapsed = 0;
        for (int tick = 0; tick < 1024; tick++) elapsed += step;
        assertState(coast.at(half), coast.at(elapsed), .1);
    }

    @Test void malformedGeometryAndTimeAreRejected() throws Exception {
        var system = sol();
        var earth = body(system, "earth");
        assertThrows(IllegalArgumentException.class, () -> CircularOrbitalEphemeris.at(system, earth, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> CircularOrbitalEphemeris.at(system, earth, -1));
        assertThrows(IllegalArgumentException.class, () -> CircularOrbitalEphemeris.at(null, earth, 0));
        assertEquals(0, new HohmannCoast(1, 1, 1, 0).eccentricity());
        assertThrows(IllegalArgumentException.class, () -> new HohmannCoast(0, 1, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new HohmannCoast(1, 1, 2, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new HohmannCoast(1, 1, 2, 0).at(-1));
    }

    private void assertState(CircularOrbitalEphemeris.State expected, CircularOrbitalEphemeris.State actual, double tolerance) {
        assertEquals(expected.xMeters(), actual.xMeters(), tolerance);
        assertEquals(expected.yMeters(), actual.yMeters(), tolerance);
        assertEquals(expected.vxMps(), actual.vxMps(), tolerance / 1000);
        assertEquals(expected.vyMps(), actual.vyMps(), tolerance / 1000);
    }
}
