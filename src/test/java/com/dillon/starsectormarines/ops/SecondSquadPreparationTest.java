package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class SecondSquadPreparationTest {
    private HeadlessStrings strings;
    @BeforeEach void strings() throws Exception { strings = new HeadlessStrings(Path.of("mod")); }
    @AfterEach void closeStrings() { strings.close(); }

    static Mission junction() {
        return Mission.builder().id("supply_route_v1_1_0_0").name("Open the Junction")
                .source(MissionSource.STORY).type(MissionType.ASSAULT).tier(OperationTier.FIRST_CONTRACT).build();
    }
    @Test void onlyAStoryMissionWantingTwoSquadsIsRelevant() {
        var roster = new MarineRoster();
        var first = roster.createSquad(); roster.recruitToSquad(first.id(), 12);
        var opening = Mission.builder().id("story_opening_relief").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.FIRST_CONTRACT).build();
        assertFalse(SecondSquadPreparation.relevant(opening, roster));
        assertTrue(SecondSquadPreparation.relevant(junction(), roster));
        var debug = Mission.builder().source(MissionSource.DEBUG).type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).build();
        assertFalse(SecondSquadPreparation.relevant(debug, roster));
    }
}
