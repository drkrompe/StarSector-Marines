package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common-folder writer throws on any string past its size cap, and does it
 * from its own thread when routed through {@code writeJSONToCommon} — so the
 * recording settings here refuse an oversize write the same way the game does.
 * The cases are the three sizes the dumper has to survive: the fixture fits
 * inline, the fixture only fits on its own, the fixture fits nowhere.
 */
class TickProfileDumperTest {
    @Test
    void serializesFrozenSquadRouteBuildDiagnostics() throws Exception {
        TickInnerProfile profile = new TickInnerProfile();
        profile.recordSquadRouteBuild(2_000_000, 17, "COST", 6, 120,
                2, 310, 150, 600, 1100, 950);
        TickInnerProfile.Snapshot snapshot = profile.snapshot();
        profile.reset();
        JSONObject json = new JSONObject();
        TickProfileDumper.putSquadRouteBuilds(json, snapshot.slowSquadRouteBuilds);
        JSONObject sample = json.getJSONArray("slowSquadRouteBuilds").getJSONObject(0);
        assertEquals(2.0, sample.getDouble("ms"));
        assertEquals(17, sample.getInt("squadId"));
        assertEquals("COST", sample.getString("reason"));
        assertEquals(310, sample.getInt("seedExpanded"));
        assertEquals(600, sample.getInt("unpaddedCells"));
        assertEquals(1100, sample.getInt("corridorCells"));
        assertEquals(950, sample.getInt("settledCells"));
        assertTrue(json.getString("squadRouteBuildSemantics").contains("bounded-age"));
    }

    private static final String PATH = "starsector_marines/debug/tick_profile_1.json";
    private static final String FIXTURE_PATH =
            "starsector_marines/debug/tick_profile_1.fixture.json";
    private static final int CAP = TickProfileDumper.MAX_COMMON_FILE_CHARS;

    @Test
    void serializesBoundedSearchSamplesAndWorkerTimeWarning() throws Exception {
        TickInnerProfile profile = new TickInnerProfile();
        profile.enterAction(48L, 9, "DEFEND_SITE");
        profile.routeReason("rally");
        profile.recordPathSearch(1_250_000L, 4, 5, 60, 70,
                true, 0, 231, 128, "MISSING_FIELD");
        profile.exitAction();
        profile.recordPathSearch(750_000L, 6, 7, 8, 9,
                false, 12, 28);
        JSONObject root = new JSONObject();

        TickProfileDumper.putPathSearches(root,
                profile.snapshot().pathfindExpandedNodes,
                profile.snapshot().slowPathSearches);
        JSONObject parsed = new JSONObject(root.toString());

        assertEquals(259L, parsed.getLong("flatPathfindExpandedNodes"));
        assertTrue(parsed.getString("pathSearchTimingSemantics")
                .contains("parallel workers"));
        assertTrue(parsed.getString("pathSearchTimingSemantics")
                .contains("public flat GridPathfinder"));
        assertEquals(2, parsed.getJSONArray("slowFlatPathSearches").length());
        JSONObject first = parsed.getJSONArray("slowFlatPathSearches").getJSONObject(0);
        assertEquals(1_250_000L, first.getLong("nanos"));
        assertEquals(1250.0, first.getDouble("us"));
        assertEquals(4, first.getInt("startX"));
        assertEquals(5, first.getInt("startY"));
        assertEquals(60, first.getInt("goalX"));
        assertEquals(70, first.getInt("goalY"));
        assertTrue(first.getBoolean("usesOccupancy"));
        assertFalse(first.getBoolean("found"));
        assertEquals(0, first.getInt("pathCells"));
        assertEquals(231, first.getInt("expandedNodes"));
        assertEquals(48L, first.getLong("memberId"));
        assertEquals(9, first.getInt("squadId"));
        assertEquals("DEFEND_SITE", first.getString("action"));
        assertEquals("rally", first.getString("routeReason"));
        assertEquals(128, first.getInt("goalOccupancy"));
        assertEquals("MISSING_FIELD", first.getString("fallbackReason"));
        JSONObject second = parsed.getJSONArray("slowFlatPathSearches").getJSONObject(1);
        assertEquals(0L, second.getLong("memberId"));
        assertEquals(-1, second.getInt("squadId"));
        assertEquals("", second.getString("action"));
        assertEquals("", second.getString("routeReason"));
        assertEquals(-1, second.getInt("goalOccupancy"));
        assertEquals("", second.getString("fallbackReason"));
        assertTrue(parsed.getJSONArray("slowFlatPathSearches").getJSONObject(1)
                .getBoolean("found"));
        assertTrue(root.toString().length() < 2_000,
                "bounded top-search detail must stay small beside an embedded fixture");
    }

    @Test
    void smallFixtureStaysEmbedded() throws Exception {
        Map<String, String> written = new LinkedHashMap<>();
        JSONObject root = new JSONObject().put("simTickIndex", 1);

        String path = write(written, root, new JSONObject().put("kind", "CONQUEST"));

        assertEquals(PATH, path);
        assertEquals(1, written.size());
        JSONObject dump = new JSONObject(written.get(PATH));
        assertEquals("CONQUEST", dump.getJSONObject("battleFixture").getString("kind"));
        assertFalse(dump.has("battleFixtureFile"));
    }

    @Test
    void fixtureTooLargeToEmbedSpillsToSiblingFile() throws Exception {
        Map<String, String> written = new LinkedHashMap<>();
        JSONObject root = new JSONObject().put("simTickIndex", 1);
        root.put("filler", "r".repeat(CAP / 4));
        JSONObject fixture = new JSONObject().put("seats", "f".repeat(CAP * 7 / 8));

        String path = write(written, root, fixture);

        assertEquals(PATH, path);
        assertEquals(2, written.size());
        JSONObject dump = new JSONObject(written.get(PATH));
        assertFalse(dump.has("battleFixture"));
        assertEquals(FIXTURE_PATH, dump.getString("battleFixtureFile"));
        assertEquals(fixture.toString(), written.get(FIXTURE_PATH));
    }

    @Test
    void fixtureTooLargeToSpillIsDroppedAndTheProfileStillLands() throws Exception {
        Map<String, String> written = new LinkedHashMap<>();
        JSONObject root = new JSONObject().put("simTickIndex", 1);
        JSONObject fixture = new JSONObject().put("seats", "f".repeat(CAP + 1));

        String path = write(written, root, fixture);

        assertEquals(PATH, path);
        assertEquals(1, written.size());
        JSONObject dump = new JSONObject(written.get(PATH));
        assertFalse(dump.has("battleFixture"));
        assertFalse(dump.has("battleFixtureFile"));
        assertTrue(dump.getString("battleFixtureOmitted").contains("exceeds"));
    }

    private static String write(Map<String, String> written, JSONObject root,
                                JSONObject fixture) throws Exception {
        SettingsAPI previous = Global.getSettings();
        Global.setSettings(recordingSettings(written));
        try {
            return TickProfileDumper.write(PATH, root, fixture);
        } finally {
            Global.setSettings(previous);
        }
    }

    private static SettingsAPI recordingSettings(Map<String, String> written) {
        return (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("writeTextFileToCommon")) {
                        String data = (String) args[1];
                        if (data.length() > CAP) {
                            throw new RuntimeException("Max text file string length: "
                                    + CAP + ", string length: " + data.length());
                        }
                        written.put((String) args[0], data);
                        return null;
                    }
                    if (method.getName().equals("writeJSONToCommon")) {
                        throw new AssertionError("dumps must be length-checked "
                                + "before they reach the writer");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        throw new AssertionError("Unhandled primitive type: " + type);
    }
}
