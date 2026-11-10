package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.door.LockedDoor;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every locked door ({@code garrisonables.md}, Locked doors and hardened walls), drawn over the wall it is shut as so
 * it reads as a door that will not open rather than more wall.
 *
 * <p>Shut, the span carries a door sprite of its length (art under {@code graphics/doors/}, from
 * {@code art-source/locked-doors}): a forcible door's two hazard-banded leaves with a red lamp on the seam, a hardened
 * door's ribbed armoured slab with a red lock bar; a hardened door whose lock is ready but still waits (powered, keys
 * not yet turned) draws its amber variant. A span longer than the longest sprite stretches it. The door is a leaf
 * door ({@link LeafDoorArt}, as a ship's compartment door): when it opens its two leaves slide apart from the seam
 * into the walls either side, where a jamb with a green lamp stands at each end, and they slide shut again when it
 * locks. The passage itself opens and shuts with the lock; the leaves only show it. A door a breach forced open has
 * lost a leaf: it lies on the floor beside the way, the other jammed part-open in its track. The sprites are authored
 * along x; a door across y is turned a quarter.
 */
final class LockedDoorRenderSystem implements RenderSystem {

    private static final String DIR = "graphics/doors/";
    /** The longest shut sprite of each kind, in cells. */
    private static final int FORCIBLE_LONGEST = 4, HARDENED_LONGEST = 2;
    /** An open jamb's width along the span, in cells. */
    private static final float JAMB = 0.4f;
    /** Seconds for a forcible door's leaves to slide fully open or shut; a hardened (blast) door's are heavier. */
    static final float FORCIBLE_TRAVEL_SECONDS = .6f, HARDENED_TRAVEL_SECONDS = 1.4f;
    /** How far a forced door's surviving leaf stands open in its track. */
    static final float FORCED_TRAVEL = .3f;

    private final BattleSprites sprites;
    /** Each door's leaves, 0 shut to 1 withdrawn, moving toward its lock's state. */
    private final Map<LockedDoor, float[]> travel = new IdentityHashMap<>();

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
            draw(cam, out, door, step(door, ctx.realDt), ctx.alphaMult);
        }
    }

    /** Moves the door's leaves toward its lock's state, and returns where they stand. */
    private float step(LockedDoor door, float seconds) {
        float target = door.isOpen() ? 1f : 0f;
        float[] t = travel.computeIfAbsent(door, d -> new float[]{target});
        float rate = (seconds > 0f ? seconds : 0f) / (door.hardened() ? HARDENED_TRAVEL_SECONDS : FORCIBLE_TRAVEL_SECONDS);
        t[0] = target > t[0] ? Math.min(target, t[0] + rate) : Math.max(target, t[0] - rate);
        return t[0];
    }

    private void draw(BattleCamera cam, DrawList out, LockedDoor door, float open, float alpha) {
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
        if (open > 0f) {
            // The leaves withdraw into the walls either side, where the jambs stand.
            piece(cam, out, kind + "-jamb-low", alongX, min - JAMB * 0.5f, across, JAMB, alpha);
            piece(cam, out, kind + "-jamb-high", alongX, max + JAMB * 0.5f, across, JAMB, alpha);
        }
        int length = Math.round(max - min);
        int sprite = Math.max(1, Math.min(length, door.hardened() ? HARDENED_LONGEST : FORCIBLE_LONGEST));
        // Powered but still locked: the hardened door's lock bar and lamp burn amber.
        String ready = door.ready() && door.hardened() ? "-ready" : "";
        ShuttleSpriteCache art = sprites.spriteByPath(DIR + kind + "-" + sprite + ready + ".png");
        if (art == null) return;
        LeafDoorArt.Span span = new LeafDoorArt.Span(alongX, min, max, across);
        float leafCells = sprite * 0.5f;
        if (door.isForced()) {
            int floor = floorSide(door, alongX);
            LeafDoorArt.torn(cam, out, art, true, span, 1f, -1, floor, alpha);
            LeafDoorArt.leaves(cam, out, art, true, leafCells, span, 1f, FORCED_TRAVEL, 1, -1, .85f, alpha);
            return;
        }
        float eased = open * open * (3f - 2f * open);
        LeafDoorArt.leaves(cam, out, art, true, leafCells, span, 1f, eased, 1, 0, 1f, alpha);
    }

    /** The side of the door's line its keep lies on, where a torn leaf falls: 1 above or right, -1 below or left. */
    private static int floorSide(LockedDoor door, boolean alongX) {
        int[] c = door.cells()[door.cells().length / 2];
        return alongX ? (c[1] + 0.5f < door.keepCentreY() ? 1 : -1) : (c[0] + 0.5f < door.keepCentreX() ? 1 : -1);
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
