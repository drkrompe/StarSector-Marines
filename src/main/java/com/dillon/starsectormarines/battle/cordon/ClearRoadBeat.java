package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.EscortPace;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.DozerBlade;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

/**
 * R1, Clear the road ({@code cordon-nouns.md}, Optional objectives): a wreck lies across the corridor in
 * the suburb, past the gap, and the base's dozer has to push it off before the first convoy needs the
 * road. The wreck is laid only when this beat is rolled, at setup, on the ground the layout held for it
 * (crate stacks, as {@code BlockKind.WRECK} is laid: there is no wreck art yet); with no dozer to clear it
 * a laid wreck stopped every convoy. The base's dozer starts moved up behind the gap and drives the corridor
 * to the far side of the wreck, its blade opening the way, with an escort out of the allied armour's surplus that it
 * waits for and keeps to ({@link EscortPace}), and the company asked only when no hull was free ({@code cordon-allied-command.md}, rule 3). Succeeds
 * when the road through the wreck is open before the first convoy's lead truck reaches it; fails when the
 * dozer dies first, or the convoy reaches a wreck still across the road (the dozer keeps at it for the
 * convoys after).
 */
final class ClearRoadBeat extends CordonBeat {

    /** Cells either side of the road's middle that must be open for a truck. */
    static final int ROAD_HALF = 1;
    /** Corridor cells past the wreck the dozer pulls aside at once the road is open. */
    static final int PAST = 8;
    /** Cells off the road the dozer pulls aside to once the road is open. */
    static final int ASIDE = 6;
    /** Corridor cells short of the gap the dozer starts. */
    static final int FORWARD = 12;
    /** Cells from a shut road cell the dozer's blade works it. */
    static final float BLADE_REACH = 6.5f;
    /** Corridor cells short of the wreck a convoy's truck needs the road open. */
    static final int NEEDED = 12;
    private static final String CRATES = "doodad.industrial-crate-stack";

    private final List<int[]> wreck = new ArrayList<>();
    /** The wreck's cells across the road's middle a truck needs open. */
    private final List<int[]> road = new ArrayList<>();
    private int wreckIndex = -1;
    private long dozer;
    private boolean sent, through, cleared;
    /** The shut road cell the dozer was last sent to. */
    private int[] target;
    /** Blade push not yet spent as whole structure points. */
    private float push;
    /** How the dozer keeps to its escort: it waits for it where it was set down, and holds when it falls behind. */
    private EscortPace pace;

    ClearRoadBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: open before the first convoy needs it; a dead dozer or a convoy at a shut road fails. */
    static Status rule(boolean cleared, boolean dozerAlive, boolean convoyAtWreck) {
        if (cleared) return Status.SUCCEEDED;
        if (!dozerAlive || convoyAtWreck) return Status.FAILED;
        return Status.ACTIVE;
    }

    /**
     * Lays the wreck on its held ground, at setup: every open cell of the site closed to movement and open to
     * sight, a crate stack on it the blade can push. Returns the cells laid.
     */
    static List<int[]> lay(BattleSimulation sim, int[] site) {
        List<int[]> laid = new ArrayList<>();
        TileRegistry registry = TileRegistry.installed();
        DoodadDef def = registry == null ? null : registry.doodad(CRATES);
        if (site == null || def == null) return laid;
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        for (int y = site[1]; y <= site[3]; y++) {
            for (int x = site[0]; x <= site[2]; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.isFixture(x, y)) continue;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, true);
                topology.setWall(x, y, false);
                topology.setFixture(x, y, true);
                sim.addDoodad(new Doodad(x, y, def));
                laid.add(new int[]{x, y});
            }
        }
        for (int[] c : laid) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (grid.inBounds(c[0] + dx, c[1] + dy)) grid.recomputeCoverAt(c[0] + dx, c[1] + dy);
                }
            }
        }
        return laid;
    }

    /** Lays the wreck and sets the dozer down at the base end of the road: at setup. */
    void setUp(BattleSimulation sim) {
        wreck.addAll(lay(sim, beats.bounds(CordonPlan.CORRIDOR_WRECK)));
        List<int[]> corridor = beats.corridor();
        if (wreck.isEmpty() || corridor == null) return;
        int[] mid = beats.centre(CordonPlan.CORRIDOR_WRECK);
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < corridor.size(); i++) {
            long dx = corridor.get(i)[0] - mid[0], dy = corridor.get(i)[1] - mid[1];
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                wreckIndex = i;
            }
        }
        int[] at = corridor.get(wreckIndex);
        for (int[] c : wreck) {
            if (Math.abs(c[0] - at[0]) <= ROAD_HALF && Math.abs(c[1] - at[1]) <= ROAD_HALF) road.add(c);
        }
    }

    /** The wreck's cells as laid, for evidence. */
    List<int[]> wreck() { return wreck; }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        if (wreckIndex < 0 || road.isEmpty()) return Status.LAPSED;
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null) return Status.LAPSED;
        List<int[]> corridor = beats.corridor();
        if (!sent) {
            sent = true;
            // Moved up behind the gap before the battle: from the base it lost the race to the first convoy on
            // every long corridor (a dozer is a third of a truck's pace).
            int[] from = corridor.get(Math.max(convoys.entry(), convoys.gapIndex() - FORWARD));
            dozer = beats.deploy(sim, VehicleType.DOZER_BREACHER, from[0], from[1]);
            if (dozer == 0L) return Status.LAPSED;
            pace = new EscortPace(tick);
        }
        if (!cleared && CordonBeats.alive(sim, dozer)) {
            boolean out = pace.setOut();
            pace.step(sim, dozer, beats.escortOf(dozer));
            // Onto the wreck's own middle, which only the blade's view of the ground opens: sent past it, the dozer
            // drove round the wreck through the fields and pushed nothing.
            if (!out && pace.setOut()) setOut(sim);
        }
        int[] lead = convoys.truck(sim, 1, 0);
        boolean atWreck = lead != null && !convoys.detoured() && lead[0] >= wreckIndex - NEEDED;
        return rule(cleared, CordonBeats.alive(sim, dozer), atWreck);
    }

    /** Sends the dozer from where it waited for its escort onto the wreck. */
    private void setOut(BattleSimulation sim) {
        int[] at = beats.corridor().get(wreckIndex);
        beats.command().station(sim, dozer, at[0], at[1]);
    }

    /** The road through the wreck opened: the convoys' routes are laid again through it, even after a failure. */
    @Override
    void watch(BattleSimulation sim, int tick) {
        if (pace != null && !active() && CordonBeats.alive(sim, dozer)) {
            // Its escort ends with the beat; the dozer keeps at the wreck for the convoys after, on its own.
            if (!pace.setOut()) setOut(sim);
            pace.release(sim, dozer);
        }
        if (cleared || road.isEmpty() || beats.sequence().convoys() == null) return;
        int[] closed = null;
        for (int[] c : road) {
            if (!sim.getGrid().isWalkable(c[0], c[1])) {
                closed = c;
                break;
            }
        }
        if (closed == null) {
            cleared = true;
            if (pace != null) pace.release(sim, dozer);
            beats.sequence().convoys().relay(sim);
            if (CordonBeats.alive(sim, dozer)) {
                // Off the road again, past the wreck, so it is not in the trucks' way.
                List<int[]> corridor = beats.corridor();
                int[] past = corridor.get(Math.min(corridor.size() - 1, wreckIndex + PAST));
                int[] aside = CordonGround.nearestWalkable(sim.getGrid(), past[0] + ASIDE, past[1] + ASIDE);
                if (aside != null) beats.command().station(sim, dozer, aside[0], aside[1]);
            }
            return;
        }
        if (sent && pace != null && pace.setOut() && CordonBeats.alive(sim, dozer)) drive(sim, closed);
    }

    /**
     * Keeps the dozer at the wreck until the road through it is open. The move order stops a hull short of a
     * goal it cannot stand on, so the blade often never bore on the wreck; a dozer standing within
     * {@link #BLADE_REACH} of a shut road cell pushes it at the blade's own rate ({@link DozerBlade#PUSH_PER_SECOND})
     * through the ordinary wall-damage path. Once it has worked at the wreck it is sent on past it.
     */
    private void drive(BattleSimulation sim, int[] closed) {
        GroundBody body = sim.convoy().body(dozer);
        if (Math.abs(body.x - closed[0]) <= BLADE_REACH && Math.abs(body.y - closed[1]) <= BLADE_REACH) {
            through = true;
            push += DozerBlade.PUSH_PER_SECOND * BattleSimulation.TICK_DT;
            int spend = (int) push;
            push -= spend;
            if (spend > 0) sim.mapEditor().damageWall(closed[0], closed[1], spend);
        } else if (target != closed) {
            target = closed;
            beats.command().station(sim, dozer, closed[0], closed[1]);
        }
    }

    @Override
    String describe(BattleSimulation sim) {
        int open = 0;
        for (int[] c : road) if (sim.getGrid().isWalkable(c[0], c[1])) open++;
        String hull = !CordonBeats.alive(sim, dozer) ? "dozer gone" : String.format(Locale.ROOT,
                "dozer at %.0f,%.0f structure %.0f", sim.convoy().body(dozer).x, sim.convoy().body(dozer).y,
                sim.convoy().structure(dozer));
        VehicleMoveOrderService.RefusedOrder refused = CordonBeats.alive(sim, dozer)
                ? sim.getVehicleMoveOrderService().refusal(dozer) : null;
        int[] at = beats.corridor().get(wreckIndex);
        return "wreck " + wreck.size() + " cells at corridor cell " + wreckIndex + " (" + at[0] + "," + at[1] + "), "
                + open + "/" + road.size() + " road cells open; " + hull + (through ? ", through" : "")
                + (refused == null ? "" : ", refused " + refused.reason() + " to " + refused.requestedX() + ","
                + refused.requestedY());
    }

    @Override
    long escortHull(BattleSimulation sim) {
        return !cleared && CordonBeats.alive(sim, dozer) ? dozer : 0L;
    }

    @Override
    int[] call(BattleSimulation sim) {
        if (!CordonBeats.alive(sim, dozer) || beats.covered(dozer)) return null;
        GroundBody body = sim.convoy().body(dozer);
        return new int[]{(int) body.x, (int) body.y, 2};
    }
}
