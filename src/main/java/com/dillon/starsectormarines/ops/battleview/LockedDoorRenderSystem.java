package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.door.LockedDoor;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * Every locked door ({@code garrisonables.md}, Locked doors and hardened walls), drawn over the wall it is shut as so
 * it reads as a door that will not open rather than more wall.
 *
 * <p>Shut, the span carries a door sprite of its length (art under {@code graphics/doors/}, from
 * {@code art-source/locked-doors}): a forcible door's two hazard-banded leaves with a red lamp on the seam, a hardened
 * door's ribbed armoured slab with a red lock bar. A span longer than the longest sprite stretches it. Open, the
 * leaves have withdrawn into the walls either side, where a jamb with a green lamp stands at each end and the doorway is
 * left clear. The sprites are authored along x; a door
 * across y is turned a quarter.
 */
final class LockedDoorRenderSystem implements RenderSystem {

    private static final String DIR = "graphics/doors/";
    /** The longest shut sprite of each kind, in cells. */
    private static final int FORCIBLE_LONGEST = 4, HARDENED_LONGEST = 2;
    /** An open jamb's width along the span, in cells. */
    private static final float JAMB = 0.4f;

    private final BattleSprites sprites;

    LockedDoorRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DOODADS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        List<LockedDoor> doors = ctx.sim.lockedDoors();
        if (doors.isEmpty() || sprites == null) return;
        BattleCamera cam = ctx.camera;
        float margin = 4f;
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

    private void draw(BattleCamera cam, DrawList out, LockedDoor door, float alpha) {
        boolean alongX = door.spansX();
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int[] c : door.cells()) {
            float a = alongX ? c[0] : c[1];
            min = Math.min(min, a);
            max = Math.max(max, a + 1f);
        }
        int[] first = door.cells()[0];
        float across = (alongX ? first[1] : first[0]) + 0.5f;
        String kind = door.hardened() ? "hardened" : "forcible";
        if (door.isOpen()) {
            // The leaves withdrew into the walls either side: the jambs stand there, the doorway left clear.
            piece(cam, out, kind + "-jamb-low", alongX, min - JAMB * 0.5f, across, JAMB, alpha);
            piece(cam, out, kind + "-jamb-high", alongX, max + JAMB * 0.5f, across, JAMB, alpha);
            return;
        }
        int length = Math.round(max - min);
        int sprite = Math.max(1, Math.min(length, door.hardened() ? HARDENED_LONGEST : FORCIBLE_LONGEST));
        piece(cam, out, kind + "-" + sprite, alongX, (min + max) * 0.5f, across, max - min, alpha);
    }

    /** One sprite centred at {@code along} on the span's axis and {@code across} it, {@code length} cells long. */
    private void piece(BattleCamera cam, DrawList out, String id, boolean alongX, float along, float across,
                       float length, float alpha) {
        ShuttleSpriteCache sprite = sprites.spriteByPath(DIR + id + ".png");
        if (sprite == null) return;
        float cx = alongX ? along : across, cy = alongX ? across : along;
        float px = cam.cellPxSize();
        out.addSprite(RenderLayer.DOODADS, sprite.sprite, cam.cellToScreenX(cx), cam.cellToScreenY(cy),
                length * px, px, alongX ? 0f : 90f, 1f, 1f, 1f, alpha);
    }
}
