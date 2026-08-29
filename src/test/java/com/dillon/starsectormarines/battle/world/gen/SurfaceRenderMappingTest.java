package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.SurfaceRole;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard for the orthogonal half of render dispatch — the surfaces a cell
 * carries <em>in addition to</em> its {@code GroundKind}.
 *
 * <p>Ground kinds have resolved through the mapping for a while; walls,
 * doorways and roofs did not. Their ids were compiled into the render systems,
 * which meant a second wall could be authored, slotted and exported perfectly
 * and still never be drawn, because nothing could ask for it by any name but
 * {@code "urban.wall"}. Nothing failed when that was true: every id resolved,
 * every cell was opaque, and the new wall was simply absent.
 *
 * <p>So the load-bearing case here is not that the shipped mapping holds the
 * right ids — it is {@link #theWallDrawnIsTheWallItWasHanded()}, which hands
 * the picker two different blocks and demands two different frames. That is the
 * one a re-hardcoded id cannot pass.
 */
public class SurfaceRenderMappingTest {

    private static GenMappingRegistry loadMapping() throws Exception {
        GenMappingRegistry reg = new GenMappingRegistry();
        for (String p : GenMappingRegistry.BUILTIN_MAPPINGS) {
            reg.ingest(new JSONObject(Files.readString(Paths.get("mod", p.split("/")))));
        }
        return reg;
    }

    private static GridBlockDef wallBlockAt(String id, int col, int row) {
        return new GridBlockDef(id, TileManifest.SHEET, 32, col, row, GridLayout.WALL_3X3, null);
    }

    /**
     * The picker resolves against the block it is given, so pointing the role
     * at different art changes what is drawn. Two blocks at different origins,
     * one mask, two frames.
     */
    @Test
    void theWallDrawnIsTheWallItWasHanded() {
        int northFace = CellTopology.WALL_DIR_N;

        TileManifest.TileFrame shipped = WallMasks.pickTileFromMask(
                northFace, wallBlockAt("urban.wall", 3, 0));
        TileManifest.TileFrame other = WallMasks.pickTileFromMask(
                northFace, wallBlockAt("submod.wall", 6, 3));

        assertNotNull(shipped);
        assertNotNull(other);
        assertEquals(4, shipped.col, "north face sits one column into the block");
        assertEquals(0, shipped.row);
        assertEquals(7, other.col, "the second wall's frame must come from its own origin");
        assertEquals(3, other.row);
    }

    /**
     * The enclosed cell stays the layout's null case whichever wall is mapped —
     * that is the block's fill colour talking, not a missing frame.
     */
    @Test
    void anEnclosedCellIsStillTheFillCase() {
        int enclosed = CellTopology.WALL_DIR_N | CellTopology.WALL_DIR_S
                | CellTopology.WALL_DIR_E | CellTopology.WALL_DIR_W;
        assertNull(WallMasks.pickTileFromMask(enclosed, wallBlockAt("submod.wall", 6, 3)));
    }

    /** With no block resolved at all, the picker still draws this mod's own wall. */
    @Test
    void anAbsentBlockFallsBackRatherThanVanishing() {
        assertNotNull(WallMasks.pickTileFromMask(CellTopology.WALL_DIR_N, null),
                "a wall with no block must still pick a frame");
    }

    /** Every orthogonal surface the render systems draw is named by the shipped mapping. */
    @Test
    void theShippedMappingNamesEveryRole() throws Exception {
        GenMappingRegistry m = loadMapping();
        for (SurfaceRole role : SurfaceRole.values()) {
            assertNotNull(m.surfaceBlockId(role),
                    role + " is drawn by a render system but no longer mapped");
        }
        assertEquals("urban.wall", m.surfaceBlockId(SurfaceRole.WALL));
        assertEquals("urban.door-open", m.surfaceBlockId(SurfaceRole.DOOR_OPEN));
        assertEquals("floors.brick", m.surfaceBlockId(SurfaceRole.ROOF));
    }

    /**
     * A role the mapping omits keeps drawing this mod's art rather than
     * disappearing, so an incomplete third-party mapping degrades quietly.
     */
    @Test
    void anUnmappedRoleFallsBackToTheShippedId() throws Exception {
        GenMappingRegistry empty = new GenMappingRegistry();
        empty.ingest(new JSONObject("{}"));
        assertNull(empty.surfaceBlockId(SurfaceRole.WALL));

        GenMappingRegistry previous = GenMappingRegistry.installed();
        try {
            GenMappingRegistry.install(empty);
            assertEquals("urban.wall",
                    GenMappingRegistry.installedSurfaceBlockId(SurfaceRole.WALL));
        } finally {
            GenMappingRegistry.install(previous);
        }
    }

    /** A surface pointed at art that does not exist fails at load, not at the first wall drawn. */
    @Test
    void aSurfacePointedAtNothingIsRefusedLoudly() throws Exception {
        TileRegistry tiles = TileRegistry.installed();
        assertNotNull(tiles, "TileRegistry not installed");

        GenMappingRegistry m = new GenMappingRegistry();
        m.ingest(new JSONObject("{\"surfaceRender\": {\"WALL\": \"submod.no-such-wall\"}}"));

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, m::validateReferences);
        assertTrue(refused.getMessage().contains("submod.no-such-wall"),
                "the message should name the id that failed: " + refused.getMessage());
    }

    /** An unknown role name is a typo in a mapping, and typos do not load silently. */
    @Test
    void anUnknownRoleIsRefused() {
        GenMappingRegistry m = new GenMappingRegistry();
        assertThrows(IllegalArgumentException.class, () ->
                m.ingest(new JSONObject("{\"surfaceRender\": {\"CEILING\": \"urban.wall\"}}")));
    }
}
