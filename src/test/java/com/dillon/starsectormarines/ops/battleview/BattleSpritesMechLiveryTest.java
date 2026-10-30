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
        assertEquals(factionPath("tri-tachyon", "hc-10.png"),
                marine.hc10.sourcePath);
        assertEquals(factionPath("tri-tachyon", "pulse-laser-arm.png"),
                marine.pulseLaserArm.sourcePath);
        assertEquals(factionPath("tri-tachyon", "dc-5.png"),
                marine.dc5.sourcePath);
        assertEquals(factionPath("tri-tachyon", "ac-6-qs.png"),
                marine.ac6Qs.sourcePath);
        assertEquals(factionPath("tri-tachyon", "dc-4-qs.png"),
                marine.dc4Qs.sourcePath);
        assertEquals(factionPath("lions-guard", "chassis-warden.png"),
                defender.wardenChassis.sourcePath);
        assertEquals(factionPath("lions-guard", "lrm-rack.png"),
                defender.lrmPod.sourcePath);
        assertEquals(factionPath("lions-guard", "lions-guard-thermal-lance.png"),
                defender.thermalLance.sourcePath);
        assertEquals(factionPath("lions-guard", "srm-3-qs.png"),
                defender.srm3QsPod.sourcePath);
        assertSame(sprites.layeredMechSprites().foot, marine.foot);
        assertSame(sprites.layeredMechSprites().muzzleFlash, defender.muzzleFlash);
    }

    @Test
    void oneMissingPaintedLayerFallsBackToTheWholeBaseFamily() {
        String missing = factionPath("lions-guard", "lrm-rack.png");
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
