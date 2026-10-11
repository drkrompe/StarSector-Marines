package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanceSweepSystemTest {

    private static final float DT = 1f / 30f;

    private static LanceSweep sweep() {
        return new LanceSweep(1L, Faction.ALLY, 10f, 10f,
                0f, (float) Math.PI / 2f, 20f, 2f, 1f, 3f, 300f, 4f, 50);
    }

    @Test
    void angleHoldsThroughTelegraphThenWalksToTheEnd() {
        LanceSweep s = sweep();
        assertEquals(0f, s.angleAt(0.5f), 1e-6);
        assertEquals((float) Math.PI / 4f, s.angleAt(2.5f), 1e-5);
        assertEquals((float) Math.PI / 2f, s.angleAt(10f), 1e-6);
    }

    @Test
    void nothingBurnsDuringTelegraph() {
        LanceSweepSystem system = new LanceSweepSystem();
        system.start(sweep());
        List<PendingDetonation> landed = new ArrayList<>();
        for (int i = 0; i < 29; i++) system.tick(DT, landed::add);
        assertTrue(landed.isEmpty());
        assertFalse(system.active().get(0).isBurning());
    }

    @Test
    void eachBurningTickLandsOneImpactPerCentreAndFriendlyFireIsOn() {
        LanceSweepSystem system = new LanceSweepSystem();
        LanceSweep s = sweep();
        system.start(s);
        List<PendingDetonation> landed = new ArrayList<>();
        for (int i = 0; i < 31; i++) system.tick(DT, d -> { });
        system.tick(DT, landed::add);
        system.tick(DT, landed::add);
        assertEquals(s.sampleCount() * 2, landed.size());
        PendingDetonation first = landed.get(0);
        assertFalse(first.friendlyFireImmune);
        assertEquals(s.halfWidth(), first.aoeRadius, 1e-6);
        assertEquals(300f * DT, first.damage, 1e-4);
    }

    @Test
    void sweepRetiresWhenItsBurnEnds() {
        LanceSweepSystem system = new LanceSweepSystem();
        system.start(sweep());
        for (int i = 0; i < 125; i++) system.tick(DT, d -> { });
        assertTrue(system.active().isEmpty());
    }

    @Test
    void impactCentresReachOutToThePlannedReach() {
        LanceSweep s = sweep();
        int last = s.sampleCount() - 1;
        assertEquals(10f + 20f, s.sampleX(0f, last), 1e-4);
        assertEquals(10f, s.sampleY(0f, last), 1e-4);
    }
}
