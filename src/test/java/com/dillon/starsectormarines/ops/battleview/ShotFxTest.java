package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.ops.battleview.ShotFx.Bolt;
import com.dillon.starsectormarines.ops.battleview.ShotFx.Sprite;
import com.dillon.starsectormarines.ops.battleview.ShotFx.Tracer;
import com.dillon.starsectormarines.render2d.ContrailStyle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link ShotFx} composition against the installed weapon sources it
 * derives from, replacing the old per-carrier cascade, so
 * these assert the derivation stays faithful (and carrier-agnostic) as weapons are
 * added. The visible-round S3 assertions additionally pin traveling-bolt
 * derivation and the generated white-base asset contract.
 */
public class ShotFxTest {

    private static ShotEvent turretShot(StructureDef structure) {
        return new ShotEvent(0, 0, 1, 1, true, Faction.DEFENDER, 0.15f, structure);
    }

    private static ShotEvent shot(StructureDef t, WeaponDef mw,
                                  SpecialEquipmentDef ms, WeaponDef mech) {
        return new ShotEvent(0, 0, 1, 1, true, Faction.MARINE, 0.15f, t, mw, ms, mech);
    }

    @Test
    public void everySourceResolvesToANonNullComposition() {
        for (StructureDef structure : TurretCatalogRegistry.installed().structures()) {
            assertNotNull(ShotFx.of(turretShot(structure)), "turret " + structure.id);
        }
        for (WeaponDef w : WeaponRegistry.installed().all()) {
            if (w.mount == MountClass.MARINE_PRIMARY) {
                assertNotNull(ShotFx.of(shot(null, w, null, null)), "primary " + w);
            }
        }
        for (SpecialEquipmentDef w : SpecialEquipmentRegistry.installed().all()) {
            if (w.weaponId() != null) {
                assertNotNull(ShotFx.of(shot(null, null, w, null)), "secondary " + w);
            }
        }
        for (WeaponDef w : WeaponRegistry.installed().all()) {
            if (w.mount == MountClass.MECH_MOUNT) {
                assertNotNull(ShotFx.of(shot(null, null, null, w)), "mech " + w.id);
            }
        }
        // No weapon source (detonations / legacy callers) → faction-default tracer.
        ShotEvent bare = new ShotEvent(0, 0, 1, 1, true, Faction.MARINE, 0.15f);
        ShotFx fx = ShotFx.of(bare);
        assertInstanceOf(Tracer.class, fx.body());
        assertNull(((Tracer) fx.body()).color(), "no-source tracer defers color to the faction default");
        assertFalse(fx.travels(), "full-line tracer impacts at fire time");
        assertNoTrailsArcOrContrail(fx);
    }

    @Test
    public void turretBodiesKeepBallisticsWhileParticlesComeFromAuthoredFx() {
        for (StructureDef structure : TurretCatalogRegistry.installed().structures()) {
            WeaponDef weapon = structure.mount.weapon;
            ShotFx fx = ShotFx.of(turretShot(structure));
            Sprite body = assertSprite(fx, "turret " + structure.id);
            assertEquals(weapon.projectileSpritePath, body.spritePath(), "sprite path for " + structure.id);
            assertEquals(weapon.projectileVisualCells, body.visualCells(), 0f, "visualCells for " + structure.id);
            assertEquals(weapon.arcHeight, fx.arcHeight(), 0f, "arcHeight for " + structure.id);
            assertEquals(weapon.boostRamp, fx.boostRamp(), "boostRamp for " + structure.id);
            assertTrue(fx.travels(), "turret body travels: " + structure.id);
            assertFalse(weapon.fx.layers(FxSlot.IMPACT).isEmpty(),
                    "turret impact particles are authored: " + structure.id);

            if (TurretCatalogRegistry.VULCAN_STRUCTURE_ID.equals(structure.id)) {
                assertTracerTail(fx, weapon, 0.45f, "Vulcan");
            } else if (TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID.equals(structure.id)) {
                assertTracerTail(fx, weapon, 0.55f, "heavy MG");
            } else {
                assertNull(fx.tracerTail(), "turret has no authored tracer tail: " + structure.id);
            }

            if (TurretCatalogRegistry.LOCUST_STRUCTURE_ID.equals(structure.id)) {
                assertSame(ContrailStyle.MISSILE_SMOKE, fx.contrail());
            } else {
                assertNull(fx.contrail(), "non-missile turret contrail: " + structure.id);
            }
        }
    }

    @Test
    public void locustBoostsAndCarriesAnAuthoredTrail() {
        StructureDef locust = TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.LOCUST_STRUCTURE_ID);
        ShotFx fx = ShotFx.of(turretShot(locust));
        assertTrue(fx.boostRamp(), "Locust boosts");
        assertSame(ContrailStyle.MISSILE_SMOKE, fx.contrail(),
                "Locust weapon data selects its widening missile ribbon");
        assertFalse(locust.mount.weapon.fx.layers(FxSlot.LAUNCH).isEmpty(),
                "Locust authored data owns its directional launch backblast");
        assertFalse(locust.mount.weapon.fx.layers(FxSlot.TRAIL).isEmpty(),
                "Locust authored data owns the engine-flame trail composition");
    }

    @Test
    public void marinePrimariesUseDistinctTravelingBodyFamilies() {
        for (WeaponDef w : WeaponRegistry.installed().all()) {
            if (w.mount != MountClass.MARINE_PRIMARY) continue;
            ShotFx fx = ShotFx.of(shot(null, w, null, null));
            if (w.projectileSpritePath() != null) {
                Sprite body = assertSprite(fx, "primary " + w);
                assertEquals(w.projectileSpritePath(), body.spritePath(), "sprite path for " + w);
                assertEquals(w.projectileVisualCells(), body.visualCells(), 0f, "visualCells for " + w);
            } else {
                assertInstanceOf(Bolt.class, fx.body(), "primary should bolt: " + w);
                Bolt bolt = (Bolt) fx.body();
                assertSame(w.tracerColor(), bolt.color(), "bolt color for " + w);
                BoltExpectation expected;
                if (WeaponRegistry.PULSE_RIFLE_ID.equals(w.id)) {
                    expected = new BoltExpectation(ShotFx.PULSE_BOLT_SPRITE_PATH, 1.0f, 0.25f);
                } else if (WeaponRegistry.DMR_ID.equals(w.id)) {
                    expected = new BoltExpectation(ShotFx.RAIL_NEEDLE_SPRITE_PATH, 1.8f, 0.16f);
                } else if (WeaponRegistry.DRONE_PULSE_ID.equals(w.id)) {
                    expected = new BoltExpectation(ShotFx.DRONE_DART_SPRITE_PATH, 0.65f, 0.16f);
                } else {
                    throw new AssertionError("sprite-backed primary reached bolt assertion: " + w);
                }
                assertEquals(expected.spritePath(), bolt.spritePath(), "bolt path for " + w);
                assertEquals(expected.lengthCells(), bolt.lengthCells(), 0f, "bolt length for " + w);
                assertEquals(expected.widthCells(), bolt.widthCells(), 0f, "bolt width for " + w);
            }
            if (WeaponRegistry.STARTER_PRIMARY_ID.equals(w.id)) {
                assertNotNull(fx.tracerTail(), "field rifle carries a short tracer tail");
                assertEquals(0.65f, fx.tracerTail().lengthCells(), 0f);
                assertSame(w.tracerColor(), fx.tracerTail().color());
            } else if (WeaponRegistry.SMG_ID.equals(w.id)) {
                assertNotNull(fx.tracerTail(), "SMG flechettes carry compact tracer tails");
                assertEquals(0.40f, fx.tracerTail().lengthCells(), 0f);
                assertSame(w.tracerColor(), fx.tracerTail().color());
            } else if (WeaponRegistry.SQUAD_AUTOMATIC_ID.equals(w.id)) {
                assertTracerTail(fx, w, 0.50f, "squad automatic");
            } else {
                assertNull(fx.tracerTail(), "primary has no authored tracer tail: " + w);
            }
            assertTrue(fx.travels(), "every primary now has a traveling body: " + w);
            assertNoTrailsArcOrContrail(fx);
        }
    }

    @Test
    public void boltFamiliesExposeTheDistinctTextureSetForCacheLoading() {
        assertEquals(Set.of(
                ShotFx.PULSE_BOLT_SPRITE_PATH,
                ShotFx.RAIL_NEEDLE_SPRITE_PATH,
                ShotFx.DRONE_DART_SPRITE_PATH), ShotFx.boltSpritePaths());
    }

    @Test
    public void dmrNeedleUsesAHighContrastPaleBlueTint() {
        assertEquals(new Color(0xE0, 0xF0, 0xFF), WeaponRegistry.require(WeaponRegistry.DMR_ID).tracerColor());
    }

    @Test
    public void generatedPulseBoltAssetIsTransparentWhiteBaseAtRuntimeDimensions() throws Exception {
        Path path = Path.of("mod/graphics/fx/round_bolt.png");
        assertTrue(Files.isRegularFile(path), "generated bolt asset must ship in mod graphics");
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, "bolt PNG must decode");
        assertEquals(64, image.getWidth());
        assertEquals(256, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha(), "bolt must preserve alpha transparency");
        assertEquals(0, image.getRGB(0, 0) >>> 24, "corner must be transparent");

        boolean hasVisiblePixel = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    hasVisiblePixel = true;
                    int argb = image.getRGB(x, y);
                    int red = argb >>> 16 & 0xFF;
                    int green = argb >>> 8 & 0xFF;
                    int blue = argb & 0xFF;
                    assertEquals(red, green, "visible bolt pixel must be neutral grayscale");
                    assertEquals(red, blue, "visible bolt pixel must be neutral grayscale");
                }
            }
        }
        assertTrue(hasVisiblePixel, "bolt asset must not be fully transparent");
    }

    @Test
    public void marineSecondariesDeriveProjectileBodiesFromTheirWeaponDefinitions() {
        for (SpecialEquipmentDef w : SpecialEquipmentRegistry.installed().all()) {
            if (w.weaponId() == null) continue;
            ShotFx fx = ShotFx.of(shot(null, null, w, null));
            if (w.projectileSpritePath() != null) {
                Sprite body = assertSprite(fx, "secondary " + w);
                assertEquals(w.projectileSpritePath(), body.spritePath(), "sprite path for " + w);
                assertEquals(w.projectileVisualCells(), body.visualCells(), 0f, "visualCells for " + w);
            } else {
                assertInstanceOf(Bolt.class, fx.body(), "precision secondary " + w);
            }
            if (w.activation() == SpecialActivation.DIRECT_EXPLOSIVE) {
                assertSame(ContrailStyle.MISSILE_SMOKE, fx.contrail());
            } else {
                assertNull(fx.contrail());
            }
            assertTrue(fx.travels(), "secondary body travels: " + w);
            assertEquals(w.arcHeight(), fx.arcHeight(), 0f, "arcHeight for " + w);
            assertEquals(w.weaponDef().boostRamp, fx.boostRamp(), "boostRamp for " + w);
            assertTrue(w.weaponDef().fx.layers(FxSlot.TRAIL).isEmpty(),
                    "secondary trail remains represented by its contrail ribbon");
        }
    }

    @Test
    public void mechWeaponsCarryAuthoredProjectileOrBeamPresentation() {
        for (WeaponDef w : WeaponRegistry.installed().all()) {
            if (w.mount != MountClass.MECH_MOUNT) continue;
            ShotFx fx = ShotFx.of(shot(null, null, null, w));
            if (w.projectileSpritePath() != null) {
                Sprite body = assertSprite(fx, "mech " + w);
                assertEquals(w.projectileSpritePath(), body.spritePath(), "sprite path for " + w);
                assertEquals(w.projectileVisualCells(), body.visualCells(), 0f, "visualCells for " + w);
                assertTrue(fx.travels(), "mech projectile travels: " + w);
            } else {
                assertInstanceOf(Tracer.class, fx.body(), "null projectile art authors a beam");
                assertEquals(w.tracerColor, ((Tracer) fx.body()).color());
                assertFalse(fx.travels(), "mech beam is drawn across the resolved lane");
            }
            assertEquals(w.arcHeight, fx.arcHeight(), 0f, "arcHeight for " + w);
            boolean expectedTrail = WeaponRegistry.MECH_SRM_POD_ID.equals(w.id)
                    || WeaponRegistry.MECH_LRM_ARTILLERY_ID.equals(w.id);
            assertEquals(expectedTrail, !w.fx.layers(FxSlot.TRAIL).isEmpty(),
                    "authored trail for " + w);
            if (WeaponRegistry.MECH_CHAINGUN_ID.equals(w.id)) {
                assertTracerTail(fx, w, 0.55f, "mech chaingun");
            } else {
                assertNull(fx.tracerTail(), "mech weapon has no authored tracer tail: " + w);
            }
            assertFalse(fx.boostRamp(), "mech weapons don't boost-ramp: " + w);
            assertNull(fx.contrail(), "mech weapons carry no contrail ribbon: " + w);
        }
    }

    private static Sprite assertSprite(ShotFx fx, String msg) {
        assertInstanceOf(Sprite.class, fx.body(), msg + " should be a Sprite body");
        return (Sprite) fx.body();
    }

    private static void assertTracerTail(ShotFx fx, WeaponDef weapon,
                                         float expectedLength, String message) {
        assertNotNull(fx.tracerTail(), message + " carries a tracer tail");
        assertEquals(expectedLength, fx.tracerTail().lengthCells(), 0f,
                message + " tracer length");
        assertSame(weapon.tracerColor(), fx.tracerTail().color(),
                message + " tracer color");
    }

    private record BoltExpectation(String spritePath, float lengthCells, float widthCells) {}

    private static void assertNoTrailsArcOrContrail(ShotFx fx) {
        assertEquals(0f, fx.arcHeight(), 0f);
        assertFalse(fx.boostRamp());
        assertNull(fx.contrail());
    }
}
