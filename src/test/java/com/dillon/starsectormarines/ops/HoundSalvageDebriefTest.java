package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HoundSalvageDebriefTest {
    @Test void agreementUsesFrozenRecoveryFactsAndOnlyTheQualifyingStoryVictory() throws Exception {
        var catalog = WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH))));
        for (boolean disabled : new boolean[]{false, true}) {
            var outcome = outcome(true, MissionSource.STORY, new WaylineHoundRecovery.Report(false, disabled));
            var lines = HoundSalvageDebrief.lines(outcome, catalog);
            assertEquals(5, lines.size());
            assertEquals("CASTLE / COMPANY COMMAND", lines.get(0).speaker());
            assertEquals("graphics/portraits/castle.png", lines.get(0).portrait());
            assertEquals("debrief.hound.transfer", lines.get(3).event(), "One handover, knocked out or not");
            assertEquals("ZOEY VENN / WAYLINE", lines.get(3).speaker());
            assertEquals("CASTLE / COMPANY COMMAND", lines.get(4).speaker(), "Castle closes it at the mech bay");
        }
        assertTrue(HoundSalvageDebrief.lines(outcome(false, MissionSource.STORY,
                new WaylineHoundRecovery.Report(true, true)), catalog).isEmpty());
        assertTrue(HoundSalvageDebrief.lines(outcome(true, MissionSource.RECURRING,
                new WaylineHoundRecovery.Report(true, false)), catalog).isEmpty());
        assertTrue(HoundSalvageDebrief.lines(outcome(true, MissionSource.STORY, null), catalog).isEmpty());
    }

    private static MissionOutcome outcome(boolean victory, MissionSource source, WaylineHoundRecovery.Report report) {
        return MissionOutcome.builder().missionId(OpeningOperationKind.COUNTERATTACK.missionId)
                .missionSource(source).victory(victory).waylineHound(report).build();
    }
}
