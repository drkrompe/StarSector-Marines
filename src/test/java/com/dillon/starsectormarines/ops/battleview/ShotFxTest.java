package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
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
 * Pins the {@link ShotFx} composition against the four sim weapon-source enums it
 * derives from — the table replaces the per-carrier {@code if turretKind … else if
 * marineWeapon …} cascade in the old {@code collectShots}/{@code drawTracers}, so
 * these assert the derivation stays faithful (and carrier-agnostic) as weapons are
 * added. F2 ships the data + this test; the sweeps that consume it land in F3.
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
    public void marinePrimariesTracerUnlessTheyHaveAProjectileSprite() {
        for (MarineWeapon w : MarineWeapon.values()) {
            ShotFx fx = ShotFx.of(shot(null, w, null, null));
            if (w.projectileSpritePath != null) {
                Sprite body = assertSprite(fx, "primary " + w);
                assertEquals(w.projectileSpritePath, body.spritePath(), "sprite path for " + w);
                assertEquals(w.projectileVisualCells, body.visualCells(), 0f, "visualCells for " + w);
            } else {
                assertInstanceOf(Tracer.class, fx.body(), "primary should tracer: " + w);
                assertSame(w.tracerColor, ((Tracer) fx.body()).color(), "tracer color for " + w);
            }
            assertNoTrailsArcOrContrail(fx);
        }
    }

    @Test
    public void marineSecondariesAreSpritesWithNoModifiers() {
        for (MarineSecondary w : MarineSecondary.values()) {
            ShotFx fx = ShotFx.of(shot(null, null, w, null));
            Sprite body = assertSprite(fx, "secondary " + w);
            assertEquals(w.projectileSpritePath, body.spritePath(), "sprite path for " + w);
            assertEquals(w.projectileVisualCells, body.visualCells(), 0f, "visualCells for " + w);
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
            assertFalse(fx.boostRamp(), "mech weapons don't boost-ramp: " + w);
            assertFalse(fx.smokeTrail(), "mech weapons carry no smoke puff: " + w);
            assertNull(fx.contrail(), "mech weapons carry no contrail ribbon: " + w);
        }
    }

    private static Sprite assertSprite(ShotFx fx, String msg) {
        assertInstanceOf(Sprite.class, fx.body(), msg + " should be a Sprite body");
        return (Sprite) fx.body();
    }

    private static void assertNoTrailsArcOrContrail(ShotFx fx) {
        assertEquals(0f, fx.arcHeight(), 0f);
        assertFalse(fx.boostRamp());
        assertFalse(fx.engineTrail());
        assertFalse(fx.smokeTrail());
        assertNull(fx.contrail());
    }
}
