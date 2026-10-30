package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.weapon.ContrailProfile;
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
 * derivation.
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
            if (weapon.hitscanBeam()) {
                // A turret that authors a beam and no projectile art is a hitscan beam, as a mech's is.
                assertInstanceOf(Tracer.class, fx.body(), "beam turret " + structure.id);
                assertEquals(weapon.beamStyle(), ((Tracer) fx.body()).style(), "beam style for " + structure.id);
                assertFalse(fx.travels(), "a beam impacts at fire time: " + structure.id);
                continue;
            }
            if (weapon.energy && weapon.projectileSpritePath == null) {
                // An energy turret with no projectile art and no beam fires a visible pulse bolt in its own colour.
                assertInstanceOf(Bolt.class, fx.body(), "energy turret bolts: " + structure.id);
                assertSame(weapon.tracerColor, ((Bolt) fx.body()).color(), "bolt colour for " + structure.id);
                assertTrue(fx.travels(), "an energy bolt travels: " + structure.id);
                continue;
            }
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
            } else if (TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID.equals(structure.id)) {
                assertTracerTail(fx, weapon, 0.9f, "tank cannon");
            } else {
                assertNull(fx.tracerTail(), "turret has no authored tracer tail: " + structure.id);
            }

            if (weapon.contrailProfile == ContrailProfile.MISSILE_SMOKE) {
                assertSame(ContrailStyle.MISSILE_SMOKE, fx.contrail(), "missile turret contrail: " + structure.id);
            } else if (weapon.contrailProfile == ContrailProfile.INTERCEPTOR_WISP) {
                assertSame(ContrailStyle.INTERCEPTOR_WISP, fx.contrail(), "interceptor contrail: " + structure.id);
            } else {
                assertNull(fx.contrail(), "non-missile turret contrail: " + structure.id);
            }
            if (TurretCatalogRegistry.LOCUST_STRUCTURE_ID.equals(structure.id)) {
                assertSame(ContrailStyle.MISSILE_SMOKE, fx.contrail());
            }
        }
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
                } else if (WeaponRegistry.DRONE_ION_ID.equals(w.id)) {
                    // No authored body: the strike drone's ion shot takes the generic energy bolt.
                    expected = new BoltExpectation(ShotFx.PULSE_BOLT_SPRITE_PATH, 1.0f, 0.22f);
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
            // A contrail is the trail of a secondary that has one; a jet with no contrail (the flamer) may author its own.
            if (fx.contrail() != null) {
                assertTrue(w.weaponDef().fx.layers(FxSlot.TRAIL).isEmpty(),
                        "secondary trail remains represented by its contrail ribbon: " + w);
            }
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
            boolean expectedTrail = WeaponRegistry.isMechSrm(w.id)
                    || WeaponRegistry.isMechLrm(w.id)
                    || WeaponRegistry.MECH_SRM_3_QS_ID.equals(w.id);
            assertEquals(expectedTrail, !w.fx.layers(FxSlot.TRAIL).isEmpty(),
                    "authored trail for " + w);
            if (w.tracerTailCells() > 0f) {
                assertTracerTail(fx, w, w.tracerTailCells(), "mech ballistic tracer");
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
