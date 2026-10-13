package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.setup.SalvageRightsSequence;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShepherdRecoveryCampaignTest {
    @Test void introductionRequiresTheCompletedAccountAndItsAcknowledgement() {
        var progress = new CorvusWaylineProgress();
        progress.acknowledgeAftermath();
        assertFalse(progress.aftermathAcknowledged());
        for (var chapter : CorvusChapter.values()) {
            String id = progress.missionId();
            assertTrue(progress.begin(id, chapter.planetName));
            assertEquals(chapter.payment, progress.settle(id, chapter.planetName, true, chapter.payment));
        }
        assertFalse(progress.aftermathAcknowledged());
        progress.acknowledgeAftermath();
        assertTrue(progress.aftermathAcknowledged());
    }

    @Test void recoveryKeepsExactFerrySeatsAndDoesNotGiftSupport() {
        var mission = ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(), "Asharu");
        assertEquals(2, MissionForceEnvelope.recommendedSquads(mission));
        assertEquals(8, MissionForceEnvelope.minimumPersonnel(mission, 100));
        var boat = FittedBoat.standard(ShuttleType.HERMES);
        for (int people : List.of(8, 24, 53)) {
            var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, List.of(boat), people);
            assertEquals(people, manifest.stream().mapToInt(m -> m.embarkedPersonnel).sum());
            assertSame(boat, manifest.get(0).airframe);
        }
        assertTrue(PowerCatalog.resolve(List.of(), mission, List.of()).isEmpty());
        assertEquals(StoryScene.SALVAGE_RIGHTS, StoryScene.forMission(mission));
    }

    @Test void claimUnlocksColdStartWithExactCompanyLiftAndNoFreeMech() {
        var progress=new ShepherdRecoveryProgress();var id=progress.missionId();
        progress.begin(id,"Asharu");progress.settle(id,"Asharu",true,18000,0,true);
        var mission=ShepherdRecoveryCampaign.offer(progress,"Asharu");
        assertEquals("Cold Start",mission.name);assertEquals(22000,mission.payout);
        assertEquals(StoryScene.COLD_START,StoryScene.forMission(mission));
        var boat=FittedBoat.standard(ShuttleType.HERMES);
        for(int people:List.of(8,24,53)) {
            var manifest=DetachmentResolver.buildShuttleManifestForPersonnel(mission,List.of(boat),people);
            assertEquals(people,manifest.stream().mapToInt(m->m.embarkedPersonnel).sum());
            assertTrue(manifest.stream().allMatch(m->m.airframe==boat));
        }
        assertTrue(PowerCatalog.resolve(List.of(),mission,List.of()).isEmpty());
        var debug=DebugMissionCatalog.Scenario.COLD_START.mission();
        var preset=DebugMissionCatalog.preset(debug);
        assertEquals(2,preset.squads());assertEquals(2,preset.boats());assertEquals(0,preset.mechs());
        assertEquals("03",DebugMissionCatalog.Scenario.COLD_START.sequence());
        assertEquals("Asharu",DebugMissionCatalog.Scenario.COLD_START.location());
    }

    @Test void writtenAgreementAccountsForInspectionLossesWithoutAwardingAWorkingShip() throws Exception {
        var catalog = WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH))));
        var mission = ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(), "Asharu");
        for (int losses : List.of(0, 1)) {
            var result = MissionOutcome.builder().mission(mission).victory(true)
                    .salvageRights(new SalvageRightsSequence.Report(true, losses)).build();
            var lines = MissionDebriefConversation.lines(result, catalog);
            assertEquals(4, lines.size());
            assertTrue(lines.stream().noneMatch(WaylineNarration.Line::voiced));
            assertEquals(losses > 0, lines.get(2).text().contains("inspection losses"));
            assertTrue(lines.get(1).text().contains("finishing the repairs and departure"));
        }
    }

    @Test void repairsUnlockActualDepartureOfferWithFittedLiftAndInKindTerms() {
        var p=new ShepherdRecoveryProgress();var id=p.missionId();
        p.begin(id,"Asharu");p.settle(id,"Asharu",true,18000,0,true);
        id=p.missionId();p.begin(id,"Asharu");p.settleColdStart(id,"Asharu",true,22000,0,true,true,true,true);
        var mission=ShepherdRecoveryCampaign.offer(p,"Asharu");
        assertEquals("Working Passage",mission.name);assertEquals(0,mission.payout);
        assertEquals(StoryScene.WORKING_PASSAGE,StoryScene.forMission(mission));
        assertEquals(2,MissionForceEnvelope.recommendedSquads(mission));
        assertTrue(mission.flavor.contains("willing working crew"));
        var boat=FittedBoat.standard(ShuttleType.HERMES);
        var manifest=DetachmentResolver.buildShuttleManifestForPersonnel(mission,List.of(boat),24);
        assertEquals(24,manifest.stream().mapToInt(m->m.embarkedPersonnel).sum());
        assertTrue(manifest.stream().allMatch(m->m.airframe==boat));
        assertTrue(PowerCatalog.resolve(List.of(),mission,List.of()).isEmpty());
        assertEquals(2,DebugMissionCatalog.preset(DebugMissionCatalog.Scenario.WORKING_PASSAGE.mission()).squads());
        assertEquals("story_shepherd_working_passage",WaylineNarration.storyMissionId(mission));
        assertEquals("story_shepherd_working_passage",WaylineNarration.storyMissionId(DebugMissionCatalog.Scenario.WORKING_PASSAGE.mission()));
    }

    @Test void fleetDeliveryIsOnceEvenAfterSaveReloadAndSale() throws Exception {
        var p=departureProgress();var mission=ShepherdRecoveryCampaign.offer(p,"Asharu");p.begin(mission.id,"Asharu");
        var outcome=MissionOutcome.builder().mission(mission).victory(true).workingPassage(new WorkingPassageProgress.Report(true,false,false,2)).build();
        var award=new CountingAward();
        assertEquals(0,ShepherdRecoveryCampaign.settlePassage(p,outcome,award));
        assertEquals(1,award.deliveries);assertEquals(15,award.crew);
        assertEquals(-1,ShepherdRecoveryCampaign.settlePassage(p,outcome,award));
        var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(p);}
        try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){p=(ShepherdRecoveryProgress)in.readObject();}
        // Selling the delivered hull cannot erase its permanent reward receipt.
        award.present=false;
        assertEquals(-1,ShepherdRecoveryCampaign.settlePassage(p,outcome,award));
        assertFalse(award.present);assertEquals(1,award.deliveries);assertEquals("fleet-morrow",p.awardedMemberId());
        assertThrows(IllegalArgumentException.class,()->ShepherdRecoveryCampaign.offer(departedProgress(),"Asharu"));
    }
    @Test void fleetTransferFailureLeavesReceiptOpenAndInvalidReceiptNeverDelivers() {
        var p=departureProgress();var mission=ShepherdRecoveryCampaign.offer(p,"Asharu");p.begin(mission.id,"Asharu");
        var victory=MissionOutcome.builder().mission(mission).victory(true).workingPassage(new WorkingPassageProgress.Report(true,false,false,0)).build();
        var wrongPlanet=MissionOutcome.builder().mission(Mission.builder(mission).targetPlanetName("Garnir").build()).victory(true)
                .workingPassage(new WorkingPassageProgress.Report(true,false,false,0)).build();
        var award=new CountingAward();assertEquals(-1,ShepherdRecoveryCampaign.settlePassage(p,wrongPlanet,award));assertEquals(0,award.deliveries);
        award.fail=true;assertThrows(IllegalStateException.class,()->ShepherdRecoveryCampaign.settlePassage(p,victory,award));
        assertFalse(p.departed());assertEquals(mission.id,p.missionId());
        award.fail=false;assertEquals(0,ShepherdRecoveryCampaign.settlePassage(p,victory,award));assertEquals(1,award.deliveries);
    }
    private static ShepherdRecoveryProgress departureProgress() {
        var p=new ShepherdRecoveryProgress();var id=p.missionId();p.begin(id,"Asharu");p.settle(id,"Asharu",true,18000,0,true);
        id=p.missionId();p.begin(id,"Asharu");p.settleColdStart(id,"Asharu",true,22000,0,true,true,true,true);return p;
    }
    private static ShepherdRecoveryProgress departedProgress() {
        var p=departureProgress();var id=p.missionId();p.begin(id,"Asharu");p.settleWorkingPassage(id,"Asharu",true,0,0,true,false,false,"fleet-morrow",15);return p;
    }
    private static final class CountingAward implements ShepherdRecoveryCampaign.FleetAward {
        int deliveries,crew;boolean present,fail;
        public String memberId(){return "fleet-morrow";}
        public int minimumCrew(){return 15;}
        public void transfer(){if(fail)throw new IllegalStateException("Transfer unavailable");deliveries++;crew+=minimumCrew();present=true;}
    }
}
