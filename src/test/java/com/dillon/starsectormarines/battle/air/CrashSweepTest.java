package com.dillon.starsectormarines.battle.air;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrashSweepTest {
    @Test void aLongTickAndManyShortTicksCoverTheSameGround() {
        var whole = new CrashSweep(0, 2, 4);
        var stepped = new CrashSweep(0, 2, 4);
        var a = new ArrayList<Float>();
        var b = new ArrayList<Float>();
        whole.advance(11, 2, (x, y) -> a.add(x));
        for (int i = 1; i <= 110; i++) stepped.advance(i / 10f, 2, (x, y) -> b.add(x));
        assertEquals(5, a.size());
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i), b.get(i), .0001f);
    }

    @Test void aStoppedHullDoesNotKeepApplyingContactDamage() {
        var sweep = new CrashSweep(3, 4, 2);
        for (int i = 0; i < 500; i++) sweep.advance(3, 4, (x, y) -> fail("stationary contact"));
    }
}
