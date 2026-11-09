package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.door.LockedDoor;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * Every locked door ({@code garrisonables.md}, Locked doors and hardened walls), drawn over the wall it is shut as so
 * it reads as a door that will not open rather than more wall.
 *
 * <p>Shut, the span is a plated slab with a seam down its middle and a red lamp on it. A hardened door is heavy grey
 * plate with a red lock bar across the seam: only its lock opens it. A forcible door carries yellow hazard banding,
 * the mark of a door a charge can open. Open, the leaves have withdrawn into the jambs and only a green lamp at each
 * jamb is left.
 */
final class LockedDoorRenderSystem implements RenderSystem {

    /** How thick the slab is through the wall, in cells. */
    private static final float DEPTH = 0.84f;
    private static final float FRAME = 0.10f;
    private static final float LAMP = 0.26f;

    private static final float[] FRAME_DARK = rgb(0x22, 0x26, 0x2A);
    private static final float[] PLATE_HARD = rgb(0x5E, 0x68, 0x72);
    private static final float[] RIB_HARD = rgb(0x7C, 0x88, 0x92);
    private static final float[] PLATE_FORCIBLE = rgb(0x4C, 0x47, 0x42);
    private static final float[] HAZARD = rgb(0xE0, 0xB0, 0x40);
    private static final float[] LOCK = rgb(0xFF, 0x4A, 0x36);
    private static final float[] OPEN_LAMP = rgb(0x5A, 0xE6, 0x7A);

    @Override
    public RenderLayer layer() {
        return RenderLayer.DOODADS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        List<LockedDoor> doors = ctx.sim.lockedDoors();
        if (doors.isEmpty()) return;
        BattleCamera cam = ctx.camera;
        float margin = 3f;
        float left = cam.screenToCellX(cam.vpX()) - margin, right = cam.screenToCellX(cam.vpX() + cam.vpW()) + margin;
        float low = Math.min(cam.screenToCellY(cam.vpY()), cam.screenToCellY(cam.vpY() + cam.vpH())) - margin;
        float high = Math.max(cam.screenToCellY(cam.vpY()), cam.screenToCellY(cam.vpY() + cam.vpH())) + margin;
        for (LockedDoor door : doors) {
            int[][] cells = door.cells();
            int[] c = cells[cells.length / 2];
            if (c[0] < left || c[0] > right || c[1] < low || c[1] > high) continue;
            draw(cam, out, door, ctx.alphaMult);
        }
    }

    private static void draw(BattleCamera cam, DrawList out, LockedDoor door, float alpha) {
        Span s = Span.of(door);
        float half = DEPTH * 0.5f;
        if (door.isOpen()) {
            float lamp = LAMP * 0.5f;
            rect(cam, out, s, s.min - lamp - 0.08f, s.min - 0.08f, -lamp, lamp, OPEN_LAMP, alpha);
            rect(cam, out, s, s.max + 0.08f, s.max + lamp + 0.08f, -lamp, lamp, OPEN_LAMP, alpha);
            return;
        }
        boolean hard = door.hardened();
        rect(cam, out, s, s.min, s.max, -half, half, FRAME_DARK, alpha);
        float inset = FRAME;
        rect(cam, out, s, s.min + inset, s.max - inset, -half + inset, half - inset,
                hard ? PLATE_HARD : PLATE_FORCIBLE, alpha);
        float seam = (s.min + s.max) * 0.5f;
        if (hard) {
            // Heavy ribs across each leaf, then the lock bar along the seam's line, through both leaves.
            for (float at = s.min + 0.3f; at < s.max - 0.25f; at += 0.3f) {
                if (Math.abs(at - seam) < 0.12f) continue;
                rect(cam, out, s, at - 0.035f, at + 0.035f, -half + inset, half - inset, RIB_HARD, alpha);
            }
            float bar = Math.min(0.55f, (s.max - s.min) * 0.5f - inset);
            rect(cam, out, s, seam - bar, seam + bar, -0.08f, 0.08f, LOCK, alpha);
        } else {
            // Hazard bands: a door a charge opens.
            for (float at = s.min + inset + 0.08f; at < s.max - inset - 0.1f; at += 0.34f) {
                rect(cam, out, s, at, Math.min(at + 0.16f, s.max - inset), -half + inset, half - inset, HAZARD, alpha);
            }
        }
        rect(cam, out, s, seam - 0.035f, seam + 0.035f, -half, half, FRAME_DARK, alpha);
        float lamp = LAMP * 0.5f;
        rect(cam, out, s, seam - lamp, seam + lamp, -lamp, lamp, LOCK, alpha);
    }

    /** A door's run along its axis, in cells, and the centre line across it. */
    private record Span(boolean alongX, float min, float max, float across) {
        static Span of(LockedDoor door) {
            boolean alongX = door.spansX();
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int[] c : door.cells()) {
                int a = alongX ? c[0] : c[1];
                lo = Math.min(lo, a);
                hi = Math.max(hi, a);
            }
            int[] first = door.cells()[0];
            return new Span(alongX, lo, hi + 1f, (alongX ? first[1] : first[0]) + 0.5f);
        }
    }

    /** Fills the door-aligned rectangle from {@code a0} to {@code a1} along the span, {@code o0} to {@code o1} across. */
    private static void rect(BattleCamera cam, DrawList out, Span s, float a0, float a1, float o0, float o1,
                             float[] colour, float alpha) {
        float s0 = Math.min(a0, a1), s1 = Math.max(a0, a1);
        float c0 = s.across + Math.min(o0, o1), c1 = s.across + Math.max(o0, o1);
        float x0 = s.alongX ? s0 : c0, x1 = s.alongX ? s1 : c1;
        float y0 = s.alongX ? c0 : s0, y1 = s.alongX ? c1 : s1;
        out.addSolidRect(RenderLayer.DOODADS, cam.cellToScreenX(x0), cam.cellToScreenY(y0),
                cam.cellToScreenX(x1), cam.cellToScreenY(y1), colour[0], colour[1], colour[2], alpha);
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }
}
