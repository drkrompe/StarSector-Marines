package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
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
    void mechWeaponMigrationPreservesShippedValues() {
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_CHAINGUN_ID),
                30f, 5f, 0.55f, 2f, 5f,
                12, 0.06f, 1.2f, 300f, 0.10f, 0f,
                0.6f, 1, 0f, false, false, 1f,
                new Color(0xFF, 0xE8, 0xC0), ImpactKind.KINETIC,
                "graphics/missiles/shell_small_yellow.png", 0.18f, "chaingun_fire");
        assertEquals(1.10f,
                WeaponRegistry.require(WeaponRegistry.MECH_CHAINGUN_ID).tracerTailCells(), EPS);
        WeaponDef pulseLaser = WeaponRegistry.require(WeaponRegistry.MECH_PULSE_LASER_ID);
        assertMech(pulseLaser,
                32f, 18f, 0.72f, 2.1f, 9f,
                8, 0.075f, 0.55f, 320f, 0.10f, 0f,
                0.35f, 3, 0f, false, false, 1f,
                new Color(0xD8, 0xFC, 0xFF), ImpactKind.RIFLE,
                null, 0f, "pulse_laser_fire");
        assertEquals(2f, pulseLaser.beamStyle().coreWidthPx(), EPS);
        assertEquals(7f, pulseLaser.beamStyle().glowWidthPx(), EPS);
        assertEquals(0.12f, pulseLaser.beamStyle().lifetimeSec(), EPS);
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_BASTION_AUTOCANNON_ID),
                34f, 20f, 0.67f, 2.6f, 14f,
                6, 0.14f, 0.45f, 180f, 0.19f, 0f,
                0.45f, 6, 0f, false, false, 1f,
                new Color(0xFF, 0xD0, 0x8A), ImpactKind.KINETIC,
                "graphics/missiles/shell_small_yellow.png", 0.22f,
                "light_autocannon_fire");
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_LINEAR_CANNON_ID),
                32f, 27f, 0.68f, 2.8f, 8f,
                2, 0.12f, 0.35f, 160f, 0.20f, 0f,
                0.35f, 4, 0f, false, false, 1f,
                new Color(0xB8, 0xE8, 0xFF), ImpactKind.KINETIC,
                "graphics/missiles/shell_large_blue.png", 0.20f, "needler_fire");
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_HEAVY_CANNON_ID),
                26f, 45f, 0.76f, 2.5f, 18f,
                1, 0f, 0.12f, 86.666664f, 0.30f, 0f,
                1f, 140, 1.1f, false, false, 1f,
                new Color(0xFF, 0xD0, 0x88), ImpactKind.CANNON_HE,
                "graphics/missiles/shell_hellbore.png", 0.34f, "hellbore_fire");
        WeaponDef heavyCannon = WeaponRegistry.require(WeaponRegistry.MECH_HEAVY_CANNON_ID);
        assertEquals(100f, heavyCannon.contactDamage, EPS);
        assertEquals(18f, heavyCannon.contactPenetration, EPS);
        WeaponDef demolition = WeaponRegistry.require(
                WeaponRegistry.MECH_DEMOLITION_CANNON_ID);
        assertMech(demolition,
                20f, 62f, 0.50f, 5.8f, 16f,
                1, 0f, 1.20f, 62.5f, 0.32f, 0f,
                1.7f, 90, 1.4f, false, false, 1f,
                new Color(0xFF, 0xB4, 0x5A), ImpactKind.CANNON_HE,
                "graphics/missiles/shell_hellbore.png", 0.40f, "hellbore_fire");
        assertEquals(88f, demolition.contactDamage, EPS);
        assertEquals(26f, demolition.contactPenetration, EPS);
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_MUSTER_AUTOGUN_ID),
                26f, 9f, 0.48f, 2.4f, 4f,
                6, 0.12f, 1.35f, 240f, 0.108333f, 0f,
                0.35f, 2, 0f, false, false, 1f,
                new Color(0xFF, 0xD6, 0x9A), ImpactKind.KINETIC,
                "graphics/missiles/shell_small_yellow.png", 0.16f,
                "light_autocannon_fire");
        WeaponDef quarry = WeaponRegistry.require(WeaponRegistry.MECH_QUARRY_BREAKER_ID);
        assertMech(quarry,
                17f, 34f, 0.42f, 5.5f, 8f,
                1, 0f, 1.35f, 53.125f, 0.32f, 0f,
                1.1f, 55, 1f, false, false, 1f,
                new Color(0xFF, 0xBE, 0x70), ImpactKind.CANNON_HE,
                "graphics/missiles/shell_hellbore.png", 0.34f, "hellbore_fire");
        assertEquals(50f, quarry.contactDamage, EPS);
        assertEquals(12f, quarry.contactPenetration, EPS);
        // The anti-armor racks: a contact warhead over a small splash (mechs-nouns.md, Missile warheads).
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_SRM_POD_ID),
                18f, 10f, 0.55f, 5.5f, 6f,
                4, 0.10f, 0f, 32.727272f, 0.55f, 0f,
                0.6f, 25, 1.3f, true, false, 1f,
                new Color(0xFF, 0xC0, 0x80), ImpactKind.HE,
                "graphics/missiles/missile_SRM.png", 0.40f, "annihilator_fire");
        assertEquals(66f, WeaponRegistry.require(WeaponRegistry.MECH_SRM_POD_ID).contactDamage, EPS);
        assertEquals(30f, WeaponRegistry.require(WeaponRegistry.MECH_SRM_POD_ID).contactPenetration, EPS);
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_LRM_ARTILLERY_ID),
                40f, 15f, 0.55f, 9f, 6f,
                5, 0.11f, 1.5f, 28.571428f, 1.40f, 5f,
                1f, 40, 2f, true, true, 0.55f,
                new Color(0xC8, 0xD8, 0xFF), ImpactKind.HE,
                "graphics/missiles/missile_LRM.png", 0.65f, "pilum_lrm_fire");
        assertEquals(95f, WeaponRegistry.require(WeaponRegistry.MECH_LRM_ARTILLERY_ID).contactDamage, EPS);
        assertEquals(30f, WeaponRegistry.require(WeaponRegistry.MECH_LRM_ARTILLERY_ID).contactPenetration, EPS);
        // The single-warhead racks they replaced, kept for the missileVariants=false control.
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_SRM_LEGACY_ID),
                18f, 49.5f, 0.55f, 5.5f, 14f,
                4, 0.10f, 0f, 32.727272f, 0.55f, 0f,
                1.3f, 25, 1.3f, true, false, 1f,
                new Color(0xFF, 0xC0, 0x80), ImpactKind.HE,
                "graphics/missiles/missile_SRM.png", 0.40f, "annihilator_fire");
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_LRM_LEGACY_ID),
                40f, 81f, 0.55f, 9f, 16f,
                5, 0.11f, 1.5f, 28.571428f, 1.40f, 5f,
                2f, 40, 2f, true, true, 0.55f,
                new Color(0xC8, 0xD8, 0xFF), ImpactKind.HE,
                "graphics/missiles/missile_LRM.png", 0.65f, "pilum_lrm_fire");

        WeaponDef laser = WeaponRegistry.require(WeaponRegistry.MECH_SHOULDER_LASER_ID);
        assertMech(laser,
                36f, 24f, 0.82f, 7f, 10f,
                1, 0f, 0.08f, 360f, 0.10f, 0f,
                1.5f, 100, 0.75f, false, false, 1f,
                new Color(0xD8, 0xFC, 0xFF), ImpactKind.CANNON_HE,
                null, 0f, "pulse_laser_fire");
        assertEquals(150f, laser.contactDamage, EPS);
        assertEquals(40f, laser.contactPenetration, EPS);
        assertEquals(1, laser.bodyPenetrations);

        WeaponDef thermal = WeaponRegistry.require(WeaponRegistry.MECH_THERMAL_LANCE_ID);
        assertMech(thermal,
                22f, 38f, 0.78f, 4.8f, 14f,
                1, 0f, 0.25f, 300f, 0.10f, 0f,
                1f, 35, 0.8f, false, false, 1f,
                new Color(0xFF, 0xF0, 0xB0), ImpactKind.CANNON_HE,
                null, 0f, "plasma_cannon_fire");
        assertEquals(70f, thermal.contactDamage, EPS);
        assertEquals(24f, thermal.contactPenetration, EPS);
        assertEquals(3f, thermal.beamStyle().coreWidthPx(), EPS);
        assertEquals(10f, thermal.beamStyle().glowWidthPx(), EPS);
        assertEquals(0.38f, thermal.beamStyle().lifetimeSec(), EPS);
    }

    @Test
    void rocketMigrationPreservesItsShippedValues() {
        SpecialEquipmentDef rocket = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID);
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

    private static void assertMech(WeaponDef weapon,
                                   float range, float damage, float accuracy,
                                   float cooldown, float penetration,
                                   int burstCount, float burstSpacing,
                                   float hitSpread, float roundVelocity,
                                   float flightSec, float arcHeight,
                                   float aoeRadius, int wallDamage,
                                   float wallDamageRadius, boolean authoredTrail,
                                   boolean indirectFire, float noLosAccuracyMult,
                                   Color tracer, ImpactKind impact,
                                   String projectileSpritePath,
                                   float projectileVisualCells,
                                   String fireSoundId) {
        assertMech(weapon, range, damage, accuracy, cooldown, penetration,
                burstCount, burstSpacing, hitSpread, roundVelocity, flightSec,
                arcHeight, aoeRadius, wallDamage, wallDamageRadius, authoredTrail,
                authoredTrail, indirectFire, noLosAccuracyMult, tracer, impact,
                projectileSpritePath, projectileVisualCells, fireSoundId);
    }

    private static void assertMech(WeaponDef weapon,
                                   float range, float damage, float accuracy,
                                   float cooldown, float penetration,
                                   int burstCount, float burstSpacing,
                                   float hitSpread, float roundVelocity,
                                   float flightSec, float arcHeight,
                                   float aoeRadius, int wallDamage,
                                   float wallDamageRadius, boolean authoredTrail,
                                   boolean boostRamp, boolean indirectFire,
                                   float noLosAccuracyMult,
                                   Color tracer, ImpactKind impact,
                                   String projectileSpritePath,
                                   float projectileVisualCells,
                                   String fireSoundId) {
        assertEquals(range, weapon.range(), EPS, weapon + " range");
        assertEquals(damage, weapon.damage(), EPS, weapon + " damage");
        assertEquals(accuracy, weapon.accuracy(), EPS, weapon + " accuracy");
        assertEquals(cooldown, weapon.cooldown(), EPS, weapon + " cooldown");
        assertEquals(penetration, weapon.penetration(), EPS, weapon + " penetration");
        assertEquals(burstCount, weapon.burstCount(), weapon + " burst count");
        assertEquals(burstSpacing, weapon.burstSpacing(), EPS, weapon + " burst spacing");
        assertEquals(hitSpread, weapon.hitSpread(), EPS, weapon + " hit spread");
        assertEquals(roundVelocity, weapon.roundVelocity(), EPS, weapon + " round velocity");
        assertEquals(flightSec, weapon.flightSec, EPS, weapon + " flight time");
        assertEquals(arcHeight, weapon.arcHeight, EPS, weapon + " arc height");
        assertEquals(aoeRadius, weapon.aoeRadius, EPS, weapon + " blast radius");
        assertEquals(wallDamage, weapon.wallDamage, weapon + " wall damage");
        assertEquals(wallDamageRadius, weapon.wallDamageRadius, EPS,
                weapon + " wall damage radius");
        assertEquals(authoredTrail, !weapon.fx.layers(FxSlot.TRAIL).isEmpty(),
                weapon + " authored trail");
        assertEquals(authoredTrail, weapon.interceptableProjectile,
                weapon + " interceptable projectile");
        assertEquals(boostRamp, weapon.boostRamp, weapon + " boost ramp");
        assertEquals(indirectFire, weapon.indirectFire, weapon + " indirect fire");
        assertEquals(noLosAccuracyMult, weapon.noLosAccuracyMult, EPS,
                weapon + " no-LOS accuracy");
        assertEquals(tracer, weapon.tracerColor(), weapon + " tracer");
        assertImpact(weapon, impact);
        assertEquals(projectileSpritePath, weapon.projectileSpritePath(),
                weapon + " projectile sprite");
        assertEquals(projectileVisualCells, weapon.projectileVisualCells(), EPS,
                weapon + " projectile visual size");
        assertEquals(fireSoundId, weapon.fireSoundId(), weapon + " fire sound");
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
