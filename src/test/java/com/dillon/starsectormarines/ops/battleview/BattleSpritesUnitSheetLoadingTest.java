package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("graphics/battle/marine-modular-topdown/variants/armor/specter-heavy/shoulder-left.png",
                specter.shoulderLeft.sourcePath);
        assertEquals("graphics/battle/marine-modular-topdown/variants/armor/specter-heavy/shoulder-right.png",
                specter.shoulderRight.sourcePath);
        assertNull(regular.shoulderLeft);
        assertNull(regular.shoulderRight);
        assertSame(regular.rifle, specter.rifle);
        assertSame(regular.srm1, specter.srm1);
        LayeredUnitAssets arbalest = sprites.layeredUnitSprites().get(LayeredArmorFamily.ARBALEST_HEAVY);
        String olive = "graphics/battle/marine-modular-topdown/variants/armor/specter-heavy/paints/olive/";
        assertEquals(olive + "body.png", arbalest.body.sourcePath);
        assertEquals(olive + "head.png", arbalest.head.sourcePath);
        assertEquals(olive + "foot.png", arbalest.foot.sourcePath);
        assertEquals(olive + "shoulder-left.png", arbalest.shoulderLeft.sourcePath);
        assertEquals(olive + "shoulder-right.png", arbalest.shoulderRight.sourcePath);
        assertSame(regular.rifle, arbalest.rifle);
        LayeredUnitAssets jack = sprites.layeredUnitSprites().get(LayeredArmorFamily.BOARDING_JACK);
        String rig = "graphics/battle/marine-modular-topdown/variants/armor/boarding-jack/";
        assertEquals(rig + "body.png", jack.body.sourcePath);
        assertEquals(rig + "head.png", jack.head.sourcePath);
        assertEquals(rig + "foot.png", jack.foot.sourcePath);
        assertEquals(rig + "shoulder-left.png", jack.shoulderLeft.sourcePath);
        assertEquals(rig + "shoulder-right.png", jack.shoulderRight.sourcePath);
        assertSame(regular.rifle, jack.rifle);
        LayeredUnitAssets raider = sprites.layeredUnitSprites().get(LayeredArmorFamily.BOARDING_JACK_PIRATE);
        assertEquals(rig + "paints/pirate/body.png", raider.body.sourcePath);
        assertEquals(rig + "paints/pirate/foot.png", raider.foot.sourcePath);
        assertEquals(rig + "paints/pirate/shoulder-left.png", raider.shoulderLeft.sourcePath);
    }

    @Test
    void absentOptionalShouldersKeepModularFamilyAvailable() {
        BattleSprites sprites = new BattleSprites() {
            @Override
            public LayeredSpriteCache loadLayeredSprite(String path) {
                return path.contains("shoulder-") ? null : LayeredSpriteCache.headless(path, 150, 95);
            }
        };
        sprites.ensureLayeredUnitSprites();
        LayeredUnitAssets specter = sprites.layeredUnitSprites().get(LayeredArmorFamily.SPECTER_HEAVY);
        assertNull(specter.shoulderLeft);
        assertNull(specter.shoulderRight);
    }

    @Test
    void unitSheetLoadingRequestsOnlyDeclaredCorpseSheets() {
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
            if (type.carriesSprite() && type.deadSpritePath != null) expectedPaths.add(type.deadSpritePath);
        }
        assertEquals(expectedPaths, loadedPaths, "live bodies have no sheet; only corpse sheets load");
    }
}
