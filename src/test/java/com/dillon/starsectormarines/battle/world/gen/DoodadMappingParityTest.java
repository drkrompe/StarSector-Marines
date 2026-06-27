package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Frozen-golden regression guard for the data-driven doodad system
 * (moddable-tilesets Phase 2). The migration parity (def cover ==
 * {@code Doodad.defaultCoverFor}; pools == the former {@code TileManifest}
 * arrays) was proven at flip time; those sources are now deleted, so this pins
 * the <em>shipped</em> values directly:
 * <ul>
 *   <li>each pool resolves (via {@link GenMappingRegistry}) to its frozen ordered
 *       {@code (col,row)} sequence — order matters, scatter indexes the pool by
 *       {@code rng.nextInt(len)}, so a re-order shifts every seeded map;</li>
 *   <li>a representative cover golden — one prop per cover bucket — guards the
 *       {@code "cover"} parse + the authored values.</li>
 * </ul>
 * A bad doodad id in any pool fails loud at resolve time (and at the test
 * bootstrap, which loads every sheet).
 */
public class DoodadMappingParityTest {

    private static GenMappingRegistry loadMapping() throws Exception {
        GenMappingRegistry reg = new GenMappingRegistry();
        for (String p : GenMappingRegistry.BUILTIN_MAPPINGS) {
            reg.ingest(new JSONObject(Files.readString(Paths.get("mod", p.split("/")))));
        }
        return reg;
    }

    @Test
    void poolsResolveToFrozenFrames() throws Exception {
        GenMappingRegistry mapping = loadMapping();
        assertPool(mapping, "MIXED",       new int[][]{{8,1},{9,1},{3,3},{4,3},{6,7},{7,7},{8,7},{9,7},{6,2}});
        assertPool(mapping, "RESIDENTIAL", new int[][]{{6,1},{7,1},{3,3},{4,3}});
        assertPool(mapping, "WAREHOUSE",   new int[][]{{8,1},{9,1},{3,3},{4,3}});
        assertPool(mapping, "SKY_PORT",    new int[][]{{8,1},{9,1},{7,7},{6,2}});
        assertPool(mapping, "COMMERCIAL",  new int[][]{{5,3},{6,3},{7,3},{8,3},{9,2},{9,3},{3,3},{4,3},{8,1},{9,1}});
    }

    @Test
    void coverGoldenPerBucket() {
        TileRegistry reg = TileRegistry.installed();
        assertNotNull(reg, "TileRegistry not installed");
        assertCover(reg, "doodad.decal-rubble-1",      DoodadCover.LIGHT);
        assertCover(reg, "doodad.box",                 DoodadCover.MED);
        assertCover(reg, "doodad.door-closed",         DoodadCover.MED);
        assertCover(reg, "doodad.shelf-dam-1",         DoodadCover.HEAVY);
        // Cover-gap fix (formerly NONE): clean chairs/desks -> MED, shelves -> HEAVY,
        // matching their damaged row-7 counterparts.
        assertCover(reg, "doodad.chair-south-yellow",  DoodadCover.MED);
        assertCover(reg, "doodad.desk-1",              DoodadCover.MED);
        assertCover(reg, "doodad.shelf-empty",         DoodadCover.HEAVY);

        // Invariant after the gap fix: every registered prop carries real cover —
        // markers (LZ pads/arrows) are literal frames, not defs, so none reach here.
        for (DoodadDef d : reg.doodads()) {
            assertNotEquals(DoodadCover.NONE, d.cover, "prop doodad must carry cover: " + d.id);
        }
    }

    private static void assertPool(GenMappingRegistry mapping, String poolId, int[][] expected) {
        List<DoodadDef> got = mapping.doodadPool(poolId);
        assertEquals(expected.length, got.size(), "pool size for " + poolId);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][0], got.get(i).col, "pool " + poolId + " entry " + i + " col");
            assertEquals(expected[i][1], got.get(i).row, "pool " + poolId + " entry " + i + " row");
        }
    }

    private static void assertCover(TileRegistry reg, String id, DoodadCover want) {
        DoodadDef def = reg.doodad(id);
        assertNotNull(def, "missing doodad " + id);
        assertEquals(want, def.cover, "cover for " + id);
    }
}
