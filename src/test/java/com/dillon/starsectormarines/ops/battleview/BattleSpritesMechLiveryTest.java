package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BattleSpritesMechLiveryTest {

    @Test
    void tacticalSidesResolveCompleteFactionChassisAndWeaponSets() {
        BattleSprites sprites = headlessSprites(null);
        sprites.configureMechLiveries("tritachyon", "lions_guard");
        sprites.ensureLayeredMechSprites();

        LayeredMechAssets marine = sprites.layeredMechSprites(Faction.MARINE);
        LayeredMechAssets defender = sprites.layeredMechSprites(Faction.DEFENDER);
        assertEquals(factionPath("tri-tachyon", "chassis-hound.png"),
                marine.houndChassis.sourcePath);
        assertEquals(factionPath("tri-tachyon", "linear-cannon-variant.png"),
                marine.linearCannon.sourcePath);
        assertEquals(factionPath("lions-guard", "chassis-sirocco.png"),
                defender.siroccoChassis.sourcePath);
        assertEquals(factionPath("lions-guard", "lrm-pod.png"),
                defender.lrmPod.sourcePath);
        assertSame(sprites.layeredMechSprites().foot, marine.foot);
        assertSame(sprites.layeredMechSprites().muzzleFlash, defender.muzzleFlash);
    }

    @Test
    void oneMissingPaintedLayerFallsBackToTheWholeBaseFamily() {
        String missing = factionPath("lions-guard", "lrm-pod.png");
        BattleSprites sprites = headlessSprites(missing);
        sprites.configureMechLiveries("tritachyon", "lions_guard");
        sprites.ensureLayeredMechSprites();

        assertEquals(factionPath("tri-tachyon", "chaingun-arm.png"),
                sprites.layeredMechSprites(Faction.MARINE).chaingunArm.sourcePath);
        assertSame(sprites.layeredMechSprites(),
                sprites.layeredMechSprites(Faction.DEFENDER));
    }

    private static BattleSprites headlessSprites(String missingPath) {
        return new BattleSprites() {
            @Override
            public LayeredSpriteCache loadLayeredSprite(String path) {
                return path.equals(missingPath)
                        ? null : LayeredSpriteCache.headless(path, 1, 1);
            }
        };
    }

    private static String factionPath(String folder, String filename) {
        return "graphics/battle/mech-modular-topdown/factions/"
                + folder + "/" + filename;
    }
}
