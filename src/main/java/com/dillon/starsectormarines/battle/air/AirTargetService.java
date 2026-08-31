package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.function.LongConsumer;

/**
 * An aircraft as something to shoot at — the air {@link BodyCarrier}.
 *
 * <p>A craft carries {@code IDENTITY}, {@code HEALTH} and {@code ARMOR} and no
 * {@code POSITION}, {@code COMBAT}, {@code MOVEMENT} or {@code ROLE}, so
 * occupancy, separation, the fire system, the mover and the planner all skip it
 * for lack of their components; what reaches it instead is the shared body
 * surface. That membership-narrowing is what lets an aircraft be perceived,
 * traced, hit, attributed and killed by the paths that already do those things
 * rather than by an air-aware branch in each of them.
 *
 * <p><b>It answers presence and altitude, not "can this be shot".</b> Whether a
 * particular shooter can engage a craft is a relation between the two, and
 * {@code EngagementService} owns it; what belongs here is whether the aircraft
 * is in the battle at all and whether it is in the air. Splitting those is what
 * lets a defence post reach a craft on final while a rifle section cannot,
 * without either answer being written out at a call site.
 *
 * <p>See {@code air-nouns.md}.
 */
public final class AirTargetService implements BodyCarrier {

    private final UnitRosterService roster;

    /** Setup-time cycle break: {@code AirSystem} is constructed after the damage service. */
    private LongConsumer destructionSink;

    public AirTargetService(UnitRosterService roster) {
        this.roster = roster;
    }

    /** True iff {@code id} is an air craft rather than a ground body or a vehicle. */
    public boolean isAircraft(long id) {
        return roster.entityWorld().has(id, roster.components().SHUTTLE_MISSION);
    }

    @Override
    public boolean owns(long id) {
        return isAircraft(id);
    }

    /**
     * Whether the craft is in the battle: on the map, alive, and not down with
     * its ramp open.
     *
     * <p>Two grounded phases are exempt and neither is about altitude. A
     * loading craft's passengers have already been taken off the roster, so
     * making it shootable would owe them a disposition nothing gives them; a
     * landed one is the same craft at the other end of the trip. Everything
     * else — taxiing, holding short, rolling, and every phase in the air — is
     * present, and whether a particular shooter can reach it is
     * {@link #isAirborne} plus that shooter's own weapon.
     */
    @Override
    public boolean isPresent(long id) {
        ShuttleMission mission = roster.world().mission(id);
        return mission != null && mission.isOnMap()
                && mission.state != ShuttleState.LOADING
                && mission.state != ShuttleState.LANDED
                && roster.isAliveById(id);
    }

    /**
     * Whether the craft is in the air, asked of the locomotion rather than of a
     * list of phases — a list is a thing the next phase added gets left out of,
     * which is how replacing an armed loiter with attack runs once made every
     * strike invulnerable while it attacked.
     */
    @Override
    public boolean isAirborne(long id) {
        ShuttleMission mission = roster.world().mission(id);
        return mission != null && AirLocomotion.of(mission.state).airborne();
    }

    /** The craft's faction, or {@code null} if {@code id} is not an air craft. */
    @Override
    public Faction faction(long id) {
        return roster.world().airFaction(id);
    }

    /**
     * Visits every aircraft in the world, in the air or on its wheels. The
     * population is a handful per battle, so the shared body snapshot walks it
     * directly rather than keeping a second list that could drift.
     */
    @Override
    public void forEachBody(LongConsumer visitor) {
        EntityWorld world = roster.entityWorld();
        BattleComponents components = roster.components();
        for (ArchetypeTable table : world.matched(components.airCraft)) {
            for (int row = 0, rows = table.rowCount(); row < rows; row++) {
                visitor.accept(table.entityAt(row));
            }
        }
    }

    /**
     * Velocity along X, in cells/sec. The body composes it from heading and
     * one speed while the craft is rolling, so a shooter's lead against a
     * taxiing aircraft is solved against what it is actually doing.
     */
    @Override
    public float velocityX(long id) {
        AirBody body = roster.world().kinematics(id);
        return body == null ? 0f : body.vx;
    }

    /** Velocity along Y, in cells/sec. */
    @Override
    public float velocityY(long id) {
        AirBody body = roster.world().kinematics(id);
        return body == null ? 0f : body.vy;
    }

    /**
     * The craft's body circle, asked of the airframe rather than derived here.
     * The hull standing on a hardstand answers the same question through the
     * same method, which is what keeps a Valkyrie the same size across the one
     * handoff this model keeps still.
     */
    @Override
    public float targetRadius(long id) {
        Airframe frame = roster.world().airframe(id);
        return frame == null ? UnitType.BASED_AIRCRAFT.radius : frame.targetRadiusCells();
    }

    @Override
    public float hitHalfHeight(long id) {
        return UnitType.BASED_AIRCRAFT.hitHalfHeight;
    }

    /**
     * Zero: a craft on its wheels is a thing to shoot at rather than a thing to
     * take cover from. Its mounted turrets run their own aim loop over the
     * battle, and taxiing is not somewhere they hunt.
     */
    @Override
    public float weaponRange(long id) {
        return 0f;
    }

    /** Setup-time cycle break: {@code AirSystem} is constructed after the damage service. */
    public void setDestructionSink(LongConsumer sink) {
        this.destructionSink = sink;
    }

    /**
     * Converges on {@code AirSystem}'s own shoot-down. There is one way for an
     * aircraft to die and it lights the cook-off, leaves the wreck and gives
     * the runway back; a second death path would be a kill that quietly skipped
     * all three.
     */
    @Override
    public void destroy(long id) {
        if (destructionSink != null) destructionSink.accept(id);
    }
}
