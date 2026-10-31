package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SupplyRouteCampaignTest {
    private HeadlessStrings strings;
    /** Reads the shipped copy, so what these tests pin is the text the player sees. */
    @BeforeEach void shippedCopy() throws Exception { strings = new HeadlessStrings(Path.of("mod")); }
    @AfterEach void restoreSettings() { strings.close(); }

    @Test void junctionOffersThunderRunDirectlyAndInspectionStartsNoAttempt() {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        Mission junction = SupplyRouteCampaign.offers(p).get(0);
        assertEquals("Open the Junction", junction.name);
        assertTrue(p.begin(junction.id)); assertEquals(12000, p.settle(junction.id, true, null));
        var offers = SupplyRouteCampaign.offers(p); assertEquals(1, offers.size());
        var convoy = offers.get(0);
        assertEquals("Thunder Run", convoy.name); assertEquals(18000, convoy.payout);
        assertTrue(SupplyRouteCampaign.isBattle(convoy)); assertTrue(SupplyRouteCampaign.freight(convoy));
        assertEquals("CONVOY / BREAKTHROUGH", SupplyRouteCampaign.operationLabel(convoy));
        assertEquals(StoryScene.THUNDER, StoryScene.forMission(convoy));
        assertEquals(MissionSource.STORY, convoy.source);
        assertFalse(convoy.flavor.contains("Rill")); assertFalse(convoy.flavor.contains("commit support"));
        var props = MissionSelectScreen.previewProps();
        MissionSelectScreen.putDetail(props, convoy, () -> {}, () -> {});
        assertEquals("BRIEF & DEPLOY", props.get("briefLabel")); assertFalse(props.containsKey("choiceSummary"));
        assertEquals(convoy.id, SupplyRouteCampaign.offers(p).get(0).id);
        assertEquals("Thunder Run", new SupplyRouteWorkGuide(p, null, () -> {}).markupProperty("mission"));
        assertEquals(-1, p.settle(convoy.id, false, null,
                new FreightRunProgress.Outcome(p.freight().previewAttempt(), List.of(), List.of(), false)));
        assertTrue(p.begin(convoy.id));
        p.settle(convoy.id, true, null, new FreightRunProgress.Outcome(p.freight().previewAttempt(), List.of(0, 1), List.of(2), true));
        var repeat = SupplyRouteCampaign.offers(p).get(0);
        assertEquals("Route Security", repeat.name); assertEquals(MissionSource.RECURRING, repeat.source);
        assertEquals(12000, repeat.payout); assertNotEquals(convoy.id, repeat.id);
        assertTrue(repeat.flavor.contains("each security job held"));
    }
    @Test void recoveryBeatBelongsOnlyToSuccessfulStoryWork() {
        var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        var mission = SupplyRouteCampaign.offers(route).get(0);
        assertFalse(SupplyRouteCampaign.recoveryDebrief(MissionOutcome.builder().mission(mission).victory(true).build()).isBlank());
        assertEquals("", SupplyRouteCampaign.recoveryDebrief(MissionOutcome.builder().mission(mission).victory(false).build()));
        assertEquals("", SupplyRouteCampaign.recoveryDebrief(MissionOutcome.builder().mission(mission)
                .missionSource(MissionSource.RECURRING).victory(true).build()));
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
        assertFalse(SupplyRouteCampaign.forceDeployable(roster,Set.of(roster.squads().get(0).id(),roster.squads().get(1).id(),"unknown")));
        assertTrue(SupplyRouteCampaign.forceDeployable(roster,Set.of(roster.squads().get(0).id())), "One squad is the player's choice");
        assertFalse(SupplyRouteCampaign.forceDeployable(roster,Set.of()));
        assertFalse(SupplyRouteCampaign.forceDeployable(roster,Set.of("unknown")));
    }
    @Test void aTwelveSeatBoatLandsWholeSquadsNotHalves() {
        var mission=SupplyRouteCampaign.offers(new BrokenSupplyRouteProgress("jangala","Jangala","Corvus")).get(0);
        var boat=FittedBoat.standard(ShuttleType.VALKYRIE);
        var manifest=DetachmentResolver.buildShuttleManifestForPersonnel(mission,List.of(boat),24);
        assertEquals(boat.seats(),DetachmentResolver.companySeatsPerTrip(mission,boat));
        assertEquals(boat.seats(),manifest.get(0).seatsPerSortie);
        assertEquals((24+boat.seats()-1)/boat.seats(),manifest.get(0).cycles);
    }
    @Test void hqRetainsFrozenSiteWhenAccessDisappearsAndReportsPendingWithoutSuccess() {
        var p=new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");var guide=new SupplyRouteWorkGuide(p,null,()->{});
        assertEquals("Jangala / Corvus",guide.markupProperty("location"));assertEquals(true,guide.markupProperty("courseDisabled"));
        String id=p.missionId();p.begin(id);p.settle(id,true,null);
        assertTrue(guide.markupProperty("preparation").toString().contains("Thunder Run pending"));
        assertEquals("Thunder Run",guide.markupProperty("mission"));assertFalse(p.successful());
    }
}
