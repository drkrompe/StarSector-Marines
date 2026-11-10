package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The pool-file writer: an entry reads back equal, and a splice touches nothing but the entry it replaces. */
class PoolEntryTextTest {

    private static final String FILE = """
            {
              "version": 2,
              "map": "x",
              "biomes": {
                "VERDANT": [ {"seed": 2, "note": "a \\"quoted\\" [note]"} ],
                "ARID": [ {"seed": 5, "story": true},
                          {"seed": 7, "patch": {"pin": [{"place": "base", "x": 1, "y": 2}]}} ]
              },
              "candidates": { "ARID": [ {"seed": 9} ] }
            }
            """;

    @Test
    void anEntryReadsBackEqual() throws Exception {
        MapPatch patch = new MapPatch(List.of(new MapPatch.Pin("redoubt-2", 40, 61)),
                List.of(new DamagePlan.Stroke(DamagePlan.Mode.BATTERED, 2.5, List.of(new DamagePlan.Point(3, 4), new DamagePlan.Point(5.25, 6)))),
                new MapPatch.Cell(10, 11), List.of(new MapPatch.Bore(1, 2, true)), List.of(new MapPatch.Cell(7, 8)),
                List.of(new Structure("airbase-1", StructureKind.AIRFIELD, 96, 8, 1, true)));
        StoryMapPool.Entry entry = new StoryMapPool.Entry(5, "the \"story\" map", patch, true);
        JSONObject json = new JSONObject(PoolEntryText.entry(entry));
        assertEquals(5, json.getLong("seed"));
        assertEquals(entry.note(), json.getString("note"));
        assertEquals(patch, MapPatch.read(json.getJSONObject("patch")));
    }

    @Test
    void aSpliceReplacesOnlyItsEntry() {
        String out = PoolEntryText.replace(FILE, SurfacePalette.ARID, 1, "{\"seed\": 7}");
        assertEquals(FILE.replace("{\"seed\": 7, \"patch\": {\"pin\": [{\"place\": \"base\", \"x\": 1, \"y\": 2}]}}", "{\"seed\": 7}"), out);
        assertEquals(FILE.replace("{\"seed\": 5, \"story\": true}", "{\"seed\": 5}"),
                PoolEntryText.replace(FILE, SurfacePalette.ARID, 0, "{\"seed\": 5}"));
    }

    @Test
    void aMissingEntryIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PoolEntryText.replace(FILE, SurfacePalette.ARID, 2, "{}"));
        assertThrows(IllegalArgumentException.class, () -> PoolEntryText.replace(FILE, SurfacePalette.FROZEN, 0, "{}"));
    }
}
