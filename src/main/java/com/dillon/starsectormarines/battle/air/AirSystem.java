package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.turret.TurretFireSink;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

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
 * <p>Dependencies are constructor-injected: {@link NavigationService} for
 * grid/occupancy, {@link UnitRosterService} for unit/squad lifecycle, a
 * shared {@link Random} for determinism, a unit-addition sink that
 * composites roster insertion with fog-of-war contributor registration,
 * and a {@link TurretFireSink} for the mounted-turret fire path.
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

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final UnitRegistry registry;
    private final TacticalScoring tacticalScoring;
    private final TurretFireSink fireSink;
    private final Random rng;
    private final Consumer<Unit> addUnitSink;

    private final List<Shuttle> shuttles = new ArrayList<>();

    public AirSystem(NavigationService navigation, UnitRosterService roster,
                     TacticalScoring tacticalScoring, TurretFireSink fireSink,
                     Random rng, Consumer<Unit> addUnitSink) {
        this.navigation = navigation;
        this.roster = roster;
        this.registry = roster.getRegistry();
        this.tacticalScoring = tacticalScoring;
        this.fireSink = fireSink;
        this.rng = rng;
        this.addUnitSink = addUnitSink;
    }

    public List<Shuttle> getShuttles() { return shuttles; }
    public void add(Shuttle s) { shuttles.add(s); }

    public void tick(float dt) {
        advanceShuttles(dt);
        tickShuttleTurrets(dt);
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
    private void advanceShuttles(float dt) {
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
                        if (tryDeboardMarine(s)) {
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
                    updateHoverFollow(s);
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
                            // Each sortie spawns an independent squad — without
                            // this reset, marines from cycle N+1 reinforce the
                            // surviving squad from cycle N instead of forming
                            // a fresh fireteam at the LZ.
                            s.squadId = Unit.NO_SQUAD;
                            s.body.teleport(s.entryX, s.entryY,
                                    AirBody.facingToward(s.lzX - s.entryX, s.lzY - s.entryY));
                            s.altitudeT = 1f;
                            s.departingFromHover = false;
                            // Re-arm: refill every mount's magazine, drop any
                            // stale target lock so the next hover starts clean.
                            for (MountedTurret mt : s.turrets) {
                                mt.ammo = mt.mount.kind.startingAmmo;
                                mt.targetId = 0L;
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

    private void tickShuttleTurrets(float dt) {
        for (Shuttle s : shuttles) {
            if (!s.isVisible()) continue;
            if (s.turrets.length == 0) continue;
            float rad = (float) Math.toRadians(s.body.facingDegrees);
            float c = (float) Math.cos(rad);
            float si = (float) Math.sin(rad);
            for (MountedTurret mt : s.turrets) {
                // Age the per-shot recoil timer every tick; reset to 0 on each
                // fired round below. Lets the renderer cycle the barrel slide
                // per round during a burst, not just on the trigger pull.
                mt.recoilTimer += dt;

                if (mt.ammoDry()) {
                    // Mag dry mid-burst — drop any pending rounds so the mount
                    // doesn't stay in a never-firing burst state.
                    mt.burstRemaining = 0;
                    mt.burstTargetId = 0L;
                    continue;
                }
                // Resolve the burst victim once per tick — null surfaces both
                // "released from registry" and "id was 0L all along," same path
                // as TurretBehavior's MapTurret-shadow read.
                Unit currentBurstTarget = registry.getOrNull(mt.burstTargetId);
                if (mt.burstRemaining > 0 && currentBurstTarget == null) {
                    // A burst whose victim died is dead too — release the lock so
                    // the aim loop can re-acquire a fresh target next tick.
                    mt.burstRemaining = 0;
                    mt.burstTargetId = 0L;
                    currentBurstTarget = null;
                }
                // Pin the slew target during a burst so the barrel tracks the
                // salvo victim even if a closer enemy walked into LOS mid-burst.
                // Direct id-to-id copy (not setTarget) — both fields are entity
                // ids in the same id space, no null encoding to apply.
                if (mt.burstRemaining > 0) {
                    mt.targetId = mt.burstTargetId;
                }

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
                aim.minRange = mt.mount.kind.minRange;
                aim.cooldownTimer = mt.cooldownTimer;
                aim.attackCooldown = mt.mount.kind.cooldown;
                aim.target = registry.getOrNull(mt.targetId);
                aim.ignoreCloseWalls = true;
                aim.closeWallRadius = SHUTTLE_AIR_LOS_RADIUS;

                TurretAim.tick(aim, tacticalScoring, navigation.getGrid(), dt);

                mt.facingDegrees = aim.facingDegrees;
                mt.cooldownTimer = aim.cooldownTimer;
                mt.setTarget(aim.target);

                // Shot origin Y carries the shuttle's visual altitude so the
                // rendered round leaves the turret at its drawn position
                // (body.y + altOffset), not the ground projection it sits over.
                // Sim LOS / aim still use the ground-projection worldY above —
                // that's the right cell for "what wall is this shuttle hovering
                // over" decisions. This offset is purely a render-origin nudge.
                float shotOriginY = worldY + s.visualAltitudeOffsetCells();

                // Burst continuation runs ahead of fresh trigger pulls. The
                // mount commits to its salvo target — closer enemies walking
                // into LOS don't interrupt rounds already on the clock.
                if (mt.burstRemaining > 0) {
                    mt.burstTimer -= dt;
                    if (mt.burstTimer <= 0f) {
                        fireSink.fire(worldX, shotOriginY, s.faction, mt.mount.kind, currentBurstTarget, /*aerialShooter*/ true);
                        mt.recoilTimer = 0f;
                        mt.ammo--;
                        mt.burstRemaining--;
                        mt.burstTimer = mt.mount.kind.burstSpacing;
                        if (mt.burstRemaining == 0) mt.burstTargetId = 0L;
                    }
                    continue;
                }

                if (aim.fireThisTick) {
                    fireSink.fire(worldX, shotOriginY, s.faction, mt.mount.kind, aim.target, /*aerialShooter*/ true);
                    mt.recoilTimer = 0f;
                    mt.ammo--;
                    // Burst weapons latch the remaining rounds; single-shot
                    // kinds (burstCount == 1) skip this and behave as before.
                    if (mt.mount.kind.burstCount > 1
                            && aim.target != null && aim.target.isAlive()) {
                        mt.burstRemaining = mt.mount.kind.burstCount - 1;
                        mt.burstTimer = mt.mount.kind.burstSpacing;
                        mt.setBurstTarget(aim.target);
                    }
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
    private void updateHoverFollow(Shuttle s) {
        if (s.squadId == Unit.NO_SQUAD) return;
        float sumX = 0f, sumY = 0f;
        int n = 0;
        for (Unit u : roster.getUnits()) {
            if (u.squadId != s.squadId) continue;
            if (!u.isAlive()) continue;
            sumX += u.getCellX() + 0.5f;
            sumY += u.getCellY() + 0.5f;
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
    private boolean tryDeboardMarine(Shuttle s) {
        int lzCellX = (int) Math.floor(s.lzX);
        int lzCellY = (int) Math.floor(s.lzY);
        int[] cell = findDeboardCell(lzCellX, lzCellY);
        if (cell == null) return false;
        UnitType deboardType = (s.deboardUnitType != null)
                ? s.deboardUnitType
                : FactionUnitRoster.forFaction(s.faction).infantry();
        Unit marine = new Unit(roster.nextMarineId(), s.faction, deboardType, cell[0], cell[1]);
        int slot = s.type.capacity - s.marinesRemaining;
        MarineLoadout loadout = (s.marineLoadout != null && slot < s.marineLoadout.length)
                ? s.marineLoadout[slot] : null;
        if (loadout != null) {
            marine.role = loadout.role;
            marine.assignedObjective = loadout.objective;
            if (loadout.primary != null) {
                marine.primaryWeapon = loadout.primary;
                marine.setAttackRange(loadout.primary.range);
                marine.setAttackDamage(loadout.primary.damage);
                marine.setAccuracy(loadout.primary.accuracy);
                marine.attackCooldown = loadout.primary.cooldown;
            }
            if (loadout.secondary != null && loadout.secondaryAmmo > 0) {
                marine.secondaryWeapon = loadout.secondary;
                marine.secondaryAmmo = loadout.secondaryAmmo;
            }
        }
        if (s.squadId == Unit.NO_SQUAD) {
            s.squadId = roster.mintSquad(s.faction, marine);
            // Garrison drops are born holding their compound: stamp HOLD_NODE so
            // the squad runs GarrisonCompound from its first tick rather than
            // idling until a commander assignment (and so the commander leaves
            // it on station — Pass 1/2 skip HOLD_NODE squads). See Shuttle#garrisonNode.
            if (s.garrisonNode != null) {
                Squad garrison = roster.getSquad(s.squadId);
                if (garrison != null) garrison.assignHoldNode(s.garrisonNode);
            }
        }
        marine.squadId = s.squadId;
        Squad squad = roster.getSquad(s.squadId);
        if (squad != null) squad.originalSize++;
        addUnitSink.accept(marine);
        return true;
    }

    /**
     * BFS outward from the LZ cell for the first walkable, unoccupied cell at
     * distance >= 1. Distance 0 (the LZ itself) is skipped so the marine
     * sprite doesn't draw directly under the parked shuttle. Returns
     * {@code null} if no eligible cell is found within {@link #DEBOARD_SCAN_RADIUS}.
     */
    private int[] findDeboardCell(int lzX, int lzY) {
        NavigationGrid grid = navigation.getGrid();
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
                    && !navigation.isCellOccupied(p[0], p[1])) {
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
