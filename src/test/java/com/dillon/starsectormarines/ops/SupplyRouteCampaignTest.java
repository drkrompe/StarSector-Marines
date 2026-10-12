package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SupplyRouteCampaignTest {
    @Test void branchCommitmentNamesFinalChapterRecurringWorkAndTheForgonePath() {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String id = p.missionId(); p.begin(id); p.settle(id, true, null);
        var choices = SupplyRouteCampaign.offers(p);
        String rill = SupplyRouteCampaign.choiceSummary(choices.get(0));
        String freight = SupplyRouteCampaign.choiceSummary(choices.get(1));
        assertTrue(rill.contains("Last Lift ends"));
        assertTrue(rill.contains("recurring Crew Recovery"));
        assertTrue(rill.contains("Relay Nine and Thunder Run are forgone"));
        assertTrue(freight.contains("Relay Nine leads to Thunder Run"));
        assertTrue(freight.contains("Last Lift is forgone"));
        var props = MissionSelectScreen.previewProps();
        MissionSelectScreen.putDetail(props, choices.get(0), () -> {}, () -> {});
        assertEquals(rill, props.get("choiceSummary"));
        assertFalse(props.get("choiceClasses").toString().contains("hidden"));
        assertTrue(p.choicePending(), "inspecting consequences must not commit the branch");
    }
    @Test void viewingOffersDoesNotIntroduceOrCommitAndStoryDisappearsAfterSuccess() {
        var p = new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");
        Mission junction=SupplyRouteCampaign.offers(p).get(0); assertEquals("Open the Junction",junction.name);
        assertEquals(junction.id,SupplyRouteCampaign.offers(p).get(0).id);
        assertTrue(p.begin(junction.id));p.settle(junction.id,true,null);
        var choices=SupplyRouteCampaign.offers(p); assertEquals(2,choices.size());
        assertTrue(p.choicePending());assertFalse(p.successful());assertEquals(0,p.branch());
        assertEquals(2,SupplyRouteCampaign.choice(choices.get(0)));assertEquals(3,SupplyRouteCampaign.choice(choices.get(1)));
        assertFalse(SupplyRouteCampaign.isBattle(choices.get(0)));
        p.commit(3);var relay=SupplyRouteCampaign.offers(p).get(0); assertTrue(SupplyRouteCampaign.isBattle(relay));
        assertEquals(MissionSource.STORY,relay.source);p.begin(relay.id);p.settle(relay.id,true,null);
        var convoy=SupplyRouteCampaign.offers(p).get(0); assertEquals("Thunder Run",convoy.name);
        assertTrue(SupplyRouteCampaign.freight(convoy)); assertEquals(MissionSource.STORY,convoy.source);
        p.begin(convoy.id); p.settle(convoy.id,true,null,new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(0,1),List.of(2),true));
        var repeat=SupplyRouteCampaign.offers(p).get(0);assertEquals("Route Security",repeat.name);
        assertEquals(MissionSource.RECURRING,repeat.source);assertEquals(12000,repeat.payout);
        assertNotEquals(relay.id,repeat.id);assertTrue(repeat.flavor.contains("new security job"));
    }
    @Test void resolutionRejectsWrongPaymentSiteSourceAndReplayedCasualtyWriteback() {
        var p=new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");String id=p.missionId();p.begin(id);
        for (MissionOutcome wrong : List.of(
                MissionOutcome.builder().missionId(id).missionSource(MissionSource.STORY).targetPlanetName("Other").victory(true).payoutEarned(12000).build(),
                MissionOutcome.builder().missionId(id).missionSource(MissionSource.RECURRING).targetPlanetName("Jangala").victory(true).payoutEarned(12000).build(),
                MissionOutcome.builder().missionId(id).missionSource(MissionSource.STORY).targetPlanetName("Jangala").victory(true).payoutEarned(99999).build())) {
            assertEquals(-1,SupplyRouteCampaign.settle(p,wrong));assertFalse(p.junctionWon());
        }
        var valid=MissionOutcome.builder().missionId(id).missionSource(MissionSource.STORY).targetPlanetName("Jangala").victory(true).payoutEarned(12000).build();
        assertEquals(12000,SupplyRouteCampaign.settle(p,valid));assertEquals(-1,SupplyRouteCampaign.settle(p,valid));
    }
    @Test void actualSelectedPersonnelAndOwnedAirframesDetermineLift() {
        var p=new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");var mission=SupplyRouteCampaign.offers(p).get(0);
        var one=FittedBoat.standard(ShuttleType.AEROSHUTTLE);var two=FittedBoat.standard(ShuttleType.VALKYRIE);
        var manifest=DetachmentResolver.buildShuttleManifestForPersonnel(mission,List.of(one,two),17);
        assertEquals(17,manifest.stream().mapToInt(a->a.embarkedPersonnel).sum());
        assertSame(one,manifest.get(0).airframe);assertSame(two,manifest.get(1).airframe);
        assertEquals(0,DetachmentResolver.employerPhysicalShipCount(mission));
        var roster=new MarineRoster();roster.ensureActiveSoldiers(24);
        assertFalse(SupplyRouteCampaign.forceReady(roster,Set.of(roster.squads().get(0).id())));
        assertTrue(SupplyRouteCampaign.forceReady(roster,Set.of(roster.squads().get(0).id(),roster.squads().get(1).id())));
        assertFalse(SupplyRouteCampaign.forceReady(roster,Set.of(roster.squads().get(0).id(),roster.squads().get(1).id(),"unknown")));
    }
    @Test void hqRetainsFrozenSiteWhenAccessDisappearsAndReportsPendingWithoutSuccess() {
        var p=new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");var guide=new SupplyRouteWorkGuide(p,null,()->{});
        assertEquals("Jangala / Corvus",guide.markupProperty("location"));assertEquals(true,guide.markupProperty("courseDisabled"));
        String id=p.missionId();p.begin(id);p.settle(id,true,null);p.commit(2);
        assertTrue(guide.markupProperty("preparation").toString().contains("recovery pending"));
        assertEquals("Last Lift",guide.markupProperty("mission"));assertFalse(p.successful());
    }
}
