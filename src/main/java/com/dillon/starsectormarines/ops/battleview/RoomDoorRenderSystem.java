package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.RoomDoor;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Every compartment's sliding door, drawn over the doorway's floor and under
 * whoever walks through it.
 *
 * <p>Two plated leaves meet across the doorway and withdraw into the jambs as
 * the door opens, so only what still stands in the opening is drawn and the
 * bulkhead hides the rest. A locked door is the same pair, shut, with a red
 * lock bar across the seam and hazard banding on its leaves: a sealed
 * compartment reads as a door that will not open rather than a patch.
 */
final class RoomDoorRenderSystem implements RenderSystem {

    /** How thick a leaf is through the bulkhead, in cells. */
    private static final float LEAF_DEPTH = 0.42f;
    /** Width of the coloured band on each leaf's meeting edge, in cells. */
    private static final float EDGE_BAND = 0.12f;

    private static final float[] LEAF = rgb(0x56, 0x62, 0x6C);
    private static final float[] LEAF_LOCKED = rgb(0x45, 0x40, 0x3E);
    private static final float[] SEAM = rgb(0x26, 0x2D, 0x33);
    private static final float[] RIB = rgb(0x74, 0x82, 0x8C);
    private static final float[] EDGE = rgb(0xE0, 0xB0, 0x40);
    private static final float[] HAZARD = rgb(0xC8, 0x3C, 0x2C);
    private static final float[] LOCK = rgb(0xFF, 0x4A, 0x36);

    @Override
    public RenderLayer layer() {
        return RenderLayer.DOODADS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.roomDoors.isEmpty()) return;
        BattleCamera cam = ctx.camera;
        float margin = 2f;
        float left = cam.screenToCellX(cam.vpX()) - margin, right = cam.screenToCellX(cam.vpX() + cam.vpW()) + margin;
        float top = cam.screenToCellY(cam.vpY()) - margin, bottom = cam.screenToCellY(cam.vpY() + cam.vpH()) + margin;
        for (RoomDoor door : ctx.roomDoors) {
            float cx = door.centerX(), cy = door.centerY();
            if (cx < left || cx > right || cy < top || cy > bottom) continue;
            draw(cam, out, door, ctx.alphaMult);
        }
    }

    private static void draw(BattleCamera cam, DrawList out, RoomDoor door, float alpha) {
        float half = (door.spanMax() - door.spanMin()) * 0.5f;
        float reach = half * (1f - door.leafTravel());
        if (reach < 0.02f) return;
        float depth = LEAF_DEPTH * 0.5f;
        boolean locked = door.locked();
        for (int side = -1; side <= 1; side += 2) {
            // From the jamb in toward the seam: [jamb, edge] along the span.
            float jamb = side < 0 ? door.spanMin() : door.spanMax();
            float edge = jamb - side * reach;
            rect(cam, out, door, jamb, edge, -depth, depth, locked ? LEAF_LOCKED : LEAF, alpha);
            rect(cam, out, door, jamb, edge, -depth, -depth + 0.05f, SEAM, alpha);
            rect(cam, out, door, jamb, edge, depth - 0.05f, depth, SEAM, alpha);
            if (locked) {
                // Hazard banding at whole-third spacing from the seam.
                for (float band = 0.18f; band < reach - 0.05f; band += 0.33f) {
                    float at = edge + side * band;
                    rect(cam, out, door, at, at + side * 0.12f, -depth + 0.05f, depth - 0.05f, HAZARD, alpha);
                }
            } else {
                for (float rib = 0.35f; rib < reach - EDGE_BAND; rib += 0.35f) {
                    float at = edge + side * rib;
                    rect(cam, out, door, at, at + side * 0.04f, -depth + 0.05f, depth - 0.05f, RIB, alpha);
                }
            }
            rect(cam, out, door, edge, edge + side * Math.min(EDGE_BAND, reach), -depth, depth,
                    locked ? LOCK : EDGE, alpha);
        }
        if (locked) {
            // The lock bar across the seam, through both leaves.
            float seam = (door.spanMin() + door.spanMax()) * 0.5f;
            rect(cam, out, door, seam - Math.min(0.45f, half), seam + Math.min(0.45f, half), -0.07f, 0.07f, LOCK, alpha);
        }
    }

    /**
     * Fills the door-aligned rectangle from {@code a0} to {@code a1} along the
     * span and {@code o0} to {@code o1} across the bulkhead from its centre line.
     */
    private static void rect(BattleCamera cam, DrawList out, RoomDoor door,
                             float a0, float a1, float o0, float o1, float[] colour, float alpha) {
        float s0 = Math.min(a0, a1), s1 = Math.max(a0, a1);
        float c0 = door.across() + Math.min(o0, o1), c1 = door.across() + Math.max(o0, o1);
        float x0 = door.spanX() ? s0 : c0, x1 = door.spanX() ? s1 : c1;
        float y0 = door.spanX() ? c0 : s0, y1 = door.spanX() ? c1 : s1;
        out.addSolidRect(RenderLayer.DOODADS, cam.cellToScreenX(x0), cam.cellToScreenY(y0),
                cam.cellToScreenX(x1), cam.cellToScreenY(y1), colour[0], colour[1], colour[2], alpha);
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }
}
