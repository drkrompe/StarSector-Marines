package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierTargeting.Candidate;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierTargeting.Prey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A deep raid: what it goes for, the way it takes round the flak, and its strike and return. */
class HarrierRaidTest {

    @Test
    void itGoesForTheRadioOverTheMedicAndNothingCovered() {
        List<Candidate> all = List.of(
                new Candidate(1, 80, 0, Prey.MEDIC, 0),
                new Candidate(2, 90, 0, Prey.RADIO, 0),
                new Candidate(3, 70, 0, Prey.RADIO, 1),
                new Candidate(4, 60, 0, Prey.INFANTRY, 0));
        assertEquals(2, HarrierRaid.choose(all, 0, 0));
        assertEquals(1, HarrierRaid.choose(all.subList(0, 1), 0, 0));
        assertEquals(0, HarrierRaid.choose(List.of(all.get(2), all.get(3)), 0, 0), "covered, or no soft target");
    }

    @Test
    void itRaidsTheSurvivorsInTheOpenWhenNothingRicherIsSoft() {
        Candidate survivor = new Candidate(5, 100, 0, Prey.SURVIVOR, 0);
        Candidate alone = new Candidate(6, 60, 0, Prey.ISOLATED, 0);
        assertEquals(5, HarrierRaid.choose(List.of(alone, survivor), 0, 0), "a colonist to snatch over a lone soldier");
        assertEquals(2, HarrierRaid.choose(List.of(survivor, new Candidate(2, 90, 0, Prey.RADIO, 0)), 0, 0),
                "the radio first");
        assertEquals(0, HarrierRaid.choose(List.of(new Candidate(5, 100, 0, Prey.SURVIVOR, 1)), 0, 0),
                "never under the guns");
    }

    @Test
    void itRaidsOnlyPastTheSpiresWakeAndWithinItsRange() {
        assertEquals(0, HarrierRaid.choose(List.of(new Candidate(1, 30, 0, Prey.MEDIC, 0)), 0, 0), "too near");
        assertEquals(0, HarrierRaid.choose(List.of(new Candidate(1, 400, 0, Prey.MEDIC, 0)), 0, 0), "too far");
    }

    @Test
    void overItsTargetItDivesOnlyOnWhatIsSoftAndNearWhereItWasSent() {
        List<Candidate> all = List.of(
                new Candidate(1, 10, 0, Prey.GUN, 2),
                new Candidate(2, 5, 0, Prey.ISOLATED, 0),
                new Candidate(3, 40, 0, Prey.RADIO, 0));
        assertEquals(2, HarrierRaid.pickAt(all, 0, 0, 0), "not the covered gun, not the radio far off");
        assertEquals(0, HarrierRaid.pickAt(all.subList(0, 1), 0, 0, 0));
    }

    @Test
    void itFliesStraightWhenTheWayIsClear() {
        assertArrayEquals(new float[]{100f, 50f}, HarrierRaid.route(10, 50, 100, 50, List.of(), 200, 200));
    }

    @Test
    void itBendsRoundAGunOnTheWayAndEveryLegKeepsClear() {
        List<float[]> guns = List.<float[]>of(new float[]{55f, 50f, 20f});
        float[] way = HarrierRaid.route(10, 50, 100, 50, guns, 200, 200);
        assertNotNull(way);
        assertTrue(way.length > 2, "a detour");
        float px = 10, py = 50;
        for (int i = 0; i < way.length; i += 2) {
            assertTrue(HarrierRaid.clear(px, py, way[i], way[i + 1], guns), "leg " + i / 2 + " clear");
            px = way[i];
            py = way[i + 1];
        }
        assertEquals(100f, way[way.length - 2]);
    }

    @Test
    void noRaidWhenTheGunsCloseEveryWay() {
        assertNull(HarrierRaid.route(10, 50, 100, 50, List.<float[]>of(new float[]{10f, 50f, 30f}), 200, 200));
    }

    @Test
    void itComesHomeTheWayItWentAndStrikesAtTheTarget() {
        HarrierPatrol p = HarrierRaid.sortie(new float[]{40f, 80f, 100f, 50f}, 10f, 50f, 4);
        assertArrayEquals(new float[]{40f, 80f, 100f, 50f, 40f, 80f, 10f, 50f}, p.route());
        assertTrue(p.raid());
        for (int i = 0; i < 2000 && !p.striking(); i++) p.step(0.1f);
        assertTrue(p.striking());
        assertEquals(100f, p.x(), 1e-3f);
        p.step(5f);
        assertEquals(100f, p.x(), 1e-3f, "it holds over the target while it strikes");
    }

    @Test
    void itTurnsForHomeAfterItsDivesOrWhenThereIsNothingThere() {
        HarrierPatrol dived = HarrierRaid.sortie(new float[]{20f, 0f}, 0f, 0f, 4);
        for (int i = 0; i < 100 && !dived.striking(); i++) dived.step(0.5f);
        dived.strikeTick(0.1f, 3, true, true);
        dived.strikeTick(0.1f, 4, false, true);
        assertTrue(dived.striking(), "one dive in");
        dived.strikeTick(0.1f, 5, true, true);
        assertFalse(dived.striking());
        assertTrue(dived.struck());

        HarrierPatrol idle = HarrierRaid.sortie(new float[]{20f, 0f}, 0f, 0f, 4);
        for (int i = 0; i < 100 && !idle.striking(); i++) idle.step(0.5f);
        for (float t = 0; t < HarrierRaid.STRIKE_IDLE_SECONDS + 0.2f; t += 0.1f) idle.strikeTick(0.1f, 0, true, false);
        assertTrue(idle.struck(), "nothing there");
        for (int i = 0; i < 100 && !idle.homing(); i++) idle.step(0.5f);
        assertTrue(idle.homing());
        assertEquals(0f, idle.x(), 1e-3f);
    }
}
