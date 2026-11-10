package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.RoomDoor;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Every compartment's sliding door, drawn over the doorway's floor and under
 * whoever walks through it.
 *
 * <p>Two plated leaves meet across the doorway and withdraw into the jambs as
 * the door opens, so only what still stands in the opening is drawn and the
 * bulkhead hides the rest. Each leaf is the ship's own painted leaf
 * (art under {@code graphics/doors/ship-*}, from {@code art-source/ship-doors}),
 * authored along x with its jamb end left and its meeting edge right: the part
 * still in the opening is the leaf's meeting end, cut from the image at the
 * art's own density, and a leaf longer than the art stretches it. A locked
 * door (a sealed hatch) is the pair shut in their hazard-banded variant with a
 * seal clamp across the seam: a sealed compartment reads as a door that will
 * not open rather than a patch.
 */
final class RoomDoorRenderSystem implements RenderSystem {

    private static final String DIR = "graphics/doors/";
    /** How thick a leaf is through the bulkhead, in cells. */
    private static final float LEAF_DEPTH = 0.42f;
    /** How long the leaf art is, in cells, at its own density. */
    private static final float LEAF_CELLS = 1.5f;
    /** How long the seal clamp is across the seam, in cells. */
    private static final float CLAMP_CELLS = 0.9f;

    private final BattleSprites sprites;

    RoomDoorRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DOODADS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.roomDoors.isEmpty() || sprites == null) return;
        ShuttleSpriteCache leaf = sprites.spriteByPath(DIR + "ship-leaf.png");
        ShuttleSpriteCache locked = sprites.spriteByPath(DIR + "ship-leaf-locked.png");
        ShuttleSpriteCache clamp = sprites.spriteByPath(DIR + "ship-clamp.png");
        if (leaf == null || locked == null || clamp == null) return;
        BattleCamera cam = ctx.camera;
        float margin = 2f;
        float left = cam.screenToCellX(cam.vpX()) - margin, right = cam.screenToCellX(cam.vpX() + cam.vpW()) + margin;
        float top = cam.screenToCellY(cam.vpY()) - margin, bottom = cam.screenToCellY(cam.vpY() + cam.vpH()) + margin;
        for (RoomDoor door : ctx.roomDoors) {
            float cx = door.centerX(), cy = door.centerY();
            if (cx < left || cx > right || cy < top || cy > bottom) continue;
            draw(cam, out, door, door.locked() ? locked : leaf, clamp, ctx.alphaMult);
        }
    }

    private static void draw(BattleCamera cam, DrawList out, RoomDoor door, ShuttleSpriteCache leaf,
                             ShuttleSpriteCache clamp, float alpha) {
        float half = (door.spanMax() - door.spanMin()) * 0.5f;
        float reach = half * (1f - door.leafTravel());
        if (reach < 0.02f) return;
        float px = cam.cellPxSize();
        // The part of the art still in the opening: its meeting end, at the art's own density.
        int srcW = Math.max(1, Math.min(leaf.pxW, Math.round(leaf.pxW * reach / LEAF_CELLS)));
        for (int side = -1; side <= 1; side += 2) {
            // From the jamb in toward the seam; the art's meeting end faces the seam.
            float jamb = side < 0 ? door.spanMin() : door.spanMax();
            float along = jamb - side * reach * 0.5f;
            float angle = (door.spanX() ? 0f : 90f) + (side < 0 ? 0f : 180f);
            out.addImageRegion(RenderLayer.DOODADS, leaf.sprite, leaf.pxW, leaf.pxH,
                    leaf.pxW - srcW, 0, srcW, leaf.pxH, screenX(cam, door, along), screenY(cam, door, along),
                    reach * px, LEAF_DEPTH * px, angle, 1f, 1f, 1f, alpha);
        }
        if (door.locked()) {
            // The seal clamp across the seam, through both leaves.
            float seam = (door.spanMin() + door.spanMax()) * 0.5f;
            float length = Math.min(CLAMP_CELLS, half * 2f);
            out.addSprite(RenderLayer.DOODADS, clamp.sprite, screenX(cam, door, seam), screenY(cam, door, seam),
                    length * px, LEAF_DEPTH * px, door.spanX() ? 0f : 90f, 1f, 1f, 1f, alpha);
        }
    }

    private static float screenX(BattleCamera cam, RoomDoor door, float along) {
        return cam.cellToScreenX(door.spanX() ? along : door.across());
    }

    private static float screenY(BattleCamera cam, RoomDoor door, float along) {
        return cam.cellToScreenY(door.spanX() ? door.across() : along);
    }
}
