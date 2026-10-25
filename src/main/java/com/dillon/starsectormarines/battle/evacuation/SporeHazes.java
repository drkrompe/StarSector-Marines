package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * A Bombard's spore hazes ({@code bombard.md}): the amber haze its glob bursts into, which spoils the aim of whoever
 * shoots through it without hiding anything, and stings.
 *
 * <p>The haze itself is a see-through field on the smoke service ({@link SmokeFieldService#deployHaze}): it costs a
 * round crossing it accuracy and never blocks sight, fog or acquisition. This class owns the rest: the
 * <b>sting</b> of the burst and the <b>pulse</b> every {@link #PULSE_SECONDS} while the haze stands. Both are small
 * splashes through the ordinary detonation path, as a Spitter's acid is: no penetration, the swarm spared, an intact
 * roof shielding. They are repeated small hits rather than a status, so they wound as hits do; a body that stands in
 * one haze for its whole life loses about a rifle hit. The haze is a reason to step out, not a killer.
 */
public final class SporeHazes {

    /** Cells of haze an adult's glob bursts into. */
    public static final float RADIUS = 3f;
    /** Seconds a haze stands whole before it starts to thin. */
    public static final float STAND_SECONDS = 12f;
    /** Seconds it then takes to thin from the outside in and go. */
    public static final float THIN_SECONDS = 6f;
    /** The burst's sting. */
    public static final float STING_DAMAGE = 4f;
    /** Each pulse's sting; nine of them over a haze's life come to about a rifle hit. */
    public static final float PULSE_DAMAGE = 1.5f;
    /** Seconds between pulses. */
    public static final float PULSE_SECONDS = 2f;

    private final List<Haze> hazes = new ArrayList<>();

    private static final class Haze {
        final long id, shooter;
        float age;

        Haze(long id, long shooter) {
            this.id = id;
            this.shooter = shooter;
        }
    }

    /**
     * Bursts a haze at {@code (x, y)}: stands it, and stings what is under it.
     *
     * @param shooter the body credited with its hits
     * @param scale   the shooter's growth-stage scale: an elder's haze is larger and stands longer
     * @return the haze's id
     */
    public long burst(BattleSimulation sim, long shooter, float x, float y, float scale) {
        float radius = RADIUS * scale;
        long id = sim.smokeFields().deployHaze(Faction.DEFENDER, x, y, radius, (STAND_SECONDS + THIN_SECONDS) * scale,
                THIN_SECONDS * scale);
        hazes.add(new Haze(id, shooter));
        sting(sim, shooter, x, y, radius, STING_DAMAGE);
        return id;
    }

    /** Ages every haze and pulses the ones whose pulse came due; forgets the ones that have gone. */
    public void tick(BattleSimulation sim, float dt) {
        if (hazes.isEmpty()) return;
        List<SmokeFieldService.SmokeFieldView> standing = sim.smokeFields().activeHazes();
        for (int i = hazes.size() - 1; i >= 0; i--) {
            Haze haze = hazes.get(i);
            SmokeFieldService.SmokeFieldView view = null;
            for (SmokeFieldService.SmokeFieldView v : standing) {
                if (v.id() == haze.id) {
                    view = v;
                    break;
                }
            }
            if (view == null) {
                hazes.remove(i);
                continue;
            }
            float before = haze.age;
            haze.age += dt;
            for (int p = pulsesDue(before, haze.age); p > 0; p--) {
                sting(sim, haze.shooter, view.x(), view.y(), view.radius(), PULSE_DAMAGE);
            }
        }
    }

    /** Pulses that come due as a haze ages from {@code before} to {@code after} seconds: one at each whole period. */
    static int pulsesDue(float before, float after) {
        if (after <= before) return 0;
        return (int) Math.floor(after / PULSE_SECONDS) - (int) Math.floor(before / PULSE_SECONDS);
    }

    private static void sting(BattleSimulation sim, long shooter, float x, float y, float radius, float damage) {
        sim.detonateNow(new PendingDetonation(shooter, x, y, 0f, radius, damage, /*penetration*/ 0f,
                /*wallDamage*/ 0, Faction.DEFENDER, /*aerialDelivery: a roof shields*/ true,
                /*wallDamageRadius*/ 0f, /*spawnDustOnWallBreak*/ false,
                /*friendlyFireImmune: it spares the swarm*/ true, /*authoredAftermath*/ true));
    }
}
