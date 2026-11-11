package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.derelict.HaulerWork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalvageWalkerArmsTest {

    private static final float DT = 1f / 30f;

    @Test
    void armsUnfoldOverTimeWhileCuttingAndTuckAgainAfter() {
        SalvageWalkerArms.State st = new SalvageWalkerArms.State();
        st.stripper(DT, true);
        assertTrue(st.unfold > 0f && st.unfold < 0.2f, "eases out, not a snap: " + st.unfold);
        run(st, SalvageWalkerArms.UNFOLD_SECONDS, true);
        assertEquals(1f, st.unfold, 1e-6f);
        run(st, SalvageWalkerArms.UNFOLD_SECONDS / 2f, false);
        assertTrue(st.unfold > 0f && st.unfold < 1f);
        run(st, SalvageWalkerArms.UNFOLD_SECONDS, false);
        assertEquals(0f, st.unfold, 1e-6f);
    }

    @Test
    void theBedFillsAGrabAtATimeAndEmptiesWhenTippedThenTheArmsTuck() {
        SalvageWalkerArms.State st = new SalvageWalkerArms.State();
        // The rules hand 0.4 of a load over in one pulse; then the hauler walks on.
        st.hauler(DT, HaulerWork.Kind.TAKE, 3f, 4f, 0.4f);
        assertEquals(0f, st.shown, 1e-6f);
        int clanks = 0;
        float lastAge = st.puffAge;
        for (float t = 0f; t < 6f; t += DT) {
            st.hauler(DT, HaulerWork.Kind.GO, 9f, 9f, 0.4f);
            if (st.puffAge < lastAge) clanks++;
            lastAge = st.puffAge;
            assertTrue(st.shown <= 0.4f + 1e-6f, "never ahead of the rules");
        }
        assertEquals(0.4f, st.shown, 1e-6f);
        assertEquals(2, clanks, "a clank a grab, two grabs of " + SalvageWalkerArms.GRAB_SHARE);
        assertEquals(3f, st.puffX, 1e-6f);
        assertEquals(0f, st.unfold, 1e-6f);
        // Unloaded: the bed tips out over time, the arms out until it is empty.
        st.hauler(DT, HaulerWork.Kind.UNLOAD, 0f, 0f, 0f);
        run(st, SalvageWalkerArms.UNFOLD_SECONDS + 0.1f, 0f);
        assertTrue(st.shown > 0f && st.dumping(), "still tipping: " + st.shown);
        run(st, SalvageWalkerArms.DUMP_SECONDS, 0f);
        assertEquals(0f, st.shown, 1e-6f);
        run(st, SalvageWalkerArms.UNFOLD_SECONDS, 0f);
        assertEquals(0f, st.unfold, 1e-6f);
    }

    private static void run(SalvageWalkerArms.State st, float seconds, boolean cutting) {
        for (float t = 0f; t < seconds; t += DT) st.stripper(DT, cutting);
    }

    private static void run(SalvageWalkerArms.State st, float seconds, float share) {
        for (float t = 0f; t < seconds; t += DT) st.hauler(DT, HaulerWork.Kind.GO, 0f, 0f, share);
    }
}
