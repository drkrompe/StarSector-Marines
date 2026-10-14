package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CoverPropDamage;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fort belt sandbags walk intact, damaged, destroyed like the plain family and release their fighting-position cells. */
class FortSandbagDamageTest {
    private static final String PREFIX = "doodad.fort-sandbag-";
    private static final String[] SHAPES = {"n", "e", "s", "w", "corner-ne", "corner-se", "corner-sw", "corner-nw"};

    private TileRegistry previous;
    private TileRegistry registry;

    @BeforeEach void install() throws Exception {
        previous = TileRegistry.installed();
        registry = new TileRegistry();
        for (String sheet : new String[]{"fort-earthworks", "fort-sandbag-damage"})
            registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/" + sheet + ".tileset.json"))));
        TileRegistry.install(registry);
    }

    @AfterEach void restore() { TileRegistry.install(previous); }

    @Test void everyFortPieceHasADamagedAndDestroyedStateWithTheRightCoverAndRole() {
        for (String shape : SHAPES) {
            DoodadDef intact = registry.doodad(PREFIX + shape);
            DoodadDef damaged = registry.doodad(PREFIX + "damaged-" + shape);
            DoodadDef destroyed = registry.doodad(PREFIX + "destroyed-" + shape);
            assertNotNull(damaged, shape);
            assertNotNull(destroyed, shape);
            assertEquals(intact.cover, damaged.cover, shape);
            assertEquals(intact.ballisticHalfHeight, damaged.ballisticHalfHeight, shape);
            assertEquals(FightingPositionRole.PARAPET, damaged.fightingPosition, shape);
            assertEquals(0, destroyed.cover.level(), shape);
            assertEquals(FightingPositionRole.NONE, destroyed.fightingPosition, shape);
        }
    }

    @Test void destroyingFortSandbagsDropsTheirCellsFromTheRebuiltFightingPositions() {
        NavigationGrid grid = new NavigationGrid(20, 10);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        DoodadService service = new DoodadService(grid);
        for (int x = 5; x <= 10; x++) service.addDoodad(new Doodad(x, 4, registry.doodad(PREFIX + "n")));
        assertEquals(1, service.fightingPositions().runCount());
        assertTrue(service.fightingPositions().isFightingPositionCell(5, 4));

        assertEquals(CoverPropDamage.Result.DAMAGED, service.damageCoverProp(5, 4, 25));
        assertEquals(1, service.fightingPositions().runCount(), "a damaged parapet still stands");
        assertTrue(service.fightingPositions().isFightingPositionCell(5, 4));

        assertEquals(CoverPropDamage.Result.DESTROYED, service.damageCoverProp(5, 4, 100));
        assertEquals(17, service.fightingPositions().runSize(0), "five bags, their two flanks and the ends");
        assertFalse(service.fightingPositions().isFightingPositionCell(5, 3), "the lost bag's flank is gone");
        for (int x = 6; x <= 7; x++)
            assertEquals(CoverPropDamage.Result.DESTROYED, service.damageCoverProp(x, 4, 100));
        assertEquals(0, service.fightingPositions().runCount(), "three bags left is cover, not a position");
        assertFalse(service.fightingPositions().isFightingPositionCell(5, 4));
        assertEquals(0, service.getDoodadLevelOnCell(5, 4));
    }
}
