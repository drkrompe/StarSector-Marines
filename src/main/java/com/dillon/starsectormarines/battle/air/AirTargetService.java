package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.function.LongConsumer;

/**
 * An aircraft as something to shoot at — which craft are reachable right now,
 * where they are, and how fast they are going.
 *
 * <p>The air twin of {@code ConvoyService}'s target surface, and deliberately
 * the same shape. A craft carries {@code IDENTITY}, {@code HEALTH} and
 * {@code ARMOR} and no {@code POSITION}, {@code COMBAT}, {@code MOVEMENT} or
 * {@code ROLE}, so occupancy, separation, the fire system, the mover and the
 * planner all skip it for lack of their components; what reaches it instead is
 * the spatial index, which indexes bodies. That membership-narrowing is what
 * lets an aircraft be perceived, traced, hit, attributed and killed by the
 * paths that already do those things rather than by an air-aware branch in each
 * of them.
 *
 * <p><b>Reachable means on its wheels in the open.</b> A craft in the air is a
 * body nothing on the ground can touch — anti-air is its own unbuilt feature
 * with its own altitude question — and one loading or landed is exempt for the
 * reason {@link ShuttleMission#isOnItsWheelsAndExposed} records. So this is the
 * gate the index and the splash sweep consult, exactly as
 * {@code ConvoyService.isTargetable} is.
 *
 * <p>See {@code air-nouns.md}.
 */
public final class AirTargetService {

    private final UnitRosterService roster;

    public AirTargetService(UnitRosterService roster) {
        this.roster = roster;
    }

    /** True iff {@code id} is an air craft rather than a ground body or a vehicle. */
    public boolean isAircraft(long id) {
        return roster.entityWorld().has(id, roster.components().SHUTTLE_MISSION);
    }

    /**
     * True iff {@code id} is an aircraft that ground fire can currently reach:
     * on its wheels, in the open, and still structurally alive.
     */
    public boolean isTargetable(long id) {
        ShuttleMission mission = roster.world().mission(id);
        return mission != null && mission.isOnItsWheelsAndExposed()
                && roster.isAliveById(id);
    }

    /** The craft's faction, or {@code null} if {@code id} is not an air craft. */
    public Faction faction(long id) {
        return roster.world().airFaction(id);
    }

    /**
     * Visits every aircraft ground fire can reach this instant. The population
     * is a handful per battle, so the per-tick index rebuild and each blast's
     * splash sweep both walk it directly rather than keeping a second list that
     * could drift from the world.
     */
    public void forEachTargetable(LongConsumer visitor) {
        EntityWorld world = roster.entityWorld();
        BattleComponents components = roster.components();
        for (ArchetypeTable table : world.matched(components.airCraft)) {
            for (int row = 0, rows = table.rowCount(); row < rows; row++) {
                long id = table.entityAt(row);
                if (isTargetable(id)) visitor.accept(id);
            }
        }
    }

    /**
     * Velocity along X, in cells/sec. The body composes it from heading and
     * one speed while the craft is rolling, so a shooter's lead against a
     * taxiing aircraft is solved against what it is actually doing.
     */
    public float velocityX(long id) {
        AirBody body = roster.world().kinematics(id);
        return body == null ? 0f : body.vx;
    }

    /** Velocity along Y, in cells/sec. */
    public float velocityY(long id) {
        AirBody body = roster.world().kinematics(id);
        return body == null ? 0f : body.vy;
    }
}
