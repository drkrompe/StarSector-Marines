package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.cordon.AlliedColumns;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Broad Front's allied lanes (`heart-nouns.md`, Lane): a column of militia at
 * the foot of each lane, sent up it together at the opening, each taking the
 * satellites in its own lane and then converging on the nearest living mother.
 *
 * <p>A column comes on and marches to its lane's first bore as the Cordon's
 * scripted columns do ({@link AlliedColumns}). Once it has arrived it is the
 * lane's: every second it is sent on to the lane's next bore when the one it is
 * on falls. The columns fight under garrison authority; they never end the
 * battle, and a lane broken below a third of its strength is a cost on the
 * outcome, not a loss.
 */
public final class HeartLanes {

    public static final String ISSUER = "heart-lanes";
    /** Militia teams in each column. */
    static final int TEAMS = 3;
    /** Seconds after the battle opens that the columns step off, together. */
    static final int STEP_OFF_SECONDS = 2;
    /** A member this close to its bore has arrived at it. */
    static final int ARRIVE_CELLS = 12;
    static final int RETARGET_TICKS = Math.round(1f / BattleSimulation.TICK_DT);

    private final class Lane implements AlliedColumns.Orders {
        final int number;
        final HeartLane ground;
        final int entryX, entryY;
        final AlliedColumns column;
        List<Integer> squads;
        int targetX = -1, targetY = -1;
        boolean broken;

        Lane(int number, HeartLane ground, int entryX, int entryY, RiskLevel risk, Random rng) {
            this.number = number;
            this.ground = ground;
            this.entryX = entryX;
            this.entryY = entryY;
            this.column = new AlliedColumns(List.of(new AlliedColumns.Departure(STEP_OFF_SECONDS, entryX, entryY)),
                    TEAMS, ISSUER, risk, rng);
        }

        @Override public int[] destination(BattleSimulation sim) {
            KnownBore bore = ground.target(known(sim), entryX, entryY);
            return bore == null ? null : new int[]{bore.x(), bore.y()};
        }

        @Override public boolean stillNeeded(BattleSimulation sim, int x, int y) {
            return livingBoreAt(sim, x, y);
        }

        @Override public boolean arrived(int x, int y, int destX, int destY) {
            return Math.max(Math.abs(x - destX), Math.abs(y - destY)) <= ARRIVE_CELLS;
        }

        @Override public void joined(BattleSimulation sim, int ignored, List<Integer> joined, int destX, int destY) {
            squads = new ArrayList<>(joined);
            targetX = destX;
            targetY = destY;
        }
    }

    private final List<Lane> lanes = new ArrayList<>();

    /**
     * @param grounds each lane's band, west to east
     * @param entries each lane's foot on the start line, {@code [x, y]}, in the same order
     */
    public HeartLanes(List<HeartLane> grounds, List<int[]> entries, RiskLevel risk, long seed) {
        Random rng = new Random(seed);
        for (int i = 0; i < grounds.size(); i++) {
            lanes.add(new Lane(i + 1, grounds.get(i), entries.get(i)[0], entries.get(i)[1], risk, rng));
        }
    }

    /** One tick; the lanes' beats go into {@code beats}. */
    public void tick(BattleSimulation sim, List<String> beats) {
        int tick = sim.getSimTickIndex();
        for (Lane lane : lanes) {
            if (lane.squads == null) {
                lane.column.advance(sim, tick, lane, (beat, observerOnly) -> {
                    if (beat.startsWith("column_arrived_")) beats.add("lane_arrived_" + lane.number);
                });
                continue;
            }
            lane.squads.removeIf(squad -> sim.squadMemberCount(squad) == 0);
            int alive = 0;
            for (int squad : lane.squads) alive += sim.squadMemberCount(squad);
            if (!lane.broken && alive * 3 < TEAMS * ColonyMilitia.POST_SIZE) {
                lane.broken = true;
                beats.add("lane_broken_" + lane.number);
            }
            if (lane.squads.isEmpty() || tick % RETARGET_TICKS != 0) continue;
            if (livingBoreAt(sim, lane.targetX, lane.targetY)) continue;
            KnownBore next = lane.ground.target(known(sim), lane.targetX, lane.targetY);
            if (next == null) continue;
            lane.targetX = next.x();
            lane.targetY = next.y();
            for (int squad : lane.squads) {
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, next.x(), next.y()),
                        CommandAuthority.GARRISON, ISSUER, "lane " + lane.number + " goes on to " + next.key());
            }
        }
    }

    private static List<KnownBore> known(BattleSimulation sim) {
        List<KnownBore> out = new ArrayList<>();
        for (SwarmBore bore : sim.swarmBores().bores()) {
            out.add(new KnownBore(bore.beatKey(), bore.mother(), bore.x(), bore.y(), !bore.collapsed()));
        }
        return out;
    }

    private static boolean livingBoreAt(BattleSimulation sim, int x, int y) {
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (!bore.collapsed() && bore.x() == x && bore.y() == y) return true;
        }
        return false;
    }
}
