package com.dillon.starsectormarines.i18n;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A key the game's copy does not hold reads as a miss, not as the game's placeholder, and is asked for once. */
class StringsMissingTest {

    /** Settings that hold {@code held} and answer any other key the way the game does. */
    private static SettingsAPI copyHolding(String held, AtomicInteger asks) {
        return (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getString")) return null;
                    String key = (String) args[args.length - 1];
                    // Only this test's keys are reported missing, so no other test inherits a remembered miss.
                    if (!key.startsWith("stringsMissingTest")) return null;
                    asks.incrementAndGet();
                    return key.equals(held) ? "Held text" : Strings.missingPlaceholder(key);
                });
    }

    @Test
    void aMissingKeyFallsBackToItsReadableNameAndIsAskedForOnce() {
        SettingsAPI previous = Global.getSettings();
        AtomicInteger asks = new AtomicInteger();
        try {
            Global.setSettings(copyHolding("stringsMissingTestHELD", asks));
            Strings.refreshLocale();

            assertEquals("North room", Strings.named("stringsMissingTest", "NORTH_ROOM"));
            assertEquals("North room", Strings.named("stringsMissingTest", "NORTH_ROOM"));
            assertEquals(1, asks.get());

            assertEquals("Held text", Strings.named("stringsMissingTest", "HELD"));
            assertEquals("Held text", Strings.get("stringsMissingTestHELD"));
            assertEquals(3, asks.get());
        } finally {
            Global.setSettings(previous);
            Strings.refreshLocale();
        }
    }
}
