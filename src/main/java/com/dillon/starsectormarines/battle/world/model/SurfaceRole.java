package com.dillon.starsectormarines.battle.world.model;

/**
 * A surface the renderer draws that is <em>not</em> a
 * {@link CellTopology.GroundKind} — the roles carried by a
 * {@link CellTopology.Tag} or by building state, which overlay whatever ground
 * a cell already has.
 *
 * <p>The two vocabularies are orthogonal on purpose and always have been: a
 * cell has exactly one ground kind and may additionally be a wall, a doorway,
 * or roofed. Folding a wall into {@code GroundKind} would claim a cell is
 * either a wall or a floor, which is not how {@link CellTopology} models it.
 *
 * <p>What this enum adds is that the orthogonal half is now <em>dispatchable</em>.
 * Ground kinds have resolved through {@code GenMappingRegistry.groundBlockId}
 * for some time, so re-pointing {@code GRASS} at different art is data; walls,
 * doorways and roofs resolved through ids compiled into the render systems, so
 * the same edit was a code change. A second authored wall could be exported
 * perfectly and never drawn, because nothing could ask for it by any name but
 * the one in the source. These names are what a mapping's {@code surfaceRender}
 * section keys on, so the orthogonal surfaces re-point the same way the ground
 * does.
 *
 * @see com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry#surfaceBlockId(SurfaceRole)
 */
public enum SurfaceRole {

    /**
     * The structural wall autotile, drawn on {@link CellTopology.Tag#WALL}
     * cells from a {@code wall-3x3} block. Its block's fill colour paints the
     * fully-enclosed cell that the layout deliberately leaves without art.
     */
    WALL("urban.wall"),

    /**
     * The open-doorway decal laid over the floor of a doorway cell. A single
     * cell rather than an autotile — a doorway is one gap in one wall, and it
     * is drawn after the ground so the floor shows through the opening.
     */
    DOOR_OPEN("urban.door-open"),

    /**
     * The intact building roof, drawn over an interior the player cannot see
     * into and faded out as the building opens up. It reads as a paved surface
     * from above, so it has always been pointed at a floor block; that it is a
     * floor block is a choice about how a roof looks, which is exactly the kind
     * of choice that belongs in the mapping rather than in the renderer.
     */
    ROOF("floors.brick");

    private final String shippedBlockId;

    SurfaceRole(String shippedBlockId) {
        this.shippedBlockId = shippedBlockId;
    }

    /**
     * The block id this mod's own art ships for the role, used when no mapping
     * is installed — a preview scene, or a test that stands up a topology
     * without the catalog. It is a default, not a second authority: a mapping
     * that names a different block wins, which is the whole point of asking for
     * a surface by role rather than by id.
     */
    public String shippedBlockId() {
        return shippedBlockId;
    }

    /**
     * The role named by {@code key}, or {@code null} when the key is not one —
     * for a mapping section whose key space is {@code GroundKind} names
     * <em>and</em> these, and which must tell the two apart without throwing.
     */
    public static SurfaceRole fromKeyOrNull(String key) {
        for (SurfaceRole role : values()) {
            if (role.name().equals(key)) return role;
        }
        return null;
    }
}
