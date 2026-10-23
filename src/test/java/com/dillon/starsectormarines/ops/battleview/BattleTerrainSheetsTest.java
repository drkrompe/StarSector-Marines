package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules that pick a battle's terrain sheets, each on a two-cell map ({@code battle-textures.md}). */
class BattleTerrainSheetsTest {

    private static TileRegistry registry(String... tilesets) throws Exception {
        TileRegistry registry = new TileRegistry();
        for (String tileset : tilesets) {
            registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/" + tileset + ".tileset.json"))));
        }
        return registry;
    }

    @Test
    void aWallBringsTheSheetsItsFallAndItsOpeningsDraw() throws Exception {
        TileRegistry registry = registry("concrete-walls", "concrete-rubble", "concrete-openings");
        CellTopology topology = new CellTopology(2, 1);
        topology.setSurface(0, 0, topology.surfaceIndex("installation.dark-concrete-wall"));

        Set<String> sheets = BattleTerrainSheets.of(registry, null, topology, 2, 1, List.of(), false, false);

        assertTrue(sheets.contains("graphics/tilesets/concrete-walls.png"), sheets.toString());
        assertTrue(sheets.contains("graphics/tilesets/concrete-rubble.png"), "its rubble: " + sheets);
        assertTrue(sheets.contains("graphics/tilesets/concrete-openings.png"), "its window and door: " + sheets);
    }

    @Test
    void aPlacedPropBringsItsDamageStatesAndNothingElse() throws Exception {
        TileRegistry registry = registry("doodads", "sandbag-damage", "forest-canopies", "forest-blight");
        Doodad sandbag = new Doodad(0, 0, registry.doodad("doodad.sandbag-straight-n"));

        Set<String> sheets = BattleTerrainSheets.of(registry, null, new CellTopology(2, 1), 2, 1,
                List.of(sandbag), true, false);

        assertTrue(sheets.contains("graphics/doodads/sandbag-damage.png"), "its damaged and destroyed run: " + sheets);
        assertFalse(sheets.contains("graphics/doodads/forest-blight.png"), "no tree, no blight: " + sheets);
        assertFalse(sheets.contains("graphics/doodads/forest-canopies.png"), sheets.toString());
    }

    @Test
    void aTreeBringsItsBlightOnlyWhereCreepCanSpread() throws Exception {
        TileRegistry registry = registry("forest-canopies", "forest-blight");
        List<Doodad> oak = List.of(new Doodad(0, 0, registry.doodad("forest.oak")));

        Set<String> clean = BattleTerrainSheets.of(registry, null, new CellTopology(2, 1), 2, 1, oak, false, false);
        Set<String> creeping = BattleTerrainSheets.of(registry, null, new CellTopology(2, 1), 2, 1, oak, true, false);

        assertFalse(clean.contains("graphics/doodads/forest-blight.png"), clean.toString());
        assertTrue(creeping.contains("graphics/doodads/forest-blight.png"), creeping.toString());
    }
}
