package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The posture choice as a function of a snapshot sequence: no world, no ship. */
class WarshipDoctrineTest {

    private static PostureInputs calm(MissionStance stance) {
        return new PostureInputs(1f, 0f, false, false, 0f, "", 0, stance);
    }

    private static PostureInputs threat(float score) {
        return new PostureInputs(1f, 0f, false, false, score, "declared " + score, 0, MissionStance.HOLD);
    }

    @Test
    void aCalmShipWithNoStanceBombardsAndSaysWhy() {
        WarshipDoctrine.Decision d = new WarshipDoctrine().assess(PostureInputs.calm(), 1f);
        assertEquals(WarshipPosture.BOMBARD, d.posture());
        assertFalse(d.reason().isBlank());
    }

    @Test
    void stanceChoosesBetweenSupportInterceptAndBombardAtOnceWithNoDwell() {
        WarshipDoctrine doctrine = new WarshipDoctrine();
        assertEquals(WarshipPosture.SUPPORT, doctrine.assess(calm(MissionStance.ATTACK), 1f).posture());
        assertEquals(WarshipPosture.INTERCEPT, doctrine.assess(calm(MissionStance.DEFEND), 1f).posture());
        assertEquals(WarshipPosture.SUPPORT, doctrine.assess(calm(MissionStance.ATTACK), 1f).posture());
        assertEquals(WarshipPosture.BOMBARD, doctrine.assess(calm(MissionStance.HOLD), 1f).posture());
    }

    @Test
    void airSuperiorityIsEnteredAtTheEntryScoreAndNotBelowIt() {
        WarshipDoctrine doctrine = new WarshipDoctrine();
        assertEquals(WarshipPosture.BOMBARD, doctrine.assess(threat(WarshipDoctrine.AIR_ENTER - 0.1f), 1f).posture());
        assertEquals(WarshipPosture.AIR_SUPERIORITY, doctrine.assess(threat(WarshipDoctrine.AIR_ENTER), 1f).posture());
    }

    @Test
    void scoresBetweenTheThresholdsKeepTheFightGoingAndNeverStartOne() {
        WarshipDoctrine doctrine = new WarshipDoctrine();
        float between = (WarshipDoctrine.AIR_ENTER + WarshipDoctrine.AIR_EXIT) / 2f;
        for (int i = 0; i < 20; i++) {
            assertEquals(WarshipPosture.BOMBARD, doctrine.assess(threat(between), 1f).posture(),
                    "a middling score does not start the fight");
        }
        doctrine.assess(threat(2f), 1f);
        for (int i = 0; i < 20; i++) {
            assertEquals(WarshipPosture.AIR_SUPERIORITY, doctrine.assess(threat(between), 1f).posture(),
                    "a middling score does not end it");
        }
    }

    @Test
    void airSuperiorityIsAchievedOnlyAfterTheQuietDwellAndABlipRestartsIt() {
        WarshipDoctrine doctrine = new WarshipDoctrine();
        doctrine.assess(threat(2f), 1f);
        int quiet = 0;
        WarshipPosture posture = WarshipPosture.AIR_SUPERIORITY;
        while (posture == WarshipPosture.AIR_SUPERIORITY && quiet < 100) {
            // A blip at the half-way mark: the dwell must start over.
            float score = quiet == 4 ? WarshipDoctrine.AIR_EXIT : 0f;
            posture = doctrine.assess(threat(score), 1f).posture();
            quiet++;
        }
        assertEquals(WarshipPosture.BOMBARD, posture);
        assertEquals(4 + 1 + (int) WarshipDoctrine.AIR_QUIET_SECONDS, quiet,
                "four quiet seconds, the blip, then the whole dwell again");
    }

    @Test
    void theAirFightOutranksStanceButStanceReturnsWhenItIsOver() {
        WarshipDoctrine doctrine = new WarshipDoctrine();
        PostureInputs fight = new PostureInputs(1f, 0f, false, false, 2f, "2 hostile ships", 0, MissionStance.ATTACK);
        assertEquals(WarshipPosture.AIR_SUPERIORITY, doctrine.assess(fight, 1f).posture());
        WarshipPosture last = null;
        for (int i = 0; i < (int) WarshipDoctrine.AIR_QUIET_SECONDS; i++) {
            last = doctrine.assess(calm(MissionStance.ATTACK), 1f).posture();
        }
        assertEquals(WarshipPosture.SUPPORT, last);
    }

    @Test
    void withdrawOutranksTheAirFightAndIsForTheRestOfTheBattle() {
        WarshipDoctrine doctrine = new WarshipDoctrine();
        PostureInputs hurt = new PostureInputs(0.3f, 0f, false, false, 3f, "x", 0, MissionStance.ATTACK);
        WarshipDoctrine.Decision d = doctrine.assess(hurt, 1f);
        assertEquals(WarshipPosture.WITHDRAW, d.posture());
        assertTrue(d.reason().contains("30%"), d.reason());
        assertTrue(doctrine.withdrawn());
        assertEquals(WarshipPosture.WITHDRAW, doctrine.assess(PostureInputs.calm(), 1f).posture(),
                "a repaired, unthreatened ship still leaves: the decision was made");
    }

    @Test
    void ownAircraftInTheAirMakeAShipSlowerToLeave() {
        PostureInputs alone = new PostureInputs(0.3f, 0f, false, false, 0f, "", 0, MissionStance.HOLD);
        PostureInputs covering = new PostureInputs(0.3f, 0f, false, false, 0f, "", 4, MissionStance.HOLD);
        assertEquals(WarshipPosture.WITHDRAW, new WarshipDoctrine().assess(alone, 1f).posture());
        assertEquals(WarshipPosture.BOMBARD, new WarshipDoctrine().assess(covering, 1f).posture());
    }

    @Test
    void anOverloadOrDownedShieldLeavesOnlyAShipThatIsBothThreatenedAndHurt() {
        PostureInputs overloadedCalm = new PostureInputs(0.5f, 1f, true, false, 0f, "", 0, MissionStance.HOLD);
        PostureInputs overloadedThreatened = new PostureInputs(0.5f, 1f, true, false, 2f, "x", 0, MissionStance.HOLD);
        PostureInputs healthyOverloadedThreatened = new PostureInputs(0.9f, 1f, true, false, 2f, "x", 0, MissionStance.HOLD);
        PostureInputs shieldDownThreatened = new PostureInputs(0.5f, 0.2f, false, true, 2f, "x", 0, MissionStance.HOLD);
        assertEquals(WarshipPosture.BOMBARD, new WarshipDoctrine().assess(overloadedCalm, 1f).posture());
        assertEquals(WarshipPosture.WITHDRAW, new WarshipDoctrine().assess(overloadedThreatened, 1f).posture());
        assertEquals(WarshipPosture.AIR_SUPERIORITY, new WarshipDoctrine().assess(healthyOverloadedThreatened, 1f).posture());
        assertEquals(WarshipPosture.WITHDRAW, new WarshipDoctrine().assess(shieldDownThreatened, 1f).posture());
    }

    @Test
    void theSameSnapshotsAlwaysGiveTheSameDecisions() {
        WarshipDoctrine a = new WarshipDoctrine();
        WarshipDoctrine b = new WarshipDoctrine();
        float[] scores = {0f, 0.7f, 1.3f, 0.2f, 0.2f, 0.9f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f};
        for (float s : scores) {
            assertEquals(a.assess(threat(s), 1f), b.assess(threat(s), 1f));
        }
    }

    @Test
    void everyProfileKeepsDamageAlone() {
        for (WarshipPosture posture : WarshipPosture.values()) {
            PostureProfile p = PostureProfile.of(posture);
            assertTrue(p.resumeLevel() < p.softLimit() && p.softLimit() <= 1f, posture + " flux pair");
        }
        assertTrue(PostureProfile.AIR_SUPERIORITY.softLimit() < PostureProfile.BOMBARD.softLimit());
        assertEquals(0, PostureProfile.WITHDRAW.maxAreas());
    }
}
