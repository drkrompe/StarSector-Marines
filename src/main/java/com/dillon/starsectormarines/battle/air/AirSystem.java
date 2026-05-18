package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MarineLoadout;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.ai.TurretAim;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Owns every airborne vehicle in the battle and drives them each tick.
 * Today that's just the shuttle roster; fighter wings and air-base
 * scaffolding land here as they come online.
 *
 * <p>The system is a goal-provider over an {@link AirBody} per vehicle: each
 * tick picks a waypoint and {@link SteeringMode} for the body to steer
 * toward, then lets the body's handling profile produce the actual motion.
 * Visual feel (bus pendulum vs nimble snap) comes from the per-{@link ShuttleType}
 * {@link AirHandling} tunables, not from authored curves.
 *
 * <p>Coupling to the rest of the simulation is mediated by
 * {@link AirSimContext} — a narrow contract for the four things air vehicles
 * actually need: grid access, RNG, occupancy lookups, and unit/squad
 * insertion. The sim implements that contract directly; tests can stub it.
 */
public class AirSystem {

    /** Visual scale of a shuttle at cruising altitude (sells "I am up high"). Driven by {@link Shuttle#altitudeT}; ground scale is 1.0. */
    private static final float SHUTTLE_CRUISE_SCALE = 1.5f;
    /** Frequency (Hz) of the in-flight scale wobble. Slower than a heartbeat — reads as atmospheric drift, not a flicker. */
    private static final float SHUTTLE_WOBBLE_HZ = 0.7f;
    /** Peak amplitude of the wobble, in scale units. ±0.04 on top of a 1.5 cruise = ~2.7%; well inside the 5% target. */
    private static final float SHUTTLE_WOBBLE_AMPLITUDE = 0.04f;
    /** Distance threshold (cells) at which an INCOMING shuttle snaps to the LZ and transitions to LANDED. Tight enough that the snap is invisible; loose enough that the asymptotic brake-to-station taper doesn't stall short. */
    private static final float SHUTTLE_LZ_ARRIVAL_DIST = 0.2f;
    /** Distance threshold (cells) at which a DEPARTING shuttle transitions to GONE / next cycle. Larger than the LZ threshold because exit points sit well off-map and we don't need pinpoint accuracy. */
    private static final float SHUTTLE_EXIT_ARRIVAL_DIST = 1.0f;
    /** Max BFS radius from the LZ when looking for a free deboard cell. Past this we drop the deboard for this tick. */
    private static final int DEBOARD_SCAN_RADIUS = 5;
    /** Cell radius around a flying turret's origin where walls are treated as transparent — models the shuttle being "above" its containing building. Tuned to typical building wall thickness; past this, real LOS rules apply. */
    private static final float SHUTTLE_AIR_LOS_RADIUS = 3.5f;

    private final List<Shuttle> shuttles = new ArrayList<>();

    public List<Shuttle> getShuttles() { return shuttles; }
    public void add(Shuttle s) { shuttles.add(s); }

    /**
     * Advances every airborne vehicle one tick by {@code dt} seconds. Today
     * that's the shuttle state machine + per-shuttle mounted-turret pass;
     * fighter wings hook in here as they land. Caller is responsible for
     * matching {@code dt} to its fixed-tick cadence (the auto-battler uses
     * 30 Hz / TICK_DT).
     *
     * <p>Takes {@link BattleSimulation} directly (which is itself an
     * {@link AirSimContext}) because the turret-aim path reaches into shared
     * targeting / line-of-sight code that's typed against the full sim. The
     * narrow context handle is still passed to the deboard path where the
     * surface is intentionally small.
     */
    public void tick(BattleSimulation sim, float dt) {
        advanceShuttles(sim, dt);
        tickShuttleTurrets(sim, dt);
    }

    /**
     * Advances each shuttle's state machine by one tick. PENDING burns down
     * the stagger delay; INCOMING/DEPARTING steer the {@link AirBody} toward
     * their LZ / exit waypoint under the shuttle's {@link ShuttleType}
     * handling profile; LANDED ticks a deboard timer and spawns a marine on
     * each fire.
     *
     * <p>The "boat" feel — slow buses pendulum, nimble craft snap into
     * headings — falls out of the per-type turn rate and lateral damping in
     * {@link AirHandling}. No parametric per-leg curve is needed; the arc is
     * what kinematic-limited steering produces.
     */
    private void advanceShuttles(BattleSimulation ctx, float dt) {
        for (Shuttle s : shuttles) {
            switch (s.state) {
                case PENDING:
                    s.pendingDelay -= dt;
                    if (s.pendingDelay <= 0f) {
                        beginShuttleLeg(s, s.lzX, s.lzY);
                        s.state = Shuttle.State.INCOMING;
                    }
                    break;

                case INCOMING:
                    s.body.tickToward(s.lzX, s.lzY, SteeringMode.BRAKE_TO_STATION, s.type, dt);
                    updateShuttleAltitude(s, s.lzX, s.lzY, /*incoming=*/true, dt);
                    if (s.body.distanceTo(s.lzX, s.lzY) < SHUTTLE_LZ_ARRIVAL_DIST) {
                        // Snap to the LZ — the BRAKE_TO_STATION taper is
                        // asymptotic, so without this the shuttle would creep
                        // the last fraction of a cell at near-zero speed.
                        s.body.teleport(s.lzX, s.lzY, s.body.facingDegrees);
                        s.altitudeT = 0f;
                        s.scaleMult = 1f;
                        s.state = Shuttle.State.LANDED;
                        s.deboardCountdown = s.type.deboardInterval;
                    }
                    break;

                case LANDED:
                    s.deboardCountdown -= dt;
                    if (s.deboardCountdown <= 0f && s.marinesRemaining > 0) {
                        if (tryDeboardMarine(s, ctx)) {
                            s.marinesRemaining--;
                        }
                        s.deboardCountdown = s.type.deboardInterval;
                    }
                    if (s.marinesRemaining == 0) {
                        if (s.shouldHoverLoiter()) {
                            // Lift off the LZ and station-keep above the squad
                            // for the type's fire-support window. Initial hover
                            // point is the LZ; each subsequent tick follows the
                            // squad centroid (leashed to LZ radius).
                            s.hoverPointX = s.lzX;
                            s.hoverPointY = s.lzY;
                            s.hoverTimerSec = s.type.fireSupportSec;
                            s.takeoffTimer = Shuttle.T_TAKEOFF_SEC;
                            s.altitudeT = 0f;       // smoothstep ramps from here
                            s.scaleMult = 1f;
                            s.departingFromHover = false;
                            s.state = Shuttle.State.HOVER_STATION;
                        } else {
                            beginShuttleLeg(s, s.exitX, s.exitY);
                            s.state = Shuttle.State.DEPARTING;
                        }
                    }
                    break;

                case HOVER_STATION:
                    // Follow the squad: hover point tracks the alive squad
                    // centroid, clamped to a leash radius around the LZ so a
                    // wiped squad or a runaway scout doesn't drag the shuttle
                    // across the whole map.
                    updateHoverFollow(s, ctx);
                    s.body.tickToward(s.hoverPointX, s.hoverPointY, SteeringMode.STATION, s.type, dt);
                    s.hoverTimerSec -= dt;
                    // Takeoff phase — smoothstep altitudeT 0 → 1 over
                    // T_TAKEOFF_SEC for a visible acceleration / deceleration
                    // climb instead of a one-tick pop into the air.
                    if (s.takeoffTimer > 0f) {
                        s.takeoffTimer -= dt;
                        float u = 1f - Math.max(0f, s.takeoffTimer / Shuttle.T_TAKEOFF_SEC);
                        s.altitudeT = u * u * (3f - 2f * u);  // smoothstep
                    } else {
                        s.altitudeT = 1f;
                    }
                    s.flightPhase += dt * 2f * (float) Math.PI * SHUTTLE_WOBBLE_HZ;
                    float hoverWobble = (float) Math.sin(s.flightPhase)
                            * SHUTTLE_WOBBLE_AMPLITUDE * s.altitudeT;
                    s.scaleMult = (1f + (SHUTTLE_CRUISE_SCALE - 1f) * s.altitudeT) + hoverWobble;
                    // Exit triggers — first-of (timer expired, all ammo dry,
                    // HP pressure). HP threshold is wired forward for AA work;
                    // today there's no damage source so it never trips.
                    boolean fuelOut = s.hoverTimerSec <= 0f;
                    boolean ammoOut = s.allTurretsDry();
                    boolean hpPressured = s.hp <= s.type.maxHp * Shuttle.HOVER_HP_THRESHOLD;
                    if (fuelOut || ammoOut || hpPressured) {
                        beginShuttleLeg(s, s.exitX, s.exitY);
                        s.departingFromHover = true;
                        s.state = Shuttle.State.DEPARTING;
                    }
                    break;

                case DEPARTING:
                    s.body.tickToward(s.exitX, s.exitY, SteeringMode.CRUISE, s.type, dt);
                    updateShuttleAltitude(s, s.exitX, s.exitY, /*incoming=*/false, dt);
                    if (s.body.distanceTo(s.exitX, s.exitY) < SHUTTLE_EXIT_ARRIVAL_DIST) {
                        if (s.currentCycle + 1 < s.totalCycles) {
                            // Recycle for another sortie. The shuttle drops out of
                            // view (PENDING is invisible + engine-silent) for
                            // s.rearmDelay sim-seconds, then re-enters INCOMING.
                            // Per-cycle loadout refreshes here so SABOTAGE planters
                            // target the next charge site on each return trip.
                            s.currentCycle++;
                            if (s.cycleLoadouts != null && s.currentCycle < s.cycleLoadouts.length) {
                                s.marineLoadout = s.cycleLoadouts[s.currentCycle];
                            }
                            s.marinesRemaining = s.type.capacity;
                            s.pendingDelay = s.rearmDelay;
                            s.body.teleport(s.entryX, s.entryY,
                                    AirBody.facingToward(s.lzX - s.entryX, s.lzY - s.entryY));
                            s.altitudeT = 1f;
                            s.departingFromHover = false;
                            // Re-arm: refill every mount's magazine, drop any
                            // stale target lock so the next hover starts clean.
                            for (MountedTurret mt : s.turrets) {
                                mt.ammo = mt.mount.kind.startingAmmo;
                                mt.target = null;
                                mt.cooldownTimer = 0f;
                            }
                            s.state = Shuttle.State.PENDING;
                        } else {
                            s.state = Shuttle.State.GONE;
                        }
                    }
                    break;

                case GONE:
                default:
                    break;
            }
        }
    }

    /**
     * Per-tick aim + fire pass for every {@link MountedTurret} on every
     * HOVER_STATION shuttle. Each turret uses the shared {@link TurretAim}
     * loop the static map turrets do — same acquisition / slew / fire-when-aligned
     * rules — but with the mount's world-rotated position as the origin and
     * {@link BattleSimulation#fireShotFrom} for the shot emission so a
     * hovering Valkyrie's mid-air mount fires from its actual rendered point
     * rather than the floored cell center.
     *
     * <p>Mounts in {@link Shuttle.State#LANDED} or any non-hover state skip
     * the pass — fire support is reserved for the hover window. Empty
     * turret arrays (pure transports) are a no-op.
     */
    private void tickShuttleTurrets(BattleSimulation sim, float dt) {
        for (Shuttle s : shuttles) {
            if (s.state != Shuttle.State.HOVER_STATION) continue;
            if (s.turrets.length == 0) continue;
            float rad = (float) Math.toRadians(s.body.facingDegrees);
            float c = (float) Math.cos(rad);
            float si = (float) Math.sin(rad);
            for (MountedTurret mt : s.turrets) {
                if (mt.ammoDry()) continue;
                float lx = mt.mount.localOffsetX;
                float ly = mt.mount.localOffsetY;
                // CCW rotation by `rad`. Local frame: +X = right of nose,
                // +Y = forward (nose). At facing 0 the shuttle faces +Y, so
                // local (0,1) maps to world (0,1) — north.
                float worldOffsetX = lx * c - ly * si;
                float worldOffsetY = lx * si + ly * c;
                float worldX = s.body.x + worldOffsetX;
                float worldY = s.body.y + worldOffsetY;

                TurretAim.State aim = new TurretAim.State();
                aim.originCellX = (int) Math.floor(worldX);
                aim.originCellY = (int) Math.floor(worldY);
                aim.originX = worldX;
                aim.originY = worldY;
                aim.faction = s.faction;
                aim.squadId = Unit.NO_SQUAD;
                aim.excludeFromCrowding = null;
                aim.facingDegrees = mt.facingDegrees;
                aim.turnRateDegPerSec = mt.mount.kind.turnRateDegPerSec;
                aim.attackRange = mt.mount.kind.range;
                aim.cooldownTimer = mt.cooldownTimer;
                aim.attackCooldown = mt.mount.kind.cooldown;
                aim.target = mt.target;
                aim.ignoreCloseWalls = true;
                aim.closeWallRadius = SHUTTLE_AIR_LOS_RADIUS;

                TurretAim.tick(aim, sim, dt);

                mt.facingDegrees = aim.facingDegrees;
                mt.cooldownTimer = aim.cooldownTimer;
                mt.target = aim.target;

                if (aim.fireThisTick) {
                    sim.fireShotFrom(worldX, worldY, s.faction, mt.mount.kind, aim.target);
                    mt.ammo--;
                }
            }
        }
    }

    /**
     * Caches the leg's straight-line distance so {@link #updateShuttleAltitude}
     * can lerp scale + engine intensity by remaining-distance ratio. Body
     * position is left untouched — it's already at the previous waypoint (the
     * entry point, or the LZ).
     */
    private void beginShuttleLeg(Shuttle s, float toX, float toY) {
        s.legStartDist = Math.max(0.001f, s.body.distanceTo(toX, toY));
    }

    /**
     * Per-tick altitude / scale update. {@code altitudeT} runs 1 → 0 on
     * INCOMING (high at entry, ground at LZ) and 0 → 1 on DEPARTING. Drives
     * {@link Shuttle#scaleMult} via {@link #SHUTTLE_CRUISE_SCALE} plus a small
     * sine wobble that's gated by altitudeT so it dies cleanly on the ground.
     */
    private void updateShuttleAltitude(Shuttle s, float toX, float toY, boolean incoming, float dt) {
        if (!incoming && s.departingFromHover) {
            // Departing straight out of HOVER_STATION — the shuttle is already
            // at cruise altitude, so a distance-ratio lerp from "ground" would
            // make it visibly descend and re-climb. Hold at the top.
            s.altitudeT = 1f;
        } else {
            float remaining = s.body.distanceTo(toX, toY);
            float ratio = remaining / s.legStartDist;
            if (ratio < 0f) ratio = 0f;
            if (ratio > 1f) ratio = 1f;
            s.altitudeT = incoming ? ratio : (1f - ratio);
        }
        float baseScale = 1f + (SHUTTLE_CRUISE_SCALE - 1f) * s.altitudeT;
        s.flightPhase += dt * 2f * (float) Math.PI * SHUTTLE_WOBBLE_HZ;
        float wobble = (float) Math.sin(s.flightPhase) * SHUTTLE_WOBBLE_AMPLITUDE * s.altitudeT;
        s.scaleMult = baseScale + wobble;
    }

    /**
     * Recomputes {@link Shuttle#hoverPointX}/{@code hoverPointY} from the
     * alive squad centroid, pulled back by {@link Shuttle#HOVER_STANDOFF_CELLS}
     * along the LZ→centroid bearing (rear-overwatch standoff). Holds the
     * previous value if the squad is wiped (no alive squadmates) so the
     * shuttle doesn't snap back to the LZ on the last marine's death — it
     * stays where it was supporting.
     */
    private void updateHoverFollow(Shuttle s, BattleSimulation sim) {
        if (s.squadId == Unit.NO_SQUAD) return;
        float sumX = 0f, sumY = 0f;
        int n = 0;
        for (Unit u : sim.getUnits()) {
            if (u.squadId != s.squadId) continue;
            if (!u.isAlive()) continue;
            sumX += u.cellX + 0.5f;
            sumY += u.cellY + 0.5f;
            n++;
        }
        if (n == 0) return;  // squad wiped — hold current hover point
        float cx = sumX / n;
        float cy = sumY / n;
        float dx = cx - s.lzX;
        float dy = cy - s.lzY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        // Rear-overwatch standoff: shift the hover point from centroid back
        // toward the LZ. Below the standoff radius there's no stable bearing,
        // so just hold over the LZ until the squad pushes out.
        if (dist > Shuttle.HOVER_STANDOFF_CELLS) {
            float k = (dist - Shuttle.HOVER_STANDOFF_CELLS) / dist;
            cx = s.lzX + dx * k;
            cy = s.lzY + dy * k;
        } else {
            cx = s.lzX;
            cy = s.lzY;
        }
        s.hoverPointX = cx;
        s.hoverPointY = cy;
    }

    /**
     * Finds a free cell adjacent to the LZ and spawns a marine there as a fresh
     * {@link Unit}. Returns {@code false} when no nearby cell is available this
     * tick (rare — only happens if the area around the LZ is fully clogged with
     * units or walls); caller leaves {@code marinesRemaining} unchanged and the
     * shuttle re-tries next interval.
     */
    private boolean tryDeboardMarine(Shuttle s, AirSimContext ctx) {
        int lzCellX = (int) Math.floor(s.lzX);
        int lzCellY = (int) Math.floor(s.lzY);
        int[] cell = findDeboardCell(lzCellX, lzCellY, ctx);
        if (cell == null) return false;
        Unit marine = new Unit(ctx.nextMarineId(), s.faction, UnitType.MARINE, cell[0], cell[1]);
        int slot = s.type.capacity - s.marinesRemaining;
        MarineLoadout loadout = (s.marineLoadout != null && slot < s.marineLoadout.length)
                ? s.marineLoadout[slot] : null;
        if (loadout != null) {
            marine.role = loadout.role;
            marine.assignedObjective = loadout.objective;
            // Apply primary weapon stats — overrides the UnitType.MARINE
            // defaults baked into the Unit at construction. If the loadout
            // didn't specify a weapon (legacy callers), the marine keeps
            // the type defaults and behaves as before.
            if (loadout.primary != null) {
                marine.primaryWeapon = loadout.primary;
                marine.attackRange = loadout.primary.range;
                marine.attackDamage = loadout.primary.damage;
                marine.accuracy = loadout.primary.accuracy;
                marine.attackCooldown = loadout.primary.cooldown;
            }
            if (loadout.secondary != null && loadout.secondaryAmmo > 0) {
                marine.secondaryWeapon = loadout.secondary;
                marine.secondaryAmmo = loadout.secondaryAmmo;
            }
        }
        // Squad assignment — first deboard from a shuttle mints a new squad
        // and takes the leader slot; subsequent deboards join the same squad.
        if (s.squadId == Unit.NO_SQUAD) {
            s.squadId = ctx.mintSquad(s.faction, marine);
        }
        marine.squadId = s.squadId;
        // Track peak strength on the squad so Story B's SurviveContact predicate
        // (SQUAD_BELOW_HALF_STRENGTH) has a denominator. Marines deboard one at
        // a time, so this ratchets up as the shuttle empties; once members start
        // dying it stays put. Defender squads stamp originalSize once in
        // BattleSetup — marine squads can't, since they grow incrementally.
        Squad squad = ctx.getSquad(s.squadId);
        if (squad != null) squad.originalSize++;
        ctx.addUnit(marine);
        return true;
    }

    /**
     * BFS outward from the LZ cell for the first walkable, unoccupied cell at
     * distance >= 1. Distance 0 (the LZ itself) is skipped so the marine
     * sprite doesn't draw directly under the parked shuttle. Returns
     * {@code null} if no eligible cell is found within {@link #DEBOARD_SCAN_RADIUS}.
     */
    private int[] findDeboardCell(int lzX, int lzY, AirSimContext ctx) {
        NavigationGrid grid = ctx.getGrid();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{lzX, lzY, 0});
        seen.add(((long) lzX << 32) | (lzY & 0xFFFFFFFFL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DEBOARD_SCAN_RADIUS) continue;
            if (p[2] > 0
                    && grid.inBounds(p[0], p[1])
                    && grid.isWalkable(p[0], p[1])
                    && !ctx.isCellOccupied(p[0], p[1])) {
                return new int[]{p[0], p[1]};
            }
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                long k = ((long) nx << 32) | (ny & 0xFFFFFFFFL);
                if (!seen.add(k)) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return null;
    }
}
