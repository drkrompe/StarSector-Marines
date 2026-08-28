package com.dillon.starsectormarines.battle.ui.debug;

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

    private static final String PATH = "starsector_marines/debug/tick_profile_1.json";
    private static final String FIXTURE_PATH =
            "starsector_marines/debug/tick_profile_1.fixture.json";
    private static final int CAP = TickProfileDumper.MAX_COMMON_FILE_CHARS;

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
