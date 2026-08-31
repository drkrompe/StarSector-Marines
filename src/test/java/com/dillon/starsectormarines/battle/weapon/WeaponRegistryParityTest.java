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
                30f, 13.5f, 0.55f, 2f, 5f,
                12, 0.06f, 1.2f, 300f, 0.10f, 0f,
                0.6f, 3, 0f, false, false, 1f,
                new Color(0xFF, 0xE8, 0xC0), ImpactKind.KINETIC,
                "graphics/missiles/shell_small_yellow.png", 0.18f, "chaingun_fire");
        assertEquals(0.55f,
                WeaponRegistry.require(WeaponRegistry.MECH_CHAINGUN_ID).tracerTailCells(), EPS);
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_LINEAR_CANNON_ID),
                32f, 27f, 0.68f, 2.8f, 8f,
                2, 0.12f, 0.35f, 160f, 0.20f, 0f,
                0.35f, 4, 0f, false, false, 1f,
                new Color(0xB8, 0xE8, 0xFF), ImpactKind.KINETIC,
                "graphics/missiles/shell_large_blue.png", 0.20f, "needler_fire");
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_HEAVY_CANNON_ID),
                26f, 45f, 0.76f, 2.5f, 18f,
                1, 0f, 0.12f, 86.666664f, 0.30f, 0f,
                1f, 18, 0.9f, false, false, 1f,
                new Color(0xFF, 0xD0, 0x88), ImpactKind.CANNON_HE,
                "graphics/missiles/shell_hellbore.png", 0.34f, "hellbore_fire");
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_SRM_POD_ID),
                18f, 49.5f, 0.55f, 5.5f, 14f,
                4, 0.10f, 0f, 32.727272f, 0.55f, 0f,
                1.3f, 25, 1.3f, true, false, 1f,
                new Color(0xFF, 0xC0, 0x80), ImpactKind.HE,
                "graphics/missiles/missile_SRM.png", 0.40f, "annihilator_fire");
        assertMech(WeaponRegistry.require(WeaponRegistry.MECH_LRM_ARTILLERY_ID),
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
        assertEquals(4, amr.startingAmmo());
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
    void fieldRifleMatchesItsShippedValues() {
        assertSim(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), 22f, 14.0f, 0.28f, 1.15f, 7f,
                1, 0f, 0.42f, 0.75f, 48f);
        assertPresentation(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), new Color(0xFF, 0xD0, 0x88),
                ImpactKind.RIFLE, "graphics/missiles/shell_small_yellow.png", 0.18f,
                "light_autocannon_fire");
        assertEquals(0.65f, WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).tracerTailCells(), EPS);
        assertEquals("Field Rifle", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).displayName());
        assertEquals("Rook", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).modelName());
    }

    @Test
    void pulseRifleMatchesItsShippedValues() {
        assertSim(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), 24f, 9.0f, 0.35f, 1.0f, 5f,
                3, 0.09f, 0.30f, 0.4f, 55f);
        assertPresentation(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), new Color(0x80, 0xFF, 0x80),
                ImpactKind.RIFLE, null, 0f, "pulse_laser_fire");
        assertEquals("Pulse Rifle", WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).displayName());
        assertEquals("Lancer", WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).modelName());
    }

    @Test
    void shredderCarbineOwnsTheCloseContactFlechetteRole() {
        assertSim(WeaponRegistry.require(WeaponRegistry.SMG_ID), 14f, 3.0f, 0.68f, 0.75f, 1f,
                1, 0f, 0.75f, 1.7f, 45f);
        assertEquals(6, WeaponRegistry.require(WeaponRegistry.SMG_ID).projectilesPerShot());
        assertPresentation(WeaponRegistry.require(WeaponRegistry.SMG_ID), new Color(0xFF, 0xE8, 0xC0),
                ImpactKind.RIFLE, "graphics/missiles/shell_small_yellow.png", 0.15f,
                "light_machinegun_fire");
        assertEquals(0.40f, WeaponRegistry.require(WeaponRegistry.SMG_ID).tracerTailCells(), EPS);
        assertEquals("Shredder Carbine", WeaponRegistry.require(WeaponRegistry.SMG_ID).displayName());
        assertEquals("Rattler", WeaponRegistry.require(WeaponRegistry.SMG_ID).modelName());
    }

    @Test
    void squadAutomaticOwnsTheSustainedSlugRole() {
        assertSim(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), 22f, 5.8f, 0.34f, 1.60f, 5f,
                8, 0.10f, 0.45f, 0.9f, 52f);
        assertEquals(1, WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID).projectilesPerShot());
        assertPresentation(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID),
                new Color(0xFF, 0xD6, 0xA0), ImpactKind.RIFLE,
                "graphics/missiles/shell_small_yellow.png", 0.16f,
                "light_machinegun_fire");
        assertEquals(0.50f,
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID).tracerTailCells(), EPS);
        assertEquals("Squad Automatic", WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID).displayName());
        assertEquals("Stalwart", WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID).modelName());
    }

    @Test
    void railgunMatchesItsShippedValues() {
        assertSim(WeaponRegistry.require(WeaponRegistry.DMR_ID), 32f, 18.0f, 0.55f, 1.10f, 11f,
                1, 0f, 0.10f, 0.15f, 110f);
        assertPresentation(WeaponRegistry.require(WeaponRegistry.DMR_ID), new Color(0xE0, 0xF0, 0xFF),
                ImpactKind.KINETIC, null, 0f, "railgun_fire");
        assertEquals("Railgun", WeaponRegistry.require(WeaponRegistry.DMR_ID).displayName());
        assertEquals("Longbow", WeaponRegistry.require(WeaponRegistry.DMR_ID).modelName());
    }

    @Test
    void dronePulseMatchesItsShippedValues() {
        assertSim(WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID), 26f, 7.2f, 0.40f, 1.0f, 5f,
                2, 0.10f, 0.35f, 0.5f, 55f);
        assertPresentation(WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID), new Color(0x60, 0xCF, 0xFF),
                ImpactKind.RIFLE, null, 0f, "pulse_laser_fire");
        assertEquals("Drone Pulse Laser", WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID).displayName());
        assertEquals("Wisp", WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID).modelName());
    }

    /**
     * Catalog naming used to be two switch statements over the enum. The
     * behavior it encoded — a tiered prefix for everything except recruit
     * issue, which is always FR-1 — is now {@code designation} plus
     * {@code designationTiered}, and this pins that the swap changed nothing.
     */
    @Test
    void catalogNamingSurvivedTheSwitchToData() {
        assertEquals("FR-1", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).designation(EquipmentGrade.SERVICE));
        assertEquals("FR-1", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).designation(EquipmentGrade.MASTERWORK),
                "recruit issue is untiered — its designation ignores grade");
        assertEquals("PLS-2", WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).designation(EquipmentGrade.SERVICE));
        assertEquals("SHD-3", WeaponRegistry.require(WeaponRegistry.SMG_ID).designation(EquipmentGrade.MILSPEC));
        assertEquals("SA-2", WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID).designation(EquipmentGrade.SERVICE));
        assertEquals("RG-4", WeaponRegistry.require(WeaponRegistry.DMR_ID).designation(EquipmentGrade.MASTERWORK));
        assertEquals("DPLS-1", WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID).designation(EquipmentGrade.SURPLUS));

        assertEquals("PLS-2 Lancer", WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).catalogName(EquipmentGrade.SERVICE));
        assertEquals("FR-1 Rook", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).catalogName(null),
                "a null grade falls back to tier 1, as the switch did");
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
        assertEquals(authoredTrail, weapon.boostRamp, weapon + " boost ramp");
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
