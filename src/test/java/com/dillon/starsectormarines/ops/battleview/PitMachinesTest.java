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
     * The ore's path reads: the loader stands at the driver's foot on the floor with its hopper turned from the rail,
     * and the cutter works a face on the floor near enough to feed it, its back to the loader.
     */
    @Test
    void theCutterFeedsTheLoaderAtTheDriversFoot() {
        NaturalVoidField field = field();
        NaturalVoid.Belt driver = spine();
        List<PitMachines.Machine> machines = PitMachines.place(field, PitVoidLook.ramps(field), driver, 0, 5);
        int n = field.steps();
        PitMachines.Machine loader = only(machines, PitMachines.Kind.LOADER);
        assertEquals(n, loader.step());
        assertTrue(PitMachines.on(field, n, loader.x(), loader.y()), "the loader is off the floor");
        float sx = driver.rimX() - driver.floorX(), sy = driver.rimY() - driver.floorY();
        assertTrue(loader.headingX() * sx + loader.headingY() * sy < 0f, "the hopper faces the rail");
        assertTrue(Math.hypot(loader.x() - driver.floorX(), loader.y() - driver.floorY()) < 4f,
                "the loader is not at the driver's foot");
        PitMachines.Machine cutter = only(machines, PitMachines.Kind.CUTTER);
        assertTrue(PitMachines.on(field, n, cutter.x(), cutter.y()), "the cutter is off the floor");
        float away = (float) Math.hypot(cutter.x() - loader.x(), cutter.y() - loader.y());
        assertTrue(away >= PitMachines.FEED_MIN && away <= PitMachines.FEED_MAX, "the cutter cannot feed the loader");
        assertTrue(cutter.headingX() * (cutter.x() - loader.x()) + cutter.headingY() * (cutter.y() - loader.y()) > 0f,
                "the cutter faces the loader instead of its face");
    }

    /**
     * Haulers bring the benches' ore to the loader: one hovering in to tip, turned toward the loader; one loaded over
     * a bench turned into the pit and one empty over another bench turned out of it; none on the haul road, and a
     * gantry drills a bench. A pit that sends no driver has nothing to load.
     */
    @Test
    void haulersFerryTheBenchesOreToTheLoader() {
        NaturalVoidField field = field();
        List<PitVoidLook.Ramp> ramps = PitVoidLook.ramps(field);
        List<PitMachines.Machine> machines = PitMachines.place(field, ramps, spine(), 0, 5);
        int n = field.steps();
        PitMachines.Machine loader = only(machines, PitMachines.Kind.LOADER);
        boolean tipping = false, lifting = false, returning = false;
        for (PitMachines.Machine m : machines) {
            assertTrue(m.rampWall() < 0, "nothing works the haul road now: " + m);
            if (m.kind() != PitMachines.Kind.HAULER_LOADED && m.kind() != PitMachines.Kind.HAULER_EMPTY) continue;
            assertTrue(m.hover() > 0f, "a hauler hovers");
            assertEquals(field.depth[m.step()] - m.hover(), m.depth(), 1e-4f, "it flies its hover over its band");
            float[] out = PitMachines.outward(field, field.reach[m.step()], m.x(), m.y());
            if (m.step() == n) {
                float toX = loader.x() - m.x(), toY = loader.y() - m.y();
                tipping |= m.kind() == PitMachines.Kind.HAULER_LOADED && m.headingX() * toX + m.headingY() * toY > 0f;
            } else if (out != null) {
                float outward = m.headingX() * out[0] + m.headingY() * out[1];
                lifting |= m.kind() == PitMachines.Kind.HAULER_LOADED && outward < 0f;
                returning |= m.kind() == PitMachines.Kind.HAULER_EMPTY && outward > 0f;
            }
        }
        assertTrue(tipping && lifting && returning, "tipping " + tipping + ", lifting " + lifting + ", returning "
                + returning);
        PitMachines.Machine gantry = only(machines, PitMachines.Kind.GANTRY);
        assertTrue(gantry.step() < n, "the gantry works a bench, not the floor");
        assertTrue(PitMachines.place(field, ramps, null, 0, 5).stream().noneMatch(m ->
                m.kind() != PitMachines.Kind.GANTRY), "no driver, nothing to load");
    }
}
