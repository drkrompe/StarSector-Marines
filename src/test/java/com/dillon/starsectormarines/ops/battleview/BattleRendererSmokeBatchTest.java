package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.QuadBatch;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class BattleRendererSmokeBatchTest {

    @Test
    void attachRegistersTheSmokeFlipbookForTheLiveGlDrain() throws Exception {
        SpriteAPI smokeSheet = spriteToken(512f, 512f);
        BattleSprites sprites = new BattleSprites() {
            @Override public void ensureSmokeSprites() {}
            @Override public void ensureSatchelSprite() {}
            @Override public SpriteAPI smokeFieldSheet() { return smokeSheet; }
        };
        BattleRenderer renderer = new BattleRenderer(sprites);

        renderer.onAttach();

        assertNotNull(registeredBatches(renderer).get(smokeSheet));
    }

    @SuppressWarnings("unchecked")
    private static Map<SpriteAPI, QuadBatch> registeredBatches(BattleRenderer renderer)
            throws ReflectiveOperationException {
        Field field = BattleRenderer.class.getDeclaredField("batchBySheet");
        field.setAccessible(true);
        return (Map<SpriteAPI, QuadBatch>) field.get(renderer);
    }

    private static SpriteAPI spriteToken(float width, float height) {
        return (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> width;
                    case "getHeight" -> height;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "smoke-sheet";
                    default -> null;
                });
    }
}
