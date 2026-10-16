package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a round meets a fitted hull: the side it came from, and the slot a mount stands on. */
class WarshipHullHitTest {

    /** A 100 x 400 hull, centre of gravity at the sprite's centre, its bounds the whole box. */
    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png","width":100,"height":400,
             "center":[50,200],"bounds":[200,50, 200,-50, -200,-50, -200,50],"weaponSlots":[]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static Warship ship(float facing) throws Exception {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) {
                    return new JSONObject("{\"hullId\":\"test\",\"weaponGroups\":[]}");
                }
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                return List.of(Map.of("id", "test", "hitpoints", "20000", "armor rating", "1750"));
            }
        });
        VanillaWeapons.clearCache();
        return Warship.fit("test", "test_Standard", Faction.MARINE, 50f, 50f, facing);
    }

    @Test
    void aRoundEntersOnTheSideItCameFrom() throws Exception {
        Warship s = ship(0f);
        WarshipPlating p = s.plating();
        assertNotNull(p, "a fitted hull carries a grid");
        // Facing 0 points the nose along +y, so the port side is toward -x.
        int fromPort = s.entryCell(s.x - 100f, s.y, s.centerX(), s.centerY());
        int fromStarboard = s.entryCell(s.x + 100f, s.y, s.centerX(), s.centerY());
        int fromAhead = s.entryCell(s.x, s.y + 100f, s.centerX(), s.centerY());
        assertEquals(WarshipPlating.Section.PORT, p.section(fromPort % p.cols, fromPort / p.cols));
        assertEquals(WarshipPlating.Section.STARBOARD, p.section(fromStarboard % p.cols, fromStarboard / p.cols));
        assertEquals(WarshipPlating.Section.BOW, p.section(fromAhead % p.cols, fromAhead / p.cols));
        assertTrue(s.cellX(fromPort) < s.x && s.cellX(fromStarboard) > s.x, "the cells lie where they were struck");
    }

    @Test
    void aBlastWithNoSourceEntersFromTheSideNearestWhereItLanded() throws Exception {
        Warship s = ship(90f);
        WarshipPlating p = s.plating();
        // Turned a quarter, the nose points along -x; a blast beside the nose enters at the bow.
        int cell = s.entryCell(Float.NaN, Float.NaN, s.x - s.lengthCells * 0.45f, s.y);
        assertEquals(WarshipPlating.Section.BOW, p.section(cell % p.cols, cell / p.cols));
    }

    @Test
    void aSlotSitsOnTheCellUnderIt() throws Exception {
        Warship s = ship(0f);
        WarshipPlating p = s.plating();
        // A slot far to port and well forward, in the hull frame (across, along) in cells.
        WarshipSlot slot = new WarshipSlot("WS", "BALLISTIC", "LARGE", false,
                -s.widthCells * 0.45f, s.lengthCells * 0.3f, 90f, 120f);
        int cell = s.slotCell(slot);
        assertEquals(0, cell % p.cols, "the port edge column");
        assertTrue(cell / p.cols < p.rows / 2, "forward half");
    }
}
