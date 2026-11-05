package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A beam that opens off target lands short and walks in: full reach on target, the shortest at the widest arc. */
class TurretFireBeamTest {

    @Test
    void reachWalksInAsTheTurretComesOnTarget() {
        assertEquals(1f, TurretFireSystem.beamReachFraction(0f), 1e-6f);
        assertEquals(TurretFireSystem.BEAM_SHORT_FRACTION,
                TurretFireSystem.beamReachFraction(TurretAim.BEAM_OPEN_ARC_DEG), 1e-6f);
        assertEquals(TurretFireSystem.BEAM_SHORT_FRACTION, TurretFireSystem.beamReachFraction(-90f), 1e-6f);
        float prev = 2f;
        for (float off = 0f; off <= TurretAim.BEAM_OPEN_ARC_DEG; off += 2f) {
            float r = TurretFireSystem.beamReachFraction(off);
            assertTrue(r < prev, "reach shortens monotonically off target");
            prev = r;
        }
    }

    /**
     * A pulse of a beam with a splash bursts where it stopped: its contact payload on the body it struck, its splash
     * round that point, its structure damage over its reach, and its heat into the ground. Into a wall, the same burst
     * with no one to take the contact.
     */
    @Test
    void aSplashBeamsPulseBurstsWhereItStoppedWithItsWallDamageAndHeat() throws Exception {
        WeaponDef laser = WeaponDef.parse(new JSONObject("""
                {
                  "id": "weapon.splash-beam-fixture", "mount": "turret-mount",
                  "catalog": { "displayName": "Splash Beam" },
                  "sim": {
                    "range": 30.0, "damage": 6.0, "accuracy": 1.0, "cooldown": 1.0, "penetration": 2.0,
                    "contact": { "damage": 16.0, "penetration": 3.0 },
                    "roundVelocity": 320.0, "flightSec": 0.05,
                    "aoeRadius": 0.5, "wallDamage": 10, "wallDamageRadius": 0.6, "incendiaryHeat": 55
                  },
                  "render": { "beam": { "coreWidthPx": 2.0, "glowColor": "4A6BFF", "glowWidthPx": 8.0,
                    "pulseCycles": 1.0, "lifetimeSec": 0.07 } },
                  "fx": { "muzzle": [ {"kind": "glow", "radius": 0.2, "lifetime": 0.1} ],
                          "impact": [ {"kind": "glow", "radius": 0.2, "lifetime": 0.1} ] }
                }
                """));
        long victim = 42L;
        PendingDetonation onBody = TurretFireSystem.groundDetonation(7L, Faction.MARINE, laser,
                new BallisticResolver.Resolution(10f, 5f, 0f, 0.05f, victim, true, false,
                        BallisticResolver.StopKind.UNIT_HIT, List.of()), laser.incendiaryHeat());
        assertEquals(victim, onBody.directTargetId, "the struck body takes the contact payload");
        assertEquals(16f, onBody.directDamage, 1e-6f);
        assertEquals(3f, onBody.directPenetration, 1e-6f);
        assertEquals(0.5f, onBody.aoeRadius, 1e-6f, "and the splash bursts round it");
        assertEquals(6f, onBody.damage, 1e-6f);
        assertEquals(10, onBody.wallDamage);
        assertEquals(0.6f, onBody.wallDamageRadius, 1e-6f);
        assertEquals(55, onBody.heat(), "the pulse's heat goes into the ground it covers");

        PendingDetonation onWall = TurretFireSystem.groundDetonation(7L, Faction.MARINE, laser,
                new BallisticResolver.Resolution(12f, 5.5f, 0f, 0.05f, 0L, true, false,
                        BallisticResolver.StopKind.WALL, List.of()), laser.incendiaryHeat());
        assertEquals(0L, onWall.directTargetId, "a pulse into a wall strikes no one");
        assertEquals(10, onWall.wallDamage, "and still wears the wall it stopped at");
        assertEquals(12f, onWall.endpointX, 1e-6f);
        assertEquals(55, onWall.heat());
    }
}
