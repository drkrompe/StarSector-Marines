package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.PendingDetonation;
import com.dillon.starsectormarines.battle.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * The physics-based AoE pipeline: rockets and missiles register a
 * {@link PendingDetonation} at fire time, fly visibly via their paired
 * {@link com.dillon.starsectormarines.battle.ShotEvent}, and detonate when
 * the timer drains — applying splash damage to nearby units (LOS-gated) plus
 * wall damage at the endpoint cell.
 *
 * <p>Stateful subsystem owning the in-flight queue. Both
 * {@link InfantryWeapons} (marine rocket launcher) and {@link HeavyWeapons}
 * (mech SRM / LRM) queue into here through the shared
 * {@link WeaponSimContext#queueDetonation} primitive on the sim. Tested
 * implicitly by playtest; a unit-test path can swap the context for a stub.
 *
 * <p>Friendly fire is ON unconditionally — every alive unit within
 * {@link PendingDetonation#aoeRadius} cells with LOS to the endpoint takes
 * damage regardless of faction. {@link PendingDetonation#shooterFaction} is
 * captured but not currently read; reserved for a future per-side filter.
 */
public class Detonations {

    private final List<PendingDetonation> pending = new ArrayList<>();

    /** Queues a detonation onto the in-flight list. Drained by {@link #tick}. */
    public void queue(PendingDetonation det) {
        pending.add(det);
    }

    /**
     * Ticks every queued detonation; when one's timer drains, applies splash
     * + wall damage and removes it. Reverse iteration for in-place removal.
     */
    public void tick(WeaponSimContext ctx) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingDetonation det = pending.get(i);
            det.remainingTime -= BattleSimulation.TICK_DT;
            if (det.remainingTime <= 0f) {
                detonate(det, ctx);
                pending.remove(i);
            }
        }
    }

    /**
     * Applies a detonation: AoE damage to every alive unit within
     * {@code aoeRadius} with line of sight to the endpoint, plus wall HP
     * damage at the endpoint cell. Cover reduction + vsTurret multiplier
     * flow through {@link WeaponSimContext#applyDamage}; LOS-blocked units
     * are spared (the wall absorbed the splash for them).
     */
    private void detonate(PendingDetonation det, WeaponSimContext ctx) {
        if (det.aoeRadius > 0f) {
            float r2 = det.aoeRadius * det.aoeRadius;
            int targetCx = (int) Math.floor(det.endpointX);
            int targetCy = (int) Math.floor(det.endpointY);
            for (Unit u : ctx.getUnits()) {
                if (!u.isAlive()) continue;
                float dx = (u.cellX + 0.5f) - det.endpointX;
                float dy = (u.cellY + 0.5f) - det.endpointY;
                if (dx * dx + dy * dy > r2) continue;
                // LOS from the detonation cell to the victim cell. Walls
                // between block the splash — gives marines hiding behind
                // walls a real reason to stay there.
                if (!ctx.getGrid().hasLineOfSight(targetCx, targetCy, u.cellX, u.cellY)) continue;
                ctx.applyDamage(u, det.damage, det.vsTurretMult);
            }
        }
        if (det.wallDamage > 0) {
            int cx = (int) Math.floor(det.endpointX);
            int cy = (int) Math.floor(det.endpointY);
            if (ctx.getGrid().inBounds(cx, cy)) {
                ctx.damageCell(cx, cy, det.wallDamage);
            }
        }
    }
}
