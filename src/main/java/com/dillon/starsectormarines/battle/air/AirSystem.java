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
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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
import com.dillon.starsectormarines.battle.vehicle.Pose;
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
     * How well fire lands on an aircraft crossing open ground, against the same
     * fire aimed at one standing on a stand.
     *
     * <p>The skin is the same skin — an airframe's structure and armour are one
     * ladder whether it is parked or rolling — so what separates the two is how
     * often a round finds it, not how much it takes when one does. A hull on
     * chocks is a mark you can settle onto; the same hull going past you at
     * taxi speed is not, and the resolver leads a mover perfectly on purpose,
     * so nothing else in the pipeline expresses that.
     *
     * <p>Set by measurement rather than by taste. The attrition field this
     * replaced was hand-tuned until a six-man fire team needed the better part
     * of four seconds to write a Broadsword off and a three-man team took about
     * half its hull over a crossing — slow enough that a craft which keeps
     * rolling gets past them, fast enough that one caught in the middle of the
     * apron does not. Measured against that: rifles at full effect killed the
     * same hull in 2.3s with six and 3.7s with three, which is a fire team
     * roughly twice as lethal as the field it replaced. At this multiplier the
     * same measurements read 3.4s and 8.1s, either side of the pair the old
     * field was chosen to produce.
     */
    private static final float ROLLING_ACCURACY_MULT = 0.40f;

    /**
     * Cell radius searched for ground units near a wreck settling onto a
     * taxiway, wide enough to cover the wreck's own footprint plus the
     * step-clear ring around it with margin to spare. Not tuned; this only has
     * to be a generous superset, since {@link GroundWreckFootprint} filters
     * the candidates itself.
     */
    private static final float GROUND_WRECK_GATHER_RADIUS_CELLS = 8f;

    /**
     * Floor under the distance at which an INCOMING craft snaps to the LZ and
     * transitions to LANDED. Tight enough that the snap is invisible; loose
     * enough that the asymptotic brake-to-station taper doesn't stall short.
     *
     * <p>A floor rather than the gate itself — see
     * {@link #flyingArrivalDist}.
     */
    private static final float SHUTTLE_LZ_ARRIVAL_FLOOR = 0.2f;

    /**
     * How near a ground waypoint counts as reached.
     *
     * <p>Wider than the LZ's, because a ground leg is walked at a speed the
     * craft chose rather than braked into a hover: a roll crosses a third of a
     * cell per tick and would step straight over a hair-fine radius.
     *
     * <p><b>A ground tolerance.</b> A wheeled aircraft can be asked to hold
     * short of a point and does, so this is the whole gate on the ground and
     * deliberately not widened by how fast the craft could fly. Where a
     * <em>flying</em> craft is asked how near a waypoint it has come, this is
     * only the floor — see {@link #flyingArrivalDist}.
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

    /** Floor under the distance at which a DEPARTING craft transitions to GONE / next cycle. Larger than the LZ floor because exit points sit well off-map and we don't need pinpoint accuracy. */
    private static final float SHUTTLE_EXIT_ARRIVAL_FLOOR = 1.0f;

    /**
     * Floor under how near its objective a strike aircraft has to get before it
     * is on station.
     *
     * <p>Wider than a touchdown, because it is not one. A transport has to be
     * on the exact cell it is setting people down on; an aircraft attacking a
     * position has arrived when it is over it.
     */
    private static final float STRIKE_ARRIVAL_FLOOR = 2.0f;

    /**
     * How many ticks' worth of travel a flying arrival gate is guaranteed to be
     * wider than.
     *
     * <p>One would be the bare condition for the gate being crossable at all;
     * two leaves margin for a body that is not flying straight at the point and
     * for the tick the crossing straddles.
     */
    private static final float ARRIVAL_TICK_MARGIN = 2f;

    /**
     * How many circuits a craft denied the strip flies before it lands anyway.
     *
     * <p>A go-around is the right answer to a runway somebody is standing on,
     * and an unbounded one is not an answer at all — it is the mechanism that
     * turns a busy minute into an aircraft that circles for the rest of the
     * battle. Two is enough for ordinary contention to clear (a departure holds
     * the strip for about five seconds and a rollout for about four, against a
     * circuit of seven and a half) and few enough that nobody watching thinks
     * the aircraft is broken.
     *
     * <p>What follows the last one is a <em>landing</em>. A craft that gives up
     * by leaving, or by ceasing to exist, has turned a queueing problem into a
     * lost airframe; one that puts down on an occupied strip has at worst two
     * aircraft on one runway for a few seconds, which is the lesser fault by a
     * wide margin.
     */
    private static final int MAX_GO_AROUNDS = 2;

    /**
     * How high a vertical lift hovers over its pad at the end of its run in,
     * before it sinks onto it.
     *
     * <p>A stated height rather than the bottom of the approach ramp, because
     * the run in and the settle are different manoeuvres: one is flown and one
     * is not. Low enough to read as a machine about to put its weight down and
     * high enough that the sink is visible.
     */
    private static final float PAD_HOVER_T = 0.3f;

    /**
     * Sim-seconds a vertical lift spends climbing straight off its own
     * hardstand before it turns for the LZ.
     *
     * <p>Unlike the settle this mirrors, there is nothing physical to wait
     * for here — the craft is holding station over the pad it started on, so
     * nothing it does changes how long the climb should take. A stated
     * duration is therefore not the fault {@link #MAX_PAD_SETTLE_SEC} exists
     * to bound; it is simply how long the climb-out is authored to look.
     */
    private static final float PAD_ASCENT_SEC = 0.9f;

    /**
     * How many sim-seconds a settle onto a pad is given to converge on its
     * own before it is landed regardless.
     *
     * <p>The settle now ends on a condition — over the pad, and its speed
     * killed — rather than on a clock, which is what fixed the bus-tier fault
     * this project measured: a stated 0.9s duration snapped a Buffalo or a
     * Mule to a stop while it was still three-odd cells short of the pad,
     * because their gentler brakes could not kill a run-in's worth of speed
     * that fast. A condition that might in principle never converge is worse
     * than the clock it replaced, though, so this is the same shape as
     * {@link #MAX_GO_AROUNDS}: a bound that is essentially never reached in
     * practice (the braking law that drives the settle converges every hull
     * tier in well under a second) and exists only so a pathological case
     * lands instead of hovering for the rest of the battle. Landing on this
     * bound still respects the brake — nothing is moved, only the settle is
     * accepted as finished — which is what keeps it from being the same snap
     * under a different name.
     */
    private static final float MAX_PAD_SETTLE_SEC = 5f;

    /**
     * Speed floor recorded as a settle's entry speed, so a craft that crosses
     * the arrival gate essentially stationary does not divide by (near) zero
     * when the descent profile normalises against it.
     */
    private static final float MIN_SETTLE_ENTRY_SPEED = 0.05f;

    /**
     * Fastest the drawn altitude may move, per second.
     *
     * <p>Height is lerped along a leg, so a leg replaced mid-flight — a
     * go-around starts a fresh approach from wherever the craft got to — would
     * otherwise jump the aircraft from the deck to cruise in one tick.
     * Rate-limiting it turns that into a climb-out.
     */
    private static final float ALTITUDE_RATE_PER_SEC = 1.2f;
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
     * The air-craft spawn archetype — adopted into the one entity world by
     * {@link UnitRosterService#allocateAir}. Cached once (the component types
     * are world-lifetime).
     *
     * <p>Still no {@code POSITION}, {@code COMBAT}, {@code MOVEMENT} or
     * {@code ROLE}, which is what keeps occupancy, separation, the fire system,
     * the mover and the planner off air for free. What it does carry is the
     * convoy chassis's trio — {@code IDENTITY}, {@code HEALTH}, {@code ARMOR} —
     * because an aircraft on its wheels has to be perceived, traced against
     * line of sight, hit, attributed and killed, and those are exactly the
     * columns the paths that already do all of that read. Membership-narrowing
     * does the work an air-aware branch in each of them used to.
     */
    private final ComponentType[] shuttleArchetype;

    /** Monotonic suffix for the greppable {@code IDENTITY} name; never recycled, so two craft never share one. */
    private int spawnSequence;

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
                components.APPEARANCE, components.IDENTITY, components.HEALTH,
                components.ARMOR};
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
                pendingDelay, 0);
        long id = roster.allocateAir(shuttleArchetype);
        world.setAirIdentity(id, frame, faction);
        world.setKinematics(id, body);
        world.setMission(id, mission);
        seedDurability(id, frame, faction);
        world.setAltitudeT(id, 1f);
        world.setFlightPhase(id, 0f);
        air.add(id);
        return id;
    }

    /**
     * Gives a fresh craft the columns that make it a body worth shooting at:
     * who it belongs to, how much of it there is, and how much skin is over
     * that.
     *
     * <p>The durability is {@link BasedAircraft}'s, deliberately and to the
     * number. A machine rolling across an apron and one standing on the pad
     * beside it are the same airframe with the same skin, and two ladders for
     * one aircraft would be a fact with two values — the sort that stays
     * consistent exactly as long as nobody re-dials either.
     */
    private void seedDurability(long id, Airframe frame, Faction faction) {
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_TYPE,
                UnitType.BASED_AIRCRAFT);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_FACTION, faction);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_NAME,
                "aircraft-" + (++spawnSequence));
        float capacity = Math.max(1f, frame.maxHp());
        world.setMaxHp(id, capacity);
        world.setHp(id, capacity);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_DAMAGE_TAKEN_MULT, 1f);
        entityWorld.setFloat(id, components.HEALTH,
                BattleComponents.HEALTH_INCOMING_ACCURACY_MULT, ROLLING_ACCURACY_MULT);
        float skin = capacity * BasedAircraft.ARMOR_CAPACITY_FRACTION;
        world.setMaxArmor(id, skin);
        world.setArmor(id, skin);
        world.setArmorRating(id, BasedAircraft.ARMOR_RATING);
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
                pendingDelay, seatsPerSortie);
        // Taken off the hull once, here, rather than read off it every tick:
        // the hull says what it can do and the sortie says what it is doing.
        mission.deboardInterval = type.deboardInterval;
        long id = roster.allocateAir(shuttleArchetype);
        world.setAirIdentity(id, type, faction);
        world.setKinematics(id, body);
        world.setMission(id, mission);
        seedDurability(id, type, faction);
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

    /**
     * The blast a craft killed on the ground shares with one burned on its
     * pad. Null until wired; a grounded kill then leaves its wreck without the
     * fire, the same graceful degradation {@link #detonations} gets.
     */
    private AirframeCookOffSystem airframeCookOff;

    /** Gives this system the same cook-off a hardstand kill lights off, for a craft it kills on the ground itself. */
    public void setAirframeCookOff(AirframeCookOffSystem airframeCookOff) {
        this.airframeCookOff = airframeCookOff;
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
    private void handBackToField(ShuttleMission mission, boolean recovered, float hullHp) {
        AirfieldService.Berth berth = mission.homeBerth;
        if (berth == null) return;
        mission.homeBerth = null;
        if (airfield == null) return;
        if (recovered) airfield.recover(berth, hullHp);
        else airfield.destroyed(berth);
    }

    private void reapGoneCraft() {
        for (Iterator<Long> it = air.iterator(); it.hasNext(); ) {
            long id = it.next();
            ShuttleMission mission = world.mission(id);
            if (mission == null || mission.state == ShuttleState.GONE) {
                // Whatever it was holding, it is not holding it any more. This
                // is the one place every craft ceases to exist — shot down,
                // scrubbed on its pad, home and shut down — so it is the one
                // place that can say so once for all of them. A craft killed
                // on the strip used to take the field with it: nothing on the
                // shoot-down path released, the claim outlived the aircraft,
                // and every sortie that came home afterwards was refused the
                // runway and flew circuits until the battle ended. Measured at
                // seventy-five go-arounds and still going.
                if (airfield != null) airfield.releaseRunway(id);
                entityWorld.destroy(id);
                it.remove();
            }
        }
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
     * for {@link ShuttleMission#hp}. Area drain, not lock-on projectiles — the same "structures threaten an area" model as
     * ground-vs-infantry. Posts come from the spatial index, so this is O(shuttles × small bucket).
     */
    private void tickAirThreat(float dt) {
        if (air.isEmpty()) return;
        LongBucket scratch = new LongBucket();
        for (long id : air) {
            ShuttleMission mission = world.mission(id);
            if (!isAirborneHittable(mission.state)) continue;
            AirBody body = world.kinematics(id);
            Faction faction = world.airFaction(id);
            scratch.clear();
            navigation.getUnitIndex().gather(body.x, body.y, AA_THREAT_RADIUS_CELLS, scratch);
            int posts = 0;
            for (int i = 0, n = scratch.size; i < n; i++) {
                long e = scratch.ids[i];
                if (roster.identity().faction(e) == faction) continue;
                if (!world.isAlive(e)) continue;
                // Only a defense post can reach up. Infantry and mechs
                // cannot engage something overhead.
                if (!roster.identity().type(e).isTurret()) continue;
                posts++;
            }
            if (posts == 0) continue;
            float hp = world.hp(id) - posts * AA_DPS_PER_POST * dt;
            world.setHp(id, hp);
            if (hp <= 0f) shootDown(id, body, mission, posts + " AA post(s)");
        }
    }

    /**
     * Airborne states an AA post can hit — the descent gauntlet, the settle
     * onto the pad, the runs, the egress and the approach home. Asked of the
     * locomotion rather than listed, because "can a post
     * reach it" is exactly "is it in the air", and a list is a thing a later
     * phase gets left out of. A LANDED shuttle deboarding on the ground is
     * exempt: it is already down.
     */
    private static boolean isAirborneHittable(ShuttleState st) {
        return AirLocomotion.of(st).airborne();
    }

    /**
     * Shoot-down: the shuttle dies with its undelivered marines aboard. Terminal like the
     * DEPARTING→GONE transition — set GONE; {@link #reapGoneCraft} destroys the entity (dropping every
     * component) at end of tick.
     *
     * <p>Grounded and airborne draw the one distinction {@code air-nouns.md} asks for. A craft killed
     * taxiing, holding short, or partway down a roll is under its own power on the ground, and the same
     * full tank under the same thin skin that makes a hardstand kill worth a fire team's time — it gets
     * the same cook-off blast and a wreck that stays where it stopped. A craft lost at altitude falls; it
     * does not leave a neat hull at the coordinates it was flying over, so it keeps the plain crash FX
     * this always used and leaves no ground wreck.
     */
    private void shootDown(long id, AirBody body, ShuttleMission mission, String killedBy) {
        if (mission.isOnItsWheelsAndExposed()) {
            groundedShootDown(id, body, mission);
        } else {
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
        }

        handBackToField(mission, /*recovered*/ false, world.hp(id));
        mission.state = ShuttleState.GONE;
        LOG.info("air: shuttle " + world.airframe(id) + " shot down by " + killedBy + " with "
                + mission.marinesRemaining + " marine(s) still aboard.");
    }

    /**
     * A craft the damage pipeline has just run out of structure. The one way
     * an aircraft dies to real fire, and deliberately the same one an AA
     * bubble uses: the cook-off, the wreck where it stopped, the berth written
     * off and the runway given back are what a kill owes, and a second death
     * path would be a kill that quietly skipped them.
     */
    public void destroyAircraft(long id) {
        ShuttleMission mission = world.mission(id);
        if (mission == null || mission.state == ShuttleState.GONE) return;
        shootDown(id, world.kinematics(id), mission, "ground fire");
    }

    /**
     * The ground half of a shoot-down. Lights the same fire a hardstand kill does — one definition of
     * the blast either way, see {@link AirframeCookOffSystem} — and leaves the hull where it stopped: a
     * {@link GroundWreck} the field remembers for the rest of the battle and a footprint stamped into the
     * ground the same way {@link AirfieldSystem} stamps one on a pad. {@code homeBerth} is written off by
     * the caller's {@link #handBackToField}, not here — a wreck away from a berth is not the berth's own
     * {@code wreckOnPad}, so the two stay independent facts the way {@link GroundWreck}'s class note
     * explains.
     */
    private void groundedShootDown(long id, AirBody body, ShuttleMission mission) {
        Faction faction = world.airFaction(id);
        if (airframeCookOff != null) {
            airframeCookOff.cookOff(id, body.x, body.y, faction);
        }
        if (airfield == null) return;
        GroundWreck wreck = new GroundWreck(body.x, body.y, body.facingDegrees, world.airframe(id));
        airfield.addGroundWreck(wreck);
        LongBucket nearby = new LongBucket();
        navigation.getUnitIndex().gather(body.x, body.y, GROUND_WRECK_GATHER_RADIUS_CELLS, nearby);
        GroundWreckFootprint.settle(navigation.getGrid(), navigation.getTopology(), world,
                nearby, wreck.cellX(), wreck.cellY());
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
                        mission.padPhaseElapsed = 0f;
                        mission.state = ShuttleState.PAD_ASCENT;
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
                            mission.padPhaseElapsed = 0f;
                            mission.state = ShuttleState.PAD_ASCENT;
                        } else {
                            // Scrubbed on the pad with nobody aboard. The
                            // aircraft never went anywhere, so it is still the
                            // field's and goes straight back on its stand.
                            handBackToField(mission, /*recovered*/ true, world.hp(id));
                            mission.state = ShuttleState.GONE;
                        }
                    }
                    break;

                case PAD_ASCENT:
                    // Holding over the pad it just left and climbing, the
                    // mirror of PAD_DESCENT: nothing is going anywhere yet,
                    // only up. Station-keeping rather than steering toward the
                    // LZ, because the LZ is the next phase's business — this
                    // one is entirely about not popping to cruise altitude in
                    // the tick INCOMING starts.
                    AirSteeringSystem.steer(body, mission.entryX, mission.entryY,
                            SteeringMode.STATION, flight, dt);
                    mission.padPhaseElapsed += dt;
                    world.setAltitudeT(id, smoothstep(
                            Math.min(1f, mission.padPhaseElapsed / PAD_ASCENT_SEC)));
                    world.setFlightPhase(id, world.flightPhase(id)
                            + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
                    if (mission.padPhaseElapsed >= PAD_ASCENT_SEC) {
                        // Full cruise height before the first INCOMING sample
                        // is taken, which is what keeps that sample a no-op:
                        // its own ratio-based ramp starts a fresh leg already
                        // believing the craft is at cruise, so there is
                        // nothing left for it to jump from.
                        beginShuttleLeg(mission, body, mission.lzX, mission.lzY);
                        mission.state = ShuttleState.INCOMING;
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
                            /*incoming*/ false, 0f, dt);
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
                            beginEgress(mission, body);
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
                    if (linedUpToRunIn(id, mission, body)) {
                        commitToTheLine(mission, body);
                        mission.state = ShuttleState.ATTACK_RUN;
                    }
                    break;

                case RETURNING: {
                    // An approach, and it is flown. The path from where the
                    // aircraft is to the threshold pointing down the strip is
                    // solved as geometry once and then tracked, so the aircraft
                    // is straight on arrival because the path it flew ended
                    // that way rather than because the simulation put it
                    // straight when it got there.
                    Runway strip = airfield == null ? null : airfield.runway();
                    if (strip == null) {
                        // No strip to come home to. Leave, rather than circle a
                        // field that is not there.
                        mission.approach = null;
                        beginShuttleLeg(mission, body, mission.exitX, mission.exitY);
                        mission.state = ShuttleState.DEPARTING;
                        break;
                    }
                    if (mission.approach == null) {
                        mission.approach = planApproach(mission, body, flight, strip);
                    }
                    RunwayApproach approach = mission.approach;
                    PurePursuit.Carrot carrot = PurePursuit.pick(body.x, body.y,
                            approach.xs, approach.ys, approach.leg, approach.lookAheadCells);
                    approach.leg = carrot.nextIdx;
                    AirSteeringSystem.steer(body, carrot.x, carrot.y, SteeringMode.CRUISE, flight, dt);
                    float toGo = PurePursuit.remainingPathLength(body.x, body.y,
                            approach.xs, approach.ys, carrot.nextIdx);
                    driveAltitude(id, approach.descentRemaining(toGo), 0f, dt);
                    // Only once the follower has actually crossed the final
                    // approach fix is there a landing to test for, and it is
                    // the cursor that says so rather than the look-ahead: the
                    // carrot runs off the end of the path a whole look-ahead
                    // early, which for a fighter is fourteen cells and lands it
                    // straight off the turn, still crabbed. The condition also
                    // has to be something a craft just sent round again fails,
                    // and that one is downfield of the threshold pointing along
                    // the strip — so a bare geometric test would land it on the
                    // runway it was refused.
                    if (carrot.nextIdx < approach.thresholdIdx) break;
                    // On final, and asking for the strip every tick of it
                    // rather than once as it crosses the numbers. A craft that
                    // asked only at the threshold asked once per circuit while
                    // a craft holding short asked thirty times a second, and
                    // lost that race about as often as the strip was busy —
                    // which on a field flying several sorties off one runway is
                    // most of the time. Both ends of the strip's queue now ask
                    // at the same rate, so a landing that has to wait waits for
                    // the strip rather than for its own next circuit.
                    boolean holdsTheStrip = airfield.claimRunway(id)
                            // An aircraft that cannot land is a worse outcome
                            // than two on one strip. After enough circuits it
                            // stops asking and puts down: a real machine low on
                            // fuel declares an emergency and lands anyway, and
                            // the fallback here has to be a landing rather than
                            // a craft that quietly ceases to exist.
                            || mission.goAroundsFlown >= MAX_GO_AROUNDS;
                    boolean arrived = pastTheThreshold(mission, body)
                            || body.distanceTo(mission.touchdownX, mission.touchdownY)
                                    < flownArrivalDist(THRESHOLD_ARRIVAL_DIST, body, dt);
                    if (!arrived) break;
                    if (holdsTheStrip) {
                        // Takes it outright rather than asking, because the
                        // craft is about to be standing on it either way: an
                        // emergency landing that left the strip recorded to
                        // somebody else would have the aircraft roll out
                        // without holding the runway it is on.
                        airfield.takeRunway(id);
                        // Touchdown, and nothing is moved. The aircraft is
                        // where it flew itself to, pointed the way the path
                        // ended, carrying the speed it arrived with; the wheels
                        // take it from there and bleed that speed off down the
                        // strip. This is the seam the teleport used to paper
                        // over.
                        world.setAltitudeT(id, 0f);
                        mission.groundSteer = 0f;
                        mission.approach = null;
                        mission.state = ShuttleState.LANDING_ROLL;
                    } else {
                        // Somebody else has the strip. Go round. Discarding the
                        // path is the whole of it: the next one is solved from
                        // this pose to the fix behind the threshold, which is a
                        // circuit rather than a straight line.
                        mission.approach = null;
                        mission.goAroundsFlown++;
                    }
                    break;
                }

                case LANDING_ROLL:
                    world.setAltitudeT(id, 0f);
                    // On the wheels, braking down the strip. The craft
                    // flew itself onto the centreline pointing along it and a
                    // wheeled body cannot leave that line sideways, so the nose
                    // does not have to be pinned there every tick to stop it
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
                        handBackToField(mission, /*recovered*/ true, world.hp(id));
                        mission.state = ShuttleState.GONE;
                    }
                    break;

                case INCOMING:
                    AirSteeringSystem.steer(body, mission.lzX, mission.lzY, SteeringMode.BRAKE_TO_STATION, flight, dt);
                    if (mission.strikeSortie) {
                        // A strike is not descending to anything. The approach
                        // ramp exists for a transport losing height onto its LZ;
                        // running it for an attack sank the aircraft toward the
                        // deck the whole way in and popped it back to cruise two
                        // cells short of the target.
                        world.setAltitudeT(id, 1f);
                    } else {
                        // Down to a hover rather than down to the ground:
                        // the last of the descent belongs to the settle, which
                        // is a different manoeuvre and its own phase.
                        updateShuttleAltitude(id, mission, body, mission.lzX, mission.lzY,
                                /*incoming=*/true, PAD_HOVER_T, dt);
                    }
                    if (mission.strikeSortie
                            && body.distanceTo(mission.lzX, mission.lzY)
                                    < flyingArrivalDist(STRIKE_ARRIVAL_FLOOR, body, flight, dt)) {
                        // On station, not on the ground. A wider arrival than a
                        // touchdown because that is what arriving means here —
                        // the craft is over the objective rather than stopped
                        // on a point on it.
                        world.setAltitudeT(id, 1f);
                        if (mission.passesLeft <= 0) mission.passesLeft = STRIKE_PASSES;
                        beginAttackRun(id, mission, body);
                        break;
                    }
                    if (body.distanceTo(mission.lzX, mission.lzY)
                            < flyingArrivalDist(SHUTTLE_LZ_ARRIVAL_FLOOR, body, flight, dt)) {
                        // Over the pad with its speed washed off. What is left
                        // is the settle, and it is flown rather than skipped.
                        mission.padDescentEntrySpeed = Math.max(body.speed(), MIN_SETTLE_ENTRY_SPEED);
                        mission.padPhaseElapsed = 0f;
                        mission.state = ShuttleState.PAD_DESCENT;
                    }
                    break;

                case PAD_DESCENT: {
                    // Holding over the spot and sinking onto it, the way a
                    // helicopter arrives. Station-keeping rather than braking
                    // toward the pad, because the craft is already there and
                    // what is left is killing the drift it came in with. Its
                    // heading is whatever the run in left it on and is never
                    // touched: the snap this replaces put the aircraft on the
                    // pad in one tick, which read as it ceasing to exist
                    // mid-air and reappearing landed.
                    AirSteeringSystem.steer(body, mission.lzX, mission.lzY,
                            SteeringMode.STATION, flight, dt);
                    mission.padPhaseElapsed += dt;
                    // Driven by how much of the craft's own drift is still
                    // there to kill, not by a clock: a gentle-braking bus and
                    // a nimble drop craft do not owe the eye the same number
                    // of seconds, only the same picture of slowing down.
                    float speedFrac = clamp01(body.speed() / mission.padDescentEntrySpeed);
                    world.setAltitudeT(id, PAD_HOVER_T * smoothstep(speedFrac));
                    world.setFlightPhase(id, world.flightPhase(id)
                            + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
                    // Down is a fact about the craft, not a duration: it has to
                    // actually be over the pad, and it has to have actually
                    // stopped. A hull that brakes gently just takes a few more
                    // ticks to satisfy the second half — it is never snapped
                    // to a stop early the way a fixed clock used to snap it.
                    boolean overThePad = body.distanceTo(mission.lzX, mission.lzY)
                            < flyingArrivalDist(SHUTTLE_LZ_ARRIVAL_FLOOR, body, flight, dt);
                    boolean speedKilled = body.speed() <= settledSpeed(flight, dt);
                    boolean timedOut = mission.padPhaseElapsed >= MAX_PAD_SETTLE_SEC;
                    if ((overThePad && speedKilled) || timedOut) {
                        if (timedOut && !(overThePad && speedKilled)) {
                            LOG.warn("air: " + world.airframe(id) + " settling onto its pad at ("
                                    + mission.lzX + "," + mission.lzY + ") did not converge within "
                                    + MAX_PAD_SETTLE_SEC + "s (speed " + body.speed()
                                    + ", " + body.distanceTo(mission.lzX, mission.lzY)
                                    + " cells out) — landing where it is.");
                        }
                        world.setAltitudeT(id, 0f);
                        // Nothing is moved: the craft stays exactly where it
                        // flew itself to, carrying whatever sliver of drift
                        // speedKilled judged not worth another tick's brake.
                        // Not forced to exactly zero — nothing downstream of
                        // LANDED reads the body's velocity for motion, and
                        // forcing it here would be one more artificial
                        // deceleration stacked on top of this tick's real one,
                        // which is exactly the kind of snap this settle exists
                        // to remove.
                        mission.state = ShuttleState.LANDED;
                        mission.deboardCountdown = mission.deboardInterval;
                    }
                    break;
                }

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
                        // Unloaded is done. A transport lifts and goes: the
                        // delivery is what it was flown for, and station-keeping
                        // over the drop point is a second job nobody ordered.
                        beginEgress(mission, body);
                    }
                    break;

                case DEPARTING:
                    AirSteeringSystem.steer(body, mission.exitX, mission.exitY, SteeringMode.CRUISE, flight, dt);
                    updateShuttleAltitude(id, mission, body, mission.exitX, mission.exitY,
                            /*incoming=*/false, 0f, dt);
                    if (body.distanceTo(mission.exitX, mission.exitY)
                            < flyingArrivalDist(SHUTTLE_EXIT_ARRIVAL_FLOOR, body, flight, dt)) {
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
                            // without this, damage taken on one run carries across sorties and a
                            // cycling shuttle dies early on a later one despite "re-arming". The
                            // skin is made good in the same breath, since a refit that left the
                            // armour spent would be a repair that only half happened. Symmetric
                            // with the magazine refill below.
                            world.setHp(id, frame.maxHp());
                            world.setArmor(id, world.maxArmor(id));
                            // Clear the sortie-local squad cache. Untagged
                            // personnel mint a fresh squad next cycle; tagged
                            // campaign personnel resolve their existing
                            // (campaign squad, LZ) group again while deboarding.
                            mission.squadId = Squad.NO_SQUAD;
                            body.teleport(mission.entryX, mission.entryY,
                                    AirBody.facingToward(mission.lzX - mission.entryX, mission.lzY - mission.entryY));
                            world.setAltitudeT(id, 1f);
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
                            handBackToField(mission, /*recovered*/ true, world.hp(id));
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
    private void beginEgress(ShuttleMission mission, AirBody body) {
        Runway strip = airfield == null ? null : airfield.runway();
        if (mission.usesRunway && strip != null) {
            mission.landOnRunway(strip, body.x, body.y, mission.shelterX, mission.shelterY);
            // The approach itself is solved on the first RETURNING tick rather
            // than here, because it is solved from a pose and the craft is
            // about to leave this one — a transport lifting off a pad is
            // pointing wherever it was parked.
            mission.approach = null;
            mission.exitX = mission.touchdownX;
            mission.exitY = mission.touchdownY;
            beginShuttleLeg(mission, body, mission.exitX, mission.exitY);
            mission.state = ShuttleState.RETURNING;
            return;
        }
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
            // The first cell of a route is the one the craft is standing on,
            // so the cursor starts on it and the picker consumes it on the
            // first tick — or keeps it, if the craft has drifted back off the
            // cell centre and the first waypoint really is still ahead.
            mission.taxiLeg = 0;
        }
        GroundHandling handling = GroundHandling.taxiing(flight);
        float carrotX = goalX;
        float carrotY = goalY;
        int[] route = mission.taxiPath;
        if (route != null && Paths.cellCount(route) > 1) {
            PurePursuit.Carrot carrot = PurePursuit.pick(body.x, body.y, route,
                    mission.taxiLeg,
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
     * Works out the circuit this craft flies onto its threshold, from the pose
     * it is in right now.
     *
     * <p>Solved from the live pose every time one is needed rather than laid
     * out at dispatch. A path is the answer to a question asked from one place
     * and one heading, so a craft that has been sent round again is asking a
     * different question.
     *
     * <p>A landing that will not solve still lands: the fallback flies straight
     * in and arrives on whatever heading it managed, which is what every
     * landing did before the arrival was solved at all. It says so, because how
     * often a field cannot fit a circuit is worth knowing rather than
     * discovering.
     */
    private RunwayApproach planApproach(ShuttleMission mission, AirBody body,
                                        AirHandling flight, Runway strip) {
        NavigationGrid grid = navigation.getGrid();
        RunwayApproach planned = RunwayApproach.plan(
                new Pose(body.x, body.y, body.facingDegrees), strip,
                new float[]{mission.touchdownX, mission.touchdownY},
                flight.minTurnRadiusCells(), grid.getWidth(), grid.getHeight());
        if (!planned.solved) {
            LOG.info("air: no curvature-feasible approach onto ("
                    + mission.touchdownX + "," + mission.touchdownY
                    + ") — flying it straight in.");
        } else if (planned.wide) {
            LOG.info("air: the approach onto (" + mission.touchdownX + ","
                    + mission.touchdownY + ") takes the craft well off the map.");
        }
        return planned;
    }

    /**
     * Whether the craft is downfield of the threshold it is landing on — over
     * the strip rather than short of it.
     *
     * <p>A projection onto the roll axis rather than a distance, because past
     * is past however wide, and a craft crossing a fine gate at flying speed
     * is inside it only between two samples.
     */
    private static boolean pastTheThreshold(ShuttleMission mission, AirBody body) {
        float axisX = mission.holdX - mission.touchdownX;
        float axisY = mission.holdY - mission.touchdownY;
        float lengthSq = axisX * axisX + axisY * axisY;
        if (lengthSq < 1e-6f) return true;
        return (body.x - mission.touchdownX) * axisX
                + (body.y - mission.touchdownY) * axisY >= 0f;
    }

    /**
     * How near a point a <em>flying</em> craft has to come before it counts as
     * having reached it.
     *
     * <p>An arrival gate is a distance a craft has to be sampled inside on some
     * tick, and a craft crossing it at speed is inside it only between two
     * samples. So a gate narrower than the step the craft takes in a tick is a
     * gate that can be jumped clean over — and a craft that jumps its arrival
     * gate does not arrive, it flies a circuit round its own destination for
     * the rest of the battle. The authored number is therefore a floor rather
     * than the gate: what the gate actually is, is whichever of the two is
     * wider.
     *
     * <p><b>The step the craft is actually taking, not the fastest it could
     * go.</b> Half these arrivals are flown braked and half at cruise, and a
     * max-speed bound is wrong for the braked half: an approach that has taken
     * a transport down to a crawl would have its landing gate widened by the
     * speed it flew in at and touch down most of a cell early. Reading the body
     * makes the gate track the fault's own shape — it opens exactly as far as
     * the craft is moving and closes again as the craft slows, so a braked
     * arrival keeps the tolerance it was authored with and only a craft
     * genuinely covering ground gets a wider one.
     *
     * <p>Derived rather than authored because one constant cannot serve every
     * craft. These floors are a transport's, tuned against a hull that covers a
     * fifth of a cell in a tick; a fighter three times as fast steps over them.
     * Nothing reconciles the two by picking a better single number.
     *
     * <p>A <em>flying</em> tolerance, deliberately separate from
     * {@link #THRESHOLD_ARRIVAL_DIST} used on the ground. A wheeled aircraft
     * can be asked to stop short of a point and does; widening its gates by
     * how fast it is rolling would let it call a threshold reached from a cell
     * and a half away with the strip still ahead of it.
     *
     * <p><b>Two ways a craft cannot be sampled inside a gate, and it has to
     * admit both.</b> The step is one. The other is the circle: a body steered
     * at a point it cannot turn tightly enough to reach settles into an orbit
     * around it — always about a turn radius out, always about ninety degrees
     * off the bearing to it, indefinitely. Watched at the wide turn radius, a
     * Broadsword sent to a landing zone circled it three and a half cells out
     * at four cells a second for the rest of the battle, which is not a craft
     * that is nearly there: it is a craft that will never be there. Both terms
     * read the body's current speed, so both close as the craft slows and
     * neither moves a braked arrival.
     *
     * @param floorCells the authored tolerance, in cells
     * @param body       the craft, read for the speed it is actually making
     * @param flight     the craft's handling, read for the circle it can fly
     * @param dt         the tick this gate is being tested on
     */
    static float flyingArrivalDist(float floorCells, AirBody body, AirHandling flight, float dt) {
        float speed = body.speed();
        float turnRateRad = (float) Math.toRadians(flight.maxTurnRateDegPerSec());
        // A craft that cannot turn at all does not orbit — it flies straight
        // past — so there is no circle to admit, only the step.
        float orbit = turnRateRad < 1e-3f ? 0f : speed / turnRateRad;
        return Math.max(floorCells, Math.max(ARRIVAL_TICK_MARGIN * speed * dt, orbit));
    }

    /**
     * How near a point a craft being <em>flown along a path</em> has to come.
     *
     * <p>The step, and only the step. {@link #flyingArrivalDist} admits a whole
     * turning circle as well, because a body steered <em>at</em> a point it
     * cannot turn tightly enough to reach settles into an orbit around it and
     * would otherwise never be sampled inside any gate. Nothing here can orbit:
     * a craft on a solved approach is chasing a carrot that slides along the
     * path and runs on past the threshold down the strip, so the one way it can
     * miss a gate is by stepping over it between two samples.
     *
     * <p>Admitting the circle anyway is not free, and the price is exactly its
     * width. A fighter turns inside seventeen cells at approach speed, so a
     * landing gated on {@link #flyingArrivalDist} fired seventeen cells short of
     * the numbers: measured on the shipped airfield the aircraft touched down at
     * x=79 on a strip that ends at x=62.5 and rolled out across open ground into
     * it, and an approach from the other end put the wheels down at x=-9.6,
     * which is off the map. The heading was right and the position was a
     * runway's width of nonsense either side.
     */
    static float flownArrivalDist(float floorCells, AirBody body, float dt) {
        return Math.max(floorCells, ARRIVAL_TICK_MARGIN * body.speed() * dt);
    }

    /**
     * How slow is stopped, for a craft settling onto a pad — tested against
     * the hull's own brake rather than an authored number, the same reasoning
     * {@link #flyingArrivalDist} applies to position.
     *
     * <p>Exactly one more tick's worth of braking: {@link AirHandling#brakingAccel()}
     * is the most forward speed {@link AirSteeringSystem#steer} can shed in a
     * single tick, so a craft at or under this is a craft one more tick of the
     * same braking law would carry to zero anyway. Nothing wider is needed —
     * the brake-to-a-point law this settle flies converges the hull's speed
     * and its distance to the pad together (both go to zero at once under
     * continuous following), so by the time position has closed to
     * {@link #flyingArrivalDist}'s gate the speed is already most of the way
     * to this floor on its own.
     */
    private static float settledSpeed(AirHandling flight, float dt) {
        return flight.brakingAccel() * dt;
    }

    /** {@code t} folded into {@code [0, 1]}. */
    private static float clamp01(float t) {
        if (t < 0f) return 0f;
        if (t > 1f) return 1f;
        return t;
    }

    /**
     * Ease {@code t ∈ [0, 1]} through a flat start and a flat finish rather
     * than a constant rate — the shape both pad phases ride so a hover reads
     * as easing to a stop / away rather than switching on and off.
     */
    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
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

    /**
     * Cells the run starts short of the target, and overshoots past it.
     *
     * <p>The run-in has to be longer than the longest weapon's firing range or
     * the aircraft is already shooting the moment the line is laid out, which
     * is not a run — it is a machine that happened to be pointed the right way.
     * A missile pod releases from 34 cells, so the line starts outside that.
     */
    private static final float RUN_LEAD_CELLS = 44f;
    private static final float RUN_OVERSHOOT_CELLS = 20f;

    /**
     * How far off the nose the objective may lie for a repositioning craft to
     * roll in on it.
     *
     * <p>A run starts when the aircraft is pointed at the position from far
     * enough out, and not when it has arrived at a place. Reaching a point and
     * reaching it pointed the right way are different things — the same
     * distinction a takeoff roll draws — and out here the difference is the
     * whole pass. The start of a run laid out one leg early sits on the far
     * side of the objective, so a craft steered onto it arrives <em>pointing
     * away</em>: at a wide turn radius it then wheels through most of a
     * half-circle to get its nose back round, wanders eight cells off its own
     * line doing it, and takes the pass past the position instead of over it.
     * Measured that way, the closest a gun run came to its objective was five
     * and a half cells, which for a weapon that lands its rounds within two is
     * a sortie flown at an empty field.
     *
     * <p>Tight rather than generous, because whatever is left when the run
     * commits is a turn the aircraft still has to fly while it is shooting.
     */
    private static final float RUN_IN_CONE_DEG = 35f;

    /**
     * How far outside its own firing range a craft rolls in from.
     *
     * <p>A run-in has to start outside the weapon's reach or the aircraft is
     * already shooting when the line is laid, which is not a run. Read off the
     * load rather than fixed, because a missile boat opens from half again as
     * far out as a gun fighter does and a single number would either crowd the
     * one or send the other out to no purpose.
     */
    private static final float RUN_IN_MARGIN_CELLS = 2f;

    /**
     * Whether a repositioning craft is out far enough and pointed close enough
     * at its objective to roll in on it.
     *
     * <p>Both halves are load-bearing. Without the standoff the craft rolls in
     * from inside its own firing range and the burst is over before it is
     * aimed; without the cone it rolls in sideways and flies the pass as one
     * long turn. A craft that satisfies neither carries on round its circuit,
     * which is what a repositioning aircraft is doing anyway — the heading
     * sweeps the whole compass on every circuit, so the condition is reached
     * rather than waited for.
     */
    private boolean linedUpToRunIn(long id, ShuttleMission mission, AirBody body) {
        float dx = mission.lzX - body.x;
        float dy = mission.lzY - body.y;
        float dist = (float) Math.hypot(dx, dy);
        AirOrdnance load = ordnanceOf(id, mission);
        float standoff = load == null
                ? RUN_LEAD_CELLS : load.firingRangeCells + RUN_IN_MARGIN_CELLS;
        if (dist < standoff) return false;
        float toTarget = (float) Math.toDegrees(Math.atan2(dy, dx));
        float nose = body.facingDegrees + 90f;
        float offNose = Math.abs(((toTarget - nose + 540f) % 360f) - 180f);
        return offNose <= RUN_IN_CONE_DEG;
    }

    /**
     * How far round the next run comes in from.
     *
     * <p>Not a fixed angle: a machine that re-attacked from the same bearing
     * every time would be flying a racetrack, and one that picked at random
     * would sometimes turn barely at all. Something a little over a right angle
     * is a wide circuit that visibly changes the direction of attack.
     */
    private static final float RUN_BEARING_SHIFT_DEG = 115f;

    /**
     * How far off the nose the target may lie and still be shot at.
     *
     * <p>Range on its own is not aim. A gun bolted to the nose can only put
     * fire where the aircraft is pointed, so a craft that has flown past its
     * target and is climbing away is not attacking it however near it still is
     * — and without this gate that was exactly what happened, for the whole
     * second half of every pass. The cone is what ends a run: as the craft
     * arrives over the position the bearing to it swings out through a right
     * angle in a fraction of a second, and fire stops there rather than
     * continuing to spray the ground behind.
     *
     * <p>Generous rather than tight, because the run line is flown by a body
     * with momentum and a craft crabbing a little is still attacking.
     */
    private static final float RELEASE_CONE_DEG = 30f;

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
        // A strike arrives on station, which is over the objective — so on the
        // first pass the craft is already well inside its own run-in and has
        // nowhere left to attack from. Send it out to the start of the line and
        // let it roll in like any later pass, rather than opening the attack
        // with a run that consists entirely of flying away.
        // A strike arrives on station, which is over the objective — so on the
        // first pass the craft is usually already inside its own run-in with
        // nowhere left to attack from, and the whole pass would consist of
        // flying away from the target while shooting. Whether it can simply run
        // in is the same question a later pass asks: is it out far enough, and
        // is it pointed the right way.
        if (linedUpToRunIn(id, mission, body)) {
            commitToTheLine(mission, body);
            mission.state = ShuttleState.ATTACK_RUN;
        } else {
            mission.state = ShuttleState.REPOSITION;
        }
    }

    /**
     * Re-lays the run through the target from wherever the craft has actually
     * ended up, at the moment it commits to the pass.
     *
     * <p>The line is still fixed for the whole run and still aimed at where the
     * enemy was when the aircraft rolled in — this is that moment, and not a
     * re-aim during the pass. What it removes is a lateral error the aircraft
     * had no way to correct. A run laid out one leg early is a line through the
     * target from a point the craft then has to <em>fly to</em>, and a machine
     * that needs twenty cells to come round arrives beside that point rather
     * than on it. From there it steers at the far end of the line, which from
     * an offset start is a chord: the pass goes past the position instead of
     * over it, by about a third of however far off the start was. Measured at
     * the wide turn radius, the closest a gun run came to its own objective was
     * five and a half cells, which for a weapon that lands its rounds within
     * two is a sortie that attacked an empty field.
     *
     * <p>Laid through the target from the craft, so the aircraft is on the line
     * by construction and the far end is straight ahead of it.
     */
    private static void commitToTheLine(ShuttleMission mission, AirBody body) {
        float dx = mission.lzX - body.x;
        float dy = mission.lzY - body.y;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-3f) return;   // On top of it; the laid line is as good as any.
        float dirX = dx / length;
        float dirY = dy / length;
        mission.lastRunBearingDeg = (float) Math.toDegrees(Math.atan2(dirY, dirX));
        mission.runFromX = body.x;
        mission.runFromY = body.y;
        mission.runToX = mission.lzX + dirX * RUN_OVERSHOOT_CELLS;
        mission.runToY = mission.lzY + dirY * RUN_OVERSHOOT_CELLS;
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
     *
     * <p>Where a round lands is not chosen here. It falls out of
     * {@link OrdnanceFlight}: a height, a launch and whatever the round keeps
     * of the aircraft's own motion. A shell is on the ground a dozen cells in
     * front before the aircraft has moved a hand's breadth; a bomb spends a
     * second and a half falling and lands behind the machine that dropped it.
     * The only thing added on top is scatter.
     *
     * <p>A round with a real flight time is <em>queued</em> rather than fired,
     * so it is genuinely in the air: the ground under it can change while it is
     * there, which is the difference between a bomb and a decision.
     */
    private void releaseOrdnance(long id, ShuttleMission mission, AirBody body, float dt) {
        AirOrdnance load = ordnanceOf(id, mission);
        if (load == null || detonations == null) return;
        if (!load.firesContinuously() && mission.roundsLeftThisPass <= 0) return;
        if (body.distanceTo(mission.lzX, mission.lzY) > load.firingRangeCells) return;
        if (!pointedAt(body, mission.lzX, mission.lzY)) return;
        mission.fireCooldown -= dt;
        if (mission.fireCooldown > 0f) return;
        mission.fireCooldown = load.fireInterval();
        if (!load.firesContinuously()) mission.roundsLeftThisPass--;

        OrdnanceFlight.Impact arrival = load.flight.deliver(
                body.x, body.y, body.facingDegrees, body.vx, body.vy);
        float impactX = arrival.x() + (float) rng.nextGaussian() * load.scatterCells;
        float impactY = arrival.y() + (float) rng.nextGaussian() * load.scatterCells;

        effects.spawnOrdnanceRelease(AirOrdnanceDelivery.release(
                id, load, body, Math.toRadians(body.facingDegrees + 90f),
                impactX, impactY, world.airFaction(id), arrival.flightTimeSec()));

        PendingDetonation round = new PendingDetonation(
                id, impactX, impactY, arrival.flightTimeSec(),
                load.aoeRadiusCells, load.damage, load.penetration,
                load.wallDamage, world.airFaction(id), /*aerialDelivery*/ true,
                /*wallDamageRadius*/ load.aoeRadiusCells, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false);
        if (arrival.flightTimeSec() < BattleSimulation.TICK_DT) {
            // A shell is already there. Queuing it would postpone the impact by
            // a whole tick for a flight that lasts a fortieth of one.
            detonations.detonateNow(round);
        } else {
            detonations.queue(round);
        }
    }

    /**
     * Whether the target lies close enough to the nose for the aircraft to be
     * attacking it at all.
     *
     * <p>The aircraft is the weapon, so being in range of something behind you
     * is not being able to shoot it. See {@link #RELEASE_CONE_DEG}.
     */
    private static boolean pointedAt(AirBody body, float targetX, float targetY) {
        double nose = Math.toRadians(body.facingDegrees + 90f);
        float noseX = (float) Math.cos(nose);
        float noseY = (float) Math.sin(nose);
        float dx = targetX - body.x;
        float dy = targetY - body.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1e-4f) return true;
        float alignment = (dx * noseX + dy * noseY) / dist;
        return alignment >= (float) Math.cos(Math.toRadians(RELEASE_CONE_DEG));
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
                                       float toX, float toY, boolean incoming,
                                       float floorT, float dt) {
        // Every leg runs the ramp now. The exception used to be a departure
        // straight out of an armed hover, which was already at cruise and would
        // otherwise dip and re-climb; that loiter no longer exists, so the
        // special case went with it rather than lingering as a flag nothing
        // ever sets.
        float remaining = body.distanceTo(toX, toY);
        float ratio = remaining / mission.legStartDist;
        if (ratio < 0f) ratio = 0f;
        if (ratio > 1f) ratio = 1f;
        float altitudeT = floorT + (1f - floorT) * (incoming ? ratio : (1f - ratio));
        world.setAltitudeT(id, altitudeT);
        // Advance the wobble phase; the scale multiplier is derived from
        // altitudeT + flightPhase by AirAppearance at render time, not stored.
        world.setFlightPhase(id, world.flightPhase(id)
                + dt * 2f * (float) Math.PI * AirAppearance.WOBBLE_HZ);
    }

    /**
     * Walks the drawn altitude toward where the craft has got to along a leg,
     * at a bounded rate.
     *
     * <p>Rate-limited, unlike the straight-line lerp above, because the leg it
     * measures against can be replaced while it is being flown: a craft sent
     * round again starts a fresh approach from wherever it is, and a bare lerp
     * would put it back at cruise height in one tick.
     *
     * @param ratio  how much of the leg is left, 1 at the start and 0 at the end
     * @param floorT the height the leg bottoms out at
     */
    private void driveAltitude(long id, float ratio, float floorT, float dt) {
        if (ratio < 0f) ratio = 0f;
        if (ratio > 1f) ratio = 1f;
        float target = floorT + (1f - floorT) * ratio;
        float step = ALTITUDE_RATE_PER_SEC * dt;
        float delta = target - world.altitudeT(id);
        if (delta > step) delta = step;
        if (delta < -step) delta = -step;
        world.setAltitudeT(id, world.altitudeT(id) + delta);
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
     * Finds a free cell adjacent to the LZ and spawns a marine there as a fresh
     * {@code Entity}. Returns {@code false} when no nearby cell is available this
     * tick (rare — only happens if the area around the LZ is fully clogged with
     * units or walls); caller leaves {@code marinesRemaining} unchanged and the
     * shuttle re-tries next interval.
     */
}
