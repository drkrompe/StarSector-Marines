package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.MovementService;
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
 * lead-and-target-plane-sampled aim point, hard-capped at the first wall, walked
 * against height-gated doodad block rolls and time-domain unit-radius contacts
 * in time order. Damage/FX application is scheduled by the caller on the returned
 * {@link Resolution#flightTime()}; this class only decides <em>where the
 * round physically ends up</em>.
 *
 * <p>See {@code roadmap/ballistics/overview.md} (design record) and
 * {@code roadmap/ballistics/stories/s2-moving-targets.md} (the "Time-domain
 * contact solve" and "Shooter lead" sections {@link #resolve} implements
 * step for step; S1's static-world solve, which the {@code w = 0} case
 * collapses to exactly, is {@code stories/s1-resolver-core.md}).
 * Target-plane aim and the lightweight vertical silhouette are specified in
 * {@code roadmap/ballistics/complete/s3b-target-plane-accuracy.md}; obstacle
 * catch bands are specified in
 * {@code roadmap/ballistics/stories/s3c-obstacle-catch-heights.md}.
 *
 * <p><b>Pure and stateless.</b> Every constructor dependency is read-only
 * from this class's perspective (grid, doodad cover, the spatial index
 * snapshot, roster by-id reads); all randomness flows through the caller's
 * {@link Random}, mirroring {@link ShotEndpoint}'s testability contract. No
 * mutable instance state, so a single shared resolver instance is safe to
 * call concurrently from the parallel UPDATE_UNITS dispatch — every local
 * (event list, gather buffer) is call-scoped. The one intentionally
 * unsynchronized read is a candidate's velocity ({@code MOVEMENT_VEL_X/Y}
 * via {@link MovementService}), written by the same tick's movement pass —
 * see {@link #resolve} for why a mid-tick-stale sample is fine.
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
     * Base margin (cells) passed to {@link UnitSpatialIndex#gatherAlongSegment}
     * — max collision radius plus slack for a stationary candidate. Movers
     * add {@link #MAX_MOVER_SPEED_CELLS} scaled by flight exposure on top of
     * this (see the margin computation in {@link #resolve}), since a mover
     * can walk into the corridor mid-flight even when its fire-tick position
     * sits outside this base margin.
     */
    public static final float GATHER_MARGIN_CELLS = 1.0f;
    /**
     * Fastest mover speed (cells/sec) assumed when scaling the gather margin
     * by flight exposure time — a tuning-surface constant per
     * {@link #GATHER_MARGIN_CELLS}'s precedent, not a per-call roster scan.
     * The fastest live mover today is ALIEN at 2.2 cells/s; 4 leaves
     * headroom for vehicles.
     */
    public static final float MAX_MOVER_SPEED_CELLS = 4f;

    /** Where a round's flight ended and what stopped it. */
    public enum StopKind { UNIT_HIT, COVER_CLIP, WALL, DOODAD_BLOCK, OVERSHOOT }

    /**
     * The outcome of one resolved round. {@code endX}/{@code endY}/{@code endZ}
     * is where the round physically stopped (visual endpoint); {@code flightTime} =
     * {@code stopDist / roundVelocity}, the delay before damage/FX apply;
     * {@code victimId} is 0 unless {@code kind == UNIT_HIT}; {@code
     * hitIntended} is true only when the recorded victim is the locked
     * target (drives {@code ShotEvent.hit}); {@code friendlyHit} is true
     * when the recorded victim shares the shooter's faction.
     */
    public record Resolution(float endX, float endY, float endZ, float flightTime,
                              long victimId, boolean hitIntended,
                              boolean friendlyHit, StopKind kind) {}

    /**
     * One contact candidate along the ray, in time order. Doodad crossings
     * roll a flat block chance; unit contacts roll cover-clip, then incidental
     * contacts retain their graze roll. Intended accuracy was already
     * committed when the target-plane aim was sampled.
     * {@code victimCellX}/{@code victimCellY} (unit events only) is the
     * victim's OWN extrapolated cell at contact time, floor(U(t)) — the
     * cover edge-clip lookup's cell, distinct from {@code x}/{@code y} (the
     * round's own FX endpoint, P(t)). See the "Contact-position split" note
     * in {@code roadmap/ballistics/stories/s2-moving-targets.md}.
     */
    private static final class Event {
        final float t;
        final boolean doodad;
        final float x;
        final float y;
        final float z;
        final int doodadLevel;
        final long unitId;
        final boolean friendly;
        final int victimCellX;
        final int victimCellY;

        private Event(float t, boolean doodad, float x, float y, float z, int doodadLevel,
                       long unitId, boolean friendly, int victimCellX, int victimCellY) {
            this.t = t;
            this.doodad = doodad;
            this.x = x;
            this.y = y;
            this.z = z;
            this.doodadLevel = doodadLevel;
            this.unitId = unitId;
            this.friendly = friendly;
            this.victimCellX = victimCellX;
            this.victimCellY = victimCellY;
        }

        static Event doodad(float t, float x, float y, float z, int level) {
            return new Event(t, true, x, y, z, level, 0L, false, 0, 0);
        }

        static Event unit(float t, float x, float y, float z, long unitId, boolean friendly,
                           int victimCellX, int victimCellY) {
            return new Event(t, false, x, y, z, 0, unitId, friendly, victimCellX, victimCellY);
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
     * where it stopped. The aim point leads the target's one-step predicted
     * position (perfect lead — tuning-neutral, a led shot against a
     * constant-velocity target contacts exactly as a stationary shot does),
     * and every unit contact is solved in the TIME domain against the
     * candidate's own extrapolated motion; a non-mover's implicit zero
     * velocity collapses both the lead and the contact solve to S1's
     * static-world math exactly. {@code finalAccuracy} is the full accuracy
     * stack with cover already removed (cover is re-expressed here as
     * physical interception); {@code effectiveSpread} broadens the sampled
     * lateral/elevation miss clearance in the target plane; {@code
     * roundVelocity} is cells/sec, already resolved by the
     * caller (see {@link #DEFAULT_ROUND_VELOCITY}). Reads only — safe to
     * call from a parallel dispatch.
     */
    public Resolution resolve(long shooter, long target,
                               float finalAccuracy, float effectiveSpread,
                               float roundVelocity, Random rng) {
        World world = roster.world();
        MovementService movement = roster.movement();
        Faction shooterFaction = roster.identity().faction(shooter);

        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        float targetX = world.renderX(target);
        float targetY = world.renderY(target);

        // Step 1: shooter lead. The aim point is the target's one-step
        // predicted position at estimated intercept time (dist / velocity),
        // not its fire-tick position — a lead without extrapolation and an
        // extrapolation without lead each systematically miss a lateral
        // mover; they only balance as a pair (see overview.md §3). wTarget
        // = 0 for a non-mover (turret/hub target), which zeroes tLead's
        // contribution and reproduces S1's aim point exactly.
        float wTargetX = 0f;
        float wTargetY = 0f;
        if (movement.has(target)) {
            wTargetX = movement.velX(target);
            wTargetY = movement.velY(target);
        }
        float tLead = dist(fromX, fromY, targetX, targetY) / roundVelocity;
        float leadX = targetX + wTargetX * tLead;
        float leadY = targetY + wTargetY * tLead;

        // Step 2: commit accuracy once in the target plane. Lateral error
        // rotates the real ground ray; elevation error becomes a linear Z
        // slope. An authored miss therefore visibly clears the intended
        // silhouette instead of crossing it and failing a hidden second roll.
        UnitType targetType = roster.identity().type(target);
        TargetPlaneAim.Sample aim = TargetPlaneAim.sample(
                finalAccuracy, world.incomingAccuracyMult(target), effectiveSpread,
                targetType.radius, targetType.hitHalfHeight, rng);
        float baseDx = leadX - fromX;
        float baseDy = leadY - fromY;
        float baseDist = (float) Math.sqrt(baseDx * baseDx + baseDy * baseDy);
        float baseDirX = baseDist > 1e-6f ? baseDx / baseDist : 1f;
        float baseDirY = baseDist > 1e-6f ? baseDy / baseDist : 0f;
        float aimX = leadX - baseDirY * aim.lateral();
        float aimY = leadY + baseDirX * aim.lateral();
        float zSlope = aim.elevation() / Math.max(baseDist, 1e-6f);

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

        // Step 3: doodad crossings — Bresenham the (wall-capped) ray's cells,
        // skipping the shooter's own cell. Doodads are static, so this stays
        // the S1 distance-domain math verbatim.
        walkDoodadCrossings(shooterCellX, shooterCellY, rayEndCellX, rayEndCellY,
                fromX, fromY, roundVelocity, zSlope, events);

        // Step 4: unit contacts over the (wall-capped) ray, solved in the
        // TIME domain against each candidate's own extrapolated motion. The
        // gather margin grows with flight exposure — a slow round's corridor
        // can be entered mid-flight by a candidate whose fire-tick position
        // sits outside the base margin.
        float margin = GATHER_MARGIN_CELLS + MAX_MOVER_SPEED_CELLS * (rayLen / roundVelocity);
        LongBucket candidates = new LongBucket();
        unitIndex.gatherAlongSegment(fromX, fromY, rayEndX, rayEndY, margin, candidates);
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

            // Velocity actually applied by this tick's movement pass; 0 for
            // a non-mover (no MOVEMENT component). MOVEMENT_VEL_X/Y are
            // zeroed+rewritten by MovementService and nudged by
            // SeparationSystem during the same tick this resolver runs
            // (parallel UPDATE_UNITS) — a mid-tick-stale read moves the
            // contact point by at most moveSpeed * TICK_DT cells. Velocity
            // is an extrapolation hint here, not an invariant, so this read
            // is intentionally unsynchronized.
            float wx = 0f;
            float wy = 0f;
            if (movement.has(candidateId)) {
                wx = movement.velX(candidateId);
                wy = movement.velY(candidateId);
            }

            // Round: P(s) = O + d*v*s (s in seconds). Candidate:
            // U(s) = U0 + w*s. Relative: R(s) = (U0 - O) + (w - v*d)*s;
            // contact when |R(s)| <= r. Quadratic a*s^2 + b*s + c = 0 in s.
            // w = 0 collapses this to S1's distance-domain ray-circle solve
            // exactly (proven by the stationary-regression test).
            float relVelX = wx - roundVelocity * dirX;
            float relVelY = wy - roundVelocity * dirY;
            float r0x = ux - fromX;
            float r0y = uy - fromY;
            float a = relVelX * relVelX + relVelY * relVelY;
            float b = 2f * (r0x * relVelX + r0y * relVelY);
            float c = r0x * r0x + r0y * r0y - r * r;

            float sEntry;
            if (a < 1e-6f) {
                // The candidate paces the round's relative closing velocity
                // exactly (rare degenerate) — R is constant over the flight,
                // so either it's already overlapping at fire time (contact
                // at s=0) or it never will be. Never falls through to the
                // sqrt below, so no NaN.
                if (c > 0f) continue;
                sEntry = 0f;
            } else {
                float disc = b * b - 4f * a * c;
                if (disc < 0f) continue; // margin match but no real intersection with the exact radius
                float sqrtDisc = (float) Math.sqrt(disc);
                float sExit = (-b + sqrtDisc) / (2f * a);
                if (sExit < 0f) continue; // circle entirely behind the shooter along this ray's timeline
                sEntry = (-b - sqrtDisc) / (2f * a);
                if (sEntry < 0f) sEntry = 0f; // shooter inside the collision radius, or the circle straddles s=0
            }

            // Wall cap and muzzle clearance stay DISTANCE tests — the wall
            // is static, so distance-along-ray is the correct cap even for
            // a moving contact.
            float rayDistAtEntry = roundVelocity * sEntry;
            if (rayDistAtEntry > rayLen) continue; // beyond the wall-capped ray

            // The ground ray may cross a body circle while the round passes
            // over its head or below its feet. Doodads and target-edge cover
            // apply their own Z gates at their respective event sites;
            // structural walls alone remain full-height.
            float contactZ = zSlope * rayDistAtEntry;
            if (Math.abs(contactZ) > candidateType.hitHalfHeight) continue;

            Faction candidateFaction = roster.identity().faction(candidateId);
            boolean friendly = candidateFaction == shooterFaction;
            if (friendly && rayDistAtEntry < FRIENDLY_MUZZLE_CLEARANCE) continue;

            // Contact-position split: the FX endpoint is where the ROUND is,
            // P(sEntry); the cover edge-clip lookup (step 5) uses the
            // VICTIM's own extrapolated cell, floor(U(sEntry)) — a target
            // sprinting out from behind a parapet has left the covered cell
            // by contact time, and vice versa.
            float contactX = fromX + dirX * rayDistAtEntry;
            float contactY = fromY + dirY * rayDistAtEntry;
            int victimCellX = (int) Math.floor(ux + wx * sEntry);
            int victimCellY = (int) Math.floor(uy + wy * sEntry);
            events.add(Event.unit(sEntry, contactX, contactY, contactZ,
                    candidateId, friendly, victimCellX, victimCellY));
        }

        // Step 5: walk events sorted by t; first stop wins. A wall (when
        // present) is never a member of this list — it necessarily sits at
        // the ray's own endpoint, so every doodad/unit event's t is <= the
        // wall's t by construction; reaching the end of the list without a
        // stop is exactly "nothing stopped it before the wall/ray end".
        events.sort((a, b) -> Float.compare(a.t, b.t));
        for (Event e : events) {
            if (e.doodad) {
                float blockChance = BLOCK_CHANCE_BY_LEVEL[Math.min(e.doodadLevel, BLOCK_CHANCE_BY_LEVEL.length - 1)];
                if (rng.nextFloat() < blockChance) {
                    return new Resolution(e.x, e.y, e.z, e.t, 0L, false, false, StopKind.DOODAD_BLOCK);
                }
                continue; // fly on
            }

            long victim = e.unitId;
            int fromDx = shooterCellX - e.victimCellX;
            int fromDy = shooterCellY - e.victimCellY;
            int coverLevel = grid.getCoverAt(e.victimCellX, e.victimCellY, fromDx, fromDy);
            float coverCatchHalfHeight = grid.getCoverCatchHalfHeight(
                    e.victimCellX, e.victimCellY, fromDx, fromDy);
            if (!intersectsCatchBand(e.z, coverCatchHalfHeight)) coverLevel = 0;
            float coverBlockChance = BLOCK_CHANCE_BY_LEVEL[Math.min(coverLevel, BLOCK_CHANCE_BY_LEVEL.length - 1)];
            if (coverLevel > 0 && rng.nextFloat() < coverBlockChance) {
                return new Resolution(e.x, e.y, e.z, e.t, 0L, false, false, StopKind.COVER_CLIP);
            }

            boolean isLockedTarget = victim == target;
            if (isLockedTarget) {
                if (!aim.onTarget()) continue;
                return new Resolution(e.x, e.y, e.z, e.t, victim, true, e.friendly, StopKind.UNIT_HIT);
            }
            float hitChance = INCIDENTAL_HIT_CHANCE * world.incomingAccuracyMult(victim);
            if (rng.nextFloat() < hitChance) {
                return new Resolution(e.x, e.y, e.z, e.t, victim, false, e.friendly, StopKind.UNIT_HIT);
            }
            // else fly on
        }

        StopKind finalKind = wallFound ? StopKind.WALL : StopKind.OVERSHOOT;
        return new Resolution(rayEndX, rayEndY, zSlope * rayLen, rayLen / roundVelocity,
                0L, false, false, finalKind);
    }

    /** Bresenham cell walk from the shooter's cell to the ray-end cell, emitting a doodad-crossing {@link Event} for every non-start cell whose physical doodad silhouette contains the round's Z. */
    private void walkDoodadCrossings(int x0, int y0, int x1, int y1,
                                      float fromX, float fromY, float roundVelocity, float zSlope,
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
                float cx = x + 0.5f;
                float cy = y + 0.5f;
                float t = dist(fromX, fromY, cx, cy) / roundVelocity;
                float z = zSlope * roundVelocity * t;
                int level = doodads.getDoodadLevelOnCell(x, y, z);
                if (level > 0) {
                    out.add(Event.doodad(t, cx, cy, z, level));
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

    private static boolean intersectsCatchBand(float z, float catchHalfHeight) {
        return catchHalfHeight > 0f && Math.abs(z) <= catchHalfHeight;
    }
}
