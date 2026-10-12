package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompanyDeckSpritesTest {
    @Test void companyDeckLoadsRealAircraftCatalogBeforeRenderingAndOnlyOnce() {
        var previous = Global.getSettings();
        List<String> loaded = new ArrayList<>();
        var sprite = (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> 64f;
                    case "getHeight" -> 96f;
                    default -> null;
                });
        var settings = (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "loadTexture" -> { loaded.add((String) args[0]); yield null; }
                    case "getSprite" -> sprite;
                    default -> null;
                });
        // Unrelated crew/floor assets are outside this aircraft-loading regression.
        var sprites = new BattleSprites() {
            @Override public void ensureTileSheet() { }
            @Override public void ensureRoadSheet() { }
            @Override public void ensureDoodadSheet() { }
            @Override public void ensureLayeredUnitSprites() { }
            @Override public void ensureLayeredMechSprites() { }
            @Override public void ensureMarineSecondarySprites() { }
        };
        try {
            Global.setSettings(settings);
            var deck = new CompanyDeck(TestHulls.transport(), 42, sprites, List::of, List::of, () -> new boolean[]{true});
            assertNull(sprites.airframeSprite(ShuttleType.HERMES));
            deck.ensureSceneSprites();
            for (ShuttleType type : ShuttleType.values()) {
                var hull = sprites.airframeSprite(type);
                assertNotNull(hull, "Missing shipboard aircraft art for " + type);
                assertSame(sprite, hull.sprite);
                assertTrue(loaded.contains(type.spritePath()));
            }
            int firstLoad = loaded.size();
            deck.ensureSceneSprites();
            assertEquals(firstLoad, loaded.size(), "Repainting or reopening rooms must reuse loaded hulls");
        } finally { Global.setSettings(previous); }
    }
}
