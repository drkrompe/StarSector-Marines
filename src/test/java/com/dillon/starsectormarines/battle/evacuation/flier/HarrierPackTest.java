package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pack's one decision, flown on its own with a still target: loiter out of reach, commit as one, dive, climb. */
class HarrierPackTest {

    private static final float DT = 1f / 30f;
    private static final long PREY = 900L;
    private static final float PREY_X = 20f, PREY_Y = 30f;

    private static HarrierPack pack(int n, float x, float y) {
        HarrierPack pack = new HarrierPack(1, x, y);
        for (int i = 0; i < n; i++) {
            AirBody body = new AirBody();
            double a = 2 * Math.PI * i / n;
            body.teleport(x + (float) Math.cos(a) * 4f, y + (float) Math.sin(a) * 4f, 0f);
            HarrierFlight f = new HarrierFlight(100 + i, body, HarrierLook.DEFAULT);
            f.slot = i;
            pack.members.add(f);
        }
        return pack;
    }

    private record Strike(long harrier, long target) {}

    /** Flies the pack for {@code seconds} at the still prey, collecting its strikes. */
    private static List<Strike> fly(HarrierPack pack, float seconds, long prey) {
        List<Strike> strikes = new ArrayList<>();
        for (int t = 0; t < Math.round(seconds / DT); t++) {
            pack.prey(prey, PREY_X, PREY_Y, 0.3f);
            pack.advance(DT, (h, target) -> strikes.add(new Strike(h, target)));
        }
        return strikes;
    }

    @Test
    void loitersOutOfReachBeforeItCommits() {
        HarrierPack pack = pack(10, 60f, 30f);
        float nearest = Float.MAX_VALUE;
        for (int t = 0; t < 90 && pack.phase() == HarrierPack.Phase.LOITER; t++) {
            pack.prey(PREY, PREY_X, PREY_Y, 0.3f);
            pack.advance(DT, (h, target) -> { });
            for (HarrierFlight m : pack.members) nearest = Math.min(nearest, m.body.distanceTo(PREY_X, PREY_Y));
        }
        assertEquals(HarrierPack.Phase.LOITER, pack.phase(), "no dive inside the minimum loiter");
        assertTrue(nearest > 22f, "a loitering flier stays past a rifle's reach, came to " + nearest);
    }

    @Test
    void aPackStillHatchingNeverCommits() {
        HarrierPack pack = pack(6, 60f, 30f);
        pack.unhatched = 1;
        fly(pack, 10f, PREY);
        assertEquals(0, pack.dives(), "the pack waits for its last Harrier out of the hatch");
        pack.unhatched = 0;
        fly(pack, 10f, PREY);
        assertTrue(pack.dives() >= 1, "with every Harrier out it commits");
    }

    @Test
    void everyStrikeOfADiveLandsOnTheOneCommittedTarget() {
        HarrierPack pack = pack(10, 60f, 30f);
        List<Strike> strikes = fly(pack, 20f, PREY);
        assertTrue(pack.dives() >= 1, "the pack committed");
        assertFalse(strikes.isEmpty(), "the dive landed strikes");
        Set<Long> struckBy = new HashSet<>();
        for (Strike s : strikes) {
            assertEquals(PREY, s.target());
            struckBy.add(s.harrier());
        }
        assertTrue(struckBy.size() >= 8, "most of the pack struck on its dive: " + struckBy.size());
    }

    @Test
    void preyLostMidDiveBreaksTheDiveOffWithoutStrikingAnythingElse() {
        HarrierPack pack = pack(6, 60f, 30f);
        for (int t = 0; t < 2000 && pack.phase() != HarrierPack.Phase.DIVE; t++) {
            pack.prey(PREY, PREY_X, PREY_Y, 0.3f);
            pack.advance(DT, (h, target) -> { });
        }
        assertEquals(HarrierPack.Phase.DIVE, pack.phase());
        List<Strike> strikes = new ArrayList<>();
        pack.prey(901L, PREY_X, PREY_Y, 0.3f);
        pack.advance(DT, (h, target) -> strikes.add(new Strike(h, target)));
        assertEquals(HarrierPack.Phase.PULL_UP, pack.phase());
        assertTrue(strikes.isEmpty());
    }

    @Test
    void diversGoLowAndClimbBackToLoiterHeight() {
        HarrierPack pack = pack(8, 60f, 30f);
        float lowest = 1f;
        boolean dived = false;
        for (int t = 0; t < Math.round(30f / DT); t++) {
            pack.prey(PREY, PREY_X, PREY_Y, 0.3f);
            pack.advance(DT, (h, target) -> { });
            for (HarrierFlight m : pack.members) lowest = Math.min(lowest, m.altitude);
            if (pack.phase() == HarrierPack.Phase.DIVE) dived = true;
            if (dived && pack.phase() == HarrierPack.Phase.LOITER) break;
        }
        assertTrue(lowest <= HarrierRules.LOW_ALTITUDE, "a diver came down to " + lowest);
        assertEquals(HarrierPack.Phase.LOITER, pack.phase(), "the pack pulled up and loiters again");
        for (int t = 0; t < Math.round(2f / DT); t++) {
            pack.prey(PREY, PREY_X, PREY_Y, 0.3f);
            pack.advance(DT, (h, target) -> { });
        }
        for (HarrierFlight m : pack.members) assertTrue(m.altitude > 0.9f, "climbed back: " + m.altitude);
    }

    @Test
    void aPackThatLostAThirdOnItsDiveSplitsOnceItHasPulledUp() {
        HarrierPack pack = pack(9, 60f, 30f);
        for (int t = 0; t < 2000 && pack.phase() != HarrierPack.Phase.DIVE; t++) {
            pack.prey(PREY, PREY_X, PREY_Y, 0.3f);
            pack.advance(DT, (h, target) -> { });
        }
        pack.members.subList(0, 3).clear();   // three shot down on the way in
        pack.reslot();
        for (int t = 0; t < 2000 && pack.phase() != HarrierPack.Phase.LOITER; t++) {
            pack.prey(PREY, PREY_X, PREY_Y, 0.3f);
            pack.advance(DT, (h, target) -> { });
        }
        assertTrue(pack.shouldSplit());
        List<HarrierFlight> half = pack.takeSplit();
        assertEquals(3, half.size());
        assertEquals(3, pack.members.size());
        assertFalse(pack.shouldSplit());
    }

    @Test
    void anUnhurtPackDoesNotSplit() {
        HarrierPack pack = pack(9, 60f, 30f);
        fly(pack, 20f, PREY);
        assertFalse(pack.shouldSplit());
    }

    @Test
    void theSamePackFliesTheSameWayTwice() {
        HarrierPack a = pack(12, 60f, 30f);
        HarrierPack b = pack(12, 60f, 30f);
        fly(a, 15f, PREY);
        fly(b, 15f, PREY);
        for (int i = 0; i < a.members.size(); i++) {
            assertEquals(a.members.get(i).body.x, b.members.get(i).body.x);
            assertEquals(a.members.get(i).body.y, b.members.get(i).body.y);
            assertEquals(a.members.get(i).altitude, b.members.get(i).altitude);
        }
    }
}
