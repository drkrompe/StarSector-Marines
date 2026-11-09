package com.dillon.starsectormarines.battle.ruins;

/**
 * A sealed door of the ruins' works ({@code ruins-works.md}, Seal doors): a span of open cells the works shut as wall
 * at setup, opened while its consumer is powered and shut again when it loses power. A breach through the shut wall
 * forces it open for good.
 *
 * @param id       stable id; also its consumer's id in the {@link PowerGraph}
 * @param name     what the map calls it
 * @param cells    the span's cells, {x, y} each
 * @param likeX    an open cell beside the span whose ground the opened span takes
 * @param keep     {minX, minY, maxX, maxY}: while anybody stands in it the door does not shut (the store room behind
 *                 a store's door, or the span itself)
 * @param store    whether it seals a store room
 */
public record SealDoor(String id, String name, int[][] cells, int likeX, int likeY, int[] keep, boolean store) {
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
