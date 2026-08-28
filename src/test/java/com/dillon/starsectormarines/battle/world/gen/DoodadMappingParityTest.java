package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef.WallSide;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Frozen-golden regression guard for the data-driven doodad system
 * (moddable-tilesets Phase 2). The migration parity (def cover ==
 * {@code Doodad.defaultCoverFor}; pools == the former {@code TileManifest}
 * arrays) was proven at flip time; those sources are now deleted, so this pins
 * the <em>shipped</em> values directly:
 * <ul>
 *   <li>each pool resolves (via {@link GenMappingRegistry}) to its frozen ordered
 *       id sequence — order matters, scatter indexes the pool by
 *       {@code rng.nextInt(len)}, so a re-order shifts every seeded map;</li>
 *   <li>a representative cover golden — one prop per cover bucket — guards the
 *       {@code "cover"} parse + the authored values.</li>
 * </ul>
 * A bad doodad id in any pool fails loud at resolve time (and at the test
 * bootstrap, which loads every sheet).
 *
 * <p>Frozen by <b>id</b> rather than by {@code (col,row)}, which is what this
 * pinned when a doodad still was a source cell. A sheet built by the tileset
 * exporter is packed afresh on every export, so a coordinate golden fails for
 * the one reason nobody needs to hear about — the packer moved something — and
 * says nothing about the pool it is guarding.
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
        assertPool(mapping, "MIXED",
                "doodad.box", "doodad.crate", "doodad.chest-1", "doodad.chest-2",
                "doodad.desk-dam", "doodad.box-dam", "doodad.chair-s-yellow-dam",
                "doodad.chair-s-green-dam", "doodad.door-closed");
        assertPool(mapping, "RESIDENTIAL",
                "doodad.chair-south-yellow", "doodad.chair-south-green",
                "doodad.chest-1", "doodad.chest-2");
        assertPool(mapping, "WAREHOUSE",
                "doodad.box", "doodad.crate", "doodad.chest-1", "doodad.chest-2");
        assertPool(mapping, "SKY_PORT",
                "doodad.box", "doodad.crate", "doodad.box-dam", "doodad.door-closed");
        assertPool(mapping, "COMMERCIAL",
                "doodad.shelf-empty", "doodad.shelf-1", "doodad.shelf-2", "doodad.shelf-3",
                "doodad.desk-1", "doodad.desk-2", "doodad.chest-1", "doodad.chest-2",
                "doodad.box", "doodad.crate");
    }

    @Test
    void coverGoldenPerBucket() throws Exception {
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

        // Invariant after the gap fix: every prop a pool can scatter carries real
        // cover. Asked of the pools rather than of the whole registry, because a
        // sheet also carries deck markings — bay paving, laid by id and walked
        // straight over. Those used to be literal (col,row) frames and so could
        // not reach a registry at all; they have ids now because a coordinate
        // into a re-packed atlas is a silent lie, and having ids is not the same
        // as being something to hide behind.
        GenMappingRegistry mapping = loadMapping();
        for (String poolId : mapping.doodadPoolNames()) {
            for (DoodadDef d : mapping.doodadPool(poolId)) {
                assertNotEquals(DoodadCover.NONE, d.cover,
                        "scattered prop must carry cover: " + d.id + " (pool " + poolId + ")");
            }
        }
    }

    @Test
    void ballisticHeightGoldenSpansLowAndTallSilhouettes() {
        TileRegistry reg = TileRegistry.installed();
        assertEquals(0.16f, reg.doodad("doodad.decal-rubble-1").ballisticHalfHeight, 1e-6f);
        assertEquals(0.28f, reg.doodad("doodad.box").ballisticHalfHeight, 1e-6f);
        assertEquals(0.38f, reg.doodad("doodad.crate").ballisticHalfHeight, 1e-6f);
        assertEquals(0.75f, reg.doodad("doodad.shelf-1").ballisticHalfHeight, 1e-6f);
        assertEquals(0.30f, reg.doodad("doodad.sandbag-straight-n").ballisticHalfHeight, 1e-6f);
        assertEquals(0.60f, reg.doodad("doodad.industrial-crate-stack").ballisticHalfHeight, 1e-6f);
        assertEquals(0.68f, reg.doodad("doodad.industrial-machine-tool").ballisticHalfHeight, 1e-6f);
        assertEquals(0.82f, reg.doodad("doodad.industrial-fluid-tank").ballisticHalfHeight, 1e-6f);
        assertEquals(0.45f, reg.doodad("doodad.industrial-control-console").ballisticHalfHeight, 1e-6f);
        assertEquals(0.72f, reg.doodad("doodad.industrial-fence-straight-h").ballisticHalfHeight, 1e-6f);
        assertEquals(0.25f, reg.doodad("doodad.military-bunk").ballisticHalfHeight, 1e-6f);
        assertEquals(0.75f, reg.doodad("doodad.military-radar-dish").ballisticHalfHeight, 1e-6f);
        assertEquals(0.45f, reg.doodad("doodad.office-workstation-bank").ballisticHalfHeight, 1e-6f);
        assertEquals(0.75f, reg.doodad("doodad.office-server-rack").ballisticHalfHeight, 1e-6f);
        assertEquals(0.38f, reg.doodad("doodad.office-conference-table").ballisticHalfHeight, 1e-6f);
        assertFootprint(reg, "doodad.office-workstation-bank", 1, 1);
        assertFootprint(reg, "doodad.office-server-rack", 1, 1);
        assertFootprint(reg, "doodad.office-conference-table", 1, 1);
        assertFootprint(reg, "doodad.civic-workstation-bank", 2, 2);
        assertFootprint(reg, "doodad.civic-server-rack", 1, 2);
        assertFootprint(reg, "doodad.civic-conference-table", 3, 2);
        assertFootprint(reg, "doodad.office-reception-counter", 3, 2);
        assertFootprint(reg, "doodad.office-records-bank", 2, 1);
        assertEquals(0.28f, reg.doodad("doodad.residential-bed-h").ballisticHalfHeight, 1e-6f);
        assertEquals(0.42f, reg.doodad("doodad.residential-sofa-v").ballisticHalfHeight, 1e-6f);
        assertEquals(0.55f, reg.doodad("doodad.residential-planter-h").ballisticHalfHeight, 1e-6f);
    }

    @Test
    void residentialFurniturePublishesOrientedTwoCellFootprints() {
        TileRegistry reg = TileRegistry.installed();
        assertOrientedFootprint(reg, "doodad.residential-bed-h", 2, 1, WallSide.W);
        assertOrientedFootprint(reg, "doodad.residential-bed-head-e", 2, 1, WallSide.E);
        assertOrientedFootprint(reg, "doodad.residential-bed-v", 1, 2, WallSide.S);
        assertOrientedFootprint(reg, "doodad.residential-bed-head-n", 1, 2, WallSide.N);
        assertOrientedFootprint(reg, "doodad.residential-sofa-h", 2, 1, WallSide.N);
        assertOrientedFootprint(reg, "doodad.residential-sofa-back-s", 2, 1, WallSide.S);
        assertOrientedFootprint(reg, "doodad.residential-sofa-v", 1, 2, WallSide.W);
        assertOrientedFootprint(reg, "doodad.residential-sofa-back-e", 1, 2, WallSide.E);
        assertFootprint(reg, "doodad.residential-planter-h", 1, 1);
        assertNull(reg.doodad("doodad.residential-planter-h").preferredWallSide);
    }

    private static void assertOrientedFootprint(TileRegistry registry, String id,
                                                int width, int height, WallSide side) {
        assertFootprint(registry, id, width, height);
        assertEquals(side, registry.doodad(id).preferredWallSide, "wall side for " + id);
    }

    private static void assertPool(GenMappingRegistry mapping, String poolId, String... expected) {
        List<DoodadDef> got = mapping.doodadPool(poolId);
        List<String> ids = new ArrayList<>();
        for (DoodadDef def : got) ids.add(def.id);
        assertEquals(List.of(expected), ids, "pool " + poolId);
    }

    private static void assertCover(TileRegistry reg, String id, DoodadCover want) {
        DoodadDef def = reg.doodad(id);
        assertNotNull(def, "missing doodad " + id);
        assertEquals(want, def.cover, "cover for " + id);
    }

    private static void assertFootprint(TileRegistry reg, String id, int width, int height) {
        DoodadDef def = reg.doodad(id);
        assertNotNull(def, "missing doodad " + id);
        assertEquals(width, def.footprintCellsX, "footprint width for " + id);
        assertEquals(height, def.footprintCellsY, "footprint height for " + id);
    }
}
