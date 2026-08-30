package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.air.engine.EngineSlotData;
import com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver;
import com.dillon.starsectormarines.battle.air.engine.ThrusterFx;
import com.dillon.starsectormarines.battle.air.engine.ThrusterFxSystem;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.SquadDirectiveControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.combat.Detonations;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.logistics.ResupplyService;
import com.dillon.starsectormarines.battle.turret.TurretFireSink;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.vehicle.PurePursuit;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

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

    /**
     * Cell radius within which ground troops can engage an aircraft that is on
     * its wheels. Rifle reach rather than the AA bubble: this is people
     * shooting at a machine trundling past them.
     */
    private static final float GROUND_FIRE_RADIUS_CELLS = 10f;

    /**
     * HP/sec each enemy shooter in range drains from a taxiing aircraft.
     *
     * <p>Tuned by watching it. At five a second a fire team of six wrote a
     * Broadsword off in a second and a half, which is not a crossing under fire
     * — the aircraft never got anywhere and the recording was of a machine
     * dying beside its own shed. At two, the same team needs the better part of
     * four seconds, so an aircraft that keeps rolling can get past them and one
     * that is caught in the middle of the apron does not. The pressure is the
     * point; the instant kill was just a number.
     */
    private static final float GROUND_FIRE_DPS_EACH = 2f;

    /** Distance threshold (cells) at which an INCOMING shuttle snaps to the LZ and transitions to LANDED. Tight enough that the snap is invisible; loose enough that the asymptotic brake-to-station taper doesn't stall short. */
    private static final float SHUTTLE_LZ_ARRIVAL_DIST = 0.2f;

    /**
     * How near a ground waypoint counts as reached.
     *
     * <p>Wider than the LZ's, because a ground leg is walked at a speed the
     * craft chose rather than braked into a hover: a roll crosses a third of a
     * cell per tick and would step straight over a hair-fine radius.
     */
    private static final float THRESHOLD_ARRIVAL_DIST = 1.2f;
    /**
     * Cells from the pad centre at which a walking marine is aboard.
     *
     * <p>Generous on purpose. The pad is five cells square and a squad arrives
     * strung out across it; asking each marine to stand on one exact cell turns
     * embarkation into a queueing puzzle nobody watching would understand.
     */
    private static final float BOARDING_REACH = 3.5f;

    /**
     * Sim-seconds a landed craft keeps trying to set a passenger down before
     * it gives up and leaves with them.
     *
     * <p>Generous against a normal unload — a full transport empties in a few
     * seconds at its deboard interval — so this only ever fires when the LZ is
     * genuinely sealed: a squad holding on top of its own drop point, or an
     * interior packed wall to wall. Reaching further (see the deboard scan
     * radius) removes most of those; this is the floor under the rest.
     */
    private static final float UNLOAD_PATIENCE_SEC = 20f;

    /** Distance threshold (cells) at which a DEPARTING shuttle transitions to GONE / next cycle. Larger than the LZ threshold because exit points sit well off-map and we don't need pinpoint accuracy. */
    private static final float SHUTTLE_EXIT_ARRIVAL_DIST = 1.0f;

    /**
     * How near its objective a strike aircraft has to get before it is on
     * station.
     *
     * <p>Wider than a touchdown, because it is not one. A transport has to be
     * on the exact cell it is setting people down on; an aircraft attacking a
     * position has arrived when it is over it.
     */
    private static final float STRIKE_ARRIVAL_DIST = 2.0f;

    /**
     * How far out on the extended centreline a homebound aircraft joins final.
     *
     * <p>Long enough that the last leg is unmistakably the runway axis and the
     * craft is straight by the time it reaches the threshold; short enough that
     * a field near a map edge still has room for it.
     */
    private static final float APPROACH_LEAD_CELLS = 14f;
    /** Cell radius around a flying turret's origin where walls are treated as transparent — models the shuttle being "above" its containing building. Tuned to typical building wall thickness; past this, real LOS rules apply. */
    private static final float SHUTTLE_AIR_LOS_RADIUS = 3.5f;

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final TacticalScoring tacticalScoring;
    private final World world;
    private final TurretFireSink fireSink;
    private final Random rng;
    private final Function<EntitySpec, Long> addUnitSink;
    private final EffectsService effects;   // crash FX on shoot-down (smoke plume + burning wreck)
    private final ResupplyService resupply;
    private final SquadDirectiveControl commandControl;
    /**
     * The berths a based sortie belongs to, or null on a battle with no
     * authored field. Set after construction because the field is registered
     * with the reinforcement layer, well after the air system exists.
     */
    private AirfieldService airfield;

    /**
     * The air entity ids this system drives — the stable per-tick iteration
     * backbone, independent of the world tables, so mid-tick component changes (FX
     * attach/detach, turret attach) never hit a swap-pop trap on a live walk.
     * Spawned ids are appended; terminal-GONE ids are reaped (world-destroyed +
     * removed) at the end of {@link #tick} via {@link #reapGoneCraft}. The
     * {@code airCraft} query mirrors this exact set for {@link #airEntityIds}.
     */
    private final List<Long> air = new ArrayList<>();

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
     * {@link UnitRosterService#allocateAir}. Cached once (the component types are world-lifetime). No
     * grid/combat components, so every grid walk skips air for free.
     */
    private final ComponentType[] shuttleArchetype;

    public AirSystem(NavigationService navigation, UnitRosterService roster,
                     TacticalScoring tacticalScoring, World world, TurretFireSink fireSink,
                     Random rng, Function<EntitySpec, Long> addUnitSink, EffectsService effects,
                     ResupplyService resupply, SquadDirectiveControl commandControl) {
        this.navigation = navigation;
        this.roster = roster;
        this.tacticalScoring = tacticalScoring;
        this.world = world;
        this.fireSink = fireSink;
        this.rng = rng;
        this.addUnitSink = addUnitSink;
        this.effects = effects;
        this.resupply = resupply;
        this.commandControl = commandControl;
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
     * node, …) and {@code attachAirTurrets} for the optional
     * turret kit. The craft is an entity-id + components — no handle object.
     */
    public long spawn(ShuttleType type, Faction faction,
                      float lzX, float lzY, float entryX, float entryY,
                      float exitX, float exitY, float pendingDelay) {
        return spawn(type, faction, lzX, lzY, entryX, entryY,
                exitX, exitY, pendingDelay, type.capacity);
    }

    /**
     * Puts an aircraft with nothing in its hold into the air.
     *
     * <p>The counterpart to the transport spawn above, which takes a
     * {@link ShuttleType} and a manifest because both only mean anything for a
     * craft with a hold. This takes any {@link Airframe} and no manifest, which
     * is what a fighter is: it flies out, does whatever it is for, and comes
     * back. The seat validation is skipped rather than passed a zero, because
     * an aircraft with no seats is not a transport carrying none.
     */
    public long spawnSortie(Airframe frame, Faction faction,
                            float lzX, float lzY, float entryX, float entryY,
                            float exitX, float exitY, float pendingDelay) {
        AirBody body = new AirBody();
        body.teleport(entryX, entryY, AirBody.facingToward(lzX - entryX, lzY - entryY));
        ShuttleMission mission = new ShuttleMission(lzX, lzY, entryX, entryY, exitX, exitY,
                pendingDelay, 0, frame.maxHp());
        long id = roster.allocateAir(shuttleArchetype);
        world.setAirIdentity(id, frame, faction);
        world.setKinematics(id, body);
        world.setMission(id, mission);
        world.setAltitudeT(id, 1f);
        world.setFlightPhase(id, 0f);
        air.add(id);
        return id;
    }

    /** Spawns an infantry shuttle carrying a validated subset of its physical seats. */
    public long spawn(ShuttleType type, Faction faction,
                      float lzX, float lzY, float entryX, float entryY,
                      float exitX, float exitY, float pendingDelay,
                      int seatsPerSortie) {
        if (seatsPerSortie < 1 || seatsPerSortie > type.capacity) {
            throw new IllegalArgumentException("seatsPerSortie must be between 1 and "
                    + type.capacity + " for " + type + ": " + seatsPerSortie);
        }
        AirBody body = new AirBody();
        body.teleport(entryX, entryY, AirBody.facingToward(lzX - entryX, lzY - entryY));
        ShuttleMission mission = new ShuttleMission(lzX, lzY, entryX, entryY, exitX, exitY,
                pendingDelay, seatsPerSortie, type.maxHp);
        // Taken off the hull once, here, rather than read off it every tick:
        // the hull says what it can do and the sortie says what it is doing.
        mission.deboardInterval = type.deboardInterval;
        mission.fireSupportSec = type.fireSupportSec;
        long id = roster.allocateAir(shuttleArchetype);
        world.setAirIdentity(id, type, faction);
        world.setKinematics(id, body);
        world.setMission(id, mission);
        // Seed the authored render-state column (cruise altitude, zero wobble
        // phase). The state-machine tick drives it thereafter; the render/audio
        // passes read it by id.
        world.setAltitudeT(id, 1f);
        world.setFlightPhase(id, 0f);
        air.add(id);
        return id;
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
     * Reaps every craft that reached terminal {@code GONE} this tick: destroys its
     * world entity (one {@code world.destroy} drops <em>all</em> its components — the
     * air core plus any {@code THRUSTER_FX}/{@code AIR_TURRETS} — superseding the
     * per-component removes the death seam used pre-dissolution) and drops the id
     * from the {@link #air} backbone. Runs once at end of {@link #tick}, after every
     * pass: a craft that flips GONE mid-tick is skipped by the later passes (they
     * gate on visibility / non-GONE) and torn down exactly once here —
     * gather-then-apply, no structural change during a live walk. A multi-sortie
     * re-arm loops back to PENDING, never reaching GONE, so it is never reaped.
     */
    /** The AoE pipeline a strafe puts its rounds through. Null until wired; a strike then flies without firing. */
    private Detonations detonations;

    /** Gives this system the detonation pipeline its gun runs deliver through. */
    public void setDetonations(Detonations detonations) {
        this.detonations = detonations;
    }

    /** Tells this system which field its based sorties belong to. */
    public void setAirfield(AirfieldService airfield) {
        this.airfield = airfield;
    }

    /**
     * Hands a based sortie's airframe back to the berth it flew off.
     *
     * <p>Every way a sortie can end runs through here, and the distinction it
     * draws is the only one that matters: a craft that reached its own pad is
     * an aircraft home from a job and goes back on the stand with whatever hull
     * it has left, while one that ended any other way — shot down, scrubbed,
     * lost — is an aircraft that did not come back, and its berth is written
     * off for the battle. A field is a finite thing to lose.
     */
    private void handBackToField(ShuttleMission mission, boolean recovered) {
        AirfieldService.Berth berth = mission.homeBerth;
        if (berth == null) return;
        mission.homeBerth = null;
        if (airfield == null) return;
        if (recovered) airfield.recover(berth, mission.hp);
        else airfield.destroyed(berth);
    }

    private void reapGoneCraft() {
        for (Iterator<Long> it = air.iterator(); it.hasNext(); ) {
            long id = it.next();
            ShuttleMission mission = world.mission(id);
            if (mission == null || mission.state == ShuttleState.GONE) {
                entityWorld.destroy(id);
                it.remove();
            }
        }
    }

    /**
     * True when this craft is armed and assigned a fire-support role — after
     * LANDED → marinesRemaining==0, gates the HOVER_STATION transition vs. the
     * immediate DEPARTING path. Presence of the {@link AirTurrets} component IS
     * "armed."
     */
    private boolean shouldHoverLoiter(long id, ShuttleMission mission) {
        return mission.postDeliveryDisposition == PostDeliveryDisposition.LOITER_IF_ARMED
                && mission.assignedRole != null && world.hasAirTurrets(id);
    }

    /** True when every mounted turret has fired dry (or the craft is unarmed) — a HOVER_STATION exit trigger. */
    private boolean allTurretsDry(long id) {
        AirTurrets t = world.airTurrets(id);
        return t == null || t.allDry();
    }

    public void tick(float dt) {
        advanceShuttles(dt);
        tickAirThreat(dt);
        tickShuttleTurrets(dt);
        advanceThrusterFx(dt);
        reapGoneCraft();
    }

    /**
     * Anti-air: each airborne shuttle within range of an enemy defense post (turret) takes HP drain,
     * summed over every post in its AA bubble. At zero HP it's shot down — the marines still aboard are
     * lost, so a hot drop zone yields a partial-success wave (S3d D3). This is the first damage source
     * for {@link ShuttleMission#hp}, so the {@link ShuttleMission#HOVER_HP_THRESHOLD} loiter-abort also goes
     * live here. Area drain, not lock-on projectiles — the same "structures threaten an area" model as
     * ground-vs-infantry. Posts come from the spatial index, so this is O(shuttles × small bucket).
     */
    private void tickAirThreat(float dt) {
        if (air.isEmpty()) return;
        LongBucket scratch = new LongBucket();
        for (long id : air) {
            ShuttleMission mission = world.mission(id);
            boolean flying = isAirborneHittable(mission.state);
            boolean rolling = isOnItsWheelsAndExposed(mission.state);
            if (!flying && !rolling) continue;
            AirBody body = world.kinematics(id);
            Faction faction = world.airFaction(id);
            scratch.clear();
            navigation.getUnitIndex().gather(body.x, body.y,
                    flying ? AA_THREAT_RADIUS_CELLS : GROUND_FIRE_RADIUS_CELLS, scratch);
            int shooters = 0;
            for (int i = 0, n = scratch.size; i < n; i++) {
                long e = scratch.ids[i];
                if (roster.identity().faction(e) == faction) continue;
                if (!world.isAlive(e)) continue;
                if (flying) {
                    // Only a defense post can reach up. Infantry and mechs
                    // cannot engage something overhead.
                    if (!roster.identity().type(e).isTurret()) continue;
                } else {
                    // On the ground it is a large slow object in the open, and
                    // anything that shoots can shoot it. A structure cannot —
                    // that would make a parked aircraft threaten a taxiing one.
                    if (roster.identity().type(e).isStatic()
                            && !roster.identity().type(e).isTurret()) {
                        continue;
                    }
                }
                shooters++;
            }
            if (shooters == 0) continue;
            mission.hp -= shooters * (flying ? AA_DPS_PER_POST : GROUND_FIRE_DPS_EACH) * dt;
            if (mission.hp <= 0f) shootDown(id, body, mission, shooters);
        }
    }

    /**
     * Airborne states an AA post can hit — the descent gauntlet, the armed loiter, and the egress. A
     * LANDED shuttle deboarding on the ground is exempt (it's already "down").
     */
    private static boolean isAirborneHittable(ShuttleState st) {
        return st == ShuttleState.INCOMING || st == ShuttleState.HOVER_STATION
                || st == ShuttleState.DEPARTING || st == ShuttleState.RETURNING
                || st == ShuttleState.ATTACK_RUN || st == ShuttleState.REPOSITION;
    }

    /**
     * Ground phases where the aircraft is out in the open under its own power,
     * and anything with a weapon can shoot it.
     *
     * <p>This is what a runway is <em>for</em>. A strip buys a minute of
     * movement across open ground in exchange for not lifting vertically off a
     * stand, and that trade is worth nothing if the minute is invulnerable —
     * which it was: air could only ever be engaged by defence posts, and only
     * while airborne, so a fighter taxiing past a fire team was in no danger
     * whatsoever.
     *
     * <p>A loading craft is deliberately not here. It is down with its ramp
     * open and its passengers have already been taken off the roster, so making
     * it shootable would owe them a disposition that nothing currently gives
     * them; see {@code air-nouns.md}.
     */
    private static boolean isOnItsWheelsAndExposed(ShuttleState st) {
        return st == ShuttleState.TAXI_OUT || st == ShuttleState.HOLDING_SHORT
                || st == ShuttleState.TAKEOFF_ROLL
                || st == ShuttleState.LANDING_ROLL || st == ShuttleState.TAXI_IN;
    }

    /**
     * Shoot-down: the shuttle dies in the air with its undelivered marines aboard. Terminal like the
     * DEPARTING→GONE transition — set GONE; {@link #reapGoneCraft} destroys the entity (dropping every
     * component) at end of tick.
     */
    private void shootDown(long id, AirBody body, ShuttleMission mission, int posts) {
        // Crash FX: a burning wreck + smoke-plume column at the crash site, so a shot-down dropship
        // reads as a flaming wreck instead of just vanishing. Both feed the smoke/fire puff lists the
        // renderer drains (EffectsService → ImpactFx → IMPACT_FX), the same path turret/mech/hub wrecks
        // use — so it shows in the bridge and standalone alike. Clamp to an in-bounds cell (a shuttle
        // can be shot down a few cells off-map on an exit leg) and co-locate the plume on that cell.
        NavigationGrid grid = navigation.getGrid();
        int wx = Math.max(0, Math.min(grid.getWidth() - 1, (int) Math.floor(body.x)));
        int wy = Math.max(0, Math.min(grid.getHeight() - 1, (int) Math.floor(body.y)));
        effects.spawnSmokePlume(wx + 0.5f, wy + 0.5f);
        effects.spawnSmokingWreck(wx, wy);

        handBackToField(mission, /*recovered*/ false);
        mission.state = ShuttleState.GONE;
        LOG.info("air: shuttle " + world.airframe(id) + " shot down by " + posts + " AA post(s) with "
                + mission.marinesRemaining + " marine(s) still aboard.");
    }

    /**
     * Ramps each live shuttle's smoothed thruster demand toward the
     * {@link com.dillon.starsectormarines.battle.air.engine.ThrusterDemand}
     * target (computed from the freshly-steered body). GONE craft drop their
     * component so the {@code THRUSTER_FX} column tracks only live air entities.
     */
    private void advanceThrusterFx(float dt) {
        for (long id : air) {
            ShuttleMission mission = world.mission(id);
            // GONE craft are reaped (destroyed) at end of tick; skip (don't
            // re-advance, which would re-attach a THRUSTER_FX component onto a
            // craft about to be torn down).
            if (mission.state == ShuttleState.GONE) continue;
            // PENDING (off-map re-arm) intentionally keeps advancing: the cycle
            // teleport zeroes the body, so demand decays to 0 over the rearm
            // window and the next INCOMING sortie spools the plumes up from cold.
            Airframe frame = world.airframe(id);
            EngineSlotData[] slots = EngineSlotResolver.resolve(frame);
            ThrusterFxSystem.advance(id, slots, world.kinematics(id), frame.flight(),
                    entityWorld, components, dt);
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
        for (long id : air) {
            ShuttleMission mission = world.mission(id);
            AirBody body = world.kinematics(id);
            Airframe frame = world.airframe(id);
            AirHandling flight = frame.flight();
            switch (mission.state) {
                case PENDING:
                    mission.pendingDelay -= dt;
                    if (mission.pendingDelay <= 0f) {
                        beginShuttleLeg(mission, body, mission.lzX, mission.lzY);
                        mission.state = ShuttleState.INCOMING;
                    }
                    break;

                case LOADING:
                    // Down on the pad with the engines idling. Nothing steers:
                    // the craft is where it lives, and the sortie starts when
                    // the people it is waiting for get here.
                    world.setAltitudeT(id, 0f);
                    mission.boardingPatience -= dt;
                    embark(mission);
                    if (readyToLift(mission)) {
                        closeBoarding(mission);
                        beginShuttleLeg(mission, body, mission.lzX, mission.lzY);
                        mission.state = ShuttleState.INCOMING;
                    } else if (mission.boardingPatience <= 0f) {
                        // Out of time. Anyone already up the ramp flies — they
                        // were taken off the roster to board, so scrubbing on
                        // top of them would not cancel a delivery, it would
                        // quietly delete the people who made it. The sortie is
                        // only off when nobody did, and either way the craft
                        // does not squat on the hardstand forever.
                        boolean anybodyAboard = mission.marinesRemaining > 0;
                        closeBoarding(mission);
                        if (anybodyAboard) {
                            beginShuttleLeg(mission, body, mission.lzX, mission.lzY);
                            mission.state = ShuttleState.INCOMING;
                        } else {
                            // Scrubbed on the pad with nobody aboard. The
                            // aircraft never went anywhere, so it is still the
                            // field's and goes straight back on its stand.
                            handBackToField(mission, /*recovered*/ true);
                            mission.state = ShuttleState.GONE;
                        }
                    }
                    break;

                case TAXI_OUT:
                    // On the wheels the whole way: the aircraft is a target for
                    // every second of this, which is what the crossing is for.
                    world.setAltitudeT(id, 0f);
                    taxiToward(id, mission, body, flight, mission.holdX, mission.holdY, dt);
                    if (body.distanceTo(mission.holdX, mission.holdY) < THRESHOLD_ARRIVAL_DIST) {
                        mission.clearTaxiRoute();
                        mission.state = ShuttleState.HOLDING_SHORT;
                    }
                    break;

                case HOLDING_SHORT:
                    // Stopped at the threshold, swinging round to face down the
                    // strip while it waits — the wait is free and the turn is
                    // not, so lining up here rather than after the strip is
                    // granted keeps the half-circle off the queue. Asking every
                    // tick rather than queueing: the strip is granted to whoever
                    // asks while it is free, and a queue would have to survive a
                    // craft in it being destroyed on the taxiway.
                    world.setAltitudeT(id, 0f);
                    mission.groundSteer = GroundDriveSystem.drive(body, mission.groundSteer,
                            rollCarrotX(mission), rollCarrotY(mission), /*targetSpeed*/ 0f,
                            GroundHandling.rolling(flight), dt);
                    if (airfield != null && airfield.claimRunway(id)) {
                        beginShuttleLeg(mission, body, mission.rollX, mission.rollY);
                        mission.state = ShuttleState.TAKEOFF_ROLL;
                    }
                    break;

                case TAKEOFF_ROLL:
                    // Driven on the wheels, not flown along the ground. The
                    // driver finishes squaring the craft up before it opens the
                    // throttle at all, and at rolling speed the wheels will bear
                    // almost no cornering — so it tracks the centreline instead
                    // of being thrown off it by the turn it should have made
                    // standing still.
                    mission.groundSteer = GroundDriveSystem.drive(body, mission.groundSteer,
                            rollCarrotX(mission), rollCarrotY(mission), flight.maxSpeed(),
                            GroundHandling.rolling(flight), dt);
                    // Altitude tracks how much of the strip is behind it, so the
                    // craft is on the ground at the threshold and flying at the
                    // far end. Rotating early would put it in the air over its
                    // own runway with the roll unfinished.
                    updateShuttleAltitude(id, mission, body, mission.rollX, mission.rollY,
                            /*incoming*/ false, dt);
                    if (reachedOrPassed(body, mission.holdX, mission.holdY,
                            mission.rollX, mission.rollY)) {
                        if (airfield != null) airfield.releaseRunway(id);
                        beginShuttleLeg(mission, body, mission.lzX, mission.lzY);
                        mission.state = ShuttleState.INCOMING;
                    }
                    break;

                case ATTACK_RUN:
                    // Straight through, at speed. Nothing steers toward the
                    // target itself: the run was laid out when it began and the
                    // aircraft flies the line, which is what aiming is for a
                    // machine whose gun is bolted to its nose.
                    AirSteeringSystem.steer(body, mission.runToX, mission.runToY,
                            SteeringMode.CRUISE, flight, dt);
                    world.setAltitudeT(id, 1f);
                    world.setFlightPhase(id, world.flightPhase(id)
                            + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
                    releaseOrdnance(id, mission, body, dt);
                    if (reachedOrPassed(body, mission.runFromX, mission.runFromY,
                            mission.runToX, mission.runToY)) {
                        mission.passesLeft--;
                        if (mission.passesLeft > 0) {
                            beginReposition(id, mission, body);
                        } else {
                            beginEgress(id, mission, body, /*fromHover*/ false);
                        }
                    }
                    break;

                case REPOSITION:
                    // Carrying its speed round, so CRUISE rather than a brake:
                    // the turn is wide because the aircraft is fast and its
                    // hull turns at the rate its hull turns at.
                    AirSteeringSystem.steer(body, mission.runFromX, mission.runFromY,
                            SteeringMode.CRUISE, flight, dt);
                    world.setAltitudeT(id, 1f);
                    world.setFlightPhase(id, world.flightPhase(id)
                            + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
                    if (body.distanceTo(mission.runFromX, mission.runFromY) < RUN_ENTRY_DIST) {
                        mission.state = ShuttleState.ATTACK_RUN;
                    }
                    break;

                case RETURNING:
                    // An approach, so it is flown like one: losing height the
                    // whole way rather than climbing out.
                    AirSteeringSystem.steer(body, mission.exitX, mission.exitY,
                            mission.onFinalApproach ? SteeringMode.CRUISE
                                    : SteeringMode.BRAKE_TO_STATION, flight, dt);
                    updateShuttleAltitude(id, mission, body, mission.exitX, mission.exitY,
                            /*incoming*/ true, dt);
                    if (body.distanceTo(mission.exitX, mission.exitY)
                            >= (mission.onFinalApproach ? THRESHOLD_ARRIVAL_DIST
                                    : joinFinalReachedDist(flight))) {
                        break;
                    }
                    if (!mission.onFinalApproach) {
                        // Out on the extended centreline. Turn in; the leg from
                        // here to the threshold is the runway axis.
                        mission.onFinalApproach = true;
                        mission.exitX = mission.touchdownX;
                        mission.exitY = mission.touchdownY;
                        beginShuttleLeg(mission, body, mission.exitX, mission.exitY);
                        break;
                    }
                    // Over the threshold, lined up. A craft that finds the strip
                    // in use holds here until whoever is rolling is done with it.
                    if (airfield != null && airfield.claimRunway(id)) {
                        // Takeover. The landing itself is not flown: the
                        // aircraft is put on the centreline pointing down it and
                        // the rollout is driven from there. Asking the steering
                        // to brake a flying body onto a point left it arriving
                        // crabbed and pirouetting on the runway to sort itself
                        // out.
                        body.teleport(mission.touchdownX, mission.touchdownY,
                                AirBody.facingToward(mission.holdX - mission.touchdownX,
                                        mission.holdY - mission.touchdownY));
                        world.setAltitudeT(id, 0f);
                        mission.groundSteer = 0f;
                        mission.state = ShuttleState.LANDING_ROLL;
                    }
                    break;

                case LANDING_ROLL:
                    world.setAltitudeT(id, 0f);
                    // On the wheels, braking down the strip. The takeover put
                    // the craft on the centreline pointing along it and a
                    // wheeled body cannot leave that line sideways, so the nose
                    // no longer has to be pinned there every tick to stop it
                    // weathercocking across the runway.
                    mission.groundSteer = GroundDriveSystem.drive(body, mission.groundSteer,
                            mission.holdX, mission.holdY,
                            brakingTaper(body, mission.holdX, mission.holdY,
                                    flight.brakingAccel(), flight.maxSpeed()),
                            GroundHandling.rolling(flight), dt);
                    if (body.distanceTo(mission.holdX, mission.holdY) < THRESHOLD_ARRIVAL_DIST) {
                        // Off the strip before anything else may have it.
                        if (airfield != null) airfield.releaseRunway(id);
                        mission.state = ShuttleState.TAXI_IN;
                    }
                    break;

                case TAXI_IN:
                    world.setAltitudeT(id, 0f);
                    taxiToward(id, mission, body, flight,
                            mission.shelterX, mission.shelterY, dt);
                    if (body.distanceTo(mission.shelterX, mission.shelterY) < THRESHOLD_ARRIVAL_DIST) {
                        mission.clearTaxiRoute();
                        handBackToField(mission, /*recovered*/ true);
                        mission.state = ShuttleState.GONE;
                    }
                    break;

                case INCOMING:
                    AirSteeringSystem.steer(body, mission.lzX, mission.lzY, SteeringMode.BRAKE_TO_STATION, flight, dt);
                    updateShuttleAltitude(id, mission, body, mission.lzX, mission.lzY, /*incoming=*/true, dt);
                    if (mission.strikeSortie
                            && body.distanceTo(mission.lzX, mission.lzY) < STRIKE_ARRIVAL_DIST) {
                        // On station, not on the ground. A wider arrival than a
                        // touchdown because that is what arriving means here —
                        // the craft is over the objective rather than stopped
                        // on a point on it.
                        // Already at height, so no climb to play: a takeoff
                        // ramp here would drop the aircraft to the deck and
                        // fly it back up over its own target.
                        mission.takeoffTimer = 0f;
                        world.setAltitudeT(id, 1f);
                        mission.departingFromHover = false;
                        if (mission.passesLeft <= 0) mission.passesLeft = STRIKE_PASSES;
                        beginAttackRun(id, mission, body);
                        break;
                    }
                    if (body.distanceTo(mission.lzX, mission.lzY) < SHUTTLE_LZ_ARRIVAL_DIST) {
                        body.teleport(mission.lzX, mission.lzY, body.facingDegrees);
                        world.setAltitudeT(id, 0f);
                        mission.state = ShuttleState.LANDED;
                        mission.deboardCountdown = mission.deboardInterval;
                    }
                    break;

                case LANDED:
                    mission.deboardCountdown -= dt;
                    if (mission.marinesRemaining > 0) mission.unloadStalledFor += dt;
                    // Only a transport has anything to set down, and the
                    // payload machinery is written against one — so the carrier
                    // is asked for here rather than carried through the whole
                    // state machine as if every aircraft had a hold.
                    if (mission.deboardCountdown <= 0f && mission.marinesRemaining > 0
                            && frame instanceof ShuttleType carrier) {
                        AirDeliveryPayload payload = mission.payload != null
                                ? mission.payload : InfantryPayload.INSTANCE;
                        if (payload.tryDeploy(new AirDeliveryContext(mission, carrier, world.airFaction(id),
                                navigation, roster, addUnitSink, resupply, commandControl))) {
                            mission.marinesRemaining--;
                            mission.deboardedThisSortie++;
                            mission.unloadStalledFor = 0f;
                        }
                        mission.deboardCountdown = mission.deboardInterval;
                    }
                    // Nowhere to put anybody, for long enough that there is not
                    // going to be. The craft leaves with whoever is still
                    // aboard rather than holding the LZ for the rest of the
                    // battle: an undelivered passenger is a failed delivery,
                    // and a parked aircraft that never departs is a silent one.
                    if (mission.marinesRemaining > 0
                            && mission.unloadStalledFor >= UNLOAD_PATIENCE_SEC) {
                        LOG.warn("air: " + world.airframe(id) + " could not unload "
                                + mission.marinesRemaining + " of its passengers at ("
                                + mission.lzX + "," + mission.lzY
                                + ") — no standable cell within " + UNLOAD_PATIENCE_SEC
                                + "s. Departing with them aboard.");
                        mission.marinesRemaining = 0;
                        mission.awaitingEvacuees = false;
                    }
                    if (mission.marinesRemaining == 0
                            && !mission.awaitingEvacuees) {
                        if (shouldHoverLoiter(id, mission)) {
                            // Lift off the LZ and station-keep above the squad
                            // for the type's fire-support window. Initial hover
                            // point is the LZ; each subsequent tick follows the
                            // squad centroid (leashed to LZ radius).
                            mission.hoverPointX = mission.lzX;
                            mission.hoverPointY = mission.lzY;
                            mission.hoverTimerSec = mission.fireSupportSec;
                            mission.takeoffTimer = ShuttleMission.T_TAKEOFF_SEC;
                            world.setAltitudeT(id, 0f);   // smoothstep ramps from here
                            mission.departingFromHover = false;
                            mission.state = ShuttleState.HOVER_STATION;
                        } else {
                            beginEgress(id, mission, body, /*fromHover*/ false);
                        }
                    }
                    break;

                case HOVER_STATION:
                    // Follow the squad: hover point tracks the alive squad
                    // centroid, clamped to a leash radius around the LZ so a
                    // wiped squad or a runaway scout doesn't drag the shuttle
                    // across the whole map.
                    updateHoverFollow(mission);
                    AirSteeringSystem.steer(body, mission.hoverPointX, mission.hoverPointY, SteeringMode.STATION, flight, dt);
                    mission.hoverTimerSec -= dt;
                    // Takeoff phase — smoothstep altitudeT 0 → 1 over
                    // T_TAKEOFF_SEC for a visible acceleration / deceleration
                    // climb instead of a one-tick pop into the air.
                    float hoverAltitudeT;
                    if (mission.takeoffTimer > 0f) {
                        mission.takeoffTimer -= dt;
                        float u = 1f - Math.max(0f, mission.takeoffTimer / ShuttleMission.T_TAKEOFF_SEC);
                        hoverAltitudeT = u * u * (3f - 2f * u);  // smoothstep
                    } else {
                        hoverAltitudeT = 1f;
                    }
                    world.setAltitudeT(id, hoverAltitudeT);
                    world.setFlightPhase(id, world.flightPhase(id)
                            + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
                    // Exit triggers — first-of (timer expired, all ammo dry,
                    // HP pressure). HP threshold is wired forward for AA work;
                    // today there's no damage source so it never trips.
                    boolean fuelOut = mission.hoverTimerSec <= 0f;
                    boolean ammoOut = allTurretsDry(id);
                    boolean hpPressured = mission.hp <= frame.maxHp() * ShuttleMission.HOVER_HP_THRESHOLD;
                    if (fuelOut || ammoOut || hpPressured) {
                        beginEgress(id, mission, body, /*fromHover*/ true);
                    }
                    break;

                case DEPARTING:
                    AirSteeringSystem.steer(body, mission.exitX, mission.exitY, SteeringMode.CRUISE, flight, dt);
                    updateShuttleAltitude(id, mission, body, mission.exitX, mission.exitY, /*incoming=*/false, dt);
                    if (body.distanceTo(mission.exitX, mission.exitY) < SHUTTLE_EXIT_ARRIVAL_DIST) {
                        if (mission.currentCycle + 1 < mission.totalCycles) {
                            // Recycle for another sortie. The shuttle drops out of
                            // view (PENDING is invisible + engine-silent) for
                            // rearmDelay sim-seconds, then re-enters INCOMING.
                            // Per-cycle loadout refreshes here so SABOTAGE planters
                            // target the next charge site on each return trip.
                            mission.currentCycle++;
                            if (mission.cycleLoadouts != null && mission.currentCycle < mission.cycleLoadouts.length) {
                                mission.marineLoadout = mission.cycleLoadouts[mission.currentCycle];
                            }
                            AirDeliveryPayload payload = mission.payload != null
                                    ? mission.payload : InfantryPayload.INSTANCE;
                            mission.marinesRemaining =
                                    payload != InfantryPayload.INSTANCE
                                            && frame instanceof ShuttleType carrier
                                    ? payload.unitsPerSortie(carrier)
                                    : mission.seatsPerSortie;
                            mission.deboardedThisSortie = 0;   // fresh sortie → loadout index restarts at 0
                            mission.pendingDelay = mission.rearmDelay;
                            // The re-arm is a full refit at the carrier, so repair the hull too —
                            // without this, AA damage (D3) carries across sorties and a cycling
                            // shuttle dies early on a later run despite "re-arming". Symmetric with
                            // the magazine refill below.
                            mission.hp = frame.maxHp();
                            // Clear the sortie-local squad cache. Untagged
                            // personnel mint a fresh squad next cycle; tagged
                            // campaign personnel resolve their existing
                            // (campaign squad, LZ) group again while deboarding.
                            mission.squadId = Squad.NO_SQUAD;
                            body.teleport(mission.entryX, mission.entryY,
                                    AirBody.facingToward(mission.lzX - mission.entryX, mission.lzY - mission.entryY));
                            world.setAltitudeT(id, 1f);
                            mission.departingFromHover = false;
                            // Re-arm: refill every mount's magazine, drop any
                            // stale target lock so the next hover starts clean.
                            AirTurrets rearm = world.airTurrets(id);
                            if (rearm != null) {
                                for (MountedTurret mt : rearm.mounts) {
                                    mt.ammo = mt.mount.mountDef().ammoCapacity;
                                    mt.targetId = 0L;
                                    mt.cooldownTimer = 0f;
                                }
                            }
                            mission.state = ShuttleState.PENDING;
                        } else {
                            // Terminal — the craft is done; reapGoneCraft destroys
                            // it (and every component) at end of tick. A craft
                            // flying off a berth is not done at all, though: it
                            // has landed at home, and the air entity ends
                            // because the aircraft has stopped being one, not
                            // because it has stopped existing.
                            handBackToField(mission, /*recovered*/ true);
                            mission.state = ShuttleState.GONE;
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
        for (long id : air) {
            ShuttleMission mission = world.mission(id);
            if (!mission.isOverTheBattle()) continue;
            // Presence: only armed craft carry an AirTurrets component.
            AirTurrets t = world.airTurrets(id);
            if (t == null) continue;
            AirBody body = world.kinematics(id);
            Faction faction = world.airFaction(id);
            float rad = (float) Math.toRadians(body.facingDegrees);
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
                // as TurretBehavior's TURRET_STATE-shadow read.
                long currentBurstTarget = roster.isLive(mt.burstTargetId) ? mt.burstTargetId : 0L;
                if (mt.burstRemaining > 0 && currentBurstTarget == 0L) {
                    // A burst whose victim died is dead too — release the lock so
                    // the aim loop can re-acquire a fresh target next tick.
                    mt.burstRemaining = 0;
                    mt.burstTargetId = 0L;
                    currentBurstTarget = 0L;
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
                float worldX = mt.worldX(body, c, si, 1f);
                float worldY = mt.worldY(body, c, si, 1f);

                TurretAim.State aim = new TurretAim.State();
                aim.originCellX = (int) Math.floor(worldX);
                aim.originCellY = (int) Math.floor(worldY);
                aim.originX = worldX;
                aim.originY = worldY;
                aim.faction = faction;
                aim.squadId = Squad.NO_SQUAD;
                aim.excludeFromCrowding = 0L;
                aim.facingDegrees = mt.facingDegrees;
                aim.turnRateDegPerSec = mt.mount.mountDef().turnRateDegPerSec;
                aim.attackRange = mt.mount.weaponDef().range;
                aim.minRange = mt.mount.weaponDef().minRange;
                aim.cooldownTimer = mt.cooldownTimer;
                aim.attackCooldown = mt.mount.weaponDef().cooldown;
                aim.target = roster.isAliveById(mt.targetId) ? mt.targetId : 0L;
                aim.ignoreCloseWalls = true;
                aim.closeWallRadius = SHUTTLE_AIR_LOS_RADIUS;

                TurretAim.tick(aim, tacticalScoring, navigation.getGrid(), world, roster.vision(), dt);

                mt.facingDegrees = aim.facingDegrees;
                mt.cooldownTimer = aim.cooldownTimer;
                mt.setTarget(aim.target);

                // Shot origin Y carries the shuttle's visual altitude so the
                // rendered round leaves the turret at its drawn position
                // (body.y + altOffset), not the ground projection it sits over.
                // Sim LOS / aim still use the ground-projection worldY above —
                // that's the right cell for "what wall is this shuttle hovering
                // over" decisions. This offset is purely a render-origin nudge.
                float shotOriginY = worldY + AirAppearance.visualAltitudeOffsetCells(world.altitudeT(id));

                // Burst continuation runs ahead of fresh trigger pulls. The
                // mount commits to its salvo target — closer enemies walking
                // into LOS don't interrupt rounds already on the clock.
                if (mt.burstRemaining > 0) {
                    mt.burstTimer -= dt;
                    if (mt.burstTimer <= 0f) {
                        int releaseIndex = TurretMountGeometry.releaseIndex(
                                mt.mount.weaponDef().burstCount, mt.burstRemaining);
                        fireSink.fire(0L, worldX, shotOriginY, faction,
                                mt.mount.structure(), currentBurstTarget,
                                /*aerialShooter*/ true, /*hasLos*/ true,
                                mt.facingDegrees, releaseIndex);
                        mt.recoilTimer = 0f;
                        mt.ammo--;
                        mt.burstRemaining--;
                        mt.burstTimer = mt.mount.weaponDef().burstSpacing;
                        if (mt.burstRemaining == 0) mt.burstTargetId = 0L;
                    }
                    continue;
                }

                if (aim.fireThisTick) {
                    fireSink.fire(0L, worldX, shotOriginY, faction,
                            mt.mount.structure(), aim.target,
                            /*aerialShooter*/ true, /*hasLos*/ true,
                            mt.facingDegrees, 0);
                    mt.recoilTimer = 0f;
                    mt.ammo--;
                    // Burst weapons latch the remaining rounds; single-shot
                    // kinds (burstCount == 1) skip this and behave as before.
                    if (mt.mount.weaponDef().burstCount > 1
                            && aim.target != 0L && world.isAlive(aim.target)) {
                        mt.burstRemaining = mt.mount.weaponDef().burstCount - 1;
                        mt.burstTimer = mt.mount.weaponDef().burstSpacing;
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
    /**
     * Whether a craft rolling from {@code (fromX, fromY)} toward
     * {@code (toX, toY)} is done with that leg.
     *
     * <p>Near it, or past it. A takeoff roll is the one leg run at full power
     * with nothing braking it, so a craft that crosses the far threshold
     * between two ticks would otherwise keep accelerating down a strip it has
     * already left — and a strip is finite. Asked as a projection onto the roll
     * axis rather than a distance, because past is past however wide.
     */
    private static boolean reachedOrPassed(AirBody body, float fromX, float fromY,
                                           float toX, float toY) {
        if (body.distanceTo(toX, toY) < THRESHOLD_ARRIVAL_DIST) return true;
        float axisX = toX - fromX;
        float axisY = toY - fromY;
        float lengthSq = axisX * axisX + axisY * axisY;
        if (lengthSq < 1e-6f) return true;
        float travelled = ((body.x - fromX) * axisX + (body.y - fromY) * axisY) / lengthSq;
        return travelled >= 1f;
    }

    /**
     * Points a sortie that is done at wherever it goes next, and says which
     * kind of leg that is.
     *
     * <p>The one decision here is whether this craft has a strip to come home
     * to. A sortie that rolled off one owes itself back to it and flies an
     * approach; everything else leaves the way it always did. Which threshold
     * it lands on is decided now rather than at dispatch, because it depends on
     * where the craft actually finished up — an aircraft should touch down at
     * the end of the runway it reaches first rather than fly the length of its
     * own field to land the wrong way down it.
     */
    private void beginEgress(long id, ShuttleMission mission, AirBody body, boolean fromHover) {
        Runway strip = airfield == null ? null : airfield.runway();
        if (mission.usesRunway && strip != null) {
            float[] touchdown = strip.touchdownThreshold(body.x, body.y);
            mission.landOnRunway(strip, body.x, body.y, mission.shelterX, mission.shelterY);
            // Out to the extended centreline first. The leg after this one is
            // the runway axis, which is what lines the aircraft up without
            // anybody having to test its heading.
            float[] joinFinal = strip.approachPoint(touchdown, APPROACH_LEAD_CELLS);
            mission.onFinalApproach = false;
            mission.exitX = joinFinal[0];
            mission.exitY = joinFinal[1];
            // Not held at cruise the way a departure out of a hover is: this
            // leg is a descent, and the altitude lerp has to be free to run it
            // down to the threshold.
            mission.departingFromHover = false;
            beginShuttleLeg(mission, body, mission.exitX, mission.exitY);
            mission.state = ShuttleState.RETURNING;
            return;
        }
        mission.departingFromHover = fromHover;
        beginShuttleLeg(mission, body, mission.exitX, mission.exitY);
        mission.state = ShuttleState.DEPARTING;
    }

    /**
     * How far up the route a taxiing aircraft aims, as a multiple of the arc it
     * can be steered round.
     *
     * <p>The look-ahead a nose-steered body is driven at, not an arrival
     * radius. Derived from the turn radius rather than stated in cells because
     * the two are the same quantity: a carrot inside the radius asks for a turn
     * the gear cannot make and the craft weaves, and one far outside it cuts
     * the corner into the hangar the route was going round. A little over the
     * radius is the band where neither happens, whatever the radius becomes.
     */
    private static final float TAXI_LOOKAHEAD_RADII = 1.25f;

    /**
     * Rolls the aircraft toward {@code (goalX, goalY)} along walkable ground.
     *
     * <p>Routed rather than steered straight at it. A shed opens onto an apron
     * and the threshold is round the far side of it, so the straight line
     * between them goes through the hangar — and a body built for flight has
     * nothing in it that stops. The route is geometric rather than
     * occupancy-aware on purpose: an aircraft is not queueing behind the
     * infantry crossing the apron, it is going round the buildings.
     *
     * <p>The route is a guide and not a rail. The craft is driven at a carrot
     * sliding along it rather than at the next cell, so it takes the corners as
     * arcs on its own gear — which is why the move is still refused outright if
     * it would end inside something.
     */
    private void taxiToward(long id, ShuttleMission mission, AirBody body,
                            AirHandling flight, float goalX, float goalY, float dt) {
        NavigationGrid grid = navigation.getGrid();
        if (mission.taxiPath == null) {
            mission.taxiPath = navigation.findGeometricPath(
                    (int) Math.floor(body.x), (int) Math.floor(body.y),
                    (int) Math.floor(goalX), (int) Math.floor(goalY));
            // The first cell of a route is the one the craft is standing on, so
            // the cursor starts on the second. The carrot picker's own
            // already-crossed test cannot advance off index zero — it measures
            // the first waypoint against the body's approach to it, and the
            // body's approach to a waypoint always points at it — so a cursor
            // left there sticks, the look-ahead is eaten by the distance back
            // to a cell already behind the craft, and the carrot converges onto
            // the craft's own nose. An aircraft did precisely that beside its
            // runway and spun there for the rest of the battle. The ground
            // vehicles start theirs at one for the same reason.
            mission.taxiLeg = 1;
        }
        GroundHandling handling = GroundHandling.taxiing(flight);
        float carrotX = goalX;
        float carrotY = goalY;
        int[] route = mission.taxiPath;
        if (route != null && Paths.cellCount(route) > 1) {
            PurePursuit.Carrot carrot = PurePursuit.pick(body.x, body.y, route,
                    Math.max(1, mission.taxiLeg),
                    handling.minTurnRadiusCells() * TAXI_LOOKAHEAD_RADII);
            mission.taxiLeg = carrot.nextIdx;
            // Off the end of the route, the goal itself: a path is a run of
            // cell centres and the thing being taxied to is a point.
            if (!carrot.atEnd) {
                carrotX = carrot.x;
                carrotY = carrot.y;
            }
        }
        float wasX = body.x;
        float wasY = body.y;
        mission.groundSteer = GroundDriveSystem.drive(body, mission.groundSteer,
                carrotX, carrotY,
                brakingTaper(body, goalX, goalY, handling.brakingAccel(), handling.maxSpeed()),
                handling, dt);
        keepOnTheGround(grid, body, wasX, wasY);
    }

    /**
     * How near the join-final point counts as reaching it, for a craft that is
     * still flying.
     *
     * <p>A flying tolerance, deliberately not the ground one. A wheeled
     * aircraft can be asked to stop on a point and does; an aircraft in the air
     * cannot fly a circle tighter than its own turn radius, so a gate narrower
     * than that radius is a gate it can orbit forever — and one Broadsword did
     * exactly that, circling its own join-final point two cells out at four
     * cells a second for the rest of the battle while the strip stood empty.
     * The radius is what the gate is derived from rather than a number in
     * cells, because the same craft's radius is not the same number between one
     * calibration of atmospheric handling and the next.
     *
     * <p>A hull whose radius is wider than the lead out to the join point joins
     * final the moment the leg starts, which is the honest answer: an aircraft
     * that cannot fly the hook has not got one to fly, and it still reaches the
     * threshold and is taken over there.
     */
    private static float joinFinalReachedDist(AirHandling flight) {
        float turnRateRad = (float) Math.toRadians(flight.maxTurnRateDegPerSec());
        if (turnRateRad < 1e-3f) return APPROACH_LEAD_CELLS;
        return Math.max(THRESHOLD_ARRIVAL_DIST, flight.maxSpeed() / turnRateRad);
    }

    /**
     * Where a takeoff roll is pointed: down the strip and out the far side of
     * it, one runway length beyond the threshold the roll ends on.
     *
     * <p>Beyond rather than at, because the driver is a pursuit controller and
     * the arc it asks for through a carrot grows without bound as the carrot
     * gets close. A craft aimed at the far threshold itself flies the strip
     * straight and then whips round the last cell of it at rotation speed,
     * which is the same fault as steering onto the centreline while
     * accelerating along it, arriving from the other end. A carrot that stays
     * far away keeps the commanded arc gentle for the whole roll. Where the
     * roll <em>ends</em> is still the threshold; that is
     * {@link #reachedOrPassed}'s business, not the carrot's.
     */
    private static float rollCarrotX(ShuttleMission mission) {
        return mission.rollX + (mission.rollX - mission.holdX);
    }

    /** @see #rollCarrotX */
    private static float rollCarrotY(ShuttleMission mission) {
        return mission.rollY + (mission.rollY - mission.holdY);
    }

    /**
     * Fastest a craft may be going and still stop on ({@code goalX},
     * {@code goalY}) — {@code sqrt(2·a·d)}, capped at {@code maxSpeed}.
     *
     * <p>The ground driver takes a speed cap rather than a stopping mode,
     * because how fast to go on this leg is the leg's business: a taxi brakes
     * into its threshold, a takeoff roll does not brake at all, and a landing
     * rollout is nothing but the brake.
     */
    private static float brakingTaper(AirBody body, float goalX, float goalY,
                                      float brakingAccel, float maxSpeed) {
        float toGo = body.distanceTo(goalX, goalY);
        return Math.min(maxSpeed, (float) Math.sqrt(2f * brakingAccel * Math.max(0f, toGo)));
    }

    /**
     * Refuses a taxi step that would put the aircraft inside something.
     *
     * <p>Per axis, so a craft that clips a corner slides along the wall
     * instead of stopping dead against it — which is what a route-following
     * body mostly does, and stopping dead would strand it there. Both axes
     * blocked puts it back where it was; the route is still pulling it
     * somewhere legal, so it works itself free on the following ticks.
     */
    private static void keepOnTheGround(NavigationGrid grid, AirBody body,
                                        float wasX, float wasY) {
        if (walkableAt(grid, body.x, body.y)) return;
        // Already standing in something, so this move is an escape rather than
        // an intrusion and must not be refused. An aircraft starts its taxi
        // inside its own shed, whose cells a hangar wall makes non-walkable —
        // refusing on the destination alone pinned it there for the rest of
        // the battle, which is a far worse fault than the one being fixed.
        if (!walkableAt(grid, wasX, wasY)) return;
        if (walkableAt(grid, body.x, wasY)) {
            body.y = wasY;
            body.vy = 0f;
            return;
        }
        if (walkableAt(grid, wasX, body.y)) {
            body.x = wasX;
            body.vx = 0f;
            return;
        }
        body.x = wasX;
        body.y = wasY;
        body.vx = 0f;
        body.vy = 0f;
    }

    private static boolean walkableAt(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        return grid.inBounds(cx, cy) && grid.isWalkable(cx, cy);
    }

    /** Passes a strike flies before it turns for home. */
    private static final int STRIKE_PASSES = 3;

    /** Cells the run starts short of the target, and overshoots past it. */
    private static final float RUN_LEAD_CELLS = 26f;
    private static final float RUN_OVERSHOOT_CELLS = 18f;

    /** How near its start point a repositioning craft has to get before it runs in. */
    private static final float RUN_ENTRY_DIST = 4f;

    /**
     * How far round the next run comes in from.
     *
     * <p>Not a fixed angle: a machine that re-attacked from the same bearing
     * every time would be flying a racetrack, and one that picked at random
     * would sometimes turn barely at all. Something a little over a right angle
     * is a wide circuit that visibly changes the direction of attack.
     */
    private static final float RUN_BEARING_SHIFT_DEG = 115f;

    /** Cells either side of the run line within which the guns are firing. */
    private static final float FIRING_WINDOW_CELLS = 22f;

    /**
     * Lays out the next pass: a straight line in through the target and out
     * the far side.
     *
     * <p>The line is fixed for the whole run. A run that keeps re-aiming at a
     * moving target is a hover that happens to be travelling, and the whole
     * point of a gun run is that the aircraft commits: it is pointed at where
     * the enemy was when it rolled in, and whether that is still where they are
     * is the target's business.
     */
    private void beginAttackRun(long id, ShuttleMission mission, AirBody body) {
        float bearing = mission.state == ShuttleState.PENDING
                ? 0f : mission.lastRunBearingDeg;
        if (mission.state != ShuttleState.REPOSITION && mission.state != ShuttleState.ATTACK_RUN) {
            // First pass runs in along whatever bearing the craft arrived on,
            // so the aircraft does not fly past its target to attack it.
            bearing = (float) Math.toDegrees(Math.atan2(
                    mission.lzY - body.y, mission.lzX - body.x));
        }
        mission.lastRunBearingDeg = bearing;
        double rad = Math.toRadians(bearing);
        float dirX = (float) Math.cos(rad);
        float dirY = (float) Math.sin(rad);
        mission.runFromX = mission.lzX - dirX * RUN_LEAD_CELLS;
        mission.runFromY = mission.lzY - dirY * RUN_LEAD_CELLS;
        mission.runToX = mission.lzX + dirX * RUN_OVERSHOOT_CELLS;
        mission.runToY = mission.lzY + dirY * RUN_OVERSHOOT_CELLS;
        loadForThePass(mission, ordnanceOf(id, mission));
        mission.state = ShuttleState.ATTACK_RUN;
    }

    /** What this pass has to give: a bomber's stick, or nothing to count for a gun. */
    private static void loadForThePass(ShuttleMission mission, AirOrdnance load) {
        mission.roundsLeftThisPass = load == null || load.firesContinuously()
                ? 0 : load.roundsPerPass;
    }

    /**
     * What this sortie is carrying, taken off the airframe the first time it is
     * asked for and kept on the sortie thereafter.
     *
     * <p>Resolved lazily rather than demanded at dispatch so that a sortie
     * assembled by hand still flies armed, and cached so that a load which is
     * later spent or changed belongs to this trip rather than to the type.
     */
    private AirOrdnance ordnanceOf(long id, ShuttleMission mission) {
        if (mission.ordnance == null) {
            Airframe airframe = world.airframe(id);
            if (airframe != null) mission.ordnance = airframe.ordnance();
        }
        return mission.ordnance;
    }

    /** Sends the craft round for another pass from a different bearing. */
    private void beginReposition(long id, ShuttleMission mission, AirBody body) {
        mission.lastRunBearingDeg += RUN_BEARING_SHIFT_DEG;
        double rad = Math.toRadians(mission.lastRunBearingDeg);
        float dirX = (float) Math.cos(rad);
        float dirY = (float) Math.sin(rad);
        mission.runFromX = mission.lzX - dirX * RUN_LEAD_CELLS;
        mission.runFromY = mission.lzY - dirY * RUN_LEAD_CELLS;
        mission.runToX = mission.lzX + dirX * RUN_OVERSHOOT_CELLS;
        mission.runToY = mission.lzY + dirY * RUN_OVERSHOOT_CELLS;
        loadForThePass(mission, ordnanceOf(id, mission));
        mission.state = ShuttleState.REPOSITION;
    }

    /**
     * Puts rounds on the ground ahead of the nose while the target is in the
     * window.
     *
     * <p>Where each one lands is rolled, and that is the mechanism rather than
     * a concession to it: the aircraft is not shooting <em>at</em> anybody, it
     * is putting fire across a piece of ground, and whether that is decisive
     * depends on how much of the enemy is standing in it. Every round is an
     * ordinary detonation, so splash, wall damage, line of sight and the
     * roof-interception rule all come from the pipeline that already owns
     * them — a squad under an intact roof is not strafed.
     */
    private void releaseOrdnance(long id, ShuttleMission mission, AirBody body, float dt) {
        AirOrdnance load = ordnanceOf(id, mission);
        if (load == null || detonations == null) return;
        if (!load.firesContinuously() && mission.roundsLeftThisPass <= 0) return;
        if (body.distanceTo(mission.lzX, mission.lzY) > load.firingRangeCells) return;
        mission.fireCooldown -= dt;
        if (mission.fireCooldown > 0f) return;
        mission.fireCooldown = load.fireInterval();
        if (!load.firesContinuously()) mission.roundsLeftThisPass--;

        // Ahead of the nose, then scattered. The heading is the aim.
        double nose = Math.toRadians(body.facingDegrees + 90f);
        float aimX = body.x + (float) Math.cos(nose) * load.leadCells;
        float aimY = body.y + (float) Math.sin(nose) * load.leadCells;
        float impactX = aimX + (float) rng.nextGaussian() * load.scatterCells;
        float impactY = aimY + (float) rng.nextGaussian() * load.scatterCells;

        detonations.detonateNow(new PendingDetonation(
                id, impactX, impactY, /*remainingTime*/ 0f,
                load.aoeRadiusCells, load.damage, load.penetration,
                load.wallDamage, world.airFaction(id), /*aerialDelivery*/ true,
                /*wallDamageRadius*/ load.aoeRadiusCells, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false));
    }

    private void beginShuttleLeg(ShuttleMission mission, AirBody body, float toX, float toY) {
        mission.legStartDist = Math.max(0.001f, body.distanceTo(toX, toY));
    }

    /**
     * Per-tick altitude update. Writes the APPEARANCE {@code altitudeT} (1 → 0 on
     * INCOMING — high at entry, ground at LZ — and 0 → 1 on DEPARTING) and advances
     * the wobble {@code flightPhase}. The derived render scale is
     * {@link AirAppearance#scaleMult(float, float)} (a sine wobble gated by
     * altitudeT so it dies cleanly on the ground), computed at render time.
     */
    private void updateShuttleAltitude(long id, ShuttleMission mission, AirBody body,
                                       float toX, float toY, boolean incoming, float dt) {
        float altitudeT;
        if (!incoming && mission.departingFromHover) {
            // Departing straight out of HOVER_STATION — the shuttle is already
            // at cruise altitude, so a distance-ratio lerp from "ground" would
            // make it visibly descend and re-climb. Hold at the top.
            altitudeT = 1f;
        } else {
            float remaining = body.distanceTo(toX, toY);
            float ratio = remaining / mission.legStartDist;
            if (ratio < 0f) ratio = 0f;
            if (ratio > 1f) ratio = 1f;
            altitudeT = incoming ? ratio : (1f - ratio);
        }
        world.setAltitudeT(id, altitudeT);
        // Advance the wobble phase; the scale multiplier is derived from
        // altitudeT + flightPhase by AirAppearance at render time, not stored.
        world.setFlightPhase(id, world.flightPhase(id)
                + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
    }

    /**
     * Take aboard any of the embarking squad that has reached the ramp.
     *
     * <p>Gathered before anything is released. The roster is a dense array that
     * swap-and-pops on release, so removing a unit part-way through a walk of
     * it moves an untouched unit into a slot the walk has already passed.
     *
     * <p>A boarded marine is removed from the battle rather than transferred:
     * the loadouts this sortie will deboard were rolled from the same profile
     * and tier as the squad walking aboard, so there is nothing to carry across
     * that is not already the same kit.
     */
    private void embark(ShuttleMission mission) {
        if (mission.embarkSquadId == Squad.NO_SQUAD) return;
        if (mission.marinesRemaining >= mission.seatsPerSortie) return;
        long[] gathered = new long[mission.seatsPerSortie];
        int found = 0;
        for (int i = 0, live = roster.liveCount(); i < live && found < gathered.length; i++) {
            long u = roster.get(i);
            if (!roster.squad().hasSquad(u) || roster.squad().squadId(u) != mission.embarkSquadId) {
                continue;
            }
            float dx = world.x(u) - mission.entryX;
            float dy = world.y(u) - mission.entryY;
            if (dx * dx + dy * dy > BOARDING_REACH * BOARDING_REACH) continue;
            gathered[found++] = u;
        }
        for (int i = 0; i < found && mission.marinesRemaining < mission.seatsPerSortie; i++) {
            roster.takeOffTheField(gathered[i]);
            mission.marinesRemaining++;
        }
    }

    /**
     * End the boarding party's tour with the sortie.
     *
     * <p>Whoever is still on the field did not fly, and the aircraft has no
     * further claim on them. Handing them to the mission commander puts them
     * back in the pool it draws threat responses from; releasing them outright
     * would only strip the label, since an unowned squad is not in that pool
     * either. Nobody left alive means nobody to hand over.
     */
    private void closeBoarding(ShuttleMission mission) {
        int squadId = mission.embarkSquadId;
        if (squadId == Squad.NO_SQUAD) return;
        mission.embarkSquadId = Squad.NO_SQUAD;
        if (commandControl == null || mission.embarkHandoff == null) return;
        if (!squadStillComing(squadId)) return;
        commandControl.handoffSquadCommand(squadId,
                SquadCommandClaim.REINFORCEMENT_ISSUER,
                mission.embarkHandoff.authority(),
                mission.embarkHandoff.issuer(),
                /*nextAssignment*/ null,
                "sortie closed");
    }

    /**
     * Whether a loading craft has what it is waiting for.
     *
     * <p>Full, or nobody left to wait for. The second is what keeps a sortie
     * honest when its squad is cut down crossing the yard: whoever reached the
     * ramp goes, and the seats their friends would have filled stay empty.
     */
    private boolean readyToLift(ShuttleMission mission) {
        if (mission.marinesRemaining <= 0) return false;
        if (mission.marinesRemaining >= mission.seatsPerSortie) return true;
        return !squadStillComing(mission.embarkSquadId);
    }

    /** Whether any of that squad is still alive and therefore still walking. */
    private boolean squadStillComing(int squadId) {
        if (squadId == Squad.NO_SQUAD) return false;
        for (int i = 0, live = roster.liveCount(); i < live; i++) {
            long u = roster.get(i);
            if (roster.squad().hasSquad(u) && roster.squad().squadId(u) == squadId) return true;
        }
        return false;
    }

    /**
     * Recomputes {@link ShuttleMission#hoverPointX}/{@code hoverPointY} from the
     * alive squad centroid, pulled back by {@link ShuttleMission#HOVER_STANDOFF_CELLS}
     * along the LZ→centroid bearing (rear-overwatch standoff). Holds the
     * previous value if the squad is wiped (no alive squadmates) so the
     * shuttle doesn't snap back to the LZ on the last marine's death — it
     * stays where it was supporting.
     */
    private void updateHoverFollow(ShuttleMission mission) {
        if (mission.squadId == Squad.NO_SQUAD) return;
        float sumX = 0f, sumY = 0f;
        int n = 0;
        for (int i = 0, live = roster.liveCount(); i < live; i++) {
            long u = roster.get(i);
            if (!roster.squad().hasSquad(u) || roster.squad().squadId(u) != mission.squadId) continue;
            sumX += world.x(u);
            sumY += world.y(u);
            n++;
        }
        if (n == 0) return;  // squad wiped — hold current hover point
        float cx = sumX / n;
        float cy = sumY / n;
        float dx = cx - mission.lzX;
        float dy = cy - mission.lzY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        // Rear-overwatch standoff: shift the hover point from centroid back
        // toward the LZ. Below the standoff radius there's no stable bearing,
        // so just hold over the LZ until the squad pushes out.
        if (dist > ShuttleMission.HOVER_STANDOFF_CELLS) {
            float k = (dist - ShuttleMission.HOVER_STANDOFF_CELLS) / dist;
            cx = mission.lzX + dx * k;
            cy = mission.lzY + dy * k;
        } else {
            cx = mission.lzX;
            cy = mission.lzY;
        }
        mission.hoverPointX = cx;
        mission.hoverPointY = cy;
    }

    /**
     * Finds a free cell adjacent to the LZ and spawns a marine there as a fresh
     * {@code Entity}. Returns {@code false} when no nearby cell is available this
     * tick (rare — only happens if the area around the LZ is fully clogged with
     * units or walls); caller leaves {@code marinesRemaining} unchanged and the
     * shuttle re-tries next interval.
     */
}
