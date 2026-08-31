package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
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
 * <p><b>Reachable means on its wheels in the open.</b> A craft in the air is a
 * body nothing on the ground can touch — anti-air is its own unbuilt feature
 * with its own altitude question — and one loading or landed is exempt for the
 * reason {@link ShuttleMission#isOnItsWheelsAndExposed} records.
 *
 * <p>See {@code air-nouns.md}.
 */
public final class AirTargetService implements BodyCarrier {

    /**
     * Fraction of a hull's drawn length that stands in for its body circle.
     *
     * <p>A single radius has to speak for a shape that is long and narrow, and
     * the two obvious answers are both wrong: half the length circumscribes the
     * hull and claims wingspans of empty air either side of it, while the
     * roster's authored half-cell describes a machine that is plainly twelve
     * cells of aircraft on the map. A vanilla hull is drawn roughly twice as
     * long as it is wide, so half its length approximates its span and half of
     * that is the radius of the circle inscribed in it.
     */
    private static final float RADIUS_PER_DRAWN_LENGTH = 0.25f;

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
     * True iff {@code id} is an aircraft that ground fire can currently reach:
     * on its wheels, in the open, and still structurally alive.
     */
    @Override
    public boolean isTargetable(long id) {
        ShuttleMission mission = roster.world().mission(id);
        return mission != null && mission.isOnItsWheelsAndExposed()
                && roster.isAliveById(id);
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
     * The craft's body circle, derived from the hull it is drawn as rather than
     * from the one authored number on {@code UnitType.BASED_AIRCRAFT}. A Kite
     * and a Valkyrie are a four-fold size ladder and answering the same figure
     * for both made a blast's catch radius against a transport the same as
     * against a light shuttle.
     */
    @Override
    public float targetRadius(long id) {
        Airframe frame = roster.world().airframe(id);
        float drawnLength = frame == null
                ? UnitType.BASED_AIRCRAFT.radius
                : HullFootprintResolver.visualLengthCells(frame.renderHullId())
                    * AirAppearance.GROUND_SCALE;
        return drawnLength * RADIUS_PER_DRAWN_LENGTH;
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
