package com.dillon.starsectormarines.battle.weapon.fx;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class WeaponFxRuntimeTest {

    private static final float EPS = 0.0001f;

    @Test
    void locustLaunchContextStaysAtMountAndBackblastRotatesOppositeEveryBearing()
            throws Exception {
        assertBackblast(4f, 5f, 9f, 5f, -90f, -1f, 0f);
        assertBackblast(4f, 5f, 4f, 10f, 0f, 0f, -1f);
        assertBackblast(4f, 5f, -1f, 5f, 90f, 1f, 0f);
        assertBackblast(4f, 5f, 4f, 0f, -180f, 0f, 1f);
    }

    @Test
    void everyCarrierFamilyResolvesItsRegistryComposition() {
        ShotEvent primary = new ShotEvent(1f, 2f, 3f, 4f, true,
                Faction.MARINE, 0.1f, null, MarineWeapon.DMR, null);
        ShotEvent special = new ShotEvent(1f, 2f, 3f, 4f, true,
                Faction.MARINE, 0.1f, null, null, MarineSecondary.ROCKET_LAUNCHER);
        ShotEvent mech = new ShotEvent(1f, 2f, 3f, 4f, true,
                Faction.MARINE, 0.1f, null, null, null, MechWeapon.SRM_POD);
        ShotEvent turret = new ShotEvent(1f, 2f, 3f, 4f, true,
                Faction.DEFENDER, 0.1f, TurretKind.ARBALEST);

        assertSame(MarineWeapon.DMR.def().fx, WeaponFxRuntime.definition(primary));
        assertSame(MarineSecondary.ROCKET_LAUNCHER.def().fx,
                WeaponFxRuntime.definition(special));
        assertSame(MechWeapon.SRM_POD.def().fx, WeaponFxRuntime.definition(mech));
        assertSame(TurretKind.ARBALEST.fx(), WeaponFxRuntime.definition(turret));
    }

    private static void assertBackblast(float fromX, float fromY, float toX, float toY,
                                        float bearing, float expectedXSign,
                                        float expectedYSign) throws Exception {
        ShotEvent shot = new ShotEvent(fromX, fromY, toX, toY, true,
                Faction.DEFENDER, 0.15f, TurretKind.LOCUST);
        FxCompositionContext context = WeaponFxRuntime.launchContext(shot);
        assertEquals(fromX, context.x(), EPS);
        assertEquals(fromY, context.y(), EPS);
        assertEquals(bearing, context.bearingDegrees(), EPS);
        FxParticleCommand command = WeaponFxComposer.compose(
                TurretKind.LOCUST.fx(), FxSlot.LAUNCH, context).get(0);
        assertFalse(TurretKind.LOCUST.fx().layers(FxSlot.MUZZLE).isEmpty());
        if (expectedXSign != 0f) {
            assertEquals(expectedXSign, Math.signum(command.velocityX()), EPS);
        }
        if (expectedYSign != 0f) {
            assertEquals(expectedYSign, Math.signum(command.velocityY()), EPS);
        }
    }
}
