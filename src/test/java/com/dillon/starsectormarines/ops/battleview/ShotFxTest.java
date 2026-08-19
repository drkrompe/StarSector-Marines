package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.ShotFx.Bolt;
import com.dillon.starsectormarines.ops.battleview.ShotFx.Sprite;
import com.dillon.starsectormarines.ops.battleview.ShotFx.Tracer;
import com.dillon.starsectormarines.render2d.ContrailStyle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
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
 * Pins the {@link ShotFx} composition against the four sim weapon-source enums it
 * derives from — the table replaces the per-carrier {@code if turretKind … else if
 * marineWeapon …} cascade in the old {@code collectShots}/{@code drawTracers}, so
 * these assert the derivation stays faithful (and carrier-agnostic) as weapons are
 * added. The visible-round S3 assertions additionally pin traveling-bolt
 * derivation and the generated white-base asset contract.
 */
public class ShotFxTest {

    private static ShotEvent turretShot(TurretKind k) {
        return new ShotEvent(0, 0, 1, 1, true, Faction.DEFENDER, 0.15f, k);
    }

    private static ShotEvent shot(TurretKind t, MarineWeapon mw, MarineSecondary ms, MechWeapon mech) {
        return new ShotEvent(0, 0, 1, 1, true, Faction.MARINE, 0.15f, t, mw, ms, mech);
    }

    @Test
    public void everySourceResolvesToANonNullComposition() {
        for (TurretKind k : TurretKind.values())      assertNotNull(ShotFx.of(turretShot(k)), "turret " + k);
        for (MarineWeapon w : MarineWeapon.values())  assertNotNull(ShotFx.of(shot(null, w, null, null)), "primary " + w);
        for (MarineSecondary w : MarineSecondary.values()) assertNotNull(ShotFx.of(shot(null, null, w, null)), "secondary " + w);
        for (MechWeapon w : MechWeapon.values())      assertNotNull(ShotFx.of(shot(null, null, null, w)), "mech " + w);
        // No weapon source (detonations / legacy callers) → faction-default tracer.
        ShotEvent bare = new ShotEvent(0, 0, 1, 1, true, Faction.MARINE, 0.15f);
        ShotFx fx = ShotFx.of(bare);
        assertInstanceOf(Tracer.class, fx.body());
        assertNull(((Tracer) fx.body()).color(), "no-source tracer defers color to the faction default");
        assertFalse(fx.travels(), "full-line tracer impacts at fire time");
        assertNoTrailsArcOrContrail(fx);
    }

    @Test
    public void turretsAreSpritesCarryingTheirArcBoostAndSmokeDeclarations() {
        for (TurretKind k : TurretKind.values()) {
            ShotFx fx = ShotFx.of(turretShot(k));
            Sprite body = assertSprite(fx, "turret " + k);
            assertEquals(k.projectileSpritePath, body.spritePath(), "sprite path for " + k);
            assertEquals(k.projectileVisualCells, body.visualCells(), 0f, "visualCells for " + k);
            assertEquals(k.arcHeight, fx.arcHeight(), 0f, "arcHeight for " + k);
            assertEquals(k.hasBoostRamp(), fx.boostRamp(), "boostRamp for " + k);
            assertFalse(fx.engineTrail(), "turrets carry no engine trail: " + k);
            assertTrue(fx.travels(), "turret body travels: " + k);

            boolean ribbon = k == TurretKind.LOCUST;
            assertEquals(ribbon ? ContrailStyle.MISSILE_SMOKE : null, fx.contrail(), "contrail for " + k);
            // Ribbon kinds suppress the smoke puff; otherwise smokeTrail tracks the kind's flag.
            assertEquals(k.smokeTrail && !ribbon, fx.smokeTrail(), "smokeTrail for " + k);
        }
    }

    @Test
    public void locustIsTheBoostingContrailKind() {
        ShotFx fx = ShotFx.of(turretShot(TurretKind.LOCUST));
        assertTrue(fx.boostRamp(), "Locust boosts");
        assertSame(ContrailStyle.MISSILE_SMOKE, fx.contrail(), "Locust ribbons");
        assertFalse(fx.smokeTrail(), "ribbon suppresses the smoke puff");
    }

    @Test
    public void marinePrimariesUseDistinctTravelingBodyFamilies() {
        for (MarineWeapon w : MarineWeapon.values()) {
            ShotFx fx = ShotFx.of(shot(null, w, null, null));
            if (w.projectileSpritePath != null) {
                Sprite body = assertSprite(fx, "primary " + w);
                assertEquals(w.projectileSpritePath, body.spritePath(), "sprite path for " + w);
                assertEquals(w.projectileVisualCells, body.visualCells(), 0f, "visualCells for " + w);
            } else {
                assertInstanceOf(Bolt.class, fx.body(), "primary should bolt: " + w);
                Bolt bolt = (Bolt) fx.body();
                assertSame(w.tracerColor, bolt.color(), "bolt color for " + w);
                BoltExpectation expected = switch (w) {
                    case PULSE_RIFLE -> new BoltExpectation(
                            ShotFx.PULSE_BOLT_SPRITE_PATH, 1.0f, 0.25f);
                    case DMR -> new BoltExpectation(
                            ShotFx.RAIL_NEEDLE_SPRITE_PATH, 1.8f, 0.16f);
                    case DRONE_PULSE -> new BoltExpectation(
                            ShotFx.DRONE_DART_SPRITE_PATH, 0.65f, 0.16f);
                    case FIELD_RIFLE, SMG -> throw new AssertionError(
                            "sprite-backed primary reached bolt assertion: " + w);
                };
                assertEquals(expected.spritePath(), bolt.spritePath(), "bolt path for " + w);
                assertEquals(expected.lengthCells(), bolt.lengthCells(), 0f, "bolt length for " + w);
                assertEquals(expected.widthCells(), bolt.widthCells(), 0f, "bolt width for " + w);
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
    public void marineSecondariesAreSpritesWithNoModifiers() {
        for (MarineSecondary w : MarineSecondary.values()) {
            ShotFx fx = ShotFx.of(shot(null, null, w, null));
            Sprite body = assertSprite(fx, "secondary " + w);
            assertEquals(w.projectileSpritePath, body.spritePath(), "sprite path for " + w);
            assertEquals(w.projectileVisualCells, body.visualCells(), 0f, "visualCells for " + w);
            assertTrue(fx.travels(), "secondary body travels: " + w);
            assertNoTrailsArcOrContrail(fx);
        }
    }

    @Test
    public void mechWeaponsAreSpritesCarryingArcAndEngineTrail() {
        for (MechWeapon w : MechWeapon.values()) {
            ShotFx fx = ShotFx.of(shot(null, null, null, w));
            Sprite body = assertSprite(fx, "mech " + w);
            assertEquals(w.projectileSpritePath, body.spritePath(), "sprite path for " + w);
            assertEquals(w.projectileVisualCells, body.visualCells(), 0f, "visualCells for " + w);
            assertEquals(w.arcHeight, fx.arcHeight(), 0f, "arcHeight for " + w);
            assertEquals(w.engineTrail, fx.engineTrail(), "engineTrail for " + w);
            assertTrue(fx.travels(), "mech body travels: " + w);
            assertFalse(fx.boostRamp(), "mech weapons don't boost-ramp: " + w);
            assertFalse(fx.smokeTrail(), "mech weapons carry no smoke puff: " + w);
            assertNull(fx.contrail(), "mech weapons carry no contrail ribbon: " + w);
        }
    }

    private static Sprite assertSprite(ShotFx fx, String msg) {
        assertInstanceOf(Sprite.class, fx.body(), msg + " should be a Sprite body");
        return (Sprite) fx.body();
    }

    private record BoltExpectation(String spritePath, float lengthCells, float widthCells) {}

    private static void assertNoTrailsArcOrContrail(ShotFx fx) {
        assertEquals(0f, fx.arcHeight(), 0f);
        assertFalse(fx.boostRamp());
        assertFalse(fx.engineTrail());
        assertFalse(fx.smokeTrail());
        assertNull(fx.contrail());
    }
}
