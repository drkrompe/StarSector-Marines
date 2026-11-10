package com.dillon.starsectormarines.battle.world.gen;

import java.util.List;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a storm bank is dressed with, and where ({@link StormBankDressing#plan}). */
class StormBankDressingTest {

    private static final int W = 120;
    private static final int H = 100;
    /** The bank's one pass, cells along the windward edge; a road runs through its middle. */
    private static final int PASS0 = 40, PASS1 = 56, ROAD0 = 46, ROAD1 = 50;
    private static final int NEAR = StormBank.OFFSET, FAR = StormBank.OFFSET + StormBank.THICKNESS - 1;

    /** A bank drawn in edge coordinates: along {@code a}, in from the windward edge {@code d}. */
    private record Bank(StormWind.Side wind, int[] along, int[] in) {
        static Bank of(StormWind.Side wind) {
            int[] along = new int[W * H], in = new int[W * H];
            for (int a = 0; a < (wind.alongX() ? W : H); a++) {
                for (int d = 0; d < (wind.alongX() ? H : W); d++) {
                    int i = StormBankDressing.index(wind, W, H, a, d);
                    along[i] = a;
                    in[i] = d;
                }
            }
            return new Bank(wind, along, in);
        }

        boolean pass(int i) { return along[i] >= PASS0 && along[i] <= PASS1; }
        boolean face(int i) { return !pass(i) && (in[i] == NEAR || in[i] == FAR); }
        boolean top(int i) { return !pass(i) && in[i] >= NEAR && in[i] <= FAR; }
        boolean ramp(int i) { return along[i] >= 20 && along[i] <= 24 && in[i] > FAR && in[i] <= FAR + StormBank.RAMP; }
        boolean road(int i) { return along[i] >= ROAD0 && along[i] <= ROAD1; }
        boolean open(int i) { return !face(i) && !ramp(i) && !road(i); }

        StormBankDressing.Plan plan() {
            return StormBankDressing.plan(W, H, wind, this::top, this::face, this::ramp, this::open, this::road);
        }

        int at(int[] cell) { return cell[1] * W + cell[0]; }
    }

    @Test
    void nothingIsPlacedOnARoadOrInAPassAndTheTrackKeepsToOpenGround() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            Bank bank = Bank.of(wind);
            StormBankDressing.Plan plan = bank.plan();
            assertFalse(plan.props().isEmpty(), wind + ": the bank is dressed");
            for (StormBankDressing.Prop p : plan.props()) {
                int i = p.y() * W + p.x();
                assertFalse(bank.road(i), wind + ": " + p.id() + " stands on the road");
                assertFalse(bank.pass(i) && bank.in[i] < StormBank.OFFSET + StormBank.THICKNESS + StormBank.RAMP,
                        wind + ": " + p.id() + " stands in the pass");
                assertFalse(bank.ramp(i), wind + ": " + p.id() + " blocks a ramp");
            }
            assertFalse(plan.track().isEmpty(), wind + ": a track runs along the foot");
            for (int[] c : plan.track()) {
                assertTrue(bank.open(bank.at(c)) && !bank.top(bank.at(c)), wind + ": the track keeps to the foot");
            }
        }
    }

    @Test
    void sandLiesOnTheWindwardHalfAndTheLeeIsClean() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            Bank bank = Bank.of(wind);
            StormBankDressing.Plan plan = bank.plan();
            assertFalse(plan.sand().isEmpty(), wind + ": sand is banked");
            boolean onCrest = false;
            for (int[] c : plan.sand()) {
                int i = bank.at(c);
                assertTrue(bank.in[i] < StormBank.OFFSET + StormBank.THICKNESS / 2, wind + ": sand on the lee at "
                        + c[0] + "," + c[1]);
                assertFalse(bank.face(i), wind + ": sand skins a cliff face");
                onCrest |= bank.top(i);
            }
            assertTrue(onCrest, wind + ": sand reaches the crest");
        }
    }

    @Test
    void thePassIsGatedAndAMachineIsParkedBesideIt() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            Bank bank = Bank.of(wind);
            StormBankDressing.Plan plan = bank.plan();
            assertEquals(1, plan.passes().size(), wind + ": the one pass");
            assertEquals(PASS0, plan.passes().get(0)[0]);
            assertEquals(PASS1, plan.passes().get(0)[1]);
            List<StormBankDressing.Prop> gates = plan.props().stream()
                    .filter(p -> p.id().equals(StormBankDressing.GATE)).toList();
            assertEquals(2, gates.size(), wind + ": a barrier either side");
            for (StormBankDressing.Prop p : gates) {
                // The barrier is two cells along the bank from its cell: neither reaches into the pass.
                int a = bank.along[p.y() * W + p.x()];
                assertTrue(a == PASS0 - 2 || a == PASS1 + 1, wind + ": the barrier flanks the pass at " + a);
            }
            assertEquals(2, plan.props().stream().filter(p -> p.id().equals(StormBankDressing.LAMP)).count(),
                    wind + ": a lamp either side");
            assertTrue(plan.props().stream().anyMatch(p -> List.of(StormBankDressing.MACHINES).contains(p.id())),
                    wind + ": a machine waits at the pass");
        }
    }

    @Test
    void theOldLineLiesFarOutAndBrokenAndTheCrestFenceHasBreaks() {
        Bank bank = Bank.of(StormWind.Side.EAST);
        StormBankDressing.Plan plan = bank.plan();
        int bags = 0, fence = 0;
        for (StormBankDressing.Prop p : plan.props()) {
            int i = p.y() * W + p.x();
            if (p.id().contains("damaged") || p.id().contains("destroyed")) {
                bags++;
                assertEquals(StormBankDressing.OLD_LINE, bank.in[i], "the old line lies out to windward");
            }
            else if (p.id().startsWith(StormBankDressing.PARAPET)) {
                fence++;
                assertTrue(bank.top(i) && !bank.face(i), "the windbreak stands on the crest");
            }
        }
        int banked = H - (PASS1 - PASS0 + 1);
        assertTrue(bags > 0 && bags < H * 2 / 3, "the old line is broken: " + bags);
        assertTrue(fence > 0 && fence < banked, "the windbreak runs in panels: " + fence);
    }

    @Test
    void groundWithNoBankIsLeftAlone() {
        IntPredicate none = i -> false;
        StormBankDressing.Plan plan = StormBankDressing.plan(W, H, StormWind.Side.WEST, none, none, none, i -> true,
                none);
        assertTrue(plan.sand().isEmpty() && plan.track().isEmpty() && plan.props().isEmpty()
                && plan.oldLine().isEmpty());
    }
}
