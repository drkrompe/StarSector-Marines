package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.PendingDetonation;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.map.CellTopology;

import java.util.ArrayList;
import java.util.Collections;
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
    private final List<PendingDetonation> pendingView = Collections.unmodifiableList(pending);

    /** Queues a detonation onto the in-flight list. Drained by {@link #tick}. */
    public void queue(PendingDetonation det) {
        pending.add(det);
    }

    /** Read-only view of the in-flight queue. Behaviors that need to coordinate (e.g. avoid rocket volleys against the same turret) read inflight ordnance from here. */
    public List<PendingDetonation> getPending() {
        return pendingView;
    }

    /**
     * Fires a detonation immediately, bypassing the in-flight queue. Used by
     * callers whose projectile flight time is already accounted for by their
     * own visuals (today: {@code FlybyOverlay}'s fighter missile, which
     * detonates on contact with the target's AoE radius rather than on a
     * countdown timer). Avoids the 1-tick delay that would otherwise appear
     * between the explosion FX and the damage application.
     */
    public void detonateNow(PendingDetonation det, WeaponSimContext ctx) {
        detonate(det, ctx);
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
        CellTopology topology = ctx.getTopology();
        int targetCx = (int) Math.floor(det.endpointX);
        int targetCy = (int) Math.floor(det.endpointY);
        if (det.aoeRadius > 0f) {
            // Lingering smoke column on REAL HE detonations only. Threshold
            // discriminates against the kinetic-saturation AoE weapons
            // (chaingun aoe=0.6, vulcan aoe=0.6, heavy MG aoe=0.6) whose
            // small splash radius lets multiple rounds catch clustered
            // targets but isn't a true explosion. Without this gate, a
            // 10-round chaingun burst spawned 10 HE-grade plumes — visually
            // equivalent to ten rocket impacts on the same cell. Real HE
            // detonations all sit at ≥ 1.0 cells (marine rocket 1.5,
            // SRM 1.3, LRM 2.0, mortar / grenade-launcher / LOCUST 1.5+).
            if (det.aoeRadius >= 1.0f) {
                ctx.spawnSmokePlume(det.endpointX, det.endpointY);
            }
            float r2 = det.aoeRadius * det.aoeRadius;
            for (Unit u : ctx.getUnits()) {
                if (!u.isAlive()) continue;
                // Friendly-fire opt-out for called-in air support. Ground
                // rockets / mech weapons leave the flag false so FF stays on
                // by default — players deciding to fire those have aim control.
                if (det.friendlyFireImmune && u.faction == det.shooterFaction) continue;
                float dx = (u.getCellX() + 0.5f) - det.endpointX;
                float dy = (u.getCellY() + 0.5f) - det.endpointY;
                if (dx * dx + dy * dy > r2) continue;
                // LOS from the detonation cell to the victim cell. Walls
                // between block the splash — gives marines hiding behind
                // walls a real reason to stay there.
                if (!ctx.getGrid().hasLineOfSight(targetCx, targetCy, u.getCellX(), u.getCellY())) continue;
                // Intact roof shields the unit from aerial splash. Binary —
                // caving the roof first (same detonation or a prior one) is
                // the tactical prerequisite to actually hurting the units
                // inside. Only fires for aerial deliveries (LRM / mortar /
                // shuttle / fighter) — a ground rocket through a doorway
                // explodes INSIDE the room and damages the interior normally.
                if (det.aerialDelivery
                        && topology.getBuildingId(u.getCellX(), u.getCellY()) != 0
                        && !topology.isRoofDestroyed(u.getCellX(), u.getCellY())) continue;
                ctx.applyDamage(u, det.damage, det.vsTurretMult);
            }
            // Roof cave-in: every building cell within the same AoE that
            // still has an intact roof + clear LOS from the endpoint loses
            // its ceiling. Drives the LRM / mortar "peel the roof off, then
            // bombard" gameplay loop — wallDamage stays low on those weapons
            // (they're indirect-fire arty, not breaching tools), but ceilings
            // are soft and go fast.
            int rCells = (int) Math.ceil(det.aoeRadius);
            for (int dy = -rCells; dy <= rCells; dy++) {
                for (int dx = -rCells; dx <= rCells; dx++) {
                    int cx = targetCx + dx;
                    int cy = targetCy + dy;
                    if (!ctx.getGrid().inBounds(cx, cy)) continue;
                    float cdx = (cx + 0.5f) - det.endpointX;
                    float cdy = (cy + 0.5f) - det.endpointY;
                    if (cdx * cdx + cdy * cdy > r2) continue;
                    if (topology.getBuildingId(cx, cy) == 0) continue;
                    if (topology.isRoofDestroyed(cx, cy)) continue;
                    if (!ctx.getGrid().hasLineOfSight(targetCx, targetCy, cx, cy)) continue;
                    ctx.destroyRoofCell(cx, cy);
                }
            }
        }
        // Wall damage. Two modes — endpoint-only (rockets / LRMs / mech SRM:
        // chip the wall they hit) or radius (heavy-blast variants like fighter
        // missiles: flatten every wall in radius in one detonation). Dust
        // burst on collapse is opt-in via spawnDustOnWallBreak.
        if (det.wallDamage > 0) {
            if (det.wallDamageRadius > 0f) {
                float wr = det.wallDamageRadius;
                float wr2 = wr * wr;
                int minX = (int) Math.floor(det.endpointX - wr);
                int maxX = (int) Math.floor(det.endpointX + wr);
                int minY = (int) Math.floor(det.endpointY - wr);
                int maxY = (int) Math.floor(det.endpointY + wr);
                for (int cy = minY; cy <= maxY; cy++) {
                    for (int cx = minX; cx <= maxX; cx++) {
                        float cdx = (cx + 0.5f) - det.endpointX;
                        float cdy = (cy + 0.5f) - det.endpointY;
                        if (cdx * cdx + cdy * cdy > wr2) continue;
                        if (ctx.damageCell(cx, cy, det.wallDamage)) {
                            if (det.spawnDustOnWallBreak) {
                                ctx.spawnDustBurst(cx + 0.5f, cy + 0.5f);
                            }
                        }
                    }
                }
            } else if (ctx.getGrid().inBounds(targetCx, targetCy)) {
                if (ctx.damageCell(targetCx, targetCy, det.wallDamage)
                        && det.spawnDustOnWallBreak) {
                    ctx.spawnDustBurst(targetCx + 0.5f, targetCy + 0.5f);
                }
            }
        }
    }
}
