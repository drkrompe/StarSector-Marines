package com.dillon.starsectormarines.battle.weapon.fx;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
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
        WeaponDef srm = WeaponRegistry.require(WeaponRegistry.MECH_SRM_POD_ID);
        StructureDef arbalest = TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.ARBALEST_STRUCTURE_ID);
        ShotEvent primary = new ShotEvent(1f, 2f, 3f, 4f, true,
                Faction.MARINE, 0.1f, null, WeaponRegistry.require(WeaponRegistry.DMR_ID), null);
        ShotEvent special = new ShotEvent(1f, 2f, 3f, 4f, true,
                Faction.MARINE, 0.1f, null, null, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID));
        ShotEvent mech = new ShotEvent(1f, 2f, 3f, 4f, true,
                Faction.MARINE, 0.1f, null, null, null, srm);
        ShotEvent turret = new ShotEvent(1f, 2f, 3f, 4f, true,
                Faction.DEFENDER, 0.1f, arbalest);

        assertSame(WeaponRegistry.require(WeaponRegistry.DMR_ID).fx, WeaponFxRuntime.definition(primary));
        assertSame(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID).weaponDef().fx,
                WeaponFxRuntime.definition(special));
        assertSame(srm.fx, WeaponFxRuntime.definition(mech));
        assertSame(arbalest.mount.weapon.fx, WeaponFxRuntime.definition(turret));
    }

    private static void assertBackblast(float fromX, float fromY, float toX, float toY,
                                        float bearing, float expectedXSign,
                                        float expectedYSign) throws Exception {
        StructureDef locust = TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.LOCUST_STRUCTURE_ID);
        ShotEvent shot = new ShotEvent(fromX, fromY, toX, toY, true,
                Faction.DEFENDER, 0.15f, locust);
        FxCompositionContext context = WeaponFxRuntime.launchContext(shot);
        assertEquals(fromX, context.x(), EPS);
        assertEquals(fromY, context.y(), EPS);
        assertEquals(bearing, context.bearingDegrees(), EPS);
        FxParticleCommand command = WeaponFxComposer.compose(
                locust.mount.weapon.fx, FxSlot.LAUNCH, context).get(0);
        assertFalse(locust.mount.weapon.fx.layers(FxSlot.MUZZLE).isEmpty());
        if (expectedXSign != 0f) {
            assertEquals(expectedXSign, Math.signum(command.velocityX()), EPS);
        }
        if (expectedYSign != 0f) {
            assertEquals(expectedYSign, Math.signum(command.velocityY()), EPS);
        }
    }
}
