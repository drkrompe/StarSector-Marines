package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A screen's retained-UI images go with its lease, and are loaded afresh afterwards. */
class UiSpriteCacheScopeTest {

    private static final String IMAGE = "graphics/ui/hq/board.png";

    /** Hands out a fresh id per load and forgets a path on unload. */
    private static final class Store implements TextureLedger.Backend {
        final Map<String, Integer> resident = new HashMap<>();
        int loads;

        @Override public int residentId(String path) { return resident.getOrDefault(path, 0); }

        @Override public int load(String path) {
            loads++;
            resident.put(path, 0);
            return 0;
        }

        @Override public long bytes(String path) { return 1024; }

        @Override public void unload(String path, int textureId) { resident.remove(path); }
    }

    @Test
    void imagesLoadedInsideAScopeAreGivenBackAndForgottenWhenItCloses() throws Exception {
        SpriteAPI sprite = (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> 64f;
                    case "getHeight" -> 32f;
                    default -> null;
                });
        SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getSprite" -> sprite;
                    case "loadText" -> throw new IOException("no manifest");
                    default -> null;
                });
        SettingsAPI previous = Global.getSettings();
        Global.setSettings(settings);
        try {
            Store store = new Store();
            TextureLedger ledger = new TextureLedger(store);
            UiSpriteCache cache = new UiSpriteCache();

            TextureLease dialog = ledger.lease("ops dialog ui");
            cache.enter(dialog);
            UiImage first = cache.resolve(IMAGE);
            assertNotNull(first);
            assertTrue(ledger.held(IMAGE), "the image is held under the dialog's lease");
            cache.leave(dialog);
            assertFalse(ledger.held(IMAGE), "closing the dialog gives it back");

            TextureLease again = ledger.lease("ops dialog ui");
            cache.enter(again);
            UiImage second = cache.resolve(IMAGE);
            assertEquals(2, store.loads, "a later paint loads it afresh rather than keeping the dead one");
            assertNotSame(first, second);
            cache.leave(again);
        } finally {
            Global.setSettings(previous);
        }
    }
}
