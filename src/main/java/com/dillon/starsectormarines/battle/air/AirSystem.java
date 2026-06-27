package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.EngineSlotData;
import com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver;
import com.dillon.starsectormarines.battle.air.engine.ThrusterFx;
import com.dillon.starsectormarines.battle.air.engine.ThrusterFxSystem;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretFireSink;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

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

    private static final Logger LOG = Global.getLogger(AirSystem.class);

    /** Cell radius within which an enemy defense post threatens an airborne shuttle (the AA bubble). */
    private static final float AA_THREAT_RADIUS_CELLS = 14f;
    /**
     * HP/sec each enemy defense post in range drains from an airborne shuttle. Gentle by design — a
     * lone post merely taxes a pass, but a cluster (a hot drop zone) shreds the wave. Tuned against
     * {@code AEROSHUTTLE} maxHp = 60: one post for a ~3s descent leg costs ~18 (survivable), three
     * posts shred it. Re-dial freely. (S3d D3; the standalone battle's arrival shuttles feel it too.)
     */
    private static final float AA_DPS_PER_POST = 6f;

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
    private final TacticalScoring tacticalScoring;
    private final World world;
    private final TurretFireSink fireSink;
    private final Random rng;
    private final Consumer<Entity> addUnitSink;

    private final List<Shuttle> shuttles = new ArrayList<>();

    /**
     * The raw entity world + its component registry, cached from {@link #roster}.
     * Air FX/turret state lives in the world's {@code THRUSTER_FX}/{@code AIR_TURRETS}
     * OBJECT columns (read via the {@link #world} facade's has-gated accessors);
     * these two are passed straight to {@link ThrusterFxSystem#advance} for its
     * lazy attach (avoiding a {@code battle.air.engine → battle.sim} cycle the
     * {@code World} facade would introduce).
     */
    private final EntityWorld entityWorld;
    private final BattleComponents components;

    /**
     * The air-craft spawn archetype {@code {AIR_IDENTITY, KINEMATICS,
     * SHUTTLE_MISSION, APPEARANCE}} — adopted into the one entity world by
     * {@link #add}. Cached once (the component types are world-lifetime). No
     * grid/combat components, so every grid walk skips air for free.
     */
    private final ComponentType[] shuttleArchetype;

    public AirSystem(NavigationService navigation, UnitRosterService roster,
                     TacticalScoring tacticalScoring, World world, TurretFireSink fireSink,
                     Random rng, Consumer<Entity> addUnitSink) {
        this.navigation = navigation;
        this.roster = roster;
        this.tacticalScoring = tacticalScoring;
        this.world = world;
        this.fireSink = fireSink;
        this.rng = rng;
        this.addUnitSink = addUnitSink;
        this.entityWorld = roster.entityWorld();
        this.components = roster.components();
        this.shuttleArchetype = new ComponentType[]{
                components.AIR_IDENTITY, components.KINEMATICS, components.SHUTTLE_MISSION,
                components.APPEARANCE};
    }

    /**
     * The live air-entity ids — every craft the world holds, collected from the
     * {@code airCraft} query (so a {@code world.destroy}'d craft drops out for
     * free). The render/audio/objective consumers walk this in place of the
     * retired {@code List<Shuttle>}; each reads the craft's state by id via the
     * {@link World} facade. Air is a tiny population, so the per-call {@code long[]}
     * is negligible.
     */
    public long[] airEntityIds() {
        int n = 0;
        for (ArchetypeTable t : entityWorld.matched(components.airCraft)) n += t.rowCount();
        long[] ids = new long[n];
        int i = 0;
        for (ArchetypeTable t : entityWorld.matched(components.airCraft)) {
            for (int r = 0, rc = t.rowCount(); r < rc; r++) ids[i++] = t.entityAt(r);
        }
        return ids;
    }

    /**
     * Spawns one shuttle: builds its {@link AirBody} + {@link ShuttleMission},
     * mints a world entity from the shared id authority
     * ({@link UnitRosterService#allocateAir}), seeds the air-archetype columns, and
     * returns the entity id. Callers configure the rest by id —
     * {@code world.mission(id)} for the mission bag (cycles, loadouts, garrison
     * node, …) and {@link BattleSimulation#attachAirTurrets} for the optional
     * turret kit. Replaces the old {@code new Shuttle(...)} + {@code add(...)}: the
     * handle is internal to this system now (it dissolves entirely in the air
     * epic's final slice).
     */
    public long spawn(ShuttleType type, Faction faction,
                      float lzX, float lzY, float entryX, float entryY,
                      float exitX, float exitY, float pendingDelay) {
        Shuttle s = new Shuttle(type, faction, lzX, lzY, entryX, entryY, exitX, exitY, pendingDelay);
        s.entityId = roster.allocateAir(shuttleArchetype);
        world.setAirIdentity(s.entityId, type, faction);
        world.setKinematics(s.entityId, s.body);
        world.setMission(s.entityId, s.mission);
        // Seed the authored render-state column (cruise altitude, zero wobble
        // phase). The state-machine tick drives it thereafter; the render/audio
        // passes read it by id.
        world.setAltitudeT(s.entityId, 1f);
        world.setFlightPhase(s.entityId, 0f);
        shuttles.add(s);
        return s.entityId;
    }

    /**
     * The smoothed per-slot engine-FX demand for {@code entityId}, or
     * {@code null} if that air entity has no engine slots / no FX component yet.
     * The render + light passes feed this to {@code EngineFxRenderer} as the
     * per-slot demand, so plumes ramp instead of snapping.
     */
    public float[] thrusterGlow(long entityId) {
        ThrusterFx fx = world.thrusterFx(entityId);
        return fx == null ? null : fx.smoothed;
    }

    /**
     * Attaches a turret loadout to an air entity (presence component). No-op for
     * a null/empty loadout — a craft with no mounts simply carries no
     * {@link AirTurrets} component. Called at setup once the entity id is minted.
     */
    public void attachTurrets(long entityId, MountedTurret[] mounts) {
        if (mounts != null && mounts.length > 0) {
            world.attachAirTurrets(entityId, new AirTurrets(mounts));
        }
    }

    /** The craft's mounts, or {@code null} if it carries no turret component. Read by the shuttle render pass. */
    public MountedTurret[] mountsFor(long entityId) {
        AirTurrets t = world.airTurrets(entityId);
        return t == null ? null : t.mounts;
    }

    /**
     * The single authoritative release point for an air entity — drops every
     * component this system holds for {@code entityId}. Called at the one death
     * transition (DEPARTING → GONE). <b>Every</b> future removal path (AA
     * shoot-down — {@link ShuttleMission#hp} / {@link Shuttle#HOVER_HP_THRESHOLD}
     * are wired forward for it — or pruning GONE craft from the list) MUST funnel
     * through here. Register each new optional air component's removal in this one
     * method and adding a component can't reintroduce an orphan leak. The entity
     * itself stays alive (its core {@code AIR_IDENTITY/KINEMATICS/SHUTTLE_MISSION}
     * row persists); the terminal {@code world.destroy} is the handle-dissolution
     * phase's job.
     */
    private void releaseAirEntity(long entityId) {
        world.removeThrusterFx(entityId);
        world.removeAirTurrets(entityId);
        // Future optional air components drop here too.
    }

    /**
     * True when this craft is armed and assigned a fire-support role — after
     * LANDED → marinesRemaining==0, gates the HOVER_STATION transition vs. the
     * immediate DEPARTING path. Presence of the {@link AirTurrets} component IS
     * "armed."
     */
    private boolean shouldHoverLoiter(Shuttle s) {
        return s.mission.assignedRole != null && world.hasAirTurrets(s.entityId);
    }

    /** True when every mounted turret has fired dry (or the craft is unarmed) — a HOVER_STATION exit trigger. */
    private boolean allTurretsDry(Shuttle s) {
        AirTurrets t = world.airTurrets(s.entityId);
        return t == null || t.allDry();
    }

    public void tick(float dt) {
        advanceShuttles(dt);
        tickAirThreat(dt);
        tickShuttleTurrets(dt);
        advanceThrusterFx(dt);
    }

    /**
     * Anti-air: each airborne shuttle within range of an enemy defense post (turret) takes HP drain,
     * summed over every post in its AA bubble. At zero HP it's shot down — the marines still aboard are
     * lost, so a hot drop zone yields a partial-success wave (S3d D3). This is the first damage source
     * for {@link ShuttleMission#hp}, so the {@link Shuttle#HOVER_HP_THRESHOLD} loiter-abort also goes
     * live here. Area drain, not lock-on projectiles — the same "structures threaten an area" model as
     * ground-vs-infantry. Posts come from the spatial index, so this is O(shuttles × small bucket).
     */
    private void tickAirThreat(float dt) {
        if (shuttles.isEmpty()) return;
        ArrayList<Entity> scratch = new ArrayList<>();
        for (Shuttle s : shuttles) {
            if (!isAirborneHittable(s)) continue;
            scratch.clear();
            navigation.getUnitIndex().gather(Math.round(s.body.x), Math.round(s.body.y),
                    AA_THREAT_RADIUS_CELLS, scratch);
            int posts = 0;
            for (int i = 0, n = scratch.size(); i < n; i++) {
                Entity e = scratch.get(i);
                if (e.faction == s.faction) continue;
                if (!(e instanceof MapTurret)) continue;     // defense posts only — not infantry / mechs
                if (!world.isAlive(e.entityId)) continue;
                posts++;
            }
            if (posts == 0) continue;
            s.mission.hp -= posts * AA_DPS_PER_POST * dt;
            if (s.mission.hp <= 0f) shootDown(s, posts);
        }
    }

    /**
     * Airborne states an AA post can hit — the descent gauntlet, the armed loiter, and the egress. A
     * LANDED shuttle deboarding on the ground is exempt (it's already "down").
     */
    private static boolean isAirborneHittable(Shuttle s) {
        ShuttleState st = s.mission.state;
        return st == ShuttleState.INCOMING || st == ShuttleState.HOVER_STATION
                || st == ShuttleState.DEPARTING;
    }

    /**
     * Shoot-down: the shuttle dies in the air with its undelivered marines aboard. Terminal like the
     * DEPARTING→GONE transition — set GONE and release its optional air components through the single
     * death seam ({@link #releaseAirEntity}).
     */
    private void shootDown(Shuttle s, int posts) {
        s.mission.state = ShuttleState.GONE;
        releaseAirEntity(s.entityId);
        LOG.info("air: shuttle " + s.type + " shot down by " + posts + " AA post(s) with "
                + s.mission.marinesRemaining + " marine(s) still aboard.");
    }

    /**
     * Ramps each live shuttle's smoothed thruster demand toward the
     * {@link com.dillon.starsectormarines.battle.air.engine.ThrusterDemand}
     * target (computed from the freshly-steered body). GONE craft drop their
     * component so the {@code THRUSTER_FX} column tracks only live air entities.
     */
    private void advanceThrusterFx(float dt) {
        for (Shuttle s : shuttles) {
            // GONE craft already released their components at the death
            // transition; skip (don't re-advance, which would resurrect them).
            if (s.mission.state == ShuttleState.GONE) continue;
            // PENDING (off-map re-arm) intentionally keeps advancing: the cycle
            // teleport zeroes the body, so demand decays to 0 over the rearm
            // window and the next INCOMING sortie spools the plumes up from cold.
            EngineSlotData[] slots = EngineSlotResolver.resolve(s.type);
            ThrusterFxSystem.advance(s.entityId, slots, s.body, s.type, entityWorld, components, dt);
        }
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
            switch (s.mission.state) {
                case PENDING:
                    s.mission.pendingDelay -= dt;
                    if (s.mission.pendingDelay <= 0f) {
                        beginShuttleLeg(s, s.mission.lzX, s.mission.lzY);
                        s.mission.state = ShuttleState.INCOMING;
                    }
                    break;

                case INCOMING:
                    AirSteeringSystem.steer(s.body, s.mission.lzX, s.mission.lzY, SteeringMode.BRAKE_TO_STATION, s.type, dt);
                    updateShuttleAltitude(s, s.mission.lzX, s.mission.lzY, /*incoming=*/true, dt);
                    if (s.body.distanceTo(s.mission.lzX, s.mission.lzY) < SHUTTLE_LZ_ARRIVAL_DIST) {
                        s.body.teleport(s.mission.lzX, s.mission.lzY, s.body.facingDegrees);
                        world.setAltitudeT(s.entityId, 0f);
                        s.mission.state = ShuttleState.LANDED;
                        s.mission.deboardCountdown = s.type.deboardInterval;
                    }
                    break;

                case LANDED:
                    s.mission.deboardCountdown -= dt;
                    if (s.mission.deboardCountdown <= 0f && s.mission.marinesRemaining > 0) {
                        if (tryDeboardMarine(s)) {
                            s.mission.marinesRemaining--;
                        }
                        s.mission.deboardCountdown = s.type.deboardInterval;
                    }
                    if (s.mission.marinesRemaining == 0) {
                        if (shouldHoverLoiter(s)) {
                            // Lift off the LZ and station-keep above the squad
                            // for the type's fire-support window. Initial hover
                            // point is the LZ; each subsequent tick follows the
                            // squad centroid (leashed to LZ radius).
                            s.mission.hoverPointX = s.mission.lzX;
                            s.mission.hoverPointY = s.mission.lzY;
                            s.mission.hoverTimerSec = s.type.fireSupportSec;
                            s.mission.takeoffTimer = Shuttle.T_TAKEOFF_SEC;
                            world.setAltitudeT(s.entityId, 0f);   // smoothstep ramps from here
                            s.mission.departingFromHover = false;
                            s.mission.state = ShuttleState.HOVER_STATION;
                        } else {
                            beginShuttleLeg(s, s.mission.exitX, s.mission.exitY);
                            s.mission.state = ShuttleState.DEPARTING;
                        }
                    }
                    break;

                case HOVER_STATION:
                    // Follow the squad: hover point tracks the alive squad
                    // centroid, clamped to a leash radius around the LZ so a
                    // wiped squad or a runaway scout doesn't drag the shuttle
                    // across the whole map.
                    updateHoverFollow(s);
                    AirSteeringSystem.steer(s.body, s.mission.hoverPointX, s.mission.hoverPointY, SteeringMode.STATION, s.type, dt);
                    s.mission.hoverTimerSec -= dt;
                    // Takeoff phase — smoothstep altitudeT 0 → 1 over
                    // T_TAKEOFF_SEC for a visible acceleration / deceleration
                    // climb instead of a one-tick pop into the air.
                    float hoverAltitudeT;
                    if (s.mission.takeoffTimer > 0f) {
                        s.mission.takeoffTimer -= dt;
                        float u = 1f - Math.max(0f, s.mission.takeoffTimer / Shuttle.T_TAKEOFF_SEC);
                        hoverAltitudeT = u * u * (3f - 2f * u);  // smoothstep
                    } else {
                        hoverAltitudeT = 1f;
                    }
                    world.setAltitudeT(s.entityId, hoverAltitudeT);
                    world.setFlightPhase(s.entityId, world.flightPhase(s.entityId)
                            + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
                    // Exit triggers — first-of (timer expired, all ammo dry,
                    // HP pressure). HP threshold is wired forward for AA work;
                    // today there's no damage source so it never trips.
                    boolean fuelOut = s.mission.hoverTimerSec <= 0f;
                    boolean ammoOut = allTurretsDry(s);
                    boolean hpPressured = s.mission.hp <= s.type.maxHp * Shuttle.HOVER_HP_THRESHOLD;
                    if (fuelOut || ammoOut || hpPressured) {
                        beginShuttleLeg(s, s.mission.exitX, s.mission.exitY);
                        s.mission.departingFromHover = true;
                        s.mission.state = ShuttleState.DEPARTING;
                    }
                    break;

                case DEPARTING:
                    AirSteeringSystem.steer(s.body, s.mission.exitX, s.mission.exitY, SteeringMode.CRUISE, s.type, dt);
                    updateShuttleAltitude(s, s.mission.exitX, s.mission.exitY, /*incoming=*/false, dt);
                    if (s.body.distanceTo(s.mission.exitX, s.mission.exitY) < SHUTTLE_EXIT_ARRIVAL_DIST) {
                        if (s.mission.currentCycle + 1 < s.mission.totalCycles) {
                            // Recycle for another sortie. The shuttle drops out of
                            // view (PENDING is invisible + engine-silent) for
                            // s.rearmDelay sim-seconds, then re-enters INCOMING.
                            // Per-cycle loadout refreshes here so SABOTAGE planters
                            // target the next charge site on each return trip.
                            s.mission.currentCycle++;
                            if (s.mission.cycleLoadouts != null && s.mission.currentCycle < s.mission.cycleLoadouts.length) {
                                s.mission.marineLoadout = s.mission.cycleLoadouts[s.mission.currentCycle];
                            }
                            s.mission.marinesRemaining = s.type.capacity;
                            s.mission.pendingDelay = s.mission.rearmDelay;
                            // The re-arm is a full refit at the carrier, so repair the hull too —
                            // without this, AA damage (D3) carries across sorties and a cycling
                            // shuttle dies early on a later run despite "re-arming". Symmetric with
                            // the magazine refill below.
                            s.mission.hp = s.type.maxHp;
                            // Each sortie spawns an independent squad — without
                            // this reset, marines from cycle N+1 reinforce the
                            // surviving squad from cycle N instead of forming
                            // a fresh fireteam at the LZ.
                            s.mission.squadId = Entity.NO_SQUAD;
                            s.body.teleport(s.mission.entryX, s.mission.entryY,
                                    AirBody.facingToward(s.mission.lzX - s.mission.entryX, s.mission.lzY - s.mission.entryY));
                            world.setAltitudeT(s.entityId, 1f);
                            s.mission.departingFromHover = false;
                            // Re-arm: refill every mount's magazine, drop any
                            // stale target lock so the next hover starts clean.
                            AirTurrets rearm = world.airTurrets(s.entityId);
                            if (rearm != null) {
                                for (MountedTurret mt : rearm.mounts) {
                                    mt.ammo = mt.mount.kind.startingAmmo;
                                    mt.targetId = 0L;
                                    mt.cooldownTimer = 0f;
                                }
                            }
                            s.mission.state = ShuttleState.PENDING;
                        } else {
                            // Terminal — the craft is done. Release all its air
                            // components in one place (the single death seam).
                            s.mission.state = ShuttleState.GONE;
                            releaseAirEntity(s.entityId);
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
            if (!s.mission.isVisible()) continue;
            // Presence: only armed craft carry an AirTurrets component.
            AirTurrets t = world.airTurrets(s.entityId);
            if (t == null) continue;
            float rad = (float) Math.toRadians(s.body.facingDegrees);
            float c = (float) Math.cos(rad);
            float si = (float) Math.sin(rad);
            for (MountedTurret mt : t.mounts) {
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
                Entity currentBurstTarget = roster.getOrNull(mt.burstTargetId);
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

                // Per-mount world position: the hull-local slot offset (scraped
                // from the hull's weaponSlots at the global density) rotated by
                // body facing. Sim passes extraScale=1 — a ground-projected,
                // sim-real position; the renderer adds the altitude zoom. Because
                // mounts sit at the real, fore-aft-spread hardpoints, each mount's
                // LoS (resolved per-State below) differs front-to-rear. Same
                // helper the render pass uses, so a round fires from where it's drawn.
                float worldX = mt.worldX(s.body, c, si, 1f);
                float worldY = mt.worldY(s.body, c, si, 1f);

                TurretAim.State aim = new TurretAim.State();
                aim.originCellX = (int) Math.floor(worldX);
                aim.originCellY = (int) Math.floor(worldY);
                aim.originX = worldX;
                aim.originY = worldY;
                aim.faction = s.faction;
                aim.squadId = Entity.NO_SQUAD;
                aim.excludeFromCrowding = null;
                aim.facingDegrees = mt.facingDegrees;
                aim.turnRateDegPerSec = mt.mount.kind.turnRateDegPerSec;
                aim.attackRange = mt.mount.kind.range;
                aim.minRange = mt.mount.kind.minRange;
                aim.cooldownTimer = mt.cooldownTimer;
                aim.attackCooldown = mt.mount.kind.cooldown;
                aim.target = roster.getOrNull(mt.targetId);
                aim.ignoreCloseWalls = true;
                aim.closeWallRadius = SHUTTLE_AIR_LOS_RADIUS;

                TurretAim.tick(aim, tacticalScoring, navigation.getGrid(), world, dt);

                mt.facingDegrees = aim.facingDegrees;
                mt.cooldownTimer = aim.cooldownTimer;
                mt.setTarget(aim.target);

                // Shot origin Y carries the shuttle's visual altitude so the
                // rendered round leaves the turret at its drawn position
                // (body.y + altOffset), not the ground projection it sits over.
                // Sim LOS / aim still use the ground-projection worldY above —
                // that's the right cell for "what wall is this shuttle hovering
                // over" decisions. This offset is purely a render-origin nudge.
                float shotOriginY = worldY + AirAppearance.visualAltitudeOffsetCells(world.altitudeT(s.entityId));

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
                            && aim.target != null && world.isAlive(aim.target.entityId)) {
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
        s.mission.legStartDist = Math.max(0.001f, s.body.distanceTo(toX, toY));
    }

    /**
     * Per-tick altitude update. Writes the APPEARANCE {@code altitudeT} (1 → 0 on
     * INCOMING — high at entry, ground at LZ — and 0 → 1 on DEPARTING) and advances
     * the wobble {@code flightPhase}. The derived render scale is
     * {@link AirAppearance#scaleMult(float, float)} (a sine wobble gated by
     * altitudeT so it dies cleanly on the ground), computed at render time.
     */
    private void updateShuttleAltitude(Shuttle s, float toX, float toY, boolean incoming, float dt) {
        float altitudeT;
        if (!incoming && s.mission.departingFromHover) {
            // Departing straight out of HOVER_STATION — the shuttle is already
            // at cruise altitude, so a distance-ratio lerp from "ground" would
            // make it visibly descend and re-climb. Hold at the top.
            altitudeT = 1f;
        } else {
            float remaining = s.body.distanceTo(toX, toY);
            float ratio = remaining / s.mission.legStartDist;
            if (ratio < 0f) ratio = 0f;
            if (ratio > 1f) ratio = 1f;
            altitudeT = incoming ? ratio : (1f - ratio);
        }
        world.setAltitudeT(s.entityId, altitudeT);
        // Advance the wobble phase; the scale multiplier is derived from
        // altitudeT + flightPhase by AirAppearance at render time, not stored.
        world.setFlightPhase(s.entityId, world.flightPhase(s.entityId)
                + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
    }

    /**
     * Recomputes {@link ShuttleMission#hoverPointX}/{@code hoverPointY} from the
     * alive squad centroid, pulled back by {@link Shuttle#HOVER_STANDOFF_CELLS}
     * along the LZ→centroid bearing (rear-overwatch standoff). Holds the
     * previous value if the squad is wiped (no alive squadmates) so the
     * shuttle doesn't snap back to the LZ on the last marine's death — it
     * stays where it was supporting.
     */
    private void updateHoverFollow(Shuttle s) {
        if (s.mission.squadId == Entity.NO_SQUAD) return;
        float sumX = 0f, sumY = 0f;
        int n = 0;
        for (int i = 0, live = roster.liveCount(); i < live; i++) {
            Entity u = roster.get(i);
            if (u.squadId != s.mission.squadId) continue;
            sumX += world.cellX(u.entityId) + 0.5f;
            sumY += world.cellY(u.entityId) + 0.5f;
            n++;
        }
        if (n == 0) return;  // squad wiped — hold current hover point
        float cx = sumX / n;
        float cy = sumY / n;
        float dx = cx - s.mission.lzX;
        float dy = cy - s.mission.lzY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        // Rear-overwatch standoff: shift the hover point from centroid back
        // toward the LZ. Below the standoff radius there's no stable bearing,
        // so just hold over the LZ until the squad pushes out.
        if (dist > Shuttle.HOVER_STANDOFF_CELLS) {
            float k = (dist - Shuttle.HOVER_STANDOFF_CELLS) / dist;
            cx = s.mission.lzX + dx * k;
            cy = s.mission.lzY + dy * k;
        } else {
            cx = s.mission.lzX;
            cy = s.mission.lzY;
        }
        s.mission.hoverPointX = cx;
        s.mission.hoverPointY = cy;
    }

    /**
     * Finds a free cell adjacent to the LZ and spawns a marine there as a fresh
     * {@link Entity}. Returns {@code false} when no nearby cell is available this
     * tick (rare — only happens if the area around the LZ is fully clogged with
     * units or walls); caller leaves {@code marinesRemaining} unchanged and the
     * shuttle re-tries next interval.
     */
    private boolean tryDeboardMarine(Shuttle s) {
        int lzCellX = (int) Math.floor(s.mission.lzX);
        int lzCellY = (int) Math.floor(s.mission.lzY);
        int[] cell = findDeboardCell(lzCellX, lzCellY);
        if (cell == null) return false;
        UnitType deboardType = (s.mission.deboardUnitType != null)
                ? s.mission.deboardUnitType
                : FactionUnitRoster.forFaction(s.faction).infantry();
        Entity marine = new Entity(roster.nextMarineId(), s.faction, deboardType, cell[0], cell[1]);
        int slot = s.type.capacity - s.mission.marinesRemaining;
        MarineLoadout loadout = (s.mission.marineLoadout != null && slot < s.mission.marineLoadout.length)
                ? s.mission.marineLoadout[slot] : null;
        if (loadout != null) {
            marine.role = loadout.role;
            marine.assignedObjective = loadout.objective;
            if (loadout.primary != null) {
                marine.primaryWeapon = loadout.primary;
                // Pre-allocate seed (marine not yet added to the registry).
                marine.seedAttackRange = loadout.primary.range;
                marine.seedAttackDamage = loadout.primary.damage;
                marine.seedAccuracy = loadout.primary.accuracy;
                marine.attackCooldown = loadout.primary.cooldown;
            }
            if (loadout.secondary != null && loadout.secondaryAmmo > 0) {
                // Pre-allocate seed — allocate adds the SECONDARY_WEAPON component.
                marine.seedSecondaryWeapon = loadout.secondary;
                marine.seedSecondaryAmmo = loadout.secondaryAmmo;
            }
        }
        if (s.mission.squadId == Entity.NO_SQUAD) {
            s.mission.squadId = roster.mintSquad(s.faction, marine);
            // Garrison drops are born holding their compound: stamp HOLD_NODE so
            // the squad runs GarrisonCompound from its first tick rather than
            // idling until a commander assignment (and so the commander leaves
            // it on station — Pass 1/2 skip HOLD_NODE squads). See ShuttleMission#garrisonNode.
            if (s.mission.garrisonNode != null) {
                Squad garrison = roster.getSquad(s.mission.squadId);
                if (garrison != null) garrison.assignHoldNode(s.mission.garrisonNode);
            }
        }
        marine.squadId = s.mission.squadId;
        Squad squad = roster.getSquad(s.mission.squadId);
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
