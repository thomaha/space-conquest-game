package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.*;
import com.spaceconquest.engine.governance.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SensorSharingTest {
    @Test void rememberedContactsAreLocalKnowledgeAndNeverRelayedByLaterAgreements() throws Exception {
        var state = refresh(accept(world("global"), "global", "owner"));
        assertTrue(SensorSharing.view(state, "owner").contacts().stream().anyMatch(contact -> contact.fleetId().equals("target")));
        // The remaining target has no powered sensor coverage.
        state = refresh(state.withFleets(List.of(state.fleets().getLast())));
        assertTrue(SensorSharing.view(state, "owner").detectedFleetIds().isEmpty());
        assertFalse(SensorSharing.view(state, "owner").contacts().isEmpty());
        state = refresh(accept(state, "owner", "third"));
        assertTrue(SensorSharing.view(state, "third").contacts().isEmpty());
        var agreement = state.diplomaticProposals().stream().filter(proposal -> SensorSharing.between(proposal, "global", "owner")).findFirst().orElseThrow();
        var revoke = new RevokeSensorSharingCommand(agreement.id(), "owner");
        assertTrue(revoke.validate(state));
        state = refresh(revoke.apply(state));
        assertFalse(SensorSharing.agreement(state, "global", "owner"));
        assertFalse(SensorSharing.view(state, "owner").contacts().isEmpty());
    }
    private Corporation corporation(String id, String home) {
        return new Corporation(id, id, home, "earth", "TRANSPORT", 1000, List.of(), List.of(), List.of());
    }
    private Fleet observer(GameState state, String owner) {
        var source = state.fleets().getFirst(); var ship = source.ships().getFirst();
        return new Fleet("observer", "Observer", owner, "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("observer-ship", ship.designId(), owner, 100, 0, ship.currentFuelKg(), ship.storedCargoKg())
                        .withPowerState(ship.powerState())), FleetLocation.at(FleetLocation.Site.deepSpace()));
    }
    private GameState world(String observerOwner) throws Exception {
        var state = new OrbitalRendezvousTest().world();
        var third = new Empire("third", "Third", "human", "Individualist", 1000, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
        var target = state.fleets().getLast().withLocation(FleetLocation.at(FleetLocation.Site.deepSpace()));
        target = target.withShips(target.ships().stream().map(ship -> ship.withPowerState(
                new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 1, 0))).toList());
        return state.toBuilder().empires(List.of(state.empires().getFirst(), state.empires().getLast(), third))
                .diplomaticRelations(List.of()).orbitalStations(List.of())
                .corporations(List.of(corporation("domestic", "owner"), corporation("foreign", "enemy"), corporation("global", "")))
                .fleets(List.of(observer(state, observerOwner), target)).build();
    }
    private GameState refresh(GameState state) {
        return state.withFogOfWarStates(new SensorProcessor().updateSensorCoverage(state, List.of()));
    }
    private GameState accept(GameState state, String sender, String receiver) {
        var command = new ProposeDiplomaticPactCommand(sender, receiver, DiplomaticPact.SENSOR_DATA_SHARING);
        assertTrue(command.validate(state)); state = command.apply(state);
        var proposal = state.diplomaticProposals().getLast();
        assertFalse(SensorSharing.agreement(state, sender, receiver));
        var acceptance = new ResolveDiplomaticProposalCommand(proposal.id(), receiver, true);
        assertTrue(acceptance.validate(state)); return acceptance.apply(state);
    }

    @Test void affiliatedCorporateFleetAndStationHardwareJoinOneEmpireView() throws Exception {
        var state = refresh(world("domestic"));
        assertTrue(SensorSharing.view(state, "owner").isFleetDetected("target"));
        assertEquals(SensorSharing.view(state, "owner").detectedFleetIds(), SensorSharing.view(state, "domestic").detectedFleetIds());
        assertSame(SensorSharing.view(state, "owner"), SensorSharing.view(state, "domestic"));
        assertFalse(SensorSharing.view(state, "third").isFleetDetected("target"));
        var module = new StationModule("sensor", "Sensor", SensorModuleCatalog.BASIC, 1, 100, 1, 0, Map.of(), "engineer", 0, true);
        var station = new OrbitalStation("corporate-station", "Station", "sol", "", "domestic", OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE,
                10, List.of(module), Map.of(), 100, 1, 0, 0, 100, 100, "steel", 0, true);
        state = refresh(state.toBuilder().fleets(List.of(state.fleets().getLast())).orbitalStations(List.of(station)).build());
        assertTrue(SensorSharing.view(state, "owner").isFleetDetected("target"));
        state = refresh(state.toBuilder().orbitalStations(List.of()).build());
        assertFalse(SensorSharing.view(state, "owner").isFleetDetected("target"));
    }

    @Test void damagedCorporateAndDiplomaticObserversLoseLiveContactsWithoutLosingAgreementsOrExploration() throws Exception {
        for (String owner : List.of("domestic", "global", "owner")) {
            var state = world(owner); String viewer = "owner";
            if (owner.equals("global")) state = accept(state, "global", "owner");
            if (owner.equals("owner")) { state = accept(state, "owner", "third"); viewer = "third"; }
            state = refresh(state); assertTrue(SensorSharing.view(state, viewer).isFleetDetected("target"));
            var observer = state.fleets().getFirst(); var ship = observer.ships().getFirst();
            var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElseThrow();
            var damage = new HashMap<Integer, Double>();
            for (int mount = 0; mount < design.equippedModuleIds().size(); mount++)
                if (SensorModuleCatalog.module(design.equippedModuleIds().get(mount)) != null) damage.put(mount, 100.0);
            var agreements = state.diplomaticProposals();
            state = refresh(state.withFleets(List.of(observer.withShips(List.of(ship.withModuleDamage(new ShipModuleDamage(damage)))), state.fleets().getLast())));
            assertFalse(SensorSharing.view(state, viewer).isFleetDetected("target"));
            assertTrue(SensorSharing.view(state, viewer).isSystemExplored("sol"));
            assertEquals(agreements, state.diplomaticProposals());
            assertFalse(new ScanSystemCommand(viewer, "sol").validate(state));
        }
    }

    @Test void crossEmpireCorporationNeedsItsOwnBilateralGrantAndReceivesAuthorizedData() throws Exception {
        var state = world("global"); assertFalse(SensorCoverage.detects(state, "owner", state.fleets().getLast()));
        state = accept(state, "global", "owner"); state = refresh(state);
        assertTrue(SensorSharing.view(state, "owner").isFleetDetected("target"));
        assertFalse(SensorSharing.view(state, "third").isFleetDetected("target"));
        assertFalse(SensorSharing.view(state, "enemy").isFleetDetected("observer"), "The corporation is not affiliated with the enemy.");
        var grant = state.diplomaticProposals().getLast();
        assertFalse(new RevokeSensorSharingCommand(grant.id(), "third").validate(state));
        var revoked = refresh(new RevokeSensorSharingCommand(grant.id(), "owner").apply(state));
        assertFalse(SensorSharing.view(revoked, "owner").isFleetDetected("target"));
        var foreign = accept(world("foreign"), "owner", "foreign");
        assertTrue(SensorCoverage.detects(foreign, "owner", foreign.fleets().getLast()));
        assertFalse(SensorSharing.observerOwners(foreign, "owner").contains("enemy"));
        var war = new SetDiplomaticTierCommand("owner", "enemy", "TOTAL_WAR").apply(foreign);
        assertFalse(SensorSharing.agreement(war, "owner", "foreign"));
        assertFalse(SensorCoverage.detects(war, "owner", war.fleets().getLast()));
        var receiver = accept(world("owner"), "owner", "global");
        assertTrue(SensorCoverage.detects(receiver, "global", receiver.fleets().getLast()));
    }

    @Test void unacceptedRejectedAndExpiredProposalsNeverExposeCorporateData() throws Exception {
        var state = world("global");
        var command = new ProposeDiplomaticPactCommand("global", "owner", DiplomaticPact.SENSOR_DATA_SHARING);
        var pending = command.apply(state); var proposal = pending.diplomaticProposals().getLast();
        assertFalse(SensorCoverage.detects(pending, "owner", pending.fleets().getLast()));
        assertFalse(new ResolveDiplomaticProposalCommand(proposal.id(), "third", true).validate(pending));
        assertFalse(new ResolveDiplomaticProposalCommand(proposal.id(), "owner", true).validate(pending.withTurn(proposal.expiresOnTurn())));
        var rejected = new ResolveDiplomaticProposalCommand(proposal.id(), "owner", false).apply(pending);
        assertFalse(SensorCoverage.detects(rejected, "owner", rejected.fleets().getLast()));
        var withdrawn = new WithdrawDiplomaticProposalCommand(proposal.id(), "global").apply(pending);
        assertEquals(DiplomaticProposal.STATUS_WITHDRAWN, withdrawn.diplomaticProposals().getLast().status());
        assertFalse(SensorCoverage.detects(withdrawn, "owner", withdrawn.fleets().getLast()));
    }

    @Test void negotiatedEmpireSharingIsReciprocalPersistentAndDoesNotRelayThirdPartyData() throws Exception {
        var state = world("third"); var original = state;
        state = accept(state, "third", "enemy"); state = accept(state, "enemy", "owner");
        assertFalse(SensorCoverage.detects(state, "owner", state.fleets().getLast()));
        assertTrue(SensorCoverage.detects(state, "enemy", state.fleets().getLast()));
        assertEquals(original.diplomaticRelations(), state.diplomaticRelations());
        assertEquals(original.shipDesigns(), state.shipDesigns()); assertEquals(original.empires(), state.empires());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        state = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(state.turn(), "RUNNING");
        assertTrue(SensorSharing.agreement(state, "owner", "enemy"));
        assertTrue(SensorSharing.observerOwners(state, "enemy").contains("domestic"));
        var war = new SetDiplomaticTierCommand("owner", "enemy", "TOTAL_WAR").apply(state);
        assertFalse(SensorSharing.agreement(war, "owner", "enemy"));
        var peace = new SetDiplomaticTierCommand("owner", "enemy", "NEUTRAL").apply(war);
        assertFalse(SensorSharing.agreement(peace, "owner", "enemy"));
        assertFalse(new ProposeDiplomaticPactCommand("owner", "enemy", DiplomaticPact.SENSOR_DATA_SHARING).validate(war));
    }
}
