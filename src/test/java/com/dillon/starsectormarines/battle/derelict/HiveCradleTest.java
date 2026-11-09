package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.HiveCradle.Hatch;
import com.dillon.starsectormarines.battle.derelict.HiveCradle.HatchState;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveCradleTest {

    private static final float DT = 0.1f;

    private static RuinsDelve.Court court(int index) {
        return new RuinsDelve.Court("ruins.layer." + (index + 1) + ".court", new Area(index * 30 + 5, 5, index * 30 + 15, 15), null);
    }

    /** A delve of {@code layers} layers, inside from {@code firstInterior}, with courts where {@code courts} says. */
    private static RuinsDelve delve(int layers, int firstInterior, boolean... courts) {
        List<RuinsDelve.Layer> list = new ArrayList<>();
        for (int i = 0; i < layers; i++) {
            list.add(new RuinsDelve.Layer("ruins.layer." + (i + 1), i, null, new Area(i * 30, 0, i * 30 + 29, 40),
                    i * 30, i * 30, List.of(), List.of(), courts[i] ? court(i) : null));
        }
        RuinsDelve.Interior interior = new RuinsDelve.Interior(RuinsDelve.INTERIOR, new Area(firstInterior * 30, 0, 200, 40),
                firstInterior, List.of(), List.of());
        return new RuinsDelve(layers, null, List.of(), null, list, null, List.of(), null, null, interior);
    }

    @Test
    void theGuardianHoldsTheCourtOfTheDeepestInteriorLayerBeforeTheVault() {
        RuinsDelve vast = delve(4, 2, false, true, true, true);
        assertSame(vast.layers().get(2).court(), HiveCradle.site(vast, RuinsGrade.VAST));
        RuinsDelve noCourtThere = delve(4, 1, false, true, false, true);
        assertSame(noCourtThere.layers().get(1).court(), HiveCradle.site(noCourtThere, RuinsGrade.EXTENSIVE),
                "back toward the entrance, still inside");
    }

    @Test
    void noGuardianBelowExtensiveOrOutsideTheInterior() {
        assertNull(HiveCradle.site(delve(3, 2, false, true, true), RuinsGrade.WIDESPREAD));
        assertNull(HiveCradle.site(delve(4, 3, false, true, true, true), RuinsGrade.VAST),
                "the only inside layer is the vault's own");
    }

    @Test
    void itOpensAfterAWarningOnlyOnceTheCycleIsUpAndItsFlockIsShort() {
        Hatch hatch = new Hatch();
        run(hatch, HiveCradle.CYCLE_SECONDS + 1f, true, HiveCradle.BAY);
        assertEquals(HatchState.SHUT, hatch.state(), "a whole flock keeps the hatch shut");
        hatch.tick(DT, true, true, HiveCradle.BAY - 5);
        assertEquals(HatchState.WARNING, hatch.state());
        assertEquals(HiveCradle.SHUT_ARMOR_RATING, hatch.armorRating(), "still plated while it warns");
        int launched = run(hatch, HiveCradle.WARNING_SECONDS + 0.05f, true, HiveCradle.BAY - 5);
        assertEquals(0, launched);
        assertEquals(HatchState.OPEN, hatch.state());
        assertEquals(HiveCradle.OPEN_ARMOR_RATING, hatch.armorRating());
    }

    @Test
    void openItPutsUpOnlyWhatTheFlockLacksThenShutsAfterAFixedWindow() {
        Hatch hatch = new Hatch();
        run(hatch, HiveCradle.CYCLE_SECONDS, true, HiveCradle.BAY);
        int up = HiveCradle.BAY - 5;
        int launched = 0;
        float open = 0f;
        for (int i = 0; i < 200; i++) {
            int n = hatch.tick(DT, true, true, up);
            up += n;
            launched += n;
            if (hatch.state() == HatchState.OPEN) open += DT;
        }
        assertEquals(5, launched);
        assertEquals(HiveCradle.BAY, up);
        assertTrue(Math.abs(open - HiveCradle.OPEN_SECONDS) < 0.25f, "open " + open);
    }

    @Test
    void cutPowerShutsItColdAndRestartsTheCycle() {
        Hatch hatch = new Hatch();
        run(hatch, HiveCradle.CYCLE_SECONDS + HiveCradle.WARNING_SECONDS + 0.5f, true, 0);
        assertEquals(HatchState.OPEN, hatch.state());
        int bay = hatch.bay();
        assertEquals(0, run(hatch, 200f, false, 0), "cold, it launches nothing");
        assertEquals(HatchState.SHUT, hatch.state());
        assertEquals(bay, hatch.bay(), "and builds nothing");
        run(hatch, HiveCradle.CYCLE_SECONDS - 1f, true, 0);
        assertEquals(HatchState.SHUT, hatch.state(), "power back, it waits a whole cycle");
    }

    @Test
    void aDeadNodeQuietsItAsCutPowerDoes() {
        Hatch hatch = new Hatch();
        int launched = 0;
        for (float t = 0f; t < 200f; t += DT) launched += hatch.tick(DT, true, false, 0);
        assertEquals(0, launched);
        assertEquals(HatchState.SHUT, hatch.state());
    }

    @Test
    void anEmptiedBayRefillsInAboutOneCycle() {
        Hatch hatch = new Hatch();
        // Its flock shot down as fast as it goes up, so the bay empties.
        for (int i = 0; i < 2000 && hatch.bay() > 0; i++) hatch.tick(DT, true, true, 0);
        assertEquals(0, hatch.bay());
        float t = 0f;
        while (hatch.bay() < HiveCradle.BAY && t < 2 * HiveCradle.CYCLE_SECONDS) {
            hatch.tick(DT, true, true, HiveCradle.BAY);
            t += DT;
        }
        assertTrue(Math.abs(t - HiveCradle.CYCLE_SECONDS) < 1f, "refilled in " + t);
    }

    private static int run(Hatch hatch, float seconds, boolean powered, int airborne) {
        int launched = 0;
        for (float t = 0f; t < seconds; t += DT) launched += hatch.tick(DT, powered, true, airborne);
        return launched;
    }
}
