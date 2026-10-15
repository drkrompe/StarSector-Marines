package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.world.gen.GarrisonStructureTable;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped structure table parses, and a malformed document degrades to the size base rather than failing. */
class GarrisonStructureCatalogTest {

    @Test
    void shippedTableNamesTheDefenceIndustries() throws Exception {
        String text = Files.readString(Path.of("mod", GarrisonStructureCatalog.PATH), StandardCharsets.UTF_8);
        GarrisonStructureTable table = GarrisonStructureCatalog.parse(new JSONObject(text));
        for (String id : new String[] {"grounddefenses", "heavybatteries", "patrolhq", "militarybase", "highcommand"}) {
            assertTrue(table.contributions().containsKey(id), id);
        }
        GarrisonStructureTable.Contribution batteries = table.of("heavybatteries");
        assertTrue(batteries.heavy() > table.of("grounddefenses").heavy(), "batteries outweigh ground defences");
    }

    @Test
    void missingWeightsReadAsZeroAndBadEntriesAreSkipped() throws Exception {
        GarrisonStructureTable table = GarrisonStructureCatalog.parse(new JSONObject(
                "{\"structures\":{\"patrolhq\":{\"elite\":0.1},\"broken\":3}}"));
        assertEquals(new GarrisonStructureTable.Contribution(0f, 0.1f, 0f), table.of("patrolhq"));
        assertEquals(GarrisonStructureTable.Contribution.NONE, table.of("broken"));
        assertSame(GarrisonStructureTable.EMPTY, GarrisonStructureCatalog.parse(new JSONObject("{}")));
    }
}
