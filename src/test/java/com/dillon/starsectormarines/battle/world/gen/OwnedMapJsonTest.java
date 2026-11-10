package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Box;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnedMapJsonTest {

    static OwnedMap sample() {
        OwnedMap.Building hall = new OwnedMap.Building("Hall", OwnedMap.Form.FACILITY, "COMMS", null, 0,
                new Box(10, 10, 20, 16), Frontage.BOTTOM, true);
        OwnedMap.Building shop = new OwnedMap.Building("Shop", OwnedMap.Form.STAMP, "T_WORKSHOP", new Cell(24, 10), 2,
                null, null, false);
        OwnedMap.Settlement works = new OwnedMap.Settlement("Works", "relay", OwnedMap.SettlementRole.WORKS,
                new Box(8, 8, 40, 30), List.of(new Cell(8, 20)), List.of(hall, shop),
                List.of(new OwnedMap.Work(OwnedMap.WorkOp.GRAVEL, new Box(30, 20, 34, 24), null, null, 0),
                        new OwnedMap.Work(OwnedMap.WorkOp.PROP, null, new Cell(31, 21), "industrial-cable-reel", 1)),
                "rift");
        OwnedMap.Drop rift = new OwnedMap.Drop("rift", OwnedMap.DropRole.RIFT,
                List.of(new Cell(0, 40), new Cell(30, 44), new Cell(63, 40)), 5, List.of(new Cell(30, 44)), 3,
                List.of(new OwnedMap.Depth(new Cell(10, 41), .25f)));
        return new OwnedMap("sample", 64, 48, "data/world/roads/x.road.json", "apron", null,
                new Cell(2, 2), new Cell(60, 20),
                List.of(new OwnedMap.Ground(OwnedMap.GroundRole.YARD, new Box(8, 8, 40, 30), 4)),
                List.of(new OwnedMap.Pad(new Cell(4, 4), 3, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON)),
                List.of(new OwnedMap.Site("Apron", TacticalNode.Kind.BEACHHEAD, null, new Cell(2, 2), new Box(0, 0, 6, 6),
                                Faction.MARINE, 20, 0),
                        new OwnedMap.Site("Hall", TacticalNode.Kind.COMMAND_POST, "Hall", null, null, Faction.DEFENDER, 70, 8)),
                List.of(works), List.of(new OwnedMap.Wall(new Cell(5, 35), new Cell(25, 35))),
                List.of(new OwnedMap.Prop(new Cell(12, 30), "doodad.industrial-crate-stack")), List.of(rift),
                List.of(new OwnedMap.Rise("north bank", OwnedMap.RiseRole.BANK, new Box(0, 46, 63, 47), .5f)),
                List.of(new Cell(60, 2)));
    }

    @Test
    void writingThenReadingGivesTheSameMap() throws Exception {
        OwnedMap map = sample();
        assertEquals(map, OwnedMapJson.read(new JSONObject(OwnedMapJson.write(map).toString())));
    }

    @Test
    void aBareDocumentNamesOnlyWhatItHas() throws Exception {
        OwnedMap map = OwnedMapJson.read(new JSONObject(
                "{\"id\":\"bare\",\"width\":32,\"height\":32,\"origin\":[1,1],\"objective\":[30,30]}"));
        assertTrue(map.settlements().isEmpty() && map.drops().isEmpty() && map.sites().isEmpty());
        assertEquals(null, map.roads());
    }

    @Test
    void aNewerDocumentIsRefused() throws Exception {
        JSONObject o = OwnedMapJson.write(sample()).put("version", OwnedMap.VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> OwnedMapJson.read(o));
    }

    @Test
    void aSiteOnABuildingTheMapLacksIsRefused() throws Exception {
        JSONObject o = OwnedMapJson.write(sample());
        o.getJSONArray("sites").getJSONObject(1).put("building", "Nowhere");
        assertThrows(IllegalArgumentException.class, () -> OwnedMapJson.read(o));
    }

    @Test
    void aSettlementTerracedIntoADropTheMapLacksIsRefused() throws Exception {
        JSONObject o = OwnedMapJson.write(sample());
        o.getJSONArray("settlements").getJSONObject(0).put("terraceInto", "Nowhere");
        assertThrows(IllegalArgumentException.class, () -> OwnedMapJson.read(o));
    }

    @Test
    void aNameIsUsedOnce() throws Exception {
        JSONObject o = OwnedMapJson.write(sample());
        o.getJSONArray("sites").getJSONObject(1).put("name", "Apron");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> OwnedMapJson.read(o));
        assertTrue(e.getMessage().contains("Apron"), e.getMessage());
    }
}
