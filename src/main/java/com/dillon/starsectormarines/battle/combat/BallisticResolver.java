package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.DoodadService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Resolves one round's full flight in a single space-time raycast at fire
 * time — the ballistics swap's core: a ray from the shooter toward a
 * spread-jittered aim point, hard-capped at the first wall, walked against
 * doodad block rolls and unit-radius contacts in time order. Damage/FX
 * application is scheduled by the caller on the returned
 * {@link Resolution#flightTime()}; this class only decides <em>where the
 * round physically ends up</em>.
 *
 * <p>See {@code roadmap/ballistics/overview.md} (design record) and
 * {@code roadmap/ballistics/stories/s1-resolver-core.md} (the "Resolution
 * algorithm" section this method implements step for step).
 *
 * <p><b>Pure and stateless.</b> Every constructor dependency is read-only
 * from this class's perspective (grid, doodad cover, the spatial index
 * snapshot, roster by-id reads); all randomness flows through the caller's
 * {@link Random}, mirroring {@link ShotEndpoint}'s testability contract. No
 * mutable instance state, so a single shared resolver instance is safe to
 * call concurrently from the parallel UPDATE_UNITS dispatch — every local
 * (event list, gather buffer) is call-scoped.
 */
public final class BallisticResolver {

    /** Missed rounds fly this far past the (spread-jittered) aim point before the ray's raw length is computed. */
    public static final float OVERSHOOT_CELLS = 3f;
    /** Hit-roll chance for a contact that is NOT the locked target — a flat graze chance rather than the full accuracy stack. */
    public static final float INCIDENTAL_HIT_CHANCE = 0.35f;
    /** Damage multiplier applied by the caller (queue time) when a round's victim shares the shooter's faction. Declared here as the tuning surface; not consumed inside {@link #resolve}. */
    public static final float FRIENDLY_FIRE_DAMAGE_MULT = 0.5f;
    /** Friendly contacts closer than this (along-ray cells from the shooter) are skipped entirely — "shooting around" a squadmate at your shoulder. Never applied to enemy contacts. */
    public static final float FRIENDLY_MUZZLE_CLEARANCE = 2.0f;
    /** Round speed (cells/sec) used when the firing weapon carries no velocity of its own. Declared here as the tuning surface; the per-shot velocity is resolved by the caller before calling {@link #resolve}. */
    public static final float DEFAULT_ROUND_VELOCITY = 60f;
    /** Block-roll chance by cover level, index = level (capped at 3 = {@link NavigationGrid#MAX_COVER}). Tuning-neutral anchor: today's 0.85/0.70/0.55 accuracy multipliers for levels 1/2/3. */
    public static final float[] BLOCK_CHANCE_BY_LEVEL = {0f, 0.15f, 0.30f, 0.45f};
    /**
     * Margin (cells) passed to {@link UnitSpatialIndex#gatherAlongSegment} —
     * max collision radius plus slack. A flat constant is fine for S1 (no
     * mech-scale radii are wildly larger than the infantry default), per the
     * story doc; S2's moving-target extrapolation is the natural place to
     * derive this from the roster's actual max radius / speed instead.
     */
    public static final float GATHER_MARGIN_CELLS = 1.0f;

    /** Where a round's flight ended and what stopped it. */
    public enum StopKind { UNIT_HIT, COVER_CLIP, WALL, DOODAD_BLOCK, OVERSHOOT }

    /**
     * The outcome of one resolved round. {@code endX}/{@code endY} is where
     * the round physically stopped (visual endpoint); {@code flightTime} =
     * {@code stopDist / roundVelocity}, the delay before damage/FX apply;
     * {@code victimId} is 0 unless {@code kind == UNIT_HIT}; {@code
     * hitIntended} is true only when the recorded victim is the locked
     * target (drives {@code ShotEvent.hit}); {@code friendlyHit} is true
     * when the recorded victim shares the shooter's faction.
     */
    public record Resolution(float endX, float endY, float flightTime,
                              long victimId, boolean hitIntended,
                              boolean friendlyHit, StopKind kind) {}

    /** One contact candidate along the ray, in time order. Doodad crossings roll a flat block chance; unit contacts roll cover-clip then hit. */
    private static final class Event {
        final float t;
        final boolean doodad;
        final float x;
        final float y;
        final int doodadLevel;
        final long unitId;
        final boolean friendly;

        private Event(float t, boolean doodad, float x, float y, int doodadLevel, long unitId, boolean friendly) {
            this.t = t;
            this.doodad = doodad;
            this.x = x;
            this.y = y;
            this.doodadLevel = doodadLevel;
            this.unitId = unitId;
            this.friendly = friendly;
        }

        static Event doodad(float t, float x, float y, int level) {
            return new Event(t, true, x, y, level, 0L, false);
        }

        static Event unit(float t, float x, float y, long unitId, boolean friendly) {
            return new Event(t, false, x, y, 0, unitId, friendly);
        }
    }

    private final NavigationGrid grid;
    private final DoodadService doodads;
    private final UnitSpatialIndex unitIndex;
    private final UnitRosterService roster;

    public BallisticResolver(NavigationGrid grid, DoodadService doodads,
                              UnitSpatialIndex unitIndex, UnitRosterService roster) {
        this.grid = grid;
        this.doodads = doodads;
        this.unitIndex = unitIndex;
        this.roster = roster;
    }

    /**
     * Resolves the shooter's round against {@code target}, walking every
     * wall/doodad/unit contact along the ray in time order and returning
     * where it stopped. {@code finalAccuracy} is the full accuracy stack
     * with cover already removed (cover is re-expressed here as physical
     * interception); {@code effectiveSpread} is the distance-scaled lateral
     * jitter radius (0 for pinpoint weapons); {@code roundVelocity} is
     * cells/sec, already resolved by the caller (see
     * {@link #DEFAULT_ROUND_VELOCITY}). Reads only — safe to call from a
     * parallel dispatch.
     */
    public Resolution resolve(long shooter, long target,
                               float finalAccuracy, float effectiveSpread,
                               float roundVelocity, Random rng) {
        World world = roster.world();
        Faction shooterFaction = roster.identity().faction(shooter);

        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        float targetX = world.renderX(target);
        float targetY = world.renderY(target);

        // Step 1: ray. Aim = target position plus a radius-uniform lateral
        // offset sampled from effectiveSpread (mirrors ShotEndpoint's hit-jitter
        // sampling; this replaces the miss ring entirely).
        float angle = rng.nextFloat() * (float) (Math.PI * 2);
        float offsetR = rng.nextFloat() * effectiveSpread;
        float aimX = targetX + (float) Math.cos(angle) * offsetR;
        float aimY = targetY + (float) Math.sin(angle) * offsetR;

        float aimDx = aimX - fromX;
        float aimDy = aimY - fromY;
        float aimDist = (float) Math.sqrt(aimDx * aimDx + aimDy * aimDy);
        float dirX, dirY;
        if (aimDist > 1e-6f) {
            dirX = aimDx / aimDist;
            dirY = aimDy / aimDist;
        } else {
            // Degenerate: shooter and jittered aim point coincide. Any fixed
            // direction is as good as another — nothing meaningful to aim at.
            dirX = 1f;
            dirY = 0f;
        }
        float rawLen = aimDist + OVERSHOOT_CELLS;
        float rawEndX = fromX + dirX * rawLen;
        float rawEndY = fromY + dirY * rawLen;

        int shooterCellX = (int) Math.floor(fromX);
        int shooterCellY = (int) Math.floor(fromY);
        int rawEndCellX = (int) Math.floor(rawEndX);
        int rawEndCellY = (int) Math.floor(rawEndY);

        long wallPacked = grid.firstWallOnLine(shooterCellX, shooterCellY, rawEndCellX, rawEndCellY);
        int wallCellX = (int) wallPacked;
        int wallCellY = (int) (wallPacked >>> 32);
        boolean wallFound = !(wallCellX == -1 && wallCellY == -1);

        float rayEndX = wallFound ? wallCellX + 0.5f : rawEndX;
        float rayEndY = wallFound ? wallCellY + 0.5f : rawEndY;
        float rayLen = dist(fromX, fromY, rayEndX, rayEndY);
        int rayEndCellX = (int) Math.floor(rayEndX);
        int rayEndCellY = (int) Math.floor(rayEndY);

        List<Event> events = new ArrayList<>();

        // Step 2: doodad crossings — Bresenham the (wall-capped) ray's cells,
        // skipping the shooter's own cell.
        walkDoodadCrossings(shooterCellX, shooterCellY, rayEndCellX, rayEndCellY,
                fromX, fromY, roundVelocity, events);

        // Step 3: unit contacts over the (wall-capped) ray.
        LongBucket candidates = new LongBucket();
        unitIndex.gatherAlongSegment(fromX, fromY, rayEndX, rayEndY, GATHER_MARGIN_CELLS, candidates);
        for (int i = 0; i < candidates.size; i++) {
            long candidateId = candidates.ids[i];
            if (candidateId == shooter) continue;
            if (!roster.isAliveById(candidateId)) continue;

            // The same physical body circle SeparationSystem shoves apart and
            // Detonations/WorldPicker size against — one radius concept per body.
            UnitType candidateType = roster.identity().type(candidateId);
            float r = candidateType.radius;
            float ux = world.x(candidateId);
            float uy = world.y(candidateId);

            // Ray-circle intersection: solve |from + dir*t - center| = r for
            // the entry AND exit roots. Both roots negative means the whole
            // circle sits behind the shooter along this ray's direction —
            // gatherAlongSegment's margin query can still surface such a
            // candidate (it's near the segment's start point, not
            // necessarily ahead of it), so the exit root must be checked
            // before treating this as a contact: skip entirely rather than
            // false-clamping to t=0.
            float ox = fromX - ux;
            float oy = fromY - uy;
            float b = 2f * (ox * dirX + oy * dirY);
            float c = ox * ox + oy * oy - r * r;
            float disc = b * b - 4f * c;
            if (disc < 0f) continue; // margin match but no real intersection with the exact radius
            float sqrtDisc = (float) Math.sqrt(disc);
            float tEntry = (-b - sqrtDisc) / 2f;
            float tExit = (-b + sqrtDisc) / 2f;
            if (tExit < 0f) continue; // circle lies entirely behind the shooter
            if (tEntry < 0f) tEntry = 0f; // shooter inside the collision radius, or the circle straddles the origin
            if (tEntry > rayLen) continue; // beyond the wall-capped ray

            Faction candidateFaction = roster.identity().faction(candidateId);
            boolean friendly = candidateFaction == shooterFaction;
            if (friendly && tEntry < FRIENDLY_MUZZLE_CLEARANCE) continue;

            float contactX = fromX + dirX * tEntry;
            float contactY = fromY + dirY * tEntry;
            events.add(Event.unit(tEntry / roundVelocity, contactX, contactY, candidateId, friendly));
        }

        // Step 4: walk events sorted by t; first stop wins. A wall (when
        // present) is never a member of this list — it necessarily sits at
        // the ray's own endpoint, so every doodad/unit event's t is <= the
        // wall's t by construction; reaching the end of the list without a
        // stop is exactly "nothing stopped it before the wall/ray end".
        events.sort((a, b) -> Float.compare(a.t, b.t));
        for (Event e : events) {
            if (e.doodad) {
                float blockChance = BLOCK_CHANCE_BY_LEVEL[Math.min(e.doodadLevel, BLOCK_CHANCE_BY_LEVEL.length - 1)];
                if (rng.nextFloat() < blockChance) {
                    return new Resolution(e.x, e.y, e.t, 0L, false, false, StopKind.DOODAD_BLOCK);
                }
                continue; // fly on
            }

            long victim = e.unitId;
            int victimCellX = (int) Math.floor(world.x(victim));
            int victimCellY = (int) Math.floor(world.y(victim));
            int fromDx = shooterCellX - victimCellX;
            int fromDy = shooterCellY - victimCellY;
            int coverLevel = grid.getCoverAt(victimCellX, victimCellY, fromDx, fromDy);
            float coverBlockChance = BLOCK_CHANCE_BY_LEVEL[Math.min(coverLevel, BLOCK_CHANCE_BY_LEVEL.length - 1)];
            if (rng.nextFloat() < coverBlockChance) {
                return new Resolution(e.x, e.y, e.t, 0L, false, false, StopKind.COVER_CLIP);
            }

            boolean isLockedTarget = victim == target;
            float hitChance = isLockedTarget ? finalAccuracy : INCIDENTAL_HIT_CHANCE;
            if (rng.nextFloat() < hitChance) {
                return new Resolution(e.x, e.y, e.t, victim, isLockedTarget, e.friendly, StopKind.UNIT_HIT);
            }
            // else fly on
        }

        StopKind finalKind = wallFound ? StopKind.WALL : StopKind.OVERSHOOT;
        return new Resolution(rayEndX, rayEndY, rayLen / roundVelocity, 0L, false, false, finalKind);
    }

    /** Bresenham cell walk from the shooter's cell to the ray-end cell, emitting a doodad-crossing {@link Event} for every non-start cell with direction-agnostic doodad cover &gt; 0. */
    private void walkDoodadCrossings(int x0, int y0, int x1, int y1,
                                      float fromX, float fromY, float roundVelocity,
                                      List<Event> out) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        boolean first = true;
        while (true) {
            if (!first) {
                int level = doodads.getDoodadLevelOnCell(x, y);
                if (level > 0) {
                    float cx = x + 0.5f;
                    float cy = y + 0.5f;
                    float t = dist(fromX, fromY, cx, cy) / roundVelocity;
                    out.add(Event.doodad(t, cx, cy, level));
                }
            }
            first = false;
            if (x == x1 && y == y1) break;
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx)  { err += dx; y += sy; }
        }
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
