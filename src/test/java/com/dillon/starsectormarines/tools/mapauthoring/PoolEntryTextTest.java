package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pool-file writer: an entry reads back equal, and a file's own entry writes the file back byte for byte. */
class PoolEntryTextTest {

    /** A pool file in the form the pool files are written. */
    private static final String FILE = """
            {
              "version": 3,
              "map": "heart-front",
              "home": "ARID",
              "entry": {
                "seed": 5,
                "note": "two mothers \\"and\\" a — dash",
                "patch": {
                  "lift": [
                    164,
                    18
                  ],
                  "place": [
                    {
                      "name": "airbase-1",
                      "kind": "AIRFIELD",
                      "x": 96,
                      "y": 8,
                      "turns": 0
                    }
                  ]
                }
              }
            }
            """;

    @Test
    void anEntryReadsBackEqual() throws Exception {
        MapPatch patch = new MapPatch(List.of(new MapPatch.Pin("redoubt-2", 40, 61)),
                List.of(new DamagePlan.Stroke(DamagePlan.Mode.BATTERED, 2.5, List.of(new DamagePlan.Point(3, 4), new DamagePlan.Point(5.25, 6)))),
                new MapPatch.Cell(10, 11), List.of(new MapPatch.Bore(1, 2, true)), List.of(new MapPatch.Cell(7, 8)),
                List.of(new Structure("airbase-1", StructureKind.AIRFIELD, 96, 8, 1, true)));
        StoryMapPool.Entry entry = new StoryMapPool.Entry(5, "the \"story\" map\\", patch, true);
        String text = PoolEntryText.file(StoryMap.HEART_FRONT, SurfacePalette.ARID, entry);
        StoryMapPool pool = StoryMapPool.read(StoryMap.HEART_FRONT, new JSONObject(text));
        assertEquals(SurfacePalette.ARID, pool.home());
        assertEquals(entry, pool.owned());
    }

    @Test
    void aFilesOwnEntryWritesItBack() throws Exception {
        StoryMapPool pool = StoryMapPool.read(StoryMap.HEART_FRONT, new JSONObject(FILE));
        assertEquals(FILE, PoolEntryText.file(StoryMap.HEART_FRONT, pool.home(), pool.owned()));
    }
}
