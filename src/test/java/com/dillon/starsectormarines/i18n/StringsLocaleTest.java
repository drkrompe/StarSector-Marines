package com.dillon.starsectormarines.i18n;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringsLocaleTest {
    private static SettingsAPI settings(String localeOverride, boolean cjk) {
        return (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getSettingsJSON" -> new JSONObject().put("localeOverride", localeOverride);
                    case "getBoolean" -> cjk;
                    case "getString" -> args[args.length - 1];
                    default -> throw new AssertionError(method.getName());
                });
    }

    @Test
    void languageComesFromTheBaseGamesLocaleOverride() {
        SettingsAPI previous = Global.getSettings();
        try {
            Global.setSettings(settings("tr_TR", true));
            Strings.refreshLocale();
            assertEquals(Locale.forLanguageTag("tr-TR"), Strings.locale());
            assertEquals("İ", Strings.upper("i"));
            assertTrue(Strings.cjk());
        } finally {
            Global.setSettings(previous);
            Strings.refreshLocale();
        }
    }
}
