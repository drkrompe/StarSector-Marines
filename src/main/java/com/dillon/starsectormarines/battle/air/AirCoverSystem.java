package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * Flies the battle's committed fighter wings as off-map sorties: in across a
 * stated map edge, gun runs on the densest enemy concentration, out across the
 * same edge.
 *
 * <p><b>This is the other origin.</b> {@link AirStrikeSystem} launches an
 * aircraft a garrison owns off a berth it owns; this launches one nobody on the
 * map owns, from a carrier overhead. Everything between arriving on station and
 * turning for home is the same code, because the difference between the two is
 * where the sortie is from and not what kind of aircraft it is. A corridor
 * sortie touches no berth, claims no runway, and lands nowhere — it has no
 * hardstand on this map to come home to, so it goes home off the edge it came
 * in by.
 *
 * <p><b>It cannot conjure an aircraft.</b> Every entry and exit point comes
 * from an {@link AirCorridor}, which is off the map by construction, and a
 * dispatch that cannot build one — no map, or no friendly unit to say which
 * edge is ours — slips the sortie rather than picking somewhere on the map to
 * start from. That failure mode has bitten this project once already and cost a
 * playtest's confidence in the whole air arm.
 *
 * <p>The schedule is the wing's own: sortie {@code k} is due at
 * {@code firstArrivalSec + k * spawnIntervalSec}, exactly as the wings were
 * authored for the overlay this replaced. A sortie that comes due with nothing
 * worth attacking is retried shortly rather than spent on an empty field.
 */
public final class AirCoverSystem {

    /**
     * Simultaneous corridor sorties, both sides together.
     *
     * <p>A backstop for a pathological roster rather than a balance dial — the
     * wing schedules normally keep the number down on their own. Six aircraft
     * over one battle is already a great deal of air.
     */
    private static final int MAX_CONCURRENT = 6;

    /**
     * Sim-seconds a due sortie waits before asking again, when it asked and the
     * answer was no.
     *
     * <p>Short, because the reason is usually about to change: an enemy that is
     * not yet concentrated shortly will be, and a side with nobody on the map
     * yet is a side whose transports are still inbound.
     */
    private static final float RETRY_SEC = 4f;

    /** Sim-seconds since this battle started, against which the wing schedules are read. */
    private float simTime;

    /** The roster being flown; compared by identity so a re-attached mission re-arms the counters. */
    private FlybyRoster roster = FlybyRoster.EMPTY;

    /** Sorties dispatched per wing, parallel to the roster's wing list. */
    private int[] dispatched = new int[0];

    /** Per-wing hold-off after a sortie came due and could not be flown. */
    private float[] retryIn = new float[0];

    /** Advances the schedule and dispatches whatever is due and possible. */
    public void tick(float dt, BattleSimulation sim) {
        if (sim == null || sim.isComplete()) return;
        simTime += dt;

        FlybyRoster current = sim.getFlybyRoster();
        if (current == null) current = FlybyRoster.EMPTY;
        if (current != roster) {
            roster = current;
            dispatched = new int[roster.wings.size()];
            retryIn = new float[roster.wings.size()];
        }
        if (roster.isEmpty()) return;

        int airborne = corridorSortiesOut(sim);
        for (int i = 0; i < roster.wings.size(); i++) {
            if (retryIn[i] > 0f) retryIn[i] -= dt;
            if (airborne >= MAX_CONCURRENT) return;
            FighterWing wing = roster.wings.get(i);
            if (dispatched[i] >= wing.sortieCount) continue;
            float dueAt = wing.firstArrivalSec + dispatched[i] * wing.spawnIntervalSec;
            if (simTime < dueAt || retryIn[i] > 0f) continue;
            if (dispatch(sim, wing)) {
                dispatched[i]++;
                airborne++;
            } else {
                retryIn[i] = RETRY_SEC;
            }
        }
    }

    /**
     * Puts one of this wing's aircraft over the battle.
     *
     * @return false when the sortie could not be flown — no target worth it, or
     *         no way to state where off the map this side comes from. Both are
     *         declines; neither invents an origin.
     */
    private boolean dispatch(BattleSimulation sim, FighterWing wing) {
        Faction enemy = opposing(wing.side);
        long target = EnemyConcentration.densest(sim, enemy);
        if (target == 0L) return false;
        World world = sim.world();
        float targetX = world.x(target);
        float targetY = world.y(target);

        float[] home = friendlyCentroid(sim, wing.side);
        if (home == null) return false;
        AirCorridor corridor = AirCorridor.acrossNearestEdge(
                wing.profile.name(), sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                home[0], home[1], targetX, targetY);
        if (corridor == null) return false;

        long craft = sim.spawnSortie(wing.profile, wing.side, targetX, targetY,
                corridor.entryX, corridor.entryY, corridor.exitX, corridor.exitY, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        // Its business over the objective is its guns, so it arrives on station
        // rather than on a cell. Nothing else is set: no berth to owe itself
        // back to, no strip to claim, and the default egress already flies a
        // craft that touched neither one back out to its exit point.
        mission.strikeSortie = true;
        mission.postDeliveryDisposition = PostDeliveryDisposition.DEPART;
        return true;
    }

    /** Corridor sorties currently on the map, on either side. */
    private int corridorSortiesOut(BattleSimulation sim) {
        int out = 0;
        World world = sim.world();
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = world.mission(id);
            if (mission == null || !mission.strikeSortie) continue;
            if (mission.homeBerth != null || mission.usesRunway) continue;
            out++;
        }
        return out;
    }

    /**
     * The centre of mass of this side's live ground force, or {@code null} when
     * it has nobody on the map.
     *
     * <p>What decides which edge is ours. A side with nothing on the ground yet
     * has no direction a player would read as friendly, so the sortie waits
     * rather than picking one — which is also the right behaviour at the start
     * of a battle, when the first wave is still inbound.
     */
    private static float[] friendlyCentroid(BattleSimulation sim, Faction side) {
        UnitRosterService roster = sim.getRoster();
        long[] ours = roster.factionDenseArray(side);
        int count = roster.factionLiveCount(side);
        if (count <= 0) return null;
        World world = sim.world();
        float sumX = 0f, sumY = 0f;
        for (int i = 0; i < count; i++) {
            sumX += world.x(ours[i]);
            sumY += world.y(ours[i]);
        }
        return new float[] { sumX / count, sumY / count };
    }

    private static Faction opposing(Faction side) {
        return side == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
    }
}
