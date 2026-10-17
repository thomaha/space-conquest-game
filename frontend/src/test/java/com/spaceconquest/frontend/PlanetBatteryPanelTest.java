package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PlanetBatteryPanelTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }
    private void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable failure) { done.completeExceptionally(failure); } });
        done.get(10, TimeUnit.SECONDS);
    }
    private GameState world() throws Exception {
        var unlocked = new ArrayList<String>(); DataModelLoader.loadTechnologies().forEach(t -> { unlocked.add(t.id()); t.applications().forEach(a -> unlocked.add(a.id())); });
        var owner = new Empire("own", "Own", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(), unlocked, List.of());
        var earth = new Planet("earth", "Earth", "", 5.97e24, 9.81, 149600000, 0, 12742, "terrestrial", "", true, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(earth), List.of());
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        var battery = new PlanetaryDefenseBattery("battery", "earth", CombatModuleCatalog.application(weapon.id()).id(), 1, true);
        var hub = new CommercialHub("hub", "earth", 0, 1e6, 1000, 10, Map.of(weapon.ammunitionId(), new MarketOrder(weapon.ammunitionId(), 1000, 0, 2, 0)));
        var yard = new IndustrialFacility("yard", "earth", "surface_shipyard", "own", "PUBLIC_STATE", 4, 100, "industrial_worker", false, 0);
        return GameState.builder().empires(List.of(owner)).solarSystems(List.of(system)).commercialHubs(List.of(hub)).industrialFacilities(List.of(yard))
                .auxiliaryForces(new AuxiliaryForces(List.of(), List.of(new AuxiliaryForces.OwnedBattery("own", battery)))).build();
    }
    private TitledPane panel(PlanetDetailView view) {
        return (TitledPane) ((ScrollPane) view.getRoot().getChildren().getLast()).getContent().lookup("#planet-batteries");
    }
    @Test void noFleetIsRequiredForDiagnosticsPaidAmmunitionBuildsAndDismantling() throws Exception {
        var state = world(); var rows = AuxiliarySupplyCard.inspectSurface(state, "earth", "own");
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-readiness")));
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-repair")));
        var buy = rows.stream().filter(r -> r.id().equals("battery-buy")).findFirst().orElseThrow(); assertTrue(buy.available());
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-dismantle") && r.available()));
        var build = rows.stream().filter(r -> r.command() instanceof BuildAuxiliaryWeaponCommand).findFirst().orElseThrow();
        assertTrue(AuxiliarySupplyCard.inspectSurface(state.toBuilder().auxiliaryForces(AuxiliaryForces.empty()).build(), "earth", "own")
                .stream().anyMatch(r -> r.command() instanceof BuildAuxiliaryWeaponCommand));
        assertTrue(rows.stream().filter(r -> r.command() instanceof BuildAuxiliaryWeaponCommand)
                .allMatch(r -> ((BuildAuxiliaryWeaponCommand) r.command()).carrierShipId() == null));
        var queue = new CommandQueue(); var paid = queue.submitTracked(buy.command()); var ordered = queue.submitTracked(build.command());
        var result = queue.drainAndExecute(state); assertEquals(CommandOutcome.EXECUTED, paid.join()); assertEquals(CommandOutcome.EXECUTED, ordered.join());
        assertTrue(result.fleets().isEmpty()); assertEquals(1, result.auxiliaryForces().orders().size());
        assertTrue(AuxiliarySupplyCard.inspectSurface(result, "earth", "own").stream()
                .anyMatch(r -> r.id().equals(result.auxiliaryForces().orders().getFirst().id()) && r.text().contains("Building battery")));
        var battery = state.auxiliaryForces().batteries().getFirst().battery(); double mass = battery.weapon().operationalStats().get("ammunitionMassKg");
        assertEquals(mass, result.auxiliaryForces().batteries().getFirst().battery().stores().ammunitionKg().get(battery.weapon().ammunitionId()));
        assertEquals(2 * mass, state.empires().getFirst().treasuryCredits() - result.empires().getFirst().treasuryCredits());
        assertTrue(battery.stores().ammunitionKg().isEmpty());
        var dryQueue = new CommandQueue(); var rejected = dryQueue.submitTracked(buy.command());
        var dry = state.withCommercialHubs(List.of()); assertEquals(dry.auxiliaryForces(), dryQueue.drainAndExecute(dry).auxiliaryForces());
        assertEquals(CommandOutcome.REJECTED, rejected.join());
    }
    @Test void chargingNeedsAnOwnedSurfaceSourceAndForeignOrOtherBodyHardwareIsHidden() throws Exception {
        var state = world();
        assertTrue(AuxiliarySupplyCard.inspectSurface(state, "earth", "own").stream().noneMatch(r -> r.id().startsWith("battery-charge")));
        var ship = new ShipInstance("source", "design", "own", 1000, 0, 0, Map.of())
                .withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 100, false, 1, 1, 0, 0, 0, 0, 0));
        var fleet = new Fleet("source-fleet", "Source", "own", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.surface("earth")));
        var rows = AuxiliarySupplyCard.inspectSurface(state.withFleets(List.of(fleet)), "earth", "own");
        assertTrue(rows.stream().anyMatch(r -> r.id().equals("battery-charge-source") && r.available()));
        var orbital = fleet.withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth")));
        assertTrue(AuxiliarySupplyCard.inspectSurface(state.withFleets(List.of(orbital)), "earth", "own").stream().noneMatch(r -> r.id().startsWith("battery-charge")));
        var foreign = new ShipInstance("foreign-source", "design", "foreign", 1000, 0, 0, Map.of()).withPowerState(ship.powerState());
        assertTrue(AuxiliarySupplyCard.inspectSurface(state.withFleets(List.of(fleet.withShips(List.of(foreign)))), "earth", "own")
                .stream().noneMatch(r -> r.id().startsWith("battery-charge")));
        assertTrue(AuxiliarySupplyCard.inspectSurface(state, "moon", "own").stream().noneMatch(r -> r.id().equals("battery")));
        assertTrue(AuxiliarySupplyCard.inspectSurface(state, "earth", "foreign").stream().noneMatch(r -> r.command() != null || r.id().equals("battery")));
    }
    @Test void planetViewPreservesExpansionAndRejectsActionsAfterChangingBody() throws Exception {
        var state = world(); var system = state.solarSystems().getFirst();
        var body = PlanetaryBodyEntry.fromPlanet(system.planets().getFirst(), system, List.of());
        var other = new PlanetaryBodyEntry("moon", "Moon", "sol", "Sol", false, null, body.planet(), null, 0, false, true, 1, 1000, 0, body.biomeGrid());
        var controller = new HumanController(); var view = new AtomicReference<PlanetDetailView>(); var pane = new AtomicReference<TitledPane>();
        fx(() -> {
            view.set(new PlanetDetailView(null)); view.get().initializeAfterConstruction(); view.get().setHumanController(controller);
            view.get().setPlayerEmpireId("own"); view.get().setSelectedBody(body); view.get().updateData(state); view.get().show();
            pane.set(panel(view.get())); assertNotNull(pane.get()); assertFalse(pane.get().isExpanded()); pane.get().setExpanded(true);
        });
        var oldBuy = new AtomicReference<Button>(); long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (oldBuy.get() == null && System.nanoTime() < deadline) {
            fx(() -> oldBuy.set((Button) pane.get().getContent().lookup("#aux-supply-battery-buy"))); if (oldBuy.get() == null) Thread.sleep(20);
        }
        assertNotNull(oldBuy.get());
        fx(() -> {
            view.get().setSelectedBody(other); oldBuy.get().fire();
            assertFalse(panel(view.get()).isExpanded());
            view.get().setSelectedBody(body); assertTrue(panel(view.get()).isExpanded());
            view.get().updateData(state.toBuilder().turn(1).build()); assertTrue(panel(view.get()).isExpanded());
            view.get().hide();
        });
        assertEquals(state.auxiliaryForces(), controller.getCommandQueue().drainAndExecute(state).auxiliaryForces());
    }
}
