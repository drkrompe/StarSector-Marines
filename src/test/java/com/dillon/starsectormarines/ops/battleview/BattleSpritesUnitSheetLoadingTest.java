package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BattleSpritesUnitSheetLoadingTest {

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
