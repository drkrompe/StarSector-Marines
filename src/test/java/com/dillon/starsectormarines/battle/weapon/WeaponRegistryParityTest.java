package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code data/marines/marine-weapons.weapon.json} field-for-field against
 * the values {@link WeaponDef} carried before the catalog moved to data.
 * This is the artifact that makes a substrate migration reviewable: the JSON is
 * correct if and only if every number below still matches, and the expectations
 * are written as literals rather than read back from the registry so a typo in
 * the data cannot quietly validate itself.
 *
 * The literal checks protect the shipped catalog while registry-coverage checks
 * ensure every mounted definition participates without a parallel enum list.
 */
class WeaponRegistryParityTest {

    private static final float EPS = 1e-6f;

    @Test
    void everyShippedHandleResolvesToExactlyOneDef() {
        Set<String> ids = new HashSet<>();
        for (WeaponDef weapon : WeaponRegistry.installed().all()) {
            assertTrue(ids.add(weapon.id), "duplicate id " + weapon.id);
        }
        for (SpecialEquipmentDef weapon : SpecialEquipmentRegistry.installed().all()) {
            if (weapon.weaponId() == null) continue;
            assertNotNull(weapon.weaponDef(), weapon + " must resolve through the registry");
            assertSame(MountClass.MARINE_SECONDARY, weapon.weaponDef().mount,
                    weapon + " is weapon-like special equipment");
        }
        assertEquals(WeaponRegistry.installed().size(), ids.size());
        assertTrue(WeaponRegistry.installed().all().stream()
                .anyMatch(weapon -> weapon.mount == MountClass.MECH_MOUNT));
        assertTrue(WeaponRegistry.installed().all().stream()
                .anyMatch(weapon -> weapon.mount == MountClass.TURRET_MOUNT));
    }

    @Test
    void mechComponentsPointAtRegistryIds() {
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            assertEquals(component.weaponId, component.weaponDef().id,
                    component + " resolves its stable weapon id");
            assertSame(MountClass.MECH_MOUNT, component.weaponDef().mount,
                    component + " resolves only a mech-mount definition");
        }
    }

    @Test
    void rocketMigrationPreservesItsShippedValues() {
        SpecialEquipmentDef rocket = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_1_ID);
        assertEquals(32f, rocket.range(), EPS);
        assertEquals(162f, rocket.damage(), EPS);
        assertEquals(0.85f, rocket.accuracy(), EPS);
        assertEquals(18f, rocket.penetration(), EPS);
        assertEquals(1.5f, rocket.aoeRadius(), EPS);
        assertEquals(50, rocket.wallDamage());
        assertEquals(0.65f, rocket.aimDuration(), EPS);
        assertImpact(rocket.weaponDef(), ImpactKind.HE);
    }

    @Test
    void antiMaterielRifleIsPreciseRegistryOwnedHeavyFire() {
        SpecialEquipmentDef amr = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID);
        assertEquals("weapon.anti-materiel-rifle", amr.weaponDef().id);
        assertEquals(5, amr.startingAmmo());
        assertEquals(70f, amr.damage(), EPS);
        assertTrue(amr.range() > WeaponRegistry.require(WeaponRegistry.DMR_ID).range());
        assertEquals(18f, amr.penetration(), EPS);
        assertEquals(0f, amr.aoeRadius(), EPS);
        assertEquals(0, amr.wallDamage());
        assertNull(amr.projectileSpritePath());
        assertImpact(amr.weaponDef(), ImpactKind.KINETIC);
    }

    @Test
    void fragmentationGrenadeOwnsItsArcAndCompactAntiPersonnelBlast() {
        SpecialEquipmentDef frag = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID);
        assertEquals("weapon.frag-grenade", frag.weaponDef().id);
        assertEquals(8.5f, frag.range(), EPS);
        assertEquals(32f, frag.damage(), EPS);
        assertEquals(2f, frag.penetration(), EPS);
        assertEquals(1.45f, frag.aoeRadius(), EPS);
        assertEquals(1.8f, frag.arcHeight(), EPS);
        assertEquals(0, frag.wallDamage());
        assertTrue(frag.weaponDef().indirectFire);
        assertTrue(frag.weaponDef().interceptableProjectile);
    }

    @Test
    void pulseRifleMatchesItsShippedValues() {
        assertSim(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), 24f, 9.0f, 0.35f, 1.0f, 5f,
                3, 0.09f, 0.30f, 0.4f, 55f);
        assertPresentation(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), new Color(0x80, 0xFF, 0x80),
                ImpactKind.RIFLE, null, 0f, "pulse_laser_fire");
    }

    @Test
    void dronePulseMatchesItsShippedValues() {
        assertSim(WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID), 26f, 7.2f, 0.40f, 1.0f, 5f,
                2, 0.10f, 0.35f, 0.5f, 55f);
        assertPresentation(WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID), new Color(0x60, 0xCF, 0xFF),
                ImpactKind.RIFLE, null, 0f, "pulse_laser_fire");
    }

    /**
     * The grade marking: a tiered entry carries its equipment tier as a
     * {@code -<tier>} suffix on its code, an untiered entry (recruit issue)
     * ignores grade, and a null grade reads as Service.
     */
    @Test
    void aTieredCodeCarriesItsGradeAndAnUntieredOneDoesNot() {
        WeaponDef recruit = WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID);
        assertEquals(recruit.displayName(), recruit.gradedName(EquipmentGrade.SERVICE));
        assertEquals(recruit.displayName(), recruit.gradedName(EquipmentGrade.MASTERWORK),
                "recruit issue is untiered — its designation ignores grade");

        WeaponDef pulse = WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID);
        assertEquals(pulse.displayName() + "-" + EquipmentGrade.MILSPEC.tier, pulse.gradedName(EquipmentGrade.MILSPEC));
        assertEquals(pulse.gradedName(EquipmentGrade.SERVICE), pulse.gradedName((EquipmentGrade) null),
                "a null grade reads as Service");
    }

    private static void assertSim(WeaponDef weapon, float range, float damage,
                                  float accuracy, float cooldown, float penetration,
                                  int burstCount, float burstSpacing,
                                  float accuracyFalloff, float hitSpread,
                                  float roundVelocity) {
        assertEquals(range, weapon.range(), EPS, weapon + " range");
        assertEquals(damage, weapon.damage(), EPS, weapon + " damage");
        assertEquals(accuracy, weapon.accuracy(), EPS, weapon + " accuracy");
        assertEquals(cooldown, weapon.cooldown(), EPS, weapon + " cooldown");
        assertEquals(penetration, weapon.penetration(), EPS, weapon + " penetration");
        assertEquals(burstCount, weapon.burstCount(), weapon + " burst count");
        assertEquals(burstSpacing, weapon.burstSpacing(), EPS, weapon + " burst spacing");
        assertEquals(accuracyFalloff, weapon.accuracyFalloff(), EPS, weapon + " accuracy falloff");
        assertEquals(hitSpread, weapon.hitSpread(), EPS, weapon + " hit spread");
        assertEquals(roundVelocity, weapon.roundVelocity(), EPS, weapon + " round velocity");
    }

    private static void assertPresentation(WeaponDef weapon, Color tracer,
                                           ImpactKind impact, String spritePath,
                                           float visualCells, String fireSound) {
        assertEquals(tracer, weapon.tracerColor(), weapon + " tracer color");
        assertImpact(weapon, impact);
        if (spritePath == null) {
            assertNull(weapon.projectileSpritePath(),
                    weapon + " shares the tinted bolt and must carry no projectile sprite");
        } else {
            assertEquals(spritePath, weapon.projectileSpritePath(), weapon + " projectile sprite");
        }
        assertEquals(visualCells, weapon.projectileVisualCells(), EPS, weapon + " projectile visual size");
        assertEquals(fireSound, weapon.fireSoundId(), weapon + " fire sound");
    }

    private static void assertImpact(WeaponDef weapon, ImpactKind expected) {
        boolean explosive = weapon.fx.hasExplosiveImpact();
        boolean heavy = weapon.fx.hasHeavyImpact();
        boolean kinetic = weapon.fx.hasKineticImpact();
        switch (expected) {
            case RIFLE -> assertEquals(false, explosive || kinetic, weapon.id + " rifle impact");
            case KINETIC -> assertTrue(kinetic, weapon.id + " kinetic impact");
            case HE -> assertEquals(true, explosive && !heavy, weapon.id + " HE impact");
            case CANNON_HE -> assertTrue(heavy, weapon.id + " heavy impact");
        }
    }

    private enum ImpactKind {
        RIFLE,
        KINETIC,
        HE,
        CANNON_HE
    }
}
