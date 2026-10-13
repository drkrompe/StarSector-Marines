package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the lance sweeps in progress and burns the ones that are lit.
 *
 * <p>The beam is not a second damage model. Each burning tick lays one ordinary
 * detonation at every impact centre along the line, so splash, line of sight,
 * wall breach, roof loss and friendly fire all come from the pipeline that
 * already owns them, at a magnitude only a capital's weapon has. Friendly fire
 * is unconditional: a hull's beam does not check colours.
 */
public final class LanceSweepSystem {

    /** A sweep and the seconds it has been alive. */
    public static final class Active {
        public final LanceSweep sweep;
        private float elapsed;

        private Active(LanceSweep sweep) {
            this.sweep = sweep;
        }

        public float elapsedSeconds() {
            return elapsed;
        }

        public boolean isBurning() {
            return sweep.isBurning(elapsed);
        }

        public float angle() {
            return sweep.angleAt(elapsed);
        }
    }

    private final List<Active> active = new ArrayList<>();
    private final List<Active> activeView = Collections.unmodifiableList(active);

    /** Commits a sweep. Its arc is fixed from here. */
    public Active start(LanceSweep sweep) {
        Active a = new Active(sweep);
        active.add(a);
        return a;
    }

    /** Sweeps in progress, telegraphing or burning. */
    public List<Active> active() {
        return activeView;
    }

    /** Advances every sweep one step and lands the beam of each lit one. */
    public void tick(float dt, Consumer<PendingDetonation> detonate) {
        for (int i = active.size() - 1; i >= 0; i--) {
            Active a = active.get(i);
            a.elapsed += dt;
            if (a.sweep.isBurning(a.elapsed)) {
                burn(a, dt, detonate);
            }
            if (a.elapsed >= a.sweep.totalSeconds()) {
                active.remove(i);
            }
        }
    }

    private static void burn(Active a, float dt, Consumer<PendingDetonation> detonate) {
        LanceSweep s = a.sweep;
        float angle = a.angle();
        float perImpact = s.damagePerSecond() * dt;
        for (int i = 0, n = s.sampleCount(); i < n; i++) {
            detonate.accept(new PendingDetonation(
                    s.shooterId(), s.sampleX(angle, i), s.sampleY(angle, i), 0f,
                    s.halfWidth(), perImpact, s.penetration(),
                    s.wallDamagePerTick(), s.faction(),
                    /*aerialDelivery*/ false,
                    /*wallDamageRadius*/ s.halfWidth(),
                    /*spawnDustOnWallBreak*/ true,
                    /*friendlyFireImmune*/ false,
                    /*authoredAftermath*/ true).asShipGrade());
        }
    }
}
