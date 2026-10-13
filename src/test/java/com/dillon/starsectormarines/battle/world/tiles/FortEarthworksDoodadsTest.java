package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** Prepared ground is declared in the data: the trench and every sandbag piece say so, wire and the destroyed stubs do not. */
    @Test
    void preparedGroundDeclaresItsFightingPositionRole() throws Exception {
        TileRegistryTestInstaller.install(Path.of("").toAbsolutePath());
        TileRegistry registry = TileRegistry.installed();
        for (int mask = 0; mask < 16; mask++) {
            assertEquals(FightingPositionRole.TRENCH, registry.doodad("doodad.fort-trench-" + mask).fightingPosition);
        }
        for (String name : new String[]{"n", "e", "s", "w", "corner-ne", "corner-se", "corner-sw", "corner-nw"}) {
            assertEquals(FightingPositionRole.PARAPET, registry.doodad("doodad.fort-sandbag-" + name).fightingPosition, name);
        }
        for (String shape : new String[]{"straight-n", "straight-e", "straight-s", "straight-w",
                "corner-nw", "corner-ne", "corner-se", "corner-sw"}) {
            assertEquals(FightingPositionRole.PARAPET, registry.doodad("doodad.sandbag-" + shape).fightingPosition, shape);
            assertEquals(FightingPositionRole.PARAPET, registry.doodad("doodad.sandbag-damaged-" + shape).fightingPosition, shape);
            assertEquals(FightingPositionRole.NONE, registry.doodad("doodad.sandbag-destroyed-" + shape).fightingPosition, shape);
        }
        assertEquals(FightingPositionRole.NONE, registry.doodad("doodad.fort-wire-h").fightingPosition);
        assertEquals(FightingPositionRole.NONE, registry.doodad("doodad.fort-hedgehog").fightingPosition);
    }
}
