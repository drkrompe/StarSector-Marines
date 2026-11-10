package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.elevation.OpenPit;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PitMachinesTest {

    static final int W = 80, H = 64;

    /** An oval pit filling most of a small field. */
    static boolean[] oval() {
        boolean[] v = new boolean[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float dx = (x + .5f - W / 2f) / 34f, dy = (y + .5f - H / 2f) / 26f;
            v[y * W + x] = dx * dx + dy * dy <= 1f;
        }
        return v;
    }

    static NaturalVoidField field() {
        return new NaturalVoidField(W, H, oval(), OpenPit.look(5, List.of()), -.7f, -.7f, .8f);
    }

    /** The spine the pit sends to a works off its east edge, as the landscape pass works it out. */
    static NaturalVoid.Belt spine() {
        return OpenPit.belt(W, H, oval(), W + 20f, H / 2f);
    }

    private static PitMachines.Machine only(List<PitMachines.Machine> machines, PitMachines.Kind kind) {
        PitMachines.Machine found = null;
        for (PitMachines.Machine m : machines) {
            if (m.kind() != kind) continue;
            assertTrue(found == null, "one " + kind);
            found = m;
        }
        assertNotNull(found, "no " + kind);
        return found;
    }

    /**
     * The ore's path reads: the crushing station stands at the spine's foot on the floor with its hopper turned from
     * the spine, and the cutter works a face on the floor near enough to feed it, its back to the station.
     */
    @Test
    void theCutterFeedsTheStationAtTheSpinesFoot() {
        NaturalVoidField field = field();
        NaturalVoid.Belt spine = spine();
        List<PitMachines.Machine> machines = PitMachines.place(field, PitVoidLook.ramps(field), spine, 0, 5);
        int n = field.steps();
        PitMachines.Machine station = only(machines, PitMachines.Kind.CRUSHER);
        assertEquals(n, station.step());
        assertTrue(PitMachines.on(field, n, station.x(), station.y()), "the station is off the floor");
        float sx = spine.rimX() - spine.floorX(), sy = spine.rimY() - spine.floorY();
        assertTrue(station.headingX() * sx + station.headingY() * sy < 0f, "the hopper faces the spine");
        assertTrue(Math.hypot(station.x() - spine.floorX(), station.y() - spine.floorY()) < 4f,
                "the station is not at the spine's foot");
        PitMachines.Machine cutter = only(machines, PitMachines.Kind.CUTTER);
        assertTrue(PitMachines.on(field, n, cutter.x(), cutter.y()), "the cutter is off the floor");
        float away = (float) Math.hypot(cutter.x() - station.x(), cutter.y() - station.y());
        assertTrue(away >= PitMachines.FEED_MIN && away <= PitMachines.FEED_MAX, "the cutter cannot feed the station");
        assertTrue(cutter.headingX() * (cutter.x() - station.x()) + cutter.headingY() * (cutter.y() - station.y()) > 0f,
                "the cutter faces the station instead of its face");
    }

    /** Ore from the upper benches joins the spine: a hopper beside it on a bench, a loaded crawler backed up to it. */
    @Test
    void theBenchesFeedTheSpineThroughAHopper() {
        NaturalVoidField field = field();
        List<PitMachines.Machine> machines = PitMachines.place(field, PitVoidLook.ramps(field), spine(), 0, 5);
        PitMachines.Machine hopper = only(machines, PitMachines.Kind.HOPPER);
        assertTrue(hopper.step() > 0 && hopper.step() < field.steps(), "the hopper is not on a bench");
        assertTrue(PitMachines.on(field, hopper.step(), hopper.x(), hopper.y()));
        assertTrue(machines.stream().anyMatch(m -> m.kind() == PitMachines.Kind.ORE_LOADED && m.rampWall() < 0
                && m.step() == hopper.step()), "no crawler tipping into the hopper");
    }

    /**
     * The haul road carries a loaded crawler up and an empty one down; a gantry drills a bench. A pit that sends no
     * spine has no cutter or station.
     */
    @Test
    void theRoadAndBenchesAreWorkedAndNoSpineMeansNoCrushing() {
        NaturalVoidField field = field();
        List<PitVoidLook.Ramp> ramps = PitVoidLook.ramps(field);
        List<PitMachines.Machine> machines = PitMachines.place(field, ramps, spine(), 0, 5);
        PitMachines.Machine gantry = only(machines, PitMachines.Kind.GANTRY);
        assertTrue(gantry.step() < field.steps(), "the gantry works a bench, not the floor");
        boolean up = false, down = false;
        for (PitMachines.Machine m : machines) {
            if (m.rampWall() < 0) continue;
            assertEquals(m.rampWall() + 1, m.step());
            PitVoidLook.Ramp piece = nearest(ramps, m);
            float[] p = piece.corners();
            float along = (p[3] - p[0]) * m.headingX() + (p[4] - p[1]) * m.headingY();
            boolean towardShallow = along > 0 ? p[5] < p[2] : p[2] < p[5];
            assertEquals(m.kind() == PitMachines.Kind.ORE_LOADED, towardShallow, m.toString());
            up |= m.kind() == PitMachines.Kind.ORE_LOADED;
            down |= m.kind() == PitMachines.Kind.ORE_EMPTY;
        }
        assertTrue(up && down, "the road carries a loaded crawler and an empty one");
        assertTrue(PitMachines.place(field, ramps, null, 0, 5).stream().noneMatch(m ->
                m.kind() == PitMachines.Kind.CUTTER || m.kind() == PitMachines.Kind.CRUSHER));
    }

    private static PitVoidLook.Ramp nearest(List<PitVoidLook.Ramp> ramps, PitMachines.Machine m) {
        PitVoidLook.Ramp best = null;
        double bestD = Double.MAX_VALUE;
        for (PitVoidLook.Ramp r : ramps) {
            if (r.side() || r.lane() != 2 || r.wall() != m.rampWall()) continue;
            float[] p = r.corners();
            double d = Math.hypot((p[0] + p[3] + p[6] + p[9]) / 4f - m.x(), (p[1] + p[4] + p[7] + p[10]) / 4f - m.y());
            if (d < bestD) { bestD = d; best = r; }
        }
        return best;
    }
}
