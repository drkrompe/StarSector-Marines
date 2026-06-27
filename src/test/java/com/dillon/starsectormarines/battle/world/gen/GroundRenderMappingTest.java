package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Frozen-golden guard for the moddable-tilesets Phase 2 GroundKind render
 * dispatch. Since the renderer has no pixel-parity oracle, this pins the two
 * things the generic {@code GroundRenderSystem.drawGroundBlock} path depends on
 * for behavior preservation:
 * <ol>
 *   <li>{@link GenMappingRegistry#groundBlockId} returns the exact id each kind
 *       rendered as before the data extraction (and {@code null} for the
 *       special-cased SIDEWALK / dead SNOW);</li>
 *   <li>each mapped block lives on the sheet + {@code cellPx} the renderer's
 *       {@code sheetFor}/inset selection assumes — so a 16px Floors block can't
 *       silently start drawing at a 32px source rect.</li>
 * </ol>
 */
public class GroundRenderMappingTest {

    private static GenMappingRegistry loadMapping() throws Exception {
        GenMappingRegistry reg = new GenMappingRegistry();
        for (String p : GenMappingRegistry.BUILTIN_MAPPINGS) {
            reg.ingest(new JSONObject(Files.readString(Paths.get("mod", p.split("/")))));
        }
        return reg;
    }

    @Test
    void groundRenderIdsAreFrozen() throws Exception {
        GenMappingRegistry m = loadMapping();
        assertEquals("urban.floor",          m.groundBlockId(GroundKind.INDOOR));
        assertEquals("urban.rubble",         m.groundBlockId(GroundKind.RUBBLE));
        assertEquals("urban3.street-square",  m.groundBlockId(GroundKind.STREET));
        assertEquals("road.courtyard",       m.groundBlockId(GroundKind.COURTYARD));
        assertEquals("road.tile",            m.groundBlockId(GroundKind.TILE));
        assertEquals("road.striped",         m.groundBlockId(GroundKind.STRIPED));
        assertEquals("road.lz-marker",       m.groundBlockId(GroundKind.LZ_MARKER));
        assertEquals("floors.grass",         m.groundBlockId(GroundKind.GRASS));
        assertEquals("floors.dirt",          m.groundBlockId(GroundKind.DIRT));
        assertEquals("floors.stone",         m.groundBlockId(GroundKind.STONE));
        assertEquals("floors.sand",          m.groundBlockId(GroundKind.SAND));
        assertEquals("floors.brick",         m.groundBlockId(GroundKind.BRICK));
        assertEquals("water.water",          m.groundBlockId(GroundKind.WATER));
        // Special-cased / dead — intentionally unmapped (renderer handles them in code).
        assertNull(m.groundBlockId(GroundKind.SIDEWALK));
        assertNull(m.groundBlockId(GroundKind.SNOW));
    }

    @Test
    void mappedBlocksLiveOnExpectedSheetAndCellPx() throws Exception {
        GenMappingRegistry m = loadMapping();
        TileRegistry reg = TileRegistry.installed();
        assertNotNull(reg, "TileRegistry not installed");

        // STREET maps to a sliced urban3 tile (not a grid block) — verify it
        // resolves as a tile and is NOT a block (so the renderer special-cases it).
        assertNotNull(reg.tile("urban3.street-square"), "STREET tile missing");
        assertNull(reg.block("urban3.street-square"), "STREET must be a sliced tile, not a block");

        // Every other mapped kind is a grid block — pin its sheet + cellPx.
        assertBlock(reg, m, GroundKind.INDOOR,    TileManifest.SHEET,        32);
        assertBlock(reg, m, GroundKind.RUBBLE,    TileManifest.SHEET,        32);
        assertBlock(reg, m, GroundKind.COURTYARD, TileManifest.ROAD_SHEET,   32);
        assertBlock(reg, m, GroundKind.TILE,      TileManifest.ROAD_SHEET,   32);
        assertBlock(reg, m, GroundKind.STRIPED,   TileManifest.ROAD_SHEET,   32);
        assertBlock(reg, m, GroundKind.LZ_MARKER, TileManifest.ROAD_SHEET,   32);
        assertBlock(reg, m, GroundKind.GRASS,     TileManifest.FLOORS_SHEET, 16);
        assertBlock(reg, m, GroundKind.DIRT,      TileManifest.FLOORS_SHEET, 16);
        assertBlock(reg, m, GroundKind.STONE,     TileManifest.FLOORS_SHEET, 16);
        assertBlock(reg, m, GroundKind.SAND,      TileManifest.FLOORS_SHEET, 16);
        assertBlock(reg, m, GroundKind.BRICK,     TileManifest.FLOORS_SHEET, 16);
        assertBlock(reg, m, GroundKind.WATER,     TileManifest.WATER_SHEET,  16);
    }

    private static void assertBlock(TileRegistry reg, GenMappingRegistry m, GroundKind kind,
                                    String expectSheet, int expectCellPx) {
        GridBlockDef b = reg.block(m.groundBlockId(kind));
        assertNotNull(b, "no block for " + kind);
        assertEquals(expectSheet, b.sheetPath, kind + " sheet");
        assertEquals(expectCellPx, b.cellPx, kind + " cellPx");
    }
}
