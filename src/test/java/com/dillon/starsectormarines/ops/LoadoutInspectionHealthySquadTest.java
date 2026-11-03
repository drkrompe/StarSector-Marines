package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The loadout screen compares a healthy squad with the cards, so losses and wounds change nothing on it. */
class LoadoutInspectionHealthySquadTest {
    private HeadlessStrings strings;

    @BeforeEach void strings() throws Exception { strings = new HeadlessStrings(new File("mod").toPath()); }
    @AfterEach void closeStrings() { strings.close(); }

    @Test
    void aMauledSquadReadsAsAHealthyOneAgainstItsOwnCards() {
        MarineRoster roster = new MarineRoster();
        MarineSquad squad = roster.createSquad();
        roster.recruitToSquad(squad.id(), MarineSquad.CAPACITY);
        var cards = roster.previewSquadEquipment(squad.id(), squad.weaponDoctrineId(), squad.armorDoctrineId());
        Map<String, Object> healthy = LoadoutInspection.project(roster, squad, cards, 6, ignored -> { });

        List<String> seats = roster.seatedMemberIds(squad);
        roster.applySoldierOutcome(Map.of(seats.get(0), MarineSoldierStatus.WIA,
                seats.get(6), MarineSoldierStatus.KIA), 0f, 1f);
        Map<String, Object> mauled = LoadoutInspection.project(roster, squad, cards, 6, ignored -> { });

        assertEquals(healthy.get("scope"), mauled.get("scope"));
        for (int seat : new int[]{0, 6}) {
            assertEquals(billet(healthy, seat).get("classes"), billet(mauled, seat).get("classes"));
            assertEquals(billet(healthy, seat).get("nameClasses"), billet(mauled, seat).get("nameClasses"));
            assertFalse(((String) billet(mauled, seat).get("classes")).contains("changes"));
            assertEquals(billet(healthy, seat).get("stats"), billet(mauled, seat).get("stats"));
        }
        assertFalse(((String) billet(mauled, 0).get("name")).contains(String.valueOf(MarineSoldierStatus.WIA)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> billet(Map<String, Object> inspection, int index) {
        for (Object team : (List<?>) inspection.get("teams")) {
            for (Object line : (List<?>) ((Map<String, Object>) team).get("rows")) {
                for (Object billet : (List<?>) ((Map<String, Object>) line).get("billets")) {
                    Map<String, Object> shown = (Map<String, Object>) billet;
                    if ((Integer) shown.get("index") == index) return shown;
                }
            }
        }
        throw new AssertionError("no billet " + index);
    }
}
