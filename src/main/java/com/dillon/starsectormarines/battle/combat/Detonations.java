package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.MapService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The physics-based AoE pipeline: rockets and missiles register a
 * {@link PendingDetonation} at fire time, fly visibly via their paired
 * {@link com.dillon.starsectormarines.battle.combat.ShotEvent}, and detonate when
 * the timer drains — applying splash damage to nearby units (LOS-gated) plus
 * wall damage at the endpoint cell.
 *
 * <p>Stateful subsystem owning the in-flight queue. Both
 * {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons} (marine rocket launcher) and {@link HeavyWeapons}
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

    private final UnitRegistry registry;
    private final NavigationGrid grid;
    private final CellTopology topology;
    private final DamageService damageService;
    private final MapService mapService;
    private final EffectsService effects;

    /**
     * Reused per-detonation gather of the units in splash range before any
     * damage is applied. applyDamage resolves inline in this serial phase, so a
     * lethal splash releases its target and swap-and-pops the registry;
     * gathering the in-range set first makes the apply pass a snapshot so that
     * release can't reshuffle the dense slots mid-loop.
     */
    private final List<Entity> aoeScratch = new ArrayList<>();

    public Detonations(UnitRegistry registry, NavigationGrid grid, CellTopology topology,
                       DamageService damageService, MapService mapService,
                       EffectsService effects) {
        this.registry = registry;
        this.grid = grid;
        this.topology = topology;
        this.damageService = damageService;
        this.mapService = mapService;
        this.effects = effects;
    }

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
    public void detonateNow(PendingDetonation det) {
        detonate(det);
    }

    /**
     * Ticks every queued detonation; when one's timer drains, applies splash
     * + wall damage and removes it. Reverse iteration for in-place removal.
     */
    public void tick() {
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingDetonation det = pending.get(i);
            det.remainingTime -= BattleSimulation.TICK_DT;
            if (det.remainingTime <= 0f) {
                detonate(det);
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
    private void detonate(PendingDetonation det) {
        int targetCx = (int) Math.floor(det.endpointX);
        int targetCy = (int) Math.floor(det.endpointY);
        if (det.aoeRadius > 0f) {
            if (det.aoeRadius >= 1.0f) {
                effects.spawnSmokePlume(det.endpointX, det.endpointY);
            }
            float r2 = det.aoeRadius * det.aoeRadius;
            // Gather the in-range, LOS-visible, non-roof-shielded units first
            // (read-only over the live registry), then apply — see aoeScratch.
            aoeScratch.clear();
            Entity[] dense = registry.denseArray();
            int[] cellX = registry.cellXArray();
            int[] cellY = registry.cellYArray();
            for (int i = 0, n = registry.liveCount(); i < n; i++) {
                Entity u = dense[i];
                if (det.friendlyFireImmune && u.faction == det.shooterFaction) continue;
                int ucx = cellX[i];
                int ucy = cellY[i];
                float dx = (ucx + 0.5f) - det.endpointX;
                float dy = (ucy + 0.5f) - det.endpointY;
                if (dx * dx + dy * dy > r2) continue;
                if (!grid.hasLineOfSight(targetCx, targetCy, ucx, ucy)) continue;
                if (det.aerialDelivery
                        && topology.getBuildingId(ucx, ucy) != 0
                        && !topology.isRoofDestroyed(ucx, ucy)) continue;
                aoeScratch.add(u);
            }
            for (int i = 0, n = aoeScratch.size(); i < n; i++) {
                damageService.applyDamage(aoeScratch.get(i), det.damage, det.vsTurretMult, 1f);
            }
            aoeScratch.clear();
            int rCells = (int) Math.ceil(det.aoeRadius);
            for (int dy = -rCells; dy <= rCells; dy++) {
                for (int dx = -rCells; dx <= rCells; dx++) {
                    int cx = targetCx + dx;
                    int cy = targetCy + dy;
                    if (!grid.inBounds(cx, cy)) continue;
                    float cdx = (cx + 0.5f) - det.endpointX;
                    float cdy = (cy + 0.5f) - det.endpointY;
                    if (cdx * cdx + cdy * cdy > r2) continue;
                    if (topology.getBuildingId(cx, cy) == 0) continue;
                    if (topology.isRoofDestroyed(cx, cy)) continue;
                    if (!grid.hasLineOfSight(targetCx, targetCy, cx, cy)) continue;
                    mapService.destroyRoof(cx, cy);
                }
            }
        }
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
                        if (mapService.damageWall(cx, cy, det.wallDamage)) {
                            if (det.spawnDustOnWallBreak) {
                                effects.spawnDustBurst(cx + 0.5f, cy + 0.5f);
                            }
                        }
                    }
                }
            } else if (grid.inBounds(targetCx, targetCy)) {
                if (mapService.damageWall(targetCx, targetCy, det.wallDamage)
                        && det.spawnDustOnWallBreak) {
                    effects.spawnDustBurst(targetCx + 0.5f, targetCy + 0.5f);
                }
            }
        }
    }
}
