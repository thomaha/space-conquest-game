package com.spaceconquest.control;

import com.spaceconquest.control.command.RescueFleetCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarRadiation;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.habitation.PassengerManifest;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.FlightMotion;
import com.spaceconquest.engine.ship.FlightRecoveryReadiness;
import com.spaceconquest.engine.ship.RescueOrder;
import com.spaceconquest.engine.ship.RescueRendezvous;
import com.spaceconquest.engine.ship.RescueStatus;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Repeatable catalog rescue audit with bounded physical, passenger and cargo ticks. */
class RescueBalanceAuditTest {
    private static final int MAX_DAYS = 180;
    private static final Race HUMAN = new Race("human", "Human", "Reference passengers", 1, 1,
            "Individualist", 9.81, 293, "carbon", "oxygen", 18, 45, "Organic", "Simple", 85);
    private enum Change { NONE, POWER_LOSS, TARGET_MOVES, PAYLOAD_LOST }
    private record Scenario(String name, String drive, String power, double gap, double velocity,
                            boolean stasis, double payloadKg, Change change) {}
    private record Row(Scenario scenario, double plannedDays, int simulatedDays, double propellant,
                       double electricalFuel, double reactorCommitted, int survivors, String outcome, String recovery) {}

    @Test void rescueReportMatchesPhysicalOutcomesAndIsRepeatable() throws Exception {
        List<Row> rows = new ArrayList<>();
        for (Scenario scenario : scenarios()) rows.add(measure(scenario));
        String report = report(rows);
        Path path = Path.of(System.getProperty("rescue.report", "target/rescue-balance-report.md"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
        List<Row> repeated = new ArrayList<>();
        for (Scenario scenario : scenarios()) repeated.add(measure(scenario));
        assertEquals(report, report(repeated));
        assertTrue(rows.stream().anyMatch(row -> row.outcome().equals("DELIVERED") && row.recovery().equals("Ready")));
        assertTrue(rows.stream().anyMatch(row -> row.outcome().equals("DELIVERED") && !row.recovery().equals("Ready")));
        assertTrue(rows.stream().anyMatch(row -> row.survivors() < 100 && row.simulatedDays() > 0));
    }

    private List<Scenario> scenarios() {
        List<Scenario> rows = new ArrayList<>();
        for (double gap : new double[]{1e7, 1e9, 1e10, SolarRadiation.AU_KM * 1000})
            rows.add(scenario("Chemical supply at " + number(gap / 1e6) + " million m", "mod_chemical_rocket",
                    ShipComponentCatalog.CHEMICAL_GENERATOR_ID, gap, false, 1000, Change.NONE));
        rows.add(scenario("Chemical supply, stasis", "mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID,
                1e9, true, 1000, Change.NONE));
        for (String drive : List.of("mod_chemical_rocket", "mod_fission_thruster"))
            for (double gap : new double[]{1e9, 1e10}) rows.add(scenario(drive + " with fission electricity at " + number(gap / 1e6) + " million m",
                    drive, ShipComponentCatalog.FISSION_REACTOR_ID, gap, false, .01, Change.NONE));
        rows.add(scenario("Fission electricity, stasis", "mod_fission_thruster", ShipComponentCatalog.FISSION_REACTOR_ID,
                1e9, true, .01, Change.NONE));
        rows.add(scenario("Short chemical rescue, larger donation", "mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID,
                1e7, false, 12000, Change.NONE));
        rows.add(scenario("Nuclear thermal rescue, larger donation", "mod_fission_thruster", ShipComponentCatalog.FISSION_REACTOR_ID,
                1e9, false, 1, Change.NONE));
        rows.add(scenario("Nuclear thermal rescue at 1 AU separation", "mod_fission_thruster", ShipComponentCatalog.FISSION_REACTOR_ID,
                SolarRadiation.AU_KM * 1000, false, .01, Change.NONE));
        rows.add(scenario("Delivered but tiny electrical payload", "mod_chemical_rocket", ShipComponentCatalog.FISSION_REACTOR_ID,
                1e9, false, .000001, Change.NONE));
        for (Change change : List.of(Change.POWER_LOSS, Change.TARGET_MOVES, Change.PAYLOAD_LOST))
            rows.add(scenario("Changed after departure: " + change, "mod_chemical_rocket", ShipComponentCatalog.FISSION_REACTOR_ID,
                    1e9, false, .01, change));
        return rows;
    }
    private Scenario scenario(String name, String drive, String power, double gap, boolean stasis, double payload, Change change) {
        return new Scenario(name, drive, power, gap, 100, stasis, payload, change);
    }

    private GameState fixture(Scenario scenario) {
        var moon = new Moon("moon", "Moon", "", 1, 1.62, 384400, 3474, "none", false, 0, List.of(), List.of());
        var planet = new Planet("earth", "Earth", "", 1, 9.81, SolarRadiation.AU_KM, 0, 12000,
                "terrestrial", "air", false, 0, List.of(), List.of(moon), List.of());
        var empire = new Empire("owner", "Owner", "human", "Individualist", 100000, 0, List.of("a"), List.of(), Map.of(),
                List.of("rocketry", "nuclear_fission", "electricity", "cryogenic_stasis"), List.of());
        var yard = new IndustrialFacility("yard", "earth", "surface_shipyard", "owner", IndustrialFacility.PUBLIC_STATE,
                3, 0, "engineer", false, 0);
        GameState state = GameState.builder().empires(List.of(empire)).industrialFacilities(List.of(yard))
                .solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG,
                        1392000, "yellow", List.of(planet), List.of()), new SolarSystem("b", "B", "", 1,
                        0, 0, SolarRadiation.SOLAR_MASS_KG, 1392000, "yellow", List.of(), List.of()))).build();
        ShipDesign donorDesign = design(state, scenario, "donorDesign", false);
        ShipDesign targetDesign = design(state, scenario, "targetDesign", scenario.stasis());
        String feed = scenario.power().equals(ShipComponentCatalog.FISSION_REACTOR_ID) ? "uranium" : "rp1";
        Map<String, Double> donorCargo = new HashMap<>(donorDesign.powerProfile().fuels().get(feed).materials(scenario.payloadKg()));
        Map<String, Double> targetCargo = new HashMap<>(Map.of("food_matrix", 2000.0, "oxygen_gas", 1000.0));
        if (scenario.drive().equals("mod_fission_thruster")) {
            donorCargo.merge("refined_uranium", donorDesign.fuelCapacityKg() * .001, Double::sum);
            targetCargo.put("refined_uranium", targetDesign.fuelCapacityKg() * .001);
        }
        double generatorKg = feed.equals("uranium") ? 1 : donorDesign.powerProfile().generatorTankKg();
        var donorPower = new ShipPowerState(donorDesign.powerProfile().fuels().get(feed).materials(generatorKg), "rp1", "uranium",
                donorDesign.powerProfile().batteryKwh(), false, 1, 1, 0, 0, 0, 0, 0);
        var targetPower = new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0);
        var donor = new ShipInstance("donor", donorDesign.id(), "owner", 100, 0, donorDesign.fuelCapacityKg(), donorCargo)
                .withPowerState(donorPower);
        var receiver = new ShipInstance("receiver", targetDesign.id(), "owner", 100, 0, targetDesign.fuelCapacityKg(),
                targetCargo, 100, "human", scenario.stasis() ? ShipInstance.MODE_CRYOGENIC_STASIS : ShipInstance.MODE_CONSCIOUS, targetPower);
        return state.withShipDesigns(List.of(donorDesign, targetDesign)).withFleets(List.of(
                new Fleet("rescuer", "Rescuer", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(donor)),
                new Fleet("target", "Target", "owner", "a", "b", 0, 0, .1, false, "PASSIVE", List.of(receiver),
                        FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_POWER_INTERRUPTED,
                        20, scenario.gap() * 10, 1, 0, scenario.velocity(), Map.of(), new FlightMotion(scenario.gap(), scenario.velocity(), null, 0))))
                .toBuilder().passengerManifests(List.of(new PassengerManifest("receiver", "earth", "moon", "human", Map.of(30, 100L)))).build();
    }
    private ShipDesign design(GameState state, Scenario scenario, String id, boolean stasis) {
        var evaluation = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification(id, id, "owner", ShipRole.CARGO_TRANSPORT,
                "steel", ShipComponentCatalog.workbenchModules(scenario.drive(), stasis, scenario.power()), "steel", 0));
        assertTrue(evaluation.valid(), scenario.name() + ": " + evaluation.errors());
        return evaluation.design();
    }

    private Row measure(Scenario scenario) {
        GameState initial = fixture(scenario);
        String feed = scenario.power().equals(ShipComponentCatalog.FISSION_REACTOR_ID) ? "uranium" : "rp1";
        var command = new RescueFleetCommand("rescuer", new RescueOrder("target", "donor", "receiver", ShipSupplyTransfer.Source.CARGO,
                ShipSupplyTransfer.Destination.ELECTRICAL_FUEL, feed, scenario.payloadKg()));
        var plan = command.preview(initial);
        if (plan == null) {
            assertSame(initial, command.apply(initial));
            return new Row(scenario, 0, 0, 0, 0, 0, 100, "Departure rejected", "Not evaluated");
        }
        double days = plan.trajectory().totalSeconds() / 86400;
        if (days > MAX_DAYS) return new Row(scenario, days, 0, 0, 0, 0, 100, "Plan only", "Not evaluated");
        GameState current = change(command.apply(initial), scenario.change());
        double initialElectricalFuel = fleet(current, "rescuer").ships().getFirst().generatorFuelMassKg();
        int simulated = 0;
        while (simulated < MAX_DAYS && status(current).phase() == RescueStatus.Phase.APPROACHING) {
            current = current.toBuilder().turn(current.turn() + 1).build();
            current = current.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(current), List.of(), List.of()));
            current = PassengerTransitProcessor.advanceDay(current, List.of(HUMAN));
            simulated++;
        }
        assertNotEquals(RescueStatus.Phase.APPROACHING, status(current).phase(), scenario.name());
        var donor = fleet(current, "rescuer").ships().getFirst();
        var receiver = fleet(current, "target").ships().getFirst();
        double propellant = fleet(initial, "rescuer").ships().getFirst().currentFuelKg() - donor.currentFuelKg();
        if (scenario.change() != Change.POWER_LOSS) assertEquals(plan.fuelKg().get("donor"), propellant, 1e-6);
        var readiness = FlightRecoveryReadiness.check(current, fleet(current, "target"));
        String recovery = readiness.ready() ? "Ready" : String.join(" ", readiness.blockers());
        return new Row(scenario, days, simulated, propellant,
                initialElectricalFuel - donor.generatorFuelMassKg(),
                plan.reactorKg().values().stream().mapToDouble(use -> use.quantityKg()).sum(), receiver.passengerCount(),
                status(current).phase().name(), recovery);
    }

    private GameState change(GameState state, Change change) {
        if (change == Change.NONE) return state;
        Fleet fleet = fleet(state, change == Change.TARGET_MOVES ? "target" : "rescuer");
        if (change == Change.TARGET_MOVES) {
            var motion = fleet.flightMotion();
            fleet = new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), "a", "b", 0, 0, .1, false, "PASSIVE",
                    fleet.ships(), fleet.location(), fleet.interstellarMode(), fleet.interstellarTravelDays(), fleet.interstellarDistanceMeters(),
                    1, 0, 150, Map.of(), new FlightMotion(motion.positionMeters(), 150, null, 0));
        } else {
            ShipInstance ship = fleet.ships().getFirst();
            if (change == Change.POWER_LOSS) ship = ship.withPowerState(ShipPowerState.empty().withRescueStatus(ship.powerState().rescueStatus()));
            else ship = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0,
                    ship.currentFuelKg(), Map.of(), 0, "", ship.transitMode(), ship.powerState());
            fleet = fleet.withShips(List.of(ship));
        }
        Fleet replaced = fleet;
        return state.withFleets(state.fleets().stream().map(item -> item.id().equals(replaced.id()) ? replaced : item).toList());
    }
    private Fleet fleet(GameState state, String id) { return RescueRendezvous.find(state, id); }
    private RescueStatus status(GameState state) { return fleet(state, "rescuer").ships().getFirst().powerState().rescueStatus(); }
    private String number(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private String precise(double value) { return String.format(Locale.ROOT, "%.6f", value); }

    private String report(List<Row> rows) {
        StringBuilder text = new StringBuilder("# Rescue balance report\n\n"
                + "## Reproduction\n\nGenerated by `RescueBalanceAuditTest`. Run from the repository root with Java 27:\n\n"
                + "```powershell\n$env:JAVA_HOME='C:\\java\\jdk-27'\nmvn --% -q -pl control -am -Dtest=RescueBalanceAuditTest -Dsurefire.failIfNoSpecifiedTests=false -Drescue.report=../RescueBalanceReport.md test\n```\n\n"
                + "## Assumptions\n\nValidated baseline catalog cargo ships use full main propellant tanks. Chemical generators start with 15,000 kg of RP-1/LOX; electrical reactors start with 1 kg uranium. Rescuers have a full battery. Disabled targets start with zero electricity, 100 passengers, 2,000 kg food and 1,000 kg oxygen. Nuclear thermal drives carry their full-tank reactor feed separately. Chemical electrical donations are 1,000 kg mixture; nuclear electrical donations are 0.01 kg uranium unless named otherwise.\n\n"
                + "Targets coast at 100 m/s. Starting separation varies along a synthetic corridor ten times that separation; these are distance probes, not real star separations. Power, cargo deterioration, passenger nutrition and outage mortality use engine ticks. Contact is processed before passenger accounting, so delivery enables next-day power and does not erase that day's outage. Passenger survival is measured at the end of the contact or failure day. Return travel, evacuation and passenger survival after contact are not simulated. Long accepted approaches over 180 days are plan-only and rejected departures never launch. Forecasts retain fixed initial mass; actual passenger and cargo losses do not change an active trajectory.\n\n"
                + "## Measurements\n\n| Scenario | Payload (kg) | Planned contact days | Tick days | Main propellant used (kg) | Electrical fuel used (kg) | Drive reactor committed (kg) | Passengers at outcome | Outcome |\n"
                + "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (Row row : rows) text.append("| ").append(row.scenario().name()).append(" | ")
                .append(precise(row.scenario().payloadKg())).append(" | ")
                .append(row.plannedDays() > 0 ? number(row.plannedDays()) : "—").append(" | ")
                .append(row.simulatedDays() > 0 ? row.simulatedDays() : "—").append(" | ")
                .append(row.simulatedDays() > 0 ? number(row.propellant()) : "—").append(" | ")
                .append(row.simulatedDays() > 0 ? precise(row.electricalFuel()) : "—").append(" | ")
                .append(row.simulatedDays() > 0 ? precise(row.reactorCommitted()) : "—").append(" | ")
                .append(row.simulatedDays() > 0 ? row.survivors() + "/100" : "—").append(" | ").append(row.outcome()).append(" |\n");
        text.append("\n## Recovery after outcome\n\n| Scenario | Current receiver readiness |\n| --- | --- |\n");
        for (Row row : rows) text.append("| ").append(row.scenario().name()).append(" | ").append(row.recovery().replace("|", "\\|")) .append(" |\n");
        text.append("\n## Tuning candidates\n\n- Compare conscious and stasis outage survival; stasis currently has a higher unpowered loss rate despite lower powered load.\n"
                + "- Review chemical electrical storage and donation size: a successful transfer can still leave too little electricity for onward travel.\n"
                + "- Compare chemical and nuclear thermal intercepts before changing thrust, exhaust velocity or main tank capacity.\n"
                + "- Current minimum-time plans nearly empty the rescuer's main tank. Evaluate a return propellant reserve before treating rescue delivery as a sustainable round trip.\n"
                + "- Inspect food deterioration alongside nutrition and electrical mortality on long approaches.\n"
                + "- Keep missed contact, lost payload and power failure distinct from successful delivery.\n");
        return text.toString();
    }
}
