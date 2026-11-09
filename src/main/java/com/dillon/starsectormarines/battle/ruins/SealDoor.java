package com.dillon.starsectormarines.battle.ruins;

/**
 * A sealed door of the ruins' works ({@code ruins-expedition.md}, Works): a span the works keep as a
 * {@link com.dillon.starsectormarines.battle.door.LockedDoor} unlocked by its consumer's power. A breach through a
 * forcible one forces it open for good; a hardened one opens only by its power.
 *
 * @param id       stable id; also its consumer's id in the {@link PowerGraph}
 * @param name     what the map calls it
 * @param cells    the span's cells, {x, y} each
 * @param likeX    an open cell beside the span whose ground the opened span takes
 * @param keep     {minX, minY, maxX, maxY}: while anybody stands in it the door does not shut (the store room behind
 *                 a store's door, or the span itself)
 * @param store    whether it seals a store room
 * @param hardened whether it is shut as a wall nothing breaks, so its power is the only way through (the vault's)
 */
public record SealDoor(String id, String name, int[][] cells, int likeX, int likeY, int[] keep, boolean store,
                       boolean hardened) {
    /** A forcible seal door. */
    public SealDoor(String id, String name, int[][] cells, int likeX, int likeY, int[] keep, boolean store) {
        this(id, name, cells, likeX, likeY, keep, store, false);
    }

    public SealDoor {
        if (id == null || id.isBlank() || cells == null || cells.length == 0 || keep == null || keep.length != 4) {
            throw new IllegalArgumentException("a seal door needs an id, cells and a keep");
        }
    }

    /** The middle cell of the span, for its marker. */
    public int[] middle() { return cells[cells.length / 2]; }

    /** Whether (x, y) is in the door's keep. */
    public boolean keeps(float x, float y) {
        return x >= keep[0] && x < keep[2] + 1 && y >= keep[1] && y < keep[3] + 1;
    }
}
