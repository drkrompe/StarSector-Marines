package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
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
    @Test void transitionShowsRealBillWithoutSpendingOrInventingPersonnel() {
        var roster = new MarineRoster();
        var first = roster.createSquad(); roster.recruitToSquad(first.id(), 12);
        var opening = Mission.builder().id("story_opening_relief").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.FIRST_CONTRACT).build();
        assertNull(SecondSquadPreparation.lesson(opening, roster, SquadFoundingResources.NONE, false, () -> {}));
        var lesson = SecondSquadPreparation.lesson(junction(), roster, SquadFoundingResources.NONE, false,
                roster::acknowledgeSecondSquadPreparation);
        assertNotNull(lesson);
        assertTrue(lesson.copy().contains("24 marines"));
        assertTrue(lesson.copy().contains("four ready marines each"));
        assertTrue(lesson.copy().contains("marines · Cost 12 · Short by 12"));
        assertTrue(lesson.copy().contains("supplies · Cost 36 · Short by 36"));
        assertTrue(lesson.copy().contains("food · Cost 24 · Short by 24"));
        assertEquals(12, roster.lineReadySoldiers().size());
        assertFalse(roster.secondSquadPreparationSeen());
        lesson.action().run();
        assertNull(SecondSquadPreparation.lesson(junction(), roster, SquadFoundingResources.NONE, false, () -> {}));
        assertNotNull(SecondSquadPreparation.lesson(junction(), roster, SquadFoundingResources.NONE, true, () -> {}));
    }
    @Test void twoDepletedFormationsAreDirectedToReplenishInsteadOfFoundAnother() {
        var roster = new MarineRoster();
        for (int i = 0; i < 2; i++) { var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 6); }
        var lesson = SecondSquadPreparation.lesson(junction(), roster, SquadFoundingResources.NONE, false, () -> {});
        assertTrue(lesson.copy().contains("existing formations"));
        assertEquals(12, roster.lineReadySoldiers().size());
    }
    @Test void preparedCompanyNeedsNoExtraSquadAndAcknowledgementSurvivesSave() throws Exception {
        var roster = new MarineRoster();
        for (int i = 0; i < 2; i++) { var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 12); }
        var lesson = SecondSquadPreparation.lesson(junction(), roster, SquadFoundingResources.NONE, false, () -> {});
        assertTrue(lesson.copy().contains("no need to raise another"));
        roster.acknowledgeSecondSquadPreparation();
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(roster); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (MarineRoster) in.readObject();
            assertTrue(restored.secondSquadPreparationSeen());
        }
        var debug = Mission.builder().source(MissionSource.DEBUG).type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).build();
        assertFalse(SecondSquadPreparation.relevant(debug, roster));
    }
}
