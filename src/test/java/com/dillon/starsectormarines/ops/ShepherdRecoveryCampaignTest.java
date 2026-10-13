package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.setup.SalvageRightsSequence;
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

    @Test void writtenAgreementAccountsForInspectionLossesWithoutAwardingAWorkingShip() throws Exception {
        var catalog = WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH))));
        var mission = ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(), "Asharu");
        for (int losses : List.of(0, 1)) {
            var result = MissionOutcome.builder().mission(mission).victory(true)
                    .salvageRights(new SalvageRightsSequence.Report(true, losses)).build();
            var lines = MissionDebriefConversation.lines(result, catalog);
            assertEquals(4, lines.size());
            assertTrue(lines.stream().noneMatch(WaylineNarration.Line::voiced));
            assertEquals(losses > 0, lines.get(2).text().contains("lost people"));
            assertTrue(lines.get(1).text().contains("after the repair and departure contracts"));
        }
    }
}
