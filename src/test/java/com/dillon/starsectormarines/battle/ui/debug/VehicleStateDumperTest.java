package com.dillon.starsectormarines.battle.ui.debug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleStateDumperTest {

    @Test
    void snapshotWriteIsSynchronousAndUsesExpectedCommonPath() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<JSONObject> json = new AtomicReference<>();
        AtomicBoolean onlyIfChanged = new AtomicBoolean(true);
        SettingsAPI previous = Global.getSettings();
        SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("writeJSONToCommon")) {
                        called.set(true);
                        path.set((String) args[0]);
                        json.set((JSONObject) args[1]);
                        onlyIfChanged.set((Boolean) args[2]);
                    }
                    return defaultValue(method.getReturnType());
                });
        JSONObject snapshot = new JSONObject().put("state", "DEPARTING");

        try {
            Global.setSettings(settings);
            VehicleStateDumper.writeSnapshot(snapshot);
        } finally {
            Global.setSettings(previous);
        }

        assertTrue(called.get());
        assertEquals("starsector_marines/debug/vehicle_state.json", path.get());
        assertSame(snapshot, json.get());
        assertFalse(onlyIfChanged.get());
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
