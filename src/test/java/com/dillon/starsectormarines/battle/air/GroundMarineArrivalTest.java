package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroundMarineArrivalTest {
    private static GroundMarineArrival arrival() {
        return new GroundMarineArrival(18, 68, SquadCommandClaim.mission("secured-yard", "Company already ashore"));
    }

    @Test
    void consumesCurrentSelectedNamedForceExactlyOnceWithoutTransportCapacity() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(24);
        String selected = roster.squads().get(1).id();
        var force = CampaignMarineDeployment.freezeSelection(roster, Set.of(selected), 24);
        List<MarineLoadout> deployed = new ArrayList<>();
        var arrival = arrival();
        arrival.deploy(force.commitments(), deployed::add);
        assertEquals(12, deployed.size());
        assertEquals(force.commitments(), deployed.stream().map(MarineSeatCommitment::capture).toList(),
                "Identity, issued kit, NCO, fire teams and command group all travel unchanged");
        assertTrue(deployed.stream().allMatch(loadout -> selected.equals(loadout.campaignSquad.squadId)));
        assertThrows(IllegalStateException.class, () -> arrival.deploy(force.commitments(), deployed::add));
        assertEquals(12, deployed.size());
    }

    @Test
    void groundPolicyNeedsNoBoatsWhileOrdinaryStoryInsertionStillDoes() {
        var ground = Mission.builder().source(MissionSource.STORY).requiredDrops(4)
                .marineArrivalPolicy(MarineArrivalPolicy.SECURED_GROUND).build();
        var air = Mission.builder(ground).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD).build();
        assertTrue(DetachmentResolver.hasRequiredTransport(ground, List.of()));
        assertFalse(DetachmentResolver.hasRequiredTransport(air, List.of()));
        assertEquals(List.of(), DetachmentResolver.buildShuttleManifestForPersonnel(ground, List.of(), 24));
    }

    @Test
    void validatesEntireForceBeforeCreatingAnyBody() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        var named = CampaignMarineDeployment.freezeSelection(roster, Set.of(roster.squads().get(0).id()), 12);
        List<MarineLoadout> deployed = new ArrayList<>();
        var anonymous = MarineSeatCommitment.capture(MarineLoadout.COMBATANT);
        assertThrows(IllegalStateException.class, () -> arrival().deploy(
                List.of(named.commitments().get(0), anonymous), deployed::add));
        assertTrue(deployed.isEmpty());
        assertThrows(IllegalStateException.class, () -> arrival().deploy(List.of(), deployed::add));
    }
}
