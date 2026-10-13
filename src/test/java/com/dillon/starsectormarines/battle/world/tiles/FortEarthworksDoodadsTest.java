package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The fort earthworks sheet is registered: the stage draws trench by mask and sandbag by facing, so every id must exist. */
class FortEarthworksDoodadsTest {

    @Test
    void everyPieceTheFortStageDrawsIsRegistered() throws Exception {
        TileRegistryTestInstaller.install(Path.of("").toAbsolutePath());
        TileRegistry registry = TileRegistry.installed();
        for (int mask = 0; mask < 16; mask++) assertNotNull(registry.doodad("doodad.fort-trench-" + mask), "trench " + mask);
        for (String name : new String[]{"n", "e", "s", "w", "corner-ne", "corner-se", "corner-sw", "corner-nw"}) {
            assertNotNull(registry.doodad("doodad.fort-sandbag-" + name), name);
        }
        for (String id : new String[]{"doodad.fort-wire-h", "doodad.fort-wire-v", "doodad.fort-hedgehog"}) {
            assertNotNull(registry.doodad(id), id);
        }
    }
}
