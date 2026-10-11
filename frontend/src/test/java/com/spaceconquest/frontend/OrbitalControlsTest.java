package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalControlsTest {
    @Test void bodyRecoveryControlsUseParkingAltitudeInTheSavedFrame() throws Exception {
        var initial = state(); var controller = new HumanController();
        for (String id : List.of("earth", "moon")) {
            var body = OrbitalBody.find(initial, "sol", id); assertNotNull(body);
            var coast = new HohmannCoast(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass(),
                    body.diameter() * 500 + 500000, body.diameter() * 500 + 550000, .3);
            var itinerary = new OrbitalFlight.Itinerary(id, id, 500, 550, 0, 0, coast, ShipSolarEnvironment.DARK,
                    List.of(new OrbitalFlight.Maneuver(0, 1, Map.of()), new OrbitalFlight.Maneuver(coast.coastSeconds(), 1, Map.of()),
                            new OrbitalFlight.Maneuver(coast.coastSeconds() + 3600, 1, Map.of())), body.frame());
            var orbital = new OrbitalFlight(itinerary, coast.coastSeconds(), 1, OrbitalFlight.Status.MISSED, "");
            var fleet = new Fleet("failed", "Failed", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(),
                    new FleetLocation(FleetLocation.Site.orbit(id, 500), FleetLocation.Site.orbit(id, 550), 1,
                            itinerary.totalSeconds() / 86400, null, orbital));
            var snapshot = initial.withFleets(List.of(fleet));
            fx(() -> {
                var card = StellarRecoveryCard.create(snapshot, fleet, controller, new Label());
                var altitude = (Spinner<?>) card.lookup("#orbital-recovery-altitude"); assertNotNull(altitude);
                assertEquals(550, (Double) altitude.getValue(), .001); assertNull(card.lookup("#stellar-recovery-radius"));
                assertNull(snapshot.fleets().getFirst().location().localFlight()); assertEquals(0, controller.getCommandQueue().size());
                return null;
            });
        }
    }
    @Test void failedStellarCaptureOffersOrbitChoiceWithoutConvertingTheSnapshot() throws Exception {
        var state = state();
        var system = state.solarSystems().stream().filter(item -> item.id().equals("sol")).findFirst().orElseThrow();
        var coast = new HohmannCoast(CircularOrbitalEphemeris.gravitationalParameter(system), 1e11, 2e11, .3);
        var itinerary = new OrbitalFlight.Itinerary("earth", "mars", 500, 1000, 0, 0, coast, ShipSolarEnvironment.DARK,
                List.of(new OrbitalFlight.Maneuver(0, 1, Map.of()), new OrbitalFlight.Maneuver(coast.coastSeconds(), 1, Map.of()),
                        new OrbitalFlight.Maneuver(coast.coastSeconds() + 3600, 1, Map.of())));
        var orbital = new OrbitalFlight(itinerary, coast.coastSeconds() + 43200, 1, OrbitalFlight.Status.MISSED, "");
        var fleet = new Fleet("failed", "Failed", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(),
                new FleetLocation(FleetLocation.Site.orbit("earth", 500), FleetLocation.Site.orbit("mars", 1000), 1,
                        itinerary.totalSeconds() / 86400, null, orbital));
        var snapshot = state.withFleets(List.of(fleet)); var controller = new HumanController();
        var savedFlight = snapshot.fleets().getFirst().location().orbitalFlight();
        fx(() -> {
            var card = StellarRecoveryCard.create(snapshot, fleet, controller, new Label());
            var radius = (Spinner<?>) card.lookup("#stellar-recovery-radius");
            assertNotNull(radius); assertNotNull(card.lookup("#stellar-recovery-phase"));
            assertEquals(orbital.position().radiusMeters() / (ShipSolarEnvironment.AU_KM * 1000), (Double) radius.getValue(), 1e-9);
            assertTrue(((Button) card.lookup("#recover-stellar-travel")).isDisabled());
            assertSame(savedFlight, snapshot.fleets().getFirst().location().orbitalFlight());
            assertNull(snapshot.fleets().getFirst().location().localFlight());
            assertEquals(0, controller.getCommandQueue().size()); return null;
        });
    }
    @Test void stellarCancellationQueuesWithoutMutatingPhysicalState() throws Exception {
        var initial = state(); var controller = new HumanController();
        var start = new LocalSiteGeometry.Point(1e11, 0, 0);
        var acceleration = new LocalSiteGeometry.Point(.01, 0, 0);
        var trajectory = new VectorFlight.Trajectory(start, new LocalSiteGeometry.Point(0, 30000, 0), acceleration,
                acceleration, 21600, 43200, new TwoBodyFlight.Gravity(CircularOrbitalEphemeris.gravitationalParameter(
                        initial.solarSystems().stream().filter(item -> item.id().equals("sol")).findFirst().orElseThrow()), 1e9));
        var vector = trajectory.at(0); var geometry = new LocalSiteGeometry.Leg(start, new LocalSiteGeometry.Point(1.1e11, 0, 0), 1e10);
        var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), false, false, null, vector);
        var fleet = new Fleet("recovery", "Recovery", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(),
                new FleetLocation(FleetLocation.Site.deepSpace(), FleetLocation.Site.deepSpace("sol_circular_coast"), 0, 1, flight));
        var snapshot = initial.withFleets(List.of(fleet));
        fx(() -> {
            var card = StellarRecoveryCard.create(snapshot, fleet, controller, new Label());
            ((Button) card.lookup("#cancel-stellar-travel")).fire();
            assertFalse(snapshot.fleets().getFirst().location().localFlight().interrupted());
            assertEquals(1, controller.getCommandQueue().size()); return null;
        });
        var changed = controller.getCommandQueue().drainAndExecute(snapshot);
        assertTrue(changed.fleets().getFirst().location().localFlight().interrupted());
        assertEquals(vector, changed.fleets().getFirst().location().localFlight().vectorFlight());
    }
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) { /* Already started. */ }
    }
    private <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private GameState state() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "space_stations"), List.of());
        return GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner)).build();
    }
    @Test void altitudeControlQueuesConstructionAndRejectsAnOrbitOutsideTheSphere() throws Exception {
        var initial = state();
        var controller = new HumanController();
        fx(() -> {
            var card = OrbitalStationConstructionCard.create(initial, "owner", controller);
            var altitude = (Spinner<?>) card.lookup("#station-parking-altitude");
            var build = (Button) card.lookup("#queue-altitude-station");
            ((javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory) altitude.getValueFactory()).setValue(1e9);
            assertTrue(build.isDisabled());
            ((javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory) altitude.getValueFactory()).setValue(10000.0);
            assertFalse(build.isDisabled());
            build.fire();
            assertTrue(initial.constructionProjects().isEmpty());
            assertEquals(1, controller.getCommandQueue().size());
            return null;
        });
        var next = controller.getCommandQueue().drainAndExecute(initial);
        assertEquals(10000, next.constructionProjects().getFirst().parkingAltitudeKm());
        assertTrue(next.orbitalStations().isEmpty(), "Construction is queued rather than awarded immediately.");
    }
    @Test void moonChoiceQueuesTheSelectedLunarParkingAltitude() throws Exception {
        var initial = state(); var controller = new HumanController();
        fx(() -> {
            var card = OrbitalStationConstructionCard.create(initial, "owner", controller);
            var bodies = (ComboBox<?>) card.lookup("#station-parking-body");
            int moonIndex = -1;
            for (int index = 0; index < bodies.getItems().size(); index++)
                if (bodies.getItems().get(index).toString().startsWith("Moon (Earth)")) moonIndex = index;
            assertTrue(moonIndex >= 0); bodies.getSelectionModel().select(moonIndex);
            var altitude = (Spinner<?>) card.lookup("#station-parking-altitude");
            var values = (javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory) altitude.getValueFactory();
            var build = (Button) card.lookup("#queue-altitude-station");
            values.setValue(100000.0); assertTrue(build.isDisabled());
            values.setValue(100.0); assertFalse(build.isDisabled()); build.fire();
            assertTrue(initial.constructionProjects().isEmpty());
            return null;
        });
        var changed = controller.getCommandQueue().drainAndExecute(initial);
        assertEquals("moon", changed.constructionProjects().getFirst().targetCelestialId());
        assertEquals(100, changed.constructionProjects().getFirst().parkingAltitudeKm());
    }

    @Test void orbitalCancellationIsQueuedAndLeavesTheRealDepartureBase() throws Exception {
        var coast = new HohmannCoast(1e20, 1e11, 2e11, 0);
        var events = List.of(new OrbitalFlight.Maneuver(10, 1, Map.of()),
                new OrbitalFlight.Maneuver(10 + coast.coastSeconds(), 1, Map.of()),
                new OrbitalFlight.Maneuver(3610 + coast.coastSeconds(), 1, Map.of()));
        var itinerary = new OrbitalFlight.Itinerary("earth", "mars", 100000, 8000, 0, 10, coast, ShipSolarEnvironment.DARK, events);
        var flight = new OrbitalFlight(itinerary, 0, 0, OrbitalFlight.Status.WAITING, "Waiting");
        var source = FleetLocation.Site.docked("earth-port");
        var location = FleetLocation.at(source).depart(FleetLocation.Site.docked("mars-port"), itinerary.totalSeconds() / 86400)
                .withOrbitalFlight(flight);
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(), location);
        var initial = state().withFleets(List.of(fleet));
        var controller = new HumanController();
        fx(() -> {
            var card = OrbitalTravelCard.status(fleet, controller, new Label());
            ((Button) card.lookup("#cancel-orbital-travel")).fire();
            assertEquals(flight, initial.fleets().getFirst().location().orbitalFlight());
            assertEquals(1, controller.getCommandQueue().size());
            return null;
        });
        assertTrue(controller.getCommandQueue().drainAndExecute(initial).fleets().getFirst().location().isAt(source));
    }

    @Test void waitingShipsCanQueuePaidResupplyButCoastingShipsCannotAccessThePort() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "electricity", "industrial_production"), List.of());
        var port = new OrbitalStation("earth-port", "Port", "sol", "earth", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(100000.0);
        var stock = new java.util.HashMap<String, MarketOrder>();
        PropulsionCatalog.drive("mod_chemical_rocket").propellantMaterials(200)
                .forEach((id, kg) -> stock.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        var state = state().toBuilder().empires(List.of(owner)).orbitalStations(List.of(port))
                .commercialHubs(List.of(new CommercialHub("market", "earth-port", 0, 1e6, 200, 10, stock)))
                .powerGrids(List.of(new PowerGridState("earth-port", 1000, 0, 1000, 1000, 1000, false))).build();
        var blueprint = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("design", "Freighter", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false), "steel", 0));
        assertTrue(blueprint.valid(), blueprint.errors().toString());
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        var coast = new HohmannCoast(1e20, 1e11, 2e11, 0);
        var itinerary = new OrbitalFlight.Itinerary("earth", "mars", 100000, 8000, 0, 10, coast, ShipSolarEnvironment.DARK,
                List.of(new OrbitalFlight.Maneuver(10, 1, Map.of()),
                        new OrbitalFlight.Maneuver(10 + coast.coastSeconds(), 1, Map.of()),
                        new OrbitalFlight.Maneuver(3610 + coast.coastSeconds(), 1, Map.of())));
        var location = FleetLocation.at(FleetLocation.Site.docked("earth-port"))
                .depart(FleetLocation.Site.docked("mars-port"), itinerary.totalSeconds() / 86400)
                .withOrbitalFlight(new OrbitalFlight(itinerary, 0, 0, OrbitalFlight.Status.WAITING, "Waiting"));
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), location);
        var initial = state.withShipDesigns(List.of(blueprint.design())).withFleets(List.of(fleet));
        var controller = new HumanController();
        fx(() -> {
            var view = new FleetManagementView(null);
            view.setHumanController(controller); view.setPlayerEmpireId("owner");
            view.initializeAfterConstruction(); view.updateData(initial); view.show();
            var content = ((javafx.scene.control.ScrollPane) view.getRoot().getChildren().getLast()).getContent();
            assertEquals("ship", ((ComboBox<?>) content.lookup("#ship-supply-carrier")).getValue());
            assertEquals("earth-port", ((ComboBox<?>) content.lookup("#ship-supply-source")).getValue());
            ((Button) content.lookup("#buy-main-propellant")).fire();
            var card = ShipPowerCard.create(initial, fleet, ship, blueprint.design(), controller, new Label());
            var charge = (Button) card.lookup("#buy-grid-electricity");
            assertNotNull(charge); charge.fire();
            assertEquals(2, controller.getCommandQueue().size());
            assertEquals(0, initial.fleets().getFirst().ships().getFirst().currentFuelKg());
            assertEquals(1000, initial.powerGrids().getFirst().currentStoredKwh());
            return null;
        });
        var paid = controller.getCommandQueue().drainAndExecute(initial);
        var supplied = paid.fleets().getFirst().ships().getFirst();
        assertEquals(100, supplied.currentFuelKg());
        assertEquals(90, supplied.powerState().batteryChargeKwh());
        assertEquals(900, paid.powerGrids().getFirst().currentStoredKwh());
        assertEquals(owner.treasuryCredits() - 100, paid.empires().getFirst().treasuryCredits());
        var underway = paid.fleets().getFirst().withLocation(location.withOrbitalFlight(
                new OrbitalFlight(itinerary, 10, 1, OrbitalFlight.Status.COAST, "Coasting")));
        fx(() -> {
            assertNull(ShipPowerCard.create(paid, underway, supplied, blueprint.design(), controller, new Label())
                    .lookup("#buy-grid-electricity"));
            return null;
        });
    }
}
