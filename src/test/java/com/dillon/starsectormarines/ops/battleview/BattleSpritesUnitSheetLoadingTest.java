package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class BattleSpritesUnitSheetLoadingTest {

    @Test
    void specterUsesItsOwnFeetAndKeepsSharedWeapons() {
        BattleSprites sprites = new BattleSprites() {
            @Override
            public LayeredSpriteCache loadLayeredSprite(String path) {
                return LayeredSpriteCache.headless(path, 38, 52);
            }
        };
        sprites.ensureLayeredUnitSprites();
        LayeredUnitAssets specter = sprites.layeredUnitSprites().get(LayeredArmorFamily.SPECTER_HEAVY);
        LayeredUnitAssets regular = sprites.layeredUnitSprites().get(LayeredArmorFamily.ARMY_GREEN);
        assertEquals("graphics/battle/marine-modular-topdown/variants/armor/specter-heavy/foot.png",
                specter.foot.sourcePath);
        assertEquals("graphics/battle/marine-modular-topdown/marine-foot.png", regular.foot.sourcePath);
        assertSame(regular.rifle, specter.rifle);
        assertSame(regular.rocketLauncher, specter.rocketLauncher);
    }

    @Test
    void unitSheetLoadingFollowsUnitTypeRenderCapability() {
        List<String> loadedPaths = new ArrayList<>();
        BattleSprites sprites = new BattleSprites() {
            @Override
            public UnitSpriteCache loadUnitSheet(String path) {
                loadedPaths.add(path);
                return null;
            }
        };

        sprites.ensureUnitSheets();

        List<String> expectedPaths = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            if (!type.drawnAsSheet()) continue;
            expectedPaths.add(type.spritePath);
            if (type.deadSpritePath != null) expectedPaths.add(type.deadSpritePath);
        }
        assertEquals(expectedPaths, loadedPaths);
        assertFalse(loadedPaths.contains(""), "non-sheet unit types must not request the mod directory as a texture");
    }
}
