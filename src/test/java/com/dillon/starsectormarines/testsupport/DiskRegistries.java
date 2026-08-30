package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The catalogs the game loads at application start, loaded from the checked-in
 * mod folder instead.
 *
 * <p>Anything that generates or draws a map outside the game needs these, and
 * <b>the failure when they are missing is not an error</b> — {@code installed()}
 * returns null and callers take their no-catalog path, so a room comes out
 * unfurnished, a preview comes out unpainted, and nothing anywhere says why.
 * Every caller that learned this the hard way grew a private copy of the same
 * loader; this is that loader, once.
 *
 * <p>Idempotent, because several of those callers can run in one process and the
 * second must not throw away the first's work.
 */
public final class DiskRegistries {

    private DiskRegistries() {}

    /**
     * Install the tile catalog and the generation mapping, if nothing has.
     *
     * <p>These two travel together. The tiles are what a fixture resolves
     * through, and the mapping is what decides which block a surface draws as —
     * so a caller with tiles and no mapping renders the right rooms in the wrong
     * materials, which looks like an art bug and is not one.
     *
     * @param projectRoot the repository root; the mod folder is found beneath it
     */
    public static void install(Path projectRoot) throws Exception {
        Path mod = projectRoot.resolve("mod");
        if (TileRegistry.installed() == null) {
            TileRegistry tiles = new TileRegistry();
            for (String path : TileRegistry.BUILTIN_TILESETS) {
                tiles.ingestSheet(new JSONObject(Files.readString(mod.resolve(path))));
            }
            tiles.validateReferences();
            TileRegistry.install(tiles);
        }
        if (GenMappingRegistry.installed() == null) {
            GenMappingRegistry mapping = new GenMappingRegistry();
            for (String path : GenMappingRegistry.BUILTIN_MAPPINGS) {
                mapping.ingest(new JSONObject(Files.readString(mod.resolve(path))));
            }
            mapping.validateReferences();
            GenMappingRegistry.install(mapping);
        }
    }

    /** The same, for a caller that is already running from the repository root. */
    public static void install() throws Exception {
        install(Paths.get(""));
    }
}
