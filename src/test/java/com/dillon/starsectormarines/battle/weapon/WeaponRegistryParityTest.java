package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.marine.SpecialActivation;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code data/marines/marine-weapons.weapon.json} field-for-field against
 * the values {@link MarineWeapon} carried before the catalog moved to data.
 * This is the artifact that makes a substrate migration reviewable: the JSON is
 * correct if and only if every number below still matches, and the expectations
 * are written as literals rather than read back from the registry so a typo in
 * the data cannot quietly validate itself.
 *
 * <p><b>Lifetime.</b> Delete this test when {@code moddable-weapons} W4 retires
 * the enum — at that point there is no second source to be in parity with, and
 * these literals become an unmaintained copy of the shipping catalog.
 */
class WeaponRegistryParityTest {

    private static final float EPS = 1e-6f;

    @Test
    void everyEnumConstantResolvesToExactlyOneDef() {
        Set<String> ids = new HashSet<>();
        for (MarineWeapon weapon : MarineWeapon.values()) {
            assertNotNull(weapon.def(), weapon + " must resolve through the registry");
            assertTrue(ids.add(weapon.id), "duplicate id " + weapon.id);
            assertSame(MountClass.MARINE_PRIMARY, weapon.def().mount,
                    weapon + " is a marine primary");
        }
        for (MarineSecondary weapon : MarineSecondary.values()) {
            if (weapon.activation() == SpecialActivation.UTILITY_SMOKE) continue;
            assertNotNull(weapon.def(), weapon + " must resolve through the registry");
            assertTrue(ids.add(weapon.def().id), "duplicate id " + weapon.def().id);
            assertSame(MountClass.MARINE_SECONDARY, weapon.def().mount,
                    weapon + " is weapon-like special equipment");
        }
        long weaponLikeSpecials = Arrays.stream(MarineSecondary.values())
                .filter(weapon -> weapon.activation() != SpecialActivation.UTILITY_SMOKE)
                .count();
        assertEquals(MarineWeapon.values().length + weaponLikeSpecials,
                WeaponRegistry.installed().size(),
                "the catalog holds exactly the shipped handheld weapons");
    }

    @Test
    void rocketMigrationPreservesItsShippedValues() {
        MarineSecondary rocket = MarineSecondary.ROCKET_LAUNCHER;
        assertEquals(32f, rocket.range(), EPS);
        assertEquals(162f, rocket.damage(), EPS);
        assertEquals(0.85f, rocket.accuracy(), EPS);
        assertEquals(3.5f, rocket.vsTurretMult(), EPS);
        assertEquals(1.5f, rocket.aoeRadius(), EPS);
        assertEquals(50, rocket.wallDamage());
        assertEquals(0.65f, rocket.aimDuration(), EPS);
        assertSame(ImpactProfile.HE, rocket.impactProfile());
    }

    @Test
    void antiMaterielRifleIsPreciseRegistryOwnedHeavyFire() {
        MarineSecondary amr = MarineSecondary.ANTI_MATERIEL_RIFLE;
        assertEquals("weapon.anti-materiel-rifle", amr.def().id);
        assertEquals(4, amr.startingAmmo());
        assertTrue(amr.range() > MarineWeapon.DMR.range());
        assertTrue(amr.vsTurretMult() > 1f);
        assertEquals(0f, amr.aoeRadius(), EPS);
        assertEquals(0, amr.wallDamage());
        assertNull(amr.projectileSpritePath());
        assertSame(ImpactProfile.KINETIC, amr.impactProfile());
    }

    @Test
    void fieldRifleMatchesItsShippedValues() {
        assertSim(MarineWeapon.FIELD_RIFLE, 22f, 14.0f, 0.28f, 1.15f, 0.25f,
                1, 0f, 0.42f, 0.75f, 48f);
        assertPresentation(MarineWeapon.FIELD_RIFLE, new Color(0xFF, 0xD0, 0x88),
                ImpactProfile.RIFLE, "graphics/missiles/shell_small_yellow.png", 0.18f,
                "light_autocannon_fire");
        assertEquals("Field Rifle", MarineWeapon.FIELD_RIFLE.displayName());
        assertEquals("Rook", MarineWeapon.FIELD_RIFLE.modelName());
    }

    @Test
    void pulseRifleMatchesItsShippedValues() {
        assertSim(MarineWeapon.PULSE_RIFLE, 24f, 9.0f, 0.35f, 1.0f, 0.30f,
                3, 0.09f, 0.30f, 0.4f, 55f);
        assertPresentation(MarineWeapon.PULSE_RIFLE, new Color(0x80, 0xFF, 0x80),
                ImpactProfile.RIFLE, null, 0f, "pulse_laser_fire");
        assertEquals("Pulse Rifle", MarineWeapon.PULSE_RIFLE.displayName());
        assertEquals("Lancer", MarineWeapon.PULSE_RIFLE.modelName());
    }

    @Test
    void machineGunMatchesItsShippedValues() {
        assertSim(MarineWeapon.SMG, 16f, 5.4f, 0.50f, 0.50f, 0.30f,
                3, 0.07f, 0.60f, 1.4f, 45f);
        assertPresentation(MarineWeapon.SMG, new Color(0xFF, 0xE8, 0xC0),
                ImpactProfile.RIFLE, "graphics/missiles/shell_small_yellow.png", 0.15f,
                "light_machinegun_fire");
        assertEquals("Light Machine Gun", MarineWeapon.SMG.displayName());
        assertEquals("Rattler", MarineWeapon.SMG.modelName());
    }

    @Test
    void railgunMatchesItsShippedValues() {
        assertSim(MarineWeapon.DMR, 32f, 18.0f, 0.55f, 1.10f, 0.40f,
                1, 0f, 0.10f, 0.15f, 110f);
        assertPresentation(MarineWeapon.DMR, new Color(0xE0, 0xF0, 0xFF),
                ImpactProfile.KINETIC, null, 0f, "railgun_fire");
        assertEquals("Railgun", MarineWeapon.DMR.displayName());
        assertEquals("Longbow", MarineWeapon.DMR.modelName());
    }

    @Test
    void dronePulseMatchesItsShippedValues() {
        assertSim(MarineWeapon.DRONE_PULSE, 26f, 7.2f, 0.40f, 1.0f, 0.30f,
                2, 0.10f, 0.35f, 0.5f, 55f);
        assertPresentation(MarineWeapon.DRONE_PULSE, new Color(0x60, 0xCF, 0xFF),
                ImpactProfile.RIFLE, null, 0f, "pulse_laser_fire");
        assertEquals("Drone Pulse Laser", MarineWeapon.DRONE_PULSE.displayName());
        assertEquals("Wisp", MarineWeapon.DRONE_PULSE.modelName());
    }

    /**
     * Catalog naming used to be two switch statements over the enum. The
     * behavior it encoded — a tiered prefix for everything except recruit
     * issue, which is always FR-1 — is now {@code designation} plus
     * {@code designationTiered}, and this pins that the swap changed nothing.
     */
    @Test
    void catalogNamingSurvivedTheSwitchToData() {
        assertEquals("FR-1", MarineWeapon.FIELD_RIFLE.designation(EquipmentGrade.SERVICE));
        assertEquals("FR-1", MarineWeapon.FIELD_RIFLE.designation(EquipmentGrade.MASTERWORK),
                "recruit issue is untiered — its designation ignores grade");
        assertEquals("PLS-2", MarineWeapon.PULSE_RIFLE.designation(EquipmentGrade.SERVICE));
        assertEquals("LMG-3", MarineWeapon.SMG.designation(EquipmentGrade.MILSPEC));
        assertEquals("RG-4", MarineWeapon.DMR.designation(EquipmentGrade.MASTERWORK));
        assertEquals("DPLS-1", MarineWeapon.DRONE_PULSE.designation(EquipmentGrade.SURPLUS));

        assertEquals("PLS-2 Lancer", MarineWeapon.PULSE_RIFLE.catalogName(EquipmentGrade.SERVICE));
        assertEquals("FR-1 Rook", MarineWeapon.FIELD_RIFLE.catalogName(null),
                "a null grade falls back to tier 1, as the switch did");
    }

    private static void assertSim(MarineWeapon weapon, float range, float damage,
                                  float accuracy, float cooldown, float vsHardened,
                                  int burstCount, float burstSpacing,
                                  float accuracyFalloff, float hitSpread,
                                  float roundVelocity) {
        assertEquals(range, weapon.range(), EPS, weapon + " range");
        assertEquals(damage, weapon.damage(), EPS, weapon + " damage");
        assertEquals(accuracy, weapon.accuracy(), EPS, weapon + " accuracy");
        assertEquals(cooldown, weapon.cooldown(), EPS, weapon + " cooldown");
        assertEquals(vsHardened, weapon.vsTurretMult(), EPS, weapon + " vs-hardened multiplier");
        assertEquals(burstCount, weapon.burstCount(), weapon + " burst count");
        assertEquals(burstSpacing, weapon.burstSpacing(), EPS, weapon + " burst spacing");
        assertEquals(accuracyFalloff, weapon.accuracyFalloff(), EPS, weapon + " accuracy falloff");
        assertEquals(hitSpread, weapon.hitSpread(), EPS, weapon + " hit spread");
        assertEquals(roundVelocity, weapon.roundVelocity(), EPS, weapon + " round velocity");
    }

    private static void assertPresentation(MarineWeapon weapon, Color tracer,
                                           ImpactProfile impact, String spritePath,
                                           float visualCells, String fireSound) {
        assertEquals(tracer, weapon.tracerColor(), weapon + " tracer color");
        assertSame(impact, weapon.impactProfile(), weapon + " impact profile");
        if (spritePath == null) {
            assertNull(weapon.projectileSpritePath(),
                    weapon + " shares the tinted bolt and must carry no projectile sprite");
        } else {
            assertEquals(spritePath, weapon.projectileSpritePath(), weapon + " projectile sprite");
        }
        assertEquals(visualCells, weapon.projectileVisualCells(), EPS, weapon + " projectile visual size");
        assertEquals(fireSound, weapon.fireSoundId(), weapon + " fire sound");
    }
}
