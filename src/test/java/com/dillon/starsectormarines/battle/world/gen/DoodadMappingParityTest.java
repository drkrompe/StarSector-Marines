package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.DistrictTheme;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Parity oracle for moddable-tilesets Phase 2 sub-slice 1 (doodad pools as data).
 * Pins the data-driven {@link DoodadDef}s + {@link GenMappingRegistry} pools to
 * the hardcoded {@code TileManifest} pools + {@code Doodad.defaultCoverFor} they
 * replace, so the consumer flip in sub-slice 2 is provably behavior-preserving:
 * <ul>
 *   <li>every doodad def's {@link DoodadDef#cover} equals the cover the
 *       {@code (col,row)} table would have derived;</li>
 *   <li>each theme pool resolves to the same ordered {@code (col,row)} frames as
 *       its {@code TileManifest} array (order matters — scatter indexes the pool
 *       by {@code rng.nextInt(len)}, so a re-order would shift every seeded map).</li>
 * </ul>
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
    void doodadDefCoversMatchDefaultCoverFor() {
        // TileRegistry (with the folded-in doodads) is installed by the global
        // TileRegistryTestInstaller before any test runs.
        TileRegistry reg = TileRegistry.installed();
        assertNotNull(reg, "TileRegistry not installed");
        assertNotNull(reg.doodad("doodad.crate"), "doodads not ingested");
        for (DoodadDef d : reg.doodads()) {
            int want = Doodad.defaultCoverFor(new TileManifest.TileFrame(d.col, d.row));
            assertEquals(want, d.cover.level(),
                    "cover drift for " + d.id + " @(" + d.col + "," + d.row + ")");
        }
    }

    @Test
    void doodadPoolsMatchTileManifestPools() throws Exception {
        GenMappingRegistry mapping = loadMapping();
        assertPoolMatches(mapping, DistrictTheme.MIXED,       TileManifest.DOODAD_POOL);
        assertPoolMatches(mapping, DistrictTheme.RESIDENTIAL, TileManifest.RESIDENTIAL_DOODADS);
        assertPoolMatches(mapping, DistrictTheme.WAREHOUSE,   TileManifest.WAREHOUSE_DOODADS);
        assertPoolMatches(mapping, DistrictTheme.SKY_PORT,    TileManifest.SKYPORT_DOODADS);
    }

    private static void assertPoolMatches(GenMappingRegistry mapping, DistrictTheme theme,
                                          TileManifest.TileFrame[] expected) {
        List<DoodadDef> got = mapping.doodadPool(theme);
        assertEquals(expected.length, got.size(), "pool size for " + theme);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].col, got.get(i).col, "pool " + theme + " entry " + i + " col");
            assertEquals(expected[i].row, got.get(i).row, "pool " + theme + " entry " + i + " row");
        }
    }
}
