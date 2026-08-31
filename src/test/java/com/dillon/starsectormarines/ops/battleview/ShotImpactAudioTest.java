package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShotImpactAudioTest {

    @Test
    void authoredShoulderLaserImpactCuePlaysAtItsTerminalStop() {
        WeaponDef laser = WeaponRegistry.require(WeaponRegistry.MECH_SHOULDER_LASER_ID);
        ShotEvent impact = shot(laser, BallisticResolver.StopKind.WALL);

        ShotImpactAudio.Cue cue = ShotImpactAudio.resolve(impact, "fallback-explosion");

        assertEquals("marines_explosion", cue.soundId());
        assertEquals(0.86f, cue.volume(), 0f);
    }

    @Test
    void overshootingPenetrativeBeamDoesNotManufactureAnImpactCue() {
        WeaponDef laser = WeaponRegistry.require(WeaponRegistry.MECH_SHOULDER_LASER_ID);

        assertNull(ShotImpactAudio.resolve(
                shot(laser, BallisticResolver.StopKind.OVERSHOOT), "fallback-explosion"));
    }

    private static ShotEvent shot(WeaponDef weapon, BallisticResolver.StopKind stop) {
        return new ShotEvent(2f, 2f, 0f, 14f, 2f, 0f,
                true, Faction.MARINE, 0.10f,
                null, null, null, weapon, 1f, true, stop);
    }
}
