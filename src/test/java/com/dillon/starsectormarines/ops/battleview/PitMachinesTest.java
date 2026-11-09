package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.elevation.OpenPit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PitMachinesTest {

    static final int W = 80, H = 64;

    /** An oval pit filling most of a small field. */
    static NaturalVoidField pit() {
        boolean[] v = new boolean[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float dx = (x + .5f - W / 2f) / 34f, dy = (y + .5f - H / 2f) / 26f;
            v[y * W + x] = dx * dx + dy * dy <= 1f;
        }
        return new NaturalVoidField(W, H, v, OpenPit.look(5, List.of()), -.7f, -.7f, .8f);
    }

    /**
     * The pit is worked: a shovel loads a truck on the floor, drills stand on a bench, and the road carries a loaded
     * truck up and an empty one down. Every machine on a band stands on it, and those on the floor stand dry.
     */
    @Test
    void aPitIsWorkedFromItsFloorItsBenchesAndItsRoad() {
        NaturalVoidField field = pit();
        List<PitVoidLook.Ramp> ramps = PitVoidLook.ramps(field);
        List<PitMachines.Machine> machines = PitMachines.place(field, ramps, 5);
        int n = field.steps();
        boolean shovel = false, drill = false, up = false, down = false;
        for (PitMachines.Machine m : machines) {
            if (m.rampWall() < 0) {
                assertTrue(PitMachines.on(field, m.step(), m.x(), m.y()), m + " is off its band");
                if (m.step() == n) shovel |= m.kind() == PitMachines.Kind.SHOVEL;
                if (m.kind() == PitMachines.Kind.DRILL) {
                    assertTrue(m.step() < n, "drills work a bench, not the floor");
                    drill = true;
                }
                continue;
            }
            assertEquals(m.rampWall() + 1, m.step());
            // Up the road is where the depth falls along the truck's heading.
            float ahead = field.depthAt(m.x() + m.headingX() * 3f, m.y() + m.headingY() * 3f);
            float behind = field.depthAt(m.x() - m.headingX() * 3f, m.y() - m.headingY() * 3f);
            if (m.kind() == PitMachines.Kind.HAUL_LOADED) up = true;
            if (m.kind() == PitMachines.Kind.HAUL_EMPTY) down = true;
            assertFalse(ahead == behind && ahead == 0f, m + " is not on the road");
        }
        assertTrue(shovel, "no shovel at the face's toe");
        assertTrue(machines.stream().anyMatch(m -> m.kind() == PitMachines.Kind.HAUL_LOADED && m.rampWall() < 0),
                "no truck loading beside the shovel");
        assertTrue(drill, "no drill on a bench");
        assertTrue(up && down, "the road carries a loaded truck and an empty one");
    }

    /** A loaded truck on the road heads up it, an empty one down. */
    @Test
    void aLoadedTruckClimbsAndAnEmptyOneDescends() {
        NaturalVoidField field = pit();
        List<PitVoidLook.Ramp> ramps = PitVoidLook.ramps(field);
        for (PitMachines.Machine m : PitMachines.place(field, ramps, 5)) {
            if (m.rampWall() < 0) continue;
            PitVoidLook.Ramp piece = nearest(ramps, m);
            float[] p = piece.corners();
            float along = (p[3] - p[0]) * m.headingX() + (p[4] - p[1]) * m.headingY();
            boolean towardShallow = along > 0 ? p[5] < p[2] : p[2] < p[5];
            assertEquals(m.kind() == PitMachines.Kind.HAUL_LOADED, towardShallow, m.toString());
        }
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
